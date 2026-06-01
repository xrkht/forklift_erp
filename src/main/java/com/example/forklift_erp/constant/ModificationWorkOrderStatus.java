package com.example.forklift_erp.constant;

public enum ModificationWorkOrderStatus implements CodedEnum {
    WAITING_PARTS,
    IN_PROGRESS,
    COMPLETED,
    CANCELED;

    @Override
    public String code() {
        return name();
    }
}
