package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.service.BusinessDataResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminMaintenanceController {

    @Autowired
    private BusinessDataResetService businessDataResetService;

    @PostMapping("/business-data/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Map<String, Long>> resetBusinessData() {
        return Result.success("业务数据已清空", businessDataResetService.resetBusinessData());
    }
}
