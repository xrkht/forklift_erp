package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.RepairRecordService;
import com.example.forklift_erp.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RepairRecordServiceImpl implements RepairRecordService {

    @Autowired
    private RepairRecordRepository repairRepository;

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
}
