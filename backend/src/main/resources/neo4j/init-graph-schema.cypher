// Neo4j 图谱初始化脚本
// 用于长篇小说上下文管理

// ===========================
// 1. 创建约束（保证数据唯一性）
// ===========================

// 章节唯一性
CREATE CONSTRAINT chapter_unique IF NOT EXISTS
FOR (c:Chapter) REQUIRE (c.novelId, c.number) IS UNIQUE;

// 事件ID唯一性
CREATE CONSTRAINT event_unique IF NOT EXISTS
FOR (e:Event) REQUIRE e.id IS UNIQUE;

// 伏笔ID唯一性
CREATE CONSTRAINT foreshadow_unique IF NOT EXISTS
FOR (f:Foreshadowing) REQUIRE f.id IS UNIQUE;

// 情节线ID唯一性
CREATE CONSTRAINT plotline_unique IF NOT EXISTS
FOR (p:PlotLine) REQUIRE p.id IS UNIQUE;

// 世界规则ID唯一性
CREATE CONSTRAINT worldrule_unique IF NOT EXISTS
FOR (r:WorldRule) REQUIRE r.id IS UNIQUE;

// 角色唯一性
CREATE CONSTRAINT character_unique IF NOT EXISTS
FOR (ch:Character) REQUIRE (ch.novelId, ch.name) IS UNIQUE;

// ===========================
// 2. 创建索引（优化查询性能）
// ===========================

// 小说ID索引
CREATE INDEX novel_id_event IF NOT EXISTS FOR (e:Event) ON (e.novelId);
CREATE INDEX novel_id_foreshadow IF NOT EXISTS FOR (f:Foreshadowing) ON (f.novelId);
CREATE INDEX novel_id_plotline IF NOT EXISTS FOR (p:PlotLine) ON (p.novelId);
CREATE INDEX novel_id_worldrule IF NOT EXISTS FOR (r:WorldRule) ON (r.novelId);
CREATE INDEX novel_id_chapter IF NOT EXISTS FOR (c:Chapter) ON (c.novelId);
CREATE INDEX novel_id_character IF NOT EXISTS FOR (ch:Character) ON (ch.novelId);

// 章节号索引
CREATE INDEX chapter_number_event IF NOT EXISTS FOR (e:Event) ON (e.chapterNumber);
CREATE INDEX chapter_number IF NOT EXISTS FOR (c:Chapter) ON (c.number);

// 状态索引
CREATE INDEX foreshadow_status IF NOT EXISTS FOR (f:Foreshadowing) ON (f.status);

// 重要性索引
CREATE INDEX event_importance IF NOT EXISTS FOR (e:Event) ON (e.importance);
CREATE INDEX foreshadow_importance IF NOT EXISTS FOR (f:Foreshadowing) ON (f.importance);
CREATE INDEX worldrule_importance IF NOT EXISTS FOR (r:WorldRule) ON (r.importance);

// 优先级索引
CREATE INDEX plotline_priority IF NOT EXISTS FOR (p:PlotLine) ON (p.priority);

// ===========================
// 3. 示例数据（可选，用于测试）
// ===========================

// 创建测试小说
MERGE (n:Novel {id: 1, title: "测试小说"});

// 创建测试卷
MERGE (v:Volume {id: 1, novelId: 1, title: "第一卷", chapterStart: 1, chapterEnd: 50})
MERGE (n)-[:HAS_VOLUME]->(v);

// 创建测试章节
MERGE (c1:Chapter {novelId: 1, number: 1, title: "第一章"})
MERGE (v)-[:HAS_CHAPTER]->(c1);

MERGE (c2:Chapter {novelId: 1, number: 2, title: "第二章"})
MERGE (v)-[:HAS_CHAPTER]->(c2);

MERGE (c3:Chapter {novelId: 1, number: 3, title: "第三章"})
MERGE (v)-[:HAS_CHAPTER]->(c3);

// 创建测试事件
MERGE (e1:Event {
  id: "event_1_1",
  novelId: 1,
  chapterNumber: 1,
  summary: "主角离开家乡",
  description: "主角因为家族变故，不得不离开生活了十八年的家乡",
  participants: ["主角", "父亲"],
  emotionalTone: "sad",
  tags: ["离别", "转折"],
  importance: 0.9
})
MERGE (c1)-[:CONTAINS_EVENT]->(e1);

MERGE (e2:Event {
  id: "event_1_2",
  novelId: 1,
  chapterNumber: 2,
  summary: "初遇神秘老人",
  description: "主角在路上遇到一位神秘老人，老人给了他一本古籍",
  participants: ["主角", "神秘老人"],
  emotionalTone: "mysterious",
  tags: ["奇遇", "关键"],
  importance: 1.0
})
MERGE (c2)-[:CONTAINS_EVENT]->(e2);

MERGE (e3:Event {
  id: "event_1_3",
  novelId: 1,
  chapterNumber: 3,
  summary: "修炼古籍功法",
  description: "主角按照古籍开始修炼，发现体内有异样",
  participants: ["主角"],
  emotionalTone: "excited",
  tags: ["修炼", "成长"],
  importance: 0.8
})
MERGE (c3)-[:CONTAINS_EVENT]->(e3);

// 创建因果关系
MERGE (e2)-[:TRIGGERS]->(e3);
MERGE (e1)-[:TRIGGERS]->(e2);

// 创建伏笔
MERGE (f1:Foreshadowing {
  id: "foreshadow_1_1",
  novelId: 1,
  content: "神秘老人临走前说：三年后再见",
  importance: "high",
  status: "PLANTED",
  plannedRevealChapter: 50
})
MERGE (e2)-[:PLANTS]->(f1)
MERGE (f1)-[:PLANTED_IN]->(c2);

// 创建情节线
MERGE (p1:PlotLine {
  id: "plotline_main",
  novelId: 1,
  name: "主线：成长之路",
  priority: 1.0
})
MERGE (p1)-[:INCLUDES]->(e1)
MERGE (p1)-[:INCLUDES]->(e2)
MERGE (p1)-[:INCLUDES]->(e3);

// 创建世界规则
MERGE (r1:WorldRule {
  id: "rule_power_system",
  novelId: 1,
  name: "修炼体系",
  content: "修炼分为：练气、筑基、金丹、元婴四大境界",
  constraint: "境界突破需要契机，不能一蹴而就",
  category: "power_system",
  scope: "global",
  importance: 1.0,
  introducedAt: 3
});

// 创建角色
MERGE (ch1:Character {
  id: "char_protagonist",
  novelId: 1,
  name: "主角",
  firstAppearance: 1
});

MERGE (ch2:Character {
  id: "char_老人",
  novelId: 1,
  name: "神秘老人",
  firstAppearance: 2
});

// ===========================
// 4. 清理脚本（谨慎使用！）
// ===========================

// 清空所有数据（仅测试用）
// MATCH (n) DETACH DELETE n;

// 清空特定小说的数据
// MATCH (n) WHERE n.novelId = 1 DETACH DELETE n;


