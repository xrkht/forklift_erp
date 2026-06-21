package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.PurchaseOrderDTO;
import com.example.forklift_erp.dto.PurchaseOrderVO;
import com.example.forklift_erp.service.PurchaseOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/purchase-orders")
@Validated
public class PurchaseOrderController {
    @Autowired
    private PurchaseOrderService service;

    @GetMapping
    public Result<?> getAll(@RequestParam(defaultValue = "true") boolean paged,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String resourceType,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        if (paged) {
            return Result.success(service.findPage(keyword, resourceType, page, size));
        }
        return Result.success(service.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<PurchaseOrderVO> create(@Valid @RequestBody PurchaseOrderDTO request) {
        return Result.success("入库订单创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<PurchaseOrderVO> update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderDTO request) {
        return Result.success("入库订单更新成功", service.update(id, request));
    }

    @PutMapping("/{id}/received")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<PurchaseOrderVO> setReceived(@PathVariable Long id,
                                               @RequestParam boolean received,
                                               @RequestParam(required = false) Long version) {
        return Result.success(received ? "入库订单已收货" : "入库订单已改为待收货", service.setReceived(id, received, version));
    }

    @PutMapping("/{id}/freight")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<PurchaseOrderVO> updateFreight(@PathVariable Long id,
                                                 @DecimalMin(value = "0.00", message = "\u8fd0\u8d39\u4e0d\u80fd\u4e3a\u8d1f\u6570")
                                                 @RequestParam(required = false) BigDecimal freightAmount,
                                                 @RequestParam(required = false) Long version) {
        return Result.success("采购运费已更新", service.updateFreight(id, freightAmount, version));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long version) {
        service.delete(id, version);
        return Result.success("入库订单删除成功");
    }
}
