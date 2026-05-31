package com.example.forklift_erp.config;

import com.example.forklift_erp.dto.ModificationWorkOrderCreateDTO;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.Customer;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.ModificationWorkOrder;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.service.MachineInventoryService;
import com.example.forklift_erp.service.ModificationWorkOrderService;
import com.example.forklift_erp.service.PartInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(20)
@ConditionalOnProperty(prefix = "forklift.seed-demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements CommandLineRunner {
    private static final String DEMO_OPERATOR = "demo-seed";
    private static final String DEMO_VEHICLE_NUMBER = "DEMO-MOD-001";
    private static final String DEMO_PART_CODE = "DEMO-PART-TIRE-SOLID";

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private MachineInventoryService machineInventoryService;

    @Autowired
    private MachineConfigRepository machineConfigRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private PartInventoryService partInventoryService;

    @Autowired
    private ModificationWorkOrderRepository workOrderRepository;

    @Autowired
    private ModificationWorkOrderService modificationWorkOrderService;

    @Override
    @Transactional
    public void run(String... args) {
        ConfigItem tireItem = ensureConfigItem("DEMO_TIRE_TYPE", "轮胎与底盘", "轮胎", "轮胎类型", 10);
        ConfigValue pneumaticTire = ensureConfigValue(tireItem, "充气胎 28x9-15", "DEMO_TIRE_PNEUMATIC", true, 10);
        ensureConfigValue(tireItem, "实心胎 28x9-15", "DEMO_TIRE_SOLID", false, 20);

        ensureCustomer();
        MachineInventory machine = ensureDemoMachine();
        MachineConfig tireConfig = ensureMachineConfig(machine, tireItem, pneumaticTire);
        PartInventory solidTire = ensureDemoPart();
        ensureDemoWorkOrder(machine, tireConfig, solidTire);
    }

    private ConfigItem ensureConfigItem(String code, String category, String subCategory, String itemName, int sortOrder) {
        return configItemRepository.findByItemCode(code).orElseGet(() -> {
            ConfigItem item = new ConfigItem();
            item.setItemCode(code);
            item.setCategory(category);
            item.setSubCategory(subCategory);
            item.setItemName(itemName);
            item.setInputType("SELECT");
            item.setIsRequired(true);
            item.setSortOrder(sortOrder);
            return configItemRepository.saveAndFlush(item);
        });
    }

    private ConfigValue ensureConfigValue(ConfigItem item, String label, String code, boolean isDefault, int sortOrder) {
        return configValueRepository.findByConfigItemIdOrderBySortOrderAsc(item.getId()).stream()
                .filter(value -> label.equals(value.getValueLabel()) || code.equals(value.getValueCode()))
                .findFirst()
                .orElseGet(() -> {
                    ConfigValue value = new ConfigValue();
                    value.setConfigItemId(item.getId());
                    value.setValueLabel(label);
                    value.setValueCode(code);
                    value.setIsDefault(isDefault);
                    value.setSortOrder(sortOrder);
                    value.setRemark("系统启动示例数据，可用于改装工单验证");
                    return configValueRepository.saveAndFlush(value);
                });
    }

    private void ensureCustomer() {
        customerRepository.findByCompanyName("演示客户-改装测试").orElseGet(() -> {
            Customer customer = new Customer();
            customer.setCompanyName("演示客户-改装测试");
            customer.setContactName("测试联系人");
            customer.setContactPhone("13800000000");
            customer.setAddress("本地演示数据");
            customer.setRemarks("系统启动自动补齐的测试客户，不会清理已有客户");
            return customerRepository.saveAndFlush(customer);
        });
    }

    private MachineInventory ensureDemoMachine() {
        return machineRepository.findByVehicleProductNumber(DEMO_VEHICLE_NUMBER).orElseGet(() -> {
            MachineInventory machine = new MachineInventory();
            machine.setVehicleProductNumber(DEMO_VEHICLE_NUMBER);
            machine.setName("演示改装叉车");
            machine.setSpecificationModel("CPCD30-DEMO");
            machine.setMachineType("内燃叉车");
            machine.setConfiguration("标准门架 / 充气胎 / 标准货叉");
            machine.setSupplier("演示供应商");
            machine.setWarehouseName("演示仓库");
            machine.setApplicationNumber("DEMO-APP-001");
            machine.setMaterialNumber("DEMO-MAT-001");
            machine.setEngineNumber("DEMO-ENG-001");
            machine.setFrameNumber("DEMO-FRAME-001");
            machine.setWarrantyCardNumber("DEMO-WARRANTY-001");
            machine.setManufacturingDate(LocalDate.now().minusMonths(2));
            machine.setInboundDate(LocalDateTime.now().minusDays(7));
            machine.setPurchasePrice(new BigDecimal("68000.00"));
            machine.setSalePrice(new BigDecimal("86000.00"));
            machine.setSettlementPrice(new BigDecimal("82000.00"));
            machine.setInventoryCount(1);
            machine.setModelOnly(false);
            machine.setStockStatus("IN_STOCK");
            machine.setRemarks("系统启动自动补齐的改装工单测试整车");
            return machineInventoryService.save(machine);
        });
    }

    private MachineConfig ensureMachineConfig(MachineInventory machine, ConfigItem item, ConfigValue value) {
        return machineConfigRepository.findByMachineId(machine.getId()).stream()
                .filter(config -> item.getId().equals(config.getConfigItemId()))
                .findFirst()
                .orElseGet(() -> {
                    MachineConfig config = new MachineConfig();
                    config.setMachineId(machine.getId());
                    config.setConfigItemId(item.getId());
                    config.setConfigValueId(value.getId());
                    config.setItemName(item.getItemName());
                    config.setSelectedValue(value.getValueLabel());
                    config.setIsStandard(true);
                    config.setConfigSource("FACTORY_STANDARD");
                    config.setInstalledDate(LocalDateTime.now().minusDays(7));
                    config.setRemark("演示车辆原始轮胎配置");
                    return machineConfigRepository.saveAndFlush(config);
                });
    }

    private PartInventory ensureDemoPart() {
        return partRepository.findByPartCode(DEMO_PART_CODE).orElseGet(() -> {
            PartInventory part = new PartInventory();
            part.setPartCode(DEMO_PART_CODE);
            part.setPartBrand("演示品牌");
            part.setPartName("实心胎 28x9-15");
            part.setSpecification("28x9-15");
            part.setPartCategory("轮胎");
            part.setApplicableModels("CPCD30-DEMO");
            part.setSource("PURCHASE");
            part.setQuantity(6);
            part.setUnit("条");
            part.setPurchasePrice(new BigDecimal("1200.00"));
            part.setSalePrice(new BigDecimal("1800.00"));
            part.setSettlementPrice(new BigDecimal("1600.00"));
            part.setInboundDate(LocalDateTime.now().minusDays(5));
            part.setRemarks("系统启动自动补齐的改装工单测试配件");
            return partInventoryService.save(part);
        });
    }

    private void ensureDemoWorkOrder(MachineInventory machine, MachineConfig config, PartInventory part) {
        boolean hasOpenOrder = workOrderRepository.findByMachineIdOrderByCreatedAtDesc(machine.getId()).stream()
                .anyMatch(order -> !isClosed(order));
        if (hasOpenOrder) {
            return;
        }

        MachineInventory currentMachine = machineRepository.findById(machine.getId()).orElse(machine);
        MachineConfig currentConfig = machineConfigRepository.findById(config.getId()).orElse(config);
        PartInventory currentPart = partRepository.findById(part.getId()).orElse(part);
        if (currentMachine.getInventoryCount() == null || currentMachine.getInventoryCount() < 1) {
            return;
        }

        ModificationWorkOrderCreateDTO.Line line = new ModificationWorkOrderCreateDTO.Line();
        line.setMachineConfigId(currentConfig.getId());
        line.setMachineConfigVersion(currentConfig.getVersion());
        line.setNewPartId(currentPart.getId());
        line.setNewPartVersion(currentPart.getVersion());
        line.setQuantity(1);
        line.setOldPartAction("STOCK_IN");
        line.setRemark("演示：客户要求替换为实心胎");

        ModificationWorkOrderCreateDTO request = new ModificationWorkOrderCreateDTO();
        request.setMachineId(currentMachine.getId());
        request.setMachineVersion(currentMachine.getVersion());
        request.setCustomerName("演示客户-改装测试");
        request.setSalesOrderNo("DEMO-SO-MOD-001");
        request.setOperator(DEMO_OPERATOR);
        request.setRemark("系统启动自动补齐的待处理改装工单");
        request.setLines(List.of(line));
        modificationWorkOrderService.create(request);
    }

    private boolean isClosed(ModificationWorkOrder order) {
        return "COMPLETED".equals(order.getStatus()) || "CANCELED".equals(order.getStatus());
    }
}
