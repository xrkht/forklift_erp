package com.example.forklift_erp;

import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BusinessWorkflowIntegrationTests extends TestcontainersDatabaseSupport {
    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        String username = unique("workflow-super");
        createUserDirectly(username, "SUPER_ADMIN");
        superToken = login(username);
    }

    @Test
    void machineInboundRollsBackWhenPurchaseOrderCreationFails() throws Exception {
        String vehicleNumber = unique("workflow-machine");
        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("vehicleProductNumber", vehicleNumber);
        machine.put("name", "Workflow rollback vehicle");
        machine.put("specificationModel", "CPCD30-WORKFLOW");
        machine.put("machineType", "Diesel Forklift");
        machine.put("inventoryCount", 1);
        machine.put("stockStatus", "IN_STOCK");

        Map<String, Object> purchaseOrder = new LinkedHashMap<>();
        purchaseOrder.put("supplierId", Long.MAX_VALUE);
        purchaseOrder.put("resourceType", "MACHINE");
        purchaseOrder.put("resourceCode", vehicleNumber);
        purchaseOrder.put("resourceName", "Workflow rollback vehicle");
        purchaseOrder.put("specificationModel", "CPCD30-WORKFLOW");
        purchaseOrder.put("quantity", 1);
        purchaseOrder.put("unitPrice", "88000.00");
        purchaseOrder.put("status", "ORDERED");

        mockMvc.perform(post("/api/workflows/machine-inbound-purchase")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "inbound", Map.of("machineInventory", machine, "configs", List.of()),
                                "purchaseOrder", purchaseOrder
                        ))))
                .andExpect(status().isNotFound());

        assertThat(machineRepository.findByVehicleProductNumber(vehicleNumber)).isEmpty();
    }

    @Test
    void customerCreationRollsBackWhenVehicleOutboundFails() throws Exception {
        String companyName = unique("workflow-customer");
        Map<String, Object> customer = Map.of(
                "companyName", companyName,
                "contactName", "Workflow test"
        );
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("machineId", Long.MAX_VALUE);
        outbound.put("settlementPrice", "1.00");

        mockMvc.perform(post("/api/workflows/vehicle-outbound-with-customer")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "customer", customer,
                                "outboundOrder", outbound
                        ))))
                .andExpect(status().isNotFound());

        assertThat(customerRepository.findByCompanyName(companyName)).isEmpty();
    }
}
