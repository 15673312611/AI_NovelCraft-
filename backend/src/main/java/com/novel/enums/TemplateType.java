package com.novel.enums;

/**
 * 模板类型枚举
 * 根据小说题材选择不同的循环模板
 */
public enum TemplateType {
    
    /**
     * 玄幻修仙模板
     * 适用于：玄幻、修仙、武侠类小说
     */
    XUANHUAN("玄幻修仙", "发现秘境→奇遇→战斗炼药→震惊认可→境界突破"),
    
    /**
     * 都市装逼模板
     * 适用于：都市、现代、商战类小说
     */
    URBAN("都市装逼", "被看不起→隐藏身份→身份曝光→打脸震惊→扩展势力"),
    
    /**
     * 系统流模板
     * 适用于：系统流、游戏类小说
     */
    SYSTEM("系统流", "系统任务→系统技能→完成任务→全服播报→系统奖励"),
    
    /**
     * 重生流模板
     * 适用于：重生、穿越类小说
     */
    REBIRTH("重生流", "遇到熟悉事件→前世知识→提前布局→众人震惊→改变未来"),
    
    /**
     * 通用模板
     * 适用于：其他类型或混合类型
     */
    GENERAL("通用", "冲突引发→优势展示→解决冲突→获得反馈→成长铺垫");
    
    private final String displayName;
    private final String pattern;
    
    TemplateType(String displayName, String pattern) {
        this.displayName = displayName;
        this.pattern = pattern;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    /**
     * 根据小说类型推断模板类型
     */
    public static TemplateType inferFromGenre(String genre) {
        if (genre == null) {
            return GENERAL;
        }
        
        String lowerGenre = genre.toLowerCase();
        
        if (lowerGenre.contains("玄幻") || lowerGenre.contains("修仙") || 
            lowerGenre.contains("武侠") || lowerGenre.contains("仙侠")) {
            return XUANHUAN;
        }
        
        if (lowerGenre.contains("都市") || lowerGenre.contains("现代") || 
            lowerGenre.contains("商战")) {
            return URBAN;
        }
        
        if (lowerGenre.contains("系统") || lowerGenre.contains("游戏")) {
            return SYSTEM;
        }
        
        if (lowerGenre.contains("重生") || lowerGenre.contains("穿越")) {
            return REBIRTH;
        }
        
        return GENERAL;
    }
}

