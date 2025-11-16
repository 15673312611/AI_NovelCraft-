# 图谱元数据管理 API

## 概述

这些接口用于管理小说的图谱数据和章节概要，支持清空、重新生成和查询操作。

## 接口列表

### 1. 清空概要和图谱数据（保留章节内容）

**接口**: `DELETE /agentic/graph/clear-metadata/{novelId}`

**描述**: 清空指定小说的所有章节概要和图谱数据，但保留章节正文内容。适用于需要重新生成元数据的场景。

**请求参数**:
- `novelId` (路径参数): 小说ID

**响应示例**:
```json
{
  "status": "success",
  "message": "图谱和概要已清空，章节内容已保留"
}
```

**使用场景**:
- 小说已经写了很多章，但图谱数据不准确或缺失
- 需要使用新的提示词重新抽取实体和关系
- 调试图谱生成逻辑

---

### 2. 重新生成指定章节范围的概要和图谱

**接口**: `POST /agentic/graph/regenerate-metadata`

**描述**: 对指定范围的章节重新生成概要和图谱数据。会依次处理每一章，生成概要并抽取实体入图。

**请求体**:
```json
{
  "novelId": 1,
  "startChapter": 1,
  "endChapter": 10,
  "provider": "openai",
  "apiKey": "your-api-key",
  "model": "gpt-4",
  "baseUrl": "https://api.openai.com/v1"
}
```

**参数说明**:
- `novelId` (必填): 小说ID
- `startChapter` (可选): 起始章节号，默认为1
- `endChapter` (可选): 结束章节号，不填则处理从startChapter开始的所有章节
- `provider`, `apiKey`, `model`, `baseUrl`: AI配置（必填）

**响应示例**:
```json
{
  "status": "success",
  "message": "元数据重新生成完成",
  "totalChapters": 10,
  "successCount": 9,
  "failCount": 1
}
```

**注意事项**:
- 每章处理间隔2秒，避免API限流
- 处理失败的章节会记录日志但不中断整体流程
- 建议在低峰期执行，避免影响正常写作

**使用场景**:
- 清空元数据后，重新生成
- 修改了实体抽取提示词，需要重新抽取
- 部分章节的图谱数据丢失或损坏

---

### 3. 查询小说的所有图谱数据

**接口**: `GET /agentic/graph/data/{novelId}`

**描述**: 获取指定小说的完整图谱数据，包括事件、伏笔、情节线、世界规则、冲突弧线、角色成长、因果关系和角色关系。

**请求参数**:
- `novelId` (路径参数): 小说ID

**响应示例**:
```json
{
  "status": "success",
  "data": {
    "events": [
      {
        "id": "event_1_1",
        "novelId": 1,
        "chapterNumber": 1,
        "summary": "主角林默发现自己的身体异常",
        "description": "...",
        "location": "宿舍",
        "participants": ["林默", "室友"],
        "emotionalTone": "tense",
        "importance": 0.8
      }
    ],
    "foreshadows": [...],
    "plotlines": [...],
    "worldRules": [...],
    "conflictArcs": [...],
    "characterArcs": [...],
    "causalRelations": [
      {
        "from": "主角林默发现自己的身体异常",
        "to": "林默开始调查诡异事件",
        "description": "身体异常促使主角主动调查"
      }
    ],
    "characterRelations": [
      {
        "from": "林默",
        "to": "室友",
        "type": "朋友",
        "strength": 0.7,
        "description": "..."
      }
    ],
    "totalEvents": 45,
    "totalForeshadows": 12,
    "totalPlotlines": 5,
    "totalWorldRules": 8,
    "totalConflictArcs": 3,
    "totalCharacterArcs": 4,
    "totalCausalRelations": 23,
    "totalCharacterRelations": 15
  }
}
```

**使用场景**:
- 查看小说的完整知识图谱
- 分析故事结构和因果关系
- 调试图谱数据是否正确
- 导出图谱数据用于其他分析工具

---

## 典型工作流

### 场景1: 已有小说，重新生成图谱

```bash
# 1. 清空现有的概要和图谱数据
curl -X DELETE http://localhost:8080/agentic/graph/clear-metadata/1

# 2. 重新生成前10章的元数据
curl -X POST http://localhost:8080/agentic/graph/regenerate-metadata \
  -H "Content-Type: application/json" \
  -d '{
    "novelId": 1,
    "startChapter": 1,
    "endChapter": 10,
    "provider": "openai",
    "apiKey": "sk-xxx",
    "model": "gpt-4",
    "baseUrl": "https://api.openai.com/v1"
  }'

# 3. 查看生成的图谱数据
curl http://localhost:8080/agentic/graph/data/1
```

### 场景2: 只重新生成部分章节

```bash
# 只重新生成第5-8章的元数据（不清空其他章节）
curl -X POST http://localhost:8080/agentic/graph/regenerate-metadata \
  -H "Content-Type: application/json" \
  -d '{
    "novelId": 1,
    "startChapter": 5,
    "endChapter": 8,
    "provider": "openai",
    "apiKey": "sk-xxx",
    "model": "gpt-4"
  }'
```

---

## 前端集成

前端已在小说列表页添加"图谱"按钮，点击后弹出模态框展示图谱数据。

**组件位置**: `frontend/src/components/graph/GraphDataModal.tsx`

**使用方式**:
```tsx
<GraphDataModal
  visible={graphModalVisible}
  novelId={novelId}
  novelTitle={novelTitle}
  onClose={() => setGraphModalVisible(false)}
/>
```

**展示内容**:
- 事件列表（按章节排序）
- 伏笔列表
- 情节线列表
- 世界规则列表
- 冲突弧线列表
- 角色成长列表
- 因果关系列表
- 角色关系列表

每个标签页都显示对应的数据表格，支持分页和排序。

---

## 注意事项

1. **API限流**: 重新生成元数据时，每章之间有2秒延迟，避免触发API限流
2. **AI配置**: 必须提供有效的AI配置（provider, apiKey, model），否则会返回错误
3. **数据一致性**: 清空元数据不会影响章节正文，但会删除所有图谱节点和关系
4. **性能考虑**: 对于大量章节（如100+章），重新生成可能需要较长时间，建议分批处理
5. **错误处理**: 如果某章处理失败，不会中断整体流程，会继续处理下一章

---

## 错误码

| 错误信息 | 原因 | 解决方法 |
|---------|------|---------|
| 图谱服务未启用 | Neo4j未配置或连接失败 | 检查Neo4j配置和连接 |
| 实体抽取服务未启用 | EntityExtractionService未初始化 | 检查后端服务启动日志 |
| AI配置无效 | 缺少必要的AI配置参数 | 确保提供完整的provider、apiKey、model |
| 未找到指定范围的章节 | 章节号不存在 | 检查章节范围是否正确 |

---

## 相关文件

**后端**:
- `GraphManagementController.java` - 图谱管理接口
- `Neo4jGraphService.java` - Neo4j图谱服务实现
- `InMemoryGraphService.java` - 内存图谱服务实现（降级方案）
- `EntityExtractionService.java` - 实体抽取服务
- `ChapterSummaryService.java` - 章节概要服务

**前端**:
- `GraphDataModal.tsx` - 图谱数据展示组件
- `NovelListPage.new.tsx` - 小说列表页（包含图谱按钮）

