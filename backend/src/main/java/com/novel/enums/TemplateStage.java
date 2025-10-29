package com.novel.enums;

/**
 * 模板循环五步阶段枚举
 * 定义每个小说生成过程中应遵循的节奏阶段
 */
public enum TemplateStage {
    
    /**
     * 动机阶段 - 给主角一个行动的理由
     * 典型内容：遇到冲突、受到挑衅、发现机会
     */
    MOTIVATION("动机", "主角为什么要行动"),
    
    /**
     * 金手指阶段 - 展示主角的优势
     * 典型内容：展示系统、特殊能力、前世记忆、独特资源
     */
    BONUS("金手指", "主角凭什么解决问题"),
    
    /**
     * 装逼/冲突阶段 - 核心爽点
     * 典型内容：碾压对手、打脸、制造反差、超出预期
     */
    CONFRONTATION("装逼", "主角具体怎么做"),
    
    /**
     * 反馈阶段 - 多层次震惊反应
     * 典型内容：敌人震惊、围观群众传播、权威人物评价
     */
    RESPONSE("反馈", "行动带来什么结果"),
    
    /**
     * 收获阶段 - 本次循环的总结与下次铺垫
     * 典型内容：实力提升、获得奖励、引出更强敌人
     */
    EARNING("收获", "主角自身获得什么");
    
    private final String displayName;
    private final String description;
    
    TemplateStage(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取下一个阶段
     * 如果当前是最后一个阶段（EARNING），则返回第一个阶段（MOTIVATION）开启新循环
     */
    public TemplateStage next() {
        TemplateStage[] stages = values();
        int nextIndex = (this.ordinal() + 1) % stages.length;
        return stages[nextIndex];
    }
    
    /**
     * 判断当前阶段是否是循环的最后一个阶段
     */
    public boolean isLastStage() {
        return this == EARNING;
    }
}

