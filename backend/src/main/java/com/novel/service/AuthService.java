package com.novel.service;

import com.novel.dto.AuthResponse;
import com.novel.dto.LoginRequest;
import com.novel.dto.RegisterRequest;
import com.novel.dto.ChangePasswordRequest;
import com.novel.domain.entity.User;
import com.novel.repository.UserRepository;
import com.novel.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 认证服务
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@Service
@Transactional
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * 用户登录
     */
    public AuthResponse login(LoginRequest request) {
        // 根据用户名或邮箱查找用户
        User user = findUserByUsernameOrEmail(request.getUsernameOrEmail());
        if (user == null) {
            throw new BadCredentialsException("用户名或邮箱不存在");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("密码错误");
        }

        // 检查用户状态
        if (!User.UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new BadCredentialsException("用户账号已被禁用");
        }

        // 生成JWT Token（包含用户ID和用户名）
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.updateById(user);

        // 清除密码字段
        user.setPassword(null);

        return new AuthResponse(user, token);
    }

    /**
     * 用户注册
     */
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        User existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查邮箱是否已存在
        existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser != null) {
            throw new RuntimeException("邮箱已存在");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setLastLoginTime(LocalDateTime.now());

        // 保存用户
        userRepository.insert(user);

        // 生成JWT Token（包含用户ID和用户名）
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());

        // 清除密码字段
        user.setPassword(null);

        return new AuthResponse(user, token);
    }

    /**
     * 根据用户名获取用户
     */
    public User getUserByUsername(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 清除密码字段
        user.setPassword(null);
        return user;
    }

    /**
     * 更新用户资料
     */
    public User updateProfile(String username, User profileData) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 只更新允许修改的字段
        if (profileData.getEmail() != null && !profileData.getEmail().equals(user.getEmail())) {
            // 检查新邮箱是否已被其他用户使用
            User existingUserWithEmail = userRepository.findByEmail(profileData.getEmail());
            if (existingUserWithEmail != null && !existingUserWithEmail.getId().equals(user.getId())) {
                throw new RuntimeException("邮箱已被其他用户使用");
            }
            user.setEmail(profileData.getEmail());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);

        // 清除密码字段
        user.setPassword(null);
        return user;
    }

    /**
     * 修改密码
     */
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证原密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
    }

    /**
     * 刷新Token
     */
    public AuthResponse refreshToken(String username) {
        User user = getUserByUsername(username);
        String newToken = jwtTokenUtil.generateToken(user.getId(), username);
        return new AuthResponse(user, newToken);
    }

    /**
     * 根据用户名或邮箱查找用户
     */
    private User findUserByUsernameOrEmail(String usernameOrEmail) {
        // 先尝试按用户名查找
        User user = userRepository.findByUsername(usernameOrEmail);
        if (user != null) {
            return user;
        }
        
        // 再尝试按邮箱查找
        return userRepository.findByEmail(usernameOrEmail);
    }
}