package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ConfigReplaceLog;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConfigReplaceLogVO {
    private Long id;
    private Long machineId;
    private Long machineConfigId;
    private String itemName;
    private String oldValue;
    private String newValue;
    private String replaceType;
    private Long newPartId;
    private String operator;
    private String remark;
    private LocalDateTime createdAt;

    public static ConfigReplaceLogVO fromEntity(ConfigReplaceLog entity) {
        ConfigReplaceLogVO vo = new ConfigReplaceLogVO();
        vo.setId(entity.getId());
        vo.setMachineId(entity.getMachineId());
        vo.setMachineConfigId(entity.getMachineConfigId());
        vo.setItemName(entity.getItemName());
        vo.setOldValue(entity.getOldValue());
        vo.setNewValue(entity.getNewValue());
        vo.setReplaceType(entity.getReplaceType());
        vo.setNewPartId(entity.getNewPartId());
        vo.setOperator(entity.getOperator());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}