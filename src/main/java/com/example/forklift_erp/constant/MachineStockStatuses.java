package com.example.forklift_erp.constant;

public final class MachineStockStatuses {
    public static final String IN_STOCK = MachineStockStatus.IN_STOCK.code();
    public static final String PENDING_INBOUND = MachineStockStatus.PENDING_INBOUND.code();
    public static final String OUTBOUND = MachineStockStatus.OUTBOUND.code();
    public static final String PENDING_MODIFICATION = MachineStockStatus.PENDING_MODIFICATION.code();
    public static final String MODIFYING = MachineStockStatus.MODIFYING.code();
    public static final String PENDING_OUTBOUND = MachineStockStatus.PENDING_OUTBOUND.code();

    private MachineStockStatuses() {
    }
}
