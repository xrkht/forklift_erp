// MachineInventoryRepository.java
package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.MachineInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 整机库存数据访问层
 */
@Repository
public interface MachineInventoryRepository extends JpaRepository<MachineInventory, Long> {
    // JpaRepository<实体类, 主键类型>

    // 根据车号/产品编号查询
    Optional<MachineInventory> findByVehicleProductNumber(String vehicleProductNumber);
}