package com.novel.service;

import com.novel.domain.entity.Novel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 小说开篇专用AI服务
 * 专门处理小说开头的吸引力和代入感
 */
@Service
public class NovelOpeningAIService {

    private static final Logger logger = LoggerFactory.getLogger(NovelOpeningAIService.class);

    @Autowired
    private NovelCraftAIService novelCraftAIService;

    /**
     * 生成小说开篇专用提示词
     */
    public String buildOpeningPrompt(
            Novel novel, 
            Map<String, Object> chapterPlan, 
            Map<String, Object> memoryBank,
            String userAdjustment) {

        StringBuilder prompt = new StringBuilder();

        // 专业开篇写作身份
        prompt.append("📖 你是【小说开篇大师】，专精于创作吸引读者的小说开头。\n")
              .append("你的核心能力：让读者在前100字内就被深深吸引，产生强烈的阅读欲望。\n\n");

        // 开篇核心原则
        prompt.append("🎯 **开篇黄金法则**\n")
              .append("1. **瞬间抓取**：开头第一句话就要有冲击力\n")
              .append("2. **立即代入**：让读者瞬间融入主角视角\n")
              .append("3. **悬念埋设**：前三段必须有让读者好奇的元素\n")
              .append("4. **情感共鸣**：触发读者的情感反应\n")
              .append("5. **视觉感强**：描写要有画面感，让读者脑海中有清晰画面\n\n");

        // 小说基本信息
        prompt.append("📚 **创作信息**\n")
              .append("- 作品：《").append(novel.getTitle()).append("》\n")
              .append("- 类型：").append(novel.getGenre()).append("\n")
              .append("- 章节：").append(chapterPlan.get("title")).append("\n\n");

        // 开篇技巧指导
        prompt.append(getOpeningTechniques(novel.getGenre()));

        // 角色信息（如果有主角信息）
        if (memoryBank != null) {
            String characterInfo = buildProtagonistInfo(memoryBank);
            if (!characterInfo.isEmpty()) {
                prompt.append("👤 **主角信息**\n").append(characterInfo).append("\n\n");
            }
        }

        // 用户特殊要求
        if (userAdjustment != null && !userAdjustment.trim().isEmpty()) {
            prompt.append("✨ **特殊要求**\n").append(userAdjustment).append("\n\n");
        }

        // 开篇专业要求（基于指导意见全新设计）
        prompt.append("**【开篇黄金原则·神秘感建立】**\n")
              .append("• **不解释，只呈现**：让读者疑惑而不是明白\n")
              .append("• **感觉优先**：通过身体反应、环境变化暗示异常\n")
              .append("• **代价感**：任何变化都要有不适、痛苦或恐惧\n")
              .append("• **情感先行**：先让读者关心人物，再引入奇遇\n")
              .append("• **碎片信息**：通过梦境、闪回、直觉等碎片暴露信息\n\n")
              
              .append("**【绝对禁止·AI常见错误】**\n")
              .append("• 禁止直接命名：不要直接出现\"修仙\"\"系统\"\"真气\"等术语\n")
              .append("• 禁止智能客服：异物不能直接显示文字或说话\n")
              .append("• 禁止无代价获得：力量提升必须伴随痛苦或风险\n")
              .append("• 禁止反派脸谱：不能简单粗暴，要有复杂动机\n")
              .append("• 禁止突然觉醒：不能一碰到东西就主角光环\n\n")
              
              .append("**【开篇具体技巧】**\n")
              .append("• **第1段**：日常场景 + 主角内心渴望/困境\n")
              .append("• **第2段**：一个小异常（不解释其意义）\n")
              .append("• **第3段**：主角的身体/心理反应\n")
              .append("• **第4段**：环境或人物反应（暗示变化）\n")
              .append("• **结尾**：一个让读者想继续看的疑问\n\n")
              
              .append("**【文风要求】**\n")
              .append("• 用动词和具体细节，不用抽象形容词\n")
              .append("• 多用感官描写：触觉、味觉、嗅觉、体感\n")
              .append("• 对话要断断续续，符合真实说话习惯\n")
              .append("• 心理描写要细腻，但不能太理性化\n\n");

        prompt.append("🎯 **目标：让读者对主角产生共情，对异常产生好奇，产生3个以上疑问**");

        return prompt.toString();
    }

    /**
     * 根据类型获取开篇技巧
     */
    private String getOpeningTechniques(String genre) {
        StringBuilder techniques = new StringBuilder();
        techniques.append("🎨 **").append(genre).append("类开篇技巧**\n");

        switch (genre) {
            case "都市":
                techniques.append("- 从冲突现场开始：车祸、争吵、突发事件\n")
                         .append("- 展现主角的独特技能或身份反差\n")
                         .append("- 用现代生活的细节增强真实感\n")
                         .append("- 暗示主角隐藏的过去或秘密\n");
                break;
            case "玄幻":
                techniques.append("- 从普通生活开始：村庄、学院、家族日常\n")
                         .append("- 埋入异常细节：奇石、古物、异象征兆\n")
                         .append("- 避免直接说'修炼''灵根''成仙'等术语\n")
                         .append("- 通过环境和物件暗示超自然世界存在\n")
                         .append("- 重点刻画主角的平凡困境和内心渴望\n");
                break;
            case "科幻":
                techniques.append("- 从未来科技的震撼场景开始\n")
                         .append("- 展现科技对人性的冲击\n")
                         .append("- 用具体的科技细节增强说服力\n")
                         .append("- 暗示人类面临的重大挑战\n");
                break;
            case "悬疑":
                techniques.append("- 从神秘事件或死亡现场开始\n")
                         .append("- 埋下多个疑点和线索\n")
                         .append("- 营造紧张诡异的氛围\n")
                         .append("- 让读者产生强烈的求知欲\n");
                break;
            default:
                techniques.append("- 从冲突或转折点开始\n")
                         .append("- 立即展现主角的特色\n")
                         .append("- 营造类型特有的氛围\n")
                         .append("- 暗示故事的核心主题\n");
        }

        techniques.append("\n**开篇模式参考：**\n")
                  .append("• 冲突开场：直接从矛盾冲突开始\n")
                  .append("• 动作开场：从紧张刺激的动作场面开始\n")
                  .append("• 对话开场：从关键对话开始\n")
                  .append("• 悬念开场：从神秘事件开始\n")
                  .append("• 反差开场：展现出人意料的反差\n\n");

        return techniques.toString();
    }

