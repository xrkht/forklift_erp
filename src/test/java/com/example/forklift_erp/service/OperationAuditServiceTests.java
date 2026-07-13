package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.OperationAuditLog;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationAuditServiceTests {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    void recordUsesAuthenticatedUserInsteadOfCallerSuppliedOperator() {
        OperationAuditLogRepository repository = mock(OperationAuditLogRepository.class);
        when(repository.save(any(OperationAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("actual-user", "password")
        );

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
                "spoofed-user",
                null
        );

        assertThat(log.getOperator()).isEqualTo("actual-user");
    }
}
