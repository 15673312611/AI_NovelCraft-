package com.novel.admin.controller;

import com.novel.admin.dto.LoginRequest;
import com.novel.admin.dto.LoginResponse;
import com.novel.admin.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }

    @PostMapping("/logout")
    public void logout() {
        // 实现登出逻辑
    }

    @GetMapping("/current")
    public Object getCurrentUser() {
        return authService.getCurrentUser();
    }
}
