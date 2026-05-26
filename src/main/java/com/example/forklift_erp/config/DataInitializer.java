package com.example.forklift_erp.config;

import com.example.forklift_erp.entity.Permission;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.PermissionRepository;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.UserRepository;
import com.example.forklift_erp.security.PermissionCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Map<String, String> ROLE_DESCRIPTIONS = Map.of(
            "SUPER_ADMIN", "Super administrator",
            "ADMIN", "Administrator",
            "USER", "Standard user"
    );

    private static final Map<String, String> PERMISSION_DESCRIPTIONS = new LinkedHashMap<>();

    private static final Map<String, Set<String>> ROLE_PERMISSION_CODES = Map.of(
            "SUPER_ADMIN", Set.of(
                    PermissionCodes.VEHICLE_WRITE,
                    PermissionCodes.PART_WRITE,
                    PermissionCodes.REPAIR_WRITE,
                    PermissionCodes.CONFIG_WRITE,
                    PermissionCodes.REPLACE_WRITE,
                    PermissionCodes.STOCK_ADJUST,
                    PermissionCodes.LOG_READ,
                    PermissionCodes.USER_READ,
                    PermissionCodes.USER_WRITE,
                    PermissionCodes.USER_ADMIN
            ),
            "ADMIN", Set.of(
                    PermissionCodes.VEHICLE_WRITE,
                    PermissionCodes.PART_WRITE,
                    PermissionCodes.REPAIR_WRITE,
                    PermissionCodes.CONFIG_WRITE,
                    PermissionCodes.REPLACE_WRITE,
                    PermissionCodes.STOCK_ADJUST,
                    PermissionCodes.LOG_READ,
                    PermissionCodes.USER_READ,
                    PermissionCodes.USER_WRITE
            ),
            "USER", Set.of(
                    PermissionCodes.VEHICLE_WRITE,
                    PermissionCodes.PART_WRITE,
                    PermissionCodes.REPAIR_WRITE,
                    PermissionCodes.CONFIG_WRITE,
                    PermissionCodes.REPLACE_WRITE,
                    PermissionCodes.STOCK_ADJUST
            )
    );

    static {
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.VEHICLE_WRITE, "Create, update, delete and lock vehicle inventory");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.PART_WRITE, "Create, update and delete part inventory");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.REPAIR_WRITE, "Create, update and delete repair records");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.CONFIG_WRITE, "Maintain configuration dictionaries");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.REPLACE_WRITE, "Perform vehicle configuration and part replacement");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.STOCK_ADJUST, "Adjust vehicle and part stock quantities");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.LOG_READ, "View operation logs");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.USER_READ, "View managed users");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.USER_WRITE, "Create users and reset standard user passwords");
        PERMISSION_DESCRIPTIONS.put(PermissionCodes.USER_ADMIN, "Rename, reset and delete privileged users");
    }

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, Role> roles = ensureRoles();
        Map<String, Permission> permissions = ensurePermissions();
        assignDefaultPermissions(roles, permissions);
        ensureDefaultSuperAdmin(roles.get("SUPER_ADMIN"));
    }

    private Map<String, Role> ensureRoles() {
        Map<String, Role> roles = new HashMap<>();
        ROLE_DESCRIPTIONS.forEach((name, description) -> roles.put(name, roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(name);
                    role.setDescription(description);
                    return roleRepository.save(role);
                })));
        return roles;
    }

    private Map<String, Permission> ensurePermissions() {
        Map<String, Permission> permissions = new HashMap<>();
        PERMISSION_DESCRIPTIONS.forEach((code, description) -> permissions.put(code, permissionRepository.findByCode(code)
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setCode(code);
                    permission.setDescription(description);
                    return permissionRepository.save(permission);
                })));
        return permissions;
    }

    private void assignDefaultPermissions(Map<String, Role> roles, Map<String, Permission> permissions) {
        ROLE_PERMISSION_CODES.forEach((roleName, permissionCodes) -> {
            Role role = roles.get(roleName);
            if (role == null) {
                return;
            }
            boolean removed = false;
            if ("USER".equals(roleName)) {
                removed = role.getPermissions().removeIf(permission -> !permissionCodes.contains(permission.getCode()));
            }
            boolean added = permissionCodes.stream()
                    .map(permissions::get)
                    .filter(permission -> permission != null && !role.getPermissions().contains(permission))
                    .peek(role.getPermissions()::add)
                    .count() > 0;
            if (added || removed) {
                roleRepository.save(role);
            }
        });
    }

    private void ensureDefaultSuperAdmin(Role superAdminRole) {
        if (!userRepository.existsByUsername("admin")) {
            User superAdmin = new User();
            superAdmin.setUsername("admin");
            superAdmin.setPassword(passwordEncoder.encode("admin123"));
            superAdmin.setRoles(Set.of(superAdminRole));
            userRepository.save(superAdmin);
        }
    }
}
