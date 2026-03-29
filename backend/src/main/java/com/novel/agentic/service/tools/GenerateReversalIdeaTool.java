package com.novel.agentic.service.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.ToolDefinition;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
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
public class GenerateReversalIdeaTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReversalIdeaTool.class);
    private static final String PROMPT_KEY = "agentic.tool.reversal_idea";
    private static final String DEFAULT_PROMPT = """
        你是一名擅长网文反转设计的剧情策划。
        请基于给定场景，设计至少 3 个可落地的反套路创意。

        工作要求：
        1. 反转必须建立在当前情境之上，不能脱离上下文硬拐。
        2. 每个方案都要同时说明铺垫方式、误导策略和落地后果。
        3. 优先考虑身份错位、信息差翻转、目标反噬、立场逆转、利益交换失控、情绪误判等方向。
        4. 输出的方案应当能直接服务章节写作，而不是抽象概念。

        输出要求：
        - 只输出 JSON 数组。
        - 每个元素必须包含以下字段：
          - twist: 反转点
          - setup: 前期铺垫方式
          - fake_out: 用于误导读者或角色的策略
          - consequence: 反转发生后的剧情或情绪后果
        - 如无法生成，输出 []。
        """;

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private ManagedPromptService managedPromptService;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getName() {
        return "generateReversalIdea";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> context = new HashMap<>();
        context.put("type", "string");
        context.put("description", "当前章节的核心事件、冲突或场景描述");

        params.put("properties", Map.of("context", context));
        params.put("required", new String[]{"context"});

        return ToolDefinition.builder()
            .name(getName())
            .description("基于当前冲突或事件生成反套路创意，包括反转点、铺垫、误导和后果。")
            .parameters(params)
            .returnExample("[{\"twist\":\"...\",\"setup\":\"...\",\"fake_out\":\"...\"}]")
            .costEstimate(700)
            .required(false)
            .build();
    }

    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        String context = args.get("context") instanceof String ? (String) args.get("context") : null;
        if (StringUtils.isBlank(context)) {
            throw new IllegalArgumentException("context 不能为空");
        }

        String prompt = buildPrompt(context);
        AIConfigRequest aiConfig = args.get("__aiConfig") instanceof AIConfigRequest
            ? (AIConfigRequest) args.get("__aiConfig") : null;
        if (aiConfig == null || !aiConfig.isValid()) {
            logger.info("缺少有效 AI 配置，跳过反套路创意生成");
            return java.util.Collections.emptyList();
        }

        String response = aiWritingService.generateContent(prompt, "reversal_idea", aiConfig);
        return parseResponse(response);
    }

    private String buildPrompt(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append(resolveBasePrompt()).append("\n\n");
        sb.append("【待创作场景】\n");
        sb.append(context.trim()).append("\n\n");
        sb.append("请严格输出 JSON 数组，列出至少 3 种打破读者预期的方案，每项必须包含：twist（反转点）、setup（铺垫方式）、fake_out（误导策略）、consequence（带来的情绪/剧情后果）。\n");
        sb.append("输出格式示例（请以实际内容替换示例值）：\n");
        sb.append("[\n");
        sb.append("  {\"twist\":\"...\",\"setup\":\"...\",\"fake_out\":\"...\",\"consequence\":\"...\"},\n");
        sb.append("  {...}\n");
        sb.append("]\n");
        sb.append("务必遵守：\n");
        sb.append("- 第一个字符必须是 '['，最后一个字符必须是 ']'.\n");
        sb.append("- 所有键和值都必须使用半角双引号，不得出现中文引号.\n");
        sb.append("- 不得输出任何额外文字、注释、前缀或后缀（如“分析：”“总结：”等）。\n");
        sb.append("- 如无法生成内容，请输出 [].\n");
        return sb.toString();
    }

    private String resolveBasePrompt() {
        String prompt = managedPromptService.getContent(SystemPromptDescriptor.builder()
            .promptKey(PROMPT_KEY)
            .name("剧情反套路创意提示词")
            .category("agentic")
            .description("用于生成当前冲突场景反转创意的基础提示词")
            .sortOrder(531)
            .defaultContent(DEFAULT_PROMPT)
            .build());
        if (StringUtils.isBlank(prompt)) {
            throw new IllegalStateException("Missing prompt template: " + PROMPT_KEY);
        }
        return prompt;
    }

    private List<Map<String, Object>> parseResponse(String response) throws Exception {
        String json = response.trim();

        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        json = json.strip();

        if (!json.startsWith("[")) {
            logger.warn("反套路创意解析失败：未找到 JSON 数组起始标记");
            return java.util.Collections.emptyList();
        }

        if (!json.endsWith("]")) {
            logger.warn("检测到 JSON 可能被截断，尝试修复");
            int lastCompleteObjEnd = findLastCompleteObject(json);
            if (lastCompleteObjEnd > 0) {
                String fixedJson = json.substring(0, lastCompleteObjEnd).trim();
                if (fixedJson.endsWith(",")) {
                    fixedJson = fixedJson.substring(0, fixedJson.length() - 1).trim();
                }
                json = fixedJson + "]";
            } else {
                logger.warn("无法修复截断的 JSON");
                return java.util.Collections.emptyList();
            }
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception firstTry) {
            logger.warn("首次解析反套路创意 JSON 失败，尝试移除最后一个不完整对象");
            int lastCompleteObjEnd = findLastCompleteObject(json);
            if (lastCompleteObjEnd > 0 && lastCompleteObjEnd < json.length() - 1) {
                String fixedJson = json.substring(0, lastCompleteObjEnd) + "]";
                try {
                    return objectMapper.readValue(fixedJson, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception secondTry) {
                    logger.warn("修复后仍无法解析反套路创意 JSON", secondTry);
                }
            }
            logger.warn("反套路创意 JSON 解析失败，原始长度={}", json.length(), firstTry);
            return java.util.Collections.emptyList();
        }
    }

    private int findLastCompleteObject(String json) {
        int braceCount = 0;
        int lastCompleteEnd = -1;
        boolean inString = false;
        char escape = '\\';

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == escape && i + 1 < json.length()) {
                i++;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    lastCompleteEnd = i + 1;
                }
            }
        }

        return lastCompleteEnd;
    }
}
