package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.ConfigItemService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config")
public class ConfigItemController {

    @Autowired
    private ConfigItemService configItemService;

    /**
     * 查询所有配置项
     * 成功：返回 Result<List<ConfigItem>>，code=200
     */
    @GetMapping("/items")
    public Result<List<ConfigItem>> getAllItems() {
        List<ConfigItem> items = configItemService.findAll();
        return Result.success(items);
    }

    /**
     * 按分类查询配置项
     */
    @GetMapping("/items/category/{category}")
    public Result<List<ConfigItem>> getItemsByCategory(@PathVariable String category) {
        List<ConfigItem> items = configItemService.findByCategory(category);
        return Result.success(items);
    }

    /**
     * 查询单个配置项
     * 找不到时抛出 BusinessException，由全局异常处理器返回 Result.error(404)
     */
    @GetMapping("/items/{id}")
    public Result<ConfigItem> getItemById(@PathVariable Long id) {
        return configItemService.findById(id)
                .map(Result::success)                          // 找到：包装为 Result.success(configItem)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置项不存在，id=" + id));
    }

    /**
     * 新增配置项
     * 返回 201 Created，同时响应体是 Result<ConfigItem>
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)   // 将 HTTP 状态码设为 201
    public Result<ConfigItem> createItem(@Valid @RequestBody ConfigItem configItem) {
        ConfigItem saved = configItemService.save(configItem);
        return Result.success("创建成功", saved);
    }

    /**
     * 删除配置项
     * 如果存在关联车辆，Service 层会抛异常，此处无需 try-catch
     * 成功时返回 200 + 成功消息
     */
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        configItemService.deleteById(id);   // 若不能删除，内部会抛异常
        return Result.success("删除成功");
    }

    // ========== 配置值相关接口 ==========

    /**
     * 查询某配置项的所有可选值
     */
    @GetMapping("/items/{itemId}/values")
    public Result<List<ConfigValue>> getValues(@PathVariable Long itemId) {
        List<ConfigValue> values = configItemService.getValuesByItemId(itemId);
        return Result.success(values);
    }

    /**
     * 新增配置可选值
     */
    @PostMapping("/values")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ConfigValue> createValue(@Valid @RequestBody ConfigValue configValue) {
        ConfigValue saved = configItemService.saveValue(configValue);
        return Result.success("创建成功", saved);
    }

    /**
     * 删除配置可选值
     */
    @DeleteMapping("/values/{id}")
    public Result<Void> deleteValue(@PathVariable Long id) {
        configItemService.deleteValueById(id);
        return Result.success("删除成功");
    }
}