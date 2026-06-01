package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.ListSummaryVO;
import com.example.forklift_erp.dto.StatisticsDashboardVO;
import com.example.forklift_erp.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/finance")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'log:read')")
    public Result<StatisticsDashboardVO> finance(@RequestParam(required = false) Integer year) {
        return Result.success(statisticsService.financeDashboard(year));
    }

    @GetMapping("/list-summary")
    @PreAuthorize("@permissionService.hasAnyPermission(authentication, 'stock:adjust', 'vehicle:write', 'repair:write', 'log:read')")
    public Result<ListSummaryVO> listSummary(@RequestParam String type,
                                             @RequestParam(required = false) String keyword) {
        return Result.success(statisticsService.listSummary(type, keyword));
    }
}
