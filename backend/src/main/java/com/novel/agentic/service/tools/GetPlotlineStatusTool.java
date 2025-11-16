package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询情节线状态工具
 * 
 * 检测久未推进、待发展的情节线，提醒AI推进剧情
 */
@Component
public class GetPlotlineStatusTool implements Tool {
    
    @Autowired
    private IGraphService graphService;
    
    @Autowired
    private ToolRegistry registry;
    
    @PostConstruct
    public void init() {
        registry.register(this);
    }
    
    @Override
    public String getName() {
        return "getPlotlineStatus";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");
        
        Map<String, Object> chapterNumberProp = new HashMap<>();
        chapterNumberProp.put("type", "integer");
        chapterNumberProp.put("description", "当前章节号");
        
        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "最多返回多少条情节线（默认5）");
        limitProp.put("default", 5);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("limit", limitProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("查询情节线发展状态。检测久未推进的情节线（\"饥饿\"情节线）、待发展的新情节线、优先级高的情节线。返回情节线名称、状态（进行中/久未推进/待发展）、上次更新章节、闲置时长等。适用于平衡多线叙事、防止遗忘支线、合理推进主线。")
            .parameters(params)
            .returnExample("[{\"type\": \"Plotline\", \"name\": \"主线：修仙之路\", \"status\": \"进行中\", \"lastUpdate\": \"第10章\", \"idleDuration\": 2, \"priority\": 1.0}]")
            .costEstimate(300)
            .required(false)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        if (args == null || !args.containsKey("novelId") || args.get("novelId") == null) {
            throw new IllegalArgumentException("缺少必需参数: novelId");
        }
        if (!args.containsKey("chapterNumber") || args.get("chapterNumber") == null) {
            throw new IllegalArgumentException("缺少必需参数: chapterNumber");
        }
        
        Long novelId = ((Number) args.get("novelId")).longValue();
        Integer chapterNumber = ((Number) args.get("chapterNumber")).intValue();
        Integer limit = args.containsKey("limit") && args.get("limit") != null
            ? ((Number) args.get("limit")).intValue()
            : 5;
        
        return graphService.getPlotlineStatus(novelId, chapterNumber, limit);
    }
}

