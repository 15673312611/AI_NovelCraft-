package com.novel.agentic.service.graph;

import com.novel.agentic.model.GraphEntity;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j 图数据库服务（真实实现）
 * 
 * 替换内存模拟版的GraphDatabaseService
 * 
 * 优先级最高：当Neo4j Driver可用时，自动使用此实现
 */
@Service
@ConditionalOnBean(Driver.class)
@org.springframework.context.annotation.Primary
public class Neo4jGraphService implements IGraphService {
    
    private static final Logger logger = LoggerFactory.getLogger(Neo4jGraphService.class);
    
    @Autowired
    private Driver driver;
    
    /**
     * 查询相关事件
     * 
     * 策略：基于因果链、参与者、关系距离综合排序
     */
    @Override
    public List<GraphEntity> getRelevantEvents(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 Neo4j查询相关事件: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);

        String cypher =
            "MATCH (c:Chapter {novelId: $novelId, number: $chapter})-[:CONTAINS_EVENT]->(eNow:Event) " +
            "OPTIONAL MATCH (eNow)-[:CAUSES|TRIGGERS|TRIGGERED_BY|RELATES_TO|PARTICIPATES_IN*1..3]-(eRel:Event)<-[:CONTAINS_EVENT]-(cRel:Chapter) " +
            "WHERE cRel.number < $chapter " +
            "WITH DISTINCT eRel, cRel, eNow, " +
            "     1.0 / ($chapter - cRel.number + 1) AS proximityScore, " +
            "     CASE " +
            "       WHEN eRel IS NOT NULL " +
            "       THEN COUNT { MATCH (eNow)-[:CAUSES|TRIGGERS|TRIGGERED_BY|RELATES_TO|PARTICIPATES_IN*1..3]-(eRel) } * 10.0 " +
            "       ELSE 0 " +
            "     END AS relationScore, " +
            "     coalesce(eRel.importance, 0.5) * 20 AS importanceScore " +
            "WHERE eRel IS NOT NULL " +
            "WITH eRel, cRel, (proximityScore + relationScore + importanceScore) AS totalScore " +
            "ORDER BY totalScore DESC " +
            "LIMIT $limit " +
            "OPTIONAL MATCH (eOtherIn:Event)-[causalIn:CAUSES]->(eRel) " +
            "OPTIONAL MATCH (eRel)-[causalOut:CAUSES]->(eOther:Event) " +
            "RETURN eRel, cRel.number AS chapterNumber, totalScore, " +
            "       collect(DISTINCT causalIn) AS inboundCausal, " +
            "       collect(DISTINCT {event: eOther.summary, type: type(causalOut)}) AS outboundCausal";

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("limit", limit);

            java.util.function.Function<Record, GraphEntity> mapper = record -> {
                Map<String, Object> eventMap = safeNodeToMap(record.get("eRel"));
                Integer chapter = record.get("chapterNumber").asInt();
                Double score = record.get("totalScore").asDouble(0.0);

                String description = safeGetString(eventMap, "summary",
                    safeGetString(eventMap, "description", ""));

                List<String> participants = new ArrayList<>();
                Object participantsObj = eventMap.get("participants");
                if (participantsObj instanceof List) {
                    ((List<?>) participantsObj).forEach(p -> participants.add(String.valueOf(p)));
                }

                String emotionalTone = safeGetString(eventMap, "emotionalTone", "neutral");

                List<String> tags = new ArrayList<>();
                Object tagsObj = eventMap.get("tags");
                if (tagsObj instanceof List) {
                    ((List<?>) tagsObj).forEach(t -> tags.add(String.valueOf(t)));
                }

                double importanceScore = 0.6;
                Object importance = eventMap.get("importance");
                if (importance != null) {
                    importanceScore = resolveImportance(importance, 0.6);
                }

                List<String> causalFrom = new ArrayList<>();
                Value inboundCausal = record.get("inboundCausal");
                if (inboundCausal != null && !inboundCausal.isNull() && inboundCausal.size() > 0) {
                    for (Value v : inboundCausal.values()) {
                        if (v != null && !v.isNull() && causalFrom.size() < 2) {
                            causalFrom.add(v.asMap().getOrDefault("description", "").toString());
                        }
                    }
                }

                List<String> causalTo = new ArrayList<>();
                Value outboundCausal = record.get("outboundCausal");
                if (outboundCausal != null && !outboundCausal.isNull() && outboundCausal.size() > 0) {
                    for (Value v : outboundCausal.values()) {
                        if (v != null && !v.isNull() && causalTo.size() < 2) {
                            Map<String, Object> map = v.asMap();
                            Object event = map.get("event");
                            if (event != null) {
                                causalTo.add(event.toString());
                            }
                        }
                    }
                }

                Map<String, Object> props = createPropertiesMap(
                    "description", description,
                    "participants", participants,
                    "emotionalTone", emotionalTone,
                    "tags", tags
                );
                props.put("importanceScore", importanceScore);
                if (!causalFrom.isEmpty()) {
                    props.put("causalFrom", String.join("; ", causalFrom));
                }
                if (!causalTo.isEmpty()) {
                    props.put("causalTo", String.join("; ", causalTo));
                }

                return GraphEntity.builder()
                    .type("Event")
                    .id(safeGetString(eventMap, "id", UUID.randomUUID().toString()))
                    .chapterNumber(chapter)
                    .relevanceScore(score)
                    .properties(props)
                    .source("第" + chapter + "章")
                    .build();
            };

