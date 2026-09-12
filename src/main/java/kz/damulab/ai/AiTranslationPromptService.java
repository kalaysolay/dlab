package kz.damulab.ai;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

/**
 * Тонкий адаптер переводчика к универсальному {@link AiPromptService}: знает
 * только DTO фичи, JSON-сериализацию данных и имена её переменных.
 */
@Service
public class AiTranslationPromptService {

    private final AiPromptService prompts;
    private final ObjectMapper objectMapper;

    public AiTranslationPromptService(AiPromptService prompts, ObjectMapper objectMapper) {
        this.prompts = prompts;
        this.objectMapper = objectMapper;
    }

    /** Формирует промпт перевода, не позволяя тексту пользователя стать частью шаблона. */
    public AiRenderedPrompt renderTranslation(AiTranslationRequest request) {
        return prompts.render(AiPromptCode.TRANSLATION_TRANSLATE, Map.of(
                "sourceLanguage", request.sourceLanguage(),
                "targetLanguage", request.targetLanguage(),
                "textJson", jsonString(request.text())
        ));
    }

    /** Формирует учебный разбор из актуального исходника и уже показанного перевода. */
    public AiRenderedPrompt renderExplanation(AiTranslationExplanationRequest request) {
        return prompts.render(AiPromptCode.TRANSLATION_EXPLAIN, Map.of(
                "sourceLanguage", request.sourceLanguage(),
                "targetLanguage", request.targetLanguage(),
                "explanationLanguage", request.explanationLanguage(),
                "sourceTextJson", jsonString(request.sourceText()),
                "translatedTextJson", jsonString(request.translatedText()),
                "explanationMode", request.explanationMode().name()
        ));
    }

    /** Собирает активные версии общих промптов в форму переводчика. */
    public AiTranslationPromptForm currentForm() {
        AiPromptSnapshot translation = prompts.current(AiPromptCode.TRANSLATION_TRANSLATE);
        AiPromptSnapshot explanation = prompts.current(AiPromptCode.TRANSLATION_EXPLAIN);
        AiTranslationPromptForm form = new AiTranslationPromptForm();
        form.setTranslationSystemPrompt(translation.systemTemplate());
        form.setTranslationUserPromptTemplate(translation.userTemplate());
        form.setExplanationSystemPrompt(explanation.systemTemplate());
        form.setExplanationUserPromptTemplate(explanation.userTemplate());
        return form;
    }

    /** Делегирует проверку переменных единому контракту каждого prompt code. */
    public void validate(AiTranslationPromptForm form, Errors errors) {
        prompts.validateTemplate(
                AiPromptCode.TRANSLATION_TRANSLATE,
                "translationUserPromptTemplate",
                form.getTranslationUserPromptTemplate(),
                errors
        );
        prompts.validateTemplate(
                AiPromptCode.TRANSLATION_EXPLAIN,
                "explanationUserPromptTemplate",
                form.getExplanationUserPromptTemplate(),
                errors
        );
    }

    /** Сохраняет новые версии обоих промптов в одной транзакции. */
    @Transactional
    public void update(AiTranslationPromptForm form) {
        prompts.update(
                AiPromptCode.TRANSLATION_TRANSLATE,
                form.getTranslationSystemPrompt(),
                form.getTranslationUserPromptTemplate()
        );
        prompts.update(
                AiPromptCode.TRANSLATION_EXPLAIN,
                form.getExplanationSystemPrompt(),
                form.getExplanationUserPromptTemplate()
        );
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize translation input", ex);
        }
    }
}
