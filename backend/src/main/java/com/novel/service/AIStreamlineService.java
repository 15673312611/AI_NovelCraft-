package com.novel.service;

import com.novel.dto.AIConfigRequest;
import org.apache.commons.math3.geometry.partitioning.BSPTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI精简服务
 * 对小说章节内容进行精简优化，去除冗余片段，加快剧情节奏
 */
@Service
public class AIStreamlineService {

    private static final Logger logger = LoggerFactory.getLogger(AIStreamlineService.class);

    /**
     * AI精简提示词 - 简洁版
     */
    private String buildStreamlinePrompt(Integer targetLength, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("帮我改一下这章 剧情不变 开头进度快一点 不必要的描述去掉 文风不变。\n");
        sb.append("只需要输出重写后的正文，不要解释。\n");
//        if (targetLength != null && targetLength > 0) {
            sb.append("尽量让重写后的长度接近约").append(targetLength).append("字，可以略多或略少。\n");
//        }
        sb.append("正文:").append(content);
        return sb.toString();
    }

    /**
     * AI精简 - 流式处理
     */
    public void streamlineContentStream(String content, Integer targetLength, AIConfigRequest aiConfig, SseEmitter emitter) {
        if (aiConfig == null || !aiConfig.isValid()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("AI配置无效"));
                emitter.completeWithError(new Exception("AI配置无效"));
            } catch (IOException e) {
                logger.error("发送错误失败", e);
            }
            return;
        }
        
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            try {
                emitter.send(SseEmitter.event().name("error").data("API Key未配置"));
                emitter.completeWithError(new Exception("API Key未配置"));
            } catch (IOException e) {
                logger.error("发送错误失败", e);
            }
            return;
        }

        try {
            logger.info("✂️ 开始AI精简，内容长度: {}, targetLength: {}", content.length(), targetLength);
            
            // 构建消息
            List<Map<String, String>> messages = new ArrayList<>();
            
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "user");
            systemMsg.put("content", buildStreamlinePrompt(targetLength,content));
            messages.add(systemMsg);

            // 构建请求体（启用流式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 8000);
            requestBody.put("temperature", 0.7);
            requestBody.put("stream", true);
            requestBody.put("messages", messages);
            
            String url = aiConfig.getApiUrl();
            logger.info("📡 调用AI接口: {}, model: {}, stream: true", url, model);
            
            // 使用OkHttp或者原生HttpURLConnection来精确控制流式读取
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(300000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("Accept", "text/event-stream");
            
            // 使用字节流而不是字符流，避免丢失换行符
            restTemplate.execute(url, HttpMethod.POST, 
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody)
                    );
                },
                response -> {
                    try {
                        // 关键修改：使用字节流读取，保留所有原始字符
                        java.io.InputStream inputStream = response.getBody();
                        byte[] buffer = new byte[8192];
                        StringBuilder lineBuffer = new StringBuilder();
                        int chunkCount = 0;
                        int totalChars = 0;
                        
                        while (true) {
                            int bytesRead = inputStream.read(buffer);
                            if (bytesRead == -1) break;
                            
                            // 将字节转换为字符串
                            String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            lineBuffer.append(chunk);
                            
                            // 按行处理
                            String bufferContent = lineBuffer.toString();
                            String[] lines = bufferContent.split("\n", -1);
                            
                            // 保留最后一个不完整的行
                            lineBuffer = new StringBuilder();
                            if (lines.length > 0) {
                                lineBuffer.append(lines[lines.length - 1]);
                            }
                            
                            // 处理完整的行
                            for (int i = 0; i < lines.length - 1; i++) {
                                String line = lines[i].trim();
                                
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    
                                    if ("[DONE]".equals(data)) {
                                        logger.info("📨 收到流式结束标记 [DONE]，共处理 {} 个chunk，总字符数: {}", chunkCount, totalChars);
                                        inputStream.close();
                                        emitter.complete();
                                        return null;
                                    }
                                    
                                    try {
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
                                                if (contentChunk != null && !contentChunk.isEmpty()) {
                                                    // 发送JSON格式数据，包裹在content字段中
                                                    Map<String, String> eventData = new HashMap<>();
                                                    eventData.put("content", contentChunk);
                                                    emitter.send(SseEmitter.event()
                                                        .name("message")
                                                        .data(eventData));
                                                    chunkCount++;
                                                    totalChars += contentChunk.length();
                                                    
                                                    if (chunkCount == 1) {
                                                        logger.info("✅ 开始接收流式数据");
                                                    }
                                                    
                                                    // 调试：记录换行符数量
                                                    if (chunkCount % 50 == 0) {
                                                        int newlineCount = contentChunk.length() - contentChunk.replace("\n", "").length();
                                                        logger.info("📊 Chunk #{}: 长度={}, 换行符数量={}", chunkCount, contentChunk.length(), newlineCount);
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.warn("⚠️ 解析流式响应失败: {}", e.getMessage());
                                    }
                                }
                            }
                        }
                        
                        inputStream.close();
                        emitter.complete();
                        logger.info("✅ AI精简完成，总chunk数: {}, 总字符数: {}", chunkCount, totalChars);
                        
                    } catch (IOException e) {
                        logger.error("❌ 读取流式响应失败", e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ignored) {}
                    }
                    return null;
                });

        } catch (Exception e) {
            logger.error("❌ AI精简失败", e);
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("精简失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                logger.error("发送错误事件失败", ex);
            }
        }
    }
}
