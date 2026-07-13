package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.PartInventoryCreateDTO;
import com.example.forklift_erp.dto.PartInventoryVO;
import com.example.forklift_erp.dto.PartStockAdjustRequestDTO;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.PartInventoryService;
import com.example.forklift_erp.service.ResourceVisibilityPolicy;
import com.example.forklift_erp.service.StockLedgerService;
import com.example.forklift_erp.util.InventoryQuantities;
import com.example.forklift_erp.util.ListPageSupport;
import com.example.forklift_erp.util.MoneyValues;
import com.example.forklift_erp.util.SearchKeywordSupport;
import com.example.forklift_erp.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private StockOperationRecorder stockOperationRecorder;

    @Autowired
    private ResourceVisibilityPolicy visibilityPolicy;

    @Override
    public List<PartInventory> findAll() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findAll();
        }
        return partRepository.findAllByIsLockedFalse();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PartInventoryVO> findPage(String keyword, String stock, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<PartInventory> result = partRepository.searchPage(
                SearchKeywordSupport.likePrefix(keyword),
                SearchKeywordSupport.fullTextBoolean(keyword),
                SecurityUtils.isAdminOrSuperAdmin(),
                stock == null || stock.isBlank() ? null : stock.trim(),
                5,
                ListPageSupport.pageRequest(page, size)
        );
        return PageResult.of(
                result.getContent().stream().map(PartInventoryVO::fromEntity).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    @Override
    public Optional<PartInventory> findById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findById(id);
        }
        return partRepository.findByIdAndIsLockedFalse(id);
    }

    @Override
    public Optional<PartInventory> findByIdForUpdate(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByIdForUpdate(id);
        }
        return partRepository.findByIdAndIsLockedFalseForUpdate(id);
    }

    @Override
    public Optional<PartInventory> findByPartCode(String partCode) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByPartCode(partCode);
        }
        return partRepository.findByPartCodeAndIsLockedFalse(partCode);
    }

    @Override
    public Optional<PartInventory> findByPartCodeForUpdate(String partCode) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByPartCodeForUpdate(partCode);
        }
        return partRepository.findByPartCodeAndIsLockedFalseForUpdate(partCode);
    }

    @Override
    @Transactional
    public PartInventory save(PartInventory part) {
        if (part.getId() != null) {
            Optional<PartInventory> existingOpt = findById(part.getId());
            if (existingOpt.isPresent()) {
                PartInventory existing = existingOpt.get();
                visibilityPolicy.ensureWritable(existing.getIsLocked(), "Part is locked and cannot be modified");
            } else {
                throw new BusinessException(ResultCode.NOT_FOUND, "Part not found");
            }
        }

        if (part.getId() == null) {
            Optional<PartInventory> exist = partRepository.findByPartCode(part.getPartCode());
            if (exist.isPresent()) {
                throw new BusinessException(ResultCode.DATA_DUPLICATE, "Part code already exists: " + part.getPartCode());
            }
        } else {
            Optional<PartInventory> exist = partRepository.findByPartCode(part.getPartCode());
            if (exist.isPresent() && !exist.get().getId().equals(part.getId())) {
                throw new BusinessException(ResultCode.DATA_DUPLICATE, "Part code is already used: " + part.getPartCode());
            }
        }

        if (part.getQuantity() == null) {
            part.setQuantity(0);
        }
        InventoryQuantities.requireNonNegative(part.getQuantity(), "Part quantity cannot be negative");
        if (part.getWarehouseId() == null) {
            part.setWarehouseId(stockLedgerService.resolveWarehouseId(null));
        }
        part.setPurchasePrice(MoneyValues.zeroIfNegative(part.getPurchasePrice()));
        part.setSalePrice(MoneyValues.zeroIfNegative(part.getSalePrice()));
        part.setSettlementPrice(MoneyValues.zeroIfNegative(part.getSettlementPrice()));
        log.info("Save part: partCode={}, name={}, quantity={}", part.getPartCode(), part.getPartName(), part.getQuantity());
        collaborationService.stampWrite(part);
        PartInventory saved = partRepository.save(part);
        stockLedgerService.reconcileAvailableQuantity(
                StockLedgerService.RESOURCE_PART,
                saved.getId(),
                saved.getWarehouseId(),
                saved.getQuantity()
        );
        return saved;
    }

    @Override
    @Transactional
    public PartInventoryVO create(PartInventoryCreateDTO dto) {
        PartInventory saved = save(dto.toEntity());
        int quantity = saved.getQuantity() == null ? 0 : saved.getQuantity();
        if (quantity > 0) {
            saveStockLog(saved, "INITIAL", quantity, 0, quantity, null, "Initial part stock");
        }
        operationAuditService.record("Part", "CREATE", "PART", saved.getId(),
                saved.getPartCode(), saved.getPartName(), "Create part", null, saved.getRemarks());
        return PartInventoryVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public PartInventoryVO update(Long id, PartInventoryCreateDTO dto) {
        PartInventory part = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND));
        collaborationService.validateWrite(part, dto.getVersion());
        int beforeQuantity = part.getQuantity() == null ? 0 : part.getQuantity();
        dto.updateEntity(part);
        PartInventory saved = save(part);
        int afterQuantity = saved.getQuantity() == null ? 0 : saved.getQuantity();
        if (beforeQuantity != afterQuantity) {
            saveStockLog(saved, "ADJUST", Math.abs(afterQuantity - beforeQuantity),
                    beforeQuantity, afterQuantity, null, "Part stock adjusted from profile edit");
        }
        operationAuditService.record("Part", "UPDATE", "PART", saved.getId(),
                saved.getPartCode(), saved.getPartName(), "Update part", null, saved.getRemarks());
        return PartInventoryVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long version) {
        PartInventory part = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND));
        collaborationService.validateWrite(part, version);
        deleteById(id);
        operationAuditService.record("Part", "DELETE", "PART", part.getId(),
                part.getPartCode(), part.getPartName(), "Delete part", null, part.getRemarks());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Optional<PartInventory> existingOpt = findByIdForUpdate(id);
        if (existingOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Part not found, id=" + id);
        }
        PartInventory existing = existingOpt.get();
        visibilityPolicy.ensureWritable(existing.getIsLocked(), "Part is locked and cannot be deleted");
        stockLedgerService.deleteEmptyBalances(StockLedgerService.RESOURCE_PART, id);
        partRepository.deleteById(id);
        log.info("Delete part: id={}", id);
    }

    @Override
    public List<PartInventory> findByCategory(String category) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByPartCategory(category);
        }
        return partRepository.findByPartCategoryAndIsLockedFalse(category);
    }

    @Override
    public List<PartInventory> findAvailableParts() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findByQuantityGreaterThan(0);
        }
        return partRepository.findByQuantityGreaterThanAndIsLockedFalse(0);
    }

    @Override
    public List<PartInventory> findBySource(String source) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findBySource(source);
        }
        return partRepository.findBySourceAndIsLockedFalse(source);
    }

    @Override
    public List<PartInventory> findBySourceMachineId(Long machineId) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return partRepository.findBySourceMachineId(machineId);
        }
        return partRepository.findBySourceMachineIdAndIsLockedFalse(machineId);
    }

    @Override
    @Transactional
    public PartInventory inbound(String partCode, int quantity, Long expectedVersion) {
        PartInventory part = findByPartCodeForUpdate(partCode)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Part code not found: " + partCode));
        visibilityPolicy.ensureWritable(part.getIsLocked(), "Part is locked and cannot be inbounded");
        collaborationService.validateWrite(part, expectedVersion);
        InventoryQuantities.QuantityChange change = InventoryQuantities.inbound(
                part.getQuantity(),
                quantity,
                "Inbound quantity must be greater than 0"
        );
        part.setQuantity(change.afterQuantity());
        part.setInboundDate(LocalDateTime.now());
        log.info("Part inbound: partCode={}, quantity={}, currentQuantity={}", partCode, quantity, part.getQuantity());
        collaborationService.stampWrite(part);
        PartInventory saved = partRepository.save(part);
        stockLedgerService.reconcileAvailableQuantity(
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
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Part code not found: " + partCode));
        visibilityPolicy.ensureWritable(part.getIsLocked(), "Part is locked and cannot be outbounded");
        collaborationService.validateWrite(part, expectedVersion);
        InventoryQuantities.QuantityChange change = InventoryQuantities.outbound(
                part.getQuantity(),
                quantity,
                "Outbound quantity must be greater than 0",
                "Insufficient stock: "
        );
        part.setQuantity(change.afterQuantity());
        log.info("Part outbound: partCode={}, quantity={}, currentQuantity={}", partCode, quantity, part.getQuantity());
        collaborationService.stampWrite(part);
        PartInventory saved = partRepository.save(part);
        stockLedgerService.reconcileAvailableQuantity(
                StockLedgerService.RESOURCE_PART,
                saved.getId(),
                saved.getWarehouseId(),
                saved.getQuantity()
        );
        return saved;
    }

    @Override
    @Transactional
    public PartInventoryVO inbound(PartStockAdjustRequestDTO request) {
        PartInventory part = inbound(request.getPartCode(), request.getQuantity(), request.getVersion());
        saveStockLog(part, "INBOUND", request.getQuantity(), part.getQuantity() - request.getQuantity(),
                part.getQuantity(), request.getOperator(), request.getRemark());
        return PartInventoryVO.fromEntity(part);
    }

    @Override
    @Transactional
    public PartInventoryVO outbound(PartStockAdjustRequestDTO request) {
        PartInventory part = outbound(request.getPartCode(), request.getQuantity(), request.getVersion());
        saveStockLog(part, "OUTBOUND", request.getQuantity(), part.getQuantity() + request.getQuantity(),
                part.getQuantity(), request.getOperator(), request.getRemark());
        return PartInventoryVO.fromEntity(part);
    }

    private StockOperationLog saveStockLog(PartInventory part, String operationType, Integer quantity,
                                           Integer beforeQuantity, Integer afterQuantity, String operator, String remark) {
        BigDecimal unitCost = stockUnitCost(part);
        return stockOperationRecorder.recordPart(part, operationType, quantity,
                beforeQuantity, afterQuantity, unitCost, operator, remark);
    }

    private BigDecimal stockUnitCost(PartInventory part) {
        return MoneyValues.firstNonNegativeOrZero(part.getSettlementPrice(), part.getPurchasePrice());
    }

}
