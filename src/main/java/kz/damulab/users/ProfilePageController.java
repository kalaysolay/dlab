package kz.damulab.users;

import java.net.URI;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfilePageController {

    private final ProfileService profileService;

    public ProfilePageController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/student/profile")
    String studentProfile(Principal principal, Model model) {
        StudentProfileResponse profile = profileService.getStudentProfile(principal.getName());
        if (!model.containsAttribute("studentProfileForm")) {
            model.addAttribute("studentProfileForm", toStudentForm(profile));
        }
        model.addAttribute("profile", profile);
        return "student/profile";
    }

    @PostMapping("/student/profile")
    String updateStudentProfile(
            Principal principal,
            @Valid @ModelAttribute("studentProfileForm") StudentProfileForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("profile", profileService.getStudentProfile(principal.getName()));
            return "student/profile";
        }
        profileService.updateStudentProfile(principal.getName(), form);
        redirectAttributes.addAttribute("saved", "true");
        return "redirect:/student/profile";
    }

    @PostMapping("/student/profile/language")
    String updateStudentLanguage(
            Principal principal,
            String preferredLanguage,
            HttpServletRequest request
    ) {
        profileService.updateStudentLanguage(principal.getName(), preferredLanguage);
        return "redirect:" + safeReturnPath(request.getHeader("Referer"), preferredLanguage);
    }

    @GetMapping("/parent/profile")
    String parentProfile(Principal principal, Model model) {
        ParentProfileResponse profile = profileService.getParentProfile(principal.getName());
        if (!model.containsAttribute("parentProfileForm")) {
            model.addAttribute("parentProfileForm", toParentForm(profile));
        }
        model.addAttribute("profile", profile);
        return "parent/profile";
    }

    @PostMapping("/parent/profile")
    String updateParentProfile(
            Principal principal,
            @Valid @ModelAttribute("parentProfileForm") ParentProfileForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("profile", profileService.getParentProfile(principal.getName()));
            return "parent/profile";
        }
        profileService.updateParentProfile(principal.getName(), form);
        redirectAttributes.addAttribute("saved", "true");
        return "redirect:/parent/profile";
    }

    private StudentProfileForm toStudentForm(StudentProfileResponse profile) {
        StudentProfileForm form = new StudentProfileForm();
        form.setFullName(profile.fullName());
        form.setPhone(profile.phone());
        form.setGradeNo(profile.gradeNo());
        form.setPreferredLanguage(profile.preferredLanguage());
        form.setLessonRemindersEnabled(profile.lessonRemindersEnabled());
        form.setWeeklyParentReportEnabled(profile.weeklyParentReportEnabled());
        form.setSessionResultPushEnabled(profile.sessionResultPushEnabled());
        return form;
    }

    private ParentProfileForm toParentForm(ParentProfileResponse profile) {
        ParentProfileForm form = new ParentProfileForm();
        form.setFullName(profile.fullName());
        form.setPhone(profile.phone());
        return form;
    }

    private String safeReturnPath(String referer, String language) {
        String suffix = "?lang=" + ("kk".equals(language) ? "kk" : "ru");
        if (referer == null || referer.isBlank()) {
            return "/dashboard" + suffix;
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            if (path == null || path.isBlank() || !(path.startsWith("/student") || path.startsWith("/dashboard"))) {
                return "/dashboard" + suffix;
            }
            return path + suffix;
        } catch (IllegalArgumentException ex) {
            return "/dashboard" + suffix;
        }
    }
}
