package com.example.forklift_erp;

import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.StockBalance;
import com.example.forklift_erp.entity.StockMovementLine;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.StockBalanceRepository;
import com.example.forklift_erp.repository.StockMovementLineRepository;
import com.example.forklift_erp.repository.UserRepository;
import com.example.forklift_erp.service.StockLedgerService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryLedgerIntegrationTests {

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
    private PartInventoryRepository partRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private StockMovementLineRepository stockMovementLineRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> usersToCleanup = new ArrayList<>();
    private final List<String> partsToCleanup = new ArrayList<>();
    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        String superUsername = unique("super");
        createUserDirectly(superUsername, "SUPER_ADMIN");
        superToken = login(superUsername);
    }

    @AfterEach
    void tearDown() {
        for (String partCode : partsToCleanup.reversed()) {
            partRepository.findByPartCode(partCode).ifPresent(partRepository::delete);
        }
        partsToCleanup.clear();

        for (String username : usersToCleanup.reversed()) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
        usersToCleanup.clear();
    }

    @Test
    void partCreateAndInboundWriteBalanceAndMovementLines() throws Exception {
        String partCode = "LEDGER-" + unique("part");
        partsToCleanup.add(partCode);

        JsonNode created = createPart(partCode, 3);
        Long partId = created.path("id").asLong();
        Long warehouseId = created.path("warehouseId").asLong();
        Long version = created.path("version").asLong();

        assertBalance(partId, warehouseId, 3);
        assertMovementLine(partId, 3, 0, 3);

        String inboundResponse = mockMvc.perform(put("/api/parts/inbound")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "partCode", partCode,
                                "quantity", 2,
                                "version", version,
                                "operator", "ledger-test",
                                "remark", "integration test inbound"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode adjusted = objectMapper.readTree(inboundResponse).path("data");
        assertThat(adjusted.path("quantity").asInt()).isEqualTo(5);
        assertBalance(partId, warehouseId, 5);
        assertMovementLine(partId, 2, 3, 5);
    }

    private JsonNode createPart(String partCode, int quantity) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partCode", partCode);
        payload.put("partName", "Ledger test part");
        payload.put("partCategory", "LEDGER_TEST");
        payload.put("quantity", quantity);
        payload.put("unit", "pcs");

        String response = mockMvc.perform(post("/api/parts")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private void assertBalance(Long partId, Long warehouseId, int quantity) {
        StockBalance balance = stockBalanceRepository
                .findByResourceTypeAndResourceIdAndWarehouseId(
                        StockLedgerService.RESOURCE_PART,
                        partId,
                        warehouseId
                )
                .orElseThrow();
        assertThat(balance.getAvailableQuantity()).isEqualTo(quantity);
        assertThat(balance.getReservedQuantity()).isZero();
        assertThat(balance.getLockedQuantity()).isZero();
    }

    private void assertMovementLine(Long partId, int delta, int before, int after) {
        List<StockMovementLine> lines = stockMovementLineRepository
                .findByResourceTypeAndResourceIdOrderByCreatedAtDesc(StockLedgerService.RESOURCE_PART, partId);
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.getQuantityDelta()).isEqualTo(delta);
            assertThat(line.getBeforeQuantity()).isEqualTo(before);
            assertThat(line.getAfterQuantity()).isEqualTo(after);
        });
    }

    private void createUserDirectly(String username, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(roleName);
                    newRole.setDescription(roleName);
                    return roleRepository.save(newRole);
                });

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        userRepository.save(user);
        usersToCleanup.add(username);
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private String unique(String prefix) {
        return "it_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
