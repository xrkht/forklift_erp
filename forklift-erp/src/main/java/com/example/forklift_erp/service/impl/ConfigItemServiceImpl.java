package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.service.ConfigItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class ConfigItemServiceImpl implements ConfigItemService {

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

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

    @Override
    @Transactional
    public void deleteById(Long id) {
        // 第一步：先删除该配置项下的所有可选值
        configValueRepository.deleteByConfigItemId(id);
        // 第二步：再删除配置项本身
        configItemRepository.deleteById(id);
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

    @Override
    @Transactional
    public void deleteValueById(Long valueId) {
        configValueRepository.deleteById(valueId);
    }
}