package com.novel.script.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 多集短视频剧本：单集产物
 */
@Entity
@Table(name = "video_script_episodes")
@Data
public class VideoScriptEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    /** 第几集（从1开始） */
    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    /** 集标题 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 本集一句话核心/看点（来自 hooks 或大纲） */
    @Column(columnDefinition = "TEXT")
    private String brief;

    /** 上一次重写时给到AI的调整指令 */
    @Column(name = "last_adjustment", columnDefinition = "TEXT")
    private String lastAdjustment;

    /** 可选：本集分镜拆解（如单独生成） */
    @Column(columnDefinition = "TEXT")
    private String storyboard;

    /** 本集最终脚本（短视频可直接录制/剪辑的格式） */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "word_count")
    private Integer wordCount = 0;

    /**
     * 集状态
     * PENDING - 待生成
     * GENERATING - 生成中
     * REVIEWING - 审稿中
     * REVISING - 修改中
     * COMPLETED - 已完成
     * FAILED - 失败
     */
    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    /** AI审稿结果（JSON） */
    @Column(columnDefinition = "TEXT")
    private String reviewResult;

    /** 连续性/后续推演分析结果（JSON，包含episodeSummary用于记忆） */
    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    /** 生成耗时（秒） */
    @Column(name = "generation_time")
    private Long generationTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
