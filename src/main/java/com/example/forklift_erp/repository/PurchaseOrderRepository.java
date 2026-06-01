package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    boolean existsBySupplierId(Long supplierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from PurchaseOrder p
            where (:keyword is null or :keyword = ''
                   or lower(coalesce(p.purchaseNo, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.supplierName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.resourceType, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.resourceCode, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.resourceName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.specificationModel, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.status, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.operator, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.remark, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<PurchaseOrder> searchPage(@Param("keyword") String keyword, Pageable pageable);
}
