package com.novel.service;

import com.novel.common.security.AuthUtils;
import com.novel.domain.entity.AIModel;
import com.novel.dto.AIConfigRequest;
import com.novel.exception.InsufficientCreditsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 负责从系统配置构建 AIConfigRequest，并处理字数点校验。
 */
@Service
public class AIConfigService {

    private static final Logger logger = LoggerFactory.getLogger(AIConfigService.class);

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    @Autowired
    private CreditService creditService;

    /**
     * 从系统配置获取 AIConfigRequest。
     *
     * @param modelId 可选模型ID，为空则使用默认模型
     * @return AIConfigRequest
     */
    public AIConfigRequest getSystemAIConfig(String modelId) {
        AIModel model = null;
        if (modelId != null && !modelId.trim().isEmpty()) {
            model = systemAIConfigService.getExactAvailableModel(modelId);
        }
        if (model == null) {
            model = systemAIConfigService.getDefaultModel();
            if (modelId != null && !modelId.trim().isEmpty()) {
                logger.warn("模型ID无效或不可用: {}, 使用默认模型", modelId);
            }
            if (model == null) {
                throw new RuntimeException("未找到可用的AI模型配置，请联系管理员在后台添加模型");
            }
        }

        Map<String, String> apiConfig = systemAIConfigService.getModelAPIConfig(model);
        String apiKey = apiConfig.get("apiKey");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("AI服务未配置API Key，请联系管理员");
        }

        AIConfigRequest config = new AIConfigRequest();
        config.setProvider(apiConfig.get("provider"));
        config.setApiKey(apiKey);
        config.setModel(model.getModelId());
        config.setBaseUrl(apiConfig.get("baseUrl"));

        logger.info("模型验证通过: {} ({})", model.getDisplayName(), model.getModelId());
        return config;
    }

    /**
     * 从系统配置获取默认 AIConfigRequest。
     */
    public AIConfigRequest getDefaultAIConfig() {
        return getSystemAIConfig(null);
    }

    public AIConfigRequest buildDirectAIConfig(String modelId, Double temperature) {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new RuntimeException("链路路由模型不能为空");
        }

        String apiKey = systemAIConfigService.getConfig("ai_api_key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("AI服务未配置 API Key，请先在后台 AI 模型页配置");
        }

        String baseUrl = systemAIConfigService.getConfig("ai_api_base_url");
        AIModel defaultModel = systemAIConfigService.getDefaultModel();
        String provider = defaultModel != null && defaultModel.getProvider() != null
            ? defaultModel.getProvider()
            : "openai-compatible";

        AIConfigRequest config = new AIConfigRequest();
        config.setProvider(provider);
        config.setApiKey(apiKey);
        config.setModel(modelId.trim());
        config.setBaseUrl(baseUrl);
        config.setTemperature(temperature);
        return config;
    }

    /**
     * 检查用户字数点是否足够。
     */
    public void checkCredits(Long userId, int estimatedInputChars, int estimatedOutputChars, String modelId) {
        BigDecimal estimatedCost = systemAIConfigService.calculateCost(modelId, estimatedInputChars, estimatedOutputChars);

        if (!creditService.hasEnoughBalance(userId, estimatedCost)) {
            BigDecimal balance = creditService.getAvailableBalance(userId);
            throw new InsufficientCreditsException(
                String.format("字数点余额不足，当前余额: %.2f，预估需要: %.2f", balance, estimatedCost)
            );
        }
    }

    /**
     * 检查当前登录用户字数点是否足够。
     */
    public void checkCurrentUserCredits(int estimatedInputTokens, int estimatedOutputTokens, String modelId) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        checkCredits(userId, estimatedInputTokens, estimatedOutputTokens, modelId);
    }

    /**
     * 基于文本长度估算当前登录用户字数点。
     */
    public void checkCurrentUserCredits(String inputText, int estimatedOutputTokens) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }

        AIModel model = systemAIConfigService.getDefaultModel();
        String modelId = model != null ? model.getModelId() : "deepseek-chat";

        int estimatedInputTokens = inputText != null ? inputText.length() / 2 : 0;
        checkCredits(userId, estimatedInputTokens, estimatedOutputTokens, modelId);
    }

    /**
     * 扣除字数点。
     */
    public boolean deductCredits(Long userId, int inputTokens, int outputTokens, String modelId, String taskDescription) {
        BigDecimal cost = systemAIConfigService.calculateCost(modelId, inputTokens, outputTokens);

        boolean success = creditService.consume(userId, cost, null, modelId, inputTokens, outputTokens, taskDescription);

        if (success) {
            logger.info("用户{}消费{}字数点，模型:{}, 输入token:{}, 输出token:{}, 任务:{}",
                userId, cost, modelId, inputTokens, outputTokens, taskDescription);
        } else {
            logger.warn("用户{}扣费失败，金额{}", userId, cost);
        }

        return success;
    }

    /**
     * 扣除当前用户字数点。
     */
    public boolean deductCurrentUserCredits(int inputTokens, int outputTokens, String modelId, String taskDescription) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            logger.warn("扣费失败：用户未登录");
            return false;
        }
        return deductCredits(userId, inputTokens, outputTokens, modelId, taskDescription);
    }

    /**
     * 获取当前登录用户余额。
     */
    public BigDecimal getCurrentUserBalance() {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        return creditService.getAvailableBalance(userId);
    }

    /**
     * 预估消费金额。
     */
    public BigDecimal estimateCost(String inputText, int estimatedOutputTokens, String modelId) {
        int estimatedInputTokens = inputText != null ? inputText.length() / 2 : 0;
        return systemAIConfigService.calculateCost(
            modelId != null ? modelId : systemAIConfigService.getDefaultModel().getModelId(),
            estimatedInputTokens,
            estimatedOutputTokens
        );
    }
}
