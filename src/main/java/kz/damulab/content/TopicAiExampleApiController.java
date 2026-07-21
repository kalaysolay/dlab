package kz.damulab.content;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST для страницы AI-генерации: отдаёт активные эталоны темы, чтобы методист мог выбрать,
 * какие включить в конкретный запрос (пикер на question-ai-generate).
 *
 * <p>Только чтение. Доступ ограничен ролью ADMIN через {@code /api/admin/**} в SecurityConfig
 * (как и остальные админ-API графа контента).
 */
@RestController
public class TopicAiExampleApiController {

    private final TopicAiExampleService exampleService;

    public TopicAiExampleApiController(TopicAiExampleService exampleService) {
        this.exampleService = exampleService;
    }

    /** Активные эталоны темы для пикера few-shot на странице генерации. */
    @GetMapping("/api/admin/topics/{topicId}/ai-examples")
    List<TopicAiExampleResponse> activeExamples(@PathVariable Long topicId) {
        return exampleService.listActiveByTopic(topicId);
    }
}
