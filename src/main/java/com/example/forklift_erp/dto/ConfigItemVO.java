// src/main/java/com/example/forklift_erp/dto/ConfigItemVO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ConfigItem;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConfigItemVO {
    private Long id;
    private Long version;
    private String category;
    private String subCategory;
    private String itemName;
    private String itemCode;
    private String inputType;
    private String unit;
    private Boolean isRequired;
    private Integer sortOrder;
    // 不加审计字段

    public static ConfigItemVO fromEntity(ConfigItem entity) {
        ConfigItemVO vo = new ConfigItemVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setCategory(entity.getCategory());
        vo.setSubCategory(entity.getSubCategory());
        vo.setItemName(entity.getItemName());
        vo.setItemCode(entity.getItemCode());
        vo.setInputType(entity.getInputType());
        vo.setUnit(entity.getUnit());
        vo.setIsRequired(entity.getIsRequired());
        vo.setSortOrder(entity.getSortOrder());
        return vo;
    }
}
