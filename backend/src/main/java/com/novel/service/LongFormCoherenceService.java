package com.novel.service;

import com.novel.domain.entity.Novel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 网文长篇连贯性管理服务
 * 专门解决超长篇小说创作中的连贯性和割裂感问题
 * 
 * 核心功能：
 * 1. 章节间连贯性检测
 * 2. 角色状态一致性管理  
 * 3. 情节线连续性保证
 * 4. 世界观设定统一性
 * 5. 写作风格一致性
 */
@Service
public class LongFormCoherenceService {

    private static final Logger logger = LoggerFactory.getLogger(LongFormCoherenceService.class);

    @Autowired
    private EnhancedWebNovelPromptService enhancedPromptService;
    
    @Autowired
    private EnhancedAntiAIDetectionService antiAIDetectionService;
    
    @Autowired
    private NovelCraftAIService novelCraftAIService;

    /**
     * 检测章节连贯性
     * 分析当前章节与前文的衔接度，避免割裂感
     */
    public Map<String, Object> analyzeChapterCoherence(
            Novel novel,
            Map<String, Object> currentChapter,
            List<Map<String, Object>> previousChapters,
            Map<String, Object> memoryBank
    ) {
        logger.info("🔗 分析章节连贯性 - 第{}章", currentChapter.get("chapterNumber"));
        
        Map<String, Object> coherenceAnalysis = new HashMap<>();
        
        // 1. 时间线连贯性检测
        double timelineCoherence = analyzeTimelineCoherence(currentChapter, previousChapters);
        
        // 2. 角色状态一致性检测
        double characterConsistency = analyzeCharacterConsistency(currentChapter, memoryBank);
        
        // 3. 情节逻辑连续性检测
        double plotContinuity = analyzePlotContinuity(currentChapter, previousChapters, memoryBank);
        
        // 4. 世界观设定一致性检测
        double worldConsistency = analyzeWorldConsistency(currentChapter, memoryBank);
        
        // 5. 写作风格连贯性检测
        double styleConsistency = analyzeStyleConsistency(currentChapter, previousChapters);
        
        // 综合评分
        double overallCoherence = (timelineCoherence + characterConsistency + 
                                 plotContinuity + worldConsistency + styleConsistency) / 5.0;
        
        coherenceAnalysis.put("overallScore", overallCoherence);
        coherenceAnalysis.put("timelineScore", timelineCoherence);
        coherenceAnalysis.put("characterScore", characterConsistency);
        coherenceAnalysis.put("plotScore", plotContinuity);
        coherenceAnalysis.put("worldScore", worldConsistency);
        coherenceAnalysis.put("styleScore", styleConsistency);
        
        // 生成具体问题和建议
        List<String> issues = generateCoherenceIssues(coherenceAnalysis);
        List<String> suggestions = generateCoherenceSuggestions(coherenceAnalysis, novel.getGenre());
        
        coherenceAnalysis.put("detectedIssues", issues);
        coherenceAnalysis.put("improvementSuggestions", suggestions);
        coherenceAnalysis.put("needsRevision", overallCoherence < 0.7);
        
        logger.info("📊 连贯性分析完成 - 总分: {:.2f}, 需要修订: {}", 
                   overallCoherence, overallCoherence < 0.7);
        
        return coherenceAnalysis;
    }

    /**
     * 智能连贯性修复
     * 根据检测结果自动修复连贯性问题
     */
    public Map<String, Object> repairCoherenceIssues(
            String chapterContent,
            Map<String, Object> coherenceAnalysis,
            Map<String, Object> memoryBank,
            String genre
    ) {
        logger.info("🔧 开始智能连贯性修复");
        
        List<String> issues = (List<String>) coherenceAnalysis.get("detectedIssues");
        double overallScore = (Double) coherenceAnalysis.get("overallScore");
        
        if (overallScore >= 0.7) {
            logger.info("连贯性良好，无需修复");
            Map<String, Object> result = new HashMap<>();
            result.put("originalContent", chapterContent);
            result.put("repairedContent", chapterContent);
            result.put("repairsMade", Collections.emptyList());
            result.put("improvementScore", 0.0);
            return result;
        }
        
        String repairPrompt = buildCoherenceRepairPrompt(chapterContent, issues, memoryBank, genre);
        
        // 调用AI进行连贯性修复
        String repairedContent = novelCraftAIService.callAI("COHERENCE_REPAIR", repairPrompt);
        
        // 使用注入的反AI检测服务优化修复后的内容
        Map<String, Object> aiAnalysis = antiAIDetectionService.analyzeAIFeatures(repairedContent);
        double aiScore = (Double) aiAnalysis.get("aiScore");
        
        // 如果修复后仍有AI痕迹，进行二次优化
        if (aiScore > 0.6) {
            logger.info("连贯性修复后仍有AI痕迹 (评分: {}), 进行二次优化", aiScore);
            Novel tempNovel = new Novel();
            tempNovel.setGenre(genre);
            
            String optimizePrompt = antiAIDetectionService.optimizeAIContent(repairedContent, aiAnalysis, tempNovel);
            repairedContent = novelCraftAIService.callAI("HUMANIZE_CONTENT", optimizePrompt);
        }
        
        // 验证修复效果
        Map<String, Object> chapterData = new HashMap<>();
        chapterData.put("content", repairedContent);
        chapterData.put("chapterNumber", 1);
        Map<String, Object> afterRepairAnalysis = analyzeChapterCoherence(
            null, // Novel对象在此处可为null，因为主要分析内容
            chapterData,
            Collections.emptyList(),
            memoryBank
        );
        
        double improvementScore = (Double) afterRepairAnalysis.get("overallScore") - overallScore;
        
        Map<String, Object> result = new HashMap<>();
        result.put("originalContent", chapterContent);
        result.put("repairedContent", repairedContent);
        result.put("repairsMade", identifyRepairs(chapterContent, repairedContent));
        result.put("improvementScore", improvementScore);
        result.put("finalCoherenceScore", afterRepairAnalysis.get("overallScore"));
        
        logger.info("✅ 连贯性修复完成 - 提升: {:.2f}, 最终评分: {:.2f}", 
                   improvementScore, afterRepairAnalysis.get("overallScore"));
        
        return result;
    }

