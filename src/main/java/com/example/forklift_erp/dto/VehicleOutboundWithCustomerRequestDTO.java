package com.example.forklift_erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehicleOutboundWithCustomerRequestDTO {
    @Valid
    @NotNull(message = "Customer data is required")
    private CustomerDTO customer;

    @NotNull(message = "Vehicle outbound data is required")
    private VehicleOutboundOrderCreateDTO outboundOrder;
}
