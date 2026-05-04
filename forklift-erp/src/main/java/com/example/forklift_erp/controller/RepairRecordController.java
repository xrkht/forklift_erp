package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.RepairRecordService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/repairs")
public class RepairRecordController {

    @Autowired
    private RepairRecordService repairService;

    /**
     * 查询所有维修记录（可按条件过滤）
     * 可选参数：machineId, repairPerson, status, startDate, endDate
     */
    @GetMapping
    public Result<List<RepairRecord>> getAll(
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) String repairPerson,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        List<RepairRecord> records;
        if (machineId != null) {
            records = repairService.findByMachineId(machineId);
        } else if (repairPerson != null) {
            records = repairService.findByRepairPerson(repairPerson);
        } else if (status != null) {
            records = repairService.findByStatus(status);
        } else if (startDate != null && endDate != null) {
            records = repairService.findByDateRange(startDate, endDate);
        } else {
            records = repairService.findAll();
        }
        return Result.success(records);
    }

    /**
     * 查询单个维修记录
     */
    @GetMapping("/{id}")
    public Result<RepairRecord> getById(@PathVariable Long id) {
        log.info("查询维修记录: id={}", id);
        return repairService.findById(id)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在，id=" + id));
    }

    /**
     * 新增维修记录
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<RepairRecord> create(@Valid @RequestBody RepairRecord record) {
        log.info("新增维修记录: machineId={}, customerName={}", record.getMachineId(), record.getCustomerName());
        return Result.success("创建成功", repairService.save(record));
    }

    /**
     * 更新维修记录（例如修改状态、完成维修）
     */
    @PutMapping("/{id}")
    public Result<RepairRecord> update(@PathVariable Long id, @Valid @RequestBody RepairRecord record) {
        log.info("更新维修记录: id={}", id);
        if (!repairService.findById(id).isPresent()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在，id=" + id);
        }
        record.setId(id);
        return Result.success("更新成功", repairService.save(record));
    }

    /**
     * 删除维修记录
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除维修记录: id={}", id);
        repairService.deleteById(id);
        return Result.success("删除成功");
    }
}