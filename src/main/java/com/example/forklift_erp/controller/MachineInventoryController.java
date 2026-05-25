package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.*;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.MachineConfigService;
import com.example.forklift_erp.service.MachineInventoryService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.StockLedgerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class MachineInventoryController {

    @Autowired
    private MachineInventoryService service;

    @Autowired
    private MachineConfigService machineConfigService;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private StockLedgerService stockLedgerService;

    // 查询所有车辆
    @GetMapping
    public Result<List<MachineInventoryVO>> getAll() {
        log.info("查询所有车辆");
        List<MachineInventoryVO> list = service.findAll().stream()
                .map(MachineInventoryVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    // 根据ID查询
    @GetMapping("/{id}")
    public Result<MachineInventoryVO> getById(@PathVariable Long id) {
        log.info("根据ID查询车辆: id={}", id);
        return service.findById(id)
                .map(MachineInventoryVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
    }

    // 根据车号查询
    @GetMapping("/vehicleProductNumber/{vehicleProductNumber}")
    public Result<MachineInventoryVO> getByVehicleProductNumber(@PathVariable String vehicleProductNumber) {
        log.info("根据车号查询: {}", vehicleProductNumber);
        return service.findByVehicleProductNumber(vehicleProductNumber)
                .map(MachineInventoryVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
    }

    // 查询详情（含配置）
    @GetMapping("/{id}/detail")
    public Result<MachineDetailVO> getDetail(@PathVariable Long id) {
        log.info("查询车辆详情: id={}", id);
        MachineInventory machine = service.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        List<MachineConfig> configs = machineConfigService.findByMachineId(id);
        MachineDetailVO detail = new MachineDetailVO();
        detail.setMachine(MachineInventoryVO.fromEntity(machine));
        detail.setConfigs(configs.stream().map(MachineConfigVO::fromEntity).collect(Collectors.toList()));
        return Result.success(detail);
    }

    // 新增车辆
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    @Transactional
    public Result<MachineInventoryVO> create(@Valid @RequestBody MachineInventoryCreateDTO dto) {
        log.info("新增车辆: name={}, vehicleProductNumber={}", dto.getName(), dto.getVehicleProductNumber());
        MachineInventory saved = service.save(dto.toEntity());
        int quantity = saved.getInventoryCount() == null ? 0 : saved.getInventoryCount();
        if (quantity > 0) {
            saveStockLog(saved, "INITIAL", quantity, 0, quantity, null, "Initial machine stock");
        }
        String summary = Boolean.TRUE.equals(saved.getModelOnly()) ? "新增车型模板" : "新增整车档案";
        operationAuditService.record("整车档案", "CREATE", "MACHINE", saved.getId(),
                saved.getVehicleProductNumber(), saved.getName(), summary, null, saved.getRemarks());
        return Result.success("新增成功", MachineInventoryVO.fromEntity(saved));
    }

    // 更新车辆
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    @Transactional
    public Result<MachineInventoryVO> update(@PathVariable Long id, @Valid @RequestBody MachineInventoryCreateDTO dto) {
        log.info("更新车辆: id={}", id);
        MachineInventory machine = service.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        collaborationService.validateWrite(machine, dto.getVersion());
        int beforeQuantity = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        dto.updateEntity(machine);
        MachineInventory saved = service.save(machine);
        int afterQuantity = saved.getInventoryCount() == null ? 0 : saved.getInventoryCount();
        if (beforeQuantity != afterQuantity) {
            saveStockLog(saved, "ADJUST", Math.abs(afterQuantity - beforeQuantity),
                    beforeQuantity, afterQuantity, null, "Machine stock adjusted from profile edit");
        }
        operationAuditService.record("整车档案", "UPDATE", "MACHINE", saved.getId(),
                saved.getVehicleProductNumber(), saved.getName(), "更新整车档案", null, saved.getRemarks());
        return Result.success("更新成功", MachineInventoryVO.fromEntity(saved));
    }

    // 删除车辆
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    @Transactional
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long version) {
        log.info("删除车辆: id={}", id);
        MachineInventory machine = service.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        collaborationService.validateWrite(machine, version);
        machineConfigService.deleteByMachineId(id);
        service.deleteById(id);
        operationAuditService.record("整车档案", "DELETE", "MACHINE", machine.getId(),
                machine.getVehicleProductNumber(), machine.getName(), "删除整车档案", null, machine.getRemarks());
        return Result.success("删除成功");
    }

    // 锁定车辆
    @PutMapping("/{id}/lock")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    @Transactional
    public Result<Void> lock(@PathVariable Long id, @RequestParam boolean locked, @RequestParam(required = false) Long version) {
        MachineInventory machine = service.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
        collaborationService.validateWrite(machine, version);
        machine.setIsLocked(locked);
        MachineInventory saved = service.save(machine);
        operationAuditService.record("整车档案", locked ? "LOCK" : "UNLOCK", "MACHINE", saved.getId(),
                saved.getVehicleProductNumber(), saved.getName(), locked ? "锁定整车档案" : "解锁整车档案", null, null);
        return Result.success(locked ? "车辆已锁定" : "车辆已解锁");
    }

    // 入库（车辆+配置）
    @PostMapping("/inbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    @Transactional
    public Result<MachineInventoryVO> inbound(@Valid @RequestBody InboundRequestDTO request) {
        log.info("车辆入库: vehicleProductNumber={}", request.getMachineInventory().getVehicleProductNumber());
        MachineInventory machine = request.getMachineInventory().toEntity();
        MachineInventory savedMachine = service.save(machine);
        Long machineId = savedMachine.getId();

        if (request.getConfigs() != null && !request.getConfigs().isEmpty()) {
            List<MachineConfig> configList = new ArrayList<>();
            for (InboundRequestDTO.ConfigSelection config : request.getConfigs()) {
                ConfigItem item = configItemRepository.findById(config.getConfigItemId())
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置类型不存在"));
                ConfigValue value = configValueRepository.findById(config.getConfigValueId())
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "具体配置不存在"));
                if (!item.getId().equals(value.getConfigItemId())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "具体配置不属于所选配置类型");
                }
                MachineConfig mc = new MachineConfig();
                mc.setMachineId(machineId);
                mc.setConfigItemId(item.getId());
                mc.setConfigValueId(value.getId());
                mc.setItemName(item.getItemName());
                mc.setSelectedValue(value.getValueLabel());
                String configSource = config.getConfigSource() == null || config.getConfigSource().isBlank()
                        ? "FACTORY_STANDARD"
                        : config.getConfigSource();
                mc.setConfigSource(configSource);
                mc.setIsStandard(config.getIsStandard() != null
                        ? config.getIsStandard()
                        : !"FACTORY_OPTIONAL".equals(configSource));
                mc.setInstalledDate(LocalDateTime.now());
                configList.add(mc);
            }
            machineConfigService.saveAll(configList);
        }
        int quantity = savedMachine.getInventoryCount() == null ? 0 : savedMachine.getInventoryCount();
        if (quantity > 0) {
            saveStockLog(savedMachine, "INBOUND", quantity, 0, quantity, null, "整车入库建档");
        }
        return Result.success("入库成功", MachineInventoryVO.fromEntity(savedMachine));
    }

    @PutMapping("/{id}/inbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    @Transactional
    public Result<MachineInventoryVO> inboundStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequestDTO request) {
        MachineInventory machine = adjustStock(id, request, true);
        return Result.success("整车入库成功", MachineInventoryVO.fromEntity(machine));
    }

    @PutMapping("/{id}/outbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    @Transactional
    public Result<MachineInventoryVO> outboundStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequestDTO request) {
        MachineInventory machine = adjustStock(id, request, false);
        return Result.success("整车出库成功", MachineInventoryVO.fromEntity(machine));
    }

    // 更新车辆配置
    @PutMapping("/{id}/configs")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    @Transactional
    public Result<List<MachineConfigVO>> updateConfigs(@PathVariable Long id,
                                                       @RequestParam(required = false) Long version,
                                                       @Valid @RequestBody List<MachineConfigVO> configVOs) {
        log.info("更新车辆配置: machineId={}, 数量={}", id, configVOs.size());
        MachineInventory machine = service.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        collaborationService.validateWrite(machine, version);
        // 将 VO 转为实体，同时维护机器关联
        List<MachineConfig> configs = configVOs.stream().map(vo -> {
            MachineConfig mc = new MachineConfig();
            mc.setMachineId(id);
            mc.setConfigItemId(vo.getConfigItemId());
            mc.setConfigValueId(vo.getConfigValueId());
            mc.setItemName(vo.getItemName());
            mc.setSelectedValue(vo.getSelectedValue());
            mc.setIsStandard(vo.getIsStandard());
            mc.setConfigSource(vo.getConfigSource() != null ? vo.getConfigSource() : "FACTORY");
            mc.setInstalledDate(vo.getInstalledDate() != null ? vo.getInstalledDate() : LocalDateTime.now());
            mc.setRemark(vo.getRemark());
            return mc;
        }).collect(Collectors.toList());

        machineConfigService.deleteByMachineId(id);
        List<MachineConfig> saved = machineConfigService.saveAll(configs);
        service.save(machine);
        List<MachineConfigVO> result = saved.stream().map(MachineConfigVO::fromEntity).collect(Collectors.toList());
        operationAuditService.record("整车配置", "CONFIG_UPDATE", "MACHINE", id,
                null, "车辆ID " + id, "更新整车配置 " + result.size() + " 项", null, null);
        return Result.success("配置更新成功", result);
    }

    private MachineInventory adjustStock(Long id, StockAdjustRequestDTO request, boolean inbound) {
        MachineInventory machine = service.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
        Integer quantity = request.getQuantity();
        int current = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        if (!inbound && current < quantity) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "整车库存不足，当前库存：" + current);
        }
        collaborationService.validateWrite(machine, request.getVersion());
        int after = inbound ? current + quantity : current - quantity;
        machine.setInventoryCount(after);
        machine.setStockStatus(after > 0 ? "IN_STOCK" : (inbound ? "PENDING_INBOUND" : "OUTBOUND"));
        if (inbound) {
            machine.setInboundDate(LocalDateTime.now());
        }
        MachineInventory saved = service.save(machine);
        saveStockLog(saved, inbound ? "INBOUND" : "OUTBOUND", quantity, current, after, request.getOperator(), request.getRemark());
        return saved;
    }

    private StockOperationLog saveStockLog(MachineInventory machine, String operationType, Integer quantity,
                              Integer beforeQuantity, Integer afterQuantity, String operator, String remark) {
        StockOperationLog stockLog = new StockOperationLog();
        stockLog.setResourceType("MACHINE");
        stockLog.setOperationType(operationType);
        stockLog.setResourceId(machine.getId());
        stockLog.setResourceCode(machine.getVehicleProductNumber());
        stockLog.setResourceName(machine.getName());
        stockLog.setQuantity(quantity);
        stockLog.setBeforeQuantity(beforeQuantity);
        stockLog.setAfterQuantity(afterQuantity);
        stockLog.setOperator(operator);
        stockLog.setRemark(remark);
        StockOperationLog savedLog = stockOperationLogRepository.save(stockLog);
        stockLedgerService.recordMovement(
                operationType,
                StockLedgerService.RESOURCE_MACHINE,
                machine.getId(),
                machine.getVehicleProductNumber(),
                machine.getName(),
                machine.getWarehouseId(),
                beforeQuantity,
                afterQuantity,
                operator,
                remark,
                "STOCK_LOG",
                savedLog.getId()
        );
        operationAuditService.record("整车出入库", operationType, "MACHINE", machine.getId(),
                machine.getVehicleProductNumber(), machine.getName(),
                ("INBOUND".equals(operationType) ? "整车入库 " : "整车出库 ") + quantity,
                operator, remark, "STOCK", savedLog.getId());
        return savedLog;
    }
}
