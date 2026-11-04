package kz.damulab.translator;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.ai.AiTextResult;
import kz.damulab.users.AppUserRepository;

/** Записывает историю в коротких транзакциях, не удерживая транзакцию во время вызова AI. */
@Service
public class TranslationHistoryService {
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;

    public TranslationHistoryService(JdbcTemplate jdbc, AppUserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** Сохраняет принятую попытку до внешнего вызова; пользователя определяет сервер. */
    @Transactional
    public Long start(TranslationDirection direction, String sourceText, String translatedText,
            String explanationLanguage, String explanationMode) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        // Журнал не является JPA-сущностью: исторические тесты запускают приложение
        // на миграциях до V73, и Hibernate не должен требовать новую таблицу при их старте.
        // Запросы к журналу выполняются только при фактическом использовании переводчика.
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into translation_request_history
                        (user_id, actor_email, operation, direction, source_text, translated_text,
                         explanation_language, explanation_mode, status, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                    """, new String[] { "id" });
            statement.setLong(1, user.getId());
            statement.setString(2, user.getEmail());
            statement.setString(3, explanationMode == null ? "TRANSLATE" : "EXPLAIN");
            statement.setString(4, direction.name());
            statement.setString(5, sourceText);
            statement.setString(6, translatedText);
            statement.setString(7, explanationLanguage);
            statement.setString(8, explanationMode);
            statement.setObject(9, OffsetDateTime.now());
            return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }

    /** Сохраняет ответ и фактическую модель успешного вызова. */
    @Transactional
    public void succeed(Long id, AiTextResult result) {
        // Ответ разбора не заменяет перевод, присланный клиентом: это разные поля.
        jdbc.update("""
                update translation_request_history
                set translated_text = case when operation = 'TRANSLATE' then ? else translated_text end,
                    explanation_text = case when operation = 'EXPLAIN' then ? else explanation_text end,
                    provider = ?, model = ?, status = 'SUCCESS', completed_at = ?
                where id = ?
                """, result.text(), result.text(), result.provider(), result.model(), OffsetDateTime.now(), id);
    }

    /** Сохраняет только технический код ошибки, без сырых сообщений внешнего провайдера. */
    @Transactional
    public void fail(Long id, String errorCode) {
        jdbc.update("""
                update translation_request_history
                set status = 'FAILED', error_code = ?, completed_at = ? where id = ?
                """, errorCode, OffsetDateTime.now(), id);
    }
}
