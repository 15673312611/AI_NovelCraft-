# 图谱系统诊断与修复报告

## 问题汇总

### 1. ✅ 地点连贯性问题（已修复）
**问题描述**：小说写到主角进山，两章后又回到镇上，缺乏位置跟踪。

**根本原因**：
- 实体抽取提示词中，`events` 缺少 `location` 字段
- `StructuredMessageBuilder` 展示事件时未显示地点信息

**修复方案**：
1. 在 `EntityExtractionService.buildExtractionPrompt` 中为 events 添加 `location` 必填字段
2. 在 `StructuredMessageBuilder.buildGraphContext` 中展示地点信息（📍地点标记）

**修复代码位置**：
- `backend/src/main/java/com/novel/agentic/service/graph/EntityExtractionService.java:121`
- `backend/src/main/java/com/novel/agentic/service/StructuredMessageBuilder.java:329-332`

---

### 2. ✅ 章节概要上下文验证（已确认正常）
**问题描述**：用户怀疑章节概要未添加到上下文。

**验证结果**：
- `AgentOrchestrator.prefetchCoreContext` 会在 ReAct 循环前预取 `getRecentChapters`
- `GetRecentChaptersTool` 正确调用 `ChapterSummaryService.getRecentChapterSummaries`
- `storeToolResult` 正确存储 `recentSummaries` 到 `WritingContext`
- `StructuredMessageBuilder.buildRecentChapters` 正确使用 `context.getRecentSummaries()`

**结论**：章节概要功能正常，如果用户发现概要为空，可能原因：
1. 概要生成是异步的，还在进行中
2. AI 生成概要失败（检查日志中 `ChapterSummaryService` 的错误）
3. 前几章没有历史概要（正常现象）

---

### 3. 🔍 图谱数据为空的可能原因

**症状**：生成十几章后，图谱数据仍然为空。

**可能原因分析**：

#### A. 实体抽取异步延迟
- 实体抽取是异步的（`CompletableFuture.runAsync`）
- 如果连续快速生成章节，前一章的抽取可能还没完成
- **解决方案**：批量生成时，在每章之间加2秒延迟（已实现）

#### B. 实体抽取失败
**检查点**：
```bash
# 查看日志中是否有以下错误
grep "实体抽取失败" logs/novel-creation-system.log
grep "解析AI返回失败" logs/novel-creation-system.log
```

**常见失败原因**：
1. AI 返回的 JSON 格式不正确
2. AI 超时或拒绝服务
3. AI 配置无效（`aiConfig.isValid()` 返回 false）

**修复建议**：
- 检查 `EntityExtractionService.parseExtractedEntities` 的解析逻辑
- 如果 AI 返回被截断，已有容错逻辑（返回空结果）

#### C. Neo4j 连接问题
**检查点**：
```bash
# 检查 Neo4j 是否运行
docker ps | grep neo4j

# 检查连接日志
grep "Neo4j" logs/novel-creation-system.log
```

**修复建议**：
1. 如果 Neo4j 未启动：`docker-compose up -d neo4j`
2. 如果连接失败，系统会降级到 `InMemoryGraphService`（内存模式）

#### D. 图谱查询逻辑问题
**检查点**：
- `Neo4jGraphService.getRelevantEvents` 的 Cypher 查询是否正确
- 查询是否返回空结果

**调试方法**：
```java
// 在 AgenticChapterWriter.checkGraphHealth 中已有健康检查日志
// 查看日志中的图谱健康检查输出
```

---

### 4. ✅ 因果关系和人物关键节点（已实现）

**当前实现状态**：
- ✅ 抽取提示词包含 `causalRelations` 和 `characterRelations`
- ✅ `EntityExtractionService` 调用 `addCausalRelations` 和 `addCharacterRelations`
- ✅ `StructuredMessageBuilder` 展示因果关系（⬅️ 前因、➡️ 后果）

**如何验证**：
1. 查看日志中是否有 "✅ 添加了X个因果关系"
2. 查看日志中是否有 "✅ 添加了X个角色关系"
3. 在 `generation_context` 字段中检查图谱数据

---

## 诊断流程

### 步骤1：检查章节是否保存
```sql
SELECT id, chapter_number, title, LENGTH(content) as content_length, LENGTH(generation_context) as context_length
FROM chapters
WHERE novel_id = <YOUR_NOVEL_ID>
ORDER BY chapter_number DESC
LIMIT 10;
```

