package com.novel.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/system")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminSystemController {

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("openaiApiKey", "");
        config.put("openaiModel", "gpt-4");
        config.put("maxTokens", 4000);
        config.put("temperature", 0.7);
        config.put("qimaoEnabled", false);
        config.put("qimaoInterval", 60);
        config.put("qimaoMaxRetry", 3);
        config.put("maxUploadSize", 10);
        config.put("sessionTimeout", 30);
        return config;
    }

    @PostMapping("/config")
    public void saveConfig(@RequestBody Map<String, Object> config) {
        // 保存配置逻辑
    }
}
