package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户灵感点余额实体
 */
@TableName("user_credits")
public class UserCredit {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private BigDecimal balance = BigDecimal.ZERO;

    @TableField("total_recharged")
    private BigDecimal totalRecharged = BigDecimal.ZERO;

    @TableField("total_consumed")
    private BigDecimal totalConsumed = BigDecimal.ZERO;

    @TableField("total_gifted")
    private BigDecimal totalGifted = BigDecimal.ZERO;

    @TableField("frozen_amount")
    private BigDecimal frozenAmount = BigDecimal.ZERO;

    /** 今日剩余免费字数 */
    @TableField("daily_free_balance")
    private BigDecimal dailyFreeBalance = BigDecimal.ZERO;

    /** 上次重置日期 */
    @TableField("daily_free_last_reset")
    private LocalDate dailyFreeLastReset;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getTotalRecharged() { return totalRecharged; }
    public void setTotalRecharged(BigDecimal totalRecharged) { this.totalRecharged = totalRecharged; }

    public BigDecimal getTotalConsumed() { return totalConsumed; }
    public void setTotalConsumed(BigDecimal totalConsumed) { this.totalConsumed = totalConsumed; }

    public BigDecimal getTotalGifted() { return totalGifted; }
    public void setTotalGifted(BigDecimal totalGifted) { this.totalGifted = totalGifted; }

    public BigDecimal getFrozenAmount() { return frozenAmount; }
    public void setFrozenAmount(BigDecimal frozenAmount) { this.frozenAmount = frozenAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getDailyFreeBalance() { return dailyFreeBalance != null ? dailyFreeBalance : BigDecimal.ZERO; }
    public void setDailyFreeBalance(BigDecimal dailyFreeBalance) { this.dailyFreeBalance = dailyFreeBalance; }

    public LocalDate getDailyFreeLastReset() { return dailyFreeLastReset; }
    public void setDailyFreeLastReset(LocalDate dailyFreeLastReset) { this.dailyFreeLastReset = dailyFreeLastReset; }

    /**
     * 获取可用余额（总余额 - 冻结金额）
     */
    public BigDecimal getAvailableBalance() {
        return balance.subtract(frozenAmount);
    }

    /**
     * 获取总可用余额（每日免费 + 字数包可用余额）
     */
    public BigDecimal getTotalAvailableBalance() {
        return getDailyFreeBalance().add(getAvailableBalance());
    }

    /**
     * 检查是否有足够余额（包含每日免费字数）
     */
    public boolean hasEnoughBalance(BigDecimal amount) {
        return getTotalAvailableBalance().compareTo(amount) >= 0;
    }
}
