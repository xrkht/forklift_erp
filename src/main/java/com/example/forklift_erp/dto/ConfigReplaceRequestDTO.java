// src/main/java/com/example/forklift_erp/dto/ConfigReplaceRequestDTO.java
package com.example.forklift_erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 配置替换请求 DTO
 * 描述一次替换需要的信息
 */
@Data
public class ConfigReplaceRequestDTO {
    private Long machineVersion;
    private Long oldConfigVersion;
    private Long newPartVersion;

    @NotNull(message = "车辆ID不能为空")
    private Long machineId;

    @NotNull(message = "配置项ID不能为空")
    private Long configItemId;

    @NotNull(message = "新配置值ID不能为空")
    private Long newConfigValueId;

    @NotBlank(message = "新配置值标签不能为空")
    private String newValueLabel;

    @NotBlank(message = "替换类型不能为空")
    @Pattern(regexp = "^(SWAP|UPGRADE|REPAIR)$", message = "替换类型非法")
    private String replaceType;

    // 可选字段
    private Long newPartId;
    private String oldPartAction;
    private String operator;
    private String remark;
}
