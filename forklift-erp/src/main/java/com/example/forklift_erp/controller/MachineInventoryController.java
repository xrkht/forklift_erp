package com.example.forklift_erp.controller;

import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.service.MachineInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/api/inventory")
public class MachineInventoryController {
    // 注入业务层
    @Autowired
    private MachineInventoryService service;

    //api接口定义

    // 查询所有
    @GetMapping
    public List<MachineInventory> getAll() {
        return service.findAll();
    }

    // 根据id查询
    @GetMapping("/{id}")
    public ResponseEntity<MachineInventory> getById(@PathVariable Long id) {
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            return ResponseEntity.ok(optional.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    // 根据车号/产品编号查询
    @GetMapping("/vehicleProductNumber/{vehicleProductNumber}")
    public ResponseEntity<MachineInventory> getByVehicleProductNumber(@PathVariable String vehicleProductNumber) {
        Optional<MachineInventory> optional = service.findByVehicleProductNumber(vehicleProductNumber);
        if (optional.isPresent()) {
            return ResponseEntity.ok(optional.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    // 更新车辆
    @PutMapping("/{id}")
    public ResponseEntity<MachineInventory> update(@PathVariable Long id,
                                                   @RequestBody MachineInventory machineInventory) {
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            machineInventory.setId(id);
            MachineInventory updated = service.save(machineInventory);
            return ResponseEntity.ok(updated);
            } else {
            return ResponseEntity.notFound().build();
            }
    }
    //新增车辆
    @PostMapping
    public ResponseEntity<MachineInventory> create(@RequestBody MachineInventory machineInventory) {
        MachineInventory saved = service.save(machineInventory);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    // 删除车辆
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            service.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}

