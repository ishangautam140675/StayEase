package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity tracking auditable administrative operations:
 * booking approvals/rejections/cancellations/check-ins/check-outs/no-shows,
 * room pricing changes, inventory maintenance blocks, etc.
 */
@Entity
@Table(name = "admin_audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_admin", columnList = "admin_email"),
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id")
})
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_email", nullable = false, length = 120)
    private String adminEmail;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 60)
    private String entityId;

    @Column(name = "details", length = 1500)
    private String details;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public AdminAuditLog() {
    }

    public AdminAuditLog(String adminEmail, String action, String entityType, String entityId, String details) {
        this.adminEmail = adminEmail != null ? adminEmail : "SYSTEM";
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminAuditLog that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
