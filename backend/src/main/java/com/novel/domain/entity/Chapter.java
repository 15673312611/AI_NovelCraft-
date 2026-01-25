package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 章节实体类
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@TableName("chapters")
public class Chapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "章节标题不能为空")
    private String title;

    private String subtitle;

    private String content;

    @TableField("simple_content")
    private String simpleContent;

    @TableField("word_count")
    private Integer wordCount = 0;

    @TableField("chapter_number")
    private Integer chapterNumber;

    @NotNull(message = "章节状态不能为空")
    private ChapterStatus status = ChapterStatus.DRAFT;

    private String summary;

    private String notes;

    @TableField("generation_context")
    private String generationContext;

    @TableField("is_public")
    private Boolean isPublic = false;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("reading_time_minutes")
    private Integer readingTimeMinutes;

    @TableField("novel_id")
    private Long novelId;

    @TableField("previous_chapter_id")
    private Long previousChapterId;

    @TableField("next_chapter_id")
    private Long nextChapterId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 构造函数
    public Chapter() {}

    public Chapter(String title, String content, Long novelId) {
        this.title = title;
        this.content = content;
        this.novelId = novelId;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        if (content != null) {
            this.wordCount = content.split("\\s+").length;
        }
    }

    public String getSimpleContent() {
        return simpleContent;
    }

    public void setSimpleContent(String simpleContent) {
        this.simpleContent = simpleContent;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public Integer getChapterNumber() {
        return chapterNumber;
    }

    public void setChapterNumber(Integer chapterNumber) {
        this.chapterNumber = chapterNumber;
    }

    public ChapterStatus getStatus() {
        return status;
    }

    public void setStatus(ChapterStatus status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getGenerationContext() {
        return generationContext;
    }

    public void setGenerationContext(String generationContext) {
        this.generationContext = generationContext;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Integer getReadingTimeMinutes() {
        return readingTimeMinutes;
    }

    public void setReadingTimeMinutes(Integer readingTimeMinutes) {
        this.readingTimeMinutes = readingTimeMinutes;
    }

    public Long getNovelId() {
        return novelId;
    }

    public void setNovelId(Long novelId) {
        this.novelId = novelId;
    }

    public Long getPreviousChapterId() {
        return previousChapterId;
    }

    public void setPreviousChapterId(Long previousChapterId) {
        this.previousChapterId = previousChapterId;
    }

    public Long getNextChapterId() {
        return nextChapterId;
    }

    public void setNextChapterId(Long nextChapterId) {
        this.nextChapterId = nextChapterId;
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

    // 业务方法
    public void publish() {
        this.status = ChapterStatus.PUBLISHED;
        this.isPublic = true;
        this.publishedAt = LocalDateTime.now();
    }

    public void unpublish() {
        this.status = ChapterStatus.DRAFT;
        this.isPublic = false;
        this.publishedAt = null;
    }

    public boolean isPublished() {
        return Boolean.TRUE.equals(this.isPublic);
    }

    public boolean isCompleted() {
        return ChapterStatus.COMPLETED.equals(this.status);
    }

    public void calculateReadingTime() {
        if (this.wordCount != null && this.wordCount > 0) {
            // 假设平均阅读速度为每分钟200字
            this.readingTimeMinutes = Math.max(1, this.wordCount / 200);
        }
    }

    // 章节状态枚举
    public enum ChapterStatus {
        DRAFT("草稿"),
        IN_PROGRESS("创作中"),
        REVIEW("审核中"),
        PUBLISHED("已发布"),
        COMPLETED("已完成"),
        ARCHIVED("已归档");

        private final String description;

        ChapterStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return "Chapter{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", chapterNumber=" + chapterNumber +
                ", status=" + status +
                ", wordCount=" + wordCount +
                ", novelId=" + novelId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chapter chapter = (Chapter) o;
        return id != null && id.equals(chapter.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 