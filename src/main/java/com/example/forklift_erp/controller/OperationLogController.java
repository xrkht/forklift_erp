package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.OperationLogVO;
import com.example.forklift_erp.entity.OperationAuditLog;
import com.example.forklift_erp.repository.ConfigReplaceLogRepository;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.util.ListPageSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/logs")
public class OperationLogController {

    @Autowired
    private ConfigReplaceLogRepository replaceLogRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'log:read')")
    public Result<?> getAll(@RequestParam(defaultValue = "false") boolean paged,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        List<OperationAuditLog> auditLogs = operationAuditLogRepository.findAllByOrderByCreatedAtDesc();
        Set<String> auditedSources = new HashSet<>();
        auditLogs.stream()
                .filter(log -> log.getSourceType() != null && log.getSourceId() != null)
                .forEach(log -> auditedSources.add(sourceKey(log.getSourceType(), log.getSourceId())));

        List<OperationLogVO> logs = Stream.of(
                        auditLogs.stream().map(OperationLogVO::fromAuditLog),
                        replaceLogRepository.findAll().stream()
                                .filter(log -> !auditedSources.contains(sourceKey("REPLACE", log.getId())))
                                .map(OperationLogVO::fromReplaceLog),
                        repairRecordRepository.findAll().stream()
                                .filter(record -> !auditedSources.contains(sourceKey("REPAIR", record.getId())))
                                .map(OperationLogVO::fromRepairRecord),
                        stockOperationLogRepository.findAllByOrderByCreatedAtDesc().stream()
                                .filter(log -> !auditedSources.contains(sourceKey("STOCK", log.getId())))
                                .map(OperationLogVO::fromStockLog)
                )
                .flatMap(stream -> stream)
                .sorted(Comparator.comparing(OperationLogVO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (paged) {
            List<OperationLogVO> filtered = ListPageSupport.filter(logs, keyword, row -> ListPageSupport.text(
                    row.getCategory(),
                    row.getAction(),
                    row.getTarget(),
                    row.getSummary(),
                    row.getOperator(),
                    row.getRemark()
            ));
            return Result.success(ListPageSupport.page(filtered, page, size));
        }
        return Result.success(logs);
    }

    private String sourceKey(String sourceType, Long sourceId) {
        return sourceType + ":" + sourceId;
    }
}
