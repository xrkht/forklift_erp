package com.example.forklift_erp.dto;

public record VehicleOutboundWithCustomerVO(
        CustomerVO customer,
        OutboundOrderVO outboundOrder
) {
}
