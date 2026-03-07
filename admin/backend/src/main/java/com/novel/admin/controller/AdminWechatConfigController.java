package com.novel.admin.controller;

import com.novel.admin.service.AdminSystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信公众号登录配置管理
 */
@RestController
@RequestMapping("/admin/wechat-config")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AdminWechatConfigController {

    private final AdminSystemConfigService configService;

    /**
     * 获取微信登录配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("mpEnabled", "true".equalsIgnoreCase(configService.getConfigValue("wechat_mp_enabled")));
        config.put("mpAppId", configService.getConfigValue("wechat_mp_app_id"));
        config.put("mpAppSecret", maskSecret(configService.getConfigValue("wechat_mp_app_secret")));
        config.put("redirectUri", configService.getConfigValue("wechat_redirect_uri"));
        return ResponseEntity.ok(config);
    }

    /**
     * 更新微信登录配置
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> config) {
        try {
            if (config.containsKey("mpEnabled")) {
                configService.setConfigValue("wechat_mp_enabled", 
                    String.valueOf(config.get("mpEnabled")), false);
            }
            if (config.containsKey("mpAppId")) {
                configService.setConfigValue("wechat_mp_app_id", 
                    (String) config.get("mpAppId"), false);
            }
            if (config.containsKey("mpAppSecret")) {
                String secret = (String) config.get("mpAppSecret");
                if (secret != null && !secret.contains("*")) {
                    configService.setConfigValue("wechat_mp_app_secret", secret, true);
                }
            }
            if (config.containsKey("redirectUri")) {
                configService.setConfigValue("wechat_redirect_uri", 
                    (String) config.get("redirectUri"), false);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "配置保存成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "配置保存失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 测试配置
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConfig(@RequestBody(required = false) Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        
        String appId = configService.getConfigValue("wechat_mp_app_id");
        String appSecret = configService.getConfigValue("wechat_mp_app_secret");
        String redirectUri = configService.getConfigValue("wechat_redirect_uri");
        
        boolean valid = true;
        StringBuilder message = new StringBuilder();
        
        if (appId == null || appId.isEmpty()) {
            valid = false;
            message.append("AppID未配置; ");
        }
        
        if (appSecret == null || appSecret.isEmpty()) {
            valid = false;
            message.append("AppSecret未配置; ");
        }
        
        if (redirectUri == null || redirectUri.isEmpty()) {
            valid = false;
            message.append("回调地址未配置; ");
        }
        
        result.put("valid", valid);
        result.put("message", valid ? "配置验证通过" : message.toString());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 掩码敏感信息
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
