# Neo4j 图数据库配置指南

## 📌 说明

当前系统使用**内存模拟**的图数据库服务（`GraphDatabaseService`），返回示例数据用于测试ReAct流程。

如需启用真实的Neo4j图数据库，请按以下步骤操作：

## 🐳 方式1：Docker Compose（推荐）

### 1. 在项目根目录的 `docker-compose.yml` 中添加：

```yaml
services:
  neo4j:
    image: neo4j:5.13-community
    container_name: novel-neo4j
    ports:
      - "7474:7474"  # HTTP
      - "7687:7687"  # Bolt
    environment:
      - NEO4J_AUTH=neo4j/your_password
      - NEO4J_PLUGINS=["apoc"]
      - NEO4J_dbms_security_procedures_unrestricted=apoc.*
    volumes:
      - neo4j_data:/data
      - neo4j_logs:/logs
    networks:
      - novel-network

volumes:
  neo4j_data:
  neo4j_logs:
```

### 2. 启动Neo4j

```bash
docker-compose up -d neo4j
```

### 3. 访问Neo4j浏览器

打开 http://localhost:7474

- 用户名: `neo4j`
- 密码: `your_password`

## 📦 方式2：添加Maven依赖

在 `backend/pom.xml` 中添加：

```xml
<!-- Neo4j Driver -->
<dependency>
    <groupId>org.neo4j.driver</groupId>
    <artifactId>neo4j-java-driver</artifactId>
    <version>5.13.0</version>
</dependency>

<!-- Spring Data Neo4j (可选，简化开发) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-neo4j</artifactId>
</dependency>
```

## ⚙️ 配置连接

### 在 `application.yml` 中添加：

```yaml
spring:
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: your_password
```

## 🔧 实现真实的Neo4j服务

### 1. 创建 `Neo4jGraphDatabaseService.java`

```java
@Service
@ConditionalOnProperty(name = "graph.database.enabled", havingValue = "true")
public class Neo4jGraphDatabaseService extends GraphDatabaseService {
    
    @Autowired
    private Driver driver;
    
    @Override
    public List<GraphEntity> getRelevantEvents(Long novelId, Integer chapterNumber, Integer limit) {
        try (Session session = driver.session()) {
            String cypher = """
                MATCH (e:Event {novelId: $novelId})
                WHERE e.chapterNumber < $chapterNumber
                WITH e, 
                     1.0 / ($chapterNumber - e.chapterNumber) AS proximityScore,
                     e.importance * 0.5 AS importanceScore
                ORDER BY (proximityScore + importanceScore) DESC
                LIMIT $limit
                RETURN e
            """;
            
            return session.run(cypher, Map.of(
                "novelId", novelId,
                "chapterNumber", chapterNumber,
                "limit", limit
            ))
            .list(record -> mapToGraphEntity(record.get("e")));
        }
    }
    
    // 其他方法类似实现...
}
```

### 2. 配置开关

在 `application.yml` 中：

```yaml
graph:
  database:
    enabled: false  # 改为 true 启用Neo4j
```

## 📊 初始化图谱数据

### 创建索引（必须）

```cypher
// 小说ID索引
CREATE INDEX novel_id_idx FOR (n:Event) ON (n.novelId);
CREATE INDEX novel_id_idx2 FOR (n:Foreshadow) ON (n.novelId);
CREATE INDEX novel_id_idx3 FOR (n:Plotline) ON (n.novelId);
CREATE INDEX novel_id_idx4 FOR (n:WorldRule) ON (n.novelId);

// 章节号索引
CREATE INDEX chapter_idx FOR (n:Event) ON (n.chapterNumber);
```

### 示例数据导入

```cypher
// 创建事件节点
CREATE (e:Event {
  id: 'event_1_5',
  novelId: 1,
  chapterNumber: 5,
  description: '主角与反派初次交锋',
  participants: ['主角', '反派'],
  impact: '推动主线发展',
  importance: 0.9
});

// 创建伏笔节点
CREATE (f:Foreshadow {
  id: 'foreshadow_1_1',
  novelId: 1,
  chapterNumber: 3,
  description: '神秘老人的预言',
  plantedAt: '第3章',
  suggestedResolveWindow: '第10-15章',
  importance: 'high',
  resolved: false
});

// 创建关系
MATCH (e1:Event {id: 'event_1_5'}), (e2:Event {id: 'event_1_3'})
CREATE (e1)-[:CAUSED_BY]->(e2);
```

## 🧪 测试Neo4j连接

### 创建测试控制器

```java
@RestController
@RequestMapping("/api/neo4j")
public class Neo4jTestController {
    
    @Autowired
    private Driver driver;
    
    @GetMapping("/test")
    public Map<String, Object> testConnection() {
        try (Session session = driver.session()) {
            session.run("RETURN 1");
            return Map.of("status", "connected", "message", "Neo4j连接成功");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
```

访问: http://localhost:8080/api/neo4j/test

## 🔄 数据同步策略

### 章节生成后自动抽取实体

在 `AgenticChapterWriter.saveChapter()` 后添加：

```java
// 异步抽取实体并存入图谱
CompletableFuture.runAsync(() -> {
    try {
        entityExtractor.extractAndSave(novel.getId(), chapterNumber, content);
    } catch (Exception e) {
        logger.error("实体抽取失败", e);
    }
});
```

## ❗ 注意事项

1. **开发环境**: 可继续使用内存模拟版，无需Neo4j
2. **生产环境**: 建议启用Neo4j以支持长篇小说的复杂关系查询
3. **数据迁移**: 历史章节需要批量抽取实体并导入图谱
4. **性能优化**: 建立合适的索引，避免全表扫描

## 🚀 后续升级路径

1. **Phase 1**: 使用内存模拟版完成ReAct流程验证 ✅
2. **Phase 2**: 集成Neo4j，实现真实图谱查询
3. **Phase 3**: 实现章节落库时的自动实体抽取
4. **Phase 4**: 完善图谱治理（关系推理、一致性检查）

---

**当前状态**: 使用内存模拟版（`GraphDatabaseService`）  
**升级建议**: 等ReAct流程稳定后再切换Neo4j


