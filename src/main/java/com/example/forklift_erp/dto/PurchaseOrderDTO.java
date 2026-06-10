package com.example.forklift_erp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PurchaseOrderDTO {
    private Long version;

    private Long supplierId;
    private String supplierName;
    private String supplier;

    private Long configItemId;
    private Long configValueId;
    private String resourceType;

    private String resourceCode;
    private String resourceName;
    private String specificationModel;

    @NotNull(message = "采购数量不能为空")
    @Min(value = 1, message = "采购数量必须大于0")
    private Integer quantity;

    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private LocalDate orderDate;
    private LocalDate expectedArrivalDate;
    private String status;
    private String operator;
    private String remark;
}
