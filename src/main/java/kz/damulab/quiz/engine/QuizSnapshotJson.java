package kz.damulab.quiz.engine;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

/** Единый строгий JSON-кодек закрытых снимков; ошибки не превращаются в пустое условие. */
@Component
public class QuizSnapshotJson {
    private final ObjectMapper mapper;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizSnapshotJson(ObjectMapper mapper) { this.mapper = mapper; }
    /** Сериализует только явно собранные значения, никогда JPA-граф. */
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new QuizEngineException("SNAPSHOT_INVALID"); }
    }
    /** Читает обязательный JSON снимка; null и невалидный JSON блокируют формирование/чтение. */
    public JsonNode read(String value) {
        try {
            JsonNode result = mapper.readTree(value);
            if (result == null || result.isNull()) throw new QuizEngineException("SNAPSHOT_INVALID");
            return result;
        } catch (JsonProcessingException | IllegalArgumentException e) { throw new QuizEngineException("SNAPSHOT_INVALID"); }
    }
    /** Читает сохранённый ответ: явный JSON null допустим для skip/timeout, но malformed JSON запрещён. */
    public JsonNode readNullable(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            if (node == null) throw new QuizEngineException("ANSWER_INVALID");
            return node;
        } catch (JsonProcessingException | IllegalArgumentException e) { throw new QuizEngineException("ANSWER_INVALID"); }
    }
    /** Выбирает локализованный текст: локаль комнаты → RU → KK; blank считается отсутствием. */
    public static String localized(String ru, String kk, String locale) {
        String preferred = "kk".equals(locale) ? kk : ru;
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (ru != null && !ru.isBlank()) return ru;
        return kk == null ? "" : kk;
    }
}
