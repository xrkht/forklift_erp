package com.example.forklift_erp.dto;

import com.example.forklift_erp.repository.MachineInventoryRepository;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VehicleModelSummaryVO {
    private String name;
    private String specificationModel;
    private String machineType;
    private String supplier;
    private String warehouseName;
    private BigDecimal purchasePrice;
    private BigDecimal salePrice;
    private BigDecimal settlementPrice;
    private Long modelTemplateId;
    private Long unitCount;
    private Long inventoryCount;
    private String vehicleNumbers;

    public static VehicleModelSummaryVO fromProjection(MachineInventoryRepository.VehicleModelSummaryProjection projection) {
        VehicleModelSummaryVO vo = new VehicleModelSummaryVO();
        vo.setName(projection.getName());
        vo.setSpecificationModel(projection.getSpecificationModel());
        vo.setMachineType(projection.getMachineType());
        vo.setSupplier(projection.getSupplier());
        vo.setWarehouseName(projection.getWarehouseName());
        vo.setPurchasePrice(projection.getPurchasePrice());
        vo.setSalePrice(projection.getSalePrice());
        vo.setSettlementPrice(projection.getSettlementPrice());
        vo.setModelTemplateId(projection.getModelTemplateId());
        vo.setUnitCount(projection.getUnitCount() == null ? 0L : projection.getUnitCount());
        vo.setInventoryCount(projection.getInventoryCount() == null ? 0L : projection.getInventoryCount());
        vo.setVehicleNumbers(projection.getVehicleNumbers() == null ? "" : projection.getVehicleNumbers());
        return vo;
    }
}
