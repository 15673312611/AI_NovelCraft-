package com.novel.agentic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.service.graph.IGraphService;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 核心状态抽取器（轻量、可靠优先）
 *
 * 只抽取：主角+Top3配角状态 + 本章任务推进标记
 * 避免复杂因果/关系网的抽取错误
 */
@Service
public class CoreStateExtractor {
    private static final Logger logger = LoggerFactory.getLogger(CoreStateExtractor.class);

    private final AIWritingService aiWritingService;
    private final IGraphService graphService;
    private final ObjectMapper objectMapper;
    private final ManagedPromptService managedPromptService;

    public CoreStateExtractor(AIWritingService aiWritingService,
                              @Autowired(required = false) IGraphService graphService,
                              ObjectMapper objectMapper,
                              ManagedPromptService managedPromptService) {
        this.aiWritingService = aiWritingService;
        this.graphService = graphService;
        this.objectMapper = objectMapper;
        this.managedPromptService = managedPromptService;

        if (graphService == null) {
            logger.warn("⚠️⚠️⚠️ CoreStateExtractor: IGraphService未注入，实体抽取将被禁用 ⚠️⚠️⚠️");
            logger.warn("   原因：Neo4j未启动或连接失败");
            logger.warn("   影响：章节生成正常，但图谱数据不会保存");
            logger.warn("   解决：启动Neo4j服务（docker-compose up neo4j）或检查配置");
        } else {
            logger.info("✅ CoreStateExtractor初始化成功，图谱服务类型: {}", graphService.getServiceType());
        }
    }
    
    /**
     * 从章节内容抽取核心状态并入库（带冲突检测）
     */
    public void extractAndSaveCoreState(Long novelId, Integer chapterNumber,
                                       String chapterContent, String chapterTitle,
                                       AIConfigRequest aiConfig) {
        // 🔒 前置检查：图谱服务不可用时直接返回
        if (graphService == null) {
            logger.warn("⚠️ 图谱服务不可用，跳过核心状态抽取（novelId={}, chapter={}）", novelId, chapterNumber);
            return;
        }

        try {
            logger.info("🔍 开始抽取核心状态: novelId={}, chapter={}", novelId, chapterNumber);
            logger.info("   章节标题: {}", chapterTitle);
            logger.info("   章节内容长度: {} 字", chapterContent != null ? chapterContent.length() : 0);

            // 1. 调用AI抽取轻量JSON
            String extractedJson = callAIForExtraction(novelId, chapterContent, chapterTitle, chapterNumber, aiConfig);
            if (extractedJson == null || extractedJson.trim().isEmpty()) {
                logger.warn("❌ AI抽取返回空，跳过状态更新");
                return;
            }

            logger.info("✅ AI抽取成功，JSON长度: {} 字", extractedJson.length());
            logger.info("📄 抽取的JSON内容:\n{}", extractedJson);

            // 2. 解析JSON
            JsonNode root = objectMapper.readTree(extractedJson);
            logger.info("✅ JSON解析成功");

            // 3. 冲突检测（location/realm变化合理性）
            List<String> conflicts = detectConflicts(novelId, chapterNumber, root);
            if (!conflicts.isEmpty()) {
                logger.warn("⚠️ 检测到状态冲突，但仍继续入库（冲突：{}）", String.join("; ", conflicts));
                // 未来可选：触发用户确认或AI补写桥段
            }

            // 4. 保存主角状态
            logger.info("💾 开始保存主角状态...");
            JsonNode protagonist = root.path("protagonist");
            saveProtagonistState(novelId, chapterNumber, protagonist);

            // 5. 保存关键配角状态（Top3）+ 关系
            logger.info("💾 开始保存关键配角状态...");
            String protagonistName = protagonist.path("name").asText("");
            saveKeyCharactersState(novelId, chapterNumber, root.path("keyCharacters"), protagonistName);

            // 6. 更新任务推进（自动创建或更新OpenQuest）
            logger.info("💾 开始更新任务推进...");
            updateQuestProgress(novelId, chapterNumber, root.path("questProgress"));

            logger.info("✅ 核心状态抽取完成: novelId={}, chapter={}", novelId, chapterNumber);

        } catch (Exception e) {
            logger.error("❌ 核心状态抽取失败: novelId={}, chapter={}", novelId, chapterNumber, e);
            // 不抛异常，避免阻塞章节保存
        }
    }
    
