package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.CustomerDTO;
import com.example.forklift_erp.dto.CustomerVO;

import java.util.List;

public interface CustomerService {
    List<CustomerVO> findAll();

    PageResult<CustomerVO> findPage(String keyword, Integer page, Integer size);

    CustomerVO findById(Long id);

    CustomerVO create(CustomerDTO request);

    CustomerVO update(Long id, CustomerDTO request);

    void delete(Long id, Long version);
}
