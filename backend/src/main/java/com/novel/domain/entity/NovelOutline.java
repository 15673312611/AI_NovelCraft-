package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 小说大纲实体类
 * 
 * 说明：本表保存“整本书的大纲/设定”，不做卷级拆分。
 * 典型字段：整体创意、主题、主角群、剧情结构、世界观等。
 * 拆分卷与章节的逻辑不在此表中进行（由 VolumeService 另行处理）。
 */
@TableName("novel_outlines")
public class NovelOutline {

    @TableId(type = IdType.AUTO)
    private Long id;                        // 主键ID

    @NotNull(message = "小说ID不能为空")
    @TableField("novel_id")
    private Long novelId;                   // 关联小说ID

    private String title;                   // 大纲标题（可选）

    private String genre;                   // 小说类型（与 novel.genre 一致，用于生成提示）

    @NotBlank(message = "基本想法不能为空")
    @TableField("basic_idea")
    private String basicIdea;               // 用户的初始构思/故事骨架（最核心的输入）

    @TableField("core_theme")
    private String coreTheme;               // 作品核心主题（如凡人修仙/家国天下等）

    @TableField("main_characters")
    private String mainCharacters;          // 主要角色设定（可为JSON或拼接字符串）

    @TableField("plot_structure")
    private String plotStructure;           // 整体剧情结构/三幕式/起承转合摘要

    @TableField("core_settings")
    private String coreSettings;            // 核心设定（从大纲提炼，不含具体剧情，用于章节写作上下文）

    @TableField("world_setting")
    private String worldSetting;            // 世界观设定（门派、势力、体系等）

    @TableField("key_elements")
    private String keyElements;             // 关键元素/法宝/功法/线索（便于后续扩写）

    @TableField("conflict_types")
    private String conflictTypes;           // 冲突类型（人vs人/人vs环境/内心等）

    @TableField("target_word_count")
    private Integer targetWordCount;        // 目标字数（用于AI规模控制，可选）

    @TableField("target_chapter_count")
    private Integer targetChapterCount;     // 目标章节数（粗粒度规划，可选）

    @NotNull(message = "大纲状态不能为空")
    private OutlineStatus status = OutlineStatus.DRAFT; // 大纲状态（草稿/已确认等）

    @TableField("feedback_history")
    private String feedbackHistory;         // 评审/修改记录（JSON/文本，可选）

    @TableField("revision_count")
    private Integer revisionCount = 0;      // 修订次数（统计用）

    @TableField("is_ai_generated")
    private Boolean isAiGenerated = false;  // 是否AI生成（标记来源）

    @TableField("last_modified_by_ai")
    private String lastModifiedByAi;        // 最后一次由哪个模型/代理修改（可选）

    @TableField("created_by")
    private Long createdBy;                 // 创建人（未来接入鉴权时使用）

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;        // 创建时间

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;        // 更新时间

    // 构造函数
    public NovelOutline() {}

    public NovelOutline(Long novelId, String basicIdea) {
        this.novelId = novelId;
        this.basicIdea = basicIdea;
        this.title = "小说大纲"; // 设置默认标题
        this.isAiGenerated = true; // 标记为AI生成
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

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getBasicIdea() {
        return basicIdea;
    }

    public void setBasicIdea(String basicIdea) {
        this.basicIdea = basicIdea;
    }

    public String getCoreTheme() {
        return coreTheme;
    }

    public void setCoreTheme(String coreTheme) {
        this.coreTheme = coreTheme;
    }

    public String getMainCharacters() {
        return mainCharacters;
    }

    public void setMainCharacters(String mainCharacters) {
        this.mainCharacters = mainCharacters;
    }

    public String getPlotStructure() {
        return plotStructure;
    }

    public void setPlotStructure(String plotStructure) {
        this.plotStructure = plotStructure;
    }

    public String getCoreSettings() {
        return coreSettings;
    }

    public void setCoreSettings(String coreSettings) {
        this.coreSettings = coreSettings;
    }

    public String getWorldSetting() {
        return worldSetting;
    }

    public void setWorldSetting(String worldSetting) {
        this.worldSetting = worldSetting;
    }

    public String getKeyElements() {
        return keyElements;
    }

    public void setKeyElements(String keyElements) {
        this.keyElements = keyElements;
    }

    public String getConflictTypes() {
        return conflictTypes;
    }

    public void setConflictTypes(String conflictTypes) {
        this.conflictTypes = conflictTypes;
    }

    public Integer getTargetWordCount() {
        return targetWordCount;
    }

    public void setTargetWordCount(Integer targetWordCount) {
        this.targetWordCount = targetWordCount;
    }

    public Integer getTargetChapterCount() {
        return targetChapterCount;
    }

    public void setTargetChapterCount(Integer targetChapterCount) {
        this.targetChapterCount = targetChapterCount;
    }

    public OutlineStatus getStatus() {
        return status;
    }

    public void setStatus(OutlineStatus status) {
        this.status = status;
    }

    public String getFeedbackHistory() {
        return feedbackHistory;
    }

    public void setFeedbackHistory(String feedbackHistory) {
        this.feedbackHistory = feedbackHistory;
    }

    public Integer getRevisionCount() {
        return revisionCount;
    }

    public void setRevisionCount(Integer revisionCount) {
        this.revisionCount = revisionCount;
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

    public Boolean getIsAiGenerated() {
        return isAiGenerated;
    }

    public void setIsAiGenerated(Boolean isAiGenerated) {
        this.isAiGenerated = isAiGenerated;
    }

    public String getLastModifiedByAi() {
        return lastModifiedByAi;
    }

    public void setLastModifiedByAi(String lastModifiedByAi) {
        this.lastModifiedByAi = lastModifiedByAi;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    // 业务方法
    public void incrementRevision() {
        this.revisionCount++;
    }

    public boolean isConfirmed() {
        return OutlineStatus.CONFIRMED.equals(this.status);
    }

    public void confirm() {
        this.status = OutlineStatus.CONFIRMED;
    }

    // 大纲状态枚举
    public enum OutlineStatus {
        DRAFT("草稿"),
        CONFIRMED("已确认"),
        REVISED("已修订"),
        REVISING("修改中");

        private final String description;

        OutlineStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return "NovelOutline{" +
                "id=" + id +
                ", novelId=" + novelId +
                ", status=" + status +
                ", revisionCount=" + revisionCount +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NovelOutline that = (NovelOutline) o;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}