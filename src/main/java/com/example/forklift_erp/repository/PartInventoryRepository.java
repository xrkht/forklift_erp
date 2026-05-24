package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.PartInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartInventoryRepository extends JpaRepository<PartInventory, Long> {

    Optional<PartInventory> findByPartCode(String partCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartInventory p where p.id = :id")
    Optional<PartInventory> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartInventory p where p.id = :id and p.isLocked = false")
    Optional<PartInventory> findByIdAndIsLockedFalseForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartInventory p where p.partCode = :partCode")
    Optional<PartInventory> findByPartCodeForUpdate(@Param("partCode") String partCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartInventory p where p.partCode = :partCode and p.isLocked = false")
    Optional<PartInventory> findByPartCodeAndIsLockedFalseForUpdate(@Param("partCode") String partCode);
    List<PartInventory> findByPartCategory(String partCategory);
    List<PartInventory> findByQuantityGreaterThan(Integer minQuantity);
    List<PartInventory> findBySource(String source);
    List<PartInventory> findBySourceMachineId(Long machineId);

    // ========== 普通用户专用（过滤锁定） ==========
    List<PartInventory> findAllByIsLockedFalse();
    Optional<PartInventory> findByIdAndIsLockedFalse(Long id);
    Optional<PartInventory> findByPartCodeAndIsLockedFalse(String partCode);
}
