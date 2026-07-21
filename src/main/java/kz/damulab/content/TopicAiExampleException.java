package kz.damulab.content;

/**
 * Ошибка бизнес-логики эталонов темы (лимит, валидация ключа под тип, не найдено и т.п.).
 *
 * <p>Несёт машинный {@code code} (например {@code "example_limit_reached"}), который
 * контроллер превращает в человекочитаемое сообщение (см. AdminTopicAiExamplePageController).
 * Отдельный тип (а не общий {@code ContentGraphException}) выбран, чтобы не влиять на
 * REST-обработчик графа контента и держать маппинг сообщений рядом с фичей.
 */
public class TopicAiExampleException extends RuntimeException {

    private final String code;

    public TopicAiExampleException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
