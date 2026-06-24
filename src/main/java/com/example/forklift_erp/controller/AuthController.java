package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.constant.JobTag;
import com.example.forklift_erp.constant.RoleNames;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.security.PermissionCodes;
import com.example.forklift_erp.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

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
            response.setJobTag(normalizeJobTag(user.getJobTag(), user.getRoles()));
            response.setEnabled(user.isEnabled());
            response.setRoles(user.getRoles().stream()
                    .map(Role::getName)
                    .sorted()
                    .toList());
            response.setPermissions(permissions.stream().sorted().toList());
            response.setCreatedAt(user.getCreatedAt());
            return response;
        }

        private static String normalizeJobTag(String value, Collection<Role> roles) {
            String normalized = value == null ? "" : value.trim().toUpperCase();
            if (JobTag.MANAGEMENT.code().equals(normalized)
                    || JobTag.CLERK.code().equals(normalized)
                    || JobTag.REPAIR.code().equals(normalized)) {
                return normalized;
            }
            return roles.stream().anyMatch(role -> RoleNames.isPrivileged(role.getName()))
                    ? JobTag.MANAGEMENT.code()
                    : JobTag.CLERK.code();
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
        return Result.success("登录成功", authService.login(request));
    }

    @GetMapping("/users")
    @PreAuthorize(PermissionCodes.HAS_USER_READ)
    public Result<?> listUsers(@RequestParam(defaultValue = "false") boolean paged,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        return Result.success(authService.listUsers(paged, keyword, page, size, authentication()));
    }

    @GetMapping("/repair-users")
    @PreAuthorize(PermissionCodes.HAS_REPAIR_WRITE)
    public Result<List<UserSummaryResponse>> listRepairUsers() {
        return Result.success(authService.listRepairUsers());
    }

    @PostMapping("/register")
    @PreAuthorize(PermissionCodes.HAS_USER_WRITE)
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request, authentication());
        return Result.success("用户创建成功");
    }

    @PutMapping("/reset-password")
    @PreAuthorize(PermissionCodes.HAS_USER_WRITE)
    public Result<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request, authentication());
        return Result.success("密码重置成功");
    }

    @PutMapping("/users/{id}/username")
    @PreAuthorize(PermissionCodes.HAS_USER_ADMIN)
    public Result<?> updateUsername(@PathVariable Long id, @Valid @RequestBody UpdateUsernameRequest request) {
        authService.updateUsername(id, request);
        return Result.success("用户名修改成功");
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize(PermissionCodes.HAS_USER_ADMIN)
    public Result<?> updateUserPassword(@PathVariable Long id, @Valid @RequestBody UpdateUserPasswordRequest request) {
        authService.updateUserPassword(id, request);
        return Result.success("用户密码修改成功");
    }

    @PutMapping("/users/{id}/job-tag")
    @PreAuthorize(PermissionCodes.HAS_USER_WRITE)
    public Result<UserSummaryResponse> updateUserJobTag(@PathVariable Long id, @Valid @RequestBody UpdateJobTagRequest request) {
        return Result.success("职务标签已更新", authService.updateUserJobTag(id, request, authentication()));
    }

    @PutMapping("/users/{id}/enabled")
    @PreAuthorize(PermissionCodes.HAS_USER_WRITE)
    public Result<UserSummaryResponse> updateUserEnabled(@PathVariable Long id, @Valid @RequestBody UpdateEnabledRequest request) {
        return Result.success("用户状态已更新", authService.updateUserEnabled(id, request, authentication()));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize(PermissionCodes.HAS_USER_ADMIN)
    public Result<?> deleteUser(@PathVariable Long id, @RequestParam(required = false) Long version) {
        authService.deleteUser(id, version, authentication());
        return Result.success("用户删除成功");
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
