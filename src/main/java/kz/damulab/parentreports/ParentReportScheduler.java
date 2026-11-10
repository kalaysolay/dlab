package kz.damulab.parentreports;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Очередь в PostgreSQL: сроки переживают рестарт, блокировки защищают от параллельной отправки. */
@Component
public class ParentReportScheduler {
    private static final Logger log=LoggerFactory.getLogger(ParentReportScheduler.class);
    private final ParentTelegramService service;
    private final ParentReportBuilder builder;
    public ParentReportScheduler(ParentTelegramService service,ParentReportBuilder builder) {this.service=service;this.builder=builder;}

    /** Ограниченный пакет: не держим одну длинную транзакцию на всех родителей. */
    @Scheduled(scheduler="parentReportTaskScheduler",fixedDelayString="${damulab.telegram.poll-delay-ms:10000}",initialDelayString="${damulab.telegram.initial-delay-ms:10000}")
    public void tick() {
        if(!service.config.available()) return;
        var parents=service.db.queryForList("select parent_id from parent_telegram_subscriptions where connected=true and enabled=true and next_run_at<=? order by next_run_at limit 100",Long.class,service.now());
        for(long parent:parents) {
            try {enqueue(parent);} catch(Exception ex) {log.warn("Не удалось подготовить отчёт родителя {} ({})",parent,ex.getClass().getSimpleName());}
        }
        var ids=service.db.queryForList("select id from parent_report_deliveries where status='PENDING' and next_attempt_at<=? order by next_attempt_at,id limit 25",Long.class,service.now());
        // Не более одного запроса в секунду: достаточно для начального объёма, без платных рассылок.
        for(long id:ids) {
            try {deliver(id);Thread.sleep(1050);} catch(InterruptedException ex) {Thread.currentThread().interrupt();return;}
            catch(Exception ex) {log.warn("Не удалось обработать отправку {} ({})",id,ex.getClass().getSimpleName());}
        }
    }
    /** После простоя создаёт только последний завершённый период, без пачки старых недель. */
    public void enqueue(long parent) {
        service.tx.executeWithoutResult(status->{
            var row=service.db.queryForMap("select * from parent_telegram_subscriptions where parent_id=? for update",parent);
            if(!Boolean.TRUE.equals(row.get("connected")) || !Boolean.TRUE.equals(row.get("enabled"))) return;
            var due=service.db.queryForObject("select next_run_at from parent_telegram_subscriptions where parent_id=?",OffsetDateTime.class,parent);
            if(due==null || due.isAfter(service.now())) return;
            ZoneId zone=ZoneId.of((String)row.get("zone_id"));
            LocalDate week=service.clock.instant().atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
            // Существующий переключатель в профиле ученика уже обещает отключение недельных
            // отчётов родителям. Не обходим его новой доставкой в Telegram.
            var children=service.db.queryForList("select l.id,l.student_profile_id from parent_student_links l join student_profiles s on s.id=l.student_profile_id join app_users u on u.id=s.user_id where l.parent_profile_id=? and u.enabled=true and s.weekly_parent_report_enabled=true",parent);
            for(var child:children) {
                long student=((Number)child.get("student_profile_id")).longValue();
                if(service.db.queryForObject("select count(*) from parent_report_deliveries where parent_id=? and student_id=? and week_start=?",Integer.class,parent,student,week)>0) continue;
                var report=builder.bilingual(student,week,zone);
                service.db.update("insert into parent_report_deliveries(parent_id,student_id,link_id,week_start,generation,full_text,message_text,next_attempt_at) values (?,?,?,?,?,?,?,?)",
                        parent,student,child.get("id"),week,row.get("generation"),report.fullText(),report.messageText(),service.now());
            }
            service.db.update("update parent_telegram_subscriptions set next_run_at=? where parent_id=?",
                    ParentTelegramService.next(service.clock.instant(),((Number)row.get("send_day")).intValue(),ParentTelegramService.time(row.get("send_time")),zone),parent);
        });
    }
    /** Подписка и связь проверяются непосредственно перед HTTP-вызовом, не только при постановке. */
    public void deliver(long id) {
        // Фиксируем начало попытки отдельно: если процесс упадёт после приёма сообщения Telegram,
        // статус SENDING не будет автоматически повторён после рестарта (исход неизвестен).
        Boolean claimed=service.tx.execute(status->service.db.update("update parent_report_deliveries set status='SENDING' where id=? and status='PENDING' and next_attempt_at<=?",id,service.now())==1);
        if(!Boolean.TRUE.equals(claimed)) return;
        service.tx.executeWithoutResult(status->{
            // Порядок блокировок общий с настройками: сначала подписка, затем её отправка.
            var parents=service.db.queryForList("select parent_id from parent_report_deliveries where id=?",Long.class,id);
            if(parents.isEmpty()) return;
            long parent=parents.getFirst();
            var row=service.db.queryForMap("select * from parent_telegram_subscriptions where parent_id=? for update",parent);
            var deliveries=service.db.queryForList("select * from parent_report_deliveries where id=? for update",id);
            if(deliveries.isEmpty()) return;
            var delivery=deliveries.getFirst();
            if(!"SENDING".equals(delivery.get("status"))) return;
            var due=service.db.queryForObject("select next_attempt_at from parent_report_deliveries where id=?",OffsetDateTime.class,id);
            if(due.isAfter(service.now())) return;
            // Сохраняем исходный link_id: отвязка и повторная привязка не оживляют старую очередь.
            boolean linked=!service.db.queryForList("select l.id from parent_student_links l join parent_profiles p on p.id=l.parent_profile_id join app_users pu on pu.id=p.user_id join student_profiles s on s.id=l.student_profile_id join app_users su on su.id=s.user_id where l.id=? and l.parent_profile_id=? and l.student_profile_id=? and pu.enabled=true and su.enabled=true and s.weekly_parent_report_enabled=true for update",Long.class,delivery.get("link_id"),parent,delivery.get("student_id")).isEmpty();
            LocalDate week=service.db.queryForObject("select week_start from parent_report_deliveries where id=?",LocalDate.class,id);
            boolean expired=week.plusWeeks(2).atStartOfDay(ZoneId.of((String)row.get("zone_id"))).toInstant().compareTo(service.clock.instant())<=0;
            if(!linked || expired || !Boolean.TRUE.equals(row.get("enabled")) || !Boolean.TRUE.equals(row.get("connected")) || !row.get("generation").equals(delivery.get("generation"))) {
                service.db.update("update parent_report_deliveries set status='CANCELLED' where id=?",id);return;
            }
            int attempts=((Number)delivery.get("attempts")).intValue()+1;
            try {
                // Короткий сетевой таймаут ограничивает блокировку подписки. Это позволяет /stop
                // дождаться текущей отправки и гарантированно отменить последующие.
                long message=service.telegram.send(((Number)row.get("chat_id")).longValue(),(String)delivery.get("message_text"),
                        service.config.publicBaseUrl().replaceAll("/+$","")+"/parent/reports/"+id);
                service.db.update("update parent_report_deliveries set status='SENT',attempts=?,message_id=?,error_code=null where id=?",attempts,message,id);
            } catch(TelegramClient.Failure ex) {
                if(ex.blocked) {
                    service.db.update("update parent_telegram_subscriptions set connected=false,enabled=false where parent_id=?",parent);
                    service.cancel(parent);
                }
                String state=ex.retry && attempts<5?"PENDING":("unknown_outcome".equals(ex.code)?"UNKNOWN":"FAILED");
                service.db.update("update parent_report_deliveries set status=?,attempts=?,error_code=?,next_attempt_at=? where id=?",state,attempts,ex.code,
                        service.now().plusSeconds(Math.max(ex.retrySeconds,60L*(1L<<Math.min(attempts,6)))),id);
            }
        });
    }
}
