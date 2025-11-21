package com.novel.agentic.service.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.GraphEntity;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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
                addCharacterRelations(novelId, characterRelations);
                logger.info("✅ 添加了{}个角色关系", characterRelations.size());
            }
            
            logger.info("🎉 实体抽取完成: novelId={}, chapter={}, count={}", novelId, chapterNumber, entities.size());
            
        } catch (Exception e) {
            logger.error("❌ 实体抽取失败: chapter={}", chapterNumber, e);
        }
    }
    
    /**
     * 构建抽取提示词
     */
    private String buildExtractionPrompt(Integer chapterNumber, String chapterTitle, String content) {
        return String.format(
            "你是一位专业的小说分析助手。请从以下章节中抽取关键实体和信息。\n" +
            "\n" +
            "【章节信息】\n" +
            "章节号：第%d章\n" +
            "章节标题：%s\n" +
            "\n" +
            "【章节内容】\n" +
            "%s\n" +
            "\n" +
            "【抽取要求】\n" +
            "请以JSON格式返回以下内容（如果某类不存在则返回空数组）：\n" +
            "\n" +
            "{\n" +
            "  \"events\": [\n" +
            "    {\n" +
            "      \"id\": \"event_%d_1\",\n" +
            "      \"summary\": \"事件摘要（30字内）\",\n" +
            "      \"description\": \"事件详细描述\",\n" +
            "      \"location\": \"事件发生地点\",\n" +
            "      \"participants\": [\"角色A\", \"角色B\"],\n" +
            "      \"emotionalTone\": \"positive/negative/neutral/tense\",\n" +
            "      \"tags\": [\"战斗\", \"对话\", \"决策\"],\n" +
            "      \"importance\": 0.8\n" +
            "    }\n" +
            "  ],\n" +
            "  \"foreshadows\": [\n" +
            "    {\n" +
            "      \"id\": \"foreshadow_%d_1\",\n" +
            "      \"content\": \"伏笔内容\",\n" +
            "      \"importance\": \"high/medium/low\",\n" +
            "      \"suggestedRevealChapter\": %d\n" +
            "    }\n" +
            "  ],\n" +
            "  \"plotlines\": [\n" +
            "    {\n" +
            "      \"id\": \"plotline_主线\",\n" +
            "      \"name\": \"主线名称\",\n" +
            "      \"priority\": 1.0\n" +
            "    }\n" +
            "  ],\n" +
            "  \"worldRules\": [\n" +
            "    {\n" +
            "      \"id\": \"rule_power_system\",\n" +
            "      \"name\": \"规则名称\",\n" +
            "      \"content\": \"规则内容\",\n" +
            "      \"constraint\": \"约束说明\",\n" +
            "      \"category\": \"power_system/world_setting/character_constraint\",\n" +
            "      \"importance\": 0.9\n" +
            "    }\n" +
            "  ],\n" +
            "  \"characters\": [\"本章新出现的具名角色（必须有明确姓名，且预计后续会再次登场的重要角色）\"],\n" +
            "  \"locations\": [\"本章新出现且对后续剧情有持续影响的地点\"],\n" +
            "  \"causalRelations\": [\n" +
            "    {\n" +
            "      \"from\": \"event_%d_1\",\n" +
            "      \"to\": \"event_%d_2\",\n" +
            "      \"type\": \"CAUSES\",\n" +
            "      \"description\": \"事件1导致了事件2\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"characterRelations\": [\n" +
            "    {\n" +
            "      \"from\": \"角色A\",\n" +
            "      \"to\": \"角色B\",\n" +
            "      \"type\": \"CONFLICT/COOPERATION/ROMANCE/MENTORSHIP/RIVALRY\",\n" +
            "      \"strength\": 0.8,\n" +
            "      \"description\": \"关系描述\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"stateChanges\": {\n" +
            "    \"characters\": [\n" +
            "      {\n" +
            "        \"name\": \"角色名\",\n" +
            "        \"alive\": true,\n" +
            "        \"location\": \"当前位置（如改变）\",\n" +
            "        \"realm\": \"实力等级（如突破）\",\n" +
            "        \"affiliation\": \"所属势力（如改变）\",\n" +
            "        \"stateDesc\": \"状态变化简述\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"factions\": [\n" +
            "      {\n" +
            "        \"name\": \"势力名\",\n" +
            "        \"status\": \"active\",\n" +
            "        \"leaderAlive\": true,\n" +
            "        \"casualties\": [{\"name\": \"死亡成员\", \"role\": \"角色\"}],\n" +
            "        \"stateDesc\": \"状态变化简述\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"locations\": [\n" +
            "      {\n" +
            "        \"name\": \"地点名\",\n" +
            "        \"currentOccupants\": [\"当前在此的主要角色\"],\n" +
            "        \"controlledBy\": \"控制者（如有）\",\n" +
            "        \"stateDesc\": \"状态变化简述\"\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  \"narrativeBeat\": {\n" +
            "    \"id\": \"beat_%d\",\n" +
            "    \"beatType\": \"CONFLICT/CLIMAX/PLOT/CHARACTER/RELIEF\",\n" +
            "    \"focus\": \"剧情/人物/世界观\",\n" +
            "    \"tension\": 0.7,\n" +
            "    \"sentiment\": \"tense/hopeful/tragic\",\n" +
            "    \"paceScore\": 0.6,\n" +
            "    \"viewpoint\": \"主角/配角/反派/旁观者\"\n" +
            "  },\n" +
            "  \"conflictArcs\": [\n" +
            "    {\n" +
            "      \"id\": \"conflict_arc_%d\",\n" +
            "      \"name\": \"冲突线名称\",\n" +
            "      \"stage\": \"酝酿/爆发/僵持/解决\",\n" +
            "      \"urgency\": 0.8,\n" +
            "      \"nextAction\": \"下一步升级计划\",\n" +
            "      \"protagonist\": \"主角名\",\n" +
            "      \"antagonist\": \"对手名\",\n" +
            "      \"trend\": \"UP/FLAT/DOWN\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"characterArcs\": [\n" +
            "    {\n" +
            "      \"id\": \"character_arc_%d\",\n" +
            "      \"characterName\": \"角色名\",\n" +
            "      \"arcName\": \"成长线名称\",\n" +
            "      \"pendingBeat\": \"待完成的成长节点\",\n" +
            "      \"nextGoal\": \"下一目标\",\n" +
            "      \"priority\": 0.7,\n" +
            "      \"progress\": 2,\n" +
            "      \"totalBeats\": 5\n" +
            "    }\n" +
            "  ],\n" +
            "  \"perspectiveUsage\": {\n" +
            "    \"id\": \"perspective_%d\",\n" +
            "    \"characterName\": \"本章视角角色\",\n" +
            "    \"mode\": \"第一人称/第三人称/全知\",\n" +
            "    \"tone\": \"tense/hopeful/warm\",\n" +
            "    \"purpose\": \"切换视角的目的\"\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "注意：\n" +
            "1. events至少抽取3-5个关键事件，每个事件必须包含location字段（地点）\n" +
            "2. location必须准确提取，用于跟踪角色位置和场景连贯性\n" +
            "3. foreshadows只抽取明显的伏笔（如神秘预言、未解之谜、隐藏信息）\n" +
            "4. worldRules只抽取新引入的设定规则\n" +
            "5. importance范围0-1，越重要值越大\n" +
            "6. causalRelations抽取事件间的因果关系（如某事件导致另一事件）\n" +
            "7. characterRelations抽取角色间关系的变化（如产生矛盾、建立友谊等），至少包含主角与关键角色之间的重要关系变动。\n" +
            "8.  **characters提取规则（严格执行）**：\n" +
            "   -  必须是具名角色（有明确的姓名，如：林晨、张伟、李教授）\n" +
            "   -  必须是有台词、有动作、有性格描写的独立角色\n" +
            "   -  预计后续章节会再次出现的重要角色\n" +
            "   -  不要提取：一次性龙套角色（只有一句台词或只是背景板）\n" +
            "   - 示例对比：正确提取[林晨、苏婉] 错误提取[记者、群众]\n" +
            "9. **stateChanges（极重要！）**必须抽取所有状态变更：\n" +
            "   - characters: 角色生死(alive)、位置(location)、实力(realm)、势力(affiliation)\n" +
            "   - factions: 势力状态(status)、领袖生死(leaderAlive)、伤亡(casualties)\n" +
            "   - locations: 地点当前占据者(currentOccupants)、控制者(controlledBy)\n" +
            "   这些状态对后续章节一致性至关重要，如有变化必须详细记录！\n" +
            "10. narrativeBeat用于总结本章节奏意图；conflictArcs/characterArcs仅列出本章推进的弧线。如果某项不存在，请返回空对象或空数组。\n" +
            "11. 只返回JSON，不要有其他解释\n",
            chapterNumber, chapterTitle, 
            content.length() > 3000 ? content.substring(0, 3000) + "..." : content,
            chapterNumber, chapterNumber, chapterNumber + 5, chapterNumber, chapterNumber,
            chapterNumber, chapterNumber, chapterNumber, chapterNumber);
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
    private void addCharacterRelations(Long novelId, List<Map<String, Object>> characterRelations) {
        for (Map<String, Object> relation : characterRelations) {
            try {
                String fromCharacter = (String) relation.get("from");
                String toCharacter = (String) relation.get("to");
                String type = (String) relation.getOrDefault("type", "RELATIONSHIP");
                Object strengthObj = relation.get("strength");
                double strength = strengthObj != null ? ((Number) strengthObj).doubleValue() : 0.5;
                String description = (String) relation.getOrDefault("description", "");
                
                Map<String, Object> properties = new HashMap<>();
                properties.put("from", fromCharacter);
                properties.put("to", toCharacter);
                properties.put("type", type);
                properties.put("strength", strength);
                properties.put("description", description);
                
                graphService.addRelationship(novelId, fromCharacter, "RELATIONSHIP", toCharacter, properties);
                
                logger.debug("✅ 添加角色关系: {} -[{}]-> {} (强度: {})", fromCharacter, type, toCharacter, strength);
                
            } catch (Exception e) {
                logger.error("❌ 添加角色关系失败", e);
            }
        }
    }
}
