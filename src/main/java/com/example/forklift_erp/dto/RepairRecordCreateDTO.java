// src/main/java/com/example/forklift_erp/dto/RepairRecordCreateDTO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.RepairRecord;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RepairRecordCreateDTO {
    private Long version;

    @NotNull(message = "维修日期不能为空")
    private LocalDateTime repairDate;

    private Long machineId;

    @Size(max = 100)
    private String vehicleNumber;

    private Long customerId;

    @Size(max = 100)
    private String customerName;

    @Size(max = 255)
    private String customerAddress;

    @NotBlank(message = "故障描述不能为空")
    @Size(max = 500)
    private String faultDescription;

    @Size(max = 1000)
    private String repairContent;

    @Size(max = 50)
    private String repairPerson;

    private String repairPersonChoice;

    private Long repairPersonUserId;

    private Boolean repairExternal;

    @Size(max = 500)
    private String usedParts;

    private List<Long> usedPartIds = new ArrayList<>();

    @DecimalMin(value = "0.00", message = "维修服务费不能为负数")
    private BigDecimal repairFee;
    @DecimalMin(value = "0.00", message = "外包维修支出不能为负数")
    private BigDecimal repairExpense;
    @DecimalMin(value = "0.00", message = "配件收费不能为负数")
    private BigDecimal partsFee;
    @DecimalMin(value = "0.00", message = "客户应收总额不能为负数")
    private BigDecimal totalFee;

    @Pattern(regexp = "^(PENDING|COMPLETED)$", message = "状态值非法")
    private String status;

    @Size(max = 500)
    private String remarks;

    public RepairRecord toEntity() {
        RepairRecord entity = new RepairRecord();
        applyToEntity(entity);
        return entity;
    }

    public void applyToEntity(RepairRecord entity) {
        entity.setRepairDate(this.repairDate);
        entity.setMachineId(this.machineId);
        entity.setVehicleNumber(this.vehicleNumber);
        entity.setCustomerId(this.customerId);
        entity.setCustomerName(this.customerName);
        entity.setCustomerAddress(this.customerAddress);
        entity.setFaultDescription(this.faultDescription);
        entity.setRepairContent(this.repairContent);
        entity.setRepairPerson(this.repairPerson);
        applyRepairPerson(entity);
        entity.setUsedParts(this.usedParts);
        entity.setUsedPartIds(joinIds(this.usedPartIds));
        entity.setRepairFee(this.repairFee);
        entity.setRepairExpense(this.repairExpense);
        entity.setPartsFee(this.partsFee);
        entity.setTotalFee(this.totalFee);
        entity.setStatus(this.status);
        entity.setRemarks(this.remarks);
    }

    private void applyRepairPerson(RepairRecord entity) {
        String choice = repairPersonChoice == null ? "" : repairPersonChoice.trim();
        if ("OTHER".equalsIgnoreCase(choice)) {
            entity.setRepairPersonUserId(null);
            entity.setRepairExternal(true);
            entity.setRepairPerson("其他");
            return;
        }
        if (!choice.isBlank()) {
            try {
                entity.setRepairPersonUserId(Long.parseLong(choice));
            } catch (NumberFormatException ignored) {
                entity.setRepairPersonUserId(this.repairPersonUserId);
            }
        } else {
            entity.setRepairPersonUserId(this.repairPersonUserId);
        }
        entity.setRepairExternal(Boolean.TRUE.equals(this.repairExternal));
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }
}
