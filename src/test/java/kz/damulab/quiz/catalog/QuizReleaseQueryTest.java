package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import kz.damulab.quiz.*;
import kz.damulab.users.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** QA-05: измеряет рост SQL смешанного каталога и реальные планы в синтетической PostgreSQL, без рабочей БД. */
@Transactional
class QuizReleaseQueryTest extends QuizGameTestSupport {
    @Autowired QuizStudentCatalog catalog;
    @Autowired StudentProfileRepository students;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;

    @Test void mixedCatalogDoesNotAddQueriesPerPublishedCard() {
        String email="query-"+UUID.randomUUID()+"@release.test";
        var user=new AppUser(email,"test-only","Проверка SQL",null);
        user.addRole(roles.findByCode(RoleCode.STUDENT).orElseThrow()); users.save(user);
        students.save(new StudentProfile(user,5,"ru"));
        var wordSet=wordSet(4); var factSet=factSet(1);
        var grade=grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==5).findFirst().orElseThrow();
        var subject=subjects.save(new kz.damulab.content.Subject(code(),"SQL предмет","SQL пән","Описание","Сипаттама"));
        subjectQuestion(subject,kz.damulab.questions.QuestionType.SCQ,2,List.of(grade.getId()));
        for(int i=0;i<3;i++) publish(i,wordSet.getId(),factSet.getId(),subject.getId(),grade.getId());
        var stats=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean previous=stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            entityManager.flush(); entityManager.clear(); stats.clear();
            int before=catalog.read(email,"ru").size(); long small=stats.getPrepareStatementCount();
            for(int i=0;i<12;i++) publish(i%3,wordSet.getId(),factSet.getId(),subject.getId(),grade.getId());
            entityManager.flush(); entityManager.clear(); stats.clear();
            assertThat(catalog.read(email,"kk")).hasSize(before+12);
            long large=stats.getPrepareStatementCount();
            assertThat(large).as("SQL не должен добавляться для каждой карточки/аудитории").isLessThanOrEqualTo(small+1);
            System.out.println("QA-05 catalog SQL: three="+small+", fifteen="+large);
        } finally { stats.setStatisticsEnabled(previous); }
    }

    @Test void mainPostgresLookupsHaveRecordedPlansOnNontrivialHistory() throws Exception {
        // H2 выполняет контракт выше; EXPLAIN PostgreSQL запускается только в его изолированном прогоне.
        String product=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)c->c.getMetaData().getDatabaseProductName());
        if(!product.equals("PostgreSQL")) return;
        String email="plan-"+UUID.randomUUID()+"@release.test";
        var user=users.save(new AppUser(email,"test-only","План SQL",null));
        var profile=students.save(new StudentProfile(user,5,"ru")); entityManager.flush();
        // Временная синтетическая история живёт только в test-транзакции; чужие строки не изменяются/удаляются.
        String prefix=UUID.randomUUID().toString().replace("-", "").substring(0,6).toUpperCase(Locale.ROOT);
        jdbc.update("insert into quiz_rooms(code,host_student_profile_id,language,question_count,round_seconds,max_players,status,created_at) select ?||lpad(n::text,6,'0'),?,'ru',1,20,4,'WAITING',current_timestamp from generate_series(1,5000) n",prefix,profile.getId());
        jdbc.execute("analyze quiz_rooms");
        StringBuilder report=new StringBuilder("PostgreSQL QA-05: 5000 собственных комнат\n");
        for(String query:List.of("select id from quiz_rooms where lower(code)=lower('"+prefix+"004999')", "select count(*) from quiz_rooms where lower(code)=lower('"+prefix+"999999')", "select id from quiz_rooms where status='WAITING' and expires_at<=current_timestamp order by expires_at,id limit 100")) {
            report.append(query).append('\n');
            var plan=new StringBuilder();
            jdbc.query("explain (analyze, buffers, format text) "+query,(org.springframework.jdbc.core.RowCallbackHandler)r->plan.append(r.getString(1)).append('\n'));
            report.append(plan);
            assertThat(plan.toString()).as(query).doesNotContain("Seq Scan on quiz_rooms");
        }
        Path path=Path.of(".run-logs/quiz-stage8-query-plans.txt"); Files.createDirectories(path.getParent()); Files.writeString(path,report);
    }
    private void publish(int type,Long word,Long fact,Long subject,Long grade) {
        var f=form(QuizGameType.values()[type]);
        if(f.getGameType()==QuizGameType.WORD_TRANSLATION){f.setVocabularySetId(word);f.setSourceLanguage("en");f.setTargetLanguage("ru");}
        if(f.getGameType()==QuizGameType.TRUE_FALSE)f.setFactSetId(fact);
        if(f.getGameType()==QuizGameType.SUBJECT_QUIZ){f.setSubjectId(subject);f.setDifficulty(2);f.setQuestionTypes(Set.of(kz.damulab.questions.QuestionType.SCQ));f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES);f.setGradeIds(List.of(grade));}
        publishGame(f);
    }
}
