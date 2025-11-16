# 🔄 Agentic AI系统 - 完整流程逻辑链

## 📊 整体流程概览

```
用户请求
   ↓
[1] Controller接收 (AgenticWritingController.java)
   ↓
[2] 主服务处理 (AgenticChapterWriter.java)
   ↓
[3] ReAct决策循环 (AgentOrchestrator.java)
   ├─ 执行固定上下文工具（必查）
   ├─ AI思考与决策（可选工具）
   └─ 收集WritingContext
   ↓
[4] 构建写作提示词 (buildWritingMessages)
   ↓
[5] AI生成章节内容 (AIWritingService)
   ↓
[6] 保存章节 (NovelDocumentService)
   ↓
[7] 异步实体抽取 (EntityExtractionService)
   ├─ AI抽取实体
   ├─ 建立因果关系
   ├─ 建立角色关系
   └─ 入图Neo4j
   ↓
完成 ✅
```

---

## 🔍 详细流程拆解

### **步骤1：用户请求进入**

**入口**：`POST /api/agentic/generate-chapters-stream`

**请求参数**：
```json
{
  "novelId": 1,
  "startChapter": 5,
  "count": 1,
  "userAdjustment": "主角与反派大战",
  "aiConfig": {
    "provider": "openai",
    "model": "gpt-4",
    "apiKey": "xxx"
  }
}
```

**代码位置**：`AgenticWritingController.java:44`

---

### **步骤2：Controller处理请求**

**文件**：`AgenticWritingController.java`

**核心逻辑**：
```java
@PostMapping("/generate-chapters-stream")
public SseEmitter generateChaptersStream(@RequestBody Map<String, Object> request) {
    Long novelId = extractNovelId(request);
    Integer startChapter = extractStartChapter(request);
    String userAdjustment = extractUserAdjustment(request);
    AIConfigRequest aiConfig = extractAIConfig(request);
    
    SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时
    
    // 异步执行生成
    CompletableFuture.runAsync(() -> {
        chapterWriter.generateChapter(
            novelId, startChapter, userAdjustment, aiConfig, emitter
        );
        emitter.complete();
    });
    
    return emitter;
}
```

**关键点**：
- 创建SSE连接，支持流式响应
- 异步执行，不阻塞请求
- 超时时间10分钟

---

### **步骤3：主服务开始处理**

**文件**：`AgenticChapterWriter.java`

**入口方法**：`generateChapter()` (第55行)

```java
public NovelDocument generateChapter(
    Long novelId,
    Integer chapterNumber,
    String userAdjustment,
    AIConfigRequest aiConfig,
    SseEmitter emitter) throws Exception {
    
    // 3.1 查询小说基础信息
    Novel novel = novelRepository.selectById(novelId);
    
    sendEvent(emitter, "phase", "🧠 AI思考中：分析需要哪些信息...");
    
    // 3.2 执行ReAct决策循环 - 收集WritingContext
    WritingContext context = orchestrator.executeReActLoop(
        novelId, chapterNumber, userAdjustment, aiConfig
    );
    
    // 3.3 发送决策过程（可选，用于调试）
    sendDecisionProcess(emitter, context.getThoughts());
    
    // 3.4 构建写作提示词
    sendEvent(emitter, "phase", "📝 开始写作...");
    List<Map<String, String>> messages = buildWritingMessages(novel, context);
    
    // 3.5 流式生成章节内容
    StringBuilder generatedContent = new StringBuilder();
    aiWritingService.streamGenerateContentWithMessages(
        messages, "chapter_writing", aiConfig,
        chunk -> {
            generatedContent.append(chunk);
            sendEvent(emitter, "content", chunk);
        }
    );
    
    // 3.6 保存章节
    sendEvent(emitter, "phase", "💾 保存中...");
    NovelDocument document = saveChapter(novel, chapterNumber, generatedContent.toString());
    
    // 3.7 异步抽取实体并入图
    CompletableFuture.runAsync(() -> {
        entityExtractionService.extractAndSave(
            novel.getId(), chapterNumber, document.getTitle(), generatedContent.toString()
        );
    });
    
    return document;
}
```

---

### **步骤4：ReAct决策循环（核心）**

**文件**：`AgentOrchestrator.java`

