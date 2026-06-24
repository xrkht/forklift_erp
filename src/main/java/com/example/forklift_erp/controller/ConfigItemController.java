package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.ConfigItemDTO;
import com.example.forklift_erp.dto.ConfigItemVO;
import com.example.forklift_erp.dto.ConfigValueDTO;
import com.example.forklift_erp.dto.ConfigValueVO;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.security.PermissionCodes;
import com.example.forklift_erp.service.ConfigItemService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigItemController {

    private final ConfigItemService configItemService;

    public ConfigItemController(ConfigItemService configItemService) {
        this.configItemService = configItemService;
    }

    // 配置项
    @GetMapping("/items")
    public Result<List<ConfigItemVO>> getAllItems() {
        List<ConfigItemVO> list = configItemService.findAll().stream()
                .map(ConfigItemVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/items/category/{category}")
    public Result<List<ConfigItemVO>> getItemsByCategory(@PathVariable String category) {
        List<ConfigItemVO> list = configItemService.findByCategory(category).stream()
                .map(ConfigItemVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/items/{id}")
    public Result<ConfigItemVO> getItemById(@PathVariable Long id) {
        return configItemService.findById(id)
                .map(ConfigItemVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置项不存在"));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<ConfigItemVO> createItem(@Valid @RequestBody ConfigItemDTO request) {
        ConfigItem saved = configItemService.save(request.toEntity(null));
        return Result.success("创建成功", ConfigItemVO.fromEntity(saved));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<ConfigItemVO> updateItem(@PathVariable Long id, @Valid @RequestBody ConfigItemDTO request) {
        ConfigItem saved = configItemService.save(request.toEntity(id));
        return Result.success("更新成功", ConfigItemVO.fromEntity(saved));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<Void> deleteItem(@PathVariable Long id, @RequestParam(required = false) Long version) {
        configItemService.deleteById(id, version);
        return Result.success("删除成功");
    }

    // 配置值
    @GetMapping("/items/{itemId}/values")
    public Result<List<ConfigValueVO>> getValues(@PathVariable Long itemId) {
        List<ConfigValueVO> list = configItemService.getValuesByItemId(itemId).stream()
                .map(ConfigValueVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/values")
    public Result<Map<Long, List<ConfigValueVO>>> getValuesByItemIds(@RequestParam List<Long> itemIds) {
        Map<Long, List<ConfigValueVO>> valuesByItemId = new LinkedHashMap<>();
        configItemService.getValuesByItemIds(itemIds)
                .forEach((itemId, values) -> valuesByItemId.put(itemId, values.stream()
                        .map(ConfigValueVO::fromEntity)
                        .toList()));
        return Result.success(valuesByItemId);
    }

    @PostMapping("/values")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<ConfigValueVO> createValue(@Valid @RequestBody ConfigValueDTO request) {
        ConfigValue saved = configItemService.saveValue(request.toEntity());
        return Result.success("创建成功", ConfigValueVO.fromEntity(saved));
    }

    @DeleteMapping("/values/{id}")
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<Void> deleteValue(@PathVariable Long id, @RequestParam(required = false) Long version) {
        configItemService.deleteValueById(id, version);
        return Result.success("删除成功");
    }
}
