package com.novel.agentic.service;

import com.novel.agentic.model.GraphEntity;
import com.novel.agentic.model.WritingContext;
import com.novel.agentic.model.TokenBudget;
import com.novel.domain.entity.Novel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 结构化消息构建器
 * 将写作上下文拆分为多条清晰的消息，避免角色混淆
 */
@Service
public class StructuredMessageBuilder {

    private static final Logger logger = LoggerFactory.getLogger(StructuredMessageBuilder.class);

    @Autowired
    private PromptAssembler promptAssembler;

    @Autowired(required = false)
    private com.novel.agentic.service.graph.IGraphService graphService;



    /**
     * 为直接写作模式构建图谱上下文（简化版）
     */
    private String buildGraphContextForDirectWriting(WritingContext context) {
        StringBuilder body = new StringBuilder();
        boolean hasContent = false;

        // 历史事件
        if (context.getRelevantEvents() != null && !context.getRelevantEvents().isEmpty()) {
            body.append("【历史事件】\n");
            context.getRelevantEvents().stream().limit(10).forEach(event -> {
                Map<String, Object> props = event.getProperties();
                body.append("- [第").append(event.getChapterNumber()).append("章] ")
                    .append(safeString(props.get("description"), "事件描述"));

                Object location = props.get("location");
                if (location != null && StringUtils.isNotBlank(location.toString())) {
                    body.append(" | 地点：").append(location);
                }

                Object participants = props.get("participants");
                if (participants != null) {
                    body.append(" | 参与者：").append(participants);
                }
                body.append("\n");
            });
            body.append("\n");
            hasContent = true;
        }

        // 待回收伏笔
        if (context.getUnresolvedForeshadows() != null && !context.getUnresolvedForeshadows().isEmpty()) {
            body.append("【待回收伏笔】\n");
            context.getUnresolvedForeshadows().stream().limit(5).forEach(foreshadow -> {
                Map<String, Object> props = foreshadow.getProperties();
                body.append("- ").append(safeString(props.get("description"), "伏笔描述"));
                Object planted = props.get("plantedAt");
                if (planted != null) {
                    body.append("（埋于第").append(planted).append("章）");
                }
                body.append("\n");
            });
            body.append("\n");
            hasContent = true;
        }

        if (!hasContent) {
            return "";
        }

        return body.toString();
    }



    /**
     * 🆕 从推理意图（plotIntent JSON）构建写作消息
     * 替代原来的"推理 → 生成Markdown章纲 → 写作"流程
     *
     * 架构：章纲（方向） + 完整上下文（约束和细节）
     * - 章纲提供：本章剧情方向、关键剧情点、伏笔操作
     * - 上下文提供：核心设定、卷蓝图、图谱、角色档案、最近章节
     * - 确保：符合世界观、人设一致、剧情连贯、有伏笔
     */
    public List<Map<String, String>> buildMessagesFromIntent(
            Novel novel,
            WritingContext context,
            Map<String, Object> intent,
            Integer chapterNumber,
            String stylePromptFile,
            Long promptTemplateId
    ) {
        List<Map<String, String>> messages = new ArrayList<>();

        // Message 1: System - 基础写作规则 + 风格
        String systemPrompt = buildSystemPrompt(null, chapterNumber, stylePromptFile, promptTemplateId);
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            logger.warn("系统提示词为空！使用默认提示词");
            systemPrompt = "你是一位专业的网文小说家AI助手。请根据章节意图和上下文创作高质量的小说章节内容。";
        }
        logger.info("系统提示词长度: {}字 (使用: {})", systemPrompt.length(),
            stylePromptFile != null ? stylePromptFile : "默认");
        messages.add(createMessage("user", systemPrompt));



        // Message 2: 小说基础信息
        String basicInfo = buildBasicInfo(novel, chapterNumber);
        messages.add(createMessage("system", basicInfo));

        // Message 3: 整体大纲（仅做参考，禁止开天眼）
        // 原“核心设定”消息暂时改为输出整体大纲，帮助AI理解全局走向，但不能提前写后面章节内容
        if (false) {
//        if (novel != null && StringUtils.isNotBlank(novel.getOutline())) {
            String outline = novel.getOutline();
            StringBuilder sb = new StringBuilder();
            sb.append("【整体大纲（仅供参考，禁止开天眼）】\n");
            sb.append("下面是全书的整体大纲，用于帮助你理解全局剧情节奏和后续大致走向。\n");
            sb.append("写本章内容时：\n");
            sb.append("- 只能使用当前进度之前已经出现或合理铺垫过的信息；\n");
            sb.append("- 不能提前写后面章节才会出现的设定、角色发展、伏笔回收或重大反转；\n");
            sb.append("- 不能凭大纲“开天眼”，一次性剧透或跳跃推进剧情。\n\n");
            sb.append(outline).append("\n");
            messages.add(createMessage("system", sb.toString()));
            logger.info("已添加整体大纲 ({}字)", outline.length());
        } else if (context != null && StringUtils.isNotBlank(context.getCoreSettings())) {
            String core = context.getCoreSettings();
            StringBuilder sb = new StringBuilder();
            sb.append("【核心设定】\n");
            sb.append(core).append("\n");
            messages.add(createMessage("system", sb.toString()));
            logger.info("已添加核心设定作为整体大纲 ({}字)", core.length());
        }

        // Message 4: 卷蓝图（如果有）
        if (context != null) {
            String volumeBlueprint = buildVolumeBlueprintMessage(context);
            if (StringUtils.isNotBlank(volumeBlueprint)) {
                messages.add(createMessage("system", volumeBlueprint));
                logger.info("已添加卷蓝图");
            }
        }

        // Message 5: 最近章节完整内容和概要
        if (context != null) {
            addRecentChapterMessages(context, messages);
        }

        // Message 6: 角色档案（如果有）
        if (context != null) {
            String characters = buildWorldAndCharacters(context);
            if (StringUtils.isNotBlank(characters)) {
                messages.add(createMessage("system", characters));
                logger.info("已添加角色信息");
            }
        }

        // Message 7: 状态硬约束（核心记忆账本）
//        if (context != null) {
//            String stateConstraints = buildStateConstraints(context);
//            if (StringUtils.isNotBlank(stateConstraints)) {
//                messages.add(createMessage("system", stateConstraints));
//                logger.info("已添加状态硬约束");
//            }
//        }

        if (context != null) {
            String characterMindmap = buildCharacterMindmap(context);
            if (StringUtils.isNotBlank(characterMindmap)) {
                messages.add(createMessage("system", characterMindmap));
                logger.info("已添加人物思维导图");
            }
        }

        // Message 8: 图谱上下文（历史事件、伏笔等）
        if (context != null) {
            String graphContext = buildGraphContextForDirectWriting(context);
            if (StringUtils.isNotBlank(graphContext)) {
                messages.add(createMessage("system", graphContext));
                logger.info("已添加图谱上下文");
            }
        }

