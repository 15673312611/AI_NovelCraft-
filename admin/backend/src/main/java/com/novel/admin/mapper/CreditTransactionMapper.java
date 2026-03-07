package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.entity.CreditTransaction;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface CreditTransactionMapper extends BaseMapper<CreditTransaction> {

    @Select("SELECT ct.*, u.username, op.username as operator_name FROM credit_transactions ct " +
            "LEFT JOIN users u ON ct.user_id = u.id " +
            "LEFT JOIN users op ON ct.operator_id = op.id " +
            "ORDER BY ct.created_at DESC")
    IPage<CreditTransaction> selectWithUserInfo(Page<CreditTransaction> page);

    @Select("SELECT ct.*, u.username, op.username as operator_name FROM credit_transactions ct " +
            "LEFT JOIN users u ON ct.user_id = u.id " +
            "LEFT JOIN users op ON ct.operator_id = op.id " +
            "WHERE ct.user_id = #{userId} " +
            "ORDER BY ct.created_at DESC")
    IPage<CreditTransaction> selectByUserIdWithInfo(Page<CreditTransaction> page, @Param("userId") Long userId);

    @Select("SELECT ct.*, u.username, op.username as operator_name FROM credit_transactions ct " +
            "LEFT JOIN users u ON ct.user_id = u.id " +
            "LEFT JOIN users op ON ct.operator_id = op.id " +
            "WHERE ct.type = #{type} " +
            "ORDER BY ct.created_at DESC")
    IPage<CreditTransaction> selectByTypeWithInfo(Page<CreditTransaction> page, @Param("type") String type);

    @Select("SELECT COALESCE(SUM(ABS(amount)), 0) FROM credit_transactions " +
            "WHERE type = 'CONSUME' AND created_at >= #{startTime}")
    BigDecimal sumConsumedSince(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM credit_transactions " +
            "WHERE type = 'RECHARGE' AND created_at >= #{startTime}")
    BigDecimal sumRechargedSince(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT model_id, COUNT(*) as call_count, " +
            "COALESCE(SUM(input_tokens), 0) as total_input_tokens, " +
            "COALESCE(SUM(output_tokens), 0) as total_output_tokens, " +
            "COALESCE(SUM(ABS(amount)), 0) as total_cost " +
            "FROM credit_transactions " +
            "WHERE type = 'CONSUME' AND model_id IS NOT NULL AND created_at >= #{startTime} " +
            "GROUP BY model_id ORDER BY total_cost DESC")
    java.util.List<java.util.Map<String, Object>> getModelUsageStats(@Param("startTime") LocalDateTime startTime);
}
