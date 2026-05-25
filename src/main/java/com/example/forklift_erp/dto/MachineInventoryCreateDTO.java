package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.MachineInventory;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MachineInventoryCreateDTO {
    private Long version;

    private String vehicleProductNumber;

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "规格型号不能为空")
    private String specificationModel;

    private String machineType;
    private String configuration;
    private String supplier;
    private String warehouseName;
    private Long warehouseId;
    private String stockStatus;
    private BigDecimal purchasePrice;
    private BigDecimal salePrice;
    private BigDecimal settlementPrice;
    private String engineNumber;
    private String frameNumber;
    private String warrantyCardNumber;
    private LocalDate manufacturingDate;
    private LocalDateTime inboundDate;
    private Integer inventoryCount;
    private Boolean modelOnly;

    public MachineInventory toEntity() {
        MachineInventory entity = new MachineInventory();
        entity.setVehicleProductNumber(this.vehicleProductNumber);
        entity.setName(this.name);
        entity.setSpecificationModel(this.specificationModel);
        entity.setMachineType(this.machineType);
        entity.setConfiguration(this.configuration);
        entity.setSupplier(this.supplier);
        entity.setWarehouseName(this.warehouseName);
        if (this.warehouseId != null) {
            entity.setWarehouseId(this.warehouseId);
        }
        if (this.stockStatus != null) {
            entity.setStockStatus(this.stockStatus);
        }
        entity.setPurchasePrice(this.purchasePrice);
        entity.setSalePrice(this.salePrice);
        entity.setSettlementPrice(this.settlementPrice);
        entity.setEngineNumber(this.engineNumber);
        entity.setFrameNumber(this.frameNumber);
        entity.setWarrantyCardNumber(this.warrantyCardNumber);
        entity.setManufacturingDate(this.manufacturingDate);
        entity.setInboundDate(this.inboundDate);
        entity.setInventoryCount(this.inventoryCount);
        if (this.modelOnly != null) {
            entity.setModelOnly(this.modelOnly);
        }
        return entity;
    }

    // 用于更新时，将 DTO 的值赋给已有的实体（避免创建新实体时丢失 id）
    public void updateEntity(MachineInventory entity) {
        entity.setVehicleProductNumber(this.vehicleProductNumber);
        entity.setName(this.name);
        entity.setSpecificationModel(this.specificationModel);
        entity.setMachineType(this.machineType);
        entity.setConfiguration(this.configuration);
        entity.setSupplier(this.supplier);
        entity.setWarehouseName(this.warehouseName);
        if (this.warehouseId != null) {
            entity.setWarehouseId(this.warehouseId);
        }
        if (this.stockStatus != null) {
            entity.setStockStatus(this.stockStatus);
        }
        entity.setPurchasePrice(this.purchasePrice);
        entity.setSalePrice(this.salePrice);
        entity.setSettlementPrice(this.settlementPrice);
        entity.setEngineNumber(this.engineNumber);
        entity.setFrameNumber(this.frameNumber);
        entity.setWarrantyCardNumber(this.warrantyCardNumber);
        entity.setManufacturingDate(this.manufacturingDate);
        entity.setInboundDate(this.inboundDate);
        entity.setInventoryCount(this.inventoryCount);
        if (this.modelOnly != null) {
            entity.setModelOnly(this.modelOnly);
        }
    }
}
