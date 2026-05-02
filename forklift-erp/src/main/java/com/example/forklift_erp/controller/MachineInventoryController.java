package com.example.forklift_erp.controller;

import com.example.forklift_erp.dto.InboundRequestDTO;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.service.MachineConfigService;
import com.example.forklift_erp.service.MachineInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController  // 改为 RestController，才能返回 JSON
@RequestMapping("/api/inventory")
public class MachineInventoryController {

    // 注入车辆业务层
    @Autowired
    private MachineInventoryService service;

    // 注入配置业务层
    @Autowired
    private MachineConfigService machineConfigService;

    // ======================== 基础 CRUD 接口 ========================

    /**
     * 查询所有车辆
     */
    @GetMapping
    public ResponseEntity<List<MachineInventory>> getAll() {
        List<MachineInventory> list = service.findAll();
        return ResponseEntity.ok(list);
    }

    /**
     * 根据 ID 查询单条车辆
     */
    @GetMapping("/{id}")
    public ResponseEntity<MachineInventory> getById(@PathVariable Long id) {
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            return ResponseEntity.ok(optional.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 根据车号/产品编号查询（唯一编号）
     */
    @GetMapping("/vehicleProductNumber/{vehicleProductNumber}")
    public ResponseEntity<MachineInventory> getByVehicleProductNumber(@PathVariable String vehicleProductNumber) {
        Optional<MachineInventory> optional = service.findByVehicleProductNumber(vehicleProductNumber);
        if (optional.isPresent()) {
            return ResponseEntity.ok(optional.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 查询车辆完整信息（含配置明细）
     */
    @GetMapping("/{id}/detail")
    public ResponseEntity<Map<String, Object>> getDetail(@PathVariable Long id) {
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            MachineInventory machine = optional.get();
            List<MachineConfig> configs = machineConfigService.findByMachineId(id);

            Map<String, Object> result = new HashMap<>();
            result.put("machine", machine);       // 车辆基本信息
            result.put("configs", configs);       // 该车的所有配置明细

            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 更新车辆基本信息（不包含配置）
     */
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

    /**
     * 简单新增车辆（不包含配置明细）
     */
    @PostMapping
    public ResponseEntity<MachineInventory> create(@RequestBody MachineInventory machineInventory) {
        MachineInventory saved = service.save(machineInventory);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 删除车辆（级联删除该车的所有配置明细）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            // 先删除该车的所有配置明细
            machineConfigService.deleteByMachineId(id);
            // 再删除车辆本身
            service.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ======================== 入库与配置管理接口 ========================

    /**
     * 完整的入库接口
     * 一次性保存车辆基本信息和配置明细
     *
     * 请求体示例：
     * {
     *   "machineInventory": { "name": "...", "vehicleProductNumber": "..." },
     *   "configs": [
     *     { "configItemId": 1, "configValueId": 1, "itemName": "发动机", "selectedValue": "全柴V29" },
     *     { "configItemId": 20, "configValueId": 2, "itemName": "轮胎", "selectedValue": "实心胎" }
     *   ]
     * }
     */
    @PostMapping("/inbound")
    public ResponseEntity<MachineInventory> inbound(@RequestBody InboundRequestDTO request) {

        // 第一步：保存车辆基本信息，获得车辆 ID
        MachineInventory savedMachine = service.save(request.getMachineInventory());
        Long machineId = savedMachine.getId();

        // 第二步：如果有配置明细，批量保存到 machine_config 表
        if (request.getConfigs() != null && !request.getConfigs().isEmpty()) {
            List<MachineConfig> configList = new ArrayList<>();

            for (InboundRequestDTO.ConfigSelection config : request.getConfigs()) {
                MachineConfig mc = new MachineConfig();
                mc.setMachineId(machineId);                    // 关联车辆 ID
                mc.setConfigItemId(config.getConfigItemId());   // 配置项 ID
                mc.setConfigValueId(config.getConfigValueId()); // 配置值 ID
                mc.setItemName(config.getItemName());           // 冗余：配置项名称
                mc.setSelectedValue(config.getSelectedValue()); // 冗余：选中的值
                mc.setIsStandard(config.getIsStandard() != null ? config.getIsStandard() : true);
                mc.setConfigSource("FACTORY");                  // 入库默认是原厂配置
                mc.setInstalledDate(LocalDateTime.now());       // 安装时间 = 入库时间
                configList.add(mc);
            }

            // 批量保存所有配置明细
            machineConfigService.saveAll(configList);
        }

        // 第三步：返回保存后的车辆信息
        return ResponseEntity.status(HttpStatus.CREATED).body(savedMachine);
    }

    /**
     * 更新车辆的配置明细
     * 先删除旧配置，再保存新配置
     */
    @PutMapping("/{id}/configs")
    public ResponseEntity<List<MachineConfig>> updateConfigs(@PathVariable Long id,
                                                             @RequestBody List<MachineConfig> configs) {
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            // 先删除该车原有的所有配置
            machineConfigService.deleteByMachineId(id);

            // 设置新的关联关系并保存
            for (MachineConfig config : configs) {
                config.setMachineId(id);
                if (config.getInstalledDate() == null) {
                    config.setInstalledDate(LocalDateTime.now());
                }
            }
            List<MachineConfig> savedConfigs = machineConfigService.saveAll(configs);
            return ResponseEntity.ok(savedConfigs);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}