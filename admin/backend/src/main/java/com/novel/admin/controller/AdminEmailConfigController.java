package com.novel.admin.controller;

import com.novel.admin.service.AdminSystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 邮箱配置管理控制器
 */
@RestController
@RequestMapping("/api/admin/email-config")
public class AdminEmailConfigController {

    @Autowired
    private AdminSystemConfigService configService;

    /**
     * 获取邮箱配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", "true".equalsIgnoreCase(configService.getConfigValue("email_login_enabled")));
        config.put("smtpHost", configService.getConfigValue("email_smtp_host"));
        config.put("smtpPort", configService.getConfigValue("email_smtp_port"));
        config.put("smtpSsl", "true".equalsIgnoreCase(configService.getConfigValue("email_smtp_ssl")));
        config.put("smtpUsername", configService.getConfigValue("email_smtp_username"));
        config.put("smtpPassword", configService.getConfigValue("email_smtp_password"));
        config.put("fromName", configService.getConfigValue("email_from_name"));
        config.put("codeExpireMinutes", configService.getConfigValue("email_code_expire_minutes"));
        return ResponseEntity.ok(config);
    }

    /**
     * 更新邮箱配置
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> request) {
        try {
            if (request.containsKey("enabled")) {
                configService.setConfigValue("email_login_enabled", String.valueOf(request.get("enabled")), false);
            }
            if (request.containsKey("smtpHost")) {
                configService.setConfigValue("email_smtp_host", (String) request.get("smtpHost"), false);
            }
            if (request.containsKey("smtpPort")) {
                configService.setConfigValue("email_smtp_port", String.valueOf(request.get("smtpPort")), false);
            }
            if (request.containsKey("smtpSsl")) {
                configService.setConfigValue("email_smtp_ssl", String.valueOf(request.get("smtpSsl")), false);
            }
            if (request.containsKey("smtpUsername")) {
                configService.setConfigValue("email_smtp_username", (String) request.get("smtpUsername"), false);
            }
            if (request.containsKey("smtpPassword")) {
                String password = (String) request.get("smtpPassword");
                if (password != null && !password.isEmpty()) {
                    configService.setConfigValue("email_smtp_password", password, true);
                }
            }
            if (request.containsKey("fromName")) {
                configService.setConfigValue("email_from_name", (String) request.get("fromName"), false);
            }
            if (request.containsKey("codeExpireMinutes")) {
                configService.setConfigValue("email_code_expire_minutes", String.valueOf(request.get("codeExpireMinutes")), false);
            }

            return ResponseEntity.ok(Map.of("message", "配置保存成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "保存失败: " + e.getMessage()));
        }
    }

    /**
     * 测试邮箱配置
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConfig(@RequestBody Map<String, String> request) {
        String testEmail = request.get("testEmail");
        if (testEmail == null || testEmail.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "请输入测试邮箱地址"));
        }

        try {
            // 这里简单验证配置是否完整
            String host = configService.getConfigValue("email_smtp_host");
            String username = configService.getConfigValue("email_smtp_username");
            String password = configService.getConfigValue("email_smtp_password");

            if (host == null || host.isEmpty()) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "SMTP服务器未配置"));
            }
            if (username == null || username.isEmpty()) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "SMTP用户名未配置"));
            }
            if (password == null || password.isEmpty()) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "SMTP授权码未配置"));
            }

            return ResponseEntity.ok(Map.of("valid", true, "message", "配置验证通过，请保存后在客户端测试发送"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false, "message", "配置验证失败: " + e.getMessage()));
        }
    }
}
