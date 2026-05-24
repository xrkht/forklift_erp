package com.example.forklift_erp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "warehouse")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_code", length = 50, nullable = false, unique = true)
    private String warehouseCode;

    @Column(name = "warehouse_name", length = 100, nullable = false)
    private String warehouseName;

    @Column(name = "warehouse_type", length = 30, nullable = false)
    private String warehouseType = "MAIN";

    @Column(length = 255)
    private String address;

    @Column(name = "is_default", nullable = false)
    private Boolean defaultWarehouse = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.defaultWarehouse == null) {
            this.defaultWarehouse = false;
        }
        if (this.warehouseType == null || this.warehouseType.isBlank()) {
            this.warehouseType = "MAIN";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
