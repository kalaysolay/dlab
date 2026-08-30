package kz.damulab.questions;

/**
 * Полные данные карточки предпросмотра вопроса в банке.
 *
 * <p>В отличие от {@link QuestionResponse}, который остаётся лёгким DTO для таблицы,
 * этот ответ содержит ключ ответа и мини-лекцию. Endpoint доступен только администратору;
 * в ученические API правильные ответы из него не попадают. Для опубликованного вопроса
 * с ожидающим черновиком показывается именно черновик — методист должен видеть ту версию,
 * которую он собирается одобрить или опубликовать.</p>
 */
public record QuestionPreviewResponse(
        QuestionResponse question,
        int previewVersionNo,
        boolean pendingDraft,
        QuestionForm content,
        String bodyRuHtml,
        String bodyKkHtml,
        String miniLectureRuHtml,
        String miniLectureKkHtml
) {
}
