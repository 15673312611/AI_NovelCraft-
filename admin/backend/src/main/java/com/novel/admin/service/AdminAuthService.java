package com.novel.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel.admin.dto.LoginResponse;
import com.novel.admin.entity.User;
import com.novel.admin.mapper.UserMapper;
import com.novel.admin.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(String username, String password) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // Check user status
        if ("BANNED".equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }

        // Check if user has admin role - first try user_roles table, then fallback to users.role field
        List<String> roles = userMapper.selectRolesByUserId(user.getId());
        boolean isAdmin = false;
        
        if (roles != null && !roles.isEmpty()) {
            // 从 user_roles 表查询到角色
            isAdmin = roles.contains("ADMIN");
        } else {
            // 回退到 users 表的 role 字段
            isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
            if (isAdmin) {
                roles = List.of("ADMIN");
            }
        }
        
        if (!isAdmin) {
            throw new RuntimeException("无管理员权限");
        }

        String token = jwtUtil.generateToken(user.getUsername());
        String roleStr = roles != null && !roles.isEmpty() ? String.join(",", roles) : "";
        return new LoginResponse(token, user.getUsername(), roleStr);
    }

    public Object getCurrentUser() {
        // TODO: 从SecurityContext获取当前用户
        return null;
    }
}
