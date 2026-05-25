package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.CustomerDTO;
import com.example.forklift_erp.dto.CustomerVO;
import com.example.forklift_erp.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService service;

    @GetMapping
    public Result<List<CustomerVO>> getAll() {
        return Result.success(service.findAll());
    }

    @GetMapping("/{id}")
    public Result<CustomerVO> getById(@PathVariable Long id) {
        return Result.success(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<CustomerVO> create(@Valid @RequestBody CustomerDTO request) {
        return Result.success("客户创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<CustomerVO> update(@PathVariable Long id, @Valid @RequestBody CustomerDTO request) {
        return Result.success("客户更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long version) {
        service.delete(id, version);
        return Result.success("客户删除成功");
    }
}
