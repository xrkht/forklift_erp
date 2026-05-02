package com.example.forklift_erp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 配置项可选值表
 * 每个 ConfigItem 可以有多个可选值
 * 例如：轮胎类型 的可选值有：充气胎、实心胎、前轮充气后轮实心
 */
@Data
@Entity
@Table(name = "config_value")
public class ConfigValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的配置项ID（config_item 表的主键）
     */
    @Column(name = "config_item_id", nullable = false)
    private Long configItemId;

    /**
     * 可选值（显示名称）
     * 例如："充气胎"、"实心胎"、"全柴V29-50V42"
     */
    @Column(name = "value_label", length = 200, nullable = false)
    private String valueLabel;

    /**
     * 可选值（内部编码，可选）
     * 例如："V29-50V42"、"PNEUMATIC"
     */
    @Column(name = "value_code", length = 100)
    private String valueCode;

    /**
     * 是否为默认值
     */
    @Column(name = "is_default")
    private Boolean isDefault = false;

    /**
     * 排序号
     */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * 备注说明
     */
    @Column(length = 255)
    private String remark;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}