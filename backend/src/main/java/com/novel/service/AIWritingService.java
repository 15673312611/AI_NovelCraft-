package com.novel.service;

import com.novel.domain.entity.Novel;
import com.novel.domain.entity.AITask;
import com.novel.domain.entity.AITask.AITaskType;
import com.novel.domain.entity.AITask.AITaskStatus;
import com.novel.domain.entity.User;
import com.novel.dto.AIConfigRequest;
import com.novel.dto.AITaskDto;
import com.novel.common.security.AuthUtils;
import com.novel.exception.InsufficientCreditsException;
import com.novel.service.ai.AIModelCallLogService;
import com.novel.service.ai.AIModelExecutionAttempt;
import com.novel.service.ai.AIModelExecutionAttemptType;
import com.novel.service.ai.AIModelExecutionPlan;
import com.novel.service.ai.AIModelRoutingService;
import com.novel.service.ai.AIModelScene;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import com.novel.config.AIClientConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

/**
 * AI写作服务
 * 实现分步骤的智能写作流程
 */
@Service
public class AIWritingService {

    private static final Logger logger = LoggerFactory.getLogger(AIWritingService.class);

    private static final class CostSnapshot {
        private final String modelId;
        private final BigDecimal multiplier;

        private CostSnapshot(String modelId, BigDecimal multiplier) {
            this.modelId = modelId;
            this.multiplier = multiplier;
        }
    }

    private boolean isSseDataLine(String line) {
        return line != null && line.startsWith("data:");
    }

    private String stripSseDataPrefix(String line) {
        if (line == null || !line.startsWith("data:")) {
            return "";
        }
        String value = line.substring(5);
        return value.startsWith(" ") ? value.substring(1) : value;
    }

    private String extractStreamingContentChunk(Map dataMap) {
        if (dataMap == null) {
            return null;
        }

        Object choicesObj = dataMap.get("choices");
        if (!(choicesObj instanceof List)) {
            return extractStreamingTextValue(dataMap.get("content"));
        }

        List choices = (List) choicesObj;
        if (choices.isEmpty() || !(choices.get(0) instanceof Map)) {
            return null;
        }

        Map firstChoice = (Map) choices.get(0);

        String text = extractStreamingTextValue(firstChoice.get("text"));
        if (text != null) {
            return text;
        }

        Object deltaObj = firstChoice.get("delta");
        if (deltaObj instanceof Map) {
            Map deltaMap = (Map) deltaObj;
            text = extractStreamingTextValue(deltaMap.get("content"));
            if (text != null) {
                return text;
            }
            text = extractStreamingTextValue(deltaMap.get("text"));
            if (text != null) {
                return text;
            }
        }

        Object messageObj = firstChoice.get("message");
        if (messageObj instanceof Map) {
            text = extractStreamingTextValue(((Map) messageObj).get("content"));
            if (text != null) {
                return text;
            }
        }

        return extractStreamingTextValue(firstChoice.get("content"));
    }

    private String extractStreamingTextValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (!(value instanceof List)) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (Object item : (List) value) {
            if (item instanceof String) {
                builder.append((String) item);
                continue;
            }
            if (!(item instanceof Map)) {
                continue;
            }

            Map part = (Map) item;
            Object text = part.get("text");
            if (text instanceof String) {
                builder.append((String) text);
                continue;
            }
            Object content = part.get("content");
            if (content instanceof String) {
                builder.append((String) content);
            }
        }

