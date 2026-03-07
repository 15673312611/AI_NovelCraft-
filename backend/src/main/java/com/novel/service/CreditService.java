package com.novel.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.CreditTransaction;
import com.novel.domain.entity.CreditTransaction.TransactionType;
import com.novel.domain.entity.UserCredit;
import com.novel.repository.CreditTransactionRepository;
import com.novel.repository.UserCreditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 灵感点服务
 * 
 * 扣费逻辑：
 * 1. 优先扣除每日免费字数
 * 2. 每日免费字数用完后，再扣除字数包余额
 * 3. 每日免费字数在每天0点自动重置
 */
@Service
public class CreditService {

    private static final Logger logger = LoggerFactory.getLogger(CreditService.class);

    /** 字数来源：每日免费 */
    public static final String SOURCE_DAILY_FREE = "DAILY_FREE";
    /** 字数来源：字数包 */
    public static final String SOURCE_PACKAGE = "PACKAGE";

    @Autowired
    private UserCreditRepository userCreditRepository;

    @Autowired
    private CreditTransactionRepository transactionRepository;

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    /**
     * 获取用户灵感点账户，如果不存在则创建
     * 同时检查并重置每日免费字数
     */
    public UserCredit getOrCreateAccount(Long userId) {
        UserCredit credit = userCreditRepository.findByUserId(userId);
        if (credit == null) {
            BigDecimal initialGift = systemAIConfigService.getNewUserGiftCredits();
            userCreditRepository.createAccount(userId, initialGift);
            credit = userCreditRepository.findByUserId(userId);
            
            // 记录赠送交易
            if (initialGift.compareTo(BigDecimal.ZERO) > 0) {
                recordTransaction(userId, TransactionType.GIFT, initialGift, 
                    BigDecimal.ZERO, initialGift, null, null, null, null, 
                    "新用户注册赠送", null, SOURCE_PACKAGE);
            }
        }
        
        // 检查并重置每日免费字数
        checkAndResetDailyFreeCredits(credit);
        
        return credit;
    }

    /**
     * 检查并重置每日免费字数
     */
    @Transactional
    public void checkAndResetDailyFreeCredits(UserCredit credit) {
        if (!isDailyFreeCreditsEnabled()) {
            return;
        }
        
        LocalDate today = LocalDate.now();
        LocalDate lastReset = credit.getDailyFreeLastReset();
        
        // 如果今天还没重置，或者从未重置过，则重置
        if (lastReset == null || lastReset.isBefore(today)) {
            BigDecimal dailyAmount = getDailyFreeCreditsAmount();
            userCreditRepository.resetDailyFreeCredits(credit.getUserId(), dailyAmount, today);
            credit.setDailyFreeBalance(dailyAmount);
            credit.setDailyFreeLastReset(today);
            logger.info("用户{}每日免费字数已重置为{}", credit.getUserId(), dailyAmount);
        }
    }

    /**
     * 获取每日免费字数是否启用
     */
    public boolean isDailyFreeCreditsEnabled() {
        String value = systemAIConfigService.getConfig("daily_free_credits_enabled", "true");
        return "true".equalsIgnoreCase(value);
    }

