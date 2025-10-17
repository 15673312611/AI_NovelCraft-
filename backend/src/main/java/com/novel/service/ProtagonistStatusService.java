package com.novel.service;

import com.novel.domain.entity.Novel;
import com.novel.domain.entity.Character;
import com.novel.repository.CharacterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 主角现状信息管理服务
 * 动态跟踪和更新主角的详细状态信息
 */
@Service
public class ProtagonistStatusService {

    private static final Logger logger = LoggerFactory.getLogger(ProtagonistStatusService.class);

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private NovelCraftAIService novelCraftAIService;

    /**
     * 获取主角详细现状信息
     */
    public String buildProtagonistStatus(Long novelId, Map<String, Object> memoryBank, int currentChapter) {
        logger.info("🎭 构建小说{}主角现状信息", novelId);
        
        try {
            // 获取主角角色信息
            Character protagonist = findProtagonist(novelId);
            if (protagonist == null) {
                return "暂无主角信息";
            }
            
            StringBuilder status = new StringBuilder();
            status.append("👑 **主角详细现状** (第").append(currentChapter).append("章)\n\n");
            
            // 基本信息
            status.append("### 📋 基本信息\n");
            status.append("- **姓名**: ").append(protagonist.getName()).append("\n");
            if (protagonist.getAlias() != null && !protagonist.getAlias().trim().isEmpty()) {
                status.append("- **别名**: ").append(protagonist.getAlias()).append("\n");
            }
            status.append("- **角色类型**: ").append(protagonist.getCharacterType()).append("\n");
            status.append("- **活跃状态**: ").append(protagonist.getStatus()).append("\n");
            status.append("- **重要性评分**: ").append(protagonist.getImportanceScore()).append("/100\n\n");
            
            // 角色特征
            if (protagonist.getPersonality() != null && !protagonist.getPersonality().trim().isEmpty()) {
                status.append("### 🧠 性格特征\n");
                status.append(protagonist.getPersonality()).append("\n\n");
            }
            
            if (protagonist.getAppearance() != null && !protagonist.getAppearance().trim().isEmpty()) {
                status.append("### 👤 外貌描述\n");
                status.append(protagonist.getAppearance()).append("\n\n");
            }
            
            if (protagonist.getBackground() != null && !protagonist.getBackground().trim().isEmpty()) {
                status.append("### 📖 背景设定\n");
                status.append(protagonist.getBackground()).append("\n\n");
            }
            
            // 动机和目标
            if (protagonist.getMotivation() != null && !protagonist.getMotivation().trim().isEmpty()) {
                status.append("### 🎯 动机驱动\n");
                status.append(protagonist.getMotivation()).append("\n\n");
            }
            
            if (protagonist.getGoals() != null && !protagonist.getGoals().trim().isEmpty()) {
                status.append("### 🏆 当前目标\n");
                status.append(protagonist.getGoals()).append("\n\n");
            }
            
            // 人际关系
            if (protagonist.getRelationships() != null && !protagonist.getRelationships().trim().isEmpty()) {
                status.append("### 👥 人际关系\n");
                status.append(protagonist.getRelationships()).append("\n\n");
            }
            
            // 出场统计
            status.append("### 📊 出场统计\n");
            if (protagonist.getFirstAppearanceChapter() != null) {
                status.append("- **首次出场**: 第").append(protagonist.getFirstAppearanceChapter()).append("章\n");
            }
            if (protagonist.getLastAppearanceChapter() != null) {
                status.append("- **最近出场**: 第").append(protagonist.getLastAppearanceChapter()).append("章\n");
            }
            if (protagonist.getAppearanceCount() != null) {
                status.append("- **出场次数**: ").append(protagonist.getAppearanceCount()).append("次\n");
            }
            status.append("\n");
            
            // 标签信息
            if (protagonist.getTags() != null && !protagonist.getTags().trim().isEmpty()) {
                status.append("### 🏷️ 角色标签\n");
                status.append(protagonist.getTags()).append("\n\n");
            }
            
            // 🆕 从记忆库读取动态主角状态（由概括生成）
            String dynamicStatus = buildDynamicStatusFromMemoryBank(memoryBank, protagonist.getName());
            if (!dynamicStatus.isEmpty()) {
                status.append(dynamicStatus);
            }
            
            return status.toString();
            
        } catch (Exception e) {
            logger.error("构建主角现状信息失败: {}", e.getMessage());
            return generateFallbackStatus(currentChapter);
        }
    }

