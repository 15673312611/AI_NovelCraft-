package com.novel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.domain.entity.User;
import com.novel.domain.entity.UserCredit;
import com.novel.dto.AuthResponse;
import com.novel.dto.WechatAccessToken;
import com.novel.dto.WechatUserInfo;
import com.novel.repository.UserRepository;
import com.novel.repository.UserCreditRepository;
import com.novel.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信公众号登录服务
 */
@Service
public class WechatAuthService {

    private static final Logger logger = LoggerFactory.getLogger(WechatAuthService.class);

    // 微信公众号网页授权URL
    private static final String MP_AUTH_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String MP_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String MP_USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    @Autowired
    private SystemAIConfigService configService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCreditRepository userCreditRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 获取微信登录配置
     */
    public Map<String, Object> getWechatLoginConfig() {
        Map<String, Object> config = new HashMap<>();
        
        boolean mpEnabled = "true".equalsIgnoreCase(configService.getConfig("wechat_mp_enabled"));
        config.put("mpEnabled", mpEnabled);
        config.put("enabled", mpEnabled);
        
        if (mpEnabled) {
            config.put("mpAppId", configService.getConfig("wechat_mp_app_id"));
        }
        
        config.put("redirectUri", configService.getConfig("wechat_redirect_uri"));
        
        return config;
    }

    /**
     * 生成微信授权URL
     */
    public String generateAuthUrl(String type, String state) {
        if (!"true".equalsIgnoreCase(configService.getConfig("wechat_mp_enabled"))) {
            throw new RuntimeException("微信登录未启用");
        }

        String appId = configService.getConfig("wechat_mp_app_id");
        String redirectUri = configService.getConfig("wechat_redirect_uri");

        if (appId == null || appId.isEmpty()) {
            throw new RuntimeException("微信AppID未配置");
        }
        if (redirectUri == null || redirectUri.isEmpty()) {
            throw new RuntimeException("微信回调地址未配置");
        }

        try {
            String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString());
            // 使用snsapi_userinfo获取用户信息
            return String.format(
                "%s?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_userinfo&state=%s#wechat_redirect",
                MP_AUTH_URL, appId, encodedRedirectUri, state
            );
        } catch (Exception e) {
            throw new RuntimeException("生成授权URL失败", e);
        }
    }

    /**
     * 微信登录
     */
    @Transactional
    public AuthResponse loginWithWechat(String type, String code, String state) {
        if (!"true".equalsIgnoreCase(configService.getConfig("wechat_mp_enabled"))) {
            throw new RuntimeException("微信登录未启用");
        }

        // 1. 获取access_token
        WechatAccessToken tokenResponse = getAccessToken(code);
        if (!tokenResponse.isSuccess()) {
            logger.error("获取微信access_token失败: {}", tokenResponse.getErrmsg());
            throw new RuntimeException("微信授权失败: " + tokenResponse.getErrmsg());
        }

        // 2. 获取用户信息
        WechatUserInfo userInfo = getUserInfo(tokenResponse.getAccessToken(), tokenResponse.getOpenid());

        // 3. 查找或创建用户
        User user = findOrCreateUser(userInfo, tokenResponse);

        // 4. 更新登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        // 5. 生成JWT token
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());

        return new AuthResponse(user, token);
    }

    /**
     * 获取微信access_token
     */
    private WechatAccessToken getAccessToken(String code) {
        String appId = configService.getConfig("wechat_mp_app_id");
        String appSecret = configService.getConfig("wechat_mp_app_secret");

        String url = String.format(
            "%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
            MP_TOKEN_URL, appId, appSecret, code
        );

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readValue(response.getBody(), WechatAccessToken.class);
        } catch (Exception e) {
            logger.error("获取微信access_token异常", e);
            throw new RuntimeException("获取微信授权信息失败", e);
        }
    }

    /**
     * 获取微信用户信息
     */
    private WechatUserInfo getUserInfo(String accessToken, String openid) {
        String url = String.format(
            "%s?access_token=%s&openid=%s&lang=zh_CN",
            MP_USER_INFO_URL, accessToken, openid
        );

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readValue(response.getBody(), WechatUserInfo.class);
        } catch (Exception e) {
            logger.error("获取微信用户信息异常", e);
            throw new RuntimeException("获取微信用户信息失败", e);
        }
    }

    /**
     * 查找或创建用户
     */
    private User findOrCreateUser(WechatUserInfo userInfo, WechatAccessToken tokenResponse) {
        User user = userRepository.findByWechatOpenid(userInfo.getOpenid());
        
        if (user != null) {
            updateUserFromWechat(user, userInfo);
            userRepository.updateById(user);
            return user;
        }

        // 创建新用户
        user = new User();
        user.setUsername("wx_" + generateShortId());
        user.setEmail("wx_" + userInfo.getOpenid() + "@wechat.placeholder");
        user.setPassword("");
        user.setNickname(userInfo.getNickname());
        user.setAvatarUrl(userInfo.getHeadImgUrl());
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.insert(user);

        // 更新微信字段
        userRepository.updateWechatInfo(user.getId(), userInfo.getOpenid(), userInfo.getUnionid(), "WECHAT");

        // 初始化灵感点账户
        initUserCredits(user.getId());

        logger.info("微信用户注册成功: userId={}, openid={}", user.getId(), userInfo.getOpenid());
        return user;
    }

    /**
     * 更新用户微信信息
     */
    private void updateUserFromWechat(User user, WechatUserInfo userInfo) {
        if (userInfo.getNickname() != null && !userInfo.getNickname().isEmpty()) {
            user.setNickname(userInfo.getNickname());
        }
        if (userInfo.getHeadImgUrl() != null && !userInfo.getHeadImgUrl().isEmpty()) {
            user.setAvatarUrl(userInfo.getHeadImgUrl());
        }
        user.setUpdatedAt(LocalDateTime.now());
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
     * 生成短ID
     */
    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 绑定微信到现有账户
     */
    @Transactional
    public void bindWechat(Long userId, String type, String code) {
        WechatAccessToken tokenResponse = getAccessToken(code);
        if (!tokenResponse.isSuccess()) {
            throw new RuntimeException("微信授权失败: " + tokenResponse.getErrmsg());
        }

        User existingUser = userRepository.findByWechatOpenid(tokenResponse.getOpenid());
        if (existingUser != null && existingUser.getId() != null && !existingUser.getId().equals(userId)) {
            throw new RuntimeException("该微信已绑定其他账户");
        }

        WechatUserInfo userInfo = getUserInfo(tokenResponse.getAccessToken(), tokenResponse.getOpenid());
        userRepository.updateWechatInfo(userId, userInfo.getOpenid(), userInfo.getUnionid(), null);

        logger.info("用户绑定微信成功: userId={}, openid={}", userId, userInfo.getOpenid());
    }

    /**
     * 解绑微信
     */
    @Transactional
    public void unbindWechat(Long userId) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new RuntimeException("请先设置密码后再解绑微信");
        }

        userRepository.updateWechatInfo(userId, null, null, "PASSWORD");
        logger.info("用户解绑微信成功: userId={}", userId);
    }
}
