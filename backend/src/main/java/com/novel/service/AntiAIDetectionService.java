package com.novel.service;

import com.novel.domain.entity.Novel;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 反AI检测服务
 * 分析和优化AI生成的文本，使其更加自然和人性化
 */
@Service
public class AntiAIDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(AntiAIDetectionService.class);

    // 常见AI写作痕迹词汇库
    private static final Set<String> AI_TYPICAL_ADJECTIVES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "璀璨", "绚烂", "精致", "优雅", "神秘", "深邃", "温柔", "坚定", 
        "澎湃", "激昂", "沉稳", "淡然", "炽热", "冰冷", "梦幻", "唯美"
    )));
    
    private static final Set<String> AI_TYPICAL_PHRASES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "心中涌起", "眼中闪烁", "嘴角上扬", "深深地", "静静地", "缓缓地",
        "似乎", "仿佛", "宛如", "如同", "犹如", "恍若"
    )));

    private static final Set<String> FLOW_LIKE_PATTERNS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "然后", "接着", "随后", "于是", "接下来", "紧接着", "之后", "后来"
    )));

    /**
     * 分析文本的AI特征
     */
    public Map<String, Object> analyzeAIFeatures(String content) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            analysis.put("aiScore", 0.0);
            analysis.put("issues", Collections.emptyList());
            return analysis;
        }

        List<String> issues = new ArrayList<>();
        double aiScore = 0.0;

        // 1. 修饰词密度检查
        double adjectiveDensity = analyzeAdjectiveDensity(content, issues);
        aiScore += adjectiveDensity * 0.3;

        // 2. 流水账模式检查
        double flowScore = analyzeFlowPattern(content, issues);
        aiScore += flowScore * 0.25;

        // 3. 重复句式检查
        double repetitionScore = analyzeSentenceRepetition(content, issues);
        aiScore += repetitionScore * 0.2;

        // 4. 情感表达单一性检查
        double emotionScore = analyzeEmotionExpression(content, issues);
        aiScore += emotionScore * 0.15;

        // 5. 对话真实性检查
        double dialogueScore = analyzeDialogueNaturalness(content, issues);
        aiScore += dialogueScore * 0.1;

        analysis.put("aiScore", Math.min(aiScore, 1.0));
        analysis.put("issues", issues);
        analysis.put("adjectiveDensity", adjectiveDensity);
        analysis.put("flowScore", flowScore);
        analysis.put("repetitionScore", repetitionScore);
        analysis.put("emotionScore", emotionScore);
        analysis.put("dialogueScore", dialogueScore);

        return analysis;
    }

    /**
     * 修饰词密度分析
     */
    private double analyzeAdjectiveDensity(String content, List<String> issues) {
        int totalWords = content.length();
        int aiAdjectiveCount = 0;

        for (String adj : AI_TYPICAL_ADJECTIVES) {
            aiAdjectiveCount += countOccurrences(content, adj);
        }

        double density = (double) aiAdjectiveCount / totalWords * 1000; // 每千字修饰词数量
        
        if (density > 15) {
            issues.add("修饰词使用过度，每千字使用" + Math.round(density) + "个典型AI修饰词");
        }
        
        return Math.min(density / 20.0, 1.0); // 归一化到0-1
    }

    /**
     * 流水账模式分析
     */
    private double analyzeFlowPattern(String content, List<String> issues) {
        int flowWordCount = 0;
        List<String> sentences = Arrays.asList(content.split("[。！？]"));
        
        for (String phrase : FLOW_LIKE_PATTERNS) {
            flowWordCount += countOccurrences(content, phrase);
        }

        double flowRatio = (double) flowWordCount / sentences.size();
        
        if (flowRatio > 0.3) {
            issues.add("流水账倾向明显，时序连接词使用频率: " + Math.round(flowRatio * 100) + "%");
        }

        return Math.min(flowRatio / 0.4, 1.0);
    }

    /**
     * 句式重复分析
     */
    private double analyzeSentenceRepetition(String content, List<String> issues) {
        List<String> sentences = Arrays.asList(content.split("[。！？]"));
        Map<String, Integer> patterns = new HashMap<>();

        for (String sentence : sentences) {
            String pattern = extractSentencePattern(sentence.trim());
            if (pattern.length() > 3) {
                patterns.put(pattern, patterns.getOrDefault(pattern, 0) + 1);
            }
        }

        int repetitivePatterns = 0;
        for (int count : patterns.values()) {
            if (count > 2) {
                repetitivePatterns++;
            }
        }

        double repetitionRatio = (double) repetitivePatterns / patterns.size();
        
        if (repetitionRatio > 0.2) {
            issues.add("句式重复严重，重复模式占比: " + Math.round(repetitionRatio * 100) + "%");
        }

        return Math.min(repetitionRatio / 0.3, 1.0);
    }

    /**
     * 情感表达分析
     */
    private double analyzeEmotionExpression(String content, List<String> issues) {
        // 简化的情感词检测
        Set<String> basicEmotions = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("高兴", "愤怒", "悲伤", "恐惧", "惊讶")));
        Set<String> foundEmotions = new HashSet<>();

        for (String emotion : basicEmotions) {
            if (content.contains(emotion)) {
                foundEmotions.add(emotion);
            }
        }

        double emotionVariety = (double) foundEmotions.size() / basicEmotions.size();
        
        if (emotionVariety < 0.3) {
            issues.add("情感表达单一，缺乏情感层次性和复杂度");
        }

        return 1.0 - emotionVariety; // 情感多样性低，AI特征高
    }

    /**
     * 对话真实性分析
     */
    private double analyzeDialogueNaturalness(String content, List<String> issues) {
        Pattern dialoguePattern = Pattern.compile("\\u201C([^\\u201C\\u201D]*)\\u201D");
        Matcher matcher = dialoguePattern.matcher(content);
        
        List<String> dialogues = new ArrayList<>();
        while (matcher.find()) {
            dialogues.add(matcher.group(1));
        }

        if (dialogues.isEmpty()) {
            return 0.0; // 没有对话，不计入AI评分
        }

        int unnaturalCount = 0;
        for (String dialogue : dialogues) {
            if (isDialogueUnnatural(dialogue)) {
                unnaturalCount++;
            }
        }

        double unnaturalRatio = (double) unnaturalCount / dialogues.size();
        
        if (unnaturalRatio > 0.5) {
            issues.add("对话表达过于正式或模式化，缺乏真实感");
        }

        return unnaturalRatio;
    }

    /**
     * 生成优化建议
     */
    public List<String> generateOptimizationSuggestions(Map<String, Object> analysis) {
        List<String> suggestions = new ArrayList<>();
        double aiScore = (Double) analysis.get("aiScore");

        if (aiScore > 0.7) {
            suggestions.add("整体AI痕迹较明显，需要大幅优化文本自然度");
        } else if (aiScore > 0.5) {
            suggestions.add("存在一定AI痕迹，建议适度调整表达方式");
        }

        Double adjectiveDensity = (Double) analysis.get("adjectiveDensity");
        if (adjectiveDensity > 0.6) {
            suggestions.add("减少修饰词使用，通过动作和对话表现情感");
            suggestions.add("用具体的行为细节替代形容词描述");
        }

        Double flowScore = (Double) analysis.get("flowScore");
        if (flowScore > 0.6) {
            suggestions.add("避免过多时序连接词，增加句式变化");
            suggestions.add("通过角色内心活动推进情节，减少机械式叙述");
        }

        Double repetitionScore = (Double) analysis.get("repetitionScore");
        if (repetitionScore > 0.5) {
            suggestions.add("增加句式多样性，避免重复的表达模式");
        }

        Double emotionScore = (Double) analysis.get("emotionScore");
        if (emotionScore > 0.6) {
            suggestions.add("丰富情感表达层次，展现复杂的人物内心状态");
        }

        Double dialogueScore = (Double) analysis.get("dialogueScore");
        if (dialogueScore > 0.5) {
            suggestions.add("让对话更加口语化，增加角色个性特征");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("文本自然度良好，继续保持这种风格");
        }

        return suggestions;
    }

    // 辅助方法
    private int countOccurrences(String text, String word) {
        return text.split(word, -1).length - 1;
    }

    private String extractSentencePattern(String sentence) {
        // 简化的句式模式提取：保留结构词，替换具体词汇
        return sentence.replaceAll("[\\u4e00-\\u9fa5]{2,}", "X")
                      .replaceAll("\\d+", "N")
                      .replaceAll("\\s+", "");
    }

    private boolean isDialogueUnnatural(String dialogue) {
        // 简单的对话自然度检测
        if (dialogue.length() > 50 && !dialogue.contains("...") && !dialogue.contains("呃") && !dialogue.contains("嗯")) {
            return true; // 过长且没有自然停顿
        }
        
        // 检查是否过于正式
        return dialogue.contains("非常") && dialogue.contains("十分") && dialogue.contains("极其");
    }

    /**
     * 对AI痕迹过重的内容进行二次优化 - 完整版
     * 使用人性化提示词让AI重写
     */
    public String optimizeAIContent(String content, Map<String, Object> aiAnalysis, Novel novel) {
        @SuppressWarnings("unchecked")
        List<String> aiFeatures = (List<String>) aiAnalysis.get("aiFeatures");
        double aiScore = (Double) aiAnalysis.get("aiScore");
        
        StringBuilder optimizePrompt = new StringBuilder();
        
        // 动态优化者身份 - 让AI成为专业编辑
        optimizePrompt.append("你是一位资深的网文编辑，擅长将AI生成的文字改写得更加人性化。\n\n");
        
        // 具体问题诊断
        optimizePrompt.append("【检测到的AI痕迹】\n");
        aiFeatures.forEach(feature -> {
            optimizePrompt.append("- ").append(feature).append("\n");
        });
        optimizePrompt.append(String.format("当前AI痕迹评分: %.2f/1.0\n\n", aiScore));
        
        // 人性化改写指导
        optimizePrompt.append("【人性化改写要求】\n");
        optimizePrompt.append("• 让文字有温度和情感，像真人写的\n");
        optimizePrompt.append("• 将程式化表达改为自然表达\n");
        optimizePrompt.append("• 添加真实的细节和不完美的地方\n");
        optimizePrompt.append("• 让对话更加口语化和个性化\n");
        optimizePrompt.append("• 减少过度修饰，增加生活化描写\n\n");
        
        // 根据小说类型的特定指导
        optimizePrompt.append("【").append(novel.getGenre()).append("类小说特殊要求】\n");
        if (novel.getGenre().equals("都市异能")) {
            optimizePrompt.append("• 超能力要融入日常生活，不脱离现实\n");
            optimizePrompt.append("• 人物反应要符合现代人思维\n");
        } else if (novel.getGenre().equals("玄幻")) {
            optimizePrompt.append("• 修炼升级要有过程感，不能太突兀\n");
            optimizePrompt.append("• 世界观设定要自然融入剧情\n");
        }
        optimizePrompt.append("\n");
        
        // 原始内容
        optimizePrompt.append("【待优化内容】\n");
        optimizePrompt.append(content).append("\n\n");
        
        // 输出要求
        optimizePrompt.append("请对上述内容进行人性化改写，让它看起来像真正的人类作者写的。直接输出改写后的正文，不要任何解释或标记。");
        
        // 这里应该调用AI接口进行优化，现在返回提示词供外部调用
        return optimizePrompt.toString();
    }

    /**
     * 检查优化后的内容是否改善
     */
    public boolean isOptimizationImproved(String originalContent, String optimizedContent) {
        Map<String, Object> originalAnalysis = analyzeAIFeatures(originalContent);
        Map<String, Object> optimizedAnalysis = analyzeAIFeatures(optimizedContent);
        
        double originalScore = (Double) originalAnalysis.get("aiScore");
        double optimizedScore = (Double) optimizedAnalysis.get("aiScore");
        
        // 如果AI评分降低了0.1以上，认为优化有效
        return (originalScore - optimizedScore) > 0.1;
    }
}