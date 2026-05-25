package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByCompanyNameAsc();

    Optional<Customer> findByCompanyName(String companyName);

    boolean existsByCompanyName(String companyName);

    boolean existsByCompanyNameAndIdNot(String companyName, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);
}
