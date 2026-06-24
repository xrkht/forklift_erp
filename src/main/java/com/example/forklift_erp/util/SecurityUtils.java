package com.example.forklift_erp.util;

import com.example.forklift_erp.constant.RoleNames;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Arrays;

public class SecurityUtils {

    /**
     * 判断当前用户是否拥有任意指定的角色
     */
    public static boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return Arrays.stream(roles).anyMatch(role ->
                auth.getAuthorities().stream().anyMatch(granted ->
                        granted.getAuthority().equals(RoleNames.authority(role))));
    }

    /**
     * 判断当前用户是否为管理员或超级管理员
     */
    public static boolean isAdminOrSuperAdmin() {
        return hasAnyRole(RoleNames.ADMIN, RoleNames.SUPER_ADMIN);
    }

    public static int currentRolePriority() {
        if (hasAnyRole(RoleNames.SUPER_ADMIN)) return 300;
        if (hasAnyRole(RoleNames.ADMIN)) return 200;
        if (hasAnyRole(RoleNames.USER)) return 100;
        return 0;
    }

    public static String currentHighestRole() {
        if (hasAnyRole(RoleNames.SUPER_ADMIN)) return RoleNames.SUPER_ADMIN;
        if (hasAnyRole(RoleNames.ADMIN)) return RoleNames.ADMIN;
        if (hasAnyRole(RoleNames.USER)) return RoleNames.USER;
        return RoleNames.SYSTEM;
    }

    public static boolean hasAnyPermission(String... permissions) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return Arrays.stream(permissions).anyMatch(permission ->
                auth.getAuthorities().stream().anyMatch(granted ->
                        granted.getAuthority().equals("PERM_" + permission)));
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }
}
