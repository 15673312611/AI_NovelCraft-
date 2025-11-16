package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelVolume;
import com.novel.service.NovelService;
import com.novel.service.NovelVolumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取卷蓝图工具
 */
@Component
public class GetVolumeBlueprintTool implements Tool {
    
    private static final double BOUNDARY_BUFFER_RATIO = 0.1;
    private static final int BOUNDARY_BUFFER_MIN = 5;
    private static final int DEFAULT_VOLUME_COUNT = 5;
    private static final int DEFAULT_VOLUME_SIZE = 100;

    private static final Logger logger = LoggerFactory.getLogger(GetVolumeBlueprintTool.class);

    @Autowired
    private NovelVolumeService volumeService;
    
    @Autowired
    private NovelService novelService;

    @Autowired
    private ToolRegistry registry;
    
    @PostConstruct
    public void init() {
        registry.register(this);
    }
    
    @Override
    public String getName() {
        return "getVolumeBlueprint";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");
        
        Map<String, Object> chapterNumberProp = new HashMap<>();
        chapterNumberProp.put("type", "integer");
        chapterNumberProp.put("description", "当前章节号");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("获取当前章节所属卷的蓝图，包含本卷的阶段目标、核心冲突、预期结局等。卷蓝图指导当前阶段的写作方向。")
            .parameters(params)
            .returnExample("{\"volumeTitle\": \"第一卷\", \"blueprint\": \"...\", \"chapterRange\": \"1-50\"}")
            .costEstimate(300)
            .required(true)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        Integer chapterNumber = ((Number) args.get("chapterNumber")).intValue();

        logger.info("🔍 [GetVolumeBlueprint] 开始查询: novelId={}, chapterNumber={}", novelId, chapterNumber);

        Novel novel = null;
        try {
            novel = novelService.getNovelById(novelId);
        } catch (Exception ignored) {
            // 没有小说信息时，后续采用卷数据兜底
        }

        List<NovelVolume> volumes = volumeService.getVolumesByNovelId(novelId);
        logger.info("🔍 [GetVolumeBlueprint] 查询到{}个卷", volumes != null ? volumes.size() : 0);

        if (volumes != null && !volumes.isEmpty()) {
            for (NovelVolume v : volumes) {
                logger.info("  - 卷{}: ID={}, 标题={}, 章节范围={}-{}, contentOutline长度={}",
                    v.getVolumeNumber(), v.getId(), v.getTitle(),
                    v.getChapterStart(), v.getChapterEnd(),
                    v.getContentOutline() != null ? v.getContentOutline().length() : 0);
            }
        }

        volumes.sort(Comparator.comparing(v -> v.getVolumeNumber() != null ? v.getVolumeNumber() : Integer.MAX_VALUE));

        VolumeSelection selection = selectVolume(volumes, novel, chapterNumber);

        Map<String, Object> result = new HashMap<>();
        if (selection != null && selection.getVolume() != null) {
            NovelVolume volume = selection.getVolume();

            // 详细日志：检查 contentOutline 字段
            String contentOutline = volume.getContentOutline();
            logger.info("🔍 [GetVolumeBlueprint] 选中卷{}: ID={}, contentOutline={}",
                volume.getVolumeNumber(), volume.getId(),
                contentOutline != null ? ("长度=" + contentOutline.length() + "字") : "为NULL");

            if (contentOutline == null || contentOutline.trim().isEmpty()) {
                logger.warn("⚠️ [GetVolumeBlueprint] 卷{}的contentOutline为空！请检查是否已生成卷蓝图", volume.getVolumeNumber());
            }

            result.put("volumeId", volume.getId());
            result.put("volumeTitle", safeString(volume.getTitle(), "第" + selection.getVolumeNumber() + "卷"));
            result.put("volumeNumber", selection.getVolumeNumber());
            result.put("blueprint", safeString(volume.getContentOutline(), "暂无蓝图"));
            result.put("chapterRange", selection.getComputedStart() + "-" + selection.getComputedEnd());
            result.put("startChapter", selection.getComputedStart());
            result.put("endChapter", selection.getComputedEnd());
            result.put("softEndChapter", selection.getSoftEnd());
            result.put("theme", safeString(volume.getTheme(), ""));
            result.put("description", safeString(volume.getDescription(), ""));
            result.put("keyEvents", safeString(volume.getKeyEvents(), ""));
            result.put("plannedVolumeCount", selection.getPlannedVolumeCount());
            result.put("targetTotalChapters", selection.getTargetTotalChapters());
            result.put("currentChapter", chapterNumber);
            result.put("chapterIndexInVolume", selection.getChapterIndex());
            result.put("volumeChapterSpan", selection.getVolumeSpan());
            result.put("volumeProgress", selection.getProgress());
            result.put("progressDescription", selection.getProgressDescription());
            result.put("overrun", selection.isOverrun());
            result.put("overrunChapters", selection.getOverrunChapters());
            result.put("bufferAllowance", selection.getBufferAllowance());
            result.put("bufferRemaining", selection.getBufferRemaining());
            result.put("remainingChapters", selection.getRemainingChapters());
            if (selection.getSoftEnd() != selection.getComputedEnd()) {
                result.put("softEndAdjusted", true);
            }
            if (selection.isFallbackUsed()) {
                result.put("fallback", true);
            }
        } else if (selection != null) {
            // 没有对应卷实体，只能输出估算信息
            result.put("volumeTitle", "第" + selection.getVolumeNumber() + "卷");
            result.put("volumeNumber", selection.getVolumeNumber());
            result.put("blueprint", "暂无蓝图");
            result.put("chapterRange", selection.getComputedStart() + "-" + selection.getComputedEnd());
            result.put("startChapter", selection.getComputedStart());
            result.put("endChapter", selection.getComputedEnd());
            result.put("softEndChapter", selection.getSoftEnd());
            result.put("plannedVolumeCount", selection.getPlannedVolumeCount());
            result.put("targetTotalChapters", selection.getTargetTotalChapters());
            result.put("currentChapter", chapterNumber);
            result.put("chapterIndexInVolume", selection.getChapterIndex());
            result.put("volumeChapterSpan", selection.getVolumeSpan());
            result.put("volumeProgress", selection.getProgress());
            result.put("progressDescription", selection.getProgressDescription());
            result.put("fallback", true);
            result.put("overrun", selection.isOverrun());
            result.put("overrunChapters", selection.getOverrunChapters());
            result.put("bufferAllowance", selection.getBufferAllowance());
            result.put("bufferRemaining", selection.getBufferRemaining());
            result.put("remainingChapters", selection.getRemainingChapters());
            result.put("warning", "未找到对应的卷实体，采用规划数据估算结果");
        } else {
            result.put("error", "未找到对应的卷，且无法根据规划数据推断卷范围");
        }

        return result;
    }

