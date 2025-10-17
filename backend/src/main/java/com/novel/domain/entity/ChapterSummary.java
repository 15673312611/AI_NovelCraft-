package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 章节概括实体类
 * 用于保存每章的简短概括，帮助AI写作时保持连贯性
 */
@TableName("chapter_summaries")
public class ChapterSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 章节号
     */
    private Integer chapterNumber;

    /**
     * 章节概括内容（100-200字）
     */
    private String summary;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    // 构造方法
    public ChapterSummary() {}

    public ChapterSummary(Long novelId, Integer chapterNumber, String summary) {
        this.novelId = novelId;
        this.chapterNumber = chapterNumber;
        this.summary = summary;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    // Getter和Setter方法
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

    public Integer getChapterNumber() {
        return chapterNumber;
    }

    public void setChapterNumber(Integer chapterNumber) {
        this.chapterNumber = chapterNumber;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ChapterSummary{" +
                "id=" + id +
                ", novelId=" + novelId +
                ", chapterNumber=" + chapterNumber +
                ", summary='" + summary + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}