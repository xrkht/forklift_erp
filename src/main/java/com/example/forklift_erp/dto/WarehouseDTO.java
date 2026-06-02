package com.example.forklift_erp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WarehouseDTO {
    private Long version;

    @NotBlank(message = "Warehouse code is required")
    private String warehouseCode;

    @NotBlank(message = "Warehouse name is required")
    private String warehouseName;

    private String warehouseType;
    private String address;
    private Boolean defaultWarehouse;
}
