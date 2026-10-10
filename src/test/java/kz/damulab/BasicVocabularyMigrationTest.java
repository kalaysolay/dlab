package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.quiz.catalog.QuizTextNormalizer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Проверяет V53→V54 на собственной схеме H2/PostgreSQL: данные, повтор, коллизии и rollback. */
class BasicVocabularyMigrationTest {
    private static final String[] CODES = {"basic-ru-en-300-v1", "basic-en-ru-400-v1", "basic-ru-kk-300-v1"};

    @Test
    void upgradeLoadsExactDraftsAndSecondMigrationPreservesEditorialChanges() throws Exception {
        Database database = database();
        database.flyway("53").migrate();
        try (Connection connection = database.connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into vocabulary_sets(code, source_language, target_language) values ('existing-bank', 'en', 'ru')");
            assertThat(database.flyway("54").migrate().migrationsExecuted).isEqualTo(1);
            assertThat(count(connection, "vocabulary_sets")).isEqualTo(4);
            assertThat(count(connection, "vocabulary_items")).isEqualTo(1000);
            assertThat(count(connection, "quiz_games")).isZero();
            assertThat(count(connection, "quiz_content_editorial_events")).isZero();
            ObjectMapper mapper = new ObjectMapper();
            QuizTextNormalizer normalizer = new QuizTextNormalizer();
            int expectedAnswers = 0;
            for (String code : CODES) {
                JsonNode data;
                try (var stream = getClass().getResourceAsStream("/quiz/basic-vocabulary/" + code + ".json")) {
                    data = mapper.readTree(stream);
                }
                try (PreparedStatement setQuery = connection.prepareStatement("select s.*, v.title_ru, v.title_kk, v.source_reference, v.cross_set_distractors_allowed, v.reviewed_by, v.published_at from vocabulary_sets s join vocabulary_set_versions v on v.id = s.draft_version_id where s.code = ?")) {
                    setQuery.setString(1, code);
                    try (ResultSet set = setQuery.executeQuery()) {
                        assertThat(set.next()).isTrue();
                        assertThat(set.getString("source_language")).isEqualTo(data.path("sourceLanguage").asText());
                        assertThat(set.getString("target_language")).isEqualTo(data.path("targetLanguage").asText());
                        assertThat(set.getString("status")).isEqualTo("DRAFT");
                        assertThat(set.getString("title_ru")).isEqualTo(data.path("titleRu").asText());
                        assertThat(set.getString("title_kk")).isEqualTo(data.path("titleKk").asText());
                        assertThat(set.getString("source_reference")).isEmpty();
                        assertThat(set.getBoolean("cross_set_distractors_allowed")).isFalse();
                        assertThat(set.getObject("published_version_id")).isNull();
                        assertThat(set.getObject("created_by")).isNull();
                        assertThat(set.getObject("reviewed_by")).isNull();
                        assertThat(set.getObject("published_at")).isNull();
                    }
                }
                try (PreparedStatement wordsQuery = connection.prepareStatement("select v.* from vocabulary_items i join vocabulary_sets s on s.id = i.set_id join vocabulary_item_versions v on v.id = i.draft_version_id where s.code = ? order by i.id")) {
                    wordsQuery.setString(1, code);
                    try (ResultSet words = wordsQuery.executeQuery()) {
                        for (JsonNode item : data.path("items")) {
                            assertThat(words.next()).isTrue();
                            assertThat(words.getString("source_text")).isEqualTo(item.path("sourceText").asText());
                            assertThat(words.getString("source_normalized")).isEqualTo(normalizer.normalize(item.path("sourceText").asText()));
                            assertThat(words.getString("meaning_note_ru")).isEqualTo(item.path("meaningNoteRu").asText());
                            assertThat(words.getString("part_of_speech")).isEqualTo(item.path("partOfSpeech").asText());
                            assertThat(words.getInt("difficulty")).isEqualTo(item.path("difficulty").asInt());
                            assertThat(words.getString("status")).isEqualTo("DRAFT");
                            assertThat(words.getBoolean("universal_audience")).isTrue();
                            assertThat(words.getString("source_reference")).isEmpty();
                            assertThat(words.getObject("reviewed_by")).isNull();
                            assertThat(words.getObject("created_by")).isNull();
                            assertThat(words.getObject("published_at")).isNull();
                            List<String> expected = new ArrayList<>();
                            expected.add(item.path("primaryAnswer").asText());
                            item.path("alternativeAnswers").forEach(a -> expected.add(a.asText()));
                            expectedAnswers += expected.size();
                            try (PreparedStatement answersQuery = connection.prepareStatement("select * from vocabulary_item_answers where item_version_id = ? order by id")) {
                                answersQuery.setLong(1, words.getLong("id"));
                                try (ResultSet answers = answersQuery.executeQuery()) {
                                    for (int index = 0; index < expected.size(); index++) {
                                        assertThat(answers.next()).isTrue();
                                        assertThat(answers.getString("answer_text")).isEqualTo(expected.get(index));
                                        assertThat(answers.getString("answer_normalized")).isEqualTo(normalizer.normalize(expected.get(index)));
                                        assertThat(answers.getBoolean("is_primary")).isEqualTo(index == 0);
                                        assertThat(answers.getObject("primary_marker")).isEqualTo(index == 0 ? 1 : null);
                                    }
                                    assertThat(answers.next()).isFalse();
                                }
                            }
                        }
                        assertThat(words.next()).isFalse();
                    }
                }
            }
            assertThat(count(connection, "vocabulary_item_answers")).isEqualTo(expectedAnswers);
            assertThat(count(connection, "vocabulary_item_grades")).isZero();
            assertThat(count(connection, "vocabulary_items where status <> 'DRAFT' or published_version_id is not null")).isZero();
            sql.executeUpdate("update vocabulary_set_versions set title_ru = 'Редактор изменил название' where set_id = (select id from vocabulary_sets where code = 'basic-ru-en-300-v1')");
            assertThat(database.flyway("54").migrate().migrationsExecuted).isZero();
            assertThat(database.flyway("54").validateWithResult().validationSuccessful).isTrue();
            assertThat(count(connection, "vocabulary_set_versions where title_ru = 'Редактор изменил название'")).isEqualTo(1);
            assertThat(count(connection, "vocabulary_items")).isEqualTo(1000);
        }
    }

