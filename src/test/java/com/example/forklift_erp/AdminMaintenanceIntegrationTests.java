package com.example.forklift_erp;

import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.StockBalanceRepository;
import com.example.forklift_erp.repository.StockMovementRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMaintenanceIntegrationTests {

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
    private CustomerRepository customerRepository;

    @Autowired
    private MachineInventoryRepository machineInventoryRepository;

    @Autowired
    private PartInventoryRepository partInventoryRepository;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String superUsername;
    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        superUsername = unique("super");
        createUserDirectly(superUsername, "SUPER_ADMIN");
        superToken = login(superUsername);
    }

    @AfterEach
    void tearDown() {
        userRepository.findByUsername(superUsername).ifPresent(userRepository::delete);
    }

    @Test
    void resetBusinessDataClearsCurrentBusinessTables() throws Exception {
        JsonNode customer = createCustomer();
        JsonNode machine = createMachine();
        createPart();
        createOutboundOrder(customer.path("id").asLong(), machine.path("id").asLong(), machine.path("version").asLong());

        assertThat(customerRepository.count()).isGreaterThan(0);
        assertThat(machineInventoryRepository.count()).isGreaterThan(0);
        assertThat(partInventoryRepository.count()).isGreaterThan(0);
        assertThat(outboundOrderRepository.count()).isGreaterThan(0);
        assertThat(stockMovementRepository.count()).isGreaterThan(0);
        assertThat(stockBalanceRepository.count()).isGreaterThan(0);
        assertThat(stockOperationLogRepository.count()).isGreaterThan(0);
        assertThat(operationAuditLogRepository.count()).isGreaterThan(0);

        mockMvc.perform(post("/api/admin/business-data/reset")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmation", "RESET-BUSINESS-DATA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.customers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.machineInventories").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.partInventories").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.outboundOrders").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        assertThat(customerRepository.count()).isZero();
        assertThat(machineInventoryRepository.count()).isZero();
        assertThat(partInventoryRepository.count()).isZero();
        assertThat(outboundOrderRepository.count()).isZero();
        assertThat(stockMovementRepository.count()).isZero();
        assertThat(stockBalanceRepository.count()).isZero();
        assertThat(stockOperationLogRepository.count()).isZero();
        assertThat(operationAuditLogRepository.count()).isZero();
        assertThat(userRepository.existsByUsername(superUsername)).isTrue();
    }

    @Test
    void resetBusinessDataRequiresConfirmationPhrase() throws Exception {
        JsonNode customer = createCustomer();
        assertThat(customerRepository.existsById(customer.path("id").asLong())).isTrue();

        mockMvc.perform(post("/api/admin/business-data/reset")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmation", "WRONG"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));

        assertThat(customerRepository.existsById(customer.path("id").asLong())).isTrue();
        customerRepository.deleteById(customer.path("id").asLong());
    }

    @Test
    void exportVehiclesReturnsExcelAttachment() throws Exception {
        createMachine();

        byte[] content = mockMvc.perform(get("/api/export/vehicles")
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
                        .contains("attachment"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(content).startsWith(new byte[] { 'P', 'K' });
    }

    private JsonNode createCustomer() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("companyName", "重置测试客户-" + unique("customer"));
        payload.put("address", "中山市测试路 1 号");
        payload.put("contactName", "陈测试");
        payload.put("contactPhone", "13800138000");
        payload.put("taxOrIdNumber", "91440000RESETTEST");

        String response = mockMvc.perform(post("/api/customers")
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

    private JsonNode createMachine() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleProductNumber", "RESET-" + unique("machine"));
        payload.put("name", "重置测试整机");
        payload.put("specificationModel", "CPCD30");
        payload.put("machineType", "内燃叉车");
        payload.put("supplier", "Reset Supplier");
        payload.put("inventoryCount", 1);
        payload.put("settlementPrice", "88000.00");

        String response = mockMvc.perform(post("/api/inventory")
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

    private JsonNode createPart() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partCode", "RESET-PART-" + unique("part"));
        payload.put("partName", "重置测试配件");
        payload.put("partCategory", "RESET_TEST");
        payload.put("quantity", 3);
        payload.put("unit", "件");

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

    private void createOutboundOrder(Long customerId, Long machineId, Long machineVersion) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("machineId", machineId);
        payload.put("machineVersion", machineVersion);
        payload.put("customerId", customerId);
        payload.put("salesDate", "2026-05-25");
        payload.put("settlementPrice", "92000.00");
        payload.put("salePrice", "96000.00");
        payload.put("operator", "reset-test");
        payload.put("orderRemark", "reset flow");

        mockMvc.perform(post("/api/outbound-orders/vehicle")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));
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
        return "it_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
