package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.ModificationWorkOrderActionDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderCreateDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderVO;
import com.example.forklift_erp.service.ModificationWorkOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/modification-work-orders")
public class ModificationWorkOrderController {

    @Autowired
    private ModificationWorkOrderService service;

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

    @GetMapping("/{id}")
    public Result<ModificationWorkOrderVO> getById(@PathVariable Long id) {
        return Result.success(service.findById(id));
    }

    @GetMapping("/machine/{machineId}")
    public Result<List<ModificationWorkOrderVO>> getByMachineId(@PathVariable Long machineId) {
        return Result.success(service.findByMachineId(machineId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'replace:write')")
    public Result<ModificationWorkOrderVO> create(@Valid @RequestBody ModificationWorkOrderCreateDTO request) {
        return Result.success("改装工单创建成功", service.create(request));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'replace:write')")
    public Result<ModificationWorkOrderVO> complete(@PathVariable Long id,
                                                    @RequestBody(required = false) ModificationWorkOrderActionDTO request) {
        return Result.success("改装工单已完成", service.complete(id, request == null ? new ModificationWorkOrderActionDTO() : request));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'replace:write')")
    public Result<ModificationWorkOrderVO> cancel(@PathVariable Long id,
                                                  @RequestBody(required = false) ModificationWorkOrderActionDTO request) {
        return Result.success("改装工单已取消", service.cancel(id, request == null ? new ModificationWorkOrderActionDTO() : request));
    }
}
