package com.example.forklift_erp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RentalRecordUpdateDTO {
    private Long version;

    @NotNull(message = "租赁去向不能为空")
    private Long customerId;

    @Size(max = 255)
    private String destination;

    @DecimalMin(value = "0.00", inclusive = false, message = "月租价格必须大于0")
    private BigDecimal monthlyRentalPrice;

    @DecimalMin(value = "0.00", message = "租金不能为负数")
    private BigDecimal rentalPrice;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private String operator;

    private String remark;
}
