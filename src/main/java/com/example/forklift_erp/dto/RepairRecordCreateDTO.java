// src/main/java/com/example/forklift_erp/dto/RepairRecordCreateDTO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.RepairRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairRecordCreateDTO {
    private Long version;

    @NotNull(message = "维修日期不能为空")
    private LocalDateTime repairDate;

    private Long machineId;

    @Size(max = 100)
    private String vehicleNumber;

    @NotBlank(message = "客户名称不能为空")
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

    @Size(max = 500)
    private String usedParts;

    private BigDecimal workHours;
    private BigDecimal repairFee;
    private BigDecimal partsFee;
    private BigDecimal totalFee;

    @Pattern(regexp = "^(PENDING|IN_PROGRESS|COMPLETED)$", message = "状态值非法")
    private String status;

    @Size(max = 500)
    private String remarks;

    public RepairRecord toEntity() {
        RepairRecord entity = new RepairRecord();
        entity.setRepairDate(this.repairDate);
        entity.setMachineId(this.machineId);
        entity.setVehicleNumber(this.vehicleNumber);
        entity.setCustomerName(this.customerName);
        entity.setCustomerAddress(this.customerAddress);
        entity.setFaultDescription(this.faultDescription);
        entity.setRepairContent(this.repairContent);
        entity.setRepairPerson(this.repairPerson);
        entity.setUsedParts(this.usedParts);
        entity.setWorkHours(this.workHours);
        entity.setRepairFee(this.repairFee);
        entity.setPartsFee(this.partsFee);
        entity.setTotalFee(this.totalFee);
        entity.setStatus(this.status);
        entity.setRemarks(this.remarks);
        return entity;
    }
}
