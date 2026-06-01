package com.example.forklift_erp.dto;

import com.example.forklift_erp.constant.PartChangeActions;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartReplaceRequestDTO {
    private Long machineVersion;
    private Long machineConfigVersion;
    private Long newPartVersion;

    @NotNull(message = "车辆ID不能为空")
    private Long machineId;

    @NotNull(message = "车辆配置ID不能为空")
    private Long machineConfigId;

    @NotNull(message = "新配件ID不能为空")
    private Long newPartId;

    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity = 1;

    private String oldPartAction = PartChangeActions.STOCK_IN;
    private String stockMovementSourceType;
    private Long stockMovementSourceId;
    private String operator;
    private String remark;
}
