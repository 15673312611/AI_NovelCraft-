package com.novel.service;

import com.novel.domain.entity.Novel;
import com.novel.domain.entity.AITask;
import com.novel.domain.entity.AITask.AITaskType;
import com.novel.domain.entity.AITask.AITaskStatus;
import com.novel.domain.entity.User;
import com.novel.dto.AITaskDto;
import org.springframework.beans.factory.annotation.Autowired;
import com.novel.config.AIClientConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpMethod;

import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

/**
 * AI写作服务
 * 实现分步骤的智能写作流程
 */
@Service
@Transactional
public class AIWritingService {

    private static final Logger logger = LoggerFactory.getLogger(AIWritingService.class);

    @Autowired
    private AITaskService aiTaskService;

    @Autowired
    private NovelService novelService;

    @Autowired
    private AIClientConfig aiConfig;
    
    @Autowired
    private LongNovelMemoryManager longNovelMemoryManager;

    /**
     * 开始AI写作流程
     */
    public AITaskDto startWritingProcess(Novel novel, User user, String writingType) {
        AITask task = new AITask();
        task.setName("AI写作 - " + novel.getTitle());
        task.setType(AITask.AITaskType.CHAPTER_WRITING);
        task.setStatus(AITask.AITaskStatus.PENDING);
        task.setUserId(user.getId());
        task.setNovelId(novel.getId());
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("writingType", writingType);
        parameters.put("novelId", novel.getId());
        parameters.put("step", "outline");
        
        task.setParameters(parameters.toString());
        task.setCreatedAt(LocalDateTime.now());
        
        return aiTaskService.createTask(task);
    }

    /**
     * 生成故事大纲
     */
    public String generateOutline(Novel novel, String genre, String theme) {
        String prompt = buildOutlinePrompt(novel, genre, theme);
        return callAI(prompt);
    }

    /**
     * 生成角色档案
     */
    public String generateCharacterProfile(String characterName, String role, String background) {
        String prompt = buildCharacterPrompt(characterName, role, background);
        return callAI(prompt);
    }

    /**
     * 生成章节内容
     */
    public String generateChapterContent(Novel novel, String chapterTitle, String outline, String previousContent) {
        String prompt = buildChapterPrompt(novel, chapterTitle, outline, previousContent);
        return callAI(prompt);
    }

    /**
     * 通用内容生成方法
     * @param prompt 提示词
     * @param type 生成类型（用于日志记录和参数优化）
     * @return 生成的内容
     */
    public String generateContent(String prompt, String type) {
        logger.info("开始生成内容，类型: {}", type);
        return callAIWithType(prompt, type);
    }

    /**
     * 非流式调用AI服务（带AI配置）
     * @param prompt 提示词
     * @param type 生成类型
     * @param aiConfig AI配置
     * @return 生成的完整内容
     */
    public String generateContent(String prompt, String type, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("开始生成内容（带AI配置），类型: {}", type);
        
        // 验证AI配置
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new RuntimeException("AI配置无效，请先在设置页面配置AI服务");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        
        // 根据提供商设置合适的 max_tokens
        int maxTokens = getMaxTokensForProvider(aiConfig.getProvider(), model);
        
        // 构建请求体（非流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", false); // 非流式
        
        // 根据生成类型优化参数
        Map<String, Object> optimizedParams = getOptimizedParameters(type);
        requestBody.putAll(optimizedParams);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        
        // 发送HTTP请求
        try {
            String url = aiConfig.getApiUrl();
            logger.info("🌐 调用AI接口（非流式）: {}", url);
            
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
                    if (messageObj != null) {
                        String content = (String) messageObj.get("content");
                        logger.info("✅ AI调用成功，返回内容长度: {} 字符", content != null ? content.length() : 0);
                        return content;
                    }
                }
            }
            
