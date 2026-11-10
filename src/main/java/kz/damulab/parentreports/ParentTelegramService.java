package kz.damulab.parentreports;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Одноразовое подключение и подписка родителя; дети всегда определяются текущими связями. */
@Service
public class ParentTelegramService {
    final JdbcTemplate db;
    final TransactionTemplate tx;
    final Clock clock;
    final TelegramProperties config;
    final TelegramClient telegram;
    public ParentTelegramService(JdbcTemplate db,TransactionTemplate tx,Clock clock,TelegramProperties config,TelegramClient telegram) {
        this.db=db;this.tx=tx;this.clock=clock;this.config=config;this.telegram=telegram;
    }
    public long parent(String email) {
        var ids=db.queryForList("select p.id from parent_profiles p join app_users u on u.id=p.user_id where lower(u.email)=lower(?) and u.enabled=true",Long.class,email);
        if(ids.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ids.getFirst();
    }
    public Map<String,Object> state(String email) {
        var rows=db.queryForList("select * from parent_telegram_subscriptions where parent_id=?",parent(email));
        return rows.isEmpty()?Map.of("connected",false,"enabled",false,"send_day",1,"send_time","09:00","zone_id","Asia/Almaty"):rows.getFirst();
    }
    /** Создаёт ссылку только из авторизованного кабинета; новый токен отзывает предыдущий. */
    public String connect(String email,String language) {
        if(!config.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Telegram пока не подключён администратором");
        long parent=parent(email);
        return tx.execute(status->{
            // Блокировка профиля сериализует первые подключения, когда строки подписки ещё нет.
            db.queryForObject("select id from parent_profiles where id=? for update",Long.class,parent);
            if(db.queryForObject("select count(*) from parent_telegram_subscriptions where parent_id=?",Integer.class,parent)==0)
                db.update("insert into parent_telegram_subscriptions(parent_id,language) values (?,?)",parent,"kk".equals(language)?"kk":"ru");
            db.update("delete from parent_telegram_tokens where parent_id=?",parent);
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
            String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            db.update("insert into parent_telegram_tokens values (?,?,?)",hash(token),parent,now().plusMinutes(10));
            return "https://t.me/"+config.botUsername()+"?start="+token;
        });
    }
    /** Сохраняет только два параметра расписания и переключатель рассылки. */
    public void settings(String email,boolean enabled,int day,LocalTime time) {
        if(day<1 || day>7 || time==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long parent=parent(email);
        tx.executeWithoutResult(status->{
            var rows=db.queryForList("select * from parent_telegram_subscriptions where parent_id=? for update",parent);
            if(rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("connected"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Подключите Telegram");
            var row=rows.getFirst();
            db.update("update parent_telegram_subscriptions set enabled=?,send_day=?,send_time=?,next_run_at=? where parent_id=?",enabled,day,time,
                    next(clock.instant(),day,time,ZoneId.of((String)row.get("zone_id"))),parent);
            if(!enabled) cancel(parent);
        });
    }
    /** Дубликаты update_id от Telegram не выполняют привязку или /stop второй раз. */
    public void update(JsonNode update) {
        if(!update.path("update_id").isIntegralNumber() || update.path("update_id").asLong()<0) return;
        String[] reply=new String[1]; long[] chat={0};
        tx.executeWithoutResult(status->{
            // Короткая общая блокировка сериализует входящие обновления на нескольких экземплярах.
            // Внешний HTTP-вызов выполняется после фиксации транзакции.
            db.queryForObject("select id from parent_telegram_webhook_lock where id=1 for update",Integer.class);
            long id=update.path("update_id").asLong();
            if(db.queryForObject("select count(*) from parent_telegram_updates where update_id=?",Integer.class,id)>0) return;
            db.update("insert into parent_telegram_updates values (?,?)",id,now());
            var msg=update.path("message");
            if(!"private".equals(msg.path("chat").path("type").asText()) || msg.path("from").path("is_bot").asBoolean()) return;
            chat[0]=msg.path("chat").path("id").asLong(); long user=msg.path("from").path("id").asLong();
            if(chat[0]<=0 || user!=chat[0]) return;
            String text=msg.path("text").asText("");
            if(text.equals("/stop")) {
                var parents=db.queryForList("select parent_id from parent_telegram_subscriptions where telegram_user_id=? and chat_id=? for update",Long.class,user,chat[0]);
                for(long p:parents) {db.update("update parent_telegram_subscriptions set enabled=false where parent_id=?",p);cancel(p);}
                reply[0]="Есептерді жіберу тоқтатылды. Оны Damulab жеке кабинетінде қайта қосуға болады.\n\nРассылка остановлена. Возобновить её можно в кабинете Damulab.";
            } else if(text.startsWith("/start ")) {
                String token=text.substring(7).trim();
                if(!token.matches("[A-Za-z0-9_-]{43}")) {reply[0]="Damulab жеке кабинетіндегі жаңа сілтемені ашыңыз.\n\nОткройте новую ссылку из кабинета Damulab.";return;}
                var tokens=db.queryForList("select parent_id from parent_telegram_tokens where token_hash=? and expires_at>? for update",Long.class,hash(token),now());
                if(tokens.isEmpty()) {reply[0]="Сілтеменің мерзімі өткен немесе ол қолданылған. Telegram-ды Damulab жеке кабинетінен қосыңыз.\n\nСсылка истекла или использована. Подключите Telegram из кабинета Damulab.";return;}
                long p=tokens.getFirst();
                if(db.queryForObject("select count(*) from parent_telegram_subscriptions where telegram_user_id=? and parent_id<>?",Integer.class,user,p)>0) {
                    reply[0]="Бұл Telegram басқа ата-ананың аккаунтына байланыстырылған.\n\nЭтот Telegram уже связан с другим родителем.";return;
                }
                var row=db.queryForMap("select * from parent_telegram_subscriptions where parent_id=? for update",p);
                // Повторный /start не возобновляет остановленную подписку. Новая привязка включает её.
                boolean same=Objects.equals(row.get("telegram_user_id"),user) && Boolean.TRUE.equals(row.get("connected"));
                boolean enabled=same?Boolean.TRUE.equals(row.get("enabled")):true;
                if(!same) cancel(p);
                var next=next(clock.instant(),((Number)row.get("send_day")).intValue(),time(row.get("send_time")),ZoneId.of((String)row.get("zone_id")));
                db.update("update parent_telegram_subscriptions set telegram_user_id=?,chat_id=?,connected=true,enabled=?,generation=generation+?,next_run_at=? where parent_id=?",user,chat[0],enabled,same?0:1,next,p);
                db.update("delete from parent_telegram_tokens where parent_id=?",p);
                String date=next.atZoneSameInstant(ZoneId.of((String)row.get("zone_id"))).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                reply[0]=enabled?"Telegram қосылды. Барлық байланыстырылған балалар туралы келесі есеп: "+date+" (Қазақстан уақыты). Тоқтату: /stop\n\nTelegram подключён. Следующий отчёт по всем связанным детям: "+date+" (время Казахстана). Остановить: /stop"
                        :"Telegram қосылды. Есептерді жіберу уақытша тоқтатылған; оны Damulab жеке кабинетінде қосуға болады.\n\nTelegram подключён. Рассылка приостановлена; включить её можно в кабинете Damulab.";
            } else if(text.equals("/start")) reply[0]="Telegram-ды Damulab ата-ана кабинетіндегі батырма арқылы қосыңыз. /stop — есептерді тоқтату.\n\nПодключите Telegram через кнопку в кабинете родителя Damulab. /stop — остановить отчёты.";
        });
        if(reply[0]!=null) {try {telegram.send(chat[0],reply[0],null);} catch(TelegramClient.Failure ignored) {/* Привязка уже сохранена; повтор обновления безопасен. */}}
    }
    void cancel(long parent) {db.update("update parent_report_deliveries set status='CANCELLED' where parent_id=? and status='PENDING'",parent);}
    OffsetDateTime now() {
        // JDBC/БД округляют наносекунды. Без нормализации «отправить сейчас» может
        // оказаться чуть позже того же системного тика и не пройти проверку <= now.
        return OffsetDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }
    static LocalTime time(Object value) {return value instanceof LocalTime t?t:LocalTime.parse(value.toString());}
    static OffsetDateTime next(Instant instant,int day,LocalTime time,ZoneId zone) {
        var local=instant.atZone(zone);var candidate=local.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day))).atTime(time).atZone(zone);
        if(!candidate.toInstant().isAfter(instant)) candidate=candidate.plusWeeks(1);
        return candidate.toOffsetDateTime();
    }
    static String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
    }
}
