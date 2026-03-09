package com.novel.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.admin.entity.CreditPackage;
import com.novel.admin.entity.CreditTransaction;
import com.novel.admin.entity.UserCredit;
import com.novel.admin.mapper.CreditPackageMapper;
import com.novel.admin.mapper.CreditTransactionMapper;
import com.novel.admin.mapper.SystemAIConfigMapper;
import com.novel.admin.mapper.UserCreditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminCreditService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_PAY_TYPES = Arrays.stream(new String[]{"alipay", "wxpay", "qqpay", "cashier"}).collect(Collectors.toSet());

    private final UserCreditMapper userCreditMapper;
    private final CreditTransactionMapper transactionMapper;
    private final CreditPackageMapper packageMapper;
    private final SystemAIConfigMapper configMapper;

    public IPage<UserCredit> getUserCredits(int page, int size, String keyword) {
        Page<UserCredit> pageParam = new Page<>(page + 1, size);
        if (keyword != null && !keyword.isEmpty()) {
            return userCreditMapper.searchWithUserInfo(pageParam, keyword);
        }
        return userCreditMapper.selectWithUserInfo(pageParam);
    }

    public UserCredit getUserCredit(Long userId) {
        return userCreditMapper.selectByUserIdWithInfo(userId);
    }

    @Transactional
    public boolean recharge(Long userId, BigDecimal amount, String description, Long operatorId) {
        UserCredit credit = userCreditMapper.selectByUserIdWithInfo(userId);
        if (credit == null) {
            userCreditMapper.createAccount(userId, amount);
            credit = userCreditMapper.selectByUserIdWithInfo(userId);
        } else {
            userCreditMapper.addRecharge(userId, amount);
        }

        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setType("RECHARGE");
        transaction.setAmount(amount);
        transaction.setBalanceBefore(credit.getBalance());
        transaction.setBalanceAfter(credit.getBalance().add(amount));
        transaction.setDescription(description != null ? description : "管理员充值");
        transaction.setOperatorId(operatorId);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);

        return true;
    }

    @Transactional
    public boolean gift(Long userId, BigDecimal amount, String description, Long operatorId) {
        UserCredit credit = userCreditMapper.selectByUserIdWithInfo(userId);
        if (credit == null) {
            userCreditMapper.createAccount(userId, amount);
            credit = userCreditMapper.selectByUserIdWithInfo(userId);
        } else {
            userCreditMapper.addGift(userId, amount);
        }

        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setType("GIFT");
        transaction.setAmount(amount);
        transaction.setBalanceBefore(credit.getBalance());
        transaction.setBalanceAfter(credit.getBalance().add(amount));
        transaction.setDescription(description != null ? description : "管理员赠送");
        transaction.setOperatorId(operatorId);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);

        return true;
    }

    @Transactional
    public boolean adjustBalance(Long userId, BigDecimal amount, String description, Long operatorId) {
        UserCredit credit = userCreditMapper.selectByUserIdWithInfo(userId);
        if (credit == null) {
            return false;
        }

        BigDecimal newBalance = credit.getBalance().add(amount);
        credit.setBalance(newBalance);
        userCreditMapper.updateById(credit);

        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setType("ADMIN_ADJUST");
        transaction.setAmount(amount);
        transaction.setBalanceBefore(credit.getBalance().subtract(amount));
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription(description != null ? description : "管理员调整");
        transaction.setOperatorId(operatorId);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);

        return true;
    }

    public IPage<CreditTransaction> getTransactions(int page, int size, Long userId, String type) {
        Page<CreditTransaction> pageParam = new Page<>(page + 1, size);
        if (userId != null) {
            return transactionMapper.selectByUserIdWithInfo(pageParam, userId);
        }
        if (type != null && !type.isEmpty()) {
            return transactionMapper.selectByTypeWithInfo(pageParam, type);
        }
        return transactionMapper.selectWithUserInfo(pageParam);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime thisMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        stats.put("totalBalance", userCreditMapper.sumTotalBalance());
        stats.put("totalConsumed", userCreditMapper.sumTotalConsumed());
        stats.put("totalRecharged", userCreditMapper.sumTotalRecharged());
        stats.put("todayConsumed", transactionMapper.sumConsumedSince(today));
        stats.put("todayRecharged", transactionMapper.sumRechargedSince(today));
        stats.put("monthConsumed", transactionMapper.sumConsumedSince(thisMonth));
        stats.put("monthRecharged", transactionMapper.sumRechargedSince(thisMonth));

        return stats;
    }

    public List<Map<String, Object>> getModelUsageStats(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        return transactionMapper.getModelUsageStats(startTime);
    }

    public List<CreditPackage> getAllPackages() {
        return packageMapper.selectAllOrdered();
    }

    public CreditPackage createPackage(CreditPackage pkg) {
        pkg.setCreatedAt(LocalDateTime.now());
        pkg.setUpdatedAt(LocalDateTime.now());
        packageMapper.insert(pkg);
        return pkg;
    }

    public CreditPackage updatePackage(Long id, CreditPackage pkg) {
        pkg.setId(id);
        pkg.setUpdatedAt(LocalDateTime.now());
        packageMapper.updateById(pkg);
        return pkg;
    }

    public boolean deletePackage(Long id) {
        return packageMapper.deleteById(id) > 0;
    }

    public String getRegistrationBonus() {
        String value = configMapper.getValueByKey("new_user_gift_credits");
        return value != null ? value : "0";
    }

    public void updateRegistrationBonus(String amount) {
        configMapper.upsertConfig("new_user_gift_credits", amount, "新用户注册送字数", false);
    }

    public Map<String, Object> getPaymentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", Boolean.parseBoolean(getConfigOrDefault("payment_yipay_enabled", "false")));
        config.put("gatewayUrl", getConfigOrDefault("payment_yipay_gateway_url", ""));
        config.put("pid", getConfigOrDefault("payment_yipay_pid", ""));
        String yipayKey = getConfigOrDefault("payment_yipay_key", "");
        config.put("key", yipayKey.isEmpty() ? "" : maskSecret(yipayKey));
        config.put("notifyUrl", getConfigOrDefault("payment_yipay_notify_url", ""));
        config.put("returnUrl", getConfigOrDefault("payment_yipay_return_url", ""));
        config.put("orderExpireMinutes", parseInt(getConfigOrDefault("payment_order_expire_minutes", "30"), 30));
        config.put("supportedTypes", parseSupportedTypes(getConfigOrDefault("payment_yipay_supported_types", "alipay,wxpay")));
        return config;
    }

    @Transactional
    public void updatePaymentConfig(Map<String, Object> body) {
        boolean enabled = parseBoolean(body.get("enabled"), Boolean.parseBoolean(getConfigOrDefault("payment_yipay_enabled", "false")));

        boolean hasGatewayUrl = body.containsKey("gatewayUrl");
        boolean hasPid = body.containsKey("pid");
        boolean hasKey = body.containsKey("key");

        String gatewayUrlInput = trimToEmpty(body.get("gatewayUrl"));
        String pidInput = trimToEmpty(body.get("pid"));
        String notifyUrlInput = trimToEmpty(body.get("notifyUrl"));
        String returnUrlInput = trimToEmpty(body.get("returnUrl"));
        String keyInput = trimToEmpty(body.get("key"));

        String gatewayUrl = hasGatewayUrl ? gatewayUrlInput : getConfigOrDefault("payment_yipay_gateway_url", "");
        String pid = hasPid ? pidInput : getConfigOrDefault("payment_yipay_pid", "");
        String key = resolveEffectiveKey(hasKey, keyInput);

        int expireMinutes = parseInt(String.valueOf(body.getOrDefault("orderExpireMinutes", getConfigOrDefault("payment_order_expire_minutes", "30"))), 30);
        if (expireMinutes < 5) {
            expireMinutes = 5;
        } else if (expireMinutes > 180) {
            expireMinutes = 180;
        }

        List<String> supportedTypes = parseSupportedTypes(body.containsKey("supportedTypes")
                ? body.get("supportedTypes")
                : getConfigOrDefault("payment_yipay_supported_types", "alipay,wxpay"));

        if (enabled) {
            validateEnabledPaymentConfig(gatewayUrl, pid, key, supportedTypes);
        }

        if (body.containsKey("enabled")) {
            upsertConfig("payment_yipay_enabled", enabled ? "true" : "false", "是否启用易支付充值", false);
        }
        if (body.containsKey("gatewayUrl")) {
            upsertConfig("payment_yipay_gateway_url", gatewayUrlInput, "易支付网关地址", false);
        }
        if (body.containsKey("pid")) {
            upsertConfig("payment_yipay_pid", pidInput, "易支付商户PID", false);
        }
        if (body.containsKey("notifyUrl")) {
            upsertConfig("payment_yipay_notify_url", notifyUrlInput, "易支付异步回调地址", false);
        }
        if (body.containsKey("returnUrl")) {
            upsertConfig("payment_yipay_return_url", returnUrlInput, "易支付同步跳转地址", false);
        }
        if (body.containsKey("orderExpireMinutes")) {
            upsertConfig("payment_order_expire_minutes", String.valueOf(expireMinutes), "充值订单过期时间(分钟)", false);
        }
        if (body.containsKey("supportedTypes")) {
            String serializedTypes = String.join(",", supportedTypes);
            upsertConfig("payment_yipay_supported_types", serializedTypes, "易支付可用支付方式", false);
        }
        if (hasKey && isRawKey(keyInput)) {
            upsertConfig("payment_yipay_key", keyInput, "易支付商户密钥", true);
        }
    }

    public Map<String, Object> verifyPaymentConfig(Map<String, Object> body) {
        boolean hasGatewayUrl = body.containsKey("gatewayUrl");
        boolean hasPid = body.containsKey("pid");
        boolean hasKey = body.containsKey("key");

        String gatewayUrlInput = trimToEmpty(body.get("gatewayUrl"));
        String pidInput = trimToEmpty(body.get("pid"));
        String keyInput = trimToEmpty(body.get("key"));

        String gatewayUrl = hasGatewayUrl ? gatewayUrlInput : getConfigOrDefault("payment_yipay_gateway_url", "");
        String pid = hasPid ? pidInput : getConfigOrDefault("payment_yipay_pid", "");
        String key = resolveEffectiveKey(hasKey, keyInput);

        List<String> supportedTypes = parseSupportedTypes(body.containsKey("supportedTypes")
                ? body.get("supportedTypes")
                : getConfigOrDefault("payment_yipay_supported_types", "alipay,wxpay"));

        validateEnabledPaymentConfig(gatewayUrl, pid, key, supportedTypes);

        String queryApi = buildQueryApiUrl(gatewayUrl);
        String requestUrl = queryApi
                + "?act=query&pid=" + urlEncode(pid)
                + "&key=" + urlEncode(key);

        RestTemplate restTemplate = new RestTemplate();
        String raw = restTemplate.getForObject(URI.create(requestUrl), String.class);

        if (raw == null || raw.trim().isEmpty()) {
            throw new RuntimeException("易支付接口未返回数据");
        }

        Map<String, Object> response;
        try {
            response = OBJECT_MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new RuntimeException("易支付返回格式异常: " + raw);
        }

        int code = parseInt(String.valueOf(response.getOrDefault("code", "0")), 0);
        String msg = trimToEmpty(response.get("msg"));
        boolean success = code == 1;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("message", success ? "连接测试成功" : (msg.isEmpty() ? "连接测试失败" : msg));
        result.put("code", code);
        result.put("pid", response.get("pid"));
        result.put("active", response.get("active"));
        result.put("money", response.get("money"));
        result.put("queryApi", queryApi);
        return result;
    }

    private void validateEnabledPaymentConfig(String gatewayUrl, String pid, String key, List<String> supportedTypes) {
        if (gatewayUrl.isEmpty()) {
            throw new RuntimeException("启用充值前请先配置支付网关地址");
        }
        if (!gatewayUrl.startsWith("http://") && !gatewayUrl.startsWith("https://")) {
            throw new RuntimeException("支付网关地址必须以 http:// 或 https:// 开头");
        }
        if (pid.isEmpty()) {
            throw new RuntimeException("启用充值前请先配置商户PID");
        }
        if (!pid.matches("^\\d+$")) {
            throw new RuntimeException("商户PID格式错误，必须为纯数字");
        }
        if (key.isEmpty()) {
            throw new RuntimeException("启用充值前请先配置商户密钥");
        }
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            throw new RuntimeException("请至少选择一种支付方式");
        }
    }

    private String buildQueryApiUrl(String gatewayUrl) {
        String normalized = trimToEmpty(gatewayUrl);
        if (normalized.contains("?")) {
            normalized = normalized.substring(0, normalized.indexOf('?'));
        }

        if (normalized.endsWith("/submit.php")) {
            return normalized.substring(0, normalized.length() - "/submit.php".length()) + "/api.php";
        }
        if (normalized.endsWith("/mapi.php")) {
            return normalized.substring(0, normalized.length() - "/mapi.php".length()) + "/api.php";
        }
        if (normalized.endsWith("/api.php")) {
            return normalized;
        }

        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/api.php";
    }

    private String resolveEffectiveKey(boolean hasInputKey, String inputKey) {
        if (!hasInputKey) {
            return trimToEmpty(getConfigOrDefault("payment_yipay_key", ""));
        }
        String trimmed = trimToEmpty(inputKey);
        if (isRawKey(trimmed)) {
            return trimmed;
        }
        return trimToEmpty(getConfigOrDefault("payment_yipay_key", ""));
    }

    private boolean isRawKey(String value) {
        return !value.isEmpty() && !isMaskedKey(value);
    }

    private boolean isMaskedKey(String value) {
        return value.contains("***") || value.equals("****");
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private List<String> parseSupportedTypes(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            for (Object obj : list) {
                values.add(trimToEmpty(obj).toLowerCase(Locale.ROOT));
            }
        } else {
            String text = trimToEmpty(raw);
            if (!text.isEmpty()) {
                values.addAll(Arrays.stream(text.split(","))
                        .map(String::trim)
                        .map(v -> v.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toList()));
            }
        }

        return values.stream()
                .filter(v -> !v.isEmpty())
                .filter(ALLOWED_PAY_TYPES::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private String getConfigOrDefault(String key, String defaultValue) {
        String value = configMapper.getValueByKey(key);
        return value == null ? defaultValue : value;
    }

    private void upsertConfig(String key, String value, String description, boolean encrypted) {
        configMapper.upsertConfig(key, value, description, encrypted);
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String maskSecret(String secret) {
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "***" + secret.substring(secret.length() - 4);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
