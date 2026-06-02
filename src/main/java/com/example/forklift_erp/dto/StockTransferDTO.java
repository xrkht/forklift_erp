package com.example.forklift_erp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockTransferDTO {
    private Long version;

    @NotBlank(message = "Resource type is required")
    private String resourceType;

    @NotNull(message = "Resource ID is required")
    private Long resourceId;

    @NotNull(message = "Source warehouse is required")
    private Long fromWarehouseId;

    @NotNull(message = "Target warehouse is required")
    private Long toWarehouseId;

    @NotNull(message = "Transfer quantity is required")
    @Min(value = 1, message = "Transfer quantity must be greater than 0")
    private Integer quantity;

    private String operator;
    private String remark;
}