    /**
     * 获取每日免费字数数量
     */
    public BigDecimal getDailyFreeCreditsAmount() {
        String value = systemAIConfigService.getConfig("daily_free_credits_amount", "50000");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return new BigDecimal("50000");
        }
    }

    /**
     * 获取用户余额（字数包余额）
     */
    public BigDecimal getBalance(Long userId) {
        UserCredit credit = getOrCreateAccount(userId);
        return credit.getBalance();
    }

    /**
     * 获取用户可用余额（字数包可用余额）
     */
    public BigDecimal getAvailableBalance(Long userId) {
        UserCredit credit = getOrCreateAccount(userId);
        return credit.getAvailableBalance();
    }

    /**
     * 获取用户每日免费字数余额
     */
    public BigDecimal getDailyFreeBalance(Long userId) {
        UserCredit credit = getOrCreateAccount(userId);
        return credit.getDailyFreeBalance();
    }

    /**
     * 获取用户总可用余额（每日免费 + 字数包）
     */
    public BigDecimal getTotalAvailableBalance(Long userId) {
        UserCredit credit = getOrCreateAccount(userId);
        return credit.getTotalAvailableBalance();
    }

    /**
     * 检查余额是否足够（包含每日免费字数）
     */
    public boolean hasEnoughBalance(Long userId, BigDecimal amount) {
        return getTotalAvailableBalance(userId).compareTo(amount) >= 0;
    }

    /**
     * 预估消费金额
     */
    public BigDecimal estimateCost(String modelId, int estimatedInputTokens, int estimatedOutputTokens) {
        return systemAIConfigService.calculateCost(modelId, estimatedInputTokens, estimatedOutputTokens);
    }

    /**
     * 预扣费（冻结金额）
     */
    @Transactional
    public boolean freezeForConsumption(Long userId, BigDecimal amount) {
        int rows = userCreditRepository.freezeAmount(userId, amount);
        return rows > 0;
    }

    /**
     * 确认消费（从冻结金额扣除）
     */
    @Transactional
    public boolean confirmConsumption(Long userId, BigDecimal frozenAmount, BigDecimal actualAmount,
                                      Long aiTaskId, String modelId, Integer inputTokens, Integer outputTokens,
                                      String description) {
        UserCredit credit = userCreditRepository.findByUserId(userId);
        if (credit == null) return false;

        BigDecimal balanceBefore = credit.getBalance();
        
        // 先解冻
        userCreditRepository.unfreezeAmount(userId, frozenAmount);
        
        // 再扣除实际金额
        int rows = userCreditRepository.deductBalance(userId, actualAmount);
        if (rows > 0) {
            BigDecimal balanceAfter = balanceBefore.subtract(actualAmount);
            recordTransaction(userId, TransactionType.CONSUME, actualAmount.negate(),
                balanceBefore, balanceAfter, aiTaskId, modelId, inputTokens, outputTokens,
                description, null, SOURCE_PACKAGE);
            return true;
        }
        return false;
    }

    /**
     * 直接消费（不经过冻结）
     * 扣费逻辑：
     * 1. 优先扣除每日免费字数
     * 2. 每日免费字数不足时，再扣除字数包余额
     * 3. 字数包余额扣到0为止，不会因余额不足而失败
     */
    @Transactional
    public boolean consume(Long userId, BigDecimal amount, Long aiTaskId, String modelId,
                          Integer inputTokens, Integer outputTokens, String description) {
        UserCredit credit = userCreditRepository.findByUserId(userId);
        if (credit == null) {
            credit = getOrCreateAccount(userId);
        }
        
        // 确保每日免费字数已重置
        checkAndResetDailyFreeCredits(credit);
        credit = userCreditRepository.findByUserId(userId); // 重新获取最新数据

        BigDecimal remainingAmount = amount;
        BigDecimal dailyFreeBalance = credit.getDailyFreeBalance();
        BigDecimal packageBalance = credit.getBalance();
        
        // 1. 优先从每日免费字数扣除
        if (isDailyFreeCreditsEnabled() && dailyFreeBalance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal deductFromDaily = dailyFreeBalance.min(remainingAmount);
            if (deductFromDaily.compareTo(BigDecimal.ZERO) > 0) {
                userCreditRepository.deductDailyFreeBalance(userId, deductFromDaily);
                
                // 记录每日免费字数消费
                recordTransaction(userId, TransactionType.CONSUME, deductFromDaily.negate(),
                    dailyFreeBalance, dailyFreeBalance.subtract(deductFromDaily), 
                    aiTaskId, modelId, inputTokens, outputTokens,
                    description, null, SOURCE_DAILY_FREE);
                
                remainingAmount = remainingAmount.subtract(deductFromDaily);
                logger.info("用户{}从每日免费字数扣除{}，剩余待扣{}", userId, deductFromDaily, remainingAmount);
            }
        }
        
        // 2. 如果还有剩余，从字数包扣除
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balanceBefore = packageBalance;
            
            // 使用强制扣费方法，扣到0为止
            int rows = userCreditRepository.deductBalanceForceToZero(userId, remainingAmount);
            if (rows > 0) {
                // 实际扣除金额 = min(余额, 应扣金额)
                BigDecimal actualDeducted = balanceBefore.min(remainingAmount);
                BigDecimal balanceAfter = balanceBefore.subtract(actualDeducted).max(BigDecimal.ZERO);
                
                recordTransaction(userId, TransactionType.CONSUME, actualDeducted.negate(),
                    balanceBefore, balanceAfter, aiTaskId, modelId, inputTokens, outputTokens,
                    description, null, SOURCE_PACKAGE);
                    
                logger.info("用户{}从字数包扣除{}（应扣{}），模型:{}, 输入token:{}, 输出token:{}", 
                    userId, actualDeducted, remainingAmount, modelId, inputTokens, outputTokens);
            }
        } else {
            logger.info("用户{}消费{}全部从每日免费字数扣除，模型:{}", userId, amount, modelId);
        }
        
        return true;
    }

    /**
     * 取消冻结（退回冻结金额）
     */
    @Transactional
    public boolean cancelFreeze(Long userId, BigDecimal amount) {
        int rows = userCreditRepository.unfreezeAmount(userId, amount);
        return rows > 0;
    }

    /**
     * 充值
     */
    @Transactional
    public boolean recharge(Long userId, BigDecimal amount, String description, Long operatorId) {
        UserCredit credit = getOrCreateAccount(userId);
        BigDecimal balanceBefore = credit.getBalance();
        
        int rows = userCreditRepository.addRecharge(userId, amount);
        if (rows > 0) {
            BigDecimal balanceAfter = balanceBefore.add(amount);
            recordTransaction(userId, TransactionType.RECHARGE, amount,
                balanceBefore, balanceAfter, null, null, null, null,
                description, operatorId, SOURCE_PACKAGE);
            logger.info("用户{}充值{}灵感点，操作人:{}", userId, amount, operatorId);
            return true;
        }
        return false;
    }

    /**
     * 赠送灵感点
     */
    @Transactional
    public boolean gift(Long userId, BigDecimal amount, String description, Long operatorId) {
        UserCredit credit = getOrCreateAccount(userId);
        BigDecimal balanceBefore = credit.getBalance();
        
        int rows = userCreditRepository.addGift(userId, amount);
        if (rows > 0) {
            BigDecimal balanceAfter = balanceBefore.add(amount);
            recordTransaction(userId, TransactionType.GIFT, amount,
                balanceBefore, balanceAfter, null, null, null, null,
                description, operatorId, SOURCE_PACKAGE);
            logger.info("用户{}获得赠送{}灵感点，操作人:{}", userId, amount, operatorId);
            return true;
        }
        return false;
    }

    /**
     * 管理员调整余额
     */
    @Transactional
    public boolean adminAdjust(Long userId, BigDecimal amount, String description, Long operatorId) {
        UserCredit credit = getOrCreateAccount(userId);
        BigDecimal balanceBefore = credit.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        
        // 直接更新余额
        credit.setBalance(balanceAfter);
        userCreditRepository.updateById(credit);
        
        recordTransaction(userId, TransactionType.ADMIN_ADJUST, amount,
            balanceBefore, balanceAfter, null, null, null, null,
            description, operatorId, SOURCE_PACKAGE);
        logger.info("管理员{}调整用户{}余额{}灵感点，原因:{}", operatorId, userId, amount, description);
        return true;
    }

    /**
     * 退款
     */
    @Transactional
    public boolean refund(Long userId, BigDecimal amount, Long aiTaskId, String description, Long operatorId) {
        UserCredit credit = getOrCreateAccount(userId);
        BigDecimal balanceBefore = credit.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        
        credit.setBalance(balanceAfter);
        credit.setTotalConsumed(credit.getTotalConsumed().subtract(amount));
        userCreditRepository.updateById(credit);
        
        recordTransaction(userId, TransactionType.REFUND, amount,
            balanceBefore, balanceAfter, aiTaskId, null, null, null,
            description, operatorId, SOURCE_PACKAGE);
        logger.info("用户{}退款{}灵感点，任务ID:{}", userId, amount, aiTaskId);
        return true;
    }

    /**
     * 获取用户交易记录
     */
    public IPage<CreditTransaction> getTransactions(Long userId, int page, int size) {
        Page<CreditTransaction> pageParam = new Page<>(page + 1, size);
        return transactionRepository.findByUserIdPaged(pageParam, userId);
    }

    /**
     * 获取用户今日消费
     */
    public BigDecimal getTodayConsumption(Long userId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        return transactionRepository.sumConsumedSince(userId, startOfDay);
    }

    /**
     * 获取用户本月消费
     */
    public BigDecimal getMonthConsumption(Long userId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return transactionRepository.sumConsumedSince(userId, startOfMonth);
    }

    /**
     * 记录交易
     */
    private void recordTransaction(Long userId, TransactionType type, BigDecimal amount,
                                   BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   Long aiTaskId, String modelId, Integer inputTokens, Integer outputTokens,
                                   String description, Long operatorId, String creditSource) {
        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setAiTaskId(aiTaskId);
        transaction.setModelId(modelId);
        transaction.setInputTokens(inputTokens);
        transaction.setOutputTokens(outputTokens);
        transaction.setDescription(description);
        transaction.setOperatorId(operatorId);
        transaction.setCreditSource(creditSource);
        transaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.insert(transaction);
    }
}
