package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询冲突发展历史工具
 * 
 * 查询主角与指定角色的所有对抗、冲突事件
 */
@Component
public class GetConflictHistoryTool implements Tool {
    
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
        return "getConflictHistory";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");
        
        Map<String, Object> protagonistNameProp = new HashMap<>();
        protagonistNameProp.put("type", "string");
        protagonistNameProp.put("description", "主角名称");
        
        Map<String, Object> antagonistNameProp = new HashMap<>();
        antagonistNameProp.put("type", "string");
        antagonistNameProp.put("description", "对手/反派名称");
        
        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "最多返回多少个冲突事件（默认10）");
        limitProp.put("default", 10);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("protagonistName", protagonistNameProp);
        properties.put("antagonistName", antagonistNameProp);
        properties.put("limit", limitProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "protagonistName", "antagonistName"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("查询两个角色之间的冲突发展历史。返回主角与指定对手的所有对抗、冲突、交锋事件，按时间顺序排列。适用于设计高潮对决、回忆宿怨、设计复仇剧情、展现矛盾升级等场景。")
            .parameters(params)
            .returnExample("[{\"type\": \"Event\", \"description\": \"首次交锋\", \"chapterNumber\": 5, \"participants\": [\"主角\", \"反派\"]}]")
            .costEstimate(400)
            .required(false)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        String protagonistName = (String) args.get("protagonistName");
        String antagonistName = (String) args.get("antagonistName");
        Integer limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 10;
        
        return graphService.getConflictHistory(novelId, protagonistName, antagonistName, limit);
    }
}

