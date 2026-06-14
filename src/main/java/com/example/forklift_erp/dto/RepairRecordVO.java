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
    private Long customerId;
    private String customerName;
    private String customerAddress;
    private String faultDescription;
    private String repairContent;
    private String repairPerson;
    private Long repairPersonUserId;
    private Boolean repairExternal;
    private String usedParts;
    private String usedPartIds;
    private BigDecimal repairFee;
    private BigDecimal repairExpense;
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
        vo.setCustomerId(entity.getCustomerId());
        vo.setCustomerName(entity.getCustomerName());
        vo.setCustomerAddress(entity.getCustomerAddress());
        vo.setFaultDescription(entity.getFaultDescription());
        vo.setRepairContent(entity.getRepairContent());
        vo.setRepairPerson(entity.getRepairPerson());
        vo.setRepairPersonUserId(entity.getRepairPersonUserId());
        vo.setRepairExternal(Boolean.TRUE.equals(entity.getRepairExternal()));
        vo.setUsedParts(entity.getUsedParts());
        vo.setUsedPartIds(entity.getUsedPartIds());
        vo.setRepairFee(entity.getRepairFee());
        vo.setRepairExpense(entity.getRepairExpense());
        vo.setPartsFee(entity.getPartsFee());
        vo.setTotalFee(entity.getTotalFee());
        vo.setStatus(entity.getStatus());
        vo.setRemarks(entity.getRemarks());
        return vo;
    }
}
