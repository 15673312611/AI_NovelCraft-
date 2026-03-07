package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.VerificationCode;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface VerificationCodeRepository extends BaseMapper<VerificationCode> {

    /**
     * 查找有效的验证码
     */
    @Select("SELECT * FROM verification_codes WHERE email = #{email} AND code = #{code} AND type = #{type} AND used = 0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1")
    VerificationCode findValidCode(@Param("email") String email, @Param("code") String code, @Param("type") String type);

    /**
     * 标记验证码为已使用
     */
    @Update("UPDATE verification_codes SET used = 1 WHERE id = #{id}")
    int markAsUsed(@Param("id") Long id);

    /**
     * 统计指定时间内发送的验证码数量（防刷）
     */
    @Select("SELECT COUNT(*) FROM verification_codes WHERE email = #{email} AND created_at > #{since}")
    int countRecentCodes(@Param("email") String email, @Param("since") LocalDateTime since);

    /**
     * 删除过期的验证码
     */
    @Delete("DELETE FROM verification_codes WHERE expires_at < NOW()")
    int deleteExpiredCodes();
}
