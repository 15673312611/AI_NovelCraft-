package com.novel.shortstory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.service.AICallService;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShortStoryAIService {

    private static final Logger logger = LoggerFactory.getLogger(ShortStoryAIService.class);
    private static final Map<String, PromptSpec> PROMPT_SPECS = buildPromptSpecs();

    @Autowired
    private AICallService aiCallService;

    @Autowired
    private ManagedPromptService managedPromptService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    private static final ThreadLocal<String> currentModelId = new ThreadLocal<>();

    public void setCurrentModelId(String modelId) {
        if (modelId != null && !modelId.isEmpty()) {
            currentModelId.set(modelId);
            logger.info("设置短篇工作流模型: {}", modelId);
        } else {
            currentModelId.remove();
        }
    }

    public void clearCurrentModelId() {
        currentModelId.remove();
    }

    private String getModelId() {
        return currentModelId.get();
    }

    @PostConstruct
    public void init() {
        for (String templateName : PROMPT_SPECS.keySet()) {
            loadPromptTemplate(templateName);
        }
    }

    private String loadPromptTemplate(String templateName) {
        PromptSpec spec = PROMPT_SPECS.getOrDefault(templateName, buildFallbackSpec(templateName));
        String defaultContent = loadPromptTemplateFromResource(templateName);

        return managedPromptService.getContent(SystemPromptDescriptor.builder()
                .promptKey(spec.promptKey)
                .name(spec.name)
                .category(spec.category)
                .description(spec.description)
                .variables(spec.variables)
                .sortOrder(spec.sortOrder)
                .defaultContent(defaultContent)
                .build());
    }

    private String loadPromptTemplateFromResource(String templateName) {
        if (promptCache.containsKey(templateName)) {
            return promptCache.get(templateName);
        }

        String[] candidates = new String[] {
                "prompts/shortstory/workflow/" + templateName + ".txt",
                "prompts/shortstory/" + templateName + ".txt"
        };

        for (String path : candidates) {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (!resource.exists()) {
                    continue;
                }

                String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                promptCache.put(templateName, content);
                logger.info("已加载短篇提示词模板: {}", path);
                return content;
            } catch (IOException e) {
                logger.warn("加载短篇提示词失败: {} - {}", path, e.getMessage());
            }
        }

        throw new RuntimeException("无法加载短篇提示词模板: " + templateName);
    }

    public String generateStorySetting(String title, String idea, int wordTarget, int chapterCount, Long userId) {
        String template = loadPromptTemplate("故事设定");
        String prompt = String.format(template, title, idea, wordTarget, chapterCount);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成故事设定");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    public String generateOutline(String idea, int wordTarget, int chapterCount, Long userId) {
        String template = loadPromptTemplate("大纲生成");
        int wordsPerChapter = chapterCount > 0 ? wordTarget / chapterCount : 2000;
        String prompt = String.format(template, idea, wordTarget, chapterCount, wordsPerChapter);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成大纲");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    public String generatePrologue(String title, String idea, String storySetting, String outline, String firstChapterHook, Long userId) {
        String template = loadPromptTemplate("导语生成");
        String prompt = String.format(template,
                title != null ? title : "",
                idea != null ? idea : "",
                storySetting != null ? storySetting : "",
                outline != null ? outline : "",
                firstChapterHook != null ? firstChapterHook : ""
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成导语");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    public List<Map<String, Object>> generateHooks(String storySetting, String outline, int chapterCount, Long userId) {
        String template = loadPromptTemplate("看点生成");
        String prompt = String.format(template, chapterCount, storySetting, outline, chapterCount);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成章节看点");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            return objectMapper.readValue(cleanJson(result.getContent()), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析看点结果失败: " + e.getMessage());
        }
    }

    public Map<String, Object> analyzeChapter(String storySetting,
                                              String outline,
                                              int chapterNumber,
                                              int chapterCount,
                                              String chapterTitle,
                                              String chapterCore,
                                              String chapterContent,
                                              Long userId) {
        String template = loadPromptTemplate("章节分析");
        String prompt = String.format(template, storySetting, outline, chapterNumber, chapterCount, chapterTitle, chapterCore, chapterContent);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "章节分析");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            return objectMapper.readValue(cleanJson(result.getContent()), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析章节分析结果失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> updateHooks(int fromChapter, int toChapter, String guidance, String currentHooksJson, Long userId) {
        String template = loadPromptTemplate("看点更新");
        String prompt = String.format(template, fromChapter, toChapter, guidance, currentHooksJson);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "更新后续看点");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            return objectMapper.readValue(cleanJson(result.getContent()), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析看点更新结果失败: " + e.getMessage());
        }
    }

    public Map<String, String> splitChapters(String outline, int chapterCount, Long userId) {
        String template = loadPromptTemplate("章节拆分");
        String prompt = String.format(template, chapterCount, outline);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "拆分章节");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            return objectMapper.readValue(cleanJson(result.getContent()), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析拆分章节结果失败: " + e.getMessage());
        }
    }

    public String generateChapter(String outline,
                                  String previousContent,
                                  String currentChapterBrief,
                                  int wordTarget,
                                  String adjustment,
                                  Long userId) {
        String previous = previousContent != null ? previousContent : "";
        String context = previous.length() > 2000 ? "..." + previous.substring(previous.length() - 2000) : previous;

        String template = loadPromptTemplate("章节生成");
        String prompt = String.format(template, outline, context, currentChapterBrief, wordTarget, adjustment != null ? adjustment : "无");

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成章节");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    public Map<String, Object> reviewChapter(String chapterContent, String chapterBrief, Long userId) {
        String template = loadPromptTemplate("章节审稿");
        String prompt = String.format(template, chapterBrief, chapterContent);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "章节审稿");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            return objectMapper.readValue(cleanJson(result.getContent()), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            String rawContent = result.getContent();
            String preview = rawContent != null && rawContent.length() > 200 ? rawContent.substring(0, 200) + "..." : rawContent;
            logger.error("章节审稿结果解析失败, 原始响应: {}, 错误: {}", preview, e.getMessage());

            Map<String, Object> defaultResult = new java.util.HashMap<>();
            defaultResult.put("score", 7);
            defaultResult.put("passed", true);
            defaultResult.put("comments", "审稿结果解析失败，默认通过");
            defaultResult.put("suggestions", "");
            return defaultResult;
        }
    }

    public String updateOutline(String originalOutline, String newDevelopment, int currentChapter, Long userId) {
        String template = loadPromptTemplate("大纲更新");
        String prompt = String.format(template, originalOutline, currentChapter, newDevelopment);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "动态调整大纲");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    private String cleanJson(String content) {
        if (content == null) {
            return "{}";
        }

        String normalized = content.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7);
        }
        if (normalized.startsWith("```")) {
            normalized = normalized.substring(3);
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        normalized = normalized.trim();

        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (!inString && c == '/' && i + 1 < normalized.length() && normalized.charAt(i + 1) == '/') {
                while (i < normalized.length() && normalized.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            sb.append(c);
        }

        return sb.toString().trim();
    }

    private static Map<String, PromptSpec> buildPromptSpecs() {
        Map<String, PromptSpec> specs = new LinkedHashMap<>();
        specs.put("故事设定", new PromptSpec(
                "short_story.story_setting",
                "短篇故事设定",
                "short_story",
                "生成短篇小说故事设定",
                Arrays.asList("title", "idea", "word_target", "chapter_count"),
                610
        ));
        specs.put("大纲生成", new PromptSpec(
                "short_story.outline_generation",
                "短篇大纲生成",
                "short_story",
                "生成短篇小说大纲",
                Arrays.asList("idea", "word_target", "chapter_count", "words_per_chapter"),
                611
        ));
        specs.put("导语生成", new PromptSpec(
                "short_story.prologue_generation",
                "短篇导语生成",
                "short_story",
                "生成短篇导语",
                Arrays.asList("title", "idea", "story_setting", "outline", "first_chapter_hook"),
                613
        ));
        specs.put("看点生成", new PromptSpec(
                "short_story.hook_generation",
                "短篇看点生成",
                "short_story",
                "生成短篇章节看点",
                Arrays.asList("chapter_count", "story_setting", "outline"),
                612
        ));
        specs.put("章节生成", new PromptSpec(
                "short_story.chapter_generation",
                "短篇章节生成",
                "short_story",
                "生成短篇章节内容",
                Arrays.asList("outline", "previous_context", "current_chapter_brief", "word_target", "adjustment"),
                614
        ));
        specs.put("章节审稿", new PromptSpec(
                "short_story.chapter_review",
                "短篇章节审稿",
                "short_story",
                "审稿短篇章节内容",
                Arrays.asList("chapter_brief", "chapter_content"),
                615
        ));
        specs.put("章节分析", new PromptSpec(
                "short_story.chapter_analysis",
                "短篇章节分析",
                "short_story",
                "分析短篇章节内容",
                Arrays.asList("story_setting", "outline", "chapter_number", "chapter_count", "chapter_title", "chapter_core", "chapter_content"),
                616
        ));
        specs.put("大纲更新", new PromptSpec(
                "short_story.outline_update",
                "短篇大纲更新",
                "short_story",
                "根据发展更新短篇大纲",
                Arrays.asList("original_outline", "current_chapter", "new_development"),
                617
        ));
        specs.put("看点更新", new PromptSpec(
                "short_story.hook_update",
                "短篇看点更新",
                "short_story",
                "更新短篇后续看点",
                Arrays.asList("from_chapter", "to_chapter", "guidance", "current_hooks_json"),
                618
        ));
        specs.put("章节拆分", new PromptSpec(
                "short_story.chapter_split",
                "短篇章节拆分",
                "short_story",
                "将短篇大纲拆分为章节概要",
                Arrays.asList("chapter_count", "outline"),
                619
        ));
        return specs;
    }

    private static PromptSpec buildFallbackSpec(String templateName) {
        return new PromptSpec(
                "short_story.other." + Integer.toHexString(templateName.hashCode()),
                "短篇提示词：" + templateName,
                "short_story",
                "短篇工作流提示词",
                null,
                699
        );
    }

    private static class PromptSpec {
        private final String promptKey;
        private final String name;
        private final String category;
        private final String description;
        private final List<String> variables;
        private final Integer sortOrder;

        private PromptSpec(String promptKey,
                           String name,
                           String category,
                           String description,
                           List<String> variables,
                           Integer sortOrder) {
            this.promptKey = promptKey;
            this.name = name;
            this.category = category;
            this.description = description;
            this.variables = variables;
            this.sortOrder = sortOrder;
        }
    }
}
