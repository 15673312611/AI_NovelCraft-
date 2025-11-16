# 🎯 Agentic AI系统 - 精简重构方案

## 核心理念

**简单即美好**：不要过度设计，相信AI的理解能力

```
┌─────────────────────────────────────────────────────────────┐
│                    AI写作决策流程                             │
└─────────────────────────────────────────────────────────────┘

第1步：给AI固定上下文
├─ 大纲（整体方向）
├─ 卷蓝图（当前卷的详细计划）
├─ 最近3章完整内容（保持连贯性和文笔一致）
└─ 最近30章概括（了解剧情发展）

第2步：AI思考与决策
├─ AI阅读固定内容
├─ AI思考：我需要什么信息？
├─ AI调用工具查询图数据库
│   ├─ 查询相关事件（基于因果关系）
│   ├─ 查询待回收伏笔
│   ├─ 查询世界规则
│   ├─ 查询角色关系
│   └─ 查询情节线状态
└─ 循环查询直到AI认为足够

第3步：封装最终上下文
├─ 固定内容（大纲+卷蓝图+最近3章+最近30章概括）
├─ AI查询到的图谱数据
├─ AI的构思过程（reasoning）
└─ 用户特殊要求

第4步：开始写作
└─ AI基于完整上下文生成章节
```

---

## 🔄 需要修改的部分

### 1. 固定上下文策略（GetRecentChaptersTool）

**当前**：
- 只返回最近1章完整内容（不够）

**修改为**：
```java
public class GetRecentChaptersTool {
    @Override
    public Object execute(Map<String, Object> args) {
        // 返回两部分：
        // 1. 最近3章完整内容
        // 2. 最近30章概括
        
        Map<String, Object> result = new HashMap<>();
        
        // 最近3章完整内容
        List<ChapterFull> recentFull = getRecentFullChapters(novelId, chapterNumber, 3);
        result.put("recentFullChapters", recentFull);
        
        // 最近30章概括（每章200字摘要）
        List<ChapterSummary> recentSummaries = getRecentSummaries(novelId, chapterNumber, 30);
        result.put("recentSummaries", recentSummaries);
        
        return result;
    }
}
```

---

### 2. 图数据库存储策略（精简）

**只存储核心内容**：

#### 存储1：事件（带因果关系）
```java
Event {
    id: "event_chapter5_1"
    summary: "主角突破筑基期"（30字内）
    chapterNumber: 5
    importance: 0.8
    participants: ["主角", "师父"]
    
    // 🔥 核心：因果关系
    causedBy: ["event_chapter3_2"]  // 前置事件ID
    causes: ["event_chapter7_1"]    // 后续事件ID
}
```

#### 存储2：伏笔
```java
Foreshadow {
    id: "foreshadow_chapter2_1"
    content: "神秘盒子"（简短描述）
    plantedAt: 2
    importance: "high"
    status: "PLANTED"  // 或 "REVEALED"
    suggestedRevealChapter: 15-20
}
```

#### 存储3：世界核心规则
```java
WorldRule {
    id: "rule_power_system"
    name: "修炼等级"
    content: "练气→筑基→金丹→元婴"
    constraint: "等级提升需要时间，不能瞬间突破"
    category: "power_system"
}
```

#### 存储4：角色重要关系变化
```java
CharacterRelation {
    from: "主角"
    to: "师姐"
    type: "ROMANCE"  // 或 CONFLICT, COOPERATION
    strength: 0.7
    changedAt: 15  // 在第15章发生变化
    description: "产生暧昧"
}
```

#### 存储5：情节线发展节点
```java
PlotlineNode {
    plotlineName: "主线：修炼之路"
    lastUpdate: 18  // 第18章最后推进
    status: "ACTIVE"  // 或 "IDLE"（久未推进）
    keyEvents: ["event_chapter5_1", "event_chapter12_3"]
}
```

**不存储**：
- ❌ 主角状态（让AI从内容推断）
- ❌ 时间线（让AI从内容理解）
- ❌ 位置信息（让AI记住）
- ❌ 详细对话（在最近3章完整内容里）

---

### 3. ReAct循环优化

**当前问题**：
- 反思机制每步都调用AI（太耗Token）
- 必查工具写死（太死板）

**修改策略**：

