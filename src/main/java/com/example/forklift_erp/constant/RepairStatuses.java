package com.example.forklift_erp.constant;

public final class RepairStatuses {
    public static final String PENDING = RepairStatus.PENDING.code();
    public static final String COMPLETED = RepairStatus.COMPLETED.code();
    public static final String VALIDATION_PATTERN = RepairStatus.VALIDATION_PATTERN;

    private RepairStatuses() {
    }
}
