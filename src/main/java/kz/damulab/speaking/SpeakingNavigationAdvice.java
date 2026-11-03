package kz.damulab.speaking;

import kz.damulab.web.PageController;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Кабинет показывает новый пункт только допущенному ученику при включённом модуле. */
@ControllerAdvice(assignableTypes=PageController.class)
public class SpeakingNavigationAdvice {
    private final SpeakingAccess access;
    public SpeakingNavigationAdvice(SpeakingAccess access) { this.access=access; }
    @ModelAttribute("speakingAvailable")
    boolean available() { return access.available(); }
}
