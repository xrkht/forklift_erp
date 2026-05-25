package com.example.forklift_erp;

import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.StockMovementRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
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
class OutboundOrderIntegrationTests {

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
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> usersToCleanup = new ArrayList<>();
    private final List<Long> ordersToCleanup = new ArrayList<>();
    private final List<Long> customersToCleanup = new ArrayList<>();
    private final List<Long> machinesToCleanup = new ArrayList<>();
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
        for (Long orderId : ordersToCleanup.reversed()) {
            outboundOrderRepository.findById(orderId).ifPresent(outboundOrderRepository::delete);
        }
        ordersToCleanup.clear();

        for (String partCode : partsToCleanup.reversed()) {
            partRepository.findByPartCode(partCode).ifPresent(partRepository::delete);
        }
        partsToCleanup.clear();

        for (Long machineId : machinesToCleanup.reversed()) {
            machineRepository.findById(machineId).ifPresent(machineRepository::delete);
        }
        machinesToCleanup.clear();

        for (Long customerId : customersToCleanup.reversed()) {
            customerRepository.findById(customerId).ifPresent(customerRepository::delete);
        }
        customersToCleanup.clear();

        for (String username : usersToCleanup.reversed()) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
        usersToCleanup.clear();
    }

    @Test
    void vehicleOutboundCreatesOrderAndUpdatesSettlementFlags() throws Exception {
        JsonNode customer = createCustomer("广东日丰电缆有限公司");
        JsonNode machine = createMachine();

        Map<String, Object> outboundPayload = new LinkedHashMap<>();
        outboundPayload.put("machineId", machine.path("id").asLong());
        outboundPayload.put("machineVersion", machine.path("version").asLong());
        outboundPayload.put("customerId", customer.path("id").asLong());
        outboundPayload.put("settlementPrice", "128000.00");
        outboundPayload.put("operator", "outbound-test");
        outboundPayload.put("orderRemark", "整车出库集成测试");

        String response = mockMvc.perform(post("/api/outbound-orders/vehicle")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(outboundPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resourceType").value("MACHINE"))
                .andExpect(jsonPath("$.data.paymentSettled").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode order = objectMapper.readTree(response).path("data");
        Long orderId = order.path("id").asLong();
        ordersToCleanup.add(orderId);
        assertThat(order.path("customerName").asText()).startsWith("广东日丰电缆有限公司");

        MachineInventory adjustedMachine = machineRepository.findById(machine.path("id").asLong()).orElseThrow();
        assertThat(adjustedMachine.getInventoryCount()).isZero();
        assertThat(adjustedMachine.getStockStatus()).isEqualTo("OUTBOUND");
        assertThat(adjustedMachine.getSettlementPrice()).isEqualByComparingTo(new BigDecimal("128000.00"));
        assertThat(adjustedMachine.getDestination1()).startsWith("广东日丰电缆有限公司");
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId("OUTBOUND_ORDER", orderId))
                .hasSizeGreaterThanOrEqualTo(1);

        Map<String, Object> updatePayload = new LinkedHashMap<>();
        updatePayload.put("version", order.path("version").asLong());
        updatePayload.put("paymentSettled", true);
        updatePayload.put("salesReported", true);
        updatePayload.put("invoiceApplied", true);
        updatePayload.put("salesReportDate", "2026-05-25");
        updatePayload.put("invoiceApplicationDate", "2026-05-25");
        updatePayload.put("orderRemark", "车款已结清，已报销售并申请发票");

        mockMvc.perform(put("/api/outbound-orders/{id}", orderId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.paymentSettled").value(true))
                .andExpect(jsonPath("$.data.salesReported").value(true))
                .andExpect(jsonPath("$.data.invoiceApplied").value(true));

        MachineInventory reportedMachine = machineRepository.findById(machine.path("id").asLong()).orElseThrow();
        assertThat(reportedMachine.getIsSalesReported()).isEqualTo("是");
        assertThat(reportedMachine.getIsInvoiceApplied()).isEqualTo("是");
        assertThat(reportedMachine.getSalesReportDate()).isEqualTo(LocalDate.of(2026, 5, 25));
    }

    @Test
    void partOutboundCreatesOrderAndReducesPartStock() throws Exception {
        JsonNode customer = createCustomer("Codex 配件客户有限公司");
        JsonNode part = createPart();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partCode", part.path("partCode").asText());
        payload.put("partVersion", part.path("version").asLong());
        payload.put("quantity", 2);
        payload.put("customerId", customer.path("id").asLong());
        payload.put("settlementPrice", "360.00");
        payload.put("operator", "part-outbound-test");
        payload.put("orderRemark", "配件出库集成测试");

        String response = mockMvc.perform(post("/api/outbound-orders/part")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resourceType").value("PART"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode order = objectMapper.readTree(response).path("data");
        ordersToCleanup.add(order.path("id").asLong());

        PartInventory adjustedPart = partRepository.findByPartCode(part.path("partCode").asText()).orElseThrow();
        assertThat(adjustedPart.getQuantity()).isEqualTo(3);
    }

    private JsonNode createCustomer(String companyName) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("companyName", companyName + "-" + unique("customer"));
        payload.put("address", "广东省佛山市测试路 1 号");
        payload.put("contactName", "陈经理");
        payload.put("contactPhone", "13800138000");
        payload.put("taxOrIdNumber", "91440000TEST");

        String response = mockMvc.perform(post("/api/customers")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode customer = objectMapper.readTree(response).path("data");
        customersToCleanup.add(customer.path("id").asLong());
        return customer;
    }

    private JsonNode createMachine() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleProductNumber", "OO-" + unique("machine"));
        payload.put("name", "出库测试车型");
        payload.put("specificationModel", "CPCD30");
        payload.put("machineType", "内燃叉车");
        payload.put("supplier", "Codex Supplier");
        payload.put("inventoryCount", 1);

        String response = mockMvc.perform(post("/api/inventory")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode machine = objectMapper.readTree(response).path("data");
        machinesToCleanup.add(machine.path("id").asLong());
        return machine;
    }

    private JsonNode createPart() throws Exception {
        String partCode = "OO-PART-" + unique("part");
        partsToCleanup.add(partCode);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partCode", partCode);
        payload.put("partName", "出库测试配件");
        payload.put("specification", "TEST-01");
        payload.put("partCategory", "测试配件");
        payload.put("quantity", 5);
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
