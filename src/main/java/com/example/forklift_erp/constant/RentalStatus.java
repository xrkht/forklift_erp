package com.example.forklift_erp.constant;

public enum RentalStatus implements CodedEnum {
    ACTIVE,
    RETURNED;

    @Override
    public String code() {
        return name();
    }

    public static String normalizeOrDefault(String value, RentalStatus fallback) {
        return EnumCodes.normalizeOrDefault(RentalStatus.class, value, fallback);
    }

    public static boolean isValid(String value) {
        return EnumCodes.isValid(RentalStatus.class, value);
    }
}
