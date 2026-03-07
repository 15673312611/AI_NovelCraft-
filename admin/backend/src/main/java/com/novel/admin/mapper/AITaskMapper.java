package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.AITaskDTO;
import com.novel.admin.entity.AITask;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AITaskMapper extends BaseMapper<AITask> {

    @Select("<script>" +
            "SELECT t.id, t.name, t.type, t.status, t.progress_percentage as progress, " +
            "t.actual_cost as cost, t.created_at as createdAt, t.completed_at as completedAt, " +
            "u.username " +
            "FROM ai_tasks t " +
            "LEFT JOIN users u ON t.user_id = u.id " +
            "<where>" +
            "<if test='status != null and status != \"\"'>" +
            "AND t.status = #{status}" +
            "</if>" +
            "</where>" +
            "ORDER BY t.created_at DESC" +
            "</script>")
    Page<AITaskDTO> selectAITaskPage(Page<AITaskDTO> page, @Param("status") String status);

    @Select("SELECT t.id, t.name, t.type, t.status, t.progress_percentage as progress, " +
            "t.actual_cost as cost, t.created_at as createdAt, t.completed_at as completedAt, " +
            "u.username " +
            "FROM ai_tasks t " +
            "LEFT JOIN users u ON t.user_id = u.id " +
            "WHERE t.id = #{id}")
    AITaskDTO selectAITaskById(@Param("id") Long id);

    @Update("UPDATE ai_tasks SET status = #{status} WHERE id = #{id}")
    void updateTaskStatus(@Param("id") Long id, @Param("status") String status);

    @Select("<script>" +
            "SELECT COUNT(*) FROM ai_tasks " +
            "<where>" +
            "<if test='status != null and status != \"\"'>" +
            "AND status = #{status}" +
            "</if>" +
            "</where>" +
            "</script>")
    Integer countByStatus(@Param("status") String status);
}
