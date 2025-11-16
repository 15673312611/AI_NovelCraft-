# 状态追踪图谱设计方案

## 核心思想
**将"事件"与"状态"分离，用状态节点追踪关键信息，用事件节点记录过程。**

## 图谱节点设计

### 1. 人物状态节点 (CharacterState)
```cypher
(:CharacterState {
  id: "character_state_林默",
  novelId: 1,
  characterName: "林默",
  
  // 核心状态（每次重大事件后更新）
  alive: true,               // 生死状态（关键！）
  currentLocation: "深山",    // 当前位置（关键！）
  affiliation: "无",          // 所属势力
  realm: "筑基期",            // 实力等级
  
  // 状态历史（JSON数组，记录变更）
  stateHistory: [
    {chapter: 5, location: "镇上", affiliation: "散修"},
    {chapter: 12, location: "深山", event: "逃入深山躲避追杀"},
    {chapter: 15, location: "深山", realm: "筑基期", event: "突破"}
  ],
  
  // 关键关系（简化查询）
  allies: ["张三", "李四"],
  enemies: ["王家"],
  
  lastUpdatedChapter: 15,
  updatedAt: "2025-11-06T10:00:00"
})
```

### 2. 势力状态节点 (FactionState)
```cypher
(:FactionState {
  id: "faction_state_王家",
  novelId: 1,
  factionName: "王家",
  
  // 核心状态
  status: "active",          // active/weakened/destroyed
  leader: "王家族长",        // 当前领袖
  leaderAlive: true,         // 领袖是否健在（关键！）
  baseLocation: "王家村",    // 势力驻地
  
  // 重大损失记录
  casualties: [
    {chapter: 10, name: "王家少主", role: "继承人", killer: "林默"},
    {chapter: 12, name: "王家大长老", role: "长老"}
  ],
  
  // 势力强度
  strength: "medium",        // strong/medium/weak/destroyed
  
  lastUpdatedChapter: 12,
  updatedAt: "2025-11-06T10:00:00"
})
```

### 3. 地点状态节点 (LocationState)
```cypher
(:LocationState {
  id: "location_state_深山",
  novelId: 1,
  locationName: "深山",
  
  // 当前状态
  controlledBy: null,        // 谁控制这里
  currentOccupants: ["林默"],// 当前在此的角色
  danger: "high",            // 危险程度
  
  // 重要事件记录
  majorEvents: [
    {chapter: 12, event: "林默进入深山避难"},
    {chapter: 13, event: "在深山发现灵泉"}
  ],
  
  lastUpdatedChapter: 13
})
```

### 4. 情节状态节点 (PlotState)
```cypher
(:PlotState {
  id: "plot_state_王家复仇线",
  novelId: 1,
  plotName: "王家复仇线",
  
  // 状态
  status: "ongoing",         // pending/ongoing/resolved/abandoned
  stage: "追杀阶段",         // 当前阶段
  
  // 因果链（关键事件序列）
  causalChain: [
    {chapter: 10, event: "林默杀王家少主", result: "王家震怒"},
    {chapter: 11, event: "王家派追兵", result: "林默逃入深山"},
    {chapter: 12, event: "追兵在深山迷路", result: "暂时安全"}
  ],
  
  // 未解决的问题
  pendingIssues: [
    "王家族长尚未出手",
    "林默需要提升实力才能对抗"
  ],
  
  lastUpdatedChapter: 12
})
```

### 5. 事件节点 (Event) - 改进版
```cypher
(:Event {
  id: "event_ch10_1",
  novelId: 1,
  chapterNumber: 10,
  
  // 基础信息
  summary: "林默杀王家少主",
  description: "...",
  location: "王家村外",
  participants: ["林默", "王家少主"],
  
  // ⭐ 新增：状态变更（关键！）
  stateChanges: {
    "王家少主": {alive: false, killedBy: "林默"},
    "林默": {enemies: ["王家"], location: "王家村外"}
  },
  
  // 因果关系
  causedBy: ["event_ch9_3"],   // 前因
  caused: ["event_ch11_1"],     // 后果
  
  importance: 0.9
})
```

## 关系设计

```cypher
// 状态更新关系
(:Event)-[:UPDATES_STATE]->(:CharacterState)
(:Event)-[:UPDATES_STATE]->(:FactionState)
(:Event)-[:UPDATES_STATE]->(:LocationState)

// 因果关系
(:Event)-[:CAUSES {description: "导致王家追杀"}]->(:Event)

// 人物关系
(:CharacterState)-[:ALLIED_WITH]->(:CharacterState)
(:CharacterState)-[:ENEMY_OF]->(:FactionState)
(:CharacterState)-[:CURRENTLY_AT]->(:LocationState)

// 情节关系
(:PlotState)-[:INVOLVES]->(:CharacterState)
(:PlotState)-[:INVOLVES]->(:FactionState)
```

## 检索策略优化

### 核心原则：**状态优先，事件补充**

