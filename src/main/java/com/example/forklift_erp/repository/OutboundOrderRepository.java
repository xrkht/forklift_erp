package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.OutboundOrder;
import jakarta.persistence.LockModeType;
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

    List<OutboundOrder> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, Long resourceId);

    boolean existsByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboundOrder o where o.id = :id")
    Optional<OutboundOrder> findByIdForUpdate(@Param("id") Long id);
}
