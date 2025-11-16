# 📮 Postman测试指南 - Agentic AI写作系统

## 🎯 批量生成10章测试

---

## 1. 接口信息

### **接口地址**
```
POST http://localhost:8080/api/agentic/generate-chapters-stream
```

### **说明**
- 方法：`POST`
- 端口：`8080`（默认）
- 路径：`/api/agentic/generate-chapters-stream`
- 响应：`SSE流式响应`（Server-Sent Events）

---

## 2. Postman配置

### **Step 1: 创建请求**

1. 打开Postman
2. 点击 `New` → `HTTP Request`
3. 设置方法为 `POST`
4. 输入URL：`http://localhost:8080/api/agentic/generate-chapters-stream`

### **Step 2: 设置Headers**

点击 `Headers` 标签，添加：

| Key | Value |
|-----|-------|
| Content-Type | application/json |
| Accept | text/event-stream |

### **Step 3: 设置Body**

点击 `Body` 标签 → 选择 `raw` → 选择 `JSON`

---

## 3. 请求参数（JSON格式）

### **完整参数示例（生成10章）**

```json
{
  "novelId": 1,
  "startChapter": 1,
  "count": 10,
  "userAdjustment": "正常推进剧情",
  "aiConfig": {
    "provider": "openai",
    "model": "gpt-4o-mini",
    "apiKey": "sk-your-api-key-here",
    "baseUrl": "https://api.openai.com"
  }
}
```

### **参数说明**

| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| novelId | number | ✅ 是 | 小说ID（必须已有大纲和卷蓝图） | 1 |
| startChapter | number | ❌ 否 | 起始章节号（默认1） | 1 |
| count | number | ❌ 否 | 生成章节数量（默认1） | 10 |
| userAdjustment | string | ❌ 否 | 用户创作要求 | "主角突破筑基期" |
| aiConfig | object | ✅ 是 | AI配置 | 见下方 |

### **aiConfig配置说明**

| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| provider | string | ❌ 否 | AI提供商（默认openai） | "openai" |
| model | string | ❌ 否 | 模型名称 | "gpt-4o-mini" 或 "gpt-4" |
| apiKey | string | ✅ **是** | OpenAI API密钥 | "sk-..." |
| baseUrl | string | ❌ 否 | API地址（默认OpenAI官方） | "https://api.openai.com" |

---

## 4. 不同场景的请求示例

### **场景1：生成单章（最简配置）**

```json
{
  "novelId": 1,
  "startChapter": 5,
  "aiConfig": {
    "apiKey": "sk-your-api-key-here"
  }
}
```

### **场景2：生成10章（批量）**

```json
{
  "novelId": 1,
  "startChapter": 1,
  "count": 10,
  "aiConfig": {
    "model": "gpt-4o-mini",
    "apiKey": "sk-your-api-key-here"
  }
}
```

### **场景3：特定要求生成（战斗章节）**

```json
{
  "novelId": 1,
  "startChapter": 15,
  "count": 3,
  "userAdjustment": "主角与反派大战，最终主角险胜并突破",
  "aiConfig": {
    "model": "gpt-4",
    "apiKey": "sk-your-api-key-here"
  }
}
```

### **场景4：使用国内API（如DeepSeek、通义千问）**

```json
{
  "novelId": 1,
  "startChapter": 1,
  "count": 5,
  "aiConfig": {
    "provider": "openai",
    "model": "deepseek-chat",
    "apiKey": "your-deepseek-api-key",
    "baseUrl": "https://api.deepseek.com"
  }
}
```

---

## 5. SSE响应格式

### **响应示例**

```
event: phase
data: 🧠 AI思考中：分析需要哪些信息...

event: decision
data: 
【AI决策过程】
Step 1: getOutline
  思考: 需要了解小说的整体大纲

event: phase
data: 📝 开始写作...

event: content
data: 李青缓缓睁开双眼

event: content
data: ，体内灵力如江河奔涌

event: content
data: ...

event: complete
data: ✅ 生成完成！共 3245 字

event: extraction
data: ✅ 实体抽取完成

event: chapter_start
data: 开始生成第 2 章 (2/10)

... (重复上述过程)

event: batch_complete
data: 批量生成完成！共生成 10 章
```

