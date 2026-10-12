package kz.damulab.quiz;

import java.security.Principal;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Общие адреса комнат и результатов обслуживает единый движок, включая чтение сохранённой истории. */
@Controller
public class QuizPageController {
    private final QuizStudentPageController pages;
    private final QuizRoomService rooms;

    public QuizPageController(QuizStudentPageController pages, QuizRoomService rooms) {
        this.pages = pages; this.rooms = rooms;
    }

    @GetMapping("/student/quiz/rooms/{code}")
    String room(Principal principal, @PathVariable String code, Model model, Locale locale) {
        return pages.room(principal, code, model, locale);
    }

    @GetMapping("/student/quiz/rooms/{code}/results")
    String results(Principal principal, @PathVariable String code, Model model, Locale locale) {
        rooms.results(code, principal.getName());
        return pages.room(principal, code, model, locale);
    }
}