        // Message 9: 章节意图（来自推理或预生成章纲）
        StringBuilder intentMsg = new StringBuilder();
        intentMsg.append("【本章创作方向】\n");
        if (intent != null) {
            // 使用 direction 作为本章剧情方向（包含关键剧情点）
            Object direction = intent.get("direction");
            if (direction != null) {
                intentMsg.append("本章剧情方向：\n").append(direction).append("\n\n");
            }

            // 伏笔操作
            Object foreshadowAction = intent.get("foreshadowAction");
            Object foreshadowDetail = intent.get("foreshadowDetail");
            if (foreshadowAction != null && !"NONE".equals(foreshadowAction.toString())) {
                intentMsg.append("伏笔操作：").append(foreshadowAction).append("\n");
                if (foreshadowDetail instanceof Map) {
                    Map<?, ?> detail = (Map<?, ?>) foreshadowDetail;
                    Object content = detail.get("content");
                    if (content != null) {
                        intentMsg.append("伏笔内容：").append(content).append("\n");
                    }
                }
                intentMsg.append("\n");
            }
        }
        messages.add(createMessage("user", intentMsg.toString()));

//        //开篇提速
//        String openingBooster = buildOpeningBooster(chapterNumber);
//        if (StringUtils.isNotBlank(openingBooster)) {
//            logger.info("添加开篇提速指令（第{}章）", chapterNumber);
//            messages.add(createMessage("system", openingBooster));
//        }

        // Message 10: 写作任务说明
        StringBuilder taskDesc = new StringBuilder();
        taskDesc.append("请开始创作第").append(chapterNumber).append("章。 \n");
//        taskDesc.append("遵循上面的指令,按照前面的上下文信息开始写作,保证逻辑通畅,衔接上一章剧情;如果上一章结尾和【本章创作方向】有出入 还要衔接上章为主 在慢慢按【本章创作方向】去编写;同时需要考虑逻辑性; 不能机械降神 不能引入超脱剧本的支线和设定 按照现有剧情设定去推理。");
        messages.add(createMessage("user",taskDesc.toString()));
        //字数限制
        String wordCountLimit = buildWordCountLimitSimple(novel);
        messages.add(createMessage("user", wordCountLimit));

        // 作者本次特别构思 / 用户调整指令（放在最底部）
        if (context != null && StringUtils.isNotBlank(context.getUserAdjustment())) {
            String userAdj = context.getUserAdjustment().trim();
            // 如果是"开始"，跳过不添加
            if (!"开始".equals(userAdj)) {
                StringBuilder ua = new StringBuilder();
                ua.append("【作者本次特别构思 / 临时要求】\n");
                ua.append(context.getUserAdjustment()).append("\n\n");
                messages.add(createMessage("system", ua.toString()));
                logger.info("已添加用户调整指令（放在最底部）");
            } else {
                logger.info("用户调整指令为'开始'，跳过不添加");
            }
        }

        // 用户提供的关联素材（参考文件和关联文档）
        if (context != null && context.getReferenceContents() != null && !context.getReferenceContents().isEmpty()) {
            String refMessage = buildUserReferenceMessage(context.getReferenceContents());
            if (StringUtils.isNotBlank(refMessage)) {
                messages.add(createMessage("system", refMessage));
                logger.info("已添加用户关联素材（{}项）", context.getReferenceContents().size());
            }
        }

        logger.info("意图驱动写作消息构建完成: 共{}条消息", messages.size());

