package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.RepairRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepairRecordRepository extends JpaRepository<RepairRecord, Long> {

    List<RepairRecord> findByMachineIdOrderByRepairDateDesc(Long machineId);
    List<RepairRecord> findByRepairPerson(String repairPerson);
    List<RepairRecord> findByStatus(String status);
    List<RepairRecord> findByRepairDateBetween(LocalDateTime start, LocalDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepairRecord r where r.id = :id")
    Optional<RepairRecord> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepairRecord r where r.id = :id and r.isLocked = false")
    Optional<RepairRecord> findByIdAndIsLockedFalseForUpdate(@Param("id") Long id);

    // ========== 普通用户专用（过滤锁定） ==========
    List<RepairRecord> findAllByIsLockedFalse();
    Optional<RepairRecord> findByIdAndIsLockedFalse(Long id);
}
