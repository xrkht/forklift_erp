// src/main/java/com/example/forklift_erp/dto/MachineConfigVO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.MachineConfig;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MachineConfigVO {
    private Long id;
    private Long version;
    private Long configItemId;
    private Long configValueId;
    private String itemName;
    private String selectedValue;
    private Boolean isStandard;
    private String configSource;
    private LocalDateTime installedDate;
    private String remark;

    public static MachineConfigVO fromEntity(MachineConfig entity) {
        MachineConfigVO vo = new MachineConfigVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setConfigItemId(entity.getConfigItemId());
        vo.setConfigValueId(entity.getConfigValueId());
        vo.setItemName(entity.getItemName());
        vo.setSelectedValue(entity.getSelectedValue());
        vo.setIsStandard(entity.getIsStandard());
        vo.setConfigSource(entity.getConfigSource());
        vo.setInstalledDate(entity.getInstalledDate());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
