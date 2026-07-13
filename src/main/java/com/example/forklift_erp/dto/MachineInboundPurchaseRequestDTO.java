package com.example.forklift_erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MachineInboundPurchaseRequestDTO {
    @Valid
    @NotNull(message = "Machine inbound data is required")
    private InboundRequestDTO inbound;

    @Valid
    @NotNull(message = "Purchase order data is required")
    private PurchaseOrderDTO purchaseOrder;
}
