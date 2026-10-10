package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import kz.damulab.config.DamulabQuizProperties;
import org.junit.jupiter.api.Test;

/** Проверяет opt-in запрет самопроверки без введения новой роли. */
class EditorialWorkflowServiceTest {
    @Test
    void strictModeRejectsAuthorAsReviewer() {
        DamulabQuizProperties properties = new DamulabQuizProperties(); properties.setStrictSelfReview(true);
        VocabularyItemVersion version = new VocabularyItemVersion(1, null, "word", "word", "source");
        version.setCreatedBy(7L); version.setStatus(EditorialStatus.NEEDS_REVIEW);
        EditorialWorkflowService service = new EditorialWorkflowService(mock(QuizContentEditorialEventRepository.class), properties);
        assertThatThrownBy(() -> service.approve(version, QuizContentType.VOCABULARY_ITEM, 1L, 7L))
                .isInstanceOf(ContentValidationException.class).hasMessageContaining("Автор не может");
    }
}
