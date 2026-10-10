package kz.damulab.quiz;

import java.util.Locale;
import kz.damulab.quiz.engine.QuizEngineException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

/** Безопасные локализованные отказы SSR: SQL/профили/закрытые снимки не попадают в страницу ошибки. */
@ControllerAdvice(assignableTypes={QuizStudentPageController.class,QuizPageController.class})
public class QuizStudentPageErrors {
    private final MessageSource messages;
    /** Подключает общие API-тексты ошибок для SSR. */
    public QuizStudentPageErrors(MessageSource messages) { this.messages=messages; }
    /** Преобразует ожидаемый отказ в страницу с исправлением профиля или возвратом к каталогу. */
    @ExceptionHandler(QuizEngineException.class)
    public String engine(QuizEngineException ex,Model model,Locale locale,HttpServletResponse response) {
        String code=QuizRoomService.externalCode(ex.getCode());
        response.setStatus(switch(code) { case "PARTICIPANT_REQUIRED","HOST_ONLY_ACTION","GUEST_ONLY_ACTION"->403; case "ROOM_NOT_FOUND","GAME_NOT_FOUND"->404; default->409; });
        model.addAttribute("error",messages.getMessage("quiz.api."+code,null,locale)); return "student/quiz-game-error";
    }
}