    /**
     * 预防性连贯性检查
     * 在生成新内容前进行预检，确保连贯性
     */
    public Map<String, Object> preventiveCoherenceCheck(
            Map<String, Object> plannedChapter,
            List<Map<String, Object>> recentChapters,
            Map<String, Object> memoryBank
    ) {
        logger.info("🔮 执行预防性连贯性检查");
        
        Map<String, Object> preventiveCheck = new HashMap<>();
        
        // 1. 检查计划内容与现有情节的逻辑一致性
        boolean plotLogicConsistent = checkPlotLogicConsistency(plannedChapter, recentChapters, memoryBank);
        
        // 2. 检查角色行为的合理性
        boolean characterBehaviorReasonable = checkCharacterBehaviorConsistency(plannedChapter, memoryBank);
        
        // 3. 检查时间线的合理性
        boolean timelineReasonable = checkTimelineReasonability(plannedChapter, recentChapters);
        
        // 4. 检查世界观设定的一致性
        boolean worldSettingConsistent = checkWorldSettingConsistency(plannedChapter, memoryBank);
        
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        if (!plotLogicConsistent) {
            warnings.add("情节逻辑可能存在不一致");
            recommendations.add("建议调整计划内容，确保与前文情节逻辑符合");
        }
        
        if (!characterBehaviorReasonable) {
            warnings.add("角色行为可能与之前设定不符");
            recommendations.add("建议检查角色状态，确保行为合理性");
        }
        
        if (!timelineReasonable) {
            warnings.add("时间线可能存在问题");
            recommendations.add("建议调整时间设定，避免时间线混乱");
        }
        
        if (!worldSettingConsistent) {
            warnings.add("世界观设定可能不一致");
            recommendations.add("建议核对世界观设定，保持一致性");
        }
        
        boolean overallSafe = plotLogicConsistent && characterBehaviorReasonable && 
                            timelineReasonable && worldSettingConsistent;
        
        preventiveCheck.put("isSafeToWrite", overallSafe);
        preventiveCheck.put("plotLogicOk", plotLogicConsistent);
        preventiveCheck.put("characterBehaviorOk", characterBehaviorReasonable);
        preventiveCheck.put("timelineOk", timelineReasonable);
        preventiveCheck.put("worldSettingOk", worldSettingConsistent);
        preventiveCheck.put("warnings", warnings);
        preventiveCheck.put("recommendations", recommendations);
        
        return preventiveCheck;
    }

