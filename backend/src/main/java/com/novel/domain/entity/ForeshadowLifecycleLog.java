package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 伏笔生命周期跨卷追踪日志
 * 对应表：foreshadow_lifecycle_log
 */
@Data
@TableName("foreshadow_lifecycle_log")
public class ForeshadowLifecycleLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 伏笔ID（关联 novel_foreshadowing 表）
     */
    private Long foreshadowId;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 卷ID
     */
    private Long volumeId;

    /**
     * 卷序号（第几卷）
     */
    private Integer volumeNumber;

    /**
     * 卷内章节序号（可选，若动作发生在具体章节）
     */
    private Integer chapterInVolume;

    /**
     * 全局章节号（可选）
     */
    private Integer globalChapterNumber;

    /**
     * 伏笔动作（PLANT-埋/REFERENCE-提/DEEPEN-加深/RESOLVE-回收）
     */
    private String action;

    /**
     * 动作详情（JSON对象）
     * - PLANT: {content, futureAnchorPlan, resolveWindow, priority, ties}
     * - REFERENCE: {reminderHint}
     * - DEEPEN: {newInfo, newAnchor:{vol,ch,hint}, escalation}
     * - RESOLVE: {anchorsUsed:[{vol,ch,hint}], revealPlan, cost}
     */
    private String detail;

    /**
     * 决策时间（AI生成章纲或写作时的时间）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime decidedAt;
}

