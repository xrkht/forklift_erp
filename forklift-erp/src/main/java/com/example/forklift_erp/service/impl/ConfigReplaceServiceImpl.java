// src/main/java/com/example/forklift_erp/service/impl/ConfigReplaceServiceImpl.java
package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.ConfigReplaceRequestDTO;
import com.example.forklift_erp.entity.*;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.*;
import com.example.forklift_erp.service.ConfigReplaceService;
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

    @Override
    @Transactional
    public ConfigReplaceLog performReplace(ConfigReplaceRequestDTO request) {
        // 1. 校验车辆存在
        MachineInventory machine = machineRepository.findById(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));

        // 2. 校验新配置值存在
        ConfigValue newConfigValue = configValueRepository.findById(request.getNewConfigValueId())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "新配置值不存在"));

        // 3. 找到旧的配置记录（同一车辆的同一配置项）
        List<MachineConfig> oldConfigs = machineConfigRepository.findByMachineId(request.getMachineId());
        MachineConfig oldConfig = oldConfigs.stream()
                .filter(c -> c.getConfigItemId().equals(request.getConfigItemId()))
                .findFirst()
                .orElse(null);  // 可能没有旧配置，比如首次添加配置

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
            machineConfigRepository.save(oldConfig);
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
            machineConfigRepository.save(newConfig);
            log.info("新增车辆配置: machineId={}, 配置项={}, 值={}",
                    machine.getId(), configItem.getItemName(), newConfigValue.getValueLabel());
        }

        // 6. 处理配件库存变动
        // 6.1 如果新配件来自库存（newPartId 不为空），扣减配件库存
        if (request.getNewPartId() != null) {
            PartInventory newPart = partRepository.findById(request.getNewPartId())
                    .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "新配件不存在"));
            if (newPart.getQuantity() < 1) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "配件库存不足: " + newPart.getPartName());
            }
            newPart.setQuantity(newPart.getQuantity() - 1);
            partRepository.save(newPart);
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
}