package com.novel.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI对话历史实体
 * 记录用户与AI的对话内容
 */
@Data
public class AIConversation {
    /**
     * 对话ID
     */
    private Long id;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 关联的文档ID
     */
    private Long documentId;

    /**
     * 使用的生成器ID
     */
    private Long generatorId;

    /**
     * 用户输入消息
     */
    private String userMessage;

    /**
     * AI回复内容
     */
    private String assistantMessage;

    /**
     * 上下文数据（JSON格式）
     * 包含：参考文件ID列表、关联文档ID列表等
     */
    private String contextData;

    /**
     * 生成字数
     */
    private Integer wordCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

