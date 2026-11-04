package kz.damulab.lectures;

import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Административный API Мастерской: обычные ROLE_ADMIN и CSRF действуют на все операции. */
@RestController
@RequestMapping("/api/admin/lectures/studio")
public class LectureStudioApiController {
    private final LectureStudioSyncService service;
    public LectureStudioApiController(LectureStudioSyncService service) { this.service = service; }

    @GetMapping("/capabilities")
    Map<String, Object> capabilities() { return service.capabilities(); }

    @GetMapping("/lookup")
    LectureStudioSyncService.SyncResponse lookup(@RequestParam String externalId) { return service.lookup(externalId); }

    @PostMapping("/sync")
    LectureStudioSyncService.SyncResponse sync(@Valid @RequestBody LectureStudioSyncService.SyncRequest request) {
        return service.sync(request);
    }

    @PostMapping("/publish")
    LectureStudioSyncService.SyncResponse publish(@Valid @RequestBody LectureStudioSyncService.PublishRequest request) {
        return service.publish(request);
    }
}
