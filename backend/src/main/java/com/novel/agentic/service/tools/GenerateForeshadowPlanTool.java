package com.novel.agentic.service.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.PromptTemplateService;
import com.novel.service.AIWritingService;
import com.novel.dto.AIConfigRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenerateForeshadowPlanTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(GenerateForeshadowPlanTool.class);
    private static final String PROMPT_FILE = "剧情伏笔锚点提取指令.txt";

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private ObjectMapper objectMapper;

    private String basePrompt;

    @PostConstruct
    public void init() {
        registry.register(this);
        basePrompt = promptTemplateService.loadTemplate(PROMPT_FILE);
    }

    @Override
    public String getName() {
        return "generateForeshadowPlan";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> novelId = new HashMap<>();
        novelId.put("type", "integer");
        novelId.put("description", "小说ID");

        Map<String, Object> chapterNumber = new HashMap<>();
        chapterNumber.put("type", "integer");
        chapterNumber.put("description", "当前章节号");

        Map<String, Object> context = new HashMap<>();
        context.put("type", "string");
        context.put("description", "本章剧情概述或需要埋设/回收的方向");

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelId);
        properties.put("chapterNumber", chapterNumber);
        properties.put("context", context);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "chapterNumber"});

        return ToolDefinition.builder()
            .name(getName())
            .description("生成本章可用的伏笔列表，包含埋设方式、回收时机、误导点等信息，帮助剧情保持悬念与反转。")
            .parameters(params)
            .returnExample("[{\"type\":\"埋设\",\"description\":\"...\"}]")
            .costEstimate(900)
            .required(false)
            .build();
    }

    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Integer chapterNumber = ((Number) args.get("chapterNumber")).intValue();
        String context = args.get("context") instanceof String ? (String) args.get("context") : null;

        String prompt = buildPrompt(chapterNumber, context);
        AIConfigRequest aiConfig = args.get("__aiConfig") instanceof AIConfigRequest
            ? (AIConfigRequest) args.get("__aiConfig") : null;

        if (aiConfig == null || !aiConfig.isValid()) {
            logger.info("⏭️ 缺少有效AI配置，跳过伏笔规划生成");
            return java.util.Collections.emptyList();
        }

        String response = aiWritingService.generateContent(prompt, "foreshadow_plan", aiConfig);
        return parseResponse(response);
    }

    private String buildPrompt(Integer chapterNumber, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append(basePrompt).append("\n\n");
        sb.append("【章节信息】\n");
        sb.append("- 章节号：").append(chapterNumber).append("\n");
        if (StringUtils.isNotBlank(context)) {
            sb.append("- 剧情概述：").append(context).append("\n");
        }
        sb.append("\n请严格输出 JSON 数组，每个元素包含：type(埋设/回收/误导)、description、trigger_chapter、payoff_hint、twist_potential。\n");
        sb.append("输出格式示例（仅供参考，请用实际内容替换）：\n");
        sb.append("[").append("\n");
        sb.append("  {\"type\":\"埋设\",\"description\":\"...\",\"trigger_chapter\":\"第2章\",\"payoff_hint\":\"...\",\"twist_potential\":\"...\"}").append("\n");
        sb.append("]\n");
        sb.append("务必遵守：\n");
        sb.append("- 第一个字符必须是 '['，最后一个字符必须是 ']'。\n");
        sb.append("- 所有键和值都必须使用半角双引号，不得出现中文引号。\n");
        sb.append("- 不得输出任何额外文本、注释、标签或前后缀。\n");
        sb.append("- 如无法生成内容，请输出 []。\n");
        return sb.toString();
    }

    private List<Map<String, Object>> parseResponse(String response) throws Exception {
        String json = response.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        json = json.strip();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            logger.warn("伏笔规划解析失败，返回非JSON格式，内容已忽略");
            return java.util.Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            logger.warn("伏笔规划解析JSON失败，内容已忽略", ex);
            return java.util.Collections.emptyList();
        }
    }
}

