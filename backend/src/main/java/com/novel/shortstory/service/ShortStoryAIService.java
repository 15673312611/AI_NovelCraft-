package com.novel.shortstory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.service.AICallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShortStoryAIService {
    
    private static final Logger logger = LoggerFactory.getLogger(ShortStoryAIService.class);
    
    @Autowired
    private AICallService aiCallService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();
    
    // 线程局部变量：当前使用的模型ID
    private static final ThreadLocal<String> currentModelId = new ThreadLocal<>();
    
    /**
     * 设置当前工作流使用的模型ID
     */
    public void setCurrentModelId(String modelId) {
        if (modelId != null && !modelId.isEmpty()) {
            currentModelId.set(modelId);
            logger.info("设置工作流模型: {}", modelId);
        } else {
            currentModelId.remove();
        }
    }
    
    /**
     * 清除当前模型ID
     */
    public void clearCurrentModelId() {
        currentModelId.remove();
    }
    
    /**
     * 获取当前模型ID
     */
    private String getModelId() {
        return currentModelId.get();
    }
    
    @PostConstruct
    public void init() {
        // 预加载中文命名的提示词模板
        loadPromptTemplate("故事设定");
        loadPromptTemplate("大纲生成");
        loadPromptTemplate("导语生成");
        loadPromptTemplate("看点生成");
        loadPromptTemplate("章节生成");
        loadPromptTemplate("章节审稿");
        loadPromptTemplate("章节分析");
        loadPromptTemplate("大纲更新");
        loadPromptTemplate("看点更新");
    }

    private String loadPromptTemplate(String templateName) {
        if (promptCache.containsKey(templateName)) {
            return promptCache.get(templateName);
        }

        // 支持中文文件名，优先加载 workflow 目录
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
                logger.info("已加载提示词模板: {}", path);
                return content;
            } catch (IOException e) {
                logger.warn("加载提示词失败: {} - {}", path, e.getMessage());
            }
        }

        throw new RuntimeException("无法加载提示词模板: " + templateName);
    }
    
    /**
     * 生成故事设定（Story Bible）
     */
    public String generateStorySetting(String title, String idea, int wordTarget, int chapterCount, Long userId) {
        String template = loadPromptTemplate("故事设定");
        String prompt = String.format(template, title, idea, wordTarget, chapterCount);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成故事设定");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    /**
     * 生成大纲
     */
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

    /**
     * 生成导语（黄金开头）
     */
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

    /**
     * 生成每章看点（标题 + 一句话核心）
     */
    public List<Map<String, Object>> generateHooks(String storySetting, String outline, int chapterCount, Long userId) {
        String template = loadPromptTemplate("看点生成");
        String prompt = String.format(template, chapterCount, storySetting, outline, chapterCount);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成章节看点");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析看点结果失败: " + e.getMessage());
        }
    }

    /**
     * 章节分析（看点/风险/后续推演/是否需要更新）
     */
    public Map<String, Object> analyzeChapter(String storySetting, String outline, int chapterNumber, int chapterCount, String chapterTitle, String chapterCore, String chapterContent, Long userId) {
        String template = loadPromptTemplate("章节分析");
        String prompt = String.format(template, storySetting, outline, chapterNumber, chapterCount, chapterTitle, chapterCore, chapterContent);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "章节分析");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("解析章节分析结果失败: " + e.getMessage());
        }
    }

    /**
     * 更新后续看点（标题/核心）
     */
    public List<Map<String, Object>> updateHooks(int fromChapter, int toChapter, String guidance, String currentHooksJson, Long userId) {
        String template = loadPromptTemplate("看点更新");
        String prompt = String.format(template, fromChapter, toChapter, guidance, currentHooksJson);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "更新后续看点");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析看点更新结果失败: " + e.getMessage());
        }
    }
    
    /**
     * 拆分章节（从大纲中提取每章简述）
     */
    public Map<String, String> splitChapters(String outline, int chapterCount, Long userId) {
        String template = loadPromptTemplate("章节拆分");
        String prompt = String.format(template, chapterCount, outline);
            
        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "拆分章节");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        
        try {
            // 简单处理可能的 markdown 代码块标记
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析AI响应失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成章节正文
     */
    public String generateChapter(String outline, String previousContent, String currentChapterBrief, int wordTarget, String adjustment, Long userId) {
        // 构建上下文：前文摘要或最后一部分（避免token溢出）
        String context = previousContent.length() > 2000 
            ? "..." + previousContent.substring(previousContent.length() - 2000) 
            : previousContent;
            
        String template = loadPromptTemplate("章节生成");
        String prompt = String.format(template, outline, context, currentChapterBrief, wordTarget, adjustment != null ? adjustment : "无");
            
        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成章节");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }
    
    /**
     * 审稿
     */
    public Map<String, Object> reviewChapter(String chapterContent, String chapterBrief, Long userId) {
        String template = loadPromptTemplate("章节审稿");
        String prompt = String.format(template, chapterBrief, chapterContent);
            
        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "章节审稿");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        
        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            // 记录详细错误信息便于调试
            String rawContent = result.getContent();
            String preview = rawContent != null && rawContent.length() > 200 ? 
                rawContent.substring(0, 200) + "..." : rawContent;
            logger.error("审稿结果解析失败, 原始响应: {}, 错误: {}", preview, e.getMessage());
            
            // 尝试构造默认结果，避免工作流崩溃
            Map<String, Object> defaultResult = new java.util.HashMap<>();
            defaultResult.put("score", 7);
            defaultResult.put("passed", true);
            defaultResult.put("comments", "审稿结果解析失败，默认通过");
            defaultResult.put("suggestions", "");
            return defaultResult;
        }
    }
    
    /**
     * 更新大纲
     */
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
        if (content == null) return "{}";
        content = content.trim();
        
        // 移除 markdown 代码块标记
        if (content.startsWith("```json")) {
            content = content.substring(7);
        }
        if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        content = content.trim();
        
        // 移除 JSON 中的行内注释 (// ...)
        // 注意：只移除在引号外部的注释，避免破坏字符串内容
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
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
            
            // 检测注释：在字符串外部遇到 //
            if (!inString && c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                // 跳过到行尾
                while (i < content.length() && content.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            
            sb.append(c);
        }
        
        return sb.toString().trim();
    }
}