    /**
     * 调用AI抽取轻量JSON（主角+Top3配角+任务）
     */
    private String callAIForExtraction(Long novelId, String content, String title, Integer chapterNumber, AIConfigRequest aiConfig) throws Exception {
        String prompt = buildExtractionPrompt(novelId, content, title, chapterNumber);
        String systemPrompt = renderManagedPrompt(
            "agentic.core_state.extraction.system",
            "核心状态提取系统提示词",
            "system_chain",
            "约束核心状态提取模型只输出 JSON，不附带解释和 markdown 围栏",
            510,
            Collections.emptyList(),
            "你是信息抽取器。严格返回JSON，不含任何解释或markdown标记。",
            Collections.emptyMap()
        );

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", prompt));

        String result = aiWritingService.generateContentWithMessages(messages, "core_state_extraction", aiConfig);

        // 清理可能的markdown代码块标记
        if (result != null) {
            result = result.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }

        return result;
    }
    
    /**
     * 构建抽取提示词（极简、只抽主角+Top3+任务），并注入已有图谱记忆
     */
    private String buildExtractionPrompt(Long novelId, String content, String title, Integer chapterNumber) {
        String characterStatesLines = "（无）";
        String openQuestsLines = "（无）";

        // 注入已有角色状态与未决任务，帮助AI做“更新”而不是“新建”
        if (graphService != null && novelId != null) {
            try {
                java.util.List<java.util.Map<String, Object>> __charStates = graphService.getCharacterStates(novelId, 200);
                java.util.List<java.util.Map<String, Object>> __openQuests = graphService.getOpenQuests(novelId, chapterNumber);
                characterStatesLines = buildCharacterStatesLines(__charStates);
                openQuestsLines = buildOpenQuestLines(__openQuests);
            } catch (Exception e) {
                logger.warn("构建核心状态抽取上下文失败（忽略）: {}", e.getMessage());
            }
        }
        return renderManagedPrompt(
            "agentic.core_state.extraction.user",
            "核心状态提取用户提示词",
            "system_chain",
            "根据章节正文和已有图谱记忆提取主角状态、关键配角状态与任务推进，严格输出 JSON",
            511,
            List.of("character_states_lines", "open_quests_lines", "chapter_title", "chapter_number", "chapter_content"),
            """
                【已有角色状态与未决任务（用于对照和更新，避免重复创建）】
                人物状态（最近若干章节）：
                {{character_states_lines}}

                未决任务（OpenQuest）：
                {{open_quests_lines}}

                ==========【防止重复创建角色-铁律】==========
                在输出 keyCharacters 之前，必须先执行以下检查流程：

                【抽取前必查清单】
                对于本章出现的每个角色，在写入 keyCharacters 之前必须：
                1. 先在上述【已有角色状态】列表中查找是否有相同或相似的名字
                2. 如果找到带括号标记的名字（如"萧文（老大）"），检查本章提到的角色是否是同一人
                3. 如果是同一人，必须使用完整的带标记名字（如"萧文（老大）"），绝对不能只写"萧文"
                4. 只有在正文明确说明是不同人时，才能创建新条目并加新标记（如"萧文（老二）"）

                【角色名匹配规则（极重要）】
                在输出本章的 keyCharacters 时，必须严格遵守以下规则：
                1. 优先复用已有角色名：如果本章出现的角色在上述【已有角色状态】中已存在，必须使用完全相同的名字（包括括号标记）。
                2. 识别带标记的名字：如果已有角色名是"张三（大师兄）"，本章提到"张三"或"大师兄"时，必须输出"张三（大师兄）"，不要创建新的"张三"条目。
                3. 同名不同人的处理：如果正文明确说明是不同人（如"另一个张三"），才可以创建新条目，并加上区分标记（如"张三（二师兄）"）。
                4. 任务名也要复用：questProgress 的 key 也要优先复用上述【未决任务】中已有的任务简称。

                【错误示例-严禁模仿】
                × 错误：已有"萧文（老大）"，本章又输出"萧文" → 这会创建重复角色！
                √ 正确：已有"萧文（老大）"，本章必须输出"萧文（老大）"

                从本章抽取核心状态信息，输出严格JSON（无多余文字）：

                {
                  "protagonist": {
                    "name": "主角名",
                    "location": "当前所在地（精确到具体地点）",
                    "realm": "当前境界/实力（如有变化必须标注）",
                    "inventory": ["关键物品1", "关键物品2"],
                    "alive": true,
                    "characterInfo": "黑化值：87/100（仅当正文明确出现系统数值时填写，否则留空）"
                  },
                  "keyCharacters": [
                    {"name": "必须先查【已有角色状态】！如果已有'李四（管家）'则必须写'李四（管家）'不能只写'李四'", "location": "所在地", "relation": "与主角的身份+情感关系（例如：主角继母,敌对 / 同门师兄,表面友好,内心敌对）", "characterInfo": "对主角好感度：40/100（仅当正文明确出现时填写）"}
                  ],
                  "questProgress": {
                    "任务简称": "触发线索/推进/受阻/完成"
                  }
                }

                要求：
                - keyCharacters筛选原则（严格执行）：
                  · 必须同时满足：(1) 本章在场景中真实出现，(2) 有明确的姓名或固定称谓，(3) 是会反复出现或对后续剧情有持续影响的核心角色。
                  · 一律排除无名龙套：只在单章出现、没有姓名、只有职业/身份描述的角色（无论台词多少）不要写。
                  · 判断方法：问自己这个角色在后续章节是否还会被提及或出现？如果答案是否定或不确定，那就不要写。
                  · 电话/回忆中的角色：只在首次出现且明显是重要剧情人物时记录；已在【已有角色状态】中的不要重复写。
                - keyCharacters[].relation 必须包含身份和情感两部分，用逗号分隔。
                - 人物身份识别与统一命名（极重要）：
                  · 仔细识别文中是否有同一角色在不同位置被用不同方式指称（可能是：身份称谓、姓名、代词、昵称、关系描述等）。
                  · 识别线索包括但不限于：明确说明（'X就是那个Y'）、代词指代、情节连续性、角色间对话的指向等。
                  · 一旦确认是同一人物，必须在本JSON的所有字段中统一使用同一个标准名称。
                  · 标准名称选择优先级：
                    1. 最优先：复用【已有角色状态】中的名字（包括括号标记），例如已有"王五（掌门）"，本章提到王五必须写"王五（掌门）"
                    2. 如果是新角色：姓名全称 > 单姓/单名 > 身份称谓 > 代词/昵称
                    3. 同名不同人时才加标记区分，如"王五（长老）"
                  · 严禁为同一角色创建多个名称不同的条目，必须先检查【已有角色状态】列表。
                  · 特别警告：如果【已有角色状态】中有"萧文（老大）"，你绝对不能输出"萧文"，必须输出"萧文（老大）"！
                - inventory只记录"关键物品"（武器/宝物/线索物），不记录普通消耗品
                - characterInfo字段（人物信息）记录原则（极重要）：
                  · 只在正文里明确出现系统数值/系统提示时才填写，包括但不限于：黑化值、好感度、忠诚度、理智值、积分、经验值等带数字的系统面板信息。
                  · 严禁脑补数值：如果正文只是"心情变差"、"更加愤怒"、"越来越黑暗"这类情绪描写，没有明确数值/系统面板 → characterInfo必须留空或设为空字符串""。
                  · 输出形式：每个角色的characterInfo最多一句话，用自然语言+数字描述即可，例如："黑化值：87/100" 或 "对宿主忠诚度：60/100，本章无变化"。
                  · 可以在一句话里同时提1-2个关键值，如："黑化值：87/100；对主角好感：40/100"。
                  · 如果本章该角色没有任何系统数值信息，characterInfo字段可省略或设为""。
                - questProgress识别与记录原则：
                  · 什么是任务：会影响多章的主角目标、困境、待解决的冲突、外部施加的压力或威胁。包括但不限于：主动追求的目标、被迫应对的麻烦、尚未解开的谜团、持续存在的敌对关系等。
                  · 记录标准：只要这个问题/目标在本章被提及或推进，且不是当章就解决的一次性小事，就应该记录。
                  · key命名：用简短稳定的动宾短语或名词短语概括任务核心，不要带 Q- 或 Q_ 前缀（系统会自动加）；后续章节继续推进同一任务时必须复用完全相同的key。
                  · progress描述：简要说明本章该任务的状态变化（触发、推进、受阻、完成等），如果本章明确完成则必须写"完成"或"解决"。
                  · 去重：同一任务在本章只输出一次，不要因为多次提及而创建多个条目；上文【已有未决任务】中存在的任务，本章有推进时才写，没推进就不写。
                - location必须具体（"南疆黑市"而非"南疆"；"瘴海边缘"而非"野外"）
                - 如果本章无关键配角或任务推进，对应字段可为空数组/空对象

                ---
                章节标题：{{chapter_title}}
                章节号：第{{chapter_number}}章
                章节内容：
                {{chapter_content}}
                ---
                请输出JSON：
                """,
            Map.of(
                "character_states_lines", characterStatesLines,
                "open_quests_lines", openQuestsLines,
                "chapter_title", safeText(title),
                "chapter_number", chapterNumber == null ? "" : chapterNumber,
                "chapter_content", safeText(content)
            )
        );
    }
    
