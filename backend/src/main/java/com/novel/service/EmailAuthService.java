package com.novel.service;

import com.novel.domain.entity.User;
import com.novel.domain.entity.UserCredit;
import com.novel.domain.entity.VerificationCode;
import com.novel.dto.AuthResponse;
import com.novel.repository.UserRepository;
import com.novel.repository.UserCreditRepository;
import com.novel.repository.VerificationCodeRepository;
import com.novel.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * 邮箱验证码登录服务
 */
@Service
public class EmailAuthService {

    private static final Logger logger = LoggerFactory.getLogger(EmailAuthService.class);

    @Autowired
    private SystemAIConfigService configService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCreditRepository userCreditRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    /**
     * 发送验证码
     */
    public void sendVerificationCode(String email, String type) {
        // 检查邮箱登录是否启用
        if (!"true".equalsIgnoreCase(configService.getConfig("email_login_enabled"))) {
            throw new RuntimeException("邮箱验证码登录未启用");
        }

        // 验证邮箱格式
        if (!isValidEmail(email)) {
            throw new RuntimeException("邮箱格式不正确");
        }

    // 防刷：30秒内只能发送1次（同一邮箱）
        int recentCount = verificationCodeRepository.countRecentCodes(email, LocalDateTime.now().minusSeconds(30));
        if (recentCount > 0) {
            throw new RuntimeException("发送太频繁，请30秒后再试");
        }

        // 防刷：1小时内最多10次（同一邮箱）
        int hourlyCount = verificationCodeRepository.countRecentCodes(email, LocalDateTime.now().minusHours(1));
        if (hourlyCount >= 10) {
            throw new RuntimeException("发送次数过多，请1小时后再试");
        }

        // 生成6位验证码
        String code = generateCode();

        // 获取过期时间配置
        String expireMinutesStr = configService.getConfig("email_code_expire_minutes");
        int expireMinutes = 5;
        try {
            expireMinutes = Integer.parseInt(expireMinutesStr);
        } catch (Exception ignored) {}

        // 保存验证码
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setType(type != null ? type : "LOGIN");
        verificationCode.setUsed(false);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(expireMinutes));
        verificationCode.setCreatedAt(LocalDateTime.now());
        verificationCodeRepository.insert(verificationCode);

        // 发送邮件
        emailService.sendVerificationCode(email, code);

        logger.info("验证码发送成功: email={}, type={}", email, type);
    }

    /**
     * 验证码登录
     */
    @Transactional
    public AuthResponse loginWithCode(String email, String code) {
        // 检查邮箱登录是否启用
        if (!"true".equalsIgnoreCase(configService.getConfig("email_login_enabled"))) {
            throw new RuntimeException("邮箱验证码登录未启用");
        }

        // 验证验证码
        VerificationCode verificationCode = verificationCodeRepository.findValidCode(email, code, "LOGIN");
        if (verificationCode == null) {
            throw new RuntimeException("验证码无效或已过期");
        }

        // 标记验证码为已使用
        verificationCodeRepository.markAsUsed(verificationCode.getId());

        // 查找或创建用户
        User user = findOrCreateUser(email);

        // 更新登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        // 生成JWT token
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());

        logger.info("邮箱验证码登录成功: userId={}, email={}", user.getId(), email);

        return new AuthResponse(user, token);
    }

    /**
     * 查找或创建用户
     */
    private User findOrCreateUser(String email) {
        User user = userRepository.findByEmail(email);

        if (user != null) {
            return user;
        }

        // 创建新用户
        user = new User();
        user.setUsername("user_" + generateShortId());
        user.setEmail(email);
        user.setPassword(""); // 邮箱登录用户无密码
        user.setNickname(maskEmail(email));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true); // 邮箱验证码登录视为已验证
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.insert(user);

        // 初始化灵感点账户
        initUserCredits(user.getId());

        logger.info("邮箱用户注册成功: userId={}, email={}", user.getId(), email);
        return user;
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
            logger.error("初始化用户灵感点失败: userId={}", userId, e);
        }
    }

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * 生成短ID
     */
    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    /**
     * 遮蔽邮箱显示
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return email.substring(0, 1) + "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    /**
     * 检查邮箱登录是否启用
     */
    public boolean isEmailLoginEnabled() {
        return "true".equalsIgnoreCase(configService.getConfig("email_login_enabled"));
    }
}
