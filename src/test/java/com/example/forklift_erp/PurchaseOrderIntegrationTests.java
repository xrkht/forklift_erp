package com.example.forklift_erp;

import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.PurchaseOrderRepository;
import com.example.forklift_erp.repository.SupplierRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PurchaseOrderIntegrationTests extends TestcontainersDatabaseSupport {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    private final List<Long> purchaseOrderIdsToCleanup = new ArrayList<>();
    private final List<Long> supplierIdsToCleanup = new ArrayList<>();
    private final List<Long> configValueIdsToCleanup = new ArrayList<>();
    private final List<Long> configItemIdsToCleanup = new ArrayList<>();

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        String superUsername = unique("super");
        createUserDirectly(superUsername, "SUPER_ADMIN");
        superToken = login(superUsername);
    }

    @AfterEach
    void tearDownPurchaseOrders() {
        for (Long orderId : purchaseOrderIdsToCleanup.reversed()) {
            purchaseOrderRepository.findById(orderId).ifPresent(purchaseOrderRepository::delete);
        }
        purchaseOrderIdsToCleanup.clear();

        for (Long supplierId : supplierIdsToCleanup.reversed()) {
            supplierRepository.findById(supplierId).ifPresent(supplierRepository::delete);
        }
        supplierIdsToCleanup.clear();

        for (Long valueId : configValueIdsToCleanup.reversed()) {
            configValueRepository.findById(valueId).ifPresent(configValueRepository::delete);
        }
        configValueIdsToCleanup.clear();

        for (Long itemId : configItemIdsToCleanup.reversed()) {
            configItemRepository.findById(itemId).ifPresent(configItemRepository::delete);
        }
        configItemIdsToCleanup.clear();
    }

    @Test
    void purchaseOrdersCanBeFilteredByPartAndMachineResourceType() throws Exception {
        String marker = unique("purchase");
        Long supplierId = createSupplier("配件供应商-" + marker);
        Long configItemId = createConfigItem(marker);
        Long configValueId = createConfigValue(configItemId, marker);

        JsonNode partOrder = createPurchaseOrder(Map.of(
                "supplierId", supplierId,
                "resourceType", "PART",
                "configItemId", configItemId,
                "configValueId", configValueId,
                "quantity", 2,
                "unitPrice", "15.00",
                "status", "ORDERED",
                "remark", marker
        ));

        JsonNode machineOrder = createPurchaseOrder(Map.of(
                "supplierName", "整车供应商-" + marker,
                "resourceType", "MACHINE",
                "resourceCode", "MACHINE-" + marker,
                "resourceName", "测试整车-" + marker,
                "specificationModel", "CPCD30-" + marker,
                "quantity", 1,
                "unitPrice", "88000.00",
                "status", "RECEIVED",
                "remark", marker
        ));

        assertThat(partOrder.path("resourceType").asText()).isEqualTo("PART");
        assertThat(machineOrder.path("resourceType").asText()).isEqualTo("MACHINE");
        assertThat(machineOrder.path("statusBeforeReceived").asText()).isEqualTo("ORDERED");

        mockMvc.perform(get("/api/purchase-orders")
                        .header("Authorization", bearer(superToken))
                        .param("paged", "true")
                        .param("keyword", marker)
                        .param("resourceType", "PART")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("PART"))
                .andExpect(jsonPath("$.data.content[0].resourceName").value("配件-" + marker));

        mockMvc.perform(get("/api/purchase-orders")
                        .header("Authorization", bearer(superToken))
                        .param("paged", "true")
                        .param("keyword", marker)
                        .param("resourceType", "MACHINE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("MACHINE"))
                .andExpect(jsonPath("$.data.content[0].specificationModel").value("CPCD30-" + marker));

        mockMvc.perform(get("/api/statistics/list-summary")
                        .header("Authorization", bearer(superToken))
                        .param("type", "purchases")
                        .param("keyword", marker)
                        .param("resourceType", "MACHINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[0].value").value(1))
                .andExpect(jsonPath("$.data.cards[2].value").value(1));
    }

    @Test
    void receiptToggleRestoresPartialStatusAndRejectsCanceledOrders() throws Exception {
        String marker = unique("receipt");
        Map<String, Object> partialPayload = machineOrderPayload(marker + "-partial", "PARTIAL");
        JsonNode partialOrder = createPurchaseOrder(partialPayload);

        String receivedResponse = mockMvc.perform(put("/api/purchase-orders/{id}/received", partialOrder.path("id").asLong())
                        .header("Authorization", bearer(superToken))
                        .param("received", "true")
                        .param("version", partialOrder.path("version").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.statusBeforeReceived").value("PARTIAL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode receivedOrder = objectMapper.readTree(receivedResponse).path("data");

        String restoredResponse = mockMvc.perform(put("/api/purchase-orders/{id}/received", partialOrder.path("id").asLong())
                        .header("Authorization", bearer(superToken))
                        .param("received", "false")
                        .param("version", receivedOrder.path("version").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode restoredOrder = objectMapper.readTree(restoredResponse).path("data");
        assertThat(restoredOrder.path("statusBeforeReceived").isNull()).isTrue();

        Map<String, Object> canceledPayload = machineOrderPayload(marker + "-canceled", "CANCELED");
        JsonNode canceledOrder = createPurchaseOrder(canceledPayload);
        mockMvc.perform(put("/api/purchase-orders/{id}/received", canceledOrder.path("id").asLong())
                        .header("Authorization", bearer(superToken))
                        .param("received", "true")
                        .param("version", canceledOrder.path("version").asText()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));

        Map<String, Object> canceledUpdate = new LinkedHashMap<>(canceledPayload);
        canceledUpdate.put("version", canceledOrder.path("version").asLong());
        canceledUpdate.put("status", "RECEIVED");
        mockMvc.perform(put("/api/purchase-orders/{id}", canceledOrder.path("id").asLong())
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(canceledUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void legacyReceivedOrderWithoutHistoryFallsBackToOrdered() throws Exception {
        JsonNode created = createPurchaseOrder(machineOrderPayload(unique("legacy"), "ORDERED"));
        var legacyOrder = purchaseOrderRepository.findById(created.path("id").asLong()).orElseThrow();
        legacyOrder.setStatus("RECEIVED");
        legacyOrder.setStatusBeforeReceived(null);
        legacyOrder = purchaseOrderRepository.saveAndFlush(legacyOrder);

        mockMvc.perform(put("/api/purchase-orders/{id}/received", legacyOrder.getId())
                        .header("Authorization", bearer(superToken))
                        .param("received", "false")
                        .param("version", String.valueOf(legacyOrder.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ORDERED"))
                .andExpect(jsonPath("$.data.statusBeforeReceived").isEmpty());
    }

    private Map<String, Object> machineOrderPayload(String marker, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplierName", "Machine supplier " + marker);
        payload.put("resourceType", "MACHINE");
        payload.put("resourceCode", "MACHINE-" + marker);
        payload.put("resourceName", "Test machine " + marker);
        payload.put("specificationModel", "CPCD30-" + marker);
        payload.put("quantity", 1);
        payload.put("unitPrice", "88000.00");
        payload.put("status", status);
        payload.put("remark", marker);
        return payload;
    }

    private Long createSupplier(String supplierName) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplierName", supplierName);
        payload.put("supplierType", "配件供应商");

        String response = mockMvc.perform(post("/api/suppliers")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(response).path("data").path("id").asLong();
        supplierIdsToCleanup.add(id);
        return id;
    }

    private Long createConfigItem(String marker) throws Exception {
        ConfigItem item = new ConfigItem();
        item.setCategory("测试配件");
        item.setSubCategory("测试分类");
        item.setItemName("测试规格-" + marker);
        item.setItemCode("PO-CFG-" + marker);
        item.setInputType("SELECT");
        item.setUnit("件");
        item.setIsRequired(false);
        item.setSortOrder(0);

        String response = mockMvc.perform(post("/api/config/items")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(item)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(response).path("data").path("id").asLong();
        configItemIdsToCleanup.add(id);
        return id;
    }

    private Long createConfigValue(Long configItemId, String marker) throws Exception {
        ConfigValue value = new ConfigValue();
        value.setConfigItemId(configItemId);
        value.setValueLabel("配件-" + marker);
        value.setValueCode("PART-" + marker);
        value.setIsDefault(false);
        value.setSortOrder(0);

        String response = mockMvc.perform(post("/api/config/values")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(value)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(response).path("data").path("id").asLong();
        configValueIdsToCleanup.add(id);
        return id;
    }

    private JsonNode createPurchaseOrder(Map<String, Object> payload) throws Exception {
        String response = mockMvc.perform(post("/api/purchase-orders")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        purchaseOrderIdsToCleanup.add(data.path("id").asLong());
        return data;
    }
}
