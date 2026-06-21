package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.OperationAuditLog;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationAuditServiceTests {

    @Test
    void recordTruncatesRemarkToColumnLength() {
        OperationAuditLogRepository repository = mock(OperationAuditLogRepository.class);
        when(repository.save(any(OperationAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationAuditService service = new OperationAuditService();
        ReflectionTestUtils.setField(service, "auditLogRepository", repository);

        OperationAuditLog log = service.record(
                "Machine",
                "UPDATE",
                "MACHINE",
                1L,
                "CPD-001",
                "Forklift",
                "Update machine",
                "tester",
                "x".repeat(600)
        );

        assertThat(log.getRemark()).hasSize(500);
    }
}
