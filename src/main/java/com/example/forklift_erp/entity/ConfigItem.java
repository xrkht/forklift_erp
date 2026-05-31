package com.example.forklift_erp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 配置项字典表 - 定义所有可配置的项目
 * 例如：发动机型号、轮胎类型、门架级数、货叉尺寸等
 */
@Data
@Entity
@Table(name = "config_item")
public class ConfigItem implements CollaborativeResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 一级分类
     * 可选值：
     *   动力与传动系统
     *   门架与属具
     *   轮胎与底盘
     *   车身配置与附件
     *   货叉与仓储车配置
     */
    @NotBlank(message = "分类不能为空")
    @Size(max = 50, message = "分类长度不能超过50")
    @Column(name = "category", length = 50, nullable = false)
    private String category;

    /**
     * 二级分类（更细化）
     * 例如："发动机"、"电池"、"门架"、"多路阀"、"货叉尺寸"
     */
    @Column(name = "sub_category", length = 50)
    private String subCategory;

    /**
     * 配置项名称（显示给用户看的）
     * 例如："发动机品牌/型号"、"轮胎类型"、"货叉长度"
     */
    @NotBlank(message = "配置项名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100")
    @Column(name = "item_name", length = 100, nullable = false)
    private String itemName;

    /**
     * 配置项编码（程序识别用，唯一）
     * 例如：ENGINE_MODEL、TIRE_TYPE、FORK_LENGTH、DOOR_STAGE
     */
    @Size(max = 80, message = "编码长度不能超过80")
    @Column(name = "item_code", length = 80, unique = true, nullable = false)
    private String itemCode;

    /**
     * 输入类型
     * SELECT = 下拉单选
     * MULTI_SELECT = 下拉多选
     * NUMBER = 数字输入
     * TEXT = 文本输入
     * BOOLEAN = 是否选择（如：有/无）
     */
    @NotBlank(message = "输入类型不能为空")
    @Pattern(regexp = "^(SELECT|MULTI_SELECT|NUMBER|TEXT|BOOLEAN)$", message = "输入类型非法")
    @Column(name = "input_type", length = 20, nullable = false)
    private String inputType = "SELECT";

    /**
     * 单位（如果适用）
     * 例如：mm、Ah、V
     */
    @Column(name = "unit", length = 20)
    private String unit;

    /**
     * 是否必选
     */
    @Column(name = "is_required")
    private Boolean isRequired = true;

    /**
     * 排序号
     */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_modified_by", length = 50)
    private String lastModifiedBy;

    @Column(name = "last_modified_role", length = 30)
    private String lastModifiedRole;

    @Column(name = "last_modified_priority")
    private Integer lastModifiedPriority = 0;

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
