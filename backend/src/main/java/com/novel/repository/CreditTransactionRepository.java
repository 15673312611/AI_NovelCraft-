package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.CreditTransaction;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CreditTransactionRepository extends BaseMapper<CreditTransaction> {

    @Select("SELECT * FROM credit_transactions WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<CreditTransaction> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM credit_transactions WHERE user_id = #{userId} ORDER BY created_at DESC")
    IPage<CreditTransaction> findByUserIdPaged(Page<CreditTransaction> page, @Param("userId") Long userId);

    @Select("SELECT * FROM credit_transactions WHERE user_id = #{userId} AND type = #{type} ORDER BY created_at DESC")
    List<CreditTransaction> findByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    @Select("SELECT COALESCE(SUM(ABS(amount)), 0) FROM credit_transactions " +
            "WHERE user_id = #{userId} AND type = 'CONSUME' AND created_at >= #{startTime}")
    BigDecimal sumConsumedSince(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    @Select("SELECT COALESCE(SUM(input_tokens), 0) FROM credit_transactions " +
            "WHERE user_id = #{userId} AND created_at >= #{startTime}")
    Long sumInputTokensSince(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    @Select("SELECT COALESCE(SUM(output_tokens), 0) FROM credit_transactions " +
            "WHERE user_id = #{userId} AND created_at >= #{startTime}")
    Long sumOutputTokensSince(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);
}
