package com.example.forklift_erp.security;

import com.example.forklift_erp.constant.RoleNames;
import com.example.forklift_erp.entity.Permission;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.PermissionRepository;
import com.example.forklift_erp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service("permissionService")
public class PermissionService {
    private static final String PERMISSION_AUTHORITY_PREFIX = "PERM_";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    public boolean hasPermission(Authentication authentication, String code) {
        return hasAnyPermission(authentication, code);
    }

    public boolean hasAnyPermission(Authentication authentication, String... codes) {
        if (authentication == null || !authentication.isAuthenticated() || codes == null || codes.length == 0) {
            return false;
        }
        if (hasAuthority(authentication, RoleNames.authority(RoleNames.SUPER_ADMIN))) {
            return true;
        }
        Set<String> required = Arrays.stream(codes)
                .filter(Objects::nonNull)
                .map(code -> PERMISSION_AUTHORITY_PREFIX + code)
                .collect(Collectors.toSet());
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> required.contains(authority.getAuthority()));
    }

    @Transactional(readOnly = true)
    public Set<String> findPermissionCodes(String username) {
        return userRepository.findByUsername(username)
                .map(this::findPermissionCodes)
                .orElseGet(Set::of);
    }

    @Transactional(readOnly = true)
    public Set<String> findPermissionCodes(User user) {
        if (user == null) {
            return Set.of();
        }
        if (user.getRoles().stream().anyMatch(role -> RoleNames.isSuperAdmin(role.getName()))) {
            return permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasAuthority(Authentication authentication, String authorityName) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authorityName.equals(authority.getAuthority()));
    }
}
