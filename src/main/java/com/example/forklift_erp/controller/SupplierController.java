package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.SupplierDTO;
import com.example.forklift_erp.dto.SupplierVO;
import com.example.forklift_erp.service.SupplierService;
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

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    @Autowired
    private SupplierService service;

    @GetMapping
    public Result<?> getAll(@RequestParam(defaultValue = "true") boolean paged,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        if (paged) {
            return Result.success(service.findPage(keyword, page, size));
        }
        return Result.success(service.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<SupplierVO> create(@Valid @RequestBody SupplierDTO request) {
        return Result.success("供应商创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<SupplierVO> update(@PathVariable Long id, @Valid @RequestBody SupplierDTO request) {
        return Result.success("供应商更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long version) {
        service.delete(id, version);
        return Result.success("供应商删除成功");
    }
}
