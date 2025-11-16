# 图谱数据排查指南

## 问题现象
生成15章后，章节的`generation_context`中图谱数据仍为空。

## 原因分析

### 1. 实体抽取未执行或失败
**症状**：日志中没有 "🔬 抽取实体中..." 或有 "❌ 实体抽取失败" 错误

**可能原因**：
- AI配置无效（API Key过期、模型不可用）
- 实体抽取提示词解析失败
- 异步任务被中断

**排查**：
```bash
# 查看后端日志
tail -f backend/logs/novel-creation-system.log | grep "实体抽取"
```

**解决**：
- 确认AI配置有效（检查`aiConfig`中的`provider`、`model`、`apiKey`）
- 重新生成章节会触发实体抽取重试

### 2. Neo4j未启动或连接失败
**症状**：日志中有 "Neo4j查询失败" 或 "ConditionalOnBean(Driver.class) not met"

**可能原因**：
- Neo4j容器未启动
- 连接配置错误（地址、端口、认证）
- 防火墙阻止连接

**排查**：
```bash
# 检查Neo4j容器状态
docker ps | grep neo4j

# 测试连接
curl http://localhost:7474
```

**解决**：
```bash
# 启动Neo4j
docker-compose up -d neo4j

# 检查配置
# backend/src/main/resources/application.yml
spring:
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: your_password
```

### 3. 数据尚未入图（前5章正常）
**症状**：前几章图谱为空，5章之后才有数据

**原因**：
- 实体抽取是异步执行的，需要时间累积
- 图谱需要多章数据才能形成有效的因果/关系网络

**判断**：
- 第1-5章图谱为空：正常
- 第15章仍为空：异常

## 健康检查

系统已内置图谱健康检查，会在每次生成时自动检测并输出日志：

```
✅ 图谱健康检查：第15章成功加载23个图谱实体
   - 历史事件: 8 个（含因果关系: 5 个）
   - 待回收伏笔: 3 个
   - 冲突弧线: 2 个
```

或：

```
⚠️ 图谱健康检查：第15章未检索到任何图谱数据
   可能原因：1) 实体抽取失败 2) Neo4j未启动或连接失败 3) 数据尚未入图
   建议：检查日志中是否有实体抽取错误，或运行 docker-compose up neo4j 启动图数据库
```

## 图谱关系网络

优化后的图谱上下文会展示：

### 事件因果链
```
【图谱上下文】

## 相关历史事件（因果网络）
- [第8章] 主角获得神秘力量 | 参与者：林默, 神秘老人 | 情绪：tense 
  | ⬅️ 前因：遭遇生命危机 
  | ➡️ 后果：触发诡异觉醒; 引来敌对势力关注
```

### 角色关系网
（通过`GetCharacterRelationshipsTool`获取，AI可主动调用）
```
- 林默 ↔ 苏晴：ROMANCE (强度0.8) - 暗生情愫，但未明确表白
- 林默 ↔ 张伟：CONFLICT (强度0.9) - 因资源争夺产生激烈对抗
```

## 查看已存储的上下文快照

```sql
-- 查看某章的完整上下文
SELECT 
    id, 
    chapter_number, 
    JSON_PRETTY(generation_context) 
FROM chapters 
WHERE novel_id = ? AND chapter_number = 15;
```

## 最佳实践

1. **启动Neo4j**（推荐但非必需）
   ```bash
   docker-compose up -d neo4j
   ```

2. **验证实体抽取**
   - 查看日志确认每章都有 "✅ 实体抽取完成"
   - 如有失败，系统会自动记录到重试队列

3. **对比上下文**
   - 查询第6、7章的`generation_context`
   - 如果JSON完全相同，说明ReAct决策出现了重复
   - 如果只是最近章节相同，说明正常（最近章节本来就是连续的）

4. **AI检测规避**
   - 图谱关系网络提供了更丰富的上下文差异
   - 因果链和角色关系让每章的剧情发展更独特
   - 可适当调整`temperature`参数（0.8-1.2）增加随机性

