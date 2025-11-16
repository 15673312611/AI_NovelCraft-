# 代理式AI小说创作系统 (Agentic Novel Writing System)

## 📖 概述

这是一个全新的代理式AI写作系统，采用 **ReAct (Reasoning + Acting)** 模式，让AI自己决定需要查询哪些数据、如何思考、如何构思。

与传统写作系统的区别：
- **传统方式**：机械地拼接"最近20章"等固定上下文
- **代理方式**：AI主动分析需求，智能选择相关信息（基于因果关系、角色参与等）

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────┐
│           AgenticWritingController              │  测试接口
│  POST /api/agentic/generate-chapters-stream     │
└─────────────────┬───────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────┐
│         AgenticChapterWriter                    │  章节生成服务
│  - 调用ReAct决策循环                             │
│  - 构建写作提示词                                │
│  - 流式生成内容                                  │
│  - 保存章节                                      │
└─────────────────┬───────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────┐
│         AgentOrchestrator                       │  ReAct编排器
│  - Step 1: THOUGHT (AI思考)                     │
│  - Step 2: ACTION (选择工具)                     │
│  - Step 3: OBSERVATION (观察结果)                │
│  - 循环直到信息充足                               │
│  - Step N: WRITE (开始写作)                      │
└─────────────────┬───────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────┐
│              ToolRegistry                       │  工具注册表
│  - getOutline (必查)                             │
│  - getVolumeBlueprint (必查)                     │
│  - getWorldRules (必查)                          │
│  - getRelevantEvents (可选)                      │
│  - getUnresolvedForeshadows (可选)               │
│  - getRecentChapters (可选)                      │
└─────────────────────────────────────────────────┘
```

## 🔧 核心组件

### 1. 数据模型 (`model/`)

- **AgentThought**: AI的思考记录（推理、动作、观察）
- **ToolDefinition**: 工具定义（名称、描述、参数schema）
- **WritingContext**: 写作上下文（包含所有收集的信息）
- **GraphEntity**: 图谱实体（事件、伏笔、角色、规则等）
- **ContextBudget**: 上下文配额（防止token溢出）

### 2. 工具系统 (`service/tools/`)

#### 必查工具（AI必须调用）
- **getOutline**: 获取小说完整大纲
- **getVolumeBlueprint**: 获取当前卷蓝图
- **getWorldRules**: 获取世界规则与设定

#### 可选工具（AI按需调用）
- **getRelevantEvents**: 从图谱检索相关历史事件（基于因果/参与者）
- **getUnresolvedForeshadows**: 查询未回收的伏笔
- **getRecentChapters**: 获取最近章节（完整内容或摘要）

### 3. ReAct编排器 (`service/orchestrator/`)

**AgentOrchestrator** - 核心决策循环：

```java
for (step = 1; step <= MAX_STEPS; step++) {
    // 1. THOUGHT: AI思考需要什么信息
    String prompt = buildThinkingPrompt(...);
    String aiResponse = callAI(prompt);
    
    // 2. ACTION: 解析AI决策
    AgentDecision decision = parseDecision(aiResponse);
    
    if (decision.action == "WRITE") {
        break; // 信息充足，开始写作
    }
    
    // 3. OBSERVATION: 执行工具并观察结果
    Object result = toolRegistry.executeTool(decision.action, args);
    storeResult(result, context);
}
```

### 4. 图数据库服务 (`service/graph/`)

**GraphDatabaseService** - 智能上下文检索：

- 当前为**内存模拟版**，返回示例数据
- 后续可替换为真实的 **Neo4j** 实现
- 支持的查询：
  - 相关事件（按因果关系、参与者排序）
  - 未回收伏笔（含建议回收窗口）
  - 情节线状态（久未推进的线）
  - 世界规则（场景相关的设定约束）

## 🚀 使用方法

### API接口

#### 生成章节（流式）

```http
POST /api/agentic/generate-chapters-stream
Content-Type: application/json

{
  "novelId": 1,
  "startChapter": 1,
  "count": 3,
  "userAdjustment": "强化主角与反派的对抗",
  "aiConfig": {
    "provider": "openai",
    "model": "gpt-4",
    "temperature": 0.8
  }
}
```

**响应**（SSE流）：

```
event: phase
data: 🧠 AI思考中：分析需要哪些信息...

event: decision
data: 
【AI决策过程】
Step 1: getOutline
  思考: 需要了解整体大纲框架
Step 2: getVolumeBlueprint
  思考: 需要当前卷的阶段目标
Step 3: WRITE
  思考: 基础信息已充足，可以开始写作

event: phase
data: 📝 开始写作...

event: content
data: 第一章...（流式输出）

