package kz.damulab.notifications;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.content.ContentGraphService;

@Controller
public class AdminPushNotificationPageController {

    private final PushNotificationService pushNotifications;
    private final ContentGraphService contentGraph;

    public AdminPushNotificationPageController(
            PushNotificationService pushNotifications,
            ContentGraphService contentGraph
    ) {
        this.pushNotifications = pushNotifications;
        this.contentGraph = contentGraph;
    }

    @GetMapping("/admin/push-notifications")
    String pushNotifications(
            @RequestParam(required = false) PushNotificationStatus status,
            Model model
    ) {
        addPageModel(model, status);
        if (!model.containsAttribute("pushForm")) {
            model.addAttribute("pushForm", defaultForm());
        }
        return "admin/push-notifications";
    }

    @PostMapping("/admin/push-notifications")
    String create(
            @Valid @ModelAttribute("pushForm") PushNotificationForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            bindingResult.reject("push", "Проверьте обязательные поля push");
            addPageModel(model, null);
            return "admin/push-notifications";
        }
        try {
            pushNotifications.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Push запланирован");
            return "redirect:/admin/push-notifications";
        } catch (PushNotificationException ex) {
            bindingResult.reject("push", humanError(ex.getCode()));
            addPageModel(model, null);
            return "admin/push-notifications";
        }
    }

    @PostMapping("/admin/push-notifications/{id}")
    String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("pushForm") PushNotificationForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Проверьте поля push");
            return "redirect:/admin/push-notifications";
        }
        try {
            pushNotifications.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Push обновлен");
        } catch (PushNotificationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/push-notifications";
    }

    @PostMapping("/admin/push-notifications/{id}/cancel")
    String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            pushNotifications.cancel(id);
            redirectAttributes.addFlashAttribute("successMessage", "Push отменен");
        } catch (PushNotificationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/push-notifications";
    }

    private void addPageModel(Model model, PushNotificationStatus selectedStatus) {
        var items = pushNotifications.list(selectedStatus);
        model.addAttribute("activeAdminNav", "push");
        model.addAttribute("pushNotifications", items);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("pushStatuses", PushNotificationStatus.values());
        model.addAttribute("targetScreens", PushTargetScreen.values());
        model.addAttribute("subjects", contentGraph.listSubjects());
        model.addAttribute("serverTimeLabel", pushNotifications.serverTimeLabel());
        model.addAttribute("scheduledCount", items.stream().filter(item -> "scheduled".equals(item.status())).count());
        model.addAttribute("sentCount", items.stream().filter(item -> "sent".equals(item.status())).count());
        model.addAttribute("failedCount", items.stream().filter(item -> "failed".equals(item.status())).count());
    }

    private PushNotificationForm defaultForm() {
        PushNotificationForm form = new PushNotificationForm();
        form.setScheduledAt(pushNotifications.defaultScheduledAt());
        form.setTargetScreen(PushTargetScreen.QUIZ_CREATE_ROOM);
        return form;
    }

    Long subjectId(Map<String, Object> payload) {
        Object value = payload.get("subject_id");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.valueOf(string);
        }
        return null;
    }

    private String humanError(String code) {
        return switch (code) {
            case "push_text_required" -> "Текст push обязателен";
            case "push_text_too_long" -> "Текст push не должен быть длиннее 120 символов";
            case "push_scheduled_at_required" -> "Укажите дату и время отправки";
            case "push_scheduled_at_invalid" -> "Используйте формат YYYY-MM-DD HH:mm";
            case "push_scheduled_at_past" -> "Нельзя запланировать push в прошлое";
            case "push_target_screen_required" -> "Выберите целевой экран";
            case "push_subject_required" -> "Для subject_test выберите предмет";
            case "subject_not_found" -> "Предмет не найден";
            case "push_not_editable" -> "Этот push уже нельзя редактировать или отменять";
            default -> "Push не сохранен: " + code;
        };
    }
}
