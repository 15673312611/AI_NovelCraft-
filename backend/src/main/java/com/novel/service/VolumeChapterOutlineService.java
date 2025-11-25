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
            count = computed > 0 ? computed : 50;
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

        //章纲提示词
        String basePrompt = buildPrompt(novel, volume, nextVolume, superOutline, unresolved, count);
        String prompt = basePrompt;

        if (volume.getChapterStart() != null && volume.getChapterEnd() != null) {
            try {
                List<Chapter> chapters = chapterRepository.findByNovelIdAndChapterNumberBetween(
                        volume.getNovelId(),
                        volume.getChapterStart(),
                        volume.getChapterEnd()
                );
                if (chapters != null && !chapters.isEmpty()) {
                    List<Chapter> chaptersWithContent = new ArrayList<>();
                    for (Chapter chapter : chapters) {
                        if (chapter.getContent() != null && !chapter.getContent().trim().isEmpty()) {
                            chaptersWithContent.add(chapter);
                        }
                    }
                    if (!chaptersWithContent.isEmpty()) {
                        StringBuilder promptBuilder = new StringBuilder(basePrompt);
                        promptBuilder.append("\n\n");
                        promptBuilder.append("# 已写章节正文与进度\n");
                        promptBuilder.append("下面是本卷中已经有正文的章节。请你先根据这些正文推导出它们对应的章纲，并将这些章纲与正文严格对齐；然后在此基础上，为整卷生成可以自然承接这些章节的完整章纲序列（共")
                                .append(count).append("章）。\n\n");
                        for (Chapter chapter : chaptersWithContent) {
                            Integer chapterNumber = chapter.getChapterNumber();
                            Integer chapterInVolume = null;
                            if (volume.getChapterStart() != null) {
                                chapterInVolume = chapterNumber - volume.getChapterStart() + 1;
                            }
                            promptBuilder.append("## 已写章节\n");
                            promptBuilder.append("【全局章节号】").append(chapterNumber).append("\n");
                            if (chapterInVolume != null && chapterInVolume > 0) {
                                promptBuilder.append("【卷内章节号】").append(chapterInVolume).append("\n");
                            }
                            promptBuilder.append("【章节标题】").append(s(chapter.getTitle())).append("\n");
                            String chapterContent = chapter.getContent();
                            if (chapterContent != null && chapterContent.length() > 2000) {
                                chapterContent = chapterContent.substring(0, 2000) + "...";
                            }
                            promptBuilder.append("【正文节选】\n");
                            promptBuilder.append(chapterContent == null ? "" : chapterContent).append("\n\n");
                        }
                        promptBuilder.append("请特别注意：\n");
                        promptBuilder.append("- 对于已写正文的章节，你生成的章纲必须与上面的正文保持一致，只能在不改变关键事件和情绪走向的前提下做轻微调整；\n");
                        promptBuilder.append("- 对于尚未写正文的章节，章纲需要在节奏、因果和伏笔上自然承接这些已写章节，而不是重新假定另一条时间线。\n");
                        prompt = promptBuilder.toString();
                    }
                }
            } catch (Exception e) {
                logger.error("构建已写章节正文上下文失败: volumeId={}", volumeId, e);
            }
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
            logger.error("❌ 解析卷章纲失败: {}\n清理后JSON(前500)：{}", e.getMessage(), json.substring(0, Math.min(500, json.length())));
            throw new RuntimeException("解析卷章纲失败，请检查AI返回格式: " + e.getMessage());
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
        int remainingChapters = totalChaptersInVolume - writtenCountInVolume;
        if (remainingChapters <= 0) {
            throw new RuntimeException("本卷章节正文已经全部写完或未剩余空白章节，无需生成新的章纲");
        }

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

        // 构建提示词
        String basePrompt = buildPrompt(novel, volume, nextVolume, superOutline, unresolved, count);
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
        entity.setKeyPlotPoints(toJson(outline.get("keyPlotPoints")));
        entity.setEmotionalTone(getString(outline, "emotionalTone"));
        entity.setForeshadowAction(getString(outline, "foreshadowAction"));
        entity.setForeshadowDetail(toJson(outline.get("foreshadowDetail")));
        entity.setSubplot(getString(outline, "subplot"));
        entity.setAntagonism(toJson(outline.get("antagonism")));
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

    private String buildPrompt(Novel novel, NovelVolume volume, NovelVolume nextVolume, NovelOutline superOutline,
                               List<NovelForeshadowing> unresolved, int count) {
        StringBuilder sb = new StringBuilder();

        sb.append("# 任务\n")
          .append("为本卷规划").append(count).append("章章纲。每章必须有明确的爽点/冲突/钩子，节奏紧凑一环扣一环，让读者停不下来。\n")
          .append("章纲只负责【发生什么+为什么爽+如何承接】，不写具体对话或场景描写。\n\n");

        sb.append("# 小说信息\n")
          .append("- 标题：").append(s(novel.getTitle())).append("\n")
          .append("- 简介/构思：").append(s(novel.getDescription())).append("\n\n");

        sb.append("# 全书大纲\n").append(s(limit(superOutline.getPlotStructure(), 12000))).append("\n\n");

        sb.append("# 本卷信息\n")
          .append("- 卷序：第").append(nz(volume.getVolumeNumber(), "?")).append("卷\n")
          .append("- 卷名：").append(s(volume.getTitle())).append("\n")
          .append("- 主题：").append(s(volume.getTheme())).append("\n")
          .append("- 卷蓝图（contentOutline）：\n").append(s(limit(volume.getContentOutline(), 8000))).append("\n")
          .append("- 章节范围：")
          .append(volume.getChapterStart() != null && volume.getChapterEnd() != null
                    ? ("第" + volume.getChapterStart() + "-" + volume.getChapterEnd() + "章") : "未指定")
          .append("\n\n");

        if (nextVolume != null) {
            sb.append("# 下一卷信息（供节奏规划参考）\n")
              .append("- 下一卷序：第").append(nz(nextVolume.getVolumeNumber(), "?")).append("卷\n")
              .append("- 下一卷卷名：").append(s(nextVolume.getTitle())).append("\n")
              .append("- 下一卷主题：").append(s(nextVolume.getTheme())).append("\n")
              .append("- 下一卷蓝图（contentOutline）：\n").append(s(limit(nextVolume.getContentOutline(), 4000))).append("\n")
              .append("- 下一卷章节范围：")
              .append(nextVolume.getChapterStart() != null && nextVolume.getChapterEnd() != null
                        ? ("第" + nextVolume.getChapterStart() + "-" + nextVolume.getChapterEnd() + "章") : "未指定")
              .append("\n\n");
        }

        sb.append("# 历史未回收伏笔池（供决策）\n");
        if (unresolved != null && !unresolved.isEmpty()) {
            int shown = 0;
            for (NovelForeshadowing f : unresolved) {
                if (shown++ >= 30) break; // 控制长度
                sb.append("- [#").append(f.getId()).append("] 优先级").append(nz(f.getPriority(), 0))
                  .append(" | 植入章节=").append(nz(f.getPlantedChapter(), 0))
                  .append(" | 内容：").append(s(limit(f.getContent(), 200))).append("\n");
            }
        } else {
            sb.append("- （无）\n");
        }
        sb.append("\n");

        sb.append("# 核心原则（必须遵守）\n")
          .append("1. **爽点密度**：每章至少1个爽点（打脸/逆袭/获得/碾压）或痛点（损失/危机/被压制），不能是纯过场章。\n")
          .append("2. **紧凑节奏**：同一冲突不要一次解决，用\"解决一半+暴露新问题\"的方式一环扣一环，让读者必须看下去。\n")
          .append("3. **章末钩子**：每章结尾必须留悬念（未解决的危机/必须做的选择/突然出现的威胁），不能平淡收尾。\n")
          .append("4. **逻辑自洽**：人物行动基于已知信息，能力前后一致，对手不降智，因果链完整（触发→行动→结果→后果）。\n")
          .append("5. **人物驱动**：事件由人物欲望/恐惧/立场推动，不是巧合或上帝视角安排。\n")
          .append("6. **开篇吸引**：前3章必须快速切入冲突，让主角面临不可逆的选择和代价，不能慢热铺垫。\n\n");

        sb.append("# 节奏设计\n")
          .append("- 总数：恰好").append(count).append("章。\n")
          .append("- 起伏波动：高压推进章、短暂缓冲章、翻盘/崩塌章交替出现，避免匀速。冲突分多轮起落，不一次性解决。\n")
          .append("- 蓄力爆发：蓝图内容如果过于概括，主动拆分为铺垫→拉扯→兑现，防止空洞。\n\n")
          .append("# 伏笔管理\n")
          .append("- 动作类型：PLANT(埋新)、REFERENCE(提醒)、DEEPEN(加深)、RESOLVE(回收)。\n")
          .append("- 控制密度：已有大量未回收伏笔时，少用PLANT，多用REFERENCE/DEEPEN；只在铺垫充足时才RESOLVE。\n")
          .append("- 回收窗口：新埋长期伏笔时，在foreshadowDetail中注明大致回收区间，避免一卷内全解决。\n\n");

        sb.append("# 输出格式（严格JSON数组，不含任何多余文本）\n")
          .append("数组长度必须为").append(count).append("。每个元素是一个对象，字段如下：\n")
          .append("- chapterInVolume: number（1..N）\n")
          .append("- globalChapterNumber: number|null（若已知卷起始章节则给出全局章节号，否则null）\n")
          .append("- direction: string（本章剧情方向，用简短语句概括本章的主要推进）\n")
          .append("- keyPlotPoints: string[]（3-6条，按顺序概括本章关键事件或抉择，每条一句话，不写具体文案）\n")
          .append("- emotionalTone: string（用少数词语概括本章整体情绪氛围）\n")
          .append("- foreshadowAction: string（NONE|PLANT|REFERENCE|DEEPEN|RESOLVE）\n")
          .append("- foreshadowDetail: object|null（{refId?:number, content?:string, targetResolveVolume?:number, resolveWindow?:{min?:number,max?:number}, anchorsUsed?:Array<{vol?:number, ch?:number, hint:string}>, futureAnchorPlan?:string, cost?:string}）\n")
          .append("  - 当 foreshadowAction=RESOLVE 时：应优先提供 anchorsUsed，且不少于2个清晰可识别的前文锚点；若难以满足，请自动降级为 DEEPEN。\n")
          .append("  - 当 foreshadowAction=PLANT 或 DEEPEN 时：可在 futureAnchorPlan 中简要描述后续将如何逐步增加锚点或制造记忆点。\n")
          .append("- subplot: string（可选，用一两句话说明本章若涉及的支线或人物刻画要点）\n")
          .append("- antagonism: object（可选，对手/阻力与赌注，如{opponent:string, stakes:string}）\n\n")
          .append("只输出一个纯净的JSON数组，不要markdown，不要代码块，不要解释。\n\n");

        Integer start = volume.getChapterStart();
        if (start != null) {
            sb.append("# 章节编号提示\n")
              .append("- 若给出globalChapterNumber：第一个章节应为").append(start)
              .append("，之后依次+1；否则用null。\n\n");
        }

        sb.append("现在开始生成：请直接输出JSON数组。\n");
        return sb.toString();
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
                trimmed = trimmed.substring(fence + 7).trim();
            }
        }
        // 再尝试找到第一个'['到匹配的']'
        int start = trimmed.indexOf('[');
        if (start != -1) {
            int depth = 0; boolean inString = false; char prev = 0;
            for (int i = start; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '"' && prev != '\\') inString = !inString;
                if (!inString) {
                    if (c == '[') depth++;
                    else if (c == ']') { depth--; if (depth == 0) { return trimmed.substring(start, i + 1); } }
                }
                prev = c;
            }
        }
        logger.warn("未找到JSON数组，返回原文前800字符");
        return trimmed.substring(0, Math.min(800, trimmed.length()));
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
        for (Map<String, Object> outline : outlines) {
            try {
                VolumeChapterOutline entity = new VolumeChapterOutline();
                entity.setNovelId(volume.getNovelId());
                entity.setVolumeId(volume.getId());
                entity.setVolumeNumber(volume.getVolumeNumber());

                Integer chapterInVolume = getInt(outline, "chapterInVolume");
                Integer globalChapterNumber = getInt(outline, "globalChapterNumber");

                // 验证必填字段
                if (chapterInVolume == null) {
                    logger.error("❌ 章纲缺少必填字段 chapterInVolume: {}", outline);
                    throw new RuntimeException("章纲缺少必填字段 chapterInVolume");
                }

                entity.setChapterInVolume(chapterInVolume);
                entity.setGlobalChapterNumber(globalChapterNumber);
                entity.setDirection(getString(outline, "direction"));
                entity.setKeyPlotPoints(toJson(outline.get("keyPlotPoints")));
                entity.setEmotionalTone(getString(outline, "emotionalTone"));
                entity.setForeshadowAction(getString(outline, "foreshadowAction"));
                entity.setForeshadowDetail(toJson(outline.get("foreshadowDetail")));
                entity.setSubplot(getString(outline, "subplot"));
                entity.setAntagonism(toJson(outline.get("antagonism")));
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
                logger.error("❌ 章纲入库失败: chapterInVolume={}, 错误: {}",
                    getInt(outline, "chapterInVolume"), e.getMessage());
                throw new RuntimeException("章纲入库失败（第" + (insertedCount + 1) + "条）: " + e.getMessage(), e);
            }
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
                entity.setKeyPlotPoints(toJson(outline.get("keyPlotPoints")));
                entity.setEmotionalTone(getString(outline, "emotionalTone"));
                entity.setForeshadowAction(getString(outline, "foreshadowAction"));
                entity.setForeshadowDetail(toJson(outline.get("foreshadowDetail")));
                entity.setSubplot(getString(outline, "subplot"));
                entity.setAntagonism(toJson(outline.get("antagonism")));
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
        if (obj instanceof String) return (String) obj;
        try { return mapper.writeValueAsString(obj); } catch (Exception e) { return null; }
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String s(String v) { return v == null ? "" : v; }
    private static int length(String v) { return v == null ? 0 : v.length(); }
    private static String nz(Object v, Object def) { return String.valueOf(v == null ? def : v); }
    private static String limit(String v, int max) { if (v == null) return ""; return v.length() > max ? v.substring(0, max) + "..." : v; }
}