**入口方法**：`executeReActLoop()` (第51行)

#### **4.1 初始化**
```java
WritingContext.WritingContextBuilder contextBuilder = WritingContext.builder();
List<AgentThought> thoughts = new ArrayList<>();

// 初始化章节计划
Map<String, Object> chapterPlan = new HashMap<>();
chapterPlan.put("chapterNumber", chapterNumber);
chapterPlan.put("userAdjustment", userAdjustment);
contextBuilder.chapterPlan(chapterPlan);
```

#### **4.2 定义工具集**
```java
// 获取所有可用工具
List<ToolDefinition> availableTools = toolRegistry.getAllDefinitions();

// 🔥 固定必查工具（必须执行）
Set<String> requiredTools = new HashSet<>();
requiredTools.add("getOutline");         // 大纲
requiredTools.add("getVolumeBlueprint"); // 卷蓝图
requiredTools.add("getRecentChapters");  // 最近3章+30章概括

// 可选工具（AI决策是否查询）
// - getRelevantEvents
// - getUnresolvedForeshadows
// - getWorldRules
// - getCharacterRelationships
// - getEventsByCharacter
// - getEventsByCausality
// - getConflictHistory
// - getPlotlineStatus
```

#### **4.3 ReAct循环（最多8步）**
```java
for (int step = 1; step <= MAX_STEPS; step++) {
    
    // Step A: THOUGHT - AI思考下一步
    String thinkingPrompt = buildThinkingPrompt(
        novelId, chapterNumber, userAdjustment,
        availableTools, executedTools, requiredTools, thoughts
    );
    
    // Step B: 调用AI获取决策
    String aiResponse = callAIForDecision(thinkingPrompt, aiConfig);
    
    // Step C: 解析AI决策
    AgentDecision decision = parseAIDecision(aiResponse);
    // decision包含：
    // - reasoning: AI的思考过程
    // - action: 要执行的工具名 或 "WRITE"
    // - actionArgs: 工具参数
    
    thought.setReasoning(decision.getReasoning());
    thought.setAction(decision.getAction());
    
    // Step D: ACTION - 执行决策
    if ("WRITE".equals(decision.getAction())) {
        // AI认为信息充足，可以开始写作
        thought.setGoalAchieved(true);
        break;
    } else {
        // 执行具体工具
        Map<String, Object> args = parseToolArgs(decision.getActionArgs(), novelId, chapterNumber);
        Object result = toolRegistry.executeTool(decision.getAction(), args);
        
        // Step E: OBSERVATION - 记录结果
        String resultJson = objectMapper.writeValueAsString(result);
        thought.setObservation(resultJson);
        
        // Step F: REFLECTION - 反思结果质量（可选）
        String reflection = reflectOnResult(decision.getAction(), resultJson, aiConfig);
        thought.setReflection(reflection);
        
        // 存储工具结果到上下文
        storeToolResult(decision.getAction(), result, contextBuilder);
        
        executedTools.add(decision.getAction());
    }
    
    thoughts.add(thought);
}
```

#### **4.4 兜底机制**
```java
// 如果AI决策循环结束但必查工具未执行，强制执行
if (!executedTools.contains("getOutline")) {
    // 强制执行getOutline
}
if (!executedTools.contains("getVolumeBlueprint")) {
    // 强制执行getVolumeBlueprint
}
```

#### **4.5 返回WritingContext**
```java
contextBuilder.thoughts(thoughts);
WritingContext context = contextBuilder.build();

// context包含：
// - outline: 大纲
// - volumeBlueprint: 卷蓝图
// - recentFullChapters: 最近3章完整内容（来自getRecentChapters）
// - recentSummaries: 最近30章概括（来自getRecentChapters）
// - relevantEvents: 相关事件（如果AI查询了）
// - unresolvedForeshadows: 待回收伏笔（如果AI查询了）
// - worldRules: 世界规则（如果AI查询了）
// - thoughts: AI的思考过程

return context;
```

---

### **步骤5：构建写作提示词**

**文件**：`AgenticChapterWriter.java`

**方法**：`buildWritingMessages()` (第192行)

