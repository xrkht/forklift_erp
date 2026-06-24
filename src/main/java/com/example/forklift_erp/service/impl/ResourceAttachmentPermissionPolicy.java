package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.security.PermissionCodes;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
class ResourceAttachmentPermissionPolicy {

    void ensureResourcePermission(String resourceType) {
        if (SecurityUtils.hasAnyPermission(resourcePermission(resourceType), PermissionCodes.STOCK_ADJUST)) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "No permission to manage attachments");
    }

    void ensureAttachmentListPermission(String resourceType) {
        if (resourceType != null) {
            ensureResourcePermission(resourceType);
            return;
        }
        if (SecurityUtils.hasAnyPermission(
                PermissionCodes.VEHICLE_WRITE,
                PermissionCodes.PART_WRITE,
                PermissionCodes.REPAIR_WRITE,
                PermissionCodes.STOCK_ADJUST)) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "No permission to view attachments");
    }

    String resourcePermission(String resourceType) {
        return switch (normalizeResourceType(resourceType)) {
            case "MACHINE" -> PermissionCodes.VEHICLE_WRITE;
            case "REPAIR" -> PermissionCodes.REPAIR_WRITE;
            case "OUTBOUND_ORDER" -> PermissionCodes.STOCK_ADJUST;
            case "PART" -> PermissionCodes.PART_WRITE;
            case "CUSTOMER" -> PermissionCodes.VEHICLE_WRITE;
            default -> PermissionCodes.STOCK_ADJUST;
        };
    }

    String normalizeResourceType(String resourceType) {
        String normalized = trimToNull(resourceType);
        if (normalized == null) {
            return "OUTBOUND_ORDER";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
