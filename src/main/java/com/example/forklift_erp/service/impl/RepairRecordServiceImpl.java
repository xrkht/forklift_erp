package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
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
import com.example.forklift_erp.service.RepairRecordService;
import com.example.forklift_erp.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public List<RepairRecord> findAll() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findAll();
        } else {
            return repairRepository.findAllByIsLockedFalse();
        }
    }

    @Override
    public Optional<RepairRecord> findById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findById(id);
        } else {
            return repairRepository.findByIdAndIsLockedFalse(id);
        }
    }

    @Override
    public Optional<RepairRecord> findByIdForUpdate(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repairRepository.findByIdForUpdate(id);
        } else {
            return repairRepository.findByIdAndIsLockedFalseForUpdate(id);
        }
    }

    @Override
    @Transactional
    public RepairRecord save(RepairRecord record) {
        if (record.getId() != null) {
            Optional<RepairRecord> existingOpt = findById(record.getId());
            if (existingOpt.isPresent()) {
                RepairRecord existing = existingOpt.get();
                if (existing.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "该维修记录已被锁定，您无权修改");
                }
            } else {
                throw new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在");
            }
        }

        if (record.getRepairDate() == null) record.setRepairDate(LocalDateTime.now());
        if (record.getStatus() == null) record.setStatus("PENDING");
        normalizeReferences(record);
        normalizeFees(record);
        log.info("保存维修记录: id={}, vehicleNumber={}, status={}", record.getId(), record.getVehicleNumber(), record.getStatus());
        collaborationService.stampWrite(record);
        return repairRepository.saveAndFlush(record);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Optional<RepairRecord> existingOpt = findByIdForUpdate(id);
        if (existingOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在，id=" + id);
        }
        RepairRecord existing = existingOpt.get();
        if (existing.getIsLocked() && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该维修记录已被锁定，您无权删除");
        }
        repairRepository.deleteById(id);
        log.info("删除维修记录: id={}", id);
    }

    @Override
    public List<RepairRecord> findByMachineId(Long machineId) {
        List<RepairRecord> list = repairRepository.findByMachineIdOrderByRepairDateDesc(machineId);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByRepairPerson(String repairPerson) {
        List<RepairRecord> list = repairRepository.findByRepairPerson(repairPerson);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByStatus(String status) {
        List<RepairRecord> list = repairRepository.findByStatus(status);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public List<RepairRecord> findByDateRange(LocalDateTime start, LocalDateTime end) {
        List<RepairRecord> list = repairRepository.findByRepairDateBetween(start, end);
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            list = list.stream().filter(r -> !r.getIsLocked()).collect(Collectors.toList());
        }
        return list;
    }

    private void normalizeReferences(RepairRecord record) {
        if (record.getMachineId() != null) {
            MachineInventory machine = machineRepository.findById(record.getMachineId())
                    .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "维修车辆不存在"));
            if (Boolean.TRUE.equals(machine.getModelOnly())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "维修记录请选择具体库存车号");
            }
            record.setVehicleNumber(machine.getVehicleProductNumber());
        }
        if (record.getCustomerId() != null) {
            Customer customer = customerRepository.findById(record.getCustomerId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修客户不存在"));
            record.setCustomerName(customer.getCompanyName());
            record.setCustomerAddress(customer.getAddress());
        }
        if (record.getCustomerName() == null || record.getCustomerName().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "维修客户不能为空");
        }
        normalizeRepairPerson(record);
        normalizeUsedParts(record);
        record.setWorkHours(null);
    }

    private void normalizeRepairPerson(RepairRecord record) {
        if (Boolean.TRUE.equals(record.getRepairExternal())) {
            record.setRepairPersonUserId(null);
            record.setRepairPerson("其他");
            return;
        }
        if (record.getRepairPersonUserId() == null) {
            return;
        }
        User user = userRepository.findById(record.getRepairPersonUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修人员不存在"));
        if (!user.isEnabled() || !"REPAIR".equals(normalizeJobTag(user.getJobTag()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "维修人员必须选择职务标签为维修的用户");
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
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "使用配件不存在: " + id)))
                .toList();
        record.setUsedPartIds(parts.stream()
                .map(part -> String.valueOf(part.getId()))
                .collect(Collectors.joining(",")));
        record.setUsedParts(parts.stream()
                .map(part -> "%s/%s".formatted(part.getPartCode(), part.getPartName()))
                .collect(Collectors.joining("，")));
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
}
