// MachineInventoryRepository.java
package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.MachineInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByVehicleProductNumber(String vehicleProductNumber);

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

    @Query("""
            select m from MachineInventory m
            where (:includeLocked = true or m.isLocked = false)
              and (:keyword is null or :keyword = ''
                   or lower(coalesce(m.vehicleProductNumber, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.specificationModel, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.configuration, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.machineType, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.supplier, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.warehouseName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.applicationNumber, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.materialNumber, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.stockStatus, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.destination1, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.destination2, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.destination3, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.destination4, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.destination5, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.remarks, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<MachineInventory> searchPage(
            @Param("keyword") String keyword,
            @Param("includeLocked") boolean includeLocked,
            Pageable pageable
    );
}