    /**
     * 冲突检测（location/realm不合理变化）
     */
    private List<String> detectConflicts(Long novelId, Integer chapterNumber, JsonNode root) {
        List<String> conflicts = new ArrayList<>();
        
        if (chapterNumber <= 1) {
            return conflicts; // 第一章无需检测
        }
        
        try {
            JsonNode protagonist = root.path("protagonist");
            String name = protagonist.path("name").asText("");
            String newLoc = protagonist.path("location").asText("");
            String newRealm = protagonist.path("realm").asText("");
            
            if (name.isEmpty()) {
                return conflicts;
            }
            
            // 查询上一章状态（从图谱CharacterState）
            // TODO: 需要在IGraphService增加 getCharacterState 查询方法
            // 暂时记录到日志，未来可触发用户确认
            
            logger.debug("🔍 冲突检测: {}章 {}@{} realm={}", chapterNumber, name, newLoc, newRealm);
            
        } catch (Exception e) {
            logger.warn("冲突检测失败（忽略）: {}", e.getMessage());
        }
        
        return conflicts;
    }
    
    /**
     * 保存主角状态（包括inventory）
     */
    private void saveProtagonistState(Long novelId, Integer chapterNumber, JsonNode protagonist) {
        if (protagonist.isMissingNode() || protagonist.isNull()) {
            logger.warn("⚠️ protagonist节点缺失或为null，跳过");
            return;
        }

        String name = protagonist.path("name").asText("");
        if (name.isEmpty()) {
            logger.warn("⚠️ 主角名为空，跳过状态保存");
            return;
        }

        String location = protagonist.path("location").asText("");
        String realm = protagonist.path("realm").asText("");
        boolean alive = protagonist.path("alive").asBoolean(true);
        String characterInfo = protagonist.path("characterInfo").asText("");

        logger.info("📝 准备保存主角状态: name={}, location={}, realm={}, alive={}, characterInfo={}", name, location, realm, alive, characterInfo);

        // 保存到CharacterState（包含characterInfo）
        graphService.upsertCharacterStateWithInfo(novelId, name, location, realm, alive, characterInfo, chapterNumber);
        logger.info("✅ 主角状态已调用upsertCharacterState");

        // 🆕 保存inventory（关键物品清单）
        JsonNode inventoryNode = protagonist.path("inventory");
        if (!inventoryNode.isMissingNode() && inventoryNode.isArray()) {
            java.util.List<String> items = new java.util.ArrayList<>();
            for (JsonNode item : inventoryNode) {
                String itemName = item.asText("");
                if (!itemName.isEmpty()) {
                    items.add(itemName);
                }
            }
            if (!items.isEmpty()) {
                logger.info("📝 准备保存主角inventory: {} 件物品", items.size());
                graphService.updateCharacterInventory(novelId, name, items, chapterNumber);
                logger.info("✅ 主角inventory已调用updateCharacterInventory");
            }
        } else {
            logger.info("ℹ️ 主角无inventory或inventory为空");
        }
    }
    
