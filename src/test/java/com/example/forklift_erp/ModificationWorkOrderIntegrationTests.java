package com.example.forklift_erp;

import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderLineRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderRepository;
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
class ModificationWorkOrderIntegrationTests {

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
    private MachineInventoryRepository machineRepository;

    @Autowired
    private MachineConfigRepository machineConfigRepository;

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private ModificationWorkOrderRepository workOrderRepository;

    @Autowired
    private ModificationWorkOrderLineRepository workOrderLineRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> usersToCleanup = new ArrayList<>();
    private final List<String> partsToCleanup = new ArrayList<>();
    private final List<Long> machinesToCleanup = new ArrayList<>();
    private final List<Long> configsToCleanup = new ArrayList<>();
    private final List<Long> configItemsToCleanup = new ArrayList<>();
    private final List<Long> workOrdersToCleanup = new ArrayList<>();
    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        String superUsername = unique("super");
        createUserDirectly(superUsername, "SUPER_ADMIN");
        superToken = login(superUsername);
    }

    @AfterEach
    void tearDown() {
        for (Long workOrderId : workOrdersToCleanup.reversed()) {
            workOrderLineRepository.findByWorkOrderIdOrderByIdAsc(workOrderId)
                    .forEach(workOrderLineRepository::delete);
            workOrderRepository.findById(workOrderId).ifPresent(workOrderRepository::delete);
        }
        workOrdersToCleanup.clear();

        for (Long configId : configsToCleanup.reversed()) {
            machineConfigRepository.findById(configId).ifPresent(machineConfigRepository::delete);
        }
        configsToCleanup.clear();

        for (Long configItemId : configItemsToCleanup.reversed()) {
            configItemRepository.findById(configItemId).ifPresent(configItemRepository::delete);
        }
        configItemsToCleanup.clear();

        for (Long machineId : machinesToCleanup.reversed()) {
            partRepository.findBySourceMachineId(machineId).forEach(partRepository::delete);
            machineRepository.findById(machineId).ifPresent(machineRepository::delete);
        }
        machinesToCleanup.clear();

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
    void completeWorkOrderMovesStockAndUpdatesVehicleConfig() throws Exception {
        JsonNode machine = createMachine();
        Long machineId = machine.path("id").asLong();
        machinesToCleanup.add(machineId);

        MachineConfig tireConfig = createMachineConfig(machineId);
        JsonNode part = createPart("TIRE", 2);
        Long partId = part.path("id").asLong();

        Map<String, Object> line = new LinkedHashMap<>();
        line.put("machineConfigId", tireConfig.getId());
        line.put("machineConfigVersion", tireConfig.getVersion());
        line.put("newPartId", partId);
        line.put("newPartVersion", part.path("version").asLong());
        line.put("quantity", 1);
        line.put("oldPartAction", "STOCK_IN");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("machineId", machineId);
        payload.put("machineVersion", machine.path("version").asLong());
        payload.put("customerName", "Codex Customer");
        payload.put("salesOrderNo", "SO-" + unique("order"));
        payload.put("operator", "work-order-test");
        payload.put("remark", "customer requested solid tire");
        payload.put("lines", List.of(line));

        String createResponse = mockMvc.perform(post("/api/modification-work-orders")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("WAITING_PARTS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdOrder = objectMapper.readTree(createResponse).path("data");
        Long workOrderId = createdOrder.path("id").asLong();
        workOrdersToCleanup.add(workOrderId);
        assertThat(machineRepository.findById(machineId).orElseThrow().getStockStatus())
                .isEqualTo("PENDING_MODIFICATION");

        Map<String, Object> action = Map.of(
                "version", createdOrder.path("version").asLong(),
                "operator", "work-order-test",
                "remark", "completed in integration test"
        );

        mockMvc.perform(put("/api/modification-work-orders/{id}/complete", workOrderId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.lines[0].replaceLogId").isNumber());

        PartInventory adjustedPart = partRepository.findById(partId).orElseThrow();
        assertThat(adjustedPart.getQuantity()).isEqualTo(1);
        assertThat(partRepository.findBySourceMachineId(machineId))
                .anySatisfy(removed -> {
                    assertThat(removed.getSource()).isEqualTo("REMOVED");
                    assertThat(removed.getQuantity()).isEqualTo(1);
                    assertThat(removed.getPartCategory()).isEqualTo("TIRE");
                });

        MachineConfig adjustedConfig = machineConfigRepository.findById(tireConfig.getId()).orElseThrow();
        assertThat(adjustedConfig.getSelectedValue()).isEqualTo("Solid tire / 28x9-15");
        assertThat(adjustedConfig.getConfigSource()).isEqualTo("WAREHOUSE");
        assertThat(machineRepository.findById(machineId).orElseThrow().getStockStatus())
                .isEqualTo("PENDING_OUTBOUND");
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId("MODIFICATION_WORK_ORDER", workOrderId))
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void createConfigItemAutoFillsSequentialCodeWhenBlank() throws Exception {
        JsonNode first = createConfigItemByApi("电动车", "轮胎", "轮胎类型");
        JsonNode second = createConfigItemByApi("内燃车", "货叉", "货叉类型");

        String firstCode = first.path("itemCode").asText();
        String secondCode = second.path("itemCode").asText();

        assertThat(firstCode).matches("^CFG-\\d+$");
        assertThat(secondCode).matches("^CFG-\\d+$");
        assertThat(Integer.parseInt(secondCode.substring(4)))
                .isEqualTo(Integer.parseInt(firstCode.substring(4)) + 1);
    }

    private JsonNode createMachine() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vehicleProductNumber", "MO-" + unique("machine"));
        payload.put("name", "Work order test forklift");
        payload.put("specificationModel", "CPCD30");
        payload.put("machineType", "TEST");
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
        return objectMapper.readTree(response).path("data");
    }

    private MachineConfig createMachineConfig(Long machineId) {
        ConfigItem configItem = new ConfigItem();
        configItem.setCategory("ELECTRIC");
        configItem.setSubCategory("TIRE");
        configItem.setItemName("Tire type");
        configItem.setItemCode("MO-CFG-" + unique("config"));
        configItem.setInputType("SELECT");
        ConfigItem savedItem = configItemRepository.saveAndFlush(configItem);
        configItemsToCleanup.add(savedItem.getId());

        MachineConfig config = new MachineConfig();
        config.setMachineId(machineId);
        config.setConfigItemId(savedItem.getId());
        config.setConfigValueId(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
        config.setItemName(savedItem.getItemName());
        config.setSelectedValue("Pneumatic tire");
        config.setIsStandard(true);
        config.setConfigSource("FACTORY_STANDARD");
        MachineConfig saved = machineConfigRepository.saveAndFlush(config);
        configsToCleanup.add(saved.getId());
        return saved;
    }

    private JsonNode createConfigItemByApi(String category, String subCategory, String itemName) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category);
        payload.put("subCategory", subCategory);
        payload.put("itemName", itemName);
        payload.put("inputType", "SELECT");

        String response = mockMvc.perform(post("/api/config/items")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode item = objectMapper.readTree(response).path("data");
        configItemsToCleanup.add(item.path("id").asLong());
        return item;
    }

    private JsonNode createPart(String category, int quantity) throws Exception {
        String partCode = "MO-PART-" + unique("part");
        partsToCleanup.add(partCode);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partCode", partCode);
        payload.put("partName", "Solid tire");
        payload.put("specification", "28x9-15");
        payload.put("partCategory", category);
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
