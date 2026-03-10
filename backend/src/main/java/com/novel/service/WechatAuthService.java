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
 * 鐎甸偊鍠曟穱濠囧礂椤戣法鑸归柛娆擃棑濞呫儴銇愰弴鐔哥疀闁?
 */
@Service
public class WechatAuthService {

    private static final Logger logger = LoggerFactory.getLogger(WechatAuthService.class);

    // 鐎甸偊鍠曟穱濠囧礂椤戣法鑸归柛娆擃棑缂嶅銇勯崹顐㈡埧闁哄鍎濺L
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
     * 闁兼儳鍢茶ぐ鍥ь嚗椤旇绻嗛柣褑顕х紞宥夋煀瀹ュ洨鏋?
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
     * 闁汇垻鍠愰崹姘嚗椤旇绻嗛柟鍝勭墛濞煎湶RL
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
            // 濞达綀娉曢弫顦檔sapi_userinfo闁兼儳鍢茶ぐ鍥偨閵婏箑鐓曞ǎ鍥ｅ墲娴?
            return String.format(
                "%s?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_userinfo&state=%s#wechat_redirect",
                MP_AUTH_URL, appId, encodedRedirectUri, state
            );
        } catch (Exception e) {
            throw new RuntimeException("生成微信授权URL失败", e);
        }
    }

    /**
     * 鐎甸偊鍠曟穱濠囨儌鐠囪尙绉?
     */
    @Transactional
    public AuthResponse loginWithWechat(String type, String code, String state) {
        if (!"true".equalsIgnoreCase(configService.getConfig("wechat_mp_enabled"))) {
            throw new RuntimeException("微信登录未启用");
        }

        // 1. 闁兼儳鍢茶ぐ鍢篶cess_token
        WechatAccessToken tokenResponse = getAccessToken(code);
        if (!tokenResponse.isSuccess()) {
            logger.error("获取微信access_token失败: {}", tokenResponse.getErrmsg());
            throw new RuntimeException("微信授权失败: " + tokenResponse.getErrmsg());
        }

        // 2. 闁兼儳鍢茶ぐ鍥偨閵婏箑鐓曞ǎ鍥ｅ墲娴?
        WechatUserInfo userInfo = getUserInfo(tokenResponse.getAccessToken(), tokenResponse.getOpenid());

        // 3. 闁哄被鍎叉竟姗€骞嬮弽褍鐏＄€点倛娅ｉ弫銈夊箣?
        User user = findOrCreateUser(userInfo, tokenResponse);

        // 4. 闁哄洤鐡ㄩ弻濠囨儌鐠囪尙绉块柡鍐ㄧ埣濡?
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        // 5. 闁汇垻鍠愰崹娆絎T token
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());

        return new AuthResponse(user, token);
    }

    /**
     * 闁兼儳鍢茶ぐ鍥ь嚗椤旇绻哸ccess_token
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
            logger.error("获取微信access_token失败", e);
            throw new RuntimeException("获取微信access_token失败", e);
        }
    }

    /**
     * 闁兼儳鍢茶ぐ鍥ь嚗椤旇绻嗛柣顫妽閸╂稒绌遍埄鍐х礀
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
            logger.error("获取微信用户信息失败", e);
            throw new RuntimeException("获取微信用户信息失败", e);
        }
    }

    /**
     * 闁哄被鍎叉竟姗€骞嬮弽褍鐏＄€点倛娅ｉ弫銈夊箣?
     */
    private User findOrCreateUser(WechatUserInfo userInfo, WechatAccessToken tokenResponse) {
        User user = userRepository.findByWechatOpenid(userInfo.getOpenid());
        
        if (user != null) {
            updateUserFromWechat(user, userInfo);
            userRepository.updateById(user);
            return user;
        }

        // 闁告帗绋戠紓鎾诲棘閹殿喗鏆忛柟?
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

        // 闁哄洤鐡ㄩ弻濠傤嚗椤旇绻嗛悗娑欘殕椤?
        userRepository.updateWechatInfo(user.getId(), userInfo.getOpenid(), userInfo.getUnionid(), "WECHAT");

        // 闁告帗绻傞～鎰板礌閺嶎偂绱ラ柟鎵枔閸嬶絿鎷归敂钘夌厱
        initUserCredits(user.getId());

        logger.info("微信用户注册成功: userId={}, openid={}", user.getId(), userInfo.getOpenid());
        return user;
    }

    /**
     * 闁哄洤鐡ㄩ弻濠囨偨閵婏箑鐓曠€甸偊鍠曟穱濠冪┍閳╁啩绱?
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
     * 闁告帗绻傞～鎰板礌閺嶎偅鏆忛柟鎾棑娴兼帡骞囬悢鍝勪化
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
            logger.error("初始化用户积分失败: userId={}", userId, e);
        }
    }

    /**
     * 闁汇垻鍠愰崹姘舵儗閻犲
     */
    private String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

}
