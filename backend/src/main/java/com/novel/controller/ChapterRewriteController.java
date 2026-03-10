package com.novel.controller;

import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import com.novel.service.NovelService;
import com.novel.service.ChapterService;
import com.novel.service.ContextManagementService;
import com.novel.domain.entity.Novel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

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

            // 构建带上下文的prompt
            String prompt;
            if (chapterNumber != null && chapterNumber > 0) {
                // 使用上下文增强的重写prompt（智能修改）
                prompt = buildRewritePromptWithContext(novelId, chapterNumber, content, requirements);
                log.info("🔄 开始章节重写流式处理（带上下文），章节号: {}, 内容长度: {}, 使用模型: {}",
                    chapterNumber, content.length(), aiConfig.getModel());
            } else {
                // 降级为简单重写prompt
                prompt = buildRewritePrompt(content, requirements);
                log.info("🔄 开始章节重写流式处理（无上下文），内容长度: {}, 使用模型: {}",
                    content.length(), aiConfig.getModel());
            }

            // 异步执行流式重写
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    aiWritingService.streamGenerateContent(
                        prompt,
                        "chapter_rewrite",
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

    private String buildRewritePrompt(String content, String userReq) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深网络小说编辑，现在处于【编辑模式】：不是从零重写整章，而是在保持世界设定、人物关系和既有剧情事实完全不变的前提下，对已有正文做精确、克制的修改。\\n\\n");
        sb.append("【编辑规则】\\n");
        sb.append("- 只根据“修改要求”相关的内容做最小必要修改；\\n");
        sb.append("- 未被要求修改的句子一个字都不要改（包括标点和换行），避免无意义的同义改写；\\n");
        sb.append("- 不新增或删减剧情信息，不引入新设定，不改变事件因果；\\n");
        sb.append("- 始终保持叙述视角、人物语气和整体文风与原文一致。\\n\\n");
        if (userReq != null && !userReq.trim().isEmpty()) {
            sb.append("【修改要求】\\n").append(userReq.trim()).append("\\n\\n");
        }
        sb.append("【待修改正文】\\n");
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

            // 使用ContextManagementService构建完整上下文（与agentic生成相同，已调整为编辑模式身份）
            Map<String, Object> chapterPlan = new HashMap<>();
            chapterPlan.put("chapterNumber", chapterNumber);

            // 获取完整上下文消息列表
            List<Map<String, String>> contextMessages =
                contextManagementService.buildFullContextMessages(novel, chapterPlan, userReq, null);

            // 构建重写prompt（编辑模式）
            sb.append("你是一名资深网络小说编辑，下面是与本章相关的上下文信息和编辑/重写要求，请在此基础上对给定正文做“最小必要修改”。\\n\\n");

            // 添加所有上下文信息（除了最后的user消息）
            for (Map<String, String> msg : contextMessages) {
                if ("system".equals(msg.get("role"))) {
                    sb.append(msg.get("content")).append("\\n\\n");
                }
            }

            sb.append("【待修改正文】\\n");
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


