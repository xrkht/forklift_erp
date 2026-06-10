package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.VehicleConfigValue;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleConfigValueVO {
    private Long id;
    private Long version;
    private Long vehicleConfigItemId;
    private Long configItemId;
    private Long configValueId;
    private String configItemName;
    private String configItemLabel;
    private String configValueLabel;
    private String configValueCode;
    private String unit;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VehicleConfigValueVO fromEntity(VehicleConfigValue entity) {
        return fromEntity(entity, null, null);
    }

    public static VehicleConfigValueVO fromEntity(VehicleConfigValue entity, ConfigItem configItem, ConfigValue configValue) {
        VehicleConfigValueVO vo = new VehicleConfigValueVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setVehicleConfigItemId(entity.getVehicleConfigItemId());
        vo.setConfigItemId(entity.getConfigItemId());
        vo.setConfigValueId(entity.getConfigValueId());
        vo.setConfigItemName(configItem == null ? entity.getConfigItemName() : configItem.getItemName());
        vo.setConfigItemLabel(configItem == null ? entity.getConfigItemName() : configItemLabel(configItem));
        vo.setConfigValueLabel(configValue == null ? entity.getConfigValueLabel() : configValue.getValueLabel());
        vo.setConfigValueCode(configValue == null ? null : configValue.getValueCode());
        vo.setUnit(configItem == null ? null : configItem.getUnit());
        vo.setSortOrder(entity.getSortOrder());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private static String configItemLabel(ConfigItem item) {
        return java.util.List.of(item.getCategory(), item.getSubCategory(), item.getItemName()).stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " / " + right)
                .orElse(item.getItemName());
    }
}
