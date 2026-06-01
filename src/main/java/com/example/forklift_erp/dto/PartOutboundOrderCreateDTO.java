package com.example.forklift_erp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PartOutboundOrderCreateDTO {
    @NotBlank(message = "配件编码不能为空")
    private String partCode;

    private Long partVersion;

    @NotNull(message = "出库数量不能为空")
    @Min(value = 1, message = "出库数量必须大于0")
    private Integer quantity;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    private BigDecimal settlementPrice;
    private BigDecimal receivableAmount;
    private BigDecimal receivedAmount;
    private LocalDate paymentDueDate;
    private LocalDate lastPaymentDate;
    private Boolean paymentSettled;
    private String paymentRemark;
    private String operator;
    private String orderRemark;
}
