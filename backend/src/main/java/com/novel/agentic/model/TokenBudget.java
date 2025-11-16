package com.novel.agentic.model;

import lombok.Data;
import lombok.Builder;

/**
 * Token预算控制
 * 
 * 解决问题：长篇小说生成Token成本过高
 */
@Data
@Builder
public class TokenBudget {
    
    /**
     * 系统提示词最大token (网文规则)
     */
    @Builder.Default
    private Integer maxSystemPrompt = 10000;
    
    /**
     * 大纲最大token
     */
    @Builder.Default
    private Integer maxOutline = 8000;
    
    /**
     * 卷蓝图最大token
     */
    @Builder.Default
    private Integer maxVolumeBlueprint = 5000;
    
    /**
     * 单个历史事件最大token
     */
    @Builder.Default
    private Integer maxEventDescription = 400;
    
    /**
     * 历史事件总数限制
     */
    @Builder.Default
    private Integer maxEvents = 20;
    
    /**
     * 伏笔总数限制
     */
    @Builder.Default
    private Integer maxForeshadows = 12;
    
    /**
     * 最近完整章节数量（每章独立message，不截断）
     */
    @Builder.Default
    private Integer maxFullChapters = 3;
    
    /**
     * 单章内容最大token（仅用于其他内容截断，历史章节不再截断）
     */
    @Builder.Default
    private Integer maxChapterContent = 8000;
    
    /**
     * 总输入token预算
     */
    @Builder.Default
    private Integer totalInputBudget = 100000;
    
    /**
     * 是否启用智能裁剪
     */
    @Builder.Default
    private Boolean enableSmartTruncation = true;
    
    /**
     * 估算文本token数（粗略估计：中文1字≈1.5token）
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 中文字符
        int chineseChars = text.replaceAll("[^\u4e00-\u9fa5]", "").length();
        // 英文单词（粗略）
        int englishWords = text.split("\\s+").length - chineseChars;
        
        return (int) (chineseChars * 1.5 + englishWords * 1.3);
    }
    
    /**
     * 智能截断文本以符合预算
     */
    public String truncate(String text, int maxTokens) {
        if (!enableSmartTruncation || text == null) {
            return text;
        }
        
        int estimatedTokens = estimateTokens(text);
        
        if (estimatedTokens <= maxTokens) {
            return text;
        }
        
        // 按比例截断，保留重要开头
        double ratio = (double) maxTokens / estimatedTokens;
        int targetLength = (int) (text.length() * ratio * 0.9); // 留10%余量
        
        if (targetLength < text.length()) {
            return text.substring(0, targetLength) + "\n...(内容过长已截断)";
        }
        
        return text;
    }
}


