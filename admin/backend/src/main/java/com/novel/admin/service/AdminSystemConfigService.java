package com.novel.admin.service;

import com.novel.admin.entity.SystemAIConfig;
import com.novel.admin.mapper.SystemAIConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统配置管理服务
 */
@Service
@RequiredArgsConstructor
public class AdminSystemConfigService {

    private final SystemAIConfigMapper configMapper;

    /**
     * Get config value
     */
    public String getConfigValue(String key) {
        return configMapper.getValueByKey(key);
    }

    /**
     * Get config value with default
     */
    public String getConfigValue(String key, String defaultValue) {
        String value = configMapper.getValueByKey(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 设置配置值
     */
    @Transactional
    public void setConfigValue(String key, String value, boolean isEncrypted) {
        SystemAIConfig config = configMapper.selectByKey(key);
        if (config != null) {
            // 使用upsert方法更新
            configMapper.upsertConfig(key, value, config.getDescription(), isEncrypted);
        } else {
            // 新增配置
            configMapper.upsertConfig(key, value, null, isEncrypted);
        }
    }

    /**
     * 获取所有配置
     */
    public List<SystemAIConfig> getAllConfigs() {
        return configMapper.selectAll();
    }

    /**
     * 获取指定前缀的配置
     */
    public List<SystemAIConfig> getConfigsByPrefix(String prefix) {
        return configMapper.selectByKeyPrefix(prefix);
    }

    /**
     * 删除配置
     */
    @Transactional
    public void deleteConfig(String key) {
        SystemAIConfig config = configMapper.selectByKey(key);
        if (config != null) {
            configMapper.deleteById(config.getId());
        }
    }
}
