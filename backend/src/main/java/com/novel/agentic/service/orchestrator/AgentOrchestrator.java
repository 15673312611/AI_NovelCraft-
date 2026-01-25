package com.novel.agentic.service.orchestrator;

import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.*;
import com.novel.agentic.service.PromptAssembler;
import com.novel.agentic.service.tools.Tool;
import com.novel.agentic.service.tools.ToolRegistry;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * AI代理编排器 - ReAct (Reasoning + Acting) 循环控制
 * 
 * 核心流程：
 * 1. THOUGHT: AI思考当前需要什么信息
 * 2. ACTION: 决定调用哪个工具及参数
 * 3. OBSERVATION: 观察工具返回结果
 * 4. 重复1-3，直到信息充足
 * 5. WRITE: 开始章节写作
 */
@Service
public class AgentOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);
    
    private static final Integer MAX_STEPS = 8; // 最大决策步数
    private static final int THINKING_OUTLINE_TOKENS = 2200;
    private static final int THINKING_BLUEPRINT_TOKENS = 1600;
    private static final int THINKING_CORE_SUMMARY_TOKENS = 1200;
    private static final int THINKING_PLAN_TOKENS = 600;
    private static final int THINKING_RECENT_SUMMARY_TOKENS = 420;
    private static final int THINKING_EVENT_TOKENS = 260;
    private static final int THINKING_PROFILE_TOKENS = 220;
    
    @Autowired
    private ToolRegistry toolRegistry;
    
    @Autowired
    private AIWritingService aiWritingService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PromptAssembler promptAssembler;
    
    /**
     * 执行ReAct决策循环（新架构：决策层看全局，写作层看章纲）
     * 
     * @param novelId 小说ID
     * @param chapterNumber 章节号
     * @param userAdjustment 用户指令
     * @param aiConfig AI配置
     * @return 写作上下文（包含AI决策过程和收集的所有信息）
     */
    public WritingContext executeReActLoop(
            Long novelId,
            Integer chapterNumber,
            String userAdjustment,
            AIConfigRequest aiConfig) throws Exception {
        
        logger.info("🧠 开始决策循环（新架构）: novelId={}, chapter={}", novelId, chapterNumber);
        
        WritingContext.WritingContextBuilder contextBuilder = WritingContext.builder();
        List<AgentThought> thoughts = new ArrayList<>();
        
        // 初始化章节计划
        Map<String, Object> chapterPlan = new HashMap<>();
        chapterPlan.put("chapterNumber", chapterNumber);
        chapterPlan.put("title", "第" + chapterNumber + "章");
        chapterPlan.put("userAdjustment", userAdjustment);
        contextBuilder.chapterPlan(chapterPlan);
        contextBuilder.userAdjustment(userAdjustment);
        
        // 获取所有可用工具定义
        List<ToolDefinition> availableTools = toolRegistry.getAllDefinitions();

        // 预取核心上下文：大纲、卷蓝图、20-30章摘要、前1章完整内容
        Set<String> executedTools = new HashSet<>();
        prefetchCoreContextEnhanced(novelId, chapterNumber, contextBuilder, executedTools);

        boolean earlyPhase = isEarlyChapter(chapterNumber);
        
        // 强制预加载图谱数据（不依赖AI决策）
        prefetchGraphData(novelId, chapterNumber, contextBuilder, executedTools, earlyPhase);

        // 移除创意信号预加载，让决策层根据实际需要调用
        // seedCreativeSignals(novelId, chapterNumber, userAdjustment, contextBuilder, executedTools, aiConfig);

        // 强制必查工具
        Set<String> requiredTools = new HashSet<>();
        if (!earlyPhase) {
            requiredTools.add("getWorldRules");
            requiredTools.add("getNarrativeRhythm");
        }

        int maxSteps = earlyPhase ? Math.min(MAX_STEPS, 3) : MAX_STEPS;
        
        // ReAct循环
        for (int step = 1; step <= maxSteps; step++) {
            logger.info("📍 Step {}/{}", step, maxSteps);
            
            // 1. THOUGHT: 让AI思考下一步
            AgentThought thought = AgentThought.builder()
                .stepNumber(step)
                .timestamp(LocalDateTime.now())
                .build();
            
            WritingContext snapshot = contextBuilder.build();
            String thinkingPrompt = buildThinkingPrompt(
                novelId, chapterNumber, userAdjustment,
                availableTools, executedTools, requiredTools, thoughts, snapshot, earlyPhase
            );

            logThinkingAgenda(step, chapterNumber, userAdjustment, requiredTools, executedTools, thoughts, earlyPhase);

            // 调用AI获取决策
            String aiResponse = callAIForDecision(thinkingPrompt, aiConfig);
            logger.info("💭 AI思考: {}", aiResponse);
            
            // 解析AI的决策
            AgentDecision decision = parseAIDecision(aiResponse);
            thought.setReasoning(decision.getReasoning());
            thought.setAction(decision.getAction());
            thought.setActionArgs(decision.getActionArgs());

            logger.info("🎯 决策输出: action={}, args={}, reasoning= {}",
                decision.getAction(),
                shorten(decision.getActionArgs(), 120),
                shorten(decision.getReasoning(), 180));
            
            // 2. ACTION: 执行工具
            if ("WRITE".equals(decision.getAction())) {
                // AI认为信息充足，可以开始写作
                thought.setGoalAchieved(true);
                thought.setObservation("信息收集完成，准备写作");
                thoughts.add(thought);
                logger.info("✅ AI决定：信息充足，开始写作");
                break;
            } else {
                // 执行具体工具
                try {
                    Map<String, Object> args = parseToolArgs(decision.getActionArgs(), novelId, chapterNumber);
                    args = enrichToolArgs(decision.getAction(), args, snapshot, userAdjustment);

                    Object result;
                    try {
                        result = toolRegistry.executeTool(decision.getAction(), args);
                    } catch (IllegalArgumentException missingTool) {
                        String instruction = decision.getAction();
                        logger.warn("AI请求不存在的工具，将作为用户指示提示: {}", instruction);
                        thought.setObservation("AI请求用户指示: " + instruction);
                        thought.setGoalAchieved(false);
                        executedTools.add(instruction);
                        thoughts.add(thought);
                        break;
                    }

                    // 3. OBSERVATION: 记录结果
                    String resultJson = objectMapper.writeValueAsString(result);
                    thought.setObservation(resultJson);
                    thought.setGoalAchieved(false);

                    executedTools.add(decision.getAction());

                    // 将结果存入上下文
                    storeToolResult(decision.getAction(), result, contextBuilder);

                    logger.info("✅ 工具执行成功: {} -> {}", decision.getAction(),
                        resultJson.length() > 200 ? resultJson.substring(0, 200) + "..." : resultJson);

                    // 🆕 REFLECTION: AI反思结果质量
                    String reflection = reflectOnResult(decision.getAction(), resultJson, aiConfig);
                    thought.setReflection(reflection);
                    logger.info("🤔 AI反思: {}", reflection);

                } catch (Exception e) {
                    thought.setObservation("工具执行失败: " + e.getMessage());
                    logger.error("❌ 工具执行失败: {}", decision.getAction(), e);
                }
            }
            
            thoughts.add(thought);
            
            // 检查是否完成必查工具
            if (executedTools.containsAll(requiredTools) && step >= 3) {
                logger.info("✅ 必查工具已完成，可以考虑结束决策");
            }

            if (earlyPhase && step >= 2 && !executedTools.isEmpty()) {
                logger.info("⏱️ 早期章节，限制决策步数，提前结束循环");
                break;
            }
        }
        
        // 兜底：如果到了MAX_STEPS还没有WRITE，强制结束
        if (thoughts.isEmpty() || !thoughts.get(thoughts.size() - 1).getGoalAchieved()) {
            logger.warn("⚠️ 达到最大步数限制，强制结束决策循环");
            
            // 🔧 修复：强制执行必查工具的兜底策略
            if (!executedTools.contains("getOutline")) {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("novelId", novelId);
                    Object result = toolRegistry.executeTool("getOutline", params);
                    storeToolResult("getOutline", result, contextBuilder);
                    logger.info("🔧 兜底执行: getOutline");
                } catch (Exception e) {
                    logger.error("兜底执行getOutline失败", e);
                }
            }
            
            if (!executedTools.contains("getVolumeBlueprint")) {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("novelId", novelId);
                    params.put("chapterNumber", chapterNumber);
                    Object result = toolRegistry.executeTool("getVolumeBlueprint", params);
                    storeToolResult("getVolumeBlueprint", result, contextBuilder);
                    logger.info("🔧 兜底执行: getVolumeBlueprint");
                } catch (Exception e) {
                    logger.error("兜底执行getVolumeBlueprint失败", e);
                }
            }
        }
        
        contextBuilder.thoughts(thoughts);
        WritingContext context = contextBuilder.build();
        context.setChapterIntent(deriveChapterIntent(context));
        
        logger.info("🎉 ReAct决策循环完成: 共{}步, 执行工具{}", thoughts.size(), executedTools);
        return context;
    }
    
    /**
     * 构建思考提示词
     */
    private String buildThinkingPrompt(
            Long novelId,
            Integer chapterNumber,
            String userAdjustment,
            List<ToolDefinition> availableTools,
            Set<String> executedTools,
            Set<String> requiredTools,
            List<AgentThought> previousThoughts,
            WritingContext currentContext,
            boolean earlyPhase) {
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一位专业的网文小说家AI助手。现在需要为小说的第").append(chapterNumber)
              .append("章进行写作准备。\n\n");
        
        prompt.append("【核心素材（已预加载）】\n");
        appendCoreFocus(prompt, currentContext);
        prompt.append("- 章节意图：").append(formatChapterIntent(currentContext)).append("\n");
        prompt.append("- 节奏指标：").append(formatRhythmSummary(currentContext)).append("\n\n");

        appendContextDigest(prompt, currentContext);

        if (earlyPhase) {
            prompt.append("【章节提示】\n");
            prompt.append("- 当前为前期章节，图谱数据有限；已有大纲/卷蓝图即可支撑写作\n");
            prompt.append("- 若核心素材充足，请直接WRITE开启创作，避免额外检索\n");
            prompt.append("- 仅当确实缺少设定或节奏信息时，再调用必要工具\n\n");
        }

        prompt.append("请先结合以上内容快速复盘：\n");
        prompt.append("1. 当前主线/支线是否具备充足上下文？\n");
        prompt.append("2. 章节节奏是否需要调整（参考 narrativeRhythm 指标）？\n");
        prompt.append("3. 是否存在必须回收的伏笔或待续情节点？\n\n");
        prompt.append("【剧情发展判断原则】\n");
        prompt.append("- 小说创作过程中，剧情会自然演化出新人物、新线索、新冲突，这是正常现象\n");
        prompt.append("- 只要新内容围绕主角展开，且能推进核心目标/生存/成长，就不算跑偏\n");
        prompt.append("- 蓝图是指导性框架，不是死板限制；AI可以根据前期内容自然延伸剧情\n");
        prompt.append("- 真正的跑偏是指：主角目标完全偏离、核心矛盾被遗忘、剧情重复冗余\n");
        prompt.append("- 若剧情流畅、有冲突、有推进，即便出现新元素也视为正常发展，不要随意判定'跑偏'\n\n");
        prompt.append("若问题已解决，可直接选择 WRITE；只有当确实缺失关键信息时再调用额外工具。\n\n");

        String innovationChecklist = promptAssembler.getInnovationChecklistSummary();
        if (StringUtils.isNotBlank(innovationChecklist)) {
            prompt.append("【反套路自检】\n").append(innovationChecklist).append("\n\n");
        }

        String antiCliche = promptAssembler.getAntiClicheSummary();
        if (StringUtils.isNotBlank(antiCliche)) {
            prompt.append("【剧情翻新提示】\n").append(antiCliche).append("\n\n");
        }

        prompt.append("【开篇自检】\n");
        prompt.append("- 第一屏必须落地动作/对话/选择，禁止堆砌环境铺陈\n");
        prompt.append("- 开头 3 段内抛出一个未解决的问题或危机\n");
        prompt.append("- 章节目标要在开头 30 秒内明确，信息密集、节奏快\n\n");

        prompt.append("【视角纪律】\n");
        prompt.append("- 主角只知道亲历与上一章显性信息，不得凭空掌握宏大设定\n");
        prompt.append("- 新世界观必须通过遭遇、对话或线索逐步揭示\n");
        prompt.append("- 凡属猜测请点明不确定性，后续用剧情验证\n\n");

        prompt.append("【风格警戒】\n");
        prompt.append("- 开头必须直接抛出行动/冲突/目标，不得写成环境散文\n");
        prompt.append("- 禁止使用‘仿佛’‘如同’‘幽蓝’‘冰冷的空气’等模板化表达\n");
        prompt.append("- 语言短促有力，优先角色体验与爽点推进，宁简不华\n");
        prompt.append("- 每个场景检视：是否让读者爽？若没有爽点或推进，请重构\n\n");

        prompt.append("【用户要求】\n").append(userAdjustment != null ? userAdjustment : "正常推进剧情")
              .append("\n\n");
        
        prompt.append("【可用工具】\n");
        for (ToolDefinition tool : availableTools) {
            String status = executedTools.contains(tool.getName()) ? " ✓已调用" : 
                           requiredTools.contains(tool.getName()) ? " ⚠️必须调用" : "";
            prompt.append("- ").append(tool.getName()).append(status).append(": ")
                  .append(tool.getDescription()).append("\n");
        }
        prompt.append("\n");
        
        if (!previousThoughts.isEmpty()) {
            prompt.append("【之前的思考和行动】\n");
            for (AgentThought thought : previousThoughts) {
                prompt.append("Step ").append(thought.getStepNumber()).append(":\n");
                prompt.append("  思考: ").append(thought.getReasoning()).append("\n");
                prompt.append("  行动: ").append(thought.getAction()).append("\n");
                String obs = thought.getObservation();
                prompt.append("  结果: ").append(obs != null && obs.length() > 150 ? 
                    obs.substring(0, 150) + "..." : obs).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("【决策格式】\n");
        prompt.append("请按以下JSON格式回复：\n");
        prompt.append("{\n");
        prompt.append("  \"reasoning\": \"你的思考过程（为什么需要这个信息/为什么现在可以写作）\",\n");
        prompt.append("  \"action\": \"工具名称或WRITE（表示开始写作）\",\n");
        prompt.append("  \"args\": \"工具参数（如果是WRITE则为空）\"\n");
        prompt.append("}\n\n");
        
        prompt.append("【决策要求】\n");
        prompt.append("1. 先复盘已持有的核心素材，确认是否足够写作\n");
        prompt.append("2. 只有当节奏或剧情信息缺失时，才调用额外工具\n");
        prompt.append("3. 必须先调用标记为'必须调用'的工具\n");
        prompt.append("4. 避免重复调用已执行的工具\n");
        prompt.append("5. 优先级：节奏校准 > 主线冲突/人物工具 > 图谱补充工具\n\n");
        
        // 🆕 根据章节类型推荐工具
        String chapterTypeHint = inferChapterTypeAndRecommendTools(userAdjustment);
        if (chapterTypeHint != null && !chapterTypeHint.isEmpty()) {
            prompt.append("【章节类型建议】\n").append(chapterTypeHint).append("\n\n");
        }
        
        prompt.append("现在，请给出你的决策：");
        
        return prompt.toString();
    }
    
    /**
     * 调用AI获取决策
     */
    private String callAIForDecision(String prompt, AIConfigRequest aiConfig) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        
        StringBuilder response = new StringBuilder();
        aiWritingService.streamGenerateContentWithMessages(
            messages, 
            "agent_decision", 
            aiConfig, 
            chunk -> response.append(chunk)
        );
        
        return response.toString();
    }
    
    /**
     * 推断章节类型并推荐工具
     * 
     * 根据用户指令智能推荐最相关的工具
     */
    private String inferChapterTypeAndRecommendTools(String userAdjustment) {
        if (userAdjustment == null || userAdjustment.isEmpty()) {
            return null;
        }
        
        String lower = userAdjustment.toLowerCase();
        StringBuilder hint = new StringBuilder();
        
        // 战斗/冲突章节
        if (lower.contains("战斗") || lower.contains("打斗") || lower.contains("对抗") || 
            lower.contains("冲突") || lower.contains("对决") || lower.contains("战") || 
            lower.contains("打败") || lower.contains("击杀")) {
            hint.append("【战斗/冲突章节】推荐工具：\n");
            hint.append("- getNarrativeRhythm: 评估冲突密度是否需要缓冲\n");
            hint.append("- getConflictArcStatus: 明确冲突弧线阶段与升级方案\n");
            hint.append("- getConflictHistory: 查看主角与对手的历史冲突，设计戏剧性对决\n");
            hint.append("- getCharacterRelationships: 了解角色间的恩怨关系\n");
            hint.append("- getWorldRules: 确认力量体系设定，避免战力崩坏\n");
            hint.append("- getRelevantEvents: 回顾前期铺垫，设计反转\n");
        }
        // 感情线章节
        else if (lower.contains("感情") || lower.contains("表白") || lower.contains("恋爱") || 
                 lower.contains("暧昧") || lower.contains("情侣") || lower.contains("关系进展")) {
            hint.append("【感情线章节】推荐工具：\n");
            hint.append("- getNarrativeRhythm: 确定是否安排人物向的节奏\n");
            hint.append("- getCharacterRelationships: 查看角色间的感情关系发展\n");
            hint.append("- getEventsByCharacter: 回顾两人的互动历史\n");
            hint.append("- getUnresolvedForeshadows: 检查是否有感情线伏笔待回收\n");
            hint.append("- getCharacterArcStatus: 明确人物弧线的待完成节点\n");
        }
        else if (lower.contains("新角色") || lower.contains("引入角色") || lower.contains("安排登场") || lower.contains("介绍角色")) {
            hint.append("【新角色登场】推荐工具：\n");
            hint.append("- generateCharacterProfile: 生成十维角色档案，设定目标与记忆点\n");
            hint.append("- getCharacterRelationships: 明确与现有角色的关系态度\n");
            hint.append("- getNarrativeRhythm: 校准角色登场节奏与冲突配比\n");
        }
        // 揭秘/伏笔回收章节
        else if (lower.contains("揭秘") || lower.contains("真相") || lower.contains("回收伏笔") || 
                 lower.contains("揭露") || lower.contains("秘密") || lower.contains("谜底")) {
            hint.append("【揭秘/伏笔回收章节】推荐工具：\n");
            hint.append("- getNarrativeRhythm: 控制节奏与张力\n");
            hint.append("- getUnresolvedForeshadows: 查看待回收的伏笔（必查！）\n");
            hint.append("- getEventsByCausality: 沿因果链追溯事件真相\n");
            hint.append("- getRelevantEvents: 回顾相关历史事件\n");
        }
        // 角色成长/突破章节
        else if (lower.contains("突破") || lower.contains("升级") || lower.contains("成长") || 
                 lower.contains("领悟") || lower.contains("顿悟") || lower.contains("进阶")) {
            hint.append("【成长/突破章节】推荐工具：\n");
            hint.append("- getNarrativeRhythm: 判断是否需要转入角色成长节奏\n");
            hint.append("- getEventsByCharacter: 回顾主角的成长历程\n");
            hint.append("- getWorldRules: 确认力量体系的升级规则\n");
            hint.append("- getRelevantEvents: 查找触发顿悟的关键事件\n");
            hint.append("- getCharacterArcStatus: 明确成长线的下一步\n");
        }
        // 多线叙事章节
        else if (lower.contains("多线") || lower.contains("支线") || lower.contains("切换视角") || 
                 lower.contains("情节线")) {
            hint.append("【多线叙事章节】推荐工具：\n");
            hint.append("- getPlotlineStatus: 检查各情节线发展状态（必查！）\n");
            hint.append("- getRelevantEvents: 了解各线的最新进展\n");
            hint.append("- getPerspectiveHistory: 规划视角切换策略\n");
        }
        // 日常/过渡章节
        else if (lower.contains("日常") || lower.contains("过渡") || lower.contains("铺垫") || 
                 lower.contains("休息") || lower.contains("闲暇")) {
            hint.append("【日常/过渡章节】推荐工具：\n");
            hint.append("- getNarrativeRhythm: 检查是否需要节奏缓冲\n");
            hint.append("- getUnresolvedForeshadows: 可埋下新伏笔或轻微推进旧伏笔\n");
            hint.append("- getCharacterRelationships: 发展角色关系\n");
            hint.append("- getPlotlineStatus: 推进久未更新的支线\n");
            hint.append("- getPerspectiveHistory: 结合视角刷新读者体验\n");
        }
        
        return hint.toString();
    }
    
    /**
     * 反思工具结果质量
     * 
     * 让AI评估获取的信息是否有用、是否还需要更多信息
     */
    private String reflectOnResult(String toolName, String resultJson, AIConfigRequest aiConfig) {
        try {
            StringBuilder reflectionPrompt = new StringBuilder();
            reflectionPrompt.append("你刚刚调用了工具【").append(toolName).append("】，返回结果如下：\n\n");
            
            // 截取结果（避免太长）
            String truncatedResult = resultJson.length() > 500 ? 
                resultJson.substring(0, 500) + "...（结果已截断）" : resultJson;
            reflectionPrompt.append(truncatedResult).append("\n\n");
            
            reflectionPrompt.append("请简短评估（1-2句话）：\n");
            reflectionPrompt.append("1. 这个结果是否有用？\n");
            reflectionPrompt.append("2. 是否还需要更多信息来完成写作？\n");
            reflectionPrompt.append("3. 如果结果为空或无用，下一步应该怎么办？\n\n");
            reflectionPrompt.append("请用简短的文字回复（不超过100字）：");
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", reflectionPrompt.toString());
            messages.add(userMessage);
            
            StringBuilder reflection = new StringBuilder();
            aiWritingService.streamGenerateContentWithMessages(
                messages, 
                "agent_reflection", 
                aiConfig, 
                chunk -> reflection.append(chunk)
            );
            
            return reflection.toString();
            
        } catch (Exception e) {
            logger.error("反思失败", e);
            return "反思失败：" + e.getMessage();
        }
    }

    private Map<String, Object> deriveChapterIntent(WritingContext context) {
        Map<String, Object> intent = new HashMap<>();

        Map<String, Object> rhythm = context.getNarrativeRhythm();
        Map<String, Object> metrics = rhythm != null ? castToMap(rhythm.get("metrics")) : null;
        @SuppressWarnings("unchecked")
        List<String> recommendations = rhythm != null && rhythm.get("recommendations") instanceof List
            ? (List<String>) rhythm.get("recommendations")
            : Collections.emptyList();

        boolean conflictFatigue = metrics != null && Boolean.TRUE.equals(metrics.get("conflictFatigue"));

        GraphEntity conflictArc = null;
        if (context.getConflictArcs() != null && !context.getConflictArcs().isEmpty()) {
            conflictArc = context.getConflictArcs().get(0);
        }

        GraphEntity characterArc = null;
        if (context.getCharacterArcs() != null && !context.getCharacterArcs().isEmpty()) {
            characterArc = context.getCharacterArcs().get(0);
        }

        String perspectiveSuggestion = null;
        if (context.getPerspectiveHistory() != null && !context.getPerspectiveHistory().isEmpty()) {
            GraphEntity first = context.getPerspectiveHistory().get(0);
            if ("PerspectiveRecommendation".equals(first.getType())) {
                perspectiveSuggestion = (String) first.getProperties().get("recommendation");
            }
        }

        String primaryFocus;
        String targetBeatType;
        if (conflictFatigue) {
            primaryFocus = "CHARACTER_RELIEF";
            targetBeatType = "RELIEF";
        } else if (conflictArc != null) {
            primaryFocus = "CONFLICT_ESCALATION";
            targetBeatType = "CONFLICT";
        } else if (characterArc != null) {
            primaryFocus = "CHARACTER_DEVELOPMENT";
            targetBeatType = "CHARACTER";
        } else {
            primaryFocus = "PLOT_ADVANCEMENT";
            targetBeatType = "PLOT";
        }

        List<String> focusNotes = new ArrayList<>();
        if (conflictFatigue) {
            focusNotes.add("连续冲突强度过高，本章优先安排人物内心或日常缓冲。");
        }
        if (conflictArc != null) {
            Map<String, Object> props = conflictArc.getProperties();
            focusNotes.add("冲突线：" + props.get("name") + " → 下一步：" + props.get("nextAction"));
            Map<String, Object> conflictPlan = new HashMap<>();
            conflictPlan.put("name", props.get("name"));
            conflictPlan.put("stage", props.get("stage"));
            conflictPlan.put("nextAction", props.get("nextAction"));
            conflictPlan.put("protagonist", props.get("protagonist"));
            conflictPlan.put("antagonist", props.get("antagonist"));
            conflictPlan.put("urgency", props.get("urgency"));
            intent.put("conflictPlan", conflictPlan);
        }
        if (characterArc != null) {
            Map<String, Object> props = characterArc.getProperties();
            focusNotes.add("人物线：" + props.get("characterName") + " → 待完成：" + props.get("pendingBeat"));
            Map<String, Object> characterPlan = new HashMap<>();
            characterPlan.put("characterName", props.get("characterName"));
            characterPlan.put("pendingBeat", props.get("pendingBeat"));
            characterPlan.put("nextGoal", props.get("nextGoal"));
            characterPlan.put("priority", props.get("priority"));
            intent.put("characterPlan", characterPlan);
        }

        if (recommendations != null && !recommendations.isEmpty()) {
            focusNotes.add(recommendations.get(0));
        }

        intent.put("primaryFocus", primaryFocus);
        intent.put("targetBeatType", targetBeatType);
        intent.put("focusNotes", focusNotes);
        intent.put("narrativeRecommendations", recommendations);
        intent.put("perspectiveSuggestion", perspectiveSuggestion);

        return intent;
    }

    private void logThinkingAgenda(int step,
                                   Integer chapterNumber,
                                   String userAdjustment,
                                   Set<String> requiredTools,
                                   Set<String> executedTools,
                                   List<AgentThought> previousThoughts,
                                   boolean earlyPhase) {
        Set<String> remaining = new LinkedHashSet<>(requiredTools);
        remaining.removeAll(executedTools);

        List<String> recentObservations = previousThoughts.stream()
            .sorted(Comparator.comparingInt(AgentThought::getStepNumber).reversed())
            .limit(2)
            .map(thought -> shorten(thought.getObservation(), 120))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        logger.info("🧭 Step {} 思考议题: chapter={}, earlyPhase={}, userAdjustment={}, 剩余必查工具={}, 已执行工具={}, 最近观察={}",
            step,
            chapterNumber,
            earlyPhase,
            shorten(userAdjustment, 120),
            remaining,
            executedTools,
            recentObservations);
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    /**
     * 🆕 增强的核心上下文预加载（决策层需要更多信息）
     */
    private void prefetchCoreContextEnhanced(Long novelId,
                                              Integer chapterNumber,
                                              WritingContext.WritingContextBuilder contextBuilder,
                                              Set<String> executedTools) {
        logger.info("📥 开始预加载核心上下文（增强版）");
        
        // 1. 大纲
        prefetchTool("getOutline", () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            return params;
        }, novelId, contextBuilder, executedTools);

        // 2. 卷蓝图
        prefetchTool("getVolumeBlueprint", () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapterNumber", chapterNumber);
            return params;
        }, novelId, contextBuilder, executedTools);

        // 3. 最近章节（getRecentChapters会返回前1章完整 + 20-30章摘要）
        prefetchTool("getRecentChapters", () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("currentChapter", chapterNumber);
            // 请求更多摘要
            params.put("summaryLimit", 30);
            logger.info("🔍 准备查询最近章节: novelId={}, currentChapter={}, summaryLimit=30", novelId, chapterNumber);
            return params;
        }, novelId, contextBuilder, executedTools);
        
        logger.info("✅ 核心上下文预加载完成");
    }
    
    /**
     * 旧版本保留（兼容性）
     */
    @Deprecated
    private void prefetchCoreContext(Long novelId,
                                     Integer chapterNumber,
                                     WritingContext.WritingContextBuilder contextBuilder,
                                     Set<String> executedTools) {
        prefetchCoreContextEnhanced(novelId, chapterNumber, contextBuilder, executedTools);
    }
    
    /**
     * 🆕 强制预加载图谱数据（不依赖AI决策，确保图谱上下文不为空）
     * 这是解决"图谱上下文总是为空"问题的关键方法
     */
    private void prefetchGraphData(Long novelId,
                                   Integer chapterNumber,
                                   WritingContext.WritingContextBuilder contextBuilder,
                                   Set<String> executedTools,
                                   boolean earlyPhase) {
        logger.info("📊 开始强制预加载图谱数据: novelId={}, chapter={}, earlyPhase={}", novelId, chapterNumber, earlyPhase);
        
        // 1. 历史事件（核心上下文，所有章节必查）
        prefetchTool("getRelevantEvents", () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapterNumber", chapterNumber);
            params.put("limit", 10);
            return params;
        }, novelId, contextBuilder, executedTools);
        
        // 2. 未解决的伏笔（中后期）
        if (!earlyPhase) {
            prefetchTool("getUnresolvedForeshadows", () -> {
                Map<String, Object> params = new HashMap<>();
                params.put("novelId", novelId);
                params.put("chapterNumber", chapterNumber);
                return params;
            }, novelId, contextBuilder, executedTools);
        }
        
        // 3. 世界规则（所有章节）
        prefetchTool("getWorldRules", () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            return params;
        }, novelId, contextBuilder, executedTools);
        
        // 4. 叙事节奏（中后期必查）
        if (!earlyPhase) {
            prefetchTool("getNarrativeRhythm", () -> {
                Map<String, Object> params = new HashMap<>();
                params.put("novelId", novelId);
                params.put("chapterNumber", chapterNumber);
                return params;
            }, novelId, contextBuilder, executedTools);
        }
        
        // 5. 情节线状态（中后期）
        if (!earlyPhase) {
            prefetchTool("getPlotlineStatus", () -> {
                Map<String, Object> params = new HashMap<>();
                params.put("novelId", novelId);
                params.put("chapterNumber", chapterNumber);
                return params;
            }, novelId, contextBuilder, executedTools);
        }
        
        // 6. 冲突弧线状态（中后期）
        if (!earlyPhase) {
            prefetchTool("getConflictArcStatus", () -> {
                Map<String, Object> params = new HashMap<>();
                params.put("novelId", novelId);
                params.put("chapterNumber", chapterNumber);
                return params;
            }, novelId, contextBuilder, executedTools);
        }
        
        // 7. 人物成长弧线（中后期）
        if (!earlyPhase) {
            prefetchTool("getCharacterArcStatus", () -> {
                Map<String, Object> params = new HashMap<>();
                params.put("novelId", novelId);
                params.put("chapterNumber", chapterNumber);
                return params;
            }, novelId, contextBuilder, executedTools);
        }
        
        logger.info("✅ 图谱数据预加载完成");
    }

    private void prefetchTool(String toolName,
                              Supplier<Map<String, Object>> argsSupplier,
                              Long novelId,
                              WritingContext.WritingContextBuilder contextBuilder,
                              Set<String> executedTools) {
        try {
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                logger.warn("⚠️ 核心上下文工具不存在: {}", toolName);
                return;
            }
            Map<String, Object> args = argsSupplier.get();
            Object result = tool.execute(args);
            storeToolResult(toolName, result, contextBuilder);
            executedTools.add(toolName);
            
            // 🔧 调试日志：打印查询结果摘要
            if ("getRecentChapters".equals(toolName) && result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> chapterResult = (Map<String, Object>) result;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fullChapters = (List<Map<String, Object>>) chapterResult.get("recentFullChapters");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> summaries = (List<Map<String, Object>>) chapterResult.get("recentSummaries");
                String fullRange = (String) chapterResult.get("fullChapterRange");
                String summaryRange = (String) chapterResult.get("summaryRange");
                
                if (fullChapters != null && !fullChapters.isEmpty()) {
                    logger.info("📖 完整内容: {} ({} 章)", fullRange != null ? fullRange : "未知范围", fullChapters.size());
                }
                if (summaries != null && !summaries.isEmpty()) {
                    logger.info("📄 章节概括: {} ({} 章)", summaryRange != null ? summaryRange : "未知范围", summaries.size());
                }
                if ((fullChapters == null || fullChapters.isEmpty()) && (summaries == null || summaries.isEmpty())) {
                    logger.warn("⚠️ 未查询到任何章节内容或概括");
                }
            }
            
            logger.info("📥 已预取核心上下文: {}", toolName);
        } catch (Exception e) {
            logger.error("❌ 预取核心上下文失败: {}", toolName, e);
        }
    }

    private void appendCoreFocus(StringBuilder prompt, WritingContext context) {
        Map<String, Object> coreSummary = context != null ? context.getCoreNarrativeSummary() : null;
        Map<String, Object> meta = null;
        if (coreSummary != null && coreSummary.get("meta") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metaMap = (Map<String, Object>) coreSummary.get("meta");
            meta = metaMap;
        }

        prompt.append("- 活跃冲突线: ").append(formatMetaLabel(meta, "activeConflictName", "activeConflict"))
              .append("\n");
        prompt.append("- 活跃情节线: ").append(formatMetaLabel(meta, "activePlotlineName", "activePlotline"))
              .append("\n");
        prompt.append("- 活跃人物弧: ").append(formatMetaLabel(meta, "activeCharacterArcName", "activeCharacterArc"))
              .append("\n");
        prompt.append("- 整体大纲、卷蓝图、核心剧情纪要\n");
        prompt.append("- 最近3章全文 + 近20-30章摘要\n");
    }

    private void appendContextDigest(StringBuilder prompt, WritingContext context) {
        if (context == null) {
            return;
        }

        TokenBudget budget = TokenBudget.builder().build();

        String core = clip(budget, context.getCoreSettings(), THINKING_OUTLINE_TOKENS);
        if (StringUtils.isNotBlank(core)) {
            prompt.append("【核心设定提要】\n").append(core).append("\n\n");
        }

        Map<String, Object> volume = context.getVolumeBlueprint();
        if (volume != null && !volume.isEmpty()) {
            String blueprint = clip(budget, safeJson(volume), THINKING_BLUEPRINT_TOKENS);
            if (StringUtils.isNotBlank(blueprint)) {
                prompt.append("【卷蓝图要点】\n").append(blueprint).append("\n\n");
            }
        }

        Map<String, Object> coreSummary = context.getCoreNarrativeSummary();
        if (coreSummary != null && !coreSummary.isEmpty()) {
            String summary = clip(budget, safeJson(coreSummary), THINKING_CORE_SUMMARY_TOKENS);
            if (StringUtils.isNotBlank(summary)) {
                prompt.append("【核心剧情锚点】\n").append(summary).append("\n\n");
            }
        }

        Map<String, Object> plan = context.getChapterPlan();
        if (plan != null && !plan.isEmpty()) {
            String planDigest = clip(budget, safeJson(plan), THINKING_PLAN_TOKENS);
            if (StringUtils.isNotBlank(planDigest)) {
                prompt.append("【章节计划摘要】\n").append(planDigest).append("\n\n");
            }
        }

        appendRecentSummariesDigest(prompt, context.getRecentSummaries(), budget);
        appendGraphDigest(prompt, "高优先级事件", context.getPrioritizedEvents(), 5, budget);
        appendGraphDigest(prompt, "待回收伏笔", context.getUnresolvedForeshadows(), 5, budget);
        appendCharacterProfilesDigest(prompt, context.getCharacterProfiles(), budget);
    }

    private void appendRecentSummariesDigest(StringBuilder prompt,
                                             List<Map<String, Object>> summaries,
                                             TokenBudget budget) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }

        prompt.append("【最近章节摘要】\n");
        int start = Math.max(0, summaries.size() - 3);
        for (int i = start; i < summaries.size(); i++) {
            Map<String, Object> summary = summaries.get(i);
            if (summary == null) {
                continue;
            }
            Object chapterNumber = summary.getOrDefault("chapterNumber", "?");
            Object raw = summary.containsKey("summary") ? summary.get("summary") : summary.get("content");
            String clipped = clip(budget, stringValue(raw), THINKING_RECENT_SUMMARY_TOKENS);
            if (StringUtils.isNotBlank(clipped)) {
                prompt.append("- 第").append(chapterNumber).append("章：").append(clipped).append("\n");
            }
        }
        prompt.append("\n");
    }

    private void appendGraphDigest(StringBuilder prompt,
                                   String title,
                                   List<GraphEntity> entities,
                                   int limit,
                                   TokenBudget budget) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        prompt.append("【").append(title).append("】\n");
        int count = 0;
        for (GraphEntity entity : entities) {
            if (entity == null) {
                continue;
            }
            prompt.append("- ");
            String type = stringValue(entity.getType());
            prompt.append(StringUtils.isNotBlank(type) ? type : "剧情点");
            if (entity.getChapterNumber() != null) {
                prompt.append("·第").append(entity.getChapterNumber()).append("章");
            }
            Map<String, Object> props = entity.getProperties();
            String name = firstNonBlank(props, "title", "name", "label", "id");
            if (StringUtils.isNotBlank(name)) {
                prompt.append("：").append(name);
            }
            String desc = clip(budget, stringValue(props != null ? props.get("description") : null), THINKING_EVENT_TOKENS);
            if (StringUtils.isNotBlank(desc)) {
                prompt.append(" —— ").append(desc);
            }
            prompt.append("\n");
            count++;
            if (count >= limit) {
                break;
            }
        }
        prompt.append("\n");
    }

    private void appendCharacterProfilesDigest(StringBuilder prompt,
                                               List<Map<String, Object>> profiles,
                                               TokenBudget budget) {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }

        prompt.append("【角色档案要点】\n");
        int limit = Math.min(profiles.size(), 3);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> profile = profiles.get(i);
            if (profile == null) {
                continue;
            }
            String name = stringValue(profile.getOrDefault("name", profile.get("characterName")));
            if (StringUtils.isBlank(name)) {
                name = "角色";
            }
            String traits = stringValue(profile.get("coreTraits"));
            if (StringUtils.isBlank(traits)) {
                traits = stringValue(profile.get("persona"));
            }
            if (StringUtils.isBlank(traits)) {
                traits = stringValue(profile.get("summary"));
            }
            String clipped = clip(budget, traits, THINKING_PROFILE_TOKENS);
            prompt.append("- ").append(name);
            if (StringUtils.isNotBlank(clipped)) {
                prompt.append("：").append(clipped);
            }
            prompt.append("\n");
        }
        prompt.append("\n");
    }

    private String clip(TokenBudget budget, String text, int tokenLimit) {
        if (budget == null || StringUtils.isBlank(text)) {
            return null;
        }
        String truncated = budget.truncate(text, tokenLimit);
        return StringUtils.isNotBlank(truncated) ? truncated : null;
    }

    private String safeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private String firstNonBlank(Map<String, Object> props, String... keys) {
        if (props == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (props.containsKey(key)) {
                String value = stringValue(props.get(key));
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private boolean isEarlyChapter(Integer chapterNumber) {
        return chapterNumber == null || chapterNumber <= 3;
    }

    private String formatChapterIntent(WritingContext context) {
        if (context == null || context.getChapterIntent() == null || context.getChapterIntent().isEmpty()) {
            return "未设定";
        }
        Map<String, Object> intent = context.getChapterIntent();
        String primaryFocus = stringOrDefault(intent.get("primaryFocus"), "PLOT");
        String beatType = stringOrDefault(intent.get("targetBeatType"), "PLOT");
        @SuppressWarnings("unchecked")
        List<String> notes = intent.get("focusNotes") instanceof List ? (List<String>) intent.get("focusNotes") : Collections.emptyList();
        String notesSummary = notes.stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .limit(2)
            .collect(Collectors.joining("；"));
        if (!notesSummary.isEmpty()) {
            return String.format("焦点:%s / 节奏:%s | 要点:%s", primaryFocus, beatType, notesSummary);
        }
        return String.format("焦点:%s / 节奏:%s", primaryFocus, beatType);
    }

    private String formatRhythmSummary(WritingContext context) {
        if (context == null || context.getNarrativeRhythm() == null) {
            return "暂无节奏分析";
        }
        Map<String, Object> rhythm = context.getNarrativeRhythm();
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = rhythm.get("metrics") instanceof Map ? (Map<String, Object>) rhythm.get("metrics") : Collections.emptyMap();
        double conflictRatio = extractRatio(metrics.get("conflictRatio"));
        double plotRatio = extractRatio(metrics.get("plotRatio"));
        double characterRatio = extractRatio(metrics.get("characterRatio"));
        boolean fatigue = metrics.get("conflictFatigue") instanceof Boolean && (Boolean) metrics.get("conflictFatigue");
        @SuppressWarnings("unchecked")
        List<String> recommendations = rhythm.get("recommendations") instanceof List ? (List<String>) rhythm.get("recommendations") : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("冲突%.0f%% / 主线%.0f%% / 人物%.0f%%", conflictRatio * 100, plotRatio * 100, characterRatio * 100));
        if (fatigue) {
            sb.append("，提示：冲突密度偏高需缓冲");
        }
        if (!recommendations.isEmpty()) {
            sb.append("，建议：").append(recommendations.get(0));
        }
        return sb.toString();
    }

    private String metaLookup(Map<String, Object> meta, String key) {
        if (meta == null) {
            return null;
        }
        Object value = meta.get(key);
        return value != null ? value.toString() : null;
    }

    private String formatMetaLabel(Map<String, Object> meta, String nameKey, String idKey) {
        String name = metaLookup(meta, nameKey);
        String id = metaLookup(meta, idKey);
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return stringOrDefault(id, "未标记");
    }

    private String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? fallback : str;
    }

    private double extractRatio(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * 解析AI的决策
     */
    private AgentDecision parseAIDecision(String aiResponse) {
        try {
            // 尝试提取JSON部分
            int jsonStart = aiResponse.indexOf("{");
            int jsonEnd = aiResponse.lastIndexOf("}") + 1;
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = aiResponse.substring(jsonStart, jsonEnd);
                @SuppressWarnings("unchecked")
                Map<String, Object> decisionMap = objectMapper.readValue(jsonStr, Map.class);
                
                return AgentDecision.builder()
                    .reasoning((String) decisionMap.get("reasoning"))
                    .action((String) decisionMap.get("action"))
                    .actionArgs(decisionMap.get("args") != null ? decisionMap.get("args").toString() : "")
                    .build();
            }
        } catch (Exception e) {
            logger.error("解析AI决策失败，使用兜底策略", e);
        }
        
        // 兜底：直接开始写作
        return AgentDecision.builder()
            .reasoning("解析失败，使用默认策略")
            .action("WRITE")
            .actionArgs("")
            .build();
    }
    
    /**
     * 解析工具参数
     */
    private Map<String, Object> parseToolArgs(String argsStr, Long novelId, Integer chapterNumber) {
        Map<String, Object> args = new HashMap<>();
        args.put("novelId", novelId);
        args.put("chapterNumber", chapterNumber);
        
        // 如果有额外参数，尝试解析
        if (argsStr != null && !argsStr.isEmpty() && !"null".equalsIgnoreCase(argsStr.trim())) {
            boolean parsed = false;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extraArgs = objectMapper.readValue(argsStr, Map.class);
                if (extraArgs != null) {
                    args.putAll(extraArgs);
                }
                parsed = true;
            } catch (Exception e) {
                Map<String, Object> lenient = parseLenientArgs(argsStr);
                if (!lenient.isEmpty()) {
                    args.putAll(lenient);
                    parsed = true;
                }
                if (!parsed) {
                    logger.warn("解析工具参数失败: {}", argsStr);
                } else {
                    logger.info("🔎 已使用宽松模式解析工具参数: {}", argsStr);
                }
            }
        }
        
        return args;
    }

    private Map<String, Object> parseLenientArgs(String argsStr) {
        Map<String, Object> result = new HashMap<>();
        if (argsStr == null || argsStr.trim().isEmpty()) {
            return result;
        }
        String trimmed = argsStr.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        int index = 0;
        while (index < trimmed.length()) {
            int eqIndex = findNextCharOutsideBracket(trimmed, '=', index);
            if (eqIndex == -1) {
                break;
            }
            String key = trimmed.substring(index, eqIndex).trim();
            index = eqIndex + 1;
            if (index >= trimmed.length()) {
                result.put(key, "");
                break;
            }

            char ch = trimmed.charAt(index);
            if (ch == '[') {
                int endBracket = findMatchingBracket(trimmed, index);
                if (endBracket == -1) {
                    endBracket = trimmed.length() - 1;
                }
                String listContent = trimmed.substring(index + 1, endBracket);
                List<String> values = parseListValues(listContent);
                result.put(key, values);
                index = endBracket + 1;
            } else {
                int commaIndex = findNextCharOutsideBracket(trimmed, ',', index);
                String value;
                if (commaIndex == -1) {
                    value = trimmed.substring(index);
                    index = trimmed.length();
                } else {
                    value = trimmed.substring(index, commaIndex);
                    index = commaIndex + 1;
                }
                result.put(key, stripQuotes(value.trim()));
            }

            while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) {
                index++;
            }
            if (index < trimmed.length() && trimmed.charAt(index) == ',') {
                index++;
            }
            while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) {
                index++;
            }
        }
        return result;
    }

    private int findNextCharOutsideBracket(String text, char target, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && ch == target) {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingBracket(String text, int startIndex) {
        int depth = 0;
        for (int i = startIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<String> parseListValues(String content) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == ',' && depth == 0) {
                String value = stripQuotes(current.toString().trim());
                if (!value.isEmpty()) {
                    values.add(value);
                }
                current.setLength(0);
            } else {
                if (ch == '[') {
                    depth++;
                } else if (ch == ']') {
                    depth = Math.max(0, depth - 1);
                }
                current.append(ch);
            }
        }
        String tail = stripQuotes(current.toString().trim());
        if (!tail.isEmpty()) {
            values.add(tail);
        }
        return values;
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Map<String, Object> enrichToolArgs(String action,
                                               Map<String, Object> args,
                                               WritingContext snapshot,
                                               String userAdjustment) {
        if (args == null) {
            args = new HashMap<>();
        }
        return args;
    }
    
    /**
     * 存储工具结果到上下文
     */
    @SuppressWarnings("unchecked")
    private void storeToolResult(String toolName, Object result, WritingContext.WritingContextBuilder builder) {
        switch (toolName) {
            case "getOutline":
                if (result instanceof Map) {
                    builder.coreSettings((String) ((Map<String, Object>) result).get("coreSettings"));
                }
                break;
            case "getVolumeBlueprint":
                if (result instanceof Map) {
                    builder.volumeBlueprint((Map<String, Object>) result);
                }
                break;
            case "getRelevantEvents":
                if (result instanceof List) {
                    builder.relevantEvents((List<GraphEntity>) result);
                }
                break;
            case "getUnresolvedForeshadows":
                if (result instanceof List) {
                    builder.unresolvedForeshadows((List<GraphEntity>) result);
                }
                break;
            case "getWorldRules":
                if (result instanceof List) {
                    builder.worldRules((List<GraphEntity>) result);
                }
                break;
            case "getRecentChapters":
                if (result instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) result;
                    if (data.get("recentFullChapters") instanceof List) {
                        builder.recentFullChapters((List<Map<String, Object>>) data.get("recentFullChapters"));
                    }
                    if (data.get("recentSummaries") instanceof List) {
                        builder.recentSummaries((List<Map<String, Object>>) data.get("recentSummaries"));
                    }
                } else if (result instanceof List) {
                    builder.recentFullChapters((List<Map<String, Object>>) result);
                }
                break;
            case "getPlotlineStatus":
                if (result instanceof List) {
                    builder.plotlineStatus((List<GraphEntity>) result);
                }
                break;
            case "getNarrativeRhythm":
                if (result instanceof Map) {
                    builder.narrativeRhythm((Map<String, Object>) result);
                }
                break;
            case "generateCharacterProfile":
                Map<String, Object> profile = result instanceof Map ? (Map<String, Object>) result : null;
                if (profile != null) {
                    List<Map<String, Object>> profiles = builder.build().getCharacterProfiles();
                    if (profiles == null) {
                        profiles = new ArrayList<>();
                    } else {
                        profiles = new ArrayList<>(profiles);
                    }
                    profiles.add(profile);
                    builder.characterProfiles(profiles);
                }
                break;
            case "getConflictArcStatus":
                if (result instanceof List) {
                    builder.conflictArcs((List<GraphEntity>) result);
                }
                break;
            case "getCharacterArcStatus":
                if (result instanceof List) {
                    builder.characterArcs((List<GraphEntity>) result);
                }
                break;
            case "getPerspectiveHistory":
                if (result instanceof List) {
                    builder.perspectiveHistory((List<GraphEntity>) result);
                }
                break;
        }
    }
    
    private <T> List<T> mergeLists(List<T> original, List<T> incoming) {
        List<T> merged = original != null ? new ArrayList<>(original) : new ArrayList<>();
        if (incoming != null) {
            merged.addAll(incoming);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /**
     * AI决策结果
     */
    @lombok.Data
    @lombok.Builder
    private static class AgentDecision {
        private String reasoning;
        private String action;
        private String actionArgs;
    }
}

