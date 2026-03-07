package com.novel.admin.controller;

import com.novel.admin.service.AdminSystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 后台公告管理控制器
 */
@RestController
@RequestMapping("/announcement")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AdminSystemConfigService configService;

    /**
     * 获取公告配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnnouncement() {
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", "true".equalsIgnoreCase(configService.getConfigValue("announcement_enabled", "false")));
        data.put("title", configService.getConfigValue("announcement_title", "系统公告"));
        data.put("content", configService.getConfigValue("announcement_content", ""));
        data.put("updatedAt", configService.getConfigValue("announcement_updated_at", ""));
        return ResponseEntity.ok(data);
    }

    /**
     * 更新公告配置
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> updateAnnouncement(@RequestBody Map<String, Object> request) {
        Boolean enabled = (Boolean) request.get("enabled");
        String title = (String) request.get("title");
        String content = (String) request.get("content");

        if (enabled != null) {
            configService.setConfigValue("announcement_enabled", enabled.toString(), false);
        }
        if (title != null) {
            configService.setConfigValue("announcement_title", title, false);
        }
        if (content != null) {
            configService.setConfigValue("announcement_content", content, false);
            // 更新时间戳，用于前端判断是否需要重新弹出
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            configService.setConfigValue("announcement_updated_at", timestamp, false);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "公告更新成功");
        return ResponseEntity.ok(result);
    }

    /**
     * 切换公告开关
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleAnnouncement() {
        String current = configService.getConfigValue("announcement_enabled", "false");
        boolean newValue = !"true".equalsIgnoreCase(current);
        configService.setConfigValue("announcement_enabled", String.valueOf(newValue), false);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("enabled", newValue);
        result.put("message", newValue ? "公告已开启" : "公告已关闭");
        return ResponseEntity.ok(result);
    }
}
