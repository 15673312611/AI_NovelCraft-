# 🚀 代理式AI写作系统 - 5分钟快速启动

## 📋 前置条件

- ✅ Java 8+
- ✅ Maven
- ✅ MySQL数据库（已有小说数据）
- ⚠️ Docker（仅Neo4j模式需要）

---

## 🎯 方案选择

### 方案A：内存模拟版（最快，推荐新手）

**优点**:
- 无需Docker
- 无需Neo4j
- 立即可用
- 快速验证流程

**适用**: 开发、测试、快速体验

### 方案B：Neo4j真实版（完整功能）

**优点**:
- 真实图谱查询
- 数据持久化
- 完整功能展示

**适用**: 生产环境、长篇小说

---

## 🏃 方案A：内存模拟版（5分钟）

### 步骤1：编译项目

```bash
cd backend
mvn clean package -DskipTests
```

### 步骤2：启动应用

```bash
java -jar target/novel-creation-system-1.0.0.jar
```

### 步骤3：测试接口

```bash
# 检查状态
curl http://localhost:8080/api/agentic/status

# 预期响应
{
  "version": "1.0.0-agentic",
  "status": "running",
  "features": ["ReAct决策循环", "智能工具选择", "图谱上下文检索", "批量章节生成"]
}
```

### 步骤4：生成第一个章节

```bash
curl -X POST http://localhost:8080/api/agentic/generate-chapters-stream \
  -H "Content-Type: application/json" \
  -d '{
    "novelId": 1,
    "startChapter": 1,
    "count": 1
  }'
```

**注意**: 确保小说ID=1存在，且有大纲和卷蓝图。

### 步骤5：观察日志

```log
🧠 开始ReAct决策循环: novelId=1, chapter=1
📍 Step 1/8
💭 AI思考: 需要先获取大纲
🔧 执行工具: getOutline
✅ 工具执行成功
📍 Step 2/8
🔧 执行工具: getVolumeBlueprint
✅ 工具执行成功
📍 Step 3/8
✅ AI决定：信息充足，开始写作
🎉 ReAct决策循环完成
🎬 开始生成章节
✅ 章节生成完成: 第1章, 字数3245
```

✅ **完成！** 章节已保存到 `novel_document` 表。

---

## 🐳 方案B：Neo4j真实版（15分钟）

### 步骤1：启动Neo4j

```bash
# 在项目根目录
docker-compose -f docker-compose.neo4j.yml up -d

# 检查状态
docker-compose -f docker-compose.neo4j.yml ps
```

### 步骤2：初始化图谱

浏览器访问: http://localhost:7474

- 用户名: `neo4j`
- 密码: `novel_graph_2025`

复制并执行 `backend/src/main/resources/neo4j/init-graph-schema.cypher` 中的内容。

### 步骤3：编译项目（添加Neo4j依赖）

```bash
cd backend
mvn clean package -DskipTests
```

### 步骤4：启动应用（启用Neo4j）

```bash
java -jar target/novel-creation-system-1.0.0.jar --spring.profiles.active=neo4j
```

或在 `application.yml` 中添加：

```yaml
graph:
  neo4j:
    enabled: true
```

### 步骤5：验证Neo4j连接

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

### 步骤6：生成章节（自动抽取实体）

```bash
curl -X POST http://localhost:8080/api/agentic/generate-chapters-stream \
  -H "Content-Type: application/json" \
  -d '{
    "novelId": 1,
    "startChapter": 1,
    "count": 3
  }'
```

### 步骤7：查看图谱

```bash
# 查看统计
curl http://localhost:8080/api/agentic/graph/stats/1

# 或在Neo4j浏览器中
# http://localhost:7474
# 执行: MATCH (e:Event {novelId: 1}) RETURN e LIMIT 20
```

✅ **完成！** 章节已生成并自动入图。

---

## 🔧 故障排查

### 问题1：小说未找到

**错误**: `小说不存在: 1`