#### **5.1 系统身份提示词**
```java
String systemPrompt = buildSystemIdentityPrompt(novel.getGenre());
// 包含：
// - 爽文黄金法则
// - 冲突制造法
// - 情绪操控术
// - 对话黄金律
// - 节奏控制法
// - 钩子布局法
// 等完整的网文写作规则
```

#### **5.2 用户上下文提示词**
```java
StringBuilder contextPrompt = new StringBuilder();

// 第1部分：小说基础信息
contextPrompt.append("【小说信息】\n");
contextPrompt.append("书名：").append(novel.getTitle()).append("\n");
contextPrompt.append("题材：").append(novel.getGenre()).append("\n");

// 第2部分：大纲
contextPrompt.append("【整体大纲】\n");
contextPrompt.append(context.getOutline()).append("\n\n");

// 第3部分：卷蓝图
contextPrompt.append("【当前卷信息】\n");
contextPrompt.append(context.getVolumeBlueprint()).append("\n\n");

// 第4部分：最近3章完整内容（保持连贯性）
contextPrompt.append("【最近3章完整内容】\n");
for (ChapterFull chapter : context.getRecentFullChapters()) {
    contextPrompt.append("=== 第").append(chapter.getNumber()).append("章 ===\n");
    contextPrompt.append(chapter.getContent()).append("\n\n");
}

// 第5部分：最近30章概括（了解剧情发展）
contextPrompt.append("【最近30章剧情概括】\n");
for (ChapterSummary summary : context.getRecentSummaries()) {
    contextPrompt.append("第").append(summary.getNumber()).append("章：");
    contextPrompt.append(summary.getSummary()).append("\n");
}

// 第6部分：世界规则（如果AI查询了）
if (hasWorldRules) {
    contextPrompt.append("【世界规则与设定】\n");
    for (GraphEntity rule : context.getWorldRules()) {
        contextPrompt.append("- ").append(rule.getName()).append(": ");
        contextPrompt.append(rule.getDescription()).append("\n");
    }
}

// 第7部分：AI查询到的相关事件（如果有）
if (hasRelevantEvents) {
    contextPrompt.append("【智能检索：强相关历史事件】\n");
    contextPrompt.append("（以下事件由AI基于因果关系智能筛选）\n");
    for (GraphEntity event : context.getRelevantEvents()) {
        contextPrompt.append("- [第").append(event.getChapterNumber()).append("章] ");
        contextPrompt.append(event.getSummary()).append("\n");
    }
}

// 第8部分：待回收伏笔（如果AI查询了）
if (hasUnresolvedForeshadows) {
    contextPrompt.append("【待回收伏笔】\n");
    for (GraphEntity foreshadow : context.getUnresolvedForeshadows()) {
        contextPrompt.append("- ").append(foreshadow.getContent());
        contextPrompt.append("（埋于第").append(foreshadow.getPlantedAt()).append("章）\n");
    }
}

// 第9部分：情节线状态（如果AI查询了）
if (hasPlotlineStatus) {
    contextPrompt.append("【情节线状态警告】\n");
    for (GraphEntity plotline : context.getPlotlineStatus()) {
        contextPrompt.append("- ").append(plotline.getName()).append("：");
        contextPrompt.append(plotline.getStatus()).append("\n");
    }
}

// 第10部分：AI的构思过程
contextPrompt.append("【AI决策过程记录】\n");
for (AgentThought thought : context.getThoughts()) {
    contextPrompt.append("Step ").append(thought.getStepNumber()).append(": ");
    contextPrompt.append(thought.getReasoning()).append(" → 执行[");
    contextPrompt.append(thought.getAction()).append("]\n");
}

// 第11部分：本章创作任务
contextPrompt.append("【本章创作任务】\n");
contextPrompt.append("章节号：第").append(chapterNumber).append("章\n");
contextPrompt.append("目标字数：3000字左右\n");
if (hasUserAdjustment) {
    contextPrompt.append("用户要求：").append(userAdjustment).append("\n");
}

// 第12部分：写作要求
contextPrompt.append("【写作要求（必须严格遵守）】\n");
contextPrompt.append("1. 与最近3章保持连贯，情节、文笔、人物性格一致\n");
contextPrompt.append("2. 参考最近30章概括，保持剧情发展逻辑\n");
contextPrompt.append("3. 严格遵守世界规则，不得设定崩坏\n");
contextPrompt.append("4. 如有合适机会，可自然回收伏笔\n");
contextPrompt.append("5. 遵循爽文黄金法则，每300-500字必有钩子\n");
contextPrompt.append("6. 章末必留悬念\n\n");

contextPrompt.append("现在，请开始创作：");
```

