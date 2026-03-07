package com.novel.agentic.service.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.entity.graph.*;
import com.novel.agentic.mapper.*;
import com.novel.agentic.model.GraphEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL 图数据库服务实现
 * 
 * 替代Neo4j实现，使用MySQL关系型数据库存储图谱数据
 */
@Service
@Primary
public class MySQLGraphService implements IGraphService {
    
    private static final Logger logger = LoggerFactory.getLogger(MySQLGraphService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private GraphCharacterStateMapper characterStateMapper;
    
    @Autowired
    private GraphRelationshipStateMapper relationshipStateMapper;
    
    @Autowired
    private GraphOpenQuestMapper openQuestMapper;
    
    @Autowired
    private GraphEventMapper eventMapper;
    
    @Autowired
    private GraphForeshadowingMapper foreshadowingMapper;
    
    @Autowired
    private GraphPlotlineMapper plotlineMapper;
    
    @Autowired
    private GraphWorldRuleMapper worldRuleMapper;
    
    // =============================
    // 查询方法实现
    // =============================
    
    @Override
    public List<GraphEntity> getRelevantEvents(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 MySQL查询相关事件: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
        
        if (chapterNumber <= 1) {
            return Collections.emptyList();
        }
        
        List<GraphEvent> events = eventMapper.findRelevantEvents(novelId, chapterNumber, limit);
        
        return events.stream().map(event -> {
            Map<String, Object> props = new HashMap<>();
            props.put("description", event.getSummary() != null ? event.getSummary() : event.getDescription());
            props.put("emotionalTone", event.getEmotionalTone());
            props.put("tags", parseJsonArray(event.getTags()));
            props.put("importanceScore", event.getImportance() != null ? event.getImportance() : 0.5);
            
            return GraphEntity.builder()
                .type("Event")
                .id(event.getEventId())
                .chapterNumber(event.getChapterNumber())
                .relevanceScore(event.getImportance())
                .properties(props)
                .source("第" + event.getChapterNumber() + "章")
                .build();
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<GraphEntity> getUnresolvedForeshadows(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 MySQL查询未回收伏笔: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
        
        List<GraphForeshadowing> foreshadows = foreshadowingMapper.findUnresolvedForeshadows(novelId, chapterNumber, limit);
        
        return foreshadows.stream().map(f -> {
            double importanceScore = mapImportance(f.getImportance(), 0.6);
            
            Map<String, Object> props = new HashMap<>();
            props.put("description", f.getContent());
            props.put("plantedAt", "第" + f.getIntroducedChapter() + "章");
            props.put("suggestedResolveWindow", "第" + chapterNumber + "-" + (chapterNumber + 10) + "章");
            props.put("importance", f.getImportance());
            props.put("status", f.getStatus());
            props.put("importanceScore", importanceScore);
            
            return GraphEntity.builder()
                .type("Foreshadow")
                .id(f.getForeshadowId())
                .chapterNumber(f.getIntroducedChapter())
                .relevanceScore(0.9)
                .properties(props)
                .source("第" + f.getIntroducedChapter() + "章")
                .build();
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<GraphEntity> getPlotlineStatus(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 MySQL查询情节线状态: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
        
        List<GraphPlotline> plotlines = plotlineMapper.findIdlePlotlines(novelId, chapterNumber, limit);
        
        return plotlines.stream().map(p -> {
            int lastTouched = p.getLastTouchedChapter() != null ? p.getLastTouchedChapter() : 0;
            int idleDuration = chapterNumber - lastTouched;
            String status = idleDuration > 10 ? "久未推进" : "待发展";
            
            Map<String, Object> props = new HashMap<>();
            props.put("name", p.getName() != null ? p.getName() : "未命名情节线");
            props.put("status", status);
            props.put("lastUpdate", "第" + lastTouched + "章");
            props.put("idleDuration", idleDuration);
            props.put("priority", p.getPriority() != null ? p.getPriority() : 0.5);
            
            return GraphEntity.builder()
                .type("Plotline")
                .id(p.getPlotlineId())
                .chapterNumber(lastTouched)
                .relevanceScore(1.0 - (idleDuration / 50.0))
                .properties(props)
                .source("系统")
                .build();
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<GraphEntity> getWorldRules(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 MySQL查询世界规则: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
        
        List<GraphWorldRule> rules = worldRuleMapper.findApplicableRules(novelId, chapterNumber, limit);
        
        return rules.stream().map(r -> {
            Map<String, Object> props = new HashMap<>();
            props.put("name", r.getName() != null ? r.getName() : "规则");
            props.put("description", r.getContent());
            props.put("constraint", r.getConstraintText());
            props.put("category", r.getCategory());
            props.put("scope", r.getScope());
            
            return GraphEntity.builder()
                .type("WorldRule")
                .id(r.getRuleId())
                .chapterNumber(r.getIntroducedAt())
                .relevanceScore(1.0)
                .properties(props)
                .source("设定")
                .build();
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<GraphEntity> getCharacterRelationships(Long novelId, String characterName, Integer limit) {
        logger.info("🔍 MySQL查询角色关系网: novelId={}, character={}", novelId, characterName);
        
        List<GraphRelationshipState> relationships = relationshipStateMapper.findByCharacterName(novelId, characterName, limit);
        
        return relationships.stream().map(r -> {
            String targetName = r.getCharacterA().equals(characterName) ? r.getCharacterB() : r.getCharacterA();
            
            Map<String, Object> props = new HashMap<>();
            props.put("from", characterName);
            props.put("to", targetName);
            props.put("relationType", r.getType());
            props.put("strength", r.getStrength() != null ? r.getStrength() : 0.5);
            props.put("description", r.getDescription());
            
            return GraphEntity.builder()
                .type("CharacterRelationship")
                .id(characterName + "_" + targetName + "_" + r.getType())
                .relevanceScore(r.getStrength())
                .properties(props)
                .source("关系网")
                .build();
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<GraphEntity> getEventsByCharacter(Long novelId, String characterName, Integer chapterNumber, Integer limit) {
        logger.info("🔍 MySQL按角色查询事件: novelId={}, character={}", novelId, characterName);
        // 简化实现：返回空列表（需要事件参与者关联表支持）
        return Collections.emptyList();
    }
    
    @Override
    public List<GraphEntity> getEventsByCausality(Long novelId, String eventId, Integer depth) {
        logger.info("🔍 MySQL按因果链查询: novelId={}, eventId={}", novelId, eventId);
        // 简化实现：返回空列表（需要因果关系表支持）
        return Collections.emptyList();
    }
    
    @Override
    public List<GraphEntity> getConflictHistory(Long novelId, String protagonistName, String antagonistName, Integer limit) {
        logger.info("🔍 MySQL查询冲突历史: novelId={}, protagonist={}, antagonist={}", novelId, protagonistName, antagonistName);
        // 简化实现：返回空列表
        return Collections.emptyList();
    }
    
    @Override
    public Map<String, Object> getNarrativeRhythmStatus(Long novelId, Integer chapterNumber, Integer window) {
        Map<String, Object> status = new HashMap<>();
        status.put("recentBeats", Collections.emptyList());
        status.put("metrics", new HashMap<>());
        status.put("recommendations", Arrays.asList("尚无节奏记录，参考卷蓝图规划章节节奏。"));
        return status;
    }
    
    @Override
    public List<GraphEntity> getActiveConflictArcs(Long novelId, Integer chapterNumber, Integer limit) {
        return Collections.emptyList();
    }
    
    @Override
    public List<GraphEntity> getCharacterArcStatus(Long novelId, Integer chapterNumber, Integer limit) {
        return Collections.emptyList();
    }
    
    @Override
    public List<GraphEntity> getPerspectiveHistory(Long novelId, Integer chapterNumber, Integer window) {
        return Collections.emptyList();
    }
    
    // =============================
    // 写入方法实现
    // =============================
    
    @Override
    @Transactional
    public void addEntity(Long novelId, GraphEntity entity) {
        logger.info("➕ MySQL添加实体: type={}, id={}", entity.getType(), entity.getId());
        
        String type = entity.getType();
        Map<String, Object> props = entity.getProperties() != null ? entity.getProperties() : new HashMap<>();
        
        switch (type) {
            case "Event":
                addEventEntity(novelId, entity, props);
                break;
            case "Foreshadow":
                addForeshadowEntity(novelId, entity, props);
                break;
            case "Plotline":
                addPlotlineEntity(novelId, entity, props);
                break;
            case "WorldRule":
                addWorldRuleEntity(novelId, entity, props);
                break;
            default:
                logger.warn("未知实体类型: {}", type);
        }
    }
    
    private void addEventEntity(Long novelId, GraphEntity entity, Map<String, Object> props) {
        GraphEvent existing = eventMapper.findByNovelIdAndEventId(novelId, entity.getId());
        
        GraphEvent event = GraphEvent.builder()
            .novelId(novelId)
            .eventId(entity.getId())
            .chapterNumber(entity.getChapterNumber())
            .summary(getStringProp(props, "summary", getStringProp(props, "description", "")))
            .description(getStringProp(props, "description", ""))
            .location(getStringProp(props, "location", ""))
            .realm(getStringProp(props, "realm", ""))
            .emotionalTone(getStringProp(props, "emotionalTone", "中性"))
            .tags(toJsonArray(props.get("tags")))
            .importance(getDoubleProp(props, "importance", 0.5))
            .build();
        
        if (existing != null) {
            event.setId(existing.getId());
            eventMapper.updateById(event);
        } else {
            eventMapper.insert(event);
        }
    }
    
    private void addForeshadowEntity(Long novelId, GraphEntity entity, Map<String, Object> props) {
        GraphForeshadowing existing = foreshadowingMapper.findByNovelIdAndForeshadowId(novelId, entity.getId());
        
        GraphForeshadowing foreshadow = GraphForeshadowing.builder()
            .novelId(novelId)
            .foreshadowId(entity.getId())
            .content(getStringProp(props, "content", getStringProp(props, "description", "")))
            .importance(getStringProp(props, "importance", "medium"))
            .status(getStringProp(props, "status", "PLANTED"))
            .introducedChapter(entity.getChapterNumber())
            .plannedRevealChapter(getIntProp(props, "plannedRevealChapter", null))
            .build();
        
        if (existing != null) {
            foreshadow.setId(existing.getId());
            foreshadowingMapper.updateById(foreshadow);
        } else {
            foreshadowingMapper.insert(foreshadow);
        }
    }
    
    private void addPlotlineEntity(Long novelId, GraphEntity entity, Map<String, Object> props) {
        GraphPlotline existing = plotlineMapper.findByNovelIdAndPlotlineId(novelId, entity.getId());
        
        GraphPlotline plotline = GraphPlotline.builder()
            .novelId(novelId)
            .plotlineId(entity.getId())
            .name(getStringProp(props, "name", "未命名情节线"))
            .priority(getDoubleProp(props, "priority", 0.5))
            .lastTouchedChapter(entity.getChapterNumber())
            .build();
        
        if (existing != null) {
            plotline.setId(existing.getId());
            plotlineMapper.updateById(plotline);
        } else {
            plotlineMapper.insert(plotline);
        }
    }
    
    private void addWorldRuleEntity(Long novelId, GraphEntity entity, Map<String, Object> props) {
        GraphWorldRule existing = worldRuleMapper.findByNovelIdAndRuleId(novelId, entity.getId());
        
        GraphWorldRule rule = GraphWorldRule.builder()
            .novelId(novelId)
            .ruleId(entity.getId())
            .name(getStringProp(props, "name", "未命名规则"))
            .content(getStringProp(props, "content", getStringProp(props, "description", "")))
            .constraintText(getStringProp(props, "constraint", ""))
            .category(getStringProp(props, "category", "general"))
            .scope(getStringProp(props, "scope", "global"))
            .importance(getDoubleProp(props, "importance", 0.5))
            .introducedAt(entity.getChapterNumber())
            .build();
        
        if (existing != null) {
            rule.setId(existing.getId());
            worldRuleMapper.updateById(rule);
        } else {
            worldRuleMapper.insert(rule);
        }
    }
    
    @Override
    @Transactional
    public void addEntities(Long novelId, List<GraphEntity> entities) {
        entities.forEach(entity -> addEntity(novelId, entity));
    }
    
    @Override
    public void addRelationship(Long novelId, String fromEntityId, String relationshipType, String toEntityId, Map<String, Object> properties) {
        logger.info("➕ MySQL添加关系: {} -[{}]-> {}", fromEntityId, relationshipType, toEntityId);
        
        if ("RELATIONSHIP".equalsIgnoreCase(relationshipType)) {
            // 角色关系
            String a = fromEntityId.compareTo(toEntityId) < 0 ? fromEntityId : toEntityId;
            String b = fromEntityId.compareTo(toEntityId) < 0 ? toEntityId : fromEntityId;
            
            GraphRelationshipState existing = relationshipStateMapper.findByNovelIdAndCharacters(novelId, a, b);
            
            GraphRelationshipState rel = GraphRelationshipState.builder()
                .novelId(novelId)
                .characterA(a)
                .characterB(b)
                .type(properties != null ? getStringProp(properties, "type", "") : "")
                .strength(properties != null ? getDoubleProp(properties, "strength", 0.5) : 0.5)
                .description(properties != null ? getStringProp(properties, "description", "") : "")
                .build();
            
            if (existing != null) {
                rel.setId(existing.getId());
                relationshipStateMapper.updateById(rel);
            } else {
                relationshipStateMapper.insert(rel);
            }
        }
    }
    
    // =============================
    // 核心记忆账本写入实现
    // =============================
    
    @Override
    @Transactional
    public void upsertCharacterState(Long novelId, String characterName, String location, String realm, Boolean alive, Integer chapterNumber) {
        upsertCharacterStateWithInfo(novelId, characterName, location, realm, alive, null, chapterNumber);
    }
    
    @Override
    @Transactional
    public void upsertCharacterStateWithInfo(Long novelId, String characterName, String location, String realm, Boolean alive, String characterInfo, Integer chapterNumber) {
        GraphCharacterState existing = characterStateMapper.findByNovelIdAndCharacterName(novelId, characterName);
        
        if (existing != null && existing.getLastUpdatedChapter() != null && chapterNumber < existing.getLastUpdatedChapter()) {
            logger.info("跳过旧章节更新: {}@{} (当前最新章节: {})", characterName, chapterNumber, existing.getLastUpdatedChapter());
            return;
        }
        
        GraphCharacterState state = GraphCharacterState.builder()
            .novelId(novelId)
            .characterName(characterName)
            .location(location != null ? location : (existing != null ? existing.getLocation() : null))
            .realm(realm != null ? realm : (existing != null ? existing.getRealm() : null))
            .alive(alive != null ? alive : (existing != null ? existing.getAlive() : true))
            .characterInfo(characterInfo != null && !characterInfo.isEmpty() ? characterInfo : (existing != null ? existing.getCharacterInfo() : null))
            .inventory(existing != null ? existing.getInventory() : null)
            .lastUpdatedChapter(chapterNumber)
            .build();
        
        if (existing != null) {
            state.setId(existing.getId());
            characterStateMapper.updateById(state);
        } else {
            characterStateMapper.insert(state);
        }
        
        logger.info("🧭 upsertCharacterState: {}@{} loc={}, realm={}, alive={}", characterName, chapterNumber, location, realm, alive);
    }
    
    @Override
    @Transactional
    public void upsertCharacterStateComplete(Long novelId, String characterName, Map<String, Object> stateData, Integer chapterNumber) {
        GraphCharacterState existing = characterStateMapper.findByNovelIdAndCharacterName(novelId, characterName);
        
        if (existing != null && existing.getLastUpdatedChapter() != null && chapterNumber < existing.getLastUpdatedChapter()) {
            return;
        }
        
        GraphCharacterState state = GraphCharacterState.builder()
            .novelId(novelId)
            .characterName(characterName)
            .location(getStringProp(stateData, "location", existing != null ? existing.getLocation() : null))
            .realm(getStringProp(stateData, "realm", existing != null ? existing.getRealm() : null))
            .alive((Boolean) stateData.getOrDefault("alive", existing != null ? existing.getAlive() : true))
            .affiliation(getStringProp(stateData, "affiliation", existing != null ? existing.getAffiliation() : null))
            .socialStatus(getStringProp(stateData, "socialStatus", existing != null ? existing.getSocialStatus() : null))
            .backers(toJsonArray(stateData.get("backers")))
            .tags(toJsonArray(stateData.get("tags")))
            .secrets(toJsonArray(stateData.get("secrets")))
            .keyItems(toJsonArray(stateData.get("keyItems")))
            .knownBy(toJsonArray(stateData.get("knownBy")))
            .lastUpdatedChapter(chapterNumber)
            .build();
        
        if (existing != null) {
            state.setId(existing.getId());
            characterStateMapper.updateById(state);
        } else {
            characterStateMapper.insert(state);
        }
    }
    
    @Override
    @Transactional
    public void updateCharacterInventory(Long novelId, String characterName, List<String> items, Integer chapterNumber) {
        GraphCharacterState existing = characterStateMapper.findByNovelIdAndCharacterName(novelId, characterName);
        
        if (existing != null) {
            existing.setInventory(toJsonArray(items));
            existing.setLastUpdatedChapter(chapterNumber);
            characterStateMapper.updateById(existing);
        }
        
        logger.info("💼 updateInventory: {} 持有{}件物品", characterName, items != null ? items.size() : 0);
    }
    
    @Override
    @Transactional
    public void upsertRelationshipState(Long novelId, String characterA, String characterB, String type, Double strength, Integer chapterNumber) {
        String a = characterA.compareTo(characterB) < 0 ? characterA : characterB;
        String b = characterA.compareTo(characterB) < 0 ? characterB : characterA;
        
        GraphRelationshipState existing = relationshipStateMapper.findByNovelIdAndCharacters(novelId, a, b);
        
        GraphRelationshipState rel = GraphRelationshipState.builder()
            .novelId(novelId)
            .characterA(a)
            .characterB(b)
            .type(type != null ? type : (existing != null ? existing.getType() : ""))
            .strength(strength != null ? strength : (existing != null ? existing.getStrength() : 0.5))
            .lastUpdatedChapter(chapterNumber)
            .build();
        
        if (existing != null) {
            rel.setId(existing.getId());
            relationshipStateMapper.updateById(rel);
        } else {
            relationshipStateMapper.insert(rel);
        }
        
        logger.info("🤝 upsertRelationshipState: {}—{} type={} strength={}", characterA, characterB, type, strength);
    }
    
    @Override
    @Transactional
    public void upsertRelationshipStateComplete(Long novelId, String characterA, String characterB, Map<String, Object> relationData, Integer chapterNumber) {
        String a = characterA.compareTo(characterB) < 0 ? characterA : characterB;
        String b = characterA.compareTo(characterB) < 0 ? characterB : characterA;
        
        GraphRelationshipState existing = relationshipStateMapper.findByNovelIdAndCharacters(novelId, a, b);
        
        GraphRelationshipState rel = GraphRelationshipState.builder()
            .novelId(novelId)
            .characterA(a)
            .characterB(b)
            .type(getStringProp(relationData, "type", existing != null ? existing.getType() : ""))
            .strength(getDoubleProp(relationData, "strength", existing != null ? existing.getStrength() : 0.5))
            .description(getStringProp(relationData, "description", existing != null ? existing.getDescription() : ""))
            .publicStatus(getStringProp(relationData, "publicStatus", existing != null ? existing.getPublicStatus() : ""))
            .lastUpdatedChapter(chapterNumber)
            .build();
        
        if (existing != null) {
            rel.setId(existing.getId());
            relationshipStateMapper.updateById(rel);
        } else {
            relationshipStateMapper.insert(rel);
        }
    }
    
    @Override
    @Transactional
    public void upsertOpenQuest(Long novelId, String questId, String description, String status, Integer introducedChapter, Integer dueByChapter, Integer lastUpdatedChapter) {
        GraphOpenQuest existing = openQuestMapper.findByNovelIdAndQuestId(novelId, questId);
        
        GraphOpenQuest quest = GraphOpenQuest.builder()
            .novelId(novelId)
            .questId(questId)
            .description(description != null ? description : (existing != null ? existing.getDescription() : ""))
            .status(status != null ? status : (existing != null ? existing.getStatus() : "OPEN"))
            .introducedChapter(existing != null ? existing.getIntroducedChapter() : introducedChapter)
            .dueByChapter(dueByChapter != null ? dueByChapter : (existing != null ? existing.getDueByChapter() : null))
            .lastUpdatedChapter(lastUpdatedChapter)
            .build();
        
        if (existing != null) {
            quest.setId(existing.getId());
            openQuestMapper.updateById(quest);
        } else {
            openQuestMapper.insert(quest);
        }
        
        logger.info("📌 upsertOpenQuest: {} status={}", questId, status);
    }
    
    @Override
    @Transactional
    public void resolveOpenQuest(Long novelId, String questId, Integer resolvedChapter) {
        GraphOpenQuest existing = openQuestMapper.findByNovelIdAndQuestId(novelId, questId);
        
        if (existing != null) {
            existing.setStatus("RESOLVED");
            existing.setResolvedChapter(resolvedChapter);
            existing.setLastUpdatedChapter(resolvedChapter);
            openQuestMapper.updateById(existing);
        }
        
        logger.info("✅ resolveOpenQuest: {}@{}", questId, resolvedChapter);
    }
    
    @Override
    public void addSummarySignals(Long novelId, Integer chapterNumber, Map<String, String> signals) {
        // 简化实现：暂不存储摘要信号
        logger.info("🧾 addSummarySignals: chapter={} keys={}", chapterNumber, signals != null ? signals.keySet() : "null");
    }
    
    // =============================
    // 删除方法实现
    // =============================
    
    @Override
    @Transactional
    public void deleteRelationshipState(Long novelId, String characterA, String characterB) {
        String a = characterA.compareTo(characterB) < 0 ? characterA : characterB;
        String b = characterA.compareTo(characterB) < 0 ? characterB : characterA;
        
        relationshipStateMapper.deleteByNovelIdAndCharacters(novelId, a, b);
        logger.info("🗑️ deleteRelationshipState: {}—{}", characterA, characterB);
    }
    
    @Override
    @Transactional
    public void deleteCharacterState(Long novelId, String characterName) {
        GraphCharacterState existing = characterStateMapper.findByNovelIdAndCharacterName(novelId, characterName);
        if (existing != null) {
            characterStateMapper.deleteById(existing.getId());
        }
        logger.info("🗑️ deleteCharacterState: {}", characterName);
    }
    
    @Override
    @Transactional
    public void deleteOpenQuest(Long novelId, String questId) {
        openQuestMapper.deleteByNovelIdAndQuestId(novelId, questId);
        logger.info("🗑️ deleteOpenQuest: {}", questId);
    }
    
    // =============================
    // 核心记忆账本查询实现
    // =============================
    
    @Override
    public List<Map<String, Object>> getCharacterStates(Long novelId, Integer limit) {
        List<GraphCharacterState> states = characterStateMapper.findByNovelIdWithLimit(novelId, limit != null ? limit : 5);
        
        return states.stream().map(state -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", state.getCharacterName());
            map.put("location", state.getLocation() != null ? state.getLocation() : "");
            map.put("realm", state.getRealm() != null ? state.getRealm() : "");
            map.put("alive", state.getAlive() != null ? state.getAlive() : true);
            map.put("characterInfo", state.getCharacterInfo() != null ? state.getCharacterInfo() : "");
            map.put("lastChapter", state.getLastUpdatedChapter() != null ? state.getLastUpdatedChapter() : 0);
            map.put("inventory", parseJsonArray(state.getInventory()));
            return map;
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<Map<String, Object>> getTopRelationships(Long novelId, Integer limit) {
        List<GraphRelationshipState> relationships = relationshipStateMapper.findByNovelIdWithLimit(novelId, limit != null ? limit : 5);
        
        return relationships.stream().map(rel -> {
            Map<String, Object> map = new HashMap<>();
            map.put("a", rel.getCharacterA());
            map.put("b", rel.getCharacterB());
            map.put("type", rel.getType() != null ? rel.getType() : "");
            map.put("strength", rel.getStrength() != null ? rel.getStrength() : 0.5);
            map.put("lastChapter", rel.getLastUpdatedChapter() != null ? rel.getLastUpdatedChapter() : 0);
            return map;
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<Map<String, Object>> getOpenQuests(Long novelId, Integer currentChapter) {
        List<GraphOpenQuest> quests = openQuestMapper.findOpenQuests(novelId, currentChapter != null ? currentChapter : 999);
        
        return quests.stream().map(quest -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", quest.getQuestId());
            map.put("description", quest.getDescription() != null ? quest.getDescription() : "");
            map.put("status", quest.getStatus() != null ? quest.getStatus() : "OPEN");
            map.put("introduced", quest.getIntroducedChapter() != null ? quest.getIntroducedChapter() : 0);
            map.put("due", quest.getDueByChapter() != null ? quest.getDueByChapter() : 0);
            map.put("lastUpdated", quest.getLastUpdatedChapter() != null ? quest.getLastUpdatedChapter() : 0);
            return map;
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<GraphEntity> getCharacterProfiles(Long novelId, Integer limit) {
        return Collections.emptyList();
    }
    
    @Override
    public Map<String, Object> getGraphStatistics(Long novelId) {
        Map<String, Object> stats = new HashMap<>();
        // 简化实现
        stats.put("totalEntities", 0);
        return stats;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public String getServiceType() {
        return "MYSQL";
    }
    
    @Override
    @Transactional
    public void clearGraph(Long novelId) {
        logger.warn("⚠️ MySQL清空小说图谱: novelId={}", novelId);
        
        characterStateMapper.deleteByNovelId(novelId);
        relationshipStateMapper.deleteByNovelId(novelId);
        openQuestMapper.deleteByNovelId(novelId);
        eventMapper.deleteByNovelId(novelId);
        foreshadowingMapper.deleteByNovelId(novelId);
        plotlineMapper.deleteByNovelId(novelId);
        worldRuleMapper.deleteByNovelId(novelId);
        
        logger.info("✅ MySQL已清空小说{}的图谱", novelId);
    }
    
    @Override
    @Transactional
    public void deleteChapterEntities(Long novelId, Integer chapterNumber) {
        logger.info("🗑️ MySQL删除章节图谱数据: novelId={}, chapterNumber={}", novelId, chapterNumber);
        
        // 检查是否是历史章节
        Integer maxChapter = characterStateMapper.getMaxUpdatedChapter(novelId);
        if (maxChapter != null && chapterNumber < maxChapter) {
            logger.warn("⚠️ 检测到重写历史章节: 当前章节={}, 最新图谱章节={}", chapterNumber, maxChapter);
            logger.warn("⚠️ 跳过图谱数据清理以保持连贯性");
            return;
        }
        
        // 删除该章节的数据
        eventMapper.deleteByNovelIdAndChapter(novelId, chapterNumber);
        foreshadowingMapper.deleteByNovelIdAndChapter(novelId, chapterNumber);
        characterStateMapper.deleteByNovelIdAndChapter(novelId, chapterNumber);
        relationshipStateMapper.deleteByNovelIdAndChapter(novelId, chapterNumber);
        openQuestMapper.deleteByNovelIdAndChapter(novelId, chapterNumber);
        openQuestMapper.deleteByIntroducedChapter(novelId, chapterNumber);
        
        logger.info("✅ MySQL章节数据清理完成");
    }
    
    @Override
    public Map<String, Object> getAllGraphData(Long novelId) {
        logger.info("📊 MySQL查询小说核心记忆账本数据: novelId={}", novelId);
        
        Map<String, Object> result = new HashMap<>();
        
        // 角色状态
        List<Map<String, Object>> characterStates = getCharacterStates(novelId, 100);
        result.put("characterStates", characterStates);
        result.put("totalCharacterStates", characterStates.size());
        
        // 关系状态
        List<Map<String, Object>> relationshipStates = getTopRelationships(novelId, 100);
        result.put("relationshipStates", relationshipStates);
        result.put("totalRelationshipStates", relationshipStates.size());
        
        // 开放任务
        List<Map<String, Object>> openQuests = getOpenQuests(novelId, 999);
        result.put("openQuests", openQuests);
        result.put("totalOpenQuests", openQuests.size());
        
        // 事件
        List<GraphEvent> events = eventMapper.findByNovelId(novelId);
        List<Map<String, Object>> eventMaps = events.stream().map(event -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", event.getEventId());
            map.put("summary", event.getSummary());
            map.put("chapter", event.getChapterNumber());
            map.put("importance", event.getImportance());
            map.put("emotionalTone", event.getEmotionalTone());
            map.put("tags", parseJsonArray(event.getTags()));
            map.put("description", event.getDescription());
            map.put("location", event.getLocation());
            return map;
        }).collect(Collectors.toList());
        result.put("events", eventMaps);
        result.put("totalEvents", eventMaps.size());
        
        logger.info("✅ 核心记忆账本查询完成: {}个角色状态, {}个关系状态, {}个任务, {}个事件",
            characterStates.size(), relationshipStates.size(), openQuests.size(), eventMaps.size());
        
        return result;
    }
    
    // =============================
    // 辅助方法
    // =============================
    
    private double mapImportance(String importance, double defaultValue) {
        if (importance == null) return defaultValue;
        switch (importance.toLowerCase()) {
            case "high":
            case "critical":
                return 0.85;
            case "medium":
            case "mid":
                return 0.6;
            case "low":
            case "minor":
                return 0.35;
            default:
                return defaultValue;
        }
    }
    
    private String getStringProp(Map<String, Object> props, String key, String defaultValue) {
        if (props == null || !props.containsKey(key) || props.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(props.get(key));
    }
    
    private Double getDoubleProp(Map<String, Object> props, String key, Double defaultValue) {
        if (props == null || !props.containsKey(key) || props.get(key) == null) {
            return defaultValue;
        }
        Object val = props.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private Integer getIntProp(Map<String, Object> props, String key, Integer defaultValue) {
        if (props == null || !props.containsKey(key) || props.get(key) == null) {
            return defaultValue;
        }
        Object val = props.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private String toJsonArray(Object obj) {
        if (obj == null) return "[]";
        if (obj instanceof String) return (String) obj;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
    
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
