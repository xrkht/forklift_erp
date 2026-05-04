package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.ConfigReplaceRequestDTO;
import com.example.forklift_erp.entity.ConfigReplaceLog;
import com.example.forklift_erp.service.ConfigReplaceLogService;
import com.example.forklift_erp.service.ConfigReplaceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/replace")
public class ConfigReplaceController {

    @Autowired
    private ConfigReplaceLogService configReplaceLogService;

    @Autowired
    private ConfigReplaceService configReplaceService;

    /**
     * 查询某车辆的所有替换记录
     */
    @GetMapping("/machine/{machineId}")
    public Result<List<ConfigReplaceLog>> getByMachineId(@PathVariable Long machineId) {
        return Result.success(configReplaceLogService.findByMachineId(machineId));
    }

    /**
     * 执行一次配置替换（真正的业务操作）
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ConfigReplaceLog> performReplace(@Valid @RequestBody ConfigReplaceRequestDTO request) {
        ConfigReplaceLog log = configReplaceService.performReplace(request);
        return Result.success("替换成功", log);
    }
}