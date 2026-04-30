package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.service.MachineInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MachineInventoryServiceImpl  implements MachineInventoryService{

    // 注入数据访问层
    @Autowired
    private MachineInventoryRepository repository;

    // 查询所有
    @Override
    public List<MachineInventory> findAll() {
        return repository.findAll();
    }

    // 根据id查询
    @Override
    public Optional<MachineInventory> findById(Long id) {
        return repository.findById(id);
    }

    // 根据车号/产品编号查询
    @Override
    public Optional<MachineInventory> findByVehicleProductNumber(String vehicleProductNumber) {
        return repository.findByVehicleProductNumber(vehicleProductNumber);
    }

    //保存或更新
    @Override
    public MachineInventory save(MachineInventory machineInventory) {
        //未来可加入逻辑
        //如检查价格是否为负数,自动设置默认值等
        return repository.save(machineInventory);
    }

    // 按id删除
    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
