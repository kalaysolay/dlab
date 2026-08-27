package kz.damulab.ai;

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
    private final AiProviderProperties providerProperties;

    public AdminAiSettingsPageController(
            AiRuntimeSettingsService settings,
            AiProviderProperties providerProperties
    ) {
        this.settings = settings;
        this.providerProperties = providerProperties;
    }

    @GetMapping("/admin/settings/ai")
    String page(Model model) {
        if (!model.containsAttribute("aiSettingsForm")) {
            model.addAttribute("aiSettingsForm", settings.currentForm());
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

    private void addReferenceModel(Model model) {
        model.addAttribute("activeAdminNav", "ai-settings");
        model.addAttribute("providers", AiProviderCode.values());
        model.addAttribute("realProvidersEnabled", providerProperties.isRealProvidersEnabled());
        model.addAttribute("openAiKeyConfigured", isConfigured(providerProperties.getOpenai().getApiKey()));
        model.addAttribute("deepSeekKeyConfigured", isConfigured(providerProperties.getDeepseek().getApiKey()));
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
