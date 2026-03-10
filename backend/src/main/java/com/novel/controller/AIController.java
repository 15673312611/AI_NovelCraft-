package com.novel.controller;

import com.novel.common.Result;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIPolishService;
import com.novel.service.AITraceRemovalService;
import com.novel.service.AIProofreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @Autowired
    public AIController(
            AITraceRemovalService aiTraceRemovalService,
            com.novel.service.AIManuscriptReviewService manuscriptReviewService,
            AIPolishService aiPolishService,
            AIProofreadService aiProofreadService,
            com.novel.service.AISmartSuggestionService smartSuggestionService,
            com.novel.service.AIWritingService aiWritingService,
            com.novel.service.AIConfigService aiConfigService
    ) {
        this.aiTraceRemovalService = aiTraceRemovalService;
        this.manuscriptReviewService = manuscriptReviewService;
        this.aiPolishService = aiPolishService;
        this.aiProofreadService = aiProofreadService;
        this.smartSuggestionService = smartSuggestionService;
        this.aiWritingService = aiWritingService;
        this.aiConfigService = aiConfigService;
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

            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
            } else if (request.get("aiConfig") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }

            if (!aiConfig.isValid()) {
                return Result.error("AI配置无效，请先在设置页面配置AI服务");
            }

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

            // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取；同时兼容嵌套aiConfig）
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
            } else if (request.get("aiConfig") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }

            if (!aiConfig.isValid()) {
                logger.error("❌ AI纠错 - AI配置无效: request={}", request);
                return Result.error("AI配置无效，请先在设置页面配置AI服务");
            }

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
            
            // 从请求中获取模型ID（可选）
            String modelId = (String) request.get("model");
            
            logger.info("🧹 开始AI消痕流式处理，内容长度: {}, 模型ID: {}", content.length(), modelId);
            
            // 从系统配置获取AI配置
            AIConfigRequest aiConfig = aiConfigService.getSystemAIConfig(modelId);
            logger.info("✅ 使用系统配置模型: {} ({})", aiConfig.getModel(), aiConfig.getProvider());
            
            // 异步执行AI消痕
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    aiTraceRemovalService.removeAITraceStream(content, aiConfig, emitter);
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
            });
            
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
            
            // 从请求中获取模型ID（可选）
            String modelId = (String) request.get("model");
            
            logger.info("🔍 开始AI审稿流式处理，稿件长度: {}, 模型ID: {}", content.length(), modelId);
            
            // 从系统配置获取AI配置
            AIConfigRequest aiConfig = aiConfigService.getSystemAIConfig(modelId);
            logger.info("✅ 使用系统配置模型: {} ({})", aiConfig.getModel(), aiConfig.getProvider());
            
            // 异步执行AI审稿
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    manuscriptReviewService.reviewManuscriptStream(content, aiConfig, emitter);
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
            });
            
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
            
            // 从请求中获取模型ID（可选）
            String modelId = (String) request.get("model");
            
            logger.info("🎭 开始AI章节取名，内容长度: {}, 模型ID: {}", content.length(), modelId);
            
            // 调用AI服务生成标题
            java.util.List<String> titles = generateTitlesFromAI(content, modelId);
            
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
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (request.containsKey("provider")) {
                aiConfig.setProvider((String) request.get("provider"));
                aiConfig.setApiKey((String) request.get("apiKey"));
                aiConfig.setModel((String) request.get("model"));
                aiConfig.setBaseUrl((String) request.get("baseUrl"));
                
                logger.info("✅ AI智能建议 - 收到AI配置: provider={}, model={}", 
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (request.get("aiConfig") instanceof Map) {
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
}
