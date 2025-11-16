package com.novel.controller;

import com.novel.common.ApiResponse;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import com.novel.service.NovelService;
import com.novel.service.ChapterService;
import com.novel.service.ContextManagementService;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.Chapter;
import com.novel.agentic.service.PromptAssembler;
import com.novel.agentic.service.StructuredMessageBuilder;
import com.novel.agentic.model.WritingContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 章节重写控制器
 *
 * @deprecated 此接口已弃用，请直接使用 /api/agentic/generate-chapters-stream 代替
 *             agentic 接口会自动检测章节是否存在，如果存在则自动清理旧数据并重写
 */
@Deprecated
@Slf4j
@RestController
@RequestMapping("/novels/{novelId}/rewrite")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class ChapterRewriteController {

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private NovelService novelService;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private ContextManagementService contextManagementService;

    @Autowired
    private PromptAssembler promptAssembler;

    @Autowired
    private StructuredMessageBuilder messageBuilder;

    @Data
    public static class RewriteRequest {
        private String content;          // 原文
        private String requirements;     // 用户要求（可选）
        private Boolean concise;         // 精炼模式（可选）
        private Integer chapterNumber;   // 章节号（用于获取上下文）
    }

    @Data
    public static class RewriteResponse {
        private String rewrittenContent;
    }

    /**
     * 章节重写接口（流式）
     * 使用SSE流式输出重写结果
     */
    @PostMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter rewriteStream(
            @PathVariable("novelId") Long novelId,
            @RequestBody Map<String, Object> requestMap
    ) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
            new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300000L);

        try {
            // 提取基本请求参数
            String content = (String) requestMap.get("content");
            String requirements = (String) requestMap.get("requirements");
            Boolean concise = (Boolean) requestMap.get("concise");

            if (content == null || content.trim().isEmpty()) {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("正文不能为空"));
                emitter.completeWithError(new Exception("正文不能为空"));
                return emitter;
            }

            // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
            AIConfigRequest aiConfig = new AIConfigRequest();
            if (requestMap.containsKey("provider")) {
                aiConfig.setProvider((String) requestMap.get("provider"));
                aiConfig.setApiKey((String) requestMap.get("apiKey"));
                aiConfig.setModel((String) requestMap.get("model"));
                aiConfig.setBaseUrl((String) requestMap.get("baseUrl"));

                log.info("✅ 章节重写流式 - 收到AI配置: provider={}, model={}",
                    aiConfig.getProvider(), aiConfig.getModel());
            } else if (requestMap.get("aiConfig") instanceof Map) {
                // 兼容旧的嵌套格式
                @SuppressWarnings("unchecked")
                Map<String, String> aiConfigMap = (Map<String, String>) requestMap.get("aiConfig");
                aiConfig.setProvider(aiConfigMap.get("provider"));
                aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                aiConfig.setModel(aiConfigMap.get("model"));
                aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
            }

            if (!aiConfig.isValid()) {
                log.error("❌ 章节重写流式 - AI配置无效: requestMap={}", requestMap);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("AI配置无效，请先在设置页面配置AI服务"));
                emitter.completeWithError(new Exception("AI配置无效"));
                return emitter;
            }

            // 获取章节号（用于构建上下文）
            Integer chapterNumber = null;
            if (requestMap.containsKey("chapterNumber")) {
                Object chapterNumObj = requestMap.get("chapterNumber");
                if (chapterNumObj instanceof Integer) {
                    chapterNumber = (Integer) chapterNumObj;
                } else if (chapterNumObj instanceof String) {
                    try {
                        chapterNumber = Integer.parseInt((String) chapterNumObj);
                    } catch (NumberFormatException e) {
                        log.warn("无法解析章节号: {}", chapterNumObj);
                    }
                }
            }

            boolean isConcise = Boolean.TRUE.equals(concise);

            // 构建带上下文的prompt
            String prompt;
            if (chapterNumber != null && chapterNumber > 0) {
                // 使用上下文增强的prompt
                prompt = isConcise
                        ? buildConcisePromptWithContext(novelId, chapterNumber, content)
                        : buildRewritePromptWithContext(novelId, chapterNumber, content, requirements);
                log.info("🔄 开始章节重写流式处理（带上下文），章节号: {}, 内容长度: {}, 使用模型: {}, 精炼模式: {}",
                    chapterNumber, content.length(), aiConfig.getModel(), isConcise);
            } else {
                // 降级为简单prompt
                prompt = isConcise
                        ? buildConcisePrompt(content)
                        : buildRewritePrompt(content, requirements);
                log.info("🔄 开始章节重写流式处理（无上下文），内容长度: {}, 使用模型: {}, 精炼模式: {}",
                    content.length(), aiConfig.getModel(), isConcise);
            }

            // 异步执行流式重写
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    aiWritingService.streamGenerateContent(
                        prompt,
                        isConcise ? "chapter_concise" : "chapter_rewrite",
                        aiConfig,
                        chunk -> {
                            try {
                                // 发送JSON格式数据，包裹在content字段中（与其他流式接口保持一致）
                                java.util.Map<String, String> eventData = new java.util.HashMap<>();
                                eventData.put("content", chunk);
                                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                    .data(eventData));
                            } catch (Exception e) {
                                log.error("发送流式数据失败", e);
                            }
                        }
                    );
                    // 流式处理完成
                    emitter.complete();
                    log.info("✅ 章节重写流式处理完成");
                } catch (Exception e) {
                    log.error("章节重写流式处理失败", e);
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error").data("重写失败: " + e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.error("发送错误事件失败", ex);
                    }
                }
            });

        } catch (Exception e) {
            log.error("章节重写初始化失败", e);
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("初始化失败: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("发送错误事件失败", ex);
            }
        }

        return emitter;
    }

    /**
     * 章节重写接口（非流式，保留作为备用）
     */
    @PostMapping
    public ApiResponse<RewriteResponse> rewrite(
            @PathVariable("novelId") Long novelId,
            @RequestBody Map<String, Object> requestMap
    ) {
        // 提取基本请求参数
        String content = (String) requestMap.get("content");
        String requirements = (String) requestMap.get("requirements");
        Boolean concise = (Boolean) requestMap.get("concise");

        if (content == null || content.trim().isEmpty()) {
            return ApiResponse.error("正文不能为空");
        }

        // 解析AI配置（前端withAIConfig是扁平化的，直接从根级别读取）
        AIConfigRequest aiConfig = new AIConfigRequest();
        if (requestMap.containsKey("provider")) {
            aiConfig.setProvider((String) requestMap.get("provider"));
            aiConfig.setApiKey((String) requestMap.get("apiKey"));
            aiConfig.setModel((String) requestMap.get("model"));
            aiConfig.setBaseUrl((String) requestMap.get("baseUrl"));

            log.info("✅ 章节重写 - 收到AI配置: provider={}, model={}",
                aiConfig.getProvider(), aiConfig.getModel());
        } else if (requestMap.get("aiConfig") instanceof Map) {
            // 兼容旧的嵌套格式
            @SuppressWarnings("unchecked")
            Map<String, String> aiConfigMap = (Map<String, String>) requestMap.get("aiConfig");
            aiConfig.setProvider(aiConfigMap.get("provider"));
            aiConfig.setApiKey(aiConfigMap.get("apiKey"));
            aiConfig.setModel(aiConfigMap.get("model"));
            aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
        }

        if (!aiConfig.isValid()) {
            log.error("❌ 章节重写 - AI配置无效: requestMap={}", requestMap);
            return ApiResponse.error("AI配置无效，请先在设置页面配置AI服务");
        }

        try {
            boolean isConcise = Boolean.TRUE.equals(concise);
            String prompt = isConcise
                    ? buildConcisePrompt(content)
                    : buildRewritePrompt(content, requirements);

            String output = aiWritingService.generateContent(
                prompt,
                isConcise ? "chapter_concise" : "chapter_rewrite",
                aiConfig
            );

            RewriteResponse resp = new RewriteResponse();
            resp.setRewrittenContent(output != null ? output.trim() : "");
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("章节重写失败", e);
            return ApiResponse.error("重写失败: " + e.getMessage());
        }
    }

    private String buildRewritePrompt(String content, String userReq) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深网络小说编辑，请在严格保持世界设定、人物名称与关系、事件因果不变的前提下，对下文进行高质量重写。\\n");
        sb.append("目标：节奏更快、信息更密、可读性更强，但整体长度保持在±10%范围内。\\n");
        sb.append("硬性约束：\\n");
        sb.append("- 严禁改动任何专有名词（人名、称呼、组织、地名、术语）。\\n");
        sb.append("- 不改变已发生的情节事实和事件因果，只优化叙述与表达。\\n");
        sb.append("- 语气、叙述视角与人设一致。\\n");
        if (userReq != null && !userReq.trim().isEmpty()) {
            sb.append("用户额外要求：").append(userReq.trim()).append("\\n");
        }
        sb.append("输出：只输出重写后的正文，不要任何解释。\\n\\n");
        sb.append("【待重写正文】\\n");
        sb.append(content);
        return sb.toString();
    }

    private String buildConcisePrompt(String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对下文进行精炼重写，以“加快节奏、去除冗余”为核心：\\n");
        sb.append("- 去掉无意义的铺陈、重复与赘词；保留承载关键信息的细节。\\n");
        sb.append("- 对话尽量保真，仅去除啰嗦语；叙述句收紧。\\n");
        sb.append("- 整体字数减少约10%~25%，勿过度删减。\\n");
        sb.append("- 严禁改动专有名词（人名、称呼、组织、地名、术语）。\\n");
        sb.append("- 不改变事实与事件因果、人物设定与关系。\\n");
        sb.append("- 保持原有语气与视角。\\n");
        sb.append("输出：只输出精炼后的正文，不要任何解释。\\n\\n");
        sb.append("【待精炼正文】\\n");
        sb.append(content);
        return sb.toString();
    }

    /**
     * 构建带上下文的重写prompt（使用与agentic章节生成相同的上下文）
     *
     * 参考：AgenticChapterWriter.buildDirectWritingContext()
     * 包含：核心设定、卷蓝图、最近章节、图谱数据等
     */
    private String buildRewritePromptWithContext(Long novelId, Integer chapterNumber, String content, String userReq) {
        StringBuilder sb = new StringBuilder();

        try {
            // 获取小说信息
            Novel novel = novelService.getNovelById(novelId);
            if (novel == null) {
                log.warn("小说不存在，使用简单prompt: novelId={}", novelId);
                return buildRewritePrompt(content, userReq);
            }

            // 使用ContextManagementService构建完整上下文（与agentic生成相同）
            Map<String, Object> chapterPlan = new HashMap<>();
            chapterPlan.put("chapterNumber", chapterNumber);

            // 获取完整上下文消息列表
            List<Map<String, String>> contextMessages =
                contextManagementService.buildFullContextMessages(novel, chapterPlan, null, null);

            // 构建重写prompt
            sb.append("你是一名资深网络小说编辑，请在严格保持世界设定、人物名称与关系、事件因果不变的前提下，对下文进行高质量重写。\\n\\n");

            // 添加所有上下文信息（除了最后的user消息）
            for (Map<String, String> msg : contextMessages) {
                if ("system".equals(msg.get("role"))) {
                    sb.append(msg.get("content")).append("\\n\\n");
                }
            }

            sb.append("【重写要求】\\n");
            sb.append("目标：节奏更快、信息更密、可读性更强，但整体长度保持在±10%范围内。\\n");
            sb.append("硬性约束：\\n");
            sb.append("- 严禁改动任何专有名词（人名、称呼、组织、地名、术语），必须与上下文完全一致。\\n");
            sb.append("- 不改变已发生的情节事实和事件因果，只优化叙述与表达。\\n");
            sb.append("- 语气、叙述视角与人设一致。\\n");
            sb.append("- 人物关系、世界设定必须与上下文保持一致。\\n");
            if (userReq != null && !userReq.trim().isEmpty()) {
                sb.append("用户额外要求：").append(userReq.trim()).append("\\n");
            }
            sb.append("输出：只输出重写后的正文，不要任何解释。\\n\\n");
            sb.append("【待重写正文】\\n");
            sb.append(content);

            return sb.toString();
        } catch (Exception e) {
            log.error("构建上下文失败，降级为简单prompt", e);
            return buildRewritePrompt(content, userReq);
        }
    }

    /**
     * 构建带上下文的精炼prompt（使用与agentic章节生成相同的上下文）
     */
    private String buildConcisePromptWithContext(Long novelId, Integer chapterNumber, String content) {
        StringBuilder sb = new StringBuilder();

        try {
            // 获取小说信息
            Novel novel = novelService.getNovelById(novelId);
            if (novel == null) {
                log.warn("小说不存在，使用简单prompt: novelId={}", novelId);
                return buildConcisePrompt(content);
            }

            // 使用ContextManagementService构建完整上下文（与agentic生成相同）
            Map<String, Object> chapterPlan = new HashMap<>();
            chapterPlan.put("chapterNumber", chapterNumber);

            // 获取完整上下文消息列表
            List<Map<String, String>> contextMessages =
                contextManagementService.buildFullContextMessages(novel, chapterPlan, null, null);

            // 构建精炼prompt
            sb.append("请对下文进行精炼重写，以加快节奏、去除冗余为核心。\\n\\n");

            // 添加所有上下文信息（除了最后的user消息）
            for (Map<String, String> msg : contextMessages) {
                if ("system".equals(msg.get("role"))) {
                    sb.append(msg.get("content")).append("\\n\\n");
                }
            }

            sb.append("【精炼要求】\\n");
            sb.append("- 去掉无意义的铺陈、重复与赘词；保留承载关键信息的细节。\\n");
            sb.append("- 对话尽量保真，仅去除啰嗦语；叙述句收紧。\\n");
            sb.append("- 整体字数减少约10%~25%，勿过度删减。\\n");
            sb.append("- 严禁改动专有名词（人名、称呼、组织、地名、术语），必须与上下文完全一致。\\n");
            sb.append("- 不改变事实与事件因果、人物设定与关系。\\n");
            sb.append("- 保持原有语气与视角。\\n");
            sb.append("- 人物关系、世界设定必须与上下文保持一致。\\n");
            sb.append("输出：只输出精炼后的正文，不要任何解释。\\n\\n");
            sb.append("【待精炼正文】\\n");
            sb.append(content);

            return sb.toString();
        } catch (Exception e) {
            log.error("构建上下文失败，降级为简单prompt", e);
            return buildConcisePrompt(content);
        }
    }
}