### **SSE事件类型**

| 事件名 | 说明 | 数据示例 |
|--------|------|----------|
| phase | 当前阶段 | "🧠 AI思考中" |
| decision | AI决策过程 | "Step 1: getOutline..." |
| content | 章节内容（流式） | "李青缓缓睁开双眼" |
| complete | 单章完成 | "✅ 生成完成！" |
| extraction | 实体抽取状态 | "✅ 实体抽取完成" |
| chapter_start | 批量生成：下一章开始 | "开始生成第2章" |
| batch_complete | 批量生成：全部完成 | "共生成10章" |
| error | 错误信息 | "生成失败: ..." |

---

## 6. Postman接收SSE响应

### **方法1：查看原始响应**

1. 发送请求后
2. 点击 `Response` 下方的 `Preview` 或 `Raw`
3. 会看到SSE事件流式到达
4. **注意**：Postman可能不会实时显示，需要等请求完成

### **方法2：使用浏览器测试（推荐）**

创建一个简单的HTML测试页面：

```html
<!DOCTYPE html>
<html>
<head>
    <title>SSE测试</title>
    <style>
        #output { 
            white-space: pre-wrap; 
            font-family: monospace; 
            padding: 20px;
            background: #f5f5f5;
        }
        .event { margin: 5px 0; }
        .phase { color: blue; font-weight: bold; }
        .content { color: green; }
        .error { color: red; font-weight: bold; }
    </style>
</head>
<body>
    <h1>Agentic AI写作测试</h1>
    <button onclick="startGeneration()">开始生成10章</button>
    <div id="output"></div>

    <script>
        function startGeneration() {
            const output = document.getElementById('output');
            output.innerHTML = '连接中...\n';

            const eventSource = new EventSource('http://localhost:8080/api/agentic/generate-chapters-stream?' + 
                new URLSearchParams({
                    // 注意：SSE通过POST不太方便，建议后端也支持GET
                    // 或使用fetch API手动处理
                }));

            eventSource.addEventListener('phase', (e) => {
                output.innerHTML += `<div class="event phase">[阶段] ${e.data}</div>`;
            });

            eventSource.addEventListener('content', (e) => {
                output.innerHTML += `<span class="content">${e.data}</span>`;
            });

            eventSource.addEventListener('complete', (e) => {
                output.innerHTML += `<div class="event">${e.data}</div>`;
            });

            eventSource.addEventListener('error', (e) => {
                output.innerHTML += `<div class="event error">[错误] ${e.data}</div>`;
                eventSource.close();
            });
        }
    </script>
</body>
</html>
```

### **方法3：使用curl命令行测试**

```bash
curl -X POST http://localhost:8080/api/agentic/generate-chapters-stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "novelId": 1,
    "startChapter": 1,
    "count": 10,
    "aiConfig": {
      "apiKey": "sk-your-api-key-here"
    }
  }' \
  --no-buffer
```

**注意**：`--no-buffer` 参数确保实时显示SSE流

---

## 7. 常见问题

### **Q1: Postman显示"Could not get response"**
**A**: SSE响应可能需要等待较长时间（50-180秒/章），Postman可能超时。建议：
- 增加Postman的超时时间（Settings → General → Request timeout）
- 或使用浏览器/curl测试

### **Q2: 返回401/403错误**
**A**: 检查：
- API Key是否正确
- API Key是否有余额
- baseUrl是否正确

### **Q3: 返回"小说不存在"**
**A**: 确保：
- novelId对应的小说已创建
- 小说已有确认的大纲（novels.outline字段）
- 小说已有卷蓝图（volumes表有数据）

### **Q4: 生成速度很慢**
**A**: 正常现象，预估时间：
- 单章：50-180秒
- 10章：500-1800秒（8-30分钟）
- 每章间有2秒间隔，避免API限流

### **Q5: 如何查看生成的章节？**
**A**: 查询数据库：
```sql
SELECT * FROM novel_documents 
WHERE novel_id = 1 
ORDER BY sort_order DESC 
LIMIT 10;
```

