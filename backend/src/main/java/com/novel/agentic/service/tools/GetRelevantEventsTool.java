package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取相关事件工具（从图谱检索）
 * 
 * 自动注入：优先使用Neo4j实现，不可用时降级到内存版
 */
@Component
public class GetRelevantEventsTool implements Tool {
    
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
        return "getRelevantEvents";
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
        limitProp.put("description", "最多返回多少个事件（默认8）");
        limitProp.put("default", 8);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("limit", limitProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("从图谱中检索与当前章节强相关的历史事件。基于因果关系、参与者、情节线等维度智能筛选，而非简单的时间顺序。适用于需要回溯前因后果、角色动机等场景。")
            .parameters(params)
            .returnExample("[{\"type\": \"Event\", \"description\": \"...\", \"participants\": [...], \"chapterNumber\": 5}]")
            .costEstimate(400)
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
            : 8;
        
        return graphService.getRelevantEvents(novelId, chapterNumber, limit);
    }
}


