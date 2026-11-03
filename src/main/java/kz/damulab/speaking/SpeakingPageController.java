package kz.damulab.speaking;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Страница разговорника внутри текущей PWA; standalone и обычный браузер используют один маршрут. */
@Controller
public class SpeakingPageController {
    private final SpeakingAccess access;
    public SpeakingPageController(SpeakingAccess access) { this.access=access; }
    @GetMapping("/student/speaking")
    String page(Model model, HttpServletResponse response) {
        access.student(false);
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("activeStudentNav", "speaking");
        return "student/speaking";
    }
}
