package com.novel.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 临时密码生成器 - 仅用于开发环境
 * 生产环境请删除此Controller
 */
@RestController
@RequestMapping("/dev")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PasswordGeneratorController {

    private final PasswordEncoder passwordEncoder;

    @GetMapping("/encode-password")
    public Map<String, String> encodePassword(@RequestParam String password) {
        String encoded = passwordEncoder.encode(password);
        
        Map<String, String> result = new HashMap<>();
        result.put("rawPassword", password);
        result.put("encodedPassword", encoded);
        result.put("sql", "UPDATE users SET password = '" + encoded + "' WHERE username = 'admin';");
        
        return result;
    }
    
    @GetMapping("/verify-password")
    public Map<String, Object> verifyPassword(
            @RequestParam String rawPassword,
            @RequestParam String encodedPassword) {
        
        boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
        
        Map<String, Object> result = new HashMap<>();
        result.put("rawPassword", rawPassword);
        result.put("encodedPassword", encodedPassword);
        result.put("matches", matches);
        
        return result;
    }
}
