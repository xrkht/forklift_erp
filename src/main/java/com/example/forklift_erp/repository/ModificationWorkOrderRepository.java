package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.ModificationWorkOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModificationWorkOrderRepository extends JpaRepository<ModificationWorkOrder, Long> {

    List<ModificationWorkOrder> findAllByOrderByCreatedAtDesc();

    List<ModificationWorkOrder> findByMachineIdOrderByCreatedAtDesc(Long machineId);

    Optional<ModificationWorkOrder> findByWorkOrderNo(String workOrderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ModificationWorkOrder w where w.id = :id")
    Optional<ModificationWorkOrder> findByIdForUpdate(@Param("id") Long id);
}