        return builder.length() == 0 ? null : builder.toString();
    }

    private String sanitizeStreamingChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }

        String sanitized = chunk.replaceAll("<think>.*?</think>", "");
        sanitized = sanitized.replaceAll("<think>.*", "");
        sanitized = sanitized.replaceAll(".*</think>", "");
        return sanitized;
    }

    /**
     * 任务类型到友好描述的映射
     */
    private static final Map<String, String> TYPE_DESCRIPTIONS = new HashMap<>();
    static {
        TYPE_DESCRIPTIONS.put("chapter_writing", "章节内容生成");
        TYPE_DESCRIPTIONS.put("content_summarization", "章节概要生成");
        TYPE_DESCRIPTIONS.put("core_state_extraction", "核心状态抽取");
        TYPE_DESCRIPTIONS.put("entity_extraction", "实体关系抽取");
        TYPE_DESCRIPTIONS.put("chapter_revision", "章节修订");
        TYPE_DESCRIPTIONS.put("chapter_optimization", "章节优化");
        TYPE_DESCRIPTIONS.put("chapter_rewrite", "章节重写");
        TYPE_DESCRIPTIONS.put("chapter_concise", "章节精简");
        TYPE_DESCRIPTIONS.put("outline_generation_stream", "大纲生成");
        TYPE_DESCRIPTIONS.put("outline_optimization", "大纲优化");
        TYPE_DESCRIPTIONS.put("volume_outline_generation", "卷大纲生成");
        TYPE_DESCRIPTIONS.put("volume_outline_optimization", "卷大纲优化");
        TYPE_DESCRIPTIONS.put("volume_blueprint_modification", "卷蓝图修改");
        TYPE_DESCRIPTIONS.put("volume_chapter_outlines_generation", "章纲批量生成");
        TYPE_DESCRIPTIONS.put("volume_chapter_outlines_generation_missing", "章纲补充生成");
        TYPE_DESCRIPTIONS.put("chapter_outline_from_content", "章纲反推生成");
        TYPE_DESCRIPTIONS.put("volume_character_profiles", "本卷人设生成");
        TYPE_DESCRIPTIONS.put("creative_ideas_generation", "创意点子生成");
        TYPE_DESCRIPTIONS.put("agent_decision", "智能决策");
        TYPE_DESCRIPTIONS.put("agent_reflection", "智能反思");
        TYPE_DESCRIPTIONS.put("narrative_evaluation", "叙事评估");
        TYPE_DESCRIPTIONS.put("rolling_planning", "滚动规划");
        TYPE_DESCRIPTIONS.put("ai_chat", "AI对话");
        TYPE_DESCRIPTIONS.put("chapter_title_generation", "章节取名");
        TYPE_DESCRIPTIONS.put("ai_review", "AI审稿");
        TYPE_DESCRIPTIONS.put("ai_trace_removal", "AI消痕");
        TYPE_DESCRIPTIONS.put("ai_streamline", "AI精简");
    }

    /**
     * 获取任务类型的友好描述
     */
    static {
        TYPE_DESCRIPTIONS.put("chapter_summary", "章节概要生成");
        TYPE_DESCRIPTIONS.put("chapter_memory_extraction", "章节概要与记忆提取");
    }

    private static String getTypeDescription(String type) {
        return TYPE_DESCRIPTIONS.getOrDefault(type, type);
    }

    private CostSnapshot captureCostSnapshot(String modelId) {
        return new CostSnapshot(modelId, systemAIConfigService.resolveCostMultiplier(modelId));
    }

    private void chargeCompletedRequest(Long userId, CostSnapshot costSnapshot, int inputTokens, int outputTokens, String type) {
        if (userId == null || outputTokens <= 0 || costSnapshot == null) {
            return;
        }

        BigDecimal actualCost = systemAIConfigService.calculateCostByMultiplier(costSnapshot.multiplier, inputTokens, outputTokens);
        boolean deducted = creditService.consume(userId, actualCost, null, costSnapshot.modelId,
            inputTokens, outputTokens, getTypeDescription(type));
        if (deducted) {
            logger.info("馃挵 鎵ｈ垂鎴愬姛: {} 鐐? 鐢ㄦ埛ID: {}, 杈撳叆: {} tokens, 杈撳嚭: {} tokens",
                actualCost, userId, inputTokens, outputTokens);
        } else {
            logger.warn("鈿狅笍 鎵ｈ垂澶辫触锛岀敤鎴稩D: {}, 閲戦: {}", userId, actualCost);
        }
    }

    @Autowired
    private AITaskService aiTaskService;

    @Autowired
    private NovelService novelService;

    @Autowired
    private AIClientConfig aiConfig;

    @Autowired
    private LongNovelMemoryManager longNovelMemoryManager;

    @Autowired
    private AIConfigService aiConfigService;

    @Autowired
    private CreditService creditService;

    @Autowired
    private ManagedPromptService managedPromptService;

    @Autowired
    private SystemAIConfigService systemAIConfigService;

    @Autowired
    private AIModelRoutingService aiModelRoutingService;

    @Autowired
    private AIModelCallLogService aiModelCallLogService;

    /**
     * 开始AI写作流程
     */
    @Transactional
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String generateOutline(Novel novel, String genre, String theme) {
        String prompt = buildOutlinePrompt(novel, genre, theme);
        return callAI(prompt);
    }

    /**
     * 生成角色档案
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String generateCharacterProfile(String characterName, String role, String background) {
        String prompt = buildCharacterPrompt(characterName, role, background);
        return callAI(prompt);
    }

    /**
     * 生成章节内容
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String generateContent(String prompt, String type) {
        logger.info("开始生成内容，类型: {}", type);
        if (AIModelScene.fromTaskType(type) != null) {
            return generateContent(prompt, type, null);
        }
        return callAIWithType(prompt, type);
    }

    /**
     * 非流式调用AI服务（带AI配置）
     * @param prompt 提示词
     * @param type 生成类型
     * @param aiConfig AI配置
     * @return 生成的完整内容
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String generateContent(String prompt, String type, com.novel.dto.AIConfigRequest aiConfig) {
        if (aiModelRoutingService != null) {
            AIModelExecutionPlan executionPlan = aiModelRoutingService.buildExecutionPlan(aiConfig, type);
            return executeWithFailover(
                executionPlan,
                type,
                attemptConfig -> generateContentOnce(prompt, type, attemptConfig)
            );
        }
        aiConfig = aiModelRoutingService.resolveTaskConfig(aiConfig, type);
        logger.info("开始生成内容（带AI配置），类型: {}", type);

        // 获取当前用户ID
        Long userId = AuthUtils.getCurrentUserId();

        // 如果AI配置无效，从系统配置读取
        if (aiConfig == null || !aiConfig.isValid()) {
            logger.info("📡 AI配置无效，从系统配置读取...");
            try {
                String modelId = aiConfig != null ? aiConfig.getModel() : null;
                aiConfig = aiConfigService.getSystemAIConfig(modelId);
                logger.info("✅ 已从系统配置加载AI配置: provider={}, model={}",
                        aiConfig.getProvider(), aiConfig.getModel());
            } catch (Exception e) {
                logger.error("❌ 加载系统AI配置失败: {}", e.getMessage());
                throw new RuntimeException("AI配置无效，请先在后台管理页面配置AI服务: " + e.getMessage());
            }
        }

        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        CostSnapshot costSnapshot = captureCostSnapshot(model);

        // 计算输入token数
        int inputTokens = prompt != null ? prompt.length() : 0;

        // 检查余额是否大于0
        if (userId != null) {
            if (!creditService.hasEnoughBalance(userId, BigDecimal.ONE)) {
                throw new InsufficientCreditsException("字数点余额不足，请先充值");
            }
        }

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

            // 使用带超时配置的 RestTemplate（连接30秒，读取5分钟）
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000);
            requestFactory.setReadTimeout(300000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)
                    (ResponseEntity<?>) restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
                    if (messageObj != null) {
                        String content = (String) messageObj.get("content");
                        logger.info("✅ AI调用成功，返回内容长度: {} 字符", content != null ? content.length() : 0);

                        // 计算实际消费并扣费
                        int outputTokens = content != null ? content.length() : 0;
                        chargeCompletedRequest(userId, costSnapshot, inputTokens, outputTokens, type);

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void streamGenerateContent(String prompt, String type, com.novel.dto.AIConfigRequest aiConfigRequest, java.util.function.Consumer<String> chunkConsumer) {
        aiConfigRequest = aiModelRoutingService.resolveTaskConfig(aiConfigRequest, type);
        // 获取当前用户ID
        Long userId = AuthUtils.getCurrentUserId();

        // 如果AI配置无效，从系统配置读取
        if (aiConfigRequest == null || !aiConfigRequest.isValid()) {
            logger.info("📡 AI配置无效，从系统配置读取...");
            try {
                String modelId = aiConfigRequest != null ? aiConfigRequest.getModel() : null;
                aiConfigRequest = aiConfigService.getSystemAIConfig(modelId);
                logger.info("✅ 已从系统配置加载AI配置: provider={}, model={}",
                        aiConfigRequest.getProvider(), aiConfigRequest.getModel());
            } catch (Exception e) {
                logger.error("❌ 加载系统AI配置失败: {}", e.getMessage());
                throw new RuntimeException("AI配置无效，请先在后台管理页面配置AI服务: " + e.getMessage());
            }
        }

        String baseUrl = aiConfigRequest.getEffectiveBaseUrl();
        String apiKey = aiConfigRequest.getApiKey();
        String model = aiConfigRequest.getModel();
        CostSnapshot costSnapshot = captureCostSnapshot(model);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key未配置");
        }

        // 计算输入token数
        int inputTokens = prompt != null ? prompt.length() : 0;

        // 检查余额是否大于0
        if (userId != null) {
            if (!creditService.hasEnoughBalance(userId, BigDecimal.ONE)) {
                throw new InsufficientCreditsException("字数点余额不足，请先充值");
            }
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

        // 用于累积输出内容以计算费用
        final StringBuilder outputBuilder = new StringBuilder();
        final Long finalUserId = userId;
        final int finalInputTokens = inputTokens;
        final CostSnapshot finalCostSnapshot = costSnapshot;

        // 发送HTTP请求（流式读取）
        try {
            String url = aiConfigRequest.getApiUrl();

            // 使用RestTemplate进行流式读取
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000); // 连接超时30秒
            requestFactory.setReadTimeout(600000); // 读取超时10分钟（有些模型写得慢）
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
                                if (isSseDataLine(line)) {
                                    String data = stripSseDataPrefix(line);
                                    if ("[DONE]".equals(data)) {
                                        break; // 流式响应结束
                                    }

                                    try {
                                        // 解析JSON数据
                                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                        java.util.Map dataMap = om.readValue(data, java.util.Map.class);
                                        String normalizedChunk = sanitizeStreamingChunk(extractStreamingContentChunk(dataMap));
                                        if (!normalizedChunk.isEmpty()) {
                                            outputBuilder.append(normalizedChunk);
                                            try {
                                                chunkConsumer.accept(normalizedChunk);
                                            } catch (Exception ce) {
                                                if (ce.getMessage() != null && ce.getMessage().contains("already completed")) {
                                                    connectionClosed = true;
                                                    break;
                                                }
                                                throw ce;
                                            }
                                            continue;
                                        }

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
                                                            // 累积输出内容用于计费
                                                            outputBuilder.append(chunk);
                                                            // 回调给消费者 (保留换行符，不要trim)
                                                            try {
                                                                chunkConsumer.accept(chunk);
                                                            } catch (Exception ce) {
                                                                // 连接已断开，停止处理后续数据
                                                                if (ce.getMessage() != null && ce.getMessage().contains("already completed")) {
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
                                            connectionClosed = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        return null;
                    });

            // 计算实际消费并扣费
            int outputTokens = outputBuilder.length();
            chargeCompletedRequest(finalUserId, finalCostSnapshot, finalInputTokens, outputTokens, type);

        } catch (Exception e) {
            logger.error("流式AI调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("流式AI调用失败: " + e.getMessage());
        }
    }

    /**
     * 构建大纲生成提示词
     */
    private String buildOutlinePrompt(Novel novel, String genre, String theme) {
        return managedPromptService.format(SystemPromptDescriptor.builder()
                        .promptKey("writing.outline.basic")
                        .name("基础大纲生成提示词")
                        .category("outline")
                        .description("根据小说基础信息生成简单故事大纲")
                        .variables(List.of("novel_title", "genre", "theme", "description"))
                        .sortOrder(210)
                        .defaultContent("请为小说《%s》生成一个详细的故事大纲。\n" +
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
                        "5. 适合%s类型小说的特点\n")
                        .build(),
                novel.getTitle(), genre, theme, novel.getDescription(), genre
        );
    }

    /**
     * 构建角色生成提示词
     */
    private String buildCharacterPrompt(String characterName, String role, String background) {
        return managedPromptService.format(SystemPromptDescriptor.builder()
                        .promptKey("writing.character.basic")
                        .name("基础角色生成提示词")
                        .category("chapter")
                        .description("根据角色基础信息生成角色档案")
                        .variables(List.of("character_name", "role", "background"))
                        .sortOrder(220)
                        .defaultContent("请为角色【%s】创建详细的角色档案。\n\n" +
                        "角色信息：\n" +
                        "- 姓名：%s\n" +
                        "- 角色：%s\n" +
                        "- 背景：%s\n\n" +
                        "请包含：基本信息、性格特点、背景故事、动机目标、角色关系、角色弧线、对话风格。")
                        .build(),
                characterName, characterName, role, background
        );
    }

    /**
     * 构建章节生成提示词
     */
    private String buildChapterPrompt(Novel novel, String chapterTitle, String outline, String previousContent) {
        return managedPromptService.format(SystemPromptDescriptor.builder()
                        .promptKey("writing.chapter.basic")
                        .name("基础章节生成提示词")
                        .category("chapter")
                        .description("根据章节大纲和前文生成章节内容")
                        .variables(List.of("novel_title", "chapter_title", "outline", "previous_content"))
                        .sortOrder(230)
                        .defaultContent("请为小说《%s》的章节【%s】生成内容。\n\n" +
                        "章节大纲：%s\n" +
                        "前文内容：%s\n\n" +
                        "要求：字数2000-3000字，保持故事节奏，注意对话真实性。")
                        .build(),
                novel.getTitle(), chapterTitle, outline, previousContent
        );
    }

    /**
     * 调用AI服务
     */
    @FunctionalInterface
    private interface AIAttemptExecutor<T> {
        T execute(AIConfigRequest aiConfig) throws Exception;
    }

    private <T> T executeWithFailover(
        AIModelExecutionPlan executionPlan,
        String taskType,
        AIAttemptExecutor<T> executor
    ) {
        RuntimeException lastException = null;

        for (AIModelExecutionAttempt attempt : executionPlan.getAttempts()) {
            long startTime = System.currentTimeMillis();
            try {
                T result = executor.execute(attempt.getConfig());
                aiModelCallLogService.recordSuccess(
                    taskType,
                    executionPlan.getScene(),
                    attempt.getConfig().getModel(),
                    attempt.getAttemptType(),
                    System.currentTimeMillis() - startTime
                );

                if (attempt.getAttemptType() == AIModelExecutionAttemptType.FALLBACK) {
                    logger.warn("AI 主模型失败后已切换备用模型成功: type={}, fallbackModel={}",
                        taskType, attempt.getConfig().getModel());
                }
                return result;
            } catch (Exception e) {
                aiModelCallLogService.recordFailure(
                    taskType,
                    executionPlan.getScene(),
                    attempt.getConfig().getModel(),
                    attempt.getAttemptType(),
                    System.currentTimeMillis() - startTime,
                    e
                );

                lastException = asRuntimeException(e);
                if (attempt.getAttemptType() == AIModelExecutionAttemptType.PRIMARY
                    && executionPlan.hasFallback()
                    && shouldTryFallback(e)) {
                    logger.warn("AI 主模型调用失败，准备切换备用模型: type={}, model={}, error={}",
                        taskType, attempt.getConfig().getModel(), e.getMessage());
                    continue;
                }
                throw lastException;
            }
        }

        throw lastException != null ? lastException : new RuntimeException("AI服务调用失败");
    }

    private boolean shouldTryFallback(Exception e) {
        return !(e instanceof InsufficientCreditsException);
    }

    private RuntimeException asRuntimeException(Exception e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        return new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
    }

    private AIConfigRequest ensureValidAIConfig(AIConfigRequest aiConfig) {
        if (aiConfig != null && aiConfig.isValid()) {
            return aiConfig;
        }

        logger.info("AI 配置无效，从系统配置加载默认配置");
        try {
            String modelId = aiConfig != null ? aiConfig.getModel() : null;
            AIConfigRequest resolvedConfig = aiConfigService.getSystemAIConfig(modelId);
            if (aiConfig != null && aiConfig.getTemperature() != null) {
                resolvedConfig.setTemperature(aiConfig.getTemperature());
            }
            return resolvedConfig;
        } catch (Exception e) {
            logger.error("加载系统 AI 配置失败: {}", e.getMessage());
            throw new RuntimeException("AI配置无效，请先在后台 AI 模型页配置 AI 服务: " + e.getMessage(), e);
        }
    }

    private String generateContentOnce(String prompt, String type, AIConfigRequest aiConfig) {
        aiConfig = ensureValidAIConfig(aiConfig);

        Long userId = AuthUtils.getCurrentUserId();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        CostSnapshot costSnapshot = captureCostSnapshot(model);
        int inputTokens = prompt != null ? prompt.length() : 0;

        if (userId != null && !creditService.hasEnoughBalance(userId, BigDecimal.ONE)) {
            throw new InsufficientCreditsException("瀛楁暟鐐逛綑棰濅笉瓒筹紝璇峰厛鍏呭€?");
        }

        int maxTokens = getMaxTokensForProvider(aiConfig.getProvider(), model);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", false);
        requestBody.putAll(getOptimizedParameters(type));
        if (aiConfig.getTemperature() != null) {
            requestBody.put("temperature", aiConfig.getTemperature());
        }

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);

        try {
            String url = aiConfig.getApiUrl();
            logger.info("AI 非流式调用: type={}, model={}, url={}", type, model, url);

            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000);
            requestFactory.setReadTimeout(300000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)
                (ResponseEntity<?>) restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
                    if (messageObj != null) {
                        String content = (String) messageObj.get("content");
                        int outputTokens = content != null ? content.length() : 0;
                        chargeCompletedRequest(userId, costSnapshot, inputTokens, outputTokens, type);
                        return content;
                    }
                }
            }

            throw new RuntimeException("AI返回内容为空");
        } catch (Exception e) {
            logger.error("AI非流式调用失败: type={}", type, e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    private String generateContentWithMessagesOnce(List<Map<String, String>> messages, String type, AIConfigRequest aiConfig) {
        aiConfig = ensureValidAIConfig(aiConfig);

        Long userId = AuthUtils.getCurrentUserId();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        CostSnapshot costSnapshot = captureCostSnapshot(model);
        int inputTokens = 0;
        for (Map<String, String> msg : messages) {
            String content = msg.get("content");
            if (content != null) {
                inputTokens += content.length();
            }
        }

        if (userId != null && !creditService.hasEnoughBalance(userId, BigDecimal.ONE)) {
            throw new InsufficientCreditsException("瀛楁暟鐐逛綑棰濅笉瓒筹紝璇峰厛鍏呭€?");
        }

        int maxTokens = getMaxTokensForProvider(aiConfig.getProvider(), model);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", false);
        requestBody.putAll(getOptimizedParameters(type));
        if (aiConfig.getTemperature() != null) {
            requestBody.put("temperature", aiConfig.getTemperature());
        }
        requestBody.put("messages", messages);

        try {
            String url = aiConfig.getApiUrl();
            logger.info("AI 非流式消息调用: type={}, model={}, url={}", type, model, url);

            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000);
            requestFactory.setReadTimeout(300000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)
                (ResponseEntity<?>) restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
                    if (messageObj != null) {
                        String content = (String) messageObj.get("content");
                        int outputTokens = content != null ? content.length() : 0;
                        chargeCompletedRequest(userId, costSnapshot, inputTokens, outputTokens, type);
                        return content;
                    }
                }
            }

            throw new RuntimeException("AI返回内容为空");
        } catch (Exception e) {
            logger.error("AI非流式消息调用失败: type={}", type, e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

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
        requestFactory.setConnectTimeout(30000); // 连接超时30秒
        requestFactory.setReadTimeout(600000); // 读取超时10分钟（有些模型写得慢）
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
                params.put("temperature", 0.65);  // 进一步增加创造性
                params.put("top_p", 0.9);
                params.put("frequency_penalty", 0.4);  // 减少重复
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
            case "volume_chapter_outlines_generation":
            case "chapter_outline_from_content":
                params.put("temperature", 0.98);  // 最大创造性，拉满温度
                params.put("top_p", 0.98);
                params.put("frequency_penalty", 0.3);  // 大幅减少重复，避免套路
                params.put("presence_penalty", 0.3);   // 大幅增加多样性和意外性
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
            return 8000; // 默认值改为8000，适配更多场景
        }

        String providerLower = provider.toLowerCase();

        switch (providerLower) {
            case "deepseek":
                // DeepSeek 模型支持较大的 max_tokens
                // 注意：官方限制约8192，但中转API可能支持更大值
                if (model != null && model.contains("reasoner")) {
                    return 24000; // R1 推理模型（增大到32000，用于长文本生成如分卷规划）
                }
                return 24000; // DeepSeek 其他模型（增大到32000）

            case "qwen":
            case "tongyi":
                // 通义千问的限制
                if (model != null && model.contains("max")) {
                    return 16000; // qwen-max 系列
                } else if (model != null && model.contains("longcontext")) {
                    return 12000; // 长上下文模型
                }
                return 8000; // 默认

            case "kimi":
                // Kimi (月之暗面) 的限制
                if (model != null && model.contains("k2")) {
                    return 16000; // K2 系列
                } else if (model != null && model.contains("thinking")) {
                    return 16000; // 长思考模型
                }
                return 8000; // 其他 Kimi 模型

            case "openai":
                // OpenAI 模型
                if (model != null && model.contains("gpt-4")) {
                    return 16000;
                } else if (model != null && model.contains("gpt-3.5")) {
                    return 4000;
                }
                return 8000;

            case "gemini":
            case "google":
                // Google Gemini 系列
                return 16000;

            default:
                // 未知提供商，使用通用默认值（中转API通常支持较大值）
                logger.info("未知的AI提供商: {}, 使用默认 max_tokens=32000", provider);
                return 32000; // 增大到32000，支持长文本生成
        }
    }

    /**
     * 非流式调用AI服务（支持messages列表）
     * @param messages 消息列表
     * @param type 生成类型
     * @param aiConfig AI配置
     * @return 生成的完整内容
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String generateContentWithMessages(List<Map<String, String>> messages, String type, com.novel.dto.AIConfigRequest aiConfig) {
        if (aiModelRoutingService != null) {
            AIModelExecutionPlan executionPlan = aiModelRoutingService.buildExecutionPlan(aiConfig, type);
            return executeWithFailover(
                executionPlan,
                type,
                attemptConfig -> generateContentWithMessagesOnce(messages, type, attemptConfig)
            );
        }
        aiConfig = aiModelRoutingService.resolveTaskConfig(aiConfig, type);
        logger.info("开始生成内容（messages模式），类型: {}, messages数量: {}", type, messages.size());

        // 获取当前用户ID
        Long userId = AuthUtils.getCurrentUserId();

        // 如果AI配置无效，从系统配置读取
        if (aiConfig == null || !aiConfig.isValid()) {
            logger.info("📡 AI配置无效，从系统配置读取...");
            try {
                String modelId = aiConfig != null ? aiConfig.getModel() : null;
                aiConfig = aiConfigService.getSystemAIConfig(modelId);
                logger.info("✅ 已从系统配置加载AI配置: provider={}, model={}",
                        aiConfig.getProvider(), aiConfig.getModel());
            } catch (Exception e) {
                logger.error("❌ 加载系统AI配置失败: {}", e.getMessage());
                throw new RuntimeException("AI配置无效，请先在后台管理页面配置AI服务: " + e.getMessage());
            }
        }

        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        CostSnapshot costSnapshot = captureCostSnapshot(model);

        // 计算输入token数（所有消息内容的总长度）
        int inputTokens = 0;
        for (Map<String, String> msg : messages) {
            String content = msg.get("content");
            if (content != null) {
                inputTokens += content.length();
            }
        }

        // 检查余额是否大于0
        if (userId != null) {
            if (!creditService.hasEnoughBalance(userId, BigDecimal.ONE)) {
                throw new InsufficientCreditsException("字数点余额不足，请先充值");
            }
        }

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

            // 使用带超时配置的 RestTemplate（连接30秒，读取5分钟）
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000);
            requestFactory.setReadTimeout(300000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>)
                    (ResponseEntity<?>) restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
                    if (messageObj != null) {
                        String content = (String) messageObj.get("content");
                        logger.info("✅ AI调用成功，返回内容长度: {} 字符", content != null ? content.length() : 0);

                        // 计算实际消费并扣费
                        int outputTokens = content != null ? content.length() : 0;
                        chargeCompletedRequest(userId, costSnapshot, inputTokens, outputTokens, type);

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void streamGenerateContentWithMessages(
            List<Map<String, String>> messages,
            String type,
            com.novel.dto.AIConfigRequest aiConfig,
            java.util.function.Consumer<String> chunkConsumer) {
        aiConfig = aiModelRoutingService.resolveTaskConfig(aiConfig, type);


        // 获取当前用户ID
        Long userId = AuthUtils.getCurrentUserId();

        // 如果AI配置无效，从系统配置读取
        if (aiConfig == null || !aiConfig.isValid()) {
            logger.info("📡 AI配置无效，从系统配置读取...");
            try {
                String modelId = aiConfig != null ? aiConfig.getModel() : null;
                aiConfig = aiConfigService.getSystemAIConfig(modelId);
                logger.info("✅ 已从系统配置加载AI配置: provider={}, model={}",
                        aiConfig.getProvider(), aiConfig.getModel());
            } catch (Exception e) {
                logger.error("❌ 加载系统AI配置失败: {}", e.getMessage());
                throw new RuntimeException("AI配置无效，请先在后台管理页面配置AI服务: " + e.getMessage());
            }
        }

        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        CostSnapshot costSnapshot = captureCostSnapshot(model);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key未配置");
        }

        // 计算输入token数（所有消息内容的总长度）
        int inputTokens = 0;
        for (Map<String, String> msg : messages) {
            String content = msg.get("content");
            if (content != null) {
                inputTokens += content.length();
            }
        }

        // 检查余额是否大于0
        if (userId != null) {
            if (!creditService.hasEnoughBalance(userId, BigDecimal.ONE)) {
                throw new InsufficientCreditsException("字数点余额不足，请先充值");
            }
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

        // 如果用户指定了温度，覆盖默认值
        if (aiConfig.getTemperature() != null) {
            requestBody.put("temperature", aiConfig.getTemperature());
        }

        requestBody.put("messages", messages);

        // 用于累积输出内容以计算费用
        final StringBuilder outputBuilder = new StringBuilder();
        final Long finalUserId = userId;
        final int finalInputTokens = inputTokens;
        final CostSnapshot finalCostSnapshot = costSnapshot;

        // 发送HTTP请求（流式读取）
        try {
            String url = aiConfig.getApiUrl();

            // 使用RestTemplate进行流式读取
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000); // 连接超时30秒
            requestFactory.setReadTimeout(600000); // 读取超时10分钟（有些模型写得慢）
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
                                if (isSseDataLine(line)) {
                                    String data = stripSseDataPrefix(line);
                                    if ("[DONE]".equals(data)) {
                                        break; // 流式响应结束
                                    }

                                    try {
                                        // 解析JSON数据
                                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                        java.util.Map dataMap = om.readValue(data, java.util.Map.class);
                                        String normalizedChunk = sanitizeStreamingChunk(extractStreamingContentChunk(dataMap));
                                        if (!normalizedChunk.isEmpty()) {
                                            outputBuilder.append(normalizedChunk);
                                            try {
                                                chunkConsumer.accept(normalizedChunk);
                                            } catch (Exception ce) {
                                                if (ce.getMessage() != null && ce.getMessage().contains("already completed")) {
                                                    connectionClosed = true;
                                                    break;
                                                }
                                                throw ce;
                                            }
                                            continue;
                                        }

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
                                                            // 累积输出内容用于计费
                                                            outputBuilder.append(chunk);
                                                            // 调用回调处理chunk (保留换行符，不要trim)
                                                            try {
                                                                chunkConsumer.accept(chunk);
                                                            } catch (Exception ce) {
                                                                // 连接已断开，停止处理后续数据
                                                                if (ce.getMessage() != null && ce.getMessage().contains("already completed")) {
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
                                            connectionClosed = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        return null;
                    }
            );

            // 计算实际消费并扣费
            int outputTokens = outputBuilder.length();
            chargeCompletedRequest(finalUserId, finalCostSnapshot, finalInputTokens, outputTokens, type);

        } catch (Exception e) {
            logger.error("流式AI调用失败（messages模式）: {}", e.getMessage(), e);
            throw new RuntimeException("流式AI调用失败: " + e.getMessage());
        }
    }

    /**
     * 生成小说脑洞简介
     * @param novelTitle 小说名称
     * @param aiConfigRequest AI配置
     * @return 生成的脑洞简介
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String generateSynopsis(String novelTitle, com.novel.dto.AIConfigRequest aiConfigRequest) {
        logger.info("开始生成小说脑洞，书名: {}", novelTitle);

        try {
            // 读取提示词模板
            String promptTemplate = loadPromptTemplate("synopsis_generation_prompt.txt");

            // 构建完整提示词
            String prompt = promptTemplate + "\n\n现在，请根据以下书名生成小说脑洞：\n\n书名：《" + novelTitle + "》\n\n请严格按照格式输出，字数控制在2000字以内。";

            // 调用AI生成
            String synopsis = generateContent(prompt, "synopsis", aiConfigRequest);

            logger.info("✅ 脑洞生成成功，长度: {} 字符", synopsis != null ? synopsis.length() : 0);

            return synopsis;

        } catch (Exception e) {
            logger.error("生成脑洞失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成脑洞失败: " + e.getMessage());
        }
    }

    /**
     * 加载提示词模板
     */
    private String loadPromptTemplate(String fileName) {
        String defaultContent = loadPromptTemplateFromSource(fileName);
        return managedPromptService.getContent(SystemPromptDescriptor.builder()
                .promptKey("writing.resource." + fileName.replace('.', '_'))
                .name("资源提示词：" + fileName)
                .category(fileName.contains("synopsis") ? "synopsis" : "other")
                .description("从资源文件同步的提示词模板")
                .variables(List.of("file_name"))
                .sortOrder(240)
                .defaultContent(defaultContent)
                .build());
    }

    private String loadPromptTemplateFromSource(String fileName) {
        try {
            // 从 prompts 目录加载
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("prompts/" + fileName);
            if (is == null) {
                // 尝试从文件系统加载
                String filePath = "backend/prompts/" + fileName;
                java.io.File file = new java.io.File(filePath);
                if (file.exists()) {
                    return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                }
                throw new RuntimeException("提示词模板文件不存在: " + fileName);
            }

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            logger.error("加载提示词模板失败: {}", fileName, e);
            throw new RuntimeException("加载提示词模板失败: " + e.getMessage());
        }
    }
} 
