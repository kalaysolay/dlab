package kz.damulab.ai;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.audit.AdminContentAuditService;

/**
 * Читает и изменяет runtime-настройки AI. Маршрут загружается из БД перед каждым
 * вызовом, поэтому смена провайдера или модели применяется без перезапуска приложения.
 */
@Service
public class AiRuntimeSettingsService {

    private final AiRuntimeSettingRepository settings;
    private final AdminContentAuditService audit;

    public AiRuntimeSettingsService(AiRuntimeSettingRepository settings, AdminContentAuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    /** Возвращает снимок маршрута; отсутствие обязательной строки считается ошибкой миграции. */
    @Transactional(readOnly = true)
    public AiRuntimeSelection resolve(AiUsageType usageType) {
        AiRuntimeSetting setting = settings.findById(usageType)
                .orElseThrow(() -> new IllegalStateException("AI setting is missing: " + usageType));
        return new AiRuntimeSelection(setting.getProvider(), setting.getModelName());
    }

    /** Собирает все строки БД в форму редактирования. */
    @Transactional(readOnly = true)
    public AiSettingsForm currentForm() {
        AiRuntimeSelection questions = resolve(AiUsageType.QUESTIONS);
        AiRuntimeSelection lectures = resolve(AiUsageType.LECTURES);
        AiRuntimeSelection translations = resolve(AiUsageType.TRANSLATIONS);
        AiSettingsForm form = new AiSettingsForm();
        form.setQuestionsProvider(questions.provider());
        form.setQuestionsModel(questions.model());
        form.setLecturesProvider(lectures.provider());
        form.setLecturesModel(lectures.model());
        form.setTranslationsProvider(translations.provider());
        form.setTranslationsModel(translations.model());
        return form;
    }

    /**
     * Сохраняет все маршруты в одной транзакции. Валидация формы не допускает
     * частичного изменения настроек.
     */
    @Transactional
    public void update(AiSettingsForm form) {
        String actor = currentActor();
        updateOne(AiUsageType.QUESTIONS, form.getQuestionsProvider(), form.getQuestionsModel(), actor);
        updateOne(AiUsageType.LECTURES, form.getLecturesProvider(), form.getLecturesModel(), actor);
        updateOne(AiUsageType.TRANSLATIONS, form.getTranslationsProvider(), form.getTranslationsModel(), actor);
    }

    private void updateOne(
            AiUsageType usageType,
            AiProviderCode provider,
            String model,
            String actor
    ) {
        AiRuntimeSetting setting = settings.findById(usageType)
                .orElseGet(() -> new AiRuntimeSetting(usageType, provider, model, actor));
        setting.update(provider, model, actor);
        settings.save(setting);
        audit.record(
                "ai_runtime_setting_updated",
                "AiRuntimeSetting",
                (long) usageType.ordinal() + 1,
                usageType + ":" + provider + ":" + model.trim()
        );
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName();
    }
}
