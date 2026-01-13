package com.novel.service;

import com.novel.dto.AuthResponse;
import com.novel.dto.LoginRequest;
import com.novel.dto.RegisterRequest;
import com.novel.dto.ChangePasswordRequest;
import com.novel.domain.entity.User;
import com.novel.domain.entity.UserCredit;
import com.novel.domain.entity.VerificationCode;
import com.novel.repository.UserRepository;
import com.novel.repository.UserCreditRepository;
import com.novel.repository.VerificationCodeRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 认证服务
 */
@Service
@Transactional
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCreditRepository userCreditRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;
    
    @Autowired
    private com.novel.repository.NovelRepository novelRepository;

    @Autowired
    private SystemAIConfigService configService;

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
        // 验证邮箱验证码
        VerificationCode verificationCode = verificationCodeRepository.findValidCode(
            request.getEmail(), request.getCode(), "REGISTER"
        );
        if (verificationCode == null) {
            throw new RuntimeException("验证码无效或已过期");
        }

        // 标记验证码为已使用
        verificationCodeRepository.markAsUsed(verificationCode.getId());

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
        user.setEmailVerified(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setLastLoginTime(LocalDateTime.now());

        // 保存用户
        userRepository.insert(user);

        // 初始化灵感点账户
        initUserCredits(user.getId());

        // 生成JWT Token
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());

        // 清除密码字段
        user.setPassword(null);

        return new AuthResponse(user, token);
    }

    /**
     * 初始化用户灵感点
     */
    private void initUserCredits(Long userId) {
        try {
            String giftCreditsStr = configService.getConfig("new_user_gift_credits");
            BigDecimal giftCredits = new BigDecimal(giftCreditsStr != null ? giftCreditsStr : "100");

            UserCredit credit = new UserCredit();
            credit.setUserId(userId);
            credit.setBalance(giftCredits);
            credit.setTotalGifted(giftCredits);
            credit.setTotalRecharged(BigDecimal.ZERO);
            credit.setTotalConsumed(BigDecimal.ZERO);
            credit.setFrozenAmount(BigDecimal.ZERO);
            credit.setCreatedAt(LocalDateTime.now());
            credit.setUpdatedAt(LocalDateTime.now());

            userCreditRepository.insert(credit);
        } catch (Exception e) {
            // 忽略灵感点初始化失败
        }
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
     * 注意：用户名和邮箱不允许修改，此方法仅用于更新其他可修改字段
     */
    public User updateProfile(String username, User profileData) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 用户名和邮箱不允许修改，直接返回当前用户信息
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
     * 获取用户统计信息
     */
    public java.util.Map<String, Object> getUserStatistics() {
        Long userId = com.novel.common.security.AuthUtils.getCurrentUserId();
        
        // 使用QueryWrapper查询用户的所有小说
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.novel.domain.entity.Novel> queryWrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("author_id", userId);
        java.util.List<com.novel.domain.entity.Novel> novels = novelRepository.selectList(queryWrapper);
        
        // 计算总字数
        int totalWords = novels.stream()
            .mapToInt(novel -> novel.getWordCount() != null ? novel.getWordCount() : 0)
            .sum();
        
        // 计算小说数量
        int novelCount = novels.size();
        
        // 计算章节数量
        int chapterCount = novels.stream()
            .mapToInt(novel -> novel.getChapterCount() != null ? novel.getChapterCount() : 0)
            .sum();
        
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalWords", totalWords);
        stats.put("novelCount", novelCount);
        stats.put("chapterCount", chapterCount);
        
        return stats;
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