    /**
     * 保存关键配角状态（Top3）+ 关系
     */
    private void saveKeyCharactersState(Long novelId, Integer chapterNumber, JsonNode keyCharacters, String protagonistName) {
        if (keyCharacters.isMissingNode() || !keyCharacters.isArray()) {
            logger.info("ℹ️ keyCharacters节点缺失或非数组，跳过");
            return;
        }

        logger.info("📝 keyCharacters数组长度: {}", keyCharacters.size());

        int count = 0;
        for (JsonNode character : keyCharacters) {
            if (count >= 3) break; // 只保存Top3

            String name = character.path("name").asText("");
            if (name.isEmpty()) {
                logger.warn("⚠️ 配角{}名称为空，跳过", count);
                continue;
            }

            String location = character.path("location").asText("");
            String relation = character.path("relation").asText("");
            String characterInfo = character.path("characterInfo").asText("");

            logger.info("📝 准备保存配角{}: name={}, location={}, relation={}, characterInfo={}", count+1, name, location, relation, characterInfo);

            // 保存状态（包含characterInfo）
            graphService.upsertCharacterStateWithInfo(novelId, name, location, "", true, characterInfo, chapterNumber);
            logger.info("✅ 配角{}状态已调用upsertCharacterState", name);

            // 保存关系（如果有关系信息且主角名不为空）
            if (!relation.isEmpty() && !protagonistName.isEmpty()) {
                // 根据关系类型设置强度
                double strength = calculateRelationshipStrength(relation);

                logger.info("📝 准备保存关系: {} <-[{}]-> {}, 强度={}", protagonistName, relation, name, strength);
                Map<String, Object> relationData = new HashMap<>();
                relationData.put("type", "");
                relationData.put("description", relation);
                relationData.put("strength", strength);
                graphService.upsertRelationshipStateComplete(novelId, protagonistName, name, relationData, chapterNumber);
                logger.info("✅ 关系已保存: {} <-> {}", protagonistName, name);
            } else if (!relation.isEmpty()) {
                logger.warn("⚠️ 配角{}有关系信息，但主角名为空，跳过关系保存", name);
            }

            count++;
        }

        logger.info("✅ 已保存{}个关键配角状态", count);
    }

