package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.JobTag;
import com.example.forklift_erp.constant.RepairStatus;
import com.example.forklift_erp.dto.RepairRecordCreateDTO;
import com.example.forklift_erp.dto.RepairRecordVO;
import com.example.forklift_erp.entity.Customer;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.User;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.UserRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.RepairRecordService;
import com.example.forklift_erp.util.ListPageSupport;
import com.example.forklift_erp.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RepairRecordServiceImpl implements RepairRecordService {

    @Autowired
    private RepairRecordRepository repairRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Override
    public List<RepairRecord> findAll() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findAll();
        }
        return repairRepository.findAllByIsLockedFalse();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RepairRecordVO> findPage(
            String keyword,
            Integer page,
            Integer size,
            Long machineId,
            String repairPerson,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<RepairRecord> result = repairRepository.searchPage(
                normalizeKeyword(keyword),
                machineId,
                normalizeKeyword(repairPerson),
                normalizeKeyword(status),
                startDate,
                endDate,
                SecurityUtils.isAdminOrSuperAdmin(),
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "repairDate"))
        );
        return PageResult.of(
                result.getContent().stream().map(RepairRecordVO::fromEntity).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    @Override
    public Optional<RepairRecord> findById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findById(id);
        }
        return repairRepository.findByIdAndIsLockedFalse(id);
    }

    @Override
    public Optional<RepairRecord> findByIdForUpdate(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findByIdForUpdate(id);
        }
        return repairRepository.findByIdAndIsLockedFalseForUpdate(id);
    }

    @Override
    @Transactional
    public RepairRecord save(RepairRecord record) {
        if (record.getId() != null) {
            Optional<RepairRecord> existingOpt = findById(record.getId());
            if (existingOpt.isPresent()) {
                RepairRecord existing = existingOpt.get();
                if (Boolean.TRUE.equals(existing.getIsLocked()) && !SecurityUtils.isAdminOrSuperAdmin()) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "Repair record is locked and cannot be modified");
                }
            } else {
                throw new BusinessException(ResultCode.NOT_FOUND, "Repair record not found");
            }
        }

        if (record.getRepairDate() == null) {
            record.setRepairDate(LocalDateTime.now());
        }
        if (record.getStatus() == null) {
            record.setStatus(RepairStatus.PENDING.code());
        } else {
            record.setStatus(RepairStatus.normalizeOrDefault(record.getStatus(), RepairStatus.PENDING));
        }
        normalizeReferences(record);
        normalizeFees(record);
        log.info("Save repair record: id={}, vehicleNumber={}, status={}", record.getId(), record.getVehicleNumber(), record.getStatus());
        collaborationService.stampWrite(record);
        return repairRepository.saveAndFlush(record);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Optional<RepairRecord> existingOpt = findByIdForUpdate(id);
        if (existingOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Repair record not found, id=" + id);
        }
        RepairRecord existing = existingOpt.get();
        if (Boolean.TRUE.equals(existing.getIsLocked()) && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Repair record is locked and cannot be deleted");
        }
        repairRepository.deleteById(id);
        log.info("Delete repair record: id={}", id);
    }

    @Override
    public List<RepairRecord> findByMachineId(Long machineId) {
        List<RepairRecord> list = repairRepository.findByMachineIdOrderByRepairDateDesc(machineId);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !Boolean.TRUE.equals(r.getIsLocked())).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByRepairPerson(String repairPerson) {
        List<RepairRecord> list = repairRepository.findByRepairPerson(repairPerson);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !Boolean.TRUE.equals(r.getIsLocked())).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByStatus(String status) {
        List<RepairRecord> list = repairRepository.findByStatus(status);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !Boolean.TRUE.equals(r.getIsLocked())).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByDateRange(LocalDateTime start, LocalDateTime end) {
        List<RepairRecord> list = repairRepository.findByRepairDateBetween(start, end);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !Boolean.TRUE.equals(r.getIsLocked())).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    @Transactional
    public RepairRecordVO create(RepairRecordCreateDTO dto) {
        RepairRecord saved = save(dto.toEntity());
        operationAuditService.record("Repair", "CREATE", "REPAIR", saved.getId(),
                saved.getVehicleNumber(), saved.getCustomerName(), saved.getFaultDescription(),
                saved.getRepairPerson(), saved.getRemarks(), "REPAIR", saved.getId());
        return RepairRecordVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public RepairRecordVO update(Long id, RepairRecordCreateDTO dto) {
        RepairRecord record = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Repair record not found"));
        collaborationService.validateWrite(record, dto.getVersion());
        dto.applyToEntity(record);
        RepairRecord saved = save(record);
        operationAuditService.record("Repair", "UPDATE", "REPAIR", saved.getId(),
                saved.getVehicleNumber(), saved.getCustomerName(), "Update repair: " + saved.getStatus(),
                saved.getRepairPerson(), saved.getRemarks(), "REPAIR", saved.getId());
        return RepairRecordVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public RepairRecordVO updateStatus(Long id, String status, Long version) {
        RepairRecord record = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Repair record not found"));
        collaborationService.validateWrite(record, version);
        record.setStatus(RepairStatus.normalizeOrDefault(status, RepairStatus.PENDING));
        RepairRecord saved = save(record);
        operationAuditService.record("Repair", saved.getStatus(), "REPAIR", saved.getId(),
                saved.getVehicleNumber(), saved.getCustomerName(), "Switch repair status: " + saved.getStatus(),
                saved.getRepairPerson(), saved.getRemarks(), "REPAIR", saved.getId());
        return RepairRecordVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long version) {
        RepairRecord record = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Repair record not found"));
        collaborationService.validateWrite(record, version);
        deleteById(id);
        operationAuditService.record("Repair", "DELETE", "REPAIR", record.getId(),
                record.getVehicleNumber(), record.getCustomerName(), "Delete repair",
                record.getRepairPerson(), record.getRemarks(), "REPAIR", record.getId());
    }

    private void normalizeReferences(RepairRecord record) {
        if (record.getMachineId() != null) {
            MachineInventory machine = machineRepository.findById(record.getMachineId())
                    .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Repair vehicle not found"));
            if (Boolean.TRUE.equals(machine.getModelOnly())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Repair record must select a concrete vehicle");
            }
            record.setVehicleNumber(machine.getVehicleProductNumber());
        }
        if (record.getCustomerId() != null) {
            Customer customer = customerRepository.findById(record.getCustomerId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Repair customer not found"));
            record.setCustomerName(customer.getCompanyName());
            record.setCustomerAddress(customer.getAddress());
        }
        if (record.getCustomerName() == null || record.getCustomerName().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Repair customer is required");
        }
        normalizeRepairPerson(record);
        normalizeUsedParts(record);
        record.setWorkHours(null);
    }

    private void normalizeRepairPerson(RepairRecord record) {
        if (Boolean.TRUE.equals(record.getRepairExternal())) {
            record.setRepairPersonUserId(null);
            record.setRepairPerson("External");
            return;
        }
        if (record.getRepairPersonUserId() == null) {
            return;
        }
        User user = userRepository.findById(record.getRepairPersonUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Repair user not found"));
        if (!user.isEnabled() || !JobTag.REPAIR.code().equals(normalizeJobTag(user.getJobTag()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Repair user must be enabled and tagged as REPAIR");
        }
        record.setRepairPerson(user.getUsername());
        record.setRepairExternal(false);
    }

    private String normalizeJobTag(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private void normalizeUsedParts(RepairRecord record) {
        List<Long> ids = parseIds(record.getUsedPartIds());
        if (ids.isEmpty()) {
            return;
        }
        List<PartInventory> parts = ids.stream()
                .map(id -> partRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Used part not found: " + id)))
                .toList();
        record.setUsedPartIds(parts.stream()
                .map(part -> String.valueOf(part.getId()))
                .collect(Collectors.joining(",")));
        record.setUsedParts(parts.stream()
                .map(part -> "%s/%s".formatted(part.getPartCode(), part.getPartName()))
                .collect(Collectors.joining(";")));
    }

    private void normalizeFees(RepairRecord record) {
        BigDecimal repairFee = money(record.getRepairFee());
        BigDecimal partsFee = money(record.getPartsFee());
        record.setTotalFee(repairFee.add(partsFee));
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .toList();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
