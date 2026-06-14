package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.RepairPartUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepairPartUsageRepository extends JpaRepository<RepairPartUsage, Long> {
    List<RepairPartUsage> findByRepairId(Long repairId);

    List<RepairPartUsage> findByRepairIdOrderByIdAsc(Long repairId);

    List<RepairPartUsage> findByRepairIdIn(List<Long> repairIds);
}
