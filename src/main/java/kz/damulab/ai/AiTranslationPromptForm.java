package kz.damulab.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Форма двух промптов переводчика на странице AI-настроек. */
public class AiTranslationPromptForm {

    @NotBlank
    @Size(max = 20_000)
    private String translationSystemPrompt;

    @NotBlank
    @Size(max = 20_000)
    private String translationUserPromptTemplate;

    @NotBlank
    @Size(max = 20_000)
    private String explanationSystemPrompt;

    @NotBlank
    @Size(max = 20_000)
    private String explanationUserPromptTemplate;

    public String getTranslationSystemPrompt() {
        return translationSystemPrompt;
    }

    public void setTranslationSystemPrompt(String translationSystemPrompt) {
        this.translationSystemPrompt = translationSystemPrompt;
    }

    public String getTranslationUserPromptTemplate() {
        return translationUserPromptTemplate;
    }

    public void setTranslationUserPromptTemplate(String translationUserPromptTemplate) {
        this.translationUserPromptTemplate = translationUserPromptTemplate;
    }

    public String getExplanationSystemPrompt() {
        return explanationSystemPrompt;
    }

    public void setExplanationSystemPrompt(String explanationSystemPrompt) {
        this.explanationSystemPrompt = explanationSystemPrompt;
    }

    public String getExplanationUserPromptTemplate() {
        return explanationUserPromptTemplate;
    }

    public void setExplanationUserPromptTemplate(String explanationUserPromptTemplate) {
        this.explanationUserPromptTemplate = explanationUserPromptTemplate;
    }
}
