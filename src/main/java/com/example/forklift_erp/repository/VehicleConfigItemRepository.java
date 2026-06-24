package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.VehicleConfigItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleConfigItemRepository extends JpaRepository<VehicleConfigItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VehicleConfigItem v where v.id = :id")
    Optional<VehicleConfigItem> findByIdForUpdate(@Param("id") Long id);

    @Query("select v from VehicleConfigItem v where lower(v.specificationModel) = lower(:specificationModel)")
    Optional<VehicleConfigItem> findBySpecificationModel(@Param("specificationModel") String specificationModel);

    List<VehicleConfigItem> findAllByOrderBySortOrderAscSpecificationModelAsc();
}
