package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.UserCredit;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface UserCreditRepository extends BaseMapper<UserCredit> {

    @Select("SELECT * FROM user_credits WHERE user_id = #{userId}")
    UserCredit findByUserId(@Param("userId") Long userId);

    @Update("UPDATE user_credits SET balance = balance + #{amount}, " +
            "total_recharged = total_recharged + #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int addRecharge(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 扣除余额（要求余额足够）
     */
    @Update("UPDATE user_credits SET balance = balance - #{amount}, " +
            "total_consumed = total_consumed + #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 强制扣除余额（扣到0为止，不检查余额是否足够）
     * 实际扣除金额 = min(当前余额, 应扣金额)
     */
    @Update("UPDATE user_credits SET " +
            "total_consumed = total_consumed + LEAST(balance, #{amount}), " +
            "balance = GREATEST(balance - #{amount}, 0), " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int deductBalanceForceToZero(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE user_credits SET balance = balance + #{amount}, " +
            "total_gifted = total_gifted + #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int addGift(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE user_credits SET frozen_amount = frozen_amount + #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId} AND (balance - frozen_amount) >= #{amount}")
    int freezeAmount(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE user_credits SET frozen_amount = frozen_amount - #{amount}, " +
            "balance = balance - #{amount}, " +
            "total_consumed = total_consumed + #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId} AND frozen_amount >= #{amount}")
    int confirmFrozen(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE user_credits SET frozen_amount = frozen_amount - #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId} AND frozen_amount >= #{amount}")
    int unfreezeAmount(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Insert("INSERT INTO user_credits (user_id, balance, total_gifted, created_at, updated_at) " +
            "VALUES (#{userId}, #{initialBalance}, #{initialBalance}, NOW(), NOW())")
    int createAccount(@Param("userId") Long userId, @Param("initialBalance") BigDecimal initialBalance);

    /**
     * 重置每日免费字数
     */
    @Update("UPDATE user_credits SET daily_free_balance = #{amount}, " +
            "daily_free_last_reset = #{resetDate}, " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int resetDailyFreeCredits(@Param("userId") Long userId, 
                              @Param("amount") BigDecimal amount, 
                              @Param("resetDate") LocalDate resetDate);

    /**
     * 扣除每日免费字数
     */
    @Update("UPDATE user_credits SET daily_free_balance = GREATEST(daily_free_balance - #{amount}, 0), " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int deductDailyFreeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
