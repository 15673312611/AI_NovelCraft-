# 设计问题清单（发现项、影响与建议）

更新时间：当前代码库快照

## 1) 路由与职责重叠（/volumes 重复 Controller）
- 位置：
  - backend/src/main/java/com/novel/controller/VolumeController.java:28-31
  - backend/src/main/java/com/novel/controller/NovelVolumeController.java:25-28
- 问题：两个控制器均使用 @RequestMapping("/volumes")，存在路由冲突风险与职责重叠。
- 影响：
  - 运行时路由解析歧义；维护成本上升；API 行为不可预测。
- 建议：
  - 方案A：合并为单一 VolumeController，保留 NovelVolumeController 的职责边界；
  - 方案B：保留 NovelVolumeController，将 VolumeController 路径调整为 /volume-tools 或纳入 /novels/{novelId}/volumes 子资源。

## 2) 响应包装不一致（ApiResponse 与 Result 并存）
- 位置：
  - backend/src/main/java/com/novel/common/ApiResponse.java
  - backend/src/main/java/com/novel/common/Result.java
  - 示例调用不一致：
    - 使用 ApiResponse：VolumeController.java 第118,131,316,350行等
    - 使用 Result：AIWorkflowController.java 第53,81,108,135,163行等
- 影响：前端解析复杂度提升，接口风格不统一，难以抽象全局拦截器。
- 建议：统一为单一响应模型（推荐 ApiResponse<T> 或 Result<T> 其一），提供适配层过渡，逐步收敛。

## 3) 占位/模拟实现未标注
- 位置与说明：
  - backend/controller/AIWorkflowController.java：
    - 173,196,209 行附近已添加 TODO，当前为占位逻辑，应下沉至 Service 并接入真实 AI 能力。
  - backend/controller/AITestController.java：
    - 33,60,141,239 行附近已添加 TODO，建议仅在 dev 环境启用。
  - frontend/src/services/aiWorkflowService.ts：
    - 133,161,187,208 行附近已添加 TODO，当前为前端模拟/聚合逻辑。
- 影响：容易被误用为生产功能；行为与文档不一致。
- 建议：
  - 使用 @Profile("dev") 或网关策略限制；
  - 为占位方法补充 @Deprecated 注释与日志警告；
  - 在迭代中替换为真实实现。

## 4) 前后端参数风格不一致（AIWriting 接口）
- 位置：
  - 前端：frontend/src/services/aiWritingService.ts（POST JSON）
  - 后端：backend/controller/AIWritingController.java（@RequestParam 方式）
- 影响：请求无法被后端正确解析，功能不可用。
- 建议（两选一）：
  - 后端改为 @RequestBody DTO；
  - 前端改为 URLSearchParams/FormData。已在前端服务处添加 TODO 提示。

## 5) 可能的无用/过时依赖
- 位置：backend/pom.xml 第28-32行（selenium-java 4.15.0）
- 影响：增加打包体积与安全面。
- 建议：如非必须，使用 Maven 移除：mvn dependency:tree 确认无引用后删除。

## 6) 文档与实现不一致
- 位置：README.md
  - 将后端栈描述为“Java 17 + Spring Boot 3 + JPA”，实际是 Java 8 + Spring Boot 2.7 + MyBatis Plus。
  - 已修正后端技术栈与 Java 版本（41-47, 83行）。
- 影响：误导开发者与部署人员。
- 建议：以本仓库 docs/* 为基准输出权威文档，并在 README 链接。

## 7) 可疑空目录/死代码
- 位置：backend/src/main/java/com/novel/douyin（空）
- 影响：目录噪音。
- 建议：直接删除空包（确认 Git 历史无用途）。

## 8) 未使用的注入或方法
- 位置示例：
  - VolumeController.java:50 行（novelService 未使用）
  - AIWorkflowController.java:223-251 buildWritingStatus() 未被调用
- 影响：增加维护噪音，迷惑读者。
- 建议：删除未用字段/方法或在后续使用前标记 TODO。

## 9) 接口一致性与使用情况
- 结论：
  - 前端 aiTaskService ⇄ 后端 AITaskController：一致，有实际使用（AIControlPanelPage）。
  - 前端 multiAIService ⇄ 后端 MultiAIController：一致，功能接口齐全。
  - 前端 aiWritingService ⇄ 后端 AIWritingController：存在参数风格不一致（见问题4）。
  - 前端 aiWorkflowService ⇄ 后端 AIWorkflowController：接口存在，但前端页面未发现直接使用（SmartNovelWriter/NovelAIWritingPage/AIControlPanelPage 未引用）。
- 建议：
  - 对 aiWorkflowService 进行全局引用检查（grep 全仓库），若确实未用，标记为候选删除；
  - 为所有接口补充 swagger 文档并生成 OpenAPI 规范，统一前后端契约。

---

# 重构建议（摘要）
1. 路由合并：/volumes 统一到单一 Controller（3-5个提交）。
2. 响应格式统一：在网关/全局响应拦截层适配，控制器迁移到单一响应类（分阶段替换）。
3. AIWriting 接口对齐：先改后端接收 @RequestBody DTO（最小改动），再清理前端冗余参数。
4. 去依赖：移除 selenium-java；执行 dependency:analyze 校验。
5. 文档基线：以 docs/ARCHITECTURE.md, API.md, DEVELOPMENT.md, DEPLOYMENT.md 为权威来源；README 简化并链接。
6. 死代码清理：删除空包 douyin；移除未用字段与私有方法；保留必要 TODO 注解。

