package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.MachineInventory;

import java.util.List;
import java.util.Optional;

public interface MachineInventoryService {
    // 查询所有车辆信息
    List<MachineInventory> findAll();

    // 根据id查询车辆信息
    Optional<MachineInventory> findById(Long id);
    Optional<MachineInventory> findByIdForUpdate(Long id);

    //保存或更新车辆信息
    MachineInventory save(MachineInventory machineInventory);

    // 根据id删除车辆信息
    void deleteById(Long id);

    // 根据车号/产品编号查询车辆信息
    Optional<MachineInventory> findByVehicleProductNumber(String vehicleProductNumber);
}
