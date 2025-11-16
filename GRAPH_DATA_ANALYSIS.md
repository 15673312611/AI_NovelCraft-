# 图谱数据架构分析与优化方案

## 问题1：buildDirectWritingContext 查询了很多图谱数据类型，但实际只保存了3种

### 当前状态

#### 📥 查询的图谱数据（11种）
`AgenticChapterWriter.buildDirectWritingContext()` 方法查询了以下数据：

1. **核心设定** (coreSettings) - ✅ 来自数据库
2. **卷蓝图** (volumeBlueprint) - ✅ 来自数据库
3. **最近1章完整内容** (recentFullChapters) - ✅ 来自数据库
4. **前30章概要** (recentSummaries) - ✅ 来自数据库
5. **角色档案** (characterProfiles) - ⚠️ 来自图谱（CharacterProfile节点）
6. **历史事件** (relevantEvents) - ❌ 来自图谱（Event节点）- **未保存**
7. **未解决伏笔** (unresolvedForeshadows) - ❌ 来自图谱（Foreshadowing节点）- **未保存**
8. **情节线状态** (plotlineStatus) - ❌ 来自图谱（PlotLine节点）- **未保存**
9. **冲突弧线** (conflictArcs) - ❌ 来自图谱（ConflictArc节点）- **未保存**
10. **角色成长弧线** (characterArcs) - ❌ 来自图谱（CharacterArc节点）- **未保存**
11. **叙事节奏状态** (narrativeRhythm) - ❌ 来自图谱（NarrativeBeat节点）- **未保存**

#### 💾 实际保存的图谱数据（3种）
`CoreStateExtractor.extractAndSaveCoreState()` 只保存：

1. **CharacterState** - 角色状态（主角+Top3配角的位置、境界、存活、物品）
2. **RelationshipState** - 关系状态（主角与配角的关系类型和强度）
3. **OpenQuest** - 未决任务（长期任务的引入、推进、截止）

### 问题分析

#### 🔴 数据不一致问题
- **查询的数据类型** ≠ **保存的数据类型**
- 查询 Event、Foreshadowing、PlotLine、ConflictArc、CharacterArc、NarrativeBeat 节点，但这些节点从未被创建
- 导致这些查询**永远返回空列表**，浪费查询时间

#### 🔴 上下文构建失败
- `StructuredMessageBuilder` 依赖这些图谱数据构建写作上下文
- 由于数据为空，上下文中缺少：
  - 历史事件参考
  - 待回收伏笔
  - 情节线活跃度
  - 冲突弧线状态
  - 人物成长节点
  - 叙事节奏建议

#### 🔴 AI生成质量下降
- AI无法获取历史事件上下文，容易产生矛盾
- AI无法获取伏笔信息，无法回收伏笔
- AI无法获取情节线状态，容易遗漏支线
- AI无法获取冲突弧线，节奏把控不准

---

## 问题2：卷蓝图获取为空

### 当前状态

#### 📍 卷蓝图来源
`GetVolumeBlueprintTool.execute()` 从数据库查询：
```java
List<NovelVolume> volumes = volumeService.getVolumesByNovelId(novelId);
```

#### 📍 返回的字段
```java
result.put("blueprint", safeString(volume.getContentOutline(), "暂无蓝图"));
```

**关键字段**：`NovelVolume.contentOutline` - 这是卷蓝图的内容

### 问题分析

#### 🔴 数据库字段为空
- 日志显示：`contentOutline=为NULL` 或 `contentOutline长度=0`
- 原因：**数据库中的 `novel_volume` 表的 `content_outline` 字段未填充**

#### 🔴 卷蓝图未生成
- 系统有生成卷蓝图的功能，但用户可能未执行
- 或者生成后未保存到 `content_outline` 字段

#### 🔴 字段映射问题
- 需要确认 `NovelVolume` 实体的 `contentOutline` 字段是否正确映射到数据库的 `content_outline` 列

---

## 解决方案

### 方案A：简化架构（推荐）- 只使用核心记忆账本

#### 优点
- ✅ 架构清晰，数据一致
- ✅ 减少查询开销
- ✅ 降低维护成本
- ✅ 核心记忆账本已经包含最重要的状态信息

#### 实施步骤

1. **删除未使用的图谱查询**
   - 从 `buildDirectWritingContext()` 中删除：
     - `getRelevantEvents()`
     - `getUnresolvedForeshadows()`
     - `getPlotlineStatus()`
     - `getActiveConflictArcs()`
     - `getCharacterArcStatus()`
     - `getNarrativeRhythmStatus()`

2. **修改 WritingContext 数据结构**
   - 删除对应的字段：
     - `relevantEvents`
     - `unresolvedForeshadows`
     - `plotlineStatus`
     - `conflictArcs`
     - `characterArcs`
     - `narrativeRhythm`

3. **修改 StructuredMessageBuilder**
   - 删除对这些字段的引用
   - 改用核心记忆账本数据构建上下文