#### **5.3 组装消息**
```java
List<Map<String, String>> messages = new ArrayList<>();

// 系统消息
Map<String, String> systemMessage = new HashMap<>();
systemMessage.put("role", "system");
systemMessage.put("content", systemPrompt);
messages.add(systemMessage);

// 用户消息
Map<String, String> userMessage = new HashMap<>();
userMessage.put("role", "user");
userMessage.put("content", contextPrompt.toString());
messages.add(userMessage);

return messages;
```

---

### **步骤6：AI生成章节内容**

**文件**：`AIWritingService.java`

**方法**：`streamGenerateContentWithMessages()`

```java
// 调用OpenAI/其他LLM API
// 流式返回，每个chunk实时通过SSE发送给前端
aiWritingService.streamGenerateContentWithMessages(
    messages,              // 完整的提示词
    "chapter_writing",     // 任务类型
    aiConfig,             // AI配置（模型、API Key等）
    chunk -> {            // 回调函数
        generatedContent.append(chunk);
        sendEvent(emitter, "content", chunk);  // 实时推送给前端
    }
);
```

**前端接收到的SSE事件**：
```
event: phase
data: 📝 开始写作...

event: content
data: 李青缓缓睁开双眼...

event: content
data: ，体内灵力...

event: content
data: 如江河奔涌...

...（持续流式输出）
```

---

### **步骤7：保存章节**

**文件**：`AgenticChapterWriter.java`

**方法**：`saveChapter()` (第514行)

```java
private NovelDocument saveChapter(Novel novel, Integer chapterNumber, String content) {
    NovelDocument document = new NovelDocument();
    document.setNovelId(novel.getId());
    document.setTitle("第" + chapterNumber + "章");
    document.setContent(content);
    document.setSortOrder(chapterNumber);
    
    // 调用NovelDocumentService保存到数据库
    return documentService.createDocument(document);
}
```

**数据库表**：`novel_documents`

---

### **步骤8：异步实体抽取**

**文件**：`EntityExtractionService.java`

**方法**：`extractAndSave()` (第43行)

#### **8.1 构建抽取提示词**
```java
String extractionPrompt = 
    "从以下章节中抽取核心信息：\n\n" +
    content + "\n\n" +
    "请以JSON格式返回：\n" +
    "{\n" +
    "  \"events\": [  // 3-5个关键事件\n" +
    "    {\n" +
    "      \"id\": \"event_chapter5_1\",\n" +
    "      \"summary\": \"30字内摘要\",\n" +
    "      \"importance\": 0.8,\n" +
    "      \"participants\": [\"主角\", \"师父\"],\n" +
    "      \"emotionalTone\": \"positive\",\n" +
    "      \"tags\": [\"战斗\", \"突破\"]\n" +
    "    }\n" +
    "  ],\n" +
    "  \"foreshadows\": [  // 明显的伏笔\n" +
    "    {\n" +
    "      \"id\": \"foreshadow_chapter5_1\",\n" +
    "      \"content\": \"神秘预言\",\n" +
    "      \"importance\": \"high\",\n" +
    "      \"suggestedRevealChapter\": 15\n" +
    "    }\n" +
    "  ],\n" +
    "  \"worldRules\": [  // 新引入的规则\n" +
    "    {\n" +
    "      \"id\": \"rule_power_system\",\n" +
    "      \"name\": \"修炼等级\",\n" +
    "      \"content\": \"筑基→金丹\",\n" +
    "      \"constraint\": \"需要时间\"\n" +
    "    }\n" +
    "  ],\n" +
    "  \"causalRelations\": [  // 🆕 因果关系\n" +
    "    {\n" +
    "      \"from\": \"event_chapter3_1\",\n" +
    "      \"to\": \"event_chapter5_1\",\n" +
    "      \"type\": \"CAUSES\",\n" +
    "      \"description\": \"前一事件导致了这一事件\"\n" +
    "    }\n" +
    "  ],\n" +
    "  \"characterRelations\": [  // 🆕 角色关系变化\n" +
    "    {\n" +
    "      \"from\": \"主角\",\n" +
    "      \"to\": \"师姐\",\n" +
    "      \"type\": \"ROMANCE\",\n" +
    "      \"strength\": 0.7,\n" +
    "      \"description\": \"产生暧昧\"\n" +
    "    }\n" +
    "  ]\n" +
    "}";
```

