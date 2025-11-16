package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.dto.AIConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI纠错服务
 * 检测文本中的错别字、名称错误、乱码等问题
 */
@Service
public class AIProofreadService {

    private static final Logger logger = LoggerFactory.getLogger(AIProofreadService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 纠错结果类
     */
    public static class ProofreadError {
        private String type;           // 错误类型：typo(错别字), name(名称错误), garbled(乱码), punctuation(标点), other(其他)
        private String original;       // 原始文本
        private String corrected;      // 修正后的文本
        private int position;          // 错误位置（字符索引）
        private String context;        // 上下文（前后各20字）
        private String reason;         // 错误原因说明

        public ProofreadError() {}

        public ProofreadError(String type, String original, String corrected, int position, String context, String reason) {
            this.type = type;
            this.original = original;
            this.corrected = corrected;
            this.position = position;
            this.context = context;
            this.reason = reason;
        }

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getOriginal() { return original; }
        public void setOriginal(String original) { this.original = original; }
        public String getCorrected() { return corrected; }
        public void setCorrected(String corrected) { this.corrected = corrected; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * 构建AI纠错的系统提示词
     */
    private String buildSystemPrompt() {
        return "你是一名专业的网络小说文字校对AI，负责检查文本中的各类错误。\n" +
                "\n" +
                "【检查范围】\n" +
                "1. 错别字：错误的汉字、同音字误用、形近字误用\n" +
                "2. 名称错误：人名、地名、物品名等专有名词前后不一致\n" +
                "3. 乱码：无意义的字符、符号、特殊字符\n" +
                "4. 标点错误：中英文标点混用、标点使用不当\n" +
                "5. 其他明显错误：语法错误、逻辑矛盾等\n" +
                "\n" +
                "【检查原则】\n" +
                "- 只标记明确的错误，不要过度纠正\n" +
                "- 尊重作者的写作风格和用词习惯\n" +
                "- 对于专有名词，优先以文中首次出现的形式为准\n" +
                "- 不要修改作者的创作意图和表达方式\n" +
                "- 网络小说的口语化表达不算错误\n" +
                "\n" +
                "【输出格式】\n" +
                "请以JSON数组格式输出所有错误，每个错误包含以下字段：\n" +
                "{\n" +
                "  \"type\": \"错误类型(typo/name/garbled/punctuation/other)\",\n" +
                "  \"original\": \"错误的文本\",\n" +
                "  \"corrected\": \"修正后的文本\",\n" +
                "  \"position\": 错误在原文中的字符位置,\n" +
                "  \"context\": \"错误处的上下文(前后各20字)\",\n" +
                "  \"reason\": \"错误原因的简短说明\"\n" +
                "}\n" +
                "\n" +
                "如果没有发现错误，返回空数组 []\n" +
                "只输出JSON数组，不要添加任何其他文字说明。";
    }

    /**
     * 执行AI纠错
     *
     * @param content 待检查的文本内容
     * @param characterNames 角色名称列表（用于检查名称一致性）
     * @param aiConfig AI配置
     * @return 错误列表
     */
    public List<ProofreadError> proofread(String content, List<String> characterNames, AIConfigRequest aiConfig) {
        try {
            // 构建用户消息
            StringBuilder userBuilder = new StringBuilder();
            
            userBuilder.append("【待检查文本】\n");
            userBuilder.append(content.trim());
            userBuilder.append("\n\n");
            
            // 如果提供了角色名称，添加到提示中
            if (characterNames != null && !characterNames.isEmpty()) {
                userBuilder.append("【已知角色名称】\n");
                for (String name : characterNames) {
                    userBuilder.append("- ").append(name).append("\n");
                }
                userBuilder.append("\n");
            }
            
            userBuilder.append("请仔细检查上述文本，找出所有错误并以JSON数组格式返回。");

            // 调用AI
            String aiResponse = callAI(aiConfig, buildSystemPrompt(), userBuilder.toString());
            
            // 解析AI返回的JSON
            List<ProofreadError> errors = parseAIResponse(aiResponse, content);
            
            logger.info("✅ AI纠错完成，发现 {} 个错误", errors.size());
            
            return errors;
            
        } catch (Exception e) {
            logger.error("AI纠错失败", e);
            throw new RuntimeException("AI纠错失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析AI返回的JSON响应
     */
    private List<ProofreadError> parseAIResponse(String aiResponse, String originalContent) {
        try {
            // 清理可能的markdown代码块标记
            String cleaned = aiResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
            
            // 如果返回空或者只是说明文字，返回空列表
            if (cleaned.isEmpty() || !cleaned.startsWith("[")) {
                logger.warn("AI返回的不是有效的JSON数组: {}", cleaned.substring(0, Math.min(100, cleaned.length())));
                return new ArrayList<>();
            }
            
            // 解析JSON数组
            List<ProofreadError> errors = objectMapper.readValue(cleaned, new TypeReference<List<ProofreadError>>() {});
            
            // 验证和修正position字段
            for (ProofreadError error : errors) {
                if (error.getPosition() < 0 || error.getPosition() >= originalContent.length()) {
                    // 如果position不准确，尝试通过original文本查找
                    int foundPos = originalContent.indexOf(error.getOriginal());
                    if (foundPos >= 0) {
                        error.setPosition(foundPos);
                    } else {
                        error.setPosition(0);
                    }
                }
                
                // 如果没有context，自动生成
                if (error.getContext() == null || error.getContext().isEmpty()) {
                    error.setContext(generateContext(originalContent, error.getPosition(), error.getOriginal().length()));
                }
            }
            
            return errors;
            
        } catch (Exception e) {
            logger.error("解析AI纠错响应失败: {}", aiResponse, e);
            return new ArrayList<>();
        }
    }

    /**
     * 生成错误位置的上下文
     */
    private String generateContext(String content, int position, int errorLength) {
        int contextRadius = 20;
        int start = Math.max(0, position - contextRadius);
        int end = Math.min(content.length(), position + errorLength + contextRadius);
        
        String context = content.substring(start, end);
        
        // 添加省略号
        if (start > 0) {
            context = "..." + context;
        }
        if (end < content.length()) {
            context = context + "...";
        }
        
        return context;
    }

    /**
     * 调用AI接口（非流式）
     */
    private String callAI(AIConfigRequest aiConfig, String systemPrompt, String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiConfig.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.3);  // 较低的temperature以获得更准确的结果
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
            
            String apiUrl = aiConfig.getApiUrl();
            
            logger.info("🔄 调用AI纠错接口: {}, model: {}", apiUrl, aiConfig.getModel());
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(apiUrl, entity, Map.class);
            
            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("AI接口返回错误状态码: " + responseEntity.getStatusCode());
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseEntity.getBody();
            
            if (response == null || response.containsKey("error")) {
                throw new RuntimeException("AI接口返回错误");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI接口未返回有效内容");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("AI返回内容为空");
            }
            
            logger.info("✅ AI纠错接口调用成功");
            return content;
            
        } catch (Exception e) {
            logger.error("❌ AI接口调用异常", e);
            throw new RuntimeException("AI接口调用失败: " + e.getMessage());
        }
    }
}

