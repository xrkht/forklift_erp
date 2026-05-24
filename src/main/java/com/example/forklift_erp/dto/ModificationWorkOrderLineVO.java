package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ModificationWorkOrderLine;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModificationWorkOrderLineVO {
    private Long id;
    private Long workOrderId;
    private Long machineConfigId;
    private Long configItemId;
    private String itemName;
    private String oldValue;
    private Long newPartId;
    private String newPartCode;
    private String newPartName;
    private String newValue;
    private Integer quantity;
    private String oldPartAction;
    private Long replaceLogId;
    private String remark;
    private LocalDateTime createdAt;

    public static ModificationWorkOrderLineVO fromEntity(ModificationWorkOrderLine entity) {
        ModificationWorkOrderLineVO vo = new ModificationWorkOrderLineVO();
        vo.setId(entity.getId());
        vo.setWorkOrderId(entity.getWorkOrderId());
        vo.setMachineConfigId(entity.getMachineConfigId());
        vo.setConfigItemId(entity.getConfigItemId());
        vo.setItemName(entity.getItemName());
        vo.setOldValue(entity.getOldValue());
        vo.setNewPartId(entity.getNewPartId());
        vo.setNewPartCode(entity.getNewPartCode());
        vo.setNewPartName(entity.getNewPartName());
        vo.setNewValue(entity.getNewValue());
        vo.setQuantity(entity.getQuantity());
        vo.setOldPartAction(entity.getOldPartAction());
        vo.setReplaceLogId(entity.getReplaceLogId());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
