package com.novel.admin.service;

import com.novel.admin.dto.DashboardStatsDTO;
import com.novel.admin.mapper.AdminDashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminDashboardMapper dashboardMapper;

    public DashboardStatsDTO getStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setTotalUsers(dashboardMapper.countTotalUsers());
        stats.setTotalNovels(dashboardMapper.countTotalNovels());
        stats.setTotalAITasks(dashboardMapper.countTotalAITasks());
        stats.setTotalCost(dashboardMapper.sumTotalCost());
        return stats;
    }

    public Object getUserTrend(int days) {
        return dashboardMapper.getUserTrend(days);
    }

    public Object getAITaskStats() {
        return dashboardMapper.getAITaskStats();
    }

    public Object getRecentTasks(int limit) {
        return dashboardMapper.getRecentTasks(limit);
    }
}
