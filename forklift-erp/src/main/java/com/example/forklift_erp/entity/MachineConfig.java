package com.example.forklift_erp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 车辆实际配置表
 * 记录每台车具体装了什么配置
 * 通过 config_value_id 关联 config_value 表，确保数据的引用完整性
 */
@Data
@Entity
@Table(name = "machine_config")
public class MachineConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的车辆ID（machine_inventory 表的主键）
     */
    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    /**
     * 关联的配置项ID（config_item 表的主键）
     */
    @Column(name = "config_item_id", nullable = false)
    private Long configItemId;

    /**
     * 关联的配置值ID（config_value 表的主键）
     */
    @Column(name = "config_value_id", nullable = false)
    private Long configValueId;

    // ===== 以下为冗余字段，方便查询时不需要JOIN多张表 =====

    /**
     * 配置项名称（冗余，方便直接显示）
     */
    @Column(name = "item_name", length = 100)
    private String itemName;

    /**
     * 选中的值（冗余，方便直接显示）
     */
    @Column(name = "selected_value", length = 200)
    private String selectedValue;

    /**
     * 是否为标准配置
     */
    @Column(name = "is_standard")
    private Boolean isStandard = true;

    /**
     * 来源
     * FACTORY = 原厂配置
     * CUSTOM = 客户选配
     * WAREHOUSE = 从仓库配件更换
     * OTHER_MACHINE = 从其他整车拆装
     */
    @Column(name = "config_source", length = 30)
    private String configSource = "FACTORY";

    /**
     * 安装日期
     */
    @Column(name = "installed_date")
    private LocalDateTime installedDate;

    /**
     * 备注
     */
    @Column(length = 255)
    private String remark;

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