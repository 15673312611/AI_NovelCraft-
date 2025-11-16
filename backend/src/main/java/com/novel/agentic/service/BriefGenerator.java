package com.novel.agentic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.WritingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 章纲生成器
 * 
 * 基于剧情推理结果和精选上下文，生成简洁的写作指引（Brief）
 * 写作层只看这个Brief，不看原始大纲/蓝图/历史全文
 */
@Service
public class BriefGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(BriefGenerator.class);
    
    /**
     * 生成章纲
     * 
     * @param plotIntent 剧情推理结果
     * @param context 完整上下文（用于提取精选信息）
     * @param chapterNumber 章节号
     * @return 章纲（Brief）
     */
    public String generateBrief(Map<String, Object> plotIntent, WritingContext context, Integer chapterNumber) {
        logger.info("📋 开始生成章纲: 第{}章", chapterNumber);
        
        StringBuilder brief = new StringBuilder();
        
        brief.append("【第").append(chapterNumber).append("章写作指引】\n\n");
        
        // 1. 剧情方向
        brief.append("## 本章剧情方向\n");
        brief.append(plotIntent.getOrDefault("direction", "继续推进主线剧情")).append("\n\n");
        
        // 2. 关键剧情点
        @SuppressWarnings("unchecked")
        List<String> keyPoints = plotIntent.get("keyPlotPoints") instanceof List
            ? (List<String>) plotIntent.get("keyPlotPoints")
            : Collections.emptyList();
        
        if (!keyPoints.isEmpty()) {
            brief.append("## 关键剧情点（必须包含）\n");
            for (int i = 0; i < keyPoints.size(); i++) {
                brief.append((i + 1)).append(". ").append(keyPoints.get(i)).append("\n");
            }
            brief.append("\n");
        }
        
        // 3. 相关角色状态（从图谱提取）
        @SuppressWarnings("unchecked")
        List<String> relevantCharacters = plotIntent.get("relevantCharacters") instanceof List
            ? (List<String>) plotIntent.get("relevantCharacters")
            : Collections.emptyList();
        
        if (!relevantCharacters.isEmpty() && context.getCharacterProfiles() != null) {
            brief.append("## 相关角色当前状态\n");
            context.getCharacterProfiles().stream()
                .filter(profile -> {
                    String name = String.valueOf(profile.getOrDefault("name", profile.get("characterName")));
                    return relevantCharacters.contains(name);
                })
                .forEach(profile -> {
                    String name = String.valueOf(profile.getOrDefault("name", profile.get("characterName")));
                    brief.append("- ").append(name).append(": ");
                    
                    List<String> states = new ArrayList<>();
                    if (profile.get("location") != null) {
                        states.add("位置=" + profile.get("location"));
                    }
                    if (profile.get("realm") != null) {
                        states.add("境界=" + profile.get("realm"));
                    }
                    if (profile.get("status") != null) {
                        states.add("状态=" + profile.get("status"));
                    }
                    
                    brief.append(String.join("; ", states)).append("\n");
                });
            brief.append("\n");
        }
        
        // 4. 前因（需要注意的因果链）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> causalLinks = plotIntent.get("causalLinks") instanceof List
            ? (List<Map<String, Object>>) plotIntent.get("causalLinks")
            : Collections.emptyList();
        
        if (!causalLinks.isEmpty()) {
            brief.append("## 因果关系（需要注意）\n");
            causalLinks.forEach(link -> {
                brief.append("- 前因: ").append(link.get("cause"))
                      .append(" → 本章后果: ").append(link.get("effect"))
                      .append("\n");
            });
            brief.append("\n");
        }
        
        // 5. 需要推进的情节线
        @SuppressWarnings("unchecked")
        List<String> plotlines = plotIntent.get("plotlinesToAdvance") instanceof List
            ? (List<String>) plotIntent.get("plotlinesToAdvance")
            : Collections.emptyList();
        
        if (!plotlines.isEmpty()) {
            brief.append("## 需要推进的情节线\n");
            plotlines.forEach(line -> brief.append("- ").append(line).append("\n"));
            brief.append("\n");
        }
        
        // 6. 需要回收的伏笔
        @SuppressWarnings("unchecked")
        List<String> foreshadows = plotIntent.get("foreshadowsToResolve") instanceof List
            ? (List<String>) plotIntent.get("foreshadowsToResolve")
            : Collections.emptyList();
        
        if (!foreshadows.isEmpty()) {
            brief.append("## 需要回收的伏笔\n");
            foreshadows.forEach(f -> brief.append("- ").append(f).append("\n"));
            brief.append("\n");
        }
        
        // 7. 用户指令（如果有）
        if (context.getUserAdjustment() != null && !context.getUserAdjustment().isEmpty()) {
            brief.append("## 用户特殊要求\n");
            brief.append(context.getUserAdjustment()).append("\n\n");
        }
        
        // 8. 写作约束
        brief.append("## 写作约束\n");
        brief.append("- 开篇三段必须有冲突/行动/选择，不要铺垫环境\n");
        brief.append("- 角色只知道亲历和显性信息，不要凭空掌握宏大设定\n");
        brief.append("- 对话与动作交替，避免空洞说教\n");
        brief.append("- 章末留悬念或情绪钩子\n\n");
        
        brief.append("---\n");
        brief.append("以上是本章的写作指引，请据此自由发挥创作。\n");
        brief.append("不要复述指引内容，直接输出小说正文。\n");
        
        String result = brief.toString();
        logger.info("✅ 章纲生成完成: 共{}字", result.length());
        
        return result;
    }
}

