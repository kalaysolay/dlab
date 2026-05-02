package kz.damulab.parentlink;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.analytics.AnalyticsService;

@Controller
public class ParentLinkPageController {

    private final ParentLinkService parentLinkService;
    private final AnalyticsService analyticsService;

    public ParentLinkPageController(ParentLinkService parentLinkService, AnalyticsService analyticsService) {
        this.parentLinkService = parentLinkService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/parent")
    String dashboard(Principal principal, Model model) {
        populateDashboard(principal, model);
        return "parent/dashboard";
    }

    @PostMapping("/parent/children")
    String createChild(
            Principal principal,
            @Valid @ModelAttribute("createChildForm") CreateChildForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateDashboard(principal, model);
            return "parent/dashboard";
        }
        try {
            ChildResponse child = parentLinkService.createChild(principal.getName(), form);
            redirectAttributes.addAttribute("childCreated", child.studentId());
            return "redirect:/parent";
        } catch (ParentLinkException exception) {
            bindingResult.reject("child.create.failed", "Не удалось создать ребенка: email уже используется.");
            populateDashboard(principal, model);
            return "parent/dashboard";
        }
    }

    @PostMapping("/parent/link-codes/attach")
    String attachChild(
            Principal principal,
            @Valid @ModelAttribute("attachChildForm") AttachChildForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateDashboard(principal, model);
            return "parent/dashboard";
        }
        try {
            ChildResponse child = parentLinkService.attachChildByCode(principal.getName(), form.getCode());
            redirectAttributes.addAttribute("childAttached", child.studentId());
            return "redirect:/parent";
        } catch (ParentLinkException exception) {
            bindingResult.reject("child.attach.failed", "Код не найден, истек или уже использован.");
            populateDashboard(principal, model);
            return "parent/dashboard";
        }
    }

    @GetMapping("/parent/children/{studentId}")
    String childDetails(Principal principal, @PathVariable Long studentId, Model model) {
        model.addAttribute("child", parentLinkService.getChild(principal.getName(), studentId));
        model.addAttribute("analytics", analyticsService.accessibleStudentSummary(principal, studentId));
        return "parent/child-details";
    }

    @PostMapping("/parent/children/{studentId}/link-code")
    String createOwnedChildLinkCode(
            Principal principal,
            @PathVariable Long studentId,
            RedirectAttributes redirectAttributes
    ) {
        LinkCodeResponse code = parentLinkService.createOwnedChildLinkCode(principal.getName(), studentId);
        redirectAttributes.addFlashAttribute("linkCode", code);
        return "redirect:/parent/children/" + studentId;
    }

    private void populateDashboard(Principal principal, Model model) {
        model.addAttribute("children", parentLinkService.listChildren(principal.getName()));
        if (!model.containsAttribute("createChildForm")) {
            model.addAttribute("createChildForm", new CreateChildForm());
        }
        if (!model.containsAttribute("attachChildForm")) {
            model.addAttribute("attachChildForm", new AttachChildForm());
        }
    }
}
