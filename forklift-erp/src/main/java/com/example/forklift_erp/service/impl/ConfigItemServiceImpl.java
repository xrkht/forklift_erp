package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.MachineConfigRepository;
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
    public ConfigItem save(ConfigItem configItem) {
        return configItemRepository.save(configItem);
    }

    /**
     * 删除配置项
     * 前提：没有任何车辆正在使用该配置项
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        // 1. 检查配置项是否存在
        ConfigItem configItem = configItemRepository.findById(id)
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
        return configValueRepository.save(configValue);
    }

    /**
     * 删除配置值
     * 前提：没有任何车辆正在使用该配置值
     */
    @Override
    @Transactional
    public void deleteValueById(Long valueId) {
        // 1. 检查配置值是否存在
        ConfigValue configValue = configValueRepository.findById(valueId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置值不存在，id=" + valueId));

        // 2. 引用检查：是否有车辆使用了这个配置值
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