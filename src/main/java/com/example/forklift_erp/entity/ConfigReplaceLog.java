package com.example.forklift_erp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "config_replace_log")
public class ConfigReplaceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的车辆ID
     */
    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    /**
     * 关联的 machine_config 记录ID
     */
    @Column(name = "machine_config_id")
    private Long machineConfigId;

    /**
     * 配置项名称
     */
    @Column(name = "item_name", length = 100, nullable = false)
    private String itemName;

    /**
     * 旧值
     */
    @Column(name = "old_value", length = 100)
    private String oldValue;

    /**
     * 新值
     */
    @Column(name = "new_value", length = 100, nullable = false)
    private String newValue;

    /**
     * 替换类型
     * SWAP = 用仓库配件替换
     * UPGRADE = 升级改装
     * REPAIR = 维修更换
     */
    @Column(name = "replace_type", length = 30)
    private String replaceType;

    /**
     * 关联的配件记录 (part_inventory 的 id)
     */
    @Column(name = "new_part_id")
    private Long newPartId;

    /**
     * 操作人
     */
    @Column(length = 50)
    private String operator;

    @Column(length = 255)
    private String remark;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}