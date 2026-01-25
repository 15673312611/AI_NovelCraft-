package com.novel.agentic.controller;

import com.novel.agentic.service.CoreStateExtractor;
import com.novel.agentic.service.graph.EntityExtractionService;
import com.novel.agentic.service.graph.IGraphService;
import com.novel.agentic.util.CollectionUtils;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.VolumeChapterOutline;
import com.novel.dto.AIConfigRequest;
import com.novel.mapper.NovelVolumeMapper;
import com.novel.repository.ChapterRepository;
import com.novel.repository.ChapterSummaryRepository;
import com.novel.repository.VolumeChapterOutlineRepository;
import com.novel.service.ChapterSummaryService;
import com.novel.service.VolumeChapterOutlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 图谱管理控制器
 */
@RestController
@RequestMapping("/agentic/graph")
@CrossOrigin(origins = "*")
public class GraphManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphManagementController.class);

    @Autowired(required = false)
    private IGraphService graphService;
    
    @Autowired(required = false)
    private EntityExtractionService entityExtractionService;

    @Autowired(required = false)
    private CoreStateExtractor coreStateExtractor;
    
    @Autowired
    private ChapterRepository chapterRepository;
    
    @Autowired
    private ChapterSummaryRepository chapterSummaryRepository;
    
    @Autowired
    private ChapterSummaryService chapterSummaryService;

    @Autowired
    private VolumeChapterOutlineService volumeChapterOutlineService;

    @Autowired
    private VolumeChapterOutlineRepository volumeChapterOutlineRepository;

    @Autowired
    private NovelVolumeMapper novelVolumeMapper;
    
    /**
     * 获取图谱统计信息
     */
    @GetMapping("/stats/{novelId}")
    public Map<String, Object> getGraphStats(@PathVariable Long novelId) {
        if (graphService != null) {
            return graphService.getGraphStatistics(novelId);
        }
        return CollectionUtils.mapOf("error", "图谱服务未启用");
    }
    
    /**
     * 手动抽取章节实体
     */
    @PostMapping("/extract")
    public Map<String, Object> extractEntities(@RequestBody Map<String, Object> request) {
        if (entityExtractionService == null) {
            return CollectionUtils.mapOf("error", "实体抽取服务未启用");
        }
        
        Long novelId = ((Number) request.get("novelId")).longValue();
        Integer chapterNumber = ((Number) request.get("chapterNumber")).intValue();
        String chapterTitle = (String) request.get("chapterTitle");
        String content = (String) request.get("content");
        AIConfigRequest aiConfig = extractAIConfig(request);
        
        try {
            entityExtractionService.extractAndSave(novelId, chapterNumber, chapterTitle, content, aiConfig);
            return CollectionUtils.mapOf("status", "success", "message", "实体抽取完成");
        } catch (Exception e) {
            logger.error("实体抽取失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 删除一个角色的最新状态记录
     */
    @DeleteMapping("/character-state")
    public Map<String, Object> deleteCharacterState(
            @RequestParam("novelId") Long novelId,
            @RequestParam("characterName") String characterName) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            graphService.deleteCharacterState(novelId, characterName);
            return CollectionUtils.mapOf("status", "success", "message", "角色状态已删除");
        } catch (Exception e) {
            logger.error("删除角色状态失败: novelId={}, characterName={}", novelId, characterName, e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 更新角色状态（位置 / 境界 / 存活状态 / 人物信息）
     */
    @PutMapping("/character-state")
    public Map<String, Object> updateCharacterState(@RequestBody Map<String, Object> request) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            String characterName = (String) request.get("name");
            String location = (String) request.get("location");
            String realm = (String) request.get("realm");
            String characterInfo = (String) request.get("characterInfo");

            Boolean alive = null;
            Object aliveObj = request.get("alive");
            if (aliveObj instanceof Boolean) {
                alive = (Boolean) aliveObj;
            }

            Number chapterNum = request.get("chapter") instanceof Number ? (Number) request.get("chapter") : null;
            Integer chapterNumber = chapterNum != null ? chapterNum.intValue() : 0;

            graphService.upsertCharacterStateWithInfo(novelId, characterName, location, realm, alive, characterInfo, chapterNumber);
            return CollectionUtils.mapOf("status", "success", "message", "角色状态已更新");
        } catch (Exception e) {
            logger.error("更新角色状态失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    private AIConfigRequest extractAIConfig(Map<String, Object> request) {
        if (request == null) {
            return null;
        }

        Object providerObj = request.get("provider");
        Object apiKeyObj = request.get("apiKey");
        Object modelObj = request.get("model");
        Object baseUrlObj = request.get("baseUrl");

        if (providerObj instanceof String || apiKeyObj instanceof String || modelObj instanceof String || baseUrlObj instanceof String) {
            AIConfigRequest config = new AIConfigRequest();
            if (providerObj instanceof String) {
                config.setProvider(((String) providerObj).trim());
            }
            if (apiKeyObj instanceof String) {
                config.setApiKey(((String) apiKeyObj).trim());
            }
            if (modelObj instanceof String) {
                config.setModel(((String) modelObj).trim());
            }
            if (baseUrlObj instanceof String) {
                config.setBaseUrl(((String) baseUrlObj).trim());
            }
            return config;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> aiConfigMap = request.get("aiConfig") instanceof Map ? (Map<String, Object>) request.get("aiConfig") : null;
        if (aiConfigMap != null) {
            AIConfigRequest config = new AIConfigRequest();
            Object provider = aiConfigMap.get("provider");
            Object apiKey = aiConfigMap.get("apiKey");
            Object model = aiConfigMap.get("model");
            Object baseUrl = aiConfigMap.get("baseUrl");
            if (provider instanceof String) {
                config.setProvider(((String) provider).trim());
            }
            if (apiKey instanceof String) {
                config.setApiKey(((String) apiKey).trim());
            }
            if (model instanceof String) {
                config.setModel(((String) model).trim());
            }
            if (baseUrl instanceof String) {
                config.setBaseUrl(((String) baseUrl).trim());
            }
            return config;
        }

        return null;
    }
    
    /**
     * 清空小说图谱，同时删除章节和概要
     */
    @DeleteMapping("/clear/{novelId}")
    public Map<String, Object> clearGraph(@PathVariable Long novelId) {
        try {
            // 清空图谱
            if (graphService != null) {
                graphService.clearGraph(novelId);
            }
            
            // 删除章节概要
            chapterSummaryRepository.deleteByNovelId(novelId);
            logger.info("已删除小说 {} 的所有章节概要", novelId);
            
            // 删除章节
            int deletedChapters = chapterRepository.deleteByNovelId(novelId);
            logger.info("已删除小说 {} 的 {} 个章节", novelId, deletedChapters);
            
            return CollectionUtils.mapOf(
                "status", "success", 
                "message", "图谱、章节和概要已全部清空",
                "deletedChapters", deletedChapters
            );
        } catch (Exception e) {
            logger.error("清空数据失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }
    
    /**
     * 检查图谱服务状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        String mode = graphService != null ? graphService.getServiceType() : "未启用";
        return CollectionUtils.mapOf(
            "graphEnabled", graphService != null,
            "extractionEnabled", entityExtractionService != null,
            "mode", mode
        );
    }
    
    /**
     * 清空小说的概要和图谱数据，但保留章节内容
     */
    @DeleteMapping("/clear-metadata/{novelId}")
    public Map<String, Object> clearMetadataOnly(@PathVariable Long novelId) {
        try {
            // 清空图谱
            if (graphService != null) {
                graphService.clearGraph(novelId);
                logger.info("✅ 已清空小说 {} 的图谱数据", novelId);
            }
            
            // 删除章节概要
            chapterSummaryRepository.deleteByNovelId(novelId);
            logger.info("✅ 已删除小说 {} 的所有章节概要", novelId);
            
            // 不删除章节内容
            
            return CollectionUtils.mapOf(
                "status", "success", 
                "message", "图谱和概要已清空，章节内容已保留"
            );
        } catch (Exception e) {
            logger.error("清空元数据失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }
    
    /**
     * 获取小说的所有图谱数据
     */
    @GetMapping("/data/{novelId}")
    public Map<String, Object> getGraphData(@PathVariable Long novelId) {
        if (graphService == null) {
            return CollectionUtils.mapOf("error", "图谱服务未启用");
        }

        try {
            Map<String, Object> graphData = graphService.getAllGraphData(novelId);
            logger.info("📊 查询小说 {} 的图谱数据完成", novelId);
            logger.info("  - CharacterStates: {}", graphData.get("totalCharacterStates"));
            logger.info("  - OpenQuests: {}", graphData.get("totalOpenQuests"));
            logger.info("  - RelationshipStates: {}", graphData.get("totalRelationshipStates"));
            return CollectionUtils.mapOf(
                "status", "success",
                "data", graphData
            );
        } catch (Exception e) {
            logger.error("查询图谱数据失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 删除一条关系状态（CharacterState之间的摘要关系）
     */
    @DeleteMapping("/relationship-state")
    public Map<String, Object> deleteRelationshipState(
            @RequestParam("novelId") Long novelId,
            @RequestParam("a") String characterA,
            @RequestParam("b") String characterB) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            graphService.deleteRelationshipState(novelId, characterA, characterB);
            return CollectionUtils.mapOf("status", "success", "message", "关系已删除");
        } catch (Exception e) {
            logger.error("删除关系状态失败: novelId={}, a={}, b={}", novelId, characterA, characterB, e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 更新关系状态（关系类型 / 强度）
     */
    @PutMapping("/relationship-state")
    public Map<String, Object> updateRelationshipState(@RequestBody Map<String, Object> request) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            String characterA = (String) request.get("a");
            String characterB = (String) request.get("b");
            String type = (String) request.get("type");

            Number strengthNum = request.get("strength") instanceof Number ? (Number) request.get("strength") : null;
            Double strength = strengthNum != null ? strengthNum.doubleValue() : null;

            Number chapterNum = request.get("chapter") instanceof Number ? (Number) request.get("chapter") : null;
            Integer chapterNumber = chapterNum != null ? chapterNum.intValue() : 0;

            graphService.upsertRelationshipState(novelId, characterA, characterB, type, strength, chapterNumber);
            return CollectionUtils.mapOf("status", "success", "message", "关系已更新");
        } catch (Exception e) {
            logger.error("更新关系状态失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }
    
    /**
     * 删除一个开放任务
     */
    @DeleteMapping("/open-quest")
    public Map<String, Object> deleteOpenQuest(
            @RequestParam("novelId") Long novelId,
            @RequestParam("id") String questId) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            graphService.deleteOpenQuest(novelId, questId);
            return CollectionUtils.mapOf("status", "success", "message", "任务已删除");
        } catch (Exception e) {
            logger.error("删除任务失败: novelId={}, questId={}", novelId, questId, e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 更新开放任务（描述 / 状态 / 截止章节）
     */
    @PutMapping("/open-quest")
    public Map<String, Object> updateOpenQuest(@RequestBody Map<String, Object> request) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            String questId = (String) request.get("id");
            String description = (String) request.get("description");
            String status = (String) request.get("status");

            Number introducedNum = request.get("introduced") instanceof Number ? (Number) request.get("introduced") : null;
            Integer introducedChapter = introducedNum != null ? introducedNum.intValue() : null;

            Number dueNum = request.get("due") instanceof Number ? (Number) request.get("due") : null;
            Integer dueByChapter = dueNum != null ? dueNum.intValue() : null;

            Number lastNum = request.get("lastUpdated") instanceof Number ? (Number) request.get("lastUpdated") : null;
            Integer lastUpdatedChapter = lastNum != null ? lastNum.intValue() : null;

            graphService.upsertOpenQuest(novelId, questId, description, status, introducedChapter, dueByChapter, lastUpdatedChapter);
            return CollectionUtils.mapOf("status", "success", "message", "任务已更新");
        } catch (Exception e) {
            logger.error("更新任务失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 重新生成指定章节范围的概要和图谱数据
     * @param request {novelId, startChapter, endChapter, aiConfig}
     */
    @PostMapping("/regenerate-metadata")
    public Map<String, Object> regenerateMetadata(@RequestBody Map<String, Object> request) {
        if (entityExtractionService == null) {
            return CollectionUtils.mapOf("error", "实体抽取服务未启用");
        }
        
        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            Integer startChapter = request.containsKey("startChapter") 
                ? ((Number) request.get("startChapter")).intValue() : 1;
            Integer endChapter = request.containsKey("endChapter") 
                ? ((Number) request.get("endChapter")).intValue() : null;
            
            AIConfigRequest aiConfig = extractAIConfig(request);
            if (aiConfig == null || !aiConfig.isValid()) {
                return CollectionUtils.mapOf("error", "AI配置无效");
            }
            
            // 获取章节列表
            List<Chapter> chapters;
            if (endChapter != null) {
                chapters = chapterRepository.findByNovelIdAndChapterNumberBetween(novelId, startChapter, endChapter);
            } else {
                chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
                chapters = chapters.stream()
                    .filter(c -> c.getChapterNumber() >= startChapter)
                    .collect(java.util.stream.Collectors.toList());
            }
            
            if (chapters.isEmpty()) {
                return CollectionUtils.mapOf("error", "未找到指定范围的章节");
            }

            java.util.Set<Long> volumeIds = new java.util.HashSet<>();
            java.util.Map<Long, Boolean> volumeHasMissingContent = new java.util.HashMap<>();
            for (Chapter chapter : chapters) {
                com.novel.domain.entity.NovelVolume volume = novelVolumeMapper.selectByChapterNumber(novelId, chapter.getChapterNumber());
                if (volume != null && volume.getId() != null) {
                    Long volumeId = volume.getId();
                    volumeIds.add(volumeId);

                    boolean missing = (chapter.getContent() == null || chapter.getContent().trim().isEmpty());
                    Boolean prev = volumeHasMissingContent.get(volumeId);
                    if (prev == null) {
                        volumeHasMissingContent.put(volumeId, missing);
                    } else if (!prev && missing) {
                        volumeHasMissingContent.put(volumeId, true);
                    }
                }
            }

            for (Long volumeId : volumeIds) {
                Boolean hasMissing = volumeHasMissingContent.get(volumeId);
                if (hasMissing == null || !hasMissing) {
                    continue;
                }
                try {
                    volumeChapterOutlineService.generateOutlinesForVolume(volumeId, null, aiConfig);
                    logger.info("✅ 卷{}章纲已重新生成", volumeId);
                } catch (Exception e) {
                    logger.error("❌ 卷{}章纲重新生成失败", volumeId, e);
                }
            }
            
            logger.info("🔄 开始重新生成元数据: novelId={}, 章节范围={}-{}, 共{}章", 
                novelId, startChapter, endChapter, chapters.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (Chapter chapter : chapters) {
                try {
                    // 生成概要
                    chapterSummaryService.generateOrUpdateSummary(chapter, aiConfig);

                    if (coreStateExtractor != null && chapter.getContent() != null && chapter.getContent().length() >= 100) {
                        try {
                            coreStateExtractor.extractAndSaveCoreState(
                                    novelId,
                                    chapter.getChapterNumber(),
                                    chapter.getContent(),
                                    chapter.getTitle(),
                                    aiConfig
                            );
                        } catch (Exception e) {
                            logger.error("核心状态抽取失败: novelId={}, chapter={}", novelId, chapter.getChapterNumber(), e);
                        }
                    }
                    
                    // 抽取实体并入图
                    entityExtractionService.extractAndSave(
                        novelId, 
                        chapter.getChapterNumber(), 
                        chapter.getTitle(), 
                        chapter.getContent(),
                        aiConfig
                    );

                    try {
                        VolumeChapterOutline existingOutline =
                                volumeChapterOutlineRepository.findByNovelAndGlobalChapter(
                                        novelId, chapter.getChapterNumber());
                        if (existingOutline == null) {
                            volumeChapterOutlineService.generateOutlineFromChapterContent(chapter, aiConfig);
                        } else {
                            logger.info("⏭️ 章节章纲已存在，跳过重写: novelId={}, chapter={}", novelId, chapter.getChapterNumber());
                        }
                    } catch (Exception e) {
                        logger.error("章节章纲生成失败: novelId={}, chapter={}", novelId, chapter.getChapterNumber(), e);
                    }
                    
                    successCount++;
                    logger.info("✅ 第{}章元数据生成完成", chapter.getChapterNumber());
                    
                    // 避免API限流
                    Thread.sleep(2000);
                    
                } catch (Exception e) {
                    failCount++;
                    logger.error("❌ 第{}章元数据生成失败", chapter.getChapterNumber(), e);
                }
            }
            
            return CollectionUtils.mapOf(
                "status", "success",
                "message", "元数据重新生成完成",
                "totalChapters", chapters.size(),
                "successCount", successCount,
                "failCount", failCount
            );
            
        } catch (Exception e) {
            logger.error("重新生成元数据失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 只重新生成图谱（清空现有图谱，不修改章节概要和章纲）
     * @param request {novelId, startChapter, endChapter, aiConfig}
     */
    @PostMapping("/regenerate-graph")
    public Map<String, Object> regenerateGraphOnly(@RequestBody Map<String, Object> request) {
        if (entityExtractionService == null) {
            return CollectionUtils.mapOf("error", "实体抽取服务未启用");
        }

        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            Integer startChapter = request.containsKey("startChapter")
                ? ((Number) request.get("startChapter")).intValue() : 1;
            Integer endChapter = request.containsKey("endChapter")
                ? ((Number) request.get("endChapter")).intValue() : null;

            AIConfigRequest aiConfig = extractAIConfig(request);
            if (aiConfig == null || !aiConfig.isValid()) {
                return CollectionUtils.mapOf("error", "AI配置无效");
            }

            // 1. 计算需要重建的章节范围
            List<Chapter> chapters;
            if (endChapter != null) {
                chapters = chapterRepository.findByNovelIdAndChapterNumberBetween(novelId, startChapter, endChapter);
            } else {
                chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
                chapters = chapters.stream()
                    .filter(c -> c.getChapterNumber() >= startChapter)
                    .collect(java.util.stream.Collectors.toList());
            }

            if (chapters.isEmpty()) {
                return CollectionUtils.mapOf("error", "未找到指定范围的章节");
            }

            logger.info("🔄 开始重新生成图谱: novelId={}, 章节范围={}-{}, 共{}章",
                novelId, startChapter, endChapter, chapters.size());

            // 2. 强制清理传入章节范围的所有图谱数据（不做历史章节保护判断）
            if (graphService != null) {
                try {
                    logger.info("🗑️ 强制清理小说{} 第{}-{}章的所有图谱节点...", novelId, startChapter, endChapter != null ? endChapter : "最后");
                    
                    // 获取需要清理的章节号列表
                    java.util.List<Integer> chapterNumbersToDelete = chapters.stream()
                        .filter(c -> c != null && c.getChapterNumber() != null)
                        .map(Chapter::getChapterNumber)
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (!chapterNumbersToDelete.isEmpty()) {
                        // 逐章清理图谱数据
                        for (Chapter chapter : chapters) {
                            try {
                                Integer chapterNumber = chapter.getChapterNumber();
                                if (chapterNumber != null) {
                                    graphService.deleteChapterEntities(novelId, chapterNumber);
                                    logger.info("🗑️ 已清理小说{} 第{}章的旧图谱数据", novelId, chapterNumber);
                                }
                            } catch (Exception e) {
                                logger.warn("清理第{}章图谱数据时出错（忽略，继续重建）", chapter.getChapterNumber(), e);
                            }
                        }
                        logger.info("✅ 已清理小说{} 指定章节范围的图谱数据", novelId);
                    }
                } catch (Exception e) {
                    logger.error("清理图谱数据失败（忽略，继续重建）", e);
                }
            } else {
                return CollectionUtils.mapOf("error", "图谱服务未启用");
            }

            int successCount = 0;
            int failCount = 0;

            for (Chapter chapter : chapters) {
                try {
                    // 核心状态抽取（写入 CharacterState / RelationshipState / OpenQuest）
                    if (coreStateExtractor != null && chapter.getContent() != null && chapter.getContent().length() >= 100) {
                        try {
                            coreStateExtractor.extractAndSaveCoreState(
                                novelId,
                                chapter.getChapterNumber(),
                                chapter.getContent(),
                                chapter.getTitle(),
                                aiConfig
                            );
                        } catch (Exception e) {
                            logger.error("核心状态抽取失败: novelId={}, chapter={}", novelId, chapter.getChapterNumber(), e);
                        }
                    }

                    // 结构化实体抽取（写入事件/伏笔/因果关系等图谱实体）
                    entityExtractionService.extractAndSave(
                        novelId,
                        chapter.getChapterNumber(),
                        chapter.getTitle(),
                        chapter.getContent(),
                        aiConfig
                    );

                    successCount++;
                    logger.info("✅ 第{}章图谱重建完成", chapter.getChapterNumber());

                    // 避免API限流
                    Thread.sleep(2000);

                } catch (Exception e) {
                    failCount++;
                    logger.error("❌ 第{}章图谱重建失败", chapter.getChapterNumber(), e);
                }
            }

            return CollectionUtils.mapOf(
                "status", "success",
                "message", "图谱重新生成完成",
                "totalChapters", chapters.size(),
                "successCount", successCount,
                "failCount", failCount
            );

        } catch (Exception e) {
            logger.error("重新生成图谱失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }
}


