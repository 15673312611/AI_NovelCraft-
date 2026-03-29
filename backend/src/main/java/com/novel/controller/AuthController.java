package com.novel.controller;

import com.novel.domain.entity.User;
import com.novel.dto.ApiResponse;
import com.novel.dto.AuthResponse;
import com.novel.dto.ChangePasswordRequest;
import com.novel.dto.LoginRequest;
import com.novel.dto.RegisterRequest;
import com.novel.dto.SafeUserDto;
import com.novel.service.AuthService;
import com.novel.service.UserAuthActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAuthActivityLogService userAuthActivityLogService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            AuthResponse response = authService.login(request);
            recordAuthActivity(response, UserAuthActivityLogService.EventType.LOGIN, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            AuthResponse response = authService.register(request);
            recordAuthActivity(response, UserAuthActivityLogService.EventType.REGISTER, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<SafeUserDto>> getProfile() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = authService.getUserByUsername(username);
            return ResponseEntity.ok(ApiResponse.success(SafeUserDto.from(user)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            authService.changePassword(username, request);
            return ResponseEntity.ok(ApiResponse.success("密码修改成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {
        try {
            SecurityContextHolder.clearContext();
            return ResponseEntity.ok(ApiResponse.success("登出成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserStatistics() {
        try {
            Map<String, Object> stats = authService.getUserStatistics();
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void recordAuthActivity(AuthResponse response, UserAuthActivityLogService.EventType eventType, HttpServletRequest httpRequest) {
        if (response == null || response.getUser() == null || response.getUser().getId() == null) {
            return;
        }
        userAuthActivityLogService.recordSuccessfulAuth(response.getUser().getId(), eventType, httpRequest);
    }
}
