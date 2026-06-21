package com.example.forklift_erp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "operation_audit_log",
        indexes = {
                @Index(name = "idx_operation_audit_created_at", columnList = "created_at"),
                @Index(name = "idx_operation_audit_source", columnList = "source_type, source_id"),
                @Index(name = "idx_operation_audit_target", columnList = "target_type, target_id")
        }
)
public class OperationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String module;

    @Column(length = 40, nullable = false)
    private String action;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_code", length = 100)
    private String targetCode;

    @Column(name = "target_name", length = 120)
    private String targetName;

    @Column(length = 500)
    private String summary;

    @Column(length = 50)
    private String operator;

    @Column(length = 500)
    private String remark;

    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
