package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.OperationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, Long> {

    List<OperationAuditLog> findAllByOrderByCreatedAtDesc();
}
