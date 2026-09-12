package kz.damulab.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Форма независимых AI-маршрутов на странице настроек админки. */
public class AiSettingsForm {

    @NotNull
    private AiProviderCode questionsProvider;

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]*", message = "Используйте идентификатор модели без пробелов")
    private String questionsModel;

    @NotNull
    private AiProviderCode lecturesProvider;

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]*", message = "Используйте идентификатор модели без пробелов")
    private String lecturesModel;

    @NotNull
    private AiProviderCode translationsProvider;

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]*", message = "Используйте идентификатор модели без пробелов")
    private String translationsModel;

    public AiProviderCode getQuestionsProvider() {
        return questionsProvider;
    }

    public void setQuestionsProvider(AiProviderCode questionsProvider) {
        this.questionsProvider = questionsProvider;
    }

    public String getQuestionsModel() {
        return questionsModel;
    }

    public void setQuestionsModel(String questionsModel) {
        this.questionsModel = questionsModel;
    }

    public AiProviderCode getLecturesProvider() {
        return lecturesProvider;
    }

    public void setLecturesProvider(AiProviderCode lecturesProvider) {
        this.lecturesProvider = lecturesProvider;
    }

    public String getLecturesModel() {
        return lecturesModel;
    }

    public void setLecturesModel(String lecturesModel) {
        this.lecturesModel = lecturesModel;
    }

    public AiProviderCode getTranslationsProvider() {
        return translationsProvider;
    }

    public void setTranslationsProvider(AiProviderCode translationsProvider) {
        this.translationsProvider = translationsProvider;
    }

    public String getTranslationsModel() {
        return translationsModel;
    }

    public void setTranslationsModel(String translationsModel) {
        this.translationsModel = translationsModel;
    }
}
