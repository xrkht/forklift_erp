package com.example.forklift_erp.service;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.stereotype.Service;

@Service
public class ResourceVisibilityPolicy {

    public boolean canSeeLockedResources() {
        return SecurityUtils.isAdminOrSuperAdmin();
    }

    public void ensureVisible(Boolean locked, ResultCode hiddenCode, String hiddenMessage) {
        if (Boolean.TRUE.equals(locked) && !canSeeLockedResources()) {
            throw new BusinessException(hiddenCode, hiddenMessage);
        }
    }

    public void ensureWritable(Boolean locked, String message) {
        if (Boolean.TRUE.equals(locked) && !canSeeLockedResources()) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }
}
