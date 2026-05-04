package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.service.RepairRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RepairRecordServiceImpl implements RepairRecordService {

    @Autowired
    private RepairRecordRepository repairRepository;

    @Override
    public List<RepairRecord> findAll() {
        return repairRepository.findAll();
    }

    @Override
    public Optional<RepairRecord> findById(Long id) {
        return repairRepository.findById(id);
    }

    @Override
    public RepairRecord save(RepairRecord record) {
        // 简单业务校验：维修日期不能为空
        if (record.getRepairDate() == null) {
            record.setRepairDate(LocalDateTime.now());
        }
        if (record.getStatus() == null) {
            record.setStatus("PENDING");
        }
        log.info("保存维修记录: id={}, vehicleNumber={}, status={}", record.getId(), record.getVehicleNumber(), record.getStatus());
        return repairRepository.save(record);
    }

    @Override
    public void deleteById(Long id) {
        if (!repairRepository.existsById(id)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在，id=" + id);
        }
        repairRepository.deleteById(id);
        log.info("删除维修记录: id={}", id);
    }

    @Override
    public List<RepairRecord> findByMachineId(Long machineId) {
        return repairRepository.findByMachineIdOrderByRepairDateDesc(machineId);
    }

    @Override
    public List<RepairRecord> findByRepairPerson(String repairPerson) {
        return repairRepository.findByRepairPerson(repairPerson);
    }

    @Override
    public List<RepairRecord> findByStatus(String status) {
        return repairRepository.findByStatus(status);
    }

    @Override
    public List<RepairRecord> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return repairRepository.findByRepairDateBetween(start, end);
    }
}