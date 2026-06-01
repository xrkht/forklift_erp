package com.example.forklift_erp.constant;

public final class RoleNames {
    public static final String AUTHORITY_PREFIX = "ROLE_";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";
    public static final String SYSTEM = "SYSTEM";

    private RoleNames() {
    }

    public static String authority(String roleName) {
        return RoleName.authority(roleName);
    }

    public static boolean isSuperAdmin(String roleName) {
        return RoleName.isSuperAdmin(roleName);
    }

    public static boolean isAdmin(String roleName) {
        return RoleName.isAdmin(roleName);
    }

    public static boolean isStandardUser(String roleName) {
        return RoleName.isStandardUser(roleName);
    }

    public static boolean isPrivileged(String roleName) {
        return isAdmin(roleName) || isSuperAdmin(roleName);
    }
}
