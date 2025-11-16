package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询视角使用历史工具
 */
@Component
public class GetPerspectiveHistoryTool implements Tool {

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
        return "getPerspectiveHistory";
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

        Map<String, Object> windowProp = new HashMap<>();
        windowProp.put("type", "integer");
        windowProp.put("description", "回溯窗口，查看最近多少章的视角使用（默认5）");
        windowProp.put("default", 5);

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("window", windowProp);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});

        return ToolDefinition.builder()
            .name(getName())
            .description("回顾最近章节的视角使用与语气，提示是否需要切换视角或调整叙述方式。")
            .parameters(params)
            .returnExample("[{\"type\":\"PerspectiveUsage\",\"properties\":{\"characterName\":\"林晓\",\"mode\":\"第三人称\"}}]")
            .costEstimate(120)
            .required(false)
            .build();
    }

    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        Integer chapterNumber = ((Number) args.get("chapterNumber")).intValue();
        Integer window = args.containsKey("window") ? ((Number) args.get("window")).intValue() : 5;

        return graphService.getPerspectiveHistory(novelId, chapterNumber, window);
    }
}


