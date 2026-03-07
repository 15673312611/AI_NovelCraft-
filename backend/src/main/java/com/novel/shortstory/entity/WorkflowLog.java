package com.novel.shortstory.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 工作流执行日志
 * 记录AI的思考、决策和操作
 */
@Entity
@Table(name = "workflow_logs")
@Data
public class WorkflowLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "novel_id", nullable = false)
    private Long novelId;
    
    @Column(name = "chapter_number")
    private Integer chapterNumber;
    
    /**
     * 日志类型
     * INFO - 普通信息
     * THOUGHT - AI思考
     * ACTION - 执行动作
     * REVIEW - 审稿意见
     * ERROR - 错误
     * SUCCESS - 成功
     */
    @Column(nullable = false, length = 50)
    private String type;
    
    /**
     * 日志内容
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    /**
     * 详细数据（JSON格式，可选）
     */
    @Column(columnDefinition = "TEXT")
    private String detail;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
