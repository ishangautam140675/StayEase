package com.stayease.service;

import com.stayease.model.AdminAuditLog;
import com.stayease.repository.AdminAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for recording and querying auditable administrative actions.
 */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AdminAuditLogRepository auditLogRepository;

    public AuditLogService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an auditable administrative operation.
     */
    @Transactional
    public AdminAuditLog log(String adminEmail, String action, String entityType, String entityId, String details) {
        String trimmedDetails = details;
        if (trimmedDetails != null && trimmedDetails.length() > 1500) {
            trimmedDetails = trimmedDetails.substring(0, 1497) + "...";
        }
        AdminAuditLog log = new AdminAuditLog(
                adminEmail != null ? adminEmail : "ADMIN",
                action,
                entityType,
                entityId,
                trimmedDetails
        );
        return auditLogRepository.save(log);
    }

    /**
     * Retrieves paginated audit logs newest first.
     */
    public Page<AdminAuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    /**
     * Searches audit logs with filters.
     */
    public Page<AdminAuditLog> searchAuditLogs(String adminEmail, String action, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        String cleanEmail = (adminEmail != null && !adminEmail.trim().isEmpty()) ? adminEmail.trim() : null;
        String cleanAction = (action != null && !action.trim().isEmpty() && !"ALL".equalsIgnoreCase(action)) ? action.trim() : null;
        return auditLogRepository.searchAuditLogs(cleanEmail, cleanAction, start, end, pageable);
    }

    /**
     * Searches unpaginated audit logs for CSV export.
     */
    public java.util.List<AdminAuditLog> searchAuditLogsForExport(String adminEmail, String action, LocalDateTime start, LocalDateTime end) {
        String cleanEmail = (adminEmail != null && !adminEmail.trim().isEmpty()) ? adminEmail.trim() : null;
        String cleanAction = (action != null && !action.trim().isEmpty() && !"ALL".equalsIgnoreCase(action)) ? action.trim() : null;
        return auditLogRepository.searchAuditLogsForExport(cleanEmail, cleanAction, start, end);
    }

    /**
     * Deletes a single audit log entry by its ID.
     */
    @Transactional
    public void deleteLog(Long id) {
        if (id != null) {
            auditLogRepository.deleteById(id);
        }
    }

    /**
     * Deletes multiple audit log entries by their IDs.
     */
    @Transactional
    public void deleteLogs(java.util.List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            auditLogRepository.deleteAllById(ids);
        }
    }

    /**
     * Clears all audit log entries.
     */
    @Transactional
    public void clearAllLogs() {
        auditLogRepository.deleteAll();
    }
}
