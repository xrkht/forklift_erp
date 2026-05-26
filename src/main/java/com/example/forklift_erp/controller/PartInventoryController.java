package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.PartInventoryCreateDTO;
import com.example.forklift_erp.dto.PartInventoryVO;
import com.example.forklift_erp.dto.PartStockAdjustRequestDTO;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.PartInventoryService;
import com.example.forklift_erp.service.StockLedgerService;
import com.example.forklift_erp.util.ListPageSupport;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/parts")
public class PartInventoryController {

    @Autowired
    private PartInventoryService partService;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @GetMapping
    public Result<?> getAll(@RequestParam(defaultValue = "false") boolean paged,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        List<PartInventoryVO> list = partService.findAll().stream()
                .map(PartInventoryVO::fromEntity)
                .collect(Collectors.toList());
        if (paged) {
            List<PartInventoryVO> filtered = ListPageSupport.filter(list, keyword, row -> ListPageSupport.text(
                    row.getPartCode(),
                    row.getPartName(),
                    row.getPartBrand(),
                    row.getSpecification(),
                    row.getPartCategory(),
                    row.getApplicableModels(),
                    row.getSource(),
                    row.getRemarks()
            ));
            return Result.success(ListPageSupport.page(filtered, page, size));
        }
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<PartInventoryVO> getById(@PathVariable Long id) {
        return partService.findById(id)
                .map(PartInventoryVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "配件不存在"));
    }

    @GetMapping("/code/{partCode}")
    public Result<PartInventoryVO> getByPartCode(@PathVariable String partCode) {
        return partService.findByPartCode(partCode)
                .map(PartInventoryVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "配件编码不存在"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'part:write')")
    @Transactional
    public Result<PartInventoryVO> create(@Valid @RequestBody PartInventoryCreateDTO dto) {
        PartInventory saved = partService.save(dto.toEntity());
        int quantity = saved.getQuantity() == null ? 0 : saved.getQuantity();
        if (quantity > 0) {
            saveStockLog(saved, "INITIAL", quantity, 0, quantity, null, "Initial part stock");
        }
        operationAuditService.record("配件档案", "CREATE", "PART", saved.getId(),
                saved.getPartCode(), saved.getPartName(), "新增配件档案", null, saved.getRemarks());
        return Result.success("新增成功", PartInventoryVO.fromEntity(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'part:write')")
    @Transactional
    public Result<PartInventoryVO> update(@PathVariable Long id, @Valid @RequestBody PartInventoryCreateDTO dto) {
        PartInventory part = partService.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND));
        collaborationService.validateWrite(part, dto.getVersion());
        int beforeQuantity = part.getQuantity() == null ? 0 : part.getQuantity();
        dto.updateEntity(part);
        PartInventory saved = partService.save(part);
        int afterQuantity = saved.getQuantity() == null ? 0 : saved.getQuantity();
        if (beforeQuantity != afterQuantity) {
            saveStockLog(saved, "ADJUST", Math.abs(afterQuantity - beforeQuantity),
                    beforeQuantity, afterQuantity, null, "Part stock adjusted from profile edit");
        }
        operationAuditService.record("配件档案", "UPDATE", "PART", saved.getId(),
                saved.getPartCode(), saved.getPartName(), "更新配件档案", null, saved.getRemarks());
        return Result.success("更新成功", PartInventoryVO.fromEntity(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'part:write')")
    @Transactional
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long version) {
        PartInventory part = partService.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND));
        collaborationService.validateWrite(part, version);
        partService.deleteById(id);
        operationAuditService.record("配件档案", "DELETE", "PART", part.getId(),
                part.getPartCode(), part.getPartName(), "删除配件档案", null, part.getRemarks());
        return Result.success("删除成功");
    }

    @GetMapping("/category/{category}")
    public Result<List<PartInventoryVO>> getByCategory(@PathVariable String category) {
        List<PartInventoryVO> list = partService.findByCategory(category).stream()
                .map(PartInventoryVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/available")
    public Result<List<PartInventoryVO>> getAvailable() {
        List<PartInventoryVO> list = partService.findAvailableParts().stream()
                .map(PartInventoryVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/source/{source}")
    public Result<List<PartInventoryVO>> getBySource(@PathVariable String source) {
        List<PartInventoryVO> list = partService.findBySource(source).stream()
                .map(PartInventoryVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/sourceMachine/{machineId}")
    public Result<List<PartInventoryVO>> getBySourceMachineId(@PathVariable Long machineId) {
        List<PartInventoryVO> list = partService.findBySourceMachineId(machineId).stream()
                .map(PartInventoryVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @PutMapping("/inbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    @Transactional
    public Result<PartInventoryVO> inbound(@Valid @RequestBody PartStockAdjustRequestDTO request) {
        PartInventory part = partService.inbound(request.getPartCode(), request.getQuantity(), request.getVersion());
        saveStockLog(part, "INBOUND", request.getQuantity(), part.getQuantity() - request.getQuantity(),
                part.getQuantity(), request.getOperator(), request.getRemark());
        return Result.success("入库成功", PartInventoryVO.fromEntity(part));
    }

    @PutMapping("/outbound")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    @Transactional
    public Result<PartInventoryVO> outbound(@Valid @RequestBody PartStockAdjustRequestDTO request) {
        PartInventory part = partService.outbound(request.getPartCode(), request.getQuantity(), request.getVersion());
        saveStockLog(part, "OUTBOUND", request.getQuantity(), part.getQuantity() + request.getQuantity(),
                part.getQuantity(), request.getOperator(), request.getRemark());
        return Result.success("出库成功", PartInventoryVO.fromEntity(part));
    }

    private StockOperationLog saveStockLog(PartInventory part, String operationType, Integer quantity,
                              Integer beforeQuantity, Integer afterQuantity, String operator, String remark) {
        StockOperationLog stockLog = new StockOperationLog();
        stockLog.setResourceType("PART");
        stockLog.setOperationType(operationType);
        stockLog.setResourceId(part.getId());
        stockLog.setResourceCode(part.getPartCode());
        stockLog.setResourceName(part.getPartName());
        stockLog.setQuantity(quantity);
        stockLog.setBeforeQuantity(beforeQuantity);
        stockLog.setAfterQuantity(afterQuantity);
        stockLog.setOperator(operator);
        stockLog.setRemark(remark);
        StockOperationLog savedLog = stockOperationLogRepository.save(stockLog);
        stockLedgerService.recordMovement(
                operationType,
                StockLedgerService.RESOURCE_PART,
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getWarehouseId(),
                beforeQuantity,
                afterQuantity,
                operator,
                remark,
                "STOCK_LOG",
                savedLog.getId()
        );
        operationAuditService.record("配件出入库", operationType, "PART", part.getId(),
                part.getPartCode(), part.getPartName(),
                ("INBOUND".equals(operationType) ? "配件入库 " : "配件出库 ") + quantity,
                operator, remark, "STOCK", savedLog.getId());
        return savedLog;
    }
}
