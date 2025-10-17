package com.novel.service;

import com.novel.domain.entity.Character;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 * 专门处理长篇小说中的角色动态管理
 */
@Service
public class CharacterManagementService {

    private static final Logger logger = LoggerFactory.getLogger(CharacterManagementService.class);

    // 删除未使用的事件服务，引入会导致不必要的耦合

    /**
     * 构建角色信息摘要，用于写作提示
     */
    public String buildCharacterSummaryForWriting(Long novelId, Map<String, Object> memoryBank, int currentChapter) {
        StringBuilder summary = new StringBuilder();
        
        // 从记忆库获取角色信息
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.getOrDefault("characters", new HashMap<>());
        
        if (characters.isEmpty()) {
            return "暂无角色信息，请在创作过程中逐步建立角色档案。";
        }

        // 按重要性和活跃度排序角色
        List<Map.Entry<String, Object>> sortedCharacters = characters.entrySet().stream()
            .sorted((a, b) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> charA = (Map<String, Object>) a.getValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> charB = (Map<String, Object>) b.getValue();
                
                int scoreA = getCharacterRelevanceScore(charA, currentChapter);
                int scoreB = getCharacterRelevanceScore(charB, currentChapter);
                
                return Integer.compare(scoreB, scoreA); // 降序
            })
            .collect(Collectors.toList());

        summary.append("【角色状态】（按重要性排序）\n");
        
        int count = 0;
        for (Map.Entry<String, Object> entry : sortedCharacters) {
            if (count >= 8) break; // 只显示最重要的8个角色
            
            String name = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> character = (Map<String, Object>) entry.getValue();
            
            summary.append("• ").append(name);
            
            // 添加角色类型标记
            String role = (String) character.get("role");
            if (role != null) {
                summary.append("（").append(role).append("）");
            }
            
            // 添加当前状态
            String currentState = (String) character.get("currentState");
            if (currentState != null && !currentState.trim().isEmpty()) {
                summary.append(" - ").append(currentState);
            }
            
            // 添加关键性格特点
            String personality = (String) character.get("personality");
            if (personality != null && personality.length() > 10) {
                String shortPersonality = personality.length() > 30 ? 
                    personality.substring(0, 30) + "..." : personality;
                summary.append(" [").append(shortPersonality).append("]");
            }
            
            summary.append("\n");
            count++;
        }

        // 添加角色关系提醒
        summary.append("\n【重要关系】\n");
        addCharacterRelationships(summary, characters, 3);

        return summary.toString();
    }

    /**
     * 计算角色相关性评分
     */
    private int getCharacterRelevanceScore(Map<String, Object> character, int currentChapter) {
        int score = 0;
        
        // 基础重要性
        String role = (String) character.get("role");
        if ("主角".equals(role) || "protagonist".equals(role)) {
            score += 100;
        } else if ("重要角色".equals(role) || "major".equals(role)) {
            score += 60;
        } else if ("配角".equals(role) || "minor".equals(role)) {
            score += 30;
        }

        // 最近活跃度
        Object lastAppearance = character.get("lastAppearance");
        if (lastAppearance instanceof Integer) {
            int gap = currentChapter - (Integer) lastAppearance;
            if (gap <= 5) {
                score += 30; // 最近出现过
            } else if (gap <= 20) {
                score += 10; // 较近出现
            } else if (gap > 50) {
                score -= 20; // 长期未出现
            }
        }

        // 状态活跃度
        String status = (String) character.get("status");
        if ("active".equals(status)) {
            score += 20;
        } else if ("inactive".equals(status)) {
            score -= 10;
        }

        return Math.max(score, 0);
    }

    /**
     * 添加角色关系信息
     */
    private void addCharacterRelationships(StringBuilder summary, Map<String, Object> characters, int maxRelations) {
        int count = 0;
        
        for (Map.Entry<String, Object> entry : characters.entrySet()) {
            if (count >= maxRelations) break;
            
            String name = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> character = (Map<String, Object>) entry.getValue();
            
            Object relationshipsObj = character.get("relationships");
            if (relationshipsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> relationships = (Map<String, Object>) relationshipsObj;
                
                if (!relationships.isEmpty()) {
                    summary.append("• ").append(name).append("：");
                    int relCount = 0;
                    for (Map.Entry<String, Object> rel : relationships.entrySet()) {
                        if (relCount > 0) summary.append("，");
                        summary.append(rel.getKey()).append("(").append(rel.getValue()).append(")");
                        relCount++;
                        if (relCount >= 2) break; // 每个角色最多显示2个关系
                    }
                    summary.append("\n");
                    count++;
                }
            }
        }
    }

    /**
     * 更新角色出场信息
     */
    public void updateCharacterAppearance(Map<String, Object> memoryBank, String characterName, int chapterNumber) {
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.getOrDefault("characters", new HashMap<>());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> character = (Map<String, Object>) characters.get(characterName);
        
        if (character != null) {
            character.put("lastAppearance", chapterNumber);
            
            // 更新出场次数
            Integer appearanceCount = (Integer) character.getOrDefault("appearanceCount", 0);
            character.put("appearanceCount", appearanceCount + 1);
            
            // 如果是首次出现，记录首次出场章节
            if (!character.containsKey("firstAppearance")) {
                character.put("firstAppearance", chapterNumber);
            }
            
            // 更新状态为活跃
            character.put("status", "active");
            
            logger.info("更新角色 {} 出场信息：第 {} 章", characterName, chapterNumber);
        }
    }

    /**
     * 分析长期不活跃角色
     */
    public List<String> findInactiveCharacters(Map<String, Object> memoryBank, int currentChapter, int inactiveThreshold) {
        List<String> inactiveCharacters = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.getOrDefault("characters", new HashMap<>());
        
        for (Map.Entry<String, Object> entry : characters.entrySet()) {
            String name = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> character = (Map<String, Object>) entry.getValue();
            
            Object lastAppearanceObj = character.get("lastAppearance");
            if (lastAppearanceObj instanceof Integer) {
                int lastAppearance = (Integer) lastAppearanceObj;
                if (currentChapter - lastAppearance > inactiveThreshold) {
                    // 排除已死亡或消失的角色
                    String status = (String) character.get("status");
                    if (!"deceased".equals(status) && !"missing".equals(status)) {
                        inactiveCharacters.add(name);
                    }
                }
            }
        }
        
        return inactiveCharacters;
    }

    /**
     * 生成角色重新激活建议
     */
    public List<String> generateCharacterReactivationSuggestions(Map<String, Object> memoryBank, int currentChapter) {
        List<String> suggestions = new ArrayList<>();
        
        // 找到长期不活跃的重要角色
        List<String> inactiveCharacters = findInactiveCharacters(memoryBank, currentChapter, 30);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.getOrDefault("characters", new HashMap<>());
        
        for (String characterName : inactiveCharacters) {
            @SuppressWarnings("unchecked")
            Map<String, Object> character = (Map<String, Object>) characters.get(characterName);
            
            String role = (String) character.get("role");
            if ("重要角色".equals(role) || "major".equals(role)) {
                Object lastAppearanceObj = character.get("lastAppearance");
                if (lastAppearanceObj instanceof Integer) {
                    int gapChapters = currentChapter - (Integer) lastAppearanceObj;
                    suggestions.add(String.format("角色 %s 已 %d 章未出现，建议安排重新登场", characterName, gapChapters));
                }
            }
        }
        
        return suggestions;
    }
}