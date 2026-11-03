package kz.damulab.speaking;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Страница разговорника внутри текущей PWA; standalone и обычный браузер используют один маршрут. */
@Controller
public class SpeakingPageController {
    private final SpeakingAccess access;
    private final SpeakingCloudProperties cloud;
    private final SpeakingVoiceProperties voice;
    public SpeakingPageController(SpeakingAccess access,SpeakingCloudProperties cloud,SpeakingVoiceProperties voice) { this.access=access;this.cloud=cloud;this.voice=voice; }
    @GetMapping("/student/speaking")
    String page(Model model, HttpServletResponse response) {
        access.student(false);
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("activeStudentNav", "speaking");
        model.addAttribute("speakingCloudEnabled",cloud.isEnabled());
        model.addAttribute("speakingSttProvider",voice.getProvider());
        return "student/speaking";
    }
}
