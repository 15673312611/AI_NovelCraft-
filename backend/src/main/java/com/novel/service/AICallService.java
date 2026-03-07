package com.novel.service;

import com.novel.domain.entity.AIModel;
import com.novel.exception.InsufficientCreditsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/**
 * AI调用服务 - 统一处理AI调用和字数点扣费
 */
@Service
public class AICallService {

    private static final Logger logger = LoggerFactory.getLogger(AICallService.class);

    @Autowired
    private CreditService creditService;

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    private final RestTemplate restTemplate;

    public AICallService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * AI调用结果
     */
    public static class AICallResult {
        private String content;
        private int inputChars;
        private int outputChars;
        private BigDecimal cost;
        private String modelId;
        private boolean success;
        private String errorMessage;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public int getInputChars() { return inputChars; }
        public void setInputChars(int inputChars) { this.inputChars = inputChars; }
        public int getOutputChars() { return outputChars; }
        public void setOutputChars(int outputChars) { this.outputChars = outputChars; }
        public BigDecimal getCost() { return cost; }
        public void setCost(BigDecimal cost) { this.cost = cost; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    /**
     * 调用AI（非流式）
     * @param prompt 提示词
     * @param modelId 模型ID（可选，为空使用默认模型）
     * @param maxTokens 最大输出token数
     * @param userId 用户ID
     * @param taskDescription 任务描述（用于记录）
     * @return AI调用结果
     */
    public AICallResult callAI(String prompt, String modelId, Integer maxTokens, Long userId, String taskDescription) {
        return callAI(prompt, modelId, maxTokens, null, userId, taskDescription);
    }

    /**
     * 调用AI（非流式，支持温度参数）
     * @param prompt 提示词
     * @param modelId 模型ID（可选，为空使用默认模型）
     * @param maxTokens 最大输出token数
     * @param temperature 温度参数（可选，为空使用模型默认值）
     * @param userId 用户ID
     * @param taskDescription 任务描述（用于记录）
     * @return AI调用结果
     */
    public AICallResult callAI(String prompt, String modelId, Integer maxTokens, Double temperature, Long userId, String taskDescription) {
        AICallResult result = new AICallResult();
        
        try {
            // 获取模型配置
            AIModel model = systemAIConfigService.getModel(modelId);
            if (model == null) {
                result.setSuccess(false);
                result.setErrorMessage("模型不可用");
                return result;
            }
            result.setModelId(model.getModelId());

            // 预估消费并检查余额（按字数计算）
            int inputChars = prompt.length();
            int estimatedOutputChars = maxTokens != null ? maxTokens * 2 : model.getMaxTokens();
            BigDecimal estimatedCost = systemAIConfigService.calculateCost(
                model.getModelId(), inputChars, estimatedOutputChars);

            if (!creditService.hasEnoughBalance(userId, estimatedCost)) {
                throw new InsufficientCreditsException("字数点余额不足，预估需要 " + estimatedCost + " 点");
            }

            // 获取API配置
            Map<String, String> apiConfig = systemAIConfigService.getModelAPIConfig(model);
            String apiKey = apiConfig.get("apiKey");
            String baseUrl = apiConfig.get("baseUrl");

            if (apiKey == null || apiKey.isEmpty()) {
                result.setSuccess(false);
                result.setErrorMessage("AI服务未配置，请联系管理员");
                return result;
            }

            // 构建请求 - 确保 baseUrl 格式正确
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // 如果 baseUrl 没有包含 /v1 路径，自动添加
            if (!baseUrl.contains("/v1") && !baseUrl.endsWith("/chat/completions")) {
                baseUrl = baseUrl + "/v1";
            }
            String url = baseUrl + "/chat/completions";
            logger.debug("AI API URL: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelId());
            requestBody.put("messages", Collections.singletonList(
                Map.of("role", "user", "content", prompt)
            ));
            if (maxTokens != null) {
                requestBody.put("max_tokens", maxTokens);
            }
            // 设置温度参数：优先使用传入的温度，否则使用模型默认温度
            if (temperature != null) {
                requestBody.put("temperature", temperature);
            } else if (model.getTemperature() != null) {
                requestBody.put("temperature", model.getTemperature().doubleValue());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 调用API - 使用 String 接收响应以便处理非JSON响应
            ResponseEntity<String> rawResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            String responseText = rawResponse.getBody();
            
            if (responseText == null || responseText.isEmpty()) {
                result.setSuccess(false);
                result.setErrorMessage("AI服务返回空响应");
                return result;
            }
            
            // 检查是否返回了 HTML（通常是错误页面）
            if (responseText.trim().startsWith("<") || responseText.contains("<!DOCTYPE") || responseText.contains("<html")) {
                logger.error("AI API 返回了 HTML 而不是 JSON，可能是 URL 配置错误或 API 不可用。URL: {}, Response: {}", 
                    url, responseText.substring(0, Math.min(500, responseText.length())));
                result.setSuccess(false);
                result.setErrorMessage("AI服务返回异常（HTML响应），请检查API配置。URL: " + url);
                return result;
            }
            
            // 解析 JSON 响应
            Map<String, Object> responseBody;
            try {
                responseBody = new com.fasterxml.jackson.databind.ObjectMapper().readValue(responseText, Map.class);
            } catch (Exception e) {
                logger.error("AI API 响应解析失败: {}", responseText.substring(0, Math.min(500, responseText.length())));
                result.setSuccess(false);
                result.setErrorMessage("AI服务响应格式错误: " + e.getMessage());
                return result;
            }
            
            // 检查 API 返回的错误
            if (responseBody.containsKey("error")) {
                Object errorObj = responseBody.get("error");
                String errorMsg;
                if (errorObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                    errorMsg = String.valueOf(errorMap.getOrDefault("message", errorObj));
                } else {
                    errorMsg = String.valueOf(errorObj);
                }
                logger.error("AI API 返回错误: {}", errorMsg);
                result.setSuccess(false);
                result.setErrorMessage("AI服务错误: " + errorMsg);
                return result;
            }

            // 解析响应
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                result.setContent((String) message.get("content"));
            }

            // 计算实际字数
            result.setInputChars(inputChars);
            result.setOutputChars(result.getContent() != null ? result.getContent().length() : 0);

            // 计算实际消费并扣费
            BigDecimal actualCost = systemAIConfigService.calculateCost(
                model.getModelId(), result.getInputChars(), result.getOutputChars());
            result.setCost(actualCost);

            // 使用模型显示名称记录
            String modelDisplayName = model.getDisplayName() != null ? model.getDisplayName() : model.getModelId();
            boolean deducted = creditService.consume(userId, actualCost, null, modelDisplayName,
                result.getInputChars(), result.getOutputChars(), taskDescription);

            if (!deducted) {
                logger.warn("扣费失败，用户ID: {}, 金额: {}", userId, actualCost);
            }

            result.setSuccess(true);
            logger.info("AI调用成功，用户: {}, 模型: {}, 输入字数: {}, 输出字数: {}, 消费: {}",
                userId, model.getModelId(), result.getInputChars(), result.getOutputChars(), actualCost);

        } catch (InsufficientCreditsException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            logger.warn("用户 {} 字数点不足: {}", userId, e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("AI调用失败: " + e.getMessage());
            logger.error("AI调用异常", e);
        }

        return result;
    }

    /**
     * 调用AI（使用当前登录用户）
     */
    public AICallResult callAI(String prompt, String modelId, Integer maxTokens, String taskDescription) {
        Long userId = com.novel.common.security.AuthUtils.getCurrentUserId();
        if (userId == null) {
            AICallResult result = new AICallResult();
            result.setSuccess(false);
            result.setErrorMessage("请先登录");
            return result;
        }
        return callAI(prompt, modelId, maxTokens, userId, taskDescription);
    }

    /**
     * 调用AI（使用默认模型和当前用户）
     */
    public AICallResult callAI(String prompt, String taskDescription) {
        return callAI(prompt, null, null, taskDescription);
    }

    /**
     * 流式调用AI
     */
    public void streamCallAI(String prompt, String modelId, Integer maxTokens, Long userId,
                             String taskDescription, Consumer<String> onContent, Consumer<AICallResult> onComplete) {
        streamCallAI(prompt, modelId, maxTokens, null, userId, taskDescription, onContent, onComplete);
    }

    /**
     * 流式调用AI（支持温度参数）
     */
    public void streamCallAI(String prompt, String modelId, Integer maxTokens, Double temperature, Long userId,
                             String taskDescription, Consumer<String> onContent, Consumer<AICallResult> onComplete) {
        AICallResult result = new AICallResult();
        StringBuilder fullContent = new StringBuilder();

        try {
            AIModel model = systemAIConfigService.getModel(modelId);
            if (model == null) {
                result.setSuccess(false);
                result.setErrorMessage("模型不可用");
                onComplete.accept(result);
                return;
            }
            result.setModelId(model.getModelId());

            // 预估消费并检查余额（按字数计算）
            int inputChars = prompt.length();
            int estimatedOutputChars = maxTokens != null ? maxTokens * 2 : 8000;
            BigDecimal estimatedCost = systemAIConfigService.calculateCost(
                model.getModelId(), inputChars, estimatedOutputChars);

            if (!creditService.hasEnoughBalance(userId, estimatedCost)) {
                throw new InsufficientCreditsException("字数点余额不足");
            }

            // 预扣费
            creditService.freezeForConsumption(userId, estimatedCost);

            Map<String, String> apiConfig = systemAIConfigService.getModelAPIConfig(model);
            String apiKey = apiConfig.get("apiKey");
            String baseUrl = apiConfig.get("baseUrl");

            if (apiKey == null || apiKey.isEmpty()) {
                creditService.cancelFreeze(userId, estimatedCost);
                result.setSuccess(false);
                result.setErrorMessage("AI服务未配置");
                onComplete.accept(result);
                return;
            }

            String url = baseUrl + "/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model.getModelId());
            requestBody.put("messages", Collections.singletonList(
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("stream", true);
            if (maxTokens != null) {
                requestBody.put("max_tokens", maxTokens);
            }
            // 设置温度参数：优先使用传入的温度，否则使用模型默认温度
            if (temperature != null) {
                requestBody.put("temperature", temperature);
            } else if (model.getTemperature() != null) {
                requestBody.put("temperature", model.getTemperature().doubleValue());
            }

            // 使用RestTemplate进行流式请求
            restTemplate.execute(url, HttpMethod.POST,
                request -> {
                    request.getHeaders().addAll(headers);
                    new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValue(request.getBody(), requestBody);
                },
                response -> {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.getBody()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                                String json = line.substring(6);
                                try {
                                    Map<String, Object> data = new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readValue(json, Map.class);
                                    List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
                                    if (choices != null && !choices.isEmpty()) {
                                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                                        if (delta != null && delta.containsKey("content")) {
                                            String content = (String) delta.get("content");
                                            fullContent.append(content);
                                            onContent.accept(content);
                                        }
                                    }
                                } catch (Exception e) {
                                    // 忽略解析错误
                                }
                            }
                        }
                    }
                    return null;
                }
            );

            // 计算实际消费（按字数）
            result.setContent(fullContent.toString());
            result.setInputChars(inputChars);
            result.setOutputChars(fullContent.length());

            BigDecimal actualCost = systemAIConfigService.calculateCost(
                model.getModelId(), result.getInputChars(), result.getOutputChars());
            result.setCost(actualCost);

            // 确认消费（使用模型显示名称）
            String modelDisplayName = model.getDisplayName() != null ? model.getDisplayName() : model.getModelId();
            creditService.confirmConsumption(userId, estimatedCost, actualCost, null,
                modelDisplayName, result.getInputChars(), result.getOutputChars(), taskDescription);

            result.setSuccess(true);

        } catch (InsufficientCreditsException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("AI调用失败: " + e.getMessage());
            logger.error("流式AI调用异常", e);
        }

        onComplete.accept(result);
    }

    /**
     * 检查用户是否有足够余额进行AI调用
     */
    public boolean checkBalance(Long userId, String modelId, String inputText, int estimatedOutputChars) {
        BigDecimal estimatedCost = systemAIConfigService.estimateCost(modelId, inputText, estimatedOutputChars);
        return creditService.hasEnoughBalance(userId, estimatedCost);
    }
}
