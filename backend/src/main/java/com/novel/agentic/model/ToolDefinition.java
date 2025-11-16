package com.novel.agentic.model;

import lombok.Data;
import lombok.Builder;
import java.util.Map;

/**
 * 工具定义（供AI选择调用）
 */
@Data
@Builder
public class ToolDefinition {
    
    /**
     * 工具名称
     */
    private String name;
    
    /**
     * 工具描述（告诉AI这个工具的用途）
     */
    private String description;
    
    /**
     * 参数schema（JSON Schema格式）
     */
    private Map<String, Object> parameters;
    
    /**
     * 返回值示例
     */
    private String returnExample;
    
    /**
     * 调用成本（token估算，用于优化决策）
     */
    private Integer costEstimate;
    
    /**
     * 是否必须调用
     */
    private Boolean required;
}


