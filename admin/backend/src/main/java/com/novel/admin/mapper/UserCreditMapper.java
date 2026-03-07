package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.entity.UserCredit;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;

@Mapper
public interface UserCreditMapper extends BaseMapper<UserCredit> {

    @Select("SELECT uc.*, u.username, u.email FROM user_credits uc " +
            "LEFT JOIN users u ON uc.user_id = u.id " +
            "ORDER BY uc.balance DESC")
    IPage<UserCredit> selectWithUserInfo(Page<UserCredit> page);

    @Select("SELECT uc.*, u.username, u.email FROM user_credits uc " +
            "LEFT JOIN users u ON uc.user_id = u.id " +
            "WHERE u.username LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY uc.balance DESC")
    IPage<UserCredit> searchWithUserInfo(Page<UserCredit> page, @Param("keyword") String keyword);

    @Select("SELECT uc.*, u.username, u.email FROM user_credits uc " +
            "LEFT JOIN users u ON uc.user_id = u.id " +
            "WHERE uc.user_id = #{userId}")
    UserCredit selectByUserIdWithInfo(@Param("userId") Long userId);

    @Update("UPDATE user_credits SET balance = balance + #{amount}, " +
            "total_recharged = total_recharged + #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int addRecharge(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE user_credits SET balance = balance + #{amount}, " +
            "total_gifted = total_gifted + #{amount}, " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int addGift(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Insert("INSERT INTO user_credits (user_id, balance, total_gifted, created_at, updated_at) " +
            "VALUES (#{userId}, #{balance}, #{balance}, NOW(), NOW())")
    int createAccount(@Param("userId") Long userId, @Param("balance") BigDecimal balance);

    @Select("SELECT COALESCE(SUM(balance), 0) FROM user_credits")
    BigDecimal sumTotalBalance();

    @Select("SELECT COALESCE(SUM(total_consumed), 0) FROM user_credits")
    BigDecimal sumTotalConsumed();

    @Select("SELECT COALESCE(SUM(total_recharged), 0) FROM user_credits")
    BigDecimal sumTotalRecharged();
}
