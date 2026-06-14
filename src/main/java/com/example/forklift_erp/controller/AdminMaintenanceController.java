package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.AdminBusinessDataResetRequest;
import com.example.forklift_erp.entity.AdminMaintenanceAuditLog;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.AdminMaintenanceAuditService;
import com.example.forklift_erp.service.BusinessDataResetService;
import com.example.forklift_erp.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Profile("!prod")
public class AdminMaintenanceController {

    private static final String RESET_CONFIRMATION = "RESET_BUSINESS_DATA";

    @Autowired
    private BusinessDataResetService businessDataResetService;

    @Autowired
    private AdminMaintenanceAuditService adminMaintenanceAuditService;

    @PostMapping("/business-data/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Map<String, Long>> resetBusinessData(
            @RequestBody(required = false) AdminBusinessDataResetRequest request,
            HttpServletRequest servletRequest
    ) {
        assertConfirmation(request);
        assertLocalOrPrivateRequest(servletRequest);

        AdminMaintenanceAuditLog auditLog = adminMaintenanceAuditService.recordResetRequested(
                SecurityUtils.currentUsername(),
                servletRequest.getRemoteAddr()
        );
        try {
            Map<String, Long> summary = businessDataResetService.resetBusinessData();
            adminMaintenanceAuditService.recordResetSucceeded(auditLog.getId(), summary);
            return Result.success("业务数据已清空", summary);
        } catch (RuntimeException e) {
            try {
                adminMaintenanceAuditService.recordResetFailed(auditLog.getId(), e);
            } catch (RuntimeException auditError) {
                e.addSuppressed(auditError);
            }
            throw e;
        }
    }

    private void assertConfirmation(AdminBusinessDataResetRequest request) {
        String confirmation = request == null ? null : request.getConfirmation();
        if (!RESET_CONFIRMATION.equals(confirmation)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "业务数据重置需要二次确认，请提交 confirmation=" + RESET_CONFIRMATION);
        }
    }

    private void assertLocalOrPrivateRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isLocalOrPrivateAddress(remoteAddr)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "业务数据重置仅允许从本机或内网发起");
        }
    }

    private boolean isLocalOrPrivateAddress(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
