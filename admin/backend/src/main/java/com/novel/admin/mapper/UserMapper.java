package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.UserDTO;
import com.novel.admin.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("<script>" +
            "SELECT u.id, u.username, u.email, u.nickname, u.status, " +
            "u.created_at as createdAt, u.last_login_at as lastLoginAt, " +
            "GROUP_CONCAT(r.name) as roles " +
            "FROM users u " +
            "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
            "LEFT JOIN roles r ON ur.role_id = r.id " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "</where>" +
            "GROUP BY u.id " +
            "ORDER BY u.created_at DESC" +
            "</script>")
    Page<UserDTO> selectUserPage(Page<UserDTO> page, @Param("keyword") String keyword);

    @Select("SELECT u.id, u.username, u.email, u.nickname, u.status, " +
            "u.created_at as createdAt, u.last_login_at as lastLoginAt, " +
            "GROUP_CONCAT(r.name) as roles " +
            "FROM users u " +
            "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
            "LEFT JOIN roles r ON ur.role_id = r.id " +
            "WHERE u.id = #{id} " +
            "GROUP BY u.id")
    UserDTO selectUserDTOById(@Param("id") Long id);

    @Select("SELECT name FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRolesByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO user_roles (user_id, role_id) " +
            "SELECT #{userId}, id FROM roles WHERE name = #{roleName}")
    void insertUserRole(@Param("userId") Long userId, @Param("roleName") String roleName);

    @Delete("DELETE FROM user_roles WHERE user_id = #{userId}")
    void deleteUserRoles(@Param("userId") Long userId);
}
