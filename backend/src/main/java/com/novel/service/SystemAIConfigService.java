package com.novel.service;

import com.novel.domain.entity.AIModel;
import com.novel.domain.entity.SystemAIConfig;
import com.novel.repository.AIModelRepository;
import com.novel.repository.SystemAIConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统AI配置服务
 */
@Service
public class SystemAIConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAIConfigService.class);

    @Autowired
    private SystemAIConfigRepository configRepository;

    @Autowired
    private AIModelRepository aiModelRepository;

    /**
     * 获取配置值
     */
    public String getConfig(String key) {
        return configRepository.getValueByKey(key);
    }

    /**
     * 获取配置值，带默认值
     */
    public String getConfig(String key, String defaultValue) {
        String value = configRepository.getValueByKey(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 设置配置值
     */
    @CacheEvict(value = "aiConfig", allEntries = true)
    public void setConfig(String key, String value) {
        configRepository.updateValue(key, value);
    }

    /**
     * 更新配置（带描述）
     */
    @CacheEvict(value = "aiConfig", allEntries = true)
    public void updateConfig(String key, String value, String description) {
        configRepository.upsertConfig(key, value, description, false);
    }

    /**
     * 获取新用户赠送灵感点数量
     */
    public BigDecimal getNewUserGiftCredits() {
        String value = getConfig("new_user_gift_credits", "100");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return new BigDecimal("100");
        }
    }

    /**
     * 获取余额不足提醒阈值
     */
    public BigDecimal getMinBalanceWarning() {
        String value = getConfig("min_balance_warning", "10");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return new BigDecimal("10");
        }
    }

    /**
     * 获取默认模型
     */
    @Cacheable(value = "aiConfig", key = "'defaultModel'")
    public AIModel getDefaultModel() {
        List<AIModel> models = aiModelRepository.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AIModel>()
                .eq("is_default", true)
                .eq("available", true)
        );
        if (!models.isEmpty()) {
            return models.get(0);
        }
        // 如果没有默认模型，返回第一个可用模型
        models = aiModelRepository.findByAvailableTrueOrderByCostPer1kAsc();
        return models.isEmpty() ? null : models.get(0);
    }

    /**
     * 获取指定模型
     */
    public AIModel getModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return getDefaultModel();
        }
        List<AIModel> models = aiModelRepository.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AIModel>()
                .eq("model_id", modelId)
                .eq("available", true)
        );
        return models.isEmpty() ? getDefaultModel() : models.get(0);
    }

    /**
     * 获取所有可用模型
     */
    public List<AIModel> getAvailableModels() {
        return aiModelRepository.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AIModel>()
                .eq("available", true)
                .orderByAsc("sort_order")
        );
    }

    /**
     * 获取模型的API配置（使用统一的AI配置）
     */
    public Map<String, String> getModelAPIConfig(AIModel model) {
        Map<String, String> config = new HashMap<>();
        
        // 使用统一的AI配置
        String apiKey = getConfig("ai_api_key");
        String baseUrl = getConfig("ai_api_base_url");
        
        // 如果统一配置为空，尝试使用旧的配置方式（兼容）
        if (apiKey == null || apiKey.isEmpty()) {
            String apiKeyRef = model.getApiKeyRef();
            if (apiKeyRef != null && !apiKeyRef.isEmpty()) {
                apiKey = getConfig(apiKeyRef);
            }
            if (apiKey == null || apiKey.isEmpty()) {
                String provider = model.getProvider() != null ? model.getProvider().toLowerCase() : "openai";
                apiKey = getConfig(provider + "_api_key");
            }
        }
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = model.getApiBaseUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                String provider = model.getProvider() != null ? model.getProvider().toLowerCase() : "openai";
                baseUrl = getConfig(provider + "_base_url");
            }
        }
        
        config.put("apiKey", apiKey);
        config.put("baseUrl", baseUrl);
        config.put("model", model.getModelId());
        config.put("provider", model.getProvider() != null ? model.getProvider().toLowerCase() : "openai-compatible");
        config.put("maxTokens", String.valueOf(model.getMaxTokens() != null ? model.getMaxTokens() : 8192));
        
        return config;
    }

    /**
     * 计算消费字数点（灵感点）
     * 
     * 计费公式：(输入token/10 + 输出token) × 模型倍率
     * 
     * 说明：
     * - 输入token：按字符数计算（中文1字符≈1token，英文约4字符≈1token）
     * - 输出token：按字符数计算
     * - 输入token除以10后再计算，大幅降低输入成本（因为输入通常包含大量上下文）
     * - 模型倍率：不同模型有不同的成本倍率，默认为1.0
     * 
     * 示例：
     * - 输入 10000 字符，输出 3000 字符，模型倍率 1.0
     * - 计算：(10000/10 + 3000) × 1.0 = 4000 点
     * 
     * @param modelId 模型ID
     * @param inputTokens 输入token数（按字符数计算）
     * @param outputTokens 输出token数（按字符数计算）
     * @return 消费的字数点（灵感点）
     */
    public BigDecimal calculateCost(String modelId, int inputTokens, int outputTokens) {
        AIModel model = getModel(modelId);
        BigDecimal multiplier = BigDecimal.ONE;
        
        if (model != null && model.getCostMultiplier() != null) {
            multiplier = model.getCostMultiplier();
        } else if (model == null) {
            logger.warn("模型{}不存在，使用默认倍率1.0", modelId);
        }
        
        // 字数点 = (输入token/10 + 输出token) × 倍率
        // 输入token除以10，降低输入成本
        BigDecimal adjustedInputTokens = new BigDecimal(inputTokens).divide(new BigDecimal(10), 2, RoundingMode.HALF_UP);
        BigDecimal totalTokens = adjustedInputTokens.add(new BigDecimal(outputTokens));
        return totalTokens.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 预估消费字数点（基于输入文本）
     * 
     * @param modelId 模型ID
     * @param inputText 输入文本（按字符长度计算token）
     * @param estimatedOutputTokens 预估输出token数
     * @return 预估消费的字数点
     */
    public BigDecimal estimateCost(String modelId, String inputText, int estimatedOutputTokens) {
        int inputTokens = inputText != null ? inputText.length() : 0;
        return calculateCost(modelId, inputTokens, estimatedOutputTokens);
    }

    /**
     * 获取所有配置（用于管理端）
     */
    public List<SystemAIConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    /**
     * 批量更新配置
     */
    @CacheEvict(value = "aiConfig", allEntries = true)
    public void updateConfigs(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            configRepository.updateValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 检查API配置是否有效
     */
    public boolean isAPIConfigValid(String provider) {
        String apiKey = getConfig(provider + "_api_key");
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
