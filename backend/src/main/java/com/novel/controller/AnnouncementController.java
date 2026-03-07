package com.novel.controller;

import com.novel.common.Result;
import com.novel.service.SystemAIConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 公告控制器
 */
@RestController
@RequestMapping("/announcement")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AnnouncementController {

    @Autowired
    private SystemAIConfigService configService;

    /**
     * 获取公告信息（前端用）
     */
    @GetMapping
    public Result<Map<String, Object>> getAnnouncement() {
        String enabled = configService.getConfig("announcement_enabled", "false");
        
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", "true".equalsIgnoreCase(enabled));
        
        if ("true".equalsIgnoreCase(enabled)) {
            data.put("title", configService.getConfig("announcement_title", "系统公告"));
            data.put("content", configService.getConfig("announcement_content", ""));
            data.put("updatedAt", configService.getConfig("announcement_updated_at", ""));
        }
        
        return Result.success(data);
    }
}
