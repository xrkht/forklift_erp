package com.example.forklift_erp.util;

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
                        granted.getAuthority().equals("ROLE_" + role)));
    }

    /**
     * 判断当前用户是否为管理员或超级管理员
     */
    public static boolean isAdminOrSuperAdmin() {
        return hasAnyRole("ADMIN", "SUPER_ADMIN");
    }

    public static int currentRolePriority() {
        if (hasAnyRole("SUPER_ADMIN")) return 300;
        if (hasAnyRole("ADMIN")) return 200;
        if (hasAnyRole("USER")) return 100;
        return 0;
    }

    public static String currentHighestRole() {
        if (hasAnyRole("SUPER_ADMIN")) return "SUPER_ADMIN";
        if (hasAnyRole("ADMIN")) return "ADMIN";
        if (hasAnyRole("USER")) return "USER";
        return "SYSTEM";
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
