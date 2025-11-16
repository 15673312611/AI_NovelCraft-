package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询角色关系网工具
 * 
 * 查询指定角色与其他角色的关系（对抗、合作、暧昧等）
 */
@Component
public class GetCharacterRelationshipsTool implements Tool {
    
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
        return "getCharacterRelationships";
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
        
        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "最多返回多少个关系（默认10）");
        limitProp.put("default", 10);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("characterName", characterNameProp);
        properties.put("limit", limitProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "characterName"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("查询指定角色的关系网络。返回该角色与其他角色的关系类型（对抗、合作、暧昧、师徒等）、关系强度、关系描述。适用于需要了解角色关系、安排角色互动、设计冲突场景等。")
            .parameters(params)
            .returnExample("[{\"type\": \"CharacterRelationship\", \"from\": \"主角\", \"to\": \"师父\", \"relationType\": \"MENTORSHIP\", \"strength\": 0.9, \"description\": \"师徒关系，感情深厚\"}]")
            .costEstimate(300)
            .required(false)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        String characterName = (String) args.get("characterName");
        Integer limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 10;
        
        return graphService.getCharacterRelationships(novelId, characterName, limit);
    }
}

