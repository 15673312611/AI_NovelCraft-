package com.novel.controller;

import com.novel.service.CaptchaService;
import com.novel.service.EmailAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 邮箱验证码控制器
 */
@RestController
@RequestMapping("/auth/email")
public class EmailAuthController {

    @Autowired
    private EmailAuthService emailAuthService;

    @Autowired
    private CaptchaService captchaService;

    /**
     * 获取邮箱验证码配置
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of("enabled", emailAuthService.isEmailLoginEnabled()));
    }

    /**
     * 获取验证token
     */
    @PostMapping("/captcha/token")
    public ResponseEntity<Map<String, Object>> getToken(HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            Map<String, Object> result = captchaService.generateToken(ip);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 验证点击行为
     */
    @PostMapping("/captcha/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        String token = (String) requestBody.get("token");
        
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少验证token"));
        }
        
        try {
            String ip = getClientIp(request);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mouseTrack = (List<Map<String, Object>>) requestBody.get("mouseTrack");
            long clickTime = requestBody.get("clickTime") != null 
                ? ((Number) requestBody.get("clickTime")).longValue() 
                : 0L;
            
            boolean success = captchaService.verify(token, ip, mouseTrack, clickTime);
            if (success) {
                return ResponseEntity.ok(Map.of("success", true, "token", token));
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "验证失败，请重试"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 发送验证码
     */
    @PostMapping("/send-code")
    public ResponseEntity<Map<String, Object>> sendCode(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        String email = requestBody.get("email");
        String type = requestBody.get("type");
        String captchaToken = requestBody.get("captchaToken");

        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请输入邮箱地址"));
        }

        // 验证token
        String ip = getClientIp(request);
        if (captchaToken == null || !captchaService.consumeToken(captchaToken, ip)) {
            return ResponseEntity.badRequest().body(Map.of("message", "请先完成人机验证"));
        }

        try {
            emailAuthService.sendVerificationCode(email, type != null ? type : "REGISTER");
            return ResponseEntity.ok(Map.of("message", "验证码已发送"));
        } catch (Exception e) {
            // 透出更明确的错误信息（用于排查线上 SMTP 535/超时等问题）
            String msg = e.getMessage();
            Throwable c = e.getCause();
            if (c != null && c.getMessage() != null && !c.getMessage().isEmpty()) {
                msg = (msg == null ? "" : msg) + " | cause: " + c.getMessage();
            }
            return ResponseEntity.badRequest().body(Map.of("message", msg != null ? msg : "邮件发送失败"));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
