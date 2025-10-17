# 开发指南

本项目当前栈：Java 8 + Spring Boot 2.7.18 + MyBatis Plus（后端），React 18 + Vite + TypeScript（前端）。

## 环境准备
- Java 8（建议 AdoptOpenJDK 8u 系列）
- Node.js 18+
- MySQL 8.0+
- Redis 6/7
- Maven 3.8+

## 本地启动
### 1) 数据库
- 创建库：`novel_creation`，字符集 `utf8mb4`，排序 `utf8mb4_unicode_ci`
- 初始化：`mysql -u root -p novel_creation < scripts/init.sql`

### 2) 后端
```bash
cd backend
mvn spring-boot:run
```
默认端口：`http://localhost:8080`

### 3) 前端
```bash
cd frontend
npm install
npm run dev
```
默认端口：`http://localhost:3000`（通过 Nginx 或开发代理转发到 /api）

## 代码风格与响应规范
- 建议统一响应模型为 `ApiResponse<T>` 或 `Result<T>`（二选一），并提供全局异常处理
- Controller 仅做入参/出参与鉴权，业务沉淀到 Service
- DTO 显式校验注解（javax.validation）

## 常见问题（当前仓库现状）
- AIWritingController 使用 `@RequestParam`，前端以 JSON 发送（不一致）
  - 方案：后端改 `@RequestBody` DTO，或前端改为 Form/URLSearchParams
- 存在两个 `/volumes` 控制器（路由/职责重叠）
  - 方案：合并或调整路径（详见 docs/DESIGN_ISSUES.md）
- AITestController 为测试用途，建议仅 dev 环境可见

## 建议的开发分支流程
- feature/*：功能分支
- fix/*：修复分支
- release/*：发布分支
- 使用 PR + 代码审查；CI 执行构建与基础检查（lint/test）

