package com.novel.script.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 剧本工作流日志
 */
@Entity
@Table(name = "video_script_logs")
@Data
public class VideoScriptLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    /** 可选：对应第几集（用于多集工作流筛选） */
    @Column(name = "episode_number")
    private Integer episodeNumber;

    /** INFO / THOUGHT / ACTION / REVIEW / ERROR / SUCCESS */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
