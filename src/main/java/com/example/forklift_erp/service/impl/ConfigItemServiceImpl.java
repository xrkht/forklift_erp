package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.ConfigItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ConfigItemServiceImpl implements ConfigItemService {

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private CollaborationService collaborationService;

    // 新增：注入 MachineConfigRepository 用于引用检查
    @Autowired
    private MachineConfigRepository machineConfigRepository;

    @Override
    public List<ConfigItem> findAll() {
        return configItemRepository.findAllByOrderBySortOrderAsc();
    }

    @Override
    public Optional<ConfigItem> findById(Long id) {
        return configItemRepository.findById(id);
    }

    @Override
    public Optional<ConfigItem> findByIdForUpdate(Long id) {
        return configItemRepository.findByIdForUpdate(id);
    }

    @Override
    @Transactional
    public ConfigItem save(ConfigItem configItem) {
        normalizeConfigItem(configItem);
        if (configItem.getId() != null) {
            ConfigItem existing = configItemRepository.findByIdForUpdate(configItem.getId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置项不存在"));
            collaborationService.validateWrite(existing, configItem.getVersion());
            ensureUniqueItemCode(configItem.getItemCode(), configItem.getId());
            existing.setCategory(configItem.getCategory());
            existing.setSubCategory(configItem.getSubCategory());
            existing.setItemName(configItem.getItemName());
            existing.setItemCode(configItem.getItemCode());
            existing.setInputType(configItem.getInputType());
            existing.setUnit(configItem.getUnit());
            existing.setIsRequired(configItem.getIsRequired());
            existing.setSortOrder(configItem.getSortOrder());
            collaborationService.stampWrite(existing);
            return configItemRepository.saveAndFlush(existing);
        }
        ensureUniqueItemCode(configItem.getItemCode(), null);
        collaborationService.stampWrite(configItem);
        return configItemRepository.saveAndFlush(configItem);
    }

    private void normalizeConfigItem(ConfigItem configItem) {
        configItem.setCategory(trimToNull(configItem.getCategory()));
        configItem.setSubCategory(trimToNull(configItem.getSubCategory()));
        configItem.setItemName(trimToNull(configItem.getItemName()));
        configItem.setItemCode(trimToNull(configItem.getItemCode()));
        configItem.setInputType(trimToNull(configItem.getInputType()));
        configItem.setUnit(trimToNull(configItem.getUnit()));
        if (configItem.getItemCode() == null) {
            configItem.setItemCode(nextItemCode());
        }
        if (configItem.getInputType() == null) {
            configItem.setInputType("SELECT");
        }
        if (configItem.getIsRequired() == null) {
            configItem.setIsRequired(true);
        }
        if (configItem.getSortOrder() == null) {
            configItem.setSortOrder(0);
        }
    }

    private String nextItemCode() {
        int max = configItemRepository.findAll().stream()
                .map(ConfigItem::getItemCode)
                .map(this::parseAutoCodeNumber)
                .filter(value -> value >= 0)
                .max(Integer::compareTo)
                .orElse(0);
        return String.format("CFG-%04d", max + 1);
    }

    private int parseAutoCodeNumber(String itemCode) {
        if (itemCode == null || !itemCode.matches("^CFG-\\d+$")) {
            return -1;
        }
        try {
            return Integer.parseInt(itemCode.substring(4));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void ensureUniqueItemCode(String itemCode, Long currentId) {
        configItemRepository.findByItemCode(itemCode)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessException(ResultCode.DATA_DUPLICATE, "配置项编码已存在: " + itemCode);
                });
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 删除配置项
     * 前提：没有任何车辆正在使用该配置项
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        // 1. 检查配置项是否存在
        ConfigItem configItem = configItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置项不存在，id=" + id));

        // 2. 引用检查：是否有车辆使用了这个配置项
        List<MachineConfig> configs = machineConfigRepository.findByConfigItemId(id);
        if (!configs.isEmpty()) {
            log.warn("删除配置项被阻止：id={}, 名称={}, 被 {} 辆车使用", id, configItem.getItemName(), configs.size());
            throw new BusinessException(ResultCode.CONFIG_IN_USE,
                    "配置项【" + configItem.getItemName() + "】正在被车辆使用，无法删除");
        }

        // 3. 安全删除：先删该配置项下的所有可选值，再删配置项本身
        configValueRepository.deleteByConfigItemId(id);
        configItemRepository.deleteById(id);
        log.info("配置项删除成功: id={}, 名称={}", id, configItem.getItemName());
    }

    @Override
    public List<ConfigItem> findByCategory(String category) {
        return configItemRepository.findByCategoryOrderBySortOrderAsc(category);
    }

    @Override
    public List<ConfigValue> getValuesByItemId(Long itemId) {
        return configValueRepository.findByConfigItemIdOrderBySortOrderAsc(itemId);
    }

    @Override
    public ConfigValue saveValue(ConfigValue configValue) {
        collaborationService.stampWrite(configValue);
        return configValueRepository.saveAndFlush(configValue);
    }

    /**
     * 删除配置值
     * 前提：没有任何车辆正在使用该配置值
     */
    @Override
    @Transactional
    public void deleteValueById(Long valueId, Long expectedVersion) {
        // 1. 检查配置值是否存在
        ConfigValue configValue = configValueRepository.findByIdForUpdate(valueId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置值不存在，id=" + valueId));

        // 2. 引用检查：是否有车辆使用了这个配置值
        collaborationService.validateWrite(configValue, expectedVersion);
        List<MachineConfig> configs = machineConfigRepository.findByConfigValueId(valueId);
        if (!configs.isEmpty()) {
            log.warn("删除配置值被阻止：id={}, 值={}, 被 {} 辆车使用", valueId, configValue.getValueLabel(), configs.size());
            throw new BusinessException(ResultCode.CONFIG_IN_USE,
                    "配置值【" + configValue.getValueLabel() + "】正在被车辆使用，无法删除");
        }

        // 3. 安全删除
        configValueRepository.deleteById(valueId);
        log.info("配置值删除成功: id={}, 值={}", valueId, configValue.getValueLabel());
    }
}