```java
public WritingContext executeReActLoop(...) {
    
    // 🔥 第1步：先给AI固定上下文（无需决策，直接给）
    executeMandatoryTools(novelId, chapterNumber, contextBuilder);
    // 这会执行：getOutline, getVolumeBlueprint, getRecentChapters(含最近3章+30章概括)
    
    // 🔥 第2步：AI基于固定内容决策还需要什么
    Set<String> availableOptionalTools = getOptionalTools();
    // 可选工具：getRelevantEvents, getUnresolvedForeshadows, getWorldRules,
    //          getCharacterRelationships, getPlotlineStatus等
    
    for (int step = 1; step <= MAX_STEPS; step++) {
        // AI思考
        String thinkingPrompt = buildThinkingPrompt(
            "你已经看过：大纲、卷蓝图、最近3章完整内容、最近30章概括。\n" +
            "现在思考：还需要查询哪些相关信息？\n" +
            "可用工具：" + availableOptionalTools
        );
        
        AgentDecision decision = callAIForDecision(thinkingPrompt, aiConfig);
        
        if ("WRITE".equals(decision.getAction())) {
            // AI认为足够了
            break;
        }
        
        // 执行工具
        Object result = executeTool(decision.getAction(), args);
        
        // 🔥 简化：不再每次都反思，只记录结果
        thought.setObservation(resultJson);
        
        // 🔥 简化：只在结果为空时提示
        if (isEmptyResult(result)) {
            thought.setReflection("结果为空，可能需要调整查询或尝试其他工具");
        }
    }
    
    return contextBuilder.build();
}
```

---

### 4. 最终上下文封装（buildWritingMessages）

**优化后的结构**：

```java
private List<Map<String, String>> buildWritingMessages(Novel novel, WritingContext context) {
    
    StringBuilder contextPrompt = new StringBuilder();
    
    // ========== 第1部分：固定上下文（AI已看过的） ==========
    contextPrompt.append("【固定上下文（你已经阅读过的信息）】\n\n");
    
    // 1.1 大纲
    contextPrompt.append("【整体大纲】\n");
    contextPrompt.append(context.getOutline()).append("\n\n");
    
    // 1.2 卷蓝图
    contextPrompt.append("【当前卷详细计划】\n");
    contextPrompt.append(context.getVolumeBlueprint()).append("\n\n");
    
    // 1.3 最近3章完整内容
    contextPrompt.append("【最近3章完整内容】\n");
    for (ChapterFull chapter : context.getRecentFullChapters()) {
        contextPrompt.append("=== 第").append(chapter.getNumber()).append("章 ===\n");
        contextPrompt.append(chapter.getContent()).append("\n\n");
    }
    
    // 1.4 最近30章概括
    contextPrompt.append("【最近30章剧情概括】\n");
    for (ChapterSummary summary : context.getRecentSummaries()) {
        contextPrompt.append("第").append(summary.getNumber()).append("章：");
        contextPrompt.append(summary.getSummary()).append("\n");
    }
    contextPrompt.append("\n");
    
    // ========== 第2部分：AI查询到的图谱数据 ==========
    contextPrompt.append("【你主动查询到的相关信息】\n\n");
    
    if (hasRelevantEvents) {
        contextPrompt.append("【相关历史事件（按因果关系筛选）】\n");
        for (GraphEntity event : context.getRelevantEvents()) {
            contextPrompt.append("- 第").append(event.getChapterNumber()).append("章：");
            contextPrompt.append(event.getSummary()).append("\n");
        }
        contextPrompt.append("\n");
    }
    
    if (hasUnresolvedForeshadows) {
        contextPrompt.append("【待回收伏笔】\n");
        for (GraphEntity foreshadow : context.getUnresolvedForeshadows()) {
            contextPrompt.append("- ").append(foreshadow.getContent());
            contextPrompt.append("（埋于第").append(foreshadow.getPlantedAt()).append("章）\n");
        }
        contextPrompt.append("\n");
    }
    
    if (hasWorldRules) {
        contextPrompt.append("【世界核心规则】\n");
        for (GraphEntity rule : context.getWorldRules()) {
            contextPrompt.append("- ").append(rule.getName()).append("：");
            contextPrompt.append(rule.getContent()).append("\n");
            if (rule.hasConstraint()) {
                contextPrompt.append("  约束：").append(rule.getConstraint()).append("\n");
            }
        }
        contextPrompt.append("\n");
    }
    
    // ========== 第3部分：AI的构思过程 ==========
    contextPrompt.append("【你的构思过程】\n");
    for (AgentThought thought : context.getThoughts()) {
        contextPrompt.append("- 思考：").append(thought.getReasoning()).append("\n");
        contextPrompt.append("  行动：").append(thought.getAction()).append("\n");
    }
    contextPrompt.append("\n");
    
    // ========== 第4部分：本章任务 ==========
    contextPrompt.append("【本章创作任务】\n");
    contextPrompt.append("章节号：第").append(chapterNumber).append("章\n");
    contextPrompt.append("目标字数：3000字左右\n");
    if (hasUserAdjustment) {
        contextPrompt.append("用户要求：").append(userAdjustment).append("\n");
    }
    contextPrompt.append("\n");
    
    // ========== 第5部分：写作要求 ==========
    contextPrompt.append("【写作要求】\n");
    contextPrompt.append("1. 与最近3章保持连贯，情节、文笔、人物性格一致\n");
    contextPrompt.append("2. 遵循最近30章的剧情发展逻辑\n");
    contextPrompt.append("3. 严格遵守世界核心规则，不得设定崩坏\n");
    contextPrompt.append("4. 如有合适机会，可自然回收伏笔\n");
    contextPrompt.append("5. 遵循爽文黄金法则，每300-500字必有钩子\n");
    contextPrompt.append("6. 章末必留悬念\n\n");
    
    contextPrompt.append("现在，请开始创作：");
    
    return messages;
}
```

