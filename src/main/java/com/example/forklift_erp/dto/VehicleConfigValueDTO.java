package com.example.forklift_erp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehicleConfigValueDTO {
    private Long version;

    @NotNull(message = "整车配置项不能为空")
    private Long vehicleConfigItemId;

    @NotNull(message = "配置项不能为空")
    private Long configItemId;

    @NotNull(message = "配置值不能为空")
    private Long configValueId;

    private Integer sortOrder;
    private String remark;
}
