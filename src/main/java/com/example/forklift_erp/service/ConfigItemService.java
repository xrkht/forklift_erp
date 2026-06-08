package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ConfigItemService {
    List<ConfigItem> findAll();
    Optional<ConfigItem> findById(Long id);
    Optional<ConfigItem> findByIdForUpdate(Long id);
    ConfigItem save(ConfigItem configItem);
    void deleteById(Long id);
    List<ConfigItem> findByCategory(String category);
    List<ConfigValue> getValuesByItemId(Long itemId);
    Map<Long, List<ConfigValue>> getValuesByItemIds(List<Long> itemIds);
    ConfigValue saveValue(ConfigValue configValue);
    void deleteValueById(Long valueId, Long expectedVersion);
}
