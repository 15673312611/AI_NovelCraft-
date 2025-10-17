package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 角色实体类
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@TableName("characters")
public class Character {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    private String name;

    private String alias;

    private String description;

    private String appearance;

    private String personality;

    private String background;

    private String motivation;

    private String goals;

    private String conflicts;

    private String relationships;

    @TableField("character_arc")
    private String characterArc;

    @TableField("is_protagonist")
    private Boolean isProtagonist = false;

    @TableField("is_antagonist")
    private Boolean isAntagonist = false;

    @TableField("is_major_character")
    private Boolean isMajorCharacter = false;

    @TableField("first_appearance_chapter")
    private Integer firstAppearanceChapter;

    @TableField("last_appearance_chapter")
    private Integer lastAppearanceChapter;

    /**
     * 出场次数统计
     */
    @TableField("appearance_count")
    private Integer appearanceCount = 0;

    /**
     * 角色类型：PROTAGONIST, MAJOR, MINOR, TEMPORARY
     */
    @TableField("character_type")
    private String characterType = "MINOR";

    /**
     * 角色状态：ACTIVE, INACTIVE, DECEASED, MISSING
     */
    @TableField("status")
    private String status = "ACTIVE";

    /**
     * 重要性评分 (0-100)
     */
    @TableField("importance_score")
    private Integer importanceScore = 0;

    /**
     * 角色标签 (逗号分隔)
     */
    @TableField("tags")
    private String tags;

    @TableField("character_image_url")
    private String characterImageUrl;

    @TableField("novel_id")
    private Long novelId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 构造函数
    public Character() {}

    public Character(String name, String description, Long novelId) {
        this.name = name;
        this.description = description;
        this.novelId = novelId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAppearance() {
        return appearance;
    }

    public void setAppearance(String appearance) {
        this.appearance = appearance;
    }

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getMotivation() {
        return motivation;
    }

    public void setMotivation(String motivation) {
        this.motivation = motivation;
    }

    public String getGoals() {
        return goals;
    }

    public void setGoals(String goals) {
        this.goals = goals;
    }

    public String getConflicts() {
        return conflicts;
    }

    public void setConflicts(String conflicts) {
        this.conflicts = conflicts;
    }

    public String getRelationships() {
        return relationships;
    }

    public void setRelationships(String relationships) {
        this.relationships = relationships;
    }

    public String getCharacterArc() {
        return characterArc;
    }

    public void setCharacterArc(String characterArc) {
        this.characterArc = characterArc;
    }

    public Boolean getIsProtagonist() {
        return isProtagonist;
    }

    public void setIsProtagonist(Boolean isProtagonist) {
        this.isProtagonist = isProtagonist;
    }

    public Boolean getIsAntagonist() {
        return isAntagonist;
    }

    public void setIsAntagonist(Boolean isAntagonist) {
        this.isAntagonist = isAntagonist;
    }

    public Boolean getIsMajorCharacter() {
        return isMajorCharacter;
    }

    public void setIsMajorCharacter(Boolean isMajorCharacter) {
        this.isMajorCharacter = isMajorCharacter;
    }

    public Integer getFirstAppearanceChapter() {
        return firstAppearanceChapter;
    }

    public void setFirstAppearanceChapter(Integer firstAppearanceChapter) {
        this.firstAppearanceChapter = firstAppearanceChapter;
    }

    public Integer getLastAppearanceChapter() {
        return lastAppearanceChapter;
    }

    public void setLastAppearanceChapter(Integer lastAppearanceChapter) {
        this.lastAppearanceChapter = lastAppearanceChapter;
    }

    public String getCharacterImageUrl() {
        return characterImageUrl;
    }

    public void setCharacterImageUrl(String characterImageUrl) {
        this.characterImageUrl = characterImageUrl;
    }

    public Long getNovelId() {
        return novelId;
    }

    public void setNovelId(Long novelId) {
        this.novelId = novelId;
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

    public Integer getAppearanceCount() {
        return appearanceCount;
    }

    public void setAppearanceCount(Integer appearanceCount) {
        this.appearanceCount = appearanceCount;
    }

    public String getCharacterType() {
        return characterType;
    }

    public void setCharacterType(String characterType) {
        this.characterType = characterType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(Integer importanceScore) {
        this.importanceScore = importanceScore;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    // 业务方法
    public boolean isMainCharacter() {
        return Boolean.TRUE.equals(this.isProtagonist) || Boolean.TRUE.equals(this.isAntagonist);
        }

        public String getDisplayName() {
        if (alias != null && !alias.trim().isEmpty()) {
            return name + " (" + alias + ")";
        }
        return name;
    }

    @Override
    public String toString() {
        return "Character{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isProtagonist=" + isProtagonist +
                ", isAntagonist=" + isAntagonist +
                ", novelId=" + novelId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Character character = (Character) o;
        return id != null && id.equals(character.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 