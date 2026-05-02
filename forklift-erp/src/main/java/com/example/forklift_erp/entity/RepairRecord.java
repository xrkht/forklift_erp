package com.example.forklift_erp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外出维修记录表
 */
@Data
@Entity
@Table(name = "repair_record")
public class RepairRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 维修日期
     */
    @Column(name = "repair_date", nullable = false)
    private LocalDateTime repairDate;

    /**
     * 关联的车辆ID（可能为空，维修配件不涉及整车）
     */
    @Column(name = "machine_id")
    private Long machineId;

    /**
     * 车辆编号
     */
    @Column(name = "vehicle_number", length = 100)
    private String vehicleNumber;

    /**
     * 客户名称
     */
    @Column(name = "customer_name", length = 100)
    private String customerName;

    /**
     * 客户地址
     */
    @Column(name = "customer_address", length = 255)
    private String customerAddress;

    /**
     * 故障描述
     */
    @Column(name = "fault_description", length = 500)
    private String faultDescription;

    /**
     * 维修内容
     */
    @Column(name = "repair_content", length = 1000)
    private String repairContent;

    /**
     * 维修人员
     */
    @Column(name = "repair_person", length = 50)
    private String repairPerson;

    /**
     * 使用的配件（可用于关联配件出库）
     */
    @Column(name = "used_parts", length = 500)
    private String usedParts;

    /**
     * 工时（小时）
     */
    @Column(name = "work_hours", precision = 5, scale = 1)
    private BigDecimal workHours;

    /**
     * 维修费用
     */
    @Column(name = "repair_fee", precision = 10, scale = 2)
    private BigDecimal repairFee;

    /**
     * 配件费用
     */
    @Column(name = "parts_fee", precision = 10, scale = 2)
    private BigDecimal partsFee;

    /**
     * 总费用
     */
    @Column(name = "total_fee", precision = 10, scale = 2)
    private BigDecimal totalFee;

    /**
     * 状态
     * PENDING = 待维修
     * IN_PROGRESS = 维修中
     * COMPLETED = 已完成
     */
    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @Column(length = 500)
    private String remarks;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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