package kz.damulab.notifications;

import jakarta.validation.Valid;

import kz.damulab.content.ContentGraphService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер HTML-страниц управления кампаниями в админке.
 * Маршруты: /admin/push-campaigns
 *
 * Для REST-операций (JS-фронтенд) используется AdminPushCampaignApiController.
 */
@Controller
public class AdminPushCampaignPageController {

    private final PushCampaignService campaignService;
    private final ContentGraphService contentGraph;

    public AdminPushCampaignPageController(
            PushCampaignService campaignService,
            ContentGraphService contentGraph
    ) {
        this.campaignService = campaignService;
        this.contentGraph = contentGraph;
    }

    @GetMapping("/admin/push-campaigns")
    String campaigns(Model model) {
        addPageModel(model);
        if (!model.containsAttribute("campaignForm")) {
            model.addAttribute("campaignForm", defaultForm());
        }
        return "admin/push-campaigns";
    }

    @PostMapping("/admin/push-campaigns")
    String create(
            @Valid @ModelAttribute("campaignForm") PushCampaignForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            bindingResult.reject("campaign", "Проверьте обязательные поля кампании");
            addPageModel(model);
            return "admin/push-campaigns";
        }
        try {
            campaignService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Кампания создана");
            return "redirect:/admin/push-campaigns";
        } catch (PushCampaignException ex) {
            bindingResult.reject("campaign", humanError(ex.getCode()));
            addPageModel(model);
            return "admin/push-campaigns";
        }
    }

    @PostMapping("/admin/push-campaigns/{id}")
    String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("campaignForm") PushCampaignForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Проверьте поля кампании");
            return "redirect:/admin/push-campaigns";
        }
        try {
            campaignService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Кампания обновлена");
        } catch (PushCampaignException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/push-campaigns";
    }

    @PostMapping("/admin/push-campaigns/{id}/toggle")
    String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            PushCampaignResponse updated = campaignService.toggleEnabled(id);
            String msg = updated.enabled() ? "Кампания включена" : "Кампания выключена";
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (PushCampaignException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/push-campaigns";
    }

    @PostMapping("/admin/push-campaigns/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            campaignService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Кампания удалена");
        } catch (PushCampaignException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/push-campaigns";
    }

    private void addPageModel(Model model) {
        model.addAttribute("activeAdminNav", "push-campaigns");
        model.addAttribute("campaigns", campaignService.list());
        model.addAttribute("targetScreens", PushTargetScreen.values());
        model.addAttribute("subjects", contentGraph.listSubjects());
    }

    private PushCampaignForm defaultForm() {
        PushCampaignForm form = new PushCampaignForm();
        form.setSendTime("11:00");
        form.setDaysOfWeek("ALL");
        form.setTargetScreen(PushTargetScreen.QUIZ_CREATE_ROOM);
        return form;
    }

    private String humanError(String code) {
        return switch (code) {
            case "campaign_name_required" -> "Название кампании обязательно";
            case "campaign_body_required" -> "Текст кампании обязателен";
            case "campaign_body_too_long" -> "Текст не должен превышать 500 символов";
            case "campaign_target_screen_required" -> "Выберите целевой экран";
            case "campaign_send_time_required" -> "Укажите время отправки";
            case "campaign_send_time_invalid" -> "Используйте формат HH:mm (например 11:00)";
            case "campaign_subject_required" -> "Для subject_test выберите предмет";
            case "subject_not_found" -> "Предмет не найден";
            case "campaign_not_found" -> "Кампания не найдена";
            default -> "Ошибка: " + code;
        };
    }
}