    private VolumeSelection selectVolume(List<NovelVolume> volumes,
                                         Novel novel,
                                         int chapterNumber) {
        if (volumes == null) {
            return null;
        }

        int plannedVolumeCount = resolvePlannedVolumeCount(novel, volumes);
        int targetTotalChapters = resolveTargetTotalChapters(novel, plannedVolumeCount, volumes);

        int effectivePlannedVolumes = plannedVolumeCount > 0 ? plannedVolumeCount : DEFAULT_VOLUME_COUNT;
        int effectiveTotalChapters = targetTotalChapters > 0
            ? targetTotalChapters
            : effectivePlannedVolumes * DEFAULT_VOLUME_SIZE;
        int expectedSpan = Math.max(1, (int) Math.ceil(effectiveTotalChapters * 1.0 / effectivePlannedVolumes));

        // 先尝试根据实际章节范围匹配
        NovelVolume matched = volumes.stream()
            .filter(v -> v.getChapterStart() != null && v.getChapterEnd() != null)
            .filter(v -> chapterNumber >= v.getChapterStart() && chapterNumber <= v.getChapterEnd())
            .findFirst()
            .orElse(null);

        if (matched != null) {
            Integer start = matched.getChapterStart();
            Integer end = matched.getChapterEnd();
            if (start != null && end != null && end >= start) {
                int actualSpan = end - start + 1;
                int minimumAcceptableSpan = Math.max(BOUNDARY_BUFFER_MIN, (int) Math.ceil(expectedSpan * 0.6));
                if (actualSpan < minimumAcceptableSpan) {
                    logger.warn("⚠️ 卷跨度异常: novelId={}, chapter={}, volumeNo={}, 实际跨度={} 小于预期跨度{}，将按规划重新估算",
                        novel != null ? novel.getId() : null,
                        chapterNumber,
                        matched.getVolumeNumber(),
                        actualSpan,
                        expectedSpan);
                } else {
                    int buffer = computeBufferForVolumeSpan(start, end);
            return VolumeSelection.fromActual(matched, novel, chapterNumber, buffer, false);
                }
            } else {
                logger.warn("⚠️ 卷{}缺少章节范围，无法直接匹配，使用估算模式", matched.getVolumeNumber());
            }
        }

        // 尝试在最后一个已定义卷范围上做延展
        NovelVolume lastDefined = volumes.stream()
            .filter(v -> v.getChapterStart() != null && v.getChapterEnd() != null && chapterNumber > v.getChapterEnd())
            .max(Comparator.comparing(NovelVolume::getChapterEnd))
            .orElse(null);
        if (lastDefined != null) {
            int buffer = computeBufferForVolumeSpan(lastDefined.getChapterStart(), lastDefined.getChapterEnd());
            int softEnd = safeChapterEnd(lastDefined) + buffer;
            if (chapterNumber <= softEnd) {
                return VolumeSelection.fromActual(lastDefined, novel, chapterNumber, buffer, true);
            }
        }

        // 如果没有匹配到，基于规划数据估算卷区间
        if (effectivePlannedVolumes <= 0 || effectiveTotalChapters <= 0) {
            return null;
        }

        int approxVolumeSize = Math.max(1, (int) Math.ceil(effectiveTotalChapters * 1.0 / effectivePlannedVolumes));
        
        System.out.println(String.format("🎯 卷划分计算: 总章节=%d ÷ 卷数=%d = 每卷约%d章", 
            effectiveTotalChapters, effectivePlannedVolumes, approxVolumeSize));

        int baseStart = 1;
        for (int index = 1; index <= effectivePlannedVolumes; index++) {
            int baseEnd = index == effectivePlannedVolumes
                ? effectiveTotalChapters
                : Math.min(effectiveTotalChapters, baseStart + approxVolumeSize - 1);
            int buffer = computeBufferForSpan(baseEnd - baseStart + 1);
            int softStart = index == 1 ? 1 : Math.max(1, baseStart - buffer);
            int softEnd = baseEnd + buffer;

            boolean isLastVolume = index == effectivePlannedVolumes;
            if (chapterNumber >= softStart && (chapterNumber <= softEnd || isLastVolume)) {
                NovelVolume targetVolume = findVolumeByNumber(volumes, index);
                boolean fallbackUsed = targetVolume == null
                    || targetVolume.getChapterStart() == null
                    || targetVolume.getChapterEnd() == null;
                return VolumeSelection.fromComputed(targetVolume,
                    index,
                    effectivePlannedVolumes,
                    effectiveTotalChapters,
                    baseStart,
                    baseEnd,
                    chapterNumber,
                    buffer,
                    fallbackUsed,
                    isLastVolume);
            }

            baseStart = baseEnd + 1;
        }

        return null;
    }

