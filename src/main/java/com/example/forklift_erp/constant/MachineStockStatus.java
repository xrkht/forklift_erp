package com.example.forklift_erp.constant;

public enum MachineStockStatus implements CodedEnum {
    IN_STOCK,
    PENDING_INBOUND,
    OUTBOUND,
    PENDING_MODIFICATION,
    MODIFYING,
    PENDING_OUTBOUND;

    @Override
    public String code() {
        return name();
    }
}
