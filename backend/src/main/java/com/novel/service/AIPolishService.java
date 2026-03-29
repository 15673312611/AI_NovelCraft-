package com.novel.service;

import com.novel.dto.AIConfigRequest;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI润色服务
 * 专注于消除AI味，让文字更自然、更贴近真人写作风格
 */
@Service
public class AIPolishService {

    private static final Logger logger = LoggerFactory.getLogger(AIPolishService.class);

    @org.springframework.beans.factory.annotation.Autowired
    private ManagedPromptService managedPromptService;

    @org.springframework.beans.factory.annotation.Autowired
    private AIWritingService aiWritingService;

    /**
     * 润色选中的文本片段
     *
     * @param chapterTitle 章节标题
     * @param fullContent 整章内容（作为上下文）
     * @param targetSelection 待润色的片段
     * @param userInstructions 用户的润色要求
     * @param aiConfig AI配置
     * @return 润色后的文本
     */
    public String polishSelection(String chapterTitle, String fullContent, String targetSelection, 
                                   String userInstructions, AIConfigRequest aiConfig) {
        try {
            String contextSnippet = buildContextSnippet(fullContent, targetSelection);
            String userInstructionsBlock = "";
            if (userInstructions != null && !userInstructions.trim().isEmpty()) {
                userInstructionsBlock = "【用户特殊要求】\n" + userInstructions.trim() + "\n\n";
            }

            String prompt = managedPromptService.render(SystemPromptDescriptor.builder()
                            .promptKey("editing.polish.selection")
                            .name("AI润色片段重写提示词")
                            .category("system_chain")
                            .description("针对选中片段进行局部重写润色")
                            .variables(List.of("user_instructions_block", "target_selection", "context_snippet"))
                            .sortOrder(815)
                            .defaultContent("""
                                    你是一个【文本替换工具】。你的唯一任务是重写下面的【待替换片段】，并直接输出结果。
                                    【格式严格死线（违反将导致系统错误）】
                                    1. **禁止任何废话**：绝对不要输出“好的”、“如下所示”、“重写版本”等任何提示语。直接开始写正文。
                                    2. **禁止包含上下文**：你只负责重写【待替换片段】这一小段。千万不要把【上下文】里的内容抄进去！
                                    3. **禁止解释**：不要告诉我你改了哪里，也不要解释为什么这么改。
                                    4. **禁止Markdown**：不要使用代码块或引用符号。

                                    【创作要求】
                                    1. **彻底换血**：必须用全新的句式、词汇来描写同一情节。如果原句平庸，就用惊艳的写法覆盖它。
                                    2. **拒绝AI味**：禁止翻译腔，禁止堆砌辞藻。要像老练的小说家一样，用白描、侧写等手法。

                                    {{user_instructions_block}}【待替换片段】
                                    {{target_selection}}

                                    【上下文（仅供参考，绝对禁止出现在输出中）】
                                    {{context_snippet}}

                                    ---
                                    (请直接输出重写后的正文，不要加任何标点前缀)：
                                    """)
                            .build(),
                    Map.of(
                            "user_instructions_block", userInstructionsBlock,
                            "target_selection", targetSelection == null ? "" : targetSelection.trim(),
                            "context_snippet", contextSnippet == null ? "" : contextSnippet
                    ));

            AIConfigRequest requestConfig = new AIConfigRequest();
            if (aiConfig != null) {
                requestConfig.setProvider(aiConfig.getProvider());
                requestConfig.setApiKey(aiConfig.getApiKey());
                requestConfig.setModel(aiConfig.getModel());
                requestConfig.setBaseUrl(aiConfig.getBaseUrl());
            }
            requestConfig.setTemperature(0.7);

            String polished = aiWritingService.generateContent(prompt, "ai_polish", requestConfig);
            
            logger.info("✅ AI重写完成，原始长度: {}, 结果长度: {}", 
                    targetSelection.length(), polished.length());
            
            return polished.trim();
            
        } catch (Exception e) {
            logger.error("AI润色失败", e);
            throw new RuntimeException("AI润色失败: " + e.getMessage(), e);
        }
    }

