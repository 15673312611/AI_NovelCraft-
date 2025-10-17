package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 小说卷实体类
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@TableName("novel_volumes")
public class NovelVolume {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "小说ID不能为空")
    @TableField("novel_id")
    private Long novelId;

    @NotBlank(message = "卷标题不能为空")
    private String title;

    @NotBlank(message = "卷主题不能为空")
    private String theme;

    @TableField("description")
    private String description;

    @TableField("content_outline")
    private String contentOutline;

    @TableField("volume_number")
    private Integer volumeNumber;

    @TableField("chapter_start")
    private Integer chapterStart;

    @TableField("chapter_end")
    private Integer chapterEnd;

    @TableField("estimated_word_count")
    private Integer estimatedWordCount;

    @TableField("actual_word_count")
    private Integer actualWordCount = 0;

    @TableField("key_events")
    private String keyEvents;

    @TableField("character_development")
    private String characterDevelopment;

    @TableField("plot_threads")
    private String plotThreads;

    @NotNull(message = "卷状态不能为空")
    private VolumeStatus status = VolumeStatus.PLANNED;

    @TableField("is_ai_generated")
    private Boolean isAiGenerated = false;

    @TableField("last_modified_by_ai")
    private LocalDateTime lastModifiedByAi;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 构造函数
    public NovelVolume() {}

    public NovelVolume(Long novelId, String title, String theme, Integer volumeNumber) {
        this.novelId = novelId;
        this.title = title;
        this.theme = theme;
        this.volumeNumber = volumeNumber;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNovelId() {
        return novelId;
    }

    public void setNovelId(Long novelId) {
        this.novelId = novelId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContentOutline() {
        return contentOutline;
    }

    public void setContentOutline(String contentOutline) {
        this.contentOutline = contentOutline;
    }

    public Integer getVolumeNumber() {
        return volumeNumber;
    }

    public void setVolumeNumber(Integer volumeNumber) {
        this.volumeNumber = volumeNumber;
    }

    public Integer getChapterStart() {
        return chapterStart;
    }

    public void setChapterStart(Integer chapterStart) {
        this.chapterStart = chapterStart;
    }

    public Integer getChapterEnd() {
        return chapterEnd;
    }

    public void setChapterEnd(Integer chapterEnd) {
        this.chapterEnd = chapterEnd;
    }

    public Integer getEstimatedWordCount() {
        return estimatedWordCount;
    }

    public void setEstimatedWordCount(Integer estimatedWordCount) {
        this.estimatedWordCount = estimatedWordCount;
    }

    public Integer getActualWordCount() {
        return actualWordCount;
    }

    public void setActualWordCount(Integer actualWordCount) {
        this.actualWordCount = actualWordCount;
    }

    public String getKeyEvents() {
        return keyEvents;
    }

    public void setKeyEvents(String keyEvents) {
        this.keyEvents = keyEvents;
    }

    public String getCharacterDevelopment() {
        return characterDevelopment;
    }

    public void setCharacterDevelopment(String characterDevelopment) {
        this.characterDevelopment = characterDevelopment;
    }

    public String getPlotThreads() {
        return plotThreads;
    }

    public void setPlotThreads(String plotThreads) {
        this.plotThreads = plotThreads;
    }

    public VolumeStatus getStatus() {
        return status;
    }

    public void setStatus(VolumeStatus status) {
        this.status = status;
    }

    public Boolean getIsAiGenerated() {
        return isAiGenerated;
    }

    public void setIsAiGenerated(Boolean isAiGenerated) {
        this.isAiGenerated = isAiGenerated;
    }

    public LocalDateTime getLastModifiedByAi() {
        return lastModifiedByAi;
    }

    public void setLastModifiedByAi(LocalDateTime lastModifiedByAi) {
        this.lastModifiedByAi = lastModifiedByAi;
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
    public void markAsAiModified() {
        this.isAiGenerated = true;
        this.lastModifiedByAi = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return VolumeStatus.COMPLETED.equals(this.status);
    }

    public boolean isInProgress() {
        return VolumeStatus.IN_PROGRESS.equals(this.status);
    }

    public int getChapterCount() {
        if (chapterStart != null && chapterEnd != null) {
            return chapterEnd - chapterStart + 1;
        }
        return 0;
    }

    public double getProgress() {
        if (estimatedWordCount != null && estimatedWordCount > 0) {
            return (double) actualWordCount / estimatedWordCount * 100;
        }
        return 0.0;
    }

    // 卷状态枚举
    public enum VolumeStatus {
        PLANNED("规划中"),
        IN_PROGRESS("进行中"),
        COMPLETED("已完成"),
        REVISED("已修订");

        private final String description;

        VolumeStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return "NovelVolume{" +
                "id=" + id +
                ", novelId=" + novelId +
                ", title='" + title + '\'' +
                ", volumeNumber=" + volumeNumber +
                ", status=" + status +
                ", progress=" + getProgress() + "%" +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NovelVolume that = (NovelVolume) o;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}