    @Test
    void existingCodeAbortsWithoutOverwritingOrCreatingPartialPackage() throws Exception {
        Database database = database(); database.flyway("53").migrate();
        try (Connection connection = database.connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into vocabulary_sets(code, source_language, target_language) values ('BASIC-RU-KK-300-V1', 'ru', 'kk')");
            assertThatThrownBy(() -> database.flyway("54").migrate()).hasStackTraceContaining("код словарного набора уже существует");
            assertThat(count(connection, "vocabulary_sets")).isEqualTo(1);
            assertThat(count(connection, "vocabulary_set_versions")).isZero();
            assertThat(count(connection, "vocabulary_items")).isZero();
        }
    }

    @Test
    void failureHalfwayThroughLoadingRollsBackEntireContentPackage() throws Exception {
        Database database = database(); database.flyway("53").migrate();
        try (Connection connection = database.connection(); Statement sql = connection.createStatement()) {
            // Ограничение только своей схемы заставляет реальный INSERT упасть после первых слов.
            sql.execute("alter table vocabulary_item_versions add constraint test_seed_failure check (source_text <> 'яблоко')");
            assertThatThrownBy(() -> database.flyway("54").migrate()).hasStackTraceContaining("test_seed_failure");
            for (String table : List.of("vocabulary_sets", "vocabulary_set_versions", "vocabulary_items", "vocabulary_item_versions", "vocabulary_item_answers")) {
                assertThat(count(connection, table)).as(table + ": пакет должен откатиться полностью").isZero();
            }
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        // SQL-фрагменты заданы самим тестом; внешние данные в запрос не подставляются.
        try (Statement sql = connection.createStatement(); ResultSet result = sql.executeQuery("select count(*) from " + table)) {
            result.next(); return result.getInt(1);
        }
    }
    private static Database database() {
        String schema = "basic_vocab_" + UUID.randomUUID().toString().replace("-", "");
        return new Database(System.getenv().getOrDefault("QUIZ_TEST_DB_URL", "jdbc:h2:mem:" + schema + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"),
                System.getenv().getOrDefault("QUIZ_TEST_DB_USER", "sa"), System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD", ""), schema);
    }
    private record Database(String url, String user, String password, String schema) {
        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target(target).load();
        }
        Connection connection() throws SQLException {
            Connection connection = DriverManager.getConnection(url, user, password); connection.setSchema(schema); return connection;
        }
    }
}
