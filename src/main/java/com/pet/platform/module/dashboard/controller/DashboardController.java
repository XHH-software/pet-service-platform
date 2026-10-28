package com.pet.platform.module.dashboard.controller;

import com.pet.platform.common.Result;
import com.pet.platform.module.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(dashboardService.summary());
    }

    @GetMapping("/dashboard/trend")
    public Result<List<Map<String, Object>>> trend() {
        return Result.ok(dashboardService.trend());
    }

    @GetMapping("/report/staff-rank")
    public Result<List<Map<String, Object>>> staffRank() {
        return Result.ok(dashboardService.staffRank());
    }
}
