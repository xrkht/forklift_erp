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

import java.math.BigDecimal;
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

    long countByWarehouseId(Long warehouseId);

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

    @Query("""
            select
              count(m) as itemCount,
              sum(coalesce(m.inventoryCount, 0)) as stockQuantity,
              sum(coalesce(coalesce(m.purchasePrice, m.settlementPrice), 0) * coalesce(m.inventoryCount, 0)) as costValue,
              sum(coalesce(coalesce(m.salePrice, m.settlementPrice), 0) * coalesce(m.inventoryCount, 0)) as retailValue
            from MachineInventory m
            where coalesce(m.modelOnly, false) = false
            """)
    StockValueProjection stockValue();

    @Query("""
            select m from MachineInventory m
            where coalesce(m.modelOnly, false) = false
              and coalesce(m.inventoryCount, 0) <= :threshold
            order by coalesce(m.inventoryCount, 0) asc, m.id asc
            """)
    List<MachineInventory> findLowStock(@Param("threshold") int threshold, Pageable pageable);

    @Query(value = """
            select
              coalesce(m.name, '') as name,
              coalesce(m.specification_model, '') as specificationModel,
              coalesce(m.machine_type, '') as machineType,
              min(nullif(m.supplier, '')) as supplier,
              min(nullif(m.warehouse_name, '')) as warehouseName,
              max(m.purchase_price) as purchasePrice,
              max(m.sale_price) as salePrice,
              max(m.settlement_price) as settlementPrice,
              max(case when coalesce(m.model_only, 0) = 1 then m.id else null end) as modelTemplateId,
              sum(case when coalesce(m.model_only, 0) = 0 then 1 else 0 end) as unitCount,
              sum(case when coalesce(m.model_only, 0) = 0 then coalesce(m.inventory_count, 0) else 0 end) as inventoryCount,
              group_concat(case when coalesce(m.model_only, 0) = 0 then m.vehicle_number else null end order by m.vehicle_number separator ' ') as vehicleNumbers
            from machine_inventory m
            where (:includeLocked = true or coalesce(m.is_locked, 0) = 0)
              and (:keyword is null or :keyword = ''
                   or lower(coalesce(m.vehicle_number, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.specification_model, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.configuration, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.machine_type, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.supplier, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.warehouse_name, '')) like lower(concat('%', :keyword, '%')))
            group by coalesce(m.name, ''), coalesce(m.specification_model, ''), coalesce(m.machine_type, '')
            order by coalesce(m.name, ''), coalesce(m.specification_model, ''), coalesce(m.machine_type, '')
            """,
            countQuery = """
            select count(*) from (
              select 1
              from machine_inventory m
              where (:includeLocked = true or coalesce(m.is_locked, 0) = 0)
                and (:keyword is null or :keyword = ''
                     or lower(coalesce(m.vehicle_number, '')) like lower(concat('%', :keyword, '%'))
                     or lower(coalesce(m.name, '')) like lower(concat('%', :keyword, '%'))
                     or lower(coalesce(m.specification_model, '')) like lower(concat('%', :keyword, '%'))
                     or lower(coalesce(m.configuration, '')) like lower(concat('%', :keyword, '%'))
                     or lower(coalesce(m.machine_type, '')) like lower(concat('%', :keyword, '%'))
                     or lower(coalesce(m.supplier, '')) like lower(concat('%', :keyword, '%'))
                     or lower(coalesce(m.warehouse_name, '')) like lower(concat('%', :keyword, '%')))
              group by coalesce(m.name, ''), coalesce(m.specification_model, ''), coalesce(m.machine_type, '')
            ) model_count
            """,
            nativeQuery = true)
    Page<VehicleModelSummaryProjection> searchModelSummaries(
            @Param("keyword") String keyword,
            @Param("includeLocked") boolean includeLocked,
            Pageable pageable
    );

    @Query("""
            select m from MachineInventory m
            where (:includeLocked = true or m.isLocked = false)
              and coalesce(m.modelOnly, false) = false
              and coalesce(m.name, '') = :name
              and coalesce(m.specificationModel, '') = :specificationModel
              and coalesce(m.machineType, '') = :machineType
            order by m.vehicleProductNumber asc
            """)
    List<MachineInventory> findVehiclesByModel(
            @Param("name") String name,
            @Param("specificationModel") String specificationModel,
            @Param("machineType") String machineType,
            @Param("includeLocked") boolean includeLocked
    );

    interface VehicleModelSummaryProjection {
        String getName();
        String getSpecificationModel();
        String getMachineType();
        String getSupplier();
        String getWarehouseName();
        BigDecimal getPurchasePrice();
        BigDecimal getSalePrice();
        BigDecimal getSettlementPrice();
        Long getModelTemplateId();
        Long getUnitCount();
        Long getInventoryCount();
        String getVehicleNumbers();
    }

    interface StockValueProjection {
        Long getItemCount();
        Long getStockQuantity();
        BigDecimal getCostValue();
        BigDecimal getRetailValue();
    }
}
