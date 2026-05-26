package com.example.forklift_erp;

import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTests {

    private static final String PASSWORD = "CodexTest123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> usersToCleanup = new ArrayList<>();
    private String superUsername;
    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        superUsername = uniqueUsername("super");
        createUserDirectly(superUsername, PASSWORD, "SUPER_ADMIN");
        superToken = login(superUsername, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        for (String username : usersToCleanup.reversed()) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
        usersToCleanup.clear();
    }

    @Test
    void superAdminCanRenameResetPasswordAndDeleteUser() throws Exception {
        String username = uniqueUsername("user");
        String renamedUsername = username + "_renamed";
        String newPassword = "CodexNew123!";

        registerViaApi(superToken, username, PASSWORD, "USER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long userId = findUserId(username);
        Long userVersion = findUserVersion(username);

        mockMvc.perform(put("/api/auth/users/{id}/username", userId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", renamedUsername, "version", userVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        usersToCleanup.add(renamedUsername);

        assertThat(userRepository.findByUsername(renamedUsername)).isPresent();
        assertThat(login(renamedUsername, PASSWORD)).isNotBlank();

        mockMvc.perform(put("/api/auth/users/{id}/password", userId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", newPassword, "version", findUserVersion(renamedUsername)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        loginExpectingStatus(renamedUsername, PASSWORD, 401);
        assertThat(login(renamedUsername, newPassword)).isNotBlank();

        mockMvc.perform(delete("/api/auth/users/{id}", userId)
                        .header("Authorization", bearer(superToken))
                        .param("version", String.valueOf(findUserVersion(renamedUsername))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        loginExpectingStatus(renamedUsername, newPassword, 401);
    }

    @Test
    void normalAdminCannotUseSuperAdminOnlyUserMaintenanceEndpoints() throws Exception {
        String adminUsername = uniqueUsername("admin");
        String userUsername = uniqueUsername("user");

        registerViaApi(superToken, adminUsername, PASSWORD, "ADMIN")
                .andExpect(status().isOk());
        registerViaApi(superToken, userUsername, PASSWORD, "USER")
                .andExpect(status().isOk());

        String adminToken = login(adminUsername, PASSWORD);
        Long userId = findUserId(userUsername);

        mockMvc.perform(put("/api/auth/users/{id}/username", userId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", userUsername + "_blocked"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(delete("/api/auth/users/{id}", userId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminCannotResetSuperAdminPasswordThroughLegacyEndpoint() throws Exception {
        String adminUsername = uniqueUsername("admin");
        registerViaApi(superToken, adminUsername, PASSWORD, "ADMIN")
                .andExpect(status().isOk());
        String adminToken = login(adminUsername, PASSWORD);

        mockMvc.perform(put("/api/auth/reset-password")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "targetUsername", superUsername,
                                "newPassword", "Blocked123!"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertThat(login(superUsername, PASSWORD)).isNotBlank();
    }

    @Test
    void adminUserListIsLimitedToNormalUsers() throws Exception {
        String adminUsername = uniqueUsername("admin");
        String userUsername = uniqueUsername("user");

        registerViaApi(superToken, adminUsername, PASSWORD, "ADMIN")
                .andExpect(status().isOk());
        registerViaApi(superToken, userUsername, PASSWORD, "USER")
                .andExpect(status().isOk());

        String adminToken = login(adminUsername, PASSWORD);
        String response = mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode users = objectMapper.readTree(response).path("data");
        for (JsonNode user : users) {
            List<String> roles = new ArrayList<>();
            user.path("roles").forEach(role -> roles.add(role.asText()));
            assertThat(roles).doesNotContain("ADMIN", "SUPER_ADMIN");
        }
        assertThat(response).contains(userUsername);
        assertThat(response).doesNotContain(adminUsername);
        assertThat(response).doesNotContain(superUsername);
    }

    @Test
    void normalUserKeepsBusinessPermissionsButCannotReadAdminModules() throws Exception {
        String adminUsername = uniqueUsername("admin");
        String userUsername = uniqueUsername("user");

        registerViaApi(superToken, adminUsername, PASSWORD, "ADMIN")
                .andExpect(status().isOk());
        registerViaApi(superToken, userUsername, PASSWORD, "USER")
                .andExpect(status().isOk());

        JsonNode superLogin = objectMapper.readTree(loginExpectingStatus(superUsername, PASSWORD, 200)).path("data");
        JsonNode adminLogin = objectMapper.readTree(loginExpectingStatus(adminUsername, PASSWORD, 200)).path("data");
        JsonNode userLogin = objectMapper.readTree(loginExpectingStatus(userUsername, PASSWORD, 200)).path("data");

        List<String> superPermissions = textValues(superLogin.path("permissions"));
        List<String> adminPermissions = textValues(adminLogin.path("permissions"));
        List<String> userPermissions = textValues(userLogin.path("permissions"));

        assertThat(superPermissions).contains("user:admin", "user:write", "log:read");
        assertThat(adminPermissions).contains("user:write", "log:read");
        assertThat(adminPermissions).doesNotContain("user:admin");
        assertThat(userPermissions).contains(
                "vehicle:write",
                "part:write",
                "repair:write",
                "config:write",
                "replace:write",
                "stock:adjust"
        );
        assertThat(userPermissions).doesNotContain("log:read", "user:read", "user:write", "user:admin");

        String userToken = userLogin.path("token").asText();
        mockMvc.perform(get("/api/inventory")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/parts")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/repairs")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/outbound-orders")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/modification-work-orders")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/config/items")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/logs")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/statistics/finance")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void superAdminCanReadAuditLogsAndFinanceStatisticsButNormalUserCannot() throws Exception {
        String userUsername = uniqueUsername("user");

        registerViaApi(superToken, userUsername, PASSWORD, "USER")
                .andExpect(status().isOk());

        String auditResponse = mockMvc.perform(get("/api/logs")
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(auditResponse).contains("用户管理", userUsername);

        mockMvc.perform(get("/api/statistics/finance")
                        .param("year", "2026")
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.selectedYear").value(2026))
                .andExpect(jsonPath("$.data.monthlyFinance").isArray())
                .andExpect(jsonPath("$.data.stockValues").isArray());

        String userToken = login(userUsername, PASSWORD);
        mockMvc.perform(get("/api/statistics/finance")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private void createUserDirectly(String username, String password, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(roleName);
                    newRole.setDescription(roleName);
                    return roleRepository.save(newRole);
                });

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        userRepository.save(user);
        usersToCleanup.add(username);
    }

    private org.springframework.test.web.servlet.ResultActions registerViaApi(
            String token,
            String username,
            String password,
            String role
    ) throws Exception {
        usersToCleanup.add(username);
        return mockMvc.perform(post("/api/auth/register")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "username", username,
                        "password", password,
                        "roles", List.of(role)
                ))));
    }

    private String login(String username, String password) throws Exception {
        String body = loginExpectingStatus(username, password, 200);
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private String loginExpectingStatus(String username, String password, int status) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().is(status))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Long findUserId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow()
                .getId();
    }

    private Long findUserVersion(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow()
                .getVersion();
    }

    private String uniqueUsername(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "it_" + prefix + "_" + suffix;
    }

    private List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
