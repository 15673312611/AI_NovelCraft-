package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.AITask;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AITaskRepository extends BaseMapper<AITask> {
    @Select("SELECT * FROM ai_tasks WHERE status = #{status}")
    IPage<AITask> findByStatus(@Param("status") String status, Page<AITask> page);
    @Select("SELECT * FROM ai_tasks WHERE type = #{type}")
    IPage<AITask> findByType(@Param("type") String type, Page<AITask> page);
    @Select("SELECT * FROM ai_tasks WHERE user_id = #{userId}")
    IPage<AITask> findByUserId(@Param("userId") Long userId, Page<AITask> page);
    @Select("SELECT * FROM ai_tasks WHERE novel_id = #{novelId}")
    IPage<AITask> findByNovelId(@Param("novelId") Long novelId, Page<AITask> page);
    @Select("SELECT * FROM ai_tasks WHERE status = #{status} AND user_id = #{userId}")
    IPage<AITask> findByStatusAndUserId(@Param("status") String status, @Param("userId") Long userId, Page<AITask> page);
    @Select("SELECT * FROM ai_tasks WHERE status = #{status} AND type = #{type}")
    IPage<AITask> findByStatusAndType(@Param("status") String status, @Param("type") String type, Page<AITask> page);
    @Select("SELECT COUNT(*) FROM ai_tasks WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
    @Select("SELECT status, COUNT(*) FROM ai_tasks WHERE user_id = #{userId} GROUP BY status")
    List<Object[]> countByStatusAndUserId(@Param("userId") Long userId);
    @Select("SELECT * FROM ai_tasks ORDER BY created_at DESC")
    IPage<AITask> findRecentTasks(Page<AITask> page);
    @Select("SELECT * FROM ai_tasks WHERE status = 'RUNNING' ORDER BY created_at ASC")
    List<AITask> findRunningTasks();
    @Select("SELECT * FROM ai_tasks WHERE status = 'PENDING' ORDER BY created_at ASC")
    List<AITask> findPendingTasks();
}

