package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.StockMovementVO;
import com.example.forklift_erp.entity.StockMovement;
import com.example.forklift_erp.entity.StockMovementLine;
import com.example.forklift_erp.entity.Warehouse;
import com.example.forklift_erp.repository.StockMovementLineRepository;
import com.example.forklift_erp.repository.StockMovementRepository;
import com.example.forklift_erp.repository.WarehouseRepository;
import com.example.forklift_erp.util.ListPageSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockMovementService {
    @Autowired
    private StockMovementLineRepository lineRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public PageResult<StockMovementVO> findPage(String keyword, String resourceType, String movementType,
                                                Long warehouseId, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<StockMovementLine> result = lineRepository.searchPage(
                normalize(keyword),
                normalize(resourceType),
                normalize(movementType),
                warehouseId,
                ListPageSupport.pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<Long, StockMovement> movements = movementRepository.findByIdIn(
                result.getContent().stream().map(StockMovementLine::getMovementId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(StockMovement::getId, Function.identity()));
        Map<Long, Warehouse> warehouses = warehouseRepository.findAllById(
                result.getContent().stream().map(StockMovementLine::getWarehouseId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        return PageResult.of(
                result.getContent().stream()
                        .map(line -> toVO(line, movements.get(line.getMovementId()), warehouses.get(line.getWarehouseId())))
                        .toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    private StockMovementVO toVO(StockMovementLine line, StockMovement movement, Warehouse warehouse) {
        StockMovementVO vo = new StockMovementVO();
        vo.setId(line.getId());
        vo.setMovementId(line.getMovementId());
        vo.setMovementNo(movement == null ? null : movement.getMovementNo());
        vo.setMovementType(movement == null ? null : movement.getMovementType());
        vo.setResourceType(line.getResourceType());
        vo.setResourceId(line.getResourceId());
        vo.setResourceCode(line.getResourceCode());
        vo.setResourceName(line.getResourceName());
        vo.setWarehouseId(line.getWarehouseId());
        vo.setWarehouseName(warehouse == null ? null : warehouse.getWarehouseName());
        vo.setQuantityDelta(line.getQuantityDelta());
        vo.setBeforeQuantity(line.getBeforeQuantity());
        vo.setAfterQuantity(line.getAfterQuantity());
        vo.setUnitCost(line.getUnitCost());
        vo.setSourceType(movement == null ? null : movement.getSourceType());
        vo.setSourceId(movement == null ? null : movement.getSourceId());
        vo.setOperator(movement == null ? null : movement.getOperator());
        vo.setRemark(movement == null ? null : movement.getRemark());
        vo.setCreatedAt(line.getCreatedAt());
        return vo;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
