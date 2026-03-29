package com.novel.script.service;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoScriptAIService {

    private static final Logger logger = LoggerFactory.getLogger(VideoScriptAIService.class);
    private static final Map<String, PromptSpec> COMMON_PROMPT_SPECS = buildCommonPromptSpecs();
    private static final Map<String, PromptSpec> HALF_NARRATION_PROMPT_SPECS = buildHalfNarrationPromptSpecs();
    private static final Map<String, PromptSpec> PURE_NARRATION_PROMPT_SPECS = buildPureNarrationPromptSpecs();

    @Autowired
    private AICallService aiCallService;

    @Autowired
    private ManagedPromptService managedPromptService;

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

    private String renderOutputFormatPrompt(String scriptFormat, int targetSeconds, int sceneCount) {
        String fmt = normalizeScriptFormat(scriptFormat);
        SystemPromptDescriptor descriptor;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("target_seconds", targetSeconds);
        values.put("scene_count", sceneCount);

        if ("SCENE".equals(fmt)) {
            descriptor = SystemPromptDescriptor.builder()
                    .promptKey("script.output_format.scene")
                    .name("剧本输出格式约束-集场台本")
                    .category("script")
                    .description("约束剧本工作流输出集-场台本格式，适用于真人短剧或影视拍摄台本")
                    .variables(Arrays.asList("target_seconds", "scene_count"))
                    .sortOrder(728)
                    .defaultContent("""
                            ━━━━━━━━━━【输出格式要求】━━━━━━━━━━
                            ❗❗❗ 强制要求：你必须严格按照以下格式输出，禁止使用其他任何格式！
                            
                            【格式类型】集-场台本（真人短剧/影视）
                            
                            【格式规则】
                            - 输出为【第X集】结构，正文按多个【场X-Y】分段
                            - 每场必须包含：内/外 + 日/夜 + 地点（如：场1-1  内  夜  地点：17层走廊）
                            - 允许使用：△动作/画面描述、【SFX】音效、【BGM】音乐、OS（内心独白）
                            - 动作描写要可拍（能看见/听见），少抽象心理
                            - 节奏：短平快；尽量每3-5秒一个推进点/钩子
                            - 目标：约{{target_seconds}}秒；建议场次≈{{scene_count}}（可自动调整）
                            - 不要输出任何解释/点评，只输出剧本文本
                            
                            【格式示例（仅示意结构，不要照抄剧情内容）】
                            第01集 《标题》
                            
                            场1-1  内  夜  地点：______
                            △（画面/动作）
                            【SFX】风声（持续）
                            A：台词……
                            B（压低）：台词……
                            
                            场1-2  外  夜  地点：______
                            △（冲突升级/反转）
                            A OS：内心独白（可选）
                            
                            ❗ 输出前请再次确认：你的输出必须符合上述格式，不能输出其他格式（如时间戳解说格式、普通文本等）。
                            """)
                    .build();
        } else if ("NARRATION".equals(fmt)) {
            descriptor = SystemPromptDescriptor.builder()
                    .promptKey("script.output_format.narration")
                    .name("剧本输出格式约束-解说口播")
                    .category("script")
                    .description("约束剧本工作流输出纯解说口播格式，适用于竞屏解说视频")
                    .variables(Arrays.asList("target_seconds"))
                    .sortOrder(729)
                    .defaultContent("""
                            ━━━━━━━━━━【输出格式要求】━━━━━━━━━━
                            ❗❗❗ 强制要求：你必须严格按照以下格式输出，禁止使用其他任何格式！
                            
                            【格式类型】解说口播文案（竞屏解说视频）
                            
                            【格式规则】
                            - 输出为纯解说口播文案，带时间戳
                            - 第一人称叙事（"我"视角），沉浸式讲故事
                            - 口语化、有节奏、有画面感，可以直接念
                            - 禁止写“画面”“字幕”“音效”“BGM”“转场”等技术标注
                            - 节奏：短平快；每2-4秒一个信息点或钩子
                            - 目标：约{{target_seconds}}秒
                            - 不要输出任何解释/点评，只输出纯口播文案
                            
                            【格式示例（仅示意结构，不要照抄剧情内容）】
                            【本集标题】xxx
                            【时长】X秒
                            【解说文案】
                            00:00 - 00:03：凌晨三点，我被一阵剧烈的震动惊醒。
                            00:03 - 00:06：我猛地坐起来，发现整个房间都在晃动。
                            00:06 - 00:10：窗外传来尖叫声，我意识到不对劲，赶紧冲向窗边。
                            ...
                            【结尾悬念】一句话
                            【下一集引子】一句话
                            
                            ❗ 输出前请再次确认：你的输出必须符合上述格式，不能输出其他格式（如时间戳解说格式、普通文本等）。
                            """)
                    .build();
        } else {
            descriptor = SystemPromptDescriptor.builder()
                    .promptKey("script.output_format.storyboard")
                    .name("剧本输出格式约束-分镜脚本")
                    .category("script")
                    .description("约束剧本工作流输出分镜脚本格式，适用于漫剧或动态漫")
                    .variables(Arrays.asList("target_seconds", "scene_count"))
                    .sortOrder(727)
                    .defaultContent("""
                            ━━━━━━━━━━【输出格式要求】━━━━━━━━━━
                            ❗❗❗ 强制要求：你必须严格按照以下格式输出，禁止使用其他任何格式！
                            
                            【格式类型】分镜脚本（漫剧/动态漫）
                            
                            【格式规则】
                            - 输出为【镜头XX】结构，镜头从01递增
                            - 每镜头尽量包含：时长、景别、运镜、画面、动效、音效/BGM、台词（无则写“无”）
                            - 画面描述要具体可视；动效/音效尽量可执行
                            - 节奏：短平快；镜头切换密度与{{scene_count}}个镜头左右匹配
                            - 目标：约{{target_seconds}}秒
                            - 不要输出任何解释/点评，只输出剧本文本
                            
                            【格式示例（仅示意结构，不要照抄剧情内容）】
                            镜头01  时长：3s  景别：特写  运镜：静止
                            画面：______
                            动效：______
                            音效/BGM：【SFX】______
                            台词：无
                            
                            镜头02  时长：5s  景别：中景  运镜：慢推
                            画面：______
                            音效/BGM：【BGM】冷氛围（轻）
                            台词：
                            - 角色A：台词……
                            - 角色B（迟疑）：台词……
                            
                            ❗ 输出前请再次确认：你的输出必须符合上述格式，不能输出其他格式（如时间戳解说格式、普通文本等）。
                            """)
                    .build();
        }

        return managedPromptService.render(descriptor, values);
    }

    private String cacheKey(String mode, String templateName) {
        return modeDir(mode) + ":" + templateName;
    }

    private String loadPromptTemplate(String mode, String templateName) {
        PromptSpec spec = resolvePromptSpec(mode, templateName);
        String defaultContent = loadPromptTemplateFromResource(mode, templateName);

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

    private String loadPromptTemplateFromResource(String mode, String templateName) {
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
    private PromptSpec resolvePromptSpec(String mode, String templateName) {
        String modeKey = normalizeModeKey(mode);
        Map<String, PromptSpec> modeSpecs;
        if ("pure_narration".equals(modeKey)) {
            modeSpecs = PURE_NARRATION_PROMPT_SPECS;
        } else if ("half_narration".equals(modeKey)) {
            modeSpecs = HALF_NARRATION_PROMPT_SPECS;
        } else {
            modeSpecs = COMMON_PROMPT_SPECS;
        }

        PromptSpec spec = modeSpecs.get(templateName);
        if (spec != null) {
            return spec;
        }

        return new PromptSpec(
                "script." + modeKey + "." + Integer.toHexString(templateName.hashCode()),
                "视频脚本提示词：" + templateName,
                "script",
                "视频脚本工作流提示词",
                null,
                759
        );
    }

    private String normalizeModeKey(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return "common";
        }
        return "PURE_NARRATION".equalsIgnoreCase(mode) ? "pure_narration" : "half_narration";
    }

    private static Map<String, PromptSpec> buildCommonPromptSpecs() {
        Map<String, PromptSpec> specs = new ConcurrentHashMap<>();
        specs.put("大纲生成", new PromptSpec(
                "script.common.outline_generation",
                "视频脚本大纲生成",
                "script",
                "生成视频脚本系列大纲",
                Arrays.asList("story_setting", "idea", "episode_count"),
                722
        ));
        specs.put("导语生成", new PromptSpec(
                "script.common.prologue_generation",
                "视频脚本导语生成",
                "script",
                "生成视频脚本导语",
                Arrays.asList("title", "idea", "story_setting", "outline", "first_episode_hook"),
                724
        ));
        specs.put("看点生成", new PromptSpec(
                "script.common.hook_generation",
                "视频脚本看点生成",
                "script",
                "生成每集看点",
                Arrays.asList("episode_count", "story_setting", "outline"),
                723
        ));
        specs.put("分集审稿", new PromptSpec(
                "script.common.episode_review",
                "视频脚本分集审稿",
                "script",
                "审稿单集脚本",
                Arrays.asList("episode_brief", "episode_content"),
                730
        ));
        specs.put("分集分析", new PromptSpec(
                "script.common.episode_analysis",
                "视频脚本分集分析",
                "script",
                "分析单集脚本",
                Arrays.asList("story_setting", "outline", "episode_number", "episode_count", "episode_title", "episode_core", "episode_content"),
                731
        ));
        specs.put("大纲更新", new PromptSpec(
                "script.common.outline_update",
                "视频脚本大纲更新",
                "script",
                "根据剧情发展更新大纲",
                Arrays.asList("original_outline", "current_episode", "new_development"),
                732
        ));
        specs.put("看点更新", new PromptSpec(
                "script.common.hook_update",
                "视频脚本看点更新",
                "script",
                "更新后续看点",
                Arrays.asList("from_episode", "to_episode", "guidance", "current_hooks_json"),
                733
        ));
        return specs;
    }

    private static Map<String, PromptSpec> buildHalfNarrationPromptSpecs() {
        Map<String, PromptSpec> specs = new ConcurrentHashMap<>();
        specs.put("系列设定", new PromptSpec(
                "script.half_narration.series_setting",
                "半解说系列设定",
                "script",
                "生成半解说系列设定",
                Arrays.asList("title", "idea", "episode_count", "target_seconds", "scene_count", "mode"),
                720
        ));
        specs.put("分集生成", new PromptSpec(
                "script.half_narration.episode_generation",
                "半解说分集生成",
                "script",
                "生成半解说单集脚本",
                Arrays.asList("outline", "previous_context", "episode_brief", "target_seconds", "scene_count", "adjustment"),
                725
        ));
        specs.put("剧本设定", new PromptSpec(
                "script.half_narration.script_setting",
                "半解说剧本设定",
                "script",
                "生成半解说剧本设定",
                Arrays.asList("idea", "target_seconds", "target_minutes", "scene_count"),
                740
        ));
        specs.put("分镜大纲", new PromptSpec(
                "script.half_narration.storyboard_outline",
                "半解说分镜大纲",
                "script",
                "生成半解说分镜大纲",
                Arrays.asList("script_setting", "target_seconds", "scene_count"),
                742
        ));
        specs.put("台词生成", new PromptSpec(
                "script.half_narration.dialogue_generation",
                "半解说台词生成",
                "script",
                "生成半解说台词脚本",
                Arrays.asList("script_setting", "storyboard", "target_seconds"),
                744
        ));
        return specs;
    }

    private static Map<String, PromptSpec> buildPureNarrationPromptSpecs() {
        Map<String, PromptSpec> specs = new ConcurrentHashMap<>();
        specs.put("系列设定", new PromptSpec(
                "script.pure_narration.series_setting",
                "纯解说系列设定",
                "script",
                "生成纯解说系列设定",
                Arrays.asList("title", "idea", "episode_count", "target_seconds", "scene_count", "mode"),
                721
        ));
        specs.put("分集生成", new PromptSpec(
                "script.pure_narration.episode_generation",
                "纯解说分集生成",
                "script",
                "生成纯解说单集脚本",
                Arrays.asList("outline", "previous_context", "episode_brief", "target_seconds", "scene_count", "adjustment"),
                726
        ));
        specs.put("剧本设定", new PromptSpec(
                "script.pure_narration.script_setting",
                "纯解说剧本设定",
                "script",
                "生成纯解说剧本设定",
                Arrays.asList("idea", "target_seconds", "target_minutes", "scene_count", "total_seconds"),
                741
        ));
        specs.put("分镜大纲", new PromptSpec(
                "script.pure_narration.storyboard_outline",
                "纯解说分镜大纲",
                "script",
                "生成纯解说分镜大纲",
                Arrays.asList("script_setting", "target_seconds", "scene_count"),
                743
        ));
        specs.put("解说词生成", new PromptSpec(
                "script.pure_narration.narration_generation",
                "纯解说解说词生成",
                "script",
                "生成纯解说解说词",
                Arrays.asList("script_setting", "storyboard", "target_seconds"),
                745
        ));
        return specs;
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

        // ✅ 输出格式约束也改为后台可管理模板
        String fmt = normalizeScriptFormat(scriptFormat);
        prompt += "\n\n" + renderOutputFormatPrompt(fmt, targetSeconds, sceneCount);

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
