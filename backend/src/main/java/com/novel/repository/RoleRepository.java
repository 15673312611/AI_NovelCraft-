package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.Role;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RoleRepository extends BaseMapper<Role> {
    @Select("SELECT * FROM roles WHERE name = #{name}")
    Role findByName(@Param("name") String name);

    @Select("SELECT * FROM roles WHERE active = true ORDER BY priority ASC, name ASC")
    List<Role> findByActiveTrue();

    @Select("SELECT * FROM roles WHERE active = #{active} ORDER BY priority ASC, name ASC")
    IPage<Role> findByActive(@Param("active") Boolean active, Page<Role> page);

    @Select("SELECT * FROM roles WHERE LOWER(name) LIKE LOWER(CONCAT('%', #{query}, '%')) OR LOWER(display_name) LIKE LOWER(CONCAT('%', #{query}, '%'))")
    List<Role> searchByName(@Param("query") String query);

    @Select("SELECT COUNT(*) FROM roles WHERE active = true")
    long countByActiveTrue();

    @Select("SELECT MAX(priority) FROM roles")
    Integer findMaxPriority();
}