    private int resolvePlannedVolumeCount(Novel novel, List<NovelVolume> volumes) {
        if (novel != null && novel.getPlannedVolumeCount() != null && novel.getPlannedVolumeCount() > 0) {
            return novel.getPlannedVolumeCount();
        }
        if (volumes != null && !volumes.isEmpty()) {
            long count = volumes.stream()
                .map(NovelVolume::getVolumeNumber)
                .filter(n -> n != null && n > 0)
                .distinct()
                .count();
            if (count > 0) {
                return (int) count;
            }
        }
        return 0;
    }

    private int resolveTargetTotalChapters(Novel novel, int plannedVolumeCount, List<NovelVolume> volumes) {
        // 优先使用小说表的目标章节数
        if (novel != null && novel.getTargetTotalChapters() != null && novel.getTargetTotalChapters() > 0) {
            System.out.println(String.format("📖 使用小说表设置: 总章节=%d", novel.getTargetTotalChapters()));
            return novel.getTargetTotalChapters();
        }
        
        // 次优：从所有卷的设定范围推断总章节数
        // ⚠️ 警告：这个逻辑有风险，只有当所有卷都明确设置了范围时才可信
        if (volumes != null && !volumes.isEmpty()) {
            long volumesWithRange = volumes.stream()
                .filter(v -> v.getChapterStart() != null && v.getChapterEnd() != null)
                .count();
            
            System.out.println(String.format("📚 卷数据检查: 总卷数=%d, 已设置范围的卷=%d", volumes.size(), volumesWithRange));
            
            // 只有当所有卷都设置了范围，才从卷数据推断
            if (volumesWithRange == volumes.size() && volumesWithRange == plannedVolumeCount) {
        int maxEnd = volumes.stream()
            .map(NovelVolume::getChapterEnd)
            .filter(end -> end != null && end > 0)
            .max(Integer::compareTo)
            .orElse(0);
        if (maxEnd > 0) {
                    System.out.println(String.format("✅ 从卷数据推断: 总章节=%d (所有%d个卷都已设置)", maxEnd, volumesWithRange));
            return maxEnd;
                }
            } else if (volumesWithRange > 0) {
                System.out.println(String.format("⚠️ 警告: 只有部分卷设置了范围，不使用卷数据，改用默认计算"));
            }
        }
        
        // 默认按照100章/卷估算
        int defaultTotal = plannedVolumeCount * DEFAULT_VOLUME_SIZE;
        System.out.println(String.format("📐 使用默认计算: %d卷 × %d章 = %d总章节", plannedVolumeCount, DEFAULT_VOLUME_SIZE, defaultTotal));
        return defaultTotal;
    }

