package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'log:read')")
    public Result<?> getAll(@RequestParam(defaultValue = "false") boolean paged,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        if (paged) {
            return Result.success(operationLogService.findPage(keyword, page, size));
        }
        return Result.success(operationLogService.findAll());
    }
}
