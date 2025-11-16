package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import java.util.Map;

/**
 * 工具接口（AI可调用的所有工具都要实现此接口）
 */
public interface Tool {
    
    /**
     * 获取工具定义（描述、参数等，供AI理解）
     */
    ToolDefinition getDefinition();
    
    /**
     * 执行工具
     * 
     * @param args 参数
     * @return 执行结果（JSON格式）
     */
    Object execute(Map<String, Object> args) throws Exception;
    
    /**
     * 工具名称
     */
    String getName();
}


