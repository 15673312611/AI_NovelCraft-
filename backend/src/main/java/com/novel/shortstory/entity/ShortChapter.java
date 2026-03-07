package com.novel.shortstory.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 短篇小说章节
 */
@Entity
@Table(name = "short_novel_chapters")
@Data
public class ShortChapter {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "novel_id", nullable = false)
    private Long novelId;
    
    /**
     * 章节序号（从1开始）
     */
    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;
    
    /**
     * 章节标题
     */
    @Column(nullable = false, length = 200)
    private String title;
    
    /**
     * 章节简述（来自大纲/看点核心）
     */
    @Column(columnDefinition = "TEXT")
    private String brief;

    /**
     * 上一次重写时给到AI的调整指令（来自审稿建议等）
     */
    @Column(name = "last_adjustment", columnDefinition = "TEXT")
    private String lastAdjustment;
    
    /**
     * 正文内容
     */
    @Column(columnDefinition = "TEXT")
    private String content;
    
    /**
     * 字数
     */
    @Column(name = "word_count")
    private Integer wordCount = 0;
    
    /**
     * 章节状态
     * PENDING - 待生成
     * GENERATING - 生成中
     * REVIEWING - 审稿中
     * REVISING - 修改中
     * COMPLETED - 已完成
     * FAILED - 失败
     */
    @Column(nullable = false, length = 50)
    private String status = "PENDING";
    
    /**
     * AI审稿结果（JSON格式）
     * {
     *   "score": 8,
     *   "comments": "剧情紧凑，但人物动机不够...",
     *   "suggestions": "建议加强...",
     *   "passed": true
     * }
     */
    @Column(columnDefinition = "TEXT")
    private String reviewResult;

    /**
     * 章节分析结果（JSON格式，可包含：看点/风险/下一章建议/是否更新大纲或看点）
     */
    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;
    
    /**
     * 生成耗时（秒）
     */
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
