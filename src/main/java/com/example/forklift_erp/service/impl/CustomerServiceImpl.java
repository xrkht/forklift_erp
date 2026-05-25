package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.CustomerDTO;
import com.example.forklift_erp.dto.CustomerVO;
import com.example.forklift_erp.entity.Customer;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.CustomerService;
import com.example.forklift_erp.service.OperationAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerVO> findAll() {
        return customerRepository.findAllByOrderByCompanyNameAsc().stream()
                .map(CustomerVO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerVO findById(Long id) {
        return customerRepository.findById(id)
                .map(CustomerVO::fromEntity)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "客户不存在"));
    }

    @Override
    @Transactional
    public CustomerVO create(CustomerDTO request) {
        ensureUniqueName(request.getCompanyName(), null);
        Customer saved = saveCustomer(request.toEntity());
        operationAuditService.record("客户列表", "CREATE", "CUSTOMER", saved.getId(),
                null, saved.getCompanyName(), "新增客户", null, saved.getRemarks());
        return CustomerVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public CustomerVO update(Long id, CustomerDTO request) {
        Customer customer = customerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "客户不存在"));
        collaborationService.validateWrite(customer, request.getVersion());
        ensureUniqueName(request.getCompanyName(), id);
        request.updateEntity(customer);
        Customer saved = saveCustomer(customer);
        operationAuditService.record("客户列表", "UPDATE", "CUSTOMER", saved.getId(),
                null, saved.getCompanyName(), "更新客户", null, saved.getRemarks());
        return CustomerVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long version) {
        Customer customer = customerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "客户不存在"));
        collaborationService.validateWrite(customer, version);
        if (outboundOrderRepository.existsByCustomerId(id)) {
            throw new BusinessException(ResultCode.CONFLICT, "客户已有出库订单，不能删除");
        }
        customerRepository.delete(customer);
        operationAuditService.record("客户列表", "DELETE", "CUSTOMER", id,
                null, customer.getCompanyName(), "删除客户", null, customer.getRemarks());
    }

    private Customer saveCustomer(Customer customer) {
        collaborationService.stampWrite(customer);
        return customerRepository.saveAndFlush(customer);
    }

    private void ensureUniqueName(String companyName, Long currentId) {
        String normalized = companyName == null ? "" : companyName.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "公司名称不能为空");
        }
        boolean duplicated = currentId == null
                ? customerRepository.existsByCompanyName(normalized)
                : customerRepository.existsByCompanyNameAndIdNot(normalized, currentId);
        if (duplicated) {
            throw new BusinessException(ResultCode.DATA_DUPLICATE, "客户名称已存在: " + normalized);
        }
    }
}
