package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.Customer;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.RepairPartUsage;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.StockMovement;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RepairPartUsageRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.repository.UserRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.RepairRecordService;
import com.example.forklift_erp.service.StockLedgerService;
import com.example.forklift_erp.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RepairRecordServiceImpl implements RepairRecordService {
    private static final String REPAIR_STOCK_SOURCE = "REPAIR_RECORD";

    @Autowired
    private RepairRecordRepository repairRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private RepairPartUsageRepository repairPartUsageRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Override
    public List<RepairRecord> findAll() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findAll();
        } else {
            return repairRepository.findAllByIsLockedFalse();
        }
    }

    @Override
    public Optional<RepairRecord> findById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findById(id);
        } else {
            return repairRepository.findByIdAndIsLockedFalse(id);
        }
    }

    @Override
    public Optional<RepairRecord> findByIdForUpdate(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findByIdForUpdate(id);
        } else {
            return repairRepository.findByIdAndIsLockedFalseForUpdate(id);
        }
    }

    @Override
    @Transactional
    public RepairRecord save(RepairRecord record) {
        if (record.getId() != null) {
            Optional<RepairRecord> existingOpt = findById(record.getId());
            if (existingOpt.isPresent()) {
                RepairRecord existing = existingOpt.get();
                if (existing.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "该维修记录已被锁定，您无权修改");
                }
            } else {
                throw new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在");
            }
        }

        if (record.getRepairDate() == null) record.setRepairDate(LocalDateTime.now());
        if (record.getStatus() == null) record.setStatus("PENDING");
        normalizeReferences(record);
        normalizeFees(record);
        log.info("保存维修记录: id={}, vehicleNumber={}, status={}", record.getId(), record.getVehicleNumber(), record.getStatus());
        collaborationService.stampWrite(record);
        RepairRecord saved = repairRepository.saveAndFlush(record);
        reconcilePartUsage(saved);
        return saved;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Optional<RepairRecord> existingOpt = findByIdForUpdate(id);
        if (existingOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在，id=" + id);
        }
        RepairRecord existing = existingOpt.get();
        if (existing.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该维修记录已被锁定，您无权删除");
        }
        reconcilePartUsage(existing, Map.of());
        repairRepository.deleteById(id);
        log.info("删除维修记录: id={}", id);
    }

    @Override
    public List<RepairRecord> findByMachineId(Long machineId) {
        List<RepairRecord> list = repairRepository.findByMachineIdOrderByRepairDateDesc(machineId);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByRepairPerson(String repairPerson) {
        List<RepairRecord> list = repairRepository.findByRepairPerson(repairPerson);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByStatus(String status) {
        List<RepairRecord> list = repairRepository.findByStatus(status);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByDateRange(LocalDateTime start, LocalDateTime end) {
        List<RepairRecord> list = repairRepository.findByRepairDateBetween(start, end);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    private void normalizeReferences(RepairRecord record) {
        if (record.getMachineId() != null) {
            MachineInventory machine = machineRepository.findById(record.getMachineId())
                    .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "维修车辆不存在"));
            if (Boolean.TRUE.equals(machine.getModelOnly())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "维修记录请选择具体库存车号");
            }
            record.setVehicleNumber(machine.getVehicleProductNumber());
        }
        if (record.getCustomerId() != null) {
            Customer customer = customerRepository.findById(record.getCustomerId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修客户不存在"));
            record.setCustomerName(customer.getCompanyName());
            record.setCustomerAddress(customer.getAddress());
        }
        if (record.getCustomerName() == null || record.getCustomerName().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "维修客户不能为空");
        }
        normalizeRepairPerson(record);
        normalizeUsedParts(record);
    }

    private void normalizeRepairPerson(RepairRecord record) {
        if (Boolean.TRUE.equals(record.getRepairExternal())) {
            record.setRepairPersonUserId(null);
            record.setRepairPerson("其他");
            return;
        }
        if (record.getRepairPersonUserId() == null) {
            return;
        }
        User user = userRepository.findById(record.getRepairPersonUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修人员不存在"));
        if (!user.isEnabled() || !"REPAIR".equals(normalizeJobTag(user.getJobTag()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "维修人员必须选择职务标签为维修的用户");
        }
        record.setRepairPerson(user.getUsername());
        record.setRepairExternal(false);
    }

    private String normalizeJobTag(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private void normalizeUsedParts(RepairRecord record) {
        List<Long> ids = parseIds(record.getUsedPartIds());
        if (ids.isEmpty()) {
            record.setUsedPartIds(null);
            if (record.getUsedParts() == null || record.getUsedParts().isBlank()) {
                record.setUsedParts(null);
            }
            return;
        }
        List<PartInventory> parts = ids.stream()
                .map(id -> partRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "使用配件不存在: " + id)))
                .toList();
        record.setUsedPartIds(parts.stream()
                .map(part -> String.valueOf(part.getId()))
                .collect(Collectors.joining(",")));
        record.setUsedParts(parts.stream()
                .map(part -> "%s/%s".formatted(part.getPartCode(), part.getPartName()))
                .collect(Collectors.joining("，")));
    }

    private void normalizeFees(RepairRecord record) {
        BigDecimal repairFee = money(record.getRepairFee());
        BigDecimal repairExpense = Boolean.TRUE.equals(record.getRepairExternal())
                ? money(record.getRepairExpense())
                : BigDecimal.ZERO;
        BigDecimal partsFee = money(record.getPartsFee());
        record.setRepairExpense(repairExpense);
        record.setTotalFee(repairFee.add(repairExpense).add(partsFee));
    }

    private void reconcilePartUsage(RepairRecord record) {
        Map<Long, Integer> desiredQuantities = "COMPLETED".equals(record.getStatus())
                ? partQuantities(record.getUsedPartIds())
                : Map.of();
        reconcilePartUsage(record, desiredQuantities);
    }

    private void reconcilePartUsage(RepairRecord record, Map<Long, Integer> desiredQuantities) {
        if (record.getId() == null) {
            return;
        }
        List<RepairPartUsage> existingUsages = repairPartUsageRepository.findByRepairIdOrderByIdAsc(record.getId());
        Map<Long, List<RepairPartUsage>> usagesByPartId = existingUsages.stream()
                .collect(Collectors.groupingBy(RepairPartUsage::getPartId, LinkedHashMap::new, Collectors.toList()));
        Set<Long> partIds = new java.util.LinkedHashSet<>();
        partIds.addAll(usagesByPartId.keySet());
        partIds.addAll(desiredQuantities.keySet());

        for (Long partId : partIds) {
            List<RepairPartUsage> usages = usagesByPartId.getOrDefault(partId, List.of());
            int currentQuantity = usages.stream()
                    .map(RepairPartUsage::getQuantity)
                    .mapToInt(quantity -> quantity == null ? 0 : quantity)
                    .sum();
            int desiredQuantity = desiredQuantities.getOrDefault(partId, 0);
            int stockDelta = currentQuantity - desiredQuantity;
            PartInventory part = null;

            if (stockDelta != 0 || desiredQuantity > 0) {
                part = partRepository.findByIdForUpdate(partId)
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "使用配件不存在: " + partId));
            }
            syncUsageRows(record, part, usages, desiredQuantity, currentQuantity, stockDelta);
        }
    }

    private void syncUsageRows(
            RepairRecord record,
            PartInventory part,
            List<RepairPartUsage> usages,
            int desiredQuantity,
            int currentQuantity,
            int stockDelta
    ) {
        if (desiredQuantity <= 0) {
            if (stockDelta != 0) {
                applyRepairPartStockDelta(record, part, stockDelta, removedUsageUnitCost(usages, currentQuantity));
            }
            if (!usages.isEmpty()) {
                repairPartUsageRepository.deleteAll(usages);
            }
            return;
        }

        ensureExistingUsageSnapshots(part, usages);

        if (desiredQuantity > currentQuantity) {
            int addedQuantity = desiredQuantity - currentQuantity;
            BigDecimal unitCost = financialCost(part);
            Long movementId = applyRepairPartStockDelta(record, part, stockDelta, unitCost);
            appendUsageRow(record, part, addedQuantity, unitCost, movementId);
            return;
        }

        if (desiredQuantity < currentQuantity) {
            int removedQuantity = currentQuantity - desiredQuantity;
            BigDecimal unitCost = removedUsageUnitCost(usages, removedQuantity);
            applyRepairPartStockDelta(record, part, stockDelta, unitCost);
            trimUsageRows(usages, removedQuantity);
        }
    }

    private void appendUsageRow(
            RepairRecord record,
            PartInventory part,
            int quantity,
            BigDecimal unitCost,
            Long movementId
    ) {
        RepairPartUsage usage = new RepairPartUsage();
        usage.setRepairId(record.getId());
        usage.setPartId(part.getId());
        usage.setPartCode(part.getPartCode());
        usage.setPartName(part.getPartName());
        usage.setQuantity(quantity);
        usage.setUnitPrice(unitCost);
        usage.setStockMovementId(movementId);
        repairPartUsageRepository.save(usage);
    }

    private void ensureExistingUsageSnapshots(PartInventory part, List<RepairPartUsage> usages) {
        if (part == null || usages.isEmpty()) {
            return;
        }
        BigDecimal fallbackCost = financialCost(part);
        for (RepairPartUsage usage : usages) {
            boolean changed = false;
            if (!Objects.equals(usage.getPartCode(), part.getPartCode())) {
                usage.setPartCode(part.getPartCode());
                changed = true;
            }
            if (!Objects.equals(usage.getPartName(), part.getPartName())) {
                usage.setPartName(part.getPartName());
                changed = true;
            }
            if (usage.getUnitPrice() == null) {
                usage.setUnitPrice(fallbackCost);
                changed = true;
            }
            if (changed) {
                repairPartUsageRepository.save(usage);
            }
        }
    }

    private void trimUsageRows(List<RepairPartUsage> usages, int quantityToRemove) {
        int remaining = quantityToRemove;
        for (int index = usages.size() - 1; index >= 0 && remaining > 0; index--) {
            RepairPartUsage usage = usages.get(index);
            int quantity = usage.getQuantity() == null ? 0 : usage.getQuantity();
            if (quantity <= remaining) {
                repairPartUsageRepository.delete(usage);
                remaining -= quantity;
            } else {
                usage.setQuantity(quantity - remaining);
                repairPartUsageRepository.save(usage);
                remaining = 0;
            }
        }
    }

    private BigDecimal removedUsageUnitCost(List<RepairPartUsage> usages, int quantityToRemove) {
        int remaining = quantityToRemove;
        int totalQuantity = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (int index = usages.size() - 1; index >= 0 && remaining > 0; index--) {
            RepairPartUsage usage = usages.get(index);
            int quantity = usage.getQuantity() == null ? 0 : usage.getQuantity();
            int removed = Math.min(quantity, remaining);
            if (removed <= 0) {
                continue;
            }
            BigDecimal unitPrice = money(usage.getUnitPrice());
            totalCost = totalCost.add(unitPrice.multiply(BigDecimal.valueOf(removed)));
            totalQuantity += removed;
            remaining -= removed;
        }
        if (totalQuantity == 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.divide(BigDecimal.valueOf(totalQuantity), 2, RoundingMode.HALF_UP);
    }

    private Long applyRepairPartStockDelta(RepairRecord record, PartInventory part, int stockDelta, BigDecimal unitCostSnapshot) {
        int beforeQuantity = part.getQuantity() == null ? 0 : part.getQuantity();
        int afterQuantity = beforeQuantity + stockDelta;
        if (afterQuantity < 0) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK,
                    "配件库存不足: " + part.getPartName() + "，当前库存：" + beforeQuantity);
        }
        part.setQuantity(afterQuantity);
        collaborationService.stampWrite(part);
        PartInventory savedPart = partRepository.saveAndFlush(part);

        String operationType = stockDelta < 0 ? "OUTBOUND" : "INBOUND";
        int quantity = Math.abs(stockDelta);
        BigDecimal unitCost = money(unitCostSnapshot);
        String remark = ("%s；维修单ID=%d").formatted(
                "OUTBOUND".equals(operationType) ? "维修配件出库" : "维修配件回库",
                record.getId()
        );

        StockOperationLog stockLog = new StockOperationLog();
        stockLog.setResourceType(StockLedgerService.RESOURCE_PART);
        stockLog.setOperationType(operationType);
        stockLog.setResourceId(savedPart.getId());
        stockLog.setResourceCode(savedPart.getPartCode());
        stockLog.setResourceName(savedPart.getPartName());
        stockLog.setQuantity(quantity);
        stockLog.setBeforeQuantity(beforeQuantity);
        stockLog.setAfterQuantity(afterQuantity);
        stockLog.setUnitCost(unitCost);
        stockLog.setOperator(record.getRepairPerson());
        stockLog.setRemark(remark);
        stockLog.setSourceType(REPAIR_STOCK_SOURCE);
        stockLog.setSourceId(record.getId());
        StockOperationLog savedLog = stockOperationLogRepository.save(stockLog);

        StockMovement movement = stockLedgerService.recordMovement(
                operationType,
                StockLedgerService.RESOURCE_PART,
                savedPart.getId(),
                savedPart.getPartCode(),
                savedPart.getPartName(),
                savedPart.getWarehouseId(),
                beforeQuantity,
                afterQuantity,
                record.getRepairPerson(),
                remark,
                REPAIR_STOCK_SOURCE,
                record.getId(),
                unitCost
        );
        operationAuditService.record("配件出入库", operationType, "PART", savedPart.getId(),
                savedPart.getPartCode(), savedPart.getPartName(),
                ("OUTBOUND".equals(operationType) ? "维修配件出库 " : "维修配件回库 ") + quantity,
                record.getRepairPerson(), remark, "STOCK", savedLog.getId());
        log.info("{}: repairId={}, partCode={}, quantity={}, before={}, after={}",
                operationType, record.getId(), savedPart.getPartCode(), quantity, beforeQuantity, afterQuantity);
        return movement.getId();
    }

    private Map<Long, Integer> partQuantities(String ids) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (Long id : parseIds(ids)) {
            quantities.merge(id, 1, Integer::sum);
        }
        return quantities;
    }

    private BigDecimal financialCost(PartInventory part) {
        if (part.getSettlementPrice() != null) {
            return part.getSettlementPrice();
        }
        return part.getPurchasePrice() == null ? BigDecimal.ZERO : part.getPurchasePrice();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "使用配件ID格式非法: " + value);
                    }
                })
                .toList();
    }
}
