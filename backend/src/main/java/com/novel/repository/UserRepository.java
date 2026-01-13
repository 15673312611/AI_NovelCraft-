package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserRepository extends BaseMapper<User> {
    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Select("SELECT * FROM users WHERE username = #{usernameOrEmail} OR email = #{usernameOrEmail}")
    User findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    /**
     * 通过微信OpenID查找用户
     */
    @Select("SELECT * FROM users WHERE wechat_openid = #{openid}")
    User findByWechatOpenid(@Param("openid") String openid);

    /**
     * 通过微信UnionID查找用户
     */
    @Select("SELECT * FROM users WHERE wechat_unionid = #{unionid}")
    User findByWechatUnionid(@Param("unionid") String unionid);

    /**
     * 更新用户微信信息
     */
    @Update("<script>" +
            "UPDATE users SET " +
            "<if test='openid != null'>wechat_openid = #{openid},</if>" +
            "<if test='openid == null'>wechat_openid = NULL,</if>" +
            "<if test='unionid != null'>wechat_unionid = #{unionid},</if>" +
            "<if test='loginType != null'>login_type = #{loginType},</if>" +
            "updated_at = NOW() " +
            "WHERE id = #{userId}" +
            "</script>")
    int updateWechatInfo(@Param("userId") Long userId, 
                         @Param("openid") String openid, 
                         @Param("unionid") String unionid,
                         @Param("loginType") String loginType);
}

