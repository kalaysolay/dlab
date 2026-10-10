package kz.damulab.quiz;

import java.security.Principal;
import java.util.*;
import kz.damulab.config.DamulabQuizProperties;
import kz.damulab.content.GradeRepository;
import kz.damulab.quiz.catalog.*;
import kz.damulab.quiz.engine.*;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** SSR-оболочки нового UI. Все игровые данные читаются через безопасный API; флаг ограничивает только новые входы. */
@Controller
public class QuizStudentPageController {
    private final QuizStudentCatalog catalog;
    private final QuizRoomService rooms;
    private final GradeRepository grades;
    private final DamulabQuizProperties properties;
    private final MessageSource messages;
    /** Подключает каталог, безопасные комнаты и существующий флаг без его включения. */
    public QuizStudentPageController(QuizStudentCatalog catalog,QuizRoomService rooms,
            GradeRepository grades,DamulabQuizProperties properties,MessageSource messages) {
        this.catalog=catalog; this.rooms=rooms;
        this.grades=grades; this.properties=properties; this.messages=messages;
    }
    /** Показывает готовые карточки при явном флаге; старые комнаты доступны независимо от него. */
    @GetMapping("/student/quiz/games")
    public String hub(Principal principal,Model model,Locale locale) {
        requireCatalog(); labels(model,locale); model.addAttribute("cards",catalog.read(principal.getName(),locale.getLanguage()));
        return "student/quiz-games";
    }
    /** POST карточки закрепляет ключ формы; никакого create через GET. */
    @PostMapping("/student/quiz/games/rooms")
    public String create(Principal principal,@RequestParam String gameCode,@RequestParam String idempotencyKey,
            @RequestParam(required=false) Long gradeId,Model model,Locale locale) {
        requireCatalog();
        try {
            var room=rooms.create(principal.getName(),new QuizGameRoomRequest.Create(gameCode,gradeId,null,null,idempotencyKey));
            return "redirect:/student/quiz/rooms/"+room.code();
        } catch (QuizEngineException ex) {
            hub(principal,model,locale); model.addAttribute("error",error(ex,locale)); return "student/quiz-games";
        }
    }
    /** Форма preview не присоединяет ученика и не создаёт комнату. */
    @GetMapping("/student/quiz/join")
    public String join(@RequestParam(required=false) String code,Model model,Locale locale) {
        labels(model,locale); model.addAttribute("quizPageTitle",messages.getMessage("quiz.ui.join",null,locale));
        model.addAttribute("joinCode",code==null?"":code); return "student/quiz-game-join";
    }
    /** Рендерит только safe shell после проверки membership; ACTIVE/терминальная история не зависит от флага. */
    public String room(Principal principal,String code,Model model,Locale locale) {
        var room=rooms.state(code,principal.getName()); labels(model,locale); model.addAttribute("room",room);
        model.addAttribute("quizPageTitle",room.gameTitle());
        // Разрешённые классы WAITING закреплены в версии комнаты, не в новой публикации игры.
        model.addAttribute("gradeChoices",roomGrades(code,principal.getName(),locale));
        var replay=room.status().equals("finished") && properties.isGameCatalogEnabled()
                ? catalog.read(principal.getName(),locale.getLanguage()).stream().filter(c->c.code().equals(room.gameCode())).findFirst().orElse(null) : null;
        model.addAttribute("replayCard",replay);
        return "student/quiz-game-room";
    }
    private List<QuizStudentCatalog.Choice> roomGrades(String code,String email,Locale locale) {
        var allowed=rooms.allowedGradeIds(code,email);
        return grades.findAllByOrderByGradeNoAsc().stream().filter(g->allowed.contains(g.getId()))
                .map(g->new QuizStudentCatalog.Choice(g.getId(),QuizSnapshotJson.localized(g.getTitleRu(),g.getTitleKk(),locale.getLanguage()),null)).toList();
    }
    /** Добавляет только UI-подписи JSON-safe через Thymeleaf inlining; сообщения не становятся HTML. */
    public void labels(Model model,Locale locale) {
        Map<String,String> labels=new LinkedHashMap<>();
        for (String key:List.of("catalog","intro","join","play","rounds","seconds","players","chooseGrade","profile","empty","back","host","you","ready","waiting","start","invite","copy","copied","cancel","leave","save","grade","round","submit","skip","sending","accepted","uncertain","next","timeout","remaining","threshold","connection","connected","reconnecting","cancelled","expired","results","score","place","percent","ranking","review","yourAnswer","correctAnswer","correct","incorrect","skipped","timedOut","time","timeRule","again","replayUnavailable","noAnswer","true","false","select","multiple","matching","fill","preview","organizer","enter","loading","requestError","source","rules","allReady","code","confirmGrade"))
            labels.put(key,messages.getMessage("quiz.ui."+key,null,locale));
        for (QuizGameType type:QuizGameType.values()) labels.put(type.name(),messages.getMessage("quiz.ui."+type.name(),null,locale));
        for (String key:List.of("PARTICIPANT_NOT_READY","GAME_CONFIGURATION_INVALID","INSUFFICIENT_PUBLISHED_CONTENT","INVALID_GRADE","PROFILE_GRADE_REQUIRED","ROOM_EXPIRED","ROOM_CANCELLED","ROOM_ALREADY_STARTED","ROOM_FULL","ROOM_CODE_INVALID","ANSWER_INVALID"))
            labels.put(key,messages.getMessage("quiz.api."+key,null,locale));
        labels.put("secondUnit",messages.getMessage("quiz.ui.secondUnit",null,locale));
        model.addAttribute("quizLabels",labels); model.addAttribute("activeStudentNav","quiz");
        model.addAttribute("quizPageTitle",labels.get("catalog"));
    }
    private String error(QuizEngineException ex,Locale locale) { return messages.getMessage("quiz.api."+QuizRoomService.externalCode(ex.getCode()),null,locale); }
    private void requireCatalog() { if (!properties.isGameCatalogEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
