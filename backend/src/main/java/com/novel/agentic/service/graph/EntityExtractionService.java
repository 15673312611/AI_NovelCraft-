package com.novel.agentic.service.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.GraphEntity;
import com.novel.domain.entity.Chapter;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体抽取服务
 * 
 * 从章节内容中抽取：事件、角色、伏笔、情节线等
 * 
 * 自动注入：优先使用Neo4j实现，不可用时降级到内存版
 */
@Service
public class EntityExtractionService {
    
    private static final Logger logger = LoggerFactory.getLogger(EntityExtractionService.class);
    
    @Autowired
    private AIWritingService aiWritingService;
    
    @Autowired
    private IGraphService graphService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ManagedPromptService managedPromptService;

    private static final int MAX_CHAPTER_SNIPPET = 5000;
    
    /**
     * 从章节内容中抽取实体并入图
     * 
     * @param novelId 小说ID
     * @param chapterNumber 章节号
     * @param chapterTitle 章节标题
     * @param content 章节内容
     */
    public void extractAndSave(Long novelId, Integer chapterNumber, String chapterTitle, String content) {
        extractAndSave(novelId, chapterNumber, chapterTitle, content, null);
    }

    public void extractAndSave(Long novelId, Integer chapterNumber, String chapterTitle, String content, AIConfigRequest aiConfig) {
        logger.info("🔬 开始抽取实体: novelId={}, chapter={}", novelId, chapterNumber);
        
        if (content == null || content.length() < 100) {
            logger.warn("⚠️ 章节内容过短，跳过抽取");
            return;
        }

        if (aiConfig == null || !aiConfig.isValid()) {
            throw new IllegalArgumentException("实体抽取AI配置无效，请检查设置");
        }
        
        try {
            // 1. 使用AI抽取实体
            String extractionPrompt = buildExtractionPrompt(chapterNumber, chapterTitle, content);
            String aiResponse = callAIForExtraction(extractionPrompt, aiConfig);
            
            // 2. 解析AI返回的实体
            Map<String, Object> extracted = parseExtractedEntities(aiResponse);
            
            // 3. 转换为GraphEntity并入图
            List<GraphEntity> entities = convertToGraphEntities(extracted, novelId, chapterNumber);
            
            logger.info("✅ 抽取到{}个实体", entities.size());
            
            // 4. 批量入图
            graphService.addEntities(novelId, entities);
            
            // 🆕 5. 添加因果关系
            if (extracted.containsKey("causalRelations")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> causalRelations = (List<Map<String, Object>>) extracted.get("causalRelations");
                addCausalRelations(novelId, causalRelations);
                logger.info("✅ 添加了{}个因果关系", causalRelations.size());
            }
            
            // 🆕 6. 添加角色关系
            if (extracted.containsKey("characterRelations")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> characterRelations = (List<Map<String, Object>>) extracted.get("characterRelations");
                addCharacterRelations(novelId, chapterNumber, characterRelations);
                logger.info("✅ 添加了{}个角色关系", characterRelations.size());
            }
            
            logger.info("🎉 实体抽取完成: novelId={}, chapter={}, count={}", novelId, chapterNumber, entities.size());
            
        } catch (Exception e) {
            logger.error("❌ 实体抽取失败: chapter={}", chapterNumber, e);
        }
    }

    /**
     * 批量抽取实体：将多章正文一次性送入AI，返回成功处理的章节号
     */
    public List<Integer> extractAndSaveBatch(Long novelId, List<Chapter> chapters, AIConfigRequest aiConfig) {
        if (chapters == null || chapters.isEmpty()) {
            return Collections.emptyList();
        }

        if (aiConfig == null || !aiConfig.isValid()) {
            throw new IllegalArgumentException("实体抽取AI配置无效，请检查设置");
        }

        if (graphService == null) {
            throw new IllegalStateException("图谱服务未启用，无法保存批量抽取结果");
        }

        try {
            List<Chapter> orderedChapters = chapters.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Chapter::getChapterNumber))
                .collect(Collectors.toList());

            if (orderedChapters.isEmpty()) {
                return Collections.emptyList();
            }

            String prompt = buildBatchExtractionPrompt(novelId, orderedChapters);
            String aiResponse = callAIForExtraction(prompt, aiConfig);

            Map<String, Object> parsed = parseExtractedEntities(aiResponse);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chapterPayloads = (List<Map<String, Object>>) parsed.getOrDefault("chapters", Collections.emptyList());

            Map<Integer, Map<String, Object>> extractedByChapter = new HashMap<>();
            for (Map<String, Object> payload : chapterPayloads) {
                if (payload == null) {
                    continue;
                }
                Object chapterNumberObj = payload.get("chapterNumber");
                Integer chapterNumber = chapterNumberObj instanceof Number
                        ? ((Number) chapterNumberObj).intValue()
                        : parseChapterNumberFromString(chapterNumberObj);
                if (chapterNumber == null) {
                    continue;
                }
                extractedByChapter.put(chapterNumber, payload);
            }

            List<Integer> processed = new ArrayList<>();
            for (Chapter chapter : orderedChapters) {
                Integer chapterNumber = chapter.getChapterNumber();
                if (chapterNumber == null) {
                    continue;
                }
                Map<String, Object> payload = extractedByChapter.get(chapterNumber);
                if (payload == null) {
                    logger.warn("⚠️ 批量抽取结果缺少第{}章数据", chapterNumber);
                    continue;
                }

                List<GraphEntity> entities = convertToGraphEntities(payload, novelId, chapterNumber);
                graphService.addEntities(novelId, entities);

                if (payload.containsKey("causalRelations")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> causalRelations = (List<Map<String, Object>>) payload.get("causalRelations");
                    addCausalRelations(novelId, causalRelations);
                }

                if (payload.containsKey("characterRelations")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> characterRelations = (List<Map<String, Object>>) payload.get("characterRelations");
                    addCharacterRelations(novelId, chapterNumber, characterRelations);
                }

                logger.info("🎉 批量实体抽取完成: novelId={}, chapter={}, count={}", novelId, chapterNumber, entities.size());
                processed.add(chapterNumber);
            }

