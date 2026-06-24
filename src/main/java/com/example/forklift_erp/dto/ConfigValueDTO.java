package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ConfigValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfigValueDTO {
    private Long version;

    @NotNull(message = "配置项不能为空")
    private Long configItemId;

    @NotBlank(message = "配置值不能为空")
    @Size(max = 200, message = "配置值长度不能超过200")
    private String valueLabel;

    @Size(max = 100, message = "配置值编码长度不能超过100")
    private String valueCode;

    private Boolean isDefault;
    private Integer sortOrder;

    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;

    public ConfigValue toEntity() {
        ConfigValue value = new ConfigValue();
        value.setVersion(version);
        value.setConfigItemId(configItemId);
        value.setValueLabel(valueLabel);
        value.setValueCode(valueCode);
        value.setIsDefault(isDefault);
        value.setSortOrder(sortOrder);
        value.setRemark(remark);
        return value;
    }
}
