package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.OperationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, Long> {

    List<OperationAuditLog> findAllByOrderByCreatedAtDesc();

    @Query("""
            select l from OperationAuditLog l
            where (:keyword is null or :keyword = ''
                   or lower(coalesce(l.module, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.action, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.targetCode, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.targetName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.summary, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.operator, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.remark, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<OperationAuditLog> searchPage(@Param("keyword") String keyword, Pageable pageable);
}
