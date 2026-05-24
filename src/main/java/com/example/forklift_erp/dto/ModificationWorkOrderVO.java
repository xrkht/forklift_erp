package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ModificationWorkOrder;
import com.example.forklift_erp.entity.ModificationWorkOrderLine;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ModificationWorkOrderVO {
    private Long id;
    private Long version;
    private String workOrderNo;
    private Long machineId;
    private String customerName;
    private String salesOrderNo;
    private String status;
    private String operator;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;
    private List<ModificationWorkOrderLineVO> lines;

    public static ModificationWorkOrderVO fromEntity(ModificationWorkOrder entity, List<ModificationWorkOrderLine> lines) {
        ModificationWorkOrderVO vo = new ModificationWorkOrderVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setWorkOrderNo(entity.getWorkOrderNo());
        vo.setMachineId(entity.getMachineId());
        vo.setCustomerName(entity.getCustomerName());
        vo.setSalesOrderNo(entity.getSalesOrderNo());
        vo.setStatus(entity.getStatus());
        vo.setOperator(entity.getOperator());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setCompletedAt(entity.getCompletedAt());
        vo.setCanceledAt(entity.getCanceledAt());
        vo.setLines(lines.stream().map(ModificationWorkOrderLineVO::fromEntity).toList());
        return vo;
    }
}