    private String safeString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private NovelVolume findVolumeByNumber(List<NovelVolume> volumes, int volumeNumber) {
        return volumes.stream()
            .filter(v -> v.getVolumeNumber() != null && v.getVolumeNumber() == volumeNumber)
            .findFirst()
            .orElse(null);
    }

    private int computeBufferForSpan(int span) {
        int effectiveSpan = Math.max(1, span);
        return Math.max(BOUNDARY_BUFFER_MIN, (int) Math.ceil(effectiveSpan * BOUNDARY_BUFFER_RATIO));
    }

    private int computeBufferForVolumeSpan(Integer start, Integer end) {
        if (start == null || end == null) {
            return Math.max(BOUNDARY_BUFFER_MIN, (int) Math.ceil(DEFAULT_VOLUME_SIZE * BOUNDARY_BUFFER_RATIO));
        }
        return computeBufferForSpan(end - start + 1);
    }

    private int safeChapterEnd(NovelVolume volume) {
        if (volume.getChapterEnd() != null) {
            return volume.getChapterEnd();
        }
        if (volume.getChapterStart() != null && volume.getVolumeNumber() != null) {
            return volume.getChapterStart() + DEFAULT_VOLUME_SIZE - 1;
        }
        if (volume.getVolumeNumber() != null) {
            return volume.getVolumeNumber() * DEFAULT_VOLUME_SIZE;
        }
        return DEFAULT_VOLUME_SIZE;
    }

