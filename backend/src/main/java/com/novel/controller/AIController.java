package com.novel.controller;

import com.novel.common.Result;
import com.novel.common.security.SecurityContextTaskWrapper;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIPolishService;
import com.novel.service.AITraceRemovalService;
import com.novel.service.AIProofreadService;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI工具Controller
 * 提供AI相关的工具功能，如AI消痕等
 */
@RestController
@RequestMapping("/ai")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AIController {

    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    private final AITraceRemovalService aiTraceRemovalService;
    private final com.novel.service.AIManuscriptReviewService manuscriptReviewService;
    private final AIPolishService aiPolishService;
    private final AIProofreadService aiProofreadService;
    private final com.novel.service.AISmartSuggestionService smartSuggestionService;
    private final com.novel.service.AIWritingService aiWritingService;
    private final com.novel.service.AIConfigService aiConfigService;
    private final ManagedPromptService managedPromptService;

    @Autowired
    public AIController(
            AITraceRemovalService aiTraceRemovalService,
            com.novel.service.AIManuscriptReviewService manuscriptReviewService,
            AIPolishService aiPolishService,
            AIProofreadService aiProofreadService,
            com.novel.service.AISmartSuggestionService smartSuggestionService,
            com.novel.service.AIWritingService aiWritingService,
            com.novel.service.AIConfigService aiConfigService,
            ManagedPromptService managedPromptService
    ) {
        this.aiTraceRemovalService = aiTraceRemovalService;
        this.manuscriptReviewService = manuscriptReviewService;
        this.aiPolishService = aiPolishService;
        this.aiProofreadService = aiProofreadService;
        this.smartSuggestionService = smartSuggestionService;
        this.aiWritingService = aiWritingService;
        this.aiConfigService = aiConfigService;
        this.managedPromptService = managedPromptService;
    }

    @PostMapping("/polish-selection")
    public Result<Map<String, Object>> polishSelection(@RequestBody Map<String, Object> request) {
        try {
            String fullContent = (String) request.getOrDefault("chapterContent", request.get("content"));
            String selection = (String) request.getOrDefault("selection", request.get("selectedText"));
            String instructions = (String) request.getOrDefault("instructions", request.get("requirement"));
            String chapterTitle = (String) request.getOrDefault("chapterTitle", request.get("title"));

            if (selection == null || selection.trim().isEmpty()) {
                return Result.error("待润色片段不能为空");
            }

            AIConfigRequest aiConfig = resolveAIConfig(request);

            String polished = aiPolishService.polishSelection(
                    chapterTitle,
                    fullContent,
                    selection,
                    instructions,
                    aiConfig
            );

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("polishedContent", polished);
            return Result.success(data);

        } catch (Exception e) {
            logger.error("AI润色片段失败", e);
            return Result.error("AI润色失败: " + e.getMessage());
        }
    }

    /**
     * AI智能纠错接口
     * 前端通过 /ai/proofread 调用，返回检测到的错误列表
     */
    @PostMapping("/proofread")
    public Result<Map<String, Object>> proofread(@RequestBody Map<String, Object> request) {
        try {
            String content = (String) request.get("content");

            if (content == null || content.trim().isEmpty()) {
                return Result.error("内容不能为空");
            }

            // 角色名称列表（可选）
            java.util.List<String> characterNames = new java.util.ArrayList<>();
            Object namesObj = request.get("characterNames");
            if (namesObj instanceof java.util.List) {
                for (Object o : (java.util.List<?>) namesObj) {
                    if (o != null) {
                        characterNames.add(o.toString());
                    }
                }
            }

            AIConfigRequest aiConfig = resolveAIConfig(request);

            java.util.List<AIProofreadService.ProofreadError> errors =
                    aiProofreadService.proofread(content, characterNames, aiConfig);

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("errors", errors);
            data.put("errorCount", errors != null ? errors.size() : 0);
            return Result.success(data);

        } catch (Exception e) {
            logger.error("AI纠错失败", e);
            return Result.error("AI纠错失败: " + e.getMessage());
        }
    }

    /**
     * AI消痕接口（流式）
     * 将AI生成的内容进行去AI味处理，使用SSE流式输出
     * 使用系统配置的AI模型
     */
    @PostMapping(value = "/remove-trace-stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter removeAITraceStream(@RequestBody Map<String, Object> request) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300000L);
        
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("内容不能为空"));
                emitter.completeWithError(new Exception("内容不能为空"));
                return emitter;
            }
            
            String modelId = resolveRequestedModelId(request);
            
            
            AIConfigRequest aiConfig = copyConfig(aiConfigService.getSystemAIConfig(modelId));
            aiConfig.setTemperature(0.8D);
            
            // 异步执行AI消痕
            java.util.concurrent.CompletableFuture.runAsync(SecurityContextTaskWrapper.wrap(() -> {
                try {
                    aiWritingService.streamGenerateContentWithMessages(
                            buildTraceRemovalMessages(content),
                            "ai_trace_removal",
                            aiConfig,
                            chunk -> {
                                try {
                                    Map<String, String> eventData = new HashMap<>();
                                    eventData.put("content", chunk);
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(eventData));
                                } catch (Exception sendEx) {
                                    throw new RuntimeException(sendEx);
                                }
                            }
                    );
                    emitter.complete();
                } catch (Exception e) {
                    logger.error("AI消痕流式处理失败", e);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error").data("处理失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("发送错误事件失败", ex);
                    }
                }
            }));
            
        } catch (Exception e) {
            logger.error("AI消痕初始化失败", e);
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("初始化失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("发送错误事件失败", ex);
            }
        }
        
        return emitter;
    }

    @PostMapping("/generate-novel-creation-field")
    public Result<Map<String, Object>> generateNovelCreationField(@RequestBody Map<String, Object> request) {
        try {
            String field = normalizeCreationField(request.get("field"));
            if (field == null) {
                return Result.error("生成字段无效，仅支持 title 或 description");
            }

            String keyword = safeText(request.get("keyword"));
            String currentTitle = safeText(request.get("currentTitle"));
            String currentDescription = safeText(request.get("currentDescription"));
            String modelId = resolveRequestedModelId(request);

            logger.info("🎲 开始生成新建小说字段, field={}, keywordLength={}", field, keyword.length());

            String generatedValue = "title".equals(field)
                    ? generateNovelTitleFromManagedPrompt(currentTitle, currentDescription, modelId, keyword)
                    : generateNovelIdeaFromManagedPrompt(currentTitle, currentDescription, modelId, keyword);

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("field", field);
            data.put("value", generatedValue);
            data.put("keyword", keyword);
            return Result.success(data);
        } catch (Exception e) {
            logger.error("❌ 新建小说字段生成失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * AI审稿接口（流式）
     * 对稿件内容进行专业审稿，提供修改建议，使用SSE流式输出
     * 使用系统配置的AI模型，不再依赖前端AI配置
     */
    @PostMapping(value = "/review-manuscript-stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter reviewManuscriptStream(@RequestBody Map<String, Object> request) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300000L);
        
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("稿件内容不能为空"));
                emitter.completeWithError(new Exception("稿件内容不能为空"));
                return emitter;
            }
            
            String modelId = resolveRequestedModelId(request);
            
            
            AIConfigRequest aiConfig = copyConfig(aiConfigService.getSystemAIConfig(modelId));
            aiConfig.setTemperature(0.7D);
            
            // 异步执行AI审稿
            java.util.concurrent.CompletableFuture.runAsync(SecurityContextTaskWrapper.wrap(() -> {
                try {
                    aiWritingService.streamGenerateContentWithMessages(
                            buildManuscriptReviewMessages(content),
                            "ai_review",
                            aiConfig,
                            chunk -> {
                                try {
                                    Map<String, String> eventData = new HashMap<>();
                                    eventData.put("content", chunk);
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .name("message")
                                            .data(eventData));
                                } catch (Exception sendEx) {
                                    throw new RuntimeException(sendEx);
                                }
                            }
                    );
                    emitter.complete();
                } catch (Exception e) {
                    logger.error("AI审稿流式处理失败", e);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error").data("审稿失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("发送错误事件失败", ex);
                    }
                }
            }));
            
        } catch (Exception e) {
            logger.error("AI审稿初始化失败", e);
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("初始化失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("发送错误事件失败", ex);
            }
        }
        
        return emitter;
    }
    
    /**
     * AI章节取名接口
     * 根据章节内容生成5个标题建议
     */
    @PostMapping("/generate-chapter-titles")
    public Result<Map<String, Object>> generateChapterTitles(@RequestBody Map<String, Object> request) {
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                return Result.error("章节内容不能为空");
            }
            
            if (content.trim().length() < 100) {
                return Result.error("章节内容太少，无法生成合适的标题");
            }
            
            String modelId = resolveRequestedModelId(request);
            
            logger.info("🎭 开始AI章节取名，内容长度: {}, 模型ID: {}", content.length(), modelId);
            
            // 调用AI服务生成标题
            java.util.List<String> titles = generateTitlesFromManagedPrompt(content, modelId);
            
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("titles", titles);
            data.put("count", titles.size());
            
            logger.info("✅ AI章节取名完成，共 {} 个标题", titles.size());
            
            return Result.success(data);
            
        } catch (Exception e) {
            logger.error("❌ AI章节取名失败", e);
            return Result.error("AI章节取名失败: " + e.getMessage());
        }
    }
    
    /**
     * 调用AI服务生成章节标题
     */
    private java.util.List<String> generateTitlesFromAI(String content, String modelId) throws Exception {
        // 从系统配置获取AI配置
        com.novel.dto.AIConfigRequest aiConfig = aiConfigService.getSystemAIConfig(modelId);
        logger.info("✅ 使用系统配置模型: {} ({})", aiConfig.getModel(), aiConfig.getProvider());
        
        // 截取章节开头用于分析
        String contentPreview = content.length() > 1500 ? content.substring(0, 1500) : content;
        
        String prompt = "# 任务\\n\\n" +
                "请为下面这一章节内容创作 5 个高质量的中文标题。\\n\\n" +
                "# 输入章节内容\\n\\n" +
                contentPreview +
                (content.length() > 1500 ? "\\n...(内容较长，已截取前1500字)" : "") +
                "\\n\\n# 核心要求\\n\\n" +
                "1. 标题要大致概括本章的核心情节或情绪，并具有一定吸引力。\\n" +
                "2. 风格不限，可以自由发挥，只需符合通俗小说的阅读习惯。\\n" +
                "3. 字数一般控制在5-15个字左右即可，不必死板卡字数。\\n\\n" +
                "# 输出格式\\n\\n" +
                "只输出 5 行标题，每行一个标题。\\n" +
                "不要编号，不要解释，不要附加其他任何文字或符号。";
        
        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
        java.util.Map<String, String> systemMsg = new java.util.HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个擅长为中文网络小说章节取名的助手，请根据用户提供的章节内容，给出5个有吸引力且贴合剧情的章节标题。");
        messages.add(systemMsg);
        
        java.util.Map<String, String> userMsg = new java.util.HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);
        
        // 调用AI写作服务
        String response = this.aiWritingService.generateContentWithMessages(
            messages, 
            "chapter_title_generation",
            aiConfig
        );
        
        // 解析返回的标题
        java.util.List<String> titles = new java.util.ArrayList<>();
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                // 过滤空行和编号
                if (!trimmed.isEmpty() && !trimmed.matches("^[\\d\\.\u3001]+.*")) {
                    // 移除可能的前缀编号
                    trimmed = trimmed.replaceAll("^[\\d\\.\u3001\\uff08\\uff09\\(\\)]+\\s*", "");
                    if (!trimmed.isEmpty() && titles.size() < 5) {
                        titles.add(trimmed);
                    }
                }
            }
        }
        
        if (titles.isEmpty()) {
            throw new Exception("未能生成有效的章节标题");
        }
        
        return titles;
    }

    private String generateNovelTitleFromManagedPrompt(String currentTitle,
                                                       String currentDescription,
                                                       String modelId,
                                                       String keyword) throws Exception {
        AIConfigRequest aiConfig = aiConfigService.getSystemAIConfig(modelId);

        String prompt = managedPromptService.render(
                SystemPromptDescriptor.builder()
                        .promptKey("outline.novel_creation.title")
                        .name("新建小说随机书名提示词")
                        .category("outline")
                        .description("在新建小说页随机生成书名，可结合关键词和已有构思")
                        .variables(java.util.List.of("keyword_block", "current_title_block", "current_idea_block"))
                        .sortOrder(320)
                        .defaultContent("""
                                你是一名擅长中文网络小说命名的策划编辑。
                                请根据给定信息，生成 1 个可以直接拿来创建作品的中文书名。

                                {{keyword_block}}{{current_title_block}}{{current_idea_block}}
                                要求：
                                1. 书名要有明确题材感、传播感或冲突感，读者一眼能感知风格方向。
                                2. 优先体现身份反差、核心设定、目标欲望、情绪张力、金手指、命运转折中的至少一项。
                                3. 不要太空泛，不要过度老套，不要出现“传奇”“风云”这类万能大词堆砌。
                                4. 书名长度尽量控制在 4-16 个字，可自然浮动。
                                5. 如果给了已有标题，只把它当参考，不要原样照搬。

                                输出要求：
                                - 只输出 1 行书名正文
                                - 不要编号，不要加书名号，不要加引号，不要解释
                                """)
                        .build(),
                java.util.Map.of(
                        "keyword_block", buildKeywordBlock(keyword),
                        "current_title_block", buildCurrentTitleBlock(currentTitle),
                        "current_idea_block", buildCurrentIdeaBlock(currentDescription)
                )
        );

        return generateSingleText(prompt, "novel_creation_title", aiConfig);
    }

    private String generateNovelIdeaFromManagedPrompt(String currentTitle,
                                                      String currentDescription,
                                                      String modelId,
                                                      String keyword) throws Exception {
        AIConfigRequest aiConfig = aiConfigService.getSystemAIConfig(modelId);

        String prompt = managedPromptService.render(
                SystemPromptDescriptor.builder()
                        .promptKey("outline.novel_creation.idea")
                        .name("新建小说随机构思提示词")
                        .category("outline")
                        .description("在新建小说页随机生成作品构思，可结合关键词和已有标题")
                        .variables(java.util.List.of("keyword_block", "current_title_block", "current_idea_block"))
                        .sortOrder(321)
                        .defaultContent("""
                                你是一名网络小说选题策划编辑。
                                请根据给定信息，生成 1 段适合直接填写到“作品构思 / 核心构思”里的中文内容。

                                {{keyword_block}}{{current_title_block}}{{current_idea_block}}
                                要求：
                                1. 输出 1 段连贯正文，长度控制在 90-180 字之间。
                                2. 内容里尽量自然包含：故事类型、主角身份或处境、核心冲突或目标、最吸引读者的钩子。
                                3. 如果给了关键词，要真正融入题材、设定、气质或冲突，不要只生硬拼接。
                                4. 如果给了已有标题，就围绕标题延展；如果给了已有构思，就参考方向但不要简单重写原句。
                                5. 不要分点，不要编号，不要写成大纲，不要出现“本书讲述了”这种生硬介绍腔。

                                输出要求：
                                - 只输出 1 段作品构思正文
                                - 不要附加标题、解释、编号或其他说明
                                """)
                        .build(),
                java.util.Map.of(
                        "keyword_block", buildKeywordBlock(keyword),
                        "current_title_block", buildCurrentTitleBlock(currentTitle),
                        "current_idea_block", buildCurrentIdeaBlock(currentDescription)
                )
        );

        return generateSingleText(prompt, "novel_creation_idea", aiConfig);
    }

    private String generateSingleText(String prompt, String type, AIConfigRequest aiConfig) throws Exception {
        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
        java.util.Map<String, String> userMsg = new java.util.HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        String response = this.aiWritingService.generateContentWithMessages(messages, type, aiConfig);
        String normalized = normalizeGeneratedText(response);
        if (normalized.isEmpty()) {
            throw new Exception("AI未能返回有效内容");
        }
        return normalized;
    }

    private String normalizeGeneratedText(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().replace("\r", "");
        normalized = normalized.replaceAll("^```[a-zA-Z]*\\n?", "");
        normalized = normalized.replaceAll("\\n?```$", "");

        String[] lines = normalized.split("\\n");
        java.util.List<String> keptLines = new java.util.ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                keptLines.add(trimmed.replaceAll("^[\\d\\-\\*\\.、]+\\s*", ""));
            }
        }

        normalized = String.join("\n", keptLines).trim();
        normalized = normalized.replaceAll("^[\"“”'《》]+", "");
        normalized = normalized.replaceAll("[\"“”'《》]+$", "");
        return normalized.trim();
    }

    private String buildKeywordBlock(String keyword) {
        String normalized = safeText(keyword);
        if (normalized.isEmpty()) {
            return "";
        }
        return "关键词偏好：" + normalized + "\n\n";
    }

    private String buildCurrentTitleBlock(String currentTitle) {
        String normalized = safeText(currentTitle);
        if (normalized.isEmpty()) {
            return "";
        }
        return "当前已有标题参考：" + normalized + "。如果它不够好，可以完全另起。\n\n";
    }

    private String buildCurrentIdeaBlock(String currentDescription) {
        String normalized = safeText(currentDescription);
        if (normalized.isEmpty()) {
            return "";
        }
        return "当前已有构思参考：\n" + truncateText(normalized, 400) + "\n\n";
    }

    private String truncateText(String text, int maxLength) {
        String normalized = safeText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "…";
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeCreationField(Object value) {
        String field = safeText(value);
        if ("title".equals(field) || "description".equals(field)) {
            return field;
        }
        return null;
    }

    private java.util.List<String> generateTitlesFromManagedPrompt(String content, String modelId) throws Exception {
        com.novel.dto.AIConfigRequest aiConfig = aiConfigService.getSystemAIConfig(modelId);
        logger.info("✅ 使用系统配置模型: {} ({})", aiConfig.getModel(), aiConfig.getProvider());

        String contentPreview = content.length() > 1500 ? content.substring(0, 1500) : content;
        String contentNotice = content.length() > 1500 ? "\n\n（内容较长，以上仅截取前 1500 字供取名参考）" : "";

        String prompt = managedPromptService.render(
                SystemPromptDescriptor.builder()
                        .promptKey("editing.chapter_title.generation")
                        .name("章节取名提示词")
                        .category("system_chain")
                        .description("根据章节正文生成 5 个章节备选标题")
                        .variables(java.util.List.of("content_preview", "content_notice"))
                        .sortOrder(810)
                        .defaultContent("""
                                # 任务
                                请根据下面这章内容，生成 5 个中文网络小说章节标题候选。

                                # 章节内容
                                {{content_preview}}{{content_notice}}

                                # 要求
                                1. 标题必须紧扣本章已经发生的核心冲突、转折、情绪、钩子或人物关系变化。
                                2. 风格要贴合中文网络小说，具备吸引力，但不要空泛，不要只换近义词。
                                3. 标题长度以 5-15 个字为宜，可以略有浮动。
                                4. 不要剧透整本书后续内容，只聚焦当前章节。
                                5. 优先体现悬念、反转、情绪张力、压迫感、爽点或人物关系推进中的一个或多个。

                                # 输出格式
                                只输出 5 行标题，每行一个。
                                不要编号，不要解释，不要附加任何其他文字或符号。
                                """)
                        .build(),
                java.util.Map.of(
                        "content_preview", contentPreview,
                        "content_notice", contentNotice
                )
        );

        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
        java.util.Map<String, String> userMsg = new java.util.HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        String response = this.aiWritingService.generateContentWithMessages(
                messages,
                "chapter_title_generation",
                aiConfig
        );

        java.util.List<String> titles = new java.util.ArrayList<>();
        if (response != null && !response.trim().isEmpty()) {
            String[] lines = response.split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.matches("^[\\d\\.\u3001]+.*")) {
                    trimmed = trimmed.replaceAll("^[\\d\\.\u3001\\uff08\\uff09\\(\\)]+\\s*", "");
                    if (!trimmed.isEmpty() && titles.size() < 5) {
                        titles.add(trimmed);
                    }
                }
            }
        }

        if (titles.isEmpty()) {
            throw new Exception("未能生成有效的章节标题");
        }

        return titles;
    }

    /**
     * AI智能建议接口
     * 对小说内容进行全面诊断，提供改进建议
     */
    @PostMapping("/smart-suggestions")
    public Result<Map<String, Object>> getSmartSuggestions(@RequestBody Map<String, Object> request) {
        try {
            String content = (String) request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                return Result.error("内容不能为空");
            }
            
            // 解析AI配置
            AIConfigRequest aiConfig = resolveAIConfig(request);
            if (false && request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
                
                logger.info("✅ AI智能建议 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (false && request.get("aiConfig") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
                
                logger.info("✅ AI智能建议 - 收到AI配置(嵌套): provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            }
            
            if (!aiConfig.isValid()) {
                return Result.error("AI配置无效，请先在设置页面配置AI服务");
            }
            
            logger.info("🔍 开始AI智能建议诊断，内容长度: {}", content.length());
            
            // 调用智能建议服务
            java.util.List<com.novel.service.AISmartSuggestionService.SmartSuggestion> suggestions = 
                smartSuggestionService.analyzeSuggestions(content, aiConfig);
            
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("suggestions", suggestions);
            data.put("total", suggestions.size());
            
            logger.info("✅ AI智能建议完成，共 {} 条建议", suggestions.size());
            
            return Result.success(data);
            
        } catch (Exception e) {
            logger.error("❌ AI智能建议失败", e);
            return Result.error("AI智能建议失败: " + e.getMessage());
        }
    }

    private String resolveRequestedModelId(Map<String, Object> request) {
        String preferredModel = safeText(request.get("preferredModel"));
        if (!preferredModel.isEmpty()) {
            return preferredModel;
        }

        String model = safeText(request.get("model"));
        if (!model.isEmpty()) {
            return model;
        }

        if (request.get("aiConfig") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> aiConfigMap = (Map<String, Object>) request.get("aiConfig");
            String nestedPreferredModel = safeText(aiConfigMap.get("preferredModel"));
            if (!nestedPreferredModel.isEmpty()) {
                return nestedPreferredModel;
            }
            return safeText(aiConfigMap.get("model"));
        }

        return "";
    }

    private AIConfigRequest resolveAIConfig(Map<String, Object> request) {
        AIConfigRequest directConfig = extractDirectAIConfig(request);
        if (directConfig.isValid()) {
            return directConfig;
        }
        return aiConfigService.getSystemAIConfig(resolveRequestedModelId(request));
    }

    private AIConfigRequest extractDirectAIConfig(Map<String, Object> request) {
        AIConfigRequest aiConfig = new AIConfigRequest();
        if (request.containsKey("provider")) {
            aiConfig.setProvider(safeText(request.get("provider")));
            aiConfig.setApiKey(safeText(request.get("apiKey")));
            aiConfig.setModel(resolveRequestedModelId(request));
            aiConfig.setBaseUrl(safeText(request.get("baseUrl")));
            return aiConfig;
        }

        if (request.get("aiConfig") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> aiConfigMap = (Map<String, Object>) request.get("aiConfig");
            aiConfig.setProvider(safeText(aiConfigMap.get("provider")));
            aiConfig.setApiKey(safeText(aiConfigMap.get("apiKey")));
            String model = safeText(aiConfigMap.get("preferredModel"));
            if (model.isEmpty()) {
                model = safeText(aiConfigMap.get("model"));
            }
            aiConfig.setModel(model);
            aiConfig.setBaseUrl(safeText(aiConfigMap.get("baseUrl")));
        }
        return aiConfig;
    }

    private AIConfigRequest copyConfig(AIConfigRequest source) {
        AIConfigRequest target = new AIConfigRequest();
        if (source == null) {
            return target;
        }
        target.setProvider(source.getProvider());
        target.setApiKey(source.getApiKey());
        target.setModel(source.getModel());
        target.setBaseUrl(source.getBaseUrl());
        target.setTemperature(source.getTemperature());
        return target;
    }

/*
    private List<Map<String, String>> buildTraceRemovalMessages(String content) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", managedPromptService.getContent(SystemPromptDescriptor.builder()
                .promptKey("editing.trace_removal.system")
                .name("AI消痕系统提示词")
                .category("system_chain")
                .description("对文本进行深度去AI味改写")
                .variables(List.of("content"))
                .sortOrder(812)
                .defaultContent("")
                .build()));
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);
        messages.add(userMessage);
        return messages;
    }

    private List<Map<String, String>> buildManuscriptReviewMessages(String content) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", managedPromptService.getContent(SystemPromptDescriptor.builder()
                .promptKey("editing.manuscript_review.system")
                .name("AI审稿系统提示词")
                .category("system_chain")
                .description("对稿件进行结构化审稿分析")
                .variables(List.of("content"))
                .sortOrder(811)
                .defaultContent("")
                .build()));
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "请审稿以下稿件：\n\n" + content);
        messages.add(userMessage);
        return messages;
    }
*/

    private List<Map<String, String>> buildTraceRemovalMessages(String content) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", managedPromptService.getContent(SystemPromptDescriptor.builder()
                .promptKey("editing.trace_removal.system")
                .name("AI trace removal prompt")
                .category("system_chain")
                .description("Trace removal system prompt")
                .variables(List.of("content"))
                .sortOrder(812)
                .defaultContent("")
                .build()));
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);
        messages.add(userMessage);
        return messages;
    }

    private List<Map<String, String>> buildManuscriptReviewMessages(String content) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", managedPromptService.getContent(SystemPromptDescriptor.builder()
                .promptKey("editing.manuscript_review.system")
                .name("AI manuscript review prompt")
                .category("system_chain")
                .description("Structured manuscript review prompt")
                .variables(List.of("content"))
                .sortOrder(811)
                .defaultContent("")
                .build()));
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "请审稿以下稿件：\n\n" + content);
        messages.add(userMessage);
        return messages;
    }
}
