package com.example.forklift_erp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VehicleOutboundOrderCreateDTO {
    @NotNull(message = "出库车辆不能为空")
    private Long machineId;

    private Long machineVersion;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotNull(message = "结算价不能为空")
    private BigDecimal settlementPrice;

    private String operator;
    private String orderRemark;
}