    private String buildContextSnippet(String fullContent, String targetSelection) {
        if (fullContent == null || fullContent.isEmpty()) {
            return "";
        }

        String content = fullContent;
        String selection = targetSelection == null ? "" : targetSelection;
        int maxLen = 1200;

        if (selection.isEmpty()) {
            return content.length() > maxLen ? content.substring(0, maxLen) : content;
        }

        int index = content.indexOf(selection);
        if (index < 0) {
            String trimmed = selection.trim();
            if (!trimmed.isEmpty()) {
                index = content.indexOf(trimmed);
            }
        }

        if (index < 0) {
            return content.length() > maxLen ? content.substring(0, maxLen) : content;
        }

        int start = Math.max(0, index - 400);
        int end = Math.min(content.length(), index + selection.length() + 400);
        String snippet = content.substring(start, end);
        return snippet.trim();
    }

    /**
     * 调用AI接口（非流式）
     */
    private String callAI(AIConfigRequest aiConfig, String systemPrompt, String userMessage) {
        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiConfig.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("max_tokens", 8000);
            requestBody.put("temperature", 0.7);
            requestBody.put("stream", false);
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
            
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);
            
            requestBody.put("messages", messages);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // 使用 getApiUrl() 方法获取完整的API地址
            String apiUrl = aiConfig.getApiUrl();
            
            logger.info("🔄 调用AI润色接口: {}, model: {}", apiUrl, aiConfig.getModel());
            
            // 创建RestTemplate
            RestTemplate restTemplate = new RestTemplate();
            
            // 发送请求并获取响应
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(
                apiUrl, entity, Map.class);
            
            // 检查HTTP状态码
            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                logger.error("❌ AI接口返回错误状态码: {}", responseEntity.getStatusCode());
                throw new RuntimeException("AI接口返回错误状态码: " + responseEntity.getStatusCode());
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseEntity.getBody();
            
            if (response == null) {
                logger.error("❌ AI接口返回空响应");
                throw new RuntimeException("AI接口返回空响应");
            }
            
            // 检查是否有错误信息
            if (response.containsKey("error")) {
                Object errorObj = response.get("error");
                String errorMsg = errorObj != null ? errorObj.toString() : "未知错误";
                logger.error("❌ AI接口返回错误: {}", errorMsg);
                throw new RuntimeException("AI接口返回错误: " + errorMsg);
            }
            
            if (!response.containsKey("choices")) {
                logger.error("❌ AI接口返回数据格式错误，缺少choices字段: {}", response);
                throw new RuntimeException("AI接口返回数据格式错误");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                logger.error("❌ AI接口未返回有效内容");
                throw new RuntimeException("AI接口未返回有效内容");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                logger.error("❌ AI接口返回的message为空");
                throw new RuntimeException("AI接口返回的message为空");
            }
            
            String content = (String) message.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                logger.error("❌ AI返回内容为空");
                throw new RuntimeException("AI返回内容为空");
            }
            
            logger.info("✅ AI润色接口调用成功，返回内容长度: {}", content.length());
            return content;
            
        } catch (HttpClientErrorException e) {
            logger.error("❌ AI接口HTTP客户端错误: status={}, body={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI接口调用失败(HTTP " + e.getStatusCode() + "): " + 
                e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            logger.error("❌ AI接口HTTP服务器错误: status={}, body={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI接口服务器错误(HTTP " + e.getStatusCode() + "): " + 
                e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            logger.error("❌ AI接口网络连接错误", e);
            throw new RuntimeException("无法连接到AI服务: " + e.getMessage());
        } catch (Exception e) {
            logger.error("❌ AI接口调用异常", e);
            throw new RuntimeException("AI接口调用失败: " + e.getMessage());
        }
    }
}

