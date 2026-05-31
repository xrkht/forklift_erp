package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.OperationAuditLog;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationAuditService {

    @Autowired
    private OperationAuditLogRepository auditLogRepository;

    @Transactional
    public OperationAuditLog record(String module, String action, String targetType, Long targetId,
                                    String targetCode, String targetName, String summary,
                                    String operator, String remark) {
        return record(module, action, targetType, targetId, targetCode, targetName, summary,
                operator, remark, null, null);
    }

    @Transactional
    public OperationAuditLog record(String module, String action, String targetType, Long targetId,
                                    String targetCode, String targetName, String summary,
                                    String operator, String remark, String sourceType, Long sourceId) {
        OperationAuditLog log = new OperationAuditLog();
        log.setModule(module);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetCode(targetCode);
        log.setTargetName(targetName);
        log.setSummary(summary);
        log.setOperator(resolveOperator(operator));
        log.setRemark(remark);
        log.setSourceType(sourceType);
        log.setSourceId(sourceId);
        return auditLogRepository.save(log);
    }

    private String resolveOperator(String operator) {
        if (operator != null && !operator.isBlank()) {
            return operator.trim();
        }
        return SecurityUtils.currentUsername();
    }
}
