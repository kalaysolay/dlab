package kz.damulab.speaking;

import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;

/** Операционная сводка только ADMIN: агрегаты модуля, без данных отдельных учеников и секретов. */
@RestController
class SpeakingOperationsController {
    private final SpeakingBudgetService budget;private final SpeakingMetrics metrics;
    SpeakingOperationsController(SpeakingBudgetService budget,SpeakingMetrics metrics){this.budget=budget;this.metrics=metrics;}
    @GetMapping("/api/admin/speaking/operations")
    ResponseEntity<Map<String,Object>> get(){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("budget",budget.report(),"processMetrics",metrics.snapshot()));
    }
}
