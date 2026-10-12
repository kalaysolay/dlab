package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.sql.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.users.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Приёмка исходной SQL-поставки до тематической замены V59: игры, сохранение редакций и повтор. */
@SpringBootTest(properties={"damulab.quiz.strict-self-review=true","damulab.quiz.timeout-worker-enabled=false"})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicVocabularyPublicationTest {
    @Autowired VocabularyItemVersionRepository versions;
    @Autowired VocabularySetRepository sets;
    @Autowired QuizGameRepository games;
    @Autowired QuizRoomService rooms;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired StudentProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private static final List<String> CODES=List.of("basic-ru-en-300-v1","basic-en-ru-400-v1","basic-ru-kk-300-v1");
    private String student;

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) {
        String schema="vocab_publish_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv().getOrDefault("QUIZ_TEST_DB_URL","jdbc:h2:mem:"+schema+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        // Hikari задаёт текущую схему при первом соединении: она должна существовать до старта пула.
        Flyway.configure().dataSource(url,System.getenv().getOrDefault("QUIZ_TEST_DB_USER","sa"),System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD",""))
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("58").load().migrate();
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("QUIZ_TEST_DB_USER","sa"));
        registry.add("spring.datasource.password",()->System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD",""));
        registry.add("spring.datasource.driver-class-name",()->url.startsWith("jdbc:postgresql:")?"org.postgresql.Driver":"org.h2.Driver");
        registry.add("spring.datasource.hikari.schema",()->schema);
        registry.add("spring.flyway.schemas",()->schema);
        registry.add("spring.flyway.default-schema",()->schema);
        // Эта историческая приёмка проверяет V57/V58; новый пакет отдельно проверяется на V59.
        registry.add("spring.flyway.target",()->"58");
        registry.add("spring.jpa.properties.hibernate.default_schema",()->schema);
    }

    @BeforeAll
    void createStudent() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            String suffix=UUID.randomUUID()+"@vocab.test";
            student="student-"+suffix;
            var user=new AppUser(student,"test-only",student,null);
            user.addRole(roles.findByCode(RoleCode.STUDENT).orElseThrow()); users.save(user);
            profiles.save(new StudentProfile(user,5,"ru"));
        });
    }

    @Test @Transactional
    void allThousandWordsArePublishedUniversalAndSourcesAndEditorialHistoryExist() {
        var counts=Map.of("basic-ru-en-300-v1",300L,"basic-en-ru-400-v1",400L,"basic-ru-kk-300-v1",300L);
        for(var set:sets.findAllByOrderByIdAsc()) if(counts.containsKey(set.getCode())) {
            assertThat(set.getStatus()).isEqualTo(EditorialStatus.PUBLISHED);
            assertThat(versions.countEligible(set.getId(),Set.of())).isEqualTo(counts.get(set.getCode()));
            var game=games.findByCode(set.getCode()).orElseThrow();
            assertThat(game.isCatalogVisible()).isTrue();
            assertThat(game.getPublishedVersion().getAudiencePolicy()).isEqualTo(AudiencePolicy.UNIVERSAL);
            assertThat(game.getPublishedVersion().getGradeIds()).isEmpty();
            assertThat(game.getPublishedVersion().getMinRoundSeconds()).isEqualTo(5);
        }
        assertThat(jdbc.queryForObject("select count(*) from vocabulary_item_versions v join vocabulary_items i on i.published_version_id=v.id join vocabulary_sets s on i.set_id=s.id where s.code like 'basic-%' and v.reviewed_by is null and v.source_reference <> ''",Long.class)).isEqualTo(1000L);
        assertThat(jdbc.queryForObject("select count(*) from vocabulary_item_versions v join vocabulary_items i on v.item_id=i.id join vocabulary_sets s on i.set_id=s.id where s.code like 'basic-%' and v.version_no=1 and v.status='DRAFT'",Long.class)).isEqualTo(1000L);
        assertThat(jdbc.queryForObject("select answer_text from vocabulary_item_answers a join vocabulary_item_versions v on a.item_version_id=v.id join vocabulary_items i on i.published_version_id=v.id join vocabulary_sets s on i.set_id=s.id where s.code='basic-ru-kk-300-v1' and v.source_text='страница' and a.is_primary=true",String.class)).isEqualTo("бет");
        assertThat(jdbc.queryForObject("select count(*) from quiz_content_editorial_events where actor_id is null and to_status='PUBLISHED'",Long.class)).isEqualTo(1006L);
    }

    @Test @Transactional
    void everyNewGameCreatesAndStartsWithUniqueSnapshotsAndFourSafeOptions() {
        for(String code:CODES) {
            var room=rooms.create(student,new QuizGameRoomRequest.Create(code,null,5,5,UUID.randomUUID().toString()));
            assertThat(room.minRoundSeconds()).isEqualTo(5);
            rooms.start(room.code(),student);
            var state=rooms.state(room.code(),student);
            assertThat(state.currentRound()).isNotNull();
            assertThat(state.currentRound().options().size()).isEqualTo(4);
            assertThat(state.currentRound().options().toString()).doesNotContain("isCorrect", "correctOption", "explanation");
            assertThat(jdbc.queryForObject("select count(distinct r.vocabulary_item_version_id) from quiz_rounds r join quiz_rooms q on r.room_id=q.id where q.code=?",Long.class,room.code())).isEqualTo(5L);
            assertThat(jdbc.queryForObject("select count(*) from quiz_rounds r join quiz_rooms q on r.room_id=q.id where q.code=? and r.prompt_snapshot is not null and r.answer_key_snapshot_json is not null",Long.class,room.code())).isEqualTo(5L);
        }
    }

    @Test
    void migrationPreservesEditedBankAndOldGamesAndSecondRunDoesNothing() throws Exception {
        String schema="vocab_upgrade_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv().getOrDefault("QUIZ_TEST_DB_URL","jdbc:h2:mem:"+schema+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        String user=System.getenv().getOrDefault("QUIZ_TEST_DB_USER","sa"), password=System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD","");
        var before=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("56").load();
        before.migrate();
        try(var connection=DriverManager.getConnection(url,user,password)) {
            connection.setSchema(schema);
            var sql=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            sql.update("update vocabulary_set_versions set title_ru='Название редактора' where set_id=(select id from vocabulary_sets where code='basic-ru-en-300-v1')");
            sql.update("insert into quiz_games(code,status) values ('starter-en-numbers-v1','PUBLISHED')");
            sql.update("insert into quiz_game_versions(game_id,version_no,game_type,status,title_ru,title_kk) select id,1,'WORD_TRANSLATION','PUBLISHED','Старое название','Ескі атау' from quiz_games where code='starter-en-numbers-v1'");
            sql.update("update quiz_games set published_version_id=(select id from quiz_game_versions where game_id=quiz_games.id) where code='starter-en-numbers-v1'");
            var upgrade=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("58").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(sql.queryForObject("select count(*) from vocabulary_items where status='PUBLISHED'",Long.class)).isEqualTo(700L);
            assertThat(sql.queryForObject("select status from vocabulary_sets where code='basic-ru-en-300-v1'",String.class)).isEqualTo("DRAFT");
            assertThat(sql.queryForObject("select count(*) from vocabulary_set_versions where title_ru='Название редактора'",Long.class)).isEqualTo(1L);
            assertThat(sql.queryForObject("select count(*) from quiz_games where code like 'basic-%'",Long.class)).isEqualTo(2L);
            assertThat(sql.queryForObject("select title_ru from quiz_game_versions where game_id=(select id from quiz_games where code='starter-en-numbers-v1')",String.class)).isEqualTo("Старое название");
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        }
    }

    @Test
    void existingPublicationSurvivesAndPostgresRollsBackFailedDelivery() throws Exception {
        String schema="vocab_existing_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv().getOrDefault("QUIZ_TEST_DB_URL","jdbc:h2:mem:"+schema+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        String user=System.getenv().getOrDefault("QUIZ_TEST_DB_USER","sa"), password=System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD","");
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("56").load().migrate();
        try(var connection=DriverManager.getConnection(url,user,password)) {
            connection.setSchema(schema);
            var sql=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            // Имитируем уже опубликованный оператором банк: ни ключи, ни ID его версий менять нельзя.
            sql.update("update vocabulary_item_versions set status='PUBLISHED',source_reference='existing-source',reviewed_at=current_timestamp,published_at=current_timestamp where item_id in (select i.id from vocabulary_items i join vocabulary_sets s on s.id=i.set_id where s.code='basic-en-ru-400-v1')");
            sql.update("update vocabulary_items set status='PUBLISHED',published_version_id=draft_version_id,draft_version_id=null where set_id=(select id from vocabulary_sets where code='basic-en-ru-400-v1')");
            sql.update("update vocabulary_set_versions set status='PUBLISHED',source_reference='existing-source',reviewed_at=current_timestamp,published_at=current_timestamp where set_id=(select id from vocabulary_sets where code='basic-en-ru-400-v1')");
            sql.update("update vocabulary_sets set status='PUBLISHED',published_version_id=draft_version_id,draft_version_id=null where code='basic-en-ru-400-v1'");
            Long previousVersion=sql.queryForObject("select published_version_id from vocabulary_sets where code='basic-en-ru-400-v1'",Long.class);
            sql.update("insert into quiz_games(code,status) values ('starter-en-numbers-v1','PUBLISHED')");
            var upgrade=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("58").load();
            if(url.startsWith("jdbc:postgresql:")) {
                // PostgreSQL откатывает и SQL-данные, и служебные таблицы Flyway-транзакции.
                // H2 делает commit на DDL, поэтому это свойство проверяем именно на PostgreSQL.
                sql.execute("alter table quiz_game_versions add constraint test_v57_failure check(title_ru <> 'Словарный тренажёр: русский → английский')");
                assertThatThrownBy(upgrade::migrate).hasStackTraceContaining("test_v57_failure");
                assertThat(sql.queryForObject("select count(*) from vocabulary_items where status='DRAFT'",Long.class)).isEqualTo(600L);
                assertThat(sql.queryForObject("select count(*) from quiz_games where code like 'basic-%'",Long.class)).isZero();
                assertThat(sql.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name like 'quiz_v57_%'",Long.class,schema)).isZero();
                sql.execute("alter table quiz_game_versions drop constraint test_v57_failure");
            }
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(sql.queryForObject("select published_version_id from vocabulary_sets where code='basic-en-ru-400-v1'",Long.class)).isEqualTo(previousVersion);
            assertThat(sql.queryForObject("select count(*) from vocabulary_item_versions where source_reference='existing-source'",Long.class)).isEqualTo(400L);
            assertThat(sql.queryForObject("select count(*) from quiz_games where code like 'basic-%'",Long.class)).isEqualTo(3L);
            assertThat(sql.queryForObject("select catalog_visible from quiz_games where code='starter-en-numbers-v1'",Boolean.class)).isFalse();
        }
    }
}
