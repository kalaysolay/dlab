package kz.damulab.audit;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_content_audit_logs")
public class AdminContentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_email", nullable = false, length = 320)
    private String actorEmail;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(length = 1024)
    private String details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AdminContentAuditLog() {
    }

    public AdminContentAuditLog(String actorEmail, String action, String entityType, Long entityId, String details) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public Long getId() {
        return id;
    }
}
