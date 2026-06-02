package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.InboundRequestDTO;
import com.example.forklift_erp.dto.MachineConfigVO;
import com.example.forklift_erp.dto.MachineDetailVO;
import com.example.forklift_erp.dto.MachineInventoryCreateDTO;
import com.example.forklift_erp.dto.MachineInventoryVO;
import com.example.forklift_erp.dto.StockAdjustRequestDTO;
import com.example.forklift_erp.dto.VehicleModelSummaryVO;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.MachineConfigService;
import com.example.forklift_erp.service.MachineInventoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
public class MachineInventoryController {

    @Autowired
    private MachineInventoryService service;

    @Autowired
    private MachineConfigService machineConfigService;

    @GetMapping
    public Result<?> getAll(@RequestParam(defaultValue = "false") boolean paged,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        if (paged) {
            return Result.success(service.findPage(keyword, page, size));
        }
        return Result.success(service.findAll().stream().map(MachineInventoryVO::fromEntity).toList());
    }

    @GetMapping("/models")
    public Result<PageResult<VehicleModelSummaryVO>> getModelPage(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        return Result.success(service.findModelPage(keyword, page, size));
    }

    @GetMapping("/model-vehicles")
    public Result<List<MachineInventoryVO>> getModelVehicles(@RequestParam String name,
                                                             @RequestParam String specificationModel,
                                                             @RequestParam(required = false) String machineType) {
        return Result.success(service.findVehiclesByModel(name, specificationModel, machineType));
    }

    @GetMapping("/{id}")
    public Result<MachineInventoryVO> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(MachineInventoryVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
    }

    @GetMapping("/vehicleProductNumber/{vehicleProductNumber}")
    public Result<MachineInventoryVO> getByVehicleProductNumber(@PathVariable String vehicleProductNumber) {
        return service.findByVehicleProductNumber(vehicleProductNumber)
                .map(MachineInventoryVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
    }

    @GetMapping("/{id}/detail")
    public Result<MachineDetailVO> getDetail(@PathVariable Long id) {
        var machine = service.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        List<MachineConfig> configs = machineConfigService.findByMachineId(id);
        MachineDetailVO detail = new MachineDetailVO();
        detail.setMachine(MachineInventoryVO.fromEntity(machine));
        detail.setConfigs(configs.stream().map(MachineConfigVO::fromEntity).toList());
        return Result.success(detail);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<MachineInventoryVO> create(@Valid @RequestBody MachineInventoryCreateDTO dto) {
        return Result.success("新增成功", service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<MachineInventoryVO> update(@PathVariable Long id, @Valid @RequestBody MachineInventoryCreateDTO dto) {
        return Result.success("更新成功", service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long version) {
        service.delete(id, version);
        return Result.success("删除成功");
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<Void> lock(@PathVariable Long id, @RequestParam boolean locked, @RequestParam(required = false) Long version) {
        service.setLocked(id, locked, version);
        return Result.success(locked ? "车辆已锁定" : "车辆已解锁");
    }

    @PostMapping("/inbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<MachineInventoryVO> inbound(@Valid @RequestBody InboundRequestDTO request) {
        return Result.success("入库成功", service.inbound(request));
    }

    @PutMapping("/{id}/inbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<MachineInventoryVO> inboundStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequestDTO request) {
        return Result.success("整车入库成功", service.inboundStock(id, request));
    }

    @PutMapping("/{id}/outbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<MachineInventoryVO> outboundStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequestDTO request) {
        return Result.success("整车出库成功", service.outboundStock(id, request));
    }

    @PutMapping("/{id}/configs")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'vehicle:write')")
    public Result<List<MachineConfigVO>> updateConfigs(@PathVariable Long id,
                                                       @RequestParam(required = false) Long version,
                                                       @Valid @RequestBody List<MachineConfigVO> configVOs) {
        return Result.success("配置更新成功", service.updateConfigs(id, version, configVOs));
    }
}
