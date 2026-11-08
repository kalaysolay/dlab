package kz.damulab;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.quiz.engine.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Проверяет ручную разрушительную поставку только в случайной схеме тестового PostgreSQL.
 * Отдельная переменная окружения исключает случайное использование настроек рабочей БД.
 * Проверяется реальный движок, история/FK и откат всей замены при ошибках, а не только текст SQL.
 */
@SpringBootTest(properties = {"damulab.quiz.timeout-worker-enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="ENGLISH_V3_TEST_DB_URL", matches="jdbc:postgresql:.*")
class EnglishVocabularyV3PackageTest {
    private static final String SCHEMA="english_v3_"+UUID.randomUUID().toString().replace("-", "");
    private static final Path PACKAGE=Path.of("scripts", "english-v3");
    @Autowired DataSource datasource;
    @Autowired QuizGameRepository games;
    @Autowired VocabularyRoundProvider provider;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) {
        String url=System.getenv("ENGLISH_V3_TEST_DB_URL");
        String user=System.getenv().getOrDefault("ENGLISH_V3_TEST_DB_USER", "postgres");
        String password=System.getenv().getOrDefault("ENGLISH_V3_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url,user,password).schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
        registry.add("spring.datasource.url", ()->url);
        registry.add("spring.datasource.username", ()->user);
        registry.add("spring.datasource.password", ()->password);
        registry.add("spring.datasource.driver-class-name", ()->"org.postgresql.Driver");
        registry.add("spring.datasource.hikari.schema", ()->SCHEMA);
        registry.add("spring.flyway.schemas", ()->SCHEMA);
        registry.add("spring.flyway.default-schema", ()->SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", ()->SCHEMA);
    }

    @Test
    void atomicReplacementPreservesOtherContentAndEveryWordHasClosePlayableOptions() throws Exception {
        try (Connection c=datasource.getConnection()) {
            c.setAutoCommit(false);
            // Настоящая legacy-история не связана со старым английским банком и обязана выжить.
            QuizFoundationMigrationTest.seedLegacy(c);
            seedEnglishHistory(c);
            c.commit();
            Map<String,List<List<Object>>> untouched=snapshot(c);
            long old=count(c,"select count(*) from vocabulary_items");

            exec(c,"update quiz_rooms set status='ACTIVE' where id=930001");
            Savepoint active=c.setSavepoint();
            assertThatThrownBy(()->run(c,"01_delete.sql")).hasStackTraceContaining("Завершите ACTIVE");
            c.rollback(active);
            assertThat(count(c,"select count(*) from vocabulary_items")).isEqualTo(old);
            exec(c,"update quiz_rooms set status='FINISHED' where id=930001");

            // Чужая игра ссылается на удаляемый банк: поставка должна отказать до удаления.
            Savepoint shared=c.setSavepoint();
            exec(c,"insert into quiz_games(id,code,status) values(930001,'qa-shared-bank','DRAFT')");
            exec(c,"insert into quiz_game_versions(id,game_id,version_no,game_type,title_ru,title_kk) values(930001,930001,1,'WORD_TRANSLATION','Тест','Тест')");
            exec(c,"insert into quiz_word_game_configs(game_version_id,vocabulary_set_id,source_language,target_language) select 930001,id,'en','ru' from vocabulary_sets where code='english-study-en-ru-v2'");
            assertThatThrownBy(()->run(c,"01_delete.sql")).hasStackTraceContaining("Сторонняя игра");
            c.rollback(shared);

            // Ошибка загрузки происходит после DELETE. Откат обязан вернуть и банки, и историю.
            Savepoint collision=c.setSavepoint();
            exec(c,"insert into vocabulary_sets(code,source_language,target_language) values('english-study-en-ru-v3','en','ru')");
            run(c,"01_delete.sql");
            assertThatThrownBy(()->run(c,"02_load.sql")).hasStackTraceContaining("Новые коды v3 уже заняты");
            c.rollback(collision);
            assertThat(count(c,"select count(*) from vocabulary_items")).isEqualTo(old);
            assertThat(count(c,"select count(*) from quiz_answers where id=930001")).isEqualTo(1);

            run(c,"01_delete.sql"); run(c,"02_load.sql"); run(c,"03_verify.sql"); c.commit();
            assertThat(snapshot(c)).isEqualTo(untouched);
            for (String table:List.of("quiz_rooms","quiz_rounds","quiz_participants","quiz_answers","quiz_results")) {
                assertThat(count(c,"select count(*) from "+table+" where id=930001")).as(table).isZero();
            }
            assertThat(count(c,"select count(*) from quiz_room_creation_keys where room_id=930001")).isZero();
            assertThat(count(c,"select count(*) from quiz_room_audience_grades where room_id=930001")).isZero();
            assertThat(count(c,"select count(*) from vocabulary_sets where code='english-study-en-ru-v2'")).isZero();
            assertThat(count(c,"select count(*) from vocabulary_sets where code='english-study-ru-en-v2'")).isEqualTo(1);

            // Проверка способна поймать даже одиночный потерянный запрет между разными группами.
            Savepoint broken=c.setSavepoint();
            exec(c,"delete from vocabulary_distractor_exclusions where id=(select min(x.id) from vocabulary_distractor_exclusions x join vocabulary_item_versions v on v.id=x.item_version_id join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code='english-study-en-ru-v3')");
            assertThatThrownBy(()->run(c,"03_verify.sql")).hasStackTraceContaining("Точная матрица запретов");
            c.rollback(broken); c.commit();
        }

        verifyRealProvider();

        try (Connection c=datasource.getConnection()) {
            c.setAutoCommit(false);
            // Отдельная проверка после COMMIT и второй DELETE не зависят от временных таблиц загрузки.
            run(c,"03_verify.sql"); c.commit();
            run(c,"01_delete.sql"); c.commit();
            assertThatThrownBy(()->run(c,"02_load.sql")).hasStackTraceContaining("Новые коды v3 уже заняты");
            c.rollback();
            assertThat(count(c,"select count(*) from vocabulary_sets where code like 'english-%-en-ru-v3'")).isEqualTo(10);
            List<List<Object>> kazakh=rows(c,"select * from vocabulary_sets where source_language='kk' or target_language='kk' order by id");
            run(c,"01_delete_all_english.sql"); c.commit();
            assertThat(count(c,"select count(*) from vocabulary_sets where code like 'english-%-v2' or code in('basic-en-ru-400-v1','basic-ru-en-300-v1')")).isZero();
            assertThat(rows(c,"select * from vocabulary_sets where source_language='kk' or target_language='kk' order by id")).isEqualTo(kazakh);
            run(c,"03_verify.sql"); c.commit();
        }
    }

    /** Все 500 источников прогоняются через действующий провайдер с несколькими перемешиваниями. */
    private void verifyRealProvider() throws Exception {
        Map<String,String[]> expected=new HashMap<>();
        Map<String,Set<String>> groupAnswers=new HashMap<>();
        for (String line:Files.readAllLines(PACKAGE.resolve("questions.tsv")).subList(1,501)) {
            String[] fields=line.split("\t"); expected.put(fields[4],fields);
            groupAnswers.computeIfAbsent(fields[3],k->new HashSet<>()).add(fields[5+"ABCD".indexOf(fields[9])]);
        }
        int[] checked={0}; Set<String> keyPositions=new HashSet<>();
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            for (var game:games.findAll()) {
                if (!game.getCode().endsWith("-en-ru-v3")) continue;
                var room=new QuizRoom("V3TEST",null,null,null,"ru",null,10,20,4);
                room.attachGameVersion(game.getPublishedVersion(),OffsetDateTime.now().plusMinutes(30));
                var sources=provider.prepare(room,Set.of());
                assertThat(sources).hasSize(50);
                assertThat(provider.countUpTo(room,Set.of(),30)).isEqualTo(30);
                for (var source:sources) {
                    for (int seed=0;seed<4;seed++) {
                        var round=source.builder().build(1,new Random(source.versionId()*17+seed));
                        String[] row=expected.get(round.getPromptSnapshot());
                        assertThat(row).isNotNull();
                        try {
                            JsonNode options=mapper.readTree(round.getOptionsSnapshotJson());
                            String key=mapper.readTree(round.getAnswerKeySnapshotJson()).asText();
                            keyPositions.add(key);
                            List<String> texts=new ArrayList<>(); String correct=null;
                            for (var option:options) {
                                texts.add(option.get("text").asText());
                                if (option.get("id").asText().equals(key)) correct=option.get("text").asText();
                            }
                            assertThat(texts).hasSize(4).doesNotHaveDuplicates();
                            assertThat(groupAnswers.get(row[3])).containsAll(texts);
                            assertThat(correct).isEqualTo(row[5+"ABCD".indexOf(row[9])]);
                        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                    }
                    checked[0]++;
                }
            }
        });
        assertThat(checked[0]).isEqualTo(500);
        assertThat(keyPositions).containsExactlyInAnyOrder("A","B","C","D");
    }

    /** Фикстура содержит все связи, которые ручной скрипт обязан удалить до удаления банков. */
    private static void seedEnglishHistory(Connection c) throws Exception {
        exec(c,"insert into quiz_rooms(id,code,host_student_profile_id,language,question_count,round_seconds,max_players,status,room_kind,game_id,game_version_id,game_type) select 930001,'V3OLD',900001,'ru',1,20,4,'FINISHED','CATALOG_GAME',g.id,g.published_version_id,'WORD_TRANSLATION' from quiz_games g where g.code='english-study-en-ru-v2'");
        exec(c,"insert into quiz_participants(id,room_id,student_profile_id,display_name) values(930001,930001,900001,'Тест')");
        exec(c,"insert into quiz_rounds(id,room_id,order_no,content_kind,vocabulary_item_version_id,vocabulary_set_version_id,prompt_snapshot,options_snapshot_json,answer_key_snapshot_json) select 930001,930001,1,'VOCABULARY',min(i.published_version_id),s.published_version_id,'Тест','[]','\"A\"' from vocabulary_sets s join vocabulary_items i on i.set_id=s.id where s.code='english-study-en-ru-v2' group by s.published_version_id");
        exec(c,"insert into quiz_answers(id,round_id,participant_id,answer_json,is_correct,points_awarded) values(930001,930001,930001,'{}',true,1)");
        exec(c,"insert into quiz_results(id,room_id,participant_id,total_questions,correct_answers,score,max_score,percent) values(930001,930001,930001,1,1,1,1,100)");
        exec(c,"insert into quiz_room_creation_keys(student_profile_id,key_hash,payload_hash,room_id) values(900001,'v3-key','v3-payload',930001)");
        exec(c,"insert into quiz_room_audience_grades(room_id,grade_no) values(930001,4)");
        exec(c,"insert into quiz_game_version_grades(game_version_id,grade_id) select g.published_version_id,r.id from quiz_games g cross join grades r where g.code='english-study-en-ru-v2' and r.grade_no=4");
        exec(c,"insert into vocabulary_item_grades(item_version_id,grade_id) select v.vocabulary_item_version_id,r.id from quiz_rounds v cross join grades r where v.id=930001 and r.grade_no=4");
        exec(c,"insert into vocabulary_distractor_exclusions(item_version_id,excluded_item_version_id,reason) select r.vocabulary_item_version_id,min(i.published_version_id),'Межбанковская тестовая связь' from quiz_rounds r cross join vocabulary_sets s join vocabulary_items i on i.set_id=s.id where r.id=930001 and s.code='basic-ru-kk-300-v1' group by r.vocabulary_item_version_id");
    }

    private static void run(Connection c,String filename) {
        ScriptUtils.executeSqlScript(c,new EncodedResource(new FileSystemResource(PACKAGE.resolve(filename)),StandardCharsets.UTF_8));
    }
    private static void exec(Connection c,String sql) throws SQLException { try(var s=c.createStatement()) { s.execute(sql); } }
    private static long count(Connection c,String sql) throws SQLException { try(var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getLong(1); } }
    private static List<List<Object>> rows(Connection c,String sql) throws SQLException {
        List<List<Object>> rows=new ArrayList<>();
        try(var s=c.createStatement(); var r=s.executeQuery(sql)) {
            while(r.next()) { List<Object> row=new ArrayList<>(); for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.add(r.getObject(i)); rows.add(row); }
        }
        return rows;
    }
    private static Map<String,List<List<Object>>> snapshot(Connection c) throws SQLException {
        Map<String,List<List<Object>>> result=new LinkedHashMap<>();
        String preserved="select id from vocabulary_sets where source_language='kk' or target_language='kk' or source_language='ru' and target_language='en'";
        result.put("sets",rows(c,"select * from vocabulary_sets where id in("+preserved+") order by id"));
        result.put("items",rows(c,"select * from vocabulary_items where set_id in("+preserved+") order by id"));
        result.put("versions",rows(c,"select v.* from vocabulary_item_versions v join vocabulary_items i on i.id=v.item_id where i.set_id in("+preserved+") order by v.id"));
        result.put("answers",rows(c,"select a.* from vocabulary_item_answers a join vocabulary_item_versions v on v.id=a.item_version_id join vocabulary_items i on i.id=v.item_id where i.set_id in("+preserved+") order by a.id"));
        for(String table:List.of("quiz_rooms","quiz_rounds","quiz_participants","quiz_answers","quiz_results"))
            result.put(table,rows(c,"select * from "+table+" where id<>930001 order by id"));
        return result;
    }
}
