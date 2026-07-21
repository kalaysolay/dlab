package kz.damulab.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import kz.damulab.questions.QuestionType;

/**
 * Эталонный вопрос темы для few-shot AI-генерации (вариант B — полный пример с ключом ответа).
 *
 * <p>Зачем: единственный семантический контекст темы в промпте генератора — это её название.
 * Эталон добавляет модели образец стиля, scope и структуры distractors, поэтому черновики
 * получаются ближе к ожиданиям методиста. Подробности и макеты — docs/topic-ai-examples.
 *
 * <p>Важно: это НЕ вопрос банка. Ученику эталон не показывается и в банк не публикуется —
 * он существует только как вход для {@code AiPromptBuilder}. Приоритет ручных эталонов над
 * возможным авто-few-shot из банка обсуждается отдельно (см. TODO ниже).
 *
 * <p>Кто вызывает: {@code TopicAiExampleService} (CRUD, валидация, аудит) и
 * {@code AiContentFactoryService} (отбор {@link #includeInAi} эталонов в запрос к провайдеру).
 *
 * <p>Хранение ключа ответа: {@link #answerKeyJson} — одна колонка, содержимое зависит от
 * {@link #questionType} (варианты / пары / пропуски). Формат совпадает с формами вопроса банка
 * ({@code ChoiceOptionForm}, {@code MatchingPairForm}, {@code FillAnswerForm}).
 *
 * <p>Инвариант: {@code answerKeyJson} всегда согласован с {@code questionType}; за это отвечает
 * {@code TopicAiExampleService} при сохранении, сущность сама структуру не валидирует.
 *
 * TODO(4a): пока используются только ручные эталоны. В будущем возможен fallback на
 * approved-вопросы банка (авто-few-shot), с приоритетом ручных и, возможно, отбором по качеству.
 */
@Entity
@Table(name = "topic_ai_examples")
public class TopicAiExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Тема-владелец. Эталоны логически принадлежат теме (в БД on delete cascade). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /** Тип вопроса-эталона. Определяет, как интерпретировать {@link #answerKeyJson}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    /** Ориентир сложности 1..5 — помогает модели держать нужный уровень. */
    @Column(nullable = false)
    private int difficulty;

    @Column(name = "body_ru", nullable = false)
    private String bodyRu;

    @Column(name = "body_kk", nullable = false)
    private String bodyKk;

    /**
     * Ключ ответа в JSON. Структура зависит от {@link #questionType}:
     * SCQ/MCQ — варианты, MATCHING — пары, FILL_IN — пропуски.
     * Сериализацию/десериализацию и валидацию делает {@code TopicAiExampleService}.
     */
    @Column(name = "answer_key_json", nullable = false, columnDefinition = "text")
    private String answerKeyJson;

    /**
     * Мягкий выключатель: если false — эталон хранится, но не попадает в промпт.
     * Позволяет отключить пример без удаления (например, если он временно неудачный).
     */
    @Column(name = "include_in_ai", nullable = false)
    private boolean includeInAi = true;

    /**
     * Внутренняя заметка методиста. В provider DTO НЕ уходит (вырезается в сервисе) —
     * нужна только команде контента.
     */
    @Column(name = "internal_note", length = 1000)
    private String internalNote;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected TopicAiExample() {
    }

    public TopicAiExample(
            Topic topic,
            QuestionType questionType,
            int difficulty,
            String bodyRu,
            String bodyKk,
            String answerKeyJson,
            boolean includeInAi,
            String internalNote
    ) {
        this.topic = topic;
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.bodyRu = bodyRu;
        this.bodyKk = bodyKk;
        this.answerKeyJson = answerKeyJson;
        this.includeInAi = includeInAi;
        this.internalNote = internalNote;
    }

    /** Обновляет updated_at при каждом insert/update (аудит изменений). */
    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Полное обновление редактируемых полей эталона.
     * Тема не меняется: эталон всегда остаётся у своей темы (перенос между темами не поддерживается).
     */
    public void update(
            QuestionType questionType,
            int difficulty,
            String bodyRu,
            String bodyKk,
            String answerKeyJson,
            boolean includeInAi,
            String internalNote
    ) {
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.bodyRu = bodyRu;
        this.bodyKk = bodyKk;
        this.answerKeyJson = answerKeyJson;
        this.includeInAi = includeInAi;
        this.internalNote = internalNote;
    }

    public Long getId() {
        return id;
    }

    public Topic getTopic() {
        return topic;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public String getBodyRu() {
        return bodyRu;
    }

    public String getBodyKk() {
        return bodyKk;
    }

    public String getAnswerKeyJson() {
        return answerKeyJson;
    }

    public boolean isIncludeInAi() {
        return includeInAi;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