4. **新增核心记忆账本查询**
   - 在 `buildDirectWritingContext()` 中添加：
     ```java
     // 获取角色状态
     List<Map<String, Object>> characterStates = graphService.getCharacterStates(novelId, 10);
     contextBuilder.characterStates(characterStates);
     
     // 获取关系状态
     List<Map<String, Object>> relationships = graphService.getTopRelationships(novelId, 10);
     contextBuilder.relationships(relationships);
     
     // 获取未决任务
     List<Map<String, Object>> openQuests = graphService.getOpenQuests(novelId, chapterNumber);
     contextBuilder.openQuests(openQuests);
     ```

5. **更新 StructuredMessageBuilder**
   - 使用核心记忆账本数据构建上下文：
     ```java
     // 角色状态
     if (context.getCharacterStates() != null && !context.getCharacterStates().isEmpty()) {
         body.append("【角色状态】\n");
         context.getCharacterStates().forEach(state -> {
             body.append("- ").append(state.get("characterName"))
                 .append("：位置=").append(state.get("location"))
                 .append("，境界=").append(state.get("realm"))
                 .append("，状态=").append(state.get("alive") ? "存活" : "死亡")
                 .append("\n");
         });
     }
     
     // 关系状态
     if (context.getRelationships() != null && !context.getRelationships().isEmpty()) {
         body.append("【关系状态】\n");
         context.getRelationships().forEach(rel -> {
             body.append("- ").append(rel.get("characterA"))
                 .append(" <-> ").append(rel.get("characterB"))
                 .append("：").append(rel.get("type"))
                 .append("（强度=").append(rel.get("strength")).append("）\n");
         });
     }
     
     // 未决任务
     if (context.getOpenQuests() != null && !context.getOpenQuests().isEmpty()) {
         body.append("【未决任务】\n");
         context.getOpenQuests().forEach(quest -> {
             body.append("- ").append(quest.get("description"))
                 .append("（引入于第").append(quest.get("introducedAt"))
                 .append("章，截止第").append(quest.get("dueChapter"))
                 .append("章）\n");
         });
     }
     ```

---

### 方案B：完整实现图谱架构（不推荐）

#### 缺点
- ❌ 需要大量开发工作
- ❌ 需要实现事件抽取、伏笔识别、情节线跟踪等复杂逻辑
- ❌ 维护成本高
- ❌ 查询开销大

#### 实施步骤（仅供参考）

1. **实现事件抽取**
   - 在 `CoreStateExtractor` 中添加事件抽取逻辑
   - 调用 AI 从章节内容中提取关键事件
   - 保存到 Neo4j 的 `Event` 节点

2. **实现伏笔识别**
   - 识别章节中埋下的伏笔
   - 保存到 `Foreshadowing` 节点
   - 标记状态：PLANTED（已埋）、RESOLVED（已回收）

3. **实现情节线跟踪**
   - 识别和跟踪多条情节线
   - 保存到 `PlotLine` 节点
   - 记录每条情节线的最后推进章节

4. **实现冲突弧线管理**
   - 识别主要冲突
   - 保存到 `ConflictArc` 节点
   - 跟踪冲突阶段：酝酿、爆发、高潮、解决

5. **实现角色成长弧线**
   - 跟踪角色成长节点
   - 保存到 `CharacterArc` 节点

6. **实现叙事节奏分析**
   - 分析章节节奏
   - 保存到 `NarrativeBeat` 节点

---

## 卷蓝图问题解决方案

### 方案1：检查数据库数据

1. **查询数据库**
   ```sql
   SELECT id, volume_number, title, content_outline 
   FROM novel_volume 
   WHERE novel_id = 100;
   ```

2. **检查 `content_outline` 字段是否为空**
   - 如果为空，说明卷蓝图未生成

### 方案2：生成卷蓝图

1. **找到生成卷蓝图的接口**
   - 可能是 `/api/volumes/{volumeId}/generate-blueprint` 或类似接口

2. **为每个卷生成蓝图**
   - 调用接口生成蓝图
   - 确保保存到 `content_outline` 字段

### 方案3：检查实体映射

1. **检查 `NovelVolume` 实体**
   ```java
   @Entity
   @Table(name = "novel_volume")
   public class NovelVolume {
       @Column(name = "content_outline")
       private String contentOutline;
       // ...
   }
   ```

2. **确认字段映射正确**

---

## 推荐实施顺序

1. ✅ **立即执行**：简化架构（方案A）
   - 删除未使用的图谱查询
   - 改用核心记忆账本

2. ✅ **立即执行**：修复卷蓝图问题
   - 检查数据库数据
   - 生成缺失的卷蓝图

3. 🔄 **后续优化**：如果需要更丰富的上下文
   - 考虑实现事件抽取（最有价值）
   - 考虑实现伏笔识别（次优先级）
   - 其他功能按需实现

