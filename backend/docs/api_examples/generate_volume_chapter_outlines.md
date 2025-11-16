# 生成卷级章纲接口调用示例

## 接口信息

- **路径**: `POST /api/volumes/{volumeId}/chapter-outlines/generate`
- **功能**: 为指定卷批量生成章节大纲（细纲）
- **参数结构**: 参考 `/agentic/graph/regenerate-metadata` 接口，使用扁平化的 AI 配置参数
- **覆盖行为**: ⚠️ **重复调用会覆盖旧章纲**（先删除该卷所有旧章纲和伏笔日志，再插入新生成的结果）

## 请求示例

### 示例1: 自动计算章数（推荐）

```json
POST /api/volumes/123/chapter-outlines/generate

{
  "provider": "openai",
  "apiKey": "sk-xxx",
  "model": "gpt-4o",
  "baseUrl": "https://api.openai.com/v1",
  "includeDecisionLog": true
}
```

**说明**:
- 不传 `count` 或 `count <= 0` 时，自动按该卷的 `chapterStart/chapterEnd` 动态计算章数
- `provider`: AI 服务商（openai、deepseek、qwen、kimi 等）
- `apiKey`: API 密钥（必填）
- `model`: 模型名称（必填）
- `baseUrl`: API 基础 URL（可选，不传则使用默认值）
- `includeDecisionLog`: 是否在响应中包含 AI 决策日志（默认 false，调试时可设为 true）
- ⚠️ **重复调用会覆盖旧章纲**：生成成功后会先删除该卷所有旧章纲和伏笔日志，再插入新结果

---

### 示例2: 手动指定章数

```json
POST /api/volumes/123/chapter-outlines/generate

{
  "count": 50,
  "provider": "deepseek",
  "apiKey": "sk-xxx",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com",
  "includeDecisionLog": false
}
```

**说明**:
- `count`: 手动指定生成章数（会覆盖动态计算值）
- 适用于需要临时调整章数的场景

---

### 示例3: 使用通义千问

```json
POST /api/volumes/123/chapter-outlines/generate

{
  "provider": "qwen",
  "apiKey": "sk-xxx",
  "model": "qwen-max",
  "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1"
}
```

---

### 示例4: 使用 Kimi

```json
POST /api/volumes/123/chapter-outlines/generate

{
  "provider": "kimi",
  "apiKey": "sk-xxx",
  "model": "moonshot-v1-8k"
}

```

**说明**:
- `baseUrl` 可选，不传则使用默认值

---

## 参数说明

### 请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `provider` | String | 是 | - | AI 服务商：openai、deepseek、qwen、kimi 等 |
| `apiKey` | String | 是 | - | API 密钥 |
| `model` | String | 是 | - | 模型名称（如 gpt-4o、deepseek-chat、qwen-max） |
| `baseUrl` | String | 否 | 根据 provider 自动推断 | API 基础 URL |
| `count` | Integer | 否 | 自动计算 | 生成章数（不传则按卷的 chapterStart/chapterEnd 计算） |
| `includeDecisionLog` | Boolean | 否 | false | 是否在响应中包含 AI 决策日志 |

**注意**:
- `maxTokens` 参数已移除，后端默认使用 16000（足够生成完整章纲）
- `temperature` 参数已移除，后端默认使用 0.8（适合创意写作）

---

## 响应示例

### 成功响应

```json
{
  "volumeId": 123,
  "volumeNumber": 2,
  "volumeTitle": "风云再起",
  "chapterCount": 50,
  "outlines": [
    {
      "chapterInVolume": 1,
      "globalChapterNumber": 51,
      "direction": "主角在拍卖会上竞拍神秘丹药，引发多方势力暗中角力",
      "keyPlotPoints": [
        "拍卖会开场，主角低调入场",
        "神秘丹药出现，引发哄抢",
        "主角出价，暴露部分实力",
        "神秘势力暗中观察主角"
      ],
      "emotionalTone": "紧张、期待、暗流涌动",
      "foreshadowAction": "PLANT",
      "foreshadowDetail": {
        "content": "神秘势力首领在暗处观察主角，似乎认出了主角的某个特征",
        "targetResolveVolume": 3,
        "resolveWindow": {
          "min": 2,
          "max": 4
        },
        "futureAnchorPlan": "在第2卷中期，让该势力首领再次出现，提供更多线索"
      },
      "subplot": "女主角暗中调查主角身份",
      "antagonism": {
        "opponent": "拍卖会幕后势力",
        "conflictType": "利益",
        "intensity": 6
      }
    },
    {
      "chapterInVolume": 2,
      "globalChapterNumber": 52,
      "direction": "主角成功拍得丹药，但遭遇神秘人跟踪",
      "keyPlotPoints": [
        "主角以高价拍得丹药",
        "离开拍卖会时察觉被跟踪",
        "巧妙甩掉跟踪者",
        "回到住处，开始研究丹药"
      ],
      "emotionalTone": "警惕、紧张、好奇",
      "foreshadowAction": "REFERENCE",
      "foreshadowDetail": {
        "refId": 1,
        "content": "跟踪者似乎与之前暗中观察的神秘势力有关"
      },
      "subplot": "女主角也在拍卖会附近，似乎在等待主角",
      "antagonism": {
        "opponent": "跟踪者",
        "conflictType": "生存",
        "intensity": 7
      }
    }
    // ... 更多章节
  ],
  "generatedAt": "2025-11-13T10:30:00Z"
}
```

