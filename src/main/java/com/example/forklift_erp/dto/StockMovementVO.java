package com.example.forklift_erp.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockMovementVO {
    private Long id;
    private Long movementId;
    private String movementNo;
    private String movementType;
    private String resourceType;
    private Long resourceId;
    private String resourceCode;
    private String resourceName;
    private Long warehouseId;
    private String warehouseName;
    private Integer quantityDelta;
    private Integer beforeQuantity;
    private Integer afterQuantity;
    private BigDecimal unitCost;
    private String sourceType;
    private Long sourceId;
    private String operator;
    private String remark;
    private LocalDateTime createdAt;
}
