package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.JobTag;
import com.example.forklift_erp.constant.RoleNames;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.UserRepository;
import com.example.forklift_erp.security.JwtTokenProvider;
import com.example.forklift_erp.security.PermissionCodes;
import com.example.forklift_erp.security.PermissionService;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.util.ListPageSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private CollaborationService collaborationService;

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class TokenResponse {
        private String token;
        private String username;
        private String jobTag;
        private List<String> roles;
        private List<String> permissions;
    }

    @Data
    public static class UserSummaryResponse {
        private Long id;
        private Long version;
        private String username;
        private String jobTag;
        private Boolean enabled;
        private List<String> roles;
        private List<String> permissions;
        private LocalDateTime createdAt;

        public static UserSummaryResponse fromEntity(User user, Collection<String> permissions) {
            UserSummaryResponse response = new UserSummaryResponse();
            response.setId(user.getId());
            response.setVersion(user.getVersion());
            response.setUsername(user.getUsername());
            response.setJobTag(normalizeJobTag(user));
            response.setEnabled(user.isEnabled());
            response.setRoles(user.getRoles().stream()
                    .map(Role::getName)
                    .sorted()
                    .toList());
            response.setPermissions(permissions.stream().sorted().toList());
            response.setCreatedAt(user.getCreatedAt());
            return response;
        }
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        private List<String> roles;

        private String jobTag;
    }

    @Data
    public static class ResetPasswordRequest {
        private Long version;

        @NotBlank(message = "目标用户名不能为空")
        private String targetUsername;

        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }

    @Data
    public static class UpdateUsernameRequest {
        private Long version;

        @NotBlank(message = "新用户名不能为空")
        private String username;
    }

    @Data
    public static class UpdateUserPasswordRequest {
        private Long version;

        @NotBlank(message = "新密码不能为空")
        private String password;
    }

    @Data
    public static class UpdateJobTagRequest {
        private Long version;

        @NotBlank(message = "职务标签不能为空")
        private String jobTag;
    }

    @Data
    public static class UpdateEnabledRequest {
        private Long version;

        private Boolean enabled;
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号已停用");
        }

        String token = jwtTokenProvider.createToken(user.getUsername());
        TokenResponse resp = new TokenResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setJobTag(normalizeJobTag(user));
        resp.setRoles(user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .toList());
        resp.setPermissions(permissionService.findPermissionCodes(user).stream().toList());

        return Result.success("登录成功", resp);
    }

    @GetMapping("/users")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:read')")
    public Result<?> listUsers(@RequestParam(defaultValue = "false") boolean paged,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean canManagePrivilegedUsers = permissionService.hasPermission(authentication, PermissionCodes.USER_ADMIN);

        if (paged) {
            int normalizedPage = ListPageSupport.page(page);
            int normalizedSize = ListPageSupport.size(size);
            var result = userRepository.searchPage(
                    normalizeKeyword(keyword),
                    canManagePrivilegedUsers,
                    PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "id"))
            );
            return Result.success(com.example.forklift_erp.common.PageResult.of(
                    result.getContent().stream()
                            .map(user -> UserSummaryResponse.fromEntity(user, permissionService.findPermissionCodes(user)))
                            .toList(),
                    normalizedPage,
                    normalizedSize,
                    result.getTotalElements()
            ));
        }

        List<UserSummaryResponse> users = userRepository.findAll().stream()
                .filter(user -> canManagePrivilegedUsers
                        || user.getRoles().stream().allMatch(role -> RoleNames.isStandardUser(role.getName())))
                .sorted(Comparator.comparing(User::getId).reversed())
                .map(user -> UserSummaryResponse.fromEntity(user, permissionService.findPermissionCodes(user)))
                .toList();
        return Result.success(users);
    }

    @GetMapping("/repair-users")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'repair:write')")
    public Result<List<UserSummaryResponse>> listRepairUsers() {
        List<UserSummaryResponse> users = userRepository.findAll().stream()
                .filter(User::isEnabled)
                .filter(user -> JobTag.REPAIR.code().equals(normalizeJobTag(user)))
                .sorted(Comparator.comparing(User::getId).reversed())
                .map(user -> UserSummaryResponse.fromEntity(user, permissionService.findPermissionCodes(user)))
                .toList();
        return Result.success(users);
    }

    @PostMapping("/register")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:write')")
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean canManagePrivilegedUsers = permissionService.hasPermission(authentication, PermissionCodes.USER_ADMIN);
        String username = request.getUsername().trim();

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ResultCode.DATA_DUPLICATE, "用户名已存在");
        }

        Set<Role> assignRoles = new HashSet<>();
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            for (String roleName : request.getRoles()) {
                if (RoleNames.isSuperAdmin(roleName)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "不允许创建超级管理员");
                }
                if (RoleNames.isAdmin(roleName) && !canManagePrivilegedUsers) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "当前权限不允许创建管理员");
                }
                if (!RoleNames.isAdmin(roleName) && !RoleNames.isStandardUser(roleName)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "无效角色: " + roleName);
                }
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new BusinessException(ResultCode.PARAM_ERROR, "无效角色: " + roleName));
                assignRoles.add(role);
            }
        } else {
            Role userRole = roleRepository.findByName(RoleNames.USER)
                    .orElseThrow(() -> new BusinessException(ResultCode.SYSTEM_ERROR, "默认角色 USER 不存在"));
            assignRoles.add(userRole);
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRoles(assignRoles);
        newUser.setJobTag(normalizeJobTag(request.getJobTag(), assignRoles));
        newUser.setEnabled(true);
        collaborationService.stampWrite(newUser);

        User savedUser = userRepository.save(newUser);
        operationAuditService.record("用户管理", "CREATE", RoleNames.USER, savedUser.getId(),
                savedUser.getUsername(), roleNames(savedUser), "创建用户", null, null, RoleNames.USER, savedUser.getId());
        return Result.success("用户创建成功");
    }

    @PutMapping("/reset-password")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:write')")
    @Transactional
    public Result<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User targetUser = userRepository.findByUsernameForUpdate(request.getTargetUsername())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "目标用户不存在"));

        boolean targetIsPrivileged = targetUser.getRoles().stream()
                .anyMatch(r -> RoleNames.isPrivileged(r.getName()));
        boolean canManagePrivilegedUsers = permissionService.hasPermission(auth, PermissionCodes.USER_ADMIN);

        if (targetIsPrivileged && !canManagePrivilegedUsers) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前权限不允许重置管理员密码");
        }

        collaborationService.validateWrite(targetUser, request.getVersion());
        targetUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        collaborationService.stampWrite(targetUser);
        User savedUser = userRepository.save(targetUser);
        operationAuditService.record("用户管理", "PASSWORD_RESET", RoleNames.USER, savedUser.getId(),
                savedUser.getUsername(), roleNames(savedUser), "重置用户密码", null, null, RoleNames.USER, savedUser.getId());
        return Result.success("密码重置成功");
    }

    @PutMapping("/users/{id}/username")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:admin')")
    @Transactional
    public Result<?> updateUsername(@PathVariable Long id, @Valid @RequestBody UpdateUsernameRequest request) {
        User targetUser = findUserByIdForUpdate(id);
        rejectSuperAdminTarget(targetUser, "超级管理员账号不允许修改用户名");

        collaborationService.validateWrite(targetUser, request.getVersion());
        String newUsername = request.getUsername().trim();
        if (userRepository.existsByUsernameAndIdNot(newUsername, id)) {
            throw new BusinessException(ResultCode.DATA_DUPLICATE, "用户名已存在");
        }

        String oldUsername = targetUser.getUsername();
        targetUser.setUsername(newUsername);
        collaborationService.stampWrite(targetUser);
        User savedUser = userRepository.save(targetUser);
        operationAuditService.record("用户管理", "USERNAME_UPDATE", RoleNames.USER, savedUser.getId(),
                savedUser.getUsername(), roleNames(savedUser), oldUsername + " -> " + savedUser.getUsername(),
                null, null, RoleNames.USER, savedUser.getId());
        return Result.success("用户名修改成功");
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:admin')")
    @Transactional
    public Result<?> updateUserPassword(@PathVariable Long id, @Valid @RequestBody UpdateUserPasswordRequest request) {
        User targetUser = findUserByIdForUpdate(id);
        rejectSuperAdminTarget(targetUser, "超级管理员账号不允许修改密码");

        collaborationService.validateWrite(targetUser, request.getVersion());
        targetUser.setPassword(passwordEncoder.encode(request.getPassword()));
        collaborationService.stampWrite(targetUser);
        User savedUser = userRepository.save(targetUser);
        operationAuditService.record("用户管理", "PASSWORD_UPDATE", RoleNames.USER, savedUser.getId(),
                savedUser.getUsername(), roleNames(savedUser), "修改用户密码", null, null, RoleNames.USER, savedUser.getId());
        return Result.success("用户密码修改成功");
    }

    @PutMapping("/users/{id}/job-tag")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:write')")
    @Transactional
    public Result<UserSummaryResponse> updateUserJobTag(@PathVariable Long id, @Valid @RequestBody UpdateJobTagRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User targetUser = findUserByIdForUpdate(id);
        boolean targetIsPrivileged = isPrivileged(targetUser);
        boolean canManagePrivilegedUsers = permissionService.hasPermission(authentication, PermissionCodes.USER_ADMIN);

        if (targetIsPrivileged && !canManagePrivilegedUsers) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前权限不允许修改管理员职务标签");
        }

        collaborationService.validateWrite(targetUser, request.getVersion());
        String oldTag = normalizeJobTag(targetUser);
        targetUser.setJobTag(normalizeJobTag(request.getJobTag(), targetUser.getRoles()));
        collaborationService.stampWrite(targetUser);
        User savedUser = userRepository.save(targetUser);
        operationAuditService.record("用户管理", "JOB_TAG_UPDATE", RoleNames.USER, savedUser.getId(),
                savedUser.getUsername(), roleNames(savedUser), oldTag + " -> " + normalizeJobTag(savedUser),
                null, null, RoleNames.USER, savedUser.getId());
        return Result.success("职务标签已更新", UserSummaryResponse.fromEntity(savedUser, permissionService.findPermissionCodes(savedUser)));
    }

    @PutMapping("/users/{id}/enabled")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:write')")
    @Transactional
    public Result<UserSummaryResponse> updateUserEnabled(@PathVariable Long id, @Valid @RequestBody UpdateEnabledRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User targetUser = findUserByIdForUpdate(id);
        rejectSuperAdminTarget(targetUser, "超级管理员账号不允许停用");
        boolean targetIsPrivileged = isPrivileged(targetUser);
        boolean canManagePrivilegedUsers = permissionService.hasPermission(authentication, PermissionCodes.USER_ADMIN);

        if (targetUser.getUsername().equals(authentication.getName())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能停用当前登录账号");
        }
        if (targetIsPrivileged && !canManagePrivilegedUsers) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前权限不允许修改管理员启用状态");
        }
        if (request.getEnabled() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "启用状态不能为空");
        }

        collaborationService.validateWrite(targetUser, request.getVersion());
        boolean oldEnabled = targetUser.isEnabled();
        targetUser.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        collaborationService.stampWrite(targetUser);
        User savedUser = userRepository.save(targetUser);
        operationAuditService.record("用户管理", savedUser.isEnabled() ? "ENABLE" : "DISABLE", RoleNames.USER, savedUser.getId(),
                savedUser.getUsername(), roleNames(savedUser), oldEnabled + " -> " + savedUser.isEnabled(),
                null, null, RoleNames.USER, savedUser.getId());
        return Result.success("用户状态已更新", UserSummaryResponse.fromEntity(savedUser, permissionService.findPermissionCodes(savedUser)));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'user:admin')")
    @Transactional
    public Result<?> deleteUser(@PathVariable Long id, @RequestParam(required = false) Long version) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User targetUser = findUserByIdForUpdate(id);

        if (targetUser.getUsername().equals(auth.getName())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能删除当前登录账号");
        }
        rejectSuperAdminTarget(targetUser, "超级管理员账号不允许删除");

        operationAuditService.record("用户管理", "DELETE", RoleNames.USER, targetUser.getId(),
                targetUser.getUsername(), roleNames(targetUser), "删除用户", null, null, RoleNames.USER, targetUser.getId());
        collaborationService.validateWrite(targetUser, version);
        userRepository.delete(targetUser);
        return Result.success("用户删除成功");
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
    }

    private User findUserByIdForUpdate(Long id) {
        return userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "用户不存在"));
    }

    private void rejectSuperAdminTarget(User user, String message) {
        if (user.getRoles().stream().anyMatch(r -> RoleNames.isSuperAdmin(r.getName()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    private boolean isPrivileged(User user) {
        return user.getRoles().stream()
                .anyMatch(role -> RoleNames.isPrivileged(role.getName()));
    }

    private static String normalizeJobTag(User user) {
        return normalizeJobTag(user.getJobTag(), user.getRoles());
    }

    private static String normalizeJobTag(String value, Collection<Role> roles) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (JobTag.MANAGEMENT.code().equals(normalized) || JobTag.CLERK.code().equals(normalized) || JobTag.REPAIR.code().equals(normalized)) {
            return normalized;
        }
        return roles.stream().anyMatch(role -> RoleNames.isPrivileged(role.getName()))
                ? JobTag.MANAGEMENT.code()
                : JobTag.CLERK.code();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String roleNames(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(java.util.stream.Collectors.joining("/"));
    }
}
