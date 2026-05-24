package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.ConfigReplaceLogVO;
import com.example.forklift_erp.dto.ConfigReplaceRequestDTO;
import com.example.forklift_erp.dto.PartReplaceRequestDTO;
import com.example.forklift_erp.entity.ConfigReplaceLog;
import com.example.forklift_erp.service.ConfigReplaceLogService;
import com.example.forklift_erp.service.ConfigReplaceService;
import com.example.forklift_erp.service.OperationAuditService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/replace")
public class ConfigReplaceController {

    @Autowired
    private ConfigReplaceLogService configReplaceLogService;

    @Autowired
    private ConfigReplaceService configReplaceService;

    @Autowired
    private OperationAuditService operationAuditService;

    @GetMapping("/machine/{machineId}")
    public Result<List<ConfigReplaceLogVO>> getByMachineId(@PathVariable Long machineId) {
        List<ConfigReplaceLogVO> list = configReplaceLogService.findByMachineId(machineId).stream()
                .map(ConfigReplaceLogVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'replace:write')")
    public Result<ConfigReplaceLogVO> performReplace(@Valid @RequestBody ConfigReplaceRequestDTO request) {
        ConfigReplaceLog log = configReplaceService.performReplace(request);
        operationAuditService.record("配置替换", "REPLACE", "MACHINE", log.getMachineId(),
                "车辆ID " + log.getMachineId(), log.getItemName(),
                display(log.getOldValue()) + " -> " + display(log.getNewValue()),
                log.getOperator(), log.getRemark(), "REPLACE", log.getId());
        return Result.success("替换成功", ConfigReplaceLogVO.fromEntity(log));
    }

    @PostMapping("/part")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'replace:write')")
    public Result<ConfigReplaceLogVO> performPartReplace(@Valid @RequestBody PartReplaceRequestDTO request) {
        ConfigReplaceLog log = configReplaceService.performPartReplace(request);
        operationAuditService.record("配置替换", "PART_REPLACE", "MACHINE", log.getMachineId(),
                "车辆ID " + log.getMachineId(), log.getItemName(),
                display(log.getOldValue()) + " -> " + display(log.getNewValue()),
                log.getOperator(), log.getRemark(), "REPLACE", log.getId());
        return Result.success("配件替换成功，旧件已自动入库", ConfigReplaceLogVO.fromEntity(log));
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
