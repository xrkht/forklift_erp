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

    private PermissionCodes() {
    }
}
