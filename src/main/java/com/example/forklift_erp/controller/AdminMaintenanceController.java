package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.BusinessDataResetService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminMaintenanceController {

    private static final String RESET_CONFIRMATION = "RESET-BUSINESS-DATA";

    @Autowired
    private BusinessDataResetService businessDataResetService;

    @Value("${forklift.admin.business-data-reset.enabled:false}")
    private boolean businessDataResetEnabled;

    @Data
    public static class BusinessDataResetRequest {
        private String confirmation;
    }

    @PostMapping("/business-data/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Map<String, Long>> resetBusinessData(@RequestBody(required = false) BusinessDataResetRequest request) {
        if (!businessDataResetEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Business data reset is disabled");
        }
        if (request == null || !RESET_CONFIRMATION.equals(request.getConfirmation())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Confirmation must be RESET-BUSINESS-DATA");
        }
        return Result.success("Business data reset completed", businessDataResetService.resetBusinessData());
    }
}
