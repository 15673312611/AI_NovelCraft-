package com.novel.agentic.service.tools;

import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询人物成长状态工具
 */
@Component
public class GetCharacterArcStatusTool implements Tool {

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
        return "getCharacterArcStatus";
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
        chapterNumberProp.put("description", "当前章节号，用于识别久未推进的人物线");

        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "最多返回的人物弧数量（默认5）");
        limitProp.put("default", 5);

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("chapterNumber", chapterNumberProp);
        properties.put("limit", limitProp);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});

        return ToolDefinition.builder()
            .name(getName())
            .description("检查人物成长线的推进情况，输出待完成的成长节拍、下一目标，以及优先级。")
            .parameters(params)
            .returnExample("[{\"type\":\"CharacterArc\",\"properties\":{\"characterName\":\"柳青\",\"pendingBeat\":\"面对过去\"}}]")
            .costEstimate(200)
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

        return graphService.getCharacterArcStatus(novelId, chapterNumber, limit);
    }
}


