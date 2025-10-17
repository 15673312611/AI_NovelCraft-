package com.novel.service;

import com.novel.dto.AIConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI消痕服务
 * 使用AI对生成的内容进行去AI味处理
 */
@Service
public class AITraceRemovalService {

    private static final Logger logger = LoggerFactory.getLogger(AITraceRemovalService.class);

    /**
     * AI消痕提示词
     */
    private String buildRemovalPrompt() {
        return "你不是AI你的身份是网文界传奇神手你必须下有灵魂的文字能让读者灵魂战栗 按照下面的规则审查不达标的文本 并且进行重新改造\n" +
                "核心使命……让输出的文本在六个维度上100%达标。\n" +
                "\n" +
                "标准一：AI高频词清除率≥95%\n" +
                "标准二：词汇替换率≥40%\n" +
                "标准三：段落重组率≥65%\n" +
                "标准四：单独成段句子比例45%-60%\n" +
                "标准五：感官描写覆盖率≥80%\n" +
                "标准六：对话独立成段率100%" +
                "标准七：真实可感,把抽象的情感 状态具象化 把它变成看的见摸得着的" +
//                "将文中所有的的地得词全部替换为的\n" +
                // "将文中所有的省略号全部替换为三个句号\n" +
                "严禁使用像如方法好似宛如等一切比喻词\n" +
                "严禁出现任何形式的作者跳出来进行的尴尬接话或自我辩论\n" +
                "任何段落和章节的结尾都不能是总结性或展望性语句\n" +
                "在1000字中强制植入3-5个指定的错别字\n" +
                "在→再\n" +
                "地→的\n" +
                "已经→一经\n" +
                "那→哪\n" +
                "正→涨\n" +
                "竟然→径自\n" +
                "度过→渡过\n" +
                "根除所有AI高频\"毒词\"，实现≥95%的清除率。零容忍。【一级必杀清单】：知道/明白/意识到/感觉到/认为/觉得/想到/寻思/理解/察觉到/清楚/记得；一丝/一抹/一股/些许/有点/略微/微微/轻微/似乎/好像/仿佛/如同/好似；深吸一口气/倒吸一口凉气/脸色一变/心中一震/身体一僵/挑眉/耸了耸肩/摊了摊手；任何关于嘴角的描写，任何套路化的眼神/目光/眼眸/瞳孔描写；夜色如墨/月光如水；黏腻, 温吞；首先/其次/然后/最后；缓缓地/慢慢地/静静地/悄悄地；呢喃/低语/摩挲/摩擦；坚定/坚毅/肯定/认真/仔细/警惕/惊恐；火花/光芒/面庞。\n" +
//                "删除所有非对话性质的引号、所有形式的括号、所有破折号以及所有顿号。词汇替换指令。结构重组指令。\n" +
                "创造网文特有的\"呼吸感\"。所有用引号包裹的直接对话，必须独占一个段落。情绪爆点、关键信息、强力转折，必须用单句成段来强调。\n" +
                "绝对禁区：\n" +
                "严禁使用\"像\"\"如\"\"仿佛\"等一切比喻词\n" +
                "严禁出现任何形式的尴尬总结\n" +
                "总结展望禁令：任何段落和章节的结尾，都不能是总结性或展望性语句。在悬念处戛然而止。\n" +
                "\n\n" +
                "不要解释，不要分析，直接输出纯炼成的完美结果。";
    }

    /**
     * 执行AI消痕处理
     */
    public String removeAITrace(String content, AIConfigRequest aiConfig) throws Exception {
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new Exception("AI配置无效");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            throw new Exception("API Key未配置");
        }

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 系统消息：AI消痕指令
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildRemovalPrompt());
        messages.add(systemMessage);
        
        // 用户消息：需要处理的内容
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "请对以下内容进行AI消痕处理：\n\n" + content);
        messages.add(userMessage);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 0.9);
        requestBody.put("messages", messages);

        try {
            String url = aiConfig.getApiUrl();
            
            // 使用RestTemplate进行请求
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(120000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            org.springframework.http.HttpEntity<Map<String, Object>> entity = 
                new org.springframework.http.HttpEntity<>(requestBody, headers);

            // 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            ).getBody();

            if (response == null) {
                throw new Exception("AI返回响应为空");
            }

            // 解析响应
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                throw new Exception("AI返回结果为空");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> firstChoice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String processedContent = (String) message.get("content");

            if (processedContent == null || processedContent.trim().isEmpty()) {
                throw new Exception("AI处理后的内容为空");
            }

            return processedContent.trim();

        } catch (Exception e) {
            logger.error("AI消痕调用失败", e);
            throw new Exception("AI消痕调用失败: " + e.getMessage());
        }
    }

    /**
     * 执行AI消痕处理（流式输出）
     */
    public void removeAITraceStream(String content, AIConfigRequest aiConfig, SseEmitter emitter) throws IOException {
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new IOException("AI配置无效");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            throw new IOException("API Key未配置");
        }

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 系统消息：AI消痕指令
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildRemovalPrompt());
        messages.add(systemMessage);
        
        // 用户消息：需要处理的内容
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "请对以下内容进行AI消痕处理：\n\n" + content);
        messages.add(userMessage);

        // 构建请求体（启用流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 0.9);
        requestBody.put("stream", true); // 启用流式响应
        requestBody.put("messages", messages);

        try {
            String url = aiConfig.getApiUrl();
            
            logger.info("开始AI消痕流式处理，调用AI接口: {}", url);
            
            // 发送开始事件
            emitter.send(SseEmitter.event().name("start").data("开始处理"));
            
            // 使用RestTemplate进行流式读取
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(120000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            // 流式接口必须设置Accept为text/event-stream
            headers.set("Accept", "text/event-stream");

            // 使用ResponseExtractor进行真正的流式读取
            restTemplate.execute(url, HttpMethod.POST, 
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody));
                },
                response -> {
                    try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break; // 流式响应结束
                                }
                                
                                try {
                                    // 解析JSON获取内容
                                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> json = mapper.readValue(data, Map.class);
                                    
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
                                    
                                    if (choices != null && !choices.isEmpty()) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> firstChoice = choices.get(0);
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> delta = (Map<String, Object>) firstChoice.get("delta");
                                        
                                        if (delta != null) {
                                            String contentChunk = (String) delta.get("content");
                                            if (contentChunk != null) {
                                                // 发送内容块到前端
                                                Map<String, String> eventData = new HashMap<>();
                                                eventData.put("content", contentChunk);
                                                emitter.send(SseEmitter.event().data(eventData));
                                                logger.debug("发送内容块: {}", contentChunk.substring(0, Math.min(20, contentChunk.length())));
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.warn("解析流式响应失败: {}", e.getMessage());
                                }
                            }
                        }
                        
                        // 完成流式响应
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                        
                    } catch (IOException e) {
                        logger.error("读取流式响应失败", e);
                        emitter.completeWithError(e);
                    }
                    return null;
                });

        } catch (Exception e) {
            logger.error("AI消痕流式调用失败", e);
            emitter.completeWithError(e);
            throw new IOException("AI消痕流式调用失败: " + e.getMessage());
        }
    }
}