#### **8.2 调用AI抽取**
```java
String aiResponse = callAIForExtraction(extractionPrompt);
```

#### **8.3 解析AI返回**
```java
Map<String, Object> extracted = parseExtractedEntities(aiResponse);
// extracted包含：
// - events: List<Map>
// - foreshadows: List<Map>
// - worldRules: List<Map>
// - causalRelations: List<Map>  // 新增
// - characterRelations: List<Map>  // 新增
```

#### **8.4 转换为GraphEntity**
```java
List<GraphEntity> entities = convertToGraphEntities(extracted, novelId, chapterNumber);
// 将AI返回的Map转换为GraphEntity对象
```

#### **8.5 批量入图**
```java
// 第1步：添加实体
graphService.addEntities(novelId, entities);

// 第2步：添加因果关系
if (extracted.containsKey("causalRelations")) {
    List<Map<String, Object>> causalRelations = extracted.get("causalRelations");
    for (Map<String, Object> relation : causalRelations) {
        String fromEventId = relation.get("from");
        String toEventId = relation.get("to");
        String type = relation.get("type");  // CAUSES
        
        graphService.addRelationship(
            novelId, fromEventId, type, toEventId, properties
        );
    }
}

// 第3步：添加角色关系
if (extracted.containsKey("characterRelations")) {
    List<Map<String, Object>> characterRelations = extracted.get("characterRelations");
    for (Map<String, Object> relation : characterRelations) {
        String fromCharacter = relation.get("from");
        String toCharacter = relation.get("to");
        String type = relation.get("type");  // ROMANCE/CONFLICT等
        
        graphService.addRelationship(
            novelId, fromCharacter, "RELATIONSHIP", toCharacter, properties
        );
    }
}
```

---

### **步骤9：入图Neo4j**

**文件**：`Neo4jGraphService.java`

#### **9.1 添加实体节点**
```cypher
// 事件节点
MERGE (e:Event {id: $id})
SET e.novelId = $novelId,
    e.chapterNumber = $chapterNumber,
    e.summary = $summary,
    e.importance = $importance,
    e.participants = $participants,
    e.emotionalTone = $emotionalTone,
    e.tags = $tags,
    e.updatedAt = datetime()

// 伏笔节点
MERGE (f:Foreshadowing {id: $id})
SET f.novelId = $novelId,
    f.content = $content,
    f.importance = $importance,
    f.status = 'PLANTED',
    f.plannedRevealChapter = $plannedRevealChapter,
    f.updatedAt = datetime()

// 世界规则节点
MERGE (r:WorldRule {id: $id})
SET r.novelId = $novelId,
    r.name = $name,
    r.content = $content,
    r.constraint = $constraint,
    r.category = $category,
    r.updatedAt = datetime()
```

#### **9.2 添加关系**
```cypher
// 因果关系
MATCH (from:Event {id: $fromId, novelId: $novelId})
MATCH (to:Event {id: $toId, novelId: $novelId})
MERGE (from)-[r:CAUSES]->(to)
SET r.description = $description,
    r.updatedAt = datetime()

// 角色关系
MATCH (from:Character {name: $fromName, novelId: $novelId})
MATCH (to:Character {name: $toName, novelId: $novelId})
MERGE (from)-[r:RELATIONSHIP]->(to)
SET r.type = $relationType,
    r.strength = $strength,
    r.description = $description,
    r.updatedAt = datetime()
```

---

## 🎯 关键决策点

### **决策点1：必查工具 vs 可选工具**

**必查工具（固定上下文）**：
- `getOutline` - 大纲
- `getVolumeBlueprint` - 卷蓝图
- `getRecentChapters` - 最近3章+30章概括

