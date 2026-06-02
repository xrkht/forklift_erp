package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.service.StockMovementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {
    @Autowired
    private StockMovementService service;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'log:read')")
    public Result<?> getAll(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String resourceType,
                            @RequestParam(required = false) String movementType,
                            @RequestParam(required = false) Long warehouseId,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        return Result.success(service.findPage(keyword, resourceType, movementType, warehouseId, page, size));
    }
}
