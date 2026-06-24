package com.example.forklift_erp.security;

public final class PermissionCodes {
    public static final String VEHICLE_WRITE = "vehicle:write";
    public static final String PART_WRITE = "part:write";
    public static final String REPAIR_WRITE = "repair:write";
    public static final String CONFIG_WRITE = "config:write";
    public static final String REPLACE_WRITE = "replace:write";
    public static final String STOCK_ADJUST = "stock:adjust";
    public static final String LOG_READ = "log:read";
    public static final String USER_READ = "user:read";
    public static final String USER_WRITE = "user:write";
    public static final String USER_ADMIN = "user:admin";

    public static final String HAS_SUPER_ADMIN = "hasRole('SUPER_ADMIN')";
    public static final String HAS_ADMIN_OR_SUPER_ADMIN = "hasAnyRole('ADMIN','SUPER_ADMIN')";
    public static final String HAS_VEHICLE_WRITE = "@permissionService.hasPermission(authentication, '" + VEHICLE_WRITE + "')";
    public static final String HAS_PART_WRITE = "@permissionService.hasPermission(authentication, '" + PART_WRITE + "')";
    public static final String HAS_REPAIR_WRITE = "@permissionService.hasPermission(authentication, '" + REPAIR_WRITE + "')";
    public static final String HAS_CONFIG_WRITE = "@permissionService.hasPermission(authentication, '" + CONFIG_WRITE + "')";
    public static final String HAS_REPLACE_WRITE = "@permissionService.hasPermission(authentication, '" + REPLACE_WRITE + "')";
    public static final String HAS_STOCK_ADJUST = "@permissionService.hasPermission(authentication, '" + STOCK_ADJUST + "')";
    public static final String HAS_LOG_READ = "@permissionService.hasPermission(authentication, '" + LOG_READ + "')";
    public static final String HAS_USER_READ = "@permissionService.hasPermission(authentication, '" + USER_READ + "')";
    public static final String HAS_USER_WRITE = "@permissionService.hasPermission(authentication, '" + USER_WRITE + "')";
    public static final String HAS_USER_ADMIN = "@permissionService.hasPermission(authentication, '" + USER_ADMIN + "')";
    public static final String HAS_ATTACHMENT_ACCESS = "@permissionService.hasAnyPermission(authentication, '"
            + VEHICLE_WRITE + "', '" + PART_WRITE + "', '" + REPAIR_WRITE + "', '" + STOCK_ADJUST + "')";
    public static final String HAS_EXPORT_ACCESS = "@permissionService.hasAnyPermission(authentication, '"
            + VEHICLE_WRITE + "', '" + PART_WRITE + "', '" + REPAIR_WRITE + "', '" + STOCK_ADJUST + "', '" + LOG_READ + "')";
    public static final String HAS_LIST_SUMMARY_ACCESS = "@permissionService.hasAnyPermission(authentication, '"
            + STOCK_ADJUST + "', '" + VEHICLE_WRITE + "', '" + REPAIR_WRITE + "', '" + LOG_READ + "')";

    private PermissionCodes() {
    }
}
