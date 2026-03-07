package com.novel.admin.dto;

import lombok.Data;

@Data
public class DashboardStatsDTO {
    private Long totalUsers;
    private Long totalNovels;
    private Long totalAITasks;
    private Double totalCost;
}
