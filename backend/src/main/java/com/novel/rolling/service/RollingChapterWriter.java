package com.novel.rolling.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.WritingContext;
import com.novel.agentic.service.AgenticChapterWriter;
import com.novel.agentic.service.CoreStateExtractor;
import com.novel.agentic.service.PlotReasoningService;
import com.novel.agentic.service.StructuredMessageBuilder;
import com.novel.agentic.service.graph.EntityExtractionService;
import com.novel.agentic.service.graph.IGraphService;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelOutline;
import com.novel.domain.entity.NovelVolume;
import com.novel.dto.AIConfigRequest;
import com.novel.mapper.NovelVolumeMapper;
import com.novel.repository.NovelOutlineRepository;
import com.novel.repository.NovelRepository;
import com.novel.service.AIWritingService;
import com.novel.service.ChapterService;
import com.novel.service.ChapterSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 滚动写作服务
 * 
 * 核心流程：
 * 1. 第1章：生成惊艳开局
 * 2. 写完后：评估叙事状态（主线进度、主角困境、悬念）
 * 3. 规划接下来2章的章纲（目标驱动，不是写死剧情点）
 * 4. 按章纲写作（复用AgenticChapterWriter的完整上下文）
 * 5. 写完后评估，继续规划下2章
 * 6. 循环直到完成指定章数
 */
@Service
public class RollingChapterWriter {

    private static final Logger logger = LoggerFactory.getLogger(RollingChapterWriter.class);

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private NovelOutlineRepository outlineRepository;

    @Autowired
    private NovelVolumeMapper volumeMapper;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private ChapterSummaryService chapterSummaryService;

    @Autowired
    private AIWritingService aiWritingService;

    // 复用现有的上下文构建和消息构建
    @Autowired
    private AgenticChapterWriter agenticChapterWriter;

    @Autowired
    private StructuredMessageBuilder structuredMessageBuilder;

    @Autowired(required = false)
    private PlotReasoningService plotReasoningService;

    @Autowired(required = false)
    private IGraphService graphService;

    @Autowired(required = false)
    private CoreStateExtractor coreStateExtractor;

    @Autowired(required = false)
    private EntityExtractionService entityExtractionService;

    private final ObjectMapper mapper = new ObjectMapper();

    // 当前叙事状态（内存中维护）
    private String currentNarrativeState;
    // 当前规划的章纲（2章窗口）
    private List<Map<String, Object>> plannedOutlines = new ArrayList<>();

    /**
     * 主入口：连续写作N章
     */
    public void writeChapters(Long novelId, int count, AIConfigRequest aiConfig, SseEmitter emitter) throws Exception {
        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("小说不存在: " + novelId);
        }

        NovelOutline outline = outlineRepository.findByNovelIdAndStatus(
                novelId, NovelOutline.OutlineStatus.CONFIRMED).orElse(null);

        // 获取当前已写到第几章
        Integer lastChapter = chapterService.getLastChapterNumber(novelId);
        int startChapter = (lastChapter == null) ? 1 : lastChapter + 1;

        sendEvent(emitter, "start", "🚀 开始滚动写作，从第" + startChapter + "章写到第" + (startChapter + count - 1) + "章");

        // 重置状态
        currentNarrativeState = null;
        plannedOutlines.clear();

        // 捕获安全上下文
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication authentication = securityContext.getAuthentication();

        for (int i = 0; i < count; i++) {
            int chapterNumber = startChapter + i;
            NovelVolume volume = findVolumeForChapter(novelId, chapterNumber);

            sendEvent(emitter, "chapter_start", "📖 开始第" + chapterNumber + "章 (" + (i + 1) + "/" + count + ")");

            try {
                if (chapterNumber == 1) {
                    // 第1章：生成惊艳开局
                    writeOpeningChapter(novel, volume, outline, chapterNumber, aiConfig, emitter, authentication);
                } else {
                    // 后续章节：检查是否需要规划
                    if (plannedOutlines.isEmpty()) {
                        // 需要评估和规划
                        sendEvent(emitter, "phase", "🔍 评估叙事状态...");
                        evaluateNarrativeState(novel, volume, outline, chapterNumber - 1, aiConfig, emitter);

                        sendEvent(emitter, "phase", "📋 规划接下来2章...");
                        planNextChapters(novel, volume, outline, chapterNumber, aiConfig, emitter);
                    }

                    // 取出当前章的章纲
                    Map<String, Object> currentOutline = null;
                    if (!plannedOutlines.isEmpty()) {
                        currentOutline = plannedOutlines.remove(0);
                    }

                    // 写作（使用完整上下文）
                    writeChapterWithFullContext(novel, volume, outline, chapterNumber, currentOutline, aiConfig, emitter, authentication);
                }

                sendEvent(emitter, "chapter_done", "✅ 第" + chapterNumber + "章完成");

                // 章节间休息
                if (i < count - 1) {
                    Thread.sleep(2000);
                }

            } catch (Exception e) {
                logger.error("第{}章写作失败", chapterNumber, e);
                sendEvent(emitter, "error", "❌ 第" + chapterNumber + "章失败: " + e.getMessage());
            }
        }

