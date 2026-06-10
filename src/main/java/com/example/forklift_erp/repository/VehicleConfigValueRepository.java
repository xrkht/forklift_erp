package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.VehicleConfigValue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleConfigValueRepository extends JpaRepository<VehicleConfigValue, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VehicleConfigValue v where v.id = :id")
    Optional<VehicleConfigValue> findByIdForUpdate(@Param("id") Long id);

    List<VehicleConfigValue> findByVehicleConfigItemIdOrderBySortOrderAscIdAsc(Long vehicleConfigItemId);

    Optional<VehicleConfigValue> findByVehicleConfigItemIdAndConfigItemId(Long vehicleConfigItemId, Long configItemId);

    boolean existsByConfigItemId(Long configItemId);

    boolean existsByConfigValueId(Long configValueId);

    void deleteByVehicleConfigItemId(Long vehicleConfigItemId);
}
