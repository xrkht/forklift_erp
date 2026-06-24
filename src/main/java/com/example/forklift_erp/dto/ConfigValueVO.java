// src/main/java/com/example/forklift_erp/dto/ConfigValueVO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ConfigValue;
import lombok.Data;

@Data
public class ConfigValueVO {
    private Long id;
    private Long version;
    private Long configItemId;
    private String valueLabel;
    private String valueCode;
    private Boolean isDefault;
    private Integer sortOrder;
    private String remark;

    public static ConfigValueVO fromEntity(ConfigValue entity) {
        ConfigValueVO vo = new ConfigValueVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setConfigItemId(entity.getConfigItemId());
        vo.setValueLabel(entity.getValueLabel());
        vo.setValueCode(entity.getValueCode());
        vo.setIsDefault(entity.getIsDefault());
        vo.setSortOrder(entity.getSortOrder());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
