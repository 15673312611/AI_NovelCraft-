package com.novel.agentic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class PromptAssembler {

    private static final Logger logger = LoggerFactory.getLogger(PromptAssembler.class);
    private static final String CONFIG_PATH = "classpath:prompts_output/promptModules.json";

    private final PromptTemplateService templateService;
    private final com.novel.service.PromptTemplateService dbTemplateService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private JsonNode configRoot;

    @Autowired
    public PromptAssembler(PromptTemplateService templateService,
                           com.novel.service.PromptTemplateService dbTemplateService,
                           ObjectMapper objectMapper,
                           ResourceLoader resourceLoader) {
        this.templateService = templateService;
        this.dbTemplateService = dbTemplateService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadConfig() {
        try {
            Resource resource = resourceLoader.getResource(CONFIG_PATH);
            if (!resource.exists()) {
                throw new IllegalStateException("未找到提示词模块配置: " + CONFIG_PATH);
            }
            byte[] bytes = resource.getInputStream().readAllBytes();
            configRoot = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("加载提示词模块配置失败", e);
        }
    }

    public String assembleSystemPrompt(String genre, Integer chapterNumber, String stylePromptFile, Long promptTemplateId) {
        // 优先使用数据库模板
        if (promptTemplateId != null) {
            String dbContent = dbTemplateService.getTemplateContent(promptTemplateId);
            if (StringUtils.isNotBlank(dbContent)) {
                logger.info("使用数据库提示词模板: templateId={}", promptTemplateId);
                return dbContent;
            }
            logger.warn("数据库模板内容为空，回退到文件模板: templateId={}", promptTemplateId);
        }
        
        String base = assembleBaseRules(stylePromptFile);
        String style = assembleStylePrompt(stylePromptFile);

        if (StringUtils.isBlank(base)) {
            return style;
        }
        if (StringUtils.isBlank(style)) {
            return base;
        }
        return base + "\n\n" + style;
    }

    public String assembleBaseRules(String stylePromptFile) {
        if (StringUtils.isNotBlank(stylePromptFile)) {
            // 使用传入的文件名
            try {
                return templateService.loadTemplate(stylePromptFile);
            } catch (Exception e) {
                logger.warn("加载指定提示词文件失败: {}, 使用默认配置", stylePromptFile, e);
                return joinModules(configRoot.path("baseRules"));
            }
        }
        return joinModules(configRoot.path("baseRules"));
    }

    public String getInnovationChecklistSummary() {
        return extractSummary(configRoot.path("innovationChecklist"));
    }

    public String getAntiClicheSummary() {
        return extractSummary(configRoot.path("antiCliche"));
    }

    private String extractSummary(JsonNode node) {
        if (node == null || !node.has("source")) {
            return "";
        }
        String file = node.path("source").asText("");
        if (StringUtils.isBlank(file)) {
            return "";
        }
        int maxLines = node.path("maxLines").asInt(6);
        try {
            String content = templateService.loadTemplate(file);
            String[] lines = content.split("\r?\n");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                String trimmed = line.strip();
                if (StringUtils.isBlank(trimmed)) {
                    continue;
                }
                sb.append("- ").append(trimmed).append("\n");
                count++;
                if (count >= maxLines) {
                    break;
                }
            }
            return sb.toString().strip();
        } catch (Exception e) {
            logger.warn("读取提示词摘要失败: {}", file, e);
            return "";
        }
    }

    private String joinModules(JsonNode node) {
        List<String> modules = new ArrayList<>();
        collectModules(node, modules);
        return String.join("\n\n", modules);
    }

    private void collectModules(JsonNode node, List<String> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectModules(item, out);
            }
            return;
        }
        if (node.isTextual()) {
            addModuleContent(node.asText(), 0, 0, out);
            return;
        }
        if (isModuleDescriptor(node)) {
            String file = node.path("file").asText("");
            int maxLines = node.path("maxLines").asInt(0);
            int maxChars = node.path("maxChars").asInt(0);
            addModuleContent(file, maxLines, maxChars, out);
            return;
        }
        if (node.isObject()) {
            // treat as keyed collection (e.g. genre overrides)
            for (JsonNode value : node) {
                collectModules(value, out);
            }
        }
    }

    private boolean isModuleDescriptor(JsonNode node) {
        return node != null && node.isObject() && (node.has("file") || node.has("content"));
    }

    private void addModuleContent(String fileName, int maxLines, int maxChars, List<String> out) {
        if (StringUtils.isBlank(fileName)) {
            return;
        }
        try {
            String content = templateService.loadTemplate(fileName);
            out.add(trimContent(content, maxLines, maxChars));
        } catch (Exception e) {
            logger.warn("加载提示词模块失败: {}", fileName, e);
        }
    }

    private String trimContent(String content, int maxLines, int maxChars) {
        if (content == null) {
            return "";
        }
        String result = content;
        if (maxLines > 0) {
            String[] lines = result.split("\r?\n");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                sb.append(line).append("\n");
                count++;
                if (count >= maxLines) {
                    sb.append("...（已截取前").append(count).append("行）");
                    break;
                }
            }
            result = sb.toString().strip();
        }
        if (maxChars > 0 && result.length() > maxChars) {
            result = result.substring(0, maxChars) + "...（已截断）";
        }
        return result;
    }

    private String assembleStylePrompt(String stylePromptFile) {
        if (StringUtils.isNotBlank(stylePromptFile)) {
            // 使用传入的文件名（已在 assembleBaseRules 中加载，这里返回空避免重复）
            return "";
        }
        JsonNode styleNode = configRoot.path("stylePrompt");
        if (styleNode == null || styleNode.isMissingNode()) {
            return "";
        }
        return joinModules(styleNode);
    }

}

