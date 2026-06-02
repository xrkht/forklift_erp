package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.PartInventory;
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

    long countByWarehouseId(Long warehouseId);

    // ========== 普通用户专用（过滤锁定） ==========
    List<PartInventory> findAllByIsLockedFalse();
    Optional<PartInventory> findByIdAndIsLockedFalse(Long id);
    Optional<PartInventory> findByPartCodeAndIsLockedFalse(String partCode);

    @Query("""
            select p from PartInventory p
            where (:includeLocked = true or p.isLocked = false)
              and (:keyword is null or :keyword = ''
                   or lower(coalesce(p.partCode, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.partName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.partBrand, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.specification, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.partCategory, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.applicableModels, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.source, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.remarks, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<PartInventory> searchPage(
            @Param("keyword") String keyword,
            @Param("includeLocked") boolean includeLocked,
            Pageable pageable
    );

    @Query("""
            select
              count(p) as itemCount,
              sum(coalesce(p.quantity, 0)) as stockQuantity,
              sum(coalesce(coalesce(p.purchasePrice, p.settlementPrice), 0) * coalesce(p.quantity, 0)) as costValue,
              sum(coalesce(coalesce(p.salePrice, p.settlementPrice), 0) * coalesce(p.quantity, 0)) as retailValue
            from PartInventory p
            """)
    StockValueProjection stockValue();

    @Query("""
            select p from PartInventory p
            where coalesce(p.quantity, 0) <= :threshold
            order by coalesce(p.quantity, 0) asc, p.id asc
            """)
    List<PartInventory> findLowStock(@Param("threshold") int threshold, Pageable pageable);

    interface StockValueProjection {
        Long getItemCount();
        Long getStockQuantity();
        BigDecimal getCostValue();
        BigDecimal getRetailValue();
    }
}
