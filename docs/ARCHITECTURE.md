# 项目架构说明

本说明基于仓库现状（Spring Boot 2.7 + Java 8 + MyBatis Plus + React 18 + Vite），用于指导开发与重构。

## 总览
- 前端：React 18 + TypeScript，Ant Design，Redux Toolkit，Axios（frontend/）
- 后端：Spring Boot 2.7.18（Java 8），Spring Security（JWT），MyBatis Plus，Redis，OpenAI Java Client（backend/）
- 数据库：MySQL 8.0（scripts/init.sql）
- 部署：Docker Compose + Nginx 反向代理（docker-compose.yml）

## 逻辑分层（后端）
- Controller 层：暴露 REST API，当前部分存在业务逻辑下沉（需上移到 Service）
- Service 层：业务编排与领域逻辑（建议承接 AI 编排、多 AI 协作、写作流程）
- Mapper 层：MyBatis Plus 数据访问
- Domain/Entity：实体与值对象

## 关键模块
- 小说管理：Novel、Chapter、Volume 等资源管理
- AI 写作：AIWritingController 提供写作大纲、章节生成、状态查看等
- AI 工作流：AIWorkflowController 提供分析、建议、质量评审（当前为占位，需要接入真实 AI）
- 多 AI 协作：MultiAIController 提供多角色 AI 的聚合能力
- 进度/伏笔/剧情：在工作流与写作服务中呈现，建议抽象为独立服务便于演进

## 跨领域关注点
- 鉴权：JWT（前端拦截器注入 Authorization），401/403 处理完善
- 统一响应：ApiResponse 与 Result 并存，建议统一（见设计问题清单）
- 日志与审计：建议在网关/切面记录调用链路与 AI 提示词（Prompt）截断版本
- 配置管理：使用 application-*.yml + 环境变量，敏感信息（如 OpenAI Key）通过 .env / Secrets 注入

## 未来演进建议
1. 引入 OpenAPI（springdoc）完善接口契约，前端使用代码生成降低错配
2. 建立 AI 编排 Service（支持提示词模板、模型选择、协作策略、评审流水线）
3. 引入领域事件（进度达成、伏笔触发、剧情转折）与异步任务队列（可选）
4. 统一错误码与响应结构，沉淀网关级告警与熔断策略

