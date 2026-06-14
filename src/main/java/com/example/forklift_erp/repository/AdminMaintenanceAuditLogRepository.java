package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.AdminMaintenanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminMaintenanceAuditLogRepository extends JpaRepository<AdminMaintenanceAuditLog, Long> {
}