    /**
     * 根据关系类型计算强度
     */
    private double calculateRelationshipStrength(String relationType) {
        if (relationType == null || relationType.isEmpty()) {
            return 0.5;
        }

        String type = relationType.toLowerCase();

        // 强关系（敌对、盟友、亲密）
        if (type.contains("敌对") || type.contains("仇恨") || type.contains("敌人")) {
            return 0.9;
        }
        if (type.contains("盟友") || type.contains("互援") || type.contains("合作")) {
            return 0.8;
        }
        if (type.contains("亲密") || type.contains("恋人") || type.contains("挚友")) {
            return 0.95;
        }

        // 中等关系
        if (type.contains("朋友") || type.contains("友好")) {
            return 0.6;
        }
        if (type.contains("竞争") || type.contains("对立")) {
            return 0.7;
        }

        // 弱关系
        if (type.contains("陌生") || type.contains("路人")) {
            return 0.2;
        }
        if (type.contains("认识") || type.contains("熟人")) {
            return 0.4;
        }

        // 默认中等强度
        return 0.5;
    }
    
    /**
     * 更新任务推进（自动创建或更新OpenQuest）
     */
    private void updateQuestProgress(Long novelId, Integer chapterNumber, JsonNode questProgress) {
        if (questProgress.isMissingNode() || questProgress.isNull() || !questProgress.isObject()) {
            logger.info("ℹ️ questProgress节点缺失或非对象，跳过");
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = questProgress.fields();
        int count = 0;

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String questName = entry.getKey();
            String progress = entry.getValue().asText("");

            if (questName.isEmpty() || progress.isEmpty()) {
                logger.warn("⚠️ 任务{}名称或进度为空，跳过", count);
                continue;
            }

            // 生成稳定的questId：
            // 1）去掉AI可能带上的前缀（如 "Q-" "Q_"）
            // 2）去掉开头的空格/下划线/短横线
            // 3）将中间的空格和短横线统一为下划线
            String normalizedName = questName;
            if (normalizedName != null) {
                normalizedName = normalizedName.trim();
                // 去掉前缀 Q- / Q_（多次叠加时循环去掉）
                while (normalizedName.startsWith("Q-") || normalizedName.startsWith("Q_")
                    || normalizedName.startsWith("q-") || normalizedName.startsWith("q_")) {
                    normalizedName = normalizedName.substring(2).trim();
                }
                // 去掉开头多余的分隔符
                normalizedName = normalizedName.replaceFirst("^[\\s_\\-]+", "");
            } else {
                normalizedName = "";
            }

            String questId = "Q-" + normalizedName.replaceAll("[\\s\\-]+", "_");

            logger.info("📝 准备更新任务: questId={}, questName={}, progress={}", questId, questName, progress);

            // 根据progress判断状态
            String status = "OPEN";
            if (progress.contains("完成") || progress.contains("解决")) {
                status = "RESOLVED";
                logger.info("📝 任务{}标记为已完成，调用resolveOpenQuest", questId);
                graphService.resolveOpenQuest(novelId, questId, chapterNumber);
            } else {
                // 自动设定due窗口：触发/推进后5章内需闭环；受阻后10章
                int dueWindow = progress.contains("受阻") ? 10 : 5;
                logger.info("📝 任务{}标记为进行中，due窗口={}章，调用upsertOpenQuest", questId, dueWindow);
                graphService.upsertOpenQuest(
                    novelId, questId, normalizedName, status,
                    chapterNumber, chapterNumber + dueWindow, chapterNumber
                );
            }

            logger.info("✅ 任务{}已调用图谱服务", questId);
            count++;
        }

        logger.info("✅ 已更新{}个任务状态", count);
    }

