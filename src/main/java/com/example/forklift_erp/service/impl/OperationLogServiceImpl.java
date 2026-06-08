package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.OperationLogVO;
import com.example.forklift_erp.entity.OperationAuditLog;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import com.example.forklift_erp.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<OperationLogVO> findPage(String keyword, Integer page, Integer size) {
        int normalizedPage = com.example.forklift_erp.util.ListPageSupport.page(page);
        int normalizedSize = com.example.forklift_erp.util.ListPageSupport.size(size);
        Page<OperationAuditLog> result = operationAuditLogRepository.searchPage(
                normalizeKeyword(keyword),
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResult.of(
                result.getContent().stream().map(OperationLogVO::fromAuditLog).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
