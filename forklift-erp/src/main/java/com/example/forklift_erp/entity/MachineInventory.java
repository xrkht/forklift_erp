// MachineInventory.java
package com.example.forklift_erp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 整机进出库实体类
@Data // Lombok注解，自动生成getter/setter/toString等方法
@Entity // JPA注解，声明这是一个数据库实体
@Table(name = "machine_inventory") // 指定数据库中对应的表名
public class MachineInventory {

    @Id // 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 主键生成策略：自增
    private Long id; // 对应Excel的“序列”列，我们作为主键

    @Column(name = "inbound_date") // 指定列名
    private LocalDateTime inboundDate; // 入库日期

    @Column(length = 50)
    private String supplier; // 供应商

    @Column(name = "application_number", length = 100)
    private String applicationNumber; // 样机申请单号

    @Column(length = 100)
    private String name; // 名称

    @Column(name = "material_number", length = 100)
    private String materialNumber; // 物料号

    @Column(name = "specification_model", length = 100)
    private String specificationModel; // 规格型号

    @Column(name = "configuration", length = 500) // 配置可能很长
    private String configuration; // 配置

    @Column(name = "vehicle_number", unique = true, length = 100) // 假设车号/产品编号是唯一的
    private String vehicleProductNumber; // 车号/产品编号

    @Column(name = "engine_number", length = 100)
    private String engineNumber; // 发动机号

    @Column(name = "frame_number", length = 100)
    private String frameNumber; // 车架号

    @Column(name = "warranty_card_number", length = 100)
    private String warrantyCardNumber; // 保修卡号

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate; // 制造日期

    @Column(name = "settlement_price", precision = 12, scale = 2) // 价格类型使用BigDecimal，精度更高
    private BigDecimal settlementPrice; // 结算价

    @Column(name = "warehouse_return_note", length = 255)
    private String warehouseReturnNote; // 注意仓储车季返

    @Column(name = "is_sales_reported", length = 10)
    private String isSalesReported; // 是否报销售

    @Column(name = "sales_report_date")
    private LocalDate salesReportDate; // 报销售日期

    @Column(name = "sales_month", length = 10)
    private String salesMonth; // 销售月份 (格式如 202211)

    @Column(name = "inventory_count")
    private Integer inventoryCount; // 库存数

    // 以下几个“去向”字段，可以用多个字段，也可以未来单独建表，我们先简单处理
    @Column(name = "destination1", length = 255)
    private String destination1;

    @Column(name = "destination2", length = 255)
    private String destination2;

    @Column(name = "destination3", length = 255)
    private String destination3;

    @Column(name = "destination4", length = 255)
    private String destination4;

    @Column(name = "destination5", length = 255)
    private String destination5;

    @Column(name = "dealer_name", length = 100)
    private String dealerName; // 经销商名称

    @Column(name = "is_invoice_applied", length = 50)
    private String isInvoiceApplied; // 是否申请发票

    @Column(length = 500)
    private String remarks; // 备注

    // 以下两个字段通常用于审计和记录
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // 创建时间

    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // 更新时间

    @PrePersist // 在实体第一次保存之前自动执行
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate // 在实体更新之前自动执行
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}