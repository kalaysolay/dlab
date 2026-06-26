package kz.damulab.notifications;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API для управления push-кампаниями.
 * Доступен только ADMIN (см. SecurityConfig).
 *
 * Endpoints:
 *   GET    /api/admin/push-campaigns            — список кампаний с последними запусками
 *   GET    /api/admin/push-campaigns/{id}       — одна кампания
 *   POST   /api/admin/push-campaigns            — создать
 *   PATCH  /api/admin/push-campaigns/{id}       — обновить
 *   POST   /api/admin/push-campaigns/{id}/toggle — включить/выключить
 *   DELETE /api/admin/push-campaigns/{id}       — удалить
 */
@RestController
@RequestMapping("/api/admin/push-campaigns")
public class AdminPushCampaignApiController {

    private final PushCampaignService campaignService;

    public AdminPushCampaignApiController(PushCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    List<PushCampaignResponse> list() {
        return campaignService.list();
    }

    @GetMapping("/{id}")
    PushCampaignResponse get(@PathVariable Long id) {
        return campaignService.get(id);
    }

    @PostMapping
    ResponseEntity<PushCampaignResponse> create(@Valid @RequestBody PushCampaignForm form) {
        PushCampaignResponse created = campaignService.create(form);
        return ResponseEntity.created(URI.create("/api/admin/push-campaigns/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    PushCampaignResponse update(@PathVariable Long id, @Valid @RequestBody PushCampaignForm form) {
        return campaignService.update(id, form);
    }

    @PostMapping("/{id}/toggle")
    PushCampaignResponse toggle(@PathVariable Long id) {
        return campaignService.toggleEnabled(id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
