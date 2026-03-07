package com.novel.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminCreditService {

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
        configMapper.upsertConfig("new_user_gift_credits", amount, "新用户注册赠送字数点", false);
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
        return config;
    }

    @Transactional
    public void updatePaymentConfig(Map<String, Object> body) {
        if (body.containsKey("enabled")) {
            boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            upsertConfig("payment_yipay_enabled", enabled ? "true" : "false", "是否启用易支付充值", false);
        }
        if (body.containsKey("gatewayUrl")) {
            upsertConfig("payment_yipay_gateway_url", trimToEmpty(body.get("gatewayUrl")), "易支付网关地址", false);
        }
        if (body.containsKey("pid")) {
            upsertConfig("payment_yipay_pid", trimToEmpty(body.get("pid")), "易支付商户PID", false);
        }
        if (body.containsKey("notifyUrl")) {
            upsertConfig("payment_yipay_notify_url", trimToEmpty(body.get("notifyUrl")), "易支付异步回调地址", false);
        }
        if (body.containsKey("returnUrl")) {
            upsertConfig("payment_yipay_return_url", trimToEmpty(body.get("returnUrl")), "易支付同步跳转地址", false);
        }
        if (body.containsKey("orderExpireMinutes")) {
            int expireMinutes = parseInt(String.valueOf(body.get("orderExpireMinutes")), 30);
            if (expireMinutes < 5) {
                expireMinutes = 5;
            } else if (expireMinutes > 180) {
                expireMinutes = 180;
            }
            upsertConfig("payment_order_expire_minutes", String.valueOf(expireMinutes), "充值订单过期时间(分钟)", false);
        }
        if (body.containsKey("key")) {
            String key = trimToEmpty(body.get("key"));
            if (!key.isEmpty() && !key.contains("***")) {
                upsertConfig("payment_yipay_key", key, "易支付商户密钥", true);
            }
        }
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
}
