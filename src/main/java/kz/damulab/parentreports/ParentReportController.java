package kz.damulab.parentreports;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalTime;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

/** Минимальный интерфейс в кабинете и единственный публичный webhook, защищённый секретом. */
@Controller
public class ParentReportController {
    private final ParentTelegramService service;
    public ParentReportController(ParentTelegramService service) {this.service=service;}

    /** Модель доступна сразу в кабинете и профиле: отдельного экрана настроек нет. */
    @ControllerAdvice(basePackages={"kz.damulab.parentlink","kz.damulab.users"})
    public static class ReportModel {
        private final ParentTelegramService service;
        public ReportModel(ParentTelegramService service) {this.service=service;}
        @ModelAttribute
        public void model(Principal principal,Model model, jakarta.servlet.http.HttpServletRequest request) {
            if(principal!=null && (request.getRequestURI().equals("/parent") || request.getRequestURI().startsWith("/parent/profile"))) {
                model.addAttribute("telegramAvailable",service.config.available());
                model.addAttribute("telegram",service.state(principal.getName()));
            }
        }
    }
    @PostMapping("/parent/telegram/connect")
    public RedirectView connect(Principal principal) {
        return new RedirectView(service.connect(principal.getName(),LocaleContextHolder.getLocale().getLanguage()));
    }
    @PostMapping("/parent/telegram/settings")
    public String settings(Principal principal,@RequestParam(defaultValue="false") boolean enabled,@RequestParam int day,@RequestParam LocalTime time) {
        service.settings(principal.getName(),enabled,day,time);return "redirect:/parent?reportSaved";
    }
    @PostMapping("/api/telegram/webhook")
    @ResponseBody
    public ResponseEntity<Void> webhook(@RequestHeader(value="X-Telegram-Bot-Api-Secret-Token",required=false) String secret,@RequestBody JsonNode update) {
        if(!service.config.available() || service.config.polling() || secret==null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),service.config.webhookSecret().getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(403).build();
        service.update(update);return ResponseEntity.ok().build();
    }
    @GetMapping("/parent/reports/{id}")
    public String report(Principal principal,@PathVariable long id,Model model) {
        var rows=service.db.queryForList("select d.full_text from parent_report_deliveries d join parent_student_links l on l.id=d.link_id where d.id=? and d.parent_id=? and l.parent_profile_id=d.parent_id and l.student_profile_id=d.student_id",id,service.parent(principal.getName()));
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("reportText",rows.getFirst().get("full_text"));return "parent/weekly-report";
    }
}
