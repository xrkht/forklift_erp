package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.OutboundOrder;
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
public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {
    List<OutboundOrder> findAllByOrderByCreatedAtDesc();

    List<OutboundOrder> findAllByIsLockedFalseOrderByCreatedAtDesc();

    @Query("""
            select o from OutboundOrder o
            where (:includeLocked = true or o.isLocked = false)
              and (:keyword is null
                or lower(coalesce(o.orderNo, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.resourceType, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.resourceCode, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.resourceName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.specificationModel, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.customerName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.customerAddress, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.contactName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.contactPhone, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.operator, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.paymentRemark, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.invoiceStatus, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.invoiceOriginalName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.registrationStatus, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.contractType, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.contractOriginalName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(o.orderRemark, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<OutboundOrder> searchPage(
            @Param("keyword") String keyword,
            @Param("includeLocked") boolean includeLocked,
            Pageable pageable
    );

    Optional<OutboundOrder> findByIdAndIsLockedFalse(Long id);

    List<OutboundOrder> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, Long resourceId);

    boolean existsByResourceTypeAndResourceIdAndIsLockedTrueAndIdNot(
            String resourceType,
            Long resourceId,
            Long id
    );

    boolean existsByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboundOrder o where o.id = :id")
    Optional<OutboundOrder> findByIdForUpdate(@Param("id") Long id);
}
