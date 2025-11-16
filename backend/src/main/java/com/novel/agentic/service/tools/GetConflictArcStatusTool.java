package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询活跃冲突弧线工具
 */
@Component
public class GetConflictArcStatusTool implements Tool {

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
        return "getConflictArcStatus";
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
        chapterNumberProp.put("description", "当前章节号，用于计算久未推进的冲突");

        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "最多返回的冲突弧线数量（默认5）");
        limitProp.put("default", 5);

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("limit", limitProp);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});

        return ToolDefinition.builder()
            .name(getName())
            .description("获取仍在进行的冲突弧线，包含阶段、紧迫度、下一步升级建议，为章节冲突设计提供依据。")
            .parameters(params)
            .returnExample("[{\"type\":\"ConflictArc\",\"properties\":{\"name\":\"魔宗入侵\",\"stage\":\"酝酿末期\"}}]")
            .costEstimate(220)
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

        return graphService.getActiveConflictArcs(novelId, chapterNumber, limit);
    }
}


