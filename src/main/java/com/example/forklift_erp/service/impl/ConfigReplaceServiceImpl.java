// src/main/java/com/example/forklift_erp/service/impl/ConfigReplaceServiceImpl.java
package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.ConfigReplaceRequestDTO;
import com.example.forklift_erp.dto.PartReplaceRequestDTO;
import com.example.forklift_erp.entity.*;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.*;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.ConfigReplaceService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.StockLedgerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ConfigReplaceServiceImpl implements ConfigReplaceService {

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private MachineConfigRepository machineConfigRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private ConfigReplaceLogRepository replaceLogRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Override
    @Transactional
    public ConfigReplaceLog performReplace(ConfigReplaceRequestDTO request) {
        // 1. 校验车辆存在
        MachineInventory machine = machineRepository.findByIdForUpdate(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));

        // 2. 校验新配置值存在
        collaborationService.validateWrite(machine, request.getMachineVersion());

        ConfigValue newConfigValue = configValueRepository.findById(request.getNewConfigValueId())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "新配置值不存在"));

        // 3. 找到旧的配置记录（同一车辆的同一配置项）
        List<MachineConfig> oldConfigs = machineConfigRepository.findByMachineId(request.getMachineId());
        MachineConfig oldConfig = oldConfigs.stream()
                .filter(c -> c.getConfigItemId().equals(request.getConfigItemId()))
                .map(c -> machineConfigRepository.findByIdForUpdate(c.getId()).orElse(c))
                .findFirst()
                .orElse(null);  // 可能没有旧配置，比如首次添加配置
        if (oldConfig != null) {
            collaborationService.validateWrite(oldConfig, request.getOldConfigVersion());
        }

        // 4. 记录旧值（如果没有旧值则为空）
        String oldValue = oldConfig != null ? oldConfig.getSelectedValue() : null;
        Long oldConfigId = oldConfig != null ? oldConfig.getId() : null;

        // 5. 执行业务：根据是否有旧配置做更新或新增
        if (oldConfig != null) {
            // 更新旧配置记录为新值
            oldConfig.setConfigValueId(request.getNewConfigValueId());
            oldConfig.setSelectedValue(newConfigValue.getValueLabel());
            oldConfig.setConfigSource(determineSource(request.getReplaceType()));
            oldConfig.setInstalledDate(LocalDateTime.now());
            oldConfig.setUpdatedAt(LocalDateTime.now());
            collaborationService.stampWrite(oldConfig);
            machineConfigRepository.saveAndFlush(oldConfig);
            log.info("更新车辆配置: machineId={}, 配置项={}, 旧值={} -> 新值={}",
                    machine.getId(), oldConfig.getItemName(), oldValue, newConfigValue.getValueLabel());
        } else {
            // 如果没有旧配置，则新增一条配置记录（可能需要从 ConfigItem 获取名称，这里简化，认为前端传递了 itemName，但DTO里没有，可以从数据库查）
            // 更好的做法：DTO里包含 itemName 或从 ConfigItem 查
            // 此处我们查一下
            ConfigItem configItem = configItemRepository.findById(request.getConfigItemId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置项不存在"));
            MachineConfig newConfig = new MachineConfig();
            newConfig.setMachineId(machine.getId());
            newConfig.setConfigItemId(request.getConfigItemId());
            newConfig.setConfigValueId(request.getNewConfigValueId());
            newConfig.setItemName(configItem.getItemName());
            newConfig.setSelectedValue(newConfigValue.getValueLabel());
            newConfig.setConfigSource(determineSource(request.getReplaceType()));
            newConfig.setIsStandard(false);  // 非原厂，因为不是入库时设置的
            newConfig.setInstalledDate(LocalDateTime.now());
            collaborationService.stampWrite(newConfig);
            machineConfigRepository.saveAndFlush(newConfig);
            log.info("新增车辆配置: machineId={}, 配置项={}, 值={}",
                    machine.getId(), configItem.getItemName(), newConfigValue.getValueLabel());
        }

        // 6. 处理配件库存变动
        // 6.1 如果新配件来自库存（newPartId 不为空），扣减配件库存
        if (request.getNewPartId() != null) {
            PartInventory newPart = partRepository.findByIdForUpdate(request.getNewPartId())
                    .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "新配件不存在"));
            collaborationService.validateWrite(newPart, request.getNewPartVersion());
            if (newPart.getQuantity() < 1) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "配件库存不足: " + newPart.getPartName());
            }
            int beforeQuantity = newPart.getQuantity();
            newPart.setQuantity(beforeQuantity - 1);
            collaborationService.stampWrite(newPart);
            newPart = partRepository.saveAndFlush(newPart);
            savePartStockLog(newPart, "OUTBOUND", 1, beforeQuantity, newPart.getQuantity(),
                    request.getOperator(), "Config replace outbound; machineId=" + machine.getId());
            log.info("配件出库: partCode={}, 剩余库存={}", newPart.getPartCode(), newPart.getQuantity());
        }

        // 6.2 如果旧件处理为入库（STOCK_IN），我们需要把旧件作为配件入库。
        //     注意：这里可能需要知道旧件的配件编码，实际业务中旧件应当已有对应的 PartInventory 记录，
        //     但目前没有。我们可以简化处理：如果旧件要入库，要求提供旧件的 partCode 或 ID。
        //     既然你目前没有旧件编码的管理，这里先做占位，实际可能需要扩展DTO。
        if ("STOCK_IN".equals(request.getOldPartAction()) && oldConfig != null) {
            // 假设旧件的配件信息：如果旧配置关联了某个配件（需要额外字段），这里没有。
            // 你可以设计：当需要旧件入库时，要求前端提供 oldPartCode，然后调用配件入库逻辑。
            // 为保持简单，这里只记录日志，不实际操作。
            log.info("旧件入库未实现：需要提供旧件配件编码");
        }

        collaborationService.stampWrite(machine);
        machineRepository.saveAndFlush(machine);

        // 7. 记录替换日志
        ConfigReplaceLog logEntry = new ConfigReplaceLog();
        logEntry.setMachineId(machine.getId());
        logEntry.setMachineConfigId(oldConfigId); // 可能为 null
        logEntry.setItemName(newConfigValue.getValueLabel()); // 这里应该填配置项名称，但request里没有，可以从 configItem 查，稍后修正
        // 修正：获取配置项名称
        String itemName = oldConfig != null ? oldConfig.getItemName() :
                configItemRepository.findById(request.getConfigItemId()).map(ConfigItem::getItemName).orElse("未知");
        logEntry.setItemName(itemName);
        logEntry.setOldValue(oldValue);
        logEntry.setNewValue(newConfigValue.getValueLabel());
        logEntry.setReplaceType(request.getReplaceType());
        logEntry.setNewPartId(request.getNewPartId());
        logEntry.setOperator(request.getOperator());
        logEntry.setRemark(request.getRemark());
        logEntry.setCreatedAt(LocalDateTime.now());

        ConfigReplaceLog savedLog = replaceLogRepository.save(logEntry);
        log.info("配置替换日志记录成功: id={}, machineId={}", savedLog.getId(), machine.getId());
        return savedLog;
    }

    @Override
    @Transactional
    public ConfigReplaceLog performPartReplace(PartReplaceRequestDTO request) {
        MachineInventory machine = machineRepository.findByIdForUpdate(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
        collaborationService.validateWrite(machine, request.getMachineVersion());

        MachineConfig oldConfig = machineConfigRepository.findByIdForUpdate(request.getMachineConfigId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "车辆配置不存在"));
        collaborationService.validateWrite(oldConfig, request.getMachineConfigVersion());
        if (!oldConfig.getMachineId().equals(machine.getId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "车辆配置不属于该车辆");
        }
        if (oldConfig.getSelectedValue() == null || oldConfig.getSelectedValue().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该配置没有可替换的旧配件");
        }

        PartInventory newPart = partRepository.findByIdForUpdate(request.getNewPartId())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "新配件不存在"));
        collaborationService.validateWrite(newPart, request.getNewPartVersion());
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        if (quantity < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "替换数量必须大于0");
        }
        if (newPart.getQuantity() == null || newPart.getQuantity() < quantity) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "配件库存不足: " + newPart.getPartName());
        }

        String actualType = normalizeType(newPart.getPartCategory());
        List<String> expectedTypes = configItemRepository.findById(oldConfig.getConfigItemId())
                .map(item -> List.of(normalizeType(item.getSubCategory()), normalizeType(item.getItemName()), normalizeType(oldConfig.getItemName())))
                .orElseGet(() -> List.of(normalizeType(oldConfig.getItemName())));
        boolean matched = !actualType.isEmpty() && expectedTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.equals(actualType));
        if (!matched) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "只能替换同类型配件：车辆配置类型=" + oldConfig.getItemName()
                            + "，库存配件类型=" + newPart.getPartCategory());
        }

        String oldValue = oldConfig.getSelectedValue();
        String newValue = partDisplayName(newPart);

        int newPartBeforeQuantity = newPart.getQuantity();
        newPart.setQuantity(newPartBeforeQuantity - quantity);
        collaborationService.stampWrite(newPart);
        newPart = partRepository.saveAndFlush(newPart);
        savePartStockLog(newPart, "OUTBOUND", quantity, newPartBeforeQuantity, newPart.getQuantity(),
                request.getOperator(), "配件替换出库；车辆ID=" + machine.getId(),
                request.getStockMovementSourceType(), request.getStockMovementSourceId());

        boolean oldPartStockIn = shouldStockInOldPart(request.getOldPartAction());
        PartInventory removedPart = null;
        if (oldPartStockIn) {
            removedPart = new PartInventory();
            removedPart.setPartCode(generateRemovedPartCode(machine.getId(), oldConfig.getConfigItemId()));
            removedPart.setPartName(oldValue);
            removedPart.setSpecification(oldValue);
            removedPart.setPartCategory(configPartCategory(oldConfig));
            removedPart.setApplicableModels(machine.getSpecificationModel());
            removedPart.setSource("REMOVED");
            removedPart.setSourceMachineId(machine.getId());
            removedPart.setWarehouseId(stockLedgerService.resolveWarehouseId(machine.getWarehouseId()));
            removedPart.setQuantity(quantity);
            removedPart.setUnit(newPart.getUnit() != null ? newPart.getUnit() : "个");
            removedPart.setInboundDate(LocalDateTime.now());
            removedPart.setRemarks("配件替换拆下入库；车辆ID=" + machine.getId()
                    + (request.getRemark() == null || request.getRemark().isBlank() ? "" : "；" + request.getRemark()));
            collaborationService.stampWrite(removedPart);
            removedPart = partRepository.saveAndFlush(removedPart);
            savePartStockLog(removedPart, "INBOUND", quantity, 0, quantity,
                    request.getOperator(), "配件替换拆下入库；车辆ID=" + machine.getId(),
                    request.getStockMovementSourceType(), request.getStockMovementSourceId());
        }

        oldConfig.setSelectedValue(newValue);
        oldConfig.setConfigSource("WAREHOUSE");
        oldConfig.setIsStandard(false);
        oldConfig.setInstalledDate(LocalDateTime.now());
        oldConfig.setRemark(request.getRemark());
        collaborationService.stampWrite(oldConfig);
        machineConfigRepository.saveAndFlush(oldConfig);

        collaborationService.stampWrite(machine);
        machineRepository.saveAndFlush(machine);

        ConfigReplaceLog logEntry = new ConfigReplaceLog();
        logEntry.setMachineId(machine.getId());
        logEntry.setMachineConfigId(oldConfig.getId());
        logEntry.setItemName(oldConfig.getItemName());
        logEntry.setOldValue(oldValue);
        logEntry.setNewValue(newValue);
        logEntry.setReplaceType("PART_REPLACE");
        logEntry.setNewPartId(newPart.getId());
        logEntry.setOperator(request.getOperator());
        String removedSummary = removedPart == null ? "拆下件未入库" : "拆下件已自动入库：" + removedPart.getPartCode();
        logEntry.setRemark(removedSummary
                + (quantity > 1 ? "；数量=" + quantity : "")
                + (request.getRemark() == null || request.getRemark().isBlank() ? "" : "；" + request.getRemark()));
        ConfigReplaceLog savedLog = replaceLogRepository.save(logEntry);

        log.info("整车配件替换成功: machineId={}, configId={}, newPart={}, removedPart={}",
                machine.getId(), oldConfig.getId(), newPart.getPartCode(), removedPart == null ? "-" : removedPart.getPartCode());
        return savedLog;
    }

    /**
     * 根据替换类型决定配置来源
     */
    private String determineSource(String replaceType) {
        if ("SWAP".equals(replaceType)) {
            return "WAREHOUSE";
        } else if ("UPGRADE".equals(replaceType)) {
            return "CUSTOM";
        } else {
            return "REPAIR";
        }
    }

    // 需要注入 ConfigItemRepository
    @Autowired
    private ConfigItemRepository configItemRepository;

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String configPartCategory(MachineConfig config) {
        return configItemRepository.findById(config.getConfigItemId())
                .map(item -> firstNonBlank(item.getSubCategory(), item.getItemName(), config.getItemName()))
                .orElse(config.getItemName());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String partDisplayName(PartInventory part) {
        if (part.getSpecification() == null || part.getSpecification().isBlank()) {
            return part.getPartName();
        }
        return part.getPartName() + " / " + part.getSpecification();
    }

    private String generateRemovedPartCode(Long machineId, Long configItemId) {
        return "REMOVED-" + machineId + "-" + configItemId + "-" + System.currentTimeMillis();
    }

    private boolean shouldStockInOldPart(String oldPartAction) {
        return oldPartAction == null
                || oldPartAction.isBlank()
                || "STOCK_IN".equalsIgnoreCase(oldPartAction)
                || "REMOVED".equalsIgnoreCase(oldPartAction)
                || "REUSABLE".equalsIgnoreCase(oldPartAction);
    }

    private StockOperationLog savePartStockLog(PartInventory part, String operationType, Integer quantity,
                                  Integer beforeQuantity, Integer afterQuantity, String operator, String remark) {
        return savePartStockLog(part, operationType, quantity, beforeQuantity, afterQuantity,
                operator, remark, "STOCK_LOG", null);
    }

    private StockOperationLog savePartStockLog(PartInventory part, String operationType, Integer quantity,
                                  Integer beforeQuantity, Integer afterQuantity, String operator, String remark,
                                  String movementSourceType, Long movementSourceId) {
        StockOperationLog stockLog = new StockOperationLog();
        stockLog.setResourceType("PART");
        stockLog.setOperationType(operationType);
        stockLog.setResourceId(part.getId());
        stockLog.setResourceCode(part.getPartCode());
        stockLog.setResourceName(part.getPartName());
        stockLog.setQuantity(quantity);
        stockLog.setBeforeQuantity(beforeQuantity);
        stockLog.setAfterQuantity(afterQuantity);
        stockLog.setOperator(operator);
        stockLog.setRemark(remark);
        StockOperationLog savedLog = stockOperationLogRepository.save(stockLog);
        String sourceType = movementSourceType == null || movementSourceType.isBlank() ? "STOCK_LOG" : movementSourceType;
        Long sourceId = movementSourceId == null ? savedLog.getId() : movementSourceId;
        stockLedgerService.recordMovement(
                operationType,
                StockLedgerService.RESOURCE_PART,
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getWarehouseId(),
                beforeQuantity,
                afterQuantity,
                operator,
                remark,
                sourceType,
                sourceId
        );
        operationAuditService.record("配件出入库", operationType, "PART", part.getId(),
                part.getPartCode(), part.getPartName(),
                ("INBOUND".equals(operationType) ? "配件入库 " : "配件出库 ") + quantity,
                operator, remark, "STOCK", savedLog.getId());
        return savedLog;
    }
}
