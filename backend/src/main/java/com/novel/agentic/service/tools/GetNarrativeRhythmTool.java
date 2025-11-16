package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取叙事节奏状态工具
 *
 * 用于帮助AI判断当前章节的节奏策略（冲突/主线/人物/缓冲）
 */
@Component
public class GetNarrativeRhythmTool implements Tool {

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
        return "getNarrativeRhythm";
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
        chapterNumberProp.put("description", "当前生成的章节号");

        Map<String, Object> windowProp = new HashMap<>();
        windowProp.put("type", "integer");
        windowProp.put("description", "回溯窗口，统计最近多少章（默认6）");
        windowProp.put("default", 6);

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("window", windowProp);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});

        return ToolDefinition.builder()
            .name(getName())
            .description("分析最近章节的叙事节奏分布，评估冲突/主线/人物/缓冲的比例，并给出节奏调整建议。")
            .parameters(params)
            .returnExample("{\"metrics\":{\"conflictRatio\":0.6},\"recommendations\":[\"转入人物刻画\"]}")
            .costEstimate(250)
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
        Integer window = args.containsKey("window") && args.get("window") != null
            ? ((Number) args.get("window")).intValue()
            : 6;

        return graphService.getNarrativeRhythmStatus(novelId, chapterNumber, window);
    }
}


