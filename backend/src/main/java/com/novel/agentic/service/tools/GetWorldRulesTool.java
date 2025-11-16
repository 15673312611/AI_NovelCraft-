package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取世界规则工具
 * 
 * 自动注入：优先使用Neo4j实现，不可用时降级到内存版
 */
@Component
public class GetWorldRulesTool implements Tool {
    
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
        return "getWorldRules";
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
        limitProp.put("description", "最多返回多少条规则（默认5）");
        limitProp.put("default", 5);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("limit", limitProp);
        
        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});
        
        return ToolDefinition.builder()
            .name(getName())
            .description("获取世界观设定和规则约束。包括力量体系、时间线规则、场景设定、社会规则等。确保当前章节符合已建立的世界观设定，避免设定崩坏。")
            .parameters(params)
            .returnExample("[{\"type\": \"WorldRule\", \"name\": \"力量体系\", \"constraint\": \"等级提升需要时间\", \"category\": \"power_system\"}]")
            .costEstimate(200)
            .required(false)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        if (args == null || !args.containsKey("novelId")) {
            throw new IllegalArgumentException("缺少必需参数: novelId");
        }
        
        Long novelId = ((Number) args.get("novelId")).longValue();
        // chapterNumber是可选参数，如果没有则使用0
        Integer chapterNumber = args.containsKey("chapterNumber") && args.get("chapterNumber") != null
            ? ((Number) args.get("chapterNumber")).intValue()
            : 0;
        Integer limit = args.containsKey("limit") && args.get("limit") != null
            ? ((Number) args.get("limit")).intValue()
            : 5;
        
        return graphService.getWorldRules(novelId, chapterNumber, limit);
    }
}