    /**
     * 动态记忆库更新
     * 基于新章节内容更新记忆库，保持信息同步
     */
    public Map<String, Object> updateMemoryBankDynamically(
            Map<String, Object> newChapter,
            Map<String, Object> currentMemoryBank
    ) {
        logger.info("📚 动态更新记忆库 - 第{}章", newChapter.get("chapterNumber"));
        
        Map<String, Object> updatedMemoryBank = new HashMap<>(currentMemoryBank);
        
        // 1. 更新角色状态
        updateCharacterStates(newChapter, updatedMemoryBank);
        
        // 2. 更新情节线状态
        updatePlotThreads(newChapter, updatedMemoryBank);
        
        // 3. 更新世界观信息
        updateWorldSettings(newChapter, updatedMemoryBank);
        
        // 4. 更新时间线
        updateTimeline(newChapter, updatedMemoryBank);
        
        // 5. 更新伏笔状态
        updateForeshadowing(newChapter, updatedMemoryBank);
        
        // 6. 添加章节摘要
        addChapterSummary(newChapter, updatedMemoryBank);
        
        updatedMemoryBank.put("lastUpdated", new Date().toString());
        updatedMemoryBank.put("version", ((Integer) currentMemoryBank.getOrDefault("version", 1)) + 1);
        
        return updatedMemoryBank;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 检测时间线连贯性 - 具体实现
     */
    private double analyzeTimelineCoherence(Map<String, Object> current, List<Map<String, Object>> previous) {
        double score = 1.0;
        
        // 1. 检查时间跳跃的合理性
        if (previous.size() > 0) {
            Map<String, Object> lastChapter = previous.get(previous.size() - 1);
            String currentTime = (String) current.get("timeReference");
            String lastTime = (String) lastChapter.get("timeReference");
            
            if (currentTime != null && lastTime != null) {
                // 检测时间跳跃是否过大或倒退
                if (hasTimeInconsistency(lastTime, currentTime)) {
                    score -= 0.3;
                }
            }
        }
        
        // 2. 检查事件发生的时序逻辑
        String currentEvents = (String) current.get("coreEvent");
        if (currentEvents != null) {
            for (Map<String, Object> prevChapter : previous) {
                String prevEvents = (String) prevChapter.get("coreEvent");
                if (prevEvents != null && hasEventSequenceConflict(prevEvents, currentEvents)) {
                    score -= 0.2;
                }
            }
        }
        
        return Math.max(0.0, score);
    }

    /**
     * 检测角色一致性 - 具体实现
     */
    private double analyzeCharacterConsistency(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        double score = 1.0;
        
        if (memoryBank == null || !memoryBank.containsKey("characters")) {
            return 0.5; // 没有记忆库信息，给中等分
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.get("characters");
        String chapterContent = (String) chapter.get("content");
        
        if (chapterContent == null) return 0.5;
        
        // 检查每个角色的行为一致性
        for (String charName : characters.keySet()) {
            if (chapterContent.contains(charName)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> charInfo = (Map<String, Object>) characters.get(charName);
                
                // 检查性格一致性
                String personality = (String) charInfo.get("personality");
                if (personality != null && hasPersonalityConflict(chapterContent, charName, personality)) {
                    score -= 0.15;
                }
                
                // 检查能力一致性
                String abilities = (String) charInfo.get("abilities");
                if (abilities != null && hasAbilityConflict(chapterContent, charName, abilities)) {
                    score -= 0.2;
                }
                
                // 检查关系一致性
                @SuppressWarnings("unchecked")
                Map<String, Object> relationships = (Map<String, Object>) charInfo.get("relationships");
                if (relationships != null && hasRelationshipConflict(chapterContent, charName, relationships)) {
                    score -= 0.15;
                }
            }
        }
        
        return Math.max(0.0, score);
    }

    /**
     * 检测情节连续性 - 具体实现
     */
    private double analyzePlotContinuity(Map<String, Object> current, List<Map<String, Object>> previous, Map<String, Object> memoryBank) {
        double score = 1.0;
        
        // 1. 检查主线推进的连续性
        String currentPlotPoint = (String) current.get("plotPoint");
        if (currentPlotPoint != null && memoryBank != null && memoryBank.containsKey("plotThreads")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) memoryBank.get("plotThreads");
            
            boolean hasValidConnection = false;
            for (Map<String, Object> thread : plotThreads) {
                String threadStatus = (String) thread.get("status");
                if ("active".equals(threadStatus) && isPlotPointConnected(currentPlotPoint, thread)) {
                    hasValidConnection = true;
                    break;
                }
            }
            
            if (!hasValidConnection) {
                score -= 0.4; // 新情节点与现有线索无关联
            }
        }
        
        // 2. 检查冲突解决的合理性
        if (previous.size() > 0) {
            Map<String, Object> lastChapter = previous.get(previous.size() - 1);
            String lastConflict = (String) lastChapter.get("conflict");
            String currentConflict = (String) current.get("conflict");
            
            if (lastConflict != null && currentConflict != null) {
                if (hasUnresolvedConflictJump(lastConflict, currentConflict)) {
                    score -= 0.3;
                }
            }
        }
        
        // 3. 检查伏笔的合理处理
        if (memoryBank != null && memoryBank.containsKey("foreshadowing")) {
            score += checkForeshadowingHandling(current, memoryBank) * 0.2;
        }
        
        return Math.max(0.0, score);
    }

    /**
     * 检测世界观一致性 - 具体实现
     */
    private double analyzeWorldConsistency(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        double score = 1.0;
        
        if (memoryBank == null || !memoryBank.containsKey("worldSettings")) {
            return 0.5;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> worldSettings = (Map<String, Object>) memoryBank.get("worldSettings");
        String chapterContent = (String) chapter.get("content");
        
        if (chapterContent == null) return 0.5;
        
        // 检查力量体系一致性
        String powerSystem = (String) worldSettings.get("powerSystem");
        if (powerSystem != null && hasPowerSystemViolation(chapterContent, powerSystem)) {
            score -= 0.25;
        }
        
        // 检查地理设定一致性
        @SuppressWarnings("unchecked")
        Map<String, Object> geography = (Map<String, Object>) worldSettings.get("geography");
        if (geography != null && hasGeographyViolation(chapterContent, geography)) {
            score -= 0.2;
        }
        
        // 检查社会规则一致性
        @SuppressWarnings("unchecked")
        List<String> socialRules = (List<String>) worldSettings.get("socialRules");
        if (socialRules != null && hasSocialRuleViolation(chapterContent, socialRules)) {
            score -= 0.15;
        }
        
        return Math.max(0.0, score);
    }

    /**
     * 检测写作风格一致性 - 具体实现
     */
    private double analyzeStyleConsistency(Map<String, Object> current, List<Map<String, Object>> previous) {
        if (previous.size() < 2) return 1.0; // 样本不足，给满分
        
        double score = 1.0;
        String currentContent = (String) current.get("content");
        
        if (currentContent == null) return 0.5;
        
        // 1. 句长分布检测
        double currentAvgSentenceLength = calculateAverageSentenceLength(currentContent);
        double previousAvgLength = 0.0;
        int validPreviousChapters = 0;
        
        for (Map<String, Object> prevChapter : previous) {
            String prevContent = (String) prevChapter.get("content");
            if (prevContent != null) {
                previousAvgLength += calculateAverageSentenceLength(prevContent);
                validPreviousChapters++;
            }
        }
        
        if (validPreviousChapters > 0) {
            previousAvgLength /= validPreviousChapters;
            double lengthDifference = Math.abs(currentAvgSentenceLength - previousAvgLength) / previousAvgLength;
            if (lengthDifference > 0.3) { // 句长差异超过30%
                score -= 0.2;
            }
        }
        
        // 2. 词汇风格检测
        Map<String, Integer> currentVocabStyle = analyzeVocabularyStyle(currentContent);
        Map<String, Integer> previousVocabStyle = new HashMap<>();
        
        for (Map<String, Object> prevChapter : previous) {
            String prevContent = (String) prevChapter.get("content");
            if (prevContent != null) {
                Map<String, Integer> prevStyle = analyzeVocabularyStyle(prevContent);
                for (String key : prevStyle.keySet()) {
                    previousVocabStyle.put(key, previousVocabStyle.getOrDefault(key, 0) + prevStyle.get(key));
                }
            }
        }
        
        double vocabularyConsistency = calculateVocabularyConsistency(currentVocabStyle, previousVocabStyle);
        score *= vocabularyConsistency;
        
        // 3. 对话风格检测
        double dialogueStyleScore = analyzeDialogueStyleConsistency(currentContent, previous);
        score = (score + dialogueStyleScore) / 2.0;
        
        return Math.max(0.0, score);
    }
    
    // ========== 辅助检测方法 ==========
    
    /**
     * 检测时间不一致性
     */
    private boolean hasTimeInconsistency(String lastTime, String currentTime) {
        // 简化的时间检测逻辑
        try {
            // 检测关键时间词汇
            String[] timeKeywords = {"早上", "中午", "下午", "晚上", "深夜", "凌晨"};
            String[] seasonKeywords = {"春天", "夏天", "秋天", "冬天"};
            
            // 检测是否有明显的时间倒流
            if (lastTime.contains("晚上") && currentTime.contains("早上")) {
                return false; // 正常的时间推进
            }
            
            if (lastTime.contains("早上") && currentTime.contains("昨天")) {
                return true; // 时间倒流
            }
            
            return false;
        } catch (Exception e) {
            return false; // 解析失败时认为无问题
        }
    }
    
    /**
     * 检测事件序列冲突
     */
    private boolean hasEventSequenceConflict(String prevEvents, String currentEvents) {
        // 检测逻辑冲突的事件序列
        if (prevEvents.contains("死亡") && currentEvents.contains("说话")) {
            return true; // 死人说话
        }
        
        if (prevEvents.contains("离开") && currentEvents.contains("继续在") && 
            !currentEvents.contains("回到")) {
            return true; // 已经离开但没有返回就继续在原地
        }
        
        return false;
    }
    
    /**
     * 检测角色性格冲突
     */
    private boolean hasPersonalityConflict(String content, String charName, String personality) {
        // 提取角色在本章中的行为
        String[] behaviors = extractCharacterBehaviors(content, charName);
        
        for (String behavior : behaviors) {
            if (isPersonalityInconsistent(behavior, personality)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检测能力冲突
     */
    private boolean hasAbilityConflict(String content, String charName, String abilities) {
        // 检测角色使用了不应有的能力
        String[] usedAbilities = extractUsedAbilities(content, charName);
        
        for (String used : usedAbilities) {
            if (!abilities.contains(used)) {
                return true; // 使用了未设定的能力
            }
        }
        
        return false;
    }
    
    /**
     * 检测关系冲突
     */
    private boolean hasRelationshipConflict(String content, String charName, Map<String, Object> relationships) {
        for (String otherChar : relationships.keySet()) {
            if (content.contains(charName) && content.contains(otherChar)) {
                String expectedRelation = (String) relationships.get(otherChar);
                String actualRelation = extractRelationshipFromContent(content, charName, otherChar);
                
                if (actualRelation != null && !isRelationshipConsistent(expectedRelation, actualRelation)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检测情节点连接
     */
    private boolean isPlotPointConnected(String plotPoint, Map<String, Object> thread) {
        String threadDescription = (String) thread.get("description");
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) thread.get("keywords");
        
        if (threadDescription != null && plotPoint.contains(threadDescription)) {
            return true;
        }
        
        if (keywords != null) {
            for (String keyword : keywords) {
                if (plotPoint.contains(keyword)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检测未解决冲突跳跃
     */
    private boolean hasUnresolvedConflictJump(String lastConflict, String currentConflict) {
        // 如果上一章有重大冲突，这一章应该有处理或延续
        String[] majorConflicts = {"战斗", "冲突", "危机", "威胁", "敌人"};
        
        for (String conflict : majorConflicts) {
            if (lastConflict.contains(conflict) && 
                !currentConflict.contains(conflict) && 
                !currentConflict.contains("解决") &&
                !currentConflict.contains("结束")) {
                return true; // 重大冲突被无视
            }
        }
        
        return false;
    }
    
    /**
     * 检查伏笔处理
     */
    private double checkForeshadowingHandling(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) memoryBank.get("foreshadowing");
        
        if (foreshadowing == null) return 0.0;
        
        String chapterContent = (String) chapter.get("content");
        if (chapterContent == null) return 0.0;
        
        double score = 0.0;
        int relevantHints = 0;
        
        for (Map<String, Object> hint : foreshadowing) {
            String hintContent = (String) hint.get("content");
            String status = (String) hint.get("status");
            
            if (hintContent != null && chapterContent.contains(hintContent)) {
                relevantHints++;
                if ("resolved".equals(status)) {
                    score += 1.0; // 伏笔得到合理解决
                } else if ("developing".equals(status)) {
                    score += 0.5; // 伏笔在发展中
                }
            }
        }
        
        return relevantHints > 0 ? score / relevantHints : 0.0;
    }

    private List<String> generateCoherenceIssues(Map<String, Object> analysis) {
        List<String> issues = new ArrayList<>();
        
        if ((Double) analysis.get("timelineScore") < 0.6) {
            issues.add("时间线存在逻辑问题");
        }
        if ((Double) analysis.get("characterScore") < 0.6) {
            issues.add("角色行为与前文不一致");
        }
        if ((Double) analysis.get("plotScore") < 0.6) {
            issues.add("情节发展缺乏连续性");
        }
        
        return issues;
    }

    private List<String> generateCoherenceSuggestions(Map<String, Object> analysis, String genre) {
        List<String> suggestions = new ArrayList<>();
        
        double overallScore = (Double) analysis.get("overallScore");
        
        if (overallScore < 0.5) {
            suggestions.add("建议重写此章节，确保与前文保持连贯");
        } else if (overallScore < 0.7) {
            suggestions.add("建议适当调整内容，增强连贯性");
        } else {
            suggestions.add("连贯性良好，建议保持当前风格");
        }
        
        return suggestions;
    }

    private String buildCoherenceRepairPrompt(String content, List<String> issues, Map<String, Object> memoryBank, String genre) {
        StringBuilder prompt = new StringBuilder();
        
        // 使用注入的enhancedPromptService构建更高质量的修复提示词
        String basePrompt = enhancedPromptService.getHumanizedWritingPrompt(
            createTempNovel(genre), 
            createTempChapterPlan(content), 
            memoryBank, 
            "连贯性修复：" + String.join("; ", issues)
        );
        
        prompt.append("你是一位资深小说编辑，专门修复长篇小说的连贯性问题。\n\n");
        prompt.append("【检测到的连贯性问题】\n");
        for (String issue : issues) {
            prompt.append("• ").append(issue).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("【原始内容】\n");
        prompt.append(content);
        prompt.append("\n\n");
        
        prompt.append("【修复指导原则】\n");
        prompt.append("1. 保持核心情节和角色不变\n");
        prompt.append("2. 修复逻辑漏洞和时间线问题\n");
        prompt.append("3. 确保角色行为与性格一致\n");
        prompt.append("4. 保持").append(genre).append("类型的写作风格\n");
        prompt.append("5. 避免AI痕迹，保持人性化表达\n");
        prompt.append("6. 直接输出修复后的完整内容\n\n");
        
        // 添加记忆库上下文
        if (memoryBank != null) {
            prompt.append("【创作上下文】\n");
            if (memoryBank.containsKey("characters")) {
                prompt.append("角色信息: ").append(memoryBank.get("characters")).append("\n");
            }
            if (memoryBank.containsKey("worldSettings")) {
                prompt.append("世界设定: ").append(memoryBank.get("worldSettings")).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("开始修复:");
        
        return prompt.toString();
    }

    private List<String> identifyRepairs(String original, String repaired) {
        List<String> repairs = new ArrayList<>();
        
        // 简化的修复识别逻辑
        if (!original.equals(repaired)) {
            repairs.add("内容已进行连贯性优化");
        }
        
        return repairs;
    }

    /**
     * 检测力量体系违反
     */
    private boolean hasPowerSystemViolation(String content, String powerSystem) {
        // 检测是否违反了既定的力量体系规则
        String[] powerLevels = powerSystem.split(",");
        
        // 检测是否出现了未定义的等级
        for (String level : powerLevels) {
            if (content.contains(level.trim())) {
                return false; // 找到合法等级
            }
        }
        
        // 检测是否提到了未定义的能力
        String[] undefinedAbilities = {"时间停止", "复活", "无敵", "神级"};
        for (String ability : undefinedAbilities) {
            if (content.contains(ability)) {
                return true; // 发现未定义能力
            }
        }
        
        return false;
    }
    
    /**
     * 检测地理设定违反
     */
    private boolean hasGeographyViolation(String content, Map<String, Object> geography) {
        @SuppressWarnings("unchecked")
        List<String> locations = (List<String>) geography.get("locations");
        @SuppressWarnings("unchecked")
        Map<String, String> distances = (Map<String, String>) geography.get("distances");
        
        // 检测是否提到了未定义的地点
        if (locations != null) {
            String[] mentionedLocations = extractMentionedLocations(content);
            for (String mentioned : mentionedLocations) {
                if (!locations.contains(mentioned)) {
                    return true; // 提到了未定义的地点
                }
            }
        }
        
        // 检测是否违反了距离设定
        if (distances != null) {
            return hasDistanceViolation(content, distances);
        }
        
        return false;
    }
    
    /**
     * 检测社会规则违反
     */
    private boolean hasSocialRuleViolation(String content, List<String> socialRules) {
        // 检测是否违反了社会规则
        for (String rule : socialRules) {
            if (rule.contains("禁止")) {
                String prohibitedAction = rule.replace("禁止", "").trim();
                if (content.contains(prohibitedAction)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 计算平均句长
     */
    private double calculateAverageSentenceLength(String content) {
        if (content == null || content.trim().isEmpty()) return 0.0;
        
        String[] sentences = content.split("[\u3002\uff01\uff1f\u2026]");
        int totalLength = 0;
        int validSentences = 0;
        
        for (String sentence : sentences) {
            if (sentence.trim().length() > 0) {
                totalLength += sentence.trim().length();
                validSentences++;
            }
        }
        
        return validSentences > 0 ? (double) totalLength / validSentences : 0.0;
    }
    
    /**
     * 分析词汇风格
     */
    private Map<String, Integer> analyzeVocabularyStyle(String content) {
        Map<String, Integer> styleMetrics = new HashMap<>();
        
        // 统计不同类型词汇的使用频率
        String[] formalWords = {"然而", "因此", "由于", "与此同时", "与此相反"};
        String[] informalWords = {"嗯", "哇", "哈", "啦", "呀"};
        String[] literaryWords = {"微风", "夕阳", "月色", "春风", "秋雨"};
        String[] modernWords = {"网络", "手机", "电脑", "汽车", "飞机"};
        
        styleMetrics.put("formal", countWordsInContent(content, formalWords));
        styleMetrics.put("informal", countWordsInContent(content, informalWords));
        styleMetrics.put("literary", countWordsInContent(content, literaryWords));
        styleMetrics.put("modern", countWordsInContent(content, modernWords));
        
        return styleMetrics;
    }
    
    /**
     * 计算词汇一致性
     */
    private double calculateVocabularyConsistency(Map<String, Integer> current, Map<String, Integer> previous) {
        if (previous.isEmpty()) return 1.0;
        
        double consistency = 1.0;
        
        for (String styleType : current.keySet()) {
            int currentCount = current.getOrDefault(styleType, 0);
            int previousCount = previous.getOrDefault(styleType, 0);
            
            if (previousCount > 0) {
                double ratio = (double) currentCount / previousCount;
                if (ratio > 2.0 || ratio < 0.5) { // 风格变化超过50%
                    consistency *= 0.8;
                }
            }
        }
        
        return consistency;
    }
    
    /**
     * 分析对话风格一致性
     */
    private double analyzeDialogueStyleConsistency(String currentContent, List<Map<String, Object>> previous) {
        String[] currentDialogues = extractDialogues(currentContent);
        List<String> previousDialogues = new ArrayList<>();
        
        for (Map<String, Object> prevChapter : previous) {
            String prevContent = (String) prevChapter.get("content");
            if (prevContent != null) {
                String[] prevDialogs = extractDialogues(prevContent);
                previousDialogues.addAll(Arrays.asList(prevDialogs));
            }
        }
        
        if (currentDialogues.length == 0 || previousDialogues.isEmpty()) {
            return 1.0; // 没有对话比较，认为一致
        }
        
        // 分析对话风格特征
        double currentDialogueLength = calculateAverageDialogueLength(currentDialogues);
        double previousDialogueLength = calculateAverageDialogueLength(previousDialogues.toArray(new String[0]));
        
        double lengthConsistency = 1.0 - Math.abs(currentDialogueLength - previousDialogueLength) / previousDialogueLength;
        
        return Math.max(0.0, lengthConsistency);
    }
    
    // ========== 工具方法 ==========
    
    private String[] extractCharacterBehaviors(String content, String charName) {
        // 简化实现：提取角色相关的行为描述
        List<String> behaviors = new ArrayList<>();
        String[] sentences = content.split("[\u3002\uff01\uff1f]");
        
        for (String sentence : sentences) {
            if (sentence.contains(charName)) {
                behaviors.add(sentence.trim());
            }
        }
        
        return behaviors.toArray(new String[0]);
    }
    
    private boolean isPersonalityInconsistent(String behavior, String personality) {
        // 简化检测：检查行为是否与性格相符
        if (personality.contains("善良") && behavior.contains("杀死")) {
            return true;
        }
        if (personality.contains("胆小") && behavior.contains("勇敢极击")) {
            return true;
        }
        if (personality.contains("内向") && behavior.contains("大声喂叫")) {
            return true;
        }
        return false;
    }
    
    private String[] extractUsedAbilities(String content, String charName) {
        List<String> abilities = new ArrayList<>();
        String[] abilityKeywords = {"使用", "施展", "发动", "释放"};
        
        for (String keyword : abilityKeywords) {
            int index = content.indexOf(charName + keyword);
            if (index != -1) {
                // 提取能力名称（简化实现）
                String substring = content.substring(index, Math.min(index + 50, content.length()));
                abilities.add(substring);
            }
        }
        
        return abilities.toArray(new String[0]);
    }
    
    private String extractRelationshipFromContent(String content, String char1, String char2) {
        // 简化实现：从内容中提取角色关系
        if (content.contains(char1 + "和" + char2)) {
            if (content.contains("朋友")) return "朋友";
            if (content.contains("敌人")) return "敌人";
            if (content.contains("恋人")) return "恋人";
        }
        return null;
    }
    
    private boolean isRelationshipConsistent(String expected, String actual) {
        return expected.equals(actual) || 
               (expected.contains("好") && actual.contains("朋友")) ||
               (expected.contains("敌") && actual.contains("敌人"));
    }
    
    private String[] extractMentionedLocations(String content) {
        // 简化实现：提取提到的地点
        List<String> locations = new ArrayList<>();
        String[] locationKeywords = {"城市", "村庄", "山脉", "河流", "森林", "宫殿"};
        
        for (String keyword : locationKeywords) {
            if (content.contains(keyword)) {
                locations.add(keyword);
            }
        }
        
        return locations.toArray(new String[0]);
    }
    
    private boolean hasDistanceViolation(String content, Map<String, String> distances) {
        // 检测距离设定是否被违反
        for (String route : distances.keySet()) {
            String distance = distances.get(route);
            if (content.contains(route) && content.contains("瑞间到达")) {
                if (distance.contains("几天") || distance.contains("一天")) {
                    return true; // 违反距离设定
                }
            }
        }
        return false;
    }
    
    private int countWordsInContent(String content, String[] words) {
        int count = 0;
        for (String word : words) {
            int index = 0;
            while ((index = content.indexOf(word, index)) != -1) {
                count++;
                index += word.length();
            }
        }
        return count;
    }
    
    private String[] extractDialogues(String content) {
        // 提取对话内容（被引号包围的文本）
        List<String> dialogues = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("“([^”]*)”");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            dialogues.add(matcher.group(1));
        }
        
        return dialogues.toArray(new String[0]);
    }
    
    private double calculateAverageDialogueLength(String[] dialogues) {
        if (dialogues.length == 0) return 0.0;
        
        int totalLength = 0;
        for (String dialogue : dialogues) {
            totalLength += dialogue.length();
        }
        
        return (double) totalLength / dialogues.length;
    }

    // ========== 预防性检查方法完整实现 ==========
    
    /**
     * 检查情节逻辑一致性
     */
    private boolean checkPlotLogicConsistency(Map<String, Object> planned, List<Map<String, Object>> recent, Map<String, Object> memoryBank) {
        String plannedEvent = (String) planned.get("coreEvent");
        if (plannedEvent == null) return true;
        
        // 1. 检查与最近章节的逻辑关系
        if (!recent.isEmpty()) {
            Map<String, Object> lastChapter = recent.get(recent.size() - 1);
            String lastEvent = (String) lastChapter.get("coreEvent");
            
            if (lastEvent != null && hasLogicalConflict(lastEvent, plannedEvent)) {
                return false;
            }
        }
        
        // 2. 检查与活跃情节线的一致性
        if (memoryBank != null && memoryBank.containsKey("plotThreads")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) memoryBank.get("plotThreads");
            
            for (Map<String, Object> thread : plotThreads) {
                String status = (String) thread.get("status");
                if ("active".equals(status)) {
                    String threadGoal = (String) thread.get("goal");
                    if (threadGoal != null && conflictsWithThread(plannedEvent, threadGoal)) {
                        return false;
                    }
                }
            }
        }
        
        // 3. 检查是否符合角色动机
        return isEventMotivationConsistent(plannedEvent, memoryBank);
    }
    
    /**
     * 检查角色行为一致性
     */
    private boolean checkCharacterBehaviorConsistency(Map<String, Object> planned, Map<String, Object> memoryBank) {
        String plannedBehavior = (String) planned.get("characterActions");
        if (plannedBehavior == null) return true;
        
        if (memoryBank == null || !memoryBank.containsKey("characters")) {
            return true; // 没有角色信息，认为没问题
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.get("characters");
        
        // 检查每个参与角色的行为合理性
        for (String charName : characters.keySet()) {
            if (plannedBehavior.contains(charName)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> charInfo = (Map<String, Object>) characters.get(charName);
                
                String personality = (String) charInfo.get("personality");
                String currentState = (String) charInfo.get("currentState");
                String motivation = (String) charInfo.get("motivation");
                
                if (!isBehaviorConsistentWithCharacter(plannedBehavior, charName, personality, currentState, motivation)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 检查时间线合理性
     */
    private boolean checkTimelineReasonability(Map<String, Object> planned, List<Map<String, Object>> recent) {
        String plannedTime = (String) planned.get("timeReference");
        if (plannedTime == null) return true;
        
        if (recent.isEmpty()) return true;
        
        // 检查时间的合理推进
        Map<String, Object> lastChapter = recent.get(recent.size() - 1);
        String lastTime = (String) lastChapter.get("timeReference");
        
        if (lastTime != null) {
            // 检查时间跳跃是否过大
            if (hasUnreasonableTimeJump(lastTime, plannedTime)) {
                return false;
            }
            
            // 检查时间是否倒流
            if (hasTimeReversal(lastTime, plannedTime)) {
                return false;
            }
        }
        
        // 检查事件持续时间的合理性
        String plannedEvent = (String) planned.get("coreEvent");
        if (plannedEvent != null) {
            return isEventDurationReasonable(plannedEvent, plannedTime);
        }
        
        return true;
    }
    
    /**
     * 检查世界设定一致性
     */
    private boolean checkWorldSettingConsistency(Map<String, Object> planned, Map<String, Object> memoryBank) {
        if (memoryBank == null || !memoryBank.containsKey("worldSettings")) {
            return true;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> worldSettings = (Map<String, Object>) memoryBank.get("worldSettings");
        
        String plannedContent = (String) planned.get("outline");
        if (plannedContent == null) return true;
        
        // 1. 检查力量体系一致性
        String powerSystem = (String) worldSettings.get("powerSystem");
        if (powerSystem != null && violatesPowerSystem(plannedContent, powerSystem)) {
            return false;
        }
        
        // 2. 检查地理一致性
        @SuppressWarnings("unchecked")
        Map<String, Object> geography = (Map<String, Object>) worldSettings.get("geography");
        if (geography != null && violatesGeography(plannedContent, geography)) {
            return false;
        }
        
        // 3. 检查社会规则一致性
        @SuppressWarnings("unchecked")
        List<String> socialRules = (List<String>) worldSettings.get("socialRules");
        if (socialRules != null && violatesSocialRules(plannedContent, socialRules)) {
            return false;
        }
        
        return true;
    }
    
    // ========== 记忆库更新方法完整实现 ==========
    
    /**
     * 更新角色状态
     */
    private void updateCharacterStates(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        String chapterContent = (String) chapter.get("content");
        if (chapterContent == null) return;
        
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.computeIfAbsent("characters", k -> new HashMap<>());
        
        // 提取本章中出现的角色及其状态变化
        Map<String, String> characterUpdates = extractCharacterStateUpdates(chapterContent);
        
        for (String charName : characterUpdates.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> charInfo = (Map<String, Object>) characters.computeIfAbsent(charName, k -> new HashMap<>());
            
            String newState = characterUpdates.get(charName);
            charInfo.put("currentState", newState);
            charInfo.put("lastAppearance", chapter.get("chapterNumber"));
            charInfo.put("lastUpdated", new Date().toString());
            
            // 更新角色关系
            updateCharacterRelationships(charInfo, chapterContent, charName);
        }
    }
    
    /**
     * 更新情节线
     */
    private void updatePlotThreads(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) memoryBank.computeIfAbsent("plotThreads", k -> new ArrayList<>());
        
        String chapterContent = (String) chapter.get("content");
        String coreEvent = (String) chapter.get("coreEvent");
        
        if (chapterContent != null && coreEvent != null) {
            // 更新现有情节线的状态
            for (Map<String, Object> thread : plotThreads) {
                String threadId = (String) thread.get("id");
                if (isThreadAffectedByChapter(thread, chapterContent, coreEvent)) {
                    updateThreadProgress(thread, chapter);
                }
            }
            
            // 检查是否需要创建新的情节线
            if (shouldCreateNewThread(coreEvent, plotThreads)) {
                Map<String, Object> newThread = createNewPlotThread(coreEvent, chapter);
                plotThreads.add(newThread);
            }
        }
    }
    
    /**
     * 更新世界设定
     */
    private void updateWorldSettings(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        @SuppressWarnings("unchecked")
        Map<String, Object> worldSettings = (Map<String, Object>) memoryBank.computeIfAbsent("worldSettings", k -> new HashMap<>());
        
        String chapterContent = (String) chapter.get("content");
        if (chapterContent == null) return;
        
        // 提取新的世界设定信息
        Map<String, Object> newSettings = extractWorldSettingsFromContent(chapterContent);
        
        for (String settingType : newSettings.keySet()) {
            Object newValue = newSettings.get(settingType);
            Object existingValue = worldSettings.get(settingType);
            
            if (existingValue == null) {
                worldSettings.put(settingType, newValue);
            } else {
                // 合并现有设定
                Object mergedValue = mergeWorldSettings(existingValue, newValue, settingType);
                worldSettings.put(settingType, mergedValue);
            }
        }
        
        worldSettings.put("lastUpdated", new Date().toString());
    }
    
    /**
     * 更新时间线
     */
    private void updateTimeline(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) memoryBank.computeIfAbsent("timeline", k -> new ArrayList<>());
        
        Map<String, Object> timelineEntry = new HashMap<>();
        timelineEntry.put("chapterNumber", chapter.get("chapterNumber"));
        timelineEntry.put("timeReference", chapter.get("timeReference"));
        timelineEntry.put("coreEvent", chapter.get("coreEvent"));
        timelineEntry.put("timestamp", new Date().toString());
        
        timeline.add(timelineEntry);
        
        // 保持时间线长度在合理范围内
        if (timeline.size() > 100) {
            timeline.remove(0); // 移除最早的记录
        }
    }
    
    /**
     * 更新伏笔状态
     */
    private void updateForeshadowing(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) memoryBank.computeIfAbsent("foreshadowing", k -> new ArrayList<>());
        
        String chapterContent = (String) chapter.get("content");
        if (chapterContent == null) return;
        
        // 检查现有伏笔的状态更新
        for (Map<String, Object> hint : foreshadowing) {
            String hintContent = (String) hint.get("content");
            String currentStatus = (String) hint.get("status");
            
            if (hintContent != null && chapterContent.contains(hintContent)) {
                if ("planned".equals(currentStatus)) {
                    hint.put("status", "planted");
                    hint.put("plantedChapter", chapter.get("chapterNumber"));
                } else if ("planted".equals(currentStatus) || "developing".equals(currentStatus)) {
                    if (isForeshadowingResolved(hintContent, chapterContent)) {
                        hint.put("status", "resolved");
                        hint.put("resolvedChapter", chapter.get("chapterNumber"));
                    } else {
                        hint.put("status", "developing");
                    }
                }
            }
        }
        
        // 检查是否有新的伏笔被埋设
        List<String> newHints = extractNewForeshadowing(chapterContent);
        for (String newHint : newHints) {
            Map<String, Object> hintEntry = new HashMap<>();
            hintEntry.put("content", newHint);
            hintEntry.put("status", "planted");
            hintEntry.put("plantedChapter", chapter.get("chapterNumber"));
            hintEntry.put("importance", 1); // 默认重要度
            hintEntry.put("createdAt", new Date().toString());
            
            foreshadowing.add(hintEntry);
        }
    }
    
    /**
     * 添加章节摘要
     */
    private void addChapterSummary(Map<String, Object> chapter, Map<String, Object> memoryBank) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapterSummaries = (List<Map<String, Object>>) memoryBank.computeIfAbsent("chapterSummaries", k -> new ArrayList<>());
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("chapterNumber", chapter.get("chapterNumber"));
        summary.put("title", chapter.get("title"));
        summary.put("coreEvent", chapter.get("coreEvent"));
        summary.put("wordCount", chapter.get("wordCount"));
        summary.put("timeReference", chapter.get("timeReference"));
        summary.put("createdAt", new Date().toString());
        
        // 生成简短摘要
        String content = (String) chapter.get("content");
        if (content != null) {
            summary.put("briefSummary", generateChapterBriefSummary(content));
        }
        
        chapterSummaries.add(summary);
        
        // 保持摘要列表在合理长度
        if (chapterSummaries.size() > 200) {
            chapterSummaries.remove(0);
        }
    }

    // ========== 辅助工具方法完整实现 ==========
    
    private boolean hasLogicalConflict(String lastEvent, String plannedEvent) {
        // 检测逻辑冲突的事件组合
        Map<String, String[]> conflictPairs = new HashMap<>();
        conflictPairs.put("死亡", new String[]{"说话", "行动", "思考"});
        conflictPairs.put("离开", new String[]{"继续在原地", "没有移动"});
        conflictPairs.put("睡觉", new String[]{"立即行动", "大声说话"});
        conflictPairs.put("受伤", new String[]{"完全恢复", "毫无影响"});
        
        for (String event : conflictPairs.keySet()) {
            if (lastEvent.contains(event)) {
                for (String conflict : conflictPairs.get(event)) {
                    if (plannedEvent.contains(conflict)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean conflictsWithThread(String event, String threadGoal) {
        // 检查事件是否与情节线目标冲突
        if (threadGoal.contains("保护") && event.contains("伤害")) {
            return true;
        }
        if (threadGoal.contains("隐藏") && event.contains("暴露")) {
            return true;
        }
        if (threadGoal.contains("和平") && event.contains("战争")) {
            return true;
        }
        return false;
    }
    
    private boolean isEventMotivationConsistent(String event, Map<String, Object> memoryBank) {
        if (memoryBank == null) return true;
        
        // 检查事件是否符合角色动机
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.get("characters");
        if (characters == null) return true;
        
        for (String charName : characters.keySet()) {
            if (event.contains(charName)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> charInfo = (Map<String, Object>) characters.get(charName);
                String motivation = (String) charInfo.get("motivation");
                
                if (motivation != null && isEventAgainstMotivation(event, motivation)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private boolean isBehaviorConsistentWithCharacter(String behavior, String charName, 
            String personality, String currentState, String motivation) {
        
        // 检查性格一致性
        if (personality != null) {
            if (personality.contains("善良") && behavior.contains(charName + "杀死")) {
                return false;
            }
            if (personality.contains("胆小") && behavior.contains(charName + "勇敢冲锋")) {
                return false;
            }
            if (personality.contains("诚实") && behavior.contains(charName + "撒谎")) {
                return false;
            }
        }
        
        // 检查当前状态一致性
        if (currentState != null) {
            if (currentState.contains("重伤") && behavior.contains(charName + "激烈战斗")) {
                return false;
            }
            if (currentState.contains("昏迷") && behavior.contains(charName + "说话")) {
                return false;
            }
        }
        
        // 检查动机一致性
        if (motivation != null && isEventAgainstMotivation(behavior, motivation)) {
            return false;
        }
        
        return true;
    }
    
    private boolean hasUnreasonableTimeJump(String lastTime, String plannedTime) {
        // 检查时间跳跃是否过大
        Map<String, Integer> timeOrder = new HashMap<>();
        timeOrder.put("凌晨", 1);
        timeOrder.put("早上", 2);
        timeOrder.put("上午", 3);
        timeOrder.put("中午", 4);
        timeOrder.put("下午", 5);
        timeOrder.put("傍晚", 6);
        timeOrder.put("晚上", 7);
        timeOrder.put("深夜", 8);
        
        Integer lastOrder = getTimeOrder(lastTime, timeOrder);
        Integer plannedOrder = getTimeOrder(plannedTime, timeOrder);
        
        if (lastOrder != null && plannedOrder != null) {
            int diff = Math.abs(plannedOrder - lastOrder);
            return diff > 4; // 时间跳跃超过4个时段
        }
        
        return false;
    }
    
    private boolean hasTimeReversal(String lastTime, String plannedTime) {
        // 检查时间是否倒流
        if (lastTime.contains("明天") && plannedTime.contains("昨天")) {
            return true;
        }
        if (lastTime.contains("下午") && plannedTime.contains("上午") && 
            !plannedTime.contains("第二天") && !plannedTime.contains("次日")) {
            return true;
        }
        return false;
    }
    
    private boolean isEventDurationReasonable(String event, String timeReference) {
        // 检查事件持续时间的合理性
        if (event.contains("长途旅行") && timeReference.contains("几分钟")) {
            return false;
        }
        if (event.contains("吃饭") && timeReference.contains("几小时")) {
            return false;
        }
        if (event.contains("睡觉") && timeReference.contains("几分钟")) {
            return false;
        }
        return true;
    }
    
    private boolean violatesPowerSystem(String content, String powerSystem) {
        // 检查是否违反力量体系
        String[] systemLevels = powerSystem.split(",");
        
        // 检查是否出现了体系外的等级
        String[] invalidLevels = {"无敌", "神级", "超越极限"};
        for (String invalid : invalidLevels) {
            if (content.contains(invalid)) {
                boolean found = false;
                for (String valid : systemLevels) {
                    if (valid.trim().equals(invalid)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return true;
            }
        }
        return false;
    }
    
    private boolean violatesGeography(String content, Map<String, Object> geography) {
        @SuppressWarnings("unchecked")
        List<String> validLocations = (List<String>) geography.get("locations");
        if (validLocations == null) return false;
        
        // 提取内容中提到的地点
        String[] mentionedPlaces = extractMentionedLocations(content);
        for (String place : mentionedPlaces) {
            if (!validLocations.contains(place)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean violatesSocialRules(String content, List<String> socialRules) {
        for (String rule : socialRules) {
            if (rule.startsWith("禁止")) {
                String forbidden = rule.substring(2);
                if (content.contains(forbidden)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // 记忆库更新辅助方法
    private Map<String, String> extractCharacterStateUpdates(String content) {
        Map<String, String> updates = new HashMap<>();
        
        // 简化的状态提取逻辑
        String[] stateKeywords = {"受伤", "恢复", "愤怒", "高兴", "疲惫", "兴奋"};
        String[] characters = extractCharacterNames(content);
        
        for (String character : characters) {
            for (String state : stateKeywords) {
                if (content.contains(character + state) || content.contains(character + "变得" + state)) {
                    updates.put(character, state);
                    break;
                }
            }
        }
        
        return updates;
    }
    
    private void updateCharacterRelationships(Map<String, Object> charInfo, String content, String charName) {
        @SuppressWarnings("unchecked")
        Map<String, String> relationships = (Map<String, String>) charInfo.computeIfAbsent("relationships", k -> new HashMap<>());
        
        // 检测关系变化的关键词
        String[] relationshipKeywords = {"朋友", "敌人", "恋人", "师父", "弟子", "同伴"};
        
        for (String keyword : relationshipKeywords) {
            if (content.contains(charName) && content.contains(keyword)) {
                // 提取与该角色产生关系的其他角色
                String[] otherChars = extractOtherCharactersInRelation(content, charName, keyword);
                for (String otherChar : otherChars) {
                    relationships.put(otherChar, keyword);
                }
            }
        }
    }
    
    private boolean isThreadAffectedByChapter(Map<String, Object> thread, String content, String coreEvent) {
        String threadDescription = (String) thread.get("description");
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) thread.get("keywords");
        
        if (threadDescription != null && (content.contains(threadDescription) || coreEvent.contains(threadDescription))) {
            return true;
        }
        
        if (keywords != null) {
            for (String keyword : keywords) {
                if (content.contains(keyword) || coreEvent.contains(keyword)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void updateThreadProgress(Map<String, Object> thread, Map<String, Object> chapter) {
        // 更新情节线进度
        Integer currentProgress = (Integer) thread.getOrDefault("progress", 0);
        thread.put("progress", currentProgress + 1);
        thread.put("lastUpdatedChapter", chapter.get("chapterNumber"));
        thread.put("lastUpdated", new Date().toString());
        
        // 检查是否应该完成这个情节线
        Integer maxProgress = (Integer) thread.get("expectedProgress");
        if (maxProgress != null && currentProgress + 1 >= maxProgress) {
            thread.put("status", "completed");
            thread.put("completedAt", new Date().toString());
        }
    }
    
    private boolean shouldCreateNewThread(String coreEvent, List<Map<String, Object>> existingThreads) {
        // 检查是否需要为新事件创建新的情节线
        String[] newThreadKeywords = {"新的挑战", "新任务", "新敌人", "新目标"};
        
        for (String keyword : newThreadKeywords) {
            if (coreEvent.contains(keyword)) {
                // 检查是否已经有相似的情节线
                boolean hasExisting = existingThreads.stream().anyMatch(thread -> {
                    String desc = (String) thread.get("description");
                    return desc != null && desc.contains(keyword.substring(1)); // 去掉"新的"
                });
                
                if (!hasExisting) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private Map<String, Object> createNewPlotThread(String coreEvent, Map<String, Object> chapter) {
        Map<String, Object> newThread = new HashMap<>();
        newThread.put("id", "thread_" + System.currentTimeMillis());
        newThread.put("description", coreEvent);
        newThread.put("status", "active");
        newThread.put("startedChapter", chapter.get("chapterNumber"));
        newThread.put("progress", 1);
        newThread.put("expectedProgress", 10); // 预期10章完成
        newThread.put("createdAt", new Date().toString());
        newThread.put("keywords", extractKeywordsFromEvent(coreEvent));
        
        return newThread;
    }
    
    private Map<String, Object> extractWorldSettingsFromContent(String content) {
        Map<String, Object> settings = new HashMap<>();
        
        // 提取地点信息
        List<String> locations = Arrays.asList(extractMentionedLocations(content));
        if (!locations.isEmpty()) {
            settings.put("newLocations", locations);
        }
        
        // 提取力量相关信息
        String[] powerKeywords = {"修为", "境界", "实力", "等级", "能力"};
        for (String keyword : powerKeywords) {
            if (content.contains(keyword)) {
                settings.put("powerSystemUpdate", extractPowerSystemInfo(content, keyword));
                break;
            }
        }
        
        return settings;
    }
    
    private Object mergeWorldSettings(Object existing, Object newValue, String settingType) {
        if ("newLocations".equals(settingType)) {
            @SuppressWarnings("unchecked")
            List<String> existingList = (List<String>) existing;
            @SuppressWarnings("unchecked")
            List<String> newList = (List<String>) newValue;
            
            Set<String> merged = new HashSet<>(existingList);
            merged.addAll(newList);
            return new ArrayList<>(merged);
        }
        
        // 默认用新值覆盖
        return newValue;
    }
    
    private boolean isForeshadowingResolved(String hintContent, String chapterContent) {
        // 检查伏笔是否在本章得到解决
        String[] resolutionKeywords = {"真相", "答案", "原来", "终于", "揭开", "解开"};
        
        for (String keyword : resolutionKeywords) {
            if (chapterContent.contains(keyword) && chapterContent.contains(hintContent)) {
                return true;
            }
        }
        
        return false;
    }
    
    private List<String> extractNewForeshadowing(String content) {
        List<String> hints = new ArrayList<>();
        
        // 检测可能的新伏笔
        String[] hintKeywords = {"奇怪", "异常", "不对劲", "预感", "似乎", "好像"};
        
        for (String keyword : hintKeywords) {
            int index = content.indexOf(keyword);
            if (index != -1) {
                // 提取包含关键词的句子作为潜在伏笔
                String sentence = extractSentenceContaining(content, index);
                if (sentence != null && sentence.length() > 10) {
                    hints.add(sentence);
                }
            }
        }
        
        return hints;
    }
    
    private String generateChapterBriefSummary(String content) {
        // 生成章节简要摘要
        if (content.length() <= 100) {
            return content;
        }
        
        // 简化的摘要生成：取前50字和后50字
        return content.substring(0, 50) + "..." + 
               content.substring(Math.max(0, content.length() - 50));
    }
    
    // 更多辅助工具方法
    private Integer getTimeOrder(String timeStr, Map<String, Integer> timeOrder) {
        for (String timeKey : timeOrder.keySet()) {
            if (timeStr.contains(timeKey)) {
                return timeOrder.get(timeKey);
            }
        }
        return null;
    }
    
    private boolean isEventAgainstMotivation(String event, String motivation) {
        if (motivation.contains("保护") && event.contains("伤害")) return true;
        if (motivation.contains("和平") && event.contains("战争")) return true;
        if (motivation.contains("正义") && event.contains("邪恶")) return true;
        return false;
    }
    
    private String[] extractCharacterNames(String content) {
        // 简化的角色名提取
        List<String> names = new ArrayList<>();
        String[] commonNames = {"李明", "张三", "王五", "赵六", "主角", "反派"};
        
        for (String name : commonNames) {
            if (content.contains(name)) {
                names.add(name);
            }
        }
        
        return names.toArray(new String[0]);
    }
    
    private String[] extractOtherCharactersInRelation(String content, String charName, String relation) {
        // 简化实现：提取与指定角色有关系的其他角色
        List<String> others = new ArrayList<>();
        String[] allChars = extractCharacterNames(content);
        
        for (String other : allChars) {
            if (!other.equals(charName) && 
                content.contains(charName + "和" + other + relation)) {
                others.add(other);
            }
        }
        
        return others.toArray(new String[0]);
    }
    
    private List<String> extractKeywordsFromEvent(String event) {
        // 从事件中提取关键词
        String[] words = event.split("\\s+");
        List<String> keywords = new ArrayList<>();
        
        for (String word : words) {
            if (word.length() > 1 && !isCommonWord(word)) {
                keywords.add(word);
            }
        }
        
        return keywords;
    }
    
    private String extractPowerSystemInfo(String content, String keyword) {
        int index = content.indexOf(keyword);
        if (index != -1) {
            return content.substring(index, Math.min(index + 20, content.length()));
        }
        return "";
    }
    
    private String extractSentenceContaining(String content, int keywordIndex) {
        // 提取包含指定位置关键词的句子
        int start = content.lastIndexOf("。", keywordIndex);
        if (start == -1) start = 0;
        else start++;
        
        int end = content.indexOf("。", keywordIndex);
        if (end == -1) end = content.length();
        else end++;
        
        if (start < end) {
            return content.substring(start, end).trim();
        }
        return null;
    }
    
    private boolean isCommonWord(String word) {
        String[] commonWords = {"的", "了", "在", "是", "我", "你", "他", "她", "它", "这", "那", "和", "与"};
        for (String common : commonWords) {
            if (word.equals(common)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 创建临时Novel对象用于调用其他服务
     */
    private Novel createTempNovel(String genre) {
        Novel tempNovel = new Novel();
        tempNovel.setGenre(genre != null ? genre : "都市异能");
        tempNovel.setTitle("临时小说");
        tempNovel.setId(1L);
        return tempNovel;
    }
    
    /**
     * 创建临时章节计划用于调用其他服务
     */
    private Map<String, Object> createTempChapterPlan(String content) {
        Map<String, Object> chapterPlan = new HashMap<>();
        chapterPlan.put("chapterNumber", 1);
        chapterPlan.put("title", "连贯性修复章节");
        chapterPlan.put("coreEvent", "修复内容连贯性");
        chapterPlan.put("estimatedWords", content != null ? content.length() : 1000);
        chapterPlan.put("content", content);
        return chapterPlan;
    }
}