            throw new RuntimeException("AI返回内容为空");
            
        } catch (Exception e) {
            logger.error("AI服务调用失败（带配置），类型: {}", type, e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 流式调用AI服务（支持实时流式响应）
     * 说明：真正的流式调用，逐块返回AI生成内容
     */
    public void streamGenerateContent(String prompt, String type, com.novel.dto.AIConfigRequest aiConfigRequest, java.util.function.Consumer<String> chunkConsumer) {
        // 验证AI配置
        if (aiConfigRequest == null || !aiConfigRequest.isValid()) {
            throw new RuntimeException("AI配置无效，请先在设置页面配置AI服务");
        }
        
        String baseUrl = aiConfigRequest.getEffectiveBaseUrl();
        String apiKey = aiConfigRequest.getApiKey();
        String model = aiConfigRequest.getModel();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key未配置");
        }

        // 构建请求体（启用流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        
        // 根据提供商设置合适的 max_tokens
        int maxTokens = getMaxTokensForProvider(aiConfigRequest.getProvider(), model);
        requestBody.put("max_tokens", maxTokens);   
        requestBody.put("stream", true); // 启用流式响应
        
        // 根据生成类型优化参数
        Map<String, Object> optimizedParams = getOptimizedParameters(type);
        requestBody.putAll(optimizedParams);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);

        // 发送HTTP请求（流式读取）
        try {
            String url = aiConfigRequest.getApiUrl();
            logger.info("🌐 调用AI接口: {}", url);
            
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


            // 使用ResponseExtractor进行流式读取
            restTemplate.execute(url, HttpMethod.POST, 
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody));
                },
                response -> {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                        
                        String line;
                        boolean connectionClosed = false;
                        while ((line = reader.readLine()) != null && !connectionClosed) {
                            line = line.trim();
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break; // 流式响应结束
                                }
                                
                                try {
                                    // 解析JSON数据
                                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                    java.util.Map dataMap = om.readValue(data, java.util.Map.class);
                                    
                                    Object choicesObj = dataMap.get("choices");
                                    if (choicesObj instanceof java.util.List) {
                                        java.util.List choices = (java.util.List) choicesObj;
                                        if (!choices.isEmpty() && choices.get(0) instanceof java.util.Map) {
                                            java.util.Map firstChoice = (java.util.Map) choices.get(0);
                                            Object deltaObj = firstChoice.get("delta");
                                            if (deltaObj instanceof java.util.Map) {
                                                Object content = ((java.util.Map) deltaObj).get("content");
                                                if (content instanceof String && !((String) content).isEmpty()) {
                                                    String chunk = (String) content;
                                                    // 过滤掉 <think> 标签及其内容
                                                    chunk = chunk.replaceAll("<think>.*?</think>", "");
                                                    chunk = chunk.replaceAll("<think>.*", ""); // 处理未闭合的情况
                                                    chunk = chunk.replaceAll(".*</think>", ""); // 处理跨chunk的结束标签
                                                    
                                                    if (!chunk.isEmpty()) {
                                                        // 回调给消费者 (保留换行符，不要trim)
                                                        try {
                                                            chunkConsumer.accept(chunk);
                                                        } catch (Exception ce) {
                                                            // 连接已断开，停止处理后续数据
                                                            if (ce.getMessage() != null && ce.getMessage().contains("already completed")) {
                                                                logger.warn("⚠️ 客户端连接已断开，停止发送数据");
                                                                connectionClosed = true;
                                                                break;
                                                            }
                                                            throw ce; // 其他异常继续抛出
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // 如果是连接断开，停止处理
                                    if (e.getMessage() != null && e.getMessage().contains("already completed")) {
                                        logger.warn("⚠️ 客户端连接已断开，停止处理数据");
                                        connectionClosed = true;
                                        break;
                                    }
                                    // 解析失败，跳过这一行
                                    logger.warn("解析流式响应数据失败: {}", e.getMessage());
                                }
                            }
                        }
                    }
                    return null;
                });
                
        } catch (Exception e) {
            logger.error("流式AI调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("流式AI调用失败: " + e.getMessage());
        }
    }

    /**
     * 构建大纲生成提示词
     */
    private String buildOutlinePrompt(Novel novel, String genre, String theme) {
        return String.format( "请为小说《%s》生成一个详细的故事大纲。\n" +
                        "\n" +
                        "小说信息：\n" +
                        "- 类型：%s\n" +
                        "- 主题：%s\n" +
                        "- 描述：%s\n" +
                        "\n" +
                        "要求：\n" +
                        "1. 采用三幕结构（开端、发展、高潮、结局）\n" +
                        "2. 每个章节都要有明确的目标和冲突\n" +
                        "3. 包含主要情节转折点\n" +
                        "4. 确保故事逻辑连贯\n" +
                        "5. 适合%s类型小说的特点\n",
            novel.getTitle(), genre, theme, novel.getDescription(), genre
        );
    }

    /**
     * 构建角色生成提示词
     */
    private String buildCharacterPrompt(String characterName, String role, String background) {
        return String.format("请为角色【%s】创建详细的角色档案。\n\n" +
                "角色信息：\n" +
                "- 姓名：%s\n" +
                "- 角色：%s\n" +
                "- 背景：%s\n\n" +
                "请包含：基本信息、性格特点、背景故事、动机目标、角色关系、角色弧线、对话风格。",
            characterName, characterName, role, background
        );
    }

    /**
     * 构建章节生成提示词
     */
    private String buildChapterPrompt(Novel novel, String chapterTitle, String outline, String previousContent) {
        return String.format("请为小说《%s》的章节【%s】生成内容。\n\n" +
                "章节大纲：%s\n" +
                "前文内容：%s\n\n" +
                "要求：字数2000-3000字，保持故事节奏，符合%s类型风格，注意对话真实性。",
            novel.getTitle(), chapterTitle, outline, previousContent, novel.getGenre()
        );
    }

    /**
     * 调用AI服务
     */
    private String callAI(String prompt) {
        try {
            return callRealAI(prompt);
        } catch (Exception e) {
            // 不再返回模拟数据，直接抛出，让上层记录真实原因
            logger.error("AI服务调用失败", e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据类型调用AI服务
     */
    private String callAIWithType(String prompt, String type) {
        try {
            return callRealAIWithType(prompt, type);
        } catch (Exception e) {
            logger.error("AI服务调用失败，类型: {}", type, e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用真实AI服务
     */
    private String callRealAI(String prompt) throws Exception {
        return callRealAIWithType(prompt, "default");
    }

    /**
     * 根据类型调用真实AI服务
     */
    private String callRealAIWithType(String prompt, String type) throws Exception {
        String baseUrl = aiConfig.getBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getDefaultModel();

        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            throw new RuntimeException("API Key未配置");
        }

        // 构建请求体（显式关闭流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 2000);
        requestBody.put("stream", false);
        
        // 根据生成类型优化参数
        Map<String, Object> optimizedParams = getOptimizedParameters(type);
        requestBody.putAll(optimizedParams);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);

        // 发送HTTP请求（设置超时，避免大回复时读超时/断开）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15000);
        requestFactory.setReadTimeout(120000);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        // 确保支持 text/plain 与 JSON
        restTemplate.getMessageConverters().add(0, new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8));
        restTemplate.getMessageConverters().add(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.setAccept(java.util.Arrays.asList(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = baseUrl + "/v1/chat/completions";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String body = response.getBody();
            MediaType ct = response.getHeaders().getContentType();
            logger.info("AI响应content-type={}, length={}", ct, body.length());

            // 先尝试按JSON解析（有些服务虽然是text/plain但实际返回JSON）
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map responseBody = om.readValue(body, java.util.Map.class);

                Object choicesObj = responseBody.get("choices");
                if (choicesObj instanceof java.util.List) {
                    java.util.List choices = (java.util.List) choicesObj;
                    if (!choices.isEmpty() && choices.get(0) instanceof java.util.Map) {
                        java.util.Map firstChoice = (java.util.Map) choices.get(0);
                        Object messageObj = firstChoice.get("message");
                        if (messageObj instanceof java.util.Map) {
                            Object content = ((java.util.Map) messageObj).get("content");
                            if (content instanceof String && !((String) content).trim().isEmpty()) return (String) content;
                        }
                        Object directContent = firstChoice.get("content");
                        if (directContent instanceof String && !((String) directContent).trim().isEmpty()) return (String) directContent;
                        Object text = firstChoice.get("text");
                        if (text instanceof String && !((String) text).trim().isEmpty()) return (String) text;
                        Object deltaObj = firstChoice.get("delta");
                        if (deltaObj instanceof java.util.Map) {
                            Object deltaContent = ((java.util.Map) deltaObj).get("content");
                            if (deltaContent instanceof String && !((String) deltaContent).trim().isEmpty()) return (String) deltaContent;
                        }
                    }
                }
                Object outputText = responseBody.get("output_text");
                if (outputText instanceof String && !((String) outputText).trim().isEmpty()) return (String) outputText;
                Object topContent = responseBody.get("content");
                if (topContent instanceof String && !((String) topContent).trim().isEmpty()) return (String) topContent;

                // JSON无法提取明确正文，兜底返回原始文本
                return body;
            } catch (Exception ignoreIfNotJson) {
                // 不是JSON，继续下面的逻辑
            }

            // 非JSON文本，直接返回
            return body;
        }

        throw new RuntimeException("AI服务响应异常，状态码: " + response.getStatusCode());
    }

    /**
     * 当AI服务不可用时抛出异常，不再使用模拟数据
     */
    private String generateMockResponse(String prompt) {
        throw new RuntimeException("AI服务暂时不可用，请检查配置或稍后重试");
    }

    /**
     * 获取优化的AI参数配置
     * @param type 生成类型
     * @return 参数配置
     */
    private Map<String, Object> getOptimizedParameters(String type) {
        Map<String, Object> params = new HashMap<>();
        
        switch (type) {
            case "chapter_writing":
                params.put("temperature", 0.85);  // 进一步增加创造性
                params.put("top_p", 0.92);
                params.put("frequency_penalty", 0.15);  // 减少重复
                params.put("presence_penalty", 0.15);   // 增加多样性
                break;
            case "content_summarization":
                params.put("temperature", 0.3);  // 更保守
                params.put("top_p", 0.8);
                break;
            case "work_position_generation":
                params.put("temperature", 0.6);  // 中等创造性
                params.put("top_p", 0.85);
                break;
            case "chapter_revision":
                params.put("temperature", 0.7);  // 保持原有风格
                params.put("top_p", 0.9);
                break;
            case "chapter_optimization":
                params.put("temperature", 0.9);  // 高度创造性优化
                params.put("top_p", 0.95);
                params.put("frequency_penalty", 0.2);  // 大幅减少重复
                params.put("presence_penalty", 0.2);   // 大幅增加多样性
                break;
            default:
                params.put("temperature", 0.7);
                params.put("top_p", 0.9);
        }
        
        return params;
    }
    
    /**
     * 根据AI提供商和模型获取合适的 max_tokens 值
     * 不同提供商对 max_tokens 的限制不同
     */
    private int getMaxTokensForProvider(String provider, String model) {
        if (provider == null) {
            return 4000; // 默认值
        }
        
        String providerLower = provider.toLowerCase();
        
        switch (providerLower) {
            case "deepseek":
                // DeepSeek 模型支持较大的 max_tokens
                if (model != null && model.contains("reasoner")) {
                    // R1 推理模型，使用较大的值
                    return 8000;
                }
                return 8000; // DeepSeek 其他模型
                
            case "qwen":
            case "tongyi":
                // 通义千问的限制
                if (model != null && model.contains("max")) {
                    return 8000; // qwen-max 系列
                } else if (model != null && model.contains("longcontext")) {
                    return 6000; // 长上下文模型
                }
                return 6000; // 默认
                
            case "kimi":
                // Kimi (月之暗面) 的限制
                if (model != null && model.contains("k2")) {
                    return 8000; // K2 系列
                } else if (model != null && model.contains("thinking")) {
                    return 8000; // 长思考模型
                }
                return 6000; // 其他 Kimi 模型
                
            case "openai":
                // OpenAI 模型
                if (model != null && model.contains("gpt-4")) {
                    return 8000;
                } else if (model != null && model.contains("gpt-3.5")) {
                    return 4000;
                }
                return 4000;
                
            default:
                // 未知提供商，使用保守的默认值
                logger.warn("未知的AI提供商: {}, 使用默认 max_tokens=4000", provider);
                return 4000;
        }
    }
    
    /**
     * 非流式调用AI服务（支持messages列表）
     * @param messages 消息列表
     * @param type 生成类型
     * @param aiConfig AI配置
     * @return 生成的完整内容
     */
    public String generateContentWithMessages(List<Map<String, String>> messages, String type, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("开始生成内容（messages模式），类型: {}, messages数量: {}", type, messages.size());
        
        // 验证AI配置
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new RuntimeException("AI配置无效，请先在设置页面配置AI服务");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        
        // 根据提供商设置合适的 max_tokens
        int maxTokens = getMaxTokensForProvider(aiConfig.getProvider(), model);
        
        // 构建请求体（非流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", false); // 非流式
        
        // 根据生成类型优化参数
        Map<String, Object> optimizedParams = getOptimizedParameters(type);
        requestBody.putAll(optimizedParams);
        
        requestBody.put("messages", messages);
        
        // 发送HTTP请求
        try {
            String url = aiConfig.getApiUrl();
            logger.info("🌐 调用AI接口（非流式，messages模式）: {}", url);
            
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
                    if (messageObj != null) {
                        String content = (String) messageObj.get("content");
                        logger.info("✅ AI调用成功，返回内容长度: {} 字符", content != null ? content.length() : 0);
                        return content;
                    }
                }
            }
            
            throw new RuntimeException("AI返回内容为空");
            
        } catch (Exception e) {
            logger.error("AI服务调用失败（messages模式），类型: {}", type, e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
    }
    
    /**
     * 流式调用AI服务（支持messages列表）
     * @param messages 消息列表
     * @param type 生成类型
     * @param aiConfig AI配置
     * @param chunkConsumer 处理每个chunk的回调
     */
    public void streamGenerateContentWithMessages(
            List<Map<String, String>> messages, 
            String type, 
            com.novel.dto.AIConfigRequest aiConfig, 
            java.util.function.Consumer<String> chunkConsumer) {
        
        logger.info("开始流式生成（messages模式），类型: {}, messages数量: {}", type, messages.size());
        
        // 验证AI配置
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new RuntimeException("AI配置无效，请先在设置页面配置AI服务");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key未配置");
        }

        // 构建请求体（启用流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        
        // 根据提供商设置合适的 max_tokens
        int maxTokens = getMaxTokensForProvider(aiConfig.getProvider(), model);
        requestBody.put("max_tokens", maxTokens);   
        requestBody.put("stream", true); // 启用流式响应
        
        // 根据生成类型优化参数
        Map<String, Object> optimizedParams = getOptimizedParameters(type);
        requestBody.putAll(optimizedParams);

        requestBody.put("messages", messages);

        // 发送HTTP请求（流式读取）
        try {
            String url = aiConfig.getApiUrl();
            logger.info("🌐 调用AI流式接口（messages模式）: {}", url);
            
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

            // 使用ResponseExtractor进行流式读取
            restTemplate.execute(url, HttpMethod.POST, 
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody));
                },
                response -> {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                        
                        String line;
                        boolean connectionClosed = false;
                        while ((line = reader.readLine()) != null && !connectionClosed) {
                            line = line.trim();
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break; // 流式响应结束
                                }
                                
                                try {
                                    // 解析JSON数据
                                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                    java.util.Map dataMap = om.readValue(data, java.util.Map.class);
                                    
                                    Object choicesObj = dataMap.get("choices");
                                    if (choicesObj instanceof java.util.List) {
                                        java.util.List choices = (java.util.List) choicesObj;
                                        if (!choices.isEmpty() && choices.get(0) instanceof java.util.Map) {
                                            java.util.Map firstChoice = (java.util.Map) choices.get(0);
                                            Object deltaObj = firstChoice.get("delta");
                                            if (deltaObj instanceof java.util.Map) {
                                                Object content = ((java.util.Map) deltaObj).get("content");
                                                if (content instanceof String && !((String) content).isEmpty()) {
                                                    String chunk = (String) content;
                                                    // 过滤掉 <think> 标签及其内容
                                                    chunk = chunk.replaceAll("<think>.*?</think>", "");
                                                    chunk = chunk.replaceAll("<think>.*", ""); // 处理未闭合的情况
                                                    chunk = chunk.replaceAll(".*</think>", ""); // 处理跨chunk的结束标签
                                                    
                                                    if (!chunk.isEmpty()) {
                                                        // 调用回调处理chunk (保留换行符，不要trim)
                                                        try {
                                                            chunkConsumer.accept(chunk);
                                                        } catch (Exception ce) {
                                                            // 连接已断开，停止处理后续数据
                                                            if (ce.getMessage() != null && ce.getMessage().contains("already completed")) {
                                                                logger.warn("⚠️ 客户端连接已断开，停止发送数据");
                                                                connectionClosed = true;
                                                                break;
                                                            }
                                                            throw ce; // 其他异常继续抛出
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // 如果是连接断开，停止处理
                                    if (e.getMessage() != null && e.getMessage().contains("already completed")) {
                                        logger.warn("⚠️ 客户端连接已断开，停止处理数据");
                                        connectionClosed = true;
                                        break;
                                    }
                                    logger.warn("解析流式数据失败: {}", e.getMessage());
                                }
                            }
                        }
                    }
                    return null;
                }
            );
            
            logger.info("✅ 流式生成完成（messages模式）");
            
        } catch (Exception e) {
            logger.error("流式AI调用失败（messages模式）: {}", e.getMessage(), e);
            throw new RuntimeException("流式AI调用失败: " + e.getMessage());
        }
    }
} 