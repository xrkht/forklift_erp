package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.Customer;
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
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByCompanyNameAsc();

    @Query("""
            select c from Customer c
            where (:keyword is null
                or lower(coalesce(c.companyName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.address, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.contactName, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.contactPhone, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.taxOrIdNumber, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(c.remarks, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<Customer> searchPage(@Param("keyword") String keyword, Pageable pageable);

    Optional<Customer> findByCompanyName(String companyName);

    boolean existsByCompanyName(String companyName);

    boolean existsByCompanyNameAndIdNot(String companyName, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);
}
