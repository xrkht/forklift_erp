package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.RepairRecordCreateDTO;
import com.example.forklift_erp.dto.RepairRecordVO;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.RepairRecordService;
import com.example.forklift_erp.util.ListPageSupport;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/repairs")
public class RepairRecordController {

    @Autowired
    private RepairRecordService repairService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private CollaborationService collaborationService;

    @GetMapping
    public Result<?> getAll(
            @RequestParam(defaultValue = "false") boolean paged,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
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
        List<RepairRecordVO> list = records.stream()
                .map(RepairRecordVO::fromEntity)
                .collect(Collectors.toList());
        if (paged) {
            List<RepairRecordVO> filtered = ListPageSupport.filter(list, keyword, row -> ListPageSupport.text(
                    row.getVehicleNumber(),
                    row.getCustomerName(),
                    row.getCustomerAddress(),
                    row.getRepairPerson(),
                    row.getStatus(),
                    row.getFaultDescription(),
                    row.getRepairContent(),
                    row.getUsedParts(),
                    row.getRemarks()
            ));
            return Result.success(ListPageSupport.page(filtered, page, size));
        }
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<RepairRecordVO> getById(@PathVariable Long id) {
        return repairService.findById(id)
                .map(RepairRecordVO::fromEntity)
                .map(Result::success)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'repair:write')")
    public Result<RepairRecordVO> create(@Valid @RequestBody RepairRecordCreateDTO dto) {
        RepairRecord saved = repairService.save(dto.toEntity());
        operationAuditService.record("维修工单", "CREATE", "REPAIR", saved.getId(),
                saved.getVehicleNumber(), saved.getCustomerName(), saved.getFaultDescription(),
                saved.getRepairPerson(), saved.getRemarks(), "REPAIR", saved.getId());
        return Result.success("创建成功", RepairRecordVO.fromEntity(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'repair:write')")
    @Transactional
    public Result<RepairRecordVO> update(@PathVariable Long id, @Valid @RequestBody RepairRecordCreateDTO dto) {
        RepairRecord record = repairService.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在"));
        // 由于 update 权限通常允许修改所有字段，这里直接覆盖属性
        collaborationService.validateWrite(record, dto.getVersion());
        dto.applyToEntity(record);
        RepairRecord saved = repairService.save(record);
        operationAuditService.record("维修工单", "UPDATE", "REPAIR", saved.getId(),
                saved.getVehicleNumber(), saved.getCustomerName(), "更新维修工单：" + saved.getStatus(),
                saved.getRepairPerson(), saved.getRemarks(), "REPAIR", saved.getId());
        return Result.success("更新成功", RepairRecordVO.fromEntity(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'repair:write')")
    @Transactional
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long version) {
        RepairRecord record = repairService.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "维修记录不存在"));
        collaborationService.validateWrite(record, version);
        repairService.deleteById(id);
        operationAuditService.record("维修工单", "DELETE", "REPAIR", record.getId(),
                record.getVehicleNumber(), record.getCustomerName(), "删除维修工单",
                record.getRepairPerson(), record.getRemarks(), "REPAIR", record.getId());
        return Result.success("删除成功");
    }
}