### 步骤2：检查概要是否生成
```sql
SELECT novel_id, chapter_number, LENGTH(summary) as summary_length
FROM chapter_summaries
WHERE novel_id = <YOUR_NOVEL_ID>
ORDER BY chapter_number DESC
LIMIT 10;
```

### 步骤3：检查图谱数据（Neo4j）
```cypher
// 查看小说的所有事件
MATCH (c:Chapter {novelId: <YOUR_NOVEL_ID>})-[:CONTAINS_EVENT]->(e:Event)
RETURN c.number, e.summary, e.location, e.participants
ORDER BY c.number DESC
LIMIT 20;

// 查看因果关系
MATCH (e1:Event)-[r:CAUSES]->(e2:Event)
WHERE e1.novelId = <YOUR_NOVEL_ID>
RETURN e1.summary, e2.summary, r.description
LIMIT 20;
```

### 步骤4：检查 generation_context
```sql
-- 查看最近一章的完整上下文
SELECT generation_context
FROM chapters
WHERE novel_id = <YOUR_NOVEL_ID>
ORDER BY chapter_number DESC
LIMIT 1;
```

解析 JSON 并查看：
- `writingContext.recentSummaries` - 章节概要
- `writingContext.relevantEvents` - 图谱事件
- `writingContext.unresolvedForeshadows` - 待回收伏笔

---

## 优化建议

### 1. 实体抽取重试机制（已实现）
- 使用 `EntityExtractionRetryService` 记录失败任务
- 后续可手动或自动重试

### 2. 图谱健康检查（已实现）
- `AgenticChapterWriter.checkGraphHealth` 在生成前检查图谱状态
- 第5章后如果图谱为空会输出警告

### 3. 章节概要降级方案（已实现）
- 如果 AI 生成概要失败，使用 `generateFallbackSummary`
- 取章节内容前200字作为简化摘要

---

## 用户反馈的具体问题

### "为啥几章都是空的"
**可能原因**：
1. 前几章（1-5章）图谱数据为空是正常的，因为需要累积
2. 如果第10章后还是空的，检查：
   - 实体抽取是否失败（查日志）
   - Neo4j 是否运行
   - AI 配置是否有效

### "小说都记录进山了，结果写两章又变镇上了"
**已修复**：
- 添加了 location 字段到事件抽取
- 在图谱上下文中显示地点信息（📍地点）
- AI 现在可以看到历史事件的地点，保持位置连贯

### "人物的经历的关键节点、因果关系没弄好"
**已实现但需要验证**：
- 因果关系抽取（`causalRelations`）
- 角色关系抽取（`characterRelations`）
- 如果仍有问题，可能是 AI 在抽取时漏掉了这些信息

**改进方向**：
- 加强抽取提示词，明确要求抽取关键节点
- 增加角色成长弧线（`characterArcs`）的权重
- 在上下文中更明显地展示因果链

---

## 下一步行动

1. **验证修复**：重新生成几章，查看 generation_context，确认 location 字段是否存在
2. **检查日志**：查找实体抽取失败的具体原因
3. **Neo4j 连接**：确保 Neo4j 正常运行并连接
4. **增强抽取**：如果因果关系仍不足，考虑在抽取提示词中加强要求

---

## 修改文件清单

1. ✅ `backend/src/main/java/com/novel/agentic/service/graph/EntityExtractionService.java`
   - 添加 location 字段到 events
   - 移除未使用的 relationId 变量

2. ✅ `backend/src/main/java/com/novel/agentic/service/StructuredMessageBuilder.java`
   - 在图谱上下文中显示地点信息

3. ✅ `backend/src/main/java/com/novel/agentic/service/StructuredMessageBuilder.java`
   - 前三章添加小说简介到基础信息

---

## 总结

核心修复：
- ✅ 地点跟踪：添加 location 字段并在上下文中展示
- ✅ 章节概要：验证流程正常，已预取并添加到上下文
- ⚠️ 图谱为空：需要用户检查日志和 Neo4j 状态，系统已有容错机制

建议用户：
1. 查看后端日志，搜索 "实体抽取失败" 或 "Neo4j"
2. 检查 Neo4j 是否运行：`docker ps | grep neo4j`
3. 重新生成几章，观察 `generation_context` 中是否有图谱数据
4. 如果问题持续，提供日志片段以便进一步诊断

