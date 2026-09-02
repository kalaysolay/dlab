package kz.damulab.auth;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Делает флаг Google OAuth доступным всем Thymeleaf-страницам без дублирования контроллеров. */
@ControllerAdvice
public class GoogleOAuthViewAdvice {

    private final GoogleOAuthAvailability availability;

    public GoogleOAuthViewAdvice(GoogleOAuthAvailability availability) {
        this.availability = availability;
    }

    @ModelAttribute("googleOAuthEnabled")
    public boolean googleOAuthEnabled() {
        return availability.isEnabled();
    }
}