            List<GraphEntity> list = session.run(cypher, params).list(mapper);
            if (list == null || list.isEmpty()) {
                logger.info("ℹ️ 未找到当前章事件锚点，使用回退查询最近历史事件");
                String fallback =
                    "MATCH (cRel:Chapter {novelId: $novelId})-[:CONTAINS_EVENT]->(eRel:Event) " +
                    "WHERE cRel.number < $chapter " +
                    "WITH eRel, cRel, 0.0 AS totalScore " +
                    "ORDER BY cRel.number DESC " +
                    "LIMIT $limit " +
                    "RETURN eRel, cRel.number AS chapterNumber, totalScore, [] AS inboundCausal, [] AS outboundCausal";
                list = session.run(fallback, params).list(mapper);
            }
            return list;
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 查询未回收伏笔
     */
    @Override
    public List<GraphEntity> getUnresolvedForeshadows(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 Neo4j查询未回收伏笔: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
        
        String cypher = 
            "MATCH (f:Foreshadowing {novelId: $novelId})-[:PLANTED_IN]->(c:Chapter) " +
            "WHERE c.number < $chapter " +
            "  AND f.status <> 'REVEALED' " +
            "  AND (f.plannedRevealChapter IS NULL OR f.plannedRevealChapter <= $chapter + 10) " +
            "WITH f, c.number AS plantedAt, " +
            "     CASE " +
            "       WHEN f.importance = 'high' THEN 3.0 " +
            "       WHEN f.importance = 'medium' THEN 2.0 " +
            "       ELSE 1.0 " +
            "     END AS importanceScore, " +
            "     ($chapter - c.number) AS age " +
            "ORDER BY importanceScore DESC, age DESC " +
            "LIMIT $limit " +
            "RETURN f, plantedAt";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("limit", limit);
            return session.run(cypher, params).list(record -> {
                Map<String, Object> fMap = safeNodeToMap(record.get("f"));
                Integer plantedAt = record.get("plantedAt").asInt();
                
                // 安全获取字符串值
                String content = safeGetString(fMap, "content", "");
                String importance = safeGetString(fMap, "importance", "medium");
                String status = safeGetString(fMap, "status", "PLANTED");
                double importanceScore = mapImportance(importance, 0.6);

                Map<String, Object> props = createPropertiesMap(
                    "description", content,
                    "plantedAt", "第" + plantedAt + "章",
                    "suggestedResolveWindow", "第" + (chapterNumber) + "-" + (chapterNumber + 10) + "章",
                    "importance", importance,
                    "status", status
                );
                props.put("importanceScore", importanceScore);

                return GraphEntity.builder()
                    .type("Foreshadow")
                    .id(safeGetString(fMap, "id", UUID.randomUUID().toString()))
                    .chapterNumber(plantedAt)
                    .relevanceScore(0.9)
                    .properties(props)
                    .source("第" + plantedAt + "章")
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 查询情节线状态
     */
    @Override
    public List<GraphEntity> getPlotlineStatus(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 Neo4j查询情节线状态: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
        
        String cypher = 
            "MATCH (p:PlotLine {novelId: $novelId})-[:INCLUDES]->(e:Event)<-[:CONTAINS_EVENT]-(c:Chapter) " +
            "WITH p, max(c.number) AS lastTouched, count(e) AS eventCount " +
            "WHERE $chapter - lastTouched > 5 OR eventCount < 3 " +
            "WITH p, lastTouched, eventCount, " +
            "     ($chapter - lastTouched) AS idleDuration, " +
            "     coalesce(p.priority, 0.5) AS priority " +
            "ORDER BY priority DESC, idleDuration DESC " +
            "LIMIT $limit " +
            "RETURN p, lastTouched, idleDuration, eventCount";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("limit", limit);
            return session.run(cypher, params).list(record -> {
                Map<String, Object> pMap = safeNodeToMap(record.get("p"));
                Integer lastTouched = record.get("lastTouched").asInt();
                Integer idleDuration = record.get("idleDuration").asInt();
                Integer eventCount = record.get("eventCount").asInt();
                
                String status = idleDuration > 10 ? "久未推进" : 
                               eventCount < 3 ? "待发展" : "进行中";
                
                // 安全获取priority
                double priority = safeGetDouble(pMap, "priority", 0.5);
                
                return GraphEntity.builder()
                    .type("Plotline")
                    .id(safeGetString(pMap, "id", UUID.randomUUID().toString()))
                    .chapterNumber(lastTouched)
                    .relevanceScore(1.0 - (idleDuration / 50.0))
                    .properties(createPropertiesMap(
                        "name", safeGetString(pMap, "name", "未命名情节线"),
                        "status", status,
                        "lastUpdate", "第" + lastTouched + "章",
                        "idleDuration", idleDuration,
                        "eventCount", eventCount,
                        "priority", priority
                    ))
                    .source("系统")
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 查询世界规则
     */
    @Override
    public List<GraphEntity> getWorldRules(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 Neo4j查询世界规则: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);
        
        String cypher = 
            "MATCH (r:WorldRule {novelId: $novelId}) " +
            "WHERE r.scope = 'global' OR r.applicableChapter IS NULL OR r.applicableChapter <= $chapter " +
            "WITH r, " +
            "     CASE " +
            "       WHEN r.category = 'power_system' THEN 10.0 " +
            "       WHEN r.category = 'world_setting' THEN 8.0 " +
            "       WHEN r.category = 'character_constraint' THEN 6.0 " +
            "       ELSE 5.0 " +
            "     END AS categoryScore " +
            "ORDER BY categoryScore DESC, r.importance DESC " +
            "LIMIT $limit " +
            "RETURN r";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("limit", limit);
            return session.run(cypher, params).list(record -> {
                Map<String, Object> rMap = new HashMap<>(record.get("r").asNode().asMap());
                
                // 安全获取值
                int introducedAt = rMap.containsKey("introducedAt") && rMap.get("introducedAt") != null
                    ? ((Number) rMap.get("introducedAt")).intValue() : 1;
                String id = rMap.containsKey("id") && rMap.get("id") != null 
                    ? String.valueOf(rMap.get("id")) : UUID.randomUUID().toString();
                String name = rMap.containsKey("name") && rMap.get("name") != null
                    ? String.valueOf(rMap.get("name")) : "规则";
                String content = rMap.containsKey("content") && rMap.get("content") != null
                    ? String.valueOf(rMap.get("content")) : "";
                String constraint = rMap.containsKey("constraint") && rMap.get("constraint") != null
                    ? String.valueOf(rMap.get("constraint")) : "";
                String category = rMap.containsKey("category") && rMap.get("category") != null
                    ? String.valueOf(rMap.get("category")) : "general";
                String scope = rMap.containsKey("scope") && rMap.get("scope") != null
                    ? String.valueOf(rMap.get("scope")) : "global";
                
                return GraphEntity.builder()
                    .type("WorldRule")
                    .id(id)
                    .chapterNumber(introducedAt)
                    .relevanceScore(1.0)
                    .properties(createPropertiesMap(
                        "name", name,
                        "description", content,
                        "constraint", constraint,
                        "category", category,
                        "scope", scope
                    ))
                    .source("设定")
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 查询角色关系网
     * 
     * 策略：查询指定角色的所有关系（对抗、合作、暧昧等）
     */
    @Override
    public List<GraphEntity> getCharacterRelationships(Long novelId, String characterName, Integer limit) {
        logger.info("🔍 Neo4j查询角色关系网: novelId={}, character={}", novelId, characterName);
        
        String cypher = 
            "MATCH (c1:Character {novelId: $novelId, name: $characterName})" +
            "-[r:RELATIONSHIP]-(c2:Character) " +
            "WITH c1, r, c2, " +
            "     CASE " +
            "       WHEN r.type = 'CONFLICT' THEN 3.0 " +
            "       WHEN r.type = 'COOPERATION' THEN 2.5 " +
            "       WHEN r.type = 'ROMANCE' THEN 2.0 " +
            "       ELSE 1.0 " +
            "     END AS relationScore " +
            "ORDER BY relationScore DESC, r.strength DESC " +
            "LIMIT $limit " +
            "RETURN c2.name AS targetName, r.type AS relationType, " +
            "       r.strength AS strength, r.description AS description";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("characterName", characterName);
            params.put("limit", limit);
            
            return session.run(cypher, params).list(record -> {
                String targetName = record.get("targetName").asString();
                String relationType = record.get("relationType").asString();
                double strength = record.get("strength").asDouble(0.5);
                String description = record.get("description").asString("");
                
                return GraphEntity.builder()
                    .type("CharacterRelationship")
                    .id(characterName + "_" + targetName + "_" + relationType)
                    .relevanceScore(strength)
                    .properties(createPropertiesMap(
                        "from", characterName,
                        "to", targetName,
                        "relationType", relationType,
                        "strength", strength,
                        "description", description
                    ))
                    .source("关系网")
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 按角色查询相关事件
     * 
     * 策略：查询角色参与的所有重要事件
     */
    @Override
    public List<GraphEntity> getEventsByCharacter(Long novelId, String characterName, Integer chapterNumber, Integer limit) {
        logger.info("🔍 Neo4j按角色查询事件: novelId={}, character={}, chapter={}", novelId, characterName, chapterNumber);
        
        String cypher = 
            "MATCH (c:Character {novelId: $novelId, name: $characterName})" +
            "-[:PARTICIPATES_IN]->(e:Event)<-[:CONTAINS_EVENT]-(ch:Chapter) " +
            "WHERE ch.number < $chapter " +
            "WITH e, ch.number AS chNum, " +
            "     coalesce(e.importance, 0.5) * 10 AS importanceScore, " +
            "     1.0 / ($chapter - ch.number + 1) AS proximityScore " +
            "ORDER BY (importanceScore + proximityScore) DESC " +
            "LIMIT $limit " +
            "RETURN e, chNum";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("characterName", characterName);
            params.put("chapter", chapterNumber);
            params.put("limit", limit);
            
            return session.run(cypher, params).list(record -> {
                Map<String, Object> eventMap = safeNodeToMap(record.get("e"));
                Integer chNum = record.get("chNum").asInt();
                
                String description = safeGetString(eventMap, "summary", "");
                
                return GraphEntity.builder()
                    .type("Event")
                    .id(safeGetString(eventMap, "id", UUID.randomUUID().toString()))
                    .chapterNumber(chNum)
                    .relevanceScore(0.8)
                    .properties(createPropertiesMap(
                        "description", description,
                        "character", characterName
                    ))
                    .source("第" + chNum + "章")
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 按因果链查询相关事件
     * 
     * 策略：从指定事件出发，沿因果链查询前因后果
     */
    @Override
    public List<GraphEntity> getEventsByCausality(Long novelId, String eventId, Integer depth) {
        logger.info("🔍 Neo4j按因果链查询: novelId={}, eventId={}, depth={}", novelId, eventId, depth);
        
        String cypher = 
            "MATCH (start:Event {novelId: $novelId, id: $eventId}) " +
            "MATCH path = (start)-[:CAUSES|TRIGGERED_BY*1.." + depth + "]-(related:Event) " +
            "WITH related, length(path) AS distance " +
            "ORDER BY distance ASC " +
            "RETURN DISTINCT related, distance";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("eventId", eventId);
            
            return session.run(cypher, params).list(record -> {
                Map<String, Object> eventMap = safeNodeToMap(record.get("related"));
                Integer distance = record.get("distance").asInt();
                
                String description = safeGetString(eventMap, "summary", "");
                Integer chNum = safeGetInt(eventMap, "chapterNumber", 0);
                
                return GraphEntity.builder()
                    .type("Event")
                    .id(safeGetString(eventMap, "id", UUID.randomUUID().toString()))
                    .chapterNumber(chNum)
                    .relevanceScore(1.0 / (distance + 1))
                    .properties(createPropertiesMap(
                        "description", description,
                        "causalDistance", distance
                    ))
                    .source("因果链")
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 查询冲突发展历史
     * 
     * 策略：查询主角与指定角色的所有对抗、冲突事件
     */
    @Override
    public List<GraphEntity> getConflictHistory(Long novelId, String protagonistName, String antagonistName, Integer limit) {
        logger.info("🔍 Neo4j查询冲突历史: novelId={}, protagonist={}, antagonist={}", 
                    novelId, protagonistName, antagonistName);
        
        String cypher = 
            "MATCH (p:Character {novelId: $novelId, name: $protagonist})" +
            "-[:PARTICIPATES_IN]->(e:Event)<-[:PARTICIPATES_IN]-(a:Character {name: $antagonist}) " +
            "WHERE e.emotionalTone IN ['conflict', 'tense', 'confrontation'] " +
            "   OR 'conflict' IN e.tags " +
            "MATCH (e)<-[:CONTAINS_EVENT]-(ch:Chapter) " +
            "WITH e, ch.number AS chNum " +
            "ORDER BY chNum ASC " +
            "LIMIT $limit " +
            "RETURN e, chNum";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("protagonist", protagonistName);
            params.put("antagonist", antagonistName);
            params.put("limit", limit);
            
            return session.run(cypher, params).list(record -> {
                Map<String, Object> eventMap = safeNodeToMap(record.get("e"));
                Integer chNum = record.get("chNum").asInt();
                
                String description = safeGetString(eventMap, "summary", "");
                
                return GraphEntity.builder()
                    .type("Event")
                    .id(safeGetString(eventMap, "id", UUID.randomUUID().toString()))
                    .chapterNumber(chNum)
                    .relevanceScore(1.0)
                    .properties(createPropertiesMap(
                        "description", description,
                        "conflictType", "protagonist_antagonist",
                        "participants", Arrays.asList(protagonistName, antagonistName)
                    ))
                    .source("第" + chNum + "章")
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询失败，返回空列表", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询叙事节奏状态
     */
    @Override
    public Map<String, Object> getNarrativeRhythmStatus(Long novelId, Integer chapterNumber, Integer window) {
        logger.info("🔍 Neo4j查询叙事节奏状态: novelId={}, chapter={}, window={}", novelId, chapterNumber, window);

        Map<String, Object> status = new HashMap<>();
        List<GraphEntity> beats = new ArrayList<>();
        String cypher =
            "MATCH (b:NarrativeBeat {novelId: $novelId}) " +
            "WHERE b.chapterNumber < $chapter " +
            "RETURN b ORDER BY b.chapterNumber DESC LIMIT $window";

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("window", window);

            List<Record> records = session.run(cypher, params).list();
            for (Record record : records) {
                Map<String, Object> beatMap = safeNodeToMap(record.get("b"));
                
                Integer beatChapter = safeGetInt(beatMap, "chapterNumber", null);
                String beatId = safeGetString(beatMap, "id", UUID.randomUUID().toString());
                String beatType = safeGetString(beatMap, "beatType", "UNKNOWN");
                String focus = safeGetString(beatMap, "focus", "UNSPECIFIED");
                String sentiment = safeGetString(beatMap, "sentiment", "neutral");
                Double tension = safeGetDouble(beatMap, "tension", 0.5);
                Double paceScore = safeGetDouble(beatMap, "paceScore", 0.5);
                String viewpoint = safeGetString(beatMap, "viewpoint", "unknown");

                beats.add(GraphEntity.builder()
                    .type("NarrativeBeat")
                    .id(beatId)
                    .chapterNumber(beatChapter)
                    .properties(createPropertiesMap(
                        "beatType", beatType,
                        "focus", focus,
                        "sentiment", sentiment,
                        "tension", tension,
                        "paceScore", paceScore,
                        "viewpoint", viewpoint
                    ))
                    .build());
            }
        } catch (Exception e) {
            logger.error("❌ Neo4j查询叙事节奏失败", e);
        }

        Collections.reverse(beats);

        Map<String, Object> metrics = new HashMap<>();
        Map<String, Long> typeCounts = beats.stream()
            .collect(Collectors.groupingBy(be -> normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN")), Collectors.counting()));
        int total = beats.size();
        Set<String> conflictTypes = new HashSet<>(Arrays.asList("CONFLICT", "CLIMAX", "冲突", "高潮"));
        Set<String> plotTypes = new HashSet<>(Arrays.asList("PLOT", "ADVANCEMENT", "PLOT_ADV", "推进", "主线"));
        Set<String> characterTypes = new HashSet<>(Arrays.asList("CHARACTER", "EMOTION", "人物", "感情"));
        Set<String> reliefTypes = new HashSet<>(Arrays.asList("RELIEF", "DAILY", "缓冲", "日常"));

        double conflictRatio = total == 0 ? 0.0 : (double) beats.stream()
            .filter(be -> conflictTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
            .count() / total;
        double plotRatio = total == 0 ? 0.0 : (double) beats.stream()
            .filter(be -> plotTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
            .count() / total;
        double characterRatio = total == 0 ? 0.0 : (double) beats.stream()
            .filter(be -> characterTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
            .count() / total;
        double reliefRatio = total == 0 ? 0.0 : (double) beats.stream()
            .filter(be -> reliefTypes.contains(normalizeBeatType((String) be.getProperties().getOrDefault("beatType", "UNKNOWN"))))
            .count() / total;

        metrics.put("conflictRatio", conflictRatio);
        metrics.put("plotRatio", plotRatio);
        metrics.put("characterRatio", characterRatio);
        metrics.put("reliefRatio", reliefRatio);
        metrics.put("beatCounts", typeCounts);

        int consecutiveConflict = 0;
        for (int i = beats.size() - 1; i >= 0; i--) {
            String type = normalizeBeatType((String) beats.get(i).getProperties().getOrDefault("beatType", "UNKNOWN"));
            if (conflictTypes.contains(type)) {
                consecutiveConflict++;
            } else {
                break;
            }
        }

        boolean conflictFatigue = consecutiveConflict >= 3;
        metrics.put("consecutiveConflict", consecutiveConflict);
        metrics.put("conflictFatigue", conflictFatigue);

        List<String> recommendations = new ArrayList<>();
        if (beats.isEmpty()) {
            recommendations.add("尚无节奏记录，参考卷蓝图规划章节节奏。");
        }
        if (conflictFatigue) {
            recommendations.add("连续高强度冲突，建议本章转为人物刻画或日常缓冲，给读者呼吸空间。");
        }
        if (plotRatio < 0.3) {
            recommendations.add("近期主线推进不足，结合卷蓝图推进关键事件。");
        }
        if (characterRatio < 0.2) {
            recommendations.add("人物内心/关系描写偏少，考虑安排角色视角或情绪戏。");
        }
        if (reliefRatio == 0 && conflictRatio > 0.5) {
            recommendations.add("缺乏缓冲章节，可加入轻松段落或日常场景。");
        }

        status.put("recentBeats", beats);
        status.put("metrics", metrics);
        status.put("recommendations", recommendations);

        return status;
    }

    /**
     * 查询活跃冲突弧线
     */
    @Override
    public List<GraphEntity> getActiveConflictArcs(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 Neo4j查询活跃冲突弧线: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);

        String cypher =
            "MATCH (arc:ConflictArc {novelId: $novelId}) " +
            "WHERE arc.stage IS NULL OR arc.stage <> '解决' " +
            "OPTIONAL MATCH (arc)-[:LAST_UPDATED_IN]->(c:Chapter) " +
            "WITH arc, coalesce(c.number, arc.lastUpdatedChapter, 0) AS lastChapter " +
            "RETURN arc, lastChapter " +
            "ORDER BY coalesce(arc.urgency, 0.5) DESC, lastChapter ASC " +
            "LIMIT $limit";

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("limit", limit);

            return session.run(cypher, params).list(record -> {
                Map<String, Object> arcMap = safeNodeToMap(record.get("arc"));
                Integer lastChapter = record.get("lastChapter").isNull() ? null : record.get("lastChapter").asInt();
                String arcId = safeGetString(arcMap, "id", UUID.randomUUID().toString());
                String name = safeGetString(arcMap, "name", arcId);
                String stage = safeGetString(arcMap, "stage", "UNDEFINED");
                Double urgency = safeGetDouble(arcMap, "urgency", 0.5);
                String nextAction = safeGetString(arcMap, "nextAction", "");
                String protagonist = safeGetString(arcMap, "protagonist", "未知");
                String antagonist = safeGetString(arcMap, "antagonist", "未知");
                String trend = safeGetString(arcMap, "trend", "STABLE");

                return GraphEntity.builder()
                    .type("ConflictArc")
                    .id(arcId)
                    .chapterNumber(lastChapter)
                    .relevanceScore(urgency)
                    .properties(createPropertiesMap(
                        "name", name,
                        "stage", stage,
                        "urgency", urgency,
                        "nextAction", nextAction,
                        "protagonist", protagonist,
                        "antagonist", antagonist,
                        "trend", trend
                    ))
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询冲突弧线失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询人物成长状态
     */
    @Override
    public List<GraphEntity> getCharacterArcStatus(Long novelId, Integer chapterNumber, Integer limit) {
        logger.info("🔍 Neo4j查询人物成长状态: novelId={}, chapter={}, limit={}", novelId, chapterNumber, limit);

        String cypher =
            "MATCH (arc:CharacterArc {novelId: $novelId}) " +
            "WHERE arc.progress IS NULL OR arc.totalBeats IS NULL OR arc.progress < arc.totalBeats " +
            "OPTIONAL MATCH (arc)-[:LAST_PROGRESS_IN]->(c:Chapter) " +
            "WITH arc, coalesce(c.number, arc.lastUpdatedChapter, 0) AS lastChapter " +
            "RETURN arc, lastChapter " +
            "ORDER BY coalesce(arc.priority, 0.5) DESC, lastChapter ASC " +
            "LIMIT $limit";

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("limit", limit);

            return session.run(cypher, params).list(record -> {
                Map<String, Object> arcMap = safeNodeToMap(record.get("arc"));
                Integer lastChapter = record.get("lastChapter").isNull() ? null : record.get("lastChapter").asInt();
                String arcId = safeGetString(arcMap, "id", UUID.randomUUID().toString());
                String characterName = safeGetString(arcMap, "characterName", "未知");
                String arcName = safeGetString(arcMap, "arcName", arcId);
                String pendingBeat = safeGetString(arcMap, "pendingBeat", "");
                String nextGoal = safeGetString(arcMap, "nextGoal", "");
                Double priority = safeGetDouble(arcMap, "priority", 0.5);
                Integer progress = safeGetInt(arcMap, "progress", 0);
                Integer totalBeats = safeGetInt(arcMap, "totalBeats", 0);

                return GraphEntity.builder()
                    .type("CharacterArc")
                    .id(arcId)
                    .chapterNumber(lastChapter)
                    .relevanceScore(priority)
                    .properties(createPropertiesMap(
                        "characterName", characterName,
                        "arcName", arcName,
                        "pendingBeat", pendingBeat,
                        "nextGoal", nextGoal,
                        "priority", priority,
                        "progress", progress,
                        "totalBeats", totalBeats
                    ))
                    .build();
            });
        } catch (Exception e) {
            logger.error("❌ Neo4j查询人物成长失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询视角历史
     */
    @Override
    public List<GraphEntity> getPerspectiveHistory(Long novelId, Integer chapterNumber, Integer window) {
        logger.info("🔍 Neo4j查询视角历史: novelId={}, chapter={}, window={}", novelId, chapterNumber, window);

        List<GraphEntity> results = new ArrayList<>();
        String cypher =
            "MATCH (p:PerspectiveUsage {novelId: $novelId}) " +
            "WHERE p.chapterNumber < $chapter " +
            "RETURN p ORDER BY p.chapterNumber DESC LIMIT $window";

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapter", chapterNumber);
            params.put("window", window);

            List<Record> records = session.run(cypher, params).list();
            for (Record record : records) {
                Map<String, Object> nodeMap = safeNodeToMap(record.get("p"));
                
                Integer ch = safeGetInt(nodeMap, "chapterNumber", null);
                String id = safeGetString(nodeMap, "id", UUID.randomUUID().toString());
                String character = safeGetString(nodeMap, "characterName", "未知");
                String mode = safeGetString(nodeMap, "mode", "第三人称");
                String tone = safeGetString(nodeMap, "tone", "neutral");
                String purpose = safeGetString(nodeMap, "purpose", "");

                results.add(GraphEntity.builder()
                    .type("PerspectiveUsage")
                    .id(id)
                    .chapterNumber(ch)
                    .properties(createPropertiesMap(
                        "characterName", character,
                        "mode", mode,
                        "tone", tone,
                        "purpose", purpose
                    ))
                    .build());
            }
        } catch (Exception e) {
            logger.error("❌ Neo4j查询视角历史失败", e);
        }

        Collections.reverse(results);

        if (!results.isEmpty()) {
            String lastCharacter = (String) results.get(results.size() - 1).getProperties().get("characterName");
            boolean allSame = results.stream().allMatch(r -> Objects.equals(r.getProperties().get("characterName"), lastCharacter));
            if (allSame && results.size() >= 3) {
                results.add(0, GraphEntity.builder()
                    .type("PerspectiveRecommendation")
                    .id("perspective_summary")
                    .properties(createPropertiesMap(
                        "recommendation", "连续多章使用" + lastCharacter + "视角，考虑切换其他角色以带来新信息或情绪。"
                    ))
                    .build());
            }
        }

        return results;
    }
    
    /**
     * 添加实体到图谱
     */
    @Override
    public void addEntity(Long novelId, GraphEntity entity) {
        logger.info("➕ Neo4j添加实体: type={}, id={}", entity.getType(), entity.getId());

        String cypher = buildInsertCypher(entity);

        try (Session session = driver.session()) {
            Map<String, Object> baseParams = buildInsertParams(novelId, entity);
            session.run(cypher, baseParams);
            logger.info("✅ 实体已入图: {}", entity.getId());

            if ("Event".equals(entity.getType())) {
                // 建立 Chapter→Event 与 Character→Event 关系
                try {
                    Map<String, Object> linkParams = new HashMap<>();
                    linkParams.put("novelId", novelId);
                    linkParams.put("chapterNumber", entity.getChapterNumber());
                    linkParams.put("id", entity.getId());

                    // Chapter -> Event
                    String rel1 = "MERGE (e:Event {id: $id}) " +
                                   "MERGE (c:Chapter {novelId: $novelId, number: $chapterNumber}) " +
                                   "MERGE (c)-[:CONTAINS_EVENT]->(e)";
                    session.run(rel1, linkParams);

                    // Character -> Event
                    List<String> participants = new ArrayList<>();
                    Map<String, Object> props = entity.getProperties();
                    if (props != null) {
                        Object participantsObj = props.get("participants");
                        if (participantsObj instanceof List) {
                            for (Object p : (List<?>) participantsObj) {
                                if (p != null) participants.add(p.toString());
                            }
                        } else if (participantsObj instanceof String) {
                            String[] parts = participantsObj.toString().split("[,，、]");
                            for (String part : parts) {
                                String t = part.trim();
                                if (!t.isEmpty()) participants.add(t);
                            }
                        }
                    }
                    if (!participants.isEmpty()) {
                        Map<String, Object> p = new HashMap<>();
                        p.put("novelId", novelId);
                        p.put("id", entity.getId());
                        p.put("participants", participants);
                        String rel2 = "UNWIND $participants AS name " +
                                      "MERGE (e:Event {id: $id}) " +
                                      "MERGE (ch:Character {novelId: $novelId, name: name}) " +
                                      "MERGE (ch)-[:PARTICIPATES_IN]->(e)";
                        session.run(rel2, p);
                    }
                } catch (Exception ex) {
                    logger.warn("建立事件关系失败（忽略，不阻断）: {}", ex.getMessage());
                }

                // 从事件属性更新参与者的角色状态（位置/境界）
                try {
                    updateCharacterStatesFromEvent(novelId, entity);
                } catch (Exception e) {
                    logger.warn("更新角色状态失败（忽略，不阻断）: {}", e.getMessage());
                }
            } else if ("Foreshadow".equals(entity.getType())) {
                // 建立 Foreshadow → Chapter 关系，便于检索未回收伏笔
                try {
                    Map<String, Object> paramsF = new HashMap<>();
                    paramsF.put("novelId", novelId);
                    paramsF.put("chapterNumber", entity.getChapterNumber());
                    paramsF.put("id", entity.getId());
                    String relF = "MERGE (f:Foreshadowing {id: $id}) " +
                                  "MERGE (c:Chapter {novelId: $novelId, number: $chapterNumber}) " +
                                  "MERGE (f)-[:PLANTED_IN]->(c)";
                    session.run(relF, paramsF);
                } catch (Exception ex) {
                    logger.warn("建立伏笔关系失败（忽略，不阻断）: {}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("❌ Neo4j写入失败", e);
        }
    }
    
    /**
     * 批量添加实体
     */
    @Override
    public void addEntities(Long novelId, List<GraphEntity> entities) {
        logger.info("➕ Neo4j批量添加实体: count={}", entities.size());
        entities.forEach(entity -> addEntity(novelId, entity));
    }
    
    /**
     * 添加关系到图谱
     */
    @Override
    public void addRelationship(Long novelId, String fromEntityId, String relationshipType,
                                String toEntityId, Map<String, Object> properties) {
        String relType = sanitizeRelationshipType(relationshipType);
        logger.info("➕ Neo4j添加关系: {} -[{}]-> {}", fromEntityId, relType, toEntityId);

        String cypher;
        if ("RELATIONSHIP".equals(relType)) {
            // 角色之间的关系：按name匹配角色节点，必要时创建
            cypher =
                "MERGE (from:Character {novelId: $novelId, name: $fromId}) " +
                "MERGE (to:Character {novelId: $novelId, name: $toId}) " +
                "MERGE (from)-[r:RELATIONSHIP]->(to) " +
                "SET r += $properties, r.updatedAt = datetime()";
        } else {
            // 默认：按id匹配实体节点
            cypher =
                "MATCH (from {id: $fromId, novelId: $novelId}) " +
                "MATCH (to {id: $toId, novelId: $novelId}) " +
                "MERGE (from)-[r:" + relType + "]->(to) " +
                "SET r += $properties, r.updatedAt = datetime()";
        }

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("fromId", fromEntityId);
            params.put("toId", toEntityId);
            params.put("properties", properties != null ? properties : new HashMap<>());

            session.run(cypher, params);
            logger.info("✅ 关系已添加");
        } catch (Exception e) {
            logger.error("❌ Neo4j添加关系失败", e);
        }
    }

    // 关系类型白名单化，避免非法字符和注入
    private String sanitizeRelationshipType(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "RELATED_TO";
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        // 仅保留字母、数字和下划线
        upper = upper.replaceAll("[^A-Z0-9_]", "");
        if (upper.isEmpty()) return "RELATED_TO";
        // 将常见同义词归一化
        if ("TRIGGERS".equals(upper)) return "TRIGGERS";
        if ("TRIGGERED_BY".equals(upper)) return "TRIGGERED_BY";
        if ("CAUSES".equals(upper)) return "CAUSES";
        if ("RELATES_TO".equals(upper)) return "RELATES_TO";
        if ("RELATIONSHIP".equals(upper)) return "RELATIONSHIP";
        if ("PARTICIPATES_IN".equals(upper)) return "PARTICIPATES_IN";
        return upper;
    }

    // =============================
    // 🆕 核心记忆账本写入实现
    // =============================

    @Override
    public void upsertCharacterState(Long novelId, String characterName, String location, String realm, Boolean alive, Integer chapterNumber) {
        try (Session session = driver.session()) {
            // 🆕 步骤1：保存当前状态到历史快照（如果存在）
            String saveHistoryQuery =
                "MATCH (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                "WHERE s.lastUpdatedChapter IS NOT NULL " +
                "CREATE (h:CharacterStateHistory {" +
                "  novelId: $novelId, " +
                "  characterName: $characterName, " +
                "  location: s.location, " +
                "  realm: s.realm, " +
                "  alive: s.alive, " +
                "  inventory: s.inventory, " +
                "  characterInfo: s.characterInfo, " +
                "  chapterNumber: s.lastUpdatedChapter, " +
                "  createdAt: datetime()" +
                "})";
            
            session.run(saveHistoryQuery, Map.of("novelId", novelId, "characterName", characterName));
            
            // 步骤2：更新当前状态
            String cypher =
                "MERGE (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                "SET s.location = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($location, s.location) ELSE s.location END, " +
                "    s.realm = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($realm, s.realm) ELSE s.realm END, " +
                "    s.alive = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($alive, s.alive) ELSE s.alive END, " +
                "    s.lastUpdatedChapter = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN $chapterNumber ELSE s.lastUpdatedChapter END, " +
                "    s.updatedAt = datetime() " +
                "RETURN s";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("characterName", characterName);
            params.put("location", location);
            params.put("realm", realm);
            params.put("alive", alive);
            params.put("chapterNumber", chapterNumber);

            logger.info("🔍 执行upsertCharacterState: novelId={}, name={}, chapter={}", novelId, characterName, chapterNumber);
            Result result = session.run(cypher, params);

            // 验证是否真的保存了
            if (result.hasNext()) {
                Record record = result.next();
                logger.info("✅ CharacterState已保存: {}", record.get("s").asMap());
            } else {
                logger.warn("⚠️ CharacterState保存后无返回结果");
            }

            logger.info("🧭 upsertCharacterState: {}@{} loc={}, realm={} alive={}", characterName, chapterNumber, location, realm, alive);
        } catch (Exception e) {
            logger.error("❌ upsertCharacterState失败", e);
        }
    }

    /**
     * 🆕 更新角色状态（包含人物信息字段）
     * 用于保存关键数值相关的一句话总结
     */
    @Override
    public void upsertCharacterStateWithInfo(Long novelId, String characterName, String location, String realm, Boolean alive, String characterInfo, Integer chapterNumber) {
        try (Session session = driver.session()) {
            // 步骤1：保存历史快照
            String saveHistoryQuery =
                "MATCH (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                "WHERE s.lastUpdatedChapter IS NOT NULL " +
                "CREATE (h:CharacterStateHistory {" +
                "  novelId: $novelId, " +
                "  characterName: $characterName, " +
                "  location: s.location, " +
                "  realm: s.realm, " +
                "  alive: s.alive, " +
                "  inventory: s.inventory, " +
                "  characterInfo: s.characterInfo, " +
                "  chapterNumber: s.lastUpdatedChapter, " +
                "  createdAt: datetime()" +
                "})";
            
            session.run(saveHistoryQuery, Map.of("novelId", novelId, "characterName", characterName));
            
            // 步骤2：更新当前状态（包含characterInfo）
            String cypher =
                "MERGE (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                "SET s.location = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($location, s.location) ELSE s.location END, " +
                "    s.realm = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($realm, s.realm) ELSE s.realm END, " +
                "    s.alive = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($alive, s.alive) ELSE s.alive END, " +
                "    s.characterInfo = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) AND $characterInfo <> '' THEN $characterInfo WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN s.characterInfo ELSE s.characterInfo END, " +
                "    s.lastUpdatedChapter = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN $chapterNumber ELSE s.lastUpdatedChapter END, " +
                "    s.updatedAt = datetime() " +
                "RETURN s";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("characterName", characterName);
            params.put("location", location);
            params.put("realm", realm);
            params.put("alive", alive);
            params.put("characterInfo", characterInfo != null ? characterInfo : "");
            params.put("chapterNumber", chapterNumber);

            logger.info("🔍 执行upsertCharacterStateWithInfo: novelId={}, name={}, chapter={}, characterInfo={}", novelId, characterName, chapterNumber, characterInfo);
            Result result = session.run(cypher, params);

            if (result.hasNext()) {
                Record record = result.next();
                logger.info("✅ CharacterState(含人物信息)已保存: {}", record.get("s").asMap());
            } else {
                logger.warn("⚠️ CharacterState保存后无返回结果");
            }

            logger.info("🧭 upsertCharacterStateWithInfo: {}@{} loc={}, realm={}, alive={}, info={}", characterName, chapterNumber, location, realm, alive, characterInfo);
        } catch (Exception e) {
            logger.error("❌ upsertCharacterStateWithInfo失败", e);
        }
    }

    /**
     * 🆕 完整更新角色状态（包含扩展字段）
     * 全题材通用设计
     */
    @Override
    public void upsertCharacterStateComplete(Long novelId, String characterName, Map<String, Object> stateData, Integer chapterNumber) {
        try (Session session = driver.session()) {
            // 步骤1：保存历史快照
            String saveHistoryQuery =
                "MATCH (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                "WHERE s.lastUpdatedChapter IS NOT NULL " +
                "CREATE (h:CharacterStateHistory {" +
                "  novelId: $novelId, " +
                "  characterName: $characterName, " +
                "  location: s.location, " +
                "  realm: s.realm, " +
                "  alive: s.alive, " +
                "  affiliation: s.affiliation, " +
                "  socialStatus: s.socialStatus, " +
                "  backers: s.backers, " +
                "  tags: s.tags, " +
                "  secrets: s.secrets, " +
                "  keyItems: s.keyItems, " +
                "  knownBy: s.knownBy, " +
                "  characterInfo: s.characterInfo, " +
                "  chapterNumber: s.lastUpdatedChapter, " +
                "  createdAt: datetime()" +
                "})";
            
            session.run(saveHistoryQuery, Map.of("novelId", novelId, "characterName", characterName));
            
            // 步骤2：更新当前状态
            String cypher =
                "MERGE (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                "SET s.location = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($location, s.location) ELSE s.location END, " +
                "    s.realm = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($realm, s.realm) ELSE s.realm END, " +
                "    s.alive = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($alive, s.alive, true) ELSE s.alive END, " +
                "    s.affiliation = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($affiliation, s.affiliation) ELSE s.affiliation END, " +
                "    s.socialStatus = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($socialStatus, s.socialStatus) ELSE s.socialStatus END, " +
                "    s.backers = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($backers, s.backers, []) ELSE coalesce(s.backers, []) END, " +
                "    s.tags = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($tags, s.tags, []) ELSE coalesce(s.tags, []) END, " +
                "    s.secrets = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($secrets, s.secrets, []) ELSE coalesce(s.secrets, []) END, " +
                "    s.keyItems = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($keyItems, s.keyItems, []) ELSE coalesce(s.keyItems, []) END, " +
                "    s.knownBy = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce($knownBy, s.knownBy, []) ELSE coalesce(s.knownBy, []) END, " +
                "    s.lastUpdatedChapter = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN $chapterNumber ELSE s.lastUpdatedChapter END, " +
                "    s.updatedAt = datetime()";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("characterName", characterName);
            params.put("location", stateData.get("location"));
            params.put("realm", stateData.get("realm"));
            params.put("alive", stateData.get("alive"));
            params.put("affiliation", stateData.get("affiliation"));
            params.put("socialStatus", stateData.get("socialStatus"));
            params.put("backers", stateData.get("backers"));
            params.put("tags", stateData.get("tags"));
            params.put("secrets", stateData.get("secrets"));
            params.put("keyItems", stateData.get("keyItems"));
            params.put("knownBy", stateData.get("knownBy"));
            params.put("chapterNumber", chapterNumber);

            session.run(cypher, params);
            logger.info("✅ 完整角色状态已更新: {} @chapter{}", characterName, chapterNumber);
        } catch (Exception e) {
            logger.error("❌ upsertCharacterStateComplete失败", e);
        }
    }

    @Override
    public void updateCharacterInventory(Long novelId, String characterName, List<String> items, Integer chapterNumber) {
        String cypher =
            "MERGE (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
            "SET s.inventory = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN $items ELSE coalesce(s.inventory, []) END, " +
            "    s.lastUpdatedChapter = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN $chapterNumber ELSE s.lastUpdatedChapter END, " +
            "    s.updatedAt = datetime()";

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("characterName", characterName);
            params.put("items", items);
            params.put("chapterNumber", chapterNumber);
            session.run(cypher, params);
            logger.info("💼 updateInventory: {} 持有{}件物品", characterName, items.size());
        } catch (Exception e) {
            logger.error("updateCharacterInventory失败", e);
        }
    }

    @Override
    public void deleteCharacterState(Long novelId, String characterName) {
        logger.info("🗑️ 删除角色状态: novelId={}, name={}", novelId, characterName);
        try (Session session = driver.session()) {
            String cypher =
                "MATCH (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                "DETACH DELETE s " +
                "WITH $novelId AS novelId, $characterName AS characterName " +
                "OPTIONAL MATCH (h:CharacterStateHistory {novelId: novelId, characterName: characterName}) " +
                "DETACH DELETE h";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("characterName", characterName);

            session.run(cypher, params);
            logger.info("✅ 角色状态已删除: {}", characterName);
        } catch (Exception e) {
            logger.error("deleteCharacterState失败", e);
            throw new RuntimeException("删除角色状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void upsertRelationshipState(Long novelId, String characterA, String characterB, String type, Double strength, Integer chapterNumber) {
        try (Session session = driver.session()) {
            // 🆕 步骤1：保存当前状态到历史快照（如果存在）
            String saveHistoryQuery =
                "WITH CASE WHEN $a < $b THEN $a ELSE $b END AS a, CASE WHEN $a < $b THEN $b ELSE $a END AS b " +
                "MATCH (r:RelationshipState {novelId: $novelId, a: a, b: b}) " +
                "WHERE r.lastUpdatedChapter IS NOT NULL " +
                "CREATE (h:RelationshipStateHistory {" +
                "  novelId: $novelId, " +
                "  a: a, " +
                "  b: b, " +
                "  type: r.type, " +
                "  strength: r.strength, " +
                "  chapterNumber: r.lastUpdatedChapter, " +
                "  createdAt: datetime()" +
                "})"; 
            
            Map<String, Object> historyParams = new HashMap<>();
            historyParams.put("novelId", novelId);
            historyParams.put("a", characterA);
            historyParams.put("b", characterB);
            session.run(saveHistoryQuery, historyParams);
            
            // 步骤2：更新当前状态
            String cypher =
                "WITH CASE WHEN $a < $b THEN $a ELSE $b END AS a, CASE WHEN $a < $b THEN $b ELSE $a END AS b " +
                "MERGE (r:RelationshipState {novelId: $novelId, a: a, b: b}) " +
                "SET r.type = coalesce($type, r.type), " +
                "    r.strength = coalesce($strength, r.strength, 0.5), " +
                "    r.lastUpdatedChapter = CASE WHEN $chapterNumber >= coalesce(r.lastUpdatedChapter,-1) THEN $chapterNumber ELSE r.lastUpdatedChapter END, " +
                "    r.updatedAt = datetime()";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("a", characterA);
            params.put("b", characterB);
            params.put("type", type);
            params.put("strength", strength);
            params.put("chapterNumber", chapterNumber);
            session.run(cypher, params);
            logger.info("🤝 upsertRelationshipState: {}—{} type={} strength={}", characterA, characterB, type, strength);
        } catch (Exception e) {
            logger.error("upsertRelationshipState失败", e);
        }
    }

    /**
     * 🆕 完整更新关系状态（包含扩展字段）
     * 全题材通用设计
     */
    @Override
    public void upsertRelationshipStateComplete(Long novelId, String characterA, String characterB, Map<String, Object> relationData, Integer chapterNumber) {
        try (Session session = driver.session()) {
            // 步骤1：保存历史快照
            String saveHistoryQuery =
                "WITH CASE WHEN $a < $b THEN $a ELSE $b END AS a, CASE WHEN $a < $b THEN $b ELSE $a END AS b " +
                "MATCH (r:RelationshipState {novelId: $novelId, a: a, b: b}) " +
                "WHERE r.lastUpdatedChapter IS NOT NULL " +
                "CREATE (h:RelationshipStateHistory {" +
                "  novelId: $novelId, " +
                "  a: a, " +
                "  b: b, " +
                "  type: r.type, " +
                "  strength: r.strength, " +
                "  description: r.description, " +
                "  publicStatus: r.publicStatus, " +
                "  chapterNumber: r.lastUpdatedChapter, " +
                "  createdAt: datetime()" +
                "})";
            
            Map<String, Object> historyParams = new HashMap<>();
            historyParams.put("novelId", novelId);
            historyParams.put("a", characterA);
            historyParams.put("b", characterB);
            session.run(saveHistoryQuery, historyParams);
            
            // 步骤2：更新当前状态
            String cypher =
                "WITH CASE WHEN $a < $b THEN $a ELSE $b END AS a, CASE WHEN $a < $b THEN $b ELSE $a END AS b " +
                "MERGE (r:RelationshipState {novelId: $novelId, a: a, b: b}) " +
                "SET r.type = CASE WHEN $chapterNumber >= coalesce(r.lastUpdatedChapter,-1) THEN coalesce($type, r.type) ELSE r.type END, " +
                "    r.strength = CASE WHEN $chapterNumber >= coalesce(r.lastUpdatedChapter,-1) THEN coalesce($strength, r.strength, 0.5) ELSE r.strength END, " +
                "    r.description = CASE WHEN $chapterNumber >= coalesce(r.lastUpdatedChapter,-1) THEN coalesce($description, r.description) ELSE r.description END, " +
                "    r.publicStatus = CASE WHEN $chapterNumber >= coalesce(r.lastUpdatedChapter,-1) THEN coalesce($publicStatus, r.publicStatus) ELSE r.publicStatus END, " +
                "    r.lastUpdatedChapter = CASE WHEN $chapterNumber >= coalesce(r.lastUpdatedChapter,-1) THEN $chapterNumber ELSE r.lastUpdatedChapter END, " +
                "    r.updatedAt = datetime()";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("a", characterA);
            params.put("b", characterB);
            params.put("type", relationData.get("type"));
            params.put("strength", relationData.get("strength"));
            params.put("description", relationData.get("description"));
            params.put("publicStatus", relationData.get("publicStatus"));
            params.put("chapterNumber", chapterNumber);
            
            session.run(cypher, params);
            logger.info("✅ 完整关系状态已更新: {}—{} @chapter{}", characterA, characterB, chapterNumber);
        } catch (Exception e) {
            logger.error("❌ upsertRelationshipStateComplete失败", e);
        }
    }

    @Override
    public void deleteRelationshipState(Long novelId, String characterA, String characterB) {
        logger.info("🗑️ 删除关系状态: novelId={}, a={}, b={}", novelId, characterA, characterB);
        try (Session session = driver.session()) {
            String cypher =
                "WITH CASE WHEN $a < $b THEN $a ELSE $b END AS a, CASE WHEN $a < $b THEN $b ELSE $a END AS b " +
                "OPTIONAL MATCH (r:RelationshipState {novelId: $novelId, a: a, b: b}) " +
                "DETACH DELETE r " +
                "WITH a, b " +
                "OPTIONAL MATCH (h:RelationshipStateHistory {novelId: $novelId, a: a, b: b}) " +
                "DETACH DELETE h";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("a", characterA);
            params.put("b", characterB);

            session.run(cypher, params);
            logger.info("✅ 关系状态已删除: {}—{}", characterA, characterB);
        } catch (Exception e) {
            logger.error("deleteRelationshipState失败", e);
            throw new RuntimeException("删除关系状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void upsertOpenQuest(Long novelId, String questId, String description, String status, Integer introducedChapter, Integer dueByChapter, Integer lastUpdatedChapter) {
        logger.info("📌 upsertOpenQuest: {} status={}", questId, status);
        try (Session session = driver.session()) {
            // 🆕 步骤1：保存当前状态到历史快照（如果存在）
            String saveHistoryQuery =
                "MATCH (q:OpenQuest {novelId: $novelId, id: $questId}) " +
                "WHERE q.lastUpdatedChapter IS NOT NULL " +
                "CREATE (h:OpenQuestHistory {" +
                "  novelId: $novelId, " +
                "  questId: $questId, " +
                "  description: q.description, " +
                "  status: q.status, " +
                "  introducedChapter: q.introducedChapter, " +
                "  dueByChapter: q.dueByChapter, " +
                "  chapterNumber: q.lastUpdatedChapter, " +
                "  createdAt: datetime()" +
                "})"; 
            
            session.run(saveHistoryQuery, Map.of("novelId", novelId, "questId", questId));
            
            // 步骤2：更新当前状态
            String cypher =
                "MERGE (q:OpenQuest {novelId: $novelId, id: $id}) " +
                "SET q.description = coalesce($description, q.description), " +
                "    q.status = coalesce($status, q.status, 'OPEN'), " +
                "    q.introducedChapter = coalesce(q.introducedChapter, $introducedChapter), " +
                "    q.dueByChapter = coalesce($dueByChapter, q.dueByChapter), " +
                "    q.lastUpdatedChapter = CASE WHEN $lastUpdatedChapter >= coalesce(q.lastUpdatedChapter,-1) THEN $lastUpdatedChapter ELSE q.lastUpdatedChapter END, " +
                "    q.updatedAt = datetime() " +
                "RETURN q";
            
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("id", questId);
            params.put("description", description);
            params.put("status", status);
            params.put("introducedChapter", introducedChapter);
            params.put("dueByChapter", dueByChapter);
            params.put("lastUpdatedChapter", lastUpdatedChapter);

            Result result = session.run(cypher, params);

            // 🔍 验证保存结果
            if (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> savedQuest = safeNodeToMap(record.get("q"));
                logger.info("✅ OpenQuest已保存到Neo4j: id={}, description={}, due={}",
                    safeGetString(savedQuest, "id", ""),
                    safeGetString(savedQuest, "description", ""),
                    safeGetInt(savedQuest, "dueByChapter", 0));
            } else {
                logger.warn("⚠️ OpenQuest保存后无返回结果");
            }
        } catch (Exception e) {
            logger.error("❌ upsertOpenQuest失败", e);
        }
    }

    @Override
    public void resolveOpenQuest(Long novelId, String questId, Integer resolvedChapter) {
        String cypher =
            "MATCH (q:OpenQuest {novelId: $novelId, id: $id}) " +
            "SET q.status='RESOLVED', q.resolvedChapter=$chapter, q.lastUpdatedChapter=$chapter, q.updatedAt=datetime()";
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("id", questId);
            params.put("chapter", resolvedChapter);
            session.run(cypher, params);
            logger.info("✅ resolveOpenQuest: {}@{}", questId, resolvedChapter);
        } catch (Exception e) {
            logger.error("resolveOpenQuest失败", e);
        }
    }

    @Override
    public void deleteOpenQuest(Long novelId, String questId) {
        logger.info("🗑️ 删除OpenQuest: novelId={}, id={}", novelId, questId);
        try (Session session = driver.session()) {
            String cypher =
                "MATCH (q:OpenQuest {novelId: $novelId, id: $id}) " +
                "DETACH DELETE q " +
                "WITH $novelId AS novelId, $id AS questId " +
                "OPTIONAL MATCH (h:OpenQuestHistory {novelId: novelId, questId: questId}) " +
                "DETACH DELETE h";

            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("id", questId);

            session.run(cypher, params);
            logger.info("✅ OpenQuest已删除: {}", questId);
        } catch (Exception e) {
            logger.error("deleteOpenQuest失败", e);
            throw new RuntimeException("删除OpenQuest失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void addSummarySignals(Long novelId, Integer chapterNumber, Map<String, String> signals) {
        if (signals == null || signals.isEmpty()) return;
        String cypher =
            "UNWIND $rows AS row " +
            "MERGE (s:SummarySignal {novelId: $novelId, chapterNumber: $chapterNumber, key: row.key}) " +
            "SET s.value = row.value, s.updatedAt = datetime()";
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : signals.entrySet()) {
            Map<String, Object> r = new HashMap<>();
            r.put("key", e.getKey());
            r.put("value", e.getValue());
            rows.add(r);
        }
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapterNumber", chapterNumber);
            params.put("rows", rows);
            session.run(cypher, params);
            logger.info("🧾 addSummarySignals: chapter={} keys={}", chapterNumber, signals.keySet());
        } catch (Exception e) {
            logger.error("addSummarySignals失败", e);
        }
    }

    // =============================
    // 🆕 核心记忆账本查询实现
    // =============================

    @Override
    public List<Map<String, Object>> getCharacterStates(Long novelId, Integer limit) {
        String cypher =
            "MATCH (s:CharacterState {novelId: $novelId}) " +
            "RETURN s.characterName AS name, s.location AS location, s.realm AS realm, " +
            "       s.alive AS alive, s.inventory AS inventory, s.characterInfo AS characterInfo, s.lastUpdatedChapter AS lastChapter " +
            "ORDER BY s.lastUpdatedChapter DESC " +
            "LIMIT $limit";
        
        List<Map<String, Object>> result = new ArrayList<>();
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("limit", limit != null ? limit : 5);
            
            session.run(cypher, params).list().forEach(record -> {
                Map<String, Object> state = new HashMap<>();
                state.put("name", safeGetString(record, "name", ""));
                state.put("location", safeGetString(record, "location", ""));
                state.put("realm", safeGetString(record, "realm", ""));
                state.put("alive", record.get("alive").asBoolean(true));
                state.put("characterInfo", safeGetString(record, "characterInfo", ""));
                state.put("lastChapter", safeGetInt(record, "lastChapter", 0));
                
                // 处理inventory（List类型）
                try {
                    org.neo4j.driver.Value invValue = record.get("inventory");
                    if (invValue != null && !invValue.isNull() && invValue.asList() != null) {
                        List<String> items = new ArrayList<>();
                        invValue.asList().forEach(v -> {
                            if (v != null) items.add(v.toString());
                        });
                        state.put("inventory", items);
                    } else {
                        state.put("inventory", new ArrayList<>());
                    }
                } catch (Exception e) {
                    state.put("inventory", new ArrayList<>());
                }
                
                result.add(state);
            });
            
            logger.info("✅ 查询到{}个角色状态", result.size());
        } catch (Exception e) {
            logger.error("查询CharacterStates失败", e);
        }
        return result;
    }

    @Override
    public List<GraphEntity> getCharacterProfiles(Long novelId, Integer limit) {
        String cypher =
            "MATCH (p:CharacterProfile {novelId: $novelId}) " +
            "RETURN p " +
            "ORDER BY p.chapterNumber DESC " +
            "LIMIT $limit";

        List<GraphEntity> result = new ArrayList<>();
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("limit", limit != null ? limit : 10);

            session.run(cypher, params).list().forEach(record -> {
                Map<String, Object> profileMap = safeNodeToMap(record.get("p"));

                String id = safeGetString(profileMap, "id", UUID.randomUUID().toString());
                Integer chapterNumber = safeGetInt(profileMap, "chapterNumber", null);

                result.add(GraphEntity.builder()
                    .type("CharacterProfile")
                    .id(id)
                    .chapterNumber(chapterNumber)
                    .properties(profileMap)
                    .build());
            });

            logger.info("✅ 查询到{}个角色档案", result.size());
        } catch (Exception e) {
            logger.error("查询CharacterProfiles失败", e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getTopRelationships(Long novelId, Integer limit) {
        String cypher =
            "MATCH (r:RelationshipState {novelId: $novelId}) " +
            "RETURN r.a AS a, r.b AS b, r.type AS type, r.strength AS strength, " +
            "       r.lastUpdatedChapter AS lastChapter " +
            "ORDER BY r.strength DESC, r.lastUpdatedChapter DESC " +
            "LIMIT $limit";

        List<Map<String, Object>> result = new ArrayList<>();
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("limit", limit != null ? limit : 5);
            
            session.run(cypher, params).list().forEach(record -> {
                Map<String, Object> rel = new HashMap<>();
                rel.put("a", safeGetString(record, "a", ""));
                rel.put("b", safeGetString(record, "b", ""));
                rel.put("type", safeGetString(record, "type", ""));
                rel.put("strength", safeGetDouble(record, "strength", 0.5));
                rel.put("lastChapter", safeGetInt(record, "lastChapter", 0));
                result.add(rel);
            });
            
            logger.info("✅ 查询到{}个关系", result.size());
        } catch (Exception e) {
            logger.error("查询TopRelationships失败", e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getOpenQuests(Long novelId, Integer currentChapter) {
        // 🔍 先查询所有任务用于调试
        String debugCypher = "MATCH (q:OpenQuest {novelId: $novelId}) RETURN q.id AS id, q.status AS status, q.dueByChapter AS due";

        String cypher =
            "MATCH (q:OpenQuest {novelId: $novelId}) " +
            "WHERE q.status = 'OPEN' AND (q.dueByChapter IS NULL OR q.dueByChapter >= $currentChapter) " +
            "RETURN q.id AS id, q.description AS description, q.status AS status, " +
            "       q.introducedChapter AS introduced, q.dueByChapter AS due, " +
            "       q.lastUpdatedChapter AS lastUpdated " +
            "ORDER BY q.dueByChapter ASC, q.lastUpdatedChapter DESC " +
            "LIMIT 10";

        List<Map<String, Object>> result = new ArrayList<>();
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("currentChapter", currentChapter != null ? currentChapter : 999);

            // 🔍 调试：先查询所有任务
            logger.info("🔍 调试：查询所有OpenQuest节点（novelId={}）", novelId);
            List<Record> allQuests = session.run(debugCypher, params).list();
            logger.info("🔍 数据库中共有{}个OpenQuest节点", allQuests.size());
            for (Record r : allQuests) {
                logger.info("  - id={}, status={}, due={}",
                    safeGetString(r, "id", ""),
                    safeGetString(r, "status", ""),
                    safeGetInt(r, "due", 0));
            }

            // 正式查询
            logger.info("🔍 正式查询：currentChapter={}, 条件: status=OPEN AND due>={}", currentChapter, currentChapter);
            session.run(cypher, params).list().forEach(record -> {
                Map<String, Object> quest = new HashMap<>();
                quest.put("id", safeGetString(record, "id", ""));
                quest.put("description", safeGetString(record, "description", ""));
                quest.put("status", safeGetString(record, "status", "OPEN"));
                quest.put("introduced", safeGetInt(record, "introduced", 0));
                quest.put("due", safeGetInt(record, "due", 0));
                quest.put("lastUpdated", safeGetInt(record, "lastUpdated", 0));
                result.add(quest);
            });

            logger.info("✅ 查询到{}个开放任务", result.size());
        } catch (Exception e) {
            logger.error("查询OpenQuests失败", e);
        }
        return result;
    }
    
    /**
     * 查询图谱统计信息
     */
    @Override
    public Map<String, Object> getGraphStatistics(Long novelId) {
        logger.info("📊 Neo4j查询图谱统计: novelId={}", novelId);
        
        String cypher = 
            "MATCH (n {novelId: $novelId}) " +
            "WITH labels(n) AS nodeLabels " +
            "UNWIND nodeLabels AS label " +
            "RETURN label, count(*) AS count";
        
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            
            Map<String, Object> stats = new HashMap<>();
            session.run(cypher, params).list().forEach(record -> {
                String label = record.get("label").asString();
                int count = record.get("count").asInt();
                stats.put(label + "Count", count);
            });
            
            // 查询关系统计
            String relCypher = "MATCH ()-[r {novelId: $novelId}]->() RETURN count(r) AS relCount";
            int relCount = session.run(relCypher, params).single().get("relCount").asInt();
            stats.put("relationshipCount", relCount);
            
            logger.info("✅ 图谱统计: {}", stats);
            return stats;
        } catch (Exception e) {
            logger.error("❌ Neo4j查询统计失败", e);
            Map<String, Object> emptyStats = new HashMap<>();
            emptyStats.put("error", e.getMessage());
            return emptyStats;
        }
    }

    @Override
    public void clearGraph(Long novelId) {
        logger.warn("⚠️ Neo4j清空小说图谱: novelId={}", novelId);
        String cypher = "MATCH (n {novelId: $novelId}) DETACH DELETE n";
        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            session.run(cypher, params);
            logger.info("✅ Neo4j已清空小说{}的图谱", novelId);
        } catch (Exception e) {
            logger.error("❌ Neo4j清空图谱失败", e);
        }
    }
    
    /**
     * 🆕 删除指定章节的图谱实体和关系
     * 注意：由于图谱设计中的状态是跨章节累积的，这里需要特殊处理
     * 
     * 策略：
     * 1. 如果重写的是历史章节（存在更新的后续章节）：
     *    - 跳过图谱清理，避免破坏后续章节的状态连贯性
     *    - 记录警告日志，提示用户图谱可能与内容不一致
     * 
     * 2. 如果重写的是最新章节或接近最新的章节：
     *    - 对于在该章节首次引入的节点：直接删除
     *    - 对于在该章节更新的节点：
     *      a. 查找该节点在前一章的历史快照
     *      b. 如果有历史快照，恢复到前一章的状态
     *      c. 如果没有历史快照，说明是该章节首次引入，直接删除
     */
    @Override
    public void deleteChapterEntities(Long novelId, Integer chapterNumber) {
        logger.info("🗑️ Neo4j删除章节图谱数据: novelId={}, chapterNumber={}", novelId, chapterNumber);
        
        try (Session session = driver.session()) {
            // ========== 🔍 步骤0：检查是否是历史章节 ==========
            // 查询该小说的最大章节号
            String maxChapterQuery = 
                "MATCH (s:CharacterState {novelId: $novelId}) " +
                "RETURN max(s.lastUpdatedChapter) as maxChapter " +
                "UNION " +
                "MATCH (r:RelationshipState {novelId: $novelId}) " +
                "RETURN max(r.lastUpdatedChapter) as maxChapter " +
                "UNION " +
                "MATCH (q:OpenQuest {novelId: $novelId}) " +
                "RETURN max(q.lastUpdatedChapter) as maxChapter";
            
            List<Record> maxChapterRecords = session.run(maxChapterQuery, 
                Map.of("novelId", novelId)).list();
            
            Integer maxChapter = null;
            for (Record record : maxChapterRecords) {
                Value value = record.get("maxChapter");
                if (!value.isNull()) {
                    int chapter = value.asInt();
                    if (maxChapter == null || chapter > maxChapter) {
                        maxChapter = chapter;
                    }
                }
            }
            
            // 如果是历史章节（后面还有更新的章节），跳过图谱清理
            if (maxChapter != null && chapterNumber < maxChapter) {
                logger.warn("⚠️ 检测到重写历史章节: 当前章节={}, 最新图谱章节={}", chapterNumber, maxChapter);
                logger.warn("⚠️ 为避免破坏后续章节的图谱连贯性，跳过图谱数据清理");
                logger.warn("⚠️ 注意：重写后的章节内容可能与图谱数据不一致");
                logger.warn("💡 建议：如需完全重写，请从该章节开始依次重写所有后续章节");
                return;
            }
            
            logger.info("✅ 确认为最新章节或接近最新章节，开始清理图谱数据");
            
            int deletedCount = 0;
            int restoredCount = 0;
            
            // ========== 1. 处理 CharacterState 节点 ==========
            // 1.1 查询在该章节更新的角色状态
            String queryCharStatesQuery = 
                "MATCH (s:CharacterState {novelId: $novelId, lastUpdatedChapter: $chapterNumber}) " +
                "RETURN s.characterName as name, s.location as location, s.realm as realm, " +
                "       s.alive as alive, s.inventory as inventory, s.characterInfo as characterInfo";
            List<Record> charStates = session.run(queryCharStatesQuery, 
                Map.of("novelId", novelId, "chapterNumber", chapterNumber)).list();
            
            logger.info("  📋 找到 {} 个在第{}章更新的角色状态", charStates.size(), chapterNumber);
            
            for (Record record : charStates) {
                String charName = record.get("name").asString();
                
                // 1.2 查询该角色在前一章的历史快照
                String queryHistoryQuery = 
                    "MATCH (h:CharacterStateHistory {novelId: $novelId, characterName: $charName}) " +
                    "WHERE h.chapterNumber < $chapterNumber " +
                    "RETURN h ORDER BY h.chapterNumber DESC LIMIT 1";
                List<Record> history = session.run(queryHistoryQuery, 
                    Map.of("novelId", novelId, "charName", charName, "chapterNumber", chapterNumber)).list();
                
                if (!history.isEmpty()) {
                    // 有历史快照，恢复到前一章的状态
                    Record historyRecord = history.get(0);
                    Map<String, Object> historyNode = safeNodeToMap(historyRecord.get("h"));
                    
                    String restoreQuery = 
                        "MATCH (s:CharacterState {novelId: $novelId, characterName: $charName}) " +
                        "SET s.location = $location, " +
                        "    s.realm = $realm, " +
                        "    s.alive = $alive, " +
                        "    s.inventory = $inventory, " +
                        "    s.characterInfo = $characterInfo, " +
                        "    s.lastUpdatedChapter = $lastUpdatedChapter, " +
                        "    s.updatedAt = datetime()";
                    
                    Map<String, Object> restoreParams = new HashMap<>();
                    restoreParams.put("novelId", novelId);
                    restoreParams.put("charName", charName);
                    restoreParams.put("location", historyNode.get("location"));
                    restoreParams.put("realm", historyNode.get("realm"));
                    restoreParams.put("alive", historyNode.get("alive"));
                    restoreParams.put("inventory", historyNode.get("inventory"));
                    restoreParams.put("characterInfo", historyNode.get("characterInfo"));
                    restoreParams.put("lastUpdatedChapter", historyNode.get("chapterNumber"));
                    
                    session.run(restoreQuery, restoreParams);
                    restoredCount++;
                    logger.info("    ↩️ 恢复角色 {} 到第{}章的状态", charName, historyNode.get("chapterNumber"));
                } else {
                    // 没有历史快照，说明是该章节首次引入，直接删除
                    String deleteQuery = 
                        "MATCH (s:CharacterState {novelId: $novelId, characterName: $charName}) " +
                        "DELETE s";
                    session.run(deleteQuery, Map.of("novelId", novelId, "charName", charName));
                    deletedCount++;
                    logger.info("    🗑️ 删除角色 {} （该章节首次引入）", charName);
                }
            }
            
            // ========== 2. 处理 RelationshipState 节点 ==========
            String queryRelStatesQuery = 
                "MATCH (r:RelationshipState {novelId: $novelId, lastUpdatedChapter: $chapterNumber}) " +
                "RETURN r.a as a, r.b as b, r.type as type";
            List<Record> relStates = session.run(queryRelStatesQuery, 
                Map.of("novelId", novelId, "chapterNumber", chapterNumber)).list();
            
            logger.info("  📋 找到 {} 个在第{}章更新的关系状态", relStates.size(), chapterNumber);
            
            for (Record record : relStates) {
                String a = record.get("a").asString();
                String b = record.get("b").asString();
                String type = record.get("type").asString();
                
                // 查询历史快照
                String queryRelHistoryQuery = 
                    "MATCH (h:RelationshipStateHistory {novelId: $novelId, a: $a, b: $b, type: $type}) " +
                    "WHERE h.chapterNumber < $chapterNumber " +
                    "RETURN h ORDER BY h.chapterNumber DESC LIMIT 1";
                List<Record> relHistory = session.run(queryRelHistoryQuery, 
                    Map.of("novelId", novelId, "a", a, "b", b, "type", type, "chapterNumber", chapterNumber)).list();
                
                if (!relHistory.isEmpty()) {
                    // 恢复到前一章的状态
                    Record historyRecord = relHistory.get(0);
                    Map<String, Object> historyNode = safeNodeToMap(historyRecord.get("h"));
                    
                    String restoreQuery = 
                        "MATCH (r:RelationshipState {novelId: $novelId, a: $a, b: $b, type: $type}) " +
                        "SET r.strength = $strength, " +
                        "    r.lastUpdatedChapter = $lastUpdatedChapter, " +
                        "    r.updatedAt = datetime()";
                    
                    Map<String, Object> restoreParams = new HashMap<>();
                    restoreParams.put("novelId", novelId);
                    restoreParams.put("a", a);
                    restoreParams.put("b", b);
                    restoreParams.put("type", type);
                    restoreParams.put("strength", historyNode.get("strength"));
                    restoreParams.put("lastUpdatedChapter", historyNode.get("chapterNumber"));
                    
                    session.run(restoreQuery, restoreParams);
                    restoredCount++;
                    logger.info("    ↩️ 恢复关系 {}-[{}]-{} 到第{}章的状态", a, type, b, historyNode.get("chapterNumber"));
                } else {
                    // 直接删除
                    String deleteQuery = 
                        "MATCH (r:RelationshipState {novelId: $novelId, a: $a, b: $b, type: $type}) " +
                        "DELETE r";
                    session.run(deleteQuery, Map.of("novelId", novelId, "a", a, "b", b, "type", type));
                    deletedCount++;
                    logger.info("    🗑️ 删除关系 {}-[{}]-{} （该章节首次引入）", a, type, b);
                }
            }
            
            // ========== 3. 处理 OpenQuest 节点 ==========
            String queryQuestsQuery = 
                "MATCH (q:OpenQuest {novelId: $novelId, lastUpdatedChapter: $chapterNumber}) " +
                "RETURN q.id as id";
            List<Record> quests = session.run(queryQuestsQuery, 
                Map.of("novelId", novelId, "chapterNumber", chapterNumber)).list();
            
            logger.info("  📋 找到 {} 个在第{}章更新的任务", quests.size(), chapterNumber);
            
            for (Record record : quests) {
                String questId = record.get("id").asString();
                
                // 查询历史快照
                String queryQuestHistoryQuery = 
                    "MATCH (h:OpenQuestHistory {novelId: $novelId, questId: $questId}) " +
                    "WHERE h.chapterNumber < $chapterNumber " +
                    "RETURN h ORDER BY h.chapterNumber DESC LIMIT 1";
                List<Record> questHistory = session.run(queryQuestHistoryQuery, 
                    Map.of("novelId", novelId, "questId", questId, "chapterNumber", chapterNumber)).list();
                
                if (!questHistory.isEmpty()) {
                    // 恢复到前一章的状态
                    Record historyRecord = questHistory.get(0);
                    Map<String, Object> historyNode = safeNodeToMap(historyRecord.get("h"));
                    
                    String restoreQuery = 
                        "MATCH (q:OpenQuest {novelId: $novelId, id: $questId}) " +
                        "SET q.description = $description, " +
                        "    q.status = $status, " +
                        "    q.lastUpdatedChapter = $lastUpdatedChapter, " +
                        "    q.updatedAt = datetime()";
                    
                    Map<String, Object> restoreParams = new HashMap<>();
                    restoreParams.put("novelId", novelId);
                    restoreParams.put("questId", questId);
                    restoreParams.put("description", historyNode.get("description"));
                    restoreParams.put("status", historyNode.get("status"));
                    restoreParams.put("lastUpdatedChapter", historyNode.get("chapterNumber"));
                    
                    session.run(restoreQuery, restoreParams);
                    restoredCount++;
                    logger.info("    ↩️ 恢复任务 {} 到第{}章的状态", questId, historyNode.get("chapterNumber"));
                } else {
                    // 直接删除
                    String deleteQuery = 
                        "MATCH (q:OpenQuest {novelId: $novelId, id: $questId}) " +
                        "DELETE q";
                    session.run(deleteQuery, Map.of("novelId", novelId, "questId", questId));
                    deletedCount++;
                    logger.info("    🗑️ 删除任务 {} （该章节首次引入）", questId);
                }
            }
            
            // ========== 4. 删除该章节引入的 OpenQuest 节点（introducedChapter == chapterNumber）==========
            String deleteIntroducedQuestQuery = 
                "MATCH (q:OpenQuest {novelId: $novelId, introducedChapter: $chapterNumber}) " +
                "DELETE q";
            Result introducedQuestResult = session.run(deleteIntroducedQuestQuery, 
                Map.of("novelId", novelId, "chapterNumber", chapterNumber));
            int deletedIntroducedQuests = introducedQuestResult.consume().counters().nodesDeleted();
            deletedCount += deletedIntroducedQuests;
            if (deletedIntroducedQuests > 0) {
                logger.info("  🗑️ 删除了 {} 个在该章节引入的 OpenQuest 节点", deletedIntroducedQuests);
            }
            
            // ========== 5. 删除章节特定的事件节点（Event 是章节特定的，直接删除）==========
            String deleteEventQuery = 
                "MATCH (e:Event {novelId: $novelId, chapterNumber: $chapterNumber}) " +
                "DETACH DELETE e";
            Result eventResult = session.run(deleteEventQuery, 
                Map.of("novelId", novelId, "chapterNumber", chapterNumber));
            int deletedEvents = eventResult.consume().counters().nodesDeleted();
            deletedCount += deletedEvents;
            if (deletedEvents > 0) {
                logger.info("  🗑️ 删除了 {} 个 Event 节点", deletedEvents);
            }
            
            // ========== 6. 删除章节特定的伏笔节点 ==========
            String deleteForeshadowQuery = 
                "MATCH (f:Foreshadowing {novelId: $novelId}) " +
                "WHERE f.introducedChapter = $chapterNumber OR f.resolvedChapter = $chapterNumber " +
                "DETACH DELETE f";
            Result foreshadowResult = session.run(deleteForeshadowQuery, 
                Map.of("novelId", novelId, "chapterNumber", chapterNumber));
            int deletedForeshadows = foreshadowResult.consume().counters().nodesDeleted();
            deletedCount += deletedForeshadows;
            if (deletedForeshadows > 0) {
                logger.info("  🗑️ 删除了 {} 个 Foreshadowing 节点", deletedForeshadows);
            }
            
            logger.info("✅ Neo4j章节数据清理完成: 删除 {} 个节点, 恢复 {} 个节点到前一章状态", deletedCount, restoredCount);
            
        } catch (Exception e) {
            logger.error("❌ Neo4j删除章节图谱数据失败", e);
            throw new RuntimeException("Neo4j删除章节图谱数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 强制删除指定章节范围的所有图谱数据（用于 regenerate-graph 接口）
     * 不做历史章节保护判断，直接删除传入章节号对应的所有节点
     * 
     * @param novelId 小说ID
     * @param chapterNumbers 要删除的章节号列表
     */
    public void forceDeleteChapterRangeEntities(Long novelId, java.util.List<Integer> chapterNumbers) {
        if (novelId == null || chapterNumbers == null || chapterNumbers.isEmpty()) {
            logger.warn("⚠️ forceDeleteChapterRangeEntities: 参数为空，跳过删除");
            return;
        }
        
        logger.info("🗑️ 强制删除小说{} 章节{} 的所有图谱节点", novelId, chapterNumbers);
        
        try (Session session = driver.session()) {
            int totalDeleted = 0;
            
            // 1. 删除 CharacterState（lastUpdatedChapter 在指定范围内的）
            String deleteCharStateQuery = 
                "MATCH (s:CharacterState {novelId: $novelId}) " +
                "WHERE s.lastUpdatedChapter IN $chapterNumbers " +
                "DETACH DELETE s";
            Result charStateResult = session.run(deleteCharStateQuery, 
                Map.of("novelId", novelId, "chapterNumbers", chapterNumbers));
            int deletedCharStates = charStateResult.consume().counters().nodesDeleted();
            totalDeleted += deletedCharStates;
            if (deletedCharStates > 0) {
                logger.info("  🗑️ 删除了 {} 个 CharacterState 节点", deletedCharStates);
            }
            
            // 2. 删除 RelationshipState（lastUpdatedChapter 在指定范围内的）
            String deleteRelStateQuery = 
                "MATCH (r:RelationshipState {novelId: $novelId}) " +
                "WHERE r.lastUpdatedChapter IN $chapterNumbers " +
                "DETACH DELETE r";
            Result relStateResult = session.run(deleteRelStateQuery, 
                Map.of("novelId", novelId, "chapterNumbers", chapterNumbers));
            int deletedRelStates = relStateResult.consume().counters().nodesDeleted();
            totalDeleted += deletedRelStates;
            if (deletedRelStates > 0) {
                logger.info("  🗑️ 删除了 {} 个 RelationshipState 节点", deletedRelStates);
            }
            
            // 3. 删除 OpenQuest（introducedChapter 或 lastUpdatedChapter 在指定范围内的）
            String deleteQuestQuery = 
                "MATCH (q:OpenQuest {novelId: $novelId}) " +
                "WHERE q.introducedChapter IN $chapterNumbers OR q.lastUpdatedChapter IN $chapterNumbers " +
                "DETACH DELETE q";
            Result questResult = session.run(deleteQuestQuery, 
                Map.of("novelId", novelId, "chapterNumbers", chapterNumbers));
            int deletedQuests = questResult.consume().counters().nodesDeleted();
            totalDeleted += deletedQuests;
            if (deletedQuests > 0) {
                logger.info("  🗑️ 删除了 {} 个 OpenQuest 节点", deletedQuests);
            }
            
            // 4. 删除 Event（chapterNumber 在指定范围内的）
            String deleteEventQuery = 
                "MATCH (e:Event {novelId: $novelId}) " +
                "WHERE e.chapterNumber IN $chapterNumbers " +
                "DETACH DELETE e";
            Result eventResult = session.run(deleteEventQuery, 
                Map.of("novelId", novelId, "chapterNumbers", chapterNumbers));
            int deletedEvents = eventResult.consume().counters().nodesDeleted();
            totalDeleted += deletedEvents;
            if (deletedEvents > 0) {
                logger.info("  🗑️ 删除了 {} 个 Event 节点", deletedEvents);
            }
            
            // 5. 删除 Foreshadowing（introducedChapter 或 resolvedChapter 在指定范围内的）
            String deleteForeshadowQuery = 
                "MATCH (f:Foreshadowing {novelId: $novelId}) " +
                "WHERE f.introducedChapter IN $chapterNumbers OR f.resolvedChapter IN $chapterNumbers " +
                "DETACH DELETE f";
            Result foreshadowResult = session.run(deleteForeshadowQuery, 
                Map.of("novelId", novelId, "chapterNumbers", chapterNumbers));
            int deletedForeshadows = foreshadowResult.consume().counters().nodesDeleted();
            totalDeleted += deletedForeshadows;
            if (deletedForeshadows > 0) {
                logger.info("  🗑️ 删除了 {} 个 Foreshadowing 节点", deletedForeshadows);
            }
            
            // 6. 删除 NarrativeBeat（chapterNumber 在指定范围内的）
            String deleteBeatQuery = 
                "MATCH (b:NarrativeBeat {novelId: $novelId}) " +
                "WHERE b.chapterNumber IN $chapterNumbers " +
                "DETACH DELETE b";
            Result beatResult = session.run(deleteBeatQuery, 
                Map.of("novelId", novelId, "chapterNumbers", chapterNumbers));
            int deletedBeats = beatResult.consume().counters().nodesDeleted();
            totalDeleted += deletedBeats;
            if (deletedBeats > 0) {
                logger.info("  🗑️ 删除了 {} 个 NarrativeBeat 节点", deletedBeats);
            }
            
            // 7. 删除 ConflictArc 和 CharacterArc（lastUpdatedChapter 在指定范围内的）
            String deleteArcQuery = 
                "MATCH (a) " +
                "WHERE (a:ConflictArc OR a:CharacterArc) AND a.novelId = $novelId AND a.lastUpdatedChapter IN $chapterNumbers " +
                "DETACH DELETE a";
            Result arcResult = session.run(deleteArcQuery, 
                Map.of("novelId", novelId, "chapterNumbers", chapterNumbers));
            int deletedArcs = arcResult.consume().counters().nodesDeleted();
            totalDeleted += deletedArcs;
            if (deletedArcs > 0) {
                logger.info("  🗑️ 删除了 {} 个 Arc 节点", deletedArcs);
            }
            
            logger.info("✅ 强制删除完成: 共删除 {} 个节点（章节范围：{}）", totalDeleted, chapterNumbers);
            
        } catch (Exception e) {
            logger.error("❌ 强制删除章节图谱数据失败", e);
            throw new RuntimeException("强制删除章节图谱数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查服务可用性
     */
    @Override
    public boolean isAvailable() {
        try {
            driver.verifyConnectivity();
            return true;
        } catch (Exception e) {
            logger.error("❌ Neo4j连接不可用", e);
            return false;
        }
    }
    
    /**
     * 获取服务类型
     */
    @Override
    public String getServiceType() {
        return "NEO4J";
    }
    
    private String normalizeBeatType(String rawType) {
        if (rawType == null) {
            return "UNKNOWN";
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "UNKNOWN";
        }
        if (normalized.contains("CONFLICT") || normalized.contains("冲突") || normalized.contains("战")) {
            return "CONFLICT";
        }
        if (normalized.contains("CLIMAX") || normalized.contains("爆发") || normalized.contains("高潮")) {
            return "CLIMAX";
        }
        if (normalized.contains("PLOT") || normalized.contains("ADV") || normalized.contains("主线") || normalized.contains("推进")) {
            return "PLOT";
        }
        if (normalized.contains("CHAR") || normalized.contains("EMOTION") || normalized.contains("人物") || normalized.contains("情")) {
            return "CHARACTER";
        }
        if (normalized.contains("RELIEF") || normalized.contains("缓冲") || normalized.contains("日常") || normalized.contains("轻松")) {
            return "RELIEF";
        }
        if (normalized.contains("SETUP") || normalized.contains("铺垫")) {
            return "SETUP";
        }
        return normalized;
    }

    private String buildInsertCypher(GraphEntity entity) {
        String type = entity.getType();
        
        if ("Event".equals(type)) {
            return "MERGE (e:Event {id: $id}) " +
                   "SET e.novelId = $novelId, " +
                   "    e.chapterNumber = $chapterNumber, " +
                   "    e.summary = $summary, " +
                   "    e.description = $description, " +
                   "    e.participants = $participants, " +
                   "    e.location = $location, " +
                   "    e.realm = $realm, " +
                   "    e.emotionalTone = $emotionalTone, " +
                   "    e.tags = $tags, " +
                   "    e.importance = $importance, " +
                   "    e.updatedAt = datetime()";
        } else if ("Foreshadow".equals(type)) {
            return "MERGE (f:Foreshadowing {id: $id}) " +
                   "SET f.novelId = $novelId, " +
                   "    f.content = $content, " +
                   "    f.importance = $importance, " +
                   "    f.status = $status, " +
                   "    f.plannedRevealChapter = $plannedRevealChapter, " +
                   "    f.updatedAt = datetime()";
        } else if ("Plotline".equals(type)) {
            return "MERGE (p:PlotLine {id: $id}) " +
                   "SET p.novelId = $novelId, " +
                   "    p.name = $name, " +
                   "    p.priority = $priority, " +
                   "    p.updatedAt = datetime()";
        } else if ("WorldRule".equals(type)) {
            return "MERGE (r:WorldRule {id: $id}) " +
                   "SET r.novelId = $novelId, " +
                   "    r.name = $name, " +
                   "    r.content = $content, " +
                   "    r.constraint = $constraint, " +
                   "    r.category = $category, " +
                   "    r.scope = $scope, " +
                   "    r.importance = $importance, " +
                   "    r.updatedAt = datetime()";
        } else if ("NarrativeBeat".equals(type)) {
            return "MERGE (b:NarrativeBeat {novelId: $novelId, chapterNumber: $chapterNumber}) " +
                   "SET b.id = $id, " +
                   "    b.beatType = $beatType, " +
                   "    b.focus = $focus, " +
                   "    b.sentiment = $sentiment, " +
                   "    b.tension = $tension, " +
                   "    b.paceScore = $paceScore, " +
                   "    b.viewpoint = $viewpoint, " +
                   "    b.updatedAt = datetime()";
        } else if ("ConflictArc".equals(type)) {
            return "MERGE (a:ConflictArc {id: $id}) " +
                   "SET a.novelId = $novelId, " +
                   "    a.name = $name, " +
                   "    a.stage = $stage, " +
                   "    a.urgency = $urgency, " +
                   "    a.nextAction = $nextAction, " +
                   "    a.protagonist = $protagonist, " +
                   "    a.antagonist = $antagonist, " +
                   "    a.trend = $trend, " +
                   "    a.lastUpdatedChapter = $chapterNumber, " +
                   "    a.updatedAt = datetime()";
        } else if ("CharacterArc".equals(type)) {
            return "MERGE (a:CharacterArc {id: $id}) " +
                   "SET a.novelId = $novelId, " +
                   "    a.characterName = $characterName, " +
                   "    a.arcName = $arcName, " +
                   "    a.pendingBeat = $pendingBeat, " +
                   "    a.nextGoal = $nextGoal, " +
                   "    a.priority = $priority, " +
                   "    a.progress = $progress, " +
                   "    a.totalBeats = $totalBeats, " +
                   "    a.lastUpdatedChapter = $chapterNumber, " +
                   "    a.updatedAt = datetime()";
        } else if ("PerspectiveUsage".equals(type)) {
            return "MERGE (p:PerspectiveUsage {id: $id}) " +
                   "SET p.novelId = $novelId, " +
                   "    p.chapterNumber = $chapterNumber, " +
                   "    p.characterName = $characterName, " +
                   "    p.mode = $mode, " +
                   "    p.tone = $tone, " +
                   "    p.purpose = $purpose, " +
                   "    p.updatedAt = datetime()";
        } else if ("CharacterState".equals(type)) {
            return "MERGE (s:CharacterState {novelId: $novelId, characterName: $characterName}) " +
                   "SET s.location = $location, " +
                   "    s.realm = $realm, " +
                   "    s.alive = coalesce($alive, true), " +
                   "    s.lastUpdatedChapter = $chapterNumber, " +
                   "    s.updatedAt = datetime()";
        } else {
            return "// Unknown entity type";
        }
    }
    
    private Map<String, Object> buildInsertParams(Long novelId, GraphEntity entity) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", entity.getId());
        params.put("novelId", novelId);
        params.put("chapterNumber", entity.getChapterNumber());
        
        // 复制所有属性
        Map<String, Object> properties = entity.getProperties();
        if (properties != null) {
            params.putAll(properties);
        }
        
        // 🔧 字段名映射和缺失字段补充（根据实体类型）
        String type = entity.getType();
        
        if ("Foreshadow".equals(type)) {
            // Foreshadow特殊处理：映射字段名
            if (!params.containsKey("plannedRevealChapter") && params.containsKey("suggestedResolveWindow")) {
                params.put("plannedRevealChapter", params.get("suggestedResolveWindow"));
            }
            // 确保必需字段存在
            if (!params.containsKey("plannedRevealChapter")) {
                params.put("plannedRevealChapter", "未指定");
            }
            if (!params.containsKey("content")) {
                params.put("content", params.getOrDefault("description", ""));
            }
            if (!params.containsKey("importance")) {
                params.put("importance", 0.5);
            }
            if (!params.containsKey("status")) {
                params.put("status", "未解决");
            }
        } else if ("Event".equals(type)) {
            // Event必需字段
            if (!params.containsKey("summary")) {
                params.put("summary", params.getOrDefault("description", ""));
            }
            if (!params.containsKey("description")) {
                params.put("description", params.getOrDefault("summary", ""));
            }
            if (!params.containsKey("participants")) {
                params.put("participants", "");
            }
            // 可选状态字段（用于后续自动更新角色状态）
            if (!params.containsKey("location")) {
                params.put("location", "");
            }
            if (!params.containsKey("realm")) {
                params.put("realm", "");
            }
            if (!params.containsKey("emotionalTone")) {
                params.put("emotionalTone", "中性");
            }
            if (!params.containsKey("tags")) {
                params.put("tags", "");
            }
            if (!params.containsKey("importance")) {
                params.put("importance", 0.5);
            }
        } else if ("Plotline".equals(type)) {
            if (!params.containsKey("name")) {
                params.put("name", "未命名情节线");
            }
            if (!params.containsKey("priority")) {
                params.put("priority", 0.5);
            }
        } else if ("WorldRule".equals(type)) {
            if (!params.containsKey("name")) {
                params.put("name", "未命名规则");
            }
            if (!params.containsKey("content")) {
                params.put("content", params.getOrDefault("description", ""));
            }
            if (!params.containsKey("constraint")) {
                params.put("constraint", "");
            }
            if (!params.containsKey("category")) {
                params.put("category", "通用");
            }
            if (!params.containsKey("scope")) {
                params.put("scope", "全局");
            }
            if (!params.containsKey("importance")) {
                params.put("importance", 0.5);
            }
        } else if ("NarrativeBeat".equals(type)) {
            if (!params.containsKey("beatType")) {
                params.put("beatType", "UNKNOWN");
            }
            if (!params.containsKey("focus")) {
                params.put("focus", "");
            }
            if (!params.containsKey("sentiment")) {
                params.put("sentiment", 0.0);
            }
            if (!params.containsKey("tension")) {
                params.put("tension", 0.5);
            }
            if (!params.containsKey("paceScore")) {
                params.put("paceScore", 0.5);
            }
            if (!params.containsKey("viewpoint")) {
                params.put("viewpoint", "");
            }
        } else if ("ConflictArc".equals(type)) {
            if (!params.containsKey("name")) {
                params.put("name", "未命名冲突");
            }
            if (!params.containsKey("stage")) {
                params.put("stage", "进行中");
            }
            if (!params.containsKey("urgency")) {
                params.put("urgency", 0.5);
            }
            if (!params.containsKey("nextAction")) {
                params.put("nextAction", "");
            }
            if (!params.containsKey("protagonist")) {
                params.put("protagonist", "");
            }
            if (!params.containsKey("antagonist")) {
                params.put("antagonist", "");
            }
            if (!params.containsKey("trend")) {
                params.put("trend", "");
            }
        } else if ("CharacterArc".equals(type)) {
            if (!params.containsKey("characterName")) {
                params.put("characterName", "未知");
            }
            if (!params.containsKey("arcName")) {
                params.put("arcName", "成长");
            }
            if (!params.containsKey("pendingBeat")) {
                params.put("pendingBeat", "");
            }
            if (!params.containsKey("nextGoal")) {
                params.put("nextGoal", "");
            }
            if (!params.containsKey("priority")) {
                params.put("priority", 0.5);
            }
            if (!params.containsKey("progress")) {
                params.put("progress", 0);
            }
            if (!params.containsKey("totalBeats")) {
                params.put("totalBeats", 0);
            }
        } else if ("PerspectiveUsage".equals(type)) {
            if (!params.containsKey("characterName")) {
                params.put("characterName", "");
            }
            if (!params.containsKey("mode")) {
                params.put("mode", "第三人称");
            }
            if (!params.containsKey("tone")) {
                params.put("tone", "");
            }
            if (!params.containsKey("purpose")) {
                params.put("purpose", "");
            }
        }
        
        return params;
    }

    /**
     * 🆕 从事件属性更新参与者的角色状态（位置/境界），写入 CharacterState
     */
    private void updateCharacterStatesFromEvent(Long novelId, GraphEntity event) {
        Map<String, Object> props = event.getProperties();
        if (props == null || props.isEmpty()) {
            return;
        }

        // 优先使用onSceneParticipants（真正出现在当前场景的角色），否则退回到participants
        Object participantsObj = props.get("onSceneParticipants");
        if (participantsObj == null) {
            participantsObj = props.get("participants");
        }
        if (participantsObj == null) {
            return;
        }

        List<String> participants = new java.util.ArrayList<>();
        if (participantsObj instanceof List) {
            for (Object p : (List<?>) participantsObj) {
                if (p != null) {
                    String t = p.toString().trim();
                    if (!t.isEmpty()) {
                        participants.add(t);
                    }
                }
            }
        } else if (participantsObj instanceof String) {
            String[] parts = participantsObj.toString().split("[,，、]");
            for (String part : parts) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    participants.add(t);
                }
            }
        }
        if (participants.isEmpty()) {
            return;
        }

        String location = props.get("location") != null ? String.valueOf(props.get("location")) : "";
        String realm = props.get("realm") != null ? String.valueOf(props.get("realm")) : "";
        Integer chapterNumber = event.getChapterNumber();

        String cypher =
            "UNWIND $rows AS row " +
            "MERGE (s:CharacterState {novelId: $novelId, characterName: row.name}) " +
            "SET s.location = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce(row.location, s.location) ELSE s.location END, " +
            "    s.realm = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce(row.realm, s.realm) ELSE s.realm END, " +
            "    s.alive = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN coalesce(row.alive, s.alive) ELSE s.alive END, " +
            "    s.lastUpdatedChapter = CASE WHEN $chapterNumber >= coalesce(s.lastUpdatedChapter,-1) THEN $chapterNumber ELSE s.lastUpdatedChapter END, " +
            "    s.updatedAt = datetime()";

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String name : participants) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", name);
            row.put("location", location);
            row.put("realm", realm);
            row.put("alive", true);
            rows.add(row);
        }

        try (Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("chapterNumber", chapterNumber);
            params.put("rows", rows);
            session.run(cypher, params);
            logger.info("🧭 已根据事件更新{}个角色状态", rows.size());
        } catch (Exception e) {
            logger.warn("更新角色状态写入失败: {}", e.getMessage());
        }
    }
    
    private double resolveImportance(Value value, double defaultValue) {
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        try {
            double numeric = value.asDouble();
            if (numeric > 1) {
                return Math.min(1.0, numeric / 10.0);
            }
            return Math.max(0.0, Math.min(1.0, numeric));
        } catch (Exception ignored) {
            // fallthrough to string handling
        }
        try {
            return mapImportance(value.asString(), defaultValue);
        } catch (Exception ex) {
            return defaultValue;
        }
    }
    
    /**
     * 重载方法：接收Object类型的importance值
     */
    private double resolveImportance(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        // 如果是数字类型
        if (value instanceof Number) {
            double numeric = ((Number) value).doubleValue();
            if (numeric > 1) {
                return Math.min(1.0, numeric / 10.0);
            }
            return Math.max(0.0, Math.min(1.0, numeric));
        }
        // 如果是字符串类型
        if (value instanceof String) {
            return mapImportance((String) value, defaultValue);
        }
        return defaultValue;
    }

    private double mapImportance(String importance, double defaultValue) {
        if (importance == null) {
            return defaultValue;
        }
        String normalized = importance.trim().toLowerCase();
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
                return defaultValue;
        }
    }

    /**
     * 创建属性Map的辅助方法（JDK 8兼容）
     */
    private Map<String, Object> createPropertiesMap(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
    
    /**
     * 获取小说的所有图谱数据（仅核心记忆账本）
     */
    @Override
    public Map<String, Object> getAllGraphData(Long novelId) {
        logger.info("📊 Neo4j查询小说核心记忆账本数据: novelId={}", novelId);

        Map<String, Object> result = new HashMap<>();

        try (Session session = driver.session()) {

            // 🆕 查询核心记忆账本数据
            // 先查询所有CharacterState节点用于调试
            logger.info("🔍 开始查询CharacterState节点...");
            String debugCharStateQuery = "MATCH (s:CharacterState) WHERE s.novelId = $novelId RETURN s";
            List<Record> debugCharStates = session.run(debugCharStateQuery, Collections.singletonMap("novelId", novelId)).list();
            logger.info("🔍 数据库中共有{}个CharacterState节点（novelId={}）", debugCharStates.size(), novelId);
            for (Record r : debugCharStates) {
                logger.info("  - CharacterState: {}", r.get("s").asMap());
            }

            // 查询角色状态
            String characterStateQuery = "MATCH (s:CharacterState {novelId: $novelId}) " +
                                        "RETURN s.characterName as name, s.location as location, s.realm as realm, " +
                                        "       s.alive as alive, s.inventory as inventory, s.characterInfo as characterInfo, " +
                                        "       s.lastUpdatedChapter as chapter " +
                                        "ORDER BY s.lastUpdatedChapter DESC";
            List<Map<String, Object>> characterStates = session.run(characterStateQuery, Collections.singletonMap("novelId", novelId))
                .list(record -> {
                    Map<String, Object> state = new HashMap<>();
                    state.put("name", record.get("name").asString(""));
                    state.put("location", record.get("location").asString(""));
                    state.put("realm", record.get("realm").asString(""));
                    state.put("alive", record.get("alive").asBoolean(true));

                    // 🔧 修复：安全处理 inventory 字段（可能为 NULL）
                    Value inventoryValue = record.get("inventory");
                    if (inventoryValue != null && !inventoryValue.isNull()) {
                        state.put("inventory", inventoryValue.asList());
                    } else {
                        state.put("inventory", Collections.emptyList());
                    }

                    // 添加 characterInfo 字段
                    Value characterInfoValue = record.get("characterInfo");
                    if (characterInfoValue != null && !characterInfoValue.isNull()) {
                        state.put("characterInfo", characterInfoValue.asString(""));
                    } else {
                        state.put("characterInfo", "");
                    }

                    state.put("chapter", record.get("chapter").asInt(0));
                    return state;
                });
            logger.info("🔍 查询到{}个CharacterState", characterStates.size());

            // 查询关系状态
            String relationshipStateQuery = "MATCH (r:RelationshipState {novelId: $novelId}) " +
                                           "RETURN r.a as a, r.b as b, r.type as type, r.strength as strength, " +
                                           "       r.lastUpdatedChapter as chapter " +
                                           "ORDER BY r.lastUpdatedChapter DESC";
            List<Map<String, Object>> relationshipStates = session.run(relationshipStateQuery, Collections.singletonMap("novelId", novelId))
                .list(record -> {
                    Map<String, Object> rel = new HashMap<>();
                    rel.put("a", record.get("a").asString(""));
                    rel.put("b", record.get("b").asString(""));
                    rel.put("type", record.get("type").asString(""));
                    rel.put("strength", record.get("strength").asDouble(0.5));
                    rel.put("chapter", record.get("chapter").asInt(0));
                    return rel;
                });

            // 先查询所有OpenQuest节点用于调试
            logger.info("🔍 开始查询OpenQuest节点...");
            String debugQuestQuery = "MATCH (q:OpenQuest) WHERE q.novelId = $novelId RETURN q";
            List<Record> debugQuests = session.run(debugQuestQuery, Collections.singletonMap("novelId", novelId)).list();
            logger.info("🔍 数据库中共有{}个OpenQuest节点（novelId={}）", debugQuests.size(), novelId);
            for (Record r : debugQuests) {
                logger.info("  - OpenQuest: {}", r.get("q").asMap());
            }

            // 查询开放任务
            String openQuestQuery = "MATCH (q:OpenQuest {novelId: $novelId}) " +
                                   "RETURN q.id as id, q.description as description, q.status as status, " +
                                   "       q.introducedChapter as introduced, q.dueByChapter as due, " +
                                   "       q.lastUpdatedChapter as lastUpdated " +
                                   "ORDER BY q.lastUpdatedChapter DESC";
            List<Map<String, Object>> openQuests = session.run(openQuestQuery, Collections.singletonMap("novelId", novelId))
                .list(record -> {
                    Map<String, Object> quest = new HashMap<>();
                    quest.put("id", record.get("id").asString(""));
                    quest.put("description", record.get("description").asString(""));
                    quest.put("status", record.get("status").asString(""));
                    quest.put("introduced", record.get("introduced").asInt(0));
                    quest.put("due", record.get("due").asInt(0));
                    quest.put("lastUpdated", record.get("lastUpdated").asInt(0));
                    return quest;
                });
            logger.info("🔍 查询到{}个OpenQuest", openQuests.size());

            // 查询历史事件
            logger.info("🔍 开始查询Event节点...");
            String eventQuery = "MATCH (e:Event {novelId: $novelId}) " +
                               "RETURN e.id as id, e.summary as summary, e.chapterNumber as chapter, " +
                               "       e.importance as importance, e.emotionalTone as emotionalTone, " +
                               "       e.tags as tags, e.description as description, " +
                               "       e.participants as participants, e.location as location " +
                               "ORDER BY e.chapterNumber DESC";
            List<Map<String, Object>> events = session.run(eventQuery, Collections.singletonMap("novelId", novelId))
                .list(record -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("id", record.get("id").asString(""));
                    event.put("summary", record.get("summary").asString(""));
                    event.put("chapter", record.get("chapter").asInt(0));
                    
                    // 安全处理 importance 字段
                    Value importanceValue = record.get("importance");
                    if (importanceValue != null && !importanceValue.isNull()) {
                        event.put("importance", importanceValue.asDouble(0.5));
                    } else {
                        event.put("importance", 0.5);
                    }
                    
                    // 安全处理 emotionalTone 字段
                    Value toneValue = record.get("emotionalTone");
                    if (toneValue != null && !toneValue.isNull()) {
                        event.put("emotionalTone", toneValue.asString(""));
                    } else {
                        event.put("emotionalTone", "");
                    }
                    
                    // 安全处理 tags 字段
                    Value tagsValue = record.get("tags");
                    if (tagsValue != null && !tagsValue.isNull()) {
                        event.put("tags", tagsValue.asList());
                    } else {
                        event.put("tags", Collections.emptyList());
                    }
                    
                    // 安全处理 description 字段
                    Value descValue = record.get("description");
                    if (descValue != null && !descValue.isNull()) {
                        event.put("description", descValue.asString(""));
                    } else {
                        event.put("description", "");
                    }
                    
                    // 安全处理 participants 字段
                    Value participantsValue = record.get("participants");
                    if (participantsValue != null && !participantsValue.isNull()) {
                        event.put("participants", participantsValue.asList());
                    } else {
                        event.put("participants", Collections.emptyList());
                    }
                    
                    // 安全处理 location 字段
                    Value locationValue = record.get("location");
                    if (locationValue != null && !locationValue.isNull()) {
                        event.put("location", locationValue.asString(""));
                    } else {
                        event.put("location", "");
                    }
                    
                    return event;
                });
            logger.info("🔍 查询到{}个Event", events.size());

            // 返回核心记忆账本数据和历史事件
            result.put("characterStates", characterStates);
            result.put("relationshipStates", relationshipStates);
            result.put("openQuests", openQuests);
            result.put("events", events);

            // 添加统计信息
            result.put("totalCharacterStates", characterStates.size());
            result.put("totalRelationshipStates", relationshipStates.size());
            result.put("totalOpenQuests", openQuests.size());
            result.put("totalEvents", events.size());

            logger.info("✅ 核心记忆账本查询完成: {}个角色状态, {}个关系状态, {}个任务, {}个历史事件",
                characterStates.size(), relationshipStates.size(), openQuests.size(), events.size());
            
        } catch (Exception e) {
            logger.error("❌ 查询图谱数据失败", e);
            throw new RuntimeException("查询图谱数据失败: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 安全地将Neo4j的Value节点转换为Map
     */
    private Map<String, Object> safeNodeToMap(Value nodeValue) {
        if (nodeValue == null || nodeValue.isNull()) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(nodeValue.asNode().asMap());
        } catch (Exception e) {
            logger.warn("无法将Value转换为Node: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 安全获取字符串值
     */
    private String safeGetString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }
    
    // 🆕 Record版本的safe方法
    private String safeGetString(org.neo4j.driver.Record record, String key, String defaultValue) {
        try {
            if (record == null || !record.containsKey(key)) return defaultValue;
            org.neo4j.driver.Value value = record.get(key);
            if (value == null || value.isNull()) return defaultValue;
            return value.asString(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private Integer safeGetInt(org.neo4j.driver.Record record, String key, Integer defaultValue) {
        try {
            if (record == null || !record.containsKey(key)) return defaultValue;
            org.neo4j.driver.Value value = record.get(key);
            if (value == null || value.isNull()) return defaultValue;
            return value.asInt(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private Double safeGetDouble(org.neo4j.driver.Record record, String key, Double defaultValue) {
        try {
            if (record == null || !record.containsKey(key)) return defaultValue;
            org.neo4j.driver.Value value = record.get(key);
            if (value == null || value.isNull()) return defaultValue;
            return value.asDouble(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 安全获取整数值
     */
    private Integer safeGetInt(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 安全获取双精度值
     */
    private Double safeGetDouble(Map<String, Object> map, String key, Double defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

