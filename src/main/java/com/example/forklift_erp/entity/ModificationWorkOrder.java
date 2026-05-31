package com.example.forklift_erp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "modification_work_order")
public class ModificationWorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "work_order_no", nullable = false, unique = true, length = 80)
    private String workOrderNo;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "sales_order_no", length = 100)
    private String salesOrderNo;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "WAITING_PARTS";

    @Column(length = 50)
    private String operator;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
