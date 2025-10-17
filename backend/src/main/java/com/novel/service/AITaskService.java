package com.novel.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.dto.AITaskDto;
import com.novel.domain.entity.AITask;
import com.novel.repository.AITaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI任务服务
 */
@Service
@Transactional
public class AITaskService {

    @Autowired
    private AITaskRepository aiTaskRepository;

    /**
     * 获取AI任务列表
     */
    public IPage<AITaskDto> getTasks(int page, int size, String status, String type) {
        Page<AITask> pageParam = new Page<>(page + 1, size);
        IPage<AITask> tasks;

        if (status != null && type != null) {
            tasks = aiTaskRepository.findByStatusAndType(status.toUpperCase(), type.toUpperCase(), pageParam);
        } else if (status != null) {
            tasks = aiTaskRepository.findByStatus(status.toUpperCase(), pageParam);
        } else if (type != null) {
            tasks = aiTaskRepository.findByType(type.toUpperCase(), pageParam);
        } else {
            tasks = aiTaskRepository.selectPage(pageParam, null);
        }

        // 转换为DTO
        IPage<AITaskDto> result = new Page<>(tasks.getCurrent(), tasks.getSize(), tasks.getTotal());
        result.setRecords(tasks.getRecords().stream().map(AITaskDto::fromEntity).collect(Collectors.toList()));
        return result;
    }

    /**
     * 根据ID获取AI任务
     */
    public AITaskDto getTaskById(Long id) {
        AITask task = aiTaskRepository.selectById(id);
        return task != null ? AITaskDto.fromEntity(task) : null;
    }

    /**
     * 创建AI任务
     */
    public AITaskDto createTask(AITask task) {
        aiTaskRepository.insert(task);
        return AITaskDto.fromEntity(task);
    }

    /**
     * 更新AI任务
     */
    public AITaskDto updateTask(Long id, AITask taskDetails) {
        AITask task = aiTaskRepository.selectById(id);
        if (task != null) {
            task.setName(taskDetails.getName());
            task.setType(taskDetails.getType());
            task.setStatus(taskDetails.getStatus());
            task.setInput(taskDetails.getInput());
            task.setOutput(taskDetails.getOutput());
            task.setParameters(taskDetails.getParameters());

            aiTaskRepository.updateById(task);
            return AITaskDto.fromEntity(task);
        }
        return null;
    }

    /**
     * 删除AI任务
     */
    public void deleteTask(Long id) {
        aiTaskRepository.deleteById(id);
    }

    /**
     * 获取任务统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        // 统计各状态的任务数量
        long totalTasks = aiTaskRepository.selectCount(null);
        long pendingTasks = aiTaskRepository.countByStatus("PENDING");
        long runningTasks = aiTaskRepository.countByStatus("RUNNING");
        long completedTasks = aiTaskRepository.countByStatus("COMPLETED");
        long failedTasks = aiTaskRepository.countByStatus("FAILED");
        
        statistics.put("totalTasks", totalTasks);
        statistics.put("pendingTasks", pendingTasks);
        statistics.put("runningTasks", runningTasks);
        statistics.put("completedTasks", completedTasks);
        statistics.put("failedTasks", failedTasks);
        
        // 计算成功率
        double successRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;
        
        // 模拟一些统计数据
        statistics.put("averageExecutionTime", 120); // 秒
        statistics.put("totalCost", 0.0);
        statistics.put("averageCost", 0.0);
        statistics.put("successRate", Math.round(successRate * 100.0) / 100.0); // 保留两位小数
        
        return statistics;
    }

    /**
     * 启动任务
     */
    public AITaskDto startTask(Long id) {
        AITask task = aiTaskRepository.selectById(id);
        if (task != null) {
            task.setStatus(AITask.AITaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());

            aiTaskRepository.updateById(task);
            return AITaskDto.fromEntity(task);
        }
        return null;
    }

    /**
     * 停止任务
     */
    public AITaskDto stopTask(Long id) {
        AITask task = aiTaskRepository.selectById(id);
        if (task != null) {
            task.setStatus(AITask.AITaskStatus.CANCELLED);

            aiTaskRepository.updateById(task);
            return AITaskDto.fromEntity(task);
        }
        return null;
    }

    /**
     * 重试任务
     */
    public AITaskDto retryTask(Long id) {
        AITask task = aiTaskRepository.selectById(id);
        if (task != null) {
            task.setStatus(AITask.AITaskStatus.PENDING);
            task.setRetryCount(task.getRetryCount() + 1);

            aiTaskRepository.updateById(task);
            return AITaskDto.fromEntity(task);
        }
        return null;
    }

    /**
     * 获取任务进度
     */
    public Map<String, Object> getTaskProgress(Long id) {
        Map<String, Object> progress = new HashMap<>();
        AITask task = aiTaskRepository.selectById(id);

        if (task != null) {
            progress.put("progress", task.getProgressPercentage());
            progress.put("status", task.getStatus());
            progress.put("startedAt", task.getStartedAt());
            progress.put("estimatedCompletion", task.getEstimatedCompletion());
        }
        
        return progress;
    }

    /**
     * 搜索用户任务
     */
    public List<AITask> searchUserTasks(Long userId, String status, String type, Long novelId) {
        QueryWrapper<AITask> queryWrapper = new QueryWrapper<>();
        
        // 根据用户ID过滤（假设AITask有userId字段，如果没有可能需要调整）
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        
        if (StringUtils.hasText(status)) {
            queryWrapper.eq("status", status);
        }
        
        if (StringUtils.hasText(type)) {
            queryWrapper.eq("type", type);
        }
        
        if (novelId != null) {
            queryWrapper.eq("novel_id", novelId);
        }
        
        queryWrapper.orderByDesc("created_at");
        return aiTaskRepository.selectList(queryWrapper);
    }

    /**
     * 更新任务进度
     */
    public void updateTaskProgress(Long id, int progressPercentage, String status, String message) {
        AITask task = aiTaskRepository.selectById(id);
        if (task != null) {
            task.setProgressPercentage(progressPercentage);
            task.setStatus(AITask.AITaskStatus.valueOf(status.toUpperCase()));
            task.setOutput(message);
            task.setUpdatedAt(LocalDateTime.now());
            aiTaskRepository.updateById(task);
        }
    }

    /**
     * 完成任务
     */
    public void completeTask(Long id, String output) {
        AITask task = aiTaskRepository.selectById(id);
        if (task != null) {
            task.setStatus(AITask.AITaskStatus.COMPLETED);
            task.setOutput(output);
            task.setCompletedAt(LocalDateTime.now());
            task.setProgressPercentage(100);
            aiTaskRepository.updateById(task);
        }
    }

    /**
     * 任务失败
     */
    public void failTask(Long id, String errorMessage) {
        AITask task = aiTaskRepository.selectById(id);
        if (task != null) {
            task.setStatus(AITask.AITaskStatus.FAILED);
            task.setError(errorMessage);
            task.setCompletedAt(LocalDateTime.now());
            aiTaskRepository.updateById(task);
        }
    }
} 