    private static class VolumeSelection {
        private final NovelVolume volume;
        private final int volumeNumber;
        private final int plannedVolumeCount;
        private final int targetTotalChapters;
        private final int computedStart;
        private final int computedEnd;
        private final int softEnd;
        private final int chapterIndex;
        private final int volumeSpan;
        private final BigDecimal progress;
        private final String progressDescription;
        private final boolean fallbackUsed;
        private final boolean overrun;
        private final int overrunChapters;
        private final int bufferAllowance;
        private final int bufferRemaining;
        private final int remainingChapters;

        private VolumeSelection(NovelVolume volume,
                                int volumeNumber,
                                int plannedVolumeCount,
                                int targetTotalChapters,
                                int computedStart,
                                int computedEnd,
                                int softEnd,
                                int chapterIndex,
                                int volumeSpan,
                                BigDecimal progress,
                                String progressDescription,
                                boolean fallbackUsed,
                                boolean overrun,
                                int overrunChapters,
                                int bufferAllowance,
                                int bufferRemaining,
                                int remainingChapters) {
            this.volume = volume;
            this.volumeNumber = volumeNumber;
            this.plannedVolumeCount = plannedVolumeCount;
            this.targetTotalChapters = targetTotalChapters;
            this.computedStart = computedStart;
            this.computedEnd = computedEnd;
            this.softEnd = softEnd;
            this.chapterIndex = chapterIndex;
            this.volumeSpan = volumeSpan;
            this.progress = progress;
            this.progressDescription = progressDescription;
            this.fallbackUsed = fallbackUsed;
            this.overrun = overrun;
            this.overrunChapters = overrunChapters;
            this.bufferAllowance = bufferAllowance;
            this.bufferRemaining = bufferRemaining;
            this.remainingChapters = remainingChapters;
        }

