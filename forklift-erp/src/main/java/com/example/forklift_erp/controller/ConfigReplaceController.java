package com.example.forklift_erp.controller;

import com.example.forklift_erp.entity.ConfigReplaceLog;
import com.example.forklift_erp.service.ConfigReplaceLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/replace")
public class ConfigReplaceController {

    @Autowired
    private ConfigReplaceLogService configReplaceLogService;

    // 查询某车辆的所有替换记录
    @GetMapping("/machine/{machineId}")
    public ResponseEntity<List<ConfigReplaceLog>> getByMachineId(@PathVariable Long machineId) {
        return ResponseEntity.ok(configReplaceLogService.findByMachineId(machineId));
    }

    // 记录一次配置替换
    @PostMapping
    public ResponseEntity<ConfigReplaceLog> record(@RequestBody ConfigReplaceLog log) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configReplaceLogService.save(log));
    }
}