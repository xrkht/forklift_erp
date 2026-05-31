package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.OperationLogVO;

import java.util.List;

public interface OperationLogService {
    List<OperationLogVO> findAll();

    PageResult<OperationLogVO> findPage(String keyword, Integer page, Integer size);
}