    /**
     * 构建主角信息
     */
    private String buildProtagonistInfo(Map<String, Object> memoryBank) {
        StringBuilder info = new StringBuilder();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.getOrDefault("characters", new HashMap<>());
        
        // 寻找主角
        for (Map.Entry<String, Object> entry : characters.entrySet()) {
            String name = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> character = (Map<String, Object>) entry.getValue();
            
            String role = (String) character.get("role");
            if ("主角".equals(role) || "protagonist".equals(role)) {
                info.append("姓名：").append(name).append("\n");
                
                String personality = (String) character.get("personality");
                if (personality != null && !personality.trim().isEmpty()) {
                    info.append("性格：").append(personality).append("\n");
                }
                
                String background = (String) character.get("background");
                if (background != null && !background.trim().isEmpty()) {
                    info.append("背景：").append(background).append("\n");
                }
                
                String motivation = (String) character.get("motivation");
                if (motivation != null && !motivation.trim().isEmpty()) {
                    info.append("动机：").append(motivation).append("\n");
                }
                
                break; // 只处理第一个主角
            }
        }
        
        return info.toString();
    }

    /**
     * 执行开篇写作
     */
    public Map<String, Object> executeOpeningWriting(
            Novel novel,
            Map<String, Object> chapterPlan,
            Map<String, Object> memoryBank,
            String userAdjustment) {
        
        logger.info("🎬 执行开篇写作：{}", novel.getTitle());
        
        // 构建开篇专用提示词
        String openingPrompt = buildOpeningPrompt(novel, chapterPlan, memoryBank, userAdjustment);
        
        // 调用AI生成开篇内容
        String response = novelCraftAIService.callAI("OPENING_MASTER", openingPrompt);
        
        // 分析开篇质量
        Map<String, Object> qualityAnalysis = analyzeOpeningQuality(response);
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", response);
        result.put("wordCount", response.length());
        result.put("qualityAnalysis", qualityAnalysis);
        result.put("type", "opening");
        result.put("generatedAt", new Date());
        
        return result;
    }

    /**
     * 分析开篇质量
     */
    private Map<String, Object> analyzeOpeningQuality(String content) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            analysis.put("score", 0);
            analysis.put("issues", Arrays.asList("内容为空"));
            return analysis;
        }
        
        List<String> issues = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        int score = 70; // 基础分
        
        // 检查开头吸引力
        String firstSentence = getFirstSentence(content);
        if (firstSentence.length() > 50) {
            issues.add("首句过长，可能影响吸引力");
            score -= 10;
        } else if (firstSentence.length() < 10) {
            issues.add("首句过短，信息量不足");
            score -= 5;
        } else {
            strengths.add("首句长度适中");
            score += 5;
        }
        
        // 检查前100字内容
        String first100 = content.length() > 100 ? content.substring(0, 100) : content;
        if (first100.contains("很久很久以前") || first100.contains("从前") || first100.contains("在一个")) {
            issues.add("使用了老套的开头方式");
            score -= 15;
        }
        
        if (hasActionOrConflict(first100)) {
            strengths.add("开头有动作或冲突元素");
            score += 10;
        }
        
        if (hasDialogue(first100)) {
            strengths.add("开头包含对话，增强代入感");
            score += 5;
        }
        
        // 检查描述性内容比例
        if (isOverDescriptive(content)) {
            issues.add("描述性内容过多，可能影响节奏");
            score -= 10;
        }
        
        analysis.put("score", Math.max(Math.min(score, 100), 0));
        analysis.put("issues", issues);
        analysis.put("strengths", strengths);
        analysis.put("firstSentence", firstSentence);
        
        return analysis;
    }

    private String getFirstSentence(String content) {
        String[] sentences = content.split("[。！？]");
        return sentences.length > 0 ? sentences[0].trim() : "";
    }

    private boolean hasActionOrConflict(String text) {
        String[] actionWords = {"打", "撞", "跑", "冲", "摔", "抓", "推", "拉", "击", "战", "斗", "争", "抢"};
        for (String word : actionWords) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDialogue(String text) {
        return text.contains("\"") && text.contains("\"");
    }

    private boolean isOverDescriptive(String content) {
        String[] descriptiveWords = {"美丽", "漂亮", "壮观", "宏伟", "精致", "优雅", "绚烂", "璀璨"};
        int count = 0;
        for (String word : descriptiveWords) {
            count += (content.split(word, -1).length - 1);
        }
        return (double) count / content.length() * 100 > 2; // 超过2%认为过度描述
    }
} 