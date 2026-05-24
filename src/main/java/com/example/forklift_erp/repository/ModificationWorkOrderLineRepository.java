package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.ModificationWorkOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModificationWorkOrderLineRepository extends JpaRepository<ModificationWorkOrderLine, Long> {

    List<ModificationWorkOrderLine> findByWorkOrderIdOrderByIdAsc(Long workOrderId);
}
