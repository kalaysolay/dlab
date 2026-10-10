package db.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.zip.CRC32;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.quiz.catalog.QuizTextNormalizer;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Однократно загружает три базовых словаря как черновики без публикации и фиктивного аудита.
 * JDBC выдаёт настоящие ID на PostgreSQL и H2; версии JSON входят в checksum миграции.
 */
public class V54__basic_vocabulary_drafts extends BaseJavaMigration {
    private static final String[] FILES = {
        "basic-ru-en-300-v1", "basic-en-ru-400-v1", "basic-ru-kk-300-v1"
    };

    /** Защищает версии исходных данных от изменения после применения V54. */
    @Override
    public Integer getChecksum() {
        CRC32 checksum = new CRC32();
        // При изменении кода уже применённой миграции нужна новая миграция, а не правка V54.
        // Различие переводов строк Windows/Linux не должно изменять checksum одного пакета.
        for (String file : FILES) checksum.update(resource(file).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
        return (int) checksum.getValue();
    }

    /** Создаёт полный пакет в транзакции Flyway; соединение и commit принадлежат Flyway. */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode[] packages = new JsonNode[FILES.length];
        for (int i = 0; i < FILES.length; i++) {
            packages[i] = mapper.readTree(resource(FILES[i]));
            try (PreparedStatement check = connection.prepareStatement("select id from vocabulary_sets where lower(code) = lower(?)")) {
                check.setString(1, packages[i].path("code").asText());
                try (ResultSet result = check.executeQuery()) {
                    if (result.next()) throw new SQLException("V54: код словарного набора уже существует: " + FILES[i]);
                }
            }
        }
        // RU→EN добавляется к прежним направлениям; самоограничение на одинаковые языки остаётся.
        try (Statement sql = connection.createStatement()) {
            sql.execute("alter table vocabulary_sets drop constraint ck_vocabulary_set_direction");
            sql.execute("alter table vocabulary_sets add constraint ck_vocabulary_set_direction check "
                    + "((source_language in ('en', 'kk') and target_language = 'ru') "
                    + "or (source_language = 'ru' and target_language in ('en', 'kk')))");
        }
        QuizTextNormalizer normalizer = new QuizTextNormalizer();
        for (JsonNode data : packages) {
            long setId = insert(connection, "insert into vocabulary_sets(code, source_language, target_language, status) values (?, ?, ?, 'DRAFT')",
                    data.path("code").asText(), data.path("sourceLanguage").asText(), data.path("targetLanguage").asText());
            long setVersionId = insert(connection, "insert into vocabulary_set_versions(set_id, version_no, status, title_ru, title_kk, source_reference, cross_set_distractors_allowed) values (?, 1, 'DRAFT', ?, ?, '', false)",
                    setId, data.path("titleRu").asText(), data.path("titleKk").asText());
            update(connection, "update vocabulary_sets set draft_version_id = ? where id = ?", setVersionId, setId);
            for (JsonNode item : data.path("items")) {
                long itemId = insert(connection, "insert into vocabulary_items(set_id, status) values (?, 'DRAFT')", setId);
                String source = item.path("sourceText").asText();
                long versionId = insert(connection, "insert into vocabulary_item_versions(item_id, version_no, status, source_text, source_normalized, part_of_speech, meaning_note_ru, difficulty, source_reference, universal_audience) values (?, 1, 'DRAFT', ?, ?, ?, ?, ?, '', true)",
                        itemId, source, normalizer.normalize(source), item.path("partOfSpeech").asText(),
                        item.path("meaningNoteRu").asText(), item.path("difficulty").asInt());
                update(connection, "update vocabulary_items set draft_version_id = ? where id = ?", versionId, itemId);
                answer(connection, normalizer, versionId, item.path("primaryAnswer").asText(), true);
                for (JsonNode alternative : item.path("alternativeAnswers")) answer(connection, normalizer, versionId, alternative.asText(), false);
            }
        }
        // Источники и author/reviewer остаются пустыми: миграция не заменяет редакционную проверку.
        // Игры, классы и каталог не создаются и не включаются.
    }

    private static String resource(String file) {
        String path = "/quiz/basic-vocabulary/" + file + ".json";
        try (var stream = V54__basic_vocabulary_drafts.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Не найден пакет V54: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Не удалось прочитать пакет V54: " + path, error);
        }
    }

    private static long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("V54: БД не вернула ID новой записи");
                return keys.getLong(1);
            }
        }
    }

    private static void update(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static void answer(Connection connection, QuizTextNormalizer normalizer, long versionId, String text, boolean primary) throws SQLException {
        update(connection, "insert into vocabulary_item_answers(item_version_id, answer_text, answer_normalized, is_primary, primary_marker) values (?, ?, ?, ?, ?)",
                versionId, text, normalizer.normalize(text), primary, primary ? 1 : null);
    }
}
