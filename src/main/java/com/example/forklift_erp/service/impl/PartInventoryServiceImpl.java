package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.PartInventoryService;
import com.example.forklift_erp.service.StockLedgerService;
import com.example.forklift_erp.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PartInventoryServiceImpl implements PartInventoryService {

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Override
    public List<PartInventory> findAll() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findAll();
        } else {
            return partRepository.findAllByIsLockedFalse();
        }
    }

    @Override
    public Optional<PartInventory> findById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findById(id);
        } else {
            return partRepository.findByIdAndIsLockedFalse(id);
        }
    }

    @Override
    public Optional<PartInventory> findByIdForUpdate(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByIdForUpdate(id);
        } else {
            return partRepository.findByIdAndIsLockedFalseForUpdate(id);
        }
    }

    @Override
    public Optional<PartInventory> findByPartCode(String partCode) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByPartCode(partCode);
        } else {
            return partRepository.findByPartCodeAndIsLockedFalse(partCode);
        }
    }

    @Override
    public Optional<PartInventory> findByPartCodeForUpdate(String partCode) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByPartCodeForUpdate(partCode);
        } else {
            return partRepository.findByPartCodeAndIsLockedFalseForUpdate(partCode);
        }
    }

    @Override
    @Transactional
    public PartInventory save(PartInventory part) {
        // 更新时检查锁定
        if (part.getId() != null) {
            Optional<PartInventory> existingOpt = findById(part.getId());
            if (existingOpt.isPresent()) {
                PartInventory existing = existingOpt.get();
                if (existing.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "该配件记录已被锁定，您无权修改");
                }
            } else {
                throw new BusinessException(ResultCode.NOT_FOUND, "配件不存在");
            }
        }

        // 编码唯一性校验（原逻辑保留）
        if (part.getId() == null) {
            Optional<PartInventory> exist = partRepository.findByPartCode(part.getPartCode());
            if (exist.isPresent()) {
                throw new BusinessException(ResultCode.DATA_DUPLICATE, "配件编码已存在: " + part.getPartCode());
            }
        } else {
            Optional<PartInventory> exist = partRepository.findByPartCode(part.getPartCode());
            if (exist.isPresent() && !exist.get().getId().equals(part.getId())) {
                throw new BusinessException(ResultCode.DATA_DUPLICATE, "配件编码已被其他配件使用: " + part.getPartCode());
            }
        }

        if (part.getQuantity() == null) part.setQuantity(0);
        if (part.getWarehouseId() == null) {
            part.setWarehouseId(stockLedgerService.resolveWarehouseId(null));
        }
        log.info("保存配件: partCode={}, name={}, quantity={}", part.getPartCode(), part.getPartName(), part.getQuantity());
        collaborationService.stampWrite(part);
        PartInventory saved = partRepository.saveAndFlush(part);
        stockLedgerService.syncBalance(
                StockLedgerService.RESOURCE_PART,
                saved.getId(),
                saved.getWarehouseId(),
                saved.getQuantity()
        );
        return saved;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Optional<PartInventory> existingOpt = findByIdForUpdate(id);
        if (existingOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配件不存在，id=" + id);
        }
        PartInventory existing = existingOpt.get();
        if (existing.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该配件记录已被锁定，您无权删除");
        }
        partRepository.deleteById(id);
        log.info("删除配件: id={}", id);
    }

    @Override
    public List<PartInventory> findByCategory(String category) {
        // 注意：此方法未做锁定过滤，因为返回的数据会用于前端展示，需要过滤
        List<PartInventory> list;
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            list = partRepository.findByPartCategory(category);
        } else {
            list = partRepository.findByPartCategory(category).stream()
                    .filter(p -> !p.getIsLocked())
                    .toList();
        }
        return list;
    }

    @Override
    public List<PartInventory> findAvailableParts() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByQuantityGreaterThan(0);
        } else {
            return partRepository.findByQuantityGreaterThan(0).stream()
                    .filter(p -> !p.getIsLocked())
                    .toList();
        }
    }

    @Override
    public List<PartInventory> findBySource(String source) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findBySource(source);
        } else {
            return partRepository.findBySource(source).stream()
                    .filter(p -> !p.getIsLocked())
                    .toList();
        }
    }

    @Override
    public List<PartInventory> findBySourceMachineId(Long machineId) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findBySourceMachineId(machineId);
        } else {
            return partRepository.findBySourceMachineId(machineId).stream()
                    .filter(p -> !p.getIsLocked())
                    .toList();
        }
    }

    @Override
    @Transactional
    public PartInventory inbound(String partCode, int quantity, Long expectedVersion) {
        // 入库操作：普通用户不能操作锁定的配件（但入库通常由管理员操作，这里简单检查）
        PartInventory part = findByPartCodeForUpdate(partCode)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配件编码不存在: " + partCode));
        if (part.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该配件已被锁定，无法入库");
        }
        if (quantity <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "入库数量必须大于0");
        collaborationService.validateWrite(part, expectedVersion);
        part.setQuantity(part.getQuantity() + quantity);
        part.setInboundDate(LocalDateTime.now());
        log.info("配件入库: partCode={}, 增加数量={}, 当前库存={}", partCode, quantity, part.getQuantity());
        collaborationService.stampWrite(part);
        PartInventory saved = partRepository.saveAndFlush(part);
        stockLedgerService.syncBalance(
                StockLedgerService.RESOURCE_PART,
                saved.getId(),
                saved.getWarehouseId(),
                saved.getQuantity()
        );
        return saved;
    }

    @Override
    @Transactional
    public PartInventory outbound(String partCode, int quantity, Long expectedVersion) {
        PartInventory part = findByPartCodeForUpdate(partCode)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配件编码不存在: " + partCode));
        if (part.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该配件已被锁定，无法出库");
        }
        if (quantity <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "出库数量必须大于0");
        if (part.getQuantity() < quantity)
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足，当前库存：" + part.getQuantity());
        collaborationService.validateWrite(part, expectedVersion);
        part.setQuantity(part.getQuantity() - quantity);
        log.info("配件出库: partCode={}, 减少数量={}, 剩余库存={}", partCode, quantity, part.getQuantity());
        collaborationService.stampWrite(part);
        PartInventory saved = partRepository.saveAndFlush(part);
        stockLedgerService.syncBalance(
                StockLedgerService.RESOURCE_PART,
                saved.getId(),
                saved.getWarehouseId(),
                saved.getQuantity()
        );
        return saved;
    }
}
