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
public class GenerateReversalIdeaTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReversalIdeaTool.class);
    private static final String PROMPT_FILE = "剧情规划拆解_-_拆仿系列2.txt";

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
        return "generateReversalIdea";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> context = new HashMap<>();
        context.put("type", "string");
        context.put("description", "当前章节核心事件或冲突描述");

        params.put("properties", Map.of("context", context));
        params.put("required", new String[]{"context"});

        return ToolDefinition.builder()
            .name(getName())
            .description("基于当前冲突/事件生成反套路创意：反转点、误导、预期破坏方式等，帮助章节打破读者预期。")
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
            logger.info("⏭️ 缺少有效AI配置，跳过反套路创意生成");
            return java.util.Collections.emptyList();
        }

        String response = aiWritingService.generateContent(prompt, "reversal_idea", aiConfig);
        return parseResponse(response);
    }

    private String buildPrompt(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append(basePrompt).append("\n\n");
        sb.append("【待创作场景】\n");
        sb.append(context).append("\n\n");
        sb.append("请严格输出 JSON 数组，列出至少3种打破读者预期的方案，每项必须包含：twist（反转点）、setup（铺垫方式）、fake_out（误导策略）、consequence（带来的情绪/剧情后果）。\n");
        sb.append("输出格式示例（请以实际内容替换示例值）：\n");
        sb.append("[").append("\n");
        sb.append("  {\"twist\":\"...\",\"setup\":\"...\",\"fake_out\":\"...\",\"consequence\":\"...\"},\n");
        sb.append("  {...}" ).append("\n");
        sb.append("]\n");
        sb.append("务必遵守：\n");
        sb.append("- 第一个字符必须是 '['，最后一个字符必须是 ']'。\n");
        sb.append("- 所有键和值都必须使用半角双引号，不得出现中文引号。\n");
        sb.append("- 不得输出任何额外文字、注释、前缀或后缀（如“分析：”“总结：”等）。\n");
        sb.append("- 如无法生成内容，请输出 []。\n");
        return sb.toString();
    }

    private List<Map<String, Object>> parseResponse(String response) throws Exception {
        String json = response.trim();
        
        // 提取JSON数组部分
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        json = json.strip();
        
        if (!json.startsWith("[")) {
            logger.warn("反套路创意解析失败：未找到JSON数组起始标记，返回空列表");
            return java.util.Collections.emptyList();
        }
        
        // 如果JSON被截断（不以]结尾），尝试修复
        if (!json.endsWith("]")) {
            logger.warn("检测到JSON可能被截断，尝试修复...");
            // 尝试找到最后一个完整的对象
            int lastCompleteObjEnd = findLastCompleteObject(json);
            if (lastCompleteObjEnd > 0) {
                String fixedJson = json.substring(0, lastCompleteObjEnd);
                // 移除末尾可能的多余逗号
                fixedJson = fixedJson.trim();
                if (fixedJson.endsWith(",")) {
                    fixedJson = fixedJson.substring(0, fixedJson.length() - 1).trim();
                }
                json = fixedJson + "]";
                logger.info("已修复截断的JSON，保留前{}个字符", json.length());
            } else {
                logger.warn("无法修复截断的JSON，返回空列表");
                return java.util.Collections.emptyList();
            }
        }
        
        // 尝试解析，如果失败，尝试移除不完整的最后一个对象
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception firstTry) {
            // 如果仍然失败，尝试移除最后一个可能不完整的对象
            logger.warn("首次解析失败，尝试移除最后一个可能不完整的对象...");
            int lastCompleteObjEnd = findLastCompleteObject(json);
            if (lastCompleteObjEnd > 0 && lastCompleteObjEnd < json.length() - 1) {
                // 移除最后一个不完整的对象
                String fixedJson = json.substring(0, lastCompleteObjEnd) + "]";
                try {
                    return objectMapper.readValue(fixedJson, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception secondTry) {
                    logger.warn("修复后仍无法解析JSON", secondTry);
                }
            }
            logger.warn("反套路创意解析JSON失败，原始内容长度: {}, 前500字符: {}", 
                json.length(), 
                json.length() > 500 ? json.substring(0, 500) : json, 
                firstTry);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 找到最后一个完整的JSON对象结束位置
     * 通过匹配大括号来找到完整的对象
     */
    private int findLastCompleteObject(String json) {
        int braceCount = 0;
        int lastCompleteEnd = -1;
        boolean inString = false;
        char escape = '\\';
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            // 处理转义字符
            if (c == escape && i + 1 < json.length()) {
                i++; // 跳过转义的下一个字符
                continue;
            }
            
            // 处理字符串边界
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            // 统计大括号
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    // 找到了一个完整的对象
                    lastCompleteEnd = i + 1;
                }
            }
        }
        
        return lastCompleteEnd;
    }
}


