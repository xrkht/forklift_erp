package com.example.forklift_erp.service;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.StockBalance;
import com.example.forklift_erp.entity.StockMovement;
import com.example.forklift_erp.entity.StockMovementLine;
import com.example.forklift_erp.entity.Warehouse;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.StockBalanceRepository;
import com.example.forklift_erp.repository.StockMovementLineRepository;
import com.example.forklift_erp.repository.StockMovementRepository;
import com.example.forklift_erp.repository.WarehouseRepository;
import com.example.forklift_erp.util.BusinessNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class StockLedgerService {

    public static final String DEFAULT_WAREHOUSE_CODE = "DEFAULT";
    public static final String RESOURCE_MACHINE = "MACHINE";
    public static final String RESOURCE_PART = "PART";

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockMovementLineRepository stockMovementLineRepository;

    @Transactional
    public Long resolveWarehouseId(Long warehouseId) {
        if (warehouseId != null) {
            if (!warehouseRepository.existsById(warehouseId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Warehouse does not exist: " + warehouseId);
            }
            return warehouseId;
        }
        return resolveDefaultWarehouse().getId();
    }

    @Transactional
    public Warehouse resolveDefaultWarehouse() {
        return warehouseRepository.findFirstByDefaultWarehouseTrueOrderByIdAsc()
                .or(() -> warehouseRepository.findByWarehouseCode(DEFAULT_WAREHOUSE_CODE))
                .orElseGet(() -> {
                    Warehouse warehouse = new Warehouse();
                    warehouse.setWarehouseCode(DEFAULT_WAREHOUSE_CODE);
                    warehouse.setWarehouseName("Default Warehouse");
                    warehouse.setWarehouseType("MAIN");
                    warehouse.setDefaultWarehouse(true);
                    return warehouseRepository.save(warehouse);
                });
    }

    @Transactional
    public StockBalance syncBalance(String resourceType, Long resourceId, Long warehouseId, Integer availableQuantity) {
        Long resolvedWarehouseId = resolveWarehouseId(warehouseId);
        int quantity = availableQuantity == null ? 0 : availableQuantity;
        StockBalance balance = stockBalanceRepository
                .findForUpdate(resourceType, resourceId, resolvedWarehouseId)
                .orElseGet(() -> {
                    StockBalance created = new StockBalance();
                    created.setResourceType(resourceType);
                    created.setResourceId(resourceId);
                    created.setWarehouseId(resolvedWarehouseId);
                    created.setReservedQuantity(0);
                    created.setLockedQuantity(0);
                    return created;
                });
        balance.setAvailableQuantity(quantity);
        return stockBalanceRepository.save(balance);
    }

    @Transactional
    public void reconcileAvailableQuantity(
            String resourceType,
            Long resourceId,
            Long preferredWarehouseId,
            Integer expectedTotalQuantity
    ) {
        Long resolvedWarehouseId = resolveWarehouseId(preferredWarehouseId);
        int expectedTotal = expectedTotalQuantity == null ? 0 : expectedTotalQuantity;
        if (expectedTotal < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Inventory quantity cannot be negative");
        }

        List<StockBalance> balances = new ArrayList<>(
                stockBalanceRepository.findAllForUpdate(resourceType, resourceId)
        );
        validateBalances(balances);
        if (balances.isEmpty()) {
            StockBalance balance = newBalance(resourceType, resourceId, resolvedWarehouseId);
            balance.setAvailableQuantity(expectedTotal);
            stockBalanceRepository.save(balance);
            return;
        }

        int currentTotal = balances.stream()
                .mapToInt(balance -> quantity(balance.getAvailableQuantity()))
                .sum();
        int delta = expectedTotal - currentTotal;
        if (delta > 0) {
            StockBalance preferred = findOrCreateBalance(balances, resourceType, resourceId, resolvedWarehouseId);
            preferred.setAvailableQuantity(quantity(preferred.getAvailableQuantity()) + delta);
            stockBalanceRepository.save(preferred);
            return;
        }
        if (delta == 0) {
            return;
        }

        int remaining = -delta;
        List<StockBalance> reductionOrder = balances.stream()
                .sorted(Comparator
                        .comparing((StockBalance balance) -> !resolvedWarehouseId.equals(balance.getWarehouseId()))
                        .thenComparing(StockBalance::getId))
                .toList();
        for (StockBalance balance : reductionOrder) {
            if (remaining == 0) {
                break;
            }
            int available = quantity(balance.getAvailableQuantity());
            int reduction = Math.min(available, remaining);
            if (reduction > 0) {
                balance.setAvailableQuantity(available - reduction);
                remaining -= reduction;
            }
        }
        if (remaining > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "Stock balances are lower than the inventory total change");
        }
        stockBalanceRepository.saveAll(reductionOrder);
    }

    @Transactional
    public void deleteEmptyBalances(String resourceType, Long resourceId) {
        List<StockBalance> balances = stockBalanceRepository.findAllForUpdate(resourceType, resourceId);
        boolean hasQuantity = balances.stream().anyMatch(balance ->
                quantity(balance.getAvailableQuantity()) != 0
                        || quantity(balance.getReservedQuantity()) != 0
                        || quantity(balance.getLockedQuantity()) != 0
        );
        if (hasQuantity) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "Inventory with a non-zero warehouse balance cannot be deleted");
        }
        stockBalanceRepository.deleteAll(balances);
    }

    @Transactional
    public StockMovement transferBalance(
            String resourceType,
            Long resourceId,
            String resourceCode,
            String resourceName,
            Long fromWarehouseId,
            Long toWarehouseId,
            Integer quantity,
            String operator,
            String remark,
            String sourceType,
            Long sourceId
    ) {
        Long resolvedFromWarehouseId = resolveWarehouseId(fromWarehouseId);
        Long resolvedToWarehouseId = resolveWarehouseId(toWarehouseId);
        if (resolvedFromWarehouseId.equals(resolvedToWarehouseId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Source and target warehouse cannot be the same");
        }
        int transferQuantity = quantity == null ? 0 : quantity;
        if (transferQuantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Transfer quantity must be greater than 0");
        }

        StockBalance sourceBalance = findOrCreateBalanceForUpdate(resourceType, resourceId, resolvedFromWarehouseId);
        int sourceBefore = sourceBalance.getAvailableQuantity() == null ? 0 : sourceBalance.getAvailableQuantity();
        if (sourceBefore < transferQuantity) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Insufficient source warehouse stock: " + sourceBefore);
        }

        StockBalance targetBalance = findOrCreateBalanceForUpdate(resourceType, resourceId, resolvedToWarehouseId);
        int targetBefore = targetBalance.getAvailableQuantity() == null ? 0 : targetBalance.getAvailableQuantity();

        sourceBalance.setAvailableQuantity(sourceBefore - transferQuantity);
        targetBalance.setAvailableQuantity(targetBefore + transferQuantity);
        stockBalanceRepository.save(sourceBalance);
        stockBalanceRepository.save(targetBalance);

        StockMovement movement = new StockMovement();
        movement.setMovementNo(nextMovementNo());
        movement.setMovementType("TRANSFER");
        movement.setResourceType(resourceType);
        movement.setSourceType(sourceType);
        movement.setSourceId(sourceId);
        movement.setOperator(operator);
        movement.setRemark(remark);
        StockMovement savedMovement = stockMovementRepository.save(movement);

        StockMovementLine sourceLine = new StockMovementLine();
        sourceLine.setMovementId(savedMovement.getId());
        sourceLine.setResourceType(resourceType);
        sourceLine.setResourceId(resourceId);
        sourceLine.setResourceCode(resourceCode);
        sourceLine.setResourceName(resourceName);
        sourceLine.setWarehouseId(resolvedFromWarehouseId);
        sourceLine.setQuantityDelta(-transferQuantity);
        sourceLine.setBeforeQuantity(sourceBefore);
        sourceLine.setAfterQuantity(sourceBefore - transferQuantity);
        stockMovementLineRepository.save(sourceLine);

        StockMovementLine targetLine = new StockMovementLine();
        targetLine.setMovementId(savedMovement.getId());
        targetLine.setResourceType(resourceType);
        targetLine.setResourceId(resourceId);
        targetLine.setResourceCode(resourceCode);
        targetLine.setResourceName(resourceName);
        targetLine.setWarehouseId(resolvedToWarehouseId);
        targetLine.setQuantityDelta(transferQuantity);
        targetLine.setBeforeQuantity(targetBefore);
        targetLine.setAfterQuantity(targetBefore + transferQuantity);
        stockMovementLineRepository.save(targetLine);

        return savedMovement;
    }

    @Transactional
    public StockMovement recordMovement(
            String movementType,
            String resourceType,
            Long resourceId,
            String resourceCode,
            String resourceName,
            Long warehouseId,
            Integer beforeQuantity,
            Integer afterQuantity,
            String operator,
            String remark,
            String sourceType,
            Long sourceId
    ) {
        return recordMovement(
                movementType,
                resourceType,
                resourceId,
                resourceCode,
                resourceName,
                warehouseId,
                beforeQuantity,
                afterQuantity,
                null,
                operator,
                remark,
                sourceType,
                sourceId
        );
    }

    @Transactional
    public StockMovement recordMovement(
            String movementType,
            String resourceType,
            Long resourceId,
            String resourceCode,
            String resourceName,
            Long warehouseId,
            Integer beforeQuantity,
            Integer afterQuantity,
            BigDecimal unitCost,
            String operator,
            String remark,
            String sourceType,
            Long sourceId
    ) {
        Long resolvedWarehouseId = resolveWarehouseId(warehouseId);
        int before = beforeQuantity == null ? 0 : beforeQuantity;
        int after = afterQuantity == null ? 0 : afterQuantity;
        int delta = after - before;

        reconcileAvailableQuantity(resourceType, resourceId, resolvedWarehouseId, after);

        StockMovement movement = new StockMovement();
        movement.setMovementNo(nextMovementNo());
        movement.setMovementType(movementType);
        movement.setResourceType(resourceType);
        movement.setSourceType(sourceType);
        movement.setSourceId(sourceId);
        movement.setOperator(operator);
        movement.setRemark(remark);
        StockMovement savedMovement = stockMovementRepository.save(movement);

        StockMovementLine line = new StockMovementLine();
        line.setMovementId(savedMovement.getId());
        line.setResourceType(resourceType);
        line.setResourceId(resourceId);
        line.setResourceCode(resourceCode);
        line.setResourceName(resourceName);
        line.setWarehouseId(resolvedWarehouseId);
        line.setQuantityDelta(delta);
        line.setBeforeQuantity(before);
        line.setAfterQuantity(after);
        line.setUnitCost(unitCost);
        stockMovementLineRepository.save(line);

        return savedMovement;
    }

    private StockBalance findOrCreateBalanceForUpdate(String resourceType, Long resourceId, Long warehouseId) {
        return stockBalanceRepository.findForUpdate(resourceType, resourceId, warehouseId)
                .orElseGet(() -> newBalance(resourceType, resourceId, warehouseId));
    }

    private StockBalance findOrCreateBalance(
            List<StockBalance> balances,
            String resourceType,
            Long resourceId,
            Long warehouseId
    ) {
        return balances.stream()
                .filter(balance -> warehouseId.equals(balance.getWarehouseId()))
                .findFirst()
                .orElseGet(() -> {
                    StockBalance created = newBalance(resourceType, resourceId, warehouseId);
                    balances.add(created);
                    return created;
                });
    }

    private StockBalance newBalance(String resourceType, Long resourceId, Long warehouseId) {
        StockBalance created = new StockBalance();
        created.setResourceType(resourceType);
        created.setResourceId(resourceId);
        created.setWarehouseId(warehouseId);
        created.setAvailableQuantity(0);
        created.setReservedQuantity(0);
        created.setLockedQuantity(0);
        return created;
    }

    private int quantity(Integer value) {
        return value == null ? 0 : value;
    }

    private void validateBalances(List<StockBalance> balances) {
        boolean hasNegativeQuantity = balances.stream().anyMatch(balance ->
                quantity(balance.getAvailableQuantity()) < 0
                        || quantity(balance.getReservedQuantity()) < 0
                        || quantity(balance.getLockedQuantity()) < 0
        );
        if (hasNegativeQuantity) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "Warehouse balance contains a negative quantity");
        }
    }

    private String nextMovementNo() {
        return BusinessNumberGenerator.next("SM", 8);
    }
}
