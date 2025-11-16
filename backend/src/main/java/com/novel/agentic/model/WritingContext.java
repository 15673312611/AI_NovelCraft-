package com.novel.agentic.model;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * 写作上下文（AI生成时使用的所有信息）
 */
@Data
@Builder(toBuilder = true)
public class WritingContext {

    /**
     * 小说基础信息
     */
    private Map<String, Object> novelInfo;

    /**
     * 大纲全文（已弃用，请使用 coreSettings）
     */
    @Deprecated
    private String outline;

    /**
     * 核心设定（替代大纲全文，避免AI上帝视角）
     */
    private String coreSettings;

    /**
     * 当前卷蓝图
     */
    private Map<String, Object> volumeBlueprint;

    /**
     * 章节计划
     */
    private Map<String, Object> chapterPlan;

    /**
     * 相关事件（从图谱检索）
     */
    private List<GraphEntity> relevantEvents;

    /**
     * 未回收伏笔
     */
    private List<GraphEntity> unresolvedForeshadows;

    /**
     * 情节线状态
     */
    private List<GraphEntity> plotlineStatus;

    /**
     * 世界规则约束
     */
    private List<GraphEntity> worldRules;

    /**
     * 叙事节奏分析结果
     */
    private Map<String, Object> narrativeRhythm;

    /**
     * 冲突弧线状态
     */
    private List<GraphEntity> conflictArcs;

    /**
     * 人物成长状态
     */
    private List<GraphEntity> characterArcs;

    /**
     * 视角使用历史
     */
    private List<GraphEntity> perspectiveHistory;

    /**
     * 最近完整章节
     */
    private List<Map<String, Object>> recentFullChapters;

    /**
     * 最近章节摘要
     */
    private List<Map<String, Object>> recentSummaries;

    /**
     * 创新/反套路创意方案
     */
    private List<Map<String, Object>> innovationIdeas;

    /**
     * 本章伏笔规划
     */
    private List<Map<String, Object>> foreshadowPlan;

    /**
     * 本章写作意图（节奏/冲突/人物/视角计划）
     */
    private Map<String, Object> chapterIntent;

    /**
     * 核心剧情纪要（用于提示词前置锚点）
     */
    private Map<String, Object> coreNarrativeSummary;

    /**
     * 经过筛选的高优先级事件
     */
    private List<GraphEntity> prioritizedEvents;

    /**
     * 相关角色档案摘要
     */
    private List<Map<String, Object>> characterProfiles;

    /**
     * 核心记忆账本：角色状态
     */
    private List<Map<String, Object>> characterStates;

    /**
     * 核心记忆账本：关系状态
     */
    private List<Map<String, Object>> relationshipStates;

    /**
     * 核心记忆账本：未决任务
     */
    private List<Map<String, Object>> openQuests;


    /**
     * 写作风格
     */
    private String writingStyle;

    /**
     * 用户调整指令
     */
    private String userAdjustment;

    /**
     * 用户手动指定的参考内容
     */
    private Map<String, String> referenceContents;

    /**
     * AI思考过程（ReAct记录）
     */
    private List<AgentThought> thoughts;
}