---

### 响应字段说明

#### 顶层字段
- `volumeId`: 卷ID
- `volumeNumber`: 卷序号
- `volumeTitle`: 卷标题
- `chapterCount`: 生成的章节数
- `outlines`: 章节大纲数组
- `generatedAt`: 生成时间

#### outlines 数组中每个章节的字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `chapterInVolume` | Integer | 卷内章节序号（从1开始） |
| `globalChapterNumber` | Integer | 全书章节序号（从1开始） |
| `direction` | String | 本章剧情方向（1句话） |
| `keyPlotPoints` | Array<String> | 关键剧情点（3-5个） |
| `emotionalTone` | String | 情感基调 |
| `foreshadowAction` | String | 伏笔动作：`NONE` / `PLANT` / `REFERENCE` / `DEEPEN` / `RESOLVE` |
| `foreshadowDetail` | Object | 伏笔详情（见下表） |
| `subplot` | String | 支线剧情 |
| `antagonism` | Object | 对抗关系（见下表） |

#### foreshadowDetail 对象字段

| 字段 | 类型 | 何时必填 | 说明 |
|------|------|----------|------|
| `refId` | Integer | REFERENCE/DEEPEN/RESOLVE | 引用的伏笔ID |
| `content` | String | PLANT | 伏笔内容 |
| `targetResolveVolume` | Integer | PLANT（可选） | 目标揭露卷数 |
| `resolveWindow` | Object | PLANT（可选） | 揭露窗口 `{min, max}` |
| `anchorsUsed` | Array<Object> | RESOLVE（必须≥2个） | 已使用的证据锚点 `[{vol, ch, hint}]` |
| `futureAnchorPlan` | String | PLANT/DEEPEN（建议） | 未来证据锚点计划 |
| `cost` | String | RESOLVE（可选） | 揭露代价 |

#### antagonism 对象字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `opponent` | String | 对手名称 |
| `conflictType` | String | 冲突类型（利益/理念/情感/生存等） |
| `intensity` | Integer | 强度（1-10） |

---

## 错误响应

### 卷不存在

```json
{
  "error": "Volume not found",
  "volumeId": 123
}
```

### 小说不存在

```json
{
  "error": "Novel not found",
  "novelId": 456
}
```

### AI 调用失败

```json
{
  "error": "AI generation failed",
  "message": "Rate limit exceeded"
}
```

---

## 使用流程

1. **创建小说和卷**
   ```
   POST /api/novels → 创建小说
   POST /api/novels/{novelId}/volumes → 创建卷
   ```

2. **生成卷级章纲**
   ```
   POST /api/volumes/{volumeId}/chapter-outlines/generate
   ```

3. **写作章节**
   ```
   POST /api/agentic/generate-chapters-stream
   ```
   - 写作时会自动查询预生成章纲
   - 有章纲：跳过推理，直接用章纲 + 上下文写作
   - 无章纲：回退到实时推理

4. **查询章纲**
   ```
   GET /api/volumes/{volumeId}/chapter-outlines
   ```

---

## 注意事项

1. **章数计算**
   - 不传 `count` 时，自动按 `Volume.chapterStart/chapterEnd` 计算
   - 例如：卷1（1-50章）→ 生成50个章纲，卷2（51-100章）→ 生成50个章纲

2. **伏笔管理**
   - 生成时会自动写入 `foreshadow_lifecycle_log` 表
   - 下一卷生成前会汇总 ACTIVE 伏笔作为上下文

3. **性能**
   - 生成50章章纲约需 30-60 秒（取决于 AI 模型）
   - 建议异步调用，前端轮询或 WebSocket 获取进度

4. **成本**
   - GPT-4o: 约 $0.5-1.0 / 50章
   - Claude-3.5-Sonnet: 约 $0.3-0.6 / 50章

---

## cURL 示例

```bash
# 自动计算章数（使用 OpenAI）
curl -X POST http://localhost:8080/api/volumes/123/chapter-outlines/generate \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "openai",
    "apiKey": "sk-xxx",
    "model": "gpt-4o",
    "baseUrl": "https://api.openai.com/v1",
    "includeDecisionLog": true
  }'

# 手动指定章数（使用 DeepSeek）
curl -X POST http://localhost:8080/api/volumes/123/chapter-outlines/generate \
  -H "Content-Type: application/json" \
  -d '{
    "count": 50,
    "provider": "deepseek",
    "apiKey": "sk-xxx",
    "model": "deepseek-chat",
    "baseUrl": "https://api.deepseek.com"
  }'

# 使用通义千问
curl -X POST http://localhost:8080/api/volumes/123/chapter-outlines/generate \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "qwen",
    "apiKey": "sk-xxx",
    "model": "qwen-max",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1"
  }'
```

