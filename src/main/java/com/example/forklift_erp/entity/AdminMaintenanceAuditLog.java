package com.example.forklift_erp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "admin_maintenance_audit_log",
        indexes = {
                @Index(name = "idx_admin_maintenance_audit_created_at", columnList = "created_at"),
                @Index(name = "idx_admin_maintenance_audit_event_status", columnList = "event_type, status"),
                @Index(name = "idx_admin_maintenance_audit_operator", columnList = "operator")
        }
)
public class AdminMaintenanceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", length = 60, nullable = false)
    private String eventType;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(length = 50)
    private String operator;

    @Column(name = "remote_addr", length = 80)
    private String remoteAddr;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
