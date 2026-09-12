package kz.damulab.ai;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Страница админки для переключения провайдера и модели без перезапуска сервера. */
@Controller
public class AdminAiSettingsPageController {

    private final AiRuntimeSettingsService settings;
    private final AiTranslationPromptService translationPrompts;
    private final AiProviderProperties providerProperties;

    public AdminAiSettingsPageController(
            AiRuntimeSettingsService settings,
            AiTranslationPromptService translationPrompts,
            AiProviderProperties providerProperties
    ) {
        this.settings = settings;
        this.translationPrompts = translationPrompts;
        this.providerProperties = providerProperties;
    }

    @GetMapping("/admin/settings/ai")
    String page(Model model) {
        if (!model.containsAttribute("aiSettingsForm")) {
            model.addAttribute("aiSettingsForm", settings.currentForm());
        }
        if (!model.containsAttribute("aiTranslationPromptForm")) {
            model.addAttribute("aiTranslationPromptForm", translationPrompts.currentForm());
        }
        addReferenceModel(model);
        return "admin/ai-settings";
    }

    @PostMapping("/admin/settings/ai")
    String update(
            @Valid @ModelAttribute("aiSettingsForm") AiSettingsForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("aiTranslationPromptForm", translationPrompts.currentForm());
            addReferenceModel(model);
            return "admin/ai-settings";
        }
        settings.update(form);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "AI-настройки сохранены и применятся к следующим запросам"
        );
        return "redirect:/admin/settings/ai";
    }

    /** Сохраняет редактируемые промпты отдельно от выбора провайдера и модели. */
    @PostMapping("/admin/settings/ai/prompts")
    String updateTranslationPrompts(
            @Valid @ModelAttribute("aiTranslationPromptForm") AiTranslationPromptForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        translationPrompts.validate(form, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("aiSettingsForm", settings.currentForm());
            addReferenceModel(model);
            return "admin/ai-settings";
        }
        translationPrompts.update(form);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Промпты переводчика сохранены и применятся к следующему запросу"
        );
        return "redirect:/admin/settings/ai";
    }

    private void addReferenceModel(Model model) {
        model.addAttribute("activeAdminNav", "ai-settings");
        model.addAttribute("providers", AiProviderCode.values());
        model.addAttribute(
                "translationProviders",
                List.of(AiProviderCode.DEEPSEEK, AiProviderCode.OPENAI)
        );
        model.addAttribute("realProvidersEnabled", providerProperties.isRealProvidersEnabled());
        model.addAttribute("openAiKeyConfigured", isConfigured(providerProperties.getOpenai().getApiKey()));
        model.addAttribute("deepSeekKeyConfigured", isConfigured(providerProperties.getDeepseek().getApiKey()));
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
