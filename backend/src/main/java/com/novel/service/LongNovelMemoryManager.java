package com.novel.service;

import com.novel.domain.entity.Novel;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.NovelCharacterProfile;
import com.novel.domain.entity.NovelChronicle;
import com.novel.domain.entity.NovelForeshadowing;
import com.novel.domain.entity.NovelWorldDictionary;
import com.novel.repository.NovelCharacterProfileRepository;
import com.novel.repository.NovelChronicleRepository;
import com.novel.repository.NovelForeshadowingRepository;
import com.novel.repository.NovelWorldDictionaryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

/**
 * 长篇小说记忆管理系统
 * 解决AI写到后面忘记前面、前后不一致、重复创建角色等问题
 * 
 * 包含4个自动模块：
 * 1. 角色档案库 - CharacterProfileModule
 * 2. 大事年表 - ChronicleModule  
 * 3. 伏笔追踪表 - ForeshadowingModule
 * 4. 世界观词典 - WorldDictionaryModule
 */
@Service
public class LongNovelMemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(LongNovelMemoryManager.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ChapterService chapterService;
    
    @Autowired
    private AIWritingService aiWritingService;
    
    @Autowired
    private NovelMemoryService novelMemoryService;
    
    @Autowired
    private NovelCharacterProfileRepository characterProfileRepository;
    
    @Autowired
    private NovelChronicleRepository chronicleRepository;
    
    @Autowired
    private NovelForeshadowingRepository foreshadowingRepository;
    
    @Autowired
    private NovelWorldDictionaryRepository worldDictionaryRepository;
    
    @Autowired
    private com.novel.repository.ChapterSummaryRepository chapterSummaryRepository;
    
    @Autowired
    private ChapterSummaryService chapterSummaryService;

    /**
     * 从章节内容自动更新记忆管理系统（使用后端配置 - 已弃用）
     * @param novelId 小说ID
     * @param chapterNumber 章节号
     * @param chapterContent 章节内容
     * @param currentMemoryBank 当前记忆库
     * @return 更新后的记忆库
     * @deprecated 建议使用 {@link #updateMemoryFromChapter(Long, Integer, String, Map, com.novel.dto.AIConfigRequest)}
     */
    @Deprecated
    public Map<String, Object> updateMemoryFromChapter(
            Long novelId, 
            Integer chapterNumber, 
            String chapterContent,
            Map<String, Object> currentMemoryBank) {
        
        logger.info("🧠 开始更新长篇记忆系统（使用后端配置） - 小说ID: {}, 第{}章", novelId, chapterNumber);
        
        // 确保记忆库结构完整
        Map<String, Object> memoryBank = ensureMemoryBankStructure(currentMemoryBank);
        
        // 异步调用AI提取章节信息
        CompletableFuture<Map<String, Object>> aiExtractionFuture = extractChapterInfoWithAIAsync(
            novelId, chapterNumber, chapterContent, memoryBank, null);
        
        try {
            // 等待AI提取完成（这里可以根据需要改为非阻塞方式）
            Map<String, Object> extractedInfo = aiExtractionFuture.get();
            
            // 将AI提取的信息合并到记忆库
            mergeAIExtractedInfo(memoryBank, extractedInfo);
            
            // 执行冲突检测
            Map<String, List<String>> conflicts = detectConflicts(memoryBank);
            memoryBank.put("conflictDetection", conflicts);
            
            // 更新系统元信息
            memoryBank.put("lastUpdatedChapter", chapterNumber);
            memoryBank.put("lastUpdatedTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            memoryBank.put("memoryVersion", (Integer) memoryBank.getOrDefault("memoryVersion", 0) + 1);
            
            // 🚀 将更新后的记忆库存储到数据库
            saveMemoryBankToDatabase(novelId, memoryBank, chapterNumber);
            
        } catch (Exception e) {
            logger.error("❌ AI提取章节信息失败: {}", e.getMessage(), e);
            // 如果AI提取失败，可以回退到原来的逻辑或记录错误
        }
        
        logger.info("✅ 记忆系统更新完成 - 角色: {}个, 事件: {}个, 伏笔: {}个, 设定: {}个", 
                   getSize(memoryBank, "characterProfiles"),
                   getSize(memoryBank, "chronicle"),
                   getSize(memoryBank, "foreshadowing"),
                   getSize(memoryBank, "worldDictionary"));
        
        return memoryBank;
    }
    


    /**
     * 构建写作前的上下文包
     * @param upToChapter 截止到第几章的记忆
     * @return 上下文包字符串
     */
    public String buildContextPackage( Integer upToChapter) {
        StringBuilder context = new StringBuilder();

        
        return context.toString();
    }

    // ================================
    // 1. 角色档案库模块
    // ================================

    @SuppressWarnings("unchecked")
    private void updateCharacterProfiles(Map<String, Object> memoryBank, Integer chapterNumber, String content) {
        Map<String, Object> profiles = (Map<String, Object>) memoryBank.get("characterProfiles");
        
        // 提取角色信息
        List<Map<String, Object>> extractedCharacters = extractCharactersFromContent(content, chapterNumber);
        
        for (Map<String, Object> character : extractedCharacters) {
            String name = (String) character.get("name");
            
            if (profiles.containsKey(name)) {
                // 更新已有角色
                Map<String, Object> existingProfile = (Map<String, Object>) profiles.get(name);
                updateExistingCharacterProfile(existingProfile, character, chapterNumber);
            } else {
                // 创建新角色档案
                Map<String, Object> newProfile = createNewCharacterProfile(character, chapterNumber);
                profiles.put(name, newProfile);
                logger.info("📝 新角色档案创建: {} (第{}章)", name, chapterNumber);
            }
        }
    }

    private List<Map<String, Object>> extractCharactersFromContent(String content, Integer chapterNumber) {
        List<Map<String, Object>> characters = new ArrayList<>();
        
        // 使用多种模式识别角色
        String[] namePatterns = {
            "([\\u4e00-\\u9fa5]{2,4})说道?[：:]",  // 中文名字+说
            "([\\u4e00-\\u9fa5]{2,4})道[：:]",     // 中文名字+道
            "([\\u4e00-\\u9fa5]{2,4})笑[着了]?道",   // 中文名字+笑道
            "([\\u4e00-\\u9fa5]{2,4})看[着了向]",   // 中文名字+看
            "([\\u4e00-\\u9fa5]{2,4})的[脸眼手]"    // 中文名字+的+身体部位
        };
        
        Set<String> foundNames = new HashSet<>();
        for (String patternStr : namePatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String name = matcher.group(1);
                // 过滤常见词汇和代词
                if (!isCommonWord(name)) {
                    foundNames.add(name);
                }
            }
        }
        
        // 为每个角色创建基础信息
        for (String name : foundNames) {
            Map<String, Object> character = new HashMap<>();
            character.put("name", name);
            character.put("firstAppearance", chapterNumber);
            
            // 提取该角色在本章的关键信息
            String characterContext = extractCharacterContext(content, name);
            character.put("chapterContext", characterContext);
            character.put("actions", extractCharacterActions(content, name));
            character.put("status", determineCharacterStatus(content, name));
            
            characters.add(character);
        }
        
        return characters;
    }

    private boolean isCommonWord(String word) {
        String[] commonWords = {
            "这个", "那个", "什么", "怎么", "为什么", "因为", "所以", "但是", "然后", "现在",
            "时候", "地方", "东西", "事情", "问题", "方法", "结果", "开始", "结束", "继续"
        };
        return Arrays.asList(commonWords).contains(word);
    }

    private String extractCharacterContext(String content, String name) {
        // 提取角色周围100字符的上下文
        int index = content.indexOf(name);
        if (index == -1) return "";
        
        int start = Math.max(0, index - 50);
        int end = Math.min(content.length(), index + name.length() + 50);
        return content.substring(start, end).replace("\n", " ");
    }

    private List<String> extractCharacterActions(String content, String name) {
        List<String> actions = new ArrayList<>();
        String[] actionPatterns = {
            name + "([走跑跳飞移动进出入离开到达]{1,2})",
            name + "([说道喊叫骂]{1,2})",
            name + "([拿取放抓握持]{1,2})",
            name + "([打击斩砍劈刺]{1,2})",
            name + "([想思考虑]{1,2})"
        };
        
        for (String patternStr : actionPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                actions.add(matcher.group(1));
                if (actions.size() >= 5) break; // 限制数量
            }
        }
        
        return actions;
    }

    private String determineCharacterStatus(String content, String name) {
        if (content.contains(name + "死了") || content.contains(name + "死亡")) {
            return "DEAD";
        } else if (content.contains(name + "离开") || content.contains(name + "走了")) {
            return "ABSENT";
        } else if (content.contains(name + "受伤") || content.contains(name + "负伤")) {
            return "INJURED";
        }
        return "ACTIVE";
    }

    @SuppressWarnings("unchecked")
    private void updateExistingCharacterProfile(Map<String, Object> profile, Map<String, Object> newInfo, Integer chapterNumber) {
        // 更新最后出现章节
        profile.put("lastAppearance", chapterNumber);
        
        // 累计出现次数
        profile.put("appearanceCount", (Integer) profile.getOrDefault("appearanceCount", 0) + 1);
        
        // 更新状态
        String newStatus = (String) newInfo.get("status");
        if (!"ACTIVE".equals(newStatus)) {
            profile.put("status", newStatus);
            profile.put("statusChangeChapter", chapterNumber);
        }
        
        // 添加新的行为
        List<String> actions = (List<String>) profile.getOrDefault("actions", new ArrayList<>());
        List<String> newActions = (List<String>) newInfo.get("actions");
        actions.addAll(newActions);
        profile.put("actions", actions.subList(Math.max(0, actions.size() - 10), actions.size())); // 保留最近10个行为
    }

    private Map<String, Object> createNewCharacterProfile(Map<String, Object> character, Integer chapterNumber) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", character.get("name"));
        profile.put("firstAppearance", chapterNumber);
        profile.put("lastAppearance", chapterNumber);
        profile.put("appearanceCount", 1);
        profile.put("status", character.get("status"));
        profile.put("actions", character.get("actions"));
        profile.put("keyEvents", new ArrayList<>());
        profile.put("relationships", new HashMap<>());
        profile.put("personalityTraits", new ArrayList<>());
        profile.put("createdTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return profile;
    }

    @SuppressWarnings("unchecked")
    private String buildCharacterProfilesContext(Map<String, Object> memoryBank, Integer upToChapter) {
        StringBuilder context = new StringBuilder();
        context.append("📋 角色档案库 (截止第").append(upToChapter).append("章)\n");
        
        Map<String, Object> profiles = (Map<String, Object>) memoryBank.get("characterProfiles");
        
        // 按重要性排序（出现次数 + 最后出现章节）
        List<Map.Entry<String, Object>> sortedProfiles = new ArrayList<>();
        for (Map.Entry<String, Object> entry : profiles.entrySet()) {
            Map<String, Object> profile = (Map<String, Object>) entry.getValue();
            Integer lastAppear = (Integer) profile.getOrDefault("lastAppearance", 0);
            if (lastAppear <= upToChapter) {
                sortedProfiles.add(entry);
            }
        }
        
        sortedProfiles.sort((a, b) -> {
            Map<String, Object> profileA = (Map<String, Object>) a.getValue();
            Map<String, Object> profileB = (Map<String, Object>) b.getValue();
            
            int scoreA = (Integer) profileA.getOrDefault("appearanceCount", 0) + 
                        (Integer) profileA.getOrDefault("lastAppearance", 0) / 10;
            int scoreB = (Integer) profileB.getOrDefault("appearanceCount", 0) + 
                        (Integer) profileB.getOrDefault("lastAppearance", 0) / 10;
            
            return Integer.compare(scoreB, scoreA);
        });
        
        // 输出前15个重要角色
        int count = 0;
        for (Map.Entry<String, Object> entry : sortedProfiles) {
            if (count >= 15) break;
            
            String name = entry.getKey();
            Map<String, Object> profile = (Map<String, Object>) entry.getValue();
            
            context.append("• ").append(name)
                   .append(" (").append(profile.get("status")).append(")")
                   .append(" [第").append(profile.get("firstAppearance")).append("-")
                   .append(profile.get("lastAppearance")).append("章, 出现")
                   .append(profile.get("appearanceCount")).append("次]");
            
            List<String> actions = (List<String>) profile.get("actions");
            if (!actions.isEmpty()) {
                context.append(" - 近期行为: ").append(String.join(",", actions.subList(Math.max(0, actions.size()-3), actions.size())));
            }
            context.append("\n");
            count++;
        }
        
        context.append("\n");
        return context.toString();
    }

    // ================================
    // 2. 大事年表模块
    // ================================

    @SuppressWarnings("unchecked")
    private void updateChronicle(Map<String, Object> memoryBank, Integer chapterNumber, String content) {
        List<Map<String, Object>> chronicle = (List<Map<String, Object>>) memoryBank.get("chronicle");
        
        // 提取本章关键事件
        List<String> keyEvents = extractKeyEventsFromContent(content);
        
        if (!keyEvents.isEmpty()) {
            Map<String, Object> chapterRecord = new HashMap<>();
            chapterRecord.put("chapter", chapterNumber);
            chapterRecord.put("events", keyEvents);
            chapterRecord.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // 检测时间线关键词
            String timelineInfo = extractTimelineInfo(content);
            if (!timelineInfo.isEmpty()) {
                chapterRecord.put("timelineInfo", timelineInfo);
            }
            
            chronicle.add(chapterRecord);
            logger.info("📅 大事年表更新: 第{}章记录{}个事件", chapterNumber, keyEvents.size());
        }
    }

    private List<String> extractKeyEventsFromContent(String content) {
        List<String> events = new ArrayList<>();
        
        // 事件关键词模式
        String[] eventPatterns = {
            "([^。！？]*)(死了|死亡|去世)([^。！？]*[。！？])",          // 死亡事件
            "([^。！？]*)(结婚|成婚|大婚)([^。！？]*[。！？])",         // 婚姻事件
            "([^。！？]*)(觉醒|突破|晋级|升级)([^。！？]*[。！？])",      // 成长事件
            "([^。！？]*)(战斗|打斗|厮杀|决战)([^。！？]*[。！？])",      // 战斗事件
            "([^。！？]*)(发现|找到|获得)([^。！？]*[。！？])",         // 发现事件
            "([^。！？]*)(离开|前往|到达|抵达)([^。！？]*[。！？])",      // 移动事件
            "([^。！？]*)(决定|打算|计划)([^。！？]*[。！？])"          // 决策事件
        };
        
        for (String patternStr : eventPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String event = matcher.group(0).trim();
                if (event.length() > 5 && event.length() < 100) { // 长度合理的事件
                    events.add(event);
                    if (events.size() >= 5) break; // 每章最多5个关键事件
                }
            }
        }
        
        return events;
    }

    private String extractTimelineInfo(String content) {
        String[] timePatterns = {
            "([0-9]+)年后",
            "([0-9]+)个月后", 
            "([0-9]+)天后",
            "第二天",
            "次日",
            "一周后",
            "一个月后",
            "半年后"
        };
        
        for (String patternStr : timePatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String buildChronicleContext(Map<String, Object> memoryBank, Integer upToChapter) {
        StringBuilder context = new StringBuilder();
        context.append("📅 大事年表 (截止第").append(upToChapter).append("章)\n");
        
        List<Map<String, Object>> chronicle = (List<Map<String, Object>>) memoryBank.get("chronicle");
        
        // 过滤并排序
        List<Map<String, Object>> relevantRecords = chronicle.stream()
                .filter(record -> (Integer) record.get("chapter") <= upToChapter)
                .sorted((a, b) -> Integer.compare((Integer) a.get("chapter"), (Integer) b.get("chapter")))
                .collect(Collectors.toList());
        
        for (Map<String, Object> record : relevantRecords) {
            context.append("第").append(record.get("chapter")).append("章: ");
            List<String> events = (List<String>) record.get("events");
            context.append(String.join("; ", events));
            
            String timelineInfo = (String) record.get("timelineInfo");
            if (timelineInfo != null && !timelineInfo.isEmpty()) {
                context.append(" [时间: ").append(timelineInfo).append("]");
            }
            context.append("\n");
        }
        
        context.append("\n");
        return context.toString();
    }

    // ================================
    // 3. 伏笔追踪表模块
    // ================================

    @SuppressWarnings("unchecked")
    private void updateForeshadowing(Map<String, Object> memoryBank, Integer chapterNumber, String content) {
        List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) memoryBank.get("foreshadowing");
        
        // 检测新的伏笔
        List<String> newForeshadowing = detectNewForeshadowing(content);
        for (String hint : newForeshadowing) {
            Map<String, Object> foreshadowRecord = new HashMap<>();
            foreshadowRecord.put("content", hint);
            foreshadowRecord.put("plantedChapter", chapterNumber);
            foreshadowRecord.put("status", "ACTIVE");
            foreshadowRecord.put("type", classifyForeshadowing(hint));
            foreshadowing.add(foreshadowRecord);
            logger.info("🎭 新伏笔发现: {} (第{}章)", hint, chapterNumber);
        }
        
        // 检测是否有伏笔被回收
        for (Map<String, Object> record : foreshadowing) {
            if ("ACTIVE".equals(record.get("status"))) {
                String foreshadowContent = (String) record.get("content");
                if (isForechadowResolved(content, foreshadowContent)) {
                    record.put("status", "RESOLVED");
                    record.put("resolvedChapter", chapterNumber);
                    logger.info("✅ 伏笔回收: {} (第{}章)", foreshadowContent, chapterNumber);
                }
            }
        }
    }

    private List<String> detectNewForeshadowing(String content) {
        List<String> foreshadowing = new ArrayList<>();
        
        // 伏笔关键词模式
        String[] foreshadowPatterns = {
            "([^。！？]*)(奇怪|神秘|诡异|异常)([^。！？]*[。！？])",      // 异常现象
            "([^。！？]*)(似乎|好像|仿佛|就像)([^。！？]*[。！？])",      // 模糊描述
            "([^。！？]*)(突然|忽然)([^。！？]*[。！？])",             // 突然事件
            "([^。！？]*)(预感|感觉|直觉)([^。！？]*[。！？])",         // 预感类
            "([^。！？]*)(秘密|隐瞒|不可告人)([^。！？]*[。！？])",      // 秘密类
            "([^。！？]*)(将来|以后|终有一天)([^。！？]*[。！？])"       // 未来暗示
        };
        
        for (String patternStr : foreshadowPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String hint = matcher.group(0).trim();
                if (hint.length() > 5 && hint.length() < 80) {
                    foreshadowing.add(hint);
                    if (foreshadowing.size() >= 3) break; // 每章最多3个伏笔
                }
            }
        }
        
        return foreshadowing;
    }

    private String classifyForeshadowing(String content) {
        if (content.contains("死") || content.contains("亡")) return "DEATH";
        if (content.contains("爱") || content.contains("情")) return "ROMANCE";
        if (content.contains("战") || content.contains("斗")) return "CONFLICT";
        if (content.contains("秘") || content.contains("谜")) return "MYSTERY";
        if (content.contains("力") || content.contains("能")) return "POWER";
        return "OTHER";
    }

    private boolean isForechadowResolved(String content, String foreshadowContent) {
        // 简单的伏笔回收检测 - 可以根据需要扩展更复杂的逻辑
        String[] keyWords = foreshadowContent.split("[，。！？；]");
        int matchCount = 0;
        for (String word : keyWords) {
            if (word.length() > 1 && content.contains(word)) {
                matchCount++;
            }
        }
        return matchCount >= 2; // 至少2个关键词匹配
    }

    @SuppressWarnings("unchecked")
    private String buildForeshadowingContext(Map<String, Object> memoryBank, Integer upToChapter) {
        StringBuilder context = new StringBuilder();
        context.append("🎭 伏笔追踪表 (截止第").append(upToChapter).append("章)\n");
        
        List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) memoryBank.get("foreshadowing");
        
        // 活跃伏笔
        context.append("🔥 活跃伏笔:\n");
        List<Map<String, Object>> activeForeshadowing = foreshadowing.stream()
                .filter(f -> "ACTIVE".equals(f.get("status")) && (Integer) f.get("plantedChapter") <= upToChapter)
                .sorted((a, b) -> Integer.compare((Integer) a.get("plantedChapter"), (Integer) b.get("plantedChapter")))
                .collect(Collectors.toList());
        
        for (Map<String, Object> f : activeForeshadowing) {
            context.append("• [第").append(f.get("plantedChapter")).append("章] ")
                   .append(f.get("content"))
                   .append(" (").append(f.get("type")).append(")\n");
        }
        
        // 已回收伏笔
        context.append("✅ 已回收伏笔:\n");
        List<Map<String, Object>> resolvedForeshadowing = foreshadowing.stream()
                .filter(f -> "RESOLVED".equals(f.get("status")) && (Integer) f.get("resolvedChapter") <= upToChapter)
                .sorted((a, b) -> Integer.compare((Integer) b.get("resolvedChapter"), (Integer) a.get("resolvedChapter")))
                .limit(5) // 最近5个
                .collect(Collectors.toList());
        
        for (Map<String, Object> f : resolvedForeshadowing) {
            context.append("• [第").append(f.get("plantedChapter")).append("章→第")
                   .append(f.get("resolvedChapter")).append("章] ")
                   .append(f.get("content")).append("\n");
        }
        
        context.append("\n");
        return context.toString();
    }

    // ================================
    // 4. 世界观词典模块
    // ================================

    @SuppressWarnings("unchecked")
    private void updateWorldDictionary(Map<String, Object> memoryBank, Integer chapterNumber, String content) {
        Map<String, Object> worldDictionary = (Map<String, Object>) memoryBank.get("worldDictionary");
        
        // 提取地理信息
        updateGeographyTerms(worldDictionary, content, chapterNumber);
        
        // 提取力量体系
        updatePowerSystemTerms(worldDictionary, content, chapterNumber);
        
        // 提取势力组织
        updateOrganizationTerms(worldDictionary, content, chapterNumber);
        
        // 提取特殊物品
        updateItemTerms(worldDictionary, content, chapterNumber);
    }

    @SuppressWarnings("unchecked")
    private void updateGeographyTerms(Map<String, Object> worldDictionary, String content, Integer chapterNumber) {
        Map<String, Object> geography = (Map<String, Object>) worldDictionary.getOrDefault("geography", new HashMap<>());
        
        String[] geoPatterns = {
            "([\\u4e00-\\u9fa5]{2,6})(城|村|镇|山|河|湖|海|林|谷|峰|岛)",
            "([\\u4e00-\\u9fa5]{2,6})(学院|宗门|门派|教派|帮派)",
            "([\\u4e00-\\u9fa5]{2,6})(国|州|郡|县|府|域|界|境)"
        };
        
        for (String patternStr : geoPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String term = matcher.group(0);
                if (!geography.containsKey(term)) {
                    Map<String, Object> termInfo = new HashMap<>();
                    termInfo.put("firstMention", chapterNumber);
                    termInfo.put("type", "GEOGRAPHY");
                    termInfo.put("description", extractTermContext(content, term));
                    geography.put(term, termInfo);
                    logger.info("🗺️ 新地理词条: {} (第{}章)", term, chapterNumber);
                }
            }
        }
        
        worldDictionary.put("geography", geography);
    }

    @SuppressWarnings("unchecked")
    private void updatePowerSystemTerms(Map<String, Object> worldDictionary, String content, Integer chapterNumber) {
        Map<String, Object> powerSystem = (Map<String, Object>) worldDictionary.getOrDefault("powerSystem", new HashMap<>());
        
        String[] powerPatterns = {
            "([\\u4e00-\\u9fa5]{2,4})(境|级|层|阶|段)",
            "([\\u4e00-\\u9fa5]{2,6})(功法|心法|秘籍|武技|法术|神通)",
            "([\\u4e00-\\u9fa5]{2,4})(真气|灵气|法力|内力|元力|斗气)"
        };
        
        for (String patternStr : powerPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String term = matcher.group(0);
                if (!powerSystem.containsKey(term)) {
                    Map<String, Object> termInfo = new HashMap<>();
                    termInfo.put("firstMention", chapterNumber);
                    termInfo.put("type", "POWER_SYSTEM");
                    termInfo.put("description", extractTermContext(content, term));
                    powerSystem.put(term, termInfo);
                    logger.info("⚡ 新力量体系词条: {} (第{}章)", term, chapterNumber);
                }
            }
        }
        
        worldDictionary.put("powerSystem", powerSystem);
    }

    @SuppressWarnings("unchecked")
    private void updateOrganizationTerms(Map<String, Object> worldDictionary, String content, Integer chapterNumber) {
        Map<String, Object> organizations = (Map<String, Object>) worldDictionary.getOrDefault("organizations", new HashMap<>());
        
        String[] orgPatterns = {
            "([\\u4e00-\\u9fa5]{2,8})(宗|门|派|教|帮|会|组织|联盟|公会)"
        };
        
        for (String patternStr : orgPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String term = matcher.group(0);
                if (!organizations.containsKey(term)) {
                    Map<String, Object> termInfo = new HashMap<>();
                    termInfo.put("firstMention", chapterNumber);
                    termInfo.put("type", "ORGANIZATION");
                    termInfo.put("description", extractTermContext(content, term));
                    organizations.put(term, termInfo);
                    logger.info("🏛️ 新势力词条: {} (第{}章)", term, chapterNumber);
                }
            }
        }
        
        worldDictionary.put("organizations", organizations);
    }

    @SuppressWarnings("unchecked")
    private void updateItemTerms(Map<String, Object> worldDictionary, String content, Integer chapterNumber) {
        Map<String, Object> items = (Map<String, Object>) worldDictionary.getOrDefault("items", new HashMap<>());
        
        String[] itemPatterns = {
            "([\\u4e00-\\u9fa5]{2,6})(剑|刀|枪|戟|鼎|印|珠|石|玉|镜)",
            "([\\u4e00-\\u9fa5]{2,6})(丹|药|符|阵|卷|册|书|经)"
        };
        
        for (String patternStr : itemPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String term = matcher.group(0);
                if (!items.containsKey(term)) {
                    Map<String, Object> termInfo = new HashMap<>();
                    termInfo.put("firstMention", chapterNumber);
                    termInfo.put("type", "ITEM");
                    termInfo.put("description", extractTermContext(content, term));
                    items.put(term, termInfo);
                    logger.info("🎒 新物品词条: {} (第{}章)", term, chapterNumber);
                }
            }
        }
        
        worldDictionary.put("items", items);
    }

    private String extractTermContext(String content, String term) {
        int index = content.indexOf(term);
        if (index == -1) return "";
        
        int start = Math.max(0, index - 30);
        int end = Math.min(content.length(), index + term.length() + 30);
        return content.substring(start, end).replace("\n", " ");
    }

    @SuppressWarnings("unchecked")
    private String buildWorldDictionaryContext(Map<String, Object> memoryBank) {
        StringBuilder context = new StringBuilder();
        context.append("🌍 世界观词典\n");
        
        Map<String, Object> worldDictionary = (Map<String, Object>) memoryBank.get("worldDictionary");
        
        // 地理词汇
        Map<String, Object> geography = (Map<String, Object>) worldDictionary.getOrDefault("geography", new HashMap<>());
        if (!geography.isEmpty()) {
            context.append("🗺️ 地理: ");
            context.append(String.join(", ", geography.keySet())).append("\n");
        }
        
        // 力量体系
        Map<String, Object> powerSystem = (Map<String, Object>) worldDictionary.getOrDefault("powerSystem", new HashMap<>());
        if (!powerSystem.isEmpty()) {
            context.append("⚡ 力量体系: ");
            context.append(String.join(", ", powerSystem.keySet())).append("\n");
        }
        
        // 组织势力
        Map<String, Object> organizations = (Map<String, Object>) worldDictionary.getOrDefault("organizations", new HashMap<>());
        if (!organizations.isEmpty()) {
            context.append("🏛️ 势力组织: ");
            context.append(String.join(", ", organizations.keySet())).append("\n");
        }
        
        // 特殊物品
        Map<String, Object> items = (Map<String, Object>) worldDictionary.getOrDefault("items", new HashMap<>());
        if (!items.isEmpty()) {
            context.append("🎒 特殊物品: ");
            context.append(String.join(", ", items.keySet())).append("\n");
        }
        
        context.append("\n");
        return context.toString();
    }

    // ================================
    // 5. 冲突检测模块
    // ================================

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> detectConflicts(Map<String, Object> memoryBank) {
        Map<String, List<String>> conflicts = new HashMap<>();
        
        // 角色冲突检测
        List<String> characterConflicts = detectCharacterConflicts(memoryBank);
        if (!characterConflicts.isEmpty()) {
            conflicts.put("characterConflicts", characterConflicts);
        }
        
        // 时间线冲突检测
        List<String> timelineConflicts = detectTimelineConflicts(memoryBank);
        if (!timelineConflicts.isEmpty()) {
            conflicts.put("timelineConflicts", timelineConflicts);
        }
        
        // 设定冲突检测
        List<String> settingConflicts = detectSettingConflicts(memoryBank);
        if (!settingConflicts.isEmpty()) {
            conflicts.put("settingConflicts", settingConflicts);
        }
        
        return conflicts;
    }

    @SuppressWarnings("unchecked")
    private List<String> detectCharacterConflicts(Map<String, Object> memoryBank) {
        List<String> conflicts = new ArrayList<>();
        
        Map<String, Object> profiles = (Map<String, Object>) memoryBank.get("characterProfiles");
        
        for (Map.Entry<String, Object> entry : profiles.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> profile = (Map<String, Object>) entry.getValue();
            String status = (String) profile.get("status");
            Integer statusChangeChapter = (Integer) profile.get("statusChangeChapter");
            Integer lastAppearance = (Integer) profile.get("lastAppearance");
            
            // 检测死人复活
            if ("DEAD".equals(status) && statusChangeChapter != null && lastAppearance > statusChangeChapter) {
                conflicts.add("角色 " + name + " 在第" + statusChangeChapter + "章死亡，但在第" + lastAppearance + "章再次出现");
            }
        }
        
        return conflicts;
    }

    @SuppressWarnings("unchecked")
    private List<String> detectTimelineConflicts(Map<String, Object> memoryBank) {
        List<String> conflicts = new ArrayList<>();
        // 可以扩展更复杂的时间线冲突检测逻辑
        return conflicts;
    }

    @SuppressWarnings("unchecked")
    private List<String> detectSettingConflicts(Map<String, Object> memoryBank) {
        List<String> conflicts = new ArrayList<>();
        // 可以扩展设定冲突检测逻辑
        return conflicts;
    }

    @SuppressWarnings("unchecked")
    private String buildConflictWarnings(Map<String, Object> memoryBank) {
        StringBuilder context = new StringBuilder();
        
        Map<String, List<String>> conflicts = (Map<String, List<String>>) memoryBank.get("conflictDetection");
        if (conflicts == null || conflicts.isEmpty()) {
            return "";
        }
        
        context.append("⚠️ 冲突警告\n");
        
        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            String conflictType = entry.getKey();
            List<String> conflictList = entry.getValue();
            
            if (!conflictList.isEmpty()) {
                context.append("🔴 ").append(conflictType).append(":\n");
                for (String conflict : conflictList) {
                    context.append("• ").append(conflict).append("\n");
                }
            }
        }
        
        context.append("\n");
        return context.toString();
    }

    // ================================
    // 辅助方法
    // ================================

    private Map<String, Object> ensureMemoryBankStructure(Map<String, Object> currentMemoryBank) {
        Map<String, Object> memoryBank = currentMemoryBank != null ? currentMemoryBank : new HashMap<>();
        
        // 确保所有模块存在
        memoryBank.putIfAbsent("characterProfiles", new HashMap<>());
        memoryBank.putIfAbsent("chronicle", new ArrayList<>());
        memoryBank.putIfAbsent("foreshadowing", new ArrayList<>());
        memoryBank.putIfAbsent("worldDictionary", new HashMap<>());
        
        // 初始化世界词典子结构
        @SuppressWarnings("unchecked")
        Map<String, Object> worldDict = (Map<String, Object>) memoryBank.get("worldDictionary");
        worldDict.putIfAbsent("geography", new HashMap<>());
        worldDict.putIfAbsent("powerSystem", new HashMap<>());
        worldDict.putIfAbsent("organizations", new HashMap<>());
        worldDict.putIfAbsent("items", new HashMap<>());
        
        return memoryBank;
    }

    private int getSize(Map<String, Object> memoryBank, String key) {
        Object obj = memoryBank.get(key);
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).size();
        } else if (obj instanceof List) {
            return ((List<?>) obj).size();
        }
        return 0;
    }

    // ================================
    // AI异步提取方法
    // ================================

    /**
     * 提取章节信息（同步方法，不需要记忆库）
     * 只针对当前章节内容进行信息提取
     */
    public Map<String, Object> extractChapterInfo(
            Long novelId, 
            Integer chapterNumber, 
            String chapterContent,
            com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("🤖 开始提取第{}章信息, 使用AI配置={}", chapterNumber, aiConfig != null);
        
        try {
            // 构建AI提示词，提取当前章节的信息（不需要记忆库）
            String optimizedContent = ChapterTextOptimizer.sampleForAi(chapterContent, 8000);
            String prompt = buildChapterAnalysisPrompt(chapterNumber, optimizedContent, null);
            
            // 调用AI服务提取内容
            String aiResponse;
            aiResponse = aiWritingService.generateContent(prompt, "chapter_memory_extraction", aiConfig);
            
            // 解析AI返回的JSON格式信息
            Map<String, Object> extractedInfo = parseAIResponse(aiResponse);
            
            logger.info("✅ 第{}章信息提取完成", chapterNumber);
            return extractedInfo;
            
        } catch (Exception e) {
            logger.error("❌ 提取第{}章信息失败: {}", chapterNumber, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 保存提取的信息到记忆库（数据库）
     */
    @SuppressWarnings("unchecked")
    public void saveExtractedInfoToMemory(Long novelId, Integer chapterNumber, Map<String, Object> extractedInfo) {
        logger.info("💾 开始保存第{}章提取信息到记忆库", chapterNumber);
        
        try {
            // 0. 保存章节概括到 chapter_summaries 表
            if (extractedInfo.containsKey("chapterSummary")) {
                String chapterSummary = (String) extractedInfo.get("chapterSummary");
                if (chapterSummary != null && !chapterSummary.trim().isEmpty()) {
                    chapterSummaryService.saveChapterSummary(novelId, chapterNumber, chapterSummary);
                    logger.info("✅ 章节概括已保存到 chapter_summaries 表: 第{}章", chapterNumber);
                }
            }
            
            // 1. 保存角色信息
            if (extractedInfo.containsKey("characterUpdates")) {
                List<Map<String, Object>> characterUpdates = (List<Map<String, Object>>) extractedInfo.get("characterUpdates");
                saveCharacterProfiles(novelId, chapterNumber, characterUpdates);
            }
            
            // 2. 保存事件信息
            if (extractedInfo.containsKey("eventUpdates")) {
                List<Map<String, Object>> eventUpdates = (List<Map<String, Object>>) extractedInfo.get("eventUpdates");
                saveChronicleEvents(novelId, chapterNumber, eventUpdates);
            }
            
            // 3. 保存伏笔信息
            if (extractedInfo.containsKey("foreshadowingUpdates")) {
                List<Map<String, Object>> foreshadowingUpdates = (List<Map<String, Object>>) extractedInfo.get("foreshadowingUpdates");
                saveForeshadowing(novelId, chapterNumber, foreshadowingUpdates);
            }
            
            // 4. 保存世界观信息
            if (extractedInfo.containsKey("worldviewUpdates")) {
                List<Map<String, Object>> worldviewUpdates = (List<Map<String, Object>>) extractedInfo.get("worldviewUpdates");
                saveWorldDictionary(novelId, chapterNumber, worldviewUpdates);
            }
            
            // 5. 保存世界实体信息（势力、地点、物品）
            if (extractedInfo.containsKey("worldEntities")) {
                List<Map<String, Object>> worldEntities = (List<Map<String, Object>>) extractedInfo.get("worldEntities");
                saveWorldEntities(novelId, chapterNumber, worldEntities);
            }
            
            logger.info("✅ 第{}章信息已保存到记忆库", chapterNumber);
            
        } catch (Exception e) {
            logger.error("❌ 保存第{}章信息到记忆库失败: {}", chapterNumber, e.getMessage(), e);
        }
    }

    /**
     * 保存角色档案到数据库
     */
    private void saveCharacterProfiles(Long novelId, Integer chapterNumber, List<Map<String, Object>> characterUpdates) {
        if (characterUpdates == null || characterUpdates.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> charData : characterUpdates) {
            try {
                String name = (String) charData.get("name");
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                
                // 查找是否已存在该角色
                NovelCharacterProfile profile = characterProfileRepository.findByNovelIdAndName(novelId, name);
                
                if (profile == null) {
                    // 新角色 - 创建
                    profile = new NovelCharacterProfile();
                    profile.setNovelId(novelId);
                    profile.setName(name);
                    profile.setFirstAppearance(chapterNumber);
                    profile.setLastAppearance(chapterNumber);
                    profile.setAppearanceCount(1);
                    profile.setStatus("ACTIVE");
                } else {
                    // 已存在角色 - 更新
                    profile.setLastAppearance(chapterNumber);
                    profile.setAppearanceCount(profile.getAppearanceCount() + 1);
                }
                
                // 更新角色详细信息
                if (charData.containsKey("coreTrait")) {
                    profile.setPersonalityTraits(objectMapper.writeValueAsString(charData.get("coreTrait")));
                }
                
                if (profile.getId() == null) {
                    characterProfileRepository.insert(profile);
                } else {
                    characterProfileRepository.updateById(profile);
                }
                logger.debug("保存角色: {}", name);
                
            } catch (Exception e) {
                logger.warn("保存角色信息失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 保存事件到大事年表
     */
    private void saveChronicleEvents(Long novelId, Integer chapterNumber, List<Map<String, Object>> eventUpdates) {
        if (eventUpdates == null || eventUpdates.isEmpty()) {
            return;
        }
        
        try {
            NovelChronicle chronicle = new NovelChronicle();
            chronicle.setNovelId(novelId);
            chronicle.setChapterNumber(chapterNumber);
            chronicle.setEvents(objectMapper.writeValueAsString(eventUpdates));
            chronicle.setImportanceLevel(5); // 默认重要程度
            
            chronicleRepository.insert(chronicle);
            logger.debug("保存{}个事件", eventUpdates.size());
            
        } catch (Exception e) {
            logger.warn("保存事件失败: {}", e.getMessage());
        }
    }

    /**
     * 保存伏笔信息
     */
    private void saveForeshadowing(Long novelId, Integer chapterNumber, List<Map<String, Object>> foreshadowingUpdates) {
        if (foreshadowingUpdates == null || foreshadowingUpdates.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> foreshadow : foreshadowingUpdates) {
            try {
                String content = (String) foreshadow.get("content");
                if (content == null || content.trim().isEmpty()) {
                    continue;
                }
                
                NovelForeshadowing entity = new NovelForeshadowing();
                entity.setNovelId(novelId);
                entity.setContent(content);
                entity.setPlantedChapter(chapterNumber);
                entity.setStatus("ACTIVE");
                entity.setPriority(5); // 默认优先级
                
                foreshadowingRepository.insert(entity);
                logger.debug("保存伏笔: {}", content.substring(0, Math.min(20, content.length())));
                
            } catch (Exception e) {
                logger.warn("保存伏笔失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 保存世界观词典
     */
    private void saveWorldDictionary(Long novelId, Integer chapterNumber, List<Map<String, Object>> worldviewUpdates) {
        if (worldviewUpdates == null || worldviewUpdates.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> worldData : worldviewUpdates) {
            try {
                String term = (String) worldData.get("term");
                if (term == null || term.trim().isEmpty()) {
                    continue;
                }
                
                // 查找是否已存在
                NovelWorldDictionary dict = worldDictionaryRepository.findByNovelIdAndTerm(novelId, term);
                
                if (dict == null) {
                    // 新词条
                    dict = new NovelWorldDictionary();
                    dict.setNovelId(novelId);
                    dict.setTerm(term);
                    dict.setFirstMention(chapterNumber);
                    dict.setUsageCount(1);
                    dict.setType("CONCEPT"); // 默认类型
                    
                    if (worldData.containsKey("description")) {
                        dict.setDescription((String) worldData.get("description"));
                    }
                }  else {
                    // 已存在，更新使用次数
                    dict.setUsageCount(dict.getUsageCount() + 1);
                }
                
                if (dict.getId() == null) {
                    worldDictionaryRepository.insert(dict);
                } else {
                    worldDictionaryRepository.updateById(dict);
                }
                logger.debug("保存世界观词条: {}", term);
                
            } catch (Exception e) {
                logger.warn("保存世界观词条失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 保存世界实体（势力、地点、物品）
     */
    private void saveWorldEntities(Long novelId, Integer chapterNumber, List<Map<String, Object>> worldEntities) {
        if (worldEntities == null || worldEntities.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> entity : worldEntities) {
            try {
                String name = (String) entity.get("name");
                String type = (String) entity.get("type");
                
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                
                // 查找是否已存在
                NovelWorldDictionary dict = worldDictionaryRepository.findByNovelIdAndTerm(novelId, name);
                
                if (dict == null) {
                    dict = new NovelWorldDictionary();
                    dict.setNovelId(novelId);
                    dict.setTerm(name);
                    dict.setFirstMention(chapterNumber);
                    dict.setUsageCount(1);
                    
                    // 设置类型
                    if ("ORGANIZATION".equals(type)) {
                        dict.setType("ORGANIZATION");
                    } else if ("LOCATION".equals(type)) {
                        dict.setType("GEOGRAPHY");
                    } else if ("ARTIFACT".equals(type)) {
                        dict.setType("ITEM");
                    } else {
                        dict.setType("CONCEPT");
                    }
                    
                    if (entity.containsKey("hookLine")) {
                        dict.setDescription((String) entity.get("hookLine"));
                    }
                    
                    dict.setIsImportant(true); // 标记为重要设定
                } else {
                    dict.setUsageCount(dict.getUsageCount() + 1);
                }
                
                if (dict.getId() == null) {
                    worldDictionaryRepository.insert(dict);
                } else {
                    worldDictionaryRepository.updateById(dict);
                }
                logger.debug("保存世界实体: {} ({})", name, type);
                
            } catch (Exception e) {
                logger.warn("保存世界实体失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 异步调用AI提取章节信息（支持AI配置参数）
     * 一次性提取角色、事件、伏笔、世界观等所有信息
     */
    @Async("novelTaskExecutor")
    public CompletableFuture<Map<String, Object>> extractChapterInfoWithAIAsync(
            Long novelId, 
            Integer chapterNumber, 
            String chapterContent,
            Map<String, Object> memoryBank,
            com.novel.dto.AIConfigRequest aiConfig) {
        
        logger.info("🤖 开始AI异步提取第{}章信息, 使用AI配置={}", chapterNumber, aiConfig != null);
        
        try {
            // 构建AI提示词，一次性提取所有需要的信息
            String optimizedContent = ChapterTextOptimizer.sampleForAi(chapterContent, 8000);
            String prompt = buildChapterAnalysisPrompt(chapterNumber, optimizedContent, memoryBank);
            
            // 调用AI服务提取内容
            String aiResponse;
            aiResponse = aiWritingService.generateContent(prompt, "chapter_memory_extraction", aiConfig);
            
            // 解析AI返回的JSON格式信息
            Map<String, Object> extractedInfo = parseAIResponse(aiResponse);
            
            logger.info("✅ AI提取第{}章信息完成", chapterNumber);
            return CompletableFuture.completedFuture(extractedInfo);
            
        } catch (Exception e) {
            logger.error("❌ AI提取第{}章信息失败: {}", chapterNumber, e.getMessage(), e);
            return CompletableFuture.completedFuture(new HashMap<>());
        }
    }

    /**
     * 构建章节分析的AI提示词
     */
    private String buildChapterAnalysisPrompt(Integer chapterNumber, String chapterContent, Map<String, Object> memoryBank) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下章节内容，提取关键信息并返回JSON格式结果。\n\n");
        prompt.append("章节号：第").append(chapterNumber).append("章\n");
        prompt.append("章节内容：\n").append(chapterContent).append("\n\n");
        
        prompt.append("请提取以下信息并以JSON格式返回：\n");
        prompt.append("1. 角色信息（重要！需智能分类与赋能）：\n");
        prompt.append("   对每个出场角色，需提供：\n");
        prompt.append("   - name: 角色名\n");
        prompt.append("   - roleTag: 角色类型(PROTAGONIST|ANTAGONIST|MAJOR|SUPPORT|CAMEO)\n");
        prompt.append("     * PROTAGONIST=主角，ANTAGONIST=主要对手，MAJOR=长期重要配角\n");
        prompt.append("     * SUPPORT=短期配角(有剧情作用但非长期)，CAMEO=龙套/路人(戏份<10%)\n");
        prompt.append("   - influenceScore: 对主线影响0-100（CAMEO通常<20，SUPPORT 20-50，MAJOR≥60）\n");
        prompt.append("   - screenTime: 本章戏份占比0-1（CAMEO<0.1，SUPPORT 0.1-0.3，MAJOR≥0.3）\n");
        prompt.append("   - returnProbability: 再登场可能性0-1（CAMEO<0.3，SUPPORT 0.3-0.6，MAJOR≥0.7）\n");
        prompt.append("   - 【首次/信息不全角色需赋能】coreTrait(核心性格特点)、speechStyle(说话风格)、desire(长期欲望)、hookLine(一句话抓人简介10-30字)\n");
        prompt.append("   - linksToProtagonist: 与主角的关联(债务/承诺/冲突/共同目标等，一句话说明)\n");
        prompt.append("   - triggerConditions: 何时该登场(满足哪些事件/地点/线索才正面出现，龙套默认\"无需触发\")\n");
        prompt.append("2. 事件信息：重要事件、时间节点、情节发展\n");
        prompt.append("3. 伏笔信息：新埋下的伏笔、伏笔的回收、暗示信息\n");
        prompt.append("4. 世界观信息：新的设定、规则变化、环境描述\n");
        prompt.append("5. 主角状态（重要！）：\n");
        prompt.append("   - 当前境界/等级/修为\n");
        prompt.append("   - 掌握的技能/法术/武学\n");
        prompt.append("   - 拥有的装备/法宝/物品\n");
        prompt.append("   - 当前所在位置\n");
        prompt.append("   - 当前目标/任务\n");
        prompt.append("   - 重要关系变化（朋友/敌人/师长）\n");
        prompt.append("6. 章节概括：用100-200字概括本章主要情节和发展\n");
        prompt.append("7. 实体抽取（新增！）：\n");
        prompt.append("   【势力组织】首次/重要出现的门派、宗门、家族、组织、帮派等\n");
        prompt.append("   【场景地点】首次/重要出现的城市、秘境、建筑、地理标志等\n");
        prompt.append("   【重要物件】首次/重要出现的法宝、武器、丹药、秘籍、特殊物品等\n");
        prompt.append("   - 每个实体需提供：name、type(ORGANIZATION|LOCATION|ARTIFACT)、hookLine(一句话高密度有趣简介，10-30字)、influenceScore(对剧情影响0-100)、relatedCharacters(关联角色名)\n\n");
        
        prompt.append("严格遵守返回格式要求：\n");
        prompt.append("{\n");
        prompt.append("  \"characterUpdates\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"角色名\",\n");
        prompt.append("      \"roleTag\": \"PROTAGONIST/ANTAGONIST/MAJOR/SUPPORT/CAMEO\",\n");
        prompt.append("      \"influenceScore\": 80,\n");
        prompt.append("      \"screenTime\": 0.4,\n");
        prompt.append("      \"returnProbability\": 0.8,\n");
        prompt.append("      \"coreTrait\": \"核心性格(首次/信息不全时补充)\",\n");
        prompt.append("      \"speechStyle\": \"说话风格\",\n");
        prompt.append("      \"desire\": \"长期欲望\",\n");
        prompt.append("      \"hookLine\": \"一句话抓人简介\",\n");
        prompt.append("      \"linksToProtagonist\": \"与主角的关联\",\n");
        prompt.append("      \"triggerConditions\": \"触发登场条件\",\n");
        prompt.append("      \"status\": \"当前状态描述\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"eventUpdates\": [事件更新列表],\n");
        prompt.append("  \"foreshadowingUpdates\": [伏笔更新列表],\n");
        prompt.append("  \"worldviewUpdates\": [世界观更新列表],\n");
        prompt.append("  \"protagonistStatus\": {\n");
        prompt.append("    \"realm\": \"当前境界\",\n");
        prompt.append("    \"skills\": [\"技能1\", \"技能2\"],\n");
        prompt.append("    \"equipment\": [\"装备1\", \"装备2\"],\n");
        prompt.append("    \"location\": \"当前位置\",\n");
        prompt.append("    \"currentGoal\": \"当前目标\",\n");
        prompt.append("    \"relationships\": {\"角色名\": \"关系\"}\n");
        prompt.append("  },\n");
        prompt.append("  \"chapterSummary\": \"章节概括文字\",\n");
        prompt.append("  \"worldEntities\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"实体名称\",\n");
        prompt.append("      \"type\": \"ORGANIZATION/LOCATION/ARTIFACT\",\n");
        prompt.append("      \"hookLine\": \"一句话简介\",\n");
        prompt.append("      \"influenceScore\": 80,\n");
        prompt.append("      \"relatedCharacters\": [\"角色1\", \"角色2\"]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("请确保返回的是有效的JSON格式，不要包含其他文字说明。");
        
        return prompt.toString();
    }

    /**
     * 解析AI返回的响应
     */
    private Map<String, Object> parseAIResponse(String aiResponse) {
        try {
            // 尝试直接解析JSON
            return objectMapper.readValue(aiResponse, Map.class);
        } catch (Exception e) {
            logger.warn("AI响应解析失败，尝试提取JSON部分: {}", e.getMessage());
            
            // 如果直接解析失败，尝试提取JSON部分
            try {
                // 查找JSON开始和结束的位置
                int start = aiResponse.indexOf('{');
                int end = aiResponse.lastIndexOf('}');
                
                if (start >= 0 && end > start) {
                    String jsonPart = aiResponse.substring(start, end + 1);
                    return objectMapper.readValue(jsonPart, Map.class);
                }
            } catch (Exception ex) {
                logger.error("无法解析AI响应: {}", aiResponse);
            }
            
            // 返回空结果
            return new HashMap<>();
        }
    }

    /**
     * 将AI提取的信息合并到记忆库
     */
    @SuppressWarnings("unchecked")
    private void mergeAIExtractedInfo(Map<String, Object> memoryBank, Map<String, Object> extractedInfo) {
        // 合并角色信息
        if (extractedInfo.containsKey("characterUpdates")) {
            List<Map<String, Object>> characterUpdates = (List<Map<String, Object>>) extractedInfo.get("characterUpdates");
            updateCharacterProfilesFromAI(memoryBank, characterUpdates);
        }
        
        // 合并事件信息
        if (extractedInfo.containsKey("eventUpdates")) {
            List<Map<String, Object>> eventUpdates = (List<Map<String, Object>>) extractedInfo.get("eventUpdates");
            updateChronicleFromAI(memoryBank, eventUpdates);
        }
        
        // 合并伏笔信息
        if (extractedInfo.containsKey("foreshadowingUpdates")) {
            List<Map<String, Object>> foreshadowingUpdates = (List<Map<String, Object>>) extractedInfo.get("foreshadowingUpdates");
            updateForeshadowingFromAI(memoryBank, foreshadowingUpdates);
        }
        
        // 合并世界观信息
        if (extractedInfo.containsKey("worldviewUpdates")) {
            List<Map<String, Object>> worldviewUpdates = (List<Map<String, Object>>) extractedInfo.get("worldviewUpdates");
            updateWorldDictionaryFromAI(memoryBank, worldviewUpdates);
        }
        
        // 🆕 更新主角状态
        if (extractedInfo.containsKey("protagonistStatus")) {
            Map<String, Object> protagonistStatus = (Map<String, Object>) extractedInfo.get("protagonistStatus");
            memoryBank.put("protagonistStatus", protagonistStatus);
            logger.info("✅ 更新主角状态: 境界={}, 位置={}, 目标={}", 
                       protagonistStatus.get("realm"), 
                       protagonistStatus.get("location"), 
                       protagonistStatus.get("currentGoal"));
        }
        
        // 🆕 添加章节概括到列表
        if (extractedInfo.containsKey("chapterSummary")) {
            String chapterSummary = (String) extractedInfo.get("chapterSummary");
            Integer chapterNumber = (Integer) memoryBank.get("lastUpdatedChapter");
            
            // 获取或创建章节概括列表
            List<Map<String, Object>> chapterSummaries = 
                (List<Map<String, Object>>) memoryBank.getOrDefault("chapterSummaries", new ArrayList<>());
            
            Map<String, Object> summaryEntry = new HashMap<>();
            summaryEntry.put("chapterNumber", chapterNumber);
            summaryEntry.put("summary", chapterSummary);
            summaryEntry.put("createdAt", LocalDateTime.now().toString());
            
            chapterSummaries.add(summaryEntry);
            memoryBank.put("chapterSummaries", chapterSummaries);
            
            logger.info("✅ 添加第{}章概括: {}", chapterNumber, 
                       chapterSummary.length() > 50 ? chapterSummary.substring(0, 50) + "..." : chapterSummary);
        }
        
        // 🆕 合并世界实体（势力/地点/物件）
        if (extractedInfo.containsKey("worldEntities")) {
            List<Map<String, Object>> worldEntities = (List<Map<String, Object>>) extractedInfo.get("worldEntities");
            updateWorldEntitiesFromAI(memoryBank, worldEntities);
        }
    }

    /**
     * 从AI提取的信息更新角色档案（增强版：分类过滤+赋能）
     */
    @SuppressWarnings("unchecked")
    private void updateCharacterProfilesFromAI(Map<String, Object> memoryBank, List<Map<String, Object>> characterUpdates) {
        try {
            Map<String, Object> characterProfiles = (Map<String, Object>) memoryBank.get("characterProfiles");
            
            // 获取或创建CAMEO轻量记录容器
            Map<String, Object> cameos = (Map<String, Object>) memoryBank.getOrDefault("cameos", new HashMap<>());
            
            int addedCount = 0;
            int updatedCount = 0;
            int cameoCount = 0;
            
            for (Map<String, Object> update : characterUpdates) {
                String characterName = (String) update.get("name");
                if (characterName == null || characterName.trim().isEmpty()) {
                    continue;
                }
                
                // 提取分类与评分字段
                String roleTag = (String) update.getOrDefault("roleTag", "SUPPORT");
                Integer influenceScore = getIntegerValue(update.get("influenceScore"));
                Double screenTime = getDoubleValue(update.get("screenTime"));
                Double returnProbability = getDoubleValue(update.get("returnProbability"));
                
                // 过滤规则：CAMEO且低影响分数的角色不入主档案
                boolean isCameo = "CAMEO".equalsIgnoreCase(roleTag) || 
                                  (influenceScore != null && influenceScore < 20) ||
                                  (screenTime != null && screenTime < 0.1) ||
                                  (returnProbability != null && returnProbability < 0.3);
                
                if (isCameo) {
                    // CAMEO只记录轻量信息（姓名+出现章节+一句话简介）
                    Map<String, Object> cameoInfo = (Map<String, Object>) cameos.getOrDefault(characterName, new HashMap<>());
                    if (cameoInfo.isEmpty()) {
                        cameoInfo.put("name", characterName);
                        cameoInfo.put("hookLine", update.get("hookLine"));
                        cameoInfo.put("firstMention", memoryBank.get("lastUpdatedChapter"));
                        cameoInfo.put("chapters", new ArrayList<>());
                    }
                    List<Integer> chapters = (List<Integer>) cameoInfo.get("chapters");
                    chapters.add((Integer) memoryBank.get("lastUpdatedChapter"));
                    cameos.put(characterName, cameoInfo);
                    cameoCount++;
                    logger.info("记录CAMEO: {} - {}", characterName, update.get("hookLine"));
                    continue;
                }
                
                // 长期/重要角色：入主档案
                if (characterProfiles.containsKey(characterName)) {
                    // 更新现有角色
                    Map<String, Object> existingProfile = (Map<String, Object>) characterProfiles.get(characterName);
                    mergeCharacterProfileEnhanced(existingProfile, update, memoryBank);
                    updatedCount++;
                    logger.info("更新角色: {} ({}) - 影响分: {}", characterName, roleTag, influenceScore);
                } else {
                    // 新增角色（自动赋能）
                    Map<String, Object> newProfile = enrichNewCharacter(update, memoryBank);
                    characterProfiles.put(characterName, newProfile);
                    addedCount++;
                    logger.info("新增角色: {} ({}) - {}", characterName, roleTag, update.get("hookLine"));
                }
            }
            
            // 更新容器回记忆库
            memoryBank.put("cameos", cameos);
            
            logger.info("✅ 角色档案更新完成 - 新增: {}, 更新: {}, CAMEO: {}", addedCount, updatedCount, cameoCount);
            logger.info("   主档案: {}个, CAMEO: {}个", characterProfiles.size(), cameos.size());
            
        } catch (Exception e) {
            logger.error("更新角色档案失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 合并角色档案（增强版：只补缺不覆盖+赋能）
     */
    @SuppressWarnings("unchecked")
    private void mergeCharacterProfileEnhanced(Map<String, Object> existing, Map<String, Object> update, Map<String, Object> memoryBank) {
        // 更新基础字段
        if (update.containsKey("status")) {
            existing.put("status", update.get("status"));
        }
        if (update.containsKey("roleTag")) {
            existing.put("roleTag", update.get("roleTag"));
        }
        
        // 更新评分字段（取较大值）
        updateIfHigher(existing, update, "influenceScore");
        updateIfHigher(existing, update, "screenTime");
        updateIfHigher(existing, update, "returnProbability");
        
        // 补充赋能字段（首次/信息不全时）
        enrichIfMissing(existing, update, "coreTrait");
        enrichIfMissing(existing, update, "speechStyle");
        enrichIfMissing(existing, update, "desire");
        enrichIfMissing(existing, update, "hookLine");
        enrichIfMissing(existing, update, "linksToProtagonist");
        enrichIfMissing(existing, update, "triggerConditions");
        
        // 更新最后出现章节
        existing.put("lastAppearance", memoryBank.get("lastUpdatedChapter"));
        
        // 出现次数+1
        Integer count = (Integer) existing.getOrDefault("appearanceCount", 0);
        existing.put("appearanceCount", count + 1);
    }

    /**
     * 为新角色赋能（自动补充人格/动机/触发条件）
     */
    private Map<String, Object> enrichNewCharacter(Map<String, Object> update, Map<String, Object> memoryBank) {
        Map<String, Object> enriched = new HashMap<>(update);
        
        // 设置基础追踪字段
        enriched.put("firstAppearance", memoryBank.get("lastUpdatedChapter"));
        enriched.put("lastAppearance", memoryBank.get("lastUpdatedChapter"));
        enriched.put("appearanceCount", 1);
        enriched.put("createdAt", LocalDateTime.now().toString());
        
        // 确保赋能字段存在（AI应该已提供，这里做兜底）
        enriched.putIfAbsent("coreTrait", "待补充");
        enriched.putIfAbsent("speechStyle", "待观察");
        enriched.putIfAbsent("desire", "未知");
        enriched.putIfAbsent("hookLine", enriched.get("name") + "（待描述）");
        enriched.putIfAbsent("linksToProtagonist", "关系待明确");
        enriched.putIfAbsent("triggerConditions", "无特定触发");
        enriched.putIfAbsent("lifecycle", determineLifecycle(enriched));
        
        return enriched;
    }

    /**
     * 确定角色生命周期
     */
    private String determineLifecycle(Map<String, Object> character) {
        String roleTag = (String) character.get("roleTag");
        Double returnProb = getDoubleValue(character.get("returnProbability"));
        
        if ("PROTAGONIST".equalsIgnoreCase(roleTag) || "ANTAGONIST".equalsIgnoreCase(roleTag)) {
            return "CORE";
        } else if ("MAJOR".equalsIgnoreCase(roleTag) || (returnProb != null && returnProb >= 0.7)) {
            return "ARC_SUPPORT";
        } else if ("SUPPORT".equalsIgnoreCase(roleTag) || (returnProb != null && returnProb >= 0.3)) {
            return "TEMP_SUPPORT";
        } else {
            return "CAMEO";
        }
    }

    /**
     * 只补缺不覆盖
     */
    private void enrichIfMissing(Map<String, Object> existing, Map<String, Object> update, String field) {
        Object existingValue = existing.get(field);
        Object updateValue = update.get(field);
        
        if (updateValue != null && !updateValue.toString().trim().isEmpty()) {
            if (existingValue == null || existingValue.toString().trim().isEmpty() || 
                "待补充".equals(existingValue) || "待观察".equals(existingValue) || "未知".equals(existingValue)) {
                existing.put(field, updateValue);
            } else if (updateValue.toString().length() > existingValue.toString().length()) {
                // 如果新值更详细，则更新
                existing.put(field, updateValue);
            }
        }
    }

    /**
     * 更新字段（取较大值）
     */
    private void updateIfHigher(Map<String, Object> existing, Map<String, Object> update, String field) {
        Object existingValue = existing.get(field);
        Object updateValue = update.get(field);
        
        if (updateValue == null) return;
        
        if (existingValue == null) {
            existing.put(field, updateValue);
        } else if (updateValue instanceof Number && existingValue instanceof Number) {
            double existingNum = ((Number) existingValue).doubleValue();
            double updateNum = ((Number) updateValue).doubleValue();
            if (updateNum > existingNum) {
                existing.put(field, updateValue);
            }
        }
    }

    /**
     * 安全获取Double值
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从AI提取的信息更新大事年表
     */
    private void updateChronicleFromAI(Map<String, Object> memoryBank, List<Map<String, Object>> eventUpdates) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chronicle = (List<Map<String, Object>>) memoryBank.get("chronicle");
            
            for (Map<String, Object> update : eventUpdates) {
                // 检查是否已存在相同的事件
                boolean exists = false;
                for (Map<String, Object> existingEvent : chronicle) {
                    if (isSameEvent(existingEvent, update)) {
                        // 更新现有事件
                        mergeEventInfo(existingEvent, update);
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    // 添加新事件
                    chronicle.add(new HashMap<>(update));
                }
            }
            logger.info("从AI提取的信息更新大事年表: {}个更新", eventUpdates.size());
        } catch (Exception e) {
            logger.error("更新大事年表失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从AI提取的信息更新伏笔追踪
     */
    private void updateForeshadowingFromAI(Map<String, Object> memoryBank, List<Map<String, Object>> foreshadowingUpdates) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) memoryBank.get("foreshadowing");
            
            for (Map<String, Object> update : foreshadowingUpdates) {
                // 检查是否已存在相同的伏笔
                boolean exists = false;
                for (Map<String, Object> existingItem : foreshadowing) {
                    if (isSameForeshadowing(existingItem, update)) {
                        // 更新现有伏笔
                        mergeForeshadowingInfo(existingItem, update);
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    // 添加新伏笔
                    foreshadowing.add(new HashMap<>(update));
                }
            }
            logger.info("从AI提取的信息更新伏笔追踪: {}个更新", foreshadowingUpdates.size());
        } catch (Exception e) {
            logger.error("更新伏笔追踪失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从AI提取的信息更新世界观词典
     */
    private void updateWorldDictionaryFromAI(Map<String, Object> memoryBank, List<Map<String, Object>> worldviewUpdates) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> worldDictionary = (Map<String, Object>) memoryBank.get("worldDictionary");
            
            for (Map<String, Object> update : worldviewUpdates) {
                String term = (String) update.get("term");
                if (term != null) {
                    if (worldDictionary.containsKey(term)) {
                        // 更新现有词条
                        @SuppressWarnings("unchecked")
                        Map<String, Object> existingTerm = (Map<String, Object>) worldDictionary.get(term);
                        mergeWorldDictionaryTerm(existingTerm, update);
                        logger.info("更新世界观词条: {} - 合并AI提取的信息", term);
                    } else {
                        // 创建新词条
                        worldDictionary.put(term, new HashMap<>(update));
                        logger.info("创建新世界观词条: {} - 来自AI提取", term);
                    }
                }
            }
            logger.info("从AI提取的信息更新世界观词典: {}个更新", worldviewUpdates.size());
        } catch (Exception e) {
            logger.error("更新世界观词典失败: {}", e.getMessage(), e);
        }
    }

    // ================================
    // 数据库存储方法
    // ================================

    /**
     * 将记忆库保存到数据库（公开方法，供其他服务调用）
     */
    public void saveMemoryBankToDatabase(Long novelId, Map<String, Object> memoryBank, Integer chapterNumber) {
        try {
            logger.info("💾 开始将记忆库保存到数据库 - 小说ID: {}, 第{}章", novelId, chapterNumber);
            
            // 保存角色档案
            saveCharacterProfilesToDatabase(novelId, memoryBank);
            
            // 保存大事年表
            saveChronicleToDatabase(novelId, memoryBank, chapterNumber);
            
            // 保存伏笔追踪
            saveForeshadowingToDatabase(novelId, memoryBank, chapterNumber);
            
            // 保存世界观词典
            saveWorldDictionaryToDatabase(novelId, memoryBank, chapterNumber);
            
            // 保存记忆库版本信息
            saveMemoryVersionToDatabase(novelId, memoryBank, chapterNumber);
            
            logger.info("✅ 记忆库数据库存储完成 - 小说ID: {}, 第{}章", novelId, chapterNumber);
            
        } catch (Exception e) {
            logger.error("❌ 记忆库数据库存储失败 - 小说ID: {}, 第{}章: {}", novelId, chapterNumber, e.getMessage(), e);
        }
    }

    /**
     * 保存角色档案到数据库
     */
    private void saveCharacterProfilesToDatabase(Long novelId, Map<String, Object> memoryBank) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> characterProfiles = (Map<String, Object>) memoryBank.get("characterProfiles");
            
            if (characterProfiles != null) {
                for (Map.Entry<String, Object> entry : characterProfiles.entrySet()) {
                    String characterName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> profile = (Map<String, Object>) entry.getValue();
                    
                    // 检查角色是否已存在
                    NovelCharacterProfile existingProfile = characterProfileRepository.findByNovelIdAndName(novelId, characterName);
                    
                    if (existingProfile != null) {
                        // 更新现有角色档案
                        updateExistingCharacterProfileInDatabase(existingProfile, profile);
                        characterProfileRepository.updateById(existingProfile);
                        logger.info("更新角色档案: {} - 小说ID: {}", characterName, novelId);
                    } else {
                        // 创建新角色档案
                        NovelCharacterProfile newProfile = createNewCharacterProfileInDatabase(novelId, characterName, profile);
                        characterProfileRepository.insert(newProfile);
                        logger.info("创建角色档案: {} - 小说ID: {}", characterName, novelId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("保存角色档案失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存大事年表到数据库
     */
    private void saveChronicleToDatabase(Long novelId, Map<String, Object> memoryBank, Integer chapterNumber) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chronicle = (List<Map<String, Object>>) memoryBank.get("chronicle");
            
            if (chronicle != null) {
                for (Map<String, Object> event : chronicle) {
                    // 检查是否已存在相同章节的事件
                    List<NovelChronicle> existingEvents = chronicleRepository.findByNovelIdAndChapterRange(novelId, chapterNumber, chapterNumber);
                    
                    if (existingEvents.isEmpty()) {
                        // 创建新事件记录
                        NovelChronicle newEvent = createNewChronicleInDatabase(novelId, chapterNumber, event);
                        chronicleRepository.insert(newEvent);
                        logger.info("创建大事年表事件: 小说ID: {}, 第{}章", novelId, chapterNumber);
                    } else {
                        // 更新现有事件记录
                        NovelChronicle existingEvent = existingEvents.get(0);
                        updateExistingChronicleInDatabase(existingEvent, event);
                        chronicleRepository.updateById(existingEvent);
                        logger.info("更新大事年表事件: 小说ID: {}, 第{}章", novelId, chapterNumber);
                    }
                }
                logger.info("保存大事年表: {}个事件 - 小说ID: {}, 第{}章", chronicle.size(), novelId, chapterNumber);
            }
        } catch (Exception e) {
            logger.error("保存大事年表失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存伏笔追踪到数据库
     */
    private void saveForeshadowingToDatabase(Long novelId, Map<String, Object> memoryBank, Integer chapterNumber) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) memoryBank.get("foreshadowing");
            
            if (foreshadowing != null) {
                for (Map<String, Object> item : foreshadowing) {
                    // 创建新伏笔记录
                    NovelForeshadowing newForeshadowing = createNewForeshadowingInDatabase(novelId, chapterNumber, item);
                    foreshadowingRepository.insert(newForeshadowing);
                    logger.info("创建伏笔追踪: 小说ID: {}, 第{}章", novelId, chapterNumber);
                }
                logger.info("保存伏笔追踪: {}个伏笔 - 小说ID: {}, 第{}章", foreshadowing.size(), novelId, chapterNumber);
            }
        } catch (Exception e) {
            logger.error("保存伏笔追踪失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存世界观词典到数据库
     */
    private void saveWorldDictionaryToDatabase(Long novelId, Map<String, Object> memoryBank, Integer chapterNumber) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> worldDictionary = (Map<String, Object>) memoryBank.get("worldDictionary");
            
            if (worldDictionary != null) {
                for (Map.Entry<String, Object> entry : worldDictionary.entrySet()) {
                    String term = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> termInfo = (Map<String, Object>) entry.getValue();
                    
                    // 检查词条是否已存在
                    NovelWorldDictionary existingTerm = worldDictionaryRepository.findByNovelIdAndTerm(novelId, term);
                    
                    if (existingTerm != null) {
                        // 更新现有词条
                        updateExistingWorldDictionaryInDatabase(existingTerm, termInfo, chapterNumber);
                        worldDictionaryRepository.updateById(existingTerm);
                        logger.info("更新世界观词条: {} - 小说ID: {}", term, novelId);
                    } else {
                        // 创建新词条
                        NovelWorldDictionary newTerm = createNewWorldDictionaryInDatabase(novelId, term, termInfo, chapterNumber);
                        worldDictionaryRepository.insert(newTerm);
                        logger.info("创建世界观词条: {} - 小说ID: {}", term, novelId);
                    }
                }
                logger.info("保存世界观词典: {}个词条 - 小说ID: {}, 第{}章", worldDictionary.size(), novelId, chapterNumber);
            }
        } catch (Exception e) {
            logger.error("保存世界观词典失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存记忆库版本信息到数据库
     */
    private void saveMemoryVersionToDatabase(Long novelId, Map<String, Object> memoryBank, Integer chapterNumber) {
        try {
            Integer version = (Integer) memoryBank.get("memoryVersion");
            String lastUpdatedTime = (String) memoryBank.get("lastUpdatedTime");
            
            logger.info("保存记忆库版本: v{} - 小说ID: {}, 第{}章, 更新时间: {}", 
                       version, novelId, chapterNumber, lastUpdatedTime);
        } catch (Exception e) {
            logger.error("保存记忆库版本失败: {}", e.getMessage(), e);
        }
    }



    // ================================
    // 数据库操作辅助方法
    // ================================

    /**
     * 创建新角色档案实体
     */
    private NovelCharacterProfile createNewCharacterProfileInDatabase(Long novelId, String characterName, Map<String, Object> profile) {
        NovelCharacterProfile newProfile = new NovelCharacterProfile();
        newProfile.setNovelId(novelId);
        newProfile.setName(characterName);
        newProfile.setFirstAppearance((Integer) profile.get("firstAppearance"));
        newProfile.setLastAppearance((Integer) profile.get("lastAppearance"));
        newProfile.setAppearanceCount((Integer) profile.getOrDefault("appearanceCount", 1));
        newProfile.setStatus((String) profile.getOrDefault("status", "ACTIVE"));
        newProfile.setStatusChangeChapter((Integer) profile.get("statusChangeChapter"));
        newProfile.setImportanceScore((Integer) profile.getOrDefault("importanceScore", 50));
        
        // 设置JSON字段
        try {
            if (profile.containsKey("personalityTraits")) {
                newProfile.setPersonalityTraits(objectMapper.writeValueAsString(profile.get("personalityTraits")));
            }
            if (profile.containsKey("keyEvents")) {
                newProfile.setKeyEvents(objectMapper.writeValueAsString(profile.get("keyEvents")));
            }
            if (profile.containsKey("relationships")) {
                newProfile.setRelationships(objectMapper.writeValueAsString(profile.get("relationships")));
            }
            if (profile.containsKey("actionsHistory")) {
                newProfile.setActionsHistory(objectMapper.writeValueAsString(profile.get("actionsHistory")));
            }
        } catch (JsonProcessingException e) {
            logger.warn("JSON序列化失败，使用默认值: {}", e.getMessage());
        }
        
        newProfile.setCreatedTime(LocalDateTime.now());
        newProfile.setUpdatedTime(LocalDateTime.now());
        
        return newProfile;
    }

    /**
     * 更新现有角色档案实体
     */
    private void updateExistingCharacterProfileInDatabase(NovelCharacterProfile existingProfile, Map<String, Object> profile) {
        if (profile.containsKey("lastAppearance")) {
            existingProfile.setLastAppearance((Integer) profile.get("lastAppearance"));
        }
        if (profile.containsKey("appearanceCount")) {
            existingProfile.setAppearanceCount((Integer) profile.get("appearanceCount"));
        }
        if (profile.containsKey("status")) {
            existingProfile.setStatus((String) profile.get("status"));
        }
        if (profile.containsKey("statusChangeChapter")) {
            existingProfile.setStatusChangeChapter((Integer) profile.get("statusChangeChapter"));
        }
        if (profile.containsKey("importanceScore")) {
            existingProfile.setImportanceScore((Integer) profile.get("importanceScore"));
        }
        
        // 更新JSON字段
        try {
            if (profile.containsKey("personalityTraits")) {
                existingProfile.setPersonalityTraits(objectMapper.writeValueAsString(profile.get("personalityTraits")));
            }
            if (profile.containsKey("keyEvents")) {
                existingProfile.setKeyEvents(objectMapper.writeValueAsString(profile.get("keyEvents")));
            }
            if (profile.containsKey("relationships")) {
                existingProfile.setRelationships(objectMapper.writeValueAsString(profile.get("relationships")));
            }
            if (profile.containsKey("actionsHistory")) {
                existingProfile.setActionsHistory(objectMapper.writeValueAsString(profile.get("actionsHistory")));
            }
        } catch (JsonProcessingException e) {
            logger.warn("JSON序列化失败，使用默认值: {}", e.getMessage());
        }
        
        existingProfile.setUpdatedTime(LocalDateTime.now());
    }

    /**
     * 创建新大事年表实体
     */
    private NovelChronicle createNewChronicleInDatabase(Long novelId, Integer chapterNumber, Map<String, Object> event) {
        NovelChronicle newEvent = new NovelChronicle();
        newEvent.setNovelId(novelId);
        newEvent.setChapterNumber(chapterNumber);
        
        try {
            newEvent.setEvents(objectMapper.writeValueAsString(event.get("events")));
        } catch (JsonProcessingException e) {
            logger.warn("JSON序列化失败，使用默认值: {}", e.getMessage());
            newEvent.setEvents("[]");
        }
        
        newEvent.setTimelineInfo((String) event.get("timelineInfo"));
        newEvent.setEventType((String) event.getOrDefault("eventType", "OTHER"));
        newEvent.setImportanceLevel((Integer) event.getOrDefault("importanceLevel", 5));
        newEvent.setCreatedTime(LocalDateTime.now());
        
        return newEvent;
    }

    /**
     * 更新现有大事年表实体
     */
    private void updateExistingChronicleInDatabase(NovelChronicle existingEvent, Map<String, Object> event) {
        if (event.containsKey("events")) {
            try {
                existingEvent.setEvents(objectMapper.writeValueAsString(event.get("events")));
            } catch (JsonProcessingException e) {
                logger.warn("JSON序列化失败，使用默认值: {}", e.getMessage());
            }
        }
        if (event.containsKey("timelineInfo")) {
            existingEvent.setTimelineInfo((String) event.get("timelineInfo"));
        }
        if (event.containsKey("eventType")) {
            existingEvent.setEventType((String) event.get("eventType"));
        }
        if (event.containsKey("importanceLevel")) {
            existingEvent.setImportanceLevel((Integer) event.get("importanceLevel"));
        }
    }

    /**
     * 创建新伏笔追踪实体
     */
    private NovelForeshadowing createNewForeshadowingInDatabase(Long novelId, Integer chapterNumber, Map<String, Object> item) {
        NovelForeshadowing newForeshadowing = new NovelForeshadowing();
        newForeshadowing.setNovelId(novelId);
        newForeshadowing.setContent((String) item.get("content"));
        newForeshadowing.setPlantedChapter(chapterNumber);
        newForeshadowing.setResolvedChapter((Integer) item.get("resolvedChapter"));
        newForeshadowing.setStatus((String) item.getOrDefault("status", "ACTIVE"));
        newForeshadowing.setType((String) item.getOrDefault("type", "OTHER"));
        newForeshadowing.setPriority((Integer) item.getOrDefault("priority", 5));
        newForeshadowing.setContextInfo((String) item.get("contextInfo"));
        newForeshadowing.setCreatedTime(LocalDateTime.now());
        
        return newForeshadowing;
    }

    /**
     * 创建新世界观词典实体
     */
    private NovelWorldDictionary createNewWorldDictionaryInDatabase(Long novelId, String term, Map<String, Object> termInfo, Integer chapterNumber) {
        NovelWorldDictionary newTerm = new NovelWorldDictionary();
        newTerm.setNovelId(novelId);
        newTerm.setTerm(term);
        newTerm.setType((String) termInfo.getOrDefault("type", "CONCEPT"));
        newTerm.setFirstMention(chapterNumber);
        newTerm.setDescription((String) termInfo.get("description"));
        newTerm.setContextInfo((String) termInfo.get("contextInfo"));
        newTerm.setUsageCount((Integer) termInfo.getOrDefault("usageCount", 1));
        newTerm.setIsImportant((Boolean) termInfo.getOrDefault("isImportant", false));
        newTerm.setCreatedTime(LocalDateTime.now());
        newTerm.setUpdatedTime(LocalDateTime.now());
        
        return newTerm;
    }

    /**
     * 更新现有世界观词典实体
     */
    private void updateExistingWorldDictionaryInDatabase(NovelWorldDictionary existingTerm, Map<String, Object> termInfo, Integer chapterNumber) {
        if (termInfo.containsKey("description")) {
            existingTerm.setDescription((String) termInfo.get("description"));
        }
        if (termInfo.containsKey("contextInfo")) {
            existingTerm.setContextInfo((String) termInfo.get("contextInfo"));
        }
        if (termInfo.containsKey("usageCount")) {
            existingTerm.setUsageCount((Integer) termInfo.get("usageCount"));
        }
        if (termInfo.containsKey("isImportant")) {
            existingTerm.setIsImportant((Boolean) termInfo.get("isImportant"));
        }
        
        existingTerm.setUpdatedTime(LocalDateTime.now());
    }

    // ================================
    // 内存合并辅助方法
    // ================================

    /**
     * 合并角色档案信息
     */
    private void mergeCharacterProfile(Map<String, Object> existingProfile, Map<String, Object> update) {
        // 合并基本信息
        if (update.containsKey("lastAppearance")) {
            existingProfile.put("lastAppearance", update.get("lastAppearance"));
        }
        if (update.containsKey("appearanceCount")) {
            Integer currentCount = (Integer) existingProfile.getOrDefault("appearanceCount", 0);
            Integer newCount = (Integer) update.getOrDefault("appearanceCount", 0);
            existingProfile.put("appearanceCount", currentCount + newCount);
        }
        if (update.containsKey("status")) {
            existingProfile.put("status", update.get("status"));
        }
        if (update.containsKey("statusChangeChapter")) {
            existingProfile.put("statusChangeChapter", update.get("statusChangeChapter"));
        }
        if (update.containsKey("importanceScore")) {
            existingProfile.put("importanceScore", update.get("importanceScore"));
        }
        
        // 合并JSON字段
        mergeJsonField(existingProfile, update, "personalityTraits");
        mergeJsonField(existingProfile, update, "keyEvents");
        mergeJsonField(existingProfile, update, "relationships");
        mergeJsonField(existingProfile, update, "actionsHistory");
    }

    /**
     * 判断是否为相同事件
     */
    private boolean isSameEvent(Map<String, Object> existingEvent, Map<String, Object> update) {
        // 简单判断：如果事件类型和描述相同，认为是同一事件
        String existingType = (String) existingEvent.get("eventType");
        String updateType = (String) update.get("eventType");
        String existingDescription = (String) existingEvent.get("description");
        String updateDescription = (String) update.get("description");
        
        return Objects.equals(existingType, updateType) && 
               Objects.equals(existingDescription, updateDescription);
    }

    /**
     * 合并事件信息
     */
    private void mergeEventInfo(Map<String, Object> existingEvent, Map<String, Object> update) {
        if (update.containsKey("timelineInfo")) {
            existingEvent.put("timelineInfo", update.get("timelineInfo"));
        }
        if (update.containsKey("importanceLevel")) {
            existingEvent.put("importanceLevel", update.get("importanceLevel"));
        }
        // 合并事件详情
        mergeJsonField(existingEvent, update, "events");
    }

    /**
     * 判断是否为相同伏笔
     */
    private boolean isSameForeshadowing(Map<String, Object> existingItem, Map<String, Object> update) {
        // 简单判断：如果内容和类型相同，认为是同一伏笔
        String existingContent = (String) existingItem.get("content");
        String updateContent = (String) update.get("content");
        String existingType = (String) existingItem.get("type");
        String updateType = (String) update.get("type");
        
        return Objects.equals(existingContent, updateContent) && 
               Objects.equals(existingType, updateType);
    }

    /**
     * 合并伏笔信息
     */
    private void mergeForeshadowingInfo(Map<String, Object> existingItem, Map<String, Object> update) {
        if (update.containsKey("resolvedChapter")) {
            existingItem.put("resolvedChapter", update.get("resolvedChapter"));
        }
        if (update.containsKey("status")) {
            existingItem.put("status", update.get("status"));
        }
        if (update.containsKey("priority")) {
            existingItem.put("priority", update.get("priority"));
        }
        if (update.containsKey("contextInfo")) {
            existingItem.put("contextInfo", update.get("contextInfo"));
        }
    }

    /**
     * 合并世界观词典词条
     */
    private void mergeWorldDictionaryTerm(Map<String, Object> existingTerm, Map<String, Object> update) {
        if (update.containsKey("description")) {
            existingTerm.put("description", update.get("description"));
        }
        if (update.containsKey("contextInfo")) {
            existingTerm.put("contextInfo", update.get("contextInfo"));
        }
        if (update.containsKey("usageCount")) {
            Integer currentCount = (Integer) existingTerm.getOrDefault("usageCount", 0);
            Integer newCount = (Integer) update.getOrDefault("usageCount", 0);
            existingTerm.put("usageCount", currentCount + newCount);
        }
        if (update.containsKey("isImportant")) {
            existingTerm.put("isImportant", update.get("isImportant"));
        }
    }

    /**
     * 合并JSON字段
     */
    private void mergeJsonField(Map<String, Object> existing, Map<String, Object> update, String fieldName) {
        if (update.containsKey(fieldName)) {
            Object updateValue = update.get(fieldName);
            if (updateValue != null) {
                existing.put(fieldName, updateValue);
            }
        }
    }

    /**
     * 从AI提取的信息更新世界实体（势力/地点/物件）
     */
    @SuppressWarnings("unchecked")
    private void updateWorldEntitiesFromAI(Map<String, Object> memoryBank, List<Map<String, Object>> worldEntities) {
        try {
            if (worldEntities == null || worldEntities.isEmpty()) {
                return;
            }
            
            // 获取或创建世界实体容器
            Map<String, Object> entities = (Map<String, Object>) memoryBank.getOrDefault("worldEntities", new HashMap<>());
            
            // 按类型分类：organizations(势力)、locations(地点)、artifacts(物件)
            Map<String, Object> organizations = (Map<String, Object>) entities.getOrDefault("organizations", new HashMap<>());
            Map<String, Object> locations = (Map<String, Object>) entities.getOrDefault("locations", new HashMap<>());
            Map<String, Object> artifacts = (Map<String, Object>) entities.getOrDefault("artifacts", new HashMap<>());
            
            int addedCount = 0;
            int updatedCount = 0;
            int filteredCount = 0;
            
            for (Map<String, Object> entity : worldEntities) {
                String name = (String) entity.get("name");
                String type = (String) entity.get("type");
                Integer influenceScore = getIntegerValue(entity.get("influenceScore"));
                
                // 过滤：影响分数<20的实体不入库（避免记录路人级别的小场景）
                if (influenceScore == null || influenceScore < 20) {
                    logger.debug("过滤低影响实体: {} (影响分数: {})", name, influenceScore);
                    filteredCount++;
                    continue;
                }
                
                if (name == null || name.trim().isEmpty() || type == null) {
                    continue;
                }
                
                // 名称归一化（全角转半角、去空格）
                name = normalizeEntityName(name);
                
                // 根据类型存入对应容器
                Map<String, Object> targetMap = null;
                if ("ORGANIZATION".equalsIgnoreCase(type)) {
                    targetMap = organizations;
                } else if ("LOCATION".equalsIgnoreCase(type)) {
                    targetMap = locations;
                } else if ("ARTIFACT".equalsIgnoreCase(type)) {
                    targetMap = artifacts;
                } else {
                    logger.warn("未知实体类型: {} - {}", name, type);
                    continue;
                }
                
                // 检查是否已存在（按名称去重）
                if (targetMap.containsKey(name)) {
                    // 更新现有实体（只补缺不覆盖）
                    Map<String, Object> existing = (Map<String, Object>) targetMap.get(name);
                    mergeEntityInfo(existing, entity);
                    updatedCount++;
                    logger.info("更新实体: {} ({}) - 影响分数: {}", name, type, influenceScore);
                } else {
                    // 新增实体
                    Map<String, Object> newEntity = new HashMap<>();
                    newEntity.put("name", name);
                    newEntity.put("type", type);
                    newEntity.put("hookLine", entity.get("hookLine")); // 一句话简介
                    newEntity.put("influenceScore", influenceScore);
                    newEntity.put("relatedCharacters", entity.getOrDefault("relatedCharacters", new ArrayList<>()));
                    newEntity.put("firstMention", memoryBank.get("lastUpdatedChapter"));
                    newEntity.put("lastMention", memoryBank.get("lastUpdatedChapter"));
                    newEntity.put("mentionCount", 1);
                    newEntity.put("createdAt", LocalDateTime.now().toString());
                    
                    targetMap.put(name, newEntity);
                    addedCount++;
                    logger.info("新增实体: {} ({}) - {}", name, type, entity.get("hookLine"));
                }
            }
            
            // 更新容器回记忆库
            entities.put("organizations", organizations);
            entities.put("locations", locations);
            entities.put("artifacts", artifacts);
            memoryBank.put("worldEntities", entities);
            
            logger.info("✅ 世界实体更新完成 - 新增: {}, 更新: {}, 过滤: {}", addedCount, updatedCount, filteredCount);
            logger.info("   势力: {}个, 地点: {}个, 物件: {}个", 
                       organizations.size(), locations.size(), artifacts.size());
            
        } catch (Exception e) {
            logger.error("更新世界实体失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 合并实体信息（只补缺不覆盖）
     */
    @SuppressWarnings("unchecked")
    private void mergeEntityInfo(Map<String, Object> existing, Map<String, Object> update) {
        // 更新最后出现章节
        existing.put("lastMention", update.get("lastMention"));
        
        // 出现次数+1
        Integer count = (Integer) existing.getOrDefault("mentionCount", 0);
        existing.put("mentionCount", count + 1);
        
        // 补充hookLine（如果新的更详细）
        String existingHook = (String) existing.get("hookLine");
        String newHook = (String) update.get("hookLine");
        if (newHook != null && (existingHook == null || newHook.length() > existingHook.length())) {
            existing.put("hookLine", newHook);
        }
        
        // 更新影响分数（取较大值）
        Integer existingScore = getIntegerValue(existing.get("influenceScore"));
        Integer newScore = getIntegerValue(update.get("influenceScore"));
        if (newScore != null && (existingScore == null || newScore > existingScore)) {
            existing.put("influenceScore", newScore);
        }
        
        // 合并关联角色列表
        List<String> existingChars = (List<String>) existing.getOrDefault("relatedCharacters", new ArrayList<>());
        List<String> newChars = (List<String>) update.getOrDefault("relatedCharacters", new ArrayList<>());
        for (String newChar : newChars) {
            if (!existingChars.contains(newChar)) {
                existingChars.add(newChar);
            }
        }
        existing.put("relatedCharacters", existingChars);
    }

    /**
     * 名称归一化（全角转半角、去空格、统一大小写）
     */
    private String normalizeEntityName(String name) {
        if (name == null) return null;
        
        // 全角转半角
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else if (c == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString().trim();
    }

    /**
     * 安全获取Integer值
     */
    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 使用AIConfigRequest调用AI服务（同步方式）
     */
    @SuppressWarnings("unchecked")
    private String callAIWithConfig(String prompt, com.novel.dto.AIConfigRequest aiConfig) throws Exception {
        String apiUrl = aiConfig.getApiUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4000);
        requestBody.put("temperature", 0.7);
        requestBody.put("stream", false);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        
        // 发送HTTP请求
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15000);
        requestFactory.setReadTimeout(120000);
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate(requestFactory);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        org.springframework.http.HttpEntity<Map<String, Object>> entity = 
            new org.springframework.http.HttpEntity<>(requestBody, headers);
        
        logger.info("调用AI提取章节信息: {}", apiUrl);
        org.springframework.http.ResponseEntity<String> response = 
            restTemplate.postForEntity(apiUrl, entity, String.class);
        
        // 解析响应
        String responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("AI响应为空");
        }
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);
        
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("AI响应格式错误：无choices字段");
        }
        
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> messageData = (Map<String, Object>) firstChoice.get("message");
        if (messageData == null) {
            throw new RuntimeException("AI响应格式错误：无message字段");
        }
        
        String content = (String) messageData.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("AI返回内容为空");
        }
        
        return content.trim();
    }
}
