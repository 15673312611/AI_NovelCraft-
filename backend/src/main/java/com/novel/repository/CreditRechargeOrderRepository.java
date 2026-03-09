package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.CreditRechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CreditRechargeOrderRepository extends BaseMapper<CreditRechargeOrder> {

    @Select("SELECT * FROM credit_recharge_orders WHERE order_no = #{orderNo} LIMIT 1")
    CreditRechargeOrder findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM credit_recharge_orders WHERE order_no = #{orderNo} AND user_id = #{userId} LIMIT 1")
    CreditRechargeOrder findByOrderNoAndUserId(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Update("UPDATE credit_recharge_orders " +
            "SET status = 'PAID', third_party_order_no = #{tradeNo}, notify_raw = #{notifyRaw}, paid_at = NOW(), updated_at = NOW() " +
            "WHERE order_no = #{orderNo} AND status IN ('PENDING','CLOSED')")
    int markPaidIfUnpaid(@Param("orderNo") String orderNo,
                         @Param("tradeNo") String tradeNo,
                         @Param("notifyRaw") String notifyRaw);

    @Update("UPDATE credit_recharge_orders " +
            "SET status = 'CLOSED', updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING' AND expired_at IS NOT NULL AND expired_at <= NOW()")
    int closeExpiredOrder(@Param("id") Long id);
}