### **Q6: 如何查看图谱数据？**
**A**: 如果Neo4j已启用，可以在Neo4j Browser中查询：
```cypher
// 查看所有事件
MATCH (e:Event {novelId: 1}) 
RETURN e 
ORDER BY e.chapterNumber 
LIMIT 10

// 查看因果关系
MATCH (e1:Event)-[r:CAUSES]->(e2:Event)
WHERE e1.novelId = 1
RETURN e1.summary, r, e2.summary
LIMIT 10
```

---

## 8. 测试前准备清单

### **必须完成的准备**：

- [ ] 1. 确保后端服务已启动（`java -jar xxx.jar`）
- [ ] 2. 确保MySQL已启动并有数据
- [ ] 3. 在数据库中创建测试小说（novels表）
- [ ] 4. 为测试小说生成并确认大纲
- [ ] 5. 为测试小说创建卷蓝图（volumes表）
- [ ] 6. 准备有效的OpenAI API Key
- [ ] 7. 确认API Key有足够余额

### **可选准备**：

- [ ] 8. 启动Neo4j（如需图谱功能）
- [ ] 9. 配置application-neo4j.yml
- [ ] 10. 启用Neo4j配置（`--spring.profiles.active=neo4j`）

---

## 9. 完整测试流程示例

### **Step 1: 创建测试小说（SQL）**

```sql
-- 创建小说
INSERT INTO novels (title, author, genre, description, outline, status, created_at, updated_at)
VALUES (
    '修仙之路',
    '测试作者',
    '玄幻修仙',
    '一个少年的修仙之路',
    '第一卷：入门篇\n主角李青在山村意外获得修仙功法，踏上修仙之路...\n\n第二卷：历练篇\n主角下山历练，遭遇各种危机与机遇...',
    'PUBLISHED',
    NOW(),
    NOW()
);

-- 查询小说ID
SELECT id FROM novels WHERE title = '修仙之路';
-- 假设返回 novelId = 123

-- 创建卷蓝图
INSERT INTO volumes (novel_id, volume_number, volume_title, start_chapter, end_chapter, blueprint, status, created_at, updated_at)
VALUES (
    123,
    1,
    '第一卷：入门篇',
    1,
    20,
    '第1-5章：主角在山村的平凡生活，铺垫\n第6-10章：意外获得修仙功法\n第11-15章：初步修炼，遇到第一个危机\n第16-20章：化解危机，小有成就',
    'PLANNING',
    NOW(),
    NOW()
);
```

### **Step 2: Postman发送请求**

```json
{
  "novelId": 123,
  "startChapter": 1,
  "count": 10,
  "aiConfig": {
    "model": "gpt-4o-mini",
    "apiKey": "sk-your-real-api-key-here"
  }
}
```

### **Step 3: 观察响应**

等待8-30分钟，观察SSE事件流。

### **Step 4: 验证结果**

```sql
-- 查看生成的章节
SELECT id, title, LENGTH(content) as word_count, created_at
FROM novel_documents
WHERE novel_id = 123
ORDER BY sort_order;

-- 应该看到10条记录
```

---

## 10. 性能优化建议

### **提升生成速度**：

1. **使用更快的模型**：
   - `gpt-3.5-turbo` 或 `gpt-4o-mini`（更快更便宜）
   - 而不是 `gpt-4`（更慢更贵）

2. **减少Token消耗**：
   - 减少最近章节数量（修改配置）
   - 简化ReAct循环步数

3. **并行生成**（需要代码修改）：
   - 多个章节并行调用API
   - 但要注意API限流

---

## ✅ 总结

**最简单的测试配置**：

```json
{
  "novelId": 1,
  "startChapter": 1,
  "count": 10,
  "aiConfig": {
    "apiKey": "sk-xxxxx"
  }
}
```

**关键点**：
- ✅ 必须提供`novelId`和`aiConfig.apiKey`
- ✅ 小说必须已有大纲和卷蓝图
- ✅ 使用SSE接收流式响应
- ✅ 预留足够的超时时间（10分钟+）

**推荐测试顺序**：
1. 先测试单章（`count: 1`）
2. 再测试3章（`count: 3`）
3. 最后测试10章（`count: 10`）

祝测试顺利！🎉

