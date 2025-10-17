# 死代码/未使用代码与导入清单

说明：以下为当前快照下通过静态检视与代码引用检索得到的“未被使用/疑似未使用”项，执行删除前请以 IDE/CI 工具（如 `mvn -DskipTests dependency:analyze`、TS 编译提示、全局搜索）复核。

## 前端
- frontend/src/services/aiWorkflowService.ts
  - 结论：未在页面或组件中发现 import/使用（代码检索未命中）。
  - 影响：移除将不影响当前页面功能；若未来启用 AIWorkflowController 则可按需恢复。
  - 建议：标记为候选删除；或压缩为极简适配器，待后端能力完善后再扩展。

## 后端
- backend/src/main/java/com/novel/controller/AIWorkflowController.java
  - 方法：`buildWritingStatus(Novel)`（约 223 行起）未被本类内部调用。
  - 建议：如无外部调用（包内/测试），删除或合并到 `getWritingStatus` 实现。

- backend/src/main/java/com/novel/controller/VolumeController.java
  - 字段：`private com.novel.service.NovelService novelService;`（约 50 行）未使用。
  - 建议：删除未用注入，或在后续实现中使用。

- backend/pom.xml
  - 依赖：`org.seleniumhq.selenium:selenium-java:4.15.0`
  - 结论：业务无浏览器自动化需求，疑似未使用。
  - 建议：`mvn dependency:tree` 与 `mvn dependency:analyze` 确认无引用后移除。

- 后端空包/目录
  - `backend/src/main/java/com/novel/douyin`（空目录）
  - 建议：删除空包，减少噪音。

## 其他
- 文档与代码不一致项已在 README 与 docs/* 更正；旧描述可视为“概念性死文档”。

---

# 清理步骤（建议）
1. 运行静态分析：
   - Java：`mvn -q -DskipTests dependency:analyze`、`spotbugs`（如配置）
   - TS：开启 `noUnusedLocals`、`noUnusedParameters` 并执行 `tsc --noEmit`
2. 提交 PR：
   - 单独提交“删除未使用代码/依赖”的 PR，变更尽量原子化，便于回滚。
3. 发布前验证：
   - 构建、启动、关键路径冒烟测试（创建小说、生成章节、AI任务）。

