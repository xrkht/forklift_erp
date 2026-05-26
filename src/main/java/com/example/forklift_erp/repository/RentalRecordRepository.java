package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.RentalRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RentalRecordRepository extends JpaRepository<RentalRecord, Long> {
    List<RentalRecord> findAllByOrderByCreatedAtDesc();

    List<RentalRecord> findByMachineIdOrderByCreatedAtDesc(Long machineId);

    boolean existsByMachineIdAndStatus(Long machineId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RentalRecord r where r.id = :id")
    Optional<RentalRecord> findByIdForUpdate(@Param("id") Long id);
}
