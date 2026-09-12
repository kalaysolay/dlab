package kz.damulab.translator;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Отдаёт мобильную страницу переводчика внутри ученической навигации. */
@Controller
public class TranslationPageController {

    /** Открывает переводчик; доступ STUDENT ограничивается в {@code SecurityConfig}. */
    @GetMapping("/student/translator")
    String page(Model model) {
        model.addAttribute("activeStudentNav", "translator");
        return "student/translator";
    }
}
