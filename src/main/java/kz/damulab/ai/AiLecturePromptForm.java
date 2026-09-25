package kz.damulab.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Редактируемая активная версия промпта генерации полноразмерной лекции. */
public class AiLecturePromptForm {

    @NotBlank
    @Size(max = 30000)
    private String systemPrompt;

    @NotBlank
    @Size(max = 30000)
    private String userPromptTemplate;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPromptTemplate() {
        return userPromptTemplate;
    }

    public void setUserPromptTemplate(String userPromptTemplate) {
        this.userPromptTemplate = userPromptTemplate;
    }
}