    /**
     * 从记忆库构建动态主角状态（由概括生成）
     */
    @SuppressWarnings("unchecked")
    private String buildDynamicStatusFromMemoryBank(Map<String, Object> memoryBank, String protagonistName) {
        StringBuilder status = new StringBuilder();
        
        try {
            // 从记忆库读取主角状态
            Map<String, Object> protagonistStatus = (Map<String, Object>) memoryBank.get("protagonistStatus");
            
            if (protagonistStatus != null && !protagonistStatus.isEmpty()) {
                status.append("### ⚡ 当前动态状态（最新概括）\n");
                
                // 境界/等级
                Object realm = protagonistStatus.get("realm");
                if (realm != null && !realm.toString().trim().isEmpty()) {
                    status.append("- **境界/等级**: ").append(realm).append("\n");
                }
                
                // 技能
                Object skills = protagonistStatus.get("skills");
                if (skills instanceof List && !((List<?>) skills).isEmpty()) {
                    status.append("- **掌握技能**: ").append(String.join(", ", (List<String>) skills)).append("\n");
                }
                
                // 装备
                Object equipment = protagonistStatus.get("equipment");
                if (equipment instanceof List && !((List<?>) equipment).isEmpty()) {
                    status.append("- **拥有装备**: ").append(String.join(", ", (List<String>) equipment)).append("\n");
                }
                
                // 位置
                Object location = protagonistStatus.get("location");
                if (location != null && !location.toString().trim().isEmpty()) {
                    status.append("- **当前位置**: ").append(location).append("\n");
                }
                
                // 目标
                Object currentGoal = protagonistStatus.get("currentGoal");
                if (currentGoal != null && !currentGoal.toString().trim().isEmpty()) {
                    status.append("- **当前目标**: ").append(currentGoal).append("\n");
                }
                
                // 关系
                Object relationships = protagonistStatus.get("relationships");
                if (relationships instanceof Map && !((Map<?, ?>) relationships).isEmpty()) {
                    status.append("- **重要关系**: ");
                    Map<String, String> relMap = (Map<String, String>) relationships;
                    List<String> relList = new ArrayList<>();
                    for (Map.Entry<String, String> entry : relMap.entrySet()) {
                        relList.add(entry.getKey() + "(" + entry.getValue() + ")");
                    }
                    status.append(String.join(", ", relList)).append("\n");
                }
                
                status.append("\n");
                logger.info("✅ 从记忆库加载主角动态状态成功");
            } else {
                logger.debug("记忆库中暂无主角状态（第一章正常）");
            }
        } catch (Exception e) {
            logger.warn("从记忆库读取主角状态失败: {}", e.getMessage());
        }
        
        return status.toString();
    }