**可选工具（AI决策）**：
- `getRelevantEvents` - 相关事件
- `getUnresolvedForeshadows` - 待回收伏笔
- `getWorldRules` - 世界规则
- `getCharacterRelationships` - 角色关系
- `getEventsByCharacter` - 按角色查事件
- `getEventsByCausality` - 按因果链查事件
- `getConflictHistory` - 冲突历史
- `getPlotlineStatus` - 情节线状态

### **决策点2：AI何时停止ReAct循环**

**停止条件**：
1. AI主动返回`"action": "WRITE"`（认为信息充足）
2. 达到8步上限（强制停止）
3. 所有必查工具已执行且步数≥3

### **决策点3：实体抽取失败处理**

**处理策略**：
1. 记录失败日志
2. 调用`EntityExtractionRetryService`记录失败信息
3. 不阻塞主流程（异步抽取）
4. 后续自动重试机制

---

## 📊 数据流向

```
用户请求
   ↓
[数据库] novels表 → 查询小说信息
   ↓
[ReAct循环] → 执行工具
   ├─ novels表 → 查询大纲
   ├─ volumes表 → 查询卷蓝图
   ├─ novel_documents表 → 查询最近章节
   ├─ [可选] Neo4j → 查询相关事件
   ├─ [可选] Neo4j → 查询伏笔
   └─ [可选] Neo4j → 查询世界规则
   ↓
[构建提示词] → 整合所有信息
   ↓
[AI生成] → OpenAI API
   ↓
[保存] → novel_documents表
   ↓
[异步抽取] → AI抽取实体
   ↓
[入图] → Neo4j数据库
   ├─ 创建Event节点
   ├─ 创建Foreshadowing节点
   ├─ 创建WorldRule节点
   ├─ 创建CAUSES关系
   └─ 创建RELATIONSHIP关系
```

---

## ⏱️ 时间估算

| 阶段 | 操作 | 预估时间 |
|------|------|----------|
| 1 | 查询小说信息 | 10-50ms |
| 2 | ReAct循环（3-8步） | 10-30秒 |
|   | ├─ 每步AI思考 | 1-3秒 |
|   | ├─ 工具执行 | 0.1-1秒 |
|   | └─ 反思（可选） | 1-2秒 |
| 3 | 构建提示词 | 10-100ms |
| 4 | AI生成章节 | 30-120秒 |
|   | ├─ 3000字内容 | ~60秒 |
|   | └─ 流式输出 | 实时 |
| 5 | 保存章节 | 10-50ms |
| 6 | 异步实体抽取 | 5-15秒 |
|   | ├─ AI抽取 | 3-10秒 |
|   | └─ 入图Neo4j | 2-5秒 |
| **总计** | **约50-180秒** | |

---

## 🔧 可配置参数

| 参数 | 位置 | 默认值 | 说明 |
|------|------|--------|------|
| MAX_STEPS | AgentOrchestrator | 8 | ReAct循环最大步数 |
| SSE超时 | Controller | 600秒 | SSE连接超时时间 |
| 最近章节数（完整） | GetRecentChaptersTool | 3 | 完整内容章节数 |
| 最近章节数（概括） | GetRecentChaptersTool | 30 | 概括内容章节数 |
| Token预算 | TokenBudget | 动态 | 各部分Token限制 |
| 抽取重试次数 | RetryService | 3 | 实体抽取失败重试 |

---

## ✅ 总结

**当前流程的核心特点**：

1. **固定+可选结合**：必查工具保证基础信息，可选工具让AI自主决策
2. **ReAct智能决策**：AI不是被动接受信息，而是主动思考需要什么
3. **流式实时反馈**：通过SSE实时推送生成内容，用户体验好
4. **异步实体抽取**：不阻塞主流程，后台自动建立知识图谱
5. **图谱关系存储**：因果链+角色关系，支持后续智能查询

**流程优势**：
- ✅ 简洁高效（固定上下文清晰）
- ✅ 灵活智能（AI按需查询）
- ✅ 可扩展性强（新增工具容易）
- ✅ 可靠性高（有兜底机制）
- ✅ 长期记忆（图谱持久化）

**适用场景**：
- ✅ 长篇网文创作（100章+）
- ✅ 多线叙事
- ✅ 复杂世界观
- ✅ 需要严格设定一致性