event: complete
data: ✅ 生成完成！共 3500 字
```

#### 系统状态

```http
GET /api/agentic/status
```

### 测试流程

1. **准备数据**：确保小说已有大纲和卷蓝图
   ```sql
   -- 检查小说是否有大纲
   SELECT id, title, outline FROM novel WHERE id = 1;
   
   -- 检查卷设置
   SELECT id, title, start_chapter, end_chapter, blueprint 
   FROM novel_volume WHERE novel_id = 1;
   ```

2. **调用接口**：
   ```bash
   curl -X POST http://localhost:8080/api/agentic/generate-chapters-stream \
     -H "Content-Type: application/json" \
     -d '{
       "novelId": 1,
       "startChapter": 1,
       "count": 1
     }'
   ```

3. **观察日志**：
   ```
   🧠 开始ReAct决策循环: novelId=1, chapter=1
   📍 Step 1/8
   💭 AI思考: 需要先获取大纲，了解整体框架
   🔧 执行工具: getOutline | 参数: {novelId=1}
   ✅ 工具执行成功: getOutline -> {...}
   📍 Step 2/8
   💭 AI思考: 需要获取卷蓝图
   🔧 执行工具: getVolumeBlueprint | 参数: {novelId=1, chapterNumber=1}
   ...
   🎉 ReAct决策循环完成: 共3步, 执行工具[getOutline, getVolumeBlueprint, getWorldRules]
   🎬 开始生成章节: 某小说 - 第1章
   📝 开始写作...
   ✅ 章节生成完成: 第1章, 字数3245
   ```

## ⚙️ 配置说明

### AI配置

可通过请求体传入自定义AI配置，或使用系统默认配置。

```json
{
  "aiConfig": {
    "provider": "openai",
    "model": "gpt-4-turbo-preview",
    "temperature": 0.8,
    "maxTokens": 4000,
    "apiKey": "sk-xxx",
    "baseUrl": "https://api.openai.com/v1"
  }
}
```

### 上下文配额

在 `ContextBudget` 中定义（防止token溢出）：

```java
ContextBudget.builder()
    .maxEvents(8)           // 最多8个相关事件
    .maxForeshadows(6)      // 最多6个伏笔
    .maxPlotlines(3)        // 最多3条情节线
    .maxWorldRules(5)       // 最多5条规则
    .maxFullChapters(2)     // 最多2章完整内容
    .maxSummaryChapters(20) // 最多20章摘要
    .totalTokenBudget(100000)
    .build();
```

### ReAct决策限制

- **MAX_STEPS**: 最大决策步数为 8 步
- **必查工具**: `getOutline`, `getVolumeBlueprint`, `getWorldRules`
- **强制结束**: 完成必查工具且步数 ≥ 3 时，AI可决定开始写作

## 🔄 后续升级计划

### 阶段1：完善工具集（当前）
- ✅ 基础工具（大纲、卷蓝图、最近章节）
- ✅ ReAct决策循环
- ✅ 流式章节生成
- ⏳ 图谱查询（当前为模拟数据）

### 阶段2：Neo4j图数据库集成
- [ ] Docker Compose配置Neo4j
- [ ] 图谱实体建模（Event/Character/Foreshadow/Plotline）
- [ ] 章节落库时自动抽取实体
- [ ] Cypher查询实现（相关性排序、时间衰减）

### 阶段3：智能增强
- [ ] 自动检测设定崩坏
- [ ] 伏笔回收提醒
- [ ] 情节线久未推进警告
- [ ] 主角能力增长曲线监控

### 阶段4：题材适配
- [ ] 都市题材Profile（强化关系/规则）
- [ ] 仙侠题材Profile（强化修行/宗门）
- [ ] 玄幻题材Profile（强化力量体系）

## 📝 注意事项

1. **独立系统**：此系统与现有写作流程完全独立，可安全测试
2. **数据兼容**：生成的章节存入现有的 `novel_document` 表
3. **图谱数据**：当前为内存模拟，重启后清空
4. **API限流**：批量生成时自动休眠2秒避免限流
5. **错误恢复**：单章失败不影响后续章节生成

## 🐛 故障排查

### 问题1：章节未保存
- 检查 `novel_document` 表是否存在
- 检查是否有同名章节冲突

### 问题2：AI决策循环卡死
- 检查日志，查看在哪一步卡住
- 可能是AI响应格式不正确，会自动兜底为WRITE

### 问题3：工具执行失败
- 检查小说是否有大纲（必查工具）
- 检查卷设置是否正确（必查工具）

## 📚 扩展开发

### 添加新工具

1. 实现 `Tool` 接口：
```java
@Component
public class MyCustomTool implements Tool {
    @PostConstruct
    public void init() {
        registry.register(this);
    }
    
    @Override
    public String getName() {
        return "myTool";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        // 定义工具描述和参数
    }
    
    @Override
    public Object execute(Map<String, Object> args) {
        // 实现工具逻辑
    }
}
```

2. 自动注册到 `ToolRegistry`
3. AI可在决策时自动选择调用

---

**开发团队**: Novel Creation System  
**版本**: 1.0.0-agentic  
**最后更新**: 2025-10-29


