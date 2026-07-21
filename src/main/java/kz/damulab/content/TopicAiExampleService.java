package kz.damulab.content;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.audit.AdminContentAuditService;
import kz.damulab.questions.ChoiceOptionForm;
import kz.damulab.questions.FillAnswerForm;
import kz.damulab.questions.FillMatchMode;
import kz.damulab.questions.MatchingPairForm;
import kz.damulab.questions.QuestionType;

/**
 * Управление эталонными вопросами темы ({@link TopicAiExample}) — вариант B (few-shot с ключом).
 *
 * <p>Отвечает за: CRUD эталонов, проверку лимита хранения на тему, валидацию ключа ответа под
 * тип вопроса (те же правила, что в банке: SCQ — ровно один correct, MCQ — минимум один,
 * MATCHING — ≥2 пары, FILL_IN — ≥1 пропуск с правилом), сериализацию ключа в JSON и запись
 * действий в {@link AdminContentAuditService}.
 *
 * <p>Кто вызывает: страничные контроллеры админки (список/редактор эталонов, хаб покрытия).
 * Отбор эталонов в промпт делает {@code AiContentFactoryService} напрямую через репозиторий —
 * чтобы пакет {@code content} не зависел от пакета {@code ai}.
 *
 * <p>Лимит: {@link #MAX_EXAMPLES_PER_TOPIC} — сколько эталонов вообще можно хранить на теме
 * (store cap). Сколько из них реально уйдёт в промпт — ограничивает {@code AiContentFactoryService}
 * (prompt cap), это разные пороги (см. решение 2b).
 */
@Service
public class TopicAiExampleService {

    /**
     * Максимум эталонов на тему (store cap). Значение 6 согласовано (решение 2b):
     * хранить до 6, в промпт отправлять меньше. Держим константой, а не в конфиге —
     * порог продуктовый и меняется редко. TODO: вынести в application.yml, если появится
     * потребность настраивать per-окружение.
     */
    public static final int MAX_EXAMPLES_PER_TOPIC = 6;

    private final TopicAiExampleRepository examples;
    private final TopicRepository topics;
    private final AdminContentAuditService audit;
    private final ObjectMapper objectMapper;