        static VolumeSelection fromActual(NovelVolume volume,
                                          Novel novel,
                                          int chapterNumber,
                                          int bufferAllowance,
                                          boolean extended) {
            int plannedVolumes = 0;
            int totalChapters = 0;
            if (novel != null) {
                if (novel.getPlannedVolumeCount() != null && novel.getPlannedVolumeCount() > 0) {
                    plannedVolumes = novel.getPlannedVolumeCount();
                }
                if (novel.getTargetTotalChapters() != null && novel.getTargetTotalChapters() > 0) {
                    totalChapters = novel.getTargetTotalChapters();
                }
            }
            if (plannedVolumes <= 0) {
                plannedVolumes = DEFAULT_VOLUME_COUNT;
            }
            if (totalChapters <= 0) {
                totalChapters = plannedVolumes * DEFAULT_VOLUME_SIZE;
            }

            int volumeNo = volume.getVolumeNumber() != null && volume.getVolumeNumber() > 0 ? volume.getVolumeNumber() : 1;
            int start = volume.getChapterStart() != null ? volume.getChapterStart() : (volumeNo - 1) * DEFAULT_VOLUME_SIZE + 1;
            int end = volume.getChapterEnd() != null ? volume.getChapterEnd() : start + DEFAULT_VOLUME_SIZE - 1;
            if (end < start) {
                end = start;
            }
            int span = Math.max(1, end - start + 1);
            int softEnd = end + bufferAllowance;
            if (chapterNumber > softEnd && !extended) {
                return null;
            }
            int index = Math.max(1, chapterNumber - start + 1);
            BigDecimal progress = BigDecimal.valueOf(index)
                .divide(BigDecimal.valueOf(span), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            boolean overrun = chapterNumber > end;
            int overrunChapters = overrun ? chapterNumber - end : 0;
            int remainingChapters = overrun ? Math.max(0, end - chapterNumber + 1) : Math.max(0, end - chapterNumber + 1);
            int bufferRemaining = Math.max(0, softEnd - chapterNumber);

            StringBuilder descBuilder = new StringBuilder();
            descBuilder.append("目标进度：");
            descBuilder.append(progress.setScale(1, RoundingMode.HALF_UP)).append("%");
            if (overrun) {
                descBuilder.append("，已超过原定终点 ").append(overrunChapters).append(" 章");
                if (bufferRemaining > 0) {
                    descBuilder.append("，可延后余量 ").append(bufferRemaining).append(" 章");
                } else {
                    descBuilder.append("，已用尽预留缓冲");
                }
            } else if (remainingChapters > 0) {
                descBuilder.append("，预计本卷剩余 ").append(remainingChapters).append(" 章");
            }

            return new VolumeSelection(volume,
                volumeNo,
                plannedVolumes,
                totalChapters,
                start,
                end,
                softEnd,
                index,
                span,
                progress,
                descBuilder.toString(),
                extended,
                overrun,
                overrunChapters,
                bufferAllowance,
                bufferRemaining,
                Math.max(0, end - chapterNumber + 1));
        }

        static VolumeSelection fromComputed(NovelVolume volume,
                                            int volumeNumber,
                                            int plannedVolumes,
                                            int totalChapters,
                                            int baseStart,
                                            int baseEnd,
                                            int chapterNumber,
                                            int bufferAllowance,
                                            boolean fallbackUsed,
                                            boolean isLastVolume) {
            int span = Math.max(1, baseEnd - baseStart + 1);
            int softEnd = baseEnd + bufferAllowance;
            if (!isLastVolume && chapterNumber > softEnd) {
                return null;
            }
            if (isLastVolume && chapterNumber > softEnd) {
                softEnd = chapterNumber;
            }

            int index = Math.max(1, chapterNumber - baseStart + 1);
            BigDecimal progress = BigDecimal.valueOf(index)
                .divide(BigDecimal.valueOf(span), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            boolean overrun = chapterNumber > baseEnd;
            int overrunChapters = overrun ? chapterNumber - baseEnd : 0;
            int bufferRemaining = Math.max(0, softEnd - chapterNumber);
            int remainingChapters = overrun ? Math.max(0, baseEnd - chapterNumber + 1) : Math.max(0, baseEnd - chapterNumber + 1);

            StringBuilder descBuilder = new StringBuilder();
            descBuilder.append("目标进度：");
            descBuilder.append(progress.setScale(1, RoundingMode.HALF_UP)).append("%");
            if (overrun) {
                descBuilder.append("，已超过原定终点 ").append(overrunChapters).append(" 章");
                if (bufferRemaining > 0) {
                    descBuilder.append("，可延后余量 ").append(bufferRemaining).append(" 章");
                } else {
                    descBuilder.append("，已用尽预留缓冲");
                }
            } else if (remainingChapters > 0) {
                descBuilder.append("，预计本卷剩余 ").append(remainingChapters).append(" 章");
            }

            return new VolumeSelection(volume,
                volumeNumber,
                plannedVolumes,
                totalChapters,
                baseStart,
                baseEnd,
                softEnd,
                index,
                span,
                progress,
                descBuilder.toString(),
                fallbackUsed,
                overrun,
                overrunChapters,
                bufferAllowance,
                bufferRemaining,
                Math.max(0, baseEnd - chapterNumber + 1));
        }

        public NovelVolume getVolume() {
            return volume;
        }

        public int getVolumeNumber() {
            return volumeNumber;
        }

        public int getPlannedVolumeCount() {
            return plannedVolumeCount;
        }

        public int getTargetTotalChapters() {
            return targetTotalChapters;
        }

        public int getComputedStart() {
            return computedStart;
        }

        public int getComputedEnd() {
            return computedEnd;
        }

        public int getSoftEnd() {
            return softEnd;
        }

        public int getChapterIndex() {
            return chapterIndex;
        }

        public int getVolumeSpan() {
            return volumeSpan;
        }

        public BigDecimal getProgress() {
            return progress;
        }

        public String getProgressDescription() {
            return progressDescription;
        }

        public boolean isFallbackUsed() {
            return fallbackUsed;
        }

        public boolean isOverrun() {
            return overrun;
        }

        public int getOverrunChapters() {
            return overrunChapters;
        }

        public int getBufferAllowance() {
            return bufferAllowance;
        }

        public int getBufferRemaining() {
            return bufferRemaining;
        }

        public int getRemainingChapters() {
            return remainingChapters;
        }
    }
}

