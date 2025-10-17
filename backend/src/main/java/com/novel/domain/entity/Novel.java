package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 小说实体类
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@TableName("novels")
public class Novel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "小说标题不能为空")
    private String title;

    private String subtitle;

    private String description;

    @TableField("cover_image_url")
    private String coverImageUrl;

    @NotNull(message = "小说状态不能为空")
    private NovelStatus status = NovelStatus.DRAFT;

    private String genre;

    private String tags;

    @TableField("target_audience")
    private String targetAudience;

    @TableField("word_count")
    private Integer wordCount = 0;

    @TableField("chapter_count")
    private Integer chapterCount = 0;

    @TableField("estimated_completion")
    private LocalDateTime estimatedCompletion;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("is_public")
    private Boolean isPublic = false;

    private Double rating = 0.0;

    @TableField("rating_count")
    private Integer ratingCount = 0;
    
    // 新增：小说创作配置字段
    @TableField("target_total_chapters")
    private Integer targetTotalChapters; // 目标总章数
    
    @TableField("words_per_chapter")
    private Integer wordsPerChapter; // 每章字数
    
    @TableField("planned_volume_count")
    private Integer plannedVolumeCount; // 计划卷数
    
    @TableField("total_word_target")
    private Integer totalWordTarget; // 总字数目标

    // 新增：整书大纲字段（确认后的大纲直接存放于此）
    @TableField("outline")
    private String outline;

    // 新增：创作阶段状态字段
    @TableField("creation_stage")
    private CreationStage creationStage = CreationStage.OUTLINE_PENDING;

    @TableField("created_by")
    private Long authorId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 构造函数
    public Novel() {}

    public Novel(String title, String description, Long authorId) {
        this.title = title;
        this.description = description;
        this.authorId = authorId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public NovelStatus getStatus() {
        return status;
    }

    public void setStatus(NovelStatus status) {
        this.status = status;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public Integer getChapterCount() {
        return chapterCount;
    }

    public void setChapterCount(Integer chapterCount) {
        this.chapterCount = chapterCount;
    }

    public LocalDateTime getEstimatedCompletion() {
        return estimatedCompletion;
    }

    public void setEstimatedCompletion(LocalDateTime estimatedCompletion) {
        this.estimatedCompletion = estimatedCompletion;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Integer ratingCount) {
        this.ratingCount = ratingCount;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Long getCreatedBy() {
        return authorId; // created_by字段与author_id相同
    }

    public void setCreatedBy(Long createdBy) {
        this.authorId = createdBy; // 设置created_by实际上是设置author_id
    }

    public String getAuthor() {
        return "作者"; // 临时返回，实际应该从用户表获取
    }

    public void setAuthor(String author) {
        // 这个方法暂时不实现，因为author字段不存在
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public CreationStage getCreationStage() {
        return creationStage;
    }

    public void setCreationStage(CreationStage creationStage) {
        this.creationStage = creationStage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // 新增字段的getter和setter
    public Integer getTargetTotalChapters() {
        return targetTotalChapters;
    }

    public void setTargetTotalChapters(Integer targetTotalChapters) {
        this.targetTotalChapters = targetTotalChapters;
    }

    public Integer getWordsPerChapter() {
        return wordsPerChapter;
    }

    public void setWordsPerChapter(Integer wordsPerChapter) {
        this.wordsPerChapter = wordsPerChapter;
    }

    public Integer getPlannedVolumeCount() {
        return plannedVolumeCount;
    }

    public void setPlannedVolumeCount(Integer plannedVolumeCount) {
        this.plannedVolumeCount = plannedVolumeCount;
    }

    public Integer getTotalWordTarget() {
        return totalWordTarget;
    }

    public void setTotalWordTarget(Integer totalWordTarget) {
        this.totalWordTarget = totalWordTarget;
    }

    // 业务方法

    public boolean isCompleted() {
        return NovelStatus.COMPLETED.equals(this.status);
    }

    public boolean isPublished() {
        return Boolean.TRUE.equals(this.isPublic);
    }



    // 小说状态枚举
    public enum NovelStatus {
        DRAFT("草稿"),
        WRITING("创作中"),
        REVIEWING("审核中"),
        COMPLETED("已完成");

        private final String description;

        NovelStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 创作阶段枚举
    public enum CreationStage {
        OUTLINE_PENDING("待确认大纲"),
        OUTLINE_CONFIRMED("大纲已确认"),
        VOLUMES_GENERATED("卷已生成"),
        DETAILED_OUTLINE_GENERATED("详细大纲已生成"),
        WRITING_IN_PROGRESS("写作进行中"),
        WRITING_COMPLETED("写作已完成");

        private final String description;

        CreationStage(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return "Novel{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", authorId=" + authorId +
                ", wordCount=" + wordCount +
                ", chapterCount=" + chapterCount +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Novel novel = (Novel) o;
        return id != null && id.equals(novel.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 