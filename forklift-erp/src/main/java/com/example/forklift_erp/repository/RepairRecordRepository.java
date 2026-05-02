package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.RepairRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RepairRecordRepository extends JpaRepository<RepairRecord, Long> {

    List<RepairRecord> findByMachineIdOrderByRepairDateDesc(Long machineId);

    List<RepairRecord> findByRepairPerson(String repairPerson);

    List<RepairRecord> findByStatus(String status);

    List<RepairRecord> findByRepairDateBetween(LocalDateTime start, LocalDateTime end);
}