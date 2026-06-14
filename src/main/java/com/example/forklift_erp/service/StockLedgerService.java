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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class StockLedgerService {

    public static final String DEFAULT_WAREHOUSE_CODE = "DEFAULT";
    public static final String RESOURCE_MACHINE = "MACHINE";
    public static final String RESOURCE_PART = "PART";

    private static final DateTimeFormatter MOVEMENT_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

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
                    return warehouseRepository.saveAndFlush(warehouse);
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
        return stockBalanceRepository.saveAndFlush(balance);
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
                operator,
                remark,
                sourceType,
                sourceId,
                null
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
            String operator,
            String remark,
            String sourceType,
            Long sourceId,
            BigDecimal unitCost
    ) {
        Long resolvedWarehouseId = resolveWarehouseId(warehouseId);
        int before = beforeQuantity == null ? 0 : beforeQuantity;
        int after = afterQuantity == null ? 0 : afterQuantity;
        int delta = after - before;

        syncBalance(resourceType, resourceId, resolvedWarehouseId, after);

        StockMovement movement = new StockMovement();
        movement.setMovementNo(nextMovementNo());
        movement.setMovementType(movementType);
        movement.setResourceType(resourceType);
        movement.setSourceType(sourceType);
        movement.setSourceId(sourceId);
        movement.setOperator(operator);
        movement.setRemark(remark);
        StockMovement savedMovement = stockMovementRepository.saveAndFlush(movement);

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
        stockMovementLineRepository.saveAndFlush(line);

        return savedMovement;
    }

    private String nextMovementNo() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return "SM-" + MOVEMENT_NO_TIME.format(LocalDateTime.now()) + "-" + suffix;
    }
}
