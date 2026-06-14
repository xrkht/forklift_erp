package com.example.forklift_erp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VehicleOutboundOrderCreateDTO {
    @NotNull(message = "出库车辆不能为空")
    private Long machineId;

    private Long machineVersion;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotNull(message = "结算价不能为空")
    @DecimalMin(value = "0.00", message = "结算价不能为负数")
    private BigDecimal settlementPrice;

    private LocalDate salesDate;
    @DecimalMin(value = "0.00", message = "销售单价不能为负数")
    private BigDecimal salePrice;
    private Boolean paymentSettled;
    private String paymentRemark;
    private Boolean salesReported;
    private LocalDate salesReportDate;
    private Boolean invoiceApplied;
    private LocalDate invoiceApplicationDate;
    private String invoiceStatus;
    private LocalDate invoiceIssuedDate;
    private String registrationStatus;
    private String contractType;

    private String operator;
    private String orderRemark;
}