        // 详细日志
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> msg = messages.get(i);
            String role = msg.get("role");
            String content = msg.get("content");
            String preview = content != null && content.length() > 80
                ? content.substring(0, 80).replaceAll("\n", " ") + "..."
                : (content != null ? content.replaceAll("\n", " ") : "null");
            logger.info("  [Message {}] role={}, 内容摘要: {}", i + 1, role, preview);
        }

        return messages;
    }





    /**
     * 构建简化的字数限制
     * 基于小说配置的每章目标字数（novels.words_per_chapter），若无配置则回退到 2500 字
     */
    private String buildWordCountLimitSimple(Novel novel) {
        int base = 2500;
        if (novel != null && novel.getWordsPerChapter() != null && novel.getWordsPerChapter() > 0) {
            base = novel.getWordsPerChapter();
        }

        int targetWords = base;
        int maxWords = base + 200; // 上下浮动约200字，这里设置硬上限为+200

        StringBuilder sb = new StringBuilder();
        sb.append("【生成的小说字数范围】\n");
        sb.append("- 范围：").append(targetWords).append(" 字（可上下浮动约 200 字）\n");
        return sb.toString();
    }

    /**
     * 旧版方法：构建结构化的多消息提示词（保留用于兼容）
     */
    @Deprecated
    public List<Map<String, String>> buildMessages(Novel novel, WritingContext context, Integer chapterNumber, String stylePromptFile) {
        List<Map<String, String>> messages = new ArrayList<>();
        TokenBudget budget = TokenBudget.builder().build();

        // Message 1: System - 底层规则 + 单一风格
        String systemPrompt = buildSystemPrompt(null, chapterNumber, stylePromptFile, null);
        if (StringUtils.isBlank(systemPrompt)) {
            logger.warn("系统提示词为空！可能是提示词文件读取失败");
            systemPrompt = "你是一位专业的网文小说家AI助手。请根据以下信息创作高质量的小说章节内容，注意保持剧情连贯、人物性格一致。";
        }
        logger.info("系统提示词长度: {}字 (使用: {})", systemPrompt.length(),
            stylePromptFile != null ? stylePromptFile : "默认");
        messages.add(createMessage("system", budget.truncate(systemPrompt, budget.getMaxSystemPrompt())));

        // Message 2: 开篇提速指令（前三章专用）
        String openingBooster = buildOpeningBooster(chapterNumber);
        if (StringUtils.isNotBlank(openingBooster)) {
            logger.info("添加开篇提速指令（第{}章）", chapterNumber);
            messages.add(createMessage("system", openingBooster));
        }

        // Message 3: 小说基础信息（前三章包含简介）
        String basicInfo = buildBasicInfo(novel, chapterNumber);
        messages.add(createMessage("system", basicInfo));

        // Message 4: 核心设定
        String core = context.getCoreSettings();
        if (StringUtils.isNotBlank(core)) {
            StringBuilder coreMsg = new StringBuilder();
            coreMsg.append("【核心设定】\n");
            coreMsg.append(budget.truncate(core, budget.getMaxOutline())).append("\n");
            messages.add(createMessage("system", coreMsg.toString()));
        }

        // Message 5: 卷蓝图
        String volume = buildVolumeBlueprintMessage(context);
        if (StringUtils.isNotBlank(volume)) {
            messages.add(createMessage("system", volume));
        }

        // Message 6: 图谱上下文（事件、伏笔、节奏）
//        String graphContext = buildGraphContext(context);
//        logger.info("图谱上下文长度: {}字 ({})",
//            graphContext != null ? graphContext.length() : 0,
//            StringUtils.isNotBlank(graphContext) ? "有内容" : "为空");
//        if (StringUtils.isNotBlank(graphContext)) {
//            messages.add(createMessage("system", graphContext));
//        } else {
//            logger.warn("图谱上下文为空！检查图谱数据预加载是否执行");
//        }
//
        // Message 9+: 最近章节内容（每章一个独立message，不截断）
        addRecentChapterMessages(context, messages);

        // Message N: 本章任务与执行要求
        String taskAndRequirements = buildTaskAndRequirements(context, chapterNumber);
        messages.add(createMessage("user", taskAndRequirements));

        // Message 11: 字数限制（单独一条消息）
        String wordCountLimit = buildWordCountLimit(context);
        messages.add(createMessage("user", wordCountLimit));

        logger.info("结构化消息构建完成: 共{}条消息", messages.size());

        // 详细日志：输出每条消息的摘要
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> msg = messages.get(i);
            String role = msg.get("role");
            String content = msg.get("content");
            String preview = content != null && content.length() > 80
                ? content.substring(0, 80).replaceAll("\n", " ") + "..."
                : (content != null ? content.replaceAll("\n", " ") : "null");
            logger.info("  [Message {}] role={}, 内容摘要: {}", i + 1, role, preview);
        }

        return messages;
    }

    /**
     * 构建系统提示词：底层规则 + 风格选择
     */
    private String buildSystemPrompt(String genre, Integer chapterNumber, String stylePromptFile, Long promptTemplateId) {
        return promptAssembler.assembleSystemPrompt(genre, chapterNumber, stylePromptFile, promptTemplateId);
    }

    private String buildOpeningBooster(Integer chapterNumber) {
        if (chapterNumber == null || chapterNumber > 3) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【黄金三章开局专用指令】\n");
        sb.append("- 本指令仅在全书最前面的章节生效，在这些章节中优先级最高。\n");
        sb.append("- 在不推翻作品核心设定和主线目标的前提下，可以暂时牺牲一部分节奏规划、世界观讲解顺序和细枝末节的严谨性，优先保证“好看、上瘾、爽”。\n");
        sb.append("- 读者第一次接触作品多半只看开头几屏，如果这里不立刻抓住人，后面再精彩也没人看到。\n");
        sb.append("- 一开场就让读者“掉进事件里”：用冲突、选择或异常场景切入，不要从天气、环境或大段设定说明写起。\n");
        sb.append("- 尽量让前几段就出现：主角明确的欲望或目标、需要立刻应对的压力或机会，以及做出选择带来的直接后果。\n");
        sb.append("- 必须让读者感到真实代价：关系紧张、局势恶化、资源被消耗、时间被压缩等，而不是虚空的口头威胁。\n");
        sb.append("- 可以在不推翻作品核心设定和主线目标的前提下，适度偏离卷蓝图或细节规划，以换取更强的开局吸引力；后续章节再慢慢校正。\n");
        sb.append("- 避免长篇讲解世界观或背景，把必要信息拆散，夹在动作、对话和冲突推进之中，让读者一边追剧情一边顺手理解设定。\n");
        sb.append("- 章节结尾必须留下钩子：未解决的问题、被打断的行动、危险的悬而未决、出乎意料的提议或信号等，迫使读者想“再看一小段”。\n");
        sb.append("- 语言上多用有画面感的动作和对话，少用空泛的议论和解释，让读者“看到场景在动”，而不是在听作者讲道理。\n");
        return sb.toString();
    }

    /**
     * Message 3: 小说基础信息（不包含书名，避免影响AI）
     */
    private String buildBasicInfo(Novel novel, Integer chapterNumber) {
        // 书名已移除：避免书名影响AI的创作风格
        // 题材已移除：让AI从大纲与素材中自推断风格
        return "";
    }



    /**
     * Message 5: 卷蓝图
     */
    private String buildVolumeBlueprintMessage(WritingContext context) {
        if (context.getVolumeBlueprint() == null) {
            return "";
        }

        Map<String, Object> volume = context.getVolumeBlueprint();
        StringBuilder sb = new StringBuilder();
        sb.append("【本卷故事蓝图(中心围绕这这部分和后面给的【本章创作方向】)】\n");
        sb.append("卷名：").append(volume.getOrDefault("volumeTitle", "未命名卷")).append("\n");
        sb.append("章节范围：").append(volume.getOrDefault("chapterRange", "未设定")).append("\n");

        String blueprint = String.valueOf(volume.get("blueprint"));
        if (StringUtils.isNotBlank(blueprint) && !"null".equals(blueprint)) {
            sb.append("蓝图摘要：").append(blueprint).append("\n");
        }

        Object progress = volume.get("progressDescription");
        if (progress != null) {
            sb.append("当前位置：").append(progress).append("\n");
        }

        return sb.toString();
    }

    /**
     * Message 6: 角色档案（已精简，移除世界规则）
     */
    private String buildWorldAndCharacters(WritingContext context) {
        StringBuilder body = new StringBuilder();
        boolean hasContent = false;

        // 移除世界规则，避免无效信息干扰当前章（按用户要求）

        if (context.getCharacterProfiles() != null && !context.getCharacterProfiles().isEmpty()) {
            body.append("## 关键角色\n");
            context.getCharacterProfiles().stream().forEach(profile -> {
                String name = safeString(profile.get("character_name"), "未知角色");
                if ("未知角色".equals(name)) {
                    name = safeString(profile.get("characterName"), name);
                }
                String role = safeString(profile.get("role_position"), "");
                if (StringUtils.isBlank(role)) {
                    role = safeString(profile.get("role"), "");
                }
                body.append("- ").append(name);
                if (StringUtils.isNotBlank(role)) {
                    body.append("（").append(role).append("）");
                }
                String trait = safeString(profile.get("extreme_trait"), "");
                if (StringUtils.isNotBlank(trait)) {
                    body.append(" | 核心特质：").append(trait);
                }
                body.append("\n");
            });
            hasContent = true;
        }

        if (!hasContent) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append("【角色信息】\n\n");
        result.append("提示：下面是当前图谱中已建档的核心人物。\n");
        result.append("- 当需要使用这些人物时，请优先复用这里给出的【姓名】和【身份】，不要为同一人物另起新名；\n");
        result.append("- 你可以根据本章剧情需要，从中选择少量关键角色登场，不必全部使用；\n");
        result.append("- 若需要引入全新、未来会长期出现的角色，可以自行创造新名字，并在后续章节保持一致。\n\n");
        result.append(body);
        return result.toString();
    }

    private String buildCharacterMindmap(WritingContext context) {
        if (context == null) {
            return "";
        }

        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        List<Map<String, Object>> profiles = context.getCharacterProfiles();
        if (profiles != null) {
            for (Map<String, Object> profile : profiles) {
                if (profile == null) {
                    continue;
                }
                String name = safeString(profile.get("character_name"), null);
                if (name == null || "未知角色".equals(name)) {
                    name = safeString(profile.get("characterName"), null);
                }
                if (name == null) {
                    continue;
                }
                Map<String, Object> data = merged.computeIfAbsent(name, k -> new LinkedHashMap<>());
                if (!data.containsKey("role")) {
                    String role = safeString(profile.get("role_position"), "");
                    if (StringUtils.isBlank(role)) {
                        role = safeString(profile.get("role"), "");
                    }
                    if (StringUtils.isNotBlank(role)) {
                        data.put("role", role);
                    }
                }
                if (!data.containsKey("trait")) {
                    String trait = safeString(profile.get("extreme_trait"), "");
                    if (StringUtils.isNotBlank(trait)) {
                        data.put("trait", trait);
                    }
                }
            }
        }

        List<Map<String, Object>> characterStates = context.getCharacterStates();
        if (characterStates != null) {
            for (Map<String, Object> state : characterStates) {
                if (state == null) {
                    continue;
                }
                String name = safeString(state.get("name"), null);
                if (name == null) {
                    continue;
                }
                Map<String, Object> data = merged.computeIfAbsent(name, k -> new LinkedHashMap<>());
                if (!data.containsKey("alive") && state.get("alive") != null) {
                    data.put("alive", state.get("alive"));
                }
                if (!data.containsKey("location") && state.get("location") != null) {
                    data.put("location", state.get("location"));
                }
                if (!data.containsKey("realm") && state.get("realm") != null) {
                    data.put("realm", state.get("realm"));
                }
                if (!data.containsKey("status") && state.get("status") != null) {
                    data.put("status", state.get("status"));
                }
                if (!data.containsKey("lastSeenChapter") && state.get("lastSeenChapter") != null) {
                    data.put("lastSeenChapter", state.get("lastSeenChapter"));
                }
                if (!data.containsKey("deathChapter") && state.get("deathChapter") != null) {
                    data.put("deathChapter", state.get("deathChapter"));
                }
            }
        }

        if (merged.isEmpty()) {
            Map<String, CharacterState> inferred = extractCharacterStates(context);
            if (!inferred.isEmpty()) {
                for (CharacterState st : inferred.values()) {
                    if (st == null || st.name == null) {
                        continue;
                    }
                    Map<String, Object> data = merged.computeIfAbsent(st.name, k -> new LinkedHashMap<>());
                    if (!data.containsKey("alive")) {
                        data.put("alive", st.isAlive);
                    }
                    if (!data.containsKey("location") && st.location != null) {
                        data.put("location", st.location);
                    }
                    if (!data.containsKey("realm") && st.realm != null) {
                        data.put("realm", st.realm);
                    }
                    if (!data.containsKey("deathChapter") && st.deathChapter != null) {
                        data.put("deathChapter", st.deathChapter);
                    }
                    if (!data.containsKey("lastSeenChapter") && st.lastSeenChapter != null) {
                        data.put("lastSeenChapter", st.lastSeenChapter);
                    }
                }
            }
        }

        Map<String, List<String>> relations = new LinkedHashMap<>();
        List<Map<String, Object>> relationshipStates = context.getRelationshipStates();
        if (relationshipStates != null) {
            for (Map<String, Object> rel : relationshipStates) {
                if (rel == null) {
                    continue;
                }
                String a = safeString(rel.get("a"), null);
                String b = safeString(rel.get("b"), null);
                if (a == null || b == null) {
                    continue;
                }

                // 确保关系中的人物也被纳入人物集合，避免只在关系里出现却不在列表里的情况
                merged.computeIfAbsent(a, k -> new LinkedHashMap<>());
                merged.computeIfAbsent(b, k -> new LinkedHashMap<>());

                String type = safeString(rel.get("type"), "");
                String ab = StringUtils.isNotBlank(type) ? b + "（" + type + "）" : b;
                String ba = StringUtils.isNotBlank(type) ? a + "（" + type + "）" : a;
                relations.computeIfAbsent(a, k -> new ArrayList<>()).add(ab);
                relations.computeIfAbsent(b, k -> new ArrayList<>()).add(ba);
            }
        }

        if (merged.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【人物关系导图】\n\n");
        sb.append("这里列出当前小说中已经登场过的角色名单及其关系网络，用于保持人名一致；本章未必全部出场，但不要给已有角色改名。已死亡角色禁止以活人身份登场。\n\n");

        final int MAX_LENGTH = 10000;
        java.util.List<Map.Entry<String, Map<String, Object>>> entries = new java.util.ArrayList<>(merged.entrySet());
        entries.sort((e1, e2) -> {
            String n1 = e1.getKey();
            String n2 = e2.getKey();
            Map<String, Object> d1 = e1.getValue();
            Map<String, Object> d2 = e2.getValue();
            double s1 = computeCharacterPriority(n1, d1, relations);
            double s2 = computeCharacterPriority(n2, d2, relations);
            int cmp = Double.compare(s2, s1);
            if (cmp != 0) {
                return cmp;
            }
            if (n1 == null && n2 == null) {
                return 0;
            }
            if (n1 == null) {
                return 1;
            }
            if (n2 == null) {
                return -1;
            }
            return n1.compareTo(n2);
        });

        for (Map.Entry<String, Map<String, Object>> entry : entries) {
            if (sb.length() >= MAX_LENGTH) {
                sb.append("...\n（角色列表已截断，后续多为边缘角色，可按需忽略）");
                break;
            }
            String name = entry.getKey();
            Map<String, Object> data = entry.getValue();
            if (StringUtils.isBlank(name)) {
                continue;
            }
            sb.append("- ").append(name);
            Object role = data.get("role");
            if (role != null && StringUtils.isNotBlank(role.toString())) {
                sb.append("（").append(role).append("）");
            }
            sb.append("\n");

            sb.append("  · 状态：");
            Object alive = data.get("alive");
            if (alive instanceof Boolean && !(Boolean) alive) {
                sb.append("已死亡");
            } else {
                Object status = data.get("status");
                if (status != null && StringUtils.isNotBlank(status.toString())) {
                    sb.append(status.toString());
                } else {
                    sb.append("存活");
                }
            }

            Object location = data.get("location");
            if (location != null && StringUtils.isNotBlank(location.toString())) {
                sb.append(" | 位置：").append(location);
            }

            Object realm = data.get("realm");
            if (realm != null && StringUtils.isNotBlank(realm.toString())) {
                sb.append(" | 阶段：").append(realm);
            }

            Object trait = data.get("trait");
            if (trait != null && StringUtils.isNotBlank(trait.toString())) {
                sb.append(" | 核心特质：").append(trait);
            }

            sb.append("\n");
        }

        if (relationshipStates != null && !relationshipStates.isEmpty() && sb.length() < MAX_LENGTH) {
            sb.append("\n【人物关系连线】\n");
            int edgeCount = 0;
            for (Map<String, Object> rel : relationshipStates) {
                if (rel == null) {
                    continue;
                }
                String a = safeString(rel.get("a"), null);
                String b = safeString(rel.get("b"), null);
                if (StringUtils.isBlank(a) || StringUtils.isBlank(b)) {
                    continue;
                }
                String type = safeString(rel.get("type"), "");
                sb.append("- ").append(a);
                if (StringUtils.isNotBlank(type)) {
                    sb.append(" —").append(type).append("→ ");
                } else {
                    sb.append(" → ");
                }
                sb.append(b).append("\n");

                edgeCount++;
                if (edgeCount >= 80 || sb.length() >= MAX_LENGTH) {
                    break;
                }
            }
        }

        return sb.toString().trim();
    }

    private double computeCharacterPriority(String name, Map<String, Object> data, Map<String, List<String>> relations) {
        double score = 0.0;

        if (data != null) {
            Object role = data.get("role");
            if (role != null) {
                String r = role.toString();
                if (r.contains("主角") || r.contains("男主") || r.contains("女主")) {
                    score += 100.0;
                } else if (r.contains("反派") || r.toLowerCase(Locale.ROOT).contains("boss")) {
                    score += 80.0;
                } else if (r.contains("配角")) {
                    score += 60.0;
                }
            }

            Object alive = data.get("alive");
            if (alive instanceof Boolean) {
                if ((Boolean) alive) {
                    score += 5.0;
                } else {
                    score += 2.0;
                }
            }

            Object trait = data.get("trait");
            if (trait != null && StringUtils.isNotBlank(trait.toString())) {
                score += 3.0;
            }
        }

        if (relations != null && name != null) {
            List<String> relList = relations.get(name);
            if (relList != null && !relList.isEmpty()) {
                score += Math.min(5.0, relList.size());
            }
        }

        return score;
    }


    /**
     * Message 8: 图谱上下文
     */
    private String buildGraphContext(WritingContext context) {
        StringBuilder body = new StringBuilder();
        boolean hasContent = false;

        // 🆕 优先构建状态强约束区块
        String stateConstraints = buildStateConstraints(context);
        if (StringUtils.isNotBlank(stateConstraints)) {
            body.append(stateConstraints);
            hasContent = true;
        }

        // 动态选择：根据章节意图裁剪图谱区块
        Map<String, Object> intent = context.getChapterIntent();
        String primaryFocus = intent != null ? safeString(intent.get("primaryFocus"), "") : "";
        String targetBeat = intent != null ? safeString(intent.get("targetBeatType"), "") : "";
        boolean focusConflict = primaryFocus.toUpperCase(Locale.ROOT).contains("CONFLICT") || targetBeat.toUpperCase(Locale.ROOT).contains("CLIMAX");
        boolean focusRelationship = primaryFocus.toUpperCase(Locale.ROOT).contains("CHAR") || primaryFocus.contains("关系");
        boolean focusMystery = primaryFocus.toUpperCase(Locale.ROOT).contains("MYSTERY") || primaryFocus.contains("伏笔");

        // 相关事件（含因果关系和地点信息）
        List<GraphEntity> events = context.getPrioritizedEvents() != null && !context.getPrioritizedEvents().isEmpty()
            ? context.getPrioritizedEvents()
            : context.getRelevantEvents();
        if (events != null && !events.isEmpty()) {
            body.append("## 📚 历史事件参考（上下文补充）\n");
            events.stream().limit(5).forEach(event -> {
                Map<String, Object> props = event.getProperties();
                body.append("- [第").append(event.getChapterNumber()).append("章] ")
                    .append(safeString(props.get("description"), "事件描述待补充"));

                // ⚠️ 重要：显示地点信息，用于跟踪角色位置
                Object location = props.get("location");
                if (location != null && StringUtils.isNotBlank(location.toString())) {
                    body.append(" | 📍地点：").append(location);
                }

                Object participants = props.get("participants");
                if (participants != null) {
                    body.append(" | 参与者：").append(participants);
                }
                Object tone = props.get("emotionalTone");
                if (tone != null) {
                    body.append(" | 情绪：").append(tone);
                }
                Object causalFrom = props.get("causalFrom");
                if (causalFrom != null) {
                    body.append(" | ⬅️ 前因：").append(causalFrom);
                }
                Object causalTo = props.get("causalTo");
                if (causalTo != null) {
                    body.append(" | ➡️ 后果：").append(causalTo);
                }
                body.append("\n");
            });
            body.append("\n");
            hasContent = true;
        }

        // 伏笔（仅在需要时展示）
        if (focusMystery && context.getUnresolvedForeshadows() != null && !context.getUnresolvedForeshadows().isEmpty()) {
            body.append("## 待回收伏笔\n");
            context.getUnresolvedForeshadows().stream().limit(5).forEach(foreshadow -> {
                Map<String, Object> props = foreshadow.getProperties();
                body.append("- ")
                    .append(safeString(props.get("description"), "伏笔描述"));
                Object planted = props.get("plantedAt");
                if (planted != null) {
                    body.append("（埋于").append(planted).append("）");
                }
                Object resolveWindow = props.get("suggestedResolveWindow");
                if (resolveWindow != null) {
                    body.append(" [建议回收：").append(resolveWindow).append("]");
                }
                body.append("\n");
            });
            body.append("\n");
            hasContent = true;
        }

        if (focusConflict && context.getConflictArcs() != null && !context.getConflictArcs().isEmpty()) {
            body.append("## 冲突弧线状态\n");
            context.getConflictArcs().stream().limit(3).forEach(arc -> {
                Map<String, Object> props = arc.getProperties();
                body.append("- ")
                    .append(safeString(props.get("name"), "冲突"))
                    .append(" | 阶段：")
                    .append(safeString(props.get("stage"), "推进"))
                    .append(" | 下一步：")
                    .append(safeString(props.get("nextAction"), "加码压力"))
                    .append("\n");
            });
            body.append("\n");
            hasContent = true;
        }

        if (focusRelationship && context.getCharacterArcs() != null && !context.getCharacterArcs().isEmpty()) {
            body.append("## 人物成长节点\n");
            context.getCharacterArcs().stream().limit(3).forEach(arc -> {
                Map<String, Object> props = arc.getProperties();
                body.append("- ")
                    .append(safeString(props.get("characterName"), "角色"))
                    .append(" 当前待完成：")
                    .append(safeString(props.get("pendingBeat"), "触发关键变化"))
                    .append(" | 下一个目标：")
                    .append(safeString(props.get("nextGoal"), "制造强驱动"))
                    .append("\n");
            });
            body.append("\n");
            hasContent = true;
        }

        if (focusConflict && context.getPlotlineStatus() != null && !context.getPlotlineStatus().isEmpty()) {
            body.append("## 情节线活跃度\n");
            context.getPlotlineStatus().stream().limit(4).forEach(plotline -> {
                Map<String, Object> props = plotline.getProperties();
                body.append("- ")
                    .append(safeString(props.get("name"), "支线"))
                    .append("：")
                    .append(safeString(props.get("status"), "待推进"));
                Object idle = props.get("idleDuration");
                if (idle instanceof Number) {
                    body.append("（已闲置").append(((Number) idle).intValue()).append("章）");
                }
                body.append("\n");
            });
            body.append("\n");
            hasContent = true;
        }

        // 叙事节奏
        if (context.getNarrativeRhythm() != null) {
            Map<String, Object> rhythm = context.getNarrativeRhythm();
            @SuppressWarnings("unchecked")
            List<String> recommendations = rhythm.get("recommendations") instanceof List
                ? (List<String>) rhythm.get("recommendations")
                : Collections.emptyList();

            if (!recommendations.isEmpty()) {
                body.append("## 节奏建议\n");
                recommendations.stream().limit(4).forEach(rec -> body.append("- ").append(rec).append("\n"));
                body.append("\n");
                hasContent = true;
            }
        }

        // 🧩 人物关系（基于事件共现的轻量关系图谱）
        if (events != null && !events.isEmpty() && focusRelationship) {
            Map<String, Integer> pairCount = new HashMap<>();
            for (GraphEntity e : events) {
                Object participantsObj = e.getProperties().get("participants");
                java.util.List<String> parts = new java.util.ArrayList<>();
                if (participantsObj instanceof java.util.List) {
                    for (Object p : (java.util.List<?>) participantsObj) if (p != null) parts.add(p.toString());
                } else if (participantsObj instanceof String) {
                    for (String s : participantsObj.toString().split("[,，、]")) { String t=s.trim(); if(!t.isEmpty()) parts.add(t); }
                }
                for (int i = 0; i < parts.size(); i++) {
                    for (int j = i + 1; j < parts.size(); j++) {
                        String a = parts.get(i); String b = parts.get(j);
                        String key = a.compareTo(b) < 0 ? a + "—" + b : b + "—" + a;
                        pairCount.put(key, pairCount.getOrDefault(key, 0) + 1);
                    }
                }
            }
            if (!pairCount.isEmpty()) {
                body.append("## 人物关系共现（近因）\n");
                pairCount.entrySet().stream()
                    .sorted((x,y) -> Integer.compare(y.getValue(), x.getValue()))
                    .limit(5)
                    .forEach(en -> body.append("- ").append(en.getKey()).append("：共现 ").append(en.getValue()).append(" 次\n"));
                body.append("\n");
                hasContent = true;
            }
        }

        // 🔗 因果链片段（基于事件的causalFrom/causalTo）
        if (events != null && !events.isEmpty()) {
            java.util.List<String> chains = new java.util.ArrayList<>();
            for (GraphEntity e : events) {
                Map<String, Object> p = e.getProperties();
                Object from = p.get("causalFrom");
                Object to = p.get("causalTo");
                String s = null;
                if (from != null) {
                    s = "因：" + from + " → 果：" + safeString(p.get("description"), "事件");
                }
                if (to != null) {
                    s = (s != null ? s + "；" : "") + "引出：" + to;
                }
                if (s != null) chains.add(s);
            }
            if (!chains.isEmpty()) {
                body.append("## 因果链片段\n");
                chains.stream().limit(5).forEach(line -> body.append("- ").append(line).append("\n"));
                body.append("\n");
                hasContent = true;
            }
        }

        if (!hasContent) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append("【图谱上下文】\n\n");
        result.append("使用方式：挑选与本章冲突或人物最相关的信息自然融入剧情，若无匹配可跳过。\n\n");
        result.append(body);
        return result.toString().strip();
    }

    /**
     * 🆕 添加最近章节内容到messages（每章独立一个message，不截断）
     */
    private void addRecentChapterMessages(WritingContext context, List<Map<String, String>> messages) {
        logger.info("🔍 addRecentChapterMessages - recentFullChapters: {}, recentSummaries: {}",
            context.getRecentFullChapters() != null ? context.getRecentFullChapters().size() : "null",
            context.getRecentSummaries() != null ? context.getRecentSummaries().size() : "null");

        // 1. 为每章完整内容创建独立的message（不截断）
        if (context.getRecentFullChapters() != null && !context.getRecentFullChapters().isEmpty()) {
            for (Map<String, Object> chapter : context.getRecentFullChapters()) {
                Object chapterNum = chapter.get("chapterNumber");
                Object title = chapter.get("title");
                String content = String.valueOf(chapter.get("content"));

                StringBuilder sb = new StringBuilder();
                sb.append("上一章剧情回顾【第").append(chapterNum).append("章完整内容】\n\n");
                if (title != null) {
                    sb.append("标题：").append(title).append("\n\n");
                }
                sb.append(content); // 不截断，完整内容


                messages.add(createMessage("system", sb.toString()));
                logger.info("✅ 已添加第{}章完整内容（{}字）", chapterNum, content.length());
            }
        } else {
            logger.warn("⚠️ recentFullChapters为空，无法添加最近章节内容");
        }

        // 2. 将所有概要合并到一个message（概要本身就很短）
        if (context.getRecentSummaries() != null && !context.getRecentSummaries().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n【以往大致剧情回顾】(了解前因后果)\n\n");

            // 显示最近10章的概括
            int displayCount = Math.min(10, context.getRecentSummaries().size());
            int start = Math.max(0, context.getRecentSummaries().size() - displayCount);

            for (int i = start; i < context.getRecentSummaries().size(); i++) {
                Map<String, Object> summary = context.getRecentSummaries().get(i);
                sb.append("- 第").append(summary.get("chapterNumber")).append("章：")
                  .append(safeString(summary.get("summary"), "暂无摘要")).append("\n");
            }

            messages.add(createMessage("system", sb.toString()));
            logger.info("✅ 已添加{}章概括到一个message", displayCount);
        } else {
            logger.warn("⚠️ recentSummaries为空，无法添加章节概括");
        }
    }

    /**
     * Message 10: 本章任务与要求
     */
    private String buildTaskAndRequirements(WritingContext context, Integer chapterNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本章创作任务】\n\n");

        Map<String, Object> plan = context.getChapterPlan() != null ? context.getChapterPlan() : new HashMap<>();
        sb.append("章节号：第").append(chapterNumber).append("章\n");

        String title = safeString(plan.get("title"), "");
        if (StringUtils.isNotBlank(title)) {
            sb.append("章节标题：").append(title).append("\n");
        }

        String coreEvent = safeString(plan.get("coreEvent"), "");
        if (StringUtils.isNotBlank(coreEvent)) {
            sb.append("核心事件：").append(coreEvent).append("\n");
        }

        String mood = safeString(plan.get("mood"), "");
        if (StringUtils.isNotBlank(mood)) {
            sb.append("氛围基调：").append(mood).append("\n");
        }

        if (context.getUserAdjustment() != null && !context.getUserAdjustment().isEmpty()) {
            sb.append("用户要求：").append(context.getUserAdjustment()).append("\n");
        }

        sb.append("\n【硬性约束（违反将导致失败）】\n");
        sb.append("- 紧扣世界规则与既定人设，不得自创设定\n");
        sb.append("- 开篇三段必须呈现行动、冲突或抉择\n");
        sb.append("- 对话与动作交替推进，避免空洞复述\n");

        sb.append("\n【执行要点】\n");
        List<String> highlights = context.getCoreNarrativeSummary() != null
            ? safeStringList(context.getCoreNarrativeSummary().get("highlights"))
            : Collections.emptyList();
        if (!highlights.isEmpty()) {
            highlights.stream().limit(3).forEach(item -> sb.append("- ").append(item).append("\n"));
        }

        List<String> focusNotes = context.getChapterIntent() != null
            ? safeStringList(context.getChapterIntent().get("focusNotes"))
            : Collections.emptyList();
        if (!focusNotes.isEmpty()) {
            focusNotes.stream().limit(3).forEach(note -> sb.append("- ").append(note).append("\n"));
        }

        sb.append("\n【执行检查表】\n");
        sb.append("- 开篇三段是否明确冲突/目标？\n");
        sb.append("- 是否落实章节意图中的关键节点？\n");
        sb.append("- 章末是否留下未决悬念或情绪钩子？\n");

        sb.append("\n请直接输出正文内容，不要附加标题或解释。\n");
        sb.append("现在，请开始创作：");

        return sb.toString();
    }

    /**
     * Message 11: 字数限制（单独一条消息）
     */
    private String buildWordCountLimit(WritingContext context) {
        Map<String, Object> plan = context.getChapterPlan() != null ? context.getChapterPlan() : new HashMap<>();
        int targetWords = safeInt(plan.getOrDefault("estimatedWords", 2500), 2500);
        int maxWords = (int) Math.ceil(targetWords * 1.1); // 允许10%容差

        StringBuilder sb = new StringBuilder();
        sb.append("【字数限制（必须严格遵守）】\n\n");
        sb.append("⚠️ 目标字数：").append(targetWords).append(" 字\n");
        sb.append("⚠️ 绝对上限：").append(maxWords).append(" 字（超过此数字必须立即停止）\n\n");
        sb.append("【写作规则】\n");
        sb.append("1. 达到 ").append(targetWords).append(" 字时，必须开始收尾\n");
        sb.append("2. 达到 ").append(maxWords).append(" 字时，必须立即停止输出，不得再多写一个字\n");
        sb.append("3. 在收尾阶段，禁止引入新情节、新角色或新线索\n");
        sb.append("4. 写作过程中，每写完一段，在心中估算当前字数\n");
        sb.append("5. 如果字数超出限制，视为创作失败，需要重新生成\n\n");
        sb.append("⚠️ 字数限制不是建议，而是硬性约束，违反将导致章节质量下降。\n");

        return sb.toString();
    }

    private int safeInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim()).intValue();
        } catch (Exception e) {
            return fallback;
        }
    }

    private Map<String, String> createMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String safeString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.isNotBlank(text) ? text : fallback;
    }

    private List<String> safeStringList(Object value) {
        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    /**
     * 🆕 构建状态强约束区块（从图谱账本读取，极简可靠）
     */
    private String buildStateConstraints(WritingContext context) {
        if (graphService == null) {
            return buildFallbackGuard(context); // 无图谱时返回fallback
        }

        // 获取 novelId
        Long novelId = null;
        if (context.getNovelInfo() != null && context.getNovelInfo().get("id") != null) {
            novelId = ((Number) context.getNovelInfo().get("id")).longValue();
        }
        if (novelId == null) {
            logger.warn("无法获取novelId，使用fallback");
            return buildFallbackGuard(context);
        }

        Integer chapterNumber = context.getChapterPlan() != null ?
            (Integer) context.getChapterPlan().get("chapterNumber") : null;

        StringBuilder sb = new StringBuilder();
        sb.append("【状态硬约束（违者判定为跑偏）】\n\n");

        // 1. 角色状态（主角+Top3配角，含inventory）
        List<Map<String, Object>> characterStates = graphService.getCharacterStates(novelId, 5);
        if (!characterStates.isEmpty()) {
            sb.append("人物\n");
            for (Map<String, Object> state : characterStates) {
                String name = (String) state.get("name");
                String loc = (String) state.get("location");
                String realm = (String) state.get("realm");
                Boolean alive = (Boolean) state.get("alive");
                Object inventoryObj = state.get("inventory");

                sb.append("- ").append(name).append("：");
                if (StringUtils.isNotBlank(loc)) {
                    sb.append("loc=").append(loc);
                }
                if (StringUtils.isNotBlank(realm)) {
                    sb.append("；realm=").append(realm);
                }
                sb.append("；alive=").append(alive != null && alive ? "是" : "否");

                // 🆕 显示inventory
                if (inventoryObj instanceof java.util.List) {
                    java.util.List<?> items = (java.util.List<?>) inventoryObj;
                    if (!items.isEmpty()) {
                        sb.append("；持有[");
                        for (int i = 0; i < items.size(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(items.get(i));
                        }
                        sb.append("]");
                    }
                }

                sb.append("\n");
            }
            sb.append("\n");
        }

        // 2. 强关系（Top5）
        List<Map<String, Object>> relationships = graphService.getTopRelationships(novelId, 5);
        if (!relationships.isEmpty()) {
            sb.append("关系（Top5，强约束）\n");
            for (Map<String, Object> rel : relationships) {
                String a = (String) rel.get("a");
                String b = (String) rel.get("b");
                String type = (String) rel.get("type");
                sb.append("- ").append(a).append(" ↔ ").append(b).append("：").append(type);
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 3. 未决任务/伏笔（本章优先级，窗口≤1章标红）
        // 🔕 注释掉未决任务：剧情按章纲发展，未决任务容易干扰AI写作
        // List<Map<String, Object>> openQuests = graphService.getOpenQuests(novelId, chapterNumber);
        // if (!openQuests.isEmpty()) {
        //     sb.append("未决任务/伏笔（本章优先级）\n");
        //     for (Map<String, Object> quest : openQuests) {
        //         String id = (String) quest.get("id");
        //         String desc = (String) quest.get("description");
        //         Integer due = (Integer) quest.get("due");
        //
        //         // 🆕 窗口≤1章标红警告
        //         if (due != null && chapterNumber != null && due <= chapterNumber + 1) {
        //             sb.append("- ⚠️ ").append(id).append("：").append(desc);
        //             sb.append("（窗口仅剩").append(Math.max(0, due - chapterNumber)).append("章，必须推进或明确受阻）");
        //         } else {
        //             sb.append("- ").append(id).append("：").append(desc);
        //             if (due != null && chapterNumber != null && due <= chapterNumber + 3) {
        //                 sb.append("（窗口：本章～下").append(due - chapterNumber).append("章内需推进）");
        //             }
        //         }
        //         sb.append("\n");
        //     }
        //     sb.append("\n");
        // }

        // 如果图谱数据全空（新小说前几章），返回fallback
        if (sb.length() < 100) {
            return buildFallbackGuard(context);
        }

        return sb.toString();
    }

    /**
     * 🆕 Fallback State Guard（图谱为空时至少显示本章目标）
     */
    private String buildFallbackGuard(WritingContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本章目标】\n\n");

        // 从chapterPlan提取关键信息
        if (context.getChapterPlan() != null) {
            Map<String, Object> plan = context.getChapterPlan();
            Object purposeObj = plan.get("purpose");
            if (purposeObj != null) {
                sb.append("- 本章意图：").append(purposeObj).append("\n");
            }
            Object focusObj = plan.get("primaryFocus");
            if (focusObj != null) {
                sb.append("- 本章重点：").append(focusObj).append("\n");
            }
        }

        if (sb.length() < 30) {
            sb.append("（图谱数据尚未建立，请根据大纲与卷蓝图创作）\n");
        }

        return sb.toString();
    }

    /**
     * 从图谱事件中提取角色状态
     */
    private Map<String, CharacterState> extractCharacterStates(WritingContext context) {
        Map<String, CharacterState> states = new HashMap<>();

        List<GraphEntity> events = context.getRelevantEvents();
        if (events == null || events.isEmpty()) {
            return states;
        }

        // 按章节号降序排序（最新的在前）
        events.sort((a, b) -> Integer.compare(
            b.getChapterNumber() != null ? b.getChapterNumber() : 0,
            a.getChapterNumber() != null ? a.getChapterNumber() : 0
        ));

        // 扫描事件，提取状态变化
        for (GraphEntity event : events) {
            Map<String, Object> props = event.getProperties();

            // 提取参与者
            Object participantsObj = props.get("participants");
            List<String> participants = new ArrayList<>();
            if (participantsObj instanceof List) {
                for (Object p : (List<?>) participantsObj) {
                    if (p != null) participants.add(p.toString());
                }
            } else if (participantsObj instanceof String) {
                String[] parts = participantsObj.toString().split("[,，、]");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) participants.add(trimmed);
                }
            }

            // 提取地点
            String location = safeString(props.get("location"), null);

            // 为每个参与者更新状态（如果还未记录）
            for (String name : participants) {
                if (!states.containsKey(name)) {
                    CharacterState state = new CharacterState();
                    state.name = name;
                    state.isAlive = true; // 默认存活
                    state.location = location;
                    state.lastSeenChapter = event.getChapterNumber();
                    states.put(name, state);
                }
            }

            // 检测死亡关键词
            String desc = safeString(props.get("description"), "").toLowerCase();
            String summary = safeString(props.get("summary"), "").toLowerCase();
            String combinedText = desc + " " + summary;

            for (String name : participants) {
                if ((combinedText.contains(name.toLowerCase()) || combinedText.contains(name)) &&
                    (combinedText.contains("死") || combinedText.contains("杀") ||
                     combinedText.contains("亡") || combinedText.contains("牺牲"))) {
                    // 检测这个人是否被杀
                    if (combinedText.contains(name + "死") ||
                        combinedText.contains("杀" + name) ||
                        combinedText.contains(name + "被杀")) {
                        CharacterState state = states.get(name);
                        if (state != null) {
                            state.isAlive = false;
                            state.deathChapter = event.getChapterNumber();
                        }
                    }
                }
            }
        }

        return states;
    }

    /**
     * 从图谱事件中提取地点状态
     */
    private Map<String, String> extractLocationStates(WritingContext context) {
        Map<String, String> locationStates = new HashMap<>();

        List<GraphEntity> events = context.getRelevantEvents();
        if (events == null || events.isEmpty()) {
            return locationStates;
        }

        // 找到最新的几个事件的地点
        events.stream()
            .sorted((a, b) -> Integer.compare(
                b.getChapterNumber() != null ? b.getChapterNumber() : 0,
                a.getChapterNumber() != null ? a.getChapterNumber() : 0
            ))
            .limit(3) // 只看最近3个事件
            .forEach(event -> {
                Map<String, Object> props = event.getProperties();
                String location = safeString(props.get("location"), null);
                Object participantsObj = props.get("participants");

                if (location != null && participantsObj != null) {
                    String participantsStr = "";
                    if (participantsObj instanceof List) {
                        List<?> list = (List<?>) participantsObj;
                        if (!list.isEmpty()) {
                            participantsStr = String.join("、",
                                list.stream().map(Object::toString).toArray(String[]::new));
                        }
                    } else {
                        participantsStr = participantsObj.toString();
                    }

                    if (!participantsStr.isEmpty() && !locationStates.containsKey(location)) {
                        locationStates.put(location, participantsStr);
                    }
                }
            });

        return locationStates;
    }

    private String buildUserReferenceMessage(Map<String, String> referenceContents) {
        if (referenceContents == null || referenceContents.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【作者提供的关联素材】\n");
        int index = 1;
        for (Map.Entry<String, String> entry : referenceContents.entrySet()) {
            String content = entry.getValue();
            if (StringUtils.isBlank(content)) {
                continue;
            }
            String title = StringUtils.isNotBlank(entry.getKey()) ? entry.getKey() : ("参考素材" + index);
            sb.append(index++).append(". ").append(title).append("\n");
            sb.append(content).append("\n\n");
        }
        if (index == 1) {
            return null;
        }
        return sb.toString().trim();
    }

    /**
     * 角色状态内部类
     */
    private static class CharacterState {
        String name;
        boolean isAlive = true;
        String location;
        String realm;
        Integer deathChapter;
        Integer lastSeenChapter;
    }
}

