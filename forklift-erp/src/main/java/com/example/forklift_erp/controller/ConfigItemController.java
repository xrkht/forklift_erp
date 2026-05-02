package com.example.forklift_erp.controller;

import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.service.ConfigItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/config")
public class ConfigItemController {

    @Autowired
    private ConfigItemService configItemService;

    // 查询所有配置项
    @GetMapping("/items")
    public ResponseEntity<List<ConfigItem>> getAllItems() {
        return ResponseEntity.ok(configItemService.findAll());
    }

    // 按分类查询配置项
    @GetMapping("/items/category/{category}")
    public ResponseEntity<List<ConfigItem>> getItemsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(configItemService.findByCategory(category));
    }

    // 查询单个配置项
    @GetMapping("/items/{id}")
    public ResponseEntity<ConfigItem> getItemById(@PathVariable Long id) {
        return configItemService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 新增配置项
    @PostMapping("/items")
    public ResponseEntity<ConfigItem> createItem(@RequestBody ConfigItem configItem) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configItemService.save(configItem));
    }

    // 删除配置项
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        configItemService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // 查询某配置项的所有可选值
    @GetMapping("/items/{itemId}/values")
    public ResponseEntity<List<ConfigValue>> getValues(@PathVariable Long itemId) {
        return ResponseEntity.ok(configItemService.getValuesByItemId(itemId));
    }

    // 新增配置可选值
    @PostMapping("/values")
    public ResponseEntity<ConfigValue> createValue(@RequestBody ConfigValue configValue) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configItemService.saveValue(configValue));
    }

    // 删除配置可选值
    @DeleteMapping("/values/{id}")
    public ResponseEntity<Void> deleteValue(@PathVariable Long id) {
        configItemService.deleteValueById(id);
        return ResponseEntity.noContent().build();
    }
}