package com.example.forklift_erp.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OutboundOrderUpdateDTO {
    private Long version;
    @DecimalMin(value = "0.00", message = "\u7ed3\u7b97\u4ef7\u4e0d\u80fd\u4e3a\u8d1f\u6570")
    private BigDecimal settlementPrice;
    private LocalDate salesDate;
    @DecimalMin(value = "0.00", message = "\u9500\u552e\u4ef7\u4e0d\u80fd\u4e3a\u8d1f\u6570")
    private BigDecimal salePrice;
    @DecimalMin(value = "0.00", message = "\u5e94\u6536\u91d1\u989d\u4e0d\u80fd\u4e3a\u8d1f\u6570")
    private BigDecimal receivableAmount;
    @DecimalMin(value = "0.00", message = "\u5df2\u6536\u91d1\u989d\u4e0d\u80fd\u4e3a\u8d1f\u6570")
    private BigDecimal receivedAmount;
    private LocalDate paymentDueDate;
    private LocalDate lastPaymentDate;
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
