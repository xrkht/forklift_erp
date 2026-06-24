package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.TodoCenterVO;
import com.example.forklift_erp.security.PermissionCodes;
import com.example.forklift_erp.service.TodoCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/todos")
public class TodoCenterController {

    @Autowired
    private TodoCenterService todoCenterService;

    @GetMapping
    @PreAuthorize(PermissionCodes.HAS_STOCK_ADJUST)
    public Result<TodoCenterVO> dashboard() {
        return Result.success(todoCenterService.dashboard());
    }
}
