package kz.damulab.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kz.damulab.questions.QuestionType;

/**
 * Доступ к эталонным вопросам темы ({@link TopicAiExample}).
 *
 * <p>Кто вызывает:
 * <ul>
 *   <li>{@code TopicAiExampleService} — CRUD и проверка лимита на тему;</li>
 *   <li>{@code AiContentFactoryService} — отбор активных ({@code include_in_ai = true}) эталонов
 *       для передачи в промпт;</li>
 *   <li>контроллер хаба «Эталоны для ИИ» — подсчёт покрытия тем.</li>
 * </ul>
 */
public interface TopicAiExampleRepository extends JpaRepository<TopicAiExample, Long> {

    /** Все эталоны темы, стабильный порядок по id (для списка и редактора). */
    List<TopicAiExample> findByTopicIdOrderByIdAsc(Long topicId);

    /** Только активные эталоны темы — источник few-shot для генерации. */
    List<TopicAiExample> findByTopicIdAndIncludeInAiTrueOrderByIdAsc(Long topicId);

    /** Сколько всего эталонов у темы — для проверки лимита хранения (store cap). */
    long countByTopicId(Long topicId);

    /** Сколько активных эталонов у темы — для карточки покрытия и подсказок в UI. */
    long countByTopicIdAndIncludeInAiTrue(Long topicId);

    /**
     * Есть ли у темы активный эталон конкретного типа.
     * Используется в подсказке на экране генерации: например, для MATCHING полезно иметь
     * эталон того же типа, иначе модель опирается только на SCQ/MCQ.
     */
    boolean existsByTopicIdAndQuestionTypeAndIncludeInAiTrue(Long topicId, QuestionType questionType);
}
