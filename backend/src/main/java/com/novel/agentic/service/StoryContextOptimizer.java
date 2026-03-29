package com.novel.agentic.service;

import com.novel.agentic.model.GraphEntity;
import com.novel.agentic.model.TokenBudget;
import com.novel.agentic.model.WritingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 写作上下文优化器
 *
 * 目标：在不丢失关键信息的情况下，压缩高价值剧情，并生成核心纪要，避免噪声稀释主线。
 */
@Service
public class StoryContextOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(StoryContextOptimizer.class);

    private static final double DEFAULT_MIN_IMPORTANCE = 0.35;
    private static final int EARLY_CHAPTER_THRESHOLD = 5;
    private static final int MIN_GRAPH_SIGNAL_COUNT = 3;

    private final TokenBudget defaultBudget = TokenBudget.builder().build();

    public WritingContext optimize(WritingContext original) {
        if (original == null) {
            return null;
        }

        if (shouldBypassOptimization(original)) {
            logger.info("🧩 早期章节或图谱信号不足，跳过优化逻辑");
            if (original.getPrioritizedEvents() == null) {
                original.setPrioritizedEvents(original.getRelevantEvents());
            }
            if (original.getCoreNarrativeSummary() == null) {
                original.setCoreNarrativeSummary(buildCoreSummary(
                    safeList(original.getRelevantEvents()),
                    safeList(original.getConflictArcs()),
                    safeList(original.getUnresolvedForeshadows()),
                    safeList(original.getCharacterArcs()),
                    safeList(original.getPlotlineStatus()),
                    original.getChapterPlan()));
            }
            if (original.getInnovationIdeas() == null) {
                original.setInnovationIdeas(Collections.emptyList());
            }
            if (original.getForeshadowPlan() == null) {
                original.setForeshadowPlan(Collections.emptyList());
            }
            return original;
        }

        List<GraphEntity> filteredEvents = filterEntities(original.getRelevantEvents(),
            defaultBudget.getMaxEvents(), DEFAULT_MIN_IMPORTANCE, true);
        List<GraphEntity> filteredForeshadows = filterEntities(original.getUnresolvedForeshadows(),
            defaultBudget.getMaxForeshadows(), DEFAULT_MIN_IMPORTANCE, false);
        List<GraphEntity> filteredConflicts = filterEntities(original.getConflictArcs(), 3, DEFAULT_MIN_IMPORTANCE, false);
        List<GraphEntity> filteredCharacterArcs = filterEntities(original.getCharacterArcs(), 3, DEFAULT_MIN_IMPORTANCE, false);
        List<GraphEntity> filteredPlotlines = filterEntities(original.getPlotlineStatus(), 4, DEFAULT_MIN_IMPORTANCE, false);

        Map<String, Object> coreSummary = buildCoreSummary(filteredEvents, filteredConflicts,
            filteredForeshadows, filteredCharacterArcs, filteredPlotlines, original.getChapterPlan());

        List<Map<String, Object>> characterProfiles = collectCharacterProfiles(original.getCharacterProfiles());

        WritingContext optimized = WritingContext.builder()
            .novelInfo(original.getNovelInfo())
            .coreSettings(original.getCoreSettings())
            .volumeBlueprint(original.getVolumeBlueprint())
            .chapterPlan(original.getChapterPlan())
            .relevantEvents(filteredEvents)
            .unresolvedForeshadows(filteredForeshadows)
            .plotlineStatus(filteredPlotlines)
            .worldRules(original.getWorldRules())
            .narrativeRhythm(original.getNarrativeRhythm())
            .conflictArcs(filteredConflicts)
            .characterArcs(filteredCharacterArcs)
            .perspectiveHistory(original.getPerspectiveHistory())
            .recentFullChapters(limitRecentChapters(original.getRecentFullChapters(), defaultBudget.getMaxFullChapters()))
            .recentSummaries(limitRecentSummaries(original.getRecentSummaries(), 10))
            .innovationIdeas(original.getInnovationIdeas())
            .foreshadowPlan(original.getForeshadowPlan())
            .chapterIntent(original.getChapterIntent())
            .coreNarrativeSummary(coreSummary)
            .prioritizedEvents(filteredEvents)
            .characterProfiles(original.getCharacterProfiles())
            .volumeCharacterProfiles(original.getVolumeCharacterProfiles())
            .characterStates(original.getCharacterStates())
            .relationshipStates(original.getRelationshipStates())
            .openQuests(original.getOpenQuests())
            .writingStyle(original.getWritingStyle())
            .userAdjustment(original.getUserAdjustment())
            .thoughts(original.getThoughts())
            .build();

        logger.info("🧩 上下文优化完成: events={}, foreshadows={}, conflicts={}"
                , filteredEvents.size(), filteredForeshadows.size(), filteredConflicts.size());

        return optimized;
    }

    private boolean shouldBypassOptimization(WritingContext context) {
        Integer chapterNumber = resolveChapterNumber(context.getChapterPlan());
        if (chapterNumber != null && chapterNumber <= EARLY_CHAPTER_THRESHOLD) {
            return true;
        }
        return totalSignalCount(context) < MIN_GRAPH_SIGNAL_COUNT;
    }

    private Integer resolveChapterNumber(Map<String, Object> chapterPlan) {
        if (chapterPlan == null) {
            return null;
        }
        Object value = chapterPlan.get("chapterNumber");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int totalSignalCount(WritingContext context) {
        int count = 0;
        count += size(context.getRelevantEvents());
        count += size(context.getUnresolvedForeshadows());
        count += size(context.getConflictArcs());
        count += size(context.getCharacterArcs());
        return count;
    }

    private int size(List<?> list) {
        return list != null ? list.size() : 0;
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private List<Map<String, Object>> limitRecentChapters(List<Map<String, Object>> chapters, int max) {
        if (chapters == null || chapters.isEmpty()) {
            return Collections.emptyList();
        }
        return chapters.stream()
            .limit(Math.max(0, max))
            .map(chapter -> {
                Map<String, Object> copy = new LinkedHashMap<>(chapter);
                Object content = copy.get("content");
                if (content instanceof String) {
                    copy.put("content", defaultBudget.truncate((String) content, defaultBudget.getMaxChapterContent()));
                }
                return copy;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> limitRecentSummaries(List<Map<String, Object>> summaries, int max) {
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptyList();
        }
        return summaries.stream()
            .skip(Math.max(0, summaries.size() - max))
            .collect(Collectors.toList());
    }

    private List<GraphEntity> filterEntities(List<GraphEntity> entities, int limit, double minScore, boolean truncateDescription) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        return entities.stream()
            .filter(Objects::nonNull)
            .map(entity -> truncateDescription ? normalizeDescription(entity) : cloneEntity(entity))
            .sorted(Comparator.comparingDouble(this::resolveScore).reversed())
            .filter(entity -> resolveScore(entity) >= minScore)
            .limit(Math.max(0, limit))
            .collect(Collectors.toList());
    }

    private GraphEntity normalizeDescription(GraphEntity entity) {
        Map<String, Object> props = new LinkedHashMap<>(Optional.ofNullable(entity.getProperties()).orElse(Collections.emptyMap()));
        Object description = props.get("description");
        if (description instanceof String) {
            props.put("description", defaultBudget.truncate((String) description, defaultBudget.getMaxEventDescription()));
        }
        return GraphEntity.builder()
            .type(entity.getType())
            .id(entity.getId())
            .chapterNumber(entity.getChapterNumber())
            .relevanceScore(entity.getRelevanceScore())
            .properties(props)
            .source(entity.getSource())
            .build();
    }

    private GraphEntity cloneEntity(GraphEntity entity) {
        Map<String, Object> props = Optional.ofNullable(entity.getProperties())
            .map(LinkedHashMap::new)
            .orElseGet(LinkedHashMap::new);
        return GraphEntity.builder()
            .type(entity.getType())
            .id(entity.getId())
            .chapterNumber(entity.getChapterNumber())
            .relevanceScore(entity.getRelevanceScore())
            .properties(props)
            .source(entity.getSource())
            .build();
    }

    private double resolveScore(GraphEntity entity) {
        if (entity == null) {
            return 0.0;
        }

        Map<String, Object> props = entity.getProperties();
        if (props != null) {
            Object raw = props.get("importanceScore");
            if (raw instanceof Number) {
                return ((Number) raw).doubleValue();
            }
            Object importance = props.get("importance");
            if (importance instanceof Number) {
                return ((Number) importance).doubleValue();
            }
            if (importance instanceof String) {
                return mapImportance((String) importance);
            }
            Object priority = props.get("priority");
            if (priority instanceof Number) {
                return ((Number) priority).doubleValue();
            }
            Object urgency = props.get("urgency");
            if (urgency instanceof Number) {
                double value = ((Number) urgency).doubleValue();
                if (value > 0 && value <= 1) {
                    return value;
                }
            }
        }

        if (entity.getRelevanceScore() != null) {
            double score = entity.getRelevanceScore();
            return score > 0 ? Math.min(1.0, score / 10.0) : 0.3;
        }

        return 0.4;
    }

    private double mapImportance(String importance) {
        String normalized = importance == null ? "" : importance.trim().toLowerCase();
        switch (normalized) {
            case "high":
            case "核心":
            case "critical":
                return 0.9;
            case "medium":
            case "mid":
            case "中":
                return 0.6;
            case "low":
            case "次要":
                return 0.3;
            default:
                return 0.45;
        }
    }

    private Map<String, Object> buildCoreSummary(List<GraphEntity> events,
                                                 List<GraphEntity> conflicts,
                                                 List<GraphEntity> foreshadows,
                                                 List<GraphEntity> characterArcs,
                                                 List<GraphEntity> plotlines,
                                                 Map<String, Object> chapterPlan) {
        Map<String, Object> summary = new LinkedHashMap<>();

        Map<String, Object> meta = new LinkedHashMap<>();
        if (!conflicts.isEmpty()) {
            GraphEntity c = conflicts.get(0);
            meta.put("activeConflict", c.getId());
            meta.put("activeConflictName", safeProp(c, "name"));
        }
        if (!plotlines.isEmpty()) {
            GraphEntity p = plotlines.get(0);
            meta.put("activePlotline", p.getId());
            meta.put("activePlotlineName", safeProp(p, "name"));
        }
        if (!characterArcs.isEmpty()) {
            GraphEntity ca = characterArcs.get(0);
            meta.put("activeCharacterArc", ca.getId());
            meta.put("activeCharacterArcName", safeProp(ca, "characterName"));
        }
        summary.put("meta", meta);

        List<String> highlights = new ArrayList<>();
        events.stream().limit(3).map(this::formatEventHighlight).filter(Objects::nonNull).forEach(highlights::add);
        if (!highlights.isEmpty()) {
            summary.put("highlights", highlights);
        }

        conflicts.stream().findFirst().ifPresent(conflict ->
            summary.put("primaryConflict", formatConflict(conflict))
        );

        foreshadows.stream().limit(2).map(this::formatForeshadow)
            .filter(Objects::nonNull)
            .findFirst()
            .ifPresent(value -> summary.put("urgentForeshadow", value));

        characterArcs.stream().findFirst().ifPresent(arc ->
            summary.put("characterProgress", formatCharacterArc(arc))
        );

        plotlines.stream().limit(2).map(this::formatPlotlineWarning)
            .filter(Objects::nonNull)
            .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                if (!list.isEmpty()) {
                    summary.put("plotlineAlerts", list);
                }
                return list;
            }));

        if (chapterPlan != null && chapterPlan.containsKey("coreEvent")) {
            summary.put("chapterGoal", chapterPlan.get("coreEvent"));
        }

        return summary;
    }

    private List<Map<String, Object>> collectCharacterProfiles(List<Map<String, Object>> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Collections.emptyList();
        }
        return profiles.stream()
            .filter(Objects::nonNull)
            .limit(5)
            .collect(Collectors.toList());
    }

    private String formatEventHighlight(GraphEntity entity) {
        if (entity == null || entity.getProperties() == null) {
            return null;
        }
        Map<String, Object> props = entity.getProperties();
        Object desc = props.get("description");
        Object chapter = entity.getChapterNumber();
        if (desc == null) {
            return null;
        }
        return String.format("第%s章：%s", chapter != null ? chapter : "?", desc.toString());
    }

    private String formatConflict(GraphEntity entity) {
        Map<String, Object> props = entity.getProperties();
        if (props == null) {
            return null;
        }
        Object name = props.get("name");
        Object stage = props.get("stage");
        Object nextAction = props.get("nextAction");
        return String.format("冲突线%s进入%s阶段，下一步：%s",
            name != null ? name : "?",
            stage != null ? stage : "推进",
            nextAction != null ? nextAction : "待定");
    }

    private String formatForeshadow(GraphEntity entity) {
        Map<String, Object> props = entity.getProperties();
        if (props == null) {
            return null;
        }
        Object description = props.get("description");
        Object plantedAt = props.get("plantedAt");
        if (description == null) {
            return null;
        }
        return String.format("伏笔：%s（埋于%s）",
            description,
            plantedAt != null ? plantedAt : "未知章节");
    }

    private String formatCharacterArc(GraphEntity entity) {
        Map<String, Object> props = entity.getProperties();
        if (props == null) {
            return null;
        }
        Object characterName = props.get("characterName");
        Object pendingBeat = props.get("pendingBeat");
        Object nextGoal = props.get("nextGoal");
        return String.format("%s 当前待完成：%s → 下一目标：%s",
            characterName != null ? characterName : "角色",
            pendingBeat != null ? pendingBeat : "关键节点",
            nextGoal != null ? nextGoal : "推进主线");
    }

    private String formatPlotlineWarning(GraphEntity entity) {
        Map<String, Object> props = entity.getProperties();
        if (props == null) {
            return null;
        }
        Object name = props.get("name");
        Object status = props.get("status");
        Object idleDuration = props.get("idleDuration");
        return String.format("情节线%s 状态：%s（闲置%s章）",
            name != null ? name : "?",
            status != null ? status : "待推进",
            idleDuration != null ? idleDuration : "0");
    }

    private String safeProp(GraphEntity entity, String key) {
        if (entity == null || entity.getProperties() == null) {
            return null;
        }
        Object value = entity.getProperties().get(key);
        return value != null ? value.toString() : null;
    }
}
