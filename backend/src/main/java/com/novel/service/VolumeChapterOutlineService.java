package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.GraphEntity;
import com.novel.agentic.service.graph.IGraphService;
import com.novel.domain.entity.*;
import com.novel.dto.AIConfigRequest;
import com.novel.mapper.NovelVolumeMapper;
import com.novel.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 卷级批量章纲生成服务
 * - 根据：全书大纲 + 本卷蓝图 + 历史伏笔池
 * - 一次性生成本卷的 N 个章纲（默认50）
 * - 返回内存结果（不落库），同时可返回 react_decision_log 供排错
 */
@Service
public class VolumeChapterOutlineService {

    private static final Logger logger = LoggerFactory.getLogger(VolumeChapterOutlineService.class);

    @Autowired
    private NovelVolumeMapper volumeMapper;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private NovelOutlineRepository outlineRepository;

    @Autowired
    private NovelForeshadowingRepository foreshadowingRepository;

    @Autowired
    private VolumeChapterOutlineRepository outlineRepo;

    @Autowired
    private ForeshadowLifecycleLogRepository lifecycleLogRepo;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired(required = false)
    private IGraphService graphService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public Map<String, Object> generateOutlinesForVolume(Long volumeId, Integer count, AIConfigRequest aiConfig) {
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }
        if (count == null || count <= 0) {
            int computed = 0;
            try { computed = volume.getChapterCount(); } catch (Exception ignore) {}
            count = computed > 0 ? computed : 35;  // 默认42章
        }
        // 强制设为42章，避免一次性生成过多章纲导致输出被截断
        if (count > 35) {
            count = 35;
        }
        Novel novel = novelRepository.selectById(volume.getNovelId());
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + volume.getNovelId());
        }
        NovelOutline superOutline = outlineRepository.findByNovelIdAndStatus(
                volume.getNovelId(), NovelOutline.OutlineStatus.CONFIRMED).orElse(null);
        if (superOutline == null || isBlank(superOutline.getPlotStructure())) {
            throw new RuntimeException("缺少已确认的全书大纲(plotStructure)");
        }

        NovelVolume nextVolume = null;
        Integer currentVolumeNumber = volume.getVolumeNumber();
        if (currentVolumeNumber != null) {
            nextVolume = volumeMapper.selectByVolumeNumber(volume.getNovelId(), currentVolumeNumber + 1);
        }

        // 历史未回收伏笔池（ACTIVE）
        List<NovelForeshadowing> unresolved = foreshadowingRepository.findByNovelIdAndStatus(
                volume.getNovelId(), "ACTIVE");

        // 收集已写章节内容（用于创意池生成）
        List<Chapter> chaptersWithContent = new ArrayList<>();
        if (volume.getChapterStart() != null && volume.getChapterEnd() != null) {
            try {
                List<Chapter> chapters = chapterRepository.findByNovelIdAndChapterNumberBetween(
                        volume.getNovelId(),
                        volume.getChapterStart(),
                        volume.getChapterEnd()
                );
                if (chapters != null && !chapters.isEmpty()) {
                    for (Chapter chapter : chapters) {
                        if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()) {
                            chaptersWithContent.add(chapter);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("收集已写章节内容失败: volumeId={}, err={}", volumeId, e.getMessage());
            }
        }

        // ========== 两步式章纲生成 ==========
        // 第一步：生成创意脑洞池（包含已写章节内容）
        logger.info("🧠 开始两步式章纲生成，volumeId={}, count={}, 已写章节数={}", volumeId, count, chaptersWithContent.size());
        String creativeIdeasPool = null;
        try {
            creativeIdeasPool = generateCreativeIdeasPool(novel, volume, superOutline, unresolved, count, aiConfig, chaptersWithContent);
            logger.info("✅ 第一步完成：创意池生成成功，长度={}", creativeIdeasPool != null ? creativeIdeasPool.length() : 0);
        } catch (Exception e) {
            logger.warn("⚠️ 创意池生成失败，将使用传统模式: {}", e.getMessage());
        }
        
        // 第二步：基于创意池生成章纲（如果创意池生成失败，则使用传统模式）
        String basePrompt;
        if (creativeIdeasPool != null && creativeIdeasPool.length() > 200) {
            basePrompt = buildPromptWithCreativePool(novel, volume, nextVolume, superOutline, unresolved, count, creativeIdeasPool);
            logger.info("📝 第二步：使用创意池增强模式生成章纲");
        } else {
            basePrompt = buildPrompt(novel, volume, nextVolume, superOutline, unresolved, count);
            logger.info("📝 使用传统模式生成章纲（创意池不可用）");
        }
        String prompt = basePrompt;

        // 第二步：基于创意池和已写章节生成完整章纲
        if (!chaptersWithContent.isEmpty()) {
                        StringBuilder promptBuilder = new StringBuilder(basePrompt);
                        promptBuilder.append("\n\n");
                        promptBuilder.append("# 已写章节正文与进度（极其重要）\n");
                        promptBuilder.append("下面是本卷中已经有正文的章节。这些正文是既定事实，你必须逐字逐句仔细阅读，深入理解每个细节、每个事件的真实发生过程。\n\n");
                        promptBuilder.append("【核心要求】\n");
                        promptBuilder.append("1. 仔细阅读：不要跳读、不要臆测、不要根据标题或开头就猜测后续内容\n");
                        promptBuilder.append("2. 精准提取：章纲必须准确反映正文中实际发生的事件、对话、物品、人物行为\n");
                        promptBuilder.append("3. 严禁篡改：不得添加正文中不存在的情节，不得修改正文中明确描述的细节\n");
                        promptBuilder.append("4. 逻辑一致：后续章节的章纲必须基于这些真实发生的事件来推进，而不是基于你的想象\n\n");
                        
                        for (Chapter chapter : chaptersWithContent) {
                            Integer chapterNumber = chapter.getChapterNumber();
                            Integer chapterInVolume = null;
                            if (volume.getChapterStart() != null) {
                                chapterInVolume = chapterNumber - volume.getChapterStart() + 1;
                            }
                            promptBuilder.append("## 已写章节正文\n");
                            promptBuilder.append("【全局章节号】").append(chapterNumber).append("\n");
                            if (chapterInVolume != null && chapterInVolume > 0) {
                                promptBuilder.append("【卷内章节号】").append(chapterInVolume).append("\n");
                            }
                            promptBuilder.append("【章节标题】").append(s(chapter.getTitle())).append("\n");
                            String chapterContent = chapter.getContent();
                            if (chapterContent != null && chapterContent.length() > 2000) {
                                chapterContent = chapterContent.substring(0, 2000) + "...";
                            }
                            promptBuilder.append("【正文内容】\n");
                            promptBuilder.append(chapterContent == null ? "" : chapterContent).append("\n\n");
                        }
                        
                        promptBuilder.append("【生成章纲的步骤】\n");
                        promptBuilder.append("第一步：逐章分析已写正文\n");
                        promptBuilder.append("- 仔细阅读每一章的完整内容\n");
                        promptBuilder.append("- 列出本章实际发生的关键事件（不是你认为应该发生的，而是正文中真实写了的）\n");
                        promptBuilder.append("- 注意人物的具体行为、对话内容、物品细节、场景描述\n");
                        promptBuilder.append("- 识别本章留下的未解决问题和伏笔\n\n");
                        
                        promptBuilder.append("第二步：为已写正文生成对应章纲\n");
                        promptBuilder.append("- 章纲必须100%忠实于正文内容\n");
                        promptBuilder.append("- 如果正文写A发生了，章纲就写A，不能写成B\n");
                        promptBuilder.append("- 如果正文中某个物品是X，章纲就写X，不能写成Y\n");
                        promptBuilder.append("- 保持事件的因果关系、时间顺序、人物动机与正文完全一致\n\n");
                        
                        promptBuilder.append("第三步：基于真实进度规划后续章纲\n");
                        promptBuilder.append("- 后续章节必须承接已写正文的真实结局，而不是你想象的结局\n");
                        promptBuilder.append("- 如果正文中某个冲突已经解决，后续不能假装它还在\n");
                        promptBuilder.append("- 如果正文中某个伏笔已经揭晓，后续不能再当悬念\n");
                        promptBuilder.append("- 新的剧情推进要基于已写正文建立的人物关系、局势、信息\n\n");
                        
                        promptBuilder.append("【最终输出】\n");
                        promptBuilder.append("生成完整的").append(count).append("章章纲序列，其中：\n");
                        promptBuilder.append("- 已写正文对应的章纲：必须与正文内容完全一致\n");
                        promptBuilder.append("- 未写正文的章纲：必须自然承接已写部分的真实进度\n");
                        promptBuilder.append("- 整体保持逻辑连贯、因果清晰、不出现矛盾\n");
                        
                        prompt = promptBuilder.toString();
        }

        List<Map<String, String>> messages = buildMessages(prompt);

        logger.info("🤖 调用AI批量生成卷章纲（流式），volumeId={}, count={}, promptLen={}", volumeId, count, prompt.length());

        // 使用流式请求收集完整响应，避免超时
        StringBuilder rawBuilder = new StringBuilder();
        try {
            aiWritingService.streamGenerateContentWithMessages(
                messages,
                "volume_chapter_outlines_generation",
                aiConfig,
                chunk -> {
                    rawBuilder.append(chunk);
                    // 可选：记录进度
                    if (rawBuilder.length() % 1000 == 0) {
                        logger.debug("已接收 {} 字符", rawBuilder.length());
                    }
                }
            );
        } catch (Exception e) {
            logger.error("AI生成卷章纲失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }

        String raw = rawBuilder.toString();
        logger.info("✅ 流式接收完成，总长度: {} 字符", raw.length());

        // 解析 JSON（失败则直接抛异常，不删除旧数据）
        String json = extractPureJson(raw);

        // 预先清理所有非标准引号，避免JSON解析失败
        json = cleanJsonQuotes(json);

        List<Map<String, Object>> outlines;
        try {
            outlines = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
        } catch (Exception e) {
            logger.error("❌ 解析卷章纲失败: {}", e.getMessage());
            logger.error("原始返回长度: {} 字符", raw.length());
            logger.error("清理后JSON长度: {} 字符", json.length());
            logger.error("清理后JSON(前500): {}", json.substring(0, Math.min(500, json.length())));
            logger.error("清理后JSON(后500): {}", json.substring(Math.max(0, json.length() - 500)));
            
            // 额外打印完整原始返回，方便排查截断或格式问题
            logger.error("📥 原始RAW返回内容（完整）:\n{}", raw);
            
            // 检查是否包含markdown或特殊字符
            if (json.contains("**") || json.contains("__")) {
                logger.error("⚠️ JSON中仍包含Markdown格式标记，清理可能不完整");
            }
            if (json.contains("```") ) {
                logger.error("⚠️ JSON中包含代码块标记");
            }
            
            throw new RuntimeException("解析卷章纲失败，请检查AI返回格式: " + e.getMessage() + " | JSON长度:" + json.length());
        }

        // 验证生成数量
        if (outlines == null || outlines.isEmpty()) {
            logger.error("❌ AI返回空章纲列表");
            throw new RuntimeException("AI返回空章纲列表，生成失败");
        }
        logger.info("✅ AI生成章纲成功: volumeId={}, 实际生成{}章", volumeId, outlines.size());

        // 附带决策日志
        String reactDecisionLog = buildDecisionLog(novel, volume, superOutline, unresolved, prompt, raw, count);

        // 入库：保存章纲 + 伏笔生命周期日志（失败则抛异常，触发事务回滚）
        persistOutlines(volume, outlines, reactDecisionLog);
        logger.info("✅ 卷章纲已入库: volumeId={}, count={}", volumeId, outlines.size());

        // 只有完全成功才返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("volumeId", volumeId);
        result.put("novelId", volume.getNovelId());
        result.put("count", outlines.size());
        result.put("outlines", outlines);
        result.put("react_decision_log", reactDecisionLog);
        return result;
    }

    @Transactional
    public Map<String, Object> generateOutlinesForRemainingChapters(
            Long volumeId,
            Integer count,
            AIConfigRequest aiConfig,
            String userRequirements
    ) {
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }

        Integer start = volume.getChapterStart();
        Integer end = volume.getChapterEnd();
        if (start == null || end == null || start <= 0 || end < start) {
            throw new RuntimeException("当前卷未配置有效的章节范围（chapterStart/chapterEnd），无法仅为未写正文的章节生成章纲");
        }

        Novel novel = novelRepository.selectById(volume.getNovelId());
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + volume.getNovelId());
        }

        NovelOutline superOutline = outlineRepository.findByNovelIdAndStatus(
                volume.getNovelId(), NovelOutline.OutlineStatus.CONFIRMED).orElse(null);
        if (superOutline == null || isBlank(superOutline.getPlotStructure())) {
            throw new RuntimeException("缺少已确认的全书大纲(plotStructure)");
        }

        NovelVolume nextVolume = null;
        Integer currentVolumeNumber = volume.getVolumeNumber();
        if (currentVolumeNumber != null) {
            nextVolume = volumeMapper.selectByVolumeNumber(volume.getNovelId(), currentVolumeNumber + 1);
        }

        // 历史未回收伏笔池（ACTIVE）
        List<NovelForeshadowing> unresolved = foreshadowingRepository.findByNovelIdAndStatus(
                volume.getNovelId(), "ACTIVE");

        // 计算当前已写到本卷第几章
        List<Chapter> chapters = chapterRepository.findByNovelIdAndChapterNumberBetween(
                volume.getNovelId(),
                start,
                end
        );
        List<Chapter> chaptersWithContent = new ArrayList<>();
        if (chapters != null) {
            for (Chapter chapter : chapters) {
                if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()) {
                    chaptersWithContent.add(chapter);
                }
            }
        }

        Integer lastWrittenGlobalChapter = null;
        if (!chaptersWithContent.isEmpty()) {
            for (Chapter chapter : chaptersWithContent) {
                Integer chapterNumber = chapter.getChapterNumber();
                if (chapterNumber != null) {
                    if (lastWrittenGlobalChapter == null || chapterNumber > lastWrittenGlobalChapter) {
                        lastWrittenGlobalChapter = chapterNumber;
                    }
                }
            }
        }

        int writtenCountInVolume = 0;
        if (lastWrittenGlobalChapter != null) {
            writtenCountInVolume = lastWrittenGlobalChapter - start + 1;
            if (writtenCountInVolume < 0) {
                writtenCountInVolume = 0;
            }
        }

        int totalChaptersInVolume = end - start + 1;
        
        // 限制总章节数最大为50章
        int maxTotalChapters = 50;
        int remainingChapters;
        
        if (writtenCountInVolume >= maxTotalChapters) {
            throw new RuntimeException("本卷已写章节数达到或超过50章上限，无法继续生成新章纲");
        }
        
        // 计算还能生成多少章(总共50章 - 已写章数)
        remainingChapters = maxTotalChapters - writtenCountInVolume;
        
        // 如果卷的章节范围本身就小于50,则以实际剩余为准
        int actualRemaining = totalChaptersInVolume - writtenCountInVolume;
        if (actualRemaining <= 0) {
            throw new RuntimeException("本卷章节正文已经全部写完或未剩余空白章节，无需生成新的章纲");
        }
        remainingChapters = Math.min(remainingChapters, actualRemaining);

        if (count == null || count <= 0 || count > remainingChapters) {
            count = remainingChapters;
        }

        int firstNewChapterInVolume = writtenCountInVolume + 1;

        // 已有章纲：用于为后续未写正文章节提供历史事件与人物关系上下文
        List<VolumeChapterOutline> previousOutlines = new ArrayList<>();
        try {
            List<VolumeChapterOutline> existingOutlines = outlineRepo.findByVolumeId(volume.getId());
            if (existingOutlines != null) {
                for (VolumeChapterOutline o : existingOutlines) {
                    if (o.getChapterInVolume() != null && o.getChapterInVolume() < firstNewChapterInVolume) {
                        previousOutlines.add(o);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("加载已有章纲失败，volumeId={}, err={}", volumeId, e.getMessage());
        }

        if (!previousOutlines.isEmpty()) {
            previousOutlines.sort(java.util.Comparator.comparing(VolumeChapterOutline::getChapterInVolume));
        }

        int firstNew = firstNewChapterInVolume;
        int lastNew = firstNewChapterInVolume + count - 1;

        java.util.List<GraphEntity> graphEvents = null;
        java.util.List<GraphEntity> graphForeshadows = null;
        Integer graphAnchorChapter = null;
        if (volume.getChapterStart() != null) {
            graphAnchorChapter = volume.getChapterStart() + firstNew - 1;
        } else if (lastWrittenGlobalChapter != null) {
            graphAnchorChapter = lastWrittenGlobalChapter;
        }
        if (graphService != null && graphAnchorChapter != null && graphAnchorChapter > 1) {
            try {
                java.util.List<GraphEntity> evts = graphService.getRelevantEvents(volume.getNovelId(), graphAnchorChapter, 10);
                if (evts != null && !evts.isEmpty()) {
                    graphEvents = evts;
                }
            } catch (Exception e) {
                logger.warn("获取图谱历史事件失败, novelId={}, chapter={}, err={}", volume.getNovelId(), graphAnchorChapter, e.getMessage());
            }
            try {
                java.util.List<GraphEntity> foreshadows = graphService.getUnresolvedForeshadows(volume.getNovelId(), graphAnchorChapter, 10);
                if (foreshadows != null && !foreshadows.isEmpty()) {
                    graphForeshadows = foreshadows;
                }
            } catch (Exception e) {
                logger.warn("获取图谱伏笔失败, novelId={}, chapter={}, err={}", volume.getNovelId(), graphAnchorChapter, e.getMessage());
            }
        }

        // ========== 两步式章纲生成（增量模式） ==========
        // 第一步：生成创意脑洞池（包含已写章节内容）
        logger.info("🧠 开始两步式增量章纲生成，volumeId={}, count={}, startChapter={}, 已写章节数={}", volumeId, count, firstNewChapterInVolume, chaptersWithContent.size());
        String creativeIdeasPool = null;
        try {
            creativeIdeasPool = generateCreativeIdeasPool(novel, volume, superOutline, unresolved, count, aiConfig, chaptersWithContent);
            logger.info("✅ 第一步完成：创意池生成成功，长度={}", creativeIdeasPool != null ? creativeIdeasPool.length() : 0);
        } catch (Exception e) {
            logger.warn("⚠️ 创意池生成失败，将使用传统模式: {}", e.getMessage());
        }
        
        // 第二步：基于创意池生成章纲
        String basePrompt;
        if (creativeIdeasPool != null && creativeIdeasPool.length() > 200) {
            basePrompt = buildPromptWithCreativePool(novel, volume, nextVolume, superOutline, unresolved, count, creativeIdeasPool);
            logger.info("📝 第二步：使用创意池增强模式生成增量章纲");
        } else {
            basePrompt = buildPrompt(novel, volume, nextVolume, superOutline, unresolved, count);
            logger.info("📝 使用传统模式生成增量章纲（创意池不可用）");
        }
        StringBuilder promptBuilder = new StringBuilder(basePrompt);

        // 先明确本次是“增量补全”模式，而不是重写整卷
        promptBuilder.append("\n# 本次任务模式说明（增量补全）\n");
        if (writtenCountInVolume > 0) {
            promptBuilder.append("本卷目前已有正文写到【卷内第")
                    .append(writtenCountInVolume)
                    .append("章】。这些章节及其对应的走向视为既定历史，本次任务不是推翻重写整卷，而是在保持这些既定章节不变的前提下，\n");
            promptBuilder.append("只为【卷内第")
                    .append(firstNew)
                    .append("章～第")
                    .append(lastNew)
                    .append("章】规划新的章纲序列，用于自然承接并放大前文留下的局面、人物关系和伏笔。\n");
        } else {
            promptBuilder.append("本卷目前尚未写出任何正文，本次任务等价于从卷首开始，为后续")
                    .append(count)
                    .append("章规划初始章纲（卷内第")
                    .append(firstNew)
                    .append("章～第")
                    .append(lastNew)
                    .append("章）。\n");
        }
        promptBuilder.append("后面列出的“已有章纲与历史事件概览”（如存在）用于帮助你理解前文历史，它们是只读时间线，不要在输出结果中尝试重写这些既有章节，只能向后续章节延伸。\n\n");

        if (!isBlank(userRequirements)) {
            promptBuilder.append("\n# 作者需求与偏好（本次仅影响尚未写正文的章节）\n");
            promptBuilder.append("下面是作者针对后续章节给出的额外要求，请在保持逻辑自洽的前提下尽量满足：\n");
            promptBuilder.append(userRequirements.trim()).append("\n");
            promptBuilder.append("当这些需求与现有大纲略有冲突时，请优先保证节奏爽感、一环扣一环的推进与强钩子，再对细节做温和调整，而不是完全推翻前文。\n\n");
        }

        if (!previousOutlines.isEmpty()) {
            promptBuilder.append("\n# 已有章纲与历史事件概览（只读，不可重写）\n");
            promptBuilder.append("下面是本卷在当前进度之前已经存在的章纲摘要。它们已经确定了主要事件走向、人物关系和历史节点，你需要在此基础上，为之后尚未写正文的章节规划新的章纲：\n\n");
            for (VolumeChapterOutline o : previousOutlines) {
                Integer chapterInVolume = o.getChapterInVolume();
                Integer globalChapterNumber = o.getGlobalChapterNumber();
                if (chapterInVolume == null || chapterInVolume <= 0) {
                    continue;
                }
                promptBuilder.append("## 卷内第").append(chapterInVolume).append("章章纲\n");
                if (globalChapterNumber != null && globalChapterNumber > 0) {
                    promptBuilder.append("【全局章节号】").append(globalChapterNumber).append("\n");
                }
                String dir = s(o.getDirection());
                if (!isBlank(dir)) {
                    promptBuilder.append("【direction（本章大方向）】").append(dir).append("\n");
                }
                String kps = o.getKeyPlotPoints();
                if (!isBlank(kps)) {
                    promptBuilder.append("【keyPlotPoints（关键剧情点，JSON数组原文）】").append(limit(kps, 2000)).append("\n");
                }
                String subplot = o.getSubplot();
                if (!isBlank(subplot)) {
                    promptBuilder.append("【subplot（支线/人物关系要点）】").append(limit(subplot, 1000)).append("\n");
                }
                String antagonism = o.getAntagonism();
                if (!isBlank(antagonism)) {
                    promptBuilder.append("【antagonism（对手与赌注，JSON对象原文）】").append(limit(antagonism, 1000)).append("\n");
                }
                promptBuilder.append("\n");
            }

            promptBuilder.append("请特别注意：\n");
            promptBuilder.append("- 上述章纲描述的章节视为【既有历史】，你不要重新设计或推翻，只能在后续章纲中自然承接这些章节留下的局面、人物关系和伏笔；\n");
            promptBuilder.append("- 本次只为【卷内第").append(firstNew).append("章到第").append(lastNew).append("章】生成新的章纲，用于承接并放大上述事件与关系；\n");
            promptBuilder.append("- 每一章都要在目标推进、冲突升级或爽点兑现上给读者明确的反馈，避免纯过场；\n");
            promptBuilder.append("- 每一章结尾都要留下尚未解决的问题、危机或强烈情绪钩子，让读者强烈想看下一章。\n");
        } else {
            promptBuilder.append("\n# 当前进度\n");
            if (writtenCountInVolume > 0) {
                promptBuilder.append("本卷目前已有正文写到卷内第").append(writtenCountInVolume)
                        .append("章，但暂未加载到对应的章纲记录。本次从卷内第").append(firstNewChapterInVolume)
                        .append("章开始，连续规划后续").append(count).append("章章纲。\n");
            } else {
                promptBuilder.append("本卷暂时还没有已写正文或既有章纲，本次任务等价于从第1章开始为后续")
                        .append(count).append("章规划章纲。\n");
            }
        }

        if ((graphEvents != null && !graphEvents.isEmpty()) || (graphForeshadows != null && !graphForeshadows.isEmpty())) {
            promptBuilder.append("\n# 图谱视角下的历史事件与伏笔摘要\n");
            if (graphEvents != null && !graphEvents.isEmpty()) {
                promptBuilder.append("【历史事件（图谱）】\n");
                for (GraphEntity ev : graphEvents) {
                    if (ev == null) {
                        continue;
                    }
                    Integer chNum = ev.getChapterNumber();
                    java.util.Map<String, Object> props = ev.getProperties();
                    Object desc = props != null ? props.get("description") : null;
                    if (desc == null) {
                        continue;
                    }
                    promptBuilder.append("- [第")
                            .append(chNum != null ? chNum : 0)
                            .append("章] ")
                            .append(limit(desc.toString(), 120))
                            .append("\n");
                    Object location = props != null ? props.get("location") : null;
                    if (location != null) {
                        String loc = location.toString().trim();
                        if (!loc.isEmpty()) {
                            promptBuilder.append("  · 地点：").append(limit(loc, 60)).append("\n");
                        }
                    }
                    Object participants = props != null ? props.get("participants") : null;
                    if (participants != null) {
                        String part = participants.toString().trim();
                        if (!part.isEmpty()) {
                            promptBuilder.append("  · 参与者：").append(limit(part, 80)).append("\n");
                        }
                    }
                }
                promptBuilder.append("\n");
            }
            if (graphForeshadows != null && !graphForeshadows.isEmpty()) {
                promptBuilder.append("【待回收伏笔（图谱）】\n");
                for (GraphEntity f : graphForeshadows) {
                    if (f == null) {
                        continue;
                    }
                    java.util.Map<String, Object> props = f.getProperties();
                    Object desc = props != null ? props.get("description") : null;
                    if (desc == null) {
                        continue;
                    }
                    promptBuilder.append("- ")
                            .append(limit(desc.toString(), 120));
                    Object planted = props != null ? props.get("plantedAt") : null;
                    if (planted != null) {
                        promptBuilder.append("（埋于第").append(planted.toString()).append("章）");
                    }
                    promptBuilder.append("\n");
                }
                promptBuilder.append("\n");
            }
            promptBuilder.append("以上图谱摘要仅用于补充提醒哪些事件和伏笔在前文已经发生或尚未回收，你在生成后续章纲时应避免与这些既定事实冲突。\n");
        }

        // ========== 加入已写章节正文内容（特别是最新章节） ==========
        if (!chaptersWithContent.isEmpty()) {
            promptBuilder.append("\n# 已写章节正文内容（极其重要 - 必须基于此推导后续）\n");
            promptBuilder.append("下面是本卷中已经有正文的章节。这些正文是既定事实，你必须仔细阅读，深入理解每个细节。\n");
            promptBuilder.append("**特别注意最后一章的内容**，后续章纲必须自然承接最后一章的结尾。\n\n");
            
            // 按章节号排序，确保最新章节在最后
            List<Chapter> sortedChapters = new ArrayList<>(chaptersWithContent);
            sortedChapters.sort((a, b) -> {
                Integer numA = a.getChapterNumber();
                Integer numB = b.getChapterNumber();
                if (numA == null) return -1;
                if (numB == null) return 1;
                return numA.compareTo(numB);
            });
            
            // 如果章节太多，只保留最近的几章（避免提示词过长）
            int maxChaptersToShow = 5;
            int startIdx = Math.max(0, sortedChapters.size() - maxChaptersToShow);
            if (startIdx > 0) {
                promptBuilder.append("（为避免提示词过长，仅展示最近").append(maxChaptersToShow).append("章的正文内容）\n\n");
            }
            
            for (int i = startIdx; i < sortedChapters.size(); i++) {
                Chapter chapter = sortedChapters.get(i);
                Integer chapterNumber = chapter.getChapterNumber();
                Integer chapterInVol = null;
                if (volume.getChapterStart() != null && chapterNumber != null) {
                    chapterInVol = chapterNumber - volume.getChapterStart() + 1;
                }
                
                boolean isLastChapter = (i == sortedChapters.size() - 1);
                if (isLastChapter) {
                    promptBuilder.append("## 【最新章节 - 必须重点关注】\n");
                } else {
                    promptBuilder.append("## 已写章节\n");
                }
                
                promptBuilder.append("【全局章节号】").append(chapterNumber).append("\n");
                if (chapterInVol != null && chapterInVol > 0) {
                    promptBuilder.append("【卷内章节号】").append(chapterInVol).append("\n");
                }
                promptBuilder.append("【章节标题】").append(s(chapter.getTitle())).append("\n");
                
                String chapterContent = chapter.getContent();
                // 最新章节给更多内容，其他章节适当截断
                int maxLen = isLastChapter ? 4000 : 2000;
                if (chapterContent != null && chapterContent.length() > maxLen) {
                    chapterContent = chapterContent.substring(0, maxLen) + "...[内容截断]";
                }
                promptBuilder.append("【正文内容】\n");
                promptBuilder.append(chapterContent == null ? "" : chapterContent).append("\n\n");
            }
            
            promptBuilder.append("**关键要求**：\n");
            promptBuilder.append("1. 后续章纲必须自然承接【最新章节】的结尾，不能出现断层\n");
            promptBuilder.append("2. 已写章节中建立的人物关系、事件进展、情绪状态都是既定事实\n");
            promptBuilder.append("3. 不要重复已写章节中已经发生的事件\n");
            promptBuilder.append("4. 如果最新章节结尾有悬念或冲突，下一章必须承接处理\n\n");
        }

        promptBuilder.append("\n# 本次任务的输出范围\n");
        promptBuilder.append("- 你需要输出一个长度恰好为").append(count).append("的JSON数组，仅包含【卷内第")
                .append(firstNew).append("章～第").append(lastNew).append("章】的章纲；\n");
        promptBuilder.append("- 按数组顺序规划剧情：数组第1个元素对应卷内第").append(firstNew)
                .append("章，数组第2个对应卷内第").append(firstNew + 1)
                .append("章，以此类推，直到数组第").append(count).append("个元素对应卷内第")
                .append(lastNew).append("章；\n");
        promptBuilder.append("- 请在每个元素的 chapterInVolume 字段中填写对应的真实卷内章节号（例如：当本次从卷内第")
                .append(firstNew).append("章开始、共生成").append(count)
                .append("章时，输出的 chapterInVolume 应分别为 ")
                .append(firstNew).append("、").append(firstNew + 1).append("、...、").append(lastNew).append("）。\n");

        String prompt = promptBuilder.toString();

        List<Map<String, String>> messages = buildMessages(prompt);

        logger.info("🤖 调用AI增量生成卷章纲（仅未写正文部分），volumeId={}, firstNewChapterInVolume={}, count={}, promptLen={}",
                volumeId, firstNewChapterInVolume, count, prompt.length());

        // 使用流式请求收集完整响应，避免超时
        StringBuilder rawBuilder = new StringBuilder();
        try {
            aiWritingService.streamGenerateContentWithMessages(
                messages,
                "volume_chapter_outlines_generation_missing",
                aiConfig,
                chunk -> {
                    rawBuilder.append(chunk);
                    if (rawBuilder.length() % 1000 == 0) {
                        logger.debug("已接收 {} 字符", rawBuilder.length());
                    }
                }
            );
        } catch (Exception e) {
            logger.error("AI增量生成卷章纲失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }

        String raw = rawBuilder.toString();
        logger.info("✅ 流式接收完成（增量生成），总长度: {} 字符", raw.length());

        String json = extractPureJson(raw);

        // 预先清理所有非标准引号，避免JSON解析失败
        json = cleanJsonQuotes(json);

        List<Map<String, Object>> outlines;
        try {
            outlines = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
        } catch (Exception e) {
            logger.error("❌ 解析增量卷章纲失败: {}\n清理后JSON(前500)：{}", e.getMessage(), json.substring(0, Math.min(500, json.length())));
            throw new RuntimeException("解析卷章纲失败，请检查AI返回格式: " + e.getMessage());
        }

        // 验证生成数量
        if (outlines == null || outlines.isEmpty()) {
            logger.error("❌ AI返回空章纲列表（增量生成）");
            throw new RuntimeException("AI返回空章纲列表，生成失败");
        }
        if (outlines.size() != count) {
            logger.warn("⚠️ 增量生成章纲数量与期望不一致: expected={}, actual={}", count, outlines.size());
        }
        logger.info("✅ AI增量生成章纲成功: volumeId={}, startChapterInVolume={}, 实际生成{}章", volumeId, firstNewChapterInVolume, outlines.size());

        // 附带决策日志
        String reactDecisionLog = buildDecisionLog(novel, volume, superOutline, unresolved, prompt, raw, count);

        // 入库：保存后半部分章纲 + 伏笔生命周期日志（不清空整卷旧数据）
        persistRemainingOutlines(volume, firstNewChapterInVolume, outlines, reactDecisionLog);
        logger.info("✅ 卷章纲增量入库完成: volumeId={}, startChapterInVolume={}, count={}", volumeId, firstNewChapterInVolume, outlines.size());

        // 只有完全成功才返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("volumeId", volumeId);
        result.put("novelId", volume.getNovelId());
        result.put("startChapterInVolume", firstNewChapterInVolume);
        result.put("count", outlines.size());
        result.put("outlines", outlines);
        result.put("react_decision_log", reactDecisionLog);
        return result;
    }

    @Transactional
    public VolumeChapterOutline generateOutlineFromChapterContent(Chapter chapter, AIConfigRequest aiConfig) {
        if (chapter == null) {
            return null;
        }
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new RuntimeException("AI配置无效，请先在设置页面配置AI服务");
        }
        if (chapter.getContent() == null || chapter.getContent().trim().isEmpty()) {
            logger.warn("章节内容为空，跳过章纲生成: novelId={}, chapter={}", chapter.getNovelId(), chapter.getChapterNumber());
            return null;
        }

        Long novelId = chapter.getNovelId();
        Integer chapterNumber = chapter.getChapterNumber();

        com.novel.domain.entity.NovelVolume volume = volumeMapper.selectByChapterNumber(novelId, chapterNumber);
        if (volume == null) {
            logger.warn("未找到章节所属卷，跳过章纲生成: novelId={}, chapter={}", novelId, chapterNumber);
            return null;
        }

        Novel novel = novelRepository.selectById(volume.getNovelId());
        if (novel == null) {
            logger.warn("小说不存在，跳过章纲生成: novelId={}", volume.getNovelId());
            return null;
        }

        NovelOutline superOutline = outlineRepository.findByNovelIdAndStatus(
                volume.getNovelId(), NovelOutline.OutlineStatus.CONFIRMED).orElse(null);
        if (superOutline == null || isBlank(superOutline.getPlotStructure())) {
            logger.warn("缺少已确认的全书大纲，跳过章纲生成: novelId={}", volume.getNovelId());
            return null;
        }

        NovelVolume nextVolume = null;
        Integer currentVolumeNumber = volume.getVolumeNumber();
        if (currentVolumeNumber != null) {
            nextVolume = volumeMapper.selectByVolumeNumber(volume.getNovelId(), currentVolumeNumber + 1);
        }

        List<NovelForeshadowing> unresolved = foreshadowingRepository.findByNovelIdAndStatus(
                volume.getNovelId(), "ACTIVE");

        String basePrompt = buildPrompt(novel, volume, nextVolume, superOutline, unresolved, 1);

        String chapterContent = chapter.getContent();
        if (chapterContent.length() > 4000) {
            chapterContent = chapterContent.substring(0, 4000) + "...";
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(basePrompt);
        promptBuilder.append("\n\n");
        promptBuilder.append("# 已有章节正文（用于校准本章章纲）\n");
        promptBuilder.append("下面是卷内本章的实际小说内容，请根据正文调整本章的方向、关键剧情点和情绪，使章纲与已写内容严格对齐：\n");
        promptBuilder.append("【全局章节号：").append(chapterNumber).append("】\n");
        promptBuilder.append("【章节标题：").append(s(chapter.getTitle())).append("】\n");
        promptBuilder.append("【章节正文节选】\n");
        promptBuilder.append(chapterContent).append("\n\n");
        promptBuilder.append("请仍然只输出一个JSON数组，长度为1，对应该章的章纲。");

        List<Map<String, String>> messages = buildMessages(promptBuilder.toString());

        // 使用流式请求收集完整响应
        StringBuilder rawBuilder = new StringBuilder();
        try {
            aiWritingService.streamGenerateContentWithMessages(
                messages, 
                "chapter_outline_from_content", 
                aiConfig, 
                chunk -> rawBuilder.append(chunk)
            );
        } catch (Exception e) {
            logger.error("AI按正文生成章纲失败: novelId={}, chapter={}, 错误={}", novelId, chapterNumber, e.getMessage(), e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
        
        String raw = rawBuilder.toString();
        logger.info("✅ 流式接收完成，总长度: {} 字符", raw.length());

        String json = extractPureJson(raw);
        
        // 预先清理所有非标准引号，避免JSON解析失败
        json = cleanJsonQuotes(json);
        
        List<Map<String, Object>> outlines;
        try {
            outlines = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
        } catch (Exception e) {
            logger.error("解析按正文生成的章纲失败: novelId={}, chapter={}, 错误={}\n清理后JSON(前500)：{}", 
                novelId, chapterNumber, e.getMessage(), json.substring(0, Math.min(500, json.length())));
            throw new RuntimeException("解析章纲失败，请检查AI返回格式: " + e.getMessage());
        }

        if (outlines == null || outlines.isEmpty()) {
            logger.error("AI返回空章纲，跳过: novelId={}, chapter={}", novelId, chapterNumber);
            return null;
        }

        Map<String, Object> outline = outlines.get(0);

        VolumeChapterOutline entity = outlineRepo.findByNovelAndGlobalChapter(novelId, chapterNumber);
        if (entity == null) {
            entity = new VolumeChapterOutline();
            entity.setNovelId(volume.getNovelId());
            entity.setVolumeId(volume.getId());
            entity.setVolumeNumber(volume.getVolumeNumber());
        }

        Integer chapterInVolume = null;
        if (volume.getChapterStart() != null) {
            chapterInVolume = chapterNumber - volume.getChapterStart() + 1;
        }
        if (chapterInVolume == null || chapterInVolume <= 0) {
            Object civ = outline.get("chapterInVolume");
            if (civ instanceof Number) {
                chapterInVolume = ((Number) civ).intValue();
            } else {
                chapterInVolume = chapterNumber;
            }
        }

        entity.setChapterInVolume(chapterInVolume);
        entity.setGlobalChapterNumber(chapterNumber);
        entity.setDirection(getString(outline, "direction"));
        entity.setKeyPlotPoints(null);  // 已废弃，不再使用
        entity.setEmotionalTone(null);  // 已废弃，不再使用
        entity.setForeshadowAction(getString(outline, "foreshadowAction"));
        entity.setForeshadowDetail(toJson(outline.get("foreshadowDetail")));
        entity.setSubplot(null);  // 已废弃，不再使用
        entity.setAntagonism(null);  // 已废弃，不再使用
        entity.setStatus("WRITTEN");

        if (entity.getId() == null) {
            outlineRepo.insert(entity);
        } else {
            outlineRepo.updateById(entity);
        }

        return entity;
    }

    private List<Map<String, String>> buildMessages(String prompt) {
        List<Map<String, String>> msgs = new ArrayList<>();
        msgs.add(msg("user", prompt));
        return msgs;
    }

    private Map<String, String> msg(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /**
     * 两步式章纲生成：第一步 - 文风识别 + 禁止事项 + 风格预判
     * 核心任务：深度分析本书文风DNA，建立风格护栏，预判并标记不符合风格的剧情
     */
    private String generateCreativeIdeasPool(Novel novel, NovelVolume volume, NovelOutline superOutline, 
                                              List<NovelForeshadowing> unresolved, int count, AIConfigRequest aiConfig,
                                              List<Chapter> chaptersWithContent) {

        StringBuilder prompt = new StringBuilder();

        // ========= 基础信息预处理 =========
        String worldView = s(superOutline.getWorldSetting());
        String levelAndFamily = s(superOutline.getCoreSettings());
        if (isBlank(levelAndFamily)) {
            levelAndFamily = s(superOutline.getMainCharacters());
        }


        // 当前剧情进度摘要（最近5章，用于文风分析）
        StringBuilder progress = new StringBuilder();
        if (chaptersWithContent != null && !chaptersWithContent.isEmpty()) {
            int startIdx = Math.max(0, chaptersWithContent.size() - 5);
            for (int i = startIdx; i < chaptersWithContent.size(); i++) {
                Chapter c = chaptersWithContent.get(i);
                progress.append("【第")
                        .append(c.getChapterNumber())
                        .append("章 ")
                        .append(s(c.getTitle()))
                        .append("】");
                String content = c.getContent();
                if (content != null) {
                    content = content.trim();
                    if (content.length() > 400) {
                        content = content.substring(0, 400) + "...";
                    }
                    progress.append("\n").append(content).append("\n\n");
                } else {
                    progress.append("\n（本章正文暂无内容）\n\n");
                }
            }
        }

        // 部分伏笔摘要，供后续“伏笔清单”使用
        StringBuilder foreshadowSummary = new StringBuilder();
        if (unresolved != null && !unresolved.isEmpty()) {
            int shown = 0;
            for (NovelForeshadowing f : unresolved) {
                if (shown++ >= 8) break;
                foreshadowSummary.append("- ")
                        .append(s(limit(f.getContent(), 120)))
                        .append("\n");
            }
        }

        // ========= 新版第一步：文风识别 + 禁止事项 + 风格预判 =========

        prompt.append("# 章纲生成第一步：文风识别与风格守护\n\n");
        prompt.append("你的核心任务是：**无视平庸的过渡剧情，只为“戏点”服务**。\n");
        prompt.append("如果当前【卷蓝图】的中间过程无聊，请直接推翻，仅保留【卷末目标】一致，用更高明的手段重写过程。\n\n");

        // 输入信息模块（用真实数据填充）
        prompt.append("# 输入信息\n");
        prompt.append("- **世界观/等级设定**：").append(worldView).append("\n");
        if (!isBlank(levelAndFamily)) {
            prompt.append("  - 等级/家族关系补充：").append(levelAndFamily).append("\n");
        }
        prompt.append("- **核心大纲**：").append(s(superOutline.getPlotStructure())).append("\n");
        prompt.append("- **当前卷蓝图（仅供参考，烂则弃之）**：").append(s(volume.getContentOutline())).append("\n");
        prompt.append("- **卷末必须达成的结果**：");
        if (!isBlank(volume.getTheme())) {
            prompt.append("围绕本卷主题「").append(s(volume.getTheme())).append("」，请推演一个具有强记忆点和强冲突收束的【卷末状态】。\n");
        } else {
            prompt.append("结合全书大纲与本卷蓝图，自行归纳一个高冲突、高代价的【卷末目标】。\n");
        }
        prompt.append("- **当前剧情进度**：\n");
        if (progress.length() > 0) {
            prompt.append(progress);
        } else {
            prompt.append("（本卷暂无已写正文，你可完全按大纲与卷蓝图重构）\n\n");
        }

        prompt.append("---\n\n");

        // 第一模块：人设“皮下”重构
        prompt.append("## 第一模块：人设“皮下”重构（挖掘“戏点”）\n");
        prompt.append("古言的剧情动力源于**“人心不足”**和**“阶级压迫”**。请不要只给我表面人设，我要看到他们的**“七情六欲”**和**“生存痛点”**。\n\n");

        prompt.append("**1. 主角现状扫描**\n");
        prompt.append("- **当前困局**：她现在的处境有什么隐形炸弹？（不仅是明面的敌人，还有名声、利益链、猪队友）\n");
        prompt.append("- **读者期待**：在这个阶段，读者最想看女主展现什么特质？（是隐忍蛰伏？是借刀杀人？还是强势碾压？）\n");
        prompt.append("- **违和感设计**：设计一个女主行为与身份不符的“钩子”，让人觉得“她不对劲，她有后手”。\n\n");

        prompt.append("**2. 配角/反派的“降智”修复与“高光”赋予**\n");
        prompt.append("*请基于大纲，挑选本卷3个关键角色进行深度侧写：*\n");
        prompt.append("- **角色A（对立面）**：\n");
        prompt.append("  - **表面动机**：嫉妒/争宠/利益。\n");
        prompt.append("  - **深层逻辑**：她为什么**必须**在这个时候搞事？（是因为家族压力？还是抓住了女主的把柄？）\n");
        prompt.append("  - **手段升级**：别让她只会骂街或罚跪。给她设计一个**符合她智商的高级陷阱**（如：捧杀、连环计、利用礼法规则杀人）。\n");
        prompt.append("- **角色B（变量/中立）**：\n");
        prompt.append("  - 此人如何被卷入局中？他/她的入局如何改变风向？\n\n");

        // 第二模块：脑洞风暴与破局方案
        prompt.append("---\n\n");
        prompt.append("## 第二模块：脑洞风暴与破局方案（提供选项库）\n");
        prompt.append("**核心指令**：抛弃平铺直叙。针对本卷目标，提供3种不同风格的**“设局-破局”**脑洞方案。方案要利用**“信息差”**和**“规则漏洞”**。\n\n");

        prompt.append("### 方案一：【极限反杀流】（爽感优先）\n");
        prompt.append("- **核心冲突**：反派布下死局（如：巫蛊、私通、大不敬），证据确凿。\n");
        prompt.append("- **危机爆点**：女主被逼到悬崖边，只有一步之遥就万劫不复。\n");
        prompt.append("- **破局脑洞**：女主如何**预判了对方的预判**？如何利用对方的证据反过来锤死对方？（强调“置之死地而后生”）\n");
        prompt.append("- **爽点预设**：当众揭穿时的打脸力度。\n\n");

        prompt.append("### 方案二：【借力打力流】（智斗优先）\n");
        prompt.append("- **核心冲突**：多方势力混战，女主看似弱小。\n");
        prompt.append("- **危机爆点**：神仙打架，凡人遭殃，女主被迫站队。\n");
        prompt.append("- **破局脑洞**：女主如何**做局**，让两方大佬斗起来，自己坐收渔利？（强调“四两拨千斤”）\n");
        prompt.append("- **细节诡计**：设计一个关键道具或一句话，成为引爆局势的导火索。\n\n");

        prompt.append("### 方案三：【人设崩塌/反转流】（情感/悬疑优先）\n");
        prompt.append("- **核心冲突**：信任危机，或盟友背叛。\n");
        prompt.append("- **危机爆点**：最亲近的人突然反咬一口，或者最完美的伪装被撕破。\n");
        prompt.append("- **破局脑洞**：利用**情感弱点**或**家族秘密**进行心理战。\n");
        prompt.append("- **人性拷问**：在利益面前的人性抉择。\n\n");

        // 第三模块：世界观与礼法规则的武器化
        prompt.append("---\n\n");
        prompt.append("## 第三模块：世界观与礼法规则的“武器化”\n");
        prompt.append("古言的特色在于**“戴着镣铐跳舞”**。请检查上述脑洞，并挖掘以下元素：\n\n");
        prompt.append("1. **规则杀人**：\n");
        prompt.append("   - 本卷中，哪条**礼法/家规/宫规**可以被反派用来压死女主？\n");
        prompt.append("   - 女主又利用了哪条**冷僻的规则**或者**潜规则**完成了反杀？\n\n");
        prompt.append("2. **环境借势**：\n");
        prompt.append("   - 场景（如：寿宴、祭祀、春猎、省亲）如何成为推动剧情的关键？\n");
        prompt.append("   - **“众目睽睽”**：设计一个场景，必须在所有重要人物面前发生冲突，让反派无法抵赖，无法私了。\n\n");
        prompt.append("3. **物件伏笔**：\n");
        prompt.append("   - 设计一个不起眼的小物件（香料、衣料、药渣、书信），它在开头出现，最后成为定罪的关键铁证。\n\n");

        // 第四模块：读者嗨点自检
        prompt.append("---\n\n");
        prompt.append("## 第四模块：读者嗨点自检（筛选器）\n");
        prompt.append("请像一个挑剔的读者一样审视以上方案，并回答：\n");
        prompt.append("- **拒绝憋屈**：女主是否有长时间的被动挨打？（如果有，必须立刻删改，改为“看似挨打实则挖坑”）。\n");
        prompt.append("- **拒绝降智**：反派的阴谋是否一眼就能看穿？（如果是，请重设阴谋）。\n");
        prompt.append("- **拒绝老梗**：是否又是“推人下水”、“下堕胎药”这种烂大街桥段？（必须换一种更新颖的陷害方式，如“利用相克食物”、“利用忌讳图案”等）。\n\n");

        // 输出要求：编剧备忘录
        prompt.append("---\n\n");
        prompt.append("## 输出要求\n");
        prompt.append("请不要输出连续的故事，而是输出**“编剧的备忘录”**：\n");
        prompt.append("1. **【关键博弈点】**：列出本卷3-5个核心的交锋回合（谁出招，怎么拆招）。\n");
        prompt.append("2. **【脑洞推荐】**：从上述三个方案中，综合出一个**最精彩、最符合人设**的剧情线建议。\n");
        prompt.append("3. **【高光时刻】**：具体描述一个**“名场面”**（画面感极强，情绪张力拉满的瞬间）。\n");
        prompt.append("4. **【伏笔清单】**：需要前置埋下的3个线索。\n\n");

        if (foreshadowSummary.length() > 0) {
            prompt.append("【可优先考虑回收或加深的既有伏笔（供参考，不必逐条照搬）】\n");
            prompt.append(foreshadowSummary).append("\n");
        }

        // 调用AI生成创意池
        logger.info("🧠 第一步：生成创意脑洞池，promptLen={}", prompt.length());
        
        StringBuilder rawBuilder = new StringBuilder();
        try {
            aiWritingService.streamGenerateContentWithMessages(
                buildMessages(prompt.toString()),
                "creative_ideas_generation",
                aiConfig,
                chunk -> rawBuilder.append(chunk)
            );
        } catch (Exception e) {
            logger.error("生成创意池失败: {}", e.getMessage(), e);
            return null;
        }
        
        String result = rawBuilder.toString();
        logger.info("✅ 文风分析生成完成，长度: {} 字符", result.length());
        return result;
    }
    
    /**
     * 构建第一步提示词：文风识别与风格守护
     * 核心任务：深度分析本书文风DNA，建立风格护栏，预判并标记不符合风格的剧情
     */
    private String buildStyleGuardPrompt(Novel novel, NovelVolume volume, NovelOutline superOutline,
                                          String worldView, String levelAndFamily, String genre,
                                          String basicIdea, String mainCharacters,
                                          StringBuilder progress, StringBuilder foreshadowSummary) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# 章纲生成第一步：文风识别与风格守护\n\n");
        prompt.append("你是一位拥有十年创作经验的网文金牌编辑，精通各类题材的文风把控。\n");
        prompt.append("你的核心任务是：**深度分析本书的文风DNA**，并为后续章纲生成建立**风格护栏**。\n\n");

        prompt.append("---\n\n");
        prompt.append("## 一、输入信息\n\n");
        
        prompt.append("### 1.1 小说基本信息\n");
        prompt.append("- **小说标题**：").append(s(novel.getTitle())).append("\n");
        prompt.append("- **小说类型**：").append(genre).append("\n");
        prompt.append("- **核心构思**：").append(basicIdea).append("\n\n");
        
        prompt.append("### 1.2 世界观与设定\n");
        prompt.append(worldView).append("\n");
        if (!isBlank(levelAndFamily)) {
            prompt.append("- **核心设定补充**：").append(levelAndFamily).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("### 1.3 主要角色\n");
        prompt.append(mainCharacters).append("\n\n");
        
        prompt.append("### 1.4 全书大纲\n");
        prompt.append(s(superOutline.getPlotStructure())).append("\n\n");
        
        prompt.append("### 1.5 当前卷信息\n");
        prompt.append("- **卷号**：第").append(nz(volume.getVolumeNumber(), 1)).append("卷\n");
        prompt.append("- **卷名**：《").append(s(volume.getTitle())).append("》\n");
        prompt.append("- **卷主题**：").append(s(volume.getTheme())).append("\n");
        prompt.append("- **本卷蓝图**：\n").append(s(volume.getContentOutline())).append("\n\n");
        
        prompt.append("### 1.6 已写正文（用于文风分析）\n");
        if (progress.length() > 0) {
            prompt.append(progress);
        } else {
            prompt.append("（本卷暂无已写正文）\n\n");
        }

        if (foreshadowSummary.length() > 0) {
            prompt.append("### 1.7 待回收伏笔\n");
            prompt.append(foreshadowSummary).append("\n");
        }

        prompt.append("---\n\n");

        // 第二模块：文风DNA识别
        prompt.append("## 二、文风DNA识别（核心任务）\n\n");
        prompt.append("请仔细阅读上述所有信息，特别是已写正文（如有），深度分析本书的文风特征：\n\n");
        
        prompt.append("### 2.1 题材风格定位\n");
        prompt.append("- **题材类型**：根据大纲和设定，判断本书属于什么细分题材？\n");
        prompt.append("- **基调定位**：本书的整体基调是什么？（轻松/紧张/温馨/沉重/爽快等）\n");
        prompt.append("- **节奏特点**：本书的叙事节奏偏向？（快节奏爽文/慢热细腻/张弛有度等）\n\n");
        
        prompt.append("### 2.2 人设风格特征\n");
        prompt.append("- **主角人设标签**：用3-5个词概括主角的核心特质\n");
        prompt.append("- **主角行事风格**：主角处理问题的典型方式是什么？\n");
        prompt.append("- **配角群像特点**：配角的设计风格是什么？\n\n");
        
        prompt.append("### 2.3 爽点与情绪设计\n");
        prompt.append("- **核心爽点类型**：本书主打什么类型的爽点？\n");
        prompt.append("- **情绪曲线偏好**：读者期待的情绪体验是什么？\n\n");

        prompt.append("---\n\n");

        // 第三模块：禁止事项清单
        prompt.append("## 三、禁止事项清单（风格护栏）\n\n");
        prompt.append("基于上述文风分析，列出本书**绝对禁止**的内容和写法：\n\n");
        
        prompt.append("### 3.1 题材禁忌\n");
        prompt.append("请列出5-8条与本书题材风格**严重不符**的内容类型：\n");
        prompt.append("格式：【禁止】具体内容 → 【原因】为什么不符合本书风格\n\n");
        
        prompt.append("### 3.2 人设禁忌\n");
        prompt.append("请列出5-8条会**破坏主角人设**的行为或情节：\n");
        prompt.append("格式：【禁止】具体行为 → 【原因】为什么与主角人设矛盾\n\n");
        
        prompt.append("### 3.3 节奏禁忌\n");
        prompt.append("请列出3-5条会**破坏本书节奏**的写法：\n");
        prompt.append("格式：【禁止】具体写法 → 【原因】为什么会破坏节奏\n\n");
        
        prompt.append("### 3.4 俗套禁忌\n");
        prompt.append("请列出5-8条**已经被用烂的老套路**，本书必须避免：\n");
        prompt.append("格式：【禁止】具体套路 → 【替代方案】更新颖的处理方式\n\n");

        prompt.append("---\n\n");

        // 第四模块：本卷风格预判
        prompt.append("## 四、本卷风格预判（提前排雷）\n\n");
        prompt.append("仔细审视本卷蓝图，预判可能出现的**不符合风格**的剧情走向：\n\n");
        
        prompt.append("### 4.1 蓝图风险点扫描\n");
        prompt.append("请逐条检查本卷蓝图中的剧情安排，标记出可能的风险：\n");
        prompt.append("- 【风险点】蓝图中的具体内容\n");
        prompt.append("- 【风险类型】人设崩塌/节奏拖沓/俗套老梗/逻辑漏洞/情绪断层\n");
        prompt.append("- 【修正建议】如何调整才能符合本书风格\n\n");
        
        prompt.append("### 4.2 剧情走向预警\n");
        prompt.append("基于本卷蓝图，预判以下可能出现的问题并提前标记禁止：\n");
        prompt.append("- **降智预警**：哪些角色可能被写得智商下线？如何避免？\n");
        prompt.append("- **憋屈预警**：哪些情节可能让读者感到过度憋屈？如何调整？\n");
        prompt.append("- **拖沓预警**：哪些过渡情节可能显得冗长无聊？如何精简？\n");
        prompt.append("- **断层预警**：哪些情节转折可能显得突兀？如何铺垫？\n\n");

        prompt.append("---\n\n");

        // 第五模块：注意事项
        prompt.append("## 五、注意事项（写作指南）\n\n");
        
        prompt.append("### 5.1 必须保持的元素\n");
        prompt.append("列出本书**必须贯穿始终**的核心元素（5-8条）\n\n");
        
        prompt.append("### 5.2 章节设计原则\n");
        prompt.append("- **开篇原则**：每章开头应该如何吸引读者？\n");
        prompt.append("- **冲突原则**：冲突应该如何设计才符合本书风格？\n");
        prompt.append("- **收尾原则**：每章结尾应该如何设置钩子？\n\n");
        
        prompt.append("### 5.3 一环扣一环的设计要求\n");
        prompt.append("本书的章纲必须做到**环环相扣**：\n");
        prompt.append("- 每章结尾必须为下一章埋下**必须解决的问题**或**必须揭晓的悬念**\n");
        prompt.append("- 每章开头必须**承接上章的钩子**，不能断层\n");
        prompt.append("- 每3-5章形成一个**小闭环**，解决一个阶段性问题\n");
        prompt.append("- 每个小闭环的结尾必须**引出更大的问题**，形成递进\n");
        prompt.append("- 每章都要思考：**下一章如何才能更惊艳、更让读者想不到？**\n\n");
        
        prompt.append("### 5.4 反俗套设计思路\n");
        prompt.append("为了让剧情不落俗套，每个关键情节都要问自己：\n");
        prompt.append("- 读者看到这里会预期什么发展？\n");
        prompt.append("- 如何在合理的前提下**颠覆这个预期**？\n");
        prompt.append("- 颠覆后的发展是否**比预期更精彩**？\n\n");

        prompt.append("---\n\n");

        // 输出要求
        prompt.append("## 六、输出要求\n\n");
        prompt.append("请按照以下结构输出你的分析结果：\n\n");
        prompt.append("【文风DNA】\n");
        prompt.append("- 题材定位：...\n");
        prompt.append("- 基调定位：...\n");
        prompt.append("- 节奏特点：...\n");
        prompt.append("- 主角标签：...\n");
        prompt.append("- 核心爽点：...\n\n");
        prompt.append("【禁止事项清单】\n");
        prompt.append("一、题材禁忌\n");
        prompt.append("1. 【禁止】... → 【原因】...\n\n");
        prompt.append("二、人设禁忌\n");
        prompt.append("1. 【禁止】... → 【原因】...\n\n");
        prompt.append("三、节奏禁忌\n");
        prompt.append("1. 【禁止】... → 【原因】...\n\n");
        prompt.append("四、俗套禁忌\n");
        prompt.append("1. 【禁止】... → 【替代方案】...\n\n");
        prompt.append("【本卷风险预警】\n");
        prompt.append("1. 【风险点】... → 【修正建议】...\n\n");
        prompt.append("【写作注意事项】\n");
        prompt.append("1. ...\n\n");
        prompt.append("【环环相扣设计要点】\n");
        prompt.append("- 本卷核心悬念线：...\n");
        prompt.append("- 阶段性小高潮安排：...\n");
        prompt.append("- 章节钩子设计思路：...\n");
        prompt.append("- 反俗套突破点：...\n\n");
        prompt.append("请确保输出内容**具体、可执行**，避免空泛的描述。\n");
        
        return prompt.toString();
    }
    
    /**
     * 从核心设定中提取主角人设信息
     */
    private String extractProtagonistProfile(NovelOutline superOutline) {
        if (superOutline == null) return null;
        
        String coreSettings = superOutline.getCoreSettings();
        if (!isBlank(coreSettings)) {
            // 尝试从核心设定中提取主角相关部分
            StringBuilder profile = new StringBuilder();
            String[] lines = coreSettings.split("\n");
            boolean inProtagonistSection = false;
            
            for (String line : lines) {
                if (line.contains("主角") || line.contains("起点设定") || line.contains("角色")) {
                    inProtagonistSection = true;
                }
                if (inProtagonistSection) {
                    profile.append(line).append("\n");
                    // 遇到下一个大标题时停止
                    if (profile.length() > 100 && (line.matches("^\\d+\\..*") || line.startsWith("##"))) {
                        break;
                    }
                }
            }
            
            if (profile.length() > 50) {
                return profile.toString();
            }
        }
        
        // 如果核心设定中没有，尝试从大纲中提取
        String plotStructure = superOutline.getPlotStructure();
        if (!isBlank(plotStructure)) {
            // 简单提取包含"主角"关键词的段落
            String[] paragraphs = plotStructure.split("\n\n");
            StringBuilder profile = new StringBuilder();
            for (String para : paragraphs) {
                if (para.contains("主角") || para.contains("女主") || para.contains("男主")) {
                    profile.append(para).append("\n");
                    if (profile.length() > 500) break;
                }
            }
            if (profile.length() > 50) {
                return profile.toString();
            }
        }
        
        return null;
    }
    
    /**
     * 两步式章纲生成：第二步 - 基于创意池生成章纲
     */
    private String buildPromptWithCreativePool(Novel novel, NovelVolume volume, NovelVolume nextVolume, 
                                                NovelOutline superOutline, List<NovelForeshadowing> unresolved, 
                                                int count, String creativeIdeasPool) {
        // 章节范围（用于节奏图描述）
        String chapterRange = volume.getChapterStart() != null && volume.getChapterEnd() != null
                ? String.format("第%s-%s章", volume.getChapterStart(), volume.getChapterEnd())
                : "未指定";

        // 下一卷信息块（可选，帮助模型校准卷末与后续衔接）
        String nextVolumeBlock = "";
        if (nextVolume != null) {
            String nextChapterRange = nextVolume.getChapterStart() != null && nextVolume.getChapterEnd() != null
                    ? String.format("第%s-%s章", nextVolume.getChapterStart(), nextVolume.getChapterEnd())
                    : "未指定";
            String nextTemplate =
                    "# 下一卷信息（供节奏规划参考）\n" +
                    "- 下一卷序：第%s卷\n" +
                    "- 下一卷卷名：%s\n" +
                    "- 下一卷主题：%s\n" +
                    "- 下一卷蓝图：\n%s\n" +
                    "- 下一卷章节范围：%s\n\n";
            nextVolumeBlock = String.format(
                    nextTemplate,
                    nz(nextVolume.getVolumeNumber(), "?"),
                    s(nextVolume.getTitle()),
                    s(nextVolume.getTheme()),
                    s(limit(nextVolume.getContentOutline(), 3000)),
                    nextChapterRange
            );
        }

        // 历史伏笔摘要块（给“伏笔清单/隐患”提供素材）
        String unresolvedBlock;
        if (unresolved != null && !unresolved.isEmpty()) {
            List<String> lines = new ArrayList<>();
            int shown = 0;
            for (NovelForeshadowing f : unresolved) {
                if (shown++ >= 20) break;
                lines.add(String.format("- [#%s] %s", f.getId(), s(limit(f.getContent(), 150))));
            }
            unresolvedBlock = String.join("\n", lines);
        } else {
            unresolvedBlock = "- （无）";
        }

        // ========= 新版第二步提示词：古言卷章纲·“上帝视角”编排 =========
        //
        // 注意：保持 JSON 输出规范与现有代码一致，仅更换前置思考与编排逻辑提示。
        String template =
                "你是一位**拥有上帝视角的古言剧情架构师**。\n" +
                "\n" +
                "你不仅要将第一步的【脑洞/戏点】转化为章节，更要进行**逻辑缝合**。你要像下围棋一样，这一章落的子，是为了三章后提掉对方的“大龙”。\n" +
                "\n" +
                "**你的目标**：生成一份逻辑严密、节奏紧凑、伏笔自洽、且具有强阅读粘性的分章细纲。\n" +
                "\n" +
                "# 输入信息\n" +
                "\n" +
                "- **核心大纲**：%s\n" +
                "- **世界观/等级/禁忌**：%s\n" +
                "- **卷蓝图与目标**：%s\n" +
                "- **【第一步输出的创意包】**：\n%s\n" +
                "- **待生成章节数**：%s 章（当前卷章节范围：%s，卷名《%s》，第%s卷）\n" +
                "\n" +
                "%s" +
                "\n" +
                "【历史未回收伏笔（供参考，可用于伏笔清单/隐患设计）】\n" +
                "%s\n" +
                "\n" +
                "---\n" +
                "\n" +
                "## 核心思考协议（生成前必读）\n" +
                "\n" +
                "在开始动笔前，请运行以下三个逻辑协议：\n" +
                "\n" +
                "1.  **“阻力递增”协议**：\n" +
                "    *   主角的任何计划，**绝不能**一次性顺利完成。\n" +
                "    *   必须遵循：**计划 → 意外（猪队友/突发状况） → 补救/将计就计 → 最终达成**。\n" +
                "    *   *拒绝流水账，拥抱波折。*\n" +
                "\n" +
                "2.  **“信息差”管理协议**：\n" +
                "    *   明确每一章中：**读者知道什么？主角知道什么？反派以为自己知道什么？**\n" +
                "    *   利用信息差制造爽点（例如：读者和主角知道陷阱在哪里，看着反派踩进去）。\n" +
                "\n" +
                "3.  **“草蛇灰线”回收协议**：\n" +
                "    *   第一步提供的【伏笔】，不能只在结尾出现。必须在前几章看似随意地提及（如：一句闲聊、一个眼神、一种特殊的香气），然后在高潮章引爆。\n" +
                "\n" +
                "---\n" +
                "\n" +
                "## 古言权谋风格与用语约束（简版）\n" +
                "\n" +
                "- **时代语感**：称谓、礼仪、场景必须符合古代语境，避免明显的现代词汇（如：人权、社保、数据、系统 BUG 等），也不要出现科普式术语。\n" +
                "- **权谋逻辑**：一切冲突背后要有清晰的利益与权力结构支撑，不能为了斗而斗；主角/反派的每一步都要有立场与动机。\n" +
                "- **去分析腔**：`keyPlotPoints` 要写成“可直接展开成剧情段落的事件/对话/心理博弈提示”，而不是论文式总结（避免使用“首先/其次/然后/综上所述”等总结性句式）。\n" +
                "- **情绪优先**：每章至少有一个明确的情绪落点（爽/虐/紧张/舒缓），并通过关键场景或对话体现出来，而不是只说“气氛紧张、压力很大”之类的空话。\n" +
                "\n" +
                "---\n" +
                "\n" +
                "## 编织步骤与节奏规划\n" +
                "\n" +
                "不要一上来就写第一章。请先进行**【剧情落位】**：\n" +
                "\n" +
                "1.  **高潮锚定**：将第一步中那个最精彩的【名场面/破局点】，安排在倒数第1或第2章。\n" +
                "2.  **起点铺垫**：前20%%的章节，必须完成危机的引入和伏笔的埋设。\n" +
                "3.  **中段拉扯**：中间章节负责“见招拆招”，体现双方势力的博弈（你出一招，我挡一招，局势升级）。\n" +
                "\n" +
                "---\n" +
                "\n" +
                "## 执行输出：分章精密细纲（结构说明）\n" +
                "\n" +
                "你需要为本段剧情生成共 %s 章的分章细纲。每一章内部要遵循以下逻辑结构，但在最终输出时，请以 JSON 的 `keyPlotPoints` 字段浓缩表达，不要写成长篇小说正文：\n" +
                "\n" +
                "### 第X章：{四字古风标题，含隐喻，如：风起萍末}\n" +
                "\n" +
                "*   **【本章信息差状态】**：\n" +
                "    *   主角掌握的信息：...\n" +
                "    *   反派掌握的信息：...（此处体现谁在算计谁）\n" +
                "\n" +
                "*   **【剧情精密推演】**（用若干条 `keyPlotPoints` 来承载）：\n" +
                "    1.  **切入（Hook）**：承接上章悬念，直接切入冲突现场或危机发酵点。（拒绝起床洗漱等无效开篇）。\n" +
                "    2.  **发展（Twist）**：\n" +
                "        *   主角/反派开始行动。\n" +
                "        *   **突发变故**：插入第一步中的某个“阻碍/变量”，导致原定计划受阻。\n" +
                "    3.  **应对（Action）**：主角如何利用规则/人心/资源进行应对？（此处展示智商）。\n" +
                "    4.  **落点（Result）**：本事件的阶段性结果，谁占了上风？是否为后续埋下更大的隐患？\n" +
                "\n" +
                "*   **【高光细节/台词】**：\n" +
                "    *   预设一段关键的**潜台词对话**或**心理博弈**（可以压缩成 1-2 条 `keyPlotPoints` 形式，例如：[高光台词] / [心理博弈]）。\n" +
                "\n" +
                "*   **【结尾悬念（钩子）】**：\n" +
                "    *   *危机钩子*：新的证据被发现/强敌突然入场。\n" +
                "    *   *情绪钩子*：误会加深/被逼入绝境。\n" +
                "    *   *（必须让读者有强烈的翻页冲动）*\n" +
                "\n" +
                "---\n" +
                "\n" +
                "## 剧情串联自检（生成后修正）\n" +
                "\n" +
                "在输出之前，请对整段分章细纲进行自检，如有问题须在内部推理层面修正后，再给出最终 JSON：\n" +
                "\n" +
                "1.  **逻辑断层**：第X章主角获得了胜利，是否解释了反派为什么会输？（是反派轻敌？还是情报错误？不能是反派突然变傻）。\n" +
                "2.  **大纲偏离**：这几章的折腾，是否确实推动了【卷蓝图与卷末目标】的达成？如果只是为了斗而斗，请删减，合并到主线中。\n" +
                "3.  **人物工具化**：配角的行动是否有自己的动机？（不能只是为了给女主送道具）。\n" +
                "4.  **古韵缺失**：是否出现了明显的现代思维或词汇？（如：人权、社保、现代医学术语等，需转化为古言语境）。\n" +
                "\n" +
                "---\n" +
                "\n" +
                "## 最终输出清单（请用 JSON 表达，不要写成自然语言段落）\n" +
                "\n" +
                "1.  **本段剧情节奏图**（可压缩进注释性 `keyPlotPoints` 或放入某一章的说明点中）：\n" +
                "    *   起：起始若干章负责危机引入与伏笔埋设。\n" +
                "    *   承：中段章节通过“见招拆招”不断抬高筹码与赌注。\n" +
                "    *   转：倒数若干章引爆【名场面/破局点】，完成情绪与局势大反转。\n" +
                "    *   合：在保持卷末目标达成的前提下，留出对下一卷的钩子与隐患。\n" +
                "\n" +
                "2.  **%s 章精密细纲**：\n" +
                "    *   使用 JSON 数组，每个元素代表卷内一章。\n" +
                "    *   通过 `chapterInVolume` 指明卷内章节号。\n" +
                "    *   通过 `keyPlotPoints`（4-7 条左右）承载本章的“信息差状态 / 冲突发展 / 高光细节 / 结尾钩子”等要点。\n" +
                "\n" +
                "3.  **遗留给下一段剧情的伏笔/隐患**：\n" +
                "    *   使用 `foreshadowAction` + `foreshadowDetail` 字段，标记哪些伏笔在本段被埋下或被部分回收。\n" +
                "\n" +
                "### JSON 输出规范（与后端解析严格对齐）\n" +
                "\n" +
                "- 最终只输出一个 **JSON 数组**，长度必须等于 %s（不要附加任何解释性文字，也不要使用 Markdown 代码块符号如 ```json）。\n" +
                "- **严禁少生成或多生成章节**：如果你在推演中一度想缩短或拉长篇幅，请在内部思考阶段自行调整结构，但最终输出的数组元素数量必须严格等于目标章节数 %s。\n" +
                "- 数组中的每个元素表示一章，字段要求如下：\n" +
                "  - \"chapterInVolume\"：整数，卷内章节序号（从1开始，依次递增）。\n" +
                "  - \"direction\"：字符串，本章剧情方向（章纲），包含关键剧情点，每个剧情点用换行分隔。\n" +
                "  - \"foreshadowAction\"：字符串，取值为 \"NONE\" / \"PLANT\" / \"RESOLVE\"。\n" +
                "  - \"foreshadowDetail\"：可以为 null，或为一个 JSON 对象，描述伏笔内容与预期回收方式（例如：{\"content\":\"宫中焚香异味\",\"expectedResolve\":\"卷末寿宴前后\"}）。\n" +
                "\n" +
                "【JSON 结构示例】（仅作结构参考）：\n" +
                "[\n" +
                "  {\n" +
                "    \"chapterInVolume\": 1,\n" +
                "    \"direction\": \"[事件] 主角收到神秘来信，决定前往调查\\n[冲突] 途中遭遇伏击，与敌人展开激战\\n[结果] 主角险胜，但发现更大的阴谋\\n[钩子] 信中提到的神秘人物即将现身\",\n" +
                "    \"foreshadowAction\": \"PLANT\",\n" +
                "    \"foreshadowDetail\": {\"content\": \"神秘来信的真正发送者\", \"expectedResolve\": \"第三卷揭晓\"}\n" +
                "  }\n" +
                "]\n" +
                "\n" +
                "注意：不要添加 keyPlotPoints、emotionalTone、subplot、antagonism 等字段。\n" +
                "请严格按照上述字段与结构输出 JSON 数组本体。";

        return String.format(
                template,
                // 核心大纲
                s(superOutline.getPlotStructure()),
                // 世界观/等级/禁忌
                s(superOutline.getWorldSetting()),
                // 卷蓝图与目标
                s(volume.getContentOutline()),
                // 第一步输出的创意包（脑洞池）
                s(creativeIdeasPool),
                // 待生成章节数 + 基础信息
                count,
                chapterRange,
                s(volume.getTitle()),
                nz(volume.getVolumeNumber(), "?"),
                // 下一卷信息块（可选）
                nextVolumeBlock,
                // 历史未回收伏笔
                unresolvedBlock,
                // 章节数（多处说明使用）
                count,
                count,
                // JSON 规范中的目标长度 + 再次强调
                count,
                count
        );
    }

    private String buildPrompt(Novel novel, NovelVolume volume, NovelVolume nextVolume, NovelOutline superOutline,
                               List<NovelForeshadowing> unresolved, int count) {
        // 章节范围
        String chapterRange = volume.getChapterStart() != null && volume.getChapterEnd() != null
                ? String.format("第%s-%s章", volume.getChapterStart(), volume.getChapterEnd())
                : "未指定";

        // 下一卷信息块（可选）
        String nextVolumeBlock = "";
        if (nextVolume != null) {
            String nextChapterRange = nextVolume.getChapterStart() != null && nextVolume.getChapterEnd() != null
                    ? String.format("第%s-%s章", nextVolume.getChapterStart(), nextVolume.getChapterEnd())
                    : "未指定";
            String nextTemplate =
                    "# 下一卷信息（供节奏规划参考）\n" +
                    "- 下一卷序：第%s卷\n" +
                    "- 下一卷卷名：%s\n" +
                    "- 下一卷主题：%s\n" +
                    "- 下一卷蓝图：\n%s\n" +
                    "- 下一卷章节范围：%s\n\n";
            nextVolumeBlock = String.format(
                    nextTemplate,
                    nz(nextVolume.getVolumeNumber(), "?"),
                    s(nextVolume.getTitle()),
                    s(nextVolume.getTheme()),
                    s(limit(nextVolume.getContentOutline(), 4000)),
                    nextChapterRange
            );
        }

        // 历史伏笔摘要块
        String unresolvedBlock;
        if (unresolved != null && !unresolved.isEmpty()) {
            List<String> lines = new ArrayList<>();
            int shown = 0;
            for (NovelForeshadowing f : unresolved) {
                if (shown++ >= 30) {
                    break;
                }
                lines.add(String.format(
                        "- [#%s] 优先级%s | 植入章节=%s | 内容：%s",
                        f.getId(),
                        nz(f.getPriority(), 0),
                        nz(f.getPlantedChapter(), 0),
                        s(limit(f.getContent(), 200))
                ));
            }
            unresolvedBlock = String.join("\n", lines);
        } else {
            unresolvedBlock = "- （无）";
        }

        // 主体提示词模板：卷级章纲生成 · 爆款节奏版
        String template ="\r\n" + //
                "【角色定位与核心使命】\r\n" + //
                "\r\n" + //
                "你是顶级的爆款网文节奏大师，深谙读者心理，擅长设计强情绪、快反馈、高粘性的剧情钩子。你的文字能让读者欲罢不能，一章上头，熬夜追读。\r\n" + //
                "\r\n" + //
                "核心使命：\r\n" + //
                "接收[小说设定]、[本卷蓝图]、[伏笔池]，你的任务是设计%s章的章纲，其唯一评判标准是“能否最大化提升读者的追读意愿”。为此，你必须做到：\r\n" + //
                "\r\n" + //
                "1. 情绪过山车：将每一章都视为一个独立的情绪产品。你的任务不是“讲故事”，而是“管理情绪”，通过“制造期待 -> 压缩情绪 -> 瞬间释放”的循环，牢牢抓住读者。\r\n" + //
                "2. 钩子为王：每一章的核心价值在于其结尾的“钩子”。你必须在结尾制造一个让读者“不点下一章就百爪挠心”的强力悬念。\r\n" + //
                "3. 爽点前置：读者的耐心极其有限。必须用“小步快跑”的方式，在3章内给予一个明确的爽点反馈，而不是长线铺垫一个大高潮。\r\n" + //
                "\r\n" + //
                "【铁律原则】\r\n" + //
                "\r\n" + //
                "铁律1：拥抱戏剧化，一切为情绪服务\r\n" + //
                "- 原则：网文的本质就是情绪消费品。剧情的“合理性”必须为情绪的“冲击力”让路。你的目标不是创造一个真实的世界，而是创造一个能让读者沉浸其中、体验极致情绪的虚拟世界。\r\n" + //
                "- 执行：大胆使用冲突、反转、误会、巧合等一切戏剧化手法，只要它能有效地调动读者情绪。\r\n" + //
                "\r\n" + //
                "铁律2：拒绝无效铺垫，追求即时反馈\r\n" + //
                "- 原则：任何不能在三章内得到回报的铺垫都是无效铺垫。读者需要的是“即时满足”，而不是“远期支票”。\r\n" + //
                "- 执行：将大的冲突分解成若干个小的“冲突-解决”单元。每个单元都必须快速完成，给读者带来一次小规模的爽点。\r\n" + //
                "\r\n" + //
                "铁律3：反派是工具，其价值在于“被打脸”\r\n" + //
                "- 原则：反派存在的唯一目的，就是为了让主角以最“爽”的方式战胜他们。反派的智商和行为逻辑，都应服务于“如何被主角打脸才能让读者最解气”。\r\n" + //
                "- 执行：在设计反派的行为时，优先考虑“他这样做是否能最大化主角后续反击的爽感”，而不是“他这样做是否绝对符合逻辑”。\r\n" + //
                "\r\n" + //
                "铁律4：钩子是生命线，悬念必须“卡”在痛点\r\n" + //
                "- 原则：章节的结束不是故事的暂停，而是下一章付费的开始。钩子必须精准地“卡”在读者最想知道答案的地方。\r\n" + //
                "- 执行：在每一章结尾，问自己：“读者此刻最关心什么？”然后就在那个问题揭晓的前一刻，戛然而止。\r\n" + //
                        "\r\n" + //
                        "【输入信息】\r\n" + //
                        "\r\n" + //
                        "小说标题：%s\r\n" + //
                        "全书总大纲：%s\r\n" + //
                        "当前卷：第%s卷《%s》\r\n" + //
                        "卷主题：%s\r\n" + //
                        "章节范围：%s\r\n" + //
                        "本卷蓝图：%s\r\n" + //
                        "%s\r\n" + //
                        "历史未回收伏笔：\r\n" + //
                        "%s\r\n" + //
                        "\r\n" + //
                        "【生成流程】\r\n" + //
                        "\r\n" + //
                        "Step 1：卷内“情绪波形图”规划（内部思考）\r\n" + //
                        "- 目标：将本卷%s章的情节，规划成一个“压抑-爆发-再压抑-再爆发”的波浪形态。\r\n" + //
                        "- 规划内容：\r\n" + //
                        "    1. 确立“憋屈点”：规划1-2个核心的压抑情节，让主角或读者感到极度不爽。\r\n" + //
                        "    2. 设计“爆发点”：针对每个“憋屈点”，设计一个高潮情节，让压抑的情绪得到彻底、畅快的释放。\r\n" + //
                        "    3. 规划“连接路径”：设计如何从一个“爆发点”平稳过渡，并自然地引入下一个“憋屈点”，形成循环。\r\n" + //
                        "\r\n" + //
                        "Step 2：逐章设计（修改为“情绪单元”结构）\r\n" + //
                        "每章包含以下要素：\r\n" + //
                        "\r\n" + //
                        "【本章在情绪波形图中的位置】[例如：憋屈点构建/爆发点前夕/爽点释放/新憋屈点引入]\r\n" + //
                        "\r\n" + //
                        "【本章核心任务】（一句话）\r\n" + //
                        "[本章要将读者的情绪引导至何种状态？（例如：愤怒/期待/爽快/紧张）]\r\n" + //
                        "\r\n" + //
                        "【起】冲突前置\r\n" + //
                        "- 原则：直接展示冲突，或抛出引向冲突的直接诱因。\r\n" + //
                        "- 自问：本章最激烈的部分是什么？从那里开始写。\r\n" + //
                        "\r\n" + //
                        "【承】情绪压缩\r\n" + //
                        "- 原则：不断加码，让矛盾激化，让主角面临的压力增大，将读者的期待感或愤怒值拉满。\r\n" + //
                        "\r\n" + //
                        "【转】价值释放\r\n" + //
                        "- 原则：在情绪最高点，让转折发生。这可以是主角的反击（爽点），也可以是危机的降临（钩子）。\r\n" + //
                        "- 核心：让积蓄的情绪有一个出口。\r\n" + //
                        "\r\n" + //
                        "【合】高潮收尾或强力钩子\r\n" + //
                        "- 原则：如果本章是“爆发点”，则停留在读者最爽的瞬间；如果本章是“连接路径”，则必须留下一个指向下一个“憋屈点”或“爆发点”的强力悬念。\r\n" + //
                        "\r\n" + //
                        "【本章伏笔/回收】\r\n" + //
                        "- [埋设]：本章埋下什么新伏笔，预计在第X章回收\r\n" + //
                        "- [回收]：本章回收了第X章埋下的什么伏笔\r\n" + //
                        "\r\n" + //
                        "【本章推进各条线索】\r\n" + //
                        "- 主线：[推进了多少，如：确认了宗门有内鬼]\r\n" + //
                        "- 辅线X：[如：与师姐的关系从怀疑变为合作]\r\n" + //
                        "- 暗线X：[如：玉佩上的纹路与主角身世有关]\r\n" + //
                        "\r\n" + //
                        "【质量自查清单】\r\n" + //
                        "生成每章后必须自问：\r\n" + //
                        "\r\n" + //
                        "【自然性检查】\r\n" + //
                        "- 主角的行为是否符合当前的信息和能力？\r\n" + //
                        "- 配角/反派的反应是否真实？\r\n" + //
                        "- 冲突是否是被迫发生，而非刻意制造？\r\n" + //
                        "\r\n" + //
                        "【节奏检查】\r\n" + //
                        "- 本章是否在前300字给出了明确的\"本章看什么\"？\r\n" + //
                        "- 本章是否塞了太多信息（超过3个核心点）？\r\n" + //
                        "- 本章结尾的钩子是否足够吸引人点下一章？\r\n" + //
                        "\r\n" + //
                        "【逻辑检查】\r\n" + //
                        "- 本章的转折是否有前置铺垫？\r\n" + //
                        "- 本章推进的速度是否合理（是否太快或太慢）？\r\n" + //
                        "- 本章是否与上一章有明确的因果关系？\r\n" + //
                        "\r\n" + //
                        "【功能检查】\r\n" + //
                        "- 本章在卷内的功能是否清晰（铺垫/推进/转折/收尾）？\r\n" + //
                        "- 本章是否推进了至少一条线索？\r\n" + //
                        "- 本章是否为后续章节埋了可用的素材？\r\n" + //
                        "\r\n" + //
                        "【反降智设计准则（极其重要）】\r\n" + //
                        "\r\n" + //
                        "❌ 禁止的降智设计：\r\n" + //
                        "1. 因果颠倒：A对B做了X，却让C承担后果（毫无逻辑）\r\n" + //
                        "2. 动机缺失：角色做出极端行为，但没有合理动机（为了虐而虐）\r\n" + //
                        "3. 工具人化：配角只为推动剧情而存在，行为完全不符合人设\r\n" + //
                        "4. 强行制造矛盾：为了凑冲突，安排不合理的事件\r\n" + //
                        "\r\n" + //
                        "✅ 正确的冲突设计：\r\n" + //
                        "1. 立场冲突：因为立场不同，产生真实的利益/观念冲突\r\n" + //
                        "   公式：[角色A偏心角色B] + [在资源分配时偏向B] → [角色C不满/反击]\r\n" + //
                        "2. 误会冲突：基于信息差的合理误会\r\n" + //
                        "   公式：[角色A误以为C做了X事] + [质问C] → [C冷漠回应/解释/反击]\r\n" + //
                        "3. 价值观冲突：双方价值观不同，产生碰撞\r\n" + //
                        "   公式：[角色A要求C做Y] + [Y违背C的价值观] → [C拒绝/反击]\r\n" + //
                        "\r\n" + //
                        "核心原则：\r\n" + //
                        "- 每个角色的行为必须符合其立场和动机\r\n" + //
                        "- 冲突是立场差异的自然结果，不是为了冲突而冲突\r\n" + //
                        "- 即使是反派，也要有合理的行为逻辑\r\n" + //
                        "\r\n" + //
                        "【人物智商保护】\r\n" + //
                        "- 对父母、长辈等重要配角，除非卷蓝图明确设定为极端反派，否则慎用当众扇耳光、撒泼打骂这类极端行为，可以通过偏心、冷脸、语言打压等方式体现立场。\r\n" + //
                        "- 保护主要角色的人物智商和底线，让读者觉得角色行为虽然过分但在其立场下还能理解，而不是简单把所有人写成工具人。\r\n" + //
                        "\r\n" + //
                        "【结尾风格】\r\n" + //
                        "- SOFT_CLOSE：小矛盾阶段性结果，稳定收束。\r\n" + //
                        "- EMOTIONAL_BEAT：有分量的台词/动作，人物立场/关系发生可感知变化。\r\n" + //
                        "- STRONG_HOOK：关键转折章使用，留下未解决的危机/反转/新情报。\r\n" + //
                        "建议：卷首/卷中转折/卷末用STRONG_HOOK，普通章节用SOFT_CLOSE或EMOTIONAL_BEAT。\r\n" + //
                        "\r\n" + //
                        "【爽点与反击方式建议】\r\n" + //
                        "- 优先通过人物语言、专业能力、局势反转来实现爽点或反击，而不是依赖猎奇画面或纯整蛊桥段。\r\n" + //
                        "- 避免设计恶心血腥、低俗猎奇或过于短视频化/综艺化的行为（例如解剖仇恨礼物、挂侮辱性牌子等），保证人物行为在现实语境下可信。\r\n" + //
                        "\r\n" + //
                        "【direction 字段说明（本章剧情方向）】\r\n" + //
                        "direction 是本章的核心章纲，用一段文字描述本章的剧情方向和关键剧情点。\r\n" + //
                        "格式建议：每个关键剧情点用换行分隔，包含以下内容：\r\n" + //
                        "- [承接]：本章如何承接上章结尾（可选，转折章必须有）\r\n" + //
                        "- [事件]：本章发生的核心事件（1-2个），简洁描述即可\r\n" + //
                        "- [冲突]：本章的主要冲突点（如有）\r\n" + //
                        "- [推进]：本章推进了哪条线索（主线/辅线/暗线）\r\n" + //
                        "- [结果]：本章事件的结果（如有明确结果）\r\n" + //
                        "- [收束]：本章如何结束\r\n" + //
                        "- [钩子]：下章悬念（仅在需要强钩子的章节使用）\r\n" + //
                        "\r\n" + //
                        "【伏笔字段说明】\r\n" + //
                        "- foreshadowAction：枚举值，取值为：NONE / PLANT / RESOLVE。\r\n" + //
                        "- foreshadowDetail：\r\n" + //
                        "  - 当 foreshadowAction = NONE 时，必须为 null。\r\n" + //
                        "  - 当 foreshadowAction = PLANT 时，应包含伏笔的大意，以及计划在哪个章节或哪一卷回收。\r\n" + //
                        "  - 当 foreshadowAction = RESOLVE 时，应指出对应的伏笔来源，以及如何在剧情中被解开。\r\n" + //
                        "\r\n" + //
                        "【JSON 输出规范】\r\n" + //
                        "- 最终只输出一个JSON数组，不要输出任何解释性文字。\r\n" + //
                        "- 数组长度必须严格等于 %s（目标章节数量）。\r\n" + //
                        "- 数组中每个元素表示一章，包含字段：\r\n" + //
                        "  - \"chapterInVolume\"：卷内章节序号（从1开始）。\r\n" + //
                        "  - \"direction\"：字符串，本章剧情方向（章纲），包含关键剧情点，每个剧情点用换行分隔。\r\n" + //
                        "  - \"foreshadowAction\"：\"NONE\" / \"PLANT\" / \"RESOLVE\"。\r\n" + //
                        "  - \"foreshadowDetail\"：根据 foreshadowAction 的要求填写，或为 null。\r\n" + //
                        "\r\n" + //
                        "【JSON 结构示例】（仅作结构参考，可根据实际剧情改写内容）：\r\n" + //
                        "[\r\n" + //
                        "  {\r\n" + //
                        "    \"chapterInVolume\": 1,\r\n" + //
                        "    \"direction\": \"[事件] 主角收到神秘来信，决定前往调查\\n[冲突] 途中遭遇伏击，与敌人展开激战\\n[结果] 主角险胜，但发现更大的阴谋\\n[钩子] 信中提到的神秘人物即将现身\",\r\n" + //
                        "    \"foreshadowAction\": \"PLANT\",\r\n" + //
                        "    \"foreshadowDetail\": {\"content\": \"神秘来信的真正发送者\", \"expectedResolve\": \"第三卷揭晓\"}\r\n" + //
                        "  }\r\n" + //
                        "]\r\n" + //
                        "\r\n" + //
                        "注意：\r\n" + //
                        "- 不要使用Markdown代码块标记（不要输出```json之类的符号）。\r\n" + //
                        "- 不要额外添加未说明的字段（不要添加 keyPlotPoints、emotionalTone、subplot、antagonism 等字段）。\r\n" + //
                        "- 请直接输出JSON数组本身作为最终答案。" ;

        return String.format(
                template,
          // 第1组：核心信息（11个占位符）
        count,                                         // %s - 核心使命中的章节数
        s(novel.getTitle()),                           // %s - 小说标题
        s(limit(superOutline.getPlotStructure(), 8000)), // %s - 全书总大纲
        nz(volume.getVolumeNumber(), "?"),             // %s - 卷号
        s(volume.getTitle()),                          // %s - 卷名
        s(volume.getTheme()),                          // %s - 主题
        chapterRange,                                  // %s - 章节范围
        s(limit(volume.getContentOutline(), 8000)),    // %s - 本卷蓝图
        nextVolumeBlock,                               // %s - 下一卷信息块
        unresolvedBlock,                               // %s - 伏笔池
        count,                                         // %s - Step 1中的章节数
        // 第2组：章节数量（1个占位符）
        count                                          // %s - JSON输出说明
        );
    }

    private String buildDecisionLog(Novel novel, NovelVolume volume, NovelOutline outline,
                                    List<NovelForeshadowing> unresolved, String prompt, String raw, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("[react_decision_log]\n");
        sb.append("route: volume_chapter_outlines_generation\n");
        sb.append("time: ").append(LocalDateTime.now()).append('\n');
        sb.append("msg1: <<<PROMPT>>>\n");
        sb.append(prompt).append('\n');
        sb.append("<<<END_PROMPT>>>\n");
        sb.append("msg2: novelId=").append(novel.getId())
          .append(", title=").append(s(novel.getTitle()))
          .append(", volumeId=").append(volume.getId())
          .append(", volumeNo=").append(nz(volume.getVolumeNumber(), 0))
          .append(", targetCount=").append(count).append('\n');
        sb.append("msg3: volume.contentOutline.len=")
          .append(length(volume.getContentOutline())).append(", outline.len=")
          .append(length(outline.getPlotStructure())).append('\n');
        sb.append("msg4: unresolvedForeshadows.size=")
          .append(unresolved == null ? 0 : unresolved.size()).append('\n');
        sb.append("msg5: <<<RAW_RESPONSE>>>\n").append(limit(raw, 2000)).append('\n');
        sb.append("<<<END_RAW_RESPONSE>>>\n");
        return sb.toString();
    }

    private String extractPureJson(String raw) {
        if (raw == null) throw new RuntimeException("AI返回为空");
        String trimmed = raw.trim();
        
        // 优先提取```json ... ```
        int fence = indexOfIgnoreCase(trimmed, "```json");
        if (fence != -1) {
            int end = trimmed.indexOf("```", fence + 7);
            if (end != -1) {
                trimmed = trimmed.substring(fence + 7, end).trim();
            } else {
                logger.warn("⚠️ 发现```json但未找到结束标记，可能被截断");
                // 帮助排查：打印本次原始返回全文
                logger.error("📥 RAW内容（缺少```结束标记，可能截断）:\n{}", raw);
                trimmed = trimmed.substring(fence + 7).trim();
            }
        }
        
        // 清理Markdown格式标记（**粗体**、__斜体__等）
        trimmed = cleanMarkdownFormatting(trimmed);
        
        // 再尝试找到第一个'['到匹配的']'
        int start = trimmed.indexOf('[');
        if (start != -1) {
            int depth = 0; boolean inString = false; char prev = 0;
            
            for (int i = start; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '"' && prev != '\\') inString = !inString;
                if (!inString) {
                    if (c == '[') depth++;
                    else if (c == ']') { 
                        depth--; 
                        if (depth == 0) { 
                            return trimmed.substring(start, i + 1); 
                        } 
                    }
                }
                prev = c;
            }
            
            // 如果循环结束depth > 0，说明JSON不完整（被截断）
            if (depth > 0) {
                logger.error("❌ JSON数组未正确闭合，depth={}, 可能被截断！原文长度: {}", depth, raw.length());
                logger.error("最后500字符: {}", raw.substring(Math.max(0, raw.length() - 500)));
                // 额外打印完整RAW，方便后续分析
                logger.error("📥 RAW内容（JSON未闭合，可能截断）:\n{}", raw);
                throw new RuntimeException("JSON数组不完整（未找到匹配的']'），可能是AI返回被截断，原文长度:" + raw.length());
            }
        }
        
        logger.warn("未找到JSON数组起始符'['，返回原文前800字符");
        return trimmed.substring(0, Math.min(800, trimmed.length()));
    }
    
    /**
     * 清理Markdown格式标记，避免干扰JSON解析
     * 去除 **粗体**、__斜体__、*斜体*、~~删除线~~ 等
     */
    private String cleanMarkdownFormatting(String text) {
        if (text == null) return null;
        
        // 在JSON字符串外部的markdown标记才清理
        // 简单策略：在JSON提取前先做全局清理
        return text
            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")  // **粗体** -> 粗体
            .replaceAll("__([^_]+)__", "$1")        // __粗体__ -> 粗体
            .replaceAll("(?<!\\*)\\*(?!\\*)([^*]+?)\\*(?!\\*)", "$1")  // *斜体* -> 斜体（避免匹配**）
            .replaceAll("~~([^~]+)~~", "$1");       // ~~删除线~~ -> 删除线
    }

    private int indexOfIgnoreCase(String s, String sub) {
        return s.toLowerCase(Locale.ROOT).indexOf(sub.toLowerCase(Locale.ROOT));
    }

    /**
     * 清理JSON中的非标准引号
     * 策略：智能识别JSON字符串内部的中文引号并转义
     */
    private String cleanJsonQuotes(String json) {
        if (json == null) return null;
        
        StringBuilder result = new StringBuilder(json.length() + 100);
        boolean inString = false;  // 是否在JSON字符串内部
        char prevChar = 0;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            // 检测标准双引号，判断是否进入/退出字符串
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
                result.append(c);
            }
            // 处理中文双引号
            else if (c == '\u201C' || c == '\u201D') {  // " "
                if (inString) {
                    // 在JSON字符串内部，需要转义
                    result.append("\\\"");
                } else {
                    // 不在字符串内部，可能是JSON结构的一部分（不应该出现，但容错处理）
                    result.append('"');
                }
            }
            // 处理全角引号
            else if (c == '\uFF02') {  // ＂
                if (inString) {
                    result.append("\\\"");
                } else {
                    result.append('"');
                }
            }
            // 处理中文单引号 - 保持原样或替换为普通单引号
            else if (c == '\u2018' || c == '\u2019') {  // ' '
                result.append('\'');
            }
            // 其他字符直接添加
            else {
                result.append(c);
            }
            
            prevChar = c;
        }
        
        return result.toString();
    }

    /**
     * 入库：保存章纲 + 伏笔生命周期日志
     * 失败时抛异常，触发事务回滚（旧数据会恢复）
     */
    private void persistOutlines(NovelVolume volume, List<Map<String, Object>> outlines, String reactDecisionLog) {
        if (outlines == null || outlines.isEmpty()) {
            throw new RuntimeException("章纲列表为空，无法入库");
        }

        // 覆盖式写入：先清空该卷旧章纲和伏笔日志，再插入新结果
        // 注意：因为有 @Transactional，如果后续插入失败，删除操作会回滚
        int deletedOutlines = outlineRepo.deleteByVolumeId(volume.getId());
        int deletedLogs = lifecycleLogRepo.deleteByVolumeId(volume.getId());
        logger.info("🧹 已清空旧数据：volumeId={}, 章纲{}条, 伏笔日志{}条",
                volume.getId(), deletedOutlines, deletedLogs);

        int insertedCount = 0;
        int index = 0;
        for (Map<String, Object> outline : outlines) {
            try {
                VolumeChapterOutline entity = new VolumeChapterOutline();
                entity.setNovelId(volume.getNovelId());
                entity.setVolumeId(volume.getId());
                entity.setVolumeNumber(volume.getVolumeNumber());

                // 统一在服务端计算章节编号，不再依赖AI返回
                int chapterInVolume = index + 1;
                Integer globalChapterNumber = null;
                if (volume.getChapterStart() != null) {
                    globalChapterNumber = volume.getChapterStart() + chapterInVolume - 1;
                }

                entity.setChapterInVolume(chapterInVolume);
                entity.setGlobalChapterNumber(globalChapterNumber);
                entity.setDirection(getString(outline, "direction"));
                entity.setKeyPlotPoints(null);  // 已废弃，不再使用
                entity.setEmotionalTone(null);  // 已废弃，不再使用
                entity.setForeshadowAction(getString(outline, "foreshadowAction"));
                entity.setForeshadowDetail(toJson(outline.get("foreshadowDetail")));
                entity.setSubplot(null);  // 已废弃，不再使用
                entity.setAntagonism(null);  // 已废弃，不再使用
                entity.setStatus("PENDING");
                entity.setReactDecisionLog(reactDecisionLog);

                outlineRepo.insert(entity);
                insertedCount++;

                logger.debug("✓ 章纲入库成功: 卷内第{}章, 全书第{}章", chapterInVolume, globalChapterNumber);

                // 若有伏笔动作，写入生命周期日志
                String action = entity.getForeshadowAction();
                if (action != null && !action.equals("NONE") && entity.getForeshadowDetail() != null) {
                    try {
                        Map<String, Object> detail = mapper.readValue(entity.getForeshadowDetail(), new TypeReference<Map<String, Object>>(){});
                        Long foreshadowId = getLong(detail, "refId");
                        if (foreshadowId == null && action.equals("PLANT")) {
                            // PLANT 时可能还没有 refId，暂时跳过或创建新伏笔
                            // 这里简化处理：只记录已有 refId 的
                        } else if (foreshadowId != null) {
                            ForeshadowLifecycleLog log = new ForeshadowLifecycleLog();
                            log.setForeshadowId(foreshadowId);
                            log.setNovelId(volume.getNovelId());
                            log.setVolumeId(volume.getId());
                            log.setVolumeNumber(volume.getVolumeNumber());
                            log.setChapterInVolume(entity.getChapterInVolume());
                            log.setGlobalChapterNumber(entity.getGlobalChapterNumber());
                            log.setAction(action);
                            log.setDetail(entity.getForeshadowDetail());
                            lifecycleLogRepo.insert(log);
                        }
                    } catch (Exception e) {
                        logger.warn("⚠️ 解析伏笔详情失败，跳过生命周期日志: {}", e.getMessage());
                    }
                }

            } catch (Exception e) {
                logger.error("❌ 章纲入库失败: chapterInVolumeIndex={}, 错误: {}", index + 1, e.getMessage());
                throw new RuntimeException("章纲入库失败（第" + (insertedCount + 1) + "条）: " + e.getMessage(), e);
            }

            index++;
        }

        logger.info("✅ 成功插入{}条章纲记录", insertedCount);
    }

    /**
     * 入库：仅更新本卷中尚未写正文部分的章纲
     * 不清空整卷旧数据，只对指定起始章节之后的章纲进行插入/更新，并追加伏笔生命周期日志
     */
    private void persistRemainingOutlines(NovelVolume volume,
                                          int firstNewChapterInVolume,
                                          List<Map<String, Object>> outlines,
                                          String reactDecisionLog) {
        if (outlines == null || outlines.isEmpty()) {
            throw new RuntimeException("章纲列表为空，无法入库");
        }

        List<VolumeChapterOutline> existing = outlineRepo.findByVolumeId(volume.getId());
        Map<Integer, VolumeChapterOutline> existingByChapter = new HashMap<>();
        if (existing != null) {
            for (VolumeChapterOutline e : existing) {
                if (e.getChapterInVolume() != null) {
                    existingByChapter.put(e.getChapterInVolume(), e);
                }
            }
        }

        int insertedOrUpdated = 0;
        int index = 0;
        for (Map<String, Object> outline : outlines) {
            try {
                int chapterInVolume = firstNewChapterInVolume + index;
                Integer globalChapterNumber = null;
                if (volume.getChapterStart() != null) {
                    globalChapterNumber = volume.getChapterStart() + chapterInVolume - 1;
                }

                VolumeChapterOutline entity = existingByChapter.get(chapterInVolume);
                if (entity == null) {
                    entity = new VolumeChapterOutline();
                    entity.setNovelId(volume.getNovelId());
                    entity.setVolumeId(volume.getId());
                    entity.setVolumeNumber(volume.getVolumeNumber());
                    entity.setChapterInVolume(chapterInVolume);
                }

                entity.setGlobalChapterNumber(globalChapterNumber);
                entity.setDirection(getString(outline, "direction"));
                entity.setKeyPlotPoints(null);  // 已废弃，不再使用
                entity.setEmotionalTone(null);  // 已废弃，不再使用
                entity.setForeshadowAction(getString(outline, "foreshadowAction"));
                entity.setForeshadowDetail(toJson(outline.get("foreshadowDetail")));
                entity.setSubplot(null);  // 已废弃，不再使用
                entity.setAntagonism(null);  // 已废弃，不再使用
                entity.setStatus("PENDING");
                entity.setReactDecisionLog(reactDecisionLog);

                if (entity.getId() == null) {
                    outlineRepo.insert(entity);
                } else {
                    outlineRepo.updateById(entity);
                }
                insertedOrUpdated++;

                logger.debug("✓ 增量章纲入库成功: 卷内第{}章, 全书第{}章", chapterInVolume, globalChapterNumber);

                // 若有伏笔动作，写入生命周期日志
                String action = entity.getForeshadowAction();
                if (action != null && !action.equals("NONE") && entity.getForeshadowDetail() != null) {
                    try {
                        Map<String, Object> detail = mapper.readValue(entity.getForeshadowDetail(), new TypeReference<Map<String, Object>>(){});
                        Long foreshadowId = getLong(detail, "refId");
                        if (foreshadowId == null && action.equals("PLANT")) {
                            // PLANT 时可能还没有 refId，暂时跳过或创建新伏笔
                        } else if (foreshadowId != null) {
                            ForeshadowLifecycleLog log = new ForeshadowLifecycleLog();
                            log.setForeshadowId(foreshadowId);
                            log.setNovelId(volume.getNovelId());
                            log.setVolumeId(volume.getId());
                            log.setVolumeNumber(volume.getVolumeNumber());
                            log.setChapterInVolume(entity.getChapterInVolume());
                            log.setGlobalChapterNumber(entity.getGlobalChapterNumber());
                            log.setAction(action);
                            log.setDetail(entity.getForeshadowDetail());
                            lifecycleLogRepo.insert(log);
                        }
                    } catch (Exception e) {
                        logger.warn("⚠️ 解析伏笔详情失败，跳过生命周期日志（增量）: {}", e.getMessage());
                    }
                }

            } catch (Exception e) {
                logger.error("❌ 增量章纲入库失败: startChapterInVolume={}, index={}, 错误: {}",
                    firstNewChapterInVolume, index, e.getMessage());
                throw new RuntimeException("章纲入库失败（增量，第" + (index + 1) + "条）: " + e.getMessage(), e);
            }

            index++;
        }

        logger.info("✅ 成功增量插入/更新{}条章纲记录（从卷内第{}章起）", insertedOrUpdated, firstNewChapterInVolume);
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;

        // 统一收敛为合法 JSON 文本，避免向 MySQL JSON 列写入非法字符串
        if (obj instanceof String) {
            String s0 = (String) obj;
            if (isBlank(s0)) return null;
            try {
                // 一律作为普通文本编码为 JSON 字符串
                return mapper.writeValueAsString(s0);
            } catch (Exception e) {
                return null;
            }
        }

        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String s(String v) { return v == null ? "" : v; }
    private static int length(String v) { return v == null ? 0 : v.length(); }
    private static String nz(Object v, Object def) { return String.valueOf(v == null ? def : v); }
    private static String limit(String v, int max) { if (v == null) return ""; return v.length() > max ? v.substring(0, max) + "..." : v; }
}
