package com.example.forklift_erp.dto;

public record MachineInboundPurchaseVO(
        MachineInventoryVO machine,
        PurchaseOrderVO purchaseOrder
) {
}
