# 滚动写作模式

## 核心逻辑

```
第1章：生成惊艳开局
    ↓
写完 → 评估叙事状态（主线进度、主角困境、悬念）
    ↓
规划接下来2章章纲（目标驱动，不是写死剧情点）
    ↓
按章纲写作
    ↓
写完 → 评估 → 规划下2章 → 循环
```

## API接口

只有一个接口：

```bash
POST /api/rolling/{novelId}/write
Content-Type: application/json

{
  "count": 5,              // 要写几章
  "provider": "deepseek",
  "apiKey": "xxx",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com"  // 可选
}
```

返回SSE流，事件类型：
- `start` - 开始写作
- `chapter_start` - 开始某章
- `phase` - 当前阶段（评估/规划/写作）
- `content` - 章节内容（流式）
- `chapter_done` - 某章完成
- `complete` - 全部完成
- `error` - 错误

## 与传统模式的区别

| 传统模式 | 滚动模式 |
|---------|---------|
| 一次性生成35章章纲 | 每次只规划2章 |
| 章纲写死剧情点 | 章纲是目标驱动 |
| 前文伏笔容易丢失 | 每次评估叙事状态 |
| 剧情僵化 | 灵活发挥 |

## 无需数据库

所有状态在内存中维护，不需要额外建表。
