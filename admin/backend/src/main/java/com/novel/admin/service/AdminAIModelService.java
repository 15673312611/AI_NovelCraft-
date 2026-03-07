package com.novel.admin.service;

import com.novel.admin.entity.AIModel;
import com.novel.admin.entity.SystemAIConfig;
import com.novel.admin.mapper.AIModelMapper;
import com.novel.admin.mapper.SystemAIConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAIModelService {

    private final AIModelMapper aiModelMapper;
    private final SystemAIConfigMapper configMapper;

    /**
     * 获取所有模型
     */
    public List<AIModel> getAllModels() {
        return aiModelMapper.selectAllOrdered();
    }

    /**
     * 获取可用模型
     */
    public List<AIModel> getAvailableModels() {
        return aiModelMapper.selectAvailable();
    }

    /**
     * 获取模型详情
     */
    public AIModel getModel(Long id) {
        return aiModelMapper.selectById(id);
    }

    /**
     * 创建模型
     */
    public AIModel createModel(AIModel model) {
        aiModelMapper.insert(model);
        return model;
    }

    /**
     * 更新模型
     */
    public boolean updateModel(AIModel model) {
        return aiModelMapper.updateById(model) > 0;
    }

    /**
     * 删除模型
     */
    public boolean deleteModel(Long id) {
        return aiModelMapper.deleteById(id) > 0;
    }

    /**
     * 设置默认模型
     */
    @Transactional
    public boolean setDefaultModel(Long id) {
        aiModelMapper.clearDefaultModel();
        return aiModelMapper.setDefaultModel(id) > 0;
    }

    /**
     * 切换模型可用状态
     */
    public boolean toggleAvailable(Long id) {
        AIModel model = aiModelMapper.selectById(id);
        if (model == null) return false;
        model.setAvailable(!model.getAvailable());
        return aiModelMapper.updateById(model) > 0;
    }

    /**
     * 获取所有系统配置
     */
    public List<SystemAIConfig> getAllConfigs() {
        return configMapper.selectAll();
    }

    /**
     * 获取配置值
     */
    public String getConfig(String key) {
        return configMapper.getValueByKey(key);
    }

    /**
     * 更新配置
     */
    public boolean updateConfig(String key, String value) {
        return configMapper.updateValue(key, value) > 0;
    }

    /**
     * 批量更新配置
     */
    @Transactional
    public void updateConfigs(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            configMapper.updateValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 获取API配置（按提供商分组）
     */
    public Map<String, Map<String, String>> getAPIConfigs() {
        Map<String, Map<String, String>> result = new HashMap<>();
        
        String[] providers = {"deepseek", "qwen", "kimi", "openai"};
        for (String provider : providers) {
            Map<String, String> config = new HashMap<>();
            String apiKey = configMapper.getValueByKey(provider + "_api_key");
            String baseUrl = configMapper.getValueByKey(provider + "_base_url");
            config.put("apiKey", apiKey != null ? maskApiKey(apiKey) : "");
            config.put("baseUrl", baseUrl != null ? baseUrl : "");
            config.put("configured", apiKey != null && !apiKey.isEmpty() ? "true" : "false");
            result.put(provider, config);
        }
        
        return result;
    }

    /**
     * 更新API配置
     */
    @Transactional
    public void updateAPIConfig(String provider, String apiKey, String baseUrl) {
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.contains("***")) {
            configMapper.updateValue(provider + "_api_key", apiKey);
        }
        if (baseUrl != null) {
            configMapper.updateValue(provider + "_base_url", baseUrl);
        }
    }

    /**
     * 遮蔽API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 获取系统设置（包含AI配置）
     */
    public Map<String, String> getSystemSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("new_user_gift_credits", getConfigOrDefault("new_user_gift_credits", "100"));
        settings.put("min_balance_warning", getConfigOrDefault("min_balance_warning", "10"));
        // AI配置
        settings.put("ai_api_base_url", getConfigOrDefault("ai_api_base_url", ""));
        String apiKey = configMapper.getValueByKey("ai_api_key");
        log.info("获取AI API Key: {}", apiKey != null ? "已配置(长度:" + apiKey.length() + ")" : "未配置");
        settings.put("ai_api_key", apiKey != null && !apiKey.isEmpty() ? maskApiKey(apiKey) : "");
        
        // 每日免费字数配置
        settings.put("daily_free_credits_enabled", getConfigOrDefault("daily_free_credits_enabled", "true"));
        settings.put("daily_free_credits_amount", getConfigOrDefault("daily_free_credits_amount", "50000"));
        
        return settings;
    }

    /**
     * 更新系统设置
     */
    @Transactional
    public void updateSystemSettings(Map<String, String> settings) {
        log.info("更新系统设置: {}", settings.keySet());
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // 如果是API Key且包含***，说明是遮蔽后的值，不更新
            if ("ai_api_key".equals(key) && value != null && value.contains("***")) {
                log.info("跳过遮蔽的API Key");
                continue;
            }
            
            // 如果是API Key且值为空，跳过（不要清空已有的API Key）
            if ("ai_api_key".equals(key) && (value == null || value.trim().isEmpty())) {
                log.info("跳过空的API Key");
                continue;
            }
            
            // 使用upsert，不存在则插入，存在则更新
            int result = configMapper.upsertConfig(key, value, key + " 配置", false);
            log.info("更新配置 {} 结果: {}", key, result);
        }
    }

    private String getConfigOrDefault(String key, String defaultValue) {
        String value = configMapper.getValueByKey(key);
        return value != null ? value : defaultValue;
    }
}
