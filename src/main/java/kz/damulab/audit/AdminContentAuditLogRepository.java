package kz.damulab.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminContentAuditLogRepository extends JpaRepository<AdminContentAuditLog, Long> {

    long countByEntityTypeAndEntityId(String entityType, Long entityId);
}
