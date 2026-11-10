package kz.damulab.parentreports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

/** Реальные миграции, SQL, авторизация, шаблоны и очередь; заменён только внешний Telegram. */
@SpringBootTest(properties={"damulab.telegram.enabled=true","damulab.telegram.bot-token=test-token",
        "damulab.telegram.bot-username=damulab_test_bot","damulab.telegram.webhook-secret=test-secret",
        "damulab.telegram.initial-delay-ms=3600000"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class ParentTelegramIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired ParentTelegramService service;
    @Autowired ParentReportScheduler scheduler;
    @Autowired ParentReportBuilder builder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean TelegramClient telegram;
    String email, childEmail;
    long parent,student,chat;
    @BeforeEach void fixture() {
        email="report-"+UUID.randomUUID()+"@example.test";
        long user=createUser(email,"Родитель");
        db.update("insert into parent_profiles(user_id) values (?)",user);
        parent=db.queryForObject("select id from parent_profiles where user_id=?",Long.class,user);
        childEmail="child-"+UUID.randomUUID()+"@example.test";
        user=createUser(childEmail,"Айя");
        db.update("insert into student_profiles(user_id,preferred_language) values (?,'ru')",user);
        student=db.queryForObject("select id from student_profiles where user_id=?",Long.class,user);
        db.update("insert into parent_student_links(parent_profile_id,student_profile_id) values (?,?)",parent,student);
        chat=100000+parent;
        when(telegram.send(anyLong(),anyString(),nullable(String.class))).thenReturn(123L);
    }
    long createUser(String mail,String name) {
        db.update("insert into app_users(email,password_hash,full_name,enabled) values (?,'test',?,true)",mail,name);
        return db.queryForObject("select id from app_users where email=?",Long.class,mail);
    }
    void update(long id,String text) throws Exception {
        service.update(mapper.valueToTree(Map.of("update_id",id,"message",Map.of("text",text,"from",Map.of("id",chat),"chat",Map.of("id",chat,"type","private")))));
    }
    String token() {String url=service.connect(email,"ru");return url.substring(url.indexOf("start=")+6);}
    void connect() throws Exception {update(parent*100,"/start "+token());}
    long queued() throws Exception {
        connect();db.update("update parent_telegram_subscriptions set next_run_at=? where parent_id=?",service.now().minusMinutes(1),parent);
        scheduler.enqueue(parent);
        return db.queryForObject("select id from parent_report_deliveries where parent_id=?",Long.class,parent);
    }
    String deliveryStatus(long id) {return db.queryForObject("select status from parent_report_deliveries where id=?",String.class,id);}
    @Test void singleUseTokenAndStopAreIdempotent() throws Exception {
        String token=token();update(1000,"/start "+token);update(1000,"/stop");
        assertThat(service.state(email).get("enabled")).isEqualTo(true);
        update(1001,"/stop");update(1002,"/start "+token);
        verify(telegram).send(eq(chat),contains("Оны Damulab жеке кабинетінде қайта қосуға болады."),isNull());
        assertThat(service.state(email).get("enabled")).isEqualTo(false);
        assertThat(db.queryForObject("select count(*) from parent_telegram_tokens where parent_id=?",Integer.class,parent)).isZero();
    }
    @Test void expiredTokenAndOtherParentsCannotTakeOver() throws Exception {
        String token=token();db.update("update parent_telegram_tokens set expires_at=? where parent_id=?",service.now().minusMinutes(1),parent);
        update(2000,"/start "+token);assertThat(service.state(email).get("connected")).isEqualTo(false);
        connect();
        long other=createUser("other-"+UUID.randomUUID()+"@example.test","Другой родитель");
        db.update("insert into parent_profiles(user_id) values (?)",other);
        String otherEmail=db.queryForObject("select email from app_users where id=?",String.class,other);
        String url=service.connect(otherEmail,"ru");update(2002,"/start "+url.substring(url.indexOf("start=")+6));
        assertThat(service.state(otherEmail).get("connected")).isEqualTo(false);
    }
    @Test void enqueueDeduplicatesAndSendsEmptyWeek() throws Exception {
        long id=queued();db.update("update parent_telegram_subscriptions set next_run_at=? where parent_id=?",service.now().minusMinutes(1),parent);scheduler.enqueue(parent);
        assertThat(db.queryForObject("select count(*) from parent_report_deliveries where parent_id=?",Integer.class,parent)).isEqualTo(1);
        reset(telegram);when(telegram.send(anyLong(),anyString(),anyString())).thenReturn(321L);
        scheduler.deliver(id);scheduler.deliver(id);
        assertThat(deliveryStatus(id)).isEqualTo("SENT");verify(telegram,times(1)).send(eq(chat),contains("не зафиксировано"),endsWith("/parent/reports/"+id));
    }
    @Test void stopAndUnlinkPreventQueuedDelivery() throws Exception {
        long id=queued();update(3000,"/stop");reset(telegram);scheduler.deliver(id);
        assertThat(deliveryStatus(id)).isEqualTo("CANCELLED");verifyNoInteractions(telegram);
        service.settings(email,true,1,LocalTime.of(9,0));
        db.update("update parent_report_deliveries set status='PENDING',next_attempt_at=? where id=?",service.now().minusMinutes(1),id);
        db.update("delete from parent_student_links where parent_profile_id=? and student_profile_id=?",parent,student);
        scheduler.deliver(id);verifyNoInteractions(telegram);
    }
    @Test void blockedBotDisablesSubscriptionAndUnknownOutcomeDoesNotRetry() throws Exception {
        long id=queued();reset(telegram);
        when(telegram.send(anyLong(),anyString(),anyString())).thenThrow(new TelegramClient.Failure("unknown_outcome",0,false,false));
        scheduler.deliver(id);scheduler.deliver(id);assertThat(deliveryStatus(id)).isEqualTo("UNKNOWN");
        db.update("update parent_report_deliveries set status='PENDING',next_attempt_at=? where id=?",service.now().minusMinutes(1),id);
        doThrow(new TelegramClient.Failure("telegram_403",0,true,false)).when(telegram).send(anyLong(),anyString(),anyString());
        scheduler.deliver(id);assertThat(service.state(email).get("connected")).isEqualTo(false);
        assertThat(service.state(email).get("enabled")).isEqualTo(false);
    }
    @Test void rateLimitUsesRetryAfter() throws Exception {
        long id=queued();reset(telegram);
        when(telegram.send(anyLong(),anyString(),anyString())).thenThrow(new TelegramClient.Failure("telegram_429",900,false,true));
        scheduler.deliver(id);assertThat(deliveryStatus(id)).isEqualTo("PENDING");
        assertThat(db.queryForObject("select next_attempt_at from parent_report_deliveries where id=?",OffsetDateTime.class,id)).isAfter(service.now().plusMinutes(14));
    }
    @Test void webhookRequiresSecretAndSettingsRequireCsrf() throws Exception {
        mvc.perform(post("/api/telegram/webhook").contentType("application/json").content("{\"update_id\":4000}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token","test-secret").contentType("application/json").content("{\"update_id\":4000}")).andExpect(status().isOk());
        mvc.perform(post("/parent/telegram/connect").with(user(email).roles("PARENT"))).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post("/parent/telegram/connect").with(user(email).roles("STUDENT")).with(csrf())).andExpect(redirectedUrl("/access-denied"));
    }
    @Test void dashboardAndFullReportRenderAndEnforceOwnership() throws Exception {
        long id=queued();
        mvc.perform(get("/parent").with(user(email).roles("PARENT"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Отчёты в Telegram")));
        mvc.perform(get("/parent/profile").with(user(email).roles("PARENT"))).andExpect(status().isOk());
        mvc.perform(get("/parent/reports/"+id).with(user(email).roles("PARENT"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Айя")));
        long u=createUser("denied-"+UUID.randomUUID()+"@example.test","Другой");db.update("insert into parent_profiles(user_id) values (?)",u);
        String other=db.queryForObject("select email from app_users where id=?",String.class,u);
        mvc.perform(get("/parent/reports/"+id).with(user(other).roles("PARENT"))).andExpect(status().isNotFound());
    }
    @Test void allTestsInPeriodAreCountedAndFutureBoundaryExcluded() {
        var zone=ZoneId.of("Asia/Almaty");var week=LocalDate.of(2026,11,2);var start=week.atStartOfDay(zone).toOffsetDateTime();
        for(int i=0;i<12;i++) {
            var at=i==11?start.plusWeeks(1):start.plusHours(i);
            db.update("insert into test_sessions(student_profile_id,test_type,status,language,started_at,finished_at,time_limit_seconds,settings_json) values (?,'DIAGNOSTIC','FINISHED','ru',?,?,600,'{}')",student,at,at);
            long session=db.queryForObject("select max(id) from test_sessions where student_profile_id=?",Long.class,student);
            db.update("insert into test_results(session_id,total_questions,correct_answers,score,max_score,percent,created_at) values (?,5,4,4,5,80,?)",session,at);
        }
        var report=builder.build(student,week,zone,"ru");
        assertThat(report.fullText()).contains("Тесты: 11","44/55 (80%)");
        assertThat(report.messageText()).contains("Ещё в полном отчёте: 6");
        assertThat(report.messageText().length()).isLessThan(4096);
        var bilingual=builder.bilingual(student,week,zone);
        assertThat(bilingual.messageText()).contains("Русский","Қазақша","Тесты: 11","Тесттер: 11","Ещё в полном отчёте: 8","Толық есепте тағы: 8");
        assertThat(bilingual.messageText().length()).isLessThan(4096);
        assertThat(bilingual.messageText()).startsWith("Қазақша\n");
        assertThat(bilingual.fullText()).startsWith("Қазақша\n");
    }
    @Test void newLinkedChildIsIncludedWithoutChangingSubscription() throws Exception {
        connect();
        long user=createUser("new-child-"+UUID.randomUUID()+"@example.test","Второй ребёнок");
        db.update("insert into student_profiles(user_id,preferred_language) values (?,'ru')",user);
        long second=db.queryForObject("select id from student_profiles where user_id=?",Long.class,user);
        db.update("insert into parent_student_links(parent_profile_id,student_profile_id) values (?,?)",parent,second);
        db.update("update parent_telegram_subscriptions set next_run_at=? where parent_id=?",service.now().minusMinutes(1),parent);
        scheduler.enqueue(parent);
        assertThat(db.queryForList("select student_id from parent_report_deliveries where parent_id=?",Long.class,parent)).containsExactlyInAnyOrder(student,second);
    }
    @Test void lectureTitleIsIncludedAndUnfinishedLectureIsExcluded() {
        var week=LocalDate.of(2026,11,2);var zone=ZoneId.of("Asia/Almaty");var at=week.atStartOfDay(zone).toOffsetDateTime();
        db.update("insert into lectures(status) values ('PUBLISHED')");
        long lecture=db.queryForObject("select max(id) from lectures",Long.class);
        db.update("insert into lecture_versions(lecture_id,version_no,title_ru,control_mode) values (?,1,'Обыкновенные дроби','NONE')",lecture);
        long version=db.queryForObject("select id from lecture_versions where lecture_id=?",Long.class,lecture);
        db.update("update lectures set current_version_id=? where id=?",version,lecture);
        db.update("insert into student_lecture_progress(student_profile_id,lecture_id,status,started_at,completed_at) values (?,?,'DONE',?,?)",student,lecture,at,at.plusDays(1));
        var report=builder.build(student,week,zone,"ru");
        assertThat(report.messageText()).contains("Завершённые уроки: 1","Обыкновенные дроби").doesNotContain("Тесты:");
        db.update("update student_lecture_progress set status='IN_PROGRESS',completed_at=null where student_profile_id=?",student);
        assertThat(builder.build(student,week,zone,"ru").messageText()).contains("не зафиксировано");
    }
    @Test void crashedAttemptIsNotSentAgain() throws Exception {
        long id=queued();db.update("update parent_report_deliveries set status='SENDING' where id=?",id);
        reset(telegram);scheduler.deliver(id);verifyNoInteractions(telegram);
    }
    @Test void existingStudentOptOutCancelsQueuedReport() throws Exception {
        long id=queued();
        db.update("update student_profiles set weekly_parent_report_enabled=false where id=?",student);
        reset(telegram);scheduler.deliver(id);
        assertThat(deliveryStatus(id)).isEqualTo("CANCELLED");verifyNoInteractions(telegram);
    }
    @Test void emptyWeekDistinguishesNoVisitsFromVisitsWithoutResults() {
        var week=LocalDate.of(2026,11,2);var zone=ZoneId.of("Asia/Almaty");
        db.update("update parent_report_activity_tracking set started_at=? where id=1",week.minusDays(1).atStartOfDay(zone).toOffsetDateTime());
        var absent=builder.bilingual(student,week,zone).messageText();
        assertThat(absent).contains("не зафиксировано посещений","кіргені тіркелмеді","Здравствуйте!","Сәлеметсіз бе!","02.11.2026–08.11.2026","Айя");
        db.update("insert into student_visit_days values (?,?)",student,week.plusDays(1));
        var visited=builder.bilingual(student,week,zone).messageText();
        assertThat(visited).contains("заходил в Damulab, но","Damulab-қа кірген, бірақ").doesNotContain("не зафиксировано посещений");
    }
    @Test void historyBeforeTrackingDoesNotClaimNoVisits() {
        var report=builder.bilingual(student,LocalDate.of(2026,11,2),ZoneId.of("Asia/Almaty"));
        assertThat(report.messageText()).contains("Полных данных о посещениях","толық дерек жоқ").doesNotContain("не зафиксировано посещений");
    }
    @Test void studentPageRecordsOneVisitPerDay() throws Exception {
        mvc.perform(get("/student/profile").with(user(childEmail).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/student/profile").with(user(childEmail).roles("STUDENT"))).andExpect(status().isOk());
        assertThat(db.queryForObject("select count(*) from student_visit_days where student_id=?",Long.class,student)).isEqualTo(1);
    }
}