    public TopicAiExampleService(
            TopicAiExampleRepository examples,
            TopicRepository topics,
            AdminContentAuditService audit,
            ObjectMapper objectMapper
    ) {
        this.examples = examples;
        this.topics = topics;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /** Все эталоны темы (для списка и редактора), в стабильном порядке по id. */
    @Transactional(readOnly = true)
    public List<TopicAiExampleResponse> listByTopic(Long topicId) {
        return examples.findByTopicIdOrderByIdAsc(topicId).stream().map(this::toResponse).toList();
    }

    /**
     * Только активные эталоны темы — для пикера на странице AI-генерации
     * (методист отмечает, какие включить в конкретный запрос).
     */
    @Transactional(readOnly = true)
    public List<TopicAiExampleResponse> listActiveByTopic(Long topicId) {
        return examples.findByTopicIdAndIncludeInAiTrueOrderByIdAsc(topicId).stream().map(this::toResponse).toList();
    }

    /**
     * Один эталон по id с проверкой принадлежности теме.
     * @throws TopicAiExampleException {@code example_not_found} / {@code example_topic_mismatch}
     */
    @Transactional(readOnly = true)
    public TopicAiExampleResponse get(Long topicId, Long exampleId) {
        return toResponse(findInTopic(topicId, exampleId));
    }

    /** Сколько эталонов уже создано у темы — для UI (счётчик «X из N»). */
    @Transactional(readOnly = true)
    public long countByTopic(Long topicId) {
        return examples.countByTopicId(topicId);
    }

    /**
     * Создаёт эталон у темы.
     * Проверяет лимит хранения и валидность ключа под тип, сериализует ключ в JSON.
     *
     * @throws TopicAiExampleException при превышении лимита ({@code example_limit_reached})
     *                                 или невалидном ключе (коды как в банке вопросов)
     */
    @Transactional
    public TopicAiExampleResponse create(Long topicId, TopicAiExampleForm form) {
        Topic topic = findTopic(topicId);
        // Лимит проверяем ДО валидации ключа: быстрый отказ и понятное сообщение методисту.
        if (examples.countByTopicId(topicId) >= MAX_EXAMPLES_PER_TOPIC) {
            throw new TopicAiExampleException("example_limit_reached");
        }
        validate(form);
        TopicAiExample example = new TopicAiExample(
                topic,
                form.getQuestionType(),
                form.getDifficulty(),
                form.getBodyRu().trim(),
                form.getBodyKk().trim(),
                answerKeyJson(form),
                form.isIncludeInAi(),
                trimToNull(form.getInternalNote())
        );
        TopicAiExample saved = examples.save(example);
        audit.record("topic_ai_example_created", "TopicAiExample", saved.getId(), topicId + "/" + form.getQuestionType());
        return toResponse(saved);
    }

    /**
     * Полное обновление эталона.
     * @throws TopicAiExampleException если не найден/не в теме или ключ невалиден
     */
    @Transactional
    public TopicAiExampleResponse update(Long topicId, Long exampleId, TopicAiExampleForm form) {
        TopicAiExample example = findInTopic(topicId, exampleId);
        validate(form);
        example.update(
                form.getQuestionType(),
                form.getDifficulty(),
                form.getBodyRu().trim(),
                form.getBodyKk().trim(),
                answerKeyJson(form),
                form.isIncludeInAi(),
                trimToNull(form.getInternalNote())
        );
        audit.record("topic_ai_example_updated", "TopicAiExample", example.getId(), topicId + "/" + form.getQuestionType());
        return toResponse(example);
    }

    /** Удаляет эталон темы (hard-delete: эталоны не участвуют в истории, как вопросы). */
    @Transactional
    public void delete(Long topicId, Long exampleId) {
        TopicAiExample example = findInTopic(topicId, exampleId);
        examples.delete(example);
        audit.record("topic_ai_example_deleted", "TopicAiExample", exampleId, String.valueOf(topicId));
    }

    /**
     * Покрытие тем эталонами для хаба «Эталоны для ИИ» по паре предмет+класс.
     * Для каждой активной темы считает всего/активных эталонов и набор покрытых типов.
     *
     * <p>Замечание по производительности: на N тем делает O(N) запросов подсчёта. Для текущих
     * объёмов (десятки тем на пару предмет/класс) это приемлемо. TODO: если тем станет много —
     * заменить на один group by запрос в репозитории.
     */
    @Transactional(readOnly = true)
    public List<TopicAiExampleCoverageResponse> coverage(Long subjectId, Long gradeId) {
        List<Topic> topicList = topics.findBySubjectIdAndGradeIdAndDeletedAtIsNullOrderByTitleRuAsc(subjectId, gradeId);
        List<TopicAiExampleCoverageResponse> rows = new ArrayList<>(topicList.size());
        for (Topic topic : topicList) {
            long total = examples.countByTopicId(topic.getId());
            long active = examples.countByTopicIdAndIncludeInAiTrue(topic.getId());
            List<String> types = activeTypes(topic.getId());
            rows.add(new TopicAiExampleCoverageResponse(
                    topic.getId(), topic.getTitleRu(), topic.getTitleKk(), total, active, types));
        }
        return rows;
    }

    // --- внутреннее ---

    /** Типы вопросов, по которым у темы есть хотя бы один активный эталон. */
    private List<String> activeTypes(Long topicId) {
        List<String> types = new ArrayList<>();
        for (QuestionType type : QuestionType.values()) {
            if (examples.existsByTopicIdAndQuestionTypeAndIncludeInAiTrue(topicId, type)) {
                types.add(type.name());
            }
        }
        return types;
    }

    private Topic findTopic(Long topicId) {
        Topic topic = topics.findById(topicId)
                .orElseThrow(() -> new TopicAiExampleException("topic_not_found"));
        if (topic.isDeleted()) {
            throw new TopicAiExampleException("topic_not_found");
        }
        return topic;
    }

    private TopicAiExample findInTopic(Long topicId, Long exampleId) {
        TopicAiExample example = examples.findById(exampleId)
                .orElseThrow(() -> new TopicAiExampleException("example_not_found"));
        if (!example.getTopic().getId().equals(topicId)) {
            throw new TopicAiExampleException("example_topic_mismatch");
        }
        return example;
    }

    /**
     * Валидация ключа ответа под тип. Правила зеркалят QuestionBankService, чтобы эталон
     * был так же корректен, как настоящий вопрос: иначе модель училась бы на «битом» примере.
     */
    private void validate(TopicAiExampleForm form) {
        if (form.getQuestionType() == null) {
            throw new TopicAiExampleException("example_type_required");
        }
        if (isBlank(form.getBodyRu()) || isBlank(form.getBodyKk())) {
            throw new TopicAiExampleException("example_body_required");
        }
        if (form.getDifficulty() < 1 || form.getDifficulty() > 5) {
            throw new TopicAiExampleException("example_difficulty_invalid");
        }
        switch (form.getQuestionType()) {
            case SCQ -> validateChoice(form.getOptions(), true);
            case MCQ -> validateChoice(form.getOptions(), false);
            case MATCHING -> validateMatching(form.getMatchingPairs());
            case FILL_IN -> validateFill(form.getFillAnswers());
        }
    }

    private void validateChoice(List<ChoiceOptionForm> options, boolean exactlyOne) {
        List<ChoiceOptionForm> filled = options.stream()
                .filter(o -> !isBlank(o.getTextRu()) || !isBlank(o.getTextKk()))
                .toList();
        if (filled.size() < 2) {
            throw new TopicAiExampleException("choice_requires_two_options");
        }
        if (filled.stream().anyMatch(o -> isBlank(o.getTextRu()) || isBlank(o.getTextKk()))) {
            throw new TopicAiExampleException("choice_option_text_required");
        }
        long correct = filled.stream().filter(ChoiceOptionForm::isCorrect).count();
        if (exactlyOne && correct != 1) {
            throw new TopicAiExampleException("scq_requires_exactly_one_correct");
        }
        if (!exactlyOne && correct < 1) {
            throw new TopicAiExampleException("mcq_requires_one_correct");
        }
    }

    private void validateMatching(List<MatchingPairForm> pairs) {
        List<MatchingPairForm> filled = pairs.stream()
                .filter(p -> !isBlank(p.getLeftRu()) || !isBlank(p.getRightRu())
                        || !isBlank(p.getLeftKk()) || !isBlank(p.getRightKk()))
                .toList();
        if (filled.size() < 2) {
            throw new TopicAiExampleException("matching_requires_two_pairs");
        }
        if (filled.stream().anyMatch(p -> isBlank(p.getLeftRu()) || isBlank(p.getRightRu())
                || isBlank(p.getLeftKk()) || isBlank(p.getRightKk()))) {
            throw new TopicAiExampleException("matching_pair_required");
        }
    }

    private void validateFill(List<FillAnswerForm> answers) {
        List<FillAnswerForm> filled = answers.stream()
                .filter(a -> !isBlank(a.getPlaceholder()) || !isBlank(a.getAnswer()))
                .toList();
        if (filled.isEmpty()) {
            throw new TopicAiExampleException("fill_in_requires_answer");
        }
        for (FillAnswerForm a : filled) {
            if (isBlank(a.getPlaceholder()) || isBlank(a.getAnswer()) || a.getMatchMode() == null) {
                throw new TopicAiExampleException("fill_in_answer_required");
            }
            if (a.getMatchMode() == FillMatchMode.NUMERIC_TOLERANCE
                    && (a.getTolerance() == null || a.getTolerance().signum() < 0)) {
                throw new TopicAiExampleException("fill_in_tolerance_required");
            }
        }
    }

    /**
     * Сериализует ключ ответа в JSON согласно типу. Хранится ПОЛНАЯ форма (с флагом correct и т.п.),
     * а не сокращённый answer key банка — чтобы эталон можно было и заново редактировать,
     * и показать модели «какой вариант правильный».
     *
     * <p>Для choice-вариантов проставляем метки A, B, C… по порядку, если они не заданы UI —
     * так ключ остаётся читаемым и стабильным.
     */
    private String answerKeyJson(TopicAiExampleForm form) {
        Object value = switch (form.getQuestionType()) {
            case SCQ, MCQ -> normalizedChoiceOptions(form.getOptions());
            case MATCHING -> form.getMatchingPairs().stream()
                    .filter(p -> !isBlank(p.getLeftRu()) || !isBlank(p.getRightRu())
                            || !isBlank(p.getLeftKk()) || !isBlank(p.getRightKk()))
                    .map(p -> new MatchingPairForm(
                            trim(p.getLeftRu()), trim(p.getLeftKk()), trim(p.getRightRu()), trim(p.getRightKk())))
                    .toList();
            case FILL_IN -> form.getFillAnswers().stream()
                    .filter(a -> !isBlank(a.getPlaceholder()) || !isBlank(a.getAnswer()))
                    .map(a -> new FillAnswerForm(
                            trim(a.getPlaceholder()), trim(a.getAnswer()), a.getMatchMode(), a.getTolerance()))
                    .toList();
        };
        return toJson(value);
    }

    private List<ChoiceOptionForm> normalizedChoiceOptions(List<ChoiceOptionForm> options) {
        List<ChoiceOptionForm> filled = options.stream()
                .filter(o -> !isBlank(o.getTextRu()) || !isBlank(o.getTextKk()))
                .toList();
        List<ChoiceOptionForm> result = new ArrayList<>(filled.size());
        for (int i = 0; i < filled.size(); i++) {
            ChoiceOptionForm o = filled.get(i);
            String label = isBlank(o.getLabel()) ? String.valueOf((char) ('A' + i)) : o.getLabel().trim();
            result.add(new ChoiceOptionForm(label, trim(o.getTextRu()), trim(o.getTextKk()), o.isCorrect()));
        }
        return result;
    }

    /**
     * Разбирает {@code answer_key_json} обратно в типизированные списки для UI.
     * Заполняет только список, соответствующий типу; остальные — пустые.
     */
    private TopicAiExampleResponse toResponse(TopicAiExample e) {
        List<ChoiceOptionForm> options = List.of();
        List<MatchingPairForm> pairs = List.of();
        List<FillAnswerForm> fills = List.of();
        switch (e.getQuestionType()) {
            case SCQ, MCQ -> options = fromJson(e.getAnswerKeyJson(), new TypeReference<List<ChoiceOptionForm>>() {
            });
            case MATCHING -> pairs = fromJson(e.getAnswerKeyJson(), new TypeReference<List<MatchingPairForm>>() {
            });
            case FILL_IN -> fills = fromJson(e.getAnswerKeyJson(), new TypeReference<List<FillAnswerForm>>() {
            });
        }
        return new TopicAiExampleResponse(
                e.getId(),
                e.getTopic().getId(),
                e.getQuestionType().name(),
                e.getDifficulty(),
                e.getBodyRu(),
                e.getBodyKk(),
                e.isIncludeInAi(),
                e.getInternalNote(),
                options,
                pairs,
                fills,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new TopicAiExampleException("example_payload_invalid");
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new TopicAiExampleException("example_payload_invalid");
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }

    private static String trimToNull(String v) {
        return isBlank(v) ? null : v.trim();
    }
}
