package com.example.forklift_erp.constant;

public enum JobTag implements CodedEnum {
    MANAGEMENT,
    CLERK,
    REPAIR;

    @Override
    public String code() {
        return name();
    }

    public static boolean isValid(String value) {
        return EnumCodes.isValid(JobTag.class, value);
    }
}