---

### 5. 实体抽取策略（精简）

**只抽取核心内容**：

```java
public void extractAndSave(...) {
    
    String extractionPrompt = 
        "从以下章节中抽取核心信息（只抽取重要的，不要过度）：\n\n" +
        content + "\n\n" +
        "请以JSON格式返回：\n" +
        "{\n" +
        "  \"events\": [\n" +
        "    {\n" +
        "      \"id\": \"event_chapter5_1\",\n" +
        "      \"summary\": \"30字内摘要\",\n" +
        "      \"importance\": 0.8,\n" +
        "      \"causedBy\": [\"event_chapter3_2\"],  // 前置事件ID（如有）\n" +
        "      \"participants\": [\"主角\", \"师父\"]\n" +
        "    }\n" +
        "  ],\n" +
        "  \"foreshadows\": [  // 只抽取明显的伏笔\n" +
        "    {\n" +
        "      \"id\": \"foreshadow_chapter5_1\",\n" +
        "      \"content\": \"简短描述\",\n" +
        "      \"importance\": \"high\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"worldRules\": [  // 只抽取新引入的规则\n" +
        "    {\n" +
        "      \"name\": \"规则名\",\n" +
        "      \"content\": \"规则内容\",\n" +
        "      \"constraint\": \"约束\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"characterRelations\": [  // 只记录重要变化\n" +
        "    {\n" +
        "      \"from\": \"主角\",\n" +
        "      \"to\": \"师姐\",\n" +
        "      \"type\": \"ROMANCE\",\n" +
        "      \"changeDescription\": \"产生暧昧\"\n" +
        "    }\n" +
        "  ]\n" +
        "}\n\n" +
        "注意：\n" +
        "1. events最多抽取3-5个最重要的\n" +
        "2. 如果本章没有伏笔，foreshadows返回空数组\n" +
        "3. 只抽取核心的、对后续剧情有影响的内容\n" +
        "4. causedBy要引用之前章节的真实事件ID（如果有因果关系）\n";
    
    // ... 后续处理
}
```

---

## 🎯 核心优势

### 1. 简洁
- 固定上下文4项，清晰明确
- 图谱只存核心，不冗余
- AI自己决策，不过度喂食

### 2. 高效
- 减少不必要的反思调用
- Token消耗可控
- 查询精准，不浪费

### 3. 灵活
- AI根据实际需求查询
- 不同章节类型自动适应
- 不强制固定流程

### 4. 可靠
- 最近3章保证连贯性
- 最近30章概括保证大局观
- 图谱因果关系保证逻辑
- 伏笔系统保证不遗忘

---

## 📋 实施计划

### Step 1: 修改GetRecentChaptersTool
- 返回最近3章完整 + 最近30章概括

### Step 2: 优化ReAct循环
- 区分必查工具（固定上下文）和可选工具（图谱查询）
- 简化反思机制

### Step 3: 精简buildWritingMessages
- 清晰的5部分结构
- 减少冗余信息

### Step 4: 优化实体抽取
- 只抽取核心内容
- 增加因果关系链

### Step 5: 测试验证
- 连贯性测试
- 设定一致性测试
- 伏笔回收测试

---

## 结论

**少即是多**：
- 不要试图"控制"AI
- 给AI足够的固定信息（最近3章+30章概括）
- 让AI自己思考和决策
- 图谱只提供"查询服务"，不主动推送
- 相信AI的理解能力

这样的系统才是**真正的Agentic AI**，而不是"包装过的传统prompt"。

