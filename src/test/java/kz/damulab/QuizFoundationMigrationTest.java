package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Выполняет все актуальные миграции с нуля и переход V46 → current на схеме с legacy-историей.
 * Схемы генерируются специально для теста и не совпадают с public/рабочими данными.
 * QUIZ_TEST_DB_* позволяет повторить сценарий на настоящем PostgreSQL без testcontainers.
 */
class QuizFoundationMigrationTest {
    @Test
    void stageTwoHistorySurvivesV52AndGameAuditIsAdditive() throws Exception {
        Database db = database(); db.flyway("51").migrate();
        try (Connection connection = db.connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into quiz_games(id, code, status) values (920001, 'upgrade-game', 'DRAFT')");
            sql.executeUpdate("insert into quiz_content_editorial_events(content_type, content_id, from_status, to_status) values ('FACT_STATEMENT', 920001, 'DRAFT', 'NEEDS_REVIEW')");
            List<List<Object>> events = rows(connection, "quiz_content_editorial_events", columns(connection, "quiz_content_editorial_events"));
            assertThat(db.flyway(null).migrate().migrationsExecuted).isEqualTo(4);
            assertThat(rows(connection, "quiz_content_editorial_events", columns(connection, "quiz_content_editorial_events"))).isEqualTo(events);
            try (ResultSet result = sql.executeQuery("select code, status, admin_revision from quiz_games where id = 920001")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("upgrade-game");
                assertThat(result.getString(2)).isEqualTo("DRAFT"); assertThat(result.getLong(3)).isZero();
            }
            sql.executeUpdate("insert into quiz_content_editorial_events(content_type, content_id, from_status, to_status) values ('GAME', 920001, 'DRAFT', 'NEEDS_REVIEW')");
            sql.executeUpdate("insert into quiz_game_visibility_events(game_id, catalog_visible) values (920001, false)");
        }
        assertThat(db.flyway(null).validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void emptyDatabaseMigratesAndSecondRunIsIdempotent() throws Exception {
        Database db = database();
        var flyway = db.flyway(null);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(55);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("55");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void existingV46RoomsRoundsAnswersAndResultsSurviveMigrationExactly() throws Exception {
        Database db = database();
        assertThat(db.flyway("46").migrate().migrationsExecuted).isEqualTo(46);
        try (Connection connection = db.connection()) {
            seedLegacy(connection);
            Map<String, List<String>> columns = new LinkedHashMap<>();
            Map<String, List<List<Object>>> before = new LinkedHashMap<>();
            for (String table : List.of("quiz_rooms", "quiz_participants", "quiz_rounds", "quiz_answers", "quiz_results", "questions", "question_versions")) {
                columns.put(table, columns(connection, table));
                before.put(table, rows(connection, table, columns.get(table)));
            }
            assertThat(db.flyway(null).migrate().migrationsExecuted).isEqualTo(9);
            for (String table : before.keySet()) assertThat(rows(connection, table, columns.get(table))).as(table + ": исходные поля").isEqualTo(before.get(table));
            try (Statement sql = connection.createStatement()) {
                try (ResultSet result = sql.executeQuery("select game_id, game_version_id, game_type, expires_at, settings_revision, command_revision from quiz_rooms order by id")) {
                    int count = 0;
                    while (result.next()) { for (int i = 1; i <= 4; i++) assertThat(result.getObject(i)).isNull(); assertThat(result.getLong(5)).isZero(); assertThat(result.getLong(6)).isZero(); count++; }
                    assertThat(count).isEqualTo(3);
                }
                try (ResultSet result = sql.executeQuery("select content_kind, question_version_id, prompt_snapshot, answer_key_snapshot_json from quiz_rounds order by id")) {
                    int count = 0;
                    while (result.next()) { assertThat(result.getString(1)).isEqualTo("QUESTION"); assertThat(result.getLong(2)).isEqualTo(900001); assertThat(result.getObject(3)).isNull(); assertThat(result.getObject(4)).isNull(); count++; }
                    assertThat(count).isEqualTo(3);
                }
                try (ResultSet result = sql.executeQuery("select response_ms, timed_out, skipped, answer_json from quiz_answers")) {
                    assertThat(result.next()).isTrue(); assertThat(result.getObject(1)).isNull(); assertThat(result.getBoolean(2)).isFalse(); assertThat(result.getBoolean(3)).isFalse(); assertThat(result.getString(4)).isEqualTo("{\"selectedOptionIds\":[\"a\"]}");
                }
            }
        }
        assertThat(db.flyway(null).migrate().migrationsExecuted).isZero();
        assertThat(db.flyway(null).validateWithResult().validationSuccessful).isTrue();
    }

    // Фикстура создаётся именно по старой V46: добавочные столбцы ещё отсутствуют.
    static void seedLegacy(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement()) {
            sql.executeUpdate("insert into app_users(id, email, password_hash, full_name) values (900001, 'migration@example.test', 'test-only', 'Ученик')");
            sql.executeUpdate("insert into student_profiles(id, user_id, grade_no, preferred_language) values (900001, 900001, 4, 'kk')");
            sql.executeUpdate("insert into questions(id, status) values (900001, 'PUBLISHED')");
            sql.executeUpdate("insert into question_versions(id, question_id, version_no, question_type, subject_id, difficulty, body_ru, body_kk, source, answer_key_json, published_at) select 900001, 900001, 1, 'SCQ', id, 1, 'Вопрос', 'Сұрақ', 'Учебник', '{\"correctOptionIds\":[\"a\"]}', current_timestamp from subjects where code = 'math'");
            sql.executeUpdate("update questions set current_version_id = 900001 where id = 900001");
            String[] states = {"WAITING", "ACTIVE", "FINISHED"};
            for (int i = 0; i < states.length; i++) {
                int id = 900001 + i;
                sql.executeUpdate("insert into quiz_rooms(id, code, host_student_profile_id, language, question_count, round_seconds, max_players, status) values (" + id + ", 'LEGACY" + i + "', 900001, 'kk', 1, 20, 4, '" + states[i] + "')");
                sql.executeUpdate("insert into quiz_participants(id, room_id, student_profile_id, display_name, ready) values (" + id + ", " + id + ", 900001, 'Ученик', true)");
                sql.executeUpdate("insert into quiz_rounds(id, room_id, question_version_id, order_no, points) values (" + id + ", " + id + ", 900001, 1, 1)");
            }
            sql.executeUpdate("insert into quiz_answers(id, round_id, participant_id, answer_json, is_correct, points_awarded) values (900003, 900003, 900003, '{\"selectedOptionIds\":[\"a\"]}', true, 1)");
            sql.executeUpdate("update quiz_rooms set started_at = current_timestamp where status in ('ACTIVE', 'FINISHED')");
            sql.executeUpdate("update quiz_rooms set finished_at = current_timestamp where status = 'FINISHED'");
            sql.executeUpdate("insert into quiz_results(id, room_id, participant_id, total_questions, correct_answers, score, max_score, percent) values (900003, 900003, 900003, 1, 1, 1, 1, 100)");
        }
    }

    private List<String> columns(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select * from " + table + " where 1 = 0")) {
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) names.add(result.getMetaData().getColumnName(i));
            return names;
        }
    }
    private List<List<Object>> rows(Connection connection, String table, List<String> columns) throws SQLException {
        // Таблицы/столбцы — закрытый список теста и metadata собственной схемы, не пользовательский SQL.
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select " + String.join(",", columns) + " from " + table + " order by id")) {
            List<List<Object>> rows = new ArrayList<>();
            while (result.next()) { List<Object> row = new ArrayList<>(); for (int i = 1; i <= columns.size(); i++) row.add(result.getObject(i)); rows.add(row); }
            return rows;
        }
    }
    private Database database() {
        String schema = "quiz_foundation_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv().getOrDefault("QUIZ_TEST_DB_URL", "jdbc:h2:mem:" + schema + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        return new Database(url, System.getenv().getOrDefault("QUIZ_TEST_DB_USER", "sa"), System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD", ""), schema);
    }
    private record Database(String url, String user, String password, String schema) {
        Flyway flyway(String target) {
            var config = Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema);
            if (target != null) config.target(target);
            return config.load();
        }
        Connection connection() throws SQLException {
            Connection connection = DriverManager.getConnection(url, user, password); connection.setSchema(schema); return connection;
        }
    }
}