    private String buildCharacterStatesLines(List<Map<String, Object>> states) {
        if (states == null || states.isEmpty()) {
            return "（无）";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> state : states) {
            if (state == null) {
                continue;
            }
            Object nameObj = state.get("name");
            if (nameObj == null) {
                continue;
            }
            String name = nameObj.toString().trim();
            if (name.isEmpty()) {
                continue;
            }

            builder.append("- 角色：").append(name);

            Object loc = state.get("location");
            if (loc != null && !loc.toString().trim().isEmpty()) {
                builder.append(" | 最近位置：").append(loc);
            }

            Object realm = state.get("realm");
            if (realm != null && !realm.toString().trim().isEmpty()) {
                builder.append(" | 实力/境界：").append(realm);
            }

            Object characterInfo = state.get("characterInfo");
            if (characterInfo != null && !characterInfo.toString().trim().isEmpty()) {
                builder.append(" | 人物信息：").append(characterInfo);
            }

            Object lastChapter = state.get("lastChapter");
            if (lastChapter != null) {
                builder.append(" | 最近出现章节：第").append(lastChapter).append("章");
            }

            builder.append("\n");
        }

        return builder.length() == 0 ? "（无）" : builder.toString().trim();
    }

    private String buildOpenQuestLines(List<Map<String, Object>> openQuests) {
        if (openQuests == null || openQuests.isEmpty()) {
            return "（无）";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> quest : openQuests) {
            if (quest == null) {
                continue;
            }
            Object idObj = quest.get("id");
            if (idObj == null) {
                continue;
            }
            String id = idObj.toString().trim();
            if (id.isEmpty()) {
                continue;
            }

            builder.append("- 任务简称/ID：").append(id);

            Object desc = quest.get("description");
            if (desc != null && !desc.toString().trim().isEmpty()) {
                builder.append(" | 简述：").append(desc);
            }

            Object status = quest.get("status");
            if (status != null && !status.toString().trim().isEmpty()) {
                builder.append(" | 当前状态：").append(status);
            }

            Object introduced = quest.get("introduced");
            if (introduced != null) {
                builder.append(" | 引入章节：第").append(introduced).append("章");
            }

            Object due = quest.get("due");
            if (due != null) {
                builder.append(" | 计划完成章节：第").append(due).append("章");
            }

            builder.append("\n");
        }

        return builder.length() == 0 ? "（无）" : builder.toString().trim();
    }

    private String renderManagedPrompt(String promptKey,
                                       String name,
                                       String category,
                                       String description,
                                       int sortOrder,
                                       List<String> variables,
                                       String template,
                                       Map<String, ?> values) {
        return managedPromptService.render(SystemPromptDescriptor.builder()
                        .promptKey(promptKey)
                        .name(name)
                        .category(category)
                        .description(description)
                        .variables(variables)
                        .sortOrder(sortOrder)
                        .defaultContent(template)
                        .build(),
                values);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
