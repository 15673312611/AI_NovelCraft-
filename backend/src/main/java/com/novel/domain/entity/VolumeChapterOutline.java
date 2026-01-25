package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 卷级批量生成的章纲实体
 * 对应表：volume_chapter_outlines
 */
@Data
@TableName("volume_chapter_outlines")
public class VolumeChapterOutline {

    @TableId(type = IdType.AUTO)
    private Long id;

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
     * 卷内章节序号（1..N）
     */
    private Integer chapterInVolume;

    /**
     * 全局章节号（可选，若卷有起始章节则计算）
     */
    private Integer globalChapterNumber;

    /**
     * 本章剧情方向（简练有力）
     */
    private String direction;

    /**
     * 关键剧情点（JSON数组，3-6条，强调冲突/抉择/代价/反转/后果）
     */
    private String keyPlotPoints;

    /**
     * 情感基调（如：危机/逆转/悬疑/温情/黑暗/希望/燃）
     */
    private String emotionalTone;

    /**
     * 伏笔动作（NONE|PLANT|REFERENCE|DEEPEN|RESOLVE）
     */
    private String foreshadowAction;

    /**
     * 伏笔详情（JSON对象）
     * - RESOLVE: {refId, anchorsUsed:[{vol,ch,hint}], revealPlan, cost}
     * - PLANT: {content, futureAnchorPlan, resolveWindow:{min,max}, priority, ties:[]}
     * - DEEPEN: {refId, newInfo, newAnchor:{vol,ch,hint}, escalation}
     * - REFERENCE: {refId, reminderHint}
     */
    private String foreshadowDetail;

    /**
     * 支线/人设刻画/世界观探索等（可选）
     */
    private String subplot;

    /**
     * 对手/阻力与赌注（JSON对象，如{opponent:string, stakes:string}）
     */
    private String antagonism;

    /**
     * 章纲状态（PENDING-待写作/WRITTEN-已写作/REVISED-已修订）
     */
    private String status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

