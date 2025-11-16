# 图数据库完整实施指南

## 🎯 现在已实现的完整功能

### ✅ 1. Neo4j Docker配置
- **文件**: `docker-compose.neo4j.yml`
- **包含**: 
  - Neo4j 5.13 Community Edition
  - APOC插件（高级查询）
  - 持久化存储
  - 健康检查

### ✅ 2. 实体建模与索引
- **文件**: `backend/src/main/resources/neo4j/init-graph-schema.cypher`
- **包含**:
  - 实体类型：Event, Foreshadowing, PlotLine, WorldRule, Character, Chapter
  - 关系类型：CONTAINS_EVENT, TRIGGERS, PLANTS, INCLUDES, APPLIES_TO
  - 唯一性约束
  - 性能索引
  - 示例数据

### ✅ 3. Neo4j服务实现
- **文件**: `backend/src/main/java/com/novel/agentic/service/graph/Neo4jGraphService.java`
- **功能**:
  - 相关事件查询（因果链+参与者+时间衰减）
  - 未回收伏笔查询（按重要性+年龄排序）
  - 情节线状态查询（久未推进检测）
  - 世界规则查询（按场景命中）
  - 实体入图（幂等性保证）

### ✅ 4. 实体自动抽取
- **文件**: `backend/src/main/java/com/novel/agentic/service/graph/EntityExtractionService.java`
- **流程**:
  1. 章节保存后异步触发
  2. AI分析内容抽取：事件、伏笔、情节线、规则
  3. 转换为GraphEntity
  4. 批量入图

### ✅ 5. 图谱初始化与管理
- **文件**: `backend/src/main/java/com/novel/agentic/service/graph/GraphInitializationService.java`
- **功能**:
  - 应用启动自动创建索引
  - 图谱统计查询
  - 清空图谱（按小说ID）

### ✅ 6. 管理接口
- **文件**: `backend/src/main/java/com/novel/agentic/controller/GraphManagementController.java`
- **接口**:
  - `GET /api/agentic/graph/stats/{novelId}` - 获取统计
  - `POST /api/agentic/graph/extract` - 手动抽取实体
  - `DELETE /api/agentic/graph/clear/{novelId}` - 清空图谱
  - `GET /api/agentic/graph/status` - 检查Neo4j状态

### ✅ 7. 配置文件
- **文件**: `backend/src/main/resources/application-neo4j.yml`
- **配置**:
  - 启用/禁用开关
  - 连接参数
  - 连接池配置

### ✅ 8. 集成到写作流程
- **文件**: `backend/src/main/java/com/novel/agentic/service/AgenticChapterWriter.java`
- **流程**:
  ```
  生成章节 → 保存文档 → 异步抽取实体 → 入图
  ```

---

## 🚀 快速启动指南

### 步骤1：启动Neo4j

```bash
# 启动Neo4j容器
docker-compose -f docker-compose.neo4j.yml up -d

# 检查状态
docker-compose -f docker-compose.neo4j.yml ps

# 查看日志
docker-compose -f docker-compose.neo4j.yml logs -f neo4j
```

### 步骤2：访问Neo4j浏览器

打开浏览器访问: http://localhost:7474

- **用户名**: `neo4j`
- **密码**: `novel_graph_2025`

### 步骤3：初始化图谱结构

复制 `backend/src/main/resources/neo4j/init-graph-schema.cypher` 中的Cypher语句，在Neo4j浏览器中执行。

或者在终端执行：

```bash
# 进入容器
docker exec -it novel-neo4j cypher-shell -u neo4j -p novel_graph_2025

# 执行初始化脚本（复制粘贴init-graph-schema.cypher内容）
```

### 步骤4：配置Maven依赖

**已自动添加到 `pom.xml`**:

```xml
<dependency>
    <groupId>org.neo4j.driver</groupId>
    <artifactId>neo4j-java-driver</artifactId>
    <version>5.13.0</version>
</dependency>
```

### 步骤5：启用Neo4j配置

**方式1**: 使用配置文件启动

```bash
java -jar target/novel-creation-system-1.0.0.jar --spring.profiles.active=neo4j
```

**方式2**: 在 `application.yml` 中添加：

```yaml
graph:
  neo4j:
    enabled: true
    uri: bolt://localhost:7687
    username: neo4j
    password: novel_graph_2025
```

### 步骤6：测试图谱连接

```bash
curl http://localhost:8080/api/agentic/graph/status
```

预期响应：
```json
{
  "neo4jEnabled": true,
  "extractionEnabled": true,
  "mode": "Neo4j"
}
```

---

## 📊 使用示例

### 1. 生成章节并自动入图

```bash
curl -X POST http://localhost:8080/api/agentic/generate-chapters-stream \
  -H "Content-Type: application/json" \
  -d '{
    "novelId": 1,
    "startChapter": 1,
    "count": 1
  }'
```

**SSE流式响应**：
```
event: phase
data: 🧠 AI思考中...

event: phase
data: 📝 开始写作...

event: content
data: 第一章内容...

event: phase
data: 💾 保存中...

event: phase
data: 🔬 抽取实体中...

event: extraction
data: ✅ 实体抽取完成

event: complete
data: ✅ 生成完成！共 3245 字
```

### 2. 查看图谱统计

```bash
curl http://localhost:8080/api/agentic/graph/stats/1
```

响应：
```json
{
  "novelId": 1,
  "stats": {
    "Event": 15,
    "Foreshadowing": 3,
    "PlotLine": 2,
    "WorldRule": 5,
    "Character": 8
  },
  "total": 33
}
```

### 3. 手动抽取章节实体

