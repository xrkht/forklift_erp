package com.example.forklift_erp.service;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.CollaborativeResource;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.stereotype.Service;

@Service
public class CollaborationService {

    public void validateWrite(CollaborativeResource resource, Long expectedVersion) {
        if (resource == null || resource.getVersion() == null) {
            return;
        }
        if (expectedVersion == null) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "数据版本缺失，请刷新后重试；多人协同写入需要携带最新版本号");
        }
        if (resource.getVersion().equals(expectedVersion)) {
            return;
        }

        int currentPriority = SecurityUtils.currentRolePriority();
        int lastPriority = resource.getLastModifiedPriority() == null ? 0 : resource.getLastModifiedPriority();
        if (currentPriority <= lastPriority) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "数据已被其他用户更新，请刷新后重试；同一数据并发操作时按 SUPER_ADMIN > ADMIN > USER 优先级处理");
        }
    }

    public void stampWrite(CollaborativeResource resource) {
        resource.setLastModifiedBy(SecurityUtils.currentUsername());
        resource.setLastModifiedRole(SecurityUtils.currentHighestRole());
        resource.setLastModifiedPriority(SecurityUtils.currentRolePriority());
    }
}
