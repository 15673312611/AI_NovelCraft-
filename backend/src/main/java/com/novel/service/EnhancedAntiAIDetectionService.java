package com.novel.service;

import com.novel.config.AIClientConfig;
import com.novel.domain.entity.Novel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 反AI检测增强服务
 * 专门用于检测和优化AI生成内容，让其更接近人类写作风格
 * 
 * 核心策略：
 * 1. 检测AI痕迹特征
 * 2. 多轮迭代优化
 * 3. 人性化改写
 * 4. 质量评估验证
 */
@Service
public class EnhancedAntiAIDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedAntiAIDetectionService.class);

    @Autowired
    private AIClientConfig aiConfig;

    // AI痕迹关键词模式
    private static final List<Pattern> AI_PATTERNS = Arrays.asList(
        Pattern.compile("需要注意的是|值得一提的是|不可否认的是", Pattern.CASE_INSENSITIVE),
        Pattern.compile("总的来说|总而言之|综上所述", Pattern.CASE_INSENSITIVE),
        Pattern.compile("首先.*其次.*最后|第一.*第二.*第三", Pattern.CASE_INSENSITIVE),
        Pattern.compile("毫无疑问|显而易见|不难理解", Pattern.CASE_INSENSITIVE),
        Pattern.compile("让我们来|现在让我|接下来", Pattern.CASE_INSENSITIVE)
    );

    // AI风格短语
    private static final Set<String> AI_PHRASES = new HashSet<>(Arrays.asList(
        "深度思考", "全方位", "多维度", "系统性", "综合性",
        "专业性", "创新性", "前瞻性", "战略性", "颠覆性",
        "赋能", "引领", "驱动", "助力", "促进",
        "优质", "卓越", "极致", "完美", "理想"
    ));

    /**
     * 检测AI特征并进行多轮优化
     */
    public Map<String, Object> analyzeAndOptimize(String content, String genre, int chapterNumber) {
        logger.info("🔍 开始AI特征检测和优化 - 章节: {}, 类型: {}", chapterNumber, genre);
        
        // 第一轮：基础特征检测
        Map<String, Object> analysis = analyzeAIFeatures(content);
        double initialScore = (Double) analysis.get("aiScore");
        
        String optimizedContent = content;
        int round = 0;
        int maxRounds = 3;
        
        // 多轮优化直到AI痕迹降到可接受水平
        while (initialScore > 0.3 && round < maxRounds) {
            round++;
            logger.info("🔄 第{}轮优化，当前AI评分: {:.2f}", round, initialScore);
            
            optimizedContent = performOptimization(optimizedContent, analysis, genre, round);
            analysis = analyzeAIFeatures(optimizedContent);
            initialScore = (Double) analysis.get("aiScore");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("originalContent", content);
        result.put("optimizedContent", optimizedContent);
        result.put("initialAIScore", analysis.get("aiScore"));
        result.put("finalAIScore", initialScore);
        result.put("optimizationRounds", round);
        result.put("improvement", calculateImprovement(content, optimizedContent));
        result.put("qualityMetrics", calculateQualityMetrics(optimizedContent, genre));
        
        logger.info("✅ 优化完成 - AI评分从 {:.2f} 降至 {:.2f}，共{}轮优化", 
                   (Double)result.get("initialAIScore"), initialScore, round);
        
        return result;
    }

    /**
     * 检测AI特征
     */
    public Map<String, Object> analyzeAIFeatures(String content) {
        Map<String, Object> analysis = new HashMap<>();
        
        // 1. 结构化特征检测
        double structureScore = detectStructuralFeatures(content);
        
        // 2. 词汇特征检测  
        double vocabularyScore = detectVocabularyFeatures(content);
        
        // 3. 语法特征检测
        double grammarScore = detectGrammarFeatures(content);
        
        // 4. 风格特征检测
        double styleScore = detectStyleFeatures(content);
        
        // 综合评分
        double aiScore = (structureScore + vocabularyScore + grammarScore + styleScore) / 4.0;
        
        analysis.put("aiScore", aiScore);
        analysis.put("structureScore", structureScore);
        analysis.put("vocabularyScore", vocabularyScore);
        analysis.put("grammarScore", grammarScore);
        analysis.put("styleScore", styleScore);
        analysis.put("detectedPatterns", findAIPatterns(content));
        analysis.put("suggestions", generateOptimizationSuggestions(content, aiScore));
        
        return analysis;
    }

    /**
     * 执行具体优化
     */
    private String performOptimization(String content, Map<String, Object> analysis, String genre, int round) {
        List<String> issues = (List<String>) analysis.get("detectedPatterns");
        double aiScore = (Double) analysis.get("aiScore");
        
        String optimizationPrompt = buildOptimizationPrompt(content, issues, genre, round, aiScore);
        
        return callAI("CONTENT_OPTIMIZER", optimizationPrompt);
    }

    /**
     * 构建优化提示词
     */
    private String buildOptimizationPrompt(String content, List<String> issues, String genre, int round, double aiScore) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一位资深的人工编辑，专门负责让AI生成的内容更像人类作者写的。\n\n");
        
        prompt.append("【当前任务】\n");
        prompt.append("- 类型: ").append(genre).append("网文\n");
        prompt.append("- 优化轮次: 第").append(round).append("轮\n");
        prompt.append("- AI痕迹评分: ").append(String.format("%.2f", aiScore)).append("/1.0\n\n");
        
        prompt.append("【检测到的问题】\n");
        if (issues != null && !issues.isEmpty()) {
            for (String issue : issues) {
                prompt.append("• ").append(issue).append("\n");
            }
        }
        prompt.append("\n");
        
        prompt.append("【优化策略】\n");
        switch (round) {
            case 1:
                prompt.append("• 去除明显的AI标识词汇和句式\n");
                prompt.append("• 让语言更加自然流畅\n");
                prompt.append("• 避免过于规整的结构\n");
                break;
            case 2:
                prompt.append("• 增加更多人性化的细节描写\n");
                prompt.append("• 让对话更贴近真实对话\n");
                prompt.append("• 调整语言节奏，避免机械感\n");
                break;
            case 3:
                prompt.append("• 深度润色，增强情感表达\n");
                prompt.append("• 优化词汇选择，更贴近该类型风格\n");
                prompt.append("• 最终检查，确保自然度\n");
                break;
        }
        prompt.append("\n");
        
        prompt.append("【原始内容】\n");
        prompt.append(content);
        prompt.append("\n\n");
        
        prompt.append("【要求】\n");
        prompt.append("1. 保持原文的核心情节和人物不变\n");
        prompt.append("2. 让文字读起来更像人类作者写的\n");
        prompt.append("3. 保持").append(genre).append("类型的特色\n");
        prompt.append("4. 直接输出优化后的内容，不要任何说明\n");
        
        return prompt.toString();
    }

    /**
     * 检测结构化特征
     */
    private double detectStructuralFeatures(String content) {
        double score = 0.0;
        
        // 检测过度结构化的段落
        String[] paragraphs = content.split("\n\n");
        if (paragraphs.length > 3) {
            int similarLengthParagraphs = 0;
            for (int i = 1; i < paragraphs.length; i++) {
                if (Math.abs(paragraphs[i].length() - paragraphs[i-1].length()) < 50) {
                    similarLengthParagraphs++;
                }
            }
            if (similarLengthParagraphs > paragraphs.length * 0.6) {
                score += 0.3;
            }
        }
        
        // 检测列表式结构
        if (content.matches(".*第[一二三四五六七八九十].*第[一二三四五六七八九十].*")) {
            score += 0.4;
        }
        
        return Math.min(score, 1.0);
    }

    /**
     * 检测词汇特征
     */
    private double detectVocabularyFeatures(String content) {
        double score = 0.0;
        
        // 检测AI风格词汇
        for (String phrase : AI_PHRASES) {
            if (content.contains(phrase)) {
                score += 0.1;
            }
        }
        
        return Math.min(score, 1.0);
    }

    /**
     * 检测语法特征
     */
    private double detectGrammarFeatures(String content) {
        double score = 0.0;
        
        // 检测AI模式
        for (Pattern pattern : AI_PATTERNS) {
            if (pattern.matcher(content).find()) {
                score += 0.2;
            }
        }
        
        return Math.min(score, 1.0);
    }

    /**
     * 检测风格特征
     */
    private double detectStyleFeatures(String content) {
        double score = 0.0;
        
        // 检测过于正式的语调
        String[] formalPhrases = {"因此", "然而", "此外", "与此同时", "由此可见"};
        for (String phrase : formalPhrases) {
            if (content.contains(phrase)) {
                score += 0.15;
            }
        }
        
        return Math.min(score, 1.0);
    }

    /**
     * 找出AI模式
     */
    private List<String> findAIPatterns(String content) {
        List<String> patterns = new ArrayList<>();
        
        for (Pattern pattern : AI_PATTERNS) {
            if (pattern.matcher(content).find()) {
                patterns.add("检测到AI句式: " + pattern.pattern());
            }
        }
        
        for (String phrase : AI_PHRASES) {
            if (content.contains(phrase)) {
                patterns.add("检测到AI词汇: " + phrase);
            }
        }
        
        return patterns;
    }

    /**
     * 生成优化建议
     */
    private List<String> generateOptimizationSuggestions(String content, double aiScore) {
        List<String> suggestions = new ArrayList<>();
        
        if (aiScore > 0.7) {
            suggestions.add("内容AI痕迹较重，需要大幅度人性化改写");
        } else if (aiScore > 0.4) {
            suggestions.add("存在明显AI特征，需要适度优化");
        } else {
            suggestions.add("AI痕迹轻微，稍作润色即可");
        }
        
        return suggestions;
    }

    /**
     * 计算改进程度
     */
    private Map<String, Object> calculateImprovement(String original, String optimized) {
        Map<String, Object> improvement = new HashMap<>();
        
        double originalAI = analyzeAIFeatures(original).get("aiScore") != null ? 
                          (Double) analyzeAIFeatures(original).get("aiScore") : 0.0;
        double optimizedAI = analyzeAIFeatures(optimized).get("aiScore") != null ? 
                           (Double) analyzeAIFeatures(optimized).get("aiScore") : 0.0;
        
        improvement.put("aiScoreReduction", originalAI - optimizedAI);
        improvement.put("improvementPercentage", ((originalAI - optimizedAI) / originalAI) * 100);
        
        return improvement;
    }

    /**
     * 计算质量指标
     */
    private Map<String, Object> calculateQualityMetrics(String content, String genre) {
        Map<String, Object> metrics = new HashMap<>();
        
        // 基础指标
        metrics.put("wordCount", content.length());
        metrics.put("readability", calculateReadability(content));
        metrics.put("emotionalTone", detectEmotionalTone(content));
        metrics.put("genreCompatibility", calculateGenreCompatibility(content, genre));
        
        return metrics;
    }

    private double calculateReadability(String content) {
        // 简化的可读性评估
        int sentences = content.split("[。！？]").length;
        int words = content.length();
        return sentences > 0 ? Math.min(words / (double) sentences / 20.0, 1.0) : 0.5;
    }

    private String detectEmotionalTone(String content) {
        // 简化的情感检测
        if (content.matches(".*[激动|兴奋|愤怒].*")) return "激烈";
        if (content.matches(".*[温暖|温柔|平静].*")) return "温和";
        if (content.matches(".*[紧张|危险|恐怖].*")) return "紧张";
        return "中性";
    }

    private double calculateGenreCompatibility(String content, String genre) {
        // 简化的类型匹配度评估
        Map<String, String[]> genreKeywords = new HashMap<>();
        genreKeywords.put("都市异能", new String[]{"都市", "异能", "现代", "城市"});
        genreKeywords.put("玄幻", new String[]{"修炼", "灵气", "境界", "法宝"});
        genreKeywords.put("现代言情", new String[]{"爱情", "感情", "心动", "浪漫"});
        
        String[] keywords = genreKeywords.getOrDefault(genre, new String[0]);
        int matches = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword)) matches++;
        }
        
        return keywords.length > 0 ? matches / (double) keywords.length : 0.5;
    }

    /**
     * 调用AI进行优化
     */
    private String callAI(String type, String prompt) {
        // 这里应该调用实际的AI服务
        // 临时返回原文，实际使用时需要实现AI调用逻辑
        return prompt; // 临时实现
    }

    /**
     * 优化AI内容（供外部调用）
     */
    public String optimizeAIContent(String content, Map<String, Object> aiAnalysis, Novel novel) {
        List<String> suggestions = generateOptimizationSuggestions(content, (Double) aiAnalysis.get("aiScore"));
        String optimizationPrompt = buildOptimizationPrompt(content, suggestions, novel.getGenre(), 1, (Double) aiAnalysis.get("aiScore"));
        return callAI("CONTENT_OPTIMIZER", optimizationPrompt);
    }

    /**
     * 检查优化是否有效
     */
    public boolean isOptimizationImproved(String originalContent, String optimizedContent) {
        Map<String, Object> originalAnalysis = analyzeAIFeatures(originalContent);
        Map<String, Object> optimizedAnalysis = analyzeAIFeatures(optimizedContent);
        
        double originalScore = (Double) originalAnalysis.get("aiScore");
        double optimizedScore = (Double) optimizedAnalysis.get("aiScore");
        
        return optimizedScore < originalScore;
    }

    /**
     * 生成优化建议（供外部调用）
     */
    public List<String> generateOptimizationSuggestions(Map<String, Object> aiAnalysis) {
        double aiScore = (Double) aiAnalysis.get("aiScore");
        List<String> suggestions = new ArrayList<>();
        
        if (aiScore > 0.7) {
            suggestions.add("内容AI痕迹较重，需要大幅度人性化改写");
        } else if (aiScore > 0.4) {
            suggestions.add("存在明显AI特征，需要适度优化");
        } else {
            suggestions.add("AI痕迹轻微，稍作润色即可");
        }
        
        return suggestions;
    }
}