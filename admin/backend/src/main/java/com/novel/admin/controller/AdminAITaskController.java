package com.novel.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.AITaskDTO;
import com.novel.admin.service.AdminAITaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai-tasks")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminAITaskController {

    private final AdminAITaskService aiTaskService;

    @GetMapping
    public Page<AITaskDTO> getAITasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return aiTaskService.getAITasks(status, page, size);
    }

    @GetMapping("/{id}")
    public AITaskDTO getAITaskById(@PathVariable Long id) {
        return aiTaskService.getAITaskById(id);
    }

    @PostMapping("/{id}/retry")
    public void retryTask(@PathVariable Long id) {
        aiTaskService.retryTask(id);
    }

    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        aiTaskService.deleteTask(id);
    }

    @GetMapping("/stats")
    public Object getTaskStats() {
        return aiTaskService.getTaskStats();
    }
}
