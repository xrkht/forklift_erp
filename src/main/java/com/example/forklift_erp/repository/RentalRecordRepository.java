package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.RentalRecord;
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
public interface RentalRecordRepository extends JpaRepository<RentalRecord, Long> {
    List<RentalRecord> findAllByOrderByCreatedAtDesc();

    @Query("""
            select r from RentalRecord r
            where :keyword is null or :keyword = ''
               or lower(r.rentalNo) like lower(concat('%', :keyword, '%'))
               or lower(r.vehicleNumber) like lower(concat('%', :keyword, '%'))
               or lower(r.machineName) like lower(concat('%', :keyword, '%'))
               or lower(r.specificationModel) like lower(concat('%', :keyword, '%'))
               or lower(r.customerName) like lower(concat('%', :keyword, '%'))
               or lower(r.customerAddress) like lower(concat('%', :keyword, '%'))
               or lower(r.destination) like lower(concat('%', :keyword, '%'))
               or lower(r.status) like lower(concat('%', :keyword, '%'))
               or lower(r.operator) like lower(concat('%', :keyword, '%'))
               or lower(r.remark) like lower(concat('%', :keyword, '%'))
            """)
    Page<RentalRecord> searchPage(@Param("keyword") String keyword, Pageable pageable);

    List<RentalRecord> findByMachineIdOrderByCreatedAtDesc(Long machineId);

    boolean existsByMachineIdAndStatus(Long machineId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RentalRecord r where r.id = :id")
    Optional<RentalRecord> findByIdForUpdate(@Param("id") Long id);
}
