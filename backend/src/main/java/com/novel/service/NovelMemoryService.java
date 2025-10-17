package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelCharacterProfile;
import com.novel.domain.entity.NovelChronicle;
import com.novel.domain.entity.NovelForeshadowing;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.ChapterSummary;
import com.novel.domain.entity.NovelWorldDictionary;
import com.novel.repository.NovelCharacterProfileRepository;
import com.novel.repository.NovelChronicleRepository;
import com.novel.repository.NovelForeshadowingRepository;
import com.novel.repository.ChapterRepository;
import com.novel.repository.ChapterSummaryRepository;
import com.novel.repository.NovelWorldDictionaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 小说记忆库服务
 * 负责从数据库装配记忆库数据，提供给AI写作使用
 */
@Service
public class NovelMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(NovelMemoryService.class);

    @Autowired
    private NovelCharacterProfileRepository characterProfileRepository;

    @Autowired
    private NovelChronicleRepository chronicleRepository;

    @Autowired
    private NovelForeshadowingRepository foreshadowingRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private ChapterSummaryRepository chapterSummaryRepository;

    @Autowired
    private NovelWorldDictionaryRepository worldDictionaryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 根据小说ID从数据库装配完整的记忆库
     */
    public Map<String, Object> buildMemoryBankFromDatabase(Long novelId) {
        logger.info("🧠 从数据库装配记忆库: 小说ID={}", novelId);

        Map<String, Object> memoryBank = new HashMap<>();

        try {
            // 1. 装配角色档案
            Map<String, Object> characterProfiles = buildCharacterProfiles(novelId);
            memoryBank.put("characterProfiles", characterProfiles);

            // 2. 装配大事年表
            List<Map<String, Object>> chronicle = buildChronicle(novelId);
            memoryBank.put("chronicle", chronicle);

            // 3. 装配伏笔追踪
            List<Map<String, Object>> foreshadowing = buildForeshadowing(novelId);
            memoryBank.put("foreshadowing", foreshadowing);

            // 4. 最近章节摘要（用于保持剧情连贯）
            try {
                List<Chapter> latest = chapterRepository.findLatestChapterByNovel(novelId, 3);
                List<Map<String, Object>> recentChapters = new ArrayList<>();
                for (Chapter ch : latest) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("chapterNumber", ch.getChapterNumber());
                    m.put("title", ch.getTitle());
                    String content = ch.getContent();
                    if (content != null) {
                        // 只提供前 800 字的片段，避免提示过长
                        m.put("contentSnippet", content.substring(0, Math.min(800, content.length())));
                    }
                    recentChapters.add(m);
                }
                memoryBank.put("recentChapters", recentChapters);
            } catch (Exception ignore) {}

            // 5. 兼容旧版消费者所需的关键键名
            // 5.1 将 characterProfiles 映射为 characters（简化结构，供 CharacterManagementService 使用）
            Map<String, Object> characters = buildCharactersFromProfiles(characterProfiles);
            memoryBank.put("characters", characters);

            // 5.2 最近章节概括列表（供前情概括/主角状态等模块使用）——改为最近20章
            memoryBank.put("chapterSummaries", buildRecentSummaries(novelId, 20));

            // 5.3 世界观设定（high-level），从世界观词典粗略拼装，兼容 ContextManagementService 的 worldSettings 读取
            memoryBank.put("worldSettings", buildWorldSettings(novelId));

            // 6. 基础信息
            memoryBank.put("novelId", novelId);
            memoryBank.put("lastUpdatedTime", new Date());
            memoryBank.put("version", 1);

            logger.info("✅ 记忆库装配完成 - 角色: {}个, 事件: {}个, 伏笔: {}个",
                       characterProfiles.size(), chronicle.size(), foreshadowing.size());

        } catch (Exception e) {
            logger.error("记忆库装配失败", e);
            // 返回空的基础结构
            memoryBank.put("characterProfiles", new HashMap<>());
            memoryBank.put("chronicle", new ArrayList<>());
            memoryBank.put("foreshadowing", new ArrayList<>());
        }

        return memoryBank;
    }

    /**
     * 构建角色档案数据
     */
    private Map<String, Object> buildCharacterProfiles(Long novelId) {
        Map<String, Object> profiles = new HashMap<>();

        try {
            List<NovelCharacterProfile> characters = characterProfileRepository.findByNovelId(novelId);

            for (NovelCharacterProfile character : characters) {
                Map<String, Object> profile = new HashMap<>();
                profile.put("name", character.getName());
                profile.put("firstAppearance", character.getFirstAppearance());
                profile.put("lastAppearance", character.getLastAppearance());
                profile.put("appearanceCount", character.getAppearanceCount());
                profile.put("status", character.getStatus());
                profile.put("statusChangeChapter", character.getStatusChangeChapter());
                profile.put("importanceScore", character.getImportanceScore());

                // 解析JSON字段
                if (character.getPersonalityTraits() != null) {
                    try {
                        List<String> traits = objectMapper.readValue(character.getPersonalityTraits(), new TypeReference<List<String>>() {});
                        profile.put("personalityTraits", traits);
                    } catch (Exception e) {
                        profile.put("personalityTraits", new ArrayList<>());
                    }
                }

                if (character.getKeyEvents() != null) {
                    try {
                        List<String> events = objectMapper.readValue(character.getKeyEvents(), new TypeReference<List<String>>() {});
                        profile.put("keyEvents", events);
                    } catch (Exception e) {
                        profile.put("keyEvents", new ArrayList<>());
                    }
                }

                if (character.getRelationships() != null) {
                    try {
                        Map<String, String> relationships = objectMapper.readValue(character.getRelationships(), new TypeReference<Map<String, String>>() {});
                        profile.put("relationships", relationships);
                    } catch (Exception e) {
                        profile.put("relationships", new HashMap<>());
                    }
                }

                if (character.getActionsHistory() != null) {
                    try {
                        List<String> actions = objectMapper.readValue(character.getActionsHistory(), new TypeReference<List<String>>() {});
                        profile.put("actionsHistory", actions);
                    } catch (Exception e) {
                        profile.put("actionsHistory", new ArrayList<>());
                    }
                }

                profiles.put(character.getName(), profile);
            }

        } catch (Exception e) {
            logger.error("构建角色档案失败", e);
        }

        return profiles;
    }

    /**
     * 构建大事年表数据
     */
    private List<Map<String, Object>> buildChronicle(Long novelId) {
        List<Map<String, Object>> chronicle = new ArrayList<>();

        try {
            List<NovelChronicle> events = chronicleRepository.findByNovelId(novelId);

            for (NovelChronicle event : events) {
                Map<String, Object> eventMap = new HashMap<>();
                eventMap.put("chapterNumber", event.getChapterNumber());
                eventMap.put("timelineInfo", event.getTimelineInfo());
                eventMap.put("eventType", event.getEventType());
                eventMap.put("importanceLevel", event.getImportanceLevel());

                // 解析事件列表
                if (event.getEvents() != null) {
                    try {
                        List<String> eventList = objectMapper.readValue(event.getEvents(), new TypeReference<List<String>>() {});
                        eventMap.put("events", eventList);
                    } catch (Exception e) {
                        eventMap.put("events", new ArrayList<>());
                    }
                }

                chronicle.add(eventMap);
            }

        } catch (Exception e) {
            logger.error("构建大事年表失败", e);
        }

        return chronicle;
    }

    /**
     * 构建伏笔追踪数据
     */
    private List<Map<String, Object>> buildForeshadowing(Long novelId) {
        List<Map<String, Object>> foreshadowing = new ArrayList<>();

        try {
            List<NovelForeshadowing> items = foreshadowingRepository.findByNovelId(novelId);

            for (NovelForeshadowing item : items) {
                Map<String, Object> foreshadowingMap = new HashMap<>();
                foreshadowingMap.put("content", item.getContent());
                foreshadowingMap.put("plantedChapter", item.getPlantedChapter());
                foreshadowingMap.put("resolvedChapter", item.getResolvedChapter());
                foreshadowingMap.put("status", item.getStatus());
                foreshadowingMap.put("type", item.getType());
                foreshadowingMap.put("priority", item.getPriority());
                foreshadowingMap.put("contextInfo", item.getContextInfo());

                foreshadowing.add(foreshadowingMap);
            }

        } catch (Exception e) {
            logger.error("构建伏笔追踪失败", e);
        }

        return foreshadowing;
    }

    /**
     * 将角色档案映射为简化的 characters 结构，兼容旧消费端
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildCharactersFromProfiles(Map<String, Object> characterProfiles) {
        Map<String, Object> characters = new HashMap<>();
        if (characterProfiles == null) return characters;
        for (Map.Entry<String, Object> entry : characterProfiles.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> profile = (Map<String, Object>) entry.getValue();
            Map<String, Object> c = new HashMap<>();
            c.put("status", profile.getOrDefault("status", "ACTIVE"));
            c.put("appearanceCount", profile.getOrDefault("appearanceCount", 0));
            c.put("lastAppearance", profile.getOrDefault("lastAppearance", 0));
            c.put("traits", profile.getOrDefault("personalityTraits", Collections.emptyList()));
            c.put("relationships", profile.getOrDefault("relationships", Collections.emptyMap()));
            characters.put(name, c);
        }
        return characters;
    }

    /**
     * 构建最近 N 章的概括列表（按章节升序）
     */
    private List<Map<String, Object>> buildRecentSummaries(Long novelId, int limit) {
        try {
            List<ChapterSummary> all = chapterSummaryRepository.findByNovelIdOrderByChapterNumber(novelId);
            if (all == null || all.isEmpty()) return new ArrayList<>();
            int from = Math.max(0, all.size() - limit);
            List<ChapterSummary> recent = all.subList(from, all.size());
            return recent.stream().map(s -> {
                Map<String, Object> m = new HashMap<>();
                m.put("chapterNumber", s.getChapterNumber());
                m.put("summary", s.getSummary());
                return m;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 基于世界观词典粗略拼装 worldSettings（供 ContextManagementService 消费）
     */
    private Map<String, Object> buildWorldSettings(Long novelId) {
        Map<String, Object> ws = new HashMap<>();
        try {
            List<NovelWorldDictionary> entries = worldDictionaryRepository.findByNovelId(novelId);
            if (entries == null || entries.isEmpty()) return ws;

            // 地理/地点
            List<Map<String, Object>> keyLocations = new ArrayList<>();
            StringBuilder geography = new StringBuilder();
            StringBuilder powerSystem = new StringBuilder();
            for (NovelWorldDictionary e : entries) {
                String type = e.getType();
                if ("GEOGRAPHY".equalsIgnoreCase(type)) {
                    geography.append(e.getTerm()).append(": ").append(e.getDescription()).append("；");
                    Map<String, Object> loc = new HashMap<>();
                    loc.put("name", e.getTerm());
                    loc.put("description", e.getDescription());
                    keyLocations.add(loc);
                } else if ("POWER_SYSTEM".equalsIgnoreCase(type)) {
                    powerSystem.append(e.getTerm()).append(": ").append(e.getDescription()).append("；");
                }
            }
            if (geography.length() > 0) ws.put("geography", geography.toString());
            if (powerSystem.length() > 0) ws.put("powerSystem", powerSystem.toString());
            if (!keyLocations.isEmpty()) ws.put("keyLocations", keyLocations);
        } catch (Exception ignore) {}
        return ws;
    }

/**
     * 根据章节号生成章节规划
     * 这里可以根据小说大纲或其他逻辑生成章节规划
     */
    public Map<String, Object> generateChapterPlan(Long novelId, Integer chapterNumber) {
        logger.info("📋 生成章节规划: 小说ID={}, 章节={}", novelId, chapterNumber);

        Map<String, Object> chapterPlan = new HashMap<>();
        chapterPlan.put("chapterNumber", chapterNumber);
        chapterPlan.put("title", "第" + chapterNumber + "章");
        chapterPlan.put("type", "剧情推进");
        chapterPlan.put("estimatedWords", 3000);
        chapterPlan.put("focus", "推进主线剧情");
        chapterPlan.put("keyPoints", Arrays.asList("情节发展", "角色互动", "伏笔设置"));

        return chapterPlan;
    }
}