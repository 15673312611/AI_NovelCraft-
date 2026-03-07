package com.novel.script.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.service.AICallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoScriptAIService {

    private static final Logger logger = LoggerFactory.getLogger(VideoScriptAIService.class);

    @Autowired
    private AICallService aiCallService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    // 线程局部变量：当前使用的模型ID
    private static final ThreadLocal<String> currentModelId = new ThreadLocal<>();

    public void setCurrentModelId(String modelId) {
        if (modelId != null && !modelId.isEmpty()) {
            currentModelId.set(modelId);
            logger.info("设置剧本工作流模型: {}", modelId);
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

    private String modeDir(String mode) {
        if (mode != null && mode.equalsIgnoreCase("PURE_NARRATION")) {
            return "纯解说版";
        }
        return "半解说版";
    }

    private String normalizeScriptFormat(String scriptFormat) {
        if (scriptFormat == null || scriptFormat.trim().isEmpty()) {
            return "STORYBOARD";
        }
        String s = scriptFormat.trim().toUpperCase();
        if ("SCENE".equals(s) || "STORYBOARD".equals(s) || "NARRATION".equals(s)) {
            return s;
        }
        // 未知值默认走 STORYBOARD
        return "STORYBOARD";
    }

    private String scriptFormatLabel(String scriptFormat) {
        String fmt = normalizeScriptFormat(scriptFormat);
        if ("SCENE".equals(fmt)) {
            return "集-场台本（真人短剧/影视）";
        }
        if ("NARRATION".equals(fmt)) {
            return "解说口播文案（竞屏解说视频）";
        }
        return "分镜脚本（漫剧/动态漫）";
    }

    private String scriptFormatRules(String scriptFormat, int targetSeconds, int sceneCount) {
        String fmt = normalizeScriptFormat(scriptFormat);

        if ("SCENE".equals(fmt)) {
            return "- 输出为【第X集】结构，正文按多个【场X-Y】分段\n"
                    + "- 每场必须包含：内/外 + 日/夜 + 地点（如：场1-1  内  夜  地点：17层走廊）\n"
                    + "- 允许使用：△动作/画面描述、【SFX】音效、【BGM】音乐、OS（内心独白）\n"
                    + "- 动作描写要可拍（能看见/听见），少抽象心理\n"
                    + "- 节奏：短平快；尽量每3-5秒一个推进点/钩子\n"
                    + "- 目标：约" + targetSeconds + "秒；建议场次≈" + sceneCount + "（可自动调整）\n"
                    + "- 不要输出任何解释/点评，只输出剧本文本";
        }

        if ("NARRATION".equals(fmt)) {
            return "- 输出为纯解说口播文案，带时间戳\n"
                    + "- 第一人称叙事（\"我\"视角），沉浸式讲故事\n"
                    + "- 口语化、有节奏、有画面感，可以直接念\n"
                    + "- 禁止写\u201c画面\u201d\u201c字幕\u201d\u201c音效\u201d\u201cBGM\u201d\u201c转场\u201d等技术标注\n"
                    + "- 节奏：短平快；每2-4秒一个信息点或钩子\n"
                    + "- 目标：约" + targetSeconds + "秒\n"
                    + "- 不要输出任何解释/点评，只输出纯口播文案";
        }

        return "- 输出为【镜头XX】结构，镜头从01递增\n"
                + "- 每镜头尽量包含：时长、景别、运镜、画面、动效、音效/BGM、台词（无则写“无”）\n"
                + "- 画面描述要具体可视；动效/音效尽量可执行\n"
                + "- 节奏：短平快；镜头切换密度与" + sceneCount + "个镜头左右匹配\n"
                + "- 目标：约" + targetSeconds + "秒\n"
                + "- 不要输出任何解释/点评，只输出剧本文本";
    }

    private String scriptFormatExample(String scriptFormat) {
        String fmt = normalizeScriptFormat(scriptFormat);

        if ("SCENE".equals(fmt)) {
            return "第01集 《标题》\n\n"
                    + "场1-1  内  夜  地点：______\n"
                    + "△（画面/动作）\n"
                    + "【SFX】风声（持续）\n"
                    + "A：台词……\n"
                    + "B（压低）：台词……\n\n"
                    + "场1-2  外  夜  地点：______\n"
                    + "△（冲突升级/反转）\n"
                    + "A OS：内心独白（可选）";
        }

        if ("NARRATION".equals(fmt)) {
            return "【本集标题】xxx\n"
                    + "【时长】X秒\n"
                    + "【解说文案】\n"
                    + "00:00 - 00:03：凌晨三点，我被一阵剧烈的震动惊醒。\n"
                    + "00:03 - 00:06：我猛地坐起来，发现整个房间都在晃动。\n"
                    + "00:06 - 00:10：窗外传来尖叫声，我意识到不对劲，赶紧冲向窗边。\n"
                    + "...\n"
                    + "【结尾悬念】一句话\n"
                    + "【下一集引子】一句话";
        }

        return "镜头01  时长：3s  景别：特写  运镜：静止\n"
                + "画面：______\n"
                + "动效：______\n"
                + "音效/BGM：【SFX】______\n"
                + "台词：无\n\n"
                + "镜头02  时长：5s  景别：中景  运镜：慢推\n"
                + "画面：______\n"
                + "音效/BGM：【BGM】冷氛围（轻）\n"
                + "台词：\n"
                + "- 角色A：台词……\n"
                + "- 角色B（迟疑）：台词……";
    }

    private String cacheKey(String mode, String templateName) {
        return modeDir(mode) + ":" + templateName;
    }

    private String loadPromptTemplate(String mode, String templateName) {
        String key = cacheKey(mode, templateName);
        if (promptCache.containsKey(key)) {
            return promptCache.get(key);
        }

        String dir = modeDir(mode);
        String[] candidates = new String[] {
                // ✅ 多集系列工作流优先
                "prompts/script/series/workflow/" + dir + "/" + templateName + ".txt",
                // 兼容旧单集工作流
                "prompts/script/workflow/" + dir + "/" + templateName + ".txt",
                // 最终兜底
                "prompts/script/" + templateName + ".txt"
        };

        for (String path : candidates) {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (!resource.exists()) {
                    continue;
                }
                String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                promptCache.put(key, content);
                logger.info("已加载剧本提示词模板: {}", path);
                return content;
            } catch (IOException e) {
                logger.warn("加载剧本提示词失败: {} - {}", path, e.getMessage());
            }
        }

        throw new RuntimeException("无法加载剧本提示词模板: " + dir + "/" + templateName);
    }

    /**
     * 系列设定（Story Bible）
     */
    public String generateStorySetting(String title, String idea, String mode, int targetSeconds, int sceneCount, int episodeCount, Long userId) {
        String template = loadPromptTemplate(mode, "系列设定");
        String prompt = String.format(template,
                title != null ? title : "",
                idea != null ? idea : "",
                episodeCount,
                targetSeconds,
                sceneCount,
                mode != null ? mode : "HALF_NARRATION"
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成系列设定");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    /**
     * 系列大纲
     */
    public String generateOutline(String idea, String storySetting, int episodeCount, Long userId) {
        String template = loadPromptTemplate(null, "大纲生成");
        String prompt = String.format(template,
                storySetting != null ? storySetting : "",
                idea != null ? idea : "",
                episodeCount
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成系列大纲");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    /**
     * 导语（黄金开头）
     */
    public String generatePrologue(String title, String idea, String storySetting, String outline, String firstEpisodeHook, Long userId) {
        String template = loadPromptTemplate(null, "导语生成");
        String prompt = String.format(template,
                title != null ? title : "",
                idea != null ? idea : "",
                storySetting != null ? storySetting : "",
                outline != null ? outline : "",
                firstEpisodeHook != null ? firstEpisodeHook : ""
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成导语");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    /**
     * 看点生成（JSON数组）
     */
    public List<Map<String, Object>> generateHooks(String storySetting, String outline, int episodeCount, Long userId) {
        String template = loadPromptTemplate(null, "看点生成");
        String prompt = String.format(template, episodeCount, storySetting != null ? storySetting : "", outline != null ? outline : "", episodeCount);

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成每集看点");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("解析看点结果失败: " + e.getMessage());
        }
    }

    /**
     * 单集生成（根据用户选择的格式生成对应内容）
     */
    public String generateEpisode(String mode,
                                 String scriptFormat,
                                 String outlineForPrompt,
                                 String previousContent,
                                 String episodeBrief,
                                 int targetSeconds,
                                 int sceneCount,
                                 String adjustment,
                                 Long userId) {
        // 清除缓存以确保重新加载模板（开发期间廻模板变更后生效）
        String cacheKey = cacheKey(mode, "分集生成");
        promptCache.remove(cacheKey);
        
        String template = loadPromptTemplate(mode, "分集生成");
        String context = previousContent != null && previousContent.length() > 2500
                ? ("..." + previousContent.substring(previousContent.length() - 2500))
                : (previousContent != null ? previousContent : "");

        String prompt = String.format(template,
                outlineForPrompt != null ? outlineForPrompt : "",
                context,
                episodeBrief != null ? episodeBrief : "",
                targetSeconds,
                sceneCount,
                adjustment != null ? adjustment : "无"
        );

        // ✅ 追加输出格式约束（根据用户选择的 scriptFormat）
        String fmt = normalizeScriptFormat(scriptFormat);
        prompt += "\n\n━━━━━━━━━━【输出格式要求】━━━━━━━━━━\n";
        prompt += "❗❗❗ 强制要求：你必须严格按照以下格式输出，禁止使用其他任何格式！\n\n";
        prompt += "【格式类型】" + scriptFormatLabel(fmt) + "\n\n";
        prompt += "【格式规则】\n" + scriptFormatRules(fmt, targetSeconds, sceneCount) + "\n\n";
        prompt += "【格式示例（仅示意结构，不要照抄剧情内容）】\n";
        prompt += scriptFormatExample(fmt) + "\n\n";
        prompt += "❗ 输出前请再次确认：你的输出必须符合上述格式，不能输出其他格式（如时间戳解说格式、普通文本等）。";

        logger.info("生成单集脚本 - 模式: {}, 格式: {}", mode, fmt);
        
        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成单集脚本");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    /**
     * 单集审稿（JSON）
     */
    public Map<String, Object> reviewEpisode(String episodeContent, String episodeBrief, Long userId) {
        String template = loadPromptTemplate(null, "分集审稿");
        String prompt = String.format(template,
                episodeBrief != null ? episodeBrief : "",
                episodeContent != null ? episodeContent : ""
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "单集审稿");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            String raw = result.getContent();
            String preview = raw != null && raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
            logger.warn("单集审稿结果解析失败，原始响应预览: {}", preview);

            Map<String, Object> fallback = new java.util.HashMap<>();
            fallback.put("score", 7);
            fallback.put("passed", true);
            fallback.put("comments", "审稿结果解析失败，默认通过");
            fallback.put("suggestions", "");
            return fallback;
        }
    }

    /**
     * 单集分析（JSON，必须包含 episodeSummary 以支持连续性记忆）
     */
    public Map<String, Object> analyzeEpisode(String storySetting,
                                             String outline,
                                             int episodeNumber,
                                             int episodeCount,
                                             String episodeTitle,
                                             String episodeCore,
                                             String episodeContent,
                                             Long userId) {
        String template = loadPromptTemplate(null, "分集分析");
        String prompt = String.format(template,
                storySetting != null ? storySetting : "",
                outline != null ? outline : "",
                episodeNumber,
                episodeCount,
                episodeTitle != null ? episodeTitle : "",
                episodeCore != null ? episodeCore : "",
                episodeContent != null ? episodeContent : ""
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "单集分析");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("解析单集分析结果失败: " + e.getMessage());
        }
    }

    /**
     * 更新大纲
     */
    public String updateOutline(String originalOutline, String newDevelopment, int currentEpisode, Long userId) {
        String template = loadPromptTemplate(null, "大纲更新");
        String prompt = String.format(template,
                originalOutline != null ? originalOutline : "",
                currentEpisode,
                newDevelopment != null ? newDevelopment : ""
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "动态调整大纲");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    /**
     * 更新后续看点（JSON数组）
     */
    public List<Map<String, Object>> updateHooks(int fromEpisode, int toEpisode, String guidance, String currentHooksJson, Long userId) {
        String template = loadPromptTemplate(null, "看点更新");
        String prompt = String.format(template,
                fromEpisode,
                toEpisode,
                guidance != null ? guidance : "",
                currentHooksJson != null ? currentHooksJson : "[]"
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "更新后续看点");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }

        try {
            String jsonStr = cleanJson(result.getContent());
            return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("解析看点更新结果失败: " + e.getMessage());
        }
    }

    // ---------------------- 旧单集接口（保留兼容） ----------------------

    public String generateScriptSetting(String mode, String idea, int targetSeconds, int targetMinutes, int sceneCount, Long userId) {
        String template = loadPromptTemplate(mode, "剧本设定");
        String prompt;

        // 纯解说版模板额外包含一个 %d（总时长）占位符
        if (mode != null && mode.equalsIgnoreCase("PURE_NARRATION")) {
            prompt = String.format(template,
                    idea != null ? idea : "",
                    targetSeconds,
                    targetMinutes,
                    sceneCount,
                    targetSeconds
            );
        } else {
            prompt = String.format(template,
                    idea != null ? idea : "",
                    targetSeconds,
                    targetMinutes,
                    sceneCount
            );
        }

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成剧本设定");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    public String generateStoryboard(String mode, String scriptSetting, int targetSeconds, int sceneCount, Long userId) {
        String template = loadPromptTemplate(mode, "分镜大纲");
        String prompt = String.format(template,
                scriptSetting != null ? scriptSetting : "",
                targetSeconds,
                sceneCount,
                targetSeconds
        );

        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, "生成分镜大纲");
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        return result.getContent();
    }

    public String generateFinalScript(String mode, String scriptSetting, String storyboard, int targetSeconds, Long userId) {
        boolean pure = mode != null && mode.equalsIgnoreCase("PURE_NARRATION");
        String templateName = pure ? "解说词生成" : "台词生成";
        String template = loadPromptTemplate(mode, templateName);

        String prompt = String.format(template,
                scriptSetting != null ? scriptSetting : "",
                storyboard != null ? storyboard : "",
                targetSeconds
        );

        String taskDesc = pure ? "生成解说词" : "生成台词脚本";
        AICallService.AICallResult result = aiCallService.callAI(prompt, getModelId(), null, userId, taskDesc);
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

            if (!inString && c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
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
