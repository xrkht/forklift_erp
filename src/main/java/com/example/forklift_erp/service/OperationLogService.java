package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.OperationLogVO;

public interface OperationLogService {
    PageResult<OperationLogVO> findPage(String keyword, Integer page, Integer size);
}