            return processed;

        } catch (Exception e) {
            logger.error("❌ 批量实体抽取失败", e);
            throw new RuntimeException("批量实体抽取失败", e);
        }
    }
    
    /**
     * 构建抽取提示词
     */
    private String buildExtractionPrompt(Integer chapterNumber, String chapterTitle, String content) {
        int safeChapterNumber = chapterNumber == null ? 0 : chapterNumber;
        return renderManagedPrompt(
            "agentic.graph.entity_extraction.single.v2",
            "图谱单章实体提取提示词",
            "system_chain",
            "从单章正文中提取事件、角色、设定、关系与状态变化，用于图谱实体入库",
            520,
            List.of("chapter_number", "chapter_title", "chapter_content", "suggested_reveal_chapter"),
            """
                你是一位专业的小说分析助手。请从以下章节中抽取关键实体和信息。

                【章节信息】
                章节号：第{{chapter_number}}章
                章节标题：{{chapter_title}}

                【章节内容】
                {{chapter_content}}

                【抽取要求】
                请以JSON格式返回以下内容（如果某类不存在则返回空数组）：

                {
                  "events": [
                    {
                      "id": "event_{{chapter_number}}_1",
                      "summary": "事件摘要（30字内）",
                      "description": "事件详细描述",
                      "location": "事件发生地点",
                      "participants": ["角色A", "角色B"],
                      "onSceneParticipants": ["真正出现在当前场景的角色（不包括电话那头、回忆里、只被提到的人）"],
                      "mentionedOnlyParticipants": ["在对话/电话/回忆中被提到，但不在当前场景的角色"],
                      "emotionalTone": "positive/negative/neutral/tense",
                      "tags": ["战斗", "对话", "决策"],
                      "importance": 0.8
                    }
                  ],
                  "foreshadows": [
                    {
                      "id": "foreshadow_{{chapter_number}}_1",
                      "content": "伏笔内容",
                      "importance": "high/medium/low",
                      "suggestedRevealChapter": {{suggested_reveal_chapter}}
                    }
                  ],
                  "plotlines": [
                    {
                      "id": "plotline_主线",
                      "name": "主线名称",
                      "priority": 1.0
                    }
                  ],
                  "worldRules": [
                    {
                      "id": "rule_power_system",
                      "name": "规则名称",
                      "content": "规则内容",
                      "constraint": "约束说明",
                      "category": "power_system/world_setting/character_constraint",
                      "importance": 0.9
                    }
                  ],
                  "characters": ["本章新出现的具名角色（必须有明确姓名，且预计后续会再次登场的重要角色）"],
                  "locations": ["本章新出现且对后续剧情有持续影响的地点"],
                  "causalRelations": [
                    {
                      "from": "event_{{chapter_number}}_1",
                      "to": "event_{{chapter_number}}_2",
                      "type": "CAUSES",
                      "description": "事件1导致了事件2"
                    }
                  ],
                  "characterRelations": [
                    {
                      "from": "角色A",
                      "to": "角色B",
                      "description": "用中文描述当前关系状态，例如：表面合作、彼此试探，私下都在防着对方",
                      "strength": 0.8,
                      "publicStatus": "PUBLIC/SECRET/SEMI_PUBLIC",
                      "type": "可选：若能用粗粒度中文概括，可填写合作/敌对/亲属/师徒；无法概括可留空"
                    }
                  ],
                  "stateChanges": {
                    "characters": [
                      {
                        "name": "角色名",
                        "alive": true,
                        "location": "当前位置（如改变）",
                        "realm": "实力等级（如突破）",
                        "affiliation": "所属势力（如改变）",
                        "stateDesc": "状态变化简述"
                      }
                    ],
                    "factions": [
                      {
                        "name": "势力名",
                        "status": "active",
                        "leaderAlive": true,
                        "casualties": [{"name": "死亡成员", "role": "角色"}],
                        "stateDesc": "状态变化简述"
                      }
                    ],
                    "locations": [
                      {
                        "name": "地点名",
                        "currentOccupants": ["当前在此的主要角色"],
                        "controlledBy": "控制者（如有）",
                        "stateDesc": "状态变化简述"
                      }
                    ]
                  },
                  "narrativeBeat": {
                    "id": "beat_{{chapter_number}}",
                    "beatType": "CONFLICT/CLIMAX/PLOT/CHARACTER/RELIEF",
                    "focus": "剧情/人物/世界观",
                    "tension": 0.7,
                    "sentiment": "tense/hopeful/tragic",
                    "paceScore": 0.6,
                    "viewpoint": "主角/配角/反派/旁观者"
                  },
                  "conflictArcs": [
                    {
                      "id": "conflict_arc_{{chapter_number}}",
                      "name": "冲突线名称",
                      "stage": "酝酿/爆发/僵持/解决",
                      "urgency": 0.8,
                      "nextAction": "下一步升级计划",
                      "protagonist": "主角名",
                      "antagonist": "对手名",
                      "trend": "UP/FLAT/DOWN"
                    }
                  ],
                  "characterArcs": [
                    {
                      "id": "character_arc_{{chapter_number}}",
                      "characterName": "角色名",
                      "arcName": "成长线名称",
                      "pendingBeat": "待完成的成长节点",
                      "nextGoal": "下一目标",
                      "priority": 0.7,
                      "progress": 2,
                      "totalBeats": 5
                    }
                  ],
                  "perspectiveUsage": {
                    "id": "perspective_{{chapter_number}}",
                    "characterName": "本章视角角色",
                    "mode": "第一人称/第三人称/全知",
                    "tone": "tense/hopeful/warm",
                    "purpose": "切换视角的目的"
                  }
                }

                注意：
                1. events抽取原则（严格控制）：
                   - 只抽取对后续剧情有长期影响的关键事件（如：角色突破、重大决策、势力变动、关键冲突）
                   - 不要抽取日常对话、普通战斗、一次性交易等短期事件
                   - 每章最多抽取2-3个真正关键的事件，宁缺毋滥
                   - 每个事件必须包含location字段（地点）
                   - importance必须>=0.7，低于0.7的事件不要记录
                2. location必须准确提取，用于跟踪角色位置和场景连贯性
                3. foreshadows不要抽取：AI难以准确判断什么是伏笔，容易误判和扰乱剧情
                4. worldRules只抽取新引入的设定规则
                5. importance范围0-1，越重要值越大
                6. causalRelations抽取事件间的因果关系（如某事件导致另一事件）
                7. characterRelations抽取角色关系网络（极重要）：
                   - 提取本章中所有重要角色之间的关系，不只是关系发生变化的，已有的稳定关系也要记录。
                   - 包括：主角与配角、配角与配角之间的关系（如敌对派系的首领之间、盟友之间、师徒关系等）。
                   - description 是核心字段，必须用中文分析当前关系状态，优先描述双方真实关系、表面关系、利益绑定、情感倾向、互相利用/猜忌/依赖等复杂信息，不能只写一个标签。
                   - type 不再要求固定英文枚举，可留空；如果确实能用一个粗粒度中文词概括，再填写如“合作”“敌对”“亲属”“师徒”。
                   - strength范围0-1：0.9-1.0=生死仇敌或至亲，0.7-0.9=重要关系，0.5-0.7=一般关系，0.3-0.5=弱关系。
                   - publicStatus 表示这段关系是公开、秘密还是半公开。
                   - 只记录对剧情有影响的角色关系，路人与路人之间的关系不要写。
                8. characters提取规则（严格执行）：
                   - 必须是具名角色（有明确的姓名，如：林晨、张伟、李教授）
                   - 必须是有台词、有动作、有性格描写的独立角色
                   - 预计后续章节会再次出现的重要角色
                   - 不要提取：一次性龙套角色（只有一句台词或只是背景板）
                   - 示例对比：正确提取[林晨、苏婉] 错误提取[记者、群众]
                   - 对同一角色在不同表述中的称呼（如“继母”“苏苏的继母”“后妈”），在输出时必须统一为一个标准名称（例如统一写成“继母”）。
                   - 所有涉及角色名的字段（events[].participants、events[].onSceneParticipants、characterRelations[].from/to、stateChanges.characters[].name、characters数组）都要使用这个标准名称；如需保留别称，可以在characters数组里增加aliases字段记录别名。
                9. stateChanges（极重要！）必须抽取所有状态变更：
                   - characters: 角色生死(alive)、位置(location)、实力(realm)、势力(affiliation)
                   - factions: 势力状态(status)、领袖生死(leaderAlive)、伤亡(casualties)
                   - locations: 地点当前占据者(currentOccupants)、控制者(controlledBy)
                   这些状态对后续章节一致性至关重要，如有变化必须详细记录！
                10. narrativeBeat用于总结本章节奏意图；conflictArcs/characterArcs仅列出本章推进的弧线。如果某项不存在，请返回空对象或空数组。
                11. 只返回JSON，不要有其他解释
                12. 对于电话那头、回忆中或只是被提到而不在当前场景的人物：可以出现在events[].participants或mentionedOnlyParticipants中，但不要出现在events[].onSceneParticipants和stateChanges.characters中；如果无法确定该角色的具体位置，请不要随意填写location。
                """,
            Map.of(
                "chapter_number", safeChapterNumber,
                "chapter_title", safeText(chapterTitle),
                "chapter_content", truncateForPrompt(content, 3000),
                "suggested_reveal_chapter", safeChapterNumber + 5
            )
        );
    }

    private String buildBatchExtractionPrompt(Long novelId, List<Chapter> chapters) {
        String characterStateLines = "（无）";
        String relationshipLines = "（无）";
        String openQuestLines = "（无）";

        // 🧠 先注入跨章节图谱记忆，帮助AI复用/更新已有角色与任务，避免重复创建
        if (graphService != null && novelId != null) {
            try {
                int currentChapter = chapters.stream()
                    .filter(Objects::nonNull)
                    .filter(c -> c.getChapterNumber() != null)
                    .mapToInt(Chapter::getChapterNumber)
                    .max()
                    .orElse(0);

                List<Map<String, Object>> characterStates = graphService.getCharacterStates(novelId, 200);
                List<Map<String, Object>> relationships = graphService.getTopRelationships(novelId, 200);
                List<Map<String, Object>> openQuests = graphService.getOpenQuests(novelId, currentChapter);
                characterStateLines = buildGraphCharacterStateLines(characterStates);
                relationshipLines = buildGraphRelationshipLines(relationships);
                openQuestLines = buildOpenQuestLines(openQuests);
            } catch (Exception e) {
                logger.warn("构建批量抽取上下文失败（忽略）: {}", e.getMessage());
            }
        }
        return renderManagedPrompt(
            "agentic.graph.entity_extraction.batch.v2",
            "图谱批量实体提取提示词",
            "agentic",
            "对多章正文进行批量实体、关系和状态变更抽取，复用已有图谱记忆避免重复创建",
            521,
            List.of("character_state_lines", "relationship_lines", "open_quest_lines", "chapter_inputs_block"),
            """
                【已有图谱记忆（用于对照和更新，避免重复创建）】
                人物状态：
                {{character_state_lines}}

                重要关系（RelationshipState）：
                {{relationship_lines}}

                未决任务（OpenQuest）：
                {{open_quest_lines}}

                在为下面这些章节抽取实体时，请严格遵守以下规则：
                - 跨章节人物身份识别与统一（极重要）：
                  · 在处理多个章节时，仔细识别同一角色在不同章节中是否被用不同方式指称（如：身份称谓、姓名全称、单名、代词、昵称、关系描述等）。
                  · 识别线索包括但不限于：文中明确说明某两个称呼指向同一人、代词指代、情节连续性、角色对话的上下文指向、身份与姓名的对应关系等。
                  · 一旦确认是同一人物（无论跨越多少章节），必须在所有章节的输出中统一使用同一个标准名称。
                  · 标准名称选择优先级：姓名全称 > 单姓/单名 > 身份称谓 > 代词/昵称。即：如果后续章节揭示了该角色的姓名，就将所有章节中该角色的名字统一为姓名；如果只有身份称谓，就用身份称谓；总是选择信息量最大、最明确的名字。
                  · 在所有章节的 characters[] / events[].participants / stateChanges.characters[].name / characterRelations[].from/to 中，都要使用这个统一的标准名字。
                  · 旧的不完整称呼可以记录在该角色的 characters[].aliases 数组中作为别名。
                  · 对于上文【已有图谱记忆】中的角色名，如果与本批次章节中的角色能确认为同一人，优先复用图谱中已有的标准名。
                  · 不要为同一人物创建多个角色节点。
                - 角色筛选原则（stateChanges.characters）：
                  · 必须同时满足：(1) 在场景中真实出现，(2) 有明确的姓名或固定称谓，(3) 会反复出现或对后续剧情有持续影响。
                  · 一律排除无名龙套：只在单章出现、没有姓名、只有职业/身份描述的角色（无论台词多少）不要写进 stateChanges.characters。
                  · 判断方法：问自己这个角色在后续章节是否还会被提及或出现？如果答案是否定或不确定，那就不要写。
                  · 电话/回忆中提到的角色：只在以下情况写进 stateChanges.characters：(1) 首次出现 且 (2) 看起来对剧情很重要（如幕后BOSS、关键线索人物）；如果该角色已在上文【已有图谱记忆】中存在，本批次就不要再写进 stateChanges.characters，避免重复更新。
                  · 只是被简单提及、没有实质内容的角色，只能出现在 events[].participants 中（如果该事件值得记录的话），不要写进 stateChanges.characters。
                - 遇到与上述未决任务含义相同/明显延续的任务，复用原任务ID（去掉其中的 Q- 前缀后的简称部分）并更新状态/描述，而不是新建一个新的任务；任务简称不要自己带 Q- 或 Q_ 前缀。
                - 新的事件和关系要尽量基于已有角色名来描述，避免因为称呼差异把同一人物拆成多份。
                - characterRelations 输出时，description 为主要字段，必须用中文分析关系状态；type 可留空，不要依赖固定英文枚举。

                你是一位专业的小说分析助手。下面会一次提供多章正文，请为每一章分别抽取关键实体。
                请严格输出如下JSON结构：
                {
                  "chapters": [
                    {
                      "chapterNumber": 12,
                      "title": "章节标题",
                      "events": [],
                      "foreshadows": [],
                      "plotlines": [],
                      "worldRules": [],
                      "characters": [],
                      "locations": [],
                      "causalRelations": [],
                      "characterRelations": [],
                      "stateChanges": {
                        "characters": [],
                        "factions": [],
                        "locations": []
                      },
                      "narrativeBeat": {},
                      "conflictArcs": [],
                      "characterArcs": [],
                      "perspectiveUsage": {}
                    }
                  ]
                }

                要求：
                1. chapters数组中每个元素对应一章，chapterNumber必须与输入一致。
                2. 其余字段含义与单章抽取时完全相同，字段缺失请返回空数组/对象。
                3. 禁止输出额外解释或markdown围栏。

                {{chapter_inputs_block}}
                """,
            Map.of(
                "character_state_lines", characterStateLines,
                "relationship_lines", relationshipLines,
                "open_quest_lines", openQuestLines,
                "chapter_inputs_block", buildBatchChapterInputsBlock(chapters)
            )
        );
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_CHAPTER_SNIPPET) {
            return content;
        }
        return content.substring(0, MAX_CHAPTER_SNIPPET) + "...";
    }

    private Integer parseChapterNumberFromString(Object chapterNumberObj) {
        if (chapterNumberObj == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(chapterNumberObj).replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 调用AI进行抽取
     */
    private String callAIForExtraction(String prompt, AIConfigRequest aiConfig) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        
        // 使用非流式生成
        return aiWritingService.generateContentWithMessages(
            messages, 
            "entity_extraction", 
            aiConfig
        );
    }
    
    /**
     * 解析AI返回的实体
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseExtractedEntities(String aiResponse) {
        try {
            String s = aiResponse == null ? "" : aiResponse;
            s = sanitizeToStrictJson(s);
            if (s != null && !s.isEmpty()) {
                return objectMapper.readValue(s, Map.class);
            }
        } catch (Exception e) {
            logger.error("解析AI返回失败", e);
        }

        // 返回空结果
        Map<String, Object> emptyResult = new HashMap<>();
        emptyResult.put("events", Collections.emptyList());
        emptyResult.put("foreshadows", Collections.emptyList());
        emptyResult.put("plotlines", Collections.emptyList());
        emptyResult.put("worldRules", Collections.emptyList());
        return emptyResult;
    }

    //  清洗AI文本为严格JSON（去围栏/噪声/拖尾逗号）
    private String sanitizeToStrictJson(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // 去除```json/```围栏
        s = s.replace("```json", "").replace("```JSON", "").replace("```", "").trim();
        // 仅保留最外层{...}
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        // 修复常见的", e  \"key\""类噪声
        s = s.replaceAll(",\\s*[A-Za-z_]+\\s*(\\\")", ", $1");
        s = s.replaceAll("\\{\\s*[A-Za-z_]+\\s*(\\\")", "{$1");
        // 移除对象/数组末尾拖尾逗号
        s = s.replaceAll(",\\s*([}\\]])", "$1");
        // 简单平衡检查
        if (!s.startsWith("{") || !s.endsWith("}")) return null;
        return s;
    }
    
    /**
     * 转换为GraphEntity
     */
    @SuppressWarnings("unchecked")
    private List<GraphEntity> convertToGraphEntities(Map<String, Object> extracted, Long novelId, Integer chapterNumber) {
        List<GraphEntity> entities = new ArrayList<>();
        
        // 事件
        List<Map<String, Object>> events = (List<Map<String, Object>>) extracted.getOrDefault("events", Collections.emptyList());
        for (Map<String, Object> event : events) {
            Map<String, Object> props = new HashMap<>(event);
            props.put("importanceScore", resolveImportance(props.get("importance"), 0.6));
            String id = event.get("id") != null ? String.valueOf(event.get("id")) : null;
            if (id == null || id.trim().isEmpty()) {
                id = "event_" + chapterNumber + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            }
            entities.add(GraphEntity.builder()
                .type("Event")
                .id(id)
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("第" + chapterNumber + "章")
                .build());
        }
        
        // 伏笔
        List<Map<String, Object>> foreshadows = (List<Map<String, Object>>) extracted.getOrDefault("foreshadows", Collections.emptyList());
        for (Map<String, Object> f : foreshadows) {
            Map<String, Object> props = new HashMap<>(f);
            props.put("status", "PLANTED");
            props.put("importanceScore", resolveImportance(props.get("importance"), 0.5));
            String id = f.get("id") != null ? String.valueOf(f.get("id")) : null;
            if (id == null || id.trim().isEmpty()) {
                id = "foreshadow_" + chapterNumber + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            }
            entities.add(GraphEntity.builder()
                .type("Foreshadow")
                .id(id)
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("第" + chapterNumber + "章")
                .build());
        }
        
        // 情节线
        List<Map<String, Object>> plotlines = (List<Map<String, Object>>) extracted.getOrDefault("plotlines", Collections.emptyList());
        for (Map<String, Object> p : plotlines) {
            Map<String, Object> props = new HashMap<>(p);
            props.put("importanceScore", resolveImportance(props.get("priority"), 0.5));
            entities.add(GraphEntity.builder()
                .type("Plotline")
                .id((String) p.get("id"))
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("系统")
                .build());
        }
        
        // 世界规则
        List<Map<String, Object>> rules = (List<Map<String, Object>>) extracted.getOrDefault("worldRules", Collections.emptyList());
        for (Map<String, Object> r : rules) {
            Map<String, Object> props = new HashMap<>(r);
            props.put("scope", "global");
            props.put("introducedAt", chapterNumber);
            props.put("importanceScore", resolveImportance(props.get("importance"), 0.5));
            
            entities.add(GraphEntity.builder()
                .type("WorldRule")
                .id((String) r.get("id"))
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("设定")
                .build());
        }

        Map<String, Object> beat = (Map<String, Object>) extracted.get("narrativeBeat");
        if (beat != null && !beat.isEmpty()) {
            String beatId = beat.containsKey("id") ? (String) beat.get("id") : "beat_auto_" + chapterNumber;
            Map<String, Object> props = new HashMap<>(beat);
            props.put("importanceScore", resolveImportance(props.get("paceScore"), 0.5));
            entities.add(GraphEntity.builder()
                .type("NarrativeBeat")
                .id(beatId)
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("第" + chapterNumber + "章")
                .build());
        }

        List<Map<String, Object>> conflictArcs = (List<Map<String, Object>>) extracted.getOrDefault("conflictArcs", Collections.emptyList());
        for (Map<String, Object> arc : conflictArcs) {
            String arcId = arc.containsKey("id") ? (String) arc.get("id") : "conflict_arc_" + UUID.randomUUID();
            Map<String, Object> props = new HashMap<>(arc);
            props.put("importanceScore", resolveImportance(props.get("urgency"), 0.6));
            entities.add(GraphEntity.builder()
                .type("ConflictArc")
                .id(arcId)
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("第" + chapterNumber + "章")
                .build());
        }

        List<Map<String, Object>> characterArcs = (List<Map<String, Object>>) extracted.getOrDefault("characterArcs", Collections.emptyList());
        for (Map<String, Object> arc : characterArcs) {
            String arcId = arc.containsKey("id") ? (String) arc.get("id") : "character_arc_" + UUID.randomUUID();
            Map<String, Object> props = new HashMap<>(arc);
            props.put("importanceScore", resolveImportance(props.get("priority"), 0.55));
            entities.add(GraphEntity.builder()
                .type("CharacterArc")
                .id(arcId)
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("第" + chapterNumber + "章")
                .build());
        }

        Map<String, Object> perspective = (Map<String, Object>) extracted.get("perspectiveUsage");
        if (perspective != null && !perspective.isEmpty()) {
            String pid = perspective.containsKey("id") ? (String) perspective.get("id") : "perspective_" + chapterNumber;
            Map<String, Object> props = new HashMap<>(perspective);
            props.put("importanceScore", resolveImportance(props.get("weight"), 0.4));
            entities.add(GraphEntity.builder()
                .type("PerspectiveUsage")
                .id(pid)
                .chapterNumber(chapterNumber)
                .properties(props)
                .source("第" + chapterNumber + "章")
                .build());
        }
        
        return entities;
    }

    private double resolveImportance(Object raw, double defaultValue) {
        if (raw instanceof Number) {
            double value = ((Number) raw).doubleValue();
            if (value > 1) {
                return Math.min(1.0, value / 10.0);
            }
            return Math.max(0.0, Math.min(1.0, value));
        }
        if (raw instanceof String) {
            String normalized = ((String) raw).trim().toLowerCase();
            switch (normalized) {
                case "high":
                case "critical":
                case "核心":
                case "urgent":
                    return 0.85;
                case "medium":
                case "mid":
                case "中":
                    return 0.6;
                case "low":
                case "minor":
                case "次要":
                    return 0.35;
                default:
                    break;
            }
        }
        return defaultValue;
    }
    
    /**
     * 添加因果关系到图谱
     * 
     * 将AI抽取的事件因果关系添加到Neo4j图谱中
     */
    private void addCausalRelations(Long novelId, List<Map<String, Object>> causalRelations) {
        for (Map<String, Object> relation : causalRelations) {
            try {
                String fromEventId = (String) relation.get("from");
                String toEventId = (String) relation.get("to");
                String type = (String) relation.getOrDefault("type", "CAUSES");
                String description = (String) relation.getOrDefault("description", "");
                
                Map<String, Object> properties = new HashMap<>();
                properties.put("description", description);
                properties.put("type", type);
                
                graphService.addRelationship(novelId, fromEventId, type, toEventId, properties);
                
                logger.debug("✅ 添加因果关系: {} -[{}]-> {}", fromEventId, type, toEventId);
                
            } catch (Exception e) {
                logger.error("❌ 添加因果关系失败", e);
            }
        }
    }
    
    /**
     * 添加角色关系到图谱
     * 
     * 将AI抽取的角色关系添加到Neo4j图谱中
     */
    private void addCharacterRelations(Long novelId, Integer chapterNumber, List<Map<String, Object>> characterRelations) {
        for (Map<String, Object> relation : characterRelations) {
            try {
                String fromCharacter = (String) relation.get("from");
                String toCharacter = (String) relation.get("to");
                String type = firstNonBlankString(relation.get("type"), relation.get("relationType"));
                Object strengthObj = relation.get("strength");
                double strength = strengthObj != null ? ((Number) strengthObj).doubleValue() : 0.5;
                String description = firstNonBlankString(
                    relation.get("description"),
                    relation.get("status"),
                    relation.get("analysis"),
                    type
                );
                String publicStatus = firstNonBlankString(relation.get("publicStatus"), relation.get("visibility"));
                
                Map<String, Object> properties = new HashMap<>();
                properties.put("from", fromCharacter);
                properties.put("to", toCharacter);
                properties.put("type", type);
                properties.put("strength", strength);
                properties.put("description", description);
                properties.put("publicStatus", publicStatus);
                properties.put("lastUpdatedChapter", chapterNumber);
                
                graphService.addRelationship(novelId, fromCharacter, "RELATIONSHIP", toCharacter, properties);
                
                logger.debug("✅ 添加角色关系: {} <-> {} (状态: {}, 强度: {})", fromCharacter, toCharacter, description, strength);
                
            } catch (Exception e) {
                logger.error("❌ 添加角色关系失败", e);
            }
        }
    }
    
    /**
     * 🆕 异步抽取角色状态和关系变化（差异化抽取）
     * 
     * 核心思路：
     * 1. 查询当前图谱中的角色状态和关系
     * 2. 构建差异化提示词，告诉AI当前状态
     * 3. AI只输出变化的部分，不重复输出已有信息
     * 4. 应用变化到图谱
     * 
     * 优势：
     * - 防止覆盖：后续章节不会覆盖前面的重要关系
     * - 信息丰富：记录靠山、身份、秘密等关键信息
     * - 全题材通用：字段设计适配所有题材
     */
    public void extractStateAndRelationsAsync(Long novelId, Integer chapterNumber, String chapterTitle, String content, AIConfigRequest aiConfig) {
        try {
            logger.info("🔄 开始异步抽取角色状态和关系: novelId={}, chapter={}", novelId, chapterNumber);
            
            // 1. 查询当前图谱状态
            List<Map<String, Object>> currentStates = graphService.getCharacterStates(novelId, 100);
            List<Map<String, Object>> currentRelations = graphService.getTopRelationships(novelId, 100);
            
            // 2. 构建差异化提示词
            String prompt = buildStateAndRelationPrompt(chapterNumber, chapterTitle, content, currentStates, currentRelations);
            
            // 3. 调用AI
            String aiResponse = callAIForExtraction(prompt, aiConfig);
            
            // 4. 解析AI返回的变化
            Map<String, Object> changes = parseStateAndRelationChanges(aiResponse);
            
            // 5. 应用角色状态变化
            if (changes.containsKey("characterStateChanges")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> stateChanges = (List<Map<String, Object>>) changes.get("characterStateChanges");
                applyCharacterStateChanges(novelId, chapterNumber, stateChanges);
            }
            
            // 6. 应用关系变化
            if (changes.containsKey("relationshipChanges")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relationChanges = (List<Map<String, Object>>) changes.get("relationshipChanges");
                applyRelationshipChanges(novelId, chapterNumber, relationChanges);
            }
            
            logger.info("✅ 角色状态和关系抽取完成: chapter={}", chapterNumber);
            
        } catch (Exception e) {
            logger.error("❌ 角色状态和关系抽取失败: chapter={}", chapterNumber, e);
        }
    }
    
    /**
     * 构建差异化提示词（告诉AI当前状态，只输出变化）
     */
    private String buildStateAndRelationPrompt(Integer chapterNumber, String chapterTitle, String content,
                                               List<Map<String, Object>> currentStates,
                                               List<Map<String, Object>> currentRelations) {
        int safeChapterNumber = chapterNumber == null ? 0 : chapterNumber;
        String chapterTitleBlock = isBlank(chapterTitle) ? "" : "标题：" + chapterTitle + "\n";
        return renderManagedPrompt(
            "agentic.graph.state_relation.extraction.v2",
            "图谱状态关系差异提取提示词",
            "agentic",
            "根据当前图谱状态和章节正文，提取角色状态变化与关系变化的差异 JSON",
            522,
            List.of("current_states_lines", "current_relations_lines", "chapter_number", "chapter_title_block", "chapter_content", "since_chapter"),
            """
                你是一个专业的小说情节分析助手。请根据本章内容，分析角色状态和关系的变化。

                【当前角色状态】（仅供参考，如无变化则不输出）
                {{current_states_lines}}

                【当前角色关系】（仅供参考，如无变化则不输出）
                {{current_relations_lines}}

                【本章内容】
                章节号：第{{chapter_number}}章
                {{chapter_title_block}}{{chapter_content}}

                【抽取任务】
                请根据本章内容，只输出发生变化的状态和关系。如果某角色/关系没有变化，不要输出。

                返回JSON格式：
                {
                  "characterStateChanges": [
                    {
                      "name": "角色名",
                      "changeType": "UPDATE",
                      "changes": {
                        "realm": "新实力",
                        "socialStatus": "新地位",
                        "backers": {
                          "action": "ADD",
                          "values": [{"name": "靠山名", "type": "PERSON", "strength": 0.9, "desc": "说明"}]
                        },
                        "tags": {"action": "ADD", "values": ["新标签"]},
                        "secrets": {"action": "ADD", "values": ["新秘密"]},
                        "keyItems": {"action": "ADD", "values": [{"name": "物品", "type": "ITEM", "importance": 0.8, "desc": "说明"}]},
                        "knownBy": {"action": "ADD", "values": [{"character": "谁", "knows": "知道什么", "since": {{since_chapter}}}]}
                      },
                      "reason": "变化原因"
                    }
                  ],
                  "relationshipChanges": [
                    {
                      "from": "角色A",
                      "to": "角色B",
                      "changeType": "UPDATE",
                      "changes": {
                        "description": "用中文分析关系状态，例如：名义上仍是盟友，但本章后已经互相防备且开始试探底线",
                        "strength": 0.95,
                        "publicStatus": "PUBLIC/SECRET/SEMI_PUBLIC",
                        "type": "可选：合作/敌对/亲属/师徒等粗粒度中文概括，无法概括可留空"
                      },
                      "reason": "变化原因"
                    }
                  ]
                }

                【抽取规则】
                1. 只输出变化：如果某角色/关系没有变化，不要输出
                2. socialStatus（社会地位）：一句话概括角色在社会体系中的位置，影响他人态度
                3. backers（靠山/资源）：type=PERSON/ORGANIZATION/REPUTATION, strength=0-1
                4. tags（身份标签）：影响他人对待方式的身份
                5. secrets（秘密/限制）：行为限制、弱点、秘密
                6. keyItems（关键物品）：type=ITEM/SKILL/ASSET/ABILITY, importance=0-1
                7. knownBy（信息差）：谁知道角色的什么信息
                8. 关系变化：只有关系状态的中文描述发生实质变化、强度变化>0.1、或公开性变化时才输出
                9. description：必须是中文关系状态分析，优先表达复杂关系，不要退化成单个英文标签
                10. type：可选的粗粒度中文概括，不再要求固定英文枚举
                11. publicStatus：PUBLIC（公开）/SECRET（秘密）/SEMI_PUBLIC（半公开）
                12. 通用原则：所有字段全题材通用，不要出现题材特定术语

                只返回JSON，不要有其他解释。
                """,
            Map.of(
                "current_states_lines", buildCurrentStatesLines(currentStates),
                "current_relations_lines", buildCurrentRelationsLines(currentRelations),
                "chapter_number", safeChapterNumber,
                "chapter_title_block", chapterTitleBlock,
                "chapter_content", truncateForPrompt(content, 3000),
                "since_chapter", safeChapterNumber
            )
        );
    }
    
    /**
     * 解析AI返回的状态和关系变化
     */
    private Map<String, Object> parseStateAndRelationChanges(String aiResponse) {
        try {
            // 提取JSON部分
            String jsonStr = aiResponse;
            if (aiResponse.contains("```json")) {
                int start = aiResponse.indexOf("```json") + 7;
                int end = aiResponse.lastIndexOf("```");
                if (end > start) {
                    jsonStr = aiResponse.substring(start, end).trim();
                }
            } else if (aiResponse.contains("```")) {
                int start = aiResponse.indexOf("```") + 3;
                int end = aiResponse.lastIndexOf("```");
                if (end > start) {
                    jsonStr = aiResponse.substring(start, end).trim();
                }
            }
            
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
            return mapper.readValue(jsonStr, typeRef);
        } catch (Exception e) {
            logger.error("❌ 解析状态和关系变化失败", e);
            return new HashMap<>();
        }
    }
    
    /**
     * 应用角色状态变化
     */
    private void applyCharacterStateChanges(Long novelId, Integer chapterNumber, List<Map<String, Object>> changes) {
        for (Map<String, Object> change : changes) {
            try {
                String name = (String) change.get("name");
                String changeType = (String) change.get("changeType");
                @SuppressWarnings("unchecked")
                Map<String, Object> changeData = (Map<String, Object>) change.get("changes");
                String reason = (String) change.get("reason");
                
                logger.info("📝 应用角色状态变化: {} - {} ({})", name, changeType, reason);
                
                // 合并变化数据
                Map<String, Object> finalData = mergeCharacterStateChanges(novelId, name, changeData, changeType);
                
                // 更新到图谱
                graphService.upsertCharacterStateComplete(novelId, name, finalData, chapterNumber);
                
            } catch (Exception e) {
                logger.error("❌ 应用角色状态变化失败", e);
            }
        }
    }
    
    /**
     * 合并角色状态变化（处理ADD/REMOVE/REPLACE操作）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeCharacterStateChanges(Long novelId, String name, Map<String, Object> changeData, String changeType) {
        Map<String, Object> finalData = new HashMap<>();
        
        if ("NEW".equals(changeType)) {
            // 新角色，直接使用变化数据
            finalData.putAll(changeData);
        } else {
            // 更新角色，需要合并
            // 这里简化处理，直接使用changeData
            // 实际应该查询现有数据然后合并
            finalData.putAll(changeData);
            
            // 处理数组字段的ADD/REMOVE/REPLACE
            for (String key : new String[]{"backers", "tags", "secrets", "keyItems", "knownBy"}) {
                if (changeData.containsKey(key) && changeData.get(key) instanceof Map) {
                    Map<String, Object> arrayOp = (Map<String, Object>) changeData.get(key);
                    String action = (String) arrayOp.get("action");
                    Object values = arrayOp.get("values");
                    
                    if ("REPLACE".equals(action)) {
                        finalData.put(key, values);
                    } else if ("ADD".equals(action)) {
                        // 简化处理：直接设置新值
                        // 实际应该查询现有值然后追加
                        finalData.put(key, values);
                    }
                    // REMOVE操作类似
                }
            }
        }
        
        return finalData;
    }
    
    /**
     * 应用关系变化
     */
    private void applyRelationshipChanges(Long novelId, Integer chapterNumber, List<Map<String, Object>> changes) {
        for (Map<String, Object> change : changes) {
            try {
                String from = (String) change.get("from");
                String to = (String) change.get("to");
                String changeType = (String) change.get("changeType");
                @SuppressWarnings("unchecked")
                Map<String, Object> changeData = (Map<String, Object>) change.get("changes");
                String reason = (String) change.get("reason");
                
                logger.info("🤝 应用关系变化: {} ↔ {} - {} ({})", from, to, changeType, reason);
                
                // 更新到图谱
                graphService.upsertRelationshipStateComplete(novelId, from, to, changeData, chapterNumber);
                
            } catch (Exception e) {
                logger.error("❌ 应用关系变化失败", e);
            }
        }
    }

    private String buildGraphCharacterStateLines(List<Map<String, Object>> states) {
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

            Object lastChapter = state.get("lastChapter");
            if (lastChapter != null) {
                builder.append(" | 最近出现章节：第").append(lastChapter).append("章");
            }

            builder.append("\n");
        }

        return builder.length() == 0 ? "（无）" : builder.toString().trim();
    }

    private String buildGraphRelationshipLines(List<Map<String, Object>> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return "（无）";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> rel : relationships) {
            if (rel == null) {
                continue;
            }
            Object aObj = rel.get("a");
            Object bObj = rel.get("b");
            if (aObj == null || bObj == null) {
                continue;
            }
            String a = aObj.toString().trim();
            String b = bObj.toString().trim();
            if (a.isEmpty() || b.isEmpty()) {
                continue;
            }

            builder.append("- ").append(a).append(" ↔ ").append(b);

            String description = firstNonBlankString(rel.get("description"), rel.get("status"), rel.get("type"));
            if (!description.isEmpty()) {
                builder.append(" | 关系状态：").append(description);
            }

            String type = firstNonBlankString(rel.get("type"));
            if (!type.isEmpty() && !type.equals(description)) {
                builder.append(" | 粗分类：").append(type);
            }

            Object strength = rel.get("strength");
            if (strength != null) {
                builder.append(" | 强度：").append(strength);
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

            builder.append("- 任务ID：").append(id);

            Object desc = quest.get("description");
            if (desc != null && !desc.toString().trim().isEmpty()) {
                builder.append(" | 简述：").append(desc);
            }

            Object status = quest.get("status");
            if (status != null && !status.toString().trim().isEmpty()) {
                builder.append(" | 状态：").append(status);
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

    private String buildBatchChapterInputsBlock(List<Chapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Chapter chapter : chapters) {
            if (chapter == null || chapter.getChapterNumber() == null) {
                continue;
            }

            builder.append("### 第").append(chapter.getChapterNumber()).append("章\n")
                .append("标题: ").append(safeText(chapter.getTitle())).append("\n")
                .append("正文:\n")
                .append(truncateContent(chapter.getContent()))
                .append("\n\n");
        }

        return builder.toString().trim();
    }

    private String buildCurrentStatesLines(List<Map<String, Object>> currentStates) {
        if (currentStates == null || currentStates.isEmpty()) {
            return "（无）";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> state : currentStates) {
            if (state == null) {
                continue;
            }

            String name = safeText((String) state.get("name"));
            if (name.isEmpty()) {
                continue;
            }

            builder.append("- ").append(name).append("：\n");
            builder.append("  * 基础：");

            if (state.get("location") != null) {
                builder.append("位置=").append(state.get("location")).append(", ");
            }
            if (state.get("realm") != null) {
                builder.append("实力=").append(state.get("realm")).append(", ");
            }
            if (state.get("affiliation") != null) {
                builder.append("势力=").append(state.get("affiliation")).append(", ");
            }

            Object aliveObj = state.getOrDefault("alive", true);
            boolean alive = aliveObj instanceof Boolean ? (Boolean) aliveObj : true;
            builder.append("生死=").append(alive ? "存活" : "死亡").append("\n");

            if (state.get("socialStatus") != null) {
                builder.append("  * 地位：").append(state.get("socialStatus")).append("\n");
            }
            if (state.get("backers") != null) {
                builder.append("  * 靠山：").append(state.get("backers")).append("\n");
            }
            if (state.get("tags") != null) {
                builder.append("  * 标签：").append(state.get("tags")).append("\n");
            }
            if (state.get("secrets") != null) {
                builder.append("  * 秘密：").append(state.get("secrets")).append("\n");
            }
            if (state.get("keyItems") != null) {
                builder.append("  * 物品：").append(state.get("keyItems")).append("\n");
            }
            builder.append("\n");
        }

        return builder.length() == 0 ? "（无）" : builder.toString().trim();
    }

    private String buildCurrentRelationsLines(List<Map<String, Object>> currentRelations) {
        if (currentRelations == null || currentRelations.isEmpty()) {
            return "（无）";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> rel : currentRelations) {
            if (rel == null) {
                continue;
            }

            String a = safeText((String) rel.get("a"));
            String b = safeText((String) rel.get("b"));
            if (a.isEmpty() || b.isEmpty()) {
                continue;
            }

            builder.append("- ").append(a).append(" ↔ ").append(b).append("：");

            String description = firstNonBlankString(rel.get("description"), rel.get("status"), rel.get("type"));
            if (!description.isEmpty()) {
                builder.append("status=\"").append(description).append("\"");
            } else {
                builder.append("status=未知");
            }

            String type = firstNonBlankString(rel.get("type"));
            if (!type.isEmpty() && !type.equals(description)) {
                builder.append(", type=").append(type);
            }

            Object strength = rel.get("strength");
            if (strength != null) {
                builder.append(", strength=").append(strength);
            }

            Object publicStatus = rel.get("publicStatus");
            if (publicStatus != null) {
                builder.append(", public=").append(publicStatus);
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

    private String truncateForPrompt(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlankString(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }
}
