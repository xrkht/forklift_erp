package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.Warehouse;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WarehouseVO {
    private Long id;
    private Long version;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String address;
    private Boolean defaultWarehouse;
    private Long vehicleCount = 0L;
    private Long partSkuCount = 0L;
    private Long partQuantity = 0L;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WarehouseVO fromEntity(Warehouse warehouse) {
        WarehouseVO vo = new WarehouseVO();
        vo.setId(warehouse.getId());
        vo.setVersion(warehouse.getVersion());
        vo.setWarehouseCode(warehouse.getWarehouseCode());
        vo.setWarehouseName(warehouse.getWarehouseName());
        vo.setWarehouseType(warehouse.getWarehouseType());
        vo.setAddress(warehouse.getAddress());
        vo.setDefaultWarehouse(warehouse.getDefaultWarehouse());
        vo.setCreatedAt(warehouse.getCreatedAt());
        vo.setUpdatedAt(warehouse.getUpdatedAt());
        return vo;
    }
}
