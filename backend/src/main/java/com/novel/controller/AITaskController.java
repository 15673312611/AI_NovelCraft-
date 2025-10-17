package com.novel.controller;

import com.novel.dto.AITaskDto;
import com.novel.domain.entity.AITask;
import com.novel.service.AITaskService;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.novel.domain.entity.AIModel;
import com.novel.repository.AIModelRepository;

/**
 * AI任务控制器
 */
@RestController
@RequestMapping("/ai-tasks")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AITaskController {

    @Autowired
    private AITaskService aiTaskService;

    @Autowired
    private AIModelRepository aiModelRepository;

    /**
     * 获取AI任务列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        IPage<AITaskDto> tasks = aiTaskService.getTasks(page, size, status, type);

        Map<String, Object> response = new HashMap<>();
        response.put("content", tasks.getRecords());
        response.put("totalElements", tasks.getTotal());
        response.put("totalPages", tasks.getPages());
        response.put("currentPage", tasks.getCurrent() - 1); // MyBatis Plus从1开始，前端从0开始
        response.put("size", tasks.getSize());
        response.put("first", tasks.getCurrent() == 1);
        response.put("last", tasks.getCurrent() == tasks.getPages());
        response.put("empty", tasks.getRecords().isEmpty());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取AI任务详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<AITaskDto> getTask(@PathVariable Long id) {
        AITaskDto task = aiTaskService.getTaskById(id);
        return ResponseEntity.ok(task);
    }

    /**
     * 创建AI任务
     */
    @PostMapping
    public ResponseEntity<AITaskDto> createTask(@RequestBody AITask task) {
        AITaskDto createdTask = aiTaskService.createTask(task);
        return ResponseEntity.ok(createdTask);
    }

    /**
     * 更新AI任务
     */
    @PutMapping("/{id}")
    public ResponseEntity<AITaskDto> updateTask(@PathVariable Long id, @RequestBody AITask task) {
        AITaskDto updatedTask = aiTaskService.updateTask(id, task);
        return ResponseEntity.ok(updatedTask);
    }

    /**
     * 删除AI任务
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        aiTaskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取任务统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> statistics = aiTaskService.getStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * 可用模型列表
     */
    @GetMapping("/models")
    public ResponseEntity<List<Map<String, Object>>> getModels() {
        List<AIModel> list = aiModelRepository.findByAvailableTrueOrderByCostPer1kAsc();
        List<Map<String, Object>> models = list.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getModelId());
            map.put("name", m.getDisplayName());
            map.put("description", m.getDescription());
            map.put("maxTokens", m.getMaxTokens());
            map.put("costPer1kTokens", m.getCostPer1k());
            map.put("available", m.getAvailable());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(models);
    }

    /**
     * 估算成本
     */
    @PostMapping("/estimate-cost")
    public ResponseEntity<Map<String, Object>> estimateCost(@RequestBody Map<String, Object> body) {
        String prompt = String.valueOf(body.getOrDefault("prompt", ""));
        String model = String.valueOf(body.getOrDefault("model", "gpt-4"));
        int maxTokens = Integer.parseInt(String.valueOf(body.getOrDefault("maxTokens", 1000)));
        int estimatedTokens = Math.min(Math.max(prompt.length() / 3, 200), maxTokens);
        double unitCost = "gpt-4".equals(model) ? 0.03 : ("gpt-4o".equals(model) ? 0.005 : 0.0015);
        double estimatedCost = (estimatedTokens / 1000.0) * unitCost;
        Map<String, Object> resp = new HashMap<>();
        resp.put("estimatedTokens", estimatedTokens);
        resp.put("estimatedCost", estimatedCost);
        resp.put("model", model);
        resp.put("maxTokens", maxTokens);
        return ResponseEntity.ok(resp);
    }

    /** 获取任务类型 */
    @GetMapping("/types")
    public ResponseEntity<List<String>> getTaskTypes() {
        return ResponseEntity.ok(Arrays.asList(
                "plot_generation",
                "character_development",
                "dialogue_generation",
                "scene_description",
                "story_outline",
                "writing_assistance"
        ));
    }

    /** 获取任务状态 */
    @GetMapping("/statuses")
    public ResponseEntity<List<String>> getTaskStatuses() {
        return ResponseEntity.ok(Arrays.asList(
                "PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"
        ));
    }

    /**
     * 启动任务
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<AITaskDto> startTask(@PathVariable Long id) {
        AITaskDto task = aiTaskService.startTask(id);
        return ResponseEntity.ok(task);
    }

    /**
     * 停止任务
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<AITaskDto> stopTask(@PathVariable Long id) {
        AITaskDto task = aiTaskService.stopTask(id);
        return ResponseEntity.ok(task);
    }

    /**
     * 重试任务
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<AITaskDto> retryTask(@PathVariable Long id) {
        AITaskDto task = aiTaskService.retryTask(id);
        return ResponseEntity.ok(task);
    }

    /**
     * 获取任务进度
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<Map<String, Object>> getTaskProgress(@PathVariable Long id) {
        Map<String, Object> progress = aiTaskService.getTaskProgress(id);
        return ResponseEntity.ok(progress);
    }
} 