package com.example.forklift_erp.constant;

public enum RepairStatus implements CodedEnum {
    PENDING,
    COMPLETED;

    public static final String VALIDATION_PATTERN = "^(PENDING|COMPLETED)$";

    @Override
    public String code() {
        return name();
    }

    public static String normalizeOrDefault(String value, RepairStatus fallback) {
        return EnumCodes.normalizeOrDefault(RepairStatus.class, value, fallback);
    }
}
