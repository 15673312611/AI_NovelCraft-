package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.SystemAIConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemAIConfigRepository extends BaseMapper<SystemAIConfig> {

    @Select("SELECT * FROM system_ai_config WHERE config_key = #{key}")
    SystemAIConfig findByKey(@Param("key") String key);

    @Select("SELECT config_value FROM system_ai_config WHERE config_key = #{key}")
    String getValueByKey(@Param("key") String key);

    @Update("UPDATE system_ai_config SET config_value = #{value}, updated_at = NOW() WHERE config_key = #{key}")
    int updateValue(@Param("key") String key, @Param("value") String value);

    @Insert("INSERT INTO system_ai_config (config_key, config_value, description, is_encrypted, created_at, updated_at) " +
            "VALUES (#{key}, #{value}, #{description}, #{isEncrypted}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE config_value = #{value}, updated_at = NOW()")
    int upsertConfig(@Param("key") String key, @Param("value") String value, 
                     @Param("description") String description, @Param("isEncrypted") boolean isEncrypted);

    @Select("SELECT * FROM system_ai_config WHERE config_key LIKE CONCAT(#{prefix}, '%')")
    List<SystemAIConfig> findByKeyPrefix(@Param("prefix") String prefix);

    @Select("SELECT * FROM system_ai_config")
    List<SystemAIConfig> findAll();
}
