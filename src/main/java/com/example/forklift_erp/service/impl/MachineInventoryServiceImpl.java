package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.service.MachineInventoryService;
import com.example.forklift_erp.service.StockLedgerService;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MachineInventoryServiceImpl implements MachineInventoryService {

    @Autowired
    private MachineInventoryRepository repository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Override
    public List<MachineInventory> findAll() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findAll();
        } else {
            return repository.findAllByIsLockedFalse();
        }
    }

    @Override
    public Optional<MachineInventory> findById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findById(id);
        } else {
            return repository.findByIdAndIsLockedFalse(id);
        }
    }

    @Override
    public Optional<MachineInventory> findByIdForUpdate(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findByIdForUpdate(id);
        } else {
            return repository.findByIdAndIsLockedFalseForUpdate(id);
        }
    }

    @Override
    public Optional<MachineInventory> findByVehicleProductNumber(String vehicleProductNumber) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findByVehicleProductNumber(vehicleProductNumber);
        } else {
            return repository.findByVehicleProductNumberAndIsLockedFalse(vehicleProductNumber);
        }
    }

    @Override
    @Transactional
    public MachineInventory save(MachineInventory machineInventory) {
        boolean creating = machineInventory.getId() == null;
        // 更新操作时，检查原记录是否被锁定且当前用户非管理员
        if (machineInventory.getId() != null) {
            Optional<MachineInventory> existingOpt = findById(machineInventory.getId()); // 此处已应用角色过滤
            if (existingOpt.isPresent()) {
                MachineInventory existing = existingOpt.get();
                // 如果原记录是锁定的，且当前用户不是管理员，则拒绝更新
                if (Boolean.TRUE.equals(existing.getIsLocked()) && !SecurityUtils.isAdminOrSuperAdmin()) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "该记录已被锁定，您无权修改");
                }
            } else {
                throw new BusinessException(ResultCode.NOT_FOUND, "要更新的记录不存在");
            }
        }
        normalizeVehicleIdentity(machineInventory);
        if (machineInventory.getInventoryCount() == null) {
            machineInventory.setInventoryCount(creating ? 1 : 0);
        }
        if (Boolean.TRUE.equals(machineInventory.getModelOnly())) {
            machineInventory.setInventoryCount(0);
            machineInventory.setStockStatus("PENDING_INBOUND");
            machineInventory.setEngineNumber(null);
            machineInventory.setFrameNumber(null);
            machineInventory.setWarrantyCardNumber(null);
            machineInventory.setInboundDate(null);
        }
        if (creating && machineInventory.getInboundDate() == null && machineInventory.getInventoryCount() > 0) {
            machineInventory.setInboundDate(java.time.LocalDateTime.now());
        }
        if (machineInventory.getWarehouseId() == null) {
            machineInventory.setWarehouseId(stockLedgerService.resolveWarehouseId(null));
        }
        if (machineInventory.getStockStatus() == null || machineInventory.getStockStatus().isBlank()) {
            machineInventory.setStockStatus(machineInventory.getInventoryCount() > 0 ? "IN_STOCK" : "PENDING_INBOUND");
        }

        collaborationService.stampWrite(machineInventory);
        MachineInventory saved = repository.saveAndFlush(machineInventory);
        if (!Boolean.TRUE.equals(saved.getModelOnly())) {
            stockLedgerService.syncBalance(
                    StockLedgerService.RESOURCE_MACHINE,
                    saved.getId(),
                    saved.getWarehouseId(),
                    saved.getInventoryCount()
            );
        }
        return saved;
    }

    private void normalizeVehicleIdentity(MachineInventory machineInventory) {
        if (machineInventory.getModelOnly() == null) {
            machineInventory.setModelOnly(false);
        }
        String vehicleNumber = trimToNull(machineInventory.getVehicleProductNumber());
        if (vehicleNumber != null) {
            machineInventory.setVehicleProductNumber(vehicleNumber);
            return;
        }
        if (Boolean.TRUE.equals(machineInventory.getModelOnly())) {
            machineInventory.setVehicleProductNumber(generateVehicleNumber("MODEL"));
            return;
        }
        if (isManualForklift(machineInventory.getMachineType())) {
            machineInventory.setVehicleProductNumber(generateVehicleNumber("MAN"));
            return;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "车号不能为空");
    }

    private boolean isManualForklift(String machineType) {
        String normalized = trimToNull(machineType);
        return normalized != null && normalized.contains("手动");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateVehicleNumber(String prefix) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "%s-%s".formatted(prefix, UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            if (!repository.existsByVehicleProductNumber(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "系统生成车号失败，请重试");
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Optional<MachineInventory> existingOpt = findByIdForUpdate(id); // 角色过滤
        if (existingOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        MachineInventory existing = existingOpt.get();
        if (Boolean.TRUE.equals(existing.getIsLocked()) && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该记录已被锁定，您无权删除");
        }
        repository.deleteById(id);
    }
}
