package com.novel.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.AITaskDTO;
import com.novel.admin.mapper.AITaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAITaskService {

    private final AITaskMapper aiTaskMapper;

    public Page<AITaskDTO> getAITasks(String status, int page, int size) {
        Page<AITaskDTO> pageParam = new Page<>(page, size);
        return aiTaskMapper.selectAITaskPage(pageParam, status);
    }

    public AITaskDTO getAITaskById(Long id) {
        return aiTaskMapper.selectAITaskById(id);
    }

    public void retryTask(Long id) {
        aiTaskMapper.updateTaskStatus(id, "PENDING");
    }

    public void deleteTask(Long id) {
        aiTaskMapper.deleteById(id);
    }

    public Map<String, Object> getTaskStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", aiTaskMapper.countByStatus(null));
        stats.put("running", aiTaskMapper.countByStatus("RUNNING"));
        stats.put("completed", aiTaskMapper.countByStatus("COMPLETED"));
        stats.put("failed", aiTaskMapper.countByStatus("FAILED"));
        return stats;
    }
}
