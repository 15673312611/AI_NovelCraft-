package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 按因果链查询事件工具
 * 
 * 从指定事件出发，沿因果链查询前因后果
 */
@Component
public class GetEventsByCausalityTool implements Tool {
    
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
        return "getEventsByCausality";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");
        
        Map<String, Object> eventIdProp = new HashMap<>();
        eventIdProp.put("type", "string");
        eventIdProp.put("description", "起始事件ID（可从其他工具的返回结果中获取）");
        
        Map<String, Object> depthProp = new HashMap<>();
        depthProp.put("type", "integer");
        depthProp.put("description", "查询深度（几度关系，默认3）");
        depthProp.put("default", 3);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("eventId", eventIdProp);
        properties.put("depth", depthProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "eventId"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("沿因果链追溯事件。从指定事件出发，查询其前因（导致该事件的原因）和后果（该事件导致的结果）。返回因果链上的相关事件及其因果距离。适用于理解事件来龙去脉、设计事件后续发展、制造戏剧性反转。")
            .parameters(params)
            .returnExample("[{\"type\": \"Event\", \"description\": \"师父被暗算\", \"chapterNumber\": 5, \"causalDistance\": 1}]")
            .costEstimate(500)
            .required(false)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        String eventId = (String) args.get("eventId");
        Integer depth = args.containsKey("depth") ? ((Number) args.get("depth")).intValue() : 3;
        
        return graphService.getEventsByCausality(novelId, eventId, depth);
    }
}

