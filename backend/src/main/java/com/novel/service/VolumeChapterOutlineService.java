package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        // 构建提示词
        String basePrompt = buildPrompt(novel, volume, nextVolume, superOutline, unresolved, count);
        StringBuilder promptBuilder = new StringBuilder(basePrompt);

        if (!isBlank(userRequirements)) {
            promptBuilder.append("\n# 作者需求与偏好（本次仅影响尚未写正文的章节）\n");
            promptBuilder.append("下面是作者针对后续章节给出的额外要求，请在保持逻辑自洽的前提下尽量满足：\n");
            promptBuilder.append(userRequirements.trim()).append("\n");
            promptBuilder.append("当这些需求与现有大纲略有冲突时，请优先保证节奏爽感、一环扣一环的推进与强钩子，再对细节做温和调整，而不是完全推翻前文。\n\n");
        }

        if (!chaptersWithContent.isEmpty()) {
            promptBuilder.append("\n# 已写章节进度概览（只读，不可重写）\n");
            promptBuilder.append("下面是本卷中已经有正文的章节的进度概览（只包含章节号和标题，不包含正文内容）。它们的走向已经固定，你只需要在此基础上，为之后尚未写正文的章节规划新的章纲：\n\n");
            for (Chapter chapter : chaptersWithContent) {
                Integer chapterNumber = chapter.getChapterNumber();
                Integer chapterInVolume = null;
                if (chapterNumber != null) {
                    chapterInVolume = chapterNumber - start + 1;
                }
                promptBuilder.append("## 已写章节\n");
                if (chapterNumber != null) {
                    promptBuilder.append("【全局章节号】").append(chapterNumber).append("\n");
                }
                if (chapterInVolume != null && chapterInVolume > 0) {
                    promptBuilder.append("【卷内章节号】").append(chapterInVolume).append("\n");
                }
                promptBuilder.append("【章节标题】").append(s(chapter.getTitle())).append("\n\n");
            }
            int lastFixed = writtenCountInVolume;
            int firstNew = firstNewChapterInVolume;
            int lastNew = firstNewChapterInVolume + count - 1;
            promptBuilder.append("请特别注意：\n");
            promptBuilder.append("- 卷内第1-").append(lastFixed).append("章已经有正文与既定走向，你不要重新设计或推翻，只能在后续章纲中自然承接这些章节留下的局面与伏笔；\n");
            promptBuilder.append("- 本次只为【卷内第").append(firstNew).append("章到第").append(lastNew).append("章】生成章纲；\n");
            promptBuilder.append("- 每一章都要在目标推进、冲突升级或爽点兑现上给读者明确的反馈，避免纯过场；\n");
            promptBuilder.append("- 每一章结尾都要留下尚未解决的问题、危机或强烈情绪钩子，让读者强烈想看下一章。\n");
        } else {
            promptBuilder.append("\n# 当前进度\n");
            promptBuilder.append("本卷暂时还没有已写正文，本次任务等价于从第1章开始为后续").append(count).append("章规划章纲。\n");
        }

        promptBuilder.append("\n# 本次任务的输出范围\n");
        promptBuilder.append("- 你需要输出一个长度恰好为").append(count).append("的JSON数组，表示从当前进度之后连续的后续章节；\n");
        promptBuilder.append("- 按数组顺序规划剧情：数组第1个元素对应当前进度之后的第一章，数组第2个对应第二章，以此类推；\n");
        promptBuilder.append("- 你可以让 chapterInVolume 字段从1顺序编号，系统会按数组下标自动映射到真实的卷内章节号和全局章节号。\n");

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

        sb.append("# 角色\n")
          .append("你是一名长期观察各类畅销作品数据的网文总策划兼金牌编辑。你不预设具体题材，也不套用单一类型的固定公式，只根据【全书大纲】和【本卷蓝图】来判断何时该提速、何时该蓄势。\n");
        sb.append("你的任务是：为当前这一卷一次性规划出").append(count)
          .append("个章节的【章纲】。章纲只负责说明每章要发生什么、为什么重要，以及大致情绪走向，不负责具体场景、对话或文风设计，这些交给后续写作AI自由发挥。\n\n");

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

        sb.append("# 剧情评估与校正原则\n")
          .append("- 先评估本卷蓝图与全书大纲中每个重大节点的因果链，若发现夸张、跳跃或缺少动机与铺垫的片段，应主动设计补偿性的铺垫、过渡或代价，确保剧情可信。\n")
          .append("- 当输入内容存在明显的不合理指令（例如角色突然拥有未曾交代的力量、无因转折等），不要照抄；请在章纲里通过补充证据、延迟兑现或改写动机的方式让其变得合理，再继续推进。\n")
          .append("- 若蓝图把大块剧情匆匆带过，但卷的目标章节数仍较多，你需要拆解这些大块剧情为多个章纲：前期铺垫、中段拉扯、后段兑现，避免“一章解决一卷冲突”。\n")
          .append("- 反之，如蓝图细碎但总章节数有限，则可以合并或并行推进支线，但仍需保持“触发→行动→后果”的因果闭环。\n")
          .append("- 始终保证人物的抉择是推进剧情的原因，而不是被动等待命运安排；必要时给出他们做出选择的心理或外部压力来源。\n\n");

        sb.append("# 章纲生成目标\n")
          .append("- 数量：恰好").append(count).append("章（不可多也不可少）。\n")
          .append("- 黄金三章：本卷最前面的若干章（至少前三个章纲）必须承担“拉读者入坑”的职责：从打破平静或打破惯性的位置切入，而不是平铺日常介绍；让主角在早期就面对清晰的欲望或目标，并被迫做出难以轻易撤回的选择；这些选择要带来实际代价或风险（例如失去某种资源、关系矛盾被抬高、局势明显恶化等）；每一章结尾都要留下尚未解决的问题、危险或情绪张力，形成继续阅读的动力。在不破坏全书大方向的前提下，黄金三章可以适度偏离原始规划，以换取更强的吸引力，后续章节再逐步校正走向。\n")
          .append("- 节奏波浪：整卷必须存在明显的起伏，而不是匀速推进。要有高压推进的章节，也要有短暂缓冲或蓄势的章节，还要有阶段性的翻盘/崩盘节点；同一条主线可以经历多轮起落，而不是一次性解决。在每章的 direction 和 keyPlotPoints 中，用自然语言体现这一章大致处于“加压推进”“短暂缓和”还是“阶段翻盘/崩塌”，但不要输出专门的标签或编号。若发现整卷被蓝图草率概述，应主动拆分节奏层次，防止“快速略写”导致内容空心。\n")
          .append("- 人物与动机：每一章的关键事件尽量由人物的欲望、恐惧或立场推动，而不是纯粹的外部巧合。章纲里要点出人物在本章“想要什么/害怕什么”，以便后续写作时围绕人物驱动剧情。\n")
          .append("- 反直线发展：在不牺牲逻辑自洽的前提下，优先考虑比“最直接解法”略微出乎意料的推进方式，如绕行、延迟、误判后反噬等，但不要为了“反转而反转”。\n")
          .append("- 适配任意题材：不要假定具体世界观或题材，只基于输入的大纲和蓝图来判断冲突强度与节奏位置，使设计对任何题材都成立。\n")
          .append("- 世界与知识边界：不得让角色掌握其不应知道的信息，不得临时创造改变世界规则走向的关键设定。存在不确定性时，更倾向于通过PLANT/DEEPEN埋伏笔或加深，而不是直接RESOLVE完全解释。\n")
          .append("- 伏笔管理：允许PLANT(埋)、REFERENCE(提及/提醒)、DEEPEN(加深/升级)、RESOLVE(回收)四类动作。若本卷已存在大量未回收伏笔，应收敛新增PLANT，多用REFERENCE/DEEPEN；只有当剧情节点成熟、证据和铺垫充足时才考虑RESOLVE。\n")
          .append("  - 新埋长期伏笔时，请在 foreshadowDetail 中给出大致回收窗口（如最早/最晚大致卷或章节区间），避免在一卷内全部解决。\n")
          .append("- 角色命名规则：若在【小说信息】【全书大纲】【本卷信息】中已经出现了明确的人名（主角、重要配角等），本卷章纲中继续使用这些姓名，不得为同一角色改名或另起新名；对于仅以关系/身份存在而未命名的角色（如“继母”“父亲”等），章纲中只使用这类称谓指代，不要新起具体姓名。\n\n");

        sb.append("# 逻辑自洽（章内）\n")
          .append("- 因果闭环：本章关键事件需具备“触发→行动→结果→后果”的链条，避免无因果跳跃或凭空获得关键资源。\n")
          .append("- 知识边界：角色只能基于其已知或合理可获得的信息行动，必要时在章纲中简要说明信息来源，不使用上帝视角。\n")
          .append("- 能力边界：人物能力与限制前后一致；如需突破，章纲中要体现相应的铺垫或代价（例如资源消耗、负面后果等）。\n")
          .append("- 对手不降智：对立方的策略与其资源、性格和信息边界相匹配，避免为了推动剧情而做明显不合逻辑的决定。\n")
          .append("- 时间承接：注意承接上一卷/上一章的状态，如有较大跳变，需在章纲中用一句话说明发生了什么过渡。\n")
          .append("- 剧情不平淡：每章至少应在目标推进、冲突升级、重要发现或付出代价四者之一上有实质进展，避免纯过场或流水账。\n\n");

        sb.append("# 节奏提示\n")
          .append("- 避免“一碰就赢”或“一味挨打”的直线节奏，多考虑拉锯、反复试探和阶段性停顿，让读者能感到波动而不是匀速。\n")
          .append("- 如需设置某章为节奏缓和段，章纲里仍应保留至少一个信息点、情绪转折点或人物关系变化点，避免成为完全可删章节。\n")
          .append("- 章末尽量安排情绪或信息上的“未完待续”（未解决的问题、悬而未决的选择、隐隐加重的危机等），增强续读意愿。\n\n")
          .append("# 爽感与钩子强化\n")
          .append("- 每一章至少设计一个清晰的“爽点”（逆袭、扳回局面、打脸、获得关键资源等）或“痛点”（重大损失、反噬、被狠狠压制），并通过后续章节的补偿或反转形成波动，让读者始终觉得有东西在输赢；\n")
          .append("- 同一冲突不要一次性解决干净，优先采用“部分兑现+新的更大问题暴露”的方式，让剧情一环扣一环，而不是简单结束；\n")
          .append("- 尽量让人物的选择带来不可逆或代价巨大的后果，让读者在每个关键节点都本能地想：接下来会怎样？他们真的扛得住吗？\n")
          .append("- 章末的钩子要具体而可感知，例如：一个尚未拆解的阴谋、一个必须做出的艰难决定、一个刚刚出现且来历成谜的威胁，而不是抽象的“故事还在继续”。\n\n");

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
