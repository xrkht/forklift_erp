package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.StockMovementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementLineRepository extends JpaRepository<StockMovementLine, Long> {
    List<StockMovementLine> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, Long resourceId);
}
