package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表（管理所有可用工具）
 */
@Service
public class ToolRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);
    
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    
    /**
     * 注册工具
     */
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        logger.info("📌 工具已注册: {}", tool.getName());
    }
    
    /**
     * 获取工具
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }
    
    /**
     * 获取所有工具定义（供AI选择）
     */
    public List<ToolDefinition> getAllDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }
    
    /**
     * 执行工具
     */
    public Object executeTool(String toolName, Map<String, Object> args) throws Exception {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在: " + toolName);
        }
        
        logger.info("🔧 执行工具: {} | 参数: {}", toolName, args);
        Object result = tool.execute(args);
        logger.info("✅ 工具执行完成: {}", toolName);
        
        return result;
    }
    
    /**
     * 获取所有工具名称
     */
    public Set<String> getAllToolNames() {
        return tools.keySet();
    }
}


