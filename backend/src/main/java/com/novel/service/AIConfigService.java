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
 * AI配置服务
 * 从系统配置构建AIConfigRequest，并处理字数点检查
 */
@Service
public class AIConfigService {

    private static final Logger logger = LoggerFactory.getLogger(AIConfigService.class);

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    @Autowired
    private CreditService creditService;

    /**
     * 从系统配置获取AIConfigRequest
     * @param modelId 可选的模型ID，为空则使用默认模型
     * @return AIConfigRequest
     */
    public AIConfigRequest getSystemAIConfig(String modelId) {
        // 验证模型ID：必须从数据库中查询，防止恶意请求
        AIModel model = systemAIConfigService.getModel(modelId);
        if (model == null) {
            logger.warn("模型ID无效或不可用: {}, 使用默认模型", modelId);
            model = systemAIConfigService.getDefaultModel();
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
        config.setModel(model.getModelId()); // 使用数据库中的模型ID，不使用前端传来的
        config.setBaseUrl(apiConfig.get("baseUrl"));

        logger.info("✅ 模型验证通过: {} ({})", model.getDisplayName(), model.getModelId());
        return config;
    }

    /**
     * 从系统配置获取默认AIConfigRequest
     */
    public AIConfigRequest getDefaultAIConfig() {
        return getSystemAIConfig(null);
    }

    /**
     * 检查用户字数点是否足够
     * @param userId 用户ID
     * @param estimatedInputChars 预估输入字数
     * @param estimatedOutputChars 预估输出字数
     * @param modelId 模型ID
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
     * 检查当前用户字数点是否足够
     */
    public void checkCurrentUserCredits(int estimatedInputTokens, int estimatedOutputTokens, String modelId) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        checkCredits(userId, estimatedInputTokens, estimatedOutputTokens, modelId);
    }

    /**
     * 检查当前用户字数点（基于文本长度估算）
     */
    public void checkCurrentUserCredits(String inputText, int estimatedOutputTokens) {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        
        AIModel model = systemAIConfigService.getDefaultModel();
        String modelId = model != null ? model.getModelId() : "deepseek-chat";
        
        // 估算输入token数（中文约1.5字符/token）
        int estimatedInputTokens = inputText != null ? inputText.length() / 2 : 0;
        
        checkCredits(userId, estimatedInputTokens, estimatedOutputTokens, modelId);
    }

    /**
     * 扣除字数点
     * @param userId 用户ID
     * @param inputTokens 实际输入token数
     * @param outputTokens 实际输出token数
     * @param modelId 模型ID
     * @param taskDescription 任务描述
     * @return 是否扣除成功
     */
    public boolean deductCredits(Long userId, int inputTokens, int outputTokens, String modelId, String taskDescription) {
        BigDecimal cost = systemAIConfigService.calculateCost(modelId, inputTokens, outputTokens);
        
        boolean success = creditService.consume(userId, cost, null, modelId, inputTokens, outputTokens, taskDescription);
        
        if (success) {
            logger.info("用户{}消费{}字数点，模型:{}, 输入token:{}, 输出token:{}, 任务:{}", 
                userId, cost, modelId, inputTokens, outputTokens, taskDescription);
        } else {
            logger.warn("用户{}扣费失败，金额:{}", userId, cost);
        }
        
        return success;
    }

    /**
     * 扣除当前用户字数点
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
     * 获取当前用户余额
     */
    public BigDecimal getCurrentUserBalance() {
        Long userId = AuthUtils.getCurrentUserId();
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        return creditService.getAvailableBalance(userId);
    }

    /**
     * 预估消费金额
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
