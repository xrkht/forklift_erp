package com.example.forklift_erp.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OutboundOrderUpdateDTO {
    private Long version;
    private Boolean paymentSettled;
    private Boolean salesReported;
    private Boolean invoiceApplied;
    private LocalDate salesReportDate;
    private LocalDate invoiceApplicationDate;
    private String orderRemark;
    private String operator;
}
