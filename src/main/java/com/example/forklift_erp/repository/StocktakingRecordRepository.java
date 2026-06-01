package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.StocktakingRecord;
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
public interface StocktakingRecordRepository extends JpaRepository<StocktakingRecord, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StocktakingRecord s where s.id = :id")
    Optional<StocktakingRecord> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select s from StocktakingRecord s
            where (:keyword is null or :keyword = ''
                   or lower(coalesce(s.stocktakingNo, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.resourceType, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.resourceCode, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.resourceName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.specificationModel, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.status, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.operator, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.remark, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<StocktakingRecord> searchPage(@Param("keyword") String keyword, Pageable pageable);
}