```bash
curl -X POST http://localhost:8080/api/agentic/graph/extract \
  -H "Content-Type: application/json" \
  -d '{
    "novelId": 1,
    "chapterNumber": 2,
    "chapterTitle": "第二章",
    "content": "章节内容..."
  }'
```

### 4. 在Neo4j浏览器中查询

**查询所有事件**:
```cypher
MATCH (e:Event {novelId: 1})
RETURN e
ORDER BY e.chapterNumber
LIMIT 20
```

**查询事件因果链**:
```cypher
MATCH path = (e1:Event)-[:TRIGGERS*1..3]->(e2:Event)
WHERE e1.novelId = 1
RETURN path
LIMIT 10
```

**查询未回收伏笔**:
```cypher
MATCH (f:Foreshadowing {novelId: 1, status: 'PLANTED'})
RETURN f.content, f.importance
ORDER BY f.importance DESC
```

**查询情节线及其事件**:
```cypher
MATCH (p:PlotLine {novelId: 1})-[:INCLUDES]->(e:Event)
RETURN p.name, collect(e.summary) AS events
```

---

## 🔧 配置说明

### 内存模拟 vs Neo4j真实版

| 模式 | 配置 | 适用场景 |
|------|------|---------|
| **内存模拟** | `graph.neo4j.enabled: false` | 开发、测试、快速验证 |
| **Neo4j真实版** | `graph.neo4j.enabled: true` | 生产、长篇小说、复杂关系 |

### 自动降级策略

如果Neo4j连接失败，系统会自动降级到内存模拟版，不影响写作功能。

```java
try {
    // Neo4j查询
} catch (Exception e) {
    logger.error("Neo4j查询失败，降级到内存版", e);
    return super.getRelevantEvents(...); // 调用内存版
}
```

---

## 📈 查询策略详解

### 1. 相关事件查询

**目标**: 找到与当前章节强相关的历史事件

**排序权重**:
- 时间衰减：`1.0 / (currentChapter - eventChapter + 1)`
- 关系深度：`关系数量 × 10`
- 重要性：`event.importance × 20`

**Cypher**:
```cypher
MATCH (eNow:Event)-[:INVOLVES|TRIGGERS|RELATES_TO*1..3]-(eRel:Event)
WHERE eRel.chapterNumber < $chapter
WITH eRel, (proximityScore + relationScore + importanceScore) AS totalScore
ORDER BY totalScore DESC
LIMIT 8
```

### 2. 伏笔回收查询

**目标**: 找到该回收但尚未回收的伏笔

**排序权重**:
- 重要性：high > medium > low
- 年龄：越久远越优先

**Cypher**:
```cypher
MATCH (f:Foreshadowing)-[:PLANTED_IN]->(c:Chapter)
WHERE f.status = 'PLANTED'
  AND c.number < $chapter
  AND (f.plannedRevealChapter IS NULL OR f.plannedRevealChapter <= $chapter + 10)
ORDER BY importance DESC, age DESC
LIMIT 6
```

### 3. 情节线久未推进检测

**目标**: 找到超过5章未推进的情节线

**Cypher**:
```cypher
MATCH (p:PlotLine)-[:INCLUDES]->(e:Event)<-[:CONTAINS_EVENT]-(c:Chapter)
WITH p, max(c.number) AS lastTouched
WHERE $chapter - lastTouched > 5
ORDER BY priority DESC, ($chapter - lastTouched) DESC
LIMIT 3
```

---

## 🛠️ 维护操作

### 清空图谱数据

```bash
# API方式
curl -X DELETE http://localhost:8080/api/agentic/graph/clear/1

# Cypher方式
MATCH (n {novelId: 1}) DETACH DELETE n
```

### 备份图谱

```bash
# 导出为Cypher脚本
docker exec novel-neo4j cypher-shell -u neo4j -p novel_graph_2025 \
  "MATCH (n {novelId: 1}) RETURN n" > backup.cypher
```

### 重建索引

```cypher
// 删除所有索引
CALL apoc.schema.assert({}, {});

// 重新运行初始化脚本
// ... (复制init-graph-schema.cypher内容)
```

---

## ⚠️ 注意事项

### 1. 性能优化

- **索引必须创建**: 否则大数据量查询会非常慢
- **限制查询深度**: 关系查询深度建议≤3
- **控制返回数量**: LIMIT必须设置

### 2. 数据一致性

- **幂等性**: 实体ID唯一，重复插入会覆盖
- **关系清理**: 删除实体前需先删除关系（DETACH DELETE）

### 3. 成本控制

- **AI抽取成本**: 每章抽取消耗1次AI调用
- **建议**: 只对重要章节抽取，或批量抽取降低成本

---

## 🎨 可视化建议

### 使用Neo4j Bloom（企业版）

- 图谱可视化
- 交互式探索
- 关系发现

### 使用Cytoscape.js（开源）

- 前端集成
- 自定义样式
- 实时更新

---

## 📚 后续优化方向

### 短期
- [ ] 添加事件因果关系自动推理
- [ ] 伏笔回收建议生成
- [ ] 角色关系网络可视化

### 中期
- [ ] 题材画像差异化（玄幻/都市/仙侠）
- [ ] 支线膨胀检测与警告
- [ ] 主题偏离自动校验

### 长期
- [ ] 多小说知识图谱共享
- [ ] AI写作风格学习
- [ ] 读者反馈关联分析

---

**当前状态**: ✅ 完整图数据库方案已实现  
**测试模式**: 内存模拟版（无需Neo4j）  
**生产模式**: 启动Neo4j + 配置启用即可

Happy Writing! 📖✨


