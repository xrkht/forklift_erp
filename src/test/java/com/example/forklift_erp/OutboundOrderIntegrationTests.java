package com.example.forklift_erp;

import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.Permission;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.PermissionRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OutboundOrderIntegrationTests {

    private static final String PASSWORD = "CodexTest123!";
    private static final Path INVOICE_STORAGE_DIR = Paths.get("target", "invoice-test-files", UUID.randomUUID().toString());

    @DynamicPropertySource
    static void registerInvoiceStorage(DynamicPropertyRegistry registry) {
        registry.add("forklift-erp.invoice-storage-dir", () -> INVOICE_STORAGE_DIR.toString());
    }

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
    private PermissionRepository permissionRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> usersToCleanup = new ArrayList<>();
    private final List<Long> ordersToCleanup = new ArrayList<>();
    private final List<Long> customersToCleanup = new ArrayList<>();
    private final List<Long> machinesToCleanup = new ArrayList<>();
    private final List<String> partsToCleanup = new ArrayList<>();
    private final List<String> rolesToCleanup = new ArrayList<>();

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

        for (String roleName : rolesToCleanup.reversed()) {
            roleRepository.findByName(roleName).ifPresent(roleRepository::delete);
        }
        rolesToCleanup.clear();
    }

    @Test
    void vehicleOutboundCreatesOrderAndUpdatesSettlementFlags() throws Exception {
        JsonNode customer = createCustomer("广东日丰电缆有限公司");
        JsonNode machine = createMachine();

        Map<String, Object> outboundPayload = new LinkedHashMap<>();
        outboundPayload.put("machineId", machine.path("id").asLong());
        outboundPayload.put("machineVersion", machine.path("version").asLong());
        outboundPayload.put("customerId", customer.path("id").asLong());
        outboundPayload.put("salesDate", "2026-05-20");
        outboundPayload.put("settlementPrice", "128000.00");
        outboundPayload.put("salePrice", "136000.00");
        outboundPayload.put("paymentRemark", "已收定金 20000 元");
        outboundPayload.put("invoiceStatus", "含税未开票");
        outboundPayload.put("registrationStatus", "包上牌");
        outboundPayload.put("contractType", "纸质合同");
        outboundPayload.put("operator", "outbound-test");
        outboundPayload.put("orderRemark", "整车出库集成测试");

        String response = mockMvc.perform(post("/api/outbound-orders/vehicle")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(outboundPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resourceType").value("MACHINE"))
                .andExpect(jsonPath("$.data.salesDate").value("2026-05-20"))
                .andExpect(jsonPath("$.data.salePrice").value(136000.00))
                .andExpect(jsonPath("$.data.paymentRemark").value("已收定金 20000 元"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("含税未开票"))
                .andExpect(jsonPath("$.data.registrationStatus").value("包上牌"))
                .andExpect(jsonPath("$.data.contractType").value("纸质合同"))
                .andExpect(jsonPath("$.data.paymentSettled").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode order = objectMapper.readTree(response).path("data");
        Long orderId = order.path("id").asLong();
        ordersToCleanup.add(orderId);
        assertThat(order.path("customerName").asText()).startsWith("广东日丰电缆有限公司");

        MockMultipartFile earlyInvoice = new MockMultipartFile(
                "file",
                "early-invoice.pdf",
                "application/pdf",
                "not issued".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/outbound-orders/{id}/invoice", orderId)
                        .file(earlyInvoice)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));

        MachineInventory adjustedMachine = machineRepository.findById(machine.path("id").asLong()).orElseThrow();
        assertThat(adjustedMachine.getInventoryCount()).isZero();
        assertThat(adjustedMachine.getStockStatus()).isEqualTo("OUTBOUND");
        assertThat(adjustedMachine.getSettlementPrice()).isEqualByComparingTo(new BigDecimal("128000.00"));
        assertThat(adjustedMachine.getSalePrice()).isEqualByComparingTo(new BigDecimal("136000.00"));
        assertThat(adjustedMachine.getSalesDate()).isEqualTo("2026-05-20");
        assertThat(adjustedMachine.getDestination1()).startsWith("广东日丰电缆有限公司");
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId("OUTBOUND_ORDER", orderId))
                .hasSizeGreaterThanOrEqualTo(1);

        Map<String, Object> updatePayload = new LinkedHashMap<>();
        updatePayload.put("version", order.path("version").asLong());
        updatePayload.put("salesDate", "2026-05-21");
        updatePayload.put("settlementPrice", "129500.00");
        updatePayload.put("salePrice", "137500.00");
        updatePayload.put("paymentSettled", true);
        updatePayload.put("paymentRemark", "尾款已结清");
        updatePayload.put("salesReported", true);
        updatePayload.put("invoiceApplied", true);
        updatePayload.put("salesReportDate", "2026-05-25");
        updatePayload.put("invoiceApplicationDate", "2026-05-25");
        updatePayload.put("invoiceStatus", "含税已开票");
        updatePayload.put("invoiceIssuedDate", "2026-05-26");
        updatePayload.put("registrationStatus", "已上牌");
        updatePayload.put("contractType", "纸质合同");
        updatePayload.put("orderRemark", "车款已结清，已报销售并申请发票");

        mockMvc.perform(put("/api/outbound-orders/{id}", orderId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.salesDate").value("2026-05-21"))
                .andExpect(jsonPath("$.data.settlementPrice").value(129500.00))
                .andExpect(jsonPath("$.data.salePrice").value(137500.00))
                .andExpect(jsonPath("$.data.paymentSettled").value(true))
                .andExpect(jsonPath("$.data.paymentRemark").value("尾款已结清"))
                .andExpect(jsonPath("$.data.salesReported").value(true))
                .andExpect(jsonPath("$.data.invoiceApplied").value(true))
                .andExpect(jsonPath("$.data.invoiceStatus").value("含税已开票"))
                .andExpect(jsonPath("$.data.invoiceIssuedDate").value("2026-05-26"))
                .andExpect(jsonPath("$.data.registrationStatus").value("已上牌"))
                .andExpect(jsonPath("$.data.contractType").value("纸质合同"));

        byte[] invoiceBytes = "%PDF-1.4 Codex invoice".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile issuedInvoice = new MockMultipartFile(
                "file",
                "invoice-test.pdf",
                "application/pdf",
                invoiceBytes
        );
        mockMvc.perform(multipart("/api/outbound-orders/{id}/invoice", orderId)
                        .file(issuedInvoice)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.invoiceOriginalName").value("invoice-test.pdf"))
                .andExpect(jsonPath("$.data.invoiceContentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.invoiceFileSize").value(invoiceBytes.length))
                .andExpect(jsonPath("$.data.invoiceFileAvailable").value(true))
                .andExpect(jsonPath("$.data.invoiceUploadedAt").exists());

        mockMvc.perform(get("/api/outbound-orders/{id}/invoice", orderId)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("invoice-test.pdf")))
                .andExpect(content().bytes(invoiceBytes));

        MachineInventory reportedMachine = machineRepository.findById(machine.path("id").asLong()).orElseThrow();
        assertThat(reportedMachine.getIsSalesReported()).isEqualTo("是");
        assertThat(reportedMachine.getIsInvoiceApplied()).isEqualTo("是");
        assertThat(reportedMachine.getSalesReportDate()).isEqualTo(LocalDate.of(2026, 5, 25));
        assertThat(reportedMachine.getSettlementPrice()).isEqualByComparingTo(new BigDecimal("129500.00"));
        assertThat(reportedMachine.getSalePrice()).isEqualByComparingTo(new BigDecimal("137500.00"));
        assertThat(reportedMachine.getSalesDate()).isEqualTo("2026-05-21");
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

    @Test
    void adminOrderLockHidesOrderAndRelatedStockFromNonAdminUsers() throws Exception {
        String adminUsername = unique("admin");
        createUserDirectly(adminUsername, "ADMIN");
        String adminToken = login(adminUsername);
        String userUsername = unique("user");
        createUserDirectly(userUsername, "USER");
        String userToken = login(userUsername);

        JsonNode customer = createCustomer("Codex 锁定客户有限公司");
        JsonNode machine = createMachine();
        JsonNode vehicleOrder = createVehicleOrder(customer, machine);
        String vehicleNumber = machine.path("vehicleProductNumber").asText();
        String vehicleOrderNo = vehicleOrder.path("orderNo").asText();

        String userOrdersBefore = mockMvc.perform(get("/api/outbound-orders")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(userOrdersBefore).contains(vehicleOrderNo);

        String vehicleLockResponse = mockMvc.perform(put("/api/outbound-orders/{id}/lock", vehicleOrder.path("id").asLong())
                        .param("locked", "true")
                        .param("version", vehicleOrder.path("version").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLocked").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode lockedVehicleOrder = objectMapper.readTree(vehicleLockResponse).path("data");
        assertThat(machineRepository.findById(machine.path("id").asLong()).orElseThrow().getIsLocked()).isTrue();

        String userOrdersAfterLock = mockMvc.perform(get("/api/outbound-orders")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(userOrdersAfterLock).doesNotContain(vehicleOrderNo);

        String userInventoryAfterLock = mockMvc.perform(get("/api/inventory")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(userInventoryAfterLock).doesNotContain(vehicleNumber);

        String adminInventoryAfterLock = mockMvc.perform(get("/api/inventory")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(adminInventoryAfterLock).contains(vehicleNumber);

        JsonNode part = createPart();
        JsonNode partOrder = createPartOrder(customer, part);
        String partCode = part.path("partCode").asText();

        mockMvc.perform(put("/api/outbound-orders/{id}/lock", partOrder.path("id").asLong())
                        .param("locked", "true")
                        .param("version", partOrder.path("version").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLocked").value(true));
        assertThat(partRepository.findByPartCode(partCode).orElseThrow().getIsLocked()).isTrue();

        String userPartsAfterLock = mockMvc.perform(get("/api/parts")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(userPartsAfterLock).doesNotContain(partCode);

        String adminPartsAfterLock = mockMvc.perform(get("/api/parts")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(adminPartsAfterLock).contains(partCode);

        mockMvc.perform(put("/api/outbound-orders/{id}/lock", lockedVehicleOrder.path("id").asLong())
                        .param("locked", "false")
                        .param("version", lockedVehicleOrder.path("version").asText())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLocked").value(false));
        assertThat(machineRepository.findById(machine.path("id").asLong()).orElseThrow().getIsLocked()).isFalse();

        String userInventoryAfterUnlock = mockMvc.perform(get("/api/inventory")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(userInventoryAfterUnlock).contains(vehicleNumber);
    }

    private JsonNode createVehicleOrder(JsonNode customer, JsonNode machine) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("machineId", machine.path("id").asLong());
        payload.put("machineVersion", machine.path("version").asLong());
        payload.put("customerId", customer.path("id").asLong());
        payload.put("salesDate", "2026-05-26");
        payload.put("settlementPrice", "118000.00");
        payload.put("operator", "order-lock-test");
        payload.put("orderRemark", "订单锁定集成测试");

        String response = mockMvc.perform(post("/api/outbound-orders/vehicle")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode order = objectMapper.readTree(response).path("data");
        ordersToCleanup.add(order.path("id").asLong());
        return order;
    }

    private JsonNode createPartOrder(JsonNode customer, JsonNode part) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partCode", part.path("partCode").asText());
        payload.put("partVersion", part.path("version").asLong());
        payload.put("quantity", 1);
        payload.put("customerId", customer.path("id").asLong());
        payload.put("settlementPrice", "180.00");
        payload.put("operator", "part-order-lock-test");

        String response = mockMvc.perform(post("/api/outbound-orders/part")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode order = objectMapper.readTree(response).path("data");
        ordersToCleanup.add(order.path("id").asLong());
        return order;
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
        payload.put("configuration", "国四 / 二节 3 米 / 1220 货叉");
        payload.put("machineType", "内燃叉车");
        payload.put("supplier", "Codex Supplier");
        payload.put("applicationNumber", "APP-" + unique("apply"));
        payload.put("materialNumber", "MAT-" + unique("material"));
        payload.put("inventoryCount", 1);
        payload.put("remarks", "整机进出库台账测试数据");

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

    private void createUserWithPermissions(String username, String roleName, String... permissionCodes) {
        Set<Permission> permissions = new HashSet<>();
        for (String code : permissionCodes) {
            Permission permission = permissionRepository.findByCode(code)
                    .orElseGet(() -> {
                        Permission created = new Permission();
                        created.setCode(code);
                        created.setDescription(code);
                        return permissionRepository.save(created);
                    });
            permissions.add(permission);
        }

        Role role = new Role();
        role.setName(roleName);
        role.setDescription(roleName);
        role.setPermissions(permissions);
        roleRepository.save(role);
        rolesToCleanup.add(roleName);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        userRepository.save(user);
        usersToCleanup.add(username);
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
