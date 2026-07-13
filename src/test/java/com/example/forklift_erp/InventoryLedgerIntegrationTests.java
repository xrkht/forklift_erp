package com.example.forklift_erp;

import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.StockBalance;
import com.example.forklift_erp.entity.StockMovementLine;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.entity.Warehouse;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.StockBalanceRepository;
import com.example.forklift_erp.repository.StockMovementLineRepository;
import com.example.forklift_erp.repository.StocktakingRecordRepository;
import com.example.forklift_erp.repository.UserRepository;
import com.example.forklift_erp.repository.WarehouseRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryLedgerIntegrationTests extends TestcontainersDatabaseSupport {

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
    private MachineInventoryRepository machineRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private StockMovementLineRepository stockMovementLineRepository;

    @Autowired
    private StocktakingRecordRepository stocktakingRecordRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> partsToCleanup = new ArrayList<>();
    private final List<String> machinesToCleanup = new ArrayList<>();
    private final List<Long> stocktakingRecordsToCleanup = new ArrayList<>();
    private String superToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        String superUsername = unique("super");
        String userUsername = unique("user");
        createUserDirectly(superUsername, "SUPER_ADMIN");
        createUserDirectly(userUsername, "USER");
        superToken = login(superUsername);
        userToken = login(userUsername);
    }

    @AfterEach
    void tearDown() {
        for (Long id : stocktakingRecordsToCleanup.reversed()) {
            stocktakingRecordRepository.findById(id).ifPresent(stocktakingRecordRepository::delete);
        }
        stocktakingRecordsToCleanup.clear();
        for (String partCode : partsToCleanup.reversed()) {
            partRepository.findByPartCode(partCode).ifPresent(part -> {
                deleteBalances(StockLedgerService.RESOURCE_PART, part.getId());
                partRepository.delete(part);
            });
        }
        partsToCleanup.clear();
        for (String vehicleNumber : machinesToCleanup.reversed()) {
            machineRepository.findByVehicleProductNumber(vehicleNumber).ifPresent(machine -> {
                deleteBalances(StockLedgerService.RESOURCE_MACHINE, machine.getId());
                machineRepository.delete(machine);
            });
        }
        machinesToCleanup.clear();

    }

    @Test
    void partCreateAndInboundWriteBalanceAndMovementLines() throws Exception {
        String partCode = "LEDGER-" + unique("part");
        partsToCleanup.add(partCode);

        JsonNode created = createPart(partCode, 3);
        Long partId = created.path("id").asLong();
        Long warehouseId = created.path("warehouseId").asLong();
        Long version = created.path("version").asLong();

        mockMvc.perform(get("/api/parts")
                        .header("Authorization", bearer(superToken))
                        .param("paged", "true")
                        .param("keyword", partCode)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].partCode").value(partCode));

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

    @Test
    void stocktakingCompletionWritesBalanceAndMovementLine() throws Exception {
        String partCode = "LEDGER-ST-" + unique("part");
        partsToCleanup.add(partCode);

        JsonNode part = createPart(partCode, 3);
        Long partId = part.path("id").asLong();
        Long warehouseId = part.path("warehouseId").asLong();

        JsonNode stocktaking = createStocktakingRecord(partId, 8);
        Long stocktakingId = stocktaking.path("id").asLong();
        stocktakingRecordsToCleanup.add(stocktakingId);

        mockMvc.perform(put("/api/stocktaking-records/{id}/complete", stocktakingId)
                        .header("Authorization", bearer(superToken))
                        .param("version", stocktaking.path("version").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.actualQuantity").value(8));

        assertBalance(partId, warehouseId, 8);
        assertMovementLine(partId, 5, 3, 8);
    }

    @Test
    void partTransferThenInboundPreservesTheDistributedTotal() throws Exception {
        String partCode = "LEDGER-TF-" + unique("part");
        partsToCleanup.add(partCode);

        JsonNode part = createPart(partCode, 3);
        Long partId = part.path("id").asLong();
        Long sourceWarehouseId = part.path("warehouseId").asLong();
        Long targetWarehouseId = createWarehouse().path("id").asLong();

        mockMvc.perform(post("/api/warehouses/transfer")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "resourceType", StockLedgerService.RESOURCE_PART,
                                "resourceId", partId,
                                "fromWarehouseId", sourceWarehouseId,
                                "toWarehouseId", targetWarehouseId,
                                "quantity", 2,
                                "version", part.path("version").asLong(),
                                "operator", "ledger-test",
                                "remark", "integration test transfer"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertBalance(partId, sourceWarehouseId, 1);
        assertBalance(partId, targetWarehouseId, 2);
        assertMovementLine(partId, -2, 3, 1);
        assertMovementLine(partId, 2, 0, 2);
        assertThat(operationAuditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getModule()).isEqualTo("Warehouse transfer");
            assertThat(log.getAction()).isEqualTo("TRANSFER");
            assertThat(log.getTargetType()).isEqualTo("PART");
            assertThat(log.getTargetId()).isEqualTo(partId);
            assertThat(log.getSourceType()).isEqualTo("STOCK_MOVEMENT");
        });

        PartInventory transferred = partRepository.findById(partId).orElseThrow();
        String inboundResponse = mockMvc.perform(put("/api/parts/inbound")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "partCode", partCode,
                                "quantity", 1,
                                "version", transferred.getVersion(),
                                "operator", "ledger-test",
                                "remark", "inbound after partial transfer"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(inboundResponse).path("data").path("warehouseId").asLong())
                .isEqualTo(sourceWarehouseId);
        assertBalance(StockLedgerService.RESOURCE_PART, partId, sourceWarehouseId, 2);
        assertBalance(StockLedgerService.RESOURCE_PART, partId, targetWarehouseId, 2);
        assertTotalBalance(StockLedgerService.RESOURCE_PART, partId, 4);
    }

    @Test
    void machineTransferThenInboundPreservesTheDistributedTotal() throws Exception {
        JsonNode machine = createMachine(3);
        Long machineId = machine.path("id").asLong();
        Long sourceWarehouseId = machine.path("warehouseId").asLong();
        Long targetWarehouseId = createWarehouse().path("id").asLong();

        mockMvc.perform(post("/api/warehouses/transfer")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "resourceType", StockLedgerService.RESOURCE_MACHINE,
                                "resourceId", machineId,
                                "fromWarehouseId", sourceWarehouseId,
                                "toWarehouseId", targetWarehouseId,
                                "quantity", 2,
                                "version", machine.path("version").asLong(),
                                "operator", "ledger-test"
                        ))))
                .andExpect(status().isOk());

        MachineInventory transferred = machineRepository.findById(machineId).orElseThrow();
        assertThat(transferred.getWarehouseId()).isEqualTo(sourceWarehouseId);
        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, sourceWarehouseId, 1);
        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, targetWarehouseId, 2);

        mockMvc.perform(put("/api/inventory/{id}/inbound", machineId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "quantity", 1,
                                "version", transferred.getVersion(),
                                "operator", "ledger-test"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inventoryCount").value(4));

        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, sourceWarehouseId, 2);
        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, targetWarehouseId, 2);
        assertTotalBalance(StockLedgerService.RESOURCE_MACHINE, machineId, 4);
    }

    @Test
    void machineCanFinishAStagedTransferFromTheSecondaryWarehouse() throws Exception {
        JsonNode machine = createMachine(3);
        Long machineId = machine.path("id").asLong();
        Long sourceWarehouseId = machine.path("warehouseId").asLong();
        Long stagingWarehouseId = createWarehouse().path("id").asLong();
        Long targetWarehouseId = createWarehouse().path("id").asLong();

        transfer(StockLedgerService.RESOURCE_MACHINE, machineId, sourceWarehouseId,
                stagingWarehouseId, 2, machine.path("version").asLong());
        MachineInventory firstStage = machineRepository.findById(machineId).orElseThrow();

        transfer(StockLedgerService.RESOURCE_MACHINE, machineId, sourceWarehouseId,
                stagingWarehouseId, 1, firstStage.getVersion());
        MachineInventory staged = machineRepository.findById(machineId).orElseThrow();
        assertThat(staged.getWarehouseId()).isEqualTo(sourceWarehouseId);
        assertThat(staged.getVersion()).isGreaterThan(firstStage.getVersion());
        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, sourceWarehouseId, 0);
        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, stagingWarehouseId, 3);

        transfer(StockLedgerService.RESOURCE_MACHINE, machineId, stagingWarehouseId,
                targetWarehouseId, 3, staged.getVersion());
        MachineInventory transferred = machineRepository.findById(machineId).orElseThrow();
        assertThat(transferred.getWarehouseId()).isEqualTo(targetWarehouseId);
        assertThat(transferred.getVersion()).isGreaterThan(staged.getVersion());
        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, stagingWarehouseId, 0);
        assertBalance(StockLedgerService.RESOURCE_MACHINE, machineId, targetWarehouseId, 3);
        assertTotalBalance(StockLedgerService.RESOURCE_MACHINE, machineId, 3);
    }

    @Test
    void inventoryDeletionRejectsNonZeroBalancesAndRemovesEmptyBalances() throws Exception {
        String partCode = "LEDGER-DEL-" + unique("part");
        partsToCleanup.add(partCode);
        JsonNode part = createPart(partCode, 1);
        Long partId = part.path("id").asLong();

        mockMvc.perform(delete("/api/parts/{id}", partId)
                        .header("Authorization", bearer(superToken))
                        .param("version", part.path("version").asText()))
                .andExpect(status().isConflict());
        assertThat(partRepository.findById(partId)).isPresent();

        String partOutbound = mockMvc.perform(put("/api/parts/outbound")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "partCode", partCode,
                                "quantity", 1,
                                "version", part.path("version").asLong()
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long emptyPartVersion = objectMapper.readTree(partOutbound).path("data").path("version").asLong();

        StockBalance invalidBalance = stockBalanceRepository
                .findByResourceTypeAndResourceIdAndWarehouseId(
                        StockLedgerService.RESOURCE_PART,
                        partId,
                        part.path("warehouseId").asLong()
                )
                .orElseThrow();
        invalidBalance.setAvailableQuantity(-1);
        invalidBalance = stockBalanceRepository.saveAndFlush(invalidBalance);
        mockMvc.perform(delete("/api/parts/{id}", partId)
                        .header("Authorization", bearer(superToken))
                        .param("version", String.valueOf(emptyPartVersion)))
                .andExpect(status().isConflict());
        invalidBalance.setAvailableQuantity(0);
        stockBalanceRepository.saveAndFlush(invalidBalance);

        mockMvc.perform(delete("/api/parts/{id}", partId)
                        .header("Authorization", bearer(superToken))
                        .param("version", String.valueOf(emptyPartVersion)))
                .andExpect(status().isOk());
        assertThat(partRepository.findById(partId)).isEmpty();
        assertThat(balances(StockLedgerService.RESOURCE_PART, partId)).isEmpty();

        JsonNode machine = createMachine(1);
        Long machineId = machine.path("id").asLong();
        mockMvc.perform(delete("/api/inventory/{id}", machineId)
                        .header("Authorization", bearer(superToken))
                        .param("version", machine.path("version").asText()))
                .andExpect(status().isConflict());

        String machineOutbound = mockMvc.perform(put("/api/inventory/{id}/outbound", machineId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "quantity", 1,
                                "version", machine.path("version").asLong()
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long emptyMachineVersion = objectMapper.readTree(machineOutbound).path("data").path("version").asLong();

        mockMvc.perform(delete("/api/inventory/{id}", machineId)
                        .header("Authorization", bearer(superToken))
                        .param("version", String.valueOf(emptyMachineVersion)))
                .andExpect(status().isOk());
        assertThat(machineRepository.findById(machineId)).isEmpty();
        assertThat(balances(StockLedgerService.RESOURCE_MACHINE, machineId)).isEmpty();
    }

    @Test
    void standardUserCannotTransferLockedInventory() throws Exception {
        String partCode = "LEDGER-LOCK-" + unique("part");
        partsToCleanup.add(partCode);
        JsonNode part = createPart(partCode, 3);
        PartInventory lockedPart = partRepository.findById(part.path("id").asLong()).orElseThrow();
        lockedPart.setIsLocked(true);
        lockedPart = partRepository.saveAndFlush(lockedPart);

        JsonNode machine = createMachine(1);
        MachineInventory lockedMachine = machineRepository.findById(machine.path("id").asLong()).orElseThrow();
        lockedMachine.setIsLocked(true);
        lockedMachine = machineRepository.saveAndFlush(lockedMachine);

        Long targetWarehouseId = createWarehouse().path("id").asLong();
        assertLockedTransferForbidden(
                StockLedgerService.RESOURCE_PART,
                lockedPart.getId(),
                lockedPart.getWarehouseId(),
                targetWarehouseId,
                lockedPart.getVersion()
        );
        assertLockedTransferForbidden(
                StockLedgerService.RESOURCE_MACHINE,
                lockedMachine.getId(),
                lockedMachine.getWarehouseId(),
                targetWarehouseId,
                lockedMachine.getVersion()
        );

        assertTotalBalance(StockLedgerService.RESOURCE_PART, lockedPart.getId(), 3);
        assertTotalBalance(StockLedgerService.RESOURCE_MACHINE, lockedMachine.getId(), 1);
        assertThat(stockBalanceRepository.findByResourceTypeAndResourceIdAndWarehouseId(
                StockLedgerService.RESOURCE_PART, lockedPart.getId(), targetWarehouseId)).isEmpty();
        assertThat(stockBalanceRepository.findByResourceTypeAndResourceIdAndWarehouseId(
                StockLedgerService.RESOURCE_MACHINE, lockedMachine.getId(), targetWarehouseId)).isEmpty();
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

    private JsonNode createMachine(int quantity) throws Exception {
        String vehicleNumber = "LEDGER-M-" + unique("machine");
        machinesToCleanup.add(vehicleNumber);
        String response = mockMvc.perform(post("/api/inventory")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "vehicleProductNumber", vehicleNumber,
                                "name", "Ledger test machine",
                                "specificationModel", "CPD25",
                                "machineType", "TEST",
                                "inventoryCount", quantity
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode createStocktakingRecord(Long partId, int actualQuantity) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceType", "PART");
        payload.put("resourceId", partId);
        payload.put("actualQuantity", actualQuantity);
        payload.put("operator", "ledger-test");
        payload.put("remark", "integration test stocktaking");

        String response = mockMvc.perform(post("/api/stocktaking-records")
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

    private JsonNode createWarehouse() throws Exception {
        String code = "WH-" + unique("warehouse");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warehouseCode", code);
        payload.put("warehouseName", "Ledger transfer warehouse " + code);
        payload.put("warehouseType", "TEST");

        String response = mockMvc.perform(post("/api/warehouses")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode warehouse = objectMapper.readTree(response).path("data");
        Warehouse saved = warehouseRepository.findById(warehouse.path("id").asLong()).orElseThrow();
        assertThat(saved.getWarehouseCode()).isEqualTo(code);
        return warehouse;
    }

    private void assertBalance(Long partId, Long warehouseId, int quantity) {
        assertBalance(StockLedgerService.RESOURCE_PART, partId, warehouseId, quantity);
    }

    private void assertBalance(String resourceType, Long resourceId, Long warehouseId, int quantity) {
        StockBalance balance = stockBalanceRepository
                .findByResourceTypeAndResourceIdAndWarehouseId(
                        resourceType,
                        resourceId,
                        warehouseId
                )
                .orElseThrow();
        assertThat(balance.getAvailableQuantity()).isEqualTo(quantity);
        assertThat(balance.getReservedQuantity()).isZero();
        assertThat(balance.getLockedQuantity()).isZero();
    }

    private void assertTotalBalance(String resourceType, Long resourceId, int expectedQuantity) {
        assertThat(balances(resourceType, resourceId).stream()
                .mapToInt(balance -> balance.getAvailableQuantity() == null ? 0 : balance.getAvailableQuantity())
                .sum()).isEqualTo(expectedQuantity);
    }

    private List<StockBalance> balances(String resourceType, Long resourceId) {
        return stockBalanceRepository.findByResourceTypeAndResourceId(resourceType, resourceId);
    }

    private void deleteBalances(String resourceType, Long resourceId) {
        stockBalanceRepository.deleteAll(balances(resourceType, resourceId));
    }

    private void assertLockedTransferForbidden(
            String resourceType,
            Long resourceId,
            Long sourceWarehouseId,
            Long targetWarehouseId,
            Long version
    ) throws Exception {
        mockMvc.perform(post("/api/warehouses/transfer")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "resourceType", resourceType,
                                "resourceId", resourceId,
                                "fromWarehouseId", sourceWarehouseId,
                                "toWarehouseId", targetWarehouseId,
                                "quantity", 1,
                                "version", version
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private void transfer(
            String resourceType,
            Long resourceId,
            Long sourceWarehouseId,
            Long targetWarehouseId,
            int quantity,
            Long version
    ) throws Exception {
        mockMvc.perform(post("/api/warehouses/transfer")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "resourceType", resourceType,
                                "resourceId", resourceId,
                                "fromWarehouseId", sourceWarehouseId,
                                "toWarehouseId", targetWarehouseId,
                                "quantity", quantity,
                                "version", version
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
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

}
