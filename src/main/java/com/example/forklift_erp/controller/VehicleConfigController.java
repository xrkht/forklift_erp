package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.VehicleConfigItemDTO;
import com.example.forklift_erp.dto.VehicleConfigItemVO;
import com.example.forklift_erp.dto.VehicleConfigTemplateVO;
import com.example.forklift_erp.dto.VehicleConfigValueDTO;
import com.example.forklift_erp.dto.VehicleConfigValueVO;
import com.example.forklift_erp.security.PermissionCodes;
import com.example.forklift_erp.service.VehicleConfigService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/config")
public class VehicleConfigController {
    @Autowired
    private VehicleConfigService service;

    @GetMapping("/vehicle-items")
    public Result<List<VehicleConfigItemVO>> getItems() {
        return Result.success(service.findAllItems());
    }

    @GetMapping("/vehicle-items/by-specification")
    public Result<VehicleConfigTemplateVO> getTemplateBySpecification(@RequestParam(required = false) String specificationModel) {
        return Result.success(service.findTemplateBySpecificationModel(specificationModel));
    }

    @PostMapping("/vehicle-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<VehicleConfigItemVO> createItem(@Valid @RequestBody VehicleConfigItemDTO request) {
        return Result.success("整车配置项创建成功", service.createItem(request));
    }

    @PutMapping("/vehicle-items/{id}")
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<VehicleConfigItemVO> updateItem(@PathVariable Long id, @Valid @RequestBody VehicleConfigItemDTO request) {
        return Result.success("整车配置项更新成功", service.updateItem(id, request));
    }

    @DeleteMapping("/vehicle-items/{id}")
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<Void> deleteItem(@PathVariable Long id, @RequestParam(required = false) Long version) {
        service.deleteItem(id, version);
        return Result.success("整车配置项删除成功");
    }

    @GetMapping("/vehicle-items/{itemId}/values")
    public Result<List<VehicleConfigValueVO>> getValues(@PathVariable Long itemId) {
        return Result.success(service.findValues(itemId));
    }

    @PostMapping("/vehicle-values")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<VehicleConfigValueVO> createValue(@Valid @RequestBody VehicleConfigValueDTO request) {
        return Result.success("整车配置值创建成功", service.createValue(request));
    }

    @PutMapping("/vehicle-values/{id}")
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<VehicleConfigValueVO> updateValue(@PathVariable Long id, @Valid @RequestBody VehicleConfigValueDTO request) {
        return Result.success("整车配置值更新成功", service.updateValue(id, request));
    }

    @DeleteMapping("/vehicle-values/{id}")
    @PreAuthorize(PermissionCodes.HAS_CONFIG_WRITE)
    public Result<Void> deleteValue(@PathVariable Long id, @RequestParam(required = false) Long version) {
        service.deleteValue(id, version);
        return Result.success("整车配置值删除成功");
    }
}
