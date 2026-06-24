package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.RentalRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RentalRecordVO {
    private Long id;
    private Long version;
    private String rentalNo;
    private Long machineId;
    private Long customerId;
    private String vehicleNumber;
    private String machineName;
    private String specificationModel;
    private String customerName;
    private String customerAddress;
    private String destination;
    private BigDecimal rentalPrice;
    private BigDecimal monthlyRentalPrice;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String operator;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RentalRecordVO fromEntity(RentalRecord entity) {
        RentalRecordVO vo = new RentalRecordVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setRentalNo(entity.getRentalNo());
        vo.setMachineId(entity.getMachineId());
        vo.setCustomerId(entity.getCustomerId());
        vo.setVehicleNumber(entity.getVehicleNumber());
        vo.setMachineName(entity.getMachineName());
        vo.setSpecificationModel(entity.getSpecificationModel());
        vo.setCustomerName(entity.getCustomerName());
        vo.setCustomerAddress(entity.getCustomerAddress());
        vo.setDestination(entity.getDestination());
        vo.setRentalPrice(entity.getRentalPrice());
        vo.setMonthlyRentalPrice(entity.getMonthlyRentalPrice());
        vo.setStartDate(entity.getStartDate());
        vo.setEndDate(entity.getEndDate());
        vo.setStatus(entity.getStatus());
        vo.setOperator(entity.getOperator());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
