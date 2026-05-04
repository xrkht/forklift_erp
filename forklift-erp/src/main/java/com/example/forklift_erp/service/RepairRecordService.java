package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.RepairRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 外出维修记录业务接口
 */
public interface RepairRecordService {

    List<RepairRecord> findAll();
    Optional<RepairRecord> findById(Long id);
    RepairRecord save(RepairRecord record);
    void deleteById(Long id);

    List<RepairRecord> findByMachineId(Long machineId);
    List<RepairRecord> findByRepairPerson(String repairPerson);
    List<RepairRecord> findByStatus(String status);
    List<RepairRecord> findByDateRange(LocalDateTime start, LocalDateTime end);
}