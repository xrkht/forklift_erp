// src/main/java/com/example/forklift_erp/dto/RepairRecordVO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.RepairRecord;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairRecordVO {
    private Long id;
    private Long version;
    private LocalDateTime repairDate;
    private Long machineId;
    private String vehicleNumber;
    private String customerName;
    private String customerAddress;
    private String faultDescription;
    private String repairContent;
    private String repairPerson;
    private String usedParts;
    private BigDecimal workHours;
    private BigDecimal repairFee;
    private BigDecimal partsFee;
    private BigDecimal totalFee;
    private String status;
    private String remarks;
    // 排除审计字段

    public static RepairRecordVO fromEntity(RepairRecord entity) {
        RepairRecordVO vo = new RepairRecordVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setRepairDate(entity.getRepairDate());
        vo.setMachineId(entity.getMachineId());
        vo.setVehicleNumber(entity.getVehicleNumber());
        vo.setCustomerName(entity.getCustomerName());
        vo.setCustomerAddress(entity.getCustomerAddress());
        vo.setFaultDescription(entity.getFaultDescription());
        vo.setRepairContent(entity.getRepairContent());
        vo.setRepairPerson(entity.getRepairPerson());
        vo.setUsedParts(entity.getUsedParts());
        vo.setWorkHours(entity.getWorkHours());
        vo.setRepairFee(entity.getRepairFee());
        vo.setPartsFee(entity.getPartsFee());
        vo.setTotalFee(entity.getTotalFee());
        vo.setStatus(entity.getStatus());
        vo.setRemarks(entity.getRemarks());
        return vo;
    }
}
