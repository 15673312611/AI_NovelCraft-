package com.novel.script.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 短视频剧本实体（剧本工厂工作流）
 *
 * 升级目标：支持“多集连续生成”工作流（参考短篇小说工厂）
 */
@Entity
@Table(name = "video_scripts")
@Data
public class VideoScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 标题（系列名） */
    @Column(nullable = false, length = 200)
    private String title;

    /** 构思 */
    @Column(columnDefinition = "TEXT")
    private String idea;

    /**
     * 模式
     * HALF_NARRATION：半解说（解说+人物对话）
     * PURE_NARRATION：纯解说（全程旁白）
     */
    @Column(nullable = false, length = 50)
    private String mode = "HALF_NARRATION";

    /**
     * 剧本格式（影响每集正文输出结构）
     * SCENE：集-场台本（真人短剧/影视）
     * STORYBOARD：分镜脚本（漫剧/动态漫）
     */
    @Column(name = "script_format", nullable = false, length = 30)
    private String scriptFormat = "STORYBOARD";

    /** 每集目标时长（秒） */
    @Column(name = "target_seconds")
    private Integer targetSeconds = 60;

    /** 每集镜头数量（镜头/场景数量） */
    @Column(name = "scene_count")
    private Integer sceneCount = 20;

    /** 系列设定 / Story Bible */
    @Column(name = "script_setting", columnDefinition = "TEXT")
    private String scriptSetting;

    /** 系列大纲（多阶段、多集走向） */
    @Column(columnDefinition = "TEXT")
    private String outline;

    /** 看点/标题/核心/悬念（JSON） */
    @Column(name = "hooks_json", columnDefinition = "TEXT")
    private String hooksJson;

    /** 导语（黄金开头/系列开场钩子） */
    @Column(columnDefinition = "TEXT")
    private String prologue;

    /**
     * 以下字段为“旧单集版本”遗留产物（仍保留字段，避免历史数据丢失）
     */
    @Column(columnDefinition = "TEXT")
    private String storyboard;

    @Column(name = "final_script", columnDefinition = "TEXT")
    private String finalScript;

    /** 计划集数（可后续扩展） */
    @Column(name = "episode_count")
    private Integer episodeCount = 10;

    /** 已完成集数（指最后完成的 episodeNumber） */
    @Column(name = "current_episode")
    private Integer currentEpisode = 0;

    /** 当前集重试次数 */
    @Column(name = "current_retry_count")
    private Integer currentRetryCount = 0;

    /** 每集最大重试次数 */
    @Column(name = "max_retry_per_episode")
    private Integer maxRetryPerEpisode = 3;

    /** 审稿最低通过分（1-10） */
    @Column(name = "min_pass_score")
    private Integer minPassScore = 7;

    /** 是否允许在生成过程中根据剧情更新大纲 */
    @Column(name = "enable_outline_update")
    private Boolean enableOutlineUpdate = true;

    /** 工作流状态 */
    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    /** 当前正在执行的工作流步骤（用于前端高亮） */
    @Column(name = "active_step", length = 100)
    private String activeStep;

    /** 工作流配置（JSON，如模型ID等） */
    @Column(name = "workflow_config", columnDefinition = "TEXT")
    private String workflowConfig;

    /** 错误信息 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

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
