package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.InboundRequestDTO;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.MachineConfigService;
import com.example.forklift_erp.service.MachineInventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j // 添加日志支持
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
     * @return 统一响应格式，包含车辆列表
     */
    @GetMapping
    public Result<List<MachineInventory>> getAll() {
        log.info("查询所有车辆信息");
        List<MachineInventory> list = service.findAll();
        return Result.success(list);
    }

    /**
     * 根据 ID 查询单条车辆
     * @param id 车辆ID
     * @return 统一响应格式，包含车辆信息
     */
    @GetMapping("/{id}")
    public Result<MachineInventory> getById(@PathVariable Long id) {
        log.info("根据ID查询车辆: id={}", id);
        return service.findById(id)
                .map(machine -> {
                    log.debug("查询到车辆: {}", machine);
                    return Result.success(machine);
                })
                .orElseThrow(() -> {
                    log.warn("车辆不存在: id={}", id);
                    return new BusinessException(ResultCode.VEHICLE_NOT_FOUND);
                });
    }

    /**
     * 根据车号/产品编号查询（唯一编号）
     * @param vehicleProductNumber 车号/产品编号
     * @return 统一响应格式，包含车辆信息
     */
    @GetMapping("/vehicleProductNumber/{vehicleProductNumber}")
    public Result<MachineInventory> getByVehicleProductNumber(@PathVariable String vehicleProductNumber) {
        log.info("根据车号查询车辆: vehicleProductNumber={}", vehicleProductNumber);
        return service.findByVehicleProductNumber(vehicleProductNumber)
                .map(machine -> {
                    log.debug("查询到车辆: {}", machine);
                    return Result.success(machine);
                })
                .orElseThrow(() -> {
                    log.warn("车辆不存在: vehicleProductNumber={}", vehicleProductNumber);
                    return new BusinessException(ResultCode.VEHICLE_NOT_FOUND,
                            "车辆编号不存在: " + vehicleProductNumber);
                });
    }

    /**
     * 查询车辆完整信息（含配置明细）
     * @param id 车辆ID
     * @return 统一响应格式，包含车辆基本信息和配置明细
     */
    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
        log.info("查询车辆详细信息: id={}", id);
        MachineInventory machine = service.findById(id)
                .orElseThrow(() -> {
                    log.warn("查询详情失败，车辆不存在: id={}", id);
                    return new BusinessException(ResultCode.VEHICLE_NOT_FOUND);
                });

        List<MachineConfig> configs = machineConfigService.findByMachineId(id);
        log.debug("车辆配置数量: {}", configs.size());

        Map<String, Object> result = new HashMap<>();
        result.put("machine", machine);       // 车辆基本信息
        result.put("configs", configs);       // 该车的所有配置明细

        return Result.success(result);
    }

    /**
     * 更新车辆基本信息（不包含配置）
     * @param id 车辆ID
     * @param machineInventory 更新后的车辆信息
     * @return 统一响应格式，包含更新后的车辆信息
     */
    @PutMapping("/{id}")
    public Result<MachineInventory> update(@PathVariable Long id,
                                           @Valid @RequestBody MachineInventory machineInventory) {
        log.info("更新车辆信息: id={}", id);
        if (!service.findById(id).isPresent()) {
            log.warn("更新失败，车辆不存在: id={}", id);
            throw new BusinessException(ResultCode.VEHICLE_NOT_FOUND);
        }
        machineInventory.setId(id);
        MachineInventory updated = service.save(machineInventory);
        log.info("车辆更新成功: id={}, name={}", updated.getId(), updated.getName());
        return Result.success("更新成功", updated);
    }

    /**
     * 简单新增车辆（不包含配置明细）
     * @param machineInventory 新增的车辆信息
     * @return 统一响应格式，包含新增的车辆信息
     */
    @PostMapping
    public Result<MachineInventory> create(@Valid @RequestBody MachineInventory machineInventory) {
        log.info("新增车辆: name={}, vehicleProductNumber={}",
                machineInventory.getName(), machineInventory.getVehicleProductNumber());
        MachineInventory saved = service.save(machineInventory);
        log.info("车辆新增成功: id={}", saved.getId());
        return Result.success("新增成功", saved);
    }

    /**
     * 删除车辆（级联删除该车的所有配置明细）
     * @param id 车辆ID
     * @return 统一响应格式，包含删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除车辆: id={}", id);
        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            MachineInventory machine = optional.get();
            // 先删除该车的所有配置明细
            int configCount = machineConfigService.findByMachineId(id).size();
            machineConfigService.deleteByMachineId(id);
            log.debug("已删除车辆 {} 的 {} 条配置明细", id, configCount);
            // 再删除车辆本身
            service.deleteById(id);
            log.info("车辆删除成功: id={}, vehicleProductNumber={}", id, machine.getVehicleProductNumber());
            return Result.success("删除成功");
        } else {
            log.warn("删除失败，车辆不存在: id={}", id);
            throw new BusinessException(ResultCode.VEHICLE_NOT_FOUND);
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
     *
     * @param request 入库请求DTO，包含车辆基本信息和配置明细
     * @return 统一响应格式，包含入库后的车辆信息
     */
    @PostMapping("/inbound")
    public Result<MachineInventory> inbound(@Valid @RequestBody InboundRequestDTO request) {
        log.info("车辆入库开始: vehicleProductNumber={}", request.getMachineInventory().getVehicleProductNumber());

        // 第一步：保存车辆基本信息，获得车辆 ID
        MachineInventory savedMachine = service.save(request.getMachineInventory());
        Long machineId = savedMachine.getId();
        log.debug("车辆基本信息保存成功: id={}", machineId);

        // 第二步：如果有配置明细，批量保存到 machine_config 表
        if (request.getConfigs() != null && !request.getConfigs().isEmpty()) {
            List<MachineConfig> configList = new ArrayList<>();
            log.debug("处理配置明细数量: {}", request.getConfigs().size());

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
            log.debug("配置明细保存成功");
        }

        // 第三步：返回保存后的车辆信息
        log.info("车辆入库成功: id={}, vehicleProductNumber={}",
                savedMachine.getId(), savedMachine.getVehicleProductNumber());
        return Result.success("入库成功", savedMachine);
    }

    /**
     * 更新车辆的配置明细
     * 先删除旧配置，再保存新配置
     *
     * @param id 车辆ID
     * @param configs 新的配置明细列表
     * @return 统一响应格式，包含更新后的配置明细列表
     */
    @PutMapping("/{id}/configs")
    public Result<List<MachineConfig>> updateConfigs(@PathVariable Long id,
                                                     @Valid @RequestBody List<MachineConfig> configs) {
        log.info("更新车辆配置: machineId={}, 新配置数量={}", id, configs.size());

        Optional<MachineInventory> optional = service.findById(id);
        if (optional.isPresent()) {
            MachineInventory machine = optional.get();

            // 先删除该车原有的所有配置
            int oldConfigCount = machineConfigService.findByMachineId(id).size();
            machineConfigService.deleteByMachineId(id);
            log.debug("已删除旧配置 {} 条", oldConfigCount);

            // 设置新的关联关系并保存
            for (MachineConfig config : configs) {
                config.setMachineId(id);
                if (config.getInstalledDate() == null) {
                    config.setInstalledDate(LocalDateTime.now());
                }
            }
            List<MachineConfig> savedConfigs = machineConfigService.saveAll(configs);
            log.info("配置更新成功: machineId={}, vehicleProductNumber={}, 配置数量={}",
                    id, machine.getVehicleProductNumber(), savedConfigs.size());
            return Result.success("配置更新成功", savedConfigs);
        } else {
            log.warn("配置更新失败，车辆不存在: id={}", id);
            throw new BusinessException(ResultCode.VEHICLE_NOT_FOUND);
        }
    }
}