    /**
     * 查找主角
     */
    private Character findProtagonist(Long novelId) {
        try {
            List<Character> characters = characterRepository.findByNovelId(novelId);
            
            // 寻找主角类型的角色
            for (Character character : characters) {
                if ("PROTAGONIST".equals(character.getCharacterType())) {
                    return character;
                }
            }
            
            // 如果没有标记为主角的，返回重要性最高的角色
            return characters.stream()
                    .filter(c -> c.getImportanceScore() != null)
                    .max(Comparator.comparing(Character::getImportanceScore))
                    .orElse(null);
                    
        } catch (Exception e) {
            logger.warn("查找主角失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成动态状态分析
     */
    private String generateDynamicStatusAnalysis(Character protagonist, Map<String, Object> memoryBank, int currentChapter) {
        StringBuilder analysis = new StringBuilder();
        
        try {
            // 构建AI分析提示词
            String analysisPrompt = buildStatusAnalysisPrompt(protagonist, memoryBank, currentChapter);
            
            // 调用AI进行状态分析
            String aiAnalysis = novelCraftAIService.callAI("PROTAGONIST_ANALYST", analysisPrompt);
            
            analysis.append("### 🔍 当前状态分析\n");
            analysis.append(aiAnalysis).append("\n\n");
            
        } catch (Exception e) {
            logger.warn("生成动态状态分析失败: {}", e.getMessage());
        }
        
        return analysis.toString();
    }

    /**
     * 构建状态分析提示词
     */
    private String buildStatusAnalysisPrompt(Character protagonist, Map<String, Object> memoryBank, int currentChapter) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("🎭 你是【主角状态分析AI】，专门分析主角的当前状态和发展情况。\n\n");
        
        prompt.append("📊 **主角信息**\n");
        prompt.append("- 姓名: ").append(protagonist.getName()).append("\n");
        prompt.append("- 当前章节: 第").append(currentChapter).append("章\n");
        prompt.append("- 出场次数: ").append(protagonist.getAppearanceCount()).append("次\n\n");
        
        // 记忆库信息
        if (memoryBank != null && !memoryBank.isEmpty()) {
            prompt.append("📝 **故事背景**\n");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recentSummaries = (List<Map<String, Object>>) memoryBank.get("chapterSummaries");
            if (recentSummaries != null && !recentSummaries.isEmpty()) {
                prompt.append("- 最近情节: ");
                int summaryCount = Math.min(recentSummaries.size(), 3);
                for (int i = recentSummaries.size() - summaryCount; i < recentSummaries.size(); i++) {
                    Map<String, Object> summary = recentSummaries.get(i);
                    prompt.append("第").append(summary.get("chapterNumber")).append("章 ");
                }
                prompt.append("\n");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) memoryBank.get("plotThreads");
            if (plotThreads != null) {
                long activePlots = plotThreads.stream().filter(p -> "active".equals(p.get("status"))).count();
                prompt.append("- 活跃情节线: ").append(activePlots).append("条\n");
            }
        }
        
        prompt.append("\n🎯 **分析任务**\n");
        prompt.append("请基于以上信息，分析主角的当前状态：\n\n");
        prompt.append("1. **心理状态**: 主角当前的心理和情感状态如何？\n");
        prompt.append("2. **能力发展**: 主角的能力和技能有什么变化？\n");
        prompt.append("3. **关系变化**: 主角与其他角色的关系发生了什么变化？\n");
        prompt.append("4. **目标进展**: 主角朝着目标的进展如何？\n");
        prompt.append("5. **面临挑战**: 主角当前面临的主要挑战是什么？\n");
        prompt.append("6. **成长轨迹**: 主角的成长和变化轨迹分析\n\n");
        
        prompt.append("📝 请提供简洁而深入的分析，重点关注主角的当前状态和发展趋势。");
        
        return prompt.toString();
    }

    /**
     * 更新主角状态信息
     */
    public void updateProtagonistStatus(Long novelId, int chapterNumber, String statusUpdate) {
        try {
            Character protagonist = findProtagonist(novelId);
            if (protagonist == null) {
                logger.warn("未找到主角，无法更新状态");
                return;
            }
            
            // 更新出场信息
            protagonist.setLastAppearanceChapter(chapterNumber);
            if (protagonist.getFirstAppearanceChapter() == null) {
                protagonist.setFirstAppearanceChapter(chapterNumber);
            }
            
            Integer appearanceCount = protagonist.getAppearanceCount();
            protagonist.setAppearanceCount(appearanceCount == null ? 1 : appearanceCount + 1);
            
            // 保存更新
            characterRepository.updateById(protagonist);
            
            logger.info("✅ 更新主角{}状态信息，第{}章", protagonist.getName(), chapterNumber);
            
        } catch (Exception e) {
            logger.error("更新主角状态失败: {}", e.getMessage());
        }
    }

    /**
     * 检查主角状态是否有变化
     */
    public boolean hasStatusChanged(Long novelId, String lastKnownStatus) {
        try {
            String currentStatus = buildProtagonistStatus(novelId, new HashMap<>(), 1);
            return !currentStatus.equals(lastKnownStatus);
        } catch (Exception e) {
            logger.warn("检查主角状态变化失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成备选状态信息
     */
    private String generateFallbackStatus(int currentChapter) {
        return "👑 **主角详细现状** (第" + currentChapter + "章)\n\n" +
               "### 📋 基本信息\n" +
               "- **状态**: 待分析\n" +
               "- **发展阶段**: 故事进行中\n\n" +
               "### 🔍 当前状态分析\n" +
               "主角信息正在更新中，请稍后查看详细状态。\n";
    }
}