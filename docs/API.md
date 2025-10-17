# API 接口文档（当前实现）

注意：部分端点仍为占位实现或仅用于开发测试，本文已标注。

## 认证
- 统一基路径：/api
- 认证方式：JWT（前端在 Authorization: Bearer <token> 头携带）

## AI 写作（AIWritingController）
- POST /ai-writing/outline
  - 参数：title, genre, style（当前后端用 @RequestParam；前端以 JSON 发送，存在不一致）
  - 返回：写作大纲（示例字段：outline, suggestions）
- POST /ai-writing/chapter
  - 参数：novelId, chapterTitle, goal
  - 返回：章节草稿
- GET /ai-writing/status
  - 参数：novelId
  - 返回：写作状态（currentWordCount, progress 等）

TODO：将上述接口改为 @RequestBody DTO 以统一风格。

## AI 工作流（AIWorkflowController）
- POST /ai-workflow/analysis
  - 参数：novelId, analysisType, userQuery（占位实现）
- POST /ai-workflow/suggestions
  - 参数：novelId, currentContent, writingGoal（占位实现）
- GET /ai-workflow/review
  - 参数：novelId
  - 返回：质量评审结果（占位实现）

标注：已在代码处加 TODO，后续迁移到 Service 并接入真实 AI。

## 多 AI 协作（MultiAIController）
- POST /multi-ai/analyze
  - 参数：novelId, targets
  - 返回：多角色分析聚合
- POST /multi-ai/decide
  - 参数：novelId, options
  - 返回：投票/仲裁结果（若实现）

## 卷/章节管理（VolumeController/NovelVolumeController）
- GET /volumes?novelId=...
- POST /volumes
- PUT /volumes/{id}
- DELETE /volumes/{id}

注意：存在 VolumeController 与 NovelVolumeController 同前缀冲突，建议合并（见 DESIGN_ISSUES）。

## 测试与工具（AITestController）
- GET /ai-test/config （仅 dev）
- POST /ai-test/connection （仅 dev）
- POST /ai-test/writing （仅 dev）
- GET /ai-test/status （仅 dev）

标注：建议使用 @Profile("dev") 或网关限制。

---

# 前端服务与后端端点映射（节选）
- frontend/src/services/aiWritingService.ts ↔ AIWritingController（参数风格不一致，需整改）
- frontend/src/services/aiWorkflowService.ts ↔ AIWorkflowController（前端多为占位聚合逻辑）
- frontend/src/services/multiAIService.ts ↔ MultiAIController（匹配）
- frontend/src/services/aiTaskService.ts ↔ AITaskController（匹配）

# 统一响应规范（建议）
- 采用 ApiResponse<T>（或 Result<T>）统一：
  - { code: number, message: string, data: T, traceId?: string }
- 提供全局异常处理与错误码表。

