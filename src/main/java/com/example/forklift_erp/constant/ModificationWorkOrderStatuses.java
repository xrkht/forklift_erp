package com.example.forklift_erp.constant;

public final class ModificationWorkOrderStatuses {
    public static final String WAITING_PARTS = ModificationWorkOrderStatus.WAITING_PARTS.code();
    public static final String IN_PROGRESS = ModificationWorkOrderStatus.IN_PROGRESS.code();
    public static final String COMPLETED = ModificationWorkOrderStatus.COMPLETED.code();
    public static final String CANCELED = ModificationWorkOrderStatus.CANCELED.code();

    private ModificationWorkOrderStatuses() {
    }
}
