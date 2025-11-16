package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 按角色查询相关事件工具
 * 
 * 查询指定角色参与的所有重要事件
 */
@Component
public class GetEventsByCharacterTool implements Tool {
    
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
        return "getEventsByCharacter";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");
        
        Map<String, Object> characterNameProp = new HashMap<>();
        characterNameProp.put("type", "string");
        characterNameProp.put("description", "角色名称");
        
        Map<String, Object> chapterNumberProp = new HashMap<>();
        chapterNumberProp.put("type", "integer");
        chapterNumberProp.put("description", "当前章节号");
        
        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "最多返回多少个事件（默认8）");
        limitProp.put("default", 8);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("characterName", characterNameProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("limit", limitProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "characterName", "chapterNumber"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("查询指定角色参与的所有重要事件。按时间和重要性排序，返回该角色的行动历史、决策记录、关键经历。适用于需要回顾角色成长轨迹、理解角色动机、设计角色回忆等场景。")
            .parameters(params)
            .returnExample("[{\"type\": \"Event\", \"description\": \"与师父初遇\", \"chapterNumber\": 3, \"relevanceScore\": 0.9}]")
            .costEstimate(400)
            .required(false)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        String characterName = (String) args.get("characterName");
        Integer chapterNumber = ((Number) args.get("chapterNumber")).intValue();
        Integer limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 8;
        
        return graphService.getEventsByCharacter(novelId, characterName, chapterNumber, limit);
    }
}

