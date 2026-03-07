package com.novel.admin.controller;

import com.novel.admin.entity.AIModel;
import com.novel.admin.entity.SystemAIConfig;
import com.novel.admin.service.AdminAIModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端AI模型控制器
 */
@RestController
@RequestMapping("/ai-models")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminAIModelController {

    private final AdminAIModelService modelService;

    /**
     * 获取所有模型
     */
    @GetMapping
    public ResponseEntity<List<AIModel>> getAllModels() {
        return ResponseEntity.ok(modelService.getAllModels());
    }

    /**
     * 获取可用模型
     */
    @GetMapping("/available")
    public ResponseEntity<List<AIModel>> getAvailableModels() {
        return ResponseEntity.ok(modelService.getAvailableModels());
    }

    /**
     * 获取模型详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<AIModel> getModel(@PathVariable Long id) {
        AIModel model = modelService.getModel(id);
        if (model == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(model);
    }

    /**
     * 创建模型
     */
    @PostMapping
    public ResponseEntity<AIModel> createModel(@RequestBody AIModel model) {
        return ResponseEntity.ok(modelService.createModel(model));
    }

    /**
     * 更新模型
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateModel(
            @PathVariable Long id,
            @RequestBody AIModel model) {
        model.setId(id);
        boolean success = modelService.updateModel(model);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "更新成功" : "更新失败");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable Long id) {
        boolean success = modelService.deleteModel(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "删除成功" : "删除失败");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 设置默认模型
     */
    @PostMapping("/{id}/set-default")
    public ResponseEntity<Map<String, Object>> setDefaultModel(@PathVariable Long id) {
        boolean success = modelService.setDefaultModel(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "设置成功" : "设置失败");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 切换模型可用状态
     */
    @PostMapping("/{id}/toggle-available")
    public ResponseEntity<Map<String, Object>> toggleAvailable(@PathVariable Long id) {
        boolean success = modelService.toggleAvailable(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "切换成功" : "切换失败");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有系统配置
     */
    @GetMapping("/configs")
    public ResponseEntity<List<SystemAIConfig>> getAllConfigs() {
        return ResponseEntity.ok(modelService.getAllConfigs());
    }

    /**
     * 获取API配置
     */
    @GetMapping("/api-configs")
    public ResponseEntity<Map<String, Map<String, String>>> getAPIConfigs() {
        return ResponseEntity.ok(modelService.getAPIConfigs());
    }

    /**
     * 更新API配置
     */
    @PostMapping("/api-configs/{provider}")
    public ResponseEntity<Map<String, Object>> updateAPIConfig(
            @PathVariable String provider,
            @RequestBody Map<String, String> config) {
        
        modelService.updateAPIConfig(provider, config.get("apiKey"), config.get("baseUrl"));
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "配置更新成功");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取系统设置
     */
    @GetMapping("/system-settings")
    public ResponseEntity<Map<String, String>> getSystemSettings() {
        return ResponseEntity.ok(modelService.getSystemSettings());
    }

    /**
     * 更新系统设置
     */
    @PostMapping("/system-settings")
    public ResponseEntity<Map<String, Object>> updateSystemSettings(
            @RequestBody Map<String, String> settings) {
        
        modelService.updateSystemSettings(settings);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "设置更新成功");
        
        return ResponseEntity.ok(response);
    }
}
