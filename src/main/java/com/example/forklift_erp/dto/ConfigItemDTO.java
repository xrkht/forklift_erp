package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ConfigItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfigItemDTO {
    private Long version;

    @NotBlank(message = "分类不能为空")
    @Size(max = 50, message = "分类长度不能超过50")
    private String category;

    @Size(max = 50, message = "子分类长度不能超过50")
    private String subCategory;

    @NotBlank(message = "配置项名称不能为空")
    @Size(max = 100, message = "配置项名称长度不能超过100")
    private String itemName;

    @Size(max = 80, message = "配置项编码长度不能超过80")
    private String itemCode;

    @Size(max = 20, message = "输入类型长度不能超过20")
    private String inputType;

    @Size(max = 20, message = "单位长度不能超过20")
    private String unit;

    private Boolean isRequired;
    private Integer sortOrder;

    public ConfigItem toEntity(Long id) {
        ConfigItem item = new ConfigItem();
        item.setId(id);
        item.setVersion(version);
        item.setCategory(category);
        item.setSubCategory(subCategory);
        item.setItemName(itemName);
        item.setItemCode(itemCode);
        item.setInputType(inputType);
        item.setUnit(unit);
        item.setIsRequired(isRequired);
        item.setSortOrder(sortOrder);
        return item;
    }
}
