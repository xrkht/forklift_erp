package com.example.forklift_erp;

import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.Permission;
import com.example.forklift_erp.entity.RepairPartUsage;
import com.example.forklift_erp.entity.Role;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.PermissionRepository;
import com.example.forklift_erp.repository.RoleRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.RepairPartUsageRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
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
    private static final Path CONTRACT_STORAGE_DIR = Paths.get("target", "contract-test-files", UUID.randomUUID().toString());

    @DynamicPropertySource
    static void registerInvoiceStorage(DynamicPropertyRegistry registry) {
        registry.add("forklift-erp.invoice-storage-dir", () -> INVOICE_STORAGE_DIR.toString());
        registry.add("forklift-erp.contract-storage-dir", () -> CONTRACT_STORAGE_DIR.toString());
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
    private RentalRecordRepository rentalRecordRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    @Autowired
    private RepairPartUsageRepository repairPartUsageRepository;

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
    private final List<Long> rentalsToCleanup = new ArrayList<>();
    private final List<Long> repairsToCleanup = new ArrayList<>();
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
        for (Long repairId : repairsToCleanup.reversed()) {
            repairRecordRepository.findById(repairId).ifPresent(repairRecordRepository::delete);
        }
        repairsToCleanup.clear();

        for (Long rentalId : rentalsToCleanup.reversed()) {
            rentalRecordRepository.findById(rentalId).ifPresent(rentalRecordRepository::delete);
        }
        rentalsToCleanup.clear();

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

        MockMultipartFile earlyContract = new MockMultipartFile(
                "file",
                "early-contract.pdf",
                "application/pdf",
                "contract".getBytes(StandardCharsets.UTF_8)
        );
        String earlyContractResponse = mockMvc.perform(multipart("/api/outbound-orders/{id}/contract", orderId)
                        .file(earlyContract)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contractOriginalName").value("early-contract.pdf"))
                .andExpect(jsonPath("$.data.contractFileAvailable").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode contractUploadedOrder = objectMapper.readTree(earlyContractResponse).path("data");
        String earlyContractStoredFileName = outboundOrderRepository.findById(orderId).orElseThrow().getContractStoredFileName();
        assertThat(CONTRACT_STORAGE_DIR.resolve(earlyContractStoredFileName)).exists();

        Map<String, Object> invoiceAppliedPayload = new LinkedHashMap<>();
        invoiceAppliedPayload.put("version", contractUploadedOrder.path("version").asLong());
        invoiceAppliedPayload.put("salesDate", "2026-05-20");
        invoiceAppliedPayload.put("settlementPrice", "128000.00");
        invoiceAppliedPayload.put("salePrice", "136000.00");
        invoiceAppliedPayload.put("paymentSettled", false);
        invoiceAppliedPayload.put("salesReported", false);
        invoiceAppliedPayload.put("invoiceApplied", true);
        invoiceAppliedPayload.put("invoiceApplicationDate", "2026-05-25");
        invoiceAppliedPayload.put("invoiceStatus", "含税已申请发票");
        invoiceAppliedPayload.put("orderRemark", "已申请发票，待上传文件");

        mockMvc.perform(put("/api/outbound-orders/{id}", orderId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invoiceAppliedPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceApplied").value(true))
                .andExpect(jsonPath("$.data.invoiceApplicationDate").value("2026-05-25"));

        byte[] appliedInvoiceBytes = "%PDF-1.4 applied invoice".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile appliedInvoice = new MockMultipartFile(
                "file",
                "invoice-applied.pdf",
                "application/pdf",
                appliedInvoiceBytes
        );
        String appliedInvoiceResponse = mockMvc.perform(multipart("/api/outbound-orders/{id}/invoice", orderId)
                        .file(appliedInvoice)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceOriginalName").value("invoice-applied.pdf"))
                .andExpect(jsonPath("$.data.invoiceFileAvailable").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode invoiceAppliedUploadedOrder = objectMapper.readTree(appliedInvoiceResponse).path("data");
        String appliedInvoiceStoredFileName = outboundOrderRepository.findById(orderId).orElseThrow().getInvoiceStoredFileName();
        assertThat(INVOICE_STORAGE_DIR.resolve(appliedInvoiceStoredFileName)).exists();

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
        updatePayload.put("version", invoiceAppliedUploadedOrder.path("version").asLong());
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
        String issuedInvoiceStoredFileName = outboundOrderRepository.findById(orderId).orElseThrow().getInvoiceStoredFileName();
        assertThat(INVOICE_STORAGE_DIR.resolve(appliedInvoiceStoredFileName)).doesNotExist();
        assertThat(INVOICE_STORAGE_DIR.resolve(issuedInvoiceStoredFileName)).exists();

        mockMvc.perform(get("/api/outbound-orders/{id}/invoice", orderId)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("invoice-test.pdf")))
                .andExpect(content().bytes(invoiceBytes));

        byte[] contractBytes = "%PDF-1.4 Codex contract".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile contractFile = new MockMultipartFile(
                "file",
                "contract-test.pdf",
                "application/pdf",
                contractBytes
        );
        mockMvc.perform(multipart("/api/outbound-orders/{id}/contract", orderId)
                        .file(contractFile)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.contractOriginalName").value("contract-test.pdf"))
                .andExpect(jsonPath("$.data.contractContentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.contractFileSize").value(contractBytes.length))
                .andExpect(jsonPath("$.data.contractFileAvailable").value(true))
                .andExpect(jsonPath("$.data.contractUploadedAt").exists());
        String finalContractStoredFileName = outboundOrderRepository.findById(orderId).orElseThrow().getContractStoredFileName();
        assertThat(CONTRACT_STORAGE_DIR.resolve(earlyContractStoredFileName)).doesNotExist();
        assertThat(CONTRACT_STORAGE_DIR.resolve(finalContractStoredFileName)).exists();

        mockMvc.perform(get("/api/outbound-orders/{id}/contract", orderId)
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("contract-test.pdf")))
                .andExpect(content().bytes(contractBytes));

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
    void rentalRecordTracksVehicleDestinationPriceAndFeedsStatistics() throws Exception {
        JsonNode customer = createCustomer("Codex 租赁后销售客户有限公司");
        JsonNode machine = createMachine();

        Map<String, Object> rentalPayload = new LinkedHashMap<>();
        rentalPayload.put("machineId", machine.path("id").asLong());
        rentalPayload.put("machineVersion", machine.path("version").asLong());
        rentalPayload.put("customerId", customer.path("id").asLong());
        rentalPayload.put("destination", "佛山禅城工地 A 区");
        rentalPayload.put("monthlyRentalPrice", "8800.00");
        rentalPayload.put("startDate", "2026-05-27");
        rentalPayload.put("operator", "rental-test");
        rentalPayload.put("remark", "租赁主路径集成测试");

        String rentalResponse = mockMvc.perform(post("/api/rentals")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(rentalPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.vehicleNumber").value(machine.path("vehicleProductNumber").asText()))
                .andExpect(jsonPath("$.data.customerId").value(customer.path("id").asLong()))
                .andExpect(jsonPath("$.data.destination").value("佛山禅城工地 A 区"))
                .andExpect(jsonPath("$.data.monthlyRentalPrice").value(8800.00))
                .andExpect(jsonPath("$.data.rentalPrice").value(8800.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode rental = objectMapper.readTree(rentalResponse).path("data");
        rentalsToCleanup.add(rental.path("id").asLong());

        Map<String, Object> outboundWhileRented = new LinkedHashMap<>();
        outboundWhileRented.put("machineId", machine.path("id").asLong());
        outboundWhileRented.put("machineVersion", machine.path("version").asLong());
        outboundWhileRented.put("customerId", customer.path("id").asLong());
        outboundWhileRented.put("salesDate", "2026-05-28");
        outboundWhileRented.put("settlementPrice", "120000.00");
        outboundWhileRented.put("operator", "rental-test");

        mockMvc.perform(post("/api/outbound-orders/vehicle")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(outboundWhileRented)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        Map<String, Object> returnedPayload = new LinkedHashMap<>();
        returnedPayload.put("version", rental.path("version").asLong());
        returnedPayload.put("customerId", customer.path("id").asLong());
        returnedPayload.put("destination", "佛山禅城工地 A 区");
        returnedPayload.put("monthlyRentalPrice", "8800.00");
        returnedPayload.put("startDate", "2026-05-27");
        returnedPayload.put("endDate", "2026-05-29");
        returnedPayload.put("status", "RETURNED");
        returnedPayload.put("operator", "rental-test");
        returnedPayload.put("remark", "已归还");

        mockMvc.perform(put("/api/rentals/{id}", rental.path("id").asLong())
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(returnedPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.endDate").value("2026-05-29"));

        mockMvc.perform(get("/api/statistics/finance")
                        .param("year", "2026")
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annualSummary.rentalIncome").value(851.61))
                .andExpect(jsonPath("$.data.annualSummary.rentalOrders").value(1))
                .andExpect(jsonPath("$.data.monthlyFinance[4].rentalIncome").value(851.61))
                .andExpect(jsonPath("$.data.topRentals[0].destination").value("佛山禅城工地 A 区"))
                .andExpect(jsonPath("$.data.topRentals[0].rentalPrice").value(851.61));

        String orderResponse = mockMvc.perform(post("/api/outbound-orders/vehicle")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(outboundWhileRented)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resourceType").value("MACHINE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        ordersToCleanup.add(objectMapper.readTree(orderResponse).path("data").path("id").asLong());
    }

    @Test
    void financeStatisticsSeparatesRepairReceivableIncomeAndExternalExpense() throws Exception {
        JsonNode customer = createCustomer("Codex 维修客户有限公司");
        JsonNode machine = createMachine();
        JsonNode before = financeDashboard(2026);

        JsonNode completedRepair = createRepair(customer, machine, "COMPLETED", "100.00", "30.00", "20.00");
        repairsToCleanup.add(completedRepair.path("id").asLong());
        JsonNode pendingRepair = createRepair(customer, machine, "PENDING", "900.00", "400.00", "100.00");
        repairsToCleanup.add(pendingRepair.path("id").asLong());

        JsonNode after = financeDashboard(2026);
        assertThat(decimalAt(after, "annualSummary", "repairIncome").subtract(decimalAt(before, "annualSummary", "repairIncome")))
                .isEqualByComparingTo("120.00");
        assertThat(decimalAt(after, "annualSummary", "repairReceivable").subtract(decimalAt(before, "annualSummary", "repairReceivable")))
                .isEqualByComparingTo("150.00");
        assertThat(decimalAt(after, "annualSummary", "repairExpense").subtract(decimalAt(before, "annualSummary", "repairExpense")))
                .isEqualByComparingTo("30.00");
        assertThat(decimalAt(after, "annualSummary", "netProfit").subtract(decimalAt(before, "annualSummary", "netProfit")))
                .isEqualByComparingTo("90.00");
        assertThat(decimalAt(after, "annualSummary", "netCashflow").subtract(decimalAt(before, "annualSummary", "netCashflow")))
                .isEqualByComparingTo("90.00");
        assertThat(after.path("annualSummary").path("repairOrders").asInt()
                - before.path("annualSummary").path("repairOrders").asInt())
                .isEqualTo(1);
        assertThat(decimalAt(completedRepair, "totalFee")).isEqualByComparingTo("150.00");
        assertThat(decimalAt(pendingRepair, "totalFee")).isEqualByComparingTo("1400.00");
    }

    @Test
    void completedRepairConsumesPartsAndCountsPartCostInRepairProfit() throws Exception {
        JsonNode customer = createCustomer("Codex 维修配件客户有限公司");
        JsonNode machine = createMachine();

        String partCode = "OO-REP-" + unique("part");
        partsToCleanup.add(partCode);
        Map<String, Object> partPayload = new LinkedHashMap<>();
        partPayload.put("partCode", partCode);
        partPayload.put("partName", "维修耗用测试配件");
        partPayload.put("specification", "REP-01");
        partPayload.put("partCategory", "维修耗用");
        partPayload.put("quantity", 2);
        partPayload.put("unit", "件");
        partPayload.put("purchasePrice", "8.00");
        partPayload.put("settlementPrice", "12.00");
        partPayload.put("salePrice", "20.00");
        String partResponse = mockMvc.perform(post("/api/parts")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(partPayload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode part = objectMapper.readTree(partResponse).path("data");

        JsonNode before = financeDashboard(2026);
        JsonNode repair = createRepair(customer, machine, "COMPLETED", "100.00", "30.00", "20.00",
                List.of(part.path("id").asLong()));
        Long repairId = repair.path("id").asLong();
        repairsToCleanup.add(repairId);

        assertThat(partRepository.findByPartCode(partCode).orElseThrow().getQuantity()).isEqualTo(1);
        assertThat(stockMovementRepository.findBySourceTypeAndSourceId("REPAIR_RECORD", repairId)).isNotEmpty();

        JsonNode afterCompleted = financeDashboard(2026);
        assertThat(decimalAt(afterCompleted, "annualSummary", "repairIncome").subtract(decimalAt(before, "annualSummary", "repairIncome")))
                .isEqualByComparingTo("120.00");
        assertThat(decimalAt(afterCompleted, "annualSummary", "repairReceivable").subtract(decimalAt(before, "annualSummary", "repairReceivable")))
                .isEqualByComparingTo("150.00");
        assertThat(decimalAt(afterCompleted, "annualSummary", "repairExpense").subtract(decimalAt(before, "annualSummary", "repairExpense")))
                .isEqualByComparingTo("30.00");
        assertThat(decimalAt(afterCompleted, "annualSummary", "repairPartsCost").subtract(decimalAt(before, "annualSummary", "repairPartsCost")))
                .isEqualByComparingTo("12.00");
        assertThat(decimalAt(afterCompleted, "annualSummary", "outboundCost").subtract(decimalAt(before, "annualSummary", "outboundCost")))
                .isEqualByComparingTo("0.00");
        assertThat(decimalAt(afterCompleted, "annualSummary", "netProfit").subtract(decimalAt(before, "annualSummary", "netProfit")))
                .isEqualByComparingTo("78.00");
        assertThat(decimalAt(afterCompleted, "annualSummary", "netCashflow").subtract(decimalAt(before, "annualSummary", "netCashflow")))
                .isEqualByComparingTo("90.00");

        mockMvc.perform(put("/api/repairs/{id}/status", repairId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "version", repair.path("version").asLong(),
                                "status", "PENDING"
                        ))))
                .andExpect(status().isOk());

        assertThat(partRepository.findByPartCode(partCode).orElseThrow().getQuantity()).isEqualTo(2);
        JsonNode afterPending = financeDashboard(2026);
        assertThat(decimalAt(afterPending, "annualSummary", "repairIncome").subtract(decimalAt(before, "annualSummary", "repairIncome")))
                .isEqualByComparingTo("0.00");
        assertThat(decimalAt(afterPending, "annualSummary", "repairPartsCost").subtract(decimalAt(before, "annualSummary", "repairPartsCost")))
                .isEqualByComparingTo("0.00");
        assertThat(decimalAt(afterPending, "annualSummary", "netProfit").subtract(decimalAt(before, "annualSummary", "netProfit")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void completedRepairKeepsSeparatePartCostSnapshotsWhenQuantityIncreases() throws Exception {
        JsonNode customer = createCustomer("Codex 维修成本快照客户有限公司");
        JsonNode machine = createMachine();

        String partCode = "OO-REP-SNAPSHOT-" + unique("part");
        partsToCleanup.add(partCode);
        Map<String, Object> partPayload = new LinkedHashMap<>();
        partPayload.put("partCode", partCode);
        partPayload.put("partName", "Repair cost snapshot part");
        partPayload.put("specification", "SNAP-01");
        partPayload.put("partCategory", "REPAIR_SNAPSHOT");
        partPayload.put("quantity", 3);
        partPayload.put("unit", "pcs");
        partPayload.put("purchasePrice", "8.00");
        partPayload.put("settlementPrice", "12.00");
        partPayload.put("salePrice", "20.00");
        String partResponse = mockMvc.perform(post("/api/parts")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(partPayload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode part = objectMapper.readTree(partResponse).path("data");
        Long partId = part.path("id").asLong();

        JsonNode repair = createRepair(customer, machine, "COMPLETED", "100.00", "0.00", "40.00",
                List.of(partId));
        Long repairId = repair.path("id").asLong();
        repairsToCleanup.add(repairId);

        List<RepairPartUsage> firstUsages = repairPartUsageRepository.findByRepairIdOrderByIdAsc(repairId);
        assertThat(firstUsages).hasSize(1);
        assertThat(firstUsages.get(0).getQuantity()).isEqualTo(1);
        assertThat(firstUsages.get(0).getUnitPrice()).isEqualByComparingTo("12.00");

        PartInventory currentPart = partRepository.findByPartCode(partCode).orElseThrow();
        Map<String, Object> priceEditPayload = new LinkedHashMap<>(partPayload);
        priceEditPayload.put("version", currentPart.getVersion());
        priceEditPayload.put("quantity", currentPart.getQuantity());
        priceEditPayload.put("settlementPrice", "30.00");
        mockMvc.perform(put("/api/parts/{id}", currentPart.getId())
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(priceEditPayload)))
                .andExpect(status().isOk());

        Map<String, Object> increaseRepairPayload = repairPayload(customer, machine, "COMPLETED",
                "100.00", "0.00", "40.00", List.of(partId, partId));
        increaseRepairPayload.put("version", repair.path("version").asLong());
        String increasedResponse = mockMvc.perform(put("/api/repairs/{id}", repairId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(increaseRepairPayload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode increasedRepair = objectMapper.readTree(increasedResponse).path("data");

        List<RepairPartUsage> increasedUsages = repairPartUsageRepository.findByRepairIdOrderByIdAsc(repairId);
        assertThat(increasedUsages).hasSize(2);
        assertThat(increasedUsages.get(0).getQuantity()).isEqualTo(1);
        assertThat(increasedUsages.get(0).getUnitPrice()).isEqualByComparingTo("12.00");
        assertThat(increasedUsages.get(1).getQuantity()).isEqualTo(1);
        assertThat(increasedUsages.get(1).getUnitPrice()).isEqualByComparingTo("30.00");
        assertThat(repairUsageTotalCost(increasedUsages)).isEqualByComparingTo("42.00");
        assertThat(partRepository.findByPartCode(partCode).orElseThrow().getQuantity()).isEqualTo(1);

        Map<String, Object> reduceRepairPayload = repairPayload(customer, machine, "COMPLETED",
                "100.00", "0.00", "40.00", List.of(partId));
        reduceRepairPayload.put("version", increasedRepair.path("version").asLong());
        mockMvc.perform(put("/api/repairs/{id}", repairId)
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reduceRepairPayload)))
                .andExpect(status().isOk());

        List<RepairPartUsage> reducedUsages = repairPartUsageRepository.findByRepairIdOrderByIdAsc(repairId);
        assertThat(reducedUsages).hasSize(1);
        assertThat(reducedUsages.get(0).getQuantity()).isEqualTo(1);
        assertThat(reducedUsages.get(0).getUnitPrice()).isEqualByComparingTo("12.00");
        assertThat(partRepository.findByPartCode(partCode).orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test
    void amountInputsRejectNegativeValues() throws Exception {
        JsonNode customer = createCustomer("Codex 负数校验客户有限公司");
        JsonNode machine = createMachine();

        Map<String, Object> machinePayload = new LinkedHashMap<>();
        machinePayload.put("vehicleProductNumber", "NEG-" + unique("machine"));
        machinePayload.put("name", "Negative price test machine");
        machinePayload.put("specificationModel", "NEG-01");
        machinePayload.put("purchasePrice", "-0.01");
        mockMvc.perform(post("/api/inventory")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(machinePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        String partCode = "NEG-PART-" + unique("part");
        Map<String, Object> partPayload = new LinkedHashMap<>();
        partPayload.put("partCode", partCode);
        partPayload.put("partName", "Negative price test part");
        partPayload.put("quantity", 1);
        partPayload.put("salePrice", "-0.01");
        mockMvc.perform(post("/api/parts")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(partPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        Map<String, Object> repairPayload = new LinkedHashMap<>();
        repairPayload.put("repairDate", "2026-05-10T10:00:00");
        repairPayload.put("machineId", machine.path("id").asLong());
        repairPayload.put("customerId", customer.path("id").asLong());
        repairPayload.put("faultDescription", "Negative repair fee test");
        repairPayload.put("repairPersonChoice", "OTHER");
        repairPayload.put("repairFee", "-0.01");
        repairPayload.put("repairExpense", "0.00");
        repairPayload.put("partsFee", "0.00");
        repairPayload.put("status", "COMPLETED");
        mockMvc.perform(post("/api/repairs")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(repairPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        Map<String, Object> vehicleOutboundPayload = new LinkedHashMap<>();
        vehicleOutboundPayload.put("machineId", machine.path("id").asLong());
        vehicleOutboundPayload.put("machineVersion", machine.path("version").asLong());
        vehicleOutboundPayload.put("customerId", customer.path("id").asLong());
        vehicleOutboundPayload.put("settlementPrice", "-0.01");
        mockMvc.perform(post("/api/outbound-orders/vehicle")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(vehicleOutboundPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        JsonNode part = createPart();
        Map<String, Object> partOutboundPayload = new LinkedHashMap<>();
        partOutboundPayload.put("partCode", part.path("partCode").asText());
        partOutboundPayload.put("partVersion", part.path("version").asLong());
        partOutboundPayload.put("quantity", 1);
        partOutboundPayload.put("customerId", customer.path("id").asLong());
        partOutboundPayload.put("settlementPrice", "-0.01");
        mockMvc.perform(post("/api/outbound-orders/part")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(partOutboundPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        JsonNode order = createVehicleOrder(customer, machine);
        Map<String, Object> updatePayload = new LinkedHashMap<>();
        updatePayload.put("version", order.path("version").asLong());
        updatePayload.put("salePrice", "-0.01");
        mockMvc.perform(put("/api/outbound-orders/{id}", order.path("id").asLong())
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void financeStatisticsPreferSettlementPriceForCostsAndSettlementStockValue() throws Exception {
        JsonNode customer = createCustomer("Codex 财报客户有限公司");
        JsonNode before = financeDashboard(2026);
        BigDecimal beforePartCostValue = stockValue(before, "PART", "costValue");
        BigDecimal beforePartSettlementValue = stockValue(before, "PART", "settlementValue");

        String partCode = "OO-FIN-" + unique("part");
        partsToCleanup.add(partCode);
        Map<String, Object> partPayload = new LinkedHashMap<>();
        partPayload.put("partCode", partCode);
        partPayload.put("partName", "财报结算价测试配件");
        partPayload.put("specification", "FIN-01");
        partPayload.put("partCategory", "测试配件");
        partPayload.put("quantity", 0);
        partPayload.put("unit", "件");
        partPayload.put("purchasePrice", "10.00");
        partPayload.put("settlementPrice", "12.00");
        partPayload.put("salePrice", "20.00");

        String partResponse = mockMvc.perform(post("/api/parts")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(partPayload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode part = objectMapper.readTree(partResponse).path("data");

        Map<String, Object> inboundPayload = new LinkedHashMap<>();
        inboundPayload.put("partCode", partCode);
        inboundPayload.put("quantity", 5);
        inboundPayload.put("version", part.path("version").asLong());
        inboundPayload.put("operator", "finance-test");
        String inboundResponse = mockMvc.perform(put("/api/parts/inbound")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(inboundPayload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode inboundPart = objectMapper.readTree(inboundResponse).path("data");

        JsonNode afterInbound = financeDashboard(2026);
        assertThat(decimalAt(afterInbound, "annualSummary", "inboundCost").subtract(decimalAt(before, "annualSummary", "inboundCost")))
                .isEqualByComparingTo("60.00");
        assertThat(stockValue(afterInbound, "PART", "costValue").subtract(beforePartCostValue))
                .isEqualByComparingTo("60.00");
        assertThat(stockValue(afterInbound, "PART", "settlementValue").subtract(beforePartSettlementValue))
                .isEqualByComparingTo("60.00");

        Map<String, Object> outboundPayload = new LinkedHashMap<>();
        outboundPayload.put("partCode", partCode);
        outboundPayload.put("partVersion", inboundPart.path("version").asLong());
        outboundPayload.put("quantity", 2);
        outboundPayload.put("customerId", customer.path("id").asLong());
        outboundPayload.put("settlementPrice", "30.00");
        outboundPayload.put("operator", "finance-test");

        String outboundResponse = mockMvc.perform(post("/api/outbound-orders/part")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(outboundPayload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode order = objectMapper.readTree(outboundResponse).path("data");
        ordersToCleanup.add(order.path("id").asLong());

        JsonNode afterOutbound = financeDashboard(2026);
        assertThat(decimalAt(afterOutbound, "annualSummary", "outboundRevenue").subtract(decimalAt(afterInbound, "annualSummary", "outboundRevenue")))
                .isEqualByComparingTo("60.00");
        assertThat(decimalAt(afterOutbound, "annualSummary", "outboundCost").subtract(decimalAt(afterInbound, "annualSummary", "outboundCost")))
                .isEqualByComparingTo("24.00");
        assertThat(decimalAt(afterOutbound, "annualSummary", "grossProfit").subtract(decimalAt(afterInbound, "annualSummary", "grossProfit")))
                .isEqualByComparingTo("36.00");
        assertThat(stockValue(afterOutbound, "PART", "costValue").subtract(stockValue(afterInbound, "PART", "costValue")))
                .isEqualByComparingTo("-24.00");
        assertThat(stockValue(afterOutbound, "PART", "settlementValue").subtract(stockValue(afterInbound, "PART", "settlementValue")))
                .isEqualByComparingTo("-24.00");

        PartInventory currentPart = partRepository.findByPartCode(partCode).orElseThrow();
        Map<String, Object> priceEditPayload = new LinkedHashMap<>();
        priceEditPayload.put("version", currentPart.getVersion());
        priceEditPayload.put("partCode", partCode);
        priceEditPayload.put("partName", "Finance snapshot test part");
        priceEditPayload.put("specification", "FIN-01");
        priceEditPayload.put("partCategory", "FINANCE_TEST");
        priceEditPayload.put("quantity", currentPart.getQuantity());
        priceEditPayload.put("unit", "pcs");
        priceEditPayload.put("purchasePrice", "90.00");
        priceEditPayload.put("settlementPrice", "99.00");
        priceEditPayload.put("salePrice", "120.00");

        mockMvc.perform(put("/api/parts/{id}", currentPart.getId())
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(priceEditPayload)))
                .andExpect(status().isOk());

        JsonNode afterPriceEdit = financeDashboard(2026);
        assertThat(decimalAt(afterPriceEdit, "annualSummary", "inboundCost").subtract(decimalAt(afterOutbound, "annualSummary", "inboundCost")))
                .isEqualByComparingTo("0.00");
        assertThat(decimalAt(afterPriceEdit, "annualSummary", "outboundCost").subtract(decimalAt(afterOutbound, "annualSummary", "outboundCost")))
                .isEqualByComparingTo("0.00");
        assertThat(decimalAt(afterPriceEdit, "annualSummary", "outboundRevenue").subtract(decimalAt(afterOutbound, "annualSummary", "outboundRevenue")))
                .isEqualByComparingTo("0.00");
        assertThat(decimalAt(afterPriceEdit, "annualSummary", "grossProfit").subtract(decimalAt(afterOutbound, "annualSummary", "grossProfit")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void financeStatisticsCountsInitialAndAdjustmentCostsButDirectOutboundIsNotSales() throws Exception {
        JsonNode before = financeDashboard(2026);

        String partCode = "OO-FIN-ADJ-" + unique("part");
        partsToCleanup.add(partCode);
        Map<String, Object> partPayload = new LinkedHashMap<>();
        partPayload.put("partCode", partCode);
        partPayload.put("partName", "Finance initial adjustment test part");
        partPayload.put("specification", "FIN-ADJ-01");
        partPayload.put("partCategory", "FINANCE_TEST");
        partPayload.put("quantity", 4);
        partPayload.put("unit", "pcs");
        partPayload.put("purchasePrice", "5.00");
        partPayload.put("settlementPrice", "7.00");
        partPayload.put("salePrice", "11.00");

        String partResponse = mockMvc.perform(post("/api/parts")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(partPayload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createdPart = objectMapper.readTree(partResponse).path("data");

        JsonNode afterCreate = financeDashboard(2026);
        assertThat(decimalAt(afterCreate, "annualSummary", "inboundCost").subtract(decimalAt(before, "annualSummary", "inboundCost")))
                .isEqualByComparingTo("28.00");

        Map<String, Object> adjustPayload = new LinkedHashMap<>(partPayload);
        adjustPayload.put("version", createdPart.path("version").asLong());
        adjustPayload.put("quantity", 6);
        String adjustResponse = mockMvc.perform(put("/api/parts/{id}", createdPart.path("id").asLong())
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(adjustPayload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode adjustedPart = objectMapper.readTree(adjustResponse).path("data");

        JsonNode afterAdjust = financeDashboard(2026);
        assertThat(decimalAt(afterAdjust, "annualSummary", "inboundCost").subtract(decimalAt(afterCreate, "annualSummary", "inboundCost")))
                .isEqualByComparingTo("14.00");

        Map<String, Object> directOutboundPayload = new LinkedHashMap<>();
        directOutboundPayload.put("partCode", partCode);
        directOutboundPayload.put("quantity", 1);
        directOutboundPayload.put("version", adjustedPart.path("version").asLong());
        directOutboundPayload.put("operator", "finance-test");
        mockMvc.perform(put("/api/parts/outbound")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(directOutboundPayload)))
                .andExpect(status().isOk());

        JsonNode afterDirectOutbound = financeDashboard(2026);
        assertThat(decimalAt(afterDirectOutbound, "annualSummary", "outboundCost").subtract(decimalAt(afterAdjust, "annualSummary", "outboundCost")))
                .isEqualByComparingTo("7.00");
        assertThat(decimalAt(afterDirectOutbound, "annualSummary", "outboundRevenue").subtract(decimalAt(afterAdjust, "annualSummary", "outboundRevenue")))
                .isEqualByComparingTo("0.00");
        assertThat(decimalAt(afterDirectOutbound, "annualSummary", "grossProfit").subtract(decimalAt(afterAdjust, "annualSummary", "grossProfit")))
                .isEqualByComparingTo("0.00");
        assertThat(afterDirectOutbound.path("topOutbounds").toString()).doesNotContain(partCode);
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

    private JsonNode createRepair(
            JsonNode customer,
            JsonNode machine,
            String repairStatus,
            String repairFee,
            String repairExpense,
            String partsFee
    ) throws Exception {
        return createRepair(customer, machine, repairStatus, repairFee, repairExpense, partsFee, List.of());
    }

    private JsonNode createRepair(
            JsonNode customer,
            JsonNode machine,
            String repairStatus,
            String repairFee,
            String repairExpense,
            String partsFee,
            List<Long> usedPartIds
    ) throws Exception {
        Map<String, Object> payload = repairPayload(customer, machine, repairStatus, repairFee, repairExpense,
                partsFee, usedPartIds);

        String response = mockMvc.perform(post("/api/repairs")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private Map<String, Object> repairPayload(
            JsonNode customer,
            JsonNode machine,
            String repairStatus,
            String repairFee,
            String repairExpense,
            String partsFee,
            List<Long> usedPartIds
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repairDate", "2026-05-10T10:00:00");
        payload.put("machineId", machine.path("id").asLong());
        payload.put("customerId", customer.path("id").asLong());
        payload.put("faultDescription", "财报维修测试故障");
        payload.put("repairContent", "财报维修测试处理");
        payload.put("repairPersonChoice", "OTHER");
        payload.put("repairFee", repairFee);
        payload.put("repairExpense", repairExpense);
        payload.put("partsFee", partsFee);
        if (usedPartIds != null && !usedPartIds.isEmpty()) {
            payload.put("usedPartIds", usedPartIds);
        }
        payload.put("status", repairStatus);
        payload.put("remarks", "财报维修统计测试");
        return payload;
    }

    private BigDecimal repairUsageTotalCost(List<RepairPartUsage> usages) {
        return usages.stream()
                .map(usage -> usage.getUnitPrice().multiply(BigDecimal.valueOf(usage.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private JsonNode financeDashboard(int year) throws Exception {
        String response = mockMvc.perform(get("/api/statistics/finance")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(superToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private BigDecimal decimalAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String segment : path) {
            current = current.path(segment);
        }
        return new BigDecimal(current.asText("0"));
    }

    private BigDecimal stockValue(JsonNode dashboard, String resourceType, String field) {
        for (JsonNode row : dashboard.path("stockValues")) {
            if (resourceType.equals(row.path("resourceType").asText())) {
                return decimalAt(row, field);
            }
        }
        return BigDecimal.ZERO;
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
