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

/**
 * AI智能建议服务
 * 对小说内容进行全面诊断，提供改进建议
 */
@Service
public class AISmartSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(AISmartSuggestionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 建议类型枚举
     */
    public enum SuggestionType {
        GRAMMAR("grammar", "语法问题"),
        LOGIC("logic", "逻辑不通"),
        REDUNDANT("redundant", "冗余内容"),
        IMPROVEMENT("improvement", "改进建议"),
        INCONSISTENCY("inconsistency", "前后矛盾"),
        STYLE("style", "文风问题");

        private final String code;
        private final String description;

        SuggestionType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }
    }

    /**
     * 建议操作类型
     */
    public enum ActionType {
        REPLACE("replace", "替换"),
        DELETE("delete", "删除"),
        INSERT("insert", "插入");

        private final String code;
        private final String description;

        ActionType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }
    }

    /**
     * 智能建议结果类
     */
    public static class SmartSuggestion {
        private String type;           // 建议类型：grammar/logic/redundant/improvement/inconsistency/style
        private String action;         // 操作类型：replace/delete/insert
        private String original;       // 原始文本
        private String suggested;      // 建议的文本（delete时为null）
        private int position;          // 位置（字符索引）
        private int length;            // 原始文本长度
        private String context;        // 上下文（前后各30字）
        private String reason;         // 建议原因
        private String severity;       // 严重程度：high/medium/low

        public SmartSuggestion() {}

        public SmartSuggestion(String type, String action, String original, String suggested, 
                             int position, int length, String context, String reason, String severity) {
            this.type = type;
            this.action = action;
            this.original = original;
            this.suggested = suggested;
            this.position = position;
            this.length = length;
            this.context = context;
            this.reason = reason;
            this.severity = severity;
        }

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getOriginal() { return original; }
        public void setOriginal(String original) { this.original = original; }
        public String getSuggested() { return suggested; }
        public void setSuggested(String suggested) { this.suggested = suggested; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }

    /**
     * 构建AI智能建议的系统提示词
     */
    private String buildSystemPrompt() {
        return "你是一名资深的网络小说编辑AI，负责对小说内容进行全面诊断并提供改进建议。\n" +
                "你的核心任务是：让AI生成的文本更像人类写的，避免被AI检测工具识别。\n" +
                "\n" +
                "【诊断范围】\n" +
                "1. 语法问题：病句、语序不当、成分残缺、搭配不当等\n" +
                "2. 逻辑不通：情节矛盾、因果关系不清、时间线混乱等\n" +
                "3. 冗余内容：重复描写、啰嗦表达、无意义的填充词等\n" +
                "4. 改进建议：可以写得更好的地方、表达不够精准的地方\n" +
                "5. 前后矛盾：人物设定前后不一致、情节冲突等\n" +
                "6. 文风问题：语气不统一、用词不当、节奏问题等\n" +
                "7. **词藻堆砌**：过度使用华丽辞藻、形容词堆叠、修饰语过多，导致文字冗余、阅读不流畅\n" +
                "8. **生僻词汇**：使用过于冷门、晦涩的词汇，影响读者理解，建议替换为更通俗易懂的表达\n" +
                "9. **AI痕迹消除（重点）**：\n" +
                "   - 删除对文章影响不大的修饰词、形容词（目标：减少约100字）\n" +
                "   - 将部分语气助词\"地\"改为\"的\"（约30%-40%的\"地\"字）\n" +
                "   - 这样可以让文章更像人类写的，避免被AI检测工具识别\n" +
                "\n" +
                "【词藻堆砌识别标准】\n" +
                "- 一句话中出现3个以上形容词修饰同一对象\n" +
                "- 连续使用多个华丽但无实际意义的修饰语\n" +
                "- 过度使用成语、四字词语堆砌\n" +
                "- 形容词、副词过多，动词、名词过少\n" +
                "- 例如：\"那绝美无比、倾国倾城、美若天仙、宛如仙子般的容颜\" → 建议简化为 \"那绝美的容颜\"\n" +
                "\n" +
                "【生僻词汇识别标准】\n" +
                "- 使用冷门、古文、文言文词汇，普通读者难以理解\n" +
                "- 使用专业术语但未解释，影响阅读体验\n" +
                "- 建议替换为更常见、通俗的同义词\n" +
                "- 例如：\"踟蹰\" → \"犹豫\"，\"睥睨\" → \"轻视\"，\"踌躇满志\" → \"信心满满\"\n" +
                "\n" +
                "【AI痕迹消除策略（核心）】\n" +
                "1. 地/的用法（仅在原文有“地”时）\n" +
                "- 仅当原文中存在“地”字时，才考虑将“地”改为“的”；如果原文已经是“的”或没有“地”，不要提出这种修改。\n" +
                "- 只改大约30%-40%的“地”，优先选择不影响理解、不产生歧义的位置。\n" +
                "\n" +
                "2. 轻度删减修饰词\n" +
                "- 目标：在不影响内容的前提下，总字数大约减少100字（可随篇幅略微浮动）。\n" +
                "- 优先删除：对情节推进没有实质作用的形容词、重复修饰、程度副词（非常/十分/极其/相当等）、名词前的多重形容词堆砌（只保留最核心一个）、明显堆砌的极端词（最/绝对/终极等）。\n" +
                "- 重点关注：对同一情绪或气氛的连续多句描写、层层比喻、主要只是反复渲染而几乎不推进剧情的段落（如多句都在写某人的恐惧/冷意/火光等），可以合并为1到2句，保留最有冲击力的画面或动作，同时保留关键转折信息（如人物“变了”“态度反转”等）。\n" +
                "- 对“不仅…更是/而且/还/甚至…”这类结构，要尽量保留“更是/而且/还/甚至”等结构词；如需简化，只删除其中的比喻或修饰短语（如“竹筒倒豆子般”），不要把这些结构词一起删掉；若整个结构显得多余，优先用replace重写整句。\n" +
                "- 删除前必须检查：删掉后句子仍然完整、语法正确、语义清晰；如会破坏句子结构或读起来别扭，就不要用delete，而是用replace对整句改写；宁可不删，也不要删坏句子。\n" +
                "\n" +
                "3. 其他轻微消痕\n" +
                "- 可以减少明显的总结性句式（如“总而言之”“综上所述”）和过于工整的排比句，改成更自然的叙述。\n" +
                "- 可以适度减少不必要的引号和过多的感叹号，将情绪更多通过动作、神态、心理来呈现，但不要改变原意和情绪基调。\n" +
                "- 可以适当打乱过于工整的句式结构，略微口语化表达，使文本更接近人类自然写作。\n" +
                "\n" +
                "【诊断原则】\n" +
                "- 只提出有价值的建议，不要过度挑剔\n" +
                "- 尊重作者的创作风格和表达习惯\n" +
                "- 网络小说的口语化、网络用语不算问题\n" +
                "- 优先指出影响阅读体验的严重问题\n" +
                "- 对于改进建议，要给出具体的修改方案\n" +
                "- 适度的修饰是必要的，只标记过度堆砌的情况\n" +
                "- 常见的成语、俗语不算生僻词汇\n" +
                "- **重点关注AI痕迹消除**：每次诊断都要找出可以删除的冗余修饰词和可以改为\"的\"的\"地\"字\n" +
                "- **【删除操作的核心原则】**\n" +
                "  * 使用delete操作前，必须确保删除后句子完整、通顺、语义清晰\n" +
                "  * 删除句中或句尾成分时，如该成分前后存在逗号、顿号、连词（和/但是/却/而是等），需要一并考虑，避免留下多余标点或孤立连词\n" +
                "  * 如果删除会导致句子不完整或不通顺，必须改用replace操作\n" +
                "  * 宁可不删除，也不要让句子变得不通顺\n" +
                "\n" +
                "【操作类型】\n" +
                "- replace: 替换原文为建议文本（用于改进、修正、简化词藻、替换生僻词、将\"地\"改为\"的\"）\n" +
                "  * **当冗余内容删除后会导致句子不完整时，必须使用replace而不是delete**\n" +
                "  * 例如：\"充满了算计\" 如果要简化，应该replace整个句子，而不是delete这个短语\n" +
                "- delete: 删除原文（用于冗余内容、多余修饰词、无意义的形容词）\n" +
                "  * **仅用于删除后句子依然完整、通顺、语义清晰的情况**\n" +
                "  * 例如：\"非常美丽的\" 可以delete \"非常\"，因为剩下\"美丽的\"句子依然完整\n" +
                "  * 删除句中插入语或修饰成分时，应将需要一并去掉的逗号/顿号等标点一并包含在original中，避免出现\"，，\"或句首多余逗号\n" +
                "  * 在“不仅…更是/而且/还/甚至…”结构中，original 不要覆盖“更是/而且/还/甚至”等结构词，只包含真正要删的修饰或比喻短语，否则会破坏整句话的对仗关系\n" +
                "  * 例如：\"一夜之间，天翻地覆\" 不能delete \"一夜之间，\"，应该replace整个句子为\"平阳侯府天翻地覆\"\n" +
                "- insert: 在指定位置插入内容（用于补充）\n" +
                "\n" +
                "【严重程度】\n" +
                "- high: 严重问题，影响阅读理解（如严重的逻辑错误、大量生僻词）\n" +
                "- medium: 中等问题，建议修改（如词藻堆砌、一般的生僻词）\n" +
                "- low: 轻微问题，可选修改（如轻微的冗余、可读性尚可的修饰、\"地\"改\"的\"、删除冗余修饰词）\n" +
                "\n" +
                "【输出格式】\n" +
                "请以JSON数组格式输出所有建议，每个建议包含以下字段：\n" +
                "{\n" +
                "  \"type\": \"建议类型(grammar/logic/redundant/improvement/inconsistency/style)\",\n" +
                "  \"action\": \"操作类型(replace/delete/insert)\",\n" +
                "  \"original\": \"原始文本\",\n" +
                "  \"suggested\": \"建议的文本(delete时为null)\",\n" +
                "  \"position\": 位置索引,\n" +
                "  \"length\": 原始文本长度,\n" +
                "  \"context\": \"上下文(前后各30字)\",\n" +
                "  \"reason\": \"建议原因的详细说明（如：词藻堆砌，建议简化；生僻词汇，建议使用更通俗的表达；将'地'改为'的'使文章更像人类写的；删除冗余修饰词减少AI痕迹）\",\n" +
                "  \"severity\": \"严重程度(high/medium/low)\"\n" +
                "}\n" +
                "\n" +
                "【特别提醒】\n" +
                "- 每次诊断都要找出至少5-10个可以删除的冗余修饰词（目标减少约100字）\n" +
                "- 每次诊断都要找出至少3-5个可以将\"地\"改为\"的\"的地方（约30%-40%的\"地\"字）\n" +
                "- 每次诊断都要主动检查是否存在对同一情绪/气氛进行连续多句渲染但几乎不推进剧情的段落，尽量在不影响转折和关键信息的前提下合并为1-2句\n" +
                "- 这些修改的severity都设为\"low\"，因为它们不影响阅读，但能有效消除AI痕迹\n" +
                "- 如果没有建议，返回空数组 []\n" +
                "- 只输出JSON数组，不要添加任何其他文字说明。";
    }

    /**
     * 执行智能建议诊断
     *
     * @param content 待诊断的文本内容
     * @param aiConfig AI配置
     * @return 建议列表
     */
    public List<SmartSuggestion> analyzeSuggestions(String content, AIConfigRequest aiConfig) {
        try {
            logger.info("🔍 开始AI智能建议诊断，内容长度: {}", content.length());
            
            // 构建用户消息
            StringBuilder userBuilder = new StringBuilder();
            userBuilder.append("【待诊断文本】\n");
            userBuilder.append(content.trim());
            userBuilder.append("\n\n");
            userBuilder.append("请仔细分析上述文本，找出所有问题并提供改进建议，以JSON数组格式返回。");

            // 调用AI
            String aiResponse = callAI(aiConfig, buildSystemPrompt(), userBuilder.toString());
            
            // 解析AI返回的JSON
            List<SmartSuggestion> suggestions = parseAIResponse(aiResponse, content);
            
            logger.info("✅ AI智能建议完成，共 {} 条建议", suggestions.size());
            
            return suggestions;
            
        } catch (Exception e) {
            logger.error("❌ AI智能建议失败", e);
            throw new RuntimeException("AI智能建议失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析AI返回的JSON响应
     */
    private List<SmartSuggestion> parseAIResponse(String aiResponse, String originalContent) {
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
                logger.warn("⚠️ AI返回的不是有效的JSON数组: {}", cleaned.substring(0, Math.min(100, cleaned.length())));
                return new ArrayList<>();
            }
            
            // 解析JSON数组
            List<SmartSuggestion> suggestions = objectMapper.readValue(cleaned, new TypeReference<List<SmartSuggestion>>() {});
            
            // 验证和修正字段
            for (SmartSuggestion suggestion : suggestions) {
                // 修正position
                if (suggestion.getPosition() < 0 || suggestion.getPosition() >= originalContent.length()) {
                    int foundPos = originalContent.indexOf(suggestion.getOriginal());
                    if (foundPos >= 0) {
                        suggestion.setPosition(foundPos);
                    } else {
                        suggestion.setPosition(0);
                    }
                }
                
                // 修正length
                if (suggestion.getLength() <= 0 && suggestion.getOriginal() != null) {
                    suggestion.setLength(suggestion.getOriginal().length());
                }
                
                // 如果没有context，自动生成
                if (suggestion.getContext() == null || suggestion.getContext().isEmpty()) {
                    suggestion.setContext(generateContext(originalContent, suggestion.getPosition(), suggestion.getLength()));
                }
                
                // 确保severity有默认值
                if (suggestion.getSeverity() == null || suggestion.getSeverity().isEmpty()) {
                    suggestion.setSeverity("medium");
                }
            }
            
            return suggestions;
            
        } catch (Exception e) {
            logger.error("❌ 解析AI建议响应失败: {}", aiResponse, e);
            return new ArrayList<>();
        }
    }

    /**
     * 生成位置的上下文
     */
    private String generateContext(String content, int position, int length) {
        int contextRadius = 30;
        int start = Math.max(0, position - contextRadius);
        int end = Math.min(content.length(), position + length + contextRadius);
        
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
            requestBody.put("max_tokens", 6000);
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
            
            logger.info("🔄 调用AI智能建议接口: {}, model: {}", apiUrl, aiConfig.getModel());
            
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
            
            logger.info("✅ AI智能建议接口调用成功");
            return content;
            
        } catch (Exception e) {
            logger.error("❌ AI接口调用异常", e);
            throw new RuntimeException("AI接口调用失败: " + e.getMessage());
        }
    }
}
