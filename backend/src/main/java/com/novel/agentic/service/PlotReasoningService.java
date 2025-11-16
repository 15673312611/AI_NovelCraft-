package com.novel.agentic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.WritingContext;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 剧情推理服务
 * 
 * 负责分析当前状态，推理本章应该写什么剧情
 */
@Service
public class PlotReasoningService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlotReasoningService.class);
    
    @Autowired
    private AIWritingService aiWritingService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 推理本章剧情方向
     * 
     * @param context 完整的上下文（大纲、蓝图、摘要、图谱等）
     * @param chapterNumber 章节号
     * @return 剧情意图（PlotIntent）
     */
    public Map<String, Object> reasonPlotIntent(WritingContext context, Integer chapterNumber, AIConfigRequest aiConfig) throws Exception {
        logger.info("🧠 开始剧情推理: 第{}章", chapterNumber);
        
        // 构建推理提示词
        String reasoningPrompt = buildReasoningPrompt(context, chapterNumber);
        
        logger.info("📝 推理提示词长度: {}字", reasoningPrompt.length());
        
        // 调用AI推理
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", reasoningPrompt);
        messages.add(userMessage);
        
        StringBuilder response = new StringBuilder();
        aiWritingService.streamGenerateContentWithMessages(
            messages, 
            "plot_reasoning", 
            aiConfig, 
            chunk -> response.append(chunk)
        );
        
        String aiResponse = response.toString();
        logger.info("💭 AI推理结果: {}", aiResponse.length() > 500 ? aiResponse.substring(0, 500) + "..." : aiResponse);
        
        // 解析AI推理结果
        Map<String, Object> plotIntent = parsePlotIntent(aiResponse);
        
        logger.info("✅ 剧情推理完成: {}", plotIntent.get("direction"));
        try { plotIntent.put("_reasoning_prompt", reasoningPrompt); } catch (Exception ignore) {}
        return plotIntent;
    }
    
    /**
     * 构建推理提示词
     */
    private String buildReasoningPrompt(WritingContext context, Integer chapterNumber) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一位专业的网文小说编剧AI。现在需要为第").append(chapterNumber)
              .append("章推理剧情方向。\n\n");
        
        prompt.append("【你的任务】\n");
        prompt.append("根据以下信息，推理本章应该写什么剧情，需要推进哪些情节线，需要回收哪些伏笔。\n\n");
        
        // 1. 核心设定（框架性参考）
        if (context.getCoreSettings() != null && !context.getCoreSettings().isEmpty()) {
            String core = context.getCoreSettings();
            if (core.length() > 2000) {
                core = core.substring(0, 2000) + "...(已截断)";
            }
            prompt.append("【核心设定】\n");
            prompt.append(core).append("\n\n");
        }
        
        // 2. 卷蓝图
        if (context.getVolumeBlueprint() != null && !context.getVolumeBlueprint().isEmpty()) {
            Map<String, Object> volume = context.getVolumeBlueprint();
            Object blueprint = volume.get("blueprint");

            logger.info("📘 [PlotReasoning] 卷蓝图信息: volumeTitle={}, blueprint={}",
                volume.get("volumeTitle"),
                blueprint != null ? (blueprint.toString().length() > 50 ?
                    blueprint.toString().substring(0, 50) + "..." : blueprint.toString()) : "NULL");

            prompt.append("【当前卷蓝图】\n");
            prompt.append("卷名: ").append(volume.getOrDefault("volumeTitle", "未命名")).append("\n");
            prompt.append("章节范围: ").append(volume.getOrDefault("chapterRange", "未设定")).append("\n");

            String blueprintText = String.valueOf(volume.getOrDefault("blueprint", "无"));
            if ("暂无蓝图".equals(blueprintText) || "无".equals(blueprintText)) {
                logger.warn("⚠️ [PlotReasoning] 卷蓝图为空，将影响剧情推理质量！");
                prompt.append("蓝图: （图谱数据尚未建立，请根据大纲与卷蓝图创作）\n\n");
            } else {
                prompt.append("蓝图: ").append(blueprintText).append("\n\n");
            }
        } else {
            logger.warn("⚠️ [PlotReasoning] 未找到卷蓝图数据！");
        }
        
        // 3. 最近20-30章摘要
        if (context.getRecentSummaries() != null && !context.getRecentSummaries().isEmpty()) {
            prompt.append("【最近章节摘要】（了解剧情脉络）\n");
            int start = Math.max(0, context.getRecentSummaries().size() - 30);
            for (int i = start; i < context.getRecentSummaries().size(); i++) {
                Map<String, Object> summary = context.getRecentSummaries().get(i);
                prompt.append("- 第").append(summary.get("chapterNumber")).append("章: ")
                      .append(summary.getOrDefault("summary", "无摘要")).append("\n");
            }
            prompt.append("\n");
        }
        
        // 4. 前一章完整内容（衔接细节）
        if (context.getRecentFullChapters() != null && !context.getRecentFullChapters().isEmpty()) {
            Map<String, Object> lastChapter = context.getRecentFullChapters()
                .get(context.getRecentFullChapters().size() - 1);
            prompt.append("【前一章完整内容】（用于衔接）\n");
            prompt.append("第").append(lastChapter.get("chapterNumber")).append("章: ")
                  .append(lastChapter.get("title")).append("\n");
            String content = String.valueOf(lastChapter.get("content"));
            if (content.length() > 3000) {
                content = content.substring(0, 3000) + "...(已截断，仅供参考结尾)";
            }
            prompt.append(content).append("\n\n");
        }
        
        // 5. 图谱节点信息
        prompt.append("【图谱信息】（前面发生的重要信息）\n\n");
        
        // 历史事件（带因果链）
        if (context.getRelevantEvents() != null && !context.getRelevantEvents().isEmpty()) {
            prompt.append("## 历史事件\n");
            context.getRelevantEvents().stream().limit(10).forEach(event -> {
                Map<String, Object> props = event.getProperties();
                prompt.append("- [第").append(event.getChapterNumber()).append("章] ")
                      .append(props.getOrDefault("description", "无描述"));
                if (props.get("causalFrom") != null) {
                    prompt.append(" ⬅️ 前因: ").append(props.get("causalFrom"));
                }
                if (props.get("causalTo") != null) {
                    prompt.append(" ➡️ 后果: ").append(props.get("causalTo"));
                }
                prompt.append("\n");
            });
            prompt.append("\n");
        }
        
        // 角色状态
        if (context.getCharacterProfiles() != null && !context.getCharacterProfiles().isEmpty()) {
            prompt.append("## 主要角色状态\n");
            context.getCharacterProfiles().stream().limit(5).forEach(profile -> {
                String name = String.valueOf(profile.getOrDefault("name", profile.get("characterName")));
                prompt.append("- ").append(name).append(": ");
                if (profile.get("location") != null) {
                    prompt.append("位置=").append(profile.get("location")).append("; ");
                }
                if (profile.get("realm") != null) {
                    prompt.append("境界=").append(profile.get("realm")).append("; ");
                }
                if (profile.get("status") != null) {
                    prompt.append("状态=").append(profile.get("status"));
                }
                prompt.append("\n");
            });
            prompt.append("\n");
        }
        
        // 未解决伏笔
        if (context.getUnresolvedForeshadows() != null && !context.getUnresolvedForeshadows().isEmpty()) {
            prompt.append("## 待回收伏笔\n");
            context.getUnresolvedForeshadows().stream().limit(5).forEach(foreshadow -> {
                Map<String, Object> props = foreshadow.getProperties();
                prompt.append("- ").append(props.getOrDefault("description", "无描述"));
                if (props.get("plantedAt") != null) {
                    prompt.append(" (埋于第").append(props.get("plantedAt")).append("章)");
                }
                if (props.get("suggestedResolveWindow") != null) {
                    prompt.append(" [建议回收: ").append(props.get("suggestedResolveWindow")).append("]");
                }
                prompt.append("\n");
            });
            prompt.append("\n");
        }
        
        // 活跃情节线
        if (context.getPlotlineStatus() != null && !context.getPlotlineStatus().isEmpty()) {
            prompt.append("## 活跃情节线\n");
            context.getPlotlineStatus().stream().limit(5).forEach(plotline -> {
                Map<String, Object> props = plotline.getProperties();
                prompt.append("- ").append(props.getOrDefault("name", "未命名")).append(": ")
                      .append(props.getOrDefault("status", "未知"));
                if (props.get("idleDuration") != null) {
                    prompt.append(" (闲置").append(props.get("idleDuration")).append("章)");
                }
                prompt.append("\n");
            });
            prompt.append("\n");
        }
        
        // 冲突弧线
        if (context.getConflictArcs() != null && !context.getConflictArcs().isEmpty()) {
            prompt.append("## 冲突弧线\n");
            context.getConflictArcs().stream().limit(3).forEach(arc -> {
                Map<String, Object> props = arc.getProperties();
                prompt.append("- ").append(props.getOrDefault("name", "未命名")).append(": ")
                      .append("阶段=").append(props.getOrDefault("stage", "未知"))
                      .append(", 下一步=").append(props.getOrDefault("nextAction", "待定"))
                      .append("\n");
            });
            prompt.append("\n");
        }
        
        // 叙事节奏
        if (context.getNarrativeRhythm() != null) {
            Map<String, Object> rhythm = context.getNarrativeRhythm();
            @SuppressWarnings("unchecked")
            List<String> recommendations = rhythm.get("recommendations") instanceof List
                ? (List<String>) rhythm.get("recommendations")
                : Collections.emptyList();
            
            if (!recommendations.isEmpty()) {
                prompt.append("## 叙事节奏建议\n");
                recommendations.forEach(rec -> prompt.append("- ").append(rec).append("\n"));
                prompt.append("\n");
            }
        }
        
        // 用户指令
        if (context.getUserAdjustment() != null && !context.getUserAdjustment().isEmpty()) {
            prompt.append("【用户指令】\n");
            prompt.append(context.getUserAdjustment()).append("\n\n");
        }
        
        // 输出格式
        prompt.append("【输出格式】\n");
        prompt.append("请按以下JSON格式回复你的推理结果：\n");
        prompt.append("{\n");
        prompt.append("  \"direction\": \"本章剧情方向（2-3句话，说明本章主要写什么）\",\n");
        prompt.append("  \"keyPlotPoints\": [\n");
        prompt.append("    \"关键剧情点1\",\n");
        prompt.append("    \"关键剧情点2\",\n");
        prompt.append("    \"关键剧情点3\"\n");
        prompt.append("  ],\n");
        prompt.append("  \"plotlinesToAdvance\": [\"需要推进的情节线1\", \"情节线2\"],\n");
        prompt.append("  \"foreshadowsToResolve\": [\"需要回收的伏笔1\", \"伏笔2\"],\n");
        prompt.append("  \"relevantCharacters\": [\"相关角色1\", \"角色2\"],\n");
        prompt.append("  \"causalLinks\": [\n");
        prompt.append("    {\"cause\": \"前因事件\", \"effect\": \"本章要产生的后果\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"reasoning\": \"推理过程（解释为什么这样安排）\"\n");
        prompt.append("}\n\n");

        prompt.append("【推理要求】\n");
        prompt.append("1. 基于前面章节的剧情自然推进，不要突兀\n");
        prompt.append("2. 考虑伏笔回收窗口，优先处理急迫的伏笔\n");
        prompt.append("3. 平衡主线和支线，避免长期闲置某条线\n");
        prompt.append("4. 注意叙事节奏，不要连续高强度冲突\n");
        prompt.append("5. 确保因果链清晰，前因有后果\n\n");
        prompt.append("重要：只输出 JSON，且不要使用```代码块、不要添加额外解释。务必以{开头、以}结尾。\n\n");

        prompt.append("现在，请开始推理：");

        return prompt.toString();
    }
    
    /**
     * 解析AI推理结果（健壮：剥离代码块/杂字符并修复常见格式噪音）
     */
    private Map<String, Object> parsePlotIntent(String aiResponse) {
        try {
            String sanitized = sanitizeToStrictJson(aiResponse);
            @SuppressWarnings("unchecked")
            Map<String, Object> intent = objectMapper.readValue(sanitized, Map.class);
            return intent;
        } catch (Exception e) {
            logger.error("解析剧情推理结果失败", e);
        }

        // 兜底：返回默认推理结果
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("direction", "根据前面剧情自然推进，继续主线发展");
        fallback.put("keyPlotPoints", Arrays.asList("继续前面的剧情", "推进主线", "展现角色成长"));
        fallback.put("plotlinesToAdvance", Collections.emptyList());
        fallback.put("foreshadowsToResolve", Collections.emptyList());
        fallback.put("relevantCharacters", Collections.emptyList());
        fallback.put("causalLinks", Collections.emptyList());
        fallback.put("reasoning", "AI推理失败，使用默认推理");
        return fallback;
    }

    /**
     * 将AI原始输出清洗为严格JSON字符串
     */
    private String sanitizeToStrictJson(String raw) {
        if (raw == null) return "{}";
        String s = raw
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .replace("\uFEFF", "")
                .trim();
        // 仅保留最外层花括号中的内容
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        // 修复：在逗号或左花括号后，若出现多余字母+引号开头的键名，移除多余字母
        // 例如 ", e  \"foreshadowsToResolve\"" -> ", \"foreshadowsToResolve\""
        s = s.replaceAll(",\\s*[A-Za-z_]+\\s*(\")", ", $1");
        s = s.replaceAll("\\{\\s*[A-Za-z_]+\\s*(\")", "{$1");
        // 修复：移除对象/数组末尾的拖尾逗号
        s = s.replaceAll(",\\s*([}\\]])", "$1");
        // 可选：去除BOM/不可见字符
        s = s.replaceAll("[\u0000-\u001F]", " ");
        return s;
    }
}


