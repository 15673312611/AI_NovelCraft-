//package com.novel.agentic.service.graph;
//
//import com.novel.agentic.model.GraphEntity;
//import org.neo4j.driver.Driver;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * 内存模拟图数据库服务（降级方案）
// *
// * 当Neo4j不可用时使用此实现
// *
// * 优先级低：只在Neo4j Driver不存在时使用
// */
//@Service
//@ConditionalOnMissingBean(Driver.class)
//public class InMemoryGraphService implements IGraphService {
//
//    private static final Logger logger = LoggerFactory.getLogger(InMemoryGraphService.class);
//
//    // 临时使用内存存储（后续替换为Neo4j）
//    private final Map<Long, List<GraphEntity>> novelGraphs = new HashMap<>();
//
//    public InMemoryGraphService() {
//        logger.warn("⚠️⚠️⚠️ 警告：正在使用内存图谱服务（降级模式）⚠️⚠️⚠️");
//        logger.warn("   图谱数据仅保存在内存中，重启后将全部丢失！");
//        logger.warn("   建议安装Neo4j实现持久化存储");
//        logger.warn("   安装指南: NEO4J_QUICK_START.md");
//        logger.warn("   修改配置: application.yml -> graph.neo4j.enabled: true");
//    }
//
//    /**
//     * 查询相关事件
//     *
//     * @param novelId 小说ID
//     * @param chapterNumber 当前章节号
//     * @param limit 最大返回数量
//     * @return 相关事件列表（按相关性排序）
//     */
//    public List<GraphEntity> getRelevantEvents(Long novelId, Integer chapterNumber, Integer limit) {
//        logger.info("🔍 查询相关事件: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
//
//        // 🔧 修复：图谱为空时返回空列表，不返回假数据
//        if (chapterNumber <= 1) {
//            logger.info("📌 第1章，图谱暂无数据");
//            return Collections.emptyList();
//        }
//
//        // 🔧 改进：检查图谱中是否真的有数据
//        List<GraphEntity> storedEvents = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> events = storedEvents.stream()
//            .filter(e -> "Event".equals(e.getType()))
//            .filter(e -> e.getChapterNumber() < chapterNumber)
//            .sorted((a, b) -> Double.compare(
//                b.getRelevanceScore() != null ? b.getRelevanceScore() : 0.5,
//                a.getRelevanceScore() != null ? a.getRelevanceScore() : 0.5
//            ))
//            .limit(limit)
//            .collect(Collectors.toList());
//
//        if (events.isEmpty()) {
//            logger.info("📌 图谱暂无历史事件数据（章节{}之前）", chapterNumber);
//        } else {
//            logger.info("✅ 从图谱查询到{}个相关事件", events.size());
//        }
//
//        return events;
//    }
//
//    /**
//     * 查询未回收伏笔
//     */
//    public List<GraphEntity> getUnresolvedForeshadows(Long novelId, Integer chapterNumber, Integer limit) {
//        logger.info("🔍 查询未回收伏笔: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
//
//        // 🔧 修复：只返回真实存在的伏笔
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> foreshadows = storedEntities.stream()
//            .filter(e -> "Foreshadow".equals(e.getType()))
//            .filter(e -> e.getChapterNumber() < chapterNumber)
//            .filter(e -> {
//                Map<String, Object> props = e.getProperties();
//                String status = (String) props.getOrDefault("status", "PLANTED");
//                return "PLANTED".equals(status); // 只返回未回收的
//            })
//            .sorted((a, b) -> {
//                String impA = (String) a.getProperties().getOrDefault("importance", "medium");
//                String impB = (String) b.getProperties().getOrDefault("importance", "medium");
//                int scoreA = "high".equals(impA) ? 3 : "medium".equals(impA) ? 2 : 1;
//                int scoreB = "high".equals(impB) ? 3 : "medium".equals(impB) ? 2 : 1;
//                return Integer.compare(scoreB, scoreA);
//            })
//            .limit(limit)
//            .collect(Collectors.toList());
//
//        if (foreshadows.isEmpty()) {
//            logger.info("📌 暂无待回收伏笔");
//        } else {
//            logger.info("✅ 查询到{}个待回收伏笔", foreshadows.size());
//        }
//
//        return foreshadows;
//    }
//
//    /**
//     * 查询情节线状态
//     */
//    @Override
//    public List<GraphEntity> getPlotlineStatus(Long novelId, Integer chapterNumber, Integer limit) {
//        logger.info("🔍 查询情节线状态: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
//
//        // 内存版：返回真实存储的情节线数据
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> plotlines = storedEntities.stream()
//            .filter(e -> "Plotline".equals(e.getType()))
//            .limit(limit)
//            .collect(Collectors.toList());
//
//        if (plotlines.isEmpty()) {
//            logger.info("📌 暂无情节线数据");
//        } else {
//            logger.info("✅ 查询到{}个情节线", plotlines.size());
//        }
//
//        return plotlines;
//    }
//
//    /**
//     * 查询世界规则
//     */
//    @Override
//    public List<GraphEntity> getWorldRules(Long novelId, Integer chapterNumber, Integer limit) {
//        logger.info("🔍 查询世界规则: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
//
//        // 内存版：返回真实存储的世界规则数据
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> rules = storedEntities.stream()
//            .filter(e -> "WorldRule".equals(e.getType()))
//            .filter(e -> e.getChapterNumber() == null || e.getChapterNumber() <= chapterNumber)
//            .limit(limit)
//            .collect(Collectors.toList());
//
//        if (rules.isEmpty()) {
//            logger.info("📌 暂无世界规则数据");
//        } else {
//            logger.info("✅ 查询到{}个世界规则", rules.size());
//        }
//
//        return rules;
//    }
//
//    /**
//     * 查询角色关系网
//     *
//     * 内存版：简化实现，返回存储的角色关系
//     */
//    @Override
//    public List<GraphEntity> getCharacterRelationships(Long novelId, String characterName, Integer limit) {
//        logger.info("🔍 内存版查询角色关系网: novelId={}, character={}", novelId, characterName);
//
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> relationships = storedEntities.stream()
//            .filter(e -> "CharacterRelationship".equals(e.getType()))
//            .filter(e -> {
//                Map<String, Object> props = e.getProperties();
//                String from = (String) props.get("from");
//                String to = (String) props.get("to");
//                return characterName.equals(from) || characterName.equals(to);
//            })
//            .limit(limit)
//            .collect(Collectors.toList());
//
//        logger.info("✅ 查询到{}个角色关系", relationships.size());
//        return relationships;
//    }
//
//    /**
//     * 按角色查询相关事件
//     *
//     * 内存版：简化实现
//     */
//    @Override
//    public List<GraphEntity> getEventsByCharacter(Long novelId, String characterName, Integer chapterNumber, Integer limit) {
//        logger.info("🔍 内存版按角色查询事件: novelId={}, character={}", novelId, characterName);
//
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> events = storedEntities.stream()
//            .filter(e -> "Event".equals(e.getType()))
//            .filter(e -> e.getChapterNumber() < chapterNumber)
//            .filter(e -> {
//                Map<String, Object> props = e.getProperties();
//                Object participants = props.get("participants");
//                if (participants instanceof List) {
//                    return ((List<?>) participants).contains(characterName);
//                }
//                return false;
//            })
//            .limit(limit)
//            .collect(Collectors.toList());
//
//        logger.info("✅ 查询到{}个角色相关事件", events.size());
//        return events;
//    }
//
//    /**
//     * 按因果链查询相关事件
//     *
//     * 内存版：简化实现，暂不支持复杂因果链
//     */
//    @Override
//    public List<GraphEntity> getEventsByCausality(Long novelId, String eventId, Integer depth) {
//        logger.info("🔍 内存版按因果链查询: novelId={}, eventId={}", novelId, eventId);
//
//        // 内存版简化：暂不支持因果链查询
//        logger.warn("⚠️ 内存版暂不支持因果链查询，建议使用Neo4j版本");
//        return Collections.emptyList();
//    }
//
//    /**
//     * 查询冲突发展历史
//     *
//     * 内存版：简化实现
//     */
//    @Override
//    public List<GraphEntity> getConflictHistory(Long novelId, String protagonistName, String antagonistName, Integer limit) {
//        logger.info("🔍 内存版查询冲突历史: novelId={}, protagonist={}, antagonist={}",
//                    novelId, protagonistName, antagonistName);
//
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> conflicts = storedEntities.stream()
//            .filter(e -> "Event".equals(e.getType()))
//            .filter(e -> {
//                Map<String, Object> props = e.getProperties();
//                Object participants = props.get("participants");
//                if (participants instanceof List) {
//                    List<?> parts = (List<?>) participants;
//                    return parts.contains(protagonistName) && parts.contains(antagonistName);
//                }
//                return false;
//            })
//            .filter(e -> {
//                Map<String, Object> props = e.getProperties();
//                Object tags = props.get("tags");
//                if (tags instanceof List) {
//                    return ((List<?>) tags).contains("conflict");
//                }
//                String emotionalTone = (String) props.get("emotionalTone");
//                return "conflict".equals(emotionalTone) || "tense".equals(emotionalTone);
//            })
//            .limit(limit)
//            .collect(Collectors.toList());
//
//        logger.info("✅ 查询到{}个冲突事件", conflicts.size());
//        return conflicts;
//    }
//
//    @Override
//    public Map<String, Object> getNarrativeRhythmStatus(Long novelId, Integer chapterNumber, Integer window) {
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> beats = storedEntities.stream()
//            .filter(e -> "NarrativeBeat".equals(e.getType()))
//            .filter(e -> e.getChapterNumber() != null && e.getChapterNumber() < chapterNumber)
//            .sorted(Comparator.comparing(GraphEntity::getChapterNumber).reversed())
//            .limit(window)
//            .collect(Collectors.toList());
//        Collections.reverse(beats);
//
//        Map<String, Object> metrics = new HashMap<>();
//        Map<String, Long> typeCounts = beats.stream()
//            .collect(Collectors.groupingBy(be -> normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN")), Collectors.counting()));
//        int total = beats.size();
//        Set<String> conflictTypes = new HashSet<>(Arrays.asList("CONFLICT", "CLIMAX"));
//        Set<String> plotTypes = new HashSet<>(Arrays.asList("PLOT", "ADVANCEMENT"));
//        Set<String> characterTypes = new HashSet<>(Arrays.asList("CHARACTER", "EMOTION"));
//        Set<String> reliefTypes = new HashSet<>(Arrays.asList("RELIEF", "DAILY"));
//
//        double conflictRatio = total == 0 ? 0.0 : (double) beats.stream()
//            .filter(be -> conflictTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
//            .count() / total;
//        double plotRatio = total == 0 ? 0.0 : (double) beats.stream()
//            .filter(be -> plotTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
//            .count() / total;
//        double characterRatio = total == 0 ? 0.0 : (double) beats.stream()
//            .filter(be -> characterTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
//            .count() / total;
//        double reliefRatio = total == 0 ? 0.0 : (double) beats.stream()
//            .filter(be -> reliefTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
//            .count() / total;
//
//        metrics.put("conflictRatio", conflictRatio);
//        metrics.put("plotRatio", plotRatio);
//        metrics.put("characterRatio", characterRatio);
//        metrics.put("reliefRatio", reliefRatio);
//        metrics.put("beatCounts", typeCounts);
//
//        int consecutiveConflict = 0;
//        for (int i = beats.size() - 1; i >= 0; i--) {
//            String type = normalizeBeatType((String) beats.get(i).getProperties().getOrDefault("beatType", "UNKNOWN"));
//            if (conflictTypes.contains(type)) {
//                consecutiveConflict++;
//            } else {
//                break;
//            }
//        }
//        boolean conflictFatigue = consecutiveConflict >= 3;
//        metrics.put("consecutiveConflict", consecutiveConflict);
//        metrics.put("conflictFatigue", conflictFatigue);
//
//        List<String> recommendations = new ArrayList<>();
//        if (beats.isEmpty()) {
//            recommendations.add("尚无节奏记录，参考卷蓝图规划章节节奏。");
//        }
//        if (conflictFatigue) {
//            recommendations.add("连续高强度冲突，建议安排人物或日常缓冲。");
//        }
//        if (plotRatio < 0.3) {
//            recommendations.add("主线推进偏低，适当推进剧情。");
//        }
//        if (characterRatio < 0.2) {
//            recommendations.add("人物描写不足，可增加角色内心或互动。");
//        }
//
//        Map<String, Object> status = new HashMap<>();
//        status.put("recentBeats", beats);
//        status.put("metrics", metrics);
//        status.put("recommendations", recommendations);
//        return status;
//    }
//
//    @Override
//    public List<GraphEntity> getActiveConflictArcs(Long novelId, Integer chapterNumber, Integer limit) {
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        return storedEntities.stream()
//            .filter(e -> "ConflictArc".equals(e.getType()))
//            .filter(e -> {
//                String stage = (String) e.getProperties().getOrDefault("stage", "酝酿");
//                return !"解决".equals(stage);
//            })
//            .sorted(Comparator.comparing((GraphEntity e) -> {
//                Object urgency = e.getProperties().getOrDefault("urgency", 0.5);
//                return urgency instanceof Number ? ((Number) urgency).doubleValue() : 0.5;
//            }).reversed())
//            .limit(limit)
//            .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<GraphEntity> getCharacterArcStatus(Long novelId, Integer chapterNumber, Integer limit) {
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        return storedEntities.stream()
//            .filter(e -> "CharacterArc".equals(e.getType()))
//            .filter(e -> {
//                Object progressObj = e.getProperties().get("progress");
//                Object totalBeatsObj = e.getProperties().get("totalBeats");
//                if (progressObj == null || totalBeatsObj == null) return true;
//                int progress = progressObj instanceof Number ? ((Number) progressObj).intValue() : 0;
//                int totalBeats = totalBeatsObj instanceof Number ? ((Number) totalBeatsObj).intValue() : 0;
//                return progress < totalBeats;
//            })
//            .sorted(Comparator.comparing((GraphEntity e) -> {
//                Object priority = e.getProperties().getOrDefault("priority", 0.5);
//                return priority instanceof Number ? ((Number) priority).doubleValue() : 0.5;
//            }).reversed())
//            .limit(limit)
//            .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<GraphEntity> getPerspectiveHistory(Long novelId, Integer chapterNumber, Integer window) {
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//        List<GraphEntity> history = storedEntities.stream()
//            .filter(e -> "PerspectiveUsage".equals(e.getType()))
//            .filter(e -> e.getChapterNumber() != null && e.getChapterNumber() < chapterNumber)
//            .sorted(Comparator.comparing(GraphEntity::getChapterNumber).reversed())
//            .limit(window)
//            .collect(Collectors.toList());
//        Collections.reverse(history);
//
//        if (!history.isEmpty()) {
//            String lastCharacter = (String) history.get(history.size() - 1).getProperties().get("characterName");
//            boolean allSame = history.stream().allMatch(e -> Objects.equals(e.getProperties().get("characterName"), lastCharacter));
//            if (allSame && history.size() >= 3) {
//                history.add(0, GraphEntity.builder()
//                    .type("PerspectiveRecommendation")
//                    .id("perspective_summary")
//                    .properties(createPropertiesMap(
//                        "recommendation", "连续多章使用" + lastCharacter + "视角，建议尝试其他视角。"
//                    ))
//                    .build());
//            }
//        }
//
//        return history;
//    }
//
//    /**
//     * 添加实体到图谱
//     */
//    @Override
//    public void addEntity(Long novelId, GraphEntity entity) {
//        novelGraphs.computeIfAbsent(novelId, k -> new ArrayList<>()).add(entity);
//        logger.info("✅ 已添加图谱实体: type={}, id={}", entity.getType(), entity.getId());
//    }
//
//    /**
//     * 批量添加实体
//     */
//    @Override
//    public void addEntities(Long novelId, List<GraphEntity> entities) {
//        entities.forEach(entity -> addEntity(novelId, entity));
//    }
//
//    /**
//     * 添加关系到图谱
//     *
//     * 内存版：简化实现
//     */
//    @Override
//    public void addRelationship(Long novelId, String fromEntityId, String relationshipType,
//                                String toEntityId, Map<String, Object> properties) {
//        logger.info("➕ 内存版添加关系: {} -[{}]-> {}", fromEntityId, relationshipType, toEntityId);
//
//        // 内存版简化：创建一个关系实体
//        GraphEntity relationship = GraphEntity.builder()
//            .type("Relationship")
//            .id(fromEntityId + "_" + relationshipType + "_" + toEntityId)
//            .properties(new HashMap<String, Object>() {{
//                put("from", fromEntityId);
//                put("to", toEntityId);
//                put("type", relationshipType);
//                if (properties != null) {
//                    putAll(properties);
//                }
//            }})
//            .build();
//
//        addEntity(novelId, relationship);
//    }
//
//    /**
//     * 查询图谱统计信息
//     */
//    @Override
//    public Map<String, Object> getGraphStatistics(Long novelId) {
//        logger.info("📊 内存版查询图谱统计: novelId={}", novelId);
//
//        List<GraphEntity> entities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//
//        Map<String, Long> typeCounts = entities.stream()
//            .collect(Collectors.groupingBy(GraphEntity::getType, Collectors.counting()));
//
//        Map<String, Object> stats = new HashMap<>();
//        typeCounts.forEach((type, count) -> stats.put(type + "Count", count));
//        stats.put("totalEntities", entities.size());
//
//        logger.info("✅ 图谱统计: {}", stats);
//        return stats;
//    }
//
//    /**
//     * 检查服务可用性
//     */
//    @Override
//    public boolean isAvailable() {
//        return true; // 内存版始终可用
//    }
//
//    /**
//     * 获取服务类型
//     */
//    @Override
//    public String getServiceType() {
//        return "MEMORY";
//    }
//
//    @Override
//    public void clearGraph(Long novelId) {
//        if (novelId == null) {
//            return;
//        }
//        novelGraphs.remove(novelId);
//        logger.info("🧹 内存版图谱已清空: novelId={}", novelId);
//    }
//
//    /**
//     * 🆕 删除指定章节的所有图谱实体和关系
//     */
//    @Override
//    public void deleteChapterEntities(Long novelId, Integer chapterNumber) {
//        if (novelId == null || chapterNumber == null) {
//            return;
//        }
//
//        List<GraphEntity> entities = novelGraphs.get(novelId);
//        if (entities != null) {
//            entities.removeIf(entity ->
//                chapterNumber.equals(entity.getChapterNumber())
//            );
//            logger.info("🗑️ 内存版图谱已删除第{}章的数据，剩余{}个实体", chapterNumber, entities.size());
//        }
//    }
//
//    /**
//     * 获取小说的所有图谱数据
//     */
//    @Override
//    public Map<String, Object> getAllGraphData(Long novelId) {
//        logger.info("📊 内存版查询小说所有图谱数据: novelId={}", novelId);
//
//        Map<String, Object> result = new HashMap<>();
//        List<GraphEntity> allEntities = novelGraphs.getOrDefault(novelId, Collections.emptyList());
//
//        // 按类型分组
//        List<Map<String, Object>> events = new ArrayList<>();
//        List<Map<String, Object>> foreshadows = new ArrayList<>();
//        List<Map<String, Object>> plotlines = new ArrayList<>();
//        List<Map<String, Object>> worldRules = new ArrayList<>();
//        List<Map<String, Object>> conflictArcs = new ArrayList<>();
//        List<Map<String, Object>> characterArcs = new ArrayList<>();
//
//        for (GraphEntity entity : allEntities) {
//            Map<String, Object> entityData = new HashMap<>(entity.getProperties());
//            entityData.put("id", entity.getId());
//            entityData.put("type", entity.getType());
//            entityData.put("chapterNumber", entity.getChapterNumber());
//
//            switch (entity.getType()) {
//                case "Event":
//                    events.add(entityData);
//                    break;
//                case "Foreshadow":
//                    foreshadows.add(entityData);
//                    break;
//                case "Plotline":
//                    plotlines.add(entityData);
//                    break;
//                case "WorldRule":
//                    worldRules.add(entityData);
//                    break;
//                case "ConflictArc":
//                    conflictArcs.add(entityData);
//                    break;
//                case "CharacterArc":
//                    characterArcs.add(entityData);
//                    break;
//            }
//        }
//
//        // 内存版暂无关系数据（简化实现）
//        List<Map<String, Object>> causalRelations = Collections.emptyList();
//        List<Map<String, Object>> characterRelations = Collections.emptyList();
//
//        // 🆕 内存版核心记忆账本数据（空实现）
//        List<Map<String, Object>> characterStates = Collections.emptyList();
//        List<Map<String, Object>> relationshipStates = Collections.emptyList();
//        List<Map<String, Object>> openQuests = Collections.emptyList();
//
//        result.put("events", events);
//        result.put("foreshadows", foreshadows);
//        result.put("plotlines", plotlines);
//        result.put("worldRules", worldRules);
//        result.put("conflictArcs", conflictArcs);
//        result.put("characterArcs", characterArcs);
//        result.put("causalRelations", causalRelations);
//        result.put("characterRelations", characterRelations);
//
//        // 🆕 添加核心记忆账本数据
//        result.put("characterStates", characterStates);
//        result.put("relationshipStates", relationshipStates);
//        result.put("openQuests", openQuests);
//
//        // 添加统计信息
//        result.put("totalEvents", events.size());
//        result.put("totalForeshadows", foreshadows.size());
//        result.put("totalPlotlines", plotlines.size());
//        result.put("totalWorldRules", worldRules.size());
//        result.put("totalConflictArcs", conflictArcs.size());
//        result.put("totalCharacterArcs", characterArcs.size());
//        result.put("totalCausalRelations", 0);
//        result.put("totalCharacterRelations", 0);
//        result.put("totalCharacterStates", 0);
//        result.put("totalRelationshipStates", 0);
//        result.put("totalOpenQuests", 0);
//
//        logger.info("✅ 内存版图谱数据查询完成: {}个事件, {}个伏笔", events.size(), foreshadows.size());
//
//        return result;
//    }
//
//    /**
//     * 创建属性Map的辅助方法（JDK 8兼容）
//     */
//    private Map<String, Object> createPropertiesMap(Object... keyValues) {
//        Map<String, Object> map = new HashMap<>();
//        for (int i = 0; i < keyValues.length; i += 2) {
//            map.put((String) keyValues[i], keyValues[i + 1]);
//        }
//        return map;
//    }
//
//    private String normalizeBeatType(String rawType) {
//        if (rawType == null) {
//            return "UNKNOWN";
//        }
//        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
//        if (normalized.contains("CONFLICT") || normalized.contains("冲突") || normalized.contains("战")) {
//            return "CONFLICT";
//        }
//        if (normalized.contains("CLIMAX") || normalized.contains("高潮") || normalized.contains("爆发")) {
//            return "CLIMAX";
//        }
//        if (normalized.contains("PLOT") || normalized.contains("ADV") || normalized.contains("主线") || normalized.contains("推进")) {
//            return "PLOT";
//        }
//        if (normalized.contains("CHAR") || normalized.contains("人物") || normalized.contains("情") || normalized.contains("EMOTION")) {
//            return "CHARACTER";
//        }
//        if (normalized.contains("RELIEF") || normalized.contains("缓冲") || normalized.contains("日常") || normalized.contains("轻松")) {
//            return "RELIEF";
//        }
//        return normalized.isEmpty() ? "UNKNOWN" : normalized;
//    }
//
//    // 🆕 核心记忆账本写入（内存版简化实现）
//    @Override
//    public void upsertCharacterState(Long novelId, String characterName, String location, String realm, Boolean alive, Integer chapterNumber) {
//        logger.info("🧭 内存版upsertCharacterState: {}@{}", characterName, chapterNumber);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void upsertCharacterStateWithInfo(Long novelId, String characterName, String location, String realm, Boolean alive, String characterInfo, Integer chapterNumber) {
//        logger.info("🧭 内存版upsertCharacterStateWithInfo: {}@{}, info={}", characterName, chapterNumber, characterInfo);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void updateCharacterInventory(Long novelId, String characterName, List<String> items, Integer chapterNumber) {
//        logger.info("💼 内存版updateInventory: {} 持有{}件物品", characterName, items != null ? items.size() : 0);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void upsertCharacterStateComplete(Long novelId, String characterName, Map<String, Object> stateData, Integer chapterNumber) {
//        logger.info("🧭 内存版upsertCharacterStateComplete: {}@{}", characterName, chapterNumber);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void upsertRelationshipState(Long novelId, String characterA, String characterB, String type, Double strength, Integer chapterNumber) {
//        logger.info("🤝 内存版upsertRelationshipState: {}—{}", characterA, characterB);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void upsertRelationshipStateComplete(Long novelId, String characterA, String characterB, Map<String, Object> relationData, Integer chapterNumber) {
//        logger.info("🤝 内存版upsertRelationshipStateComplete: {}—{}", characterA, characterB);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void upsertOpenQuest(Long novelId, String questId, String description, String status, Integer introducedChapter, Integer dueByChapter, Integer lastUpdatedChapter) {
//        logger.info("📌 内存版upsertOpenQuest: {}", questId);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void resolveOpenQuest(Long novelId, String questId, Integer resolvedChapter) {
//        logger.info("✅ 内存版resolveOpenQuest: {}", questId);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void addSummarySignals(Long novelId, Integer chapterNumber, Map<String, String> signals) {
//        logger.info("🧾 内存版addSummarySignals: chapter={}", chapterNumber);
//        // 内存版：简化存储，不实现查询功能
//    }
//
//    @Override
//    public void deleteRelationshipState(Long novelId, String characterA, String characterB) {
//        logger.info("🗑️ 内存版deleteRelationshipState: {}—{} (noop)", characterA, characterB);
//        // 内存版：不存储关系状态，此处为空实现
//    }
//
//    @Override
//    public void deleteCharacterState(Long novelId, String characterName) {
//        logger.info("🗑️ 内存版deleteCharacterState: {} (noop)", characterName);
//        // 内存版：不存储角色状态，此处为空实现
//    }
//
//    @Override
//    public void deleteOpenQuest(Long novelId, String questId) {
//        logger.info("🗑️ 内存版deleteOpenQuest: {} (noop)", questId);
//        // 内存版：不存储任务状态，此处为空实现
//    }
//
//    // 🆕 核心记忆账本查询（内存版空实现）
//    @Override
//    public List<Map<String, Object>> getCharacterStates(Long novelId, Integer limit) {
//        logger.info("✅ 内存版getCharacterStates（返回空）");
//        return Collections.emptyList();
//    }
//
//    @Override
//    public List<Map<String, Object>> getTopRelationships(Long novelId, Integer limit) {
//        logger.info("✅ 内存版getTopRelationships（返回空）");
//        return Collections.emptyList();
//    }
//
//    @Override
//    public List<Map<String, Object>> getOpenQuests(Long novelId, Integer currentChapter) {
//        logger.info("✅ 内存版getOpenQuests（返回空）");
//        return Collections.emptyList();
//    }
//
//    @Override
//    public List<GraphEntity> getCharacterProfiles(Long novelId, Integer limit) {
//        logger.info("✅ 内存版getCharacterProfiles（返回空）");
//        return Collections.emptyList();
//    }
//
//    /**
//     * 查询势力关系网
//     *
//     * 内存版：简化实现
//     */
//    public List<GraphEntity> getFactionRelationships(Long novelId, String factionName, Integer limit) {
//        logger.info("🔍 内存版查询势力关系网: novelId={}, faction={}", novelId, factionName);
//
//        // 内存版：返回空列表
//        return Collections.emptyList();
//    }
//
//    /**
//     * 查询地点层级结构
//     *
//     * 内存版：简化实现
//     */
//    public List<GraphEntity> getLocationHierarchy(Long novelId, String locationName, Integer limit) {
//        logger.info("🔍 内存版查询地点层级结构: novelId={}, location={}", novelId, locationName);
//
//        // 内存版：返回空列表
//        return Collections.emptyList();
//    }
//
//    /**
//     * 获取完整的图谱数据（用于可视化）
//     *
//     * @param novelId 小说ID
//     * @return 包含所有节点和关系的图谱数据
//     */
//    public Map<String, Object> getFullGraphData(Long novelId) {
//        logger.info("🔍 内存版获取完整图谱数据: novelId={}", novelId);
//
//        Map<String, Object> result = new HashMap<>();
//        List<Map<String, Object>> nodes = new ArrayList<>();
//        List<Map<String, Object>> relationships = new ArrayList<>();
//
//        // 获取所有存储的实体
//        List<GraphEntity> storedEntities = novelGraphs.getOrDefault(novelId, new ArrayList<>());
//
//        // 转换实体为节点
//        for (GraphEntity entity : storedEntities) {
//            Map<String, Object> nodeMap = new HashMap<>();
//            nodeMap.put("id", entity.getId());
//            nodeMap.put("type", entity.getType());
//            nodeMap.put("chapterNumber", entity.getChapterNumber());
//            nodeMap.put("relevanceScore", entity.getRelevanceScore());
//            nodeMap.putAll(entity.getProperties());
//            nodes.add(nodeMap);
//        }
//
//        // 内存版简化处理：不生成关系数据
//        // 在实际应用中，如果有关系数据存储，应该在这里处理
//
//        result.put("nodes", nodes);
//        result.put("relationships", relationships);
//
//        logger.info("✅ 内存版获取到{}个节点和{}个关系", nodes.size(), relationships.size());
//        return result;
//    }
//}
//
