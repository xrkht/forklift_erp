package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.OutboundOrderUpdateDTO;
import com.example.forklift_erp.dto.OutboundOrderVO;
import com.example.forklift_erp.dto.PartOutboundOrderCreateDTO;
import com.example.forklift_erp.dto.VehicleOutboundOrderCreateDTO;
import com.example.forklift_erp.service.OutboundOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outbound-orders")
public class OutboundOrderController {

    @Autowired
    private OutboundOrderService service;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<List<OutboundOrderVO>> getAll() {
        return Result.success(service.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> getById(@PathVariable Long id) {
        return Result.success(service.findById(id));
    }

    @PostMapping("/vehicle")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> createVehicleOutbound(@Valid @RequestBody VehicleOutboundOrderCreateDTO request) {
        return Result.success("整车出库订单创建成功", service.createVehicleOutbound(request));
    }

    @PostMapping("/part")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> createPartOutbound(@Valid @RequestBody PartOutboundOrderCreateDTO request) {
        return Result.success("配件出库订单创建成功", service.createPartOutbound(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> update(@PathVariable Long id, @RequestBody OutboundOrderUpdateDTO request) {
        return Result.success("出库订单更新成功", service.update(id, request == null ? new OutboundOrderUpdateDTO() : request));
    }
}
