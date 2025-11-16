package com.novel.agentic.model;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * AI代理的思考记录
 */
@Data
@Builder
public class AgentThought {
    
    /**
     * 步骤编号
     */
    private Integer stepNumber;
    
    /**
     * 思考内容（推理过程）
     */
    private String reasoning;
    
    /**
     * 决定执行的动作
     */
    private String action;
    
    /**
     * 动作参数（JSON格式）
     */
    private String actionArgs;
    
    /**
     * 观察到的结果
     */
    private String observation;
    
    /**
     * 对结果的反思（评估结果质量、判断是否需要更多信息）
     */
    private String reflection;
    
    /**
     * 是否达成目标
     */
    private Boolean goalAchieved;
    
    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 额外信息
     */
    private String metadata;
}


