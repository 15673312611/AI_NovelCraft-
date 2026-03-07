package com.novel.admin.controller;

import com.novel.admin.dto.DashboardStatsDTO;
import com.novel.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/stats")
    public DashboardStatsDTO getStats() {
        return dashboardService.getStats();
    }

    @GetMapping("/user-trend")
    public Object getUserTrend(@RequestParam(defaultValue = "30") int days) {
        return dashboardService.getUserTrend(days);
    }

    @GetMapping("/ai-task-stats")
    public Object getAITaskStats() {
        return dashboardService.getAITaskStats();
    }

    @GetMapping("/recent-tasks")
    public Object getRecentTasks(@RequestParam(defaultValue = "10") int limit) {
        return dashboardService.getRecentTasks(limit);
    }
}
