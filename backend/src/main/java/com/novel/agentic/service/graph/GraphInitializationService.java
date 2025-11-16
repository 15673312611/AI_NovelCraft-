package com.novel.agentic.service.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 图数据库初始化服务
 * 
 * 应用启动时自动创建索引和约束
 */
@Service
@ConditionalOnBean(Driver.class)
public class GraphInitializationService {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphInitializationService.class);
    
    @Autowired
    private Driver driver;
    
    @EventListener(ApplicationReadyEvent.class)
    public void initializeGraph() {
        logger.info("🔧 开始初始化Neo4j图数据库...");
        
        try (Session session = driver.session()) {
            // 1. 创建约束（保证数据唯一性）
            createConstraints(session);
            
            // 2. 创建索引（优化查询性能）
            createIndexes(session);
            
            logger.info("✅ Neo4j图数据库初始化完成");
        } catch (Exception e) {
            logger.error("❌ Neo4j初始化失败", e);
        }
    }
    
    private void createConstraints(Session session) {
        logger.info("📌 创建唯一性约束...");
        
        String[] constraints = {
            // 章节约束
            "CREATE CONSTRAINT chapter_unique IF NOT EXISTS FOR (c:Chapter) REQUIRE (c.novelId, c.number) IS UNIQUE",
            
            // 事件约束
            "CREATE CONSTRAINT event_unique IF NOT EXISTS FOR (e:Event) REQUIRE e.id IS UNIQUE",
            
            // 伏笔约束
            "CREATE CONSTRAINT foreshadow_unique IF NOT EXISTS FOR (f:Foreshadowing) REQUIRE f.id IS UNIQUE",
            
            // 情节线约束
            "CREATE CONSTRAINT plotline_unique IF NOT EXISTS FOR (p:PlotLine) REQUIRE p.id IS UNIQUE",
            
            // 世界规则约束
            "CREATE CONSTRAINT worldrule_unique IF NOT EXISTS FOR (r:WorldRule) REQUIRE r.id IS UNIQUE"
        };
        
        for (String constraint : constraints) {
            try {
                session.run(constraint);
                logger.info("✓ {}", constraint.split(" FOR ")[1].split(" REQUIRE")[0]);
            } catch (Exception e) {
                // 约束可能已存在，忽略错误
                logger.debug("约束可能已存在: {}", e.getMessage());
            }
        }
    }
    
    private void createIndexes(Session session) {
        logger.info("📌 创建索引...");
        
        String[] indexes = {
            // 小说ID索引（用于按小说查询）
            "CREATE INDEX novel_id_event IF NOT EXISTS FOR (e:Event) ON (e.novelId)",
            "CREATE INDEX novel_id_foreshadow IF NOT EXISTS FOR (f:Foreshadowing) ON (f.novelId)",
            "CREATE INDEX novel_id_plotline IF NOT EXISTS FOR (p:PlotLine) ON (p.novelId)",
            "CREATE INDEX novel_id_worldrule IF NOT EXISTS FOR (r:WorldRule) ON (r.novelId)",
            "CREATE INDEX novel_id_chapter IF NOT EXISTS FOR (c:Chapter) ON (c.novelId)",
            
            // 章节号索引（用于时间范围查询）
            "CREATE INDEX chapter_number_event IF NOT EXISTS FOR (e:Event) ON (e.chapterNumber)",
            "CREATE INDEX chapter_number IF NOT EXISTS FOR (c:Chapter) ON (c.number)",
            
            // 状态索引（用于查询未回收伏笔）
            "CREATE INDEX foreshadow_status IF NOT EXISTS FOR (f:Foreshadowing) ON (f.status)",
            
            // 重要性索引（用于排序）
            "CREATE INDEX event_importance IF NOT EXISTS FOR (e:Event) ON (e.importance)",
            "CREATE INDEX foreshadow_importance IF NOT EXISTS FOR (f:Foreshadowing) ON (f.importance)",
            
            // 优先级索引
            "CREATE INDEX plotline_priority IF NOT EXISTS FOR (p:PlotLine) ON (p.priority)"
        };
        
        for (String index : indexes) {
            try {
                session.run(index);
                logger.info("✓ {}", index.split(" FOR ")[1].split(" ON")[0]);
            } catch (Exception e) {
                // 索引可能已存在，忽略错误
                logger.debug("索引可能已存在: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 清空图数据库（谨慎使用！）
     */
    public void clearGraph(Long novelId) {
        logger.warn("⚠️ 清空小说{}的图谱数据", novelId);
        
        String cypher = 
            "MATCH (n) " +
            "WHERE n.novelId = $novelId " +
            "DETACH DELETE n";
        
        try (Session session = driver.session()) {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("novelId", novelId);
            session.run(cypher, params);
            logger.info("✅ 已清空小说{}的图谱", novelId);
        } catch (Exception e) {
            logger.error("清空失败", e);
        }
    }
    
    /**
     * 获取图谱统计信息
     */
    public java.util.Map<String, Object> getGraphStats(Long novelId) {
        String cypher = 
            "MATCH (n) " +
            "WHERE n.novelId = $novelId " +
            "WITH labels(n)[0] AS type, count(n) AS count " +
            "RETURN type, count " +
            "ORDER BY count DESC";
        
        try (Session session = driver.session()) {
            java.util.Map<String, Long> stats = new java.util.HashMap<>();
            
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("novelId", novelId);
            session.run(cypher, params)
                .list(record -> {
                    String type = record.get("type").asString();
                    Long count = record.get("count").asLong();
                    stats.put(type, count);
                    return null;
                });
            
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("novelId", novelId);
            result.put("stats", stats);
            result.put("total", stats.values().stream().mapToLong(Long::longValue).sum());
            return result;
        } catch (Exception e) {
            logger.error("获取统计失败", e);
            java.util.Map<String, Object> errorMap = new java.util.HashMap<>();
            errorMap.put("error", e.getMessage());
            return errorMap;
        }
    }
}