**解决**:
```sql
-- 检查小说是否存在
SELECT id, title, outline FROM novel WHERE id = 1;

-- 如果outline为空，需要先生成大纲
```

### 问题2：卷未找到

**错误**: `未找到对应的卷`

**解决**:
```sql
-- 检查卷设置
SELECT id, title, start_chapter, end_chapter, blueprint 
FROM novel_volume WHERE novel_id = 1;

-- 确保第1章在某个卷的范围内
```

### 问题3：Neo4j连接失败

**错误**: `Unable to connect to Neo4j`

**解决**:
```bash
# 检查Docker容器
docker ps | grep neo4j

# 查看日志
docker logs novel-neo4j

# 重启容器
docker-compose -f docker-compose.neo4j.yml restart
```

### 问题4：AI配置问题

**错误**: `API key missing`

**解决**:
在请求体中添加AI配置：
```json
{
  "novelId": 1,
  "startChapter": 1,
  "count": 1,
  "aiConfig": {
    "provider": "openai",
    "model": "gpt-4",
    "apiKey": "your-api-key"
  }
}
```

---

## 📊 常用查询

### SQL查询（MySQL）

```sql
-- 查看生成的章节
SELECT id, title, LENGTH(content) AS word_count, created_at 
FROM novel_document 
WHERE novel_id = 1 
ORDER BY id DESC 
LIMIT 10;

-- 查看小说详情
SELECT * FROM novel WHERE id = 1;

-- 查看卷设置
SELECT * FROM novel_volume WHERE novel_id = 1;
```

### Cypher查询（Neo4j）

```cypher
-- 查看所有事件
MATCH (e:Event {novelId: 1})
RETURN e
ORDER BY e.chapterNumber
LIMIT 20;

-- 查看事件因果链
MATCH path = (e1:Event)-[:TRIGGERS*1..3]->(e2:Event)
WHERE e1.novelId = 1
RETURN path
LIMIT 10;

-- 查看未回收伏笔
MATCH (f:Foreshadowing {novelId: 1, status: 'PLANTED'})
RETURN f.content, f.importance
ORDER BY f.importance DESC;

-- 查看图谱统计
MATCH (n {novelId: 1})
RETURN labels(n)[0] AS type, count(n) AS count
ORDER BY count DESC;
```

---

## 📚 下一步

1. **阅读完整文档**:
   - `backend/src/main/java/com/novel/agentic/README.md` - 使用指南
   - `backend/src/main/java/com/novel/agentic/GRAPH_DATABASE_GUIDE.md` - 图数据库指南
   - `backend/src/main/java/com/novel/agentic/COMPLETE_SUMMARY.md` - 完整总结

2. **批量生成测试**:
   ```bash
   # 生成3章
   curl -X POST http://localhost:8080/api/agentic/generate-chapters-stream \
     -H "Content-Type: application/json" \
     -d '{
       "novelId": 1,
       "startChapter": 1,
       "count": 3
     }'
   ```

3. **手动抽取实体**（已有章节）:
   ```bash
   curl -X POST http://localhost:8080/api/agentic/graph/extract \
     -H "Content-Type: application/json" \
     -d '{
       "novelId": 1,
       "chapterNumber": 1,
       "chapterTitle": "第一章",
       "content": "章节内容..."
     }'
   ```

4. **切换到生产模式**:
   - 启用Neo4j
   - 配置AI API Key
   - 调整上下文配额
   - 监控性能指标

---

## 🎉 成功标志

### ✅ 内存模拟版

- [ ] 接口返回200
- [ ] 日志显示ReAct决策过程
- [ ] 章节已保存到数据库
- [ ] SSE流式返回内容

### ✅ Neo4j真实版

- [ ] 以上所有
- [ ] Neo4j浏览器可访问
- [ ] 图谱统计API有数据
- [ ] Neo4j中可查询到实体

---

**需要帮助？** 查看完整文档或提issue！

Happy Writing! 📖✨


