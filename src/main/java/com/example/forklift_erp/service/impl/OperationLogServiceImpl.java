package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.OperationLogVO;
import com.example.forklift_erp.entity.OperationAuditLog;
import com.example.forklift_erp.repository.ConfigReplaceLogRepository;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.service.OperationLogService;
import com.example.forklift_erp.util.ListPageSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private ConfigReplaceLogRepository replaceLogRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public List<OperationLogVO> findAll() {
        List<OperationAuditLog> auditLogs = operationAuditLogRepository.findAllByOrderByCreatedAtDesc();
        Set<String> auditedSources = new HashSet<>();
        auditLogs.stream()
                .filter(log -> log.getSourceType() != null && log.getSourceId() != null)
                .forEach(log -> auditedSources.add(sourceKey(log.getSourceType(), log.getSourceId())));

        return Stream.of(
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
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OperationLogVO> findPage(String keyword, Integer page, Integer size) {
        List<OperationLogVO> filtered = ListPageSupport.filter(findAll(), keyword, row -> ListPageSupport.text(
                row.getCategory(),
                row.getAction(),
                row.getTarget(),
                row.getSummary(),
                row.getOperator(),
                row.getRemark()
        ));
        return ListPageSupport.page(filtered, page, size);
    }

    private String sourceKey(String sourceType, Long sourceId) {
        return sourceType + ":" + sourceId;
    }
}
