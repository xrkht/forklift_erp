package com.example.forklift_erp.constant;

public enum RoleName implements CodedEnum {
    SUPER_ADMIN,
    ADMIN,
    USER,
    SYSTEM;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    @Override
    public String code() {
        return name();
    }

    public String authority() {
        return AUTHORITY_PREFIX + code();
    }

    public static String authority(String roleName) {
        return AUTHORITY_PREFIX + roleName;
    }

    public static boolean isSuperAdmin(String roleName) {
        return SUPER_ADMIN.code().equals(roleName);
    }

    public static boolean isAdmin(String roleName) {
        return ADMIN.code().equals(roleName);
    }

    public static boolean isStandardUser(String roleName) {
        return USER.code().equals(roleName);
    }

    public static boolean isPrivileged(String roleName) {
        return isAdmin(roleName) || isSuperAdmin(roleName);
    }
}
