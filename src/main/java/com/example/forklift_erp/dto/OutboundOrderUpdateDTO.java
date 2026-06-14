package com.example.forklift_erp.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OutboundOrderUpdateDTO {
    private Long version;
    @DecimalMin(value = "0.00", message = "结算价不能为负数")
    private BigDecimal settlementPrice;
    private LocalDate salesDate;
    @DecimalMin(value = "0.00", message = "销售单价不能为负数")
    private BigDecimal salePrice;
    private Boolean paymentSettled;
    private String paymentRemark;
    private Boolean salesReported;
    private Boolean invoiceApplied;
    private LocalDate salesReportDate;
    private LocalDate invoiceApplicationDate;
    private String invoiceStatus;
    private LocalDate invoiceIssuedDate;
    private String registrationStatus;
    private String contractType;
    private String orderRemark;
    private String operator;
}
