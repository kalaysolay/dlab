package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.quiz.engine.*;
import kz.damulab.users.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Проверяет V58→V60 на настоящем движке: 700 источников, тематику вариантов и сохранность истории. */
@SpringBootTest(properties={"damulab.quiz.timeout-worker-enabled=false"})
@ActiveProfiles("test")
@Import(ThematicEnglishVocabularyTest.TimeConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThematicEnglishVocabularyTest {
    private static final String SCHEMA="english_topics_"+UUID.randomUUID().toString().replace("-","");
    private static final String URL=System.getenv().getOrDefault("QUIZ_TEST_DB_URL","jdbc:h2:mem:"+SCHEMA+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
    private static final String USER=System.getenv().getOrDefault("QUIZ_TEST_DB_USER","sa");
    private static final String PASSWORD=System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD","");
    @Autowired QuizRoomService rooms;
    @Autowired QuizGameResultService results;
    @Autowired VocabularyRoundProvider provider;
    @Autowired VocabularyDistractorService distractors;
    @Autowired QuizGameRepository games;
    @Autowired VocabularySetRepository sets;
    @Autowired VocabularyItemVersionRepository versions;
    @Autowired VocabularyItemAnswerRepository answers;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired StudentProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestClock clock;
    private String student,finishedCode,activeCode,oldResult;
    private Long studentId;
    private List<Map<String,Object>> oldSnapshots,oldBanks;
    private List<Map<String,Object>> originalThematicVersions,originalThematicAnswers;

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) {
        // Сначала прежняя версия БД: до V59 успевают начаться и завершиться реальные игры.
        flyway(SCHEMA,"58").migrate();
        registry.add("spring.datasource.url",()->URL);
        registry.add("spring.datasource.username",()->USER);
        registry.add("spring.datasource.password",()->PASSWORD);
        registry.add("spring.datasource.driver-class-name",()->URL.startsWith("jdbc:postgresql:")?"org.postgresql.Driver":"org.h2.Driver");
        registry.add("spring.datasource.hikari.schema",()->SCHEMA);
        registry.add("spring.flyway.schemas",()->SCHEMA);
        registry.add("spring.flyway.default-schema",()->SCHEMA);
        registry.add("spring.flyway.target",()->"58");
        registry.add("spring.jpa.properties.hibernate.default_schema",()->SCHEMA);
    }

    @BeforeAll
    void upgradeWithRealHistory() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            student="thematic-"+UUID.randomUUID()+"@quiz.test";
            var user=new AppUser(student,"test-only",student,null);
            user.addRole(roles.findByCode(RoleCode.STUDENT).orElseThrow()); users.save(user);
            studentId=profiles.save(new StudentProfile(user,5,"ru")).getId();
        });
        var finished=rooms.create(student,new QuizGameRoomRequest.Create("basic-ru-en-300-v1",null,5,20,UUID.randomUUID().toString()));
        finishedCode=finished.code(); var started=rooms.start(finishedCode,student);
        // У комнат есть серверный отсчёт до старта; ответ отправляется уже внутри первого окна.
        long began=Duration.between(clock.base,started.startedAt().toInstant()).getSeconds();
        clock.offset=began+2;
        var current=rooms.state(finishedCode,student).currentRound();
        String key=jdbc.queryForObject("select answer_key_snapshot_json from quiz_rounds where id=?",String.class,current.id());
        var answer=mapper.createObjectNode(); answer.putArray("selected").add(mapper.readTree(key).asText());
        rooms.answer(finishedCode,student,new QuizGameRoomRequest.Answer(current.id(),answer,false));
        clock.offset=began+101; rooms.state(finishedCode,student);
        oldResult=mapper.writeValueAsString(results.read(finishedCode,studentId));
        var active=rooms.create(student,new QuizGameRoomRequest.Create("basic-en-ru-400-v1",null,5,20,UUID.randomUUID().toString()));
        activeCode=active.code(); var activeStarted=rooms.start(activeCode,student);
        clock.offset=Duration.between(clock.base,activeStarted.startedAt().toInstant()).getSeconds()+1;
        oldSnapshots=jdbc.queryForList("select r.* from quiz_rounds r join quiz_rooms q on q.id=r.room_id where q.code in (?,?) order by r.id",finishedCode,activeCode);
        oldBanks=jdbc.queryForList("select v.* from vocabulary_item_versions v join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'basic-%' order by v.id");
        assertThat(flyway(SCHEMA,"59").migrate().migrationsExecuted).isEqualTo(1);
        originalThematicVersions=jdbc.queryForList("select v.* from vocabulary_item_versions v join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'english-%-v2' order by v.id");
        originalThematicAnswers=jdbc.queryForList("select a.* from vocabulary_item_answers a join vocabulary_item_versions v on v.id=a.item_version_id join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'english-%-v2' order by a.id");
        assertThat(flyway(SCHEMA,"60").migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test @Transactional
    void allSevenHundredSourcesHaveFourOptionsFromTheirThemeAndNoSynonymKeys() throws Exception {
        int ruEn=0,enRu=0;
        List<String[]> content;
        try(var stream=getClass().getResourceAsStream("/quiz/basic-vocabulary/english-topics-v2.tsv")) {
            content=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines()
                    .skip(1).filter(l->!l.isBlank()).map(l->l.split("\t",-1)).toList();
        }
        assertThat(content).hasSize(400);
        for(var set:sets.findAllByOrderByIdAsc()) if(set.getCode().startsWith("english-")) {
            assertThat(set.getPublishedVersion().isCrossSetDistractorsAllowed()).isFalse();
            assertThat(versions.countEligible(set.getId(),Set.of())).isEqualTo(50);
            if(set.getSourceLanguage().equals("ru"))ruEn+=50; else enRu+=50;
            var game=games.findByCode(set.getCode()).orElseThrow();
            assertThat(game.isCatalogVisible()).isTrue();
            assertThat(game.getPublishedVersion().getAudiencePolicy()).isEqualTo(AudiencePolicy.UNIVERSAL);
            var room=new QuizRoom("THEME",null,null,null,"ru",null,10,20,4);
            room.attachGameVersion(game.getPublishedVersion(),OffsetDateTime.now().plusHours(1));
            var prepared=provider.prepare(room,Set.of());
            assertThat(prepared).hasSize(50);
            var dataset=distractors.prepareForGame(set);
            var acceptedTexts=new HashSet<String>();
            var expected=new HashMap<String,String>();
            for(var row:content) if(set.getCode().equals("english-"+row[0]+"-"+set.getSourceLanguage()+"-"+set.getTargetLanguage()+"-v2"))
                expected.put(set.getSourceLanguage().equals("en")?row[4]:row[2],set.getSourceLanguage().equals("en")?row[2]:row[1]);
            assertThat(expected).hasSize(50);
            for(var version:dataset.versions()) {
                assertThat(version.getItem().getSet().getId()).isEqualTo(set.getId());
                assertThat(version.getDifficulty()).isBetween(2,3);
                assertThat(version.getSourceReference()).startsWith("https://dictionary.cambridge.org/");
                // Проверяем не только число строк, но и точную пару после назначения БД новых ID.
                assertThat(expected).containsKey(version.getSourceText());
                assertThat(dataset.answers().get(version.getId())).filteredOn(VocabularyItemAnswer::isPrimary)
                        .extracting(VocabularyItemAnswer::getAnswerText).containsExactly(expected.get(version.getSourceText()));
                dataset.answers().get(version.getId()).stream().filter(VocabularyItemAnswer::isPrimary).forEach(a->acceptedTexts.add(a.getAnswerText()));
            }
            var normalizer=new QuizTextNormalizer();
            for(var source:prepared) {
                var round=source.builder().build(1,new Random(source.versionId()));
                var options=mapper.readTree(round.getOptionsSnapshotJson());
                assertThat(options.size()).isEqualTo(4);
                var ownAnswers=new HashSet<String>();
                dataset.answers().get(source.versionId()).forEach(a->ownAnswers.add(a.getAnswerNormalized()));
                int matchingKeys=0; var distinct=new HashSet<String>();
                for(var option:options) {
                    String text=option.path("text").asText();
                    assertThat(acceptedTexts).contains(text); assertThat(distinct.add(normalizer.normalize(text))).isTrue();
                    if(ownAnswers.contains(normalizer.normalize(text)))matchingKeys++;
                }
                assertThat(matchingKeys).isEqualTo(1);
                assertThat(round.getVocabularySetVersion().getSet().getId()).isEqualTo(set.getId());
            }
        }
        assertThat(ruEn).isEqualTo(300); assertThat(enRu).isEqualTo(400);
    }

    @Test @Transactional
    void fourteenGamesStartWithThirtyUniqueSnapshotsAndEditorialExclusionsWork() {
        int count=0;
        for(var game:games.findAll()) if(game.getCode().startsWith("english-")) {
            var room=rooms.create(student,new QuizGameRoomRequest.Create(game.getCode(),null,30,20,UUID.randomUUID().toString()));
            var started=rooms.start(room.code(),student);
            clock.offset=Duration.between(clock.base,started.startedAt().toInstant()).getSeconds()+1;
            assertThat(jdbc.queryForObject("select count(distinct r.vocabulary_item_version_id) from quiz_rounds r join quiz_rooms q on q.id=r.room_id where q.code=?",Long.class,room.code())).isEqualTo(30L);
            var current=rooms.state(room.code(),student).currentRound();
            assertThat(current.options().size()).isEqualTo(4);
            assertThat(current.options().toString()).doesNotContain("isCorrect","answerKey","explanation"); count++;
        }
        assertThat(count).isEqualTo(14);
        var pairs=jdbc.queryForList("select e.item_version_id,e.excluded_item_version_id from vocabulary_distractor_exclusions e join vocabulary_item_versions v on v.id=e.item_version_id join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'english-%'");
        assertThat(pairs).hasSize(14);
        for(var pair:pairs) {
            Long first=((Number)pair.get("item_version_id")).longValue(),second=((Number)pair.get("excluded_item_version_id")).longValue();
            assertThat(distractors.diagnose(first,Set.of()).candidates()).extracting(VocabularyDistractorService.Candidate::versionId).doesNotContain(second);
            assertThat(distractors.diagnose(second,Set.of()).candidates()).extracting(VocabularyDistractorService.Candidate::versionId).doesNotContain(first);
        }
    }

    @Test
    void oldSnapshotsResultsBanksAndKazakhGameAreUnchangedAndRepeatAddsNothing() throws Exception {
        assertThat(jdbc.queryForList("select r.* from quiz_rounds r join quiz_rooms q on q.id=r.room_id where q.code in (?,?) order by r.id",finishedCode,activeCode)).isEqualTo(oldSnapshots);
        assertThat(jdbc.queryForList("select v.* from vocabulary_item_versions v join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'basic-%' order by v.id")).isEqualTo(oldBanks);
        assertThat(mapper.writeValueAsString(results.read(finishedCode,studentId))).isEqualTo(oldResult);
        assertThat(rooms.state(activeCode,student).currentRound()).isNotNull();
        assertThat(jdbc.queryForObject("select catalog_visible from quiz_games where code='basic-ru-kk-300-v1'",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from quiz_games where code in ('basic-en-ru-400-v1','basic-ru-en-300-v1') and catalog_visible=false",Long.class)).isEqualTo(2L);
        assertThat(flyway(SCHEMA,"60").migrate().migrationsExecuted).isZero();
        assertThat(flyway(SCHEMA,"60").validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void clarificationKeepsAppliedChecksumAndOriginalVersionsAndSkipsEditorialWork() throws Exception {
        assertThat(jdbc.queryForObject("select checksum from flyway_schema_history where version='59'",Integer.class)).isEqualTo(-728678416);
        assertThat(jdbc.queryForList("select v.* from vocabulary_item_versions v join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'english-%-v2' and v.version_no=1 order by v.id")).isEqualTo(originalThematicVersions);
        assertThat(jdbc.queryForList("select a.* from vocabulary_item_answers a join vocabulary_item_versions v on v.id=a.item_version_id join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'english-%-v2' and v.version_no=1 order by a.id")).isEqualTo(originalThematicAnswers);
        assertThat(jdbc.queryForObject("select count(*) from vocabulary_item_versions v join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'english-%-v2' and v.version_no=2",Long.class)).isEqualTo(4L);
        // Отдельная схема воспроизводит администратора, начавшего редактуру до V60.
        String schema="english_editor_"+UUID.randomUUID().toString().replace("-","");
        flyway(schema,"59").migrate();
        try(var connection=DriverManager.getConnection(URL,USER,PASSWORD)) {
            connection.setSchema(schema);
            var sql=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            long item=sql.queryForObject("select i.id from vocabulary_items i join vocabulary_sets s on s.id=i.set_id join vocabulary_item_versions v on v.id=i.published_version_id where s.code='english-study-en-ru-v2' and v.source_text='approach (method)'",Long.class);
            sql.update("insert into vocabulary_item_versions(item_id,version_no,status,source_text,source_normalized,source_reference) values (?,2,'DRAFT','редакторский текст','редакторский текст','editor')",item);
            sql.update("update vocabulary_items set draft_version_id=(select id from vocabulary_item_versions where item_id=? and version_no=2) where id=?",item,item);
            sql.update("update vocabulary_items set status='ARCHIVED' where id in(select i.id from vocabulary_items i join vocabulary_sets s on s.id=i.set_id join vocabulary_item_versions v on v.id=i.published_version_id where s.code='english-communication-ru-en-v2' and v.source_text='чуткий к чувствам других')");
            var before=sql.queryForList("select v.* from vocabulary_item_versions v where v.item_id=? order by v.id",item);
            assertThat(flyway(schema,"60").migrate().migrationsExecuted).isEqualTo(1);
            assertThat(sql.queryForList("select v.* from vocabulary_item_versions v where v.item_id=? order by v.id",item)).isEqualTo(before);
            assertThat(sql.queryForObject("select count(*) from vocabulary_item_versions v join vocabulary_items i on i.id=v.item_id join vocabulary_sets s on s.id=i.set_id where s.code like 'english-%-v2' and v.version_no=2 and v.status='PUBLISHED'",Long.class)).isEqualTo(2L);
            assertThat(flyway(schema,"60").validateWithResult().validationSuccessful).isTrue();
        }
    }

    @Test
    void collisionAndPostgresInsertionFailureDoNotOverwriteOrLeavePartialPackage() throws Exception {
        String schema="english_collision_"+UUID.randomUUID().toString().replace("-","");
        flyway(schema,"58").migrate();
        try(var connection=DriverManager.getConnection(URL,USER,PASSWORD)) {
            connection.setSchema(schema);
            var sql=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            sql.update("insert into vocabulary_sets(code,source_language,target_language) values ('english-study-en-ru-v2','en','ru')");
            assertThatThrownBy(()->flyway(schema,"59").migrate()).hasStackTraceContaining("quiz_v59_guard");
            assertThat(sql.queryForObject("select count(*) from vocabulary_sets where code like 'english-%'",Long.class)).isEqualTo(1L);
            assertThat(sql.queryForObject("select count(*) from quiz_games where code like 'english-%'",Long.class)).isZero();
        }
        if(URL.startsWith("jdbc:postgresql:")) {
            String rollback="english_rollback_"+UUID.randomUUID().toString().replace("-","");
            flyway(rollback,"58").migrate();
            try(var connection=DriverManager.getConnection(URL,USER,PASSWORD)) {
                connection.setSchema(rollback);
                var sql=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                // Ошибка после записи банков/слов должна откатить всю SQL-поставку PostgreSQL.
                sql.execute("alter table quiz_game_versions add constraint test_v59_failure check(title_ru not like 'Английский: Учёба%')");
                assertThatThrownBy(()->flyway(rollback,"59").migrate()).hasStackTraceContaining("test_v59_failure");
                assertThat(sql.queryForObject("select count(*) from vocabulary_sets where code like 'english-%'",Long.class)).isZero();
                assertThat(sql.queryForObject("select count(*) from vocabulary_items",Long.class)).isEqualTo(1000L);
                assertThat(sql.queryForObject("select count(*) from quiz_games where code like 'basic-%' and catalog_visible=true",Long.class)).isEqualTo(3L);
            }
        }
    }

    private static Flyway flyway(String schema,String target) {
        return Flyway.configure().dataSource(URL,USER,PASSWORD).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target(target).load();
    }
    /** Управляемые часы позволяют завершить старую игру без ожидания и записать настоящие результаты. */
    static class TestClock extends Clock {
        final Instant base=Instant.now(); long offset;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return base.plusSeconds(offset);}
    }
    @TestConfiguration static class TimeConfig {
        /** Подменяет только время тестового контекста, не часы работающего приложения. */
        @Bean @Primary TestClock testClock(){return new TestClock();}
    }
}
