package com.novel.controller;

import com.novel.common.Result;
import com.novel.dto.AuthResponse;
import com.novel.dto.WechatLoginRequest;
import com.novel.service.WechatAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wechat Login Controller
 * Supports both Open Platform (PC) and MP (H5)
 */
@RestController
@RequestMapping("/auth/wechat")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class WechatAuthController {

    @Autowired
    private WechatAuthService wechatAuthService;

    /**
     * Get wechat login config
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = wechatAuthService.getWechatLoginConfig();
        return Result.success(config);
    }

    /**
     * Get wechat auth URL
     * @param type "open" for PC QR code, "mp" for H5 web auth
     */
    @GetMapping("/auth-url")
    public Result<Map<String, String>> getAuthUrl(
            @RequestParam(defaultValue = "open") String type) {
        try {
            String state = UUID.randomUUID().toString().replace("-", "");
            String authUrl = wechatAuthService.generateAuthUrl(type, state);
            
            Map<String, String> data = new HashMap<>();
            data.put("authUrl", authUrl);
            data.put("state", state);
            data.put("type", type);
            
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Wechat login
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody WechatLoginRequest request) {
        try {
            String type = request.getType() != null ? request.getType() : "open";
            AuthResponse response = wechatAuthService.loginWithWechat(type, request.getCode(), request.getState());
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Wechat callback (GET for redirect)
     */
    @GetMapping("/callback")
    public Result<AuthResponse> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            @RequestParam(value = "type", defaultValue = "open") String type) {
        try {
            AuthResponse response = wechatAuthService.loginWithWechat(type, code, state);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

}
