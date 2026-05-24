package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.ConfigItemVO;
import com.example.forklift_erp.dto.ConfigValueVO;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.ConfigItemService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigItemController {

    @Autowired
    private ConfigItemService configItemService;

    @Autowired
    private CollaborationService collaborationService;

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
    @PreAuthorize("@permissionService.hasPermission(authentication, 'config:write')")
    public Result<ConfigItemVO> createItem(@Valid @RequestBody ConfigItem item) {
        ConfigItem saved = configItemService.save(item);
        return Result.success("创建成功", ConfigItemVO.fromEntity(saved));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'config:write')")
    @Transactional
    public Result<ConfigItemVO> updateItem(@PathVariable Long id, @Valid @RequestBody ConfigItem item) {
        if (!configItemService.findById(id).isPresent()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置项不存在");
        }
        item.setId(id);
        collaborationService.validateWrite(item, item.getVersion());
        ConfigItem saved = configItemService.save(item);
        return Result.success("更新成功", ConfigItemVO.fromEntity(saved));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'config:write')")
    @Transactional
    public Result<Void> deleteItem(@PathVariable Long id, @RequestParam(required = false) Long version) {
        ConfigItem existing = configItemService.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置项不存在"));
        collaborationService.validateWrite(existing, version);
        configItemService.deleteById(id);
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

    @PostMapping("/values")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'config:write')")
    public Result<ConfigValueVO> createValue(@Valid @RequestBody ConfigValue configValue) {
        ConfigValue saved = configItemService.saveValue(configValue);
        return Result.success("创建成功", ConfigValueVO.fromEntity(saved));
    }

    @DeleteMapping("/values/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'config:write')")
    public Result<Void> deleteValue(@PathVariable Long id, @RequestParam(required = false) Long version) {
        configItemService.deleteValueById(id, version);
        return Result.success("删除成功");
    }
}
