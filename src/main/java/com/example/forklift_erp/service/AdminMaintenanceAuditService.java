package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.AdminMaintenanceAuditLog;
import com.example.forklift_erp.repository.AdminMaintenanceAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AdminMaintenanceAuditService {

    private static final String EVENT_BUSINESS_DATA_RESET = "BUSINESS_DATA_RESET";

    @Autowired
    private AdminMaintenanceAuditLogRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminMaintenanceAuditLog recordResetRequested(String operator, String remoteAddr) {
        AdminMaintenanceAuditLog log = new AdminMaintenanceAuditLog();
        log.setEventType(EVENT_BUSINESS_DATA_RESET);
        log.setStatus("REQUESTED");
        log.setOperator(operator);
        log.setRemoteAddr(remoteAddr);
        return repository.saveAndFlush(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResetSucceeded(Long auditId, Map<String, Long> summary) {
        AdminMaintenanceAuditLog log = repository.findById(auditId).orElseGet(() -> {
            AdminMaintenanceAuditLog created = new AdminMaintenanceAuditLog();
            created.setEventType(EVENT_BUSINESS_DATA_RESET);
            return created;
        });
        log.setStatus("SUCCESS");
        log.setSummary(toJson(summary));
        log.setErrorMessage(null);
        repository.saveAndFlush(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResetFailed(Long auditId, Exception error) {
        AdminMaintenanceAuditLog log = repository.findById(auditId).orElseGet(() -> {
            AdminMaintenanceAuditLog created = new AdminMaintenanceAuditLog();
            created.setEventType(EVENT_BUSINESS_DATA_RESET);
            return created;
        });
        log.setStatus("FAILED");
        log.setErrorMessage(truncate(error.getMessage(), 1000));
        repository.saveAndFlush(log);
    }

    private String toJson(Map<String, Long> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            return String.valueOf(summary);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
