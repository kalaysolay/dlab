package kz.damulab.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminContentAuditService {

    private final AdminContentAuditLogRepository auditLogs;

    public AdminContentAuditService(AdminContentAuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public void record(String action, String entityType, Long entityId, String details) {
        auditLogs.save(new AdminContentAuditLog(currentActor(), action, entityType, entityId, details));
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "system";
        }
        return authentication.getName();
    }
}