```python
def get_context_for_chapter(novelId, chapterNumber):
    # 1. 优先获取最新状态（硬约束）
    character_states = get_latest_character_states(novelId, chapterNumber)
    faction_states = get_latest_faction_states(novelId, chapterNumber)
    location_states = get_latest_location_states(novelId, chapterNumber)
    plot_states = get_active_plot_states(novelId, chapterNumber)
    
    # 2. 补充相关事件（上下文参考）
    recent_events = get_recent_events(novelId, chapterNumber, limit=5)
    
    return {
        "CRITICAL_STATES": {  # 硬约束
            "characters": character_states,
            "factions": faction_states,
            "locations": location_states,
            "plots": plot_states
        },
        "REFERENCE_EVENTS": recent_events  # 软参考
    }
```

### Cypher查询示例

```cypher
// 获取所有角色的最新状态
MATCH (cs:CharacterState {novelId: $novelId})
WHERE cs.lastUpdatedChapter <= $chapterNumber
RETURN cs
ORDER BY cs.lastUpdatedChapter DESC

// 获取当前活跃情节
MATCH (ps:PlotState {novelId: $novelId})
WHERE ps.status IN ['ongoing', 'pending']
  AND ps.lastUpdatedChapter <= $chapterNumber
RETURN ps
ORDER BY ps.lastUpdatedChapter DESC

// 获取主角当前位置的状态
MATCH (cs:CharacterState {novelId: $novelId, characterName: $protagonist})
-[:CURRENTLY_AT]->(loc:LocationState)
WHERE cs.lastUpdatedChapter <= $chapterNumber
RETURN loc
```

## 提示词构建策略

### 新增：状态强约束区块

```markdown
【🚨 必须遵守的当前状态（不可违反）】

## 人物状态
- 林默：✅存活 | 📍当前位置：深山 | 实力：筑基期 | 敌对势力：王家
- 王家少主：❌已死（第10章被林默所杀）
- 王家族长：✅健在，尚未出手

## 势力状态
- 王家：🟡受损但未覆灭 | 领袖：王家族长（健在）| 状态：正在追杀林默

## 地点状态
- 主角当前位置：深山（第12章进入，尚未离开）
- 深山状态：危险但隐蔽，王家追兵未能深入

## 活跃情节线
- 王家复仇线：进行中 | 当前阶段：追杀阶段 | 未解决：族长尚未亲自出手

---

⚠️ **写作铁律**：以上状态为事实依据，AI生成内容必须与这些状态完全一致。
- 若需改变状态（如角色死亡、位置变更），必须在本章明确描写该变化过程。
- 不可凭空改变状态（如突然让死人复活、让角色瞬移到未描写的地点）。
```

## 实体抽取改进

### 新增：状态变更抽取

```json
{
  "events": [...],
  "stateChanges": {
    "characters": [
      {
        "name": "林默",
        "changes": {
          "location": "深山",
          "realm": "筑基期"
        },
        "chapter": 15
      }
    ],
    "factions": [
      {
        "name": "王家",
        "changes": {
          "casualties": [{"name": "王家少主", "status": "killed"}]
        },
        "chapter": 10
      }
    ]
  }
}
```

## 验证机制

### 生成前状态检查

```python
def validate_before_generation(context, draft_plan):
    """
    在生成前检查计划是否违反状态
    """
    errors = []
    
    # 检查死人是否"复活"
    for char in context["CRITICAL_STATES"]["characters"]:
        if not char["alive"] and char["name"] in draft_plan:
            errors.append(f"错误：{char['name']}已在第{char['deathChapter']}章死亡，不可再出现")
    
    # 检查位置跳跃
    protagonist_location = get_character_location("主角", context)
    if "前往" in draft_plan:
        target = extract_target_location(draft_plan)
        if target != protagonist_location and "离开" not in draft_plan:
            errors.append(f"错误：主角当前在{protagonist_location}，不可直接跳转到{target}")
    
    return errors
```

## 实施优先级

1. **P0（立即）**：
   - 实现 CharacterState 节点
   - 实现 FactionState 节点
   - 优化检索逻辑（状态优先）
   - 改进提示词（加入状态强约束）

2. **P1（后续）**：
   - 实现 LocationState 节点
   - 实现 PlotState 节点
   - 添加状态验证机制

3. **P2（优化）**：
   - 状态冲突自动检测
   - 状态可视化界面
   - 状态回溯与修复工具

## 效果预期

### 问题1：记错人物生死
**原因**：无状态追踪
**解决**：CharacterState.alive + 提示词硬约束
**效果**：AI会看到"王家少主：❌已死"，不可能再让他出现

### 问题2：地点混乱
**原因**：检索到旧位置事件
**解决**：CharacterState.currentLocation + 最新状态优先检索
**效果**：AI会看到"林默当前位置：深山"，不会写成镇上

### 问题3：势力状态混乱
**原因**：无势力整体状态
**解决**：FactionState节点记录势力完整状态
**效果**：AI知道"王家受损但未覆灭，族长健在"

## 下一步行动

1. 创建新的实体类型（CharacterState, FactionState）
2. 修改 EntityExtractionService 提示词（抽取状态变更）
3. 新增 Neo4jGraphService 状态查询方法
4. 修改 StructuredMessageBuilder（加入状态强约束区块）
5. 测试验证

