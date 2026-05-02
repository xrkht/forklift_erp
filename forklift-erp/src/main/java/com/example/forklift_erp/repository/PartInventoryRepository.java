package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.PartInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartInventoryRepository extends JpaRepository<PartInventory, Long> {
    Optional<PartInventory> findByPartCode(String partCode);
    List<PartInventory> findByPartCategory(String partCategory);
    List<PartInventory> findByQuantityGreaterThan(Integer minQuantity);
    List<PartInventory> findBySource(String source);
    List<PartInventory> findBySourceMachineId(Long machineId);
}