        sendEvent(emitter, "complete", "🎉 全部完成！共写作" + count + "章");
    }

    /**
     * 写惊艳开局（第1章）- 使用完整上下文
     */
    private void writeOpeningChapter(Novel novel, NovelVolume volume, NovelOutline outline,
                                      int chapterNumber, AIConfigRequest aiConfig, SseEmitter emitter,
                                      Authentication authentication) throws Exception {
        sendEvent(emitter, "phase", "🌟 生成惊艳开局...");

        // 收集完整上下文（复用AgenticChapterWriter的逻辑）
        sendEvent(emitter, "phase", "📥 收集写作上下文...");
        WritingContext context = agenticChapterWriter.buildDirectWritingContext(novel.getId(), chapterNumber, null, null);

        // 构建开局意图
        Map<String, Object> openingIntent = buildOpeningIntent(novel, volume, outline);

        // 使用StructuredMessageBuilder构建消息
        List<Map<String, String>> messages = structuredMessageBuilder.buildMessagesFromIntent(
                novel, context, openingIntent, chapterNumber, null, null);

        // 添加开局专用指令
        Map<String, String> openingBooster = new HashMap<>();
        openingBooster.put("role", "system");
        openingBooster.put("content", buildOpeningBoosterPrompt());
        messages.add(1, openingBooster); // 插入到第二位

        sendEvent(emitter, "phase", "✍️ AI创作中...");
        StringBuilder contentBuilder = new StringBuilder();

        aiWritingService.streamGenerateContentWithMessages(
                messages, "chapter_writing", aiConfig,
                chunk -> {
                    contentBuilder.append(chunk);
                    sendEvent(emitter, "content", chunk);
                }
        );

        String content = contentBuilder.toString();
        
        // 保存章节
        sendEvent(emitter, "phase", "💾 保存中...");
        Chapter chapter = saveChapter(novel, chapterNumber, content, aiConfig);

        // 异步抽取实体（复用现有逻辑）
        asyncExtractEntities(novel.getId(), chapterNumber, content, chapter.getTitle(), aiConfig, emitter, authentication);

        logger.info("✅ 开局完成，字数: {}", content.length());
    }

    /**
     * 评估叙事状态
     */
    private void evaluateNarrativeState(Novel novel, NovelVolume volume, NovelOutline outline,
                                         int lastChapterNumber, AIConfigRequest aiConfig, SseEmitter emitter) {
        try {
            // 获取最近2章内容
            List<Chapter> recentChapters = chapterService.getRecentChapters(novel.getId(), lastChapterNumber + 1, 2);
            if (recentChapters == null || recentChapters.isEmpty()) {
                currentNarrativeState = "故事刚开始";
                return;
            }

            String prompt = buildEvaluationPrompt(novel, volume, outline, recentChapters);
            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是资深网文编辑，擅长分析故事状态。请简洁输出JSON格式的分析结果。");
            messages.add(systemMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            StringBuilder responseBuilder = new StringBuilder();
            aiWritingService.streamGenerateContentWithMessages(
                    messages, "narrative_evaluation", aiConfig,
                    chunk -> responseBuilder.append(chunk)
            );

            currentNarrativeState = responseBuilder.toString();
            sendEvent(emitter, "evaluation", "✅ 评估完成");

        } catch (Exception e) {
            logger.warn("评估失败，使用默认状态: {}", e.getMessage());
            currentNarrativeState = "继续推进主线";
        }
    }

    /**
     * 规划接下来2章
     */
    private void planNextChapters(Novel novel, NovelVolume volume, NovelOutline outline,
                                   int startChapter, AIConfigRequest aiConfig, SseEmitter emitter) {
        try {
            List<Chapter> recentChapters = chapterService.getRecentChapters(novel.getId(), startChapter, 2);

            String prompt = buildPlanningPrompt(novel, volume, outline, startChapter, recentChapters);
            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", buildPlanningSystemPrompt());
            messages.add(systemMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            StringBuilder responseBuilder = new StringBuilder();
            aiWritingService.streamGenerateContentWithMessages(
                    messages, "rolling_planning", aiConfig,
                    chunk -> responseBuilder.append(chunk)
            );

            // 解析章纲
            String json = extractJson(responseBuilder.toString());
            plannedOutlines = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});

            sendEvent(emitter, "planning", "✅ 已规划" + plannedOutlines.size() + "章");

        } catch (Exception e) {
            logger.warn("规划失败，使用默认章纲: {}", e.getMessage());
            plannedOutlines = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Map<String, Object> defaultOutline = new HashMap<>();
                defaultOutline.put("chapterNumber", startChapter + i);
                defaultOutline.put("direction", "继续推进主线，制造冲突和悬念");
                defaultOutline.put("emotionalTone", "紧张");
                plannedOutlines.add(defaultOutline);
            }
        }
    }

    /**
     * 使用完整上下文写作章节
     */
    private void writeChapterWithFullContext(Novel novel, NovelVolume volume, NovelOutline outline,
                                              int chapterNumber, Map<String, Object> rollingOutline,
                                              AIConfigRequest aiConfig, SseEmitter emitter,
                                              Authentication authentication) throws Exception {
        // 收集完整上下文（复用AgenticChapterWriter的逻辑）
        sendEvent(emitter, "phase", "📥 收集写作上下文...");
        WritingContext context = agenticChapterWriter.buildDirectWritingContext(novel.getId(), chapterNumber, null, null);

        // 将滚动章纲转换为intent格式
        Map<String, Object> intent = convertRollingOutlineToIntent(rollingOutline);

        // 使用StructuredMessageBuilder构建消息（包含图谱、角色、概要等完整上下文）
        List<Map<String, String>> messages = structuredMessageBuilder.buildMessagesFromIntent(
                novel, context, intent, chapterNumber, null, null);

        sendEvent(emitter, "phase", "✍️ AI创作第" + chapterNumber + "章...");
        StringBuilder contentBuilder = new StringBuilder();

        aiWritingService.streamGenerateContentWithMessages(
                messages, "chapter_writing", aiConfig,
                chunk -> {
                    contentBuilder.append(chunk);
                    sendEvent(emitter, "content", chunk);
                }
        );

        String content = contentBuilder.toString();

        // 保存章节
        sendEvent(emitter, "phase", "💾 保存中...");
        Chapter chapter = saveChapter(novel, chapterNumber, content, aiConfig);

        // 异步抽取实体
        asyncExtractEntities(novel.getId(), chapterNumber, content, chapter.getTitle(), aiConfig, emitter, authentication);

        logger.info("✅ 第{}章完成，字数: {}", chapterNumber, content.length());
    }

    /**
     * 异步抽取实体（复用现有逻辑）
     */
    private void asyncExtractEntities(Long novelId, int chapterNumber, String content, String title,
                                       AIConfigRequest aiConfig, SseEmitter emitter, Authentication authentication) {
        // 异步抽取核心状态
        if (coreStateExtractor != null) {
            CompletableFuture.runAsync(() -> {
                SecurityContext asyncContext = SecurityContextHolder.createEmptyContext();
                asyncContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(asyncContext);
                try {
                    coreStateExtractor.extractAndSaveCoreState(novelId, chapterNumber, content, title, aiConfig);
                    sendEvent(emitter, "extraction", "✅ 核心状态抽取完成");
                } catch (Exception e) {
                    logger.error("核心状态抽取失败", e);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
        }

        // 异步抽取结构化实体
        if (entityExtractionService != null) {
            CompletableFuture.runAsync(() -> {
                SecurityContext asyncContext = SecurityContextHolder.createEmptyContext();
                asyncContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(asyncContext);
                try {
                    entityExtractionService.extractAndSave(novelId, chapterNumber, title, content, aiConfig);
                    sendEvent(emitter, "extraction", "✅ 实体抽取完成");
                } catch (Exception e) {
                    logger.error("实体抽取失败", e);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
        }
    }

    /**
     * 将滚动章纲转换为intent格式
     */
    private Map<String, Object> convertRollingOutlineToIntent(Map<String, Object> rollingOutline) {
        Map<String, Object> intent = new HashMap<>();

        if (rollingOutline == null) {
            intent.put("direction", "继续推进主线，制造冲突和悬念");
            return intent;
        }

        // 直接把规划AI输出的内容传过去
        Object direction = rollingOutline.get("direction");
        if (direction != null) {
            intent.put("direction", direction);
        }

        Object keyPoints = rollingOutline.get("keyPoints");
        if (keyPoints != null) {
            intent.put("keyPlotPoints", keyPoints);
        }

        return intent;
    }

    /**
     * 构建开局意图
     */
    private Map<String, Object> buildOpeningIntent(Novel novel, NovelVolume volume, NovelOutline outline) {
        Map<String, Object> intent = new HashMap<>();

        StringBuilder direction = new StringBuilder();
        direction.append("这是全书第一章，需要一个惊艳的开局。");
        direction.append("用最吸引人的方式开场，前50字就要抓住读者。");
        direction.append("快速建立主角形象和核心冲突，展示世界观的独特之处。");
        direction.append("结尾留下强烈的悬念钩子。");

        intent.put("direction", direction.toString());
        intent.put("emotionalTone", "紧张/悬疑/引人入胜");

        List<String> keyPoints = new ArrayList<>();
        keyPoints.add("用冲突、悬念或强烈情绪开场，不要从日常开始");
        keyPoints.add("快速展示主角的处境和核心矛盾");
        keyPoints.add("用行动和对话展现角色，不要大段介绍");
        keyPoints.add("结尾必须有让读者想看下一章的悬念");
        intent.put("keyPlotPoints", keyPoints);

        return intent;
    }

    // ========== 提示词构建 ==========

    private String buildOpeningBoosterPrompt() {
        return "【黄金开局专用指令】\n" +
               "- 本指令优先级最高，在不推翻核心设定的前提下，优先保证'好看、上瘾、爽'。\n" +
               "- 一开场就让读者'掉进事件里'：用冲突、选择或异常场景切入。\n" +
               "- 前几段就要出现：主角明确的欲望或目标、需要立刻应对的压力或机会。\n" +
               "- 必须让读者感到真实代价：关系紧张、局势恶化、资源被消耗等。\n" +
               "- 避免长篇讲解世界观，把必要信息夹在动作、对话和冲突推进之中。\n" +
               "- 章节结尾必须留下钩子：未解决的问题、危险的悬而未决等。\n";
    }

    private String buildEvaluationPrompt(Novel novel, NovelVolume volume, NovelOutline outline, List<Chapter> recentChapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 叙事状态评估\n\n");
        sb.append("分析最新章节，判断当前故事状态。\n\n");

        sb.append("## 小说类型\n");
        sb.append(novel.getGenre()).append("\n\n");

        if (volume != null && volume.getContentOutline() != null) {
            sb.append("## 卷蓝图\n").append(truncate(volume.getContentOutline(), 1500)).append("\n\n");
        }

        sb.append("## 最近章节内容\n");
        for (Chapter ch : recentChapters) {
            sb.append("### 第").append(ch.getChapterNumber()).append("章\n");
            sb.append(truncate(ch.getContent(), 3000)).append("\n\n");
        }

        sb.append("## 请分析\n");
        sb.append("用简短的文字描述：\n");
        sb.append("1. 主线进度到哪了\n");
        sb.append("2. 女主现在什么处境，委屈积累了多少\n");
        sb.append("3. 男主和女主的关系发展到哪了\n");
        sb.append("4. 反派嚣张程度，有没有该被打脸了\n");
        sb.append("5. 读者现在最期待什么\n");
        sb.append("6. 下一步建议怎么走\n");

        return sb.toString();
    }

    private String buildPlanningSystemPrompt() {
        return "你是顶级女频网文策划，擅长把控故事节奏和读者情绪。\n\n" +
               "【女频爽点核心逻辑】\n" +
               "女频和男频不一样！男频是即时爽，女频是'憋着爽'：\n" +
               "- 女主要先受委屈（被误解、被欺负、被看不起），读者跟着心疼、着急\n" +
               "- 委屈要积累几章，让读者一直'憋着'，等那个反转时刻\n" +
               "- 然后一次性爆发：真相揭露、打脸反派、或者男主霸气护短\n" +
               "- 爆发时要写反派的震惊后悔、旁观者的惊叹羡慕，让读者出一口恶气\n\n" +
               "【男主的作用】\n" +
               "- 男主要强大、有地位，是女主的靠山\n" +
               "- 关键时刻要出来护短，让欺负女主的人付出代价\n" +
               "- 日常要宠女主，细节处体现独宠\n\n" +
               "【节奏把控】\n" +
               "- 不要每章都爽，那样读者会疲劳\n" +
               "- 铺垫2-3章委屈，然后来一个小爆发\n" +
               "- 铺垫一整卷的大委屈，卷末来个大爆发\n" +
               "- 承接前文，自然衔接，每章结尾留悬念\n\n" +
               "输出JSON数组格式的章纲。";
    }

    private String buildPlanningPrompt(Novel novel, NovelVolume volume, NovelOutline outline,
                                        int startChapter, List<Chapter> recentChapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 滚动规划任务\n\n");
        sb.append("请基于当前叙事状态，规划第").append(startChapter).append("章和第").append(startChapter + 1).append("章。\n\n");

        sb.append("## 小说类型\n");
        sb.append(novel.getGenre()).append("\n\n");

        if (volume != null && volume.getContentOutline() != null) {
            sb.append("## 卷蓝图\n").append(truncate(volume.getContentOutline(), 2000)).append("\n\n");
        }

        sb.append("## 当前叙事状态\n");
        sb.append(currentNarrativeState != null ? currentNarrativeState : "故事进行中").append("\n\n");

        if (recentChapters != null && !recentChapters.isEmpty()) {
            sb.append("## 最近章节\n");
            for (Chapter ch : recentChapters) {
                sb.append("### 第").append(ch.getChapterNumber()).append("章\n");
                sb.append(truncate(ch.getContent(), 2000)).append("\n\n");
            }
        }

        sb.append("## 规划要求\n");
        sb.append("分析一下：\n");
        sb.append("1. 女主现在委屈积累到什么程度了？是该继续铺垫还是该爆发了？\n");
        sb.append("2. 反派现在嚣张到什么程度？读者是不是已经很想看他倒霉了？\n");
        sb.append("3. 男主该出场护短了吗？还是再等等？\n");
        sb.append("4. 读者现在最期待看到什么？\n\n");

        sb.append("然后给出接下来2章的方向，直接输出JSON：\n");
        sb.append("```json\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"chapterNumber\": ").append(startChapter).append(",\n");
        sb.append("    \"direction\": \"本章要做什么，给读者什么感觉\",\n");
        sb.append("    \"keyPoints\": [\"关键点1\", \"关键点2\"]\n");
        sb.append("  },\n");
        sb.append("  { 第").append(startChapter + 1).append("章同上 }\n");
        sb.append("]\n```\n");

        return sb.toString();
    }

    // ========== 辅助方法 ==========

    private Chapter saveChapter(Novel novel, int chapterNumber, String content, AIConfigRequest aiConfig) {
        Chapter existing = chapterService.getChapterByNovelAndNumber(novel.getId(), chapterNumber);

        Chapter chapter;
        if (existing == null) {
            chapter = new Chapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNumber(chapterNumber);
            chapter.setTitle("第" + chapterNumber + "章");
            chapter.setContent(content);
            chapter = chapterService.createChapter(chapter);
        } else {
            existing.setContent(content);
            chapter = chapterService.updateChapterInternal(existing.getId(), existing);
        }

        // 生成概括
        try {
            chapterSummaryService.generateOrUpdateSummary(chapter, aiConfig);
            logger.info("✅ 章节概括已生成");
        } catch (Exception e) {
            logger.warn("生成概括失败: {}", e.getMessage());
        }

        return chapter;
    }

    private NovelVolume findVolumeForChapter(Long novelId, int chapterNumber) {
        List<NovelVolume> volumes = volumeMapper.selectByNovelId(novelId);
        if (volumes == null || volumes.isEmpty()) return null;

        for (NovelVolume v : volumes) {
            Integer start = v.getChapterStart();
            Integer end = v.getChapterEnd();
            if (start != null && end != null && chapterNumber >= start && chapterNumber <= end) {
                return v;
            }
        }
        return volumes.get(0);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private String extractJson(String text) {
        if (text == null) return "[]";

        // 尝试提取```json```块
        int start = text.indexOf("```json");
        if (start >= 0) {
            start = text.indexOf("\n", start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // 尝试提取```块
        start = text.indexOf("```");
        if (start >= 0) {
            start = text.indexOf("\n", start) + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // 尝试找[开头
        start = text.indexOf("[");
        if (start >= 0) {
            int end = text.lastIndexOf("]");
            if (end > start) return text.substring(start, end + 1);
        }

        return "[]";
    }

    private void sendEvent(SseEmitter emitter, String type, String data) {
        if (emitter == null) return;
        try {
            String payload = data == null ? "" : data;
            if ("content".equals(type)) {
                payload = payload.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
            }
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .data(payload, MediaType.TEXT_PLAIN);
            if (StringUtils.hasText(type)) {
                builder.name(type);
            }
            emitter.send(builder);
        } catch (IOException e) {
            logger.warn("发送事件失败: {}", type);
        }
    }
}
