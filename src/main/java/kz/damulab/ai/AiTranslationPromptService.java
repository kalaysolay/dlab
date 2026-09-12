package kz.damulab.ai;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import kz.damulab.audit.AdminContentAuditService;

/**
 * Загружает промпты переводчика из БД перед каждым вызовом. Кэша намеренно нет:
 * сохранённая в админке версия применяется уже к следующему запросу ученика.
 */
@Service
public class AiTranslationPromptService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final Set<String> TRANSLATION_PLACEHOLDERS = Set.of(
            "sourceLanguage", "targetLanguage", "textJson"
    );
    private static final Set<String> EXPLANATION_PLACEHOLDERS = Set.of(
            "sourceLanguage", "targetLanguage", "explanationLanguage",
            "sourceTextJson", "translatedTextJson"
    );

    private final AiTranslationPromptRepository prompts;
    private final ObjectMapper objectMapper;
    private final AdminContentAuditService audit;

    public AiTranslationPromptService(
            AiTranslationPromptRepository prompts,
            ObjectMapper objectMapper,
            AdminContentAuditService audit
    ) {
        this.prompts = prompts;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    /** Формирует промпт перевода, не позволяя тексту пользователя стать частью шаблона. */
    @Transactional(readOnly = true)
    public AiRenderedPrompt renderTranslation(AiTranslationRequest request) {
        AiTranslationPrompt prompt = requirePrompt(AiTranslationPromptCode.TRANSLATE);
        String userPrompt = renderTemplate(prompt.getUserPromptTemplate(), Map.of(
                "sourceLanguage", request.sourceLanguage(),
                "targetLanguage", request.targetLanguage(),
                "textJson", jsonString(request.text())
        ), TRANSLATION_PLACEHOLDERS);
        return new AiRenderedPrompt(prompt.getSystemPrompt(), userPrompt);
    }

    /** Формирует учебный разбор из актуального исходника и уже показанного перевода. */
    @Transactional(readOnly = true)
    public AiRenderedPrompt renderExplanation(AiTranslationExplanationRequest request) {
        AiTranslationPrompt prompt = requirePrompt(AiTranslationPromptCode.EXPLAIN);
        String userPrompt = renderTemplate(prompt.getUserPromptTemplate(), Map.of(
                "sourceLanguage", request.sourceLanguage(),
                "targetLanguage", request.targetLanguage(),
                "explanationLanguage", request.explanationLanguage(),
                "sourceTextJson", jsonString(request.sourceText()),
                "translatedTextJson", jsonString(request.translatedText())
        ), EXPLANATION_PLACEHOLDERS);
        return new AiRenderedPrompt(prompt.getSystemPrompt(), userPrompt);
    }

    /** Собирает обе строки БД в форму администрирования. */
    @Transactional(readOnly = true)
    public AiTranslationPromptForm currentForm() {
        AiTranslationPrompt translation = requirePrompt(AiTranslationPromptCode.TRANSLATE);
        AiTranslationPrompt explanation = requirePrompt(AiTranslationPromptCode.EXPLAIN);
        AiTranslationPromptForm form = new AiTranslationPromptForm();
        form.setTranslationSystemPrompt(translation.getSystemPrompt());
        form.setTranslationUserPromptTemplate(translation.getUserPromptTemplate());
        form.setExplanationSystemPrompt(explanation.getSystemPrompt());
        form.setExplanationUserPromptTemplate(explanation.getUserPromptTemplate());
        return form;
    }

    /** Добавляет понятные ошибки формы, если потеряна или придумана переменная шаблона. */
    public void validate(AiTranslationPromptForm form, Errors errors) {
        validateTemplateField(
                "translationUserPromptTemplate",
                form.getTranslationUserPromptTemplate(),
                TRANSLATION_PLACEHOLDERS,
                errors
        );
        validateTemplateField(
                "explanationUserPromptTemplate",
                form.getExplanationUserPromptTemplate(),
                EXPLANATION_PLACEHOLDERS,
                errors
        );
    }

    /** Сохраняет оба промпта атомарно; текст ученических запросов здесь не участвует. */
    @Transactional
    public void update(AiTranslationPromptForm form) {
        // Повторная серверная проверка защищает вызовы сервиса в обход MVC-валидации.
        assertTemplate(form.getTranslationUserPromptTemplate(), TRANSLATION_PLACEHOLDERS);
        assertTemplate(form.getExplanationUserPromptTemplate(), EXPLANATION_PLACEHOLDERS);
        String actor = currentActor();
        updateOne(
                AiTranslationPromptCode.TRANSLATE,
                form.getTranslationSystemPrompt(),
                form.getTranslationUserPromptTemplate(),
                actor
        );
        updateOne(
                AiTranslationPromptCode.EXPLAIN,
                form.getExplanationSystemPrompt(),
                form.getExplanationUserPromptTemplate(),
                actor
        );
    }

    private void updateOne(
            AiTranslationPromptCode code,
            String systemPrompt,
            String userPromptTemplate,
            String actor
    ) {
        AiTranslationPrompt prompt = requirePrompt(code);
        prompt.update(systemPrompt, userPromptTemplate, actor);
        prompts.save(prompt);
        audit.record(
                "ai_translation_prompt_updated",
                "AiTranslationPrompt",
                (long) code.ordinal() + 1,
                code + ":systemLen=" + systemPrompt.length() + ":userLen=" + userPromptTemplate.length()
        );
    }

    private AiTranslationPrompt requirePrompt(AiTranslationPromptCode code) {
        return prompts.findById(code)
                .orElseThrow(() -> new IllegalStateException("AI translation prompt is missing: " + code));
    }

    private String renderTemplate(String template, Map<String, String> values, Set<String> required) {
        assertTemplate(template, required);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length() + 128);
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("Unknown AI prompt placeholder: " + matcher.group());
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void validateTemplateField(String field, String template, Set<String> required, Errors errors) {
        if (template == null || template.isBlank()) {
            return;
        }
        Set<String> actual = templatePlaceholders(template);
        Set<String> missing = required.stream()
                .filter(value -> !actual.contains(value))
                .collect(Collectors.toSet());
        Set<String> unknown = actual.stream()
                .filter(value -> !required.contains(value))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            errors.rejectValue(field, "prompt.placeholders.missing", "Добавьте переменные: " + formatPlaceholders(missing));
        } else if (!unknown.isEmpty()) {
            errors.rejectValue(field, "prompt.placeholders.unknown", "Неизвестные переменные: " + formatPlaceholders(unknown));
        }
    }

    private void assertTemplate(String template, Set<String> required) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("AI prompt template must not be blank");
        }
        Set<String> actual = templatePlaceholders(template);
        if (!actual.equals(required)) {
            throw new IllegalArgumentException(
                    "AI prompt placeholders must be exactly: " + formatPlaceholders(required)
            );
        }
    }

    private Set<String> templatePlaceholders(String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        HashSet<String> result = new HashSet<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private String formatPlaceholders(Set<String> placeholders) {
        return placeholders.stream()
                .sorted()
                .map(value -> "{" + value + "}")
                .collect(Collectors.joining(", "));
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize translation input", ex);
        }
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName();
    }
}
