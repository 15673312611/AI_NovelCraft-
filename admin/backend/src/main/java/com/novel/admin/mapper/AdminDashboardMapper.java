package com.novel.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDashboardMapper {

    @Select("SELECT COUNT(*) FROM users")
    Long countTotalUsers();

    @Select("SELECT COUNT(*) FROM novels")
    Long countTotalNovels();

    @Select("SELECT COUNT(*) FROM ai_tasks")
    Long countTotalAITasks();

    @Select("SELECT COALESCE(SUM(actual_cost), 0) FROM ai_tasks WHERE actual_cost IS NOT NULL")
    Double sumTotalCost();

    @Select("SELECT DATE(created_at) as date, COUNT(*) as count FROM users " +
            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> getUserTrend(int days);

    @Select("SELECT type, COUNT(*) as count FROM ai_tasks GROUP BY type")
    List<Map<String, Object>> getAITaskStats();

    @Select("SELECT * FROM ai_tasks ORDER BY created_at DESC LIMIT #{limit}")
    List<Map<String, Object>> getRecentTasks(int limit);
}
