package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Проверка foundation и последующих добавочных миграций через Flyway: H2 по умолчанию, PostgreSQL через QUIZ_TEST_DB_*.
 * Каждый тест откатывает свои данные; рабочая БД и seed приложения не используются.
 * Уровень БД дополняют migration/startup-тесты со старой V46-историей;
 * результаты H2 и PostgreSQL отдельно фиксируются в GAP.md.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class QuizGameFoundationSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void draftDoesNotEnterCatalogAndVisibilityIsIndependentOfPublication() {
        Long gameId = createGame();
        Long versionId = createVersion(gameId, 1, "SUBJECT_QUIZ");

        assertThat(catalogCount(gameId)).isZero();
        jdbc.update("update quiz_games set published_version_id = ?, status = 'PUBLISHED' where id = ?", versionId, gameId);
        // Указатель сам по себе не публикует черновик; каталог проверяет и редакцию.
        assertThat(catalogCount(gameId)).isZero();
        jdbc.update("update quiz_game_versions set status = 'PUBLISHED', published_at = current_timestamp where id = ?", versionId);
        assertThat(catalogCount(gameId)).isEqualTo(1);

        jdbc.update("update quiz_games set catalog_visible = false where id = ?", gameId);
        assertThat(catalogCount(gameId)).isZero();
        assertThat(jdbc.queryForObject("select published_version_id from quiz_games where id = ?", Long.class, gameId))
                .isEqualTo(versionId);

        jdbc.update("update quiz_games set catalog_visible = true where id = ?", gameId);
        assertThat(catalogCount(gameId)).isEqualTo(1);
    }

    @Test
    void publishedAndDraftPointersMustBelongToTheirGame() {
        Long gameId = createGame();
        Long otherGameId = createGame();
        Long foreignVersion = createVersion(otherGameId, 1, "TRUE_FALSE");

        assertThatThrownBy(() -> isolated(() -> jdbc.update("update quiz_games set published_version_id = ? where id = ?", foreignVersion, gameId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> isolated(() -> jdbc.update("update quiz_games set draft_version_id = ? where id = ?", foreignVersion, gameId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void versionNumbersAreUniqueWithinGameAndPositive() {
        Long gameId = createGame();
        createVersion(gameId, 1, "WORD_TRANSLATION");

        assertThatThrownBy(() -> isolated(() -> createVersion(gameId, 1, "WORD_TRANSLATION")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> isolated(() -> createVersion(gameId, 0, "WORD_TRANSLATION")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "max_players,0", "max_players,5",
            "min_question_count,0", "default_question_count,21",
            "min_round_seconds,0", "default_round_seconds,121"
    })
    void invalidPlayerAndTimingLimitsAreRejected(String column, int value) {
        Long versionId = createVersion(createGame(), 1, "SUBJECT_QUIZ");

        // Имя столбца — только статическая CsvSource теста, не вход пользователя.
        assertThatThrownBy(() -> isolated(() -> jdbc.update("update quiz_game_versions set " + column + " = ? where id = ?", value, versionId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roomGradeRequiresGradeSelectionAndParticipantGradesHasNoSelector() {
        Long versionId = createVersion(createGame(), 1, "SUBJECT_QUIZ");

        assertThatThrownBy(() -> isolated(() -> jdbc.update("update quiz_game_versions set audience_policy = 'ROOM_GRADE' where id = ?", versionId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update quiz_game_versions set audience_policy = 'ROOM_GRADE', grade_selection_mode = 'PROFILE_DEFAULT' where id = ?", versionId);
        assertThatThrownBy(() -> isolated(() -> jdbc.update("update quiz_game_versions set audience_policy = 'PARTICIPANT_GRADES' where id = ?", versionId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update quiz_game_versions set audience_policy = 'PARTICIPANT_GRADES', grade_selection_mode = 'NONE' where id = ?", versionId);
    }

    @Test
    void subjectConfigurationCannotPointToAWordGameVersion() {
        Long subjectId = createSubject();
        Long wordVersion = createVersion(createGame(), 1, "WORD_TRANSLATION");
        Long subjectVersion = createVersion(createGame(), 1, "SUBJECT_QUIZ");

        assertThatThrownBy(() -> isolated(() -> jdbc.update("insert into quiz_subject_game_configs(game_version_id, subject_id) values (?, ?)", wordVersion, subjectId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into quiz_subject_game_configs(game_version_id, subject_id) values (?, ?)", subjectVersion, subjectId);
        jdbc.update("insert into quiz_subject_game_question_types(game_version_id, question_type) values (?, 'MCQ')", subjectVersion);
        assertThatThrownBy(() -> isolated(() -> jdbc.update("insert into quiz_subject_game_question_types(game_version_id, question_type) values (?, 'TRUE_FALSE')", subjectVersion)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referencedVersionCannotBeDeletedOrUsedAsBothPointers() {
        Long gameId = createGame();
        Long versionId = createVersion(gameId, 1, "TRUE_FALSE");
        jdbc.update("update quiz_games set published_version_id = ? where id = ?", versionId, gameId);

        assertThatThrownBy(() -> isolated(() -> jdbc.update("delete from quiz_game_versions where id = ?", versionId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> isolated(() -> jdbc.update("update quiz_games set draft_version_id = ? where id = ?", versionId, gameId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // PostgreSQL прерывает транзакцию после нарушения CHECK/FK. Savepoint позволяет проверять
    // следующее ограничение, не принимая «transaction aborted» за ожидаемую ошибку данных.
    private void isolated(Runnable action) {
        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        nested.executeWithoutResult(status -> action.run());
    }

    private Long createGame() {
        String code = "foundation-" + UUID.randomUUID();
        jdbc.update("insert into quiz_games(code) values (?)", code);
        return jdbc.queryForObject("select id from quiz_games where code = ?", Long.class, code);
    }

    private Long createVersion(Long gameId, int versionNo, String type) {
        jdbc.update("insert into quiz_game_versions(game_id, version_no, game_type, title_ru, title_kk) values (?, ?, ?, ?, ?)",
                gameId, versionNo, type, "Игра", "Ойын");
        return jdbc.queryForObject("select id from quiz_game_versions where game_id = ? and version_no = ?", Long.class, gameId, versionNo);
    }

    private Long createSubject() {
        String code = "foundation-" + UUID.randomUUID();
        jdbc.update("insert into subjects(code, title_ru, title_kk, description_ru, description_kk) values (?, ?, ?, ?, ?)",
                code, "Предмет", "Пән", "Тестовый предмет", "Сынақ пәні");
        return jdbc.queryForObject("select id from subjects where code = ?", Long.class, code);
    }

    private int catalogCount(Long gameId) {
        return jdbc.queryForObject("select count(*) from quiz_games g join quiz_game_versions v on v.id = g.published_version_id where g.id = ? and g.catalog_visible = true and g.status <> 'ARCHIVED' and v.status = 'PUBLISHED' and v.published_at is not null",
                Integer.class, gameId);
    }

    @Test
    void vocabularyDirectionAndVersionOwnershipAreEnforced() {
        Long set = vocabularySet("en", "ru");
        Long other = vocabularySet("kk", "ru");
        Long version = setVersion(other);
        reject("update vocabulary_sets set published_version_id = ? where id = ?", version, set);
        reject("update vocabulary_sets set source_language = 'en', target_language = 'kk' where id = ?", set);
        assertThat(vocabularySet("ru", "en")).isNotNull();
        Long gameVersion = createVersion(createGame(), 1, "WORD_TRANSLATION");
        reject("insert into quiz_word_game_configs(game_version_id, vocabulary_set_id, source_language, target_language) values (?, ?, 'kk', 'ru')", gameVersion, set);
        jdbc.update("insert into quiz_word_game_configs(game_version_id, vocabulary_set_id, source_language, target_language) values (?, ?, 'en', 'ru')", gameVersion, set);
    }

    @Test
    void vocabularyPrimaryAndNormalizedAnswersAreConstrained() {
        Long word = wordVersion(vocabularySet("en", "ru"));
        jdbc.update("insert into vocabulary_item_answers(item_version_id, answer_text, answer_normalized, is_primary, primary_marker) values (?, 'Дом', 'дом', true, 1)", word);
        reject("insert into vocabulary_item_answers(item_version_id, answer_text, answer_normalized, is_primary, primary_marker) values (?, 'Жильё', 'жильё', true, 1)", word);
        reject("insert into vocabulary_item_answers(item_version_id, answer_text, answer_normalized, is_primary, primary_marker) values (?, 'Дом', 'дом', false, null)", word);
        reject("insert into vocabulary_item_answers(item_version_id, answer_text, answer_normalized, is_primary, primary_marker) values (?, 'Жильё', 'жильё', true, null)", word);
        reject("insert into vocabulary_item_answers(item_version_id, answer_text, answer_normalized, is_primary, primary_marker) values (?, 'Жильё', 'жильё', false, 1)", word);
        jdbc.update("insert into vocabulary_item_answers(item_version_id, answer_text, answer_normalized, is_primary, primary_marker) values (?, 'Жильё', 'жильё', false, null)", word);
        reject("insert into vocabulary_distractor_exclusions(item_version_id, excluded_item_version_id, reason) values (?, ?, 'self')", word, word);
        reject("insert into vocabulary_item_grades(item_version_id, grade_id) values (?, -1)", word);
    }

    @Test
    void falseIsValidAndFactPointersCannotCrossOwners() {
        Long set = factSet();
        Long statement = statement(set);
        Long foreign = statement(set);
        Long fact = factVersion(foreign);
        jdbc.update("update fact_statement_versions set truth_value = false where id = ?", fact);
        assertThat(jdbc.queryForObject("select truth_value from fact_statement_versions where id = ?", Boolean.class, fact)).isFalse();
        reject("update fact_statements set published_version_id = ? where id = ?", fact, statement);
        Long wordGame = createVersion(createGame(), 1, "WORD_TRANSLATION");
        reject("insert into quiz_fact_game_configs(game_version_id, fact_set_id) values (?, ?)", wordGame, set);
    }

    @Test
    void roundHasExactlyOneSourceAndReferencedVersionsCannotDisappear() {
        Long room = room();
        Long set = vocabularySet("en", "ru");
        Long setVersion = setVersion(set);
        Long word = wordVersion(set);
        Long fact = factVersion(statement(factSet()));
        reject("insert into quiz_rounds(room_id, order_no, content_kind) values (?, 1, 'FACT')", room);
        String columns = "room_id, order_no, content_kind, vocabulary_item_version_id, vocabulary_set_version_id, fact_statement_version_id";
        reject("insert into quiz_rounds(" + columns + ") values (?, 1, 'VOCABULARY', ?, ?, ?)", room, word, setVersion, fact);
        jdbc.update("insert into quiz_rounds(room_id, order_no, content_kind, vocabulary_item_version_id, vocabulary_set_version_id) values (?, 1, 'VOCABULARY', ?, ?)", room, word, setVersion);
        reject("delete from vocabulary_item_versions where id = ?", word);
        reject("delete from vocabulary_set_versions where id = ?", setVersion);
        reject("insert into quiz_rounds(room_id, order_no, content_kind, vocabulary_item_version_id, vocabulary_set_version_id) values (?, 2, 'VOCABULARY', ?, ?)", room, word, setVersion);
        jdbc.update("insert into quiz_rounds(room_id, order_no, content_kind, fact_statement_version_id) values (?, 2, 'FACT', ?)", room, fact);
        reject("delete from fact_statement_versions where id = ?", fact);
    }

    @Test
    void roomOwnershipRevisionAndCreateKeyAreConstrained() {
        Long room = room();
        Long profile = jdbc.queryForObject("select host_student_profile_id from quiz_rooms where id = ?", Long.class, room);
        Long game = createGame();
        Long other = createGame();
        Long version = createVersion(game, 1, "TRUE_FALSE");
        reject("update quiz_rooms set game_id = ? where id = ?", game, room);
        reject("update quiz_rooms set game_id = ?, game_version_id = ?, game_type = 'TRUE_FALSE' where id = ?", other, version, room);
        reject("update quiz_rooms set game_id = ?, game_version_id = ?, game_type = 'WORD_TRANSLATION' where id = ?", game, version, room);
        jdbc.update("update quiz_rooms set game_id = ?, game_version_id = ?, game_type = 'TRUE_FALSE' where id = ?", game, version, room);
        reject("update quiz_rooms set settings_revision = -1 where id = ?", room);
        assertThat(jdbc.queryForObject("select command_revision from quiz_rooms where id = ?", Long.class, room)).isZero();
        reject("update quiz_rooms set command_revision = -1 where id = ?", room);
        reject("update quiz_rooms set status = 'UNKNOWN' where id = ?", room);
        jdbc.update("insert into quiz_room_creation_keys(student_profile_id, key_hash, payload_hash, room_id) values (?, 'key', 'payload', ?)", profile, room);
        reject("insert into quiz_room_creation_keys(student_profile_id, key_hash, payload_hash, room_id) values (?, 'key', 'other', ?)", profile, room);
        reject("delete from quiz_game_versions where id = ?", version);
        jdbc.update("update quiz_rooms set status = 'CANCELLED' where id = ?", room);
        jdbc.update("update quiz_rooms set status = 'EXPIRED' where id = ?", room);
    }

    @Test
    void skippedAndTimedOutAnswersHaveNoPointsAndNoNegativeTime() {
        Long room = room();
        Long profile = jdbc.queryForObject("select host_student_profile_id from quiz_rooms where id = ?", Long.class, room);
        jdbc.update("insert into quiz_participants(room_id, student_profile_id, display_name) values (?, ?, 'Ученик')", room, profile);
        Long participant = jdbc.queryForObject("select id from quiz_participants where room_id = ?", Long.class, room);
        Long fact = factVersion(statement(factSet()));
        jdbc.update("insert into quiz_rounds(room_id, order_no, content_kind, fact_statement_version_id) values (?, 1, 'FACT', ?)", room, fact);
        Long round = jdbc.queryForObject("select id from quiz_rounds where room_id = ?", Long.class, room);
        jdbc.update("insert into quiz_answers(round_id, participant_id, answer_json, is_correct, points_awarded) values (?, ?, 'false', false, 0)", round, participant);
        reject("update quiz_answers set response_ms = -1 where round_id = ?", round);
        reject("update quiz_answers set timed_out = true, skipped = true where round_id = ?", round);
        reject("update quiz_answers set skipped = true, points_awarded = 1 where round_id = ?", round);
        jdbc.update("update quiz_answers set skipped = true, response_ms = 20000 where round_id = ?", round);
        reject("update quiz_participants set ready_revision = -1 where id = ?", participant);
    }

    private void reject(String sql, Object... args) {
        assertThatThrownBy(() -> isolated(() -> jdbc.update(sql, args))).isInstanceOf(DataIntegrityViolationException.class);
    }
    private Long vocabularySet(String source, String target) {
        String code = UUID.randomUUID().toString();
        jdbc.update("insert into vocabulary_sets(code, source_language, target_language) values (?, ?, ?)", code, source, target);
        return jdbc.queryForObject("select id from vocabulary_sets where code = ?", Long.class, code);
    }
    private Long setVersion(Long set) {
        jdbc.update("insert into vocabulary_set_versions(set_id, version_no, title_ru, title_kk, source_reference) values (?, 1, 'Набор', 'Жинақ', 'Учебник')", set);
        return jdbc.queryForObject("select id from vocabulary_set_versions where set_id = ?", Long.class, set);
    }
    private Long wordVersion(Long set) {
        jdbc.update("insert into vocabulary_items(set_id) values (?)", set);
        Long item = jdbc.queryForObject("select max(id) from vocabulary_items where set_id = ?", Long.class, set);
        jdbc.update("insert into vocabulary_item_versions(item_id, version_no, source_text, source_normalized, source_reference) values (?, 1, 'house', 'house', 'Учебник')", item);
        return jdbc.queryForObject("select id from vocabulary_item_versions where item_id = ?", Long.class, item);
    }
    private Long factSet() {
        String code = UUID.randomUUID().toString();
        jdbc.update("insert into fact_sets(code, title_ru, title_kk, source_policy) values (?, 'Факты', 'Деректер', 'Учебник')", code);
        return jdbc.queryForObject("select id from fact_sets where code = ?", Long.class, code);
    }
    private Long statement(Long set) {
        jdbc.update("insert into fact_statements(set_id) values (?)", set);
        return jdbc.queryForObject("select max(id) from fact_statements where set_id = ?", Long.class, set);
    }
    private Long factVersion(Long statement) {
        jdbc.update("insert into fact_statement_versions(statement_id, version_no, text_ru, text_kk, truth_value, explanation_ru, explanation_kk, source_reference) values (?, 1, 'Утверждение', 'Тұжырым', false, 'Объяснение', 'Түсіндірме', 'Учебник')", statement);
        return jdbc.queryForObject("select id from fact_statement_versions where statement_id = ?", Long.class, statement);
    }
    private Long room() {
        String email = UUID.randomUUID() + "@example.test";
        jdbc.update("insert into app_users(email, password_hash, full_name) values (?, 'test-only', 'Тест')", email);
        Long user = jdbc.queryForObject("select id from app_users where email = ?", Long.class, email);
        jdbc.update("insert into student_profiles(user_id) values (?)", user);
        Long profile = jdbc.queryForObject("select id from student_profiles where user_id = ?", Long.class, user);
        String code = UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("insert into quiz_rooms(code, host_student_profile_id, language, question_count, round_seconds, max_players, status) values (?, ?, 'ru', 10, 20, 4, 'WAITING')", code, profile);
        return jdbc.queryForObject("select id from quiz_rooms where code = ?", Long.class, code);
    }
}
