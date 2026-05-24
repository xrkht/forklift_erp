// MachineInventoryRepository.java
package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.MachineInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MachineInventoryRepository extends JpaRepository<MachineInventory, Long> {

    Optional<MachineInventory> findByVehicleProductNumber(String vehicleProductNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MachineInventory m where m.id = :id")
    Optional<MachineInventory> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MachineInventory m where m.id = :id and m.isLocked = false")
    Optional<MachineInventory> findByIdAndIsLockedFalseForUpdate(@Param("id") Long id);

    // ========== 以下为普通用户（过滤锁定记录）专用查询 ==========
    List<MachineInventory> findAllByIsLockedFalse();
    Optional<MachineInventory> findByIdAndIsLockedFalse(Long id);
    Optional<MachineInventory> findByVehicleProductNumberAndIsLockedFalse(String vehicleProductNumber);
}
