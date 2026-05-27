package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.RentalRecordCreateDTO;
import com.example.forklift_erp.dto.RentalRecordUpdateDTO;
import com.example.forklift_erp.dto.RentalRecordVO;
import com.example.forklift_erp.entity.Customer;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.RentalRecordService;
import com.example.forklift_erp.util.ListPageSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RentalRecordServiceImpl implements RentalRecordService {
    private static final String SOURCE_TYPE = "RENTAL_RECORD";
    private static final DateTimeFormatter RENTAL_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Autowired
    private RentalRecordRepository rentalRecordRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Override
    @Transactional(readOnly = true)
    public List<RentalRecordVO> findAll() {
        return rentalRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(RentalRecordVO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RentalRecordVO> findPage(String keyword, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<RentalRecord> records = rentalRecordRepository.searchPage(
                normalizedKeyword,
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResult.of(
                records.getContent().stream().map(RentalRecordVO::fromEntity).toList(),
                records.getNumber(),
                records.getSize(),
                records.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RentalRecordVO findById(Long id) {
        return rentalRecordRepository.findById(id)
                .map(RentalRecordVO::fromEntity)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "租赁记录不存在"));
    }

    @Override
    @Transactional
    public RentalRecordVO create(RentalRecordCreateDTO request) {
        MachineInventory machine = machineRepository.findByIdForUpdate(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "租赁车辆不存在"));
        if (Boolean.TRUE.equals(machine.getModelOnly())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "车型模板不能直接登记租赁，请选择具体库存车号");
        }
        if (Boolean.TRUE.equals(machine.getIsLocked())) {
            throw new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "租赁车辆不存在或已锁定");
        }
        int inventoryCount = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        if (inventoryCount < 1 || "OUTBOUND".equals(machine.getStockStatus())) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "车辆不在库，不能登记租赁");
        }
        if (rentalRecordRepository.existsByMachineIdAndStatus(machine.getId(), RentalRecord.STATUS_ACTIVE)) {
            throw new BusinessException(ResultCode.CONFLICT, "该车辆已有进行中的租赁记录");
        }
        collaborationService.validateWrite(machine, request.getMachineVersion());

        RentalRecord record = new RentalRecord();
        record.setRentalNo(nextRentalNo());
        copyMachine(record, machine);
        copyCustomer(record, request.getCustomerId(), request.getDestination());
        BigDecimal monthlyPrice = resolveMonthlyPrice(request.getMonthlyRentalPrice(), request.getRentalPrice());
        record.setMonthlyRentalPrice(monthlyPrice);
        record.setRentalPrice(monthlyPrice);
        record.setStartDate(request.getStartDate() == null ? LocalDate.now() : request.getStartDate());
        record.setEndDate(request.getEndDate());
        record.setStatus(RentalRecord.STATUS_ACTIVE);
        record.setOperator(blankToNull(request.getOperator()));
        record.setRemark(blankToNull(request.getRemark()));
        collaborationService.stampWrite(record);
        RentalRecord saved = rentalRecordRepository.saveAndFlush(record);

        operationAuditService.record("租赁管理", "CREATE", "RENTAL_RECORD", saved.getId(),
                saved.getRentalNo(), saved.getVehicleNumber(),
                "新增车辆租赁记录：" + saved.getDestination(), saved.getOperator(), saved.getRemark(),
                SOURCE_TYPE, saved.getId());
        return RentalRecordVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public RentalRecordVO update(Long id, RentalRecordUpdateDTO request) {
        RentalRecord record = rentalRecordRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "租赁记录不存在"));
        collaborationService.validateWrite(record, request.getVersion());

        RentalRecord before = new RentalRecord();
        before.setStatus(record.getStatus());

        copyCustomer(record, request.getCustomerId(), request.getDestination());
        BigDecimal monthlyPrice = resolveMonthlyPrice(request.getMonthlyRentalPrice(), request.getRentalPrice());
        record.setMonthlyRentalPrice(monthlyPrice);
        record.setRentalPrice(monthlyPrice);
        record.setStartDate(request.getStartDate());
        record.setEndDate(request.getEndDate());
        record.setStatus(normalizeStatus(request.getStatus()));
        record.setOperator(blankToNull(request.getOperator()));
        record.setRemark(blankToNull(request.getRemark()));
        collaborationService.stampWrite(record);
        RentalRecord saved = rentalRecordRepository.saveAndFlush(record);

        operationAuditService.record("租赁管理", "UPDATE", "RENTAL_RECORD", saved.getId(),
                saved.getRentalNo(), saved.getVehicleNumber(),
                "更新车辆租赁记录：" + before.getStatus() + " -> " + saved.getStatus(),
                saved.getOperator(), saved.getRemark(), SOURCE_TYPE, saved.getId());
        return RentalRecordVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long version) {
        RentalRecord record = rentalRecordRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "租赁记录不存在"));
        collaborationService.validateWrite(record, version);
        rentalRecordRepository.delete(record);
        operationAuditService.record("租赁管理", "DELETE", "RENTAL_RECORD", record.getId(),
                record.getRentalNo(), record.getVehicleNumber(),
                "删除车辆租赁记录", record.getOperator(), record.getRemark(), SOURCE_TYPE, record.getId());
    }

    private void copyMachine(RentalRecord record, MachineInventory machine) {
        record.setMachineId(machine.getId());
        record.setVehicleNumber(machine.getVehicleProductNumber());
        record.setMachineName(machine.getName());
        record.setSpecificationModel(machine.getSpecificationModel());
    }

    private void copyCustomer(RentalRecord record, Long customerId, String destination) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "租赁客户不存在"));
        record.setCustomerId(customer.getId());
        record.setCustomerName(customer.getCompanyName());
        record.setCustomerAddress(customer.getAddress());
        String resolvedDestination = blankToNull(destination);
        if (resolvedDestination == null) {
            resolvedDestination = blankToNull(customer.getAddress());
        }
        if (resolvedDestination == null) {
            resolvedDestination = customer.getCompanyName();
        }
        record.setDestination(resolvedDestination);
    }

    private BigDecimal resolveMonthlyPrice(BigDecimal monthlyRentalPrice, BigDecimal legacyRentalPrice) {
        BigDecimal price = monthlyRentalPrice != null ? monthlyRentalPrice : legacyRentalPrice;
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "月租价格必须大于0");
        }
        return price;
    }

    private String normalizeStatus(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return RentalRecord.STATUS_ACTIVE;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (RentalRecord.STATUS_ACTIVE.equals(upper) || RentalRecord.STATUS_RETURNED.equals(upper)) {
            return upper;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "租赁状态仅支持 ACTIVE 或 RETURNED");
    }

    private String nextRentalNo() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
        return "RT-" + RENTAL_NO_TIME.format(LocalDateTime.now()) + "-" + suffix;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
