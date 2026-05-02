package kz.damulab.notifications;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/push-notifications")
public class AdminPushNotificationApiController {

    private final PushNotificationService pushNotifications;

    public AdminPushNotificationApiController(PushNotificationService pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    @GetMapping
    List<PushNotificationResponse> notifications(@RequestParam(required = false) PushNotificationStatus status) {
        return pushNotifications.list(status);
    }

    @PostMapping
    ResponseEntity<PushNotificationResponse> create(@Valid @RequestBody PushNotificationForm form) {
        PushNotificationResponse created = pushNotifications.create(form);
        return ResponseEntity.created(URI.create("/api/admin/push-notifications/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    PushNotificationResponse update(@PathVariable Long id, @Valid @RequestBody PushNotificationForm form) {
        return pushNotifications.update(id, form);
    }

    @PostMapping("/{id}/cancel")
    PushNotificationResponse cancel(@PathVariable Long id) {
        return pushNotifications.cancel(id);
    }
}
