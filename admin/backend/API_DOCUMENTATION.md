# 后台管理系统 API 文档

## 基础信息

- **Base URL**: `/admin`
- **认证方式**: JWT Token (Bearer)
- **响应格式**: JSON

## API 列表

### 1. 认证接口

#### 1.1 登录
```
POST /auth/login
```
**请求体**:
```json
{
  "username": "admin",
  "password": "password"
}
```
**响应**:
```json
{
  "token": "jwt_token_here",
  "username": "admin"
}
```

---

### 2. 仪表盘接口

#### 2.1 获取统计数据
```
GET /dashboard/stats
```
**响应**:
```json
{
  "totalUsers": 1248,
  "totalNovels": 856,
  "totalAITasks": 3147,
  "totalCost": 2847.50
}
```

#### 2.2 获取用户增长趋势
```
GET /dashboard/user-trend?days=30
```

#### 2.3 获取AI任务统计
```
GET /dashboard/ai-task-stats
```

#### 2.4 获取最近任务
```
GET /dashboard/recent-tasks?limit=10
```

---

### 3. 用户管理接口

#### 3.1 获取用户列表
```
GET /users?keyword=&page=1&size=10
```
**响应**:
```json
{
  "records": [
    {
      "id": 1,
      "username": "user1",
      "email": "user1@example.com",
      "role": "USER",
      "status": "ACTIVE",
      "novelCount": 5,
      "aiTaskCount": 20,
      "createdAt": "2024-01-01T00:00:00"
    }
  ],
  "total": 100,
  "size": 10,
  "current": 1
}
```

#### 3.2 获取用户详情
```
GET /users/{id}
```

#### 3.3 创建用户
```
POST /users
```
**请求体**:
```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "password123",
  "role": "USER",
  "status": "ACTIVE"
}
```

#### 3.4 更新用户
```
PUT /users/{id}
```

#### 3.5 删除用户
```
DELETE /users/{id}
```

#### 3.6 获取用户统计
```
GET /users/{id}/stats
```

---

### 4. 小说管理接口

#### 4.1 获取小说列表
```
GET /novels?keyword=&page=1&size=10
```
**响应**:
```json
{
  "records": [
    {
      "id": 1,
      "title": "小说标题",
      "author": "作者名",
      "genre": "玄幻",
      "status": "ONGOING",
      "chapterCount": 100,
      "wordCount": 200000,
      "createdAt": "2024-01-01T00:00:00"
    }
  ],
  "total": 856,
  "size": 10,
  "current": 1
}
```

#### 4.2 获取小说详情
```
GET /novels/{id}
```

#### 4.3 删除小说
```
DELETE /novels/{id}
```

#### 4.4 获取小说统计
```
GET /novels/{id}/stats
```
**响应**:
```json
{
  "chapterCount": 100,
  "wordCount": 200000,
  "characterCount": 15
}
```

---

### 5. AI任务管理接口

#### 5.1 获取AI任务列表
```
GET /ai-tasks?status=&page=1&size=10
```
**响应**:
```json
{
  "records": [
    {
      "id": 1,
      "name": "章节生成 - 第15章",
      "type": "CHAPTER_GENERATION",
      "status": "COMPLETED",
      "progress": 100,
      "cost": 0.50,
      "username": "user1",
      "createdAt": "2024-01-01T00:00:00",
      "completedAt": "2024-01-01T00:05:00"
    }
  ],
  "total": 3147,
  "size": 10,
  "current": 1
}
```

#### 5.2 获取任务详情
```
GET /ai-tasks/{id}
```

#### 5.3 重试任务
```
POST /ai-tasks/{id}/retry
```

#### 5.4 删除任务
```
DELETE /ai-tasks/{id}
```

#### 5.5 获取任务统计
```
GET /ai-tasks/stats
```
**响应**:
```json
{
  "total": 3147,
  "running": 25,
  "completed": 3000,
  "failed": 122
}
```

---

### 6. 模板管理接口

#### 6.1 获取模板列表
```
GET /templates?category=&page=1&size=10
```
**响应**:
```json
{
  "records": [
    {
      "id": 1,
      "name": "章节生成模板",
      "category": "chapter",
      "content": "模板内容...",
      "usageCount": 150,
      "createdAt": "2024-01-01T00:00:00"
    }
  ],
  "total": 50,
  "size": 10,
  "current": 1
}
```

#### 6.2 获取模板详情
```
GET /templates/{id}
```

#### 6.3 创建模板
```
POST /templates
```
**请求体**:
```json
{
  "name": "新模板",
  "category": "chapter",
  "content": "模板内容..."
}
```

#### 6.4 更新模板
```
PUT /templates/{id}
```

#### 6.5 删除模板
```
DELETE /templates/{id}
```

---

### 7. 系统配置接口

#### 7.1 获取系统配置
```
GET /system/config
```
**响应**:
```json
{
  "openaiApiKey": "",
  "openaiModel": "gpt-4",
  "maxTokens": 4000,
  "temperature": 0.7,
  "qimaoEnabled": false,
  "qimaoInterval": 60,
  "qimaoMaxRetry": 3,
  "maxUploadSize": 10,
  "sessionTimeout": 30
}
```

#### 7.2 保存系统配置
```
POST /system/config
```

---

### 8. 操作日志接口

#### 8.1 获取操作日志
```
GET /logs?username=&startDate=&endDate=&page=1&size=20
```
**响应**:
```json
{
  "records": [
    {
      "id": 1,
      "username": "admin",
      "action": "创建用户",
      "module": "用户管理",
      "ip": "192.168.1.1",
      "createdAt": "2024-01-01T00:00:00"
    }
  ],
  "total": 1000,
  "size": 20,
  "current": 1
}
```

#### 8.2 获取日志统计
```
GET /logs/stats
```
**响应**:
```json
{
  "todayCount": 150,
  "activeUsers": 25
}
```

---

## 状态码说明

- `200`: 成功
- `400`: 请求参数错误
- `401`: 未授权
- `403`: 无权限
- `404`: 资源不存在
- `500`: 服务器错误

## 数据库表说明

### 主要表结构

1. **users** - 用户表
2. **novels** - 小说表
3. **chapters** - 章节表
4. **ai_tasks** - AI任务表
5. **prompts** - 提示词模板表
6. **characters** - 角色表
7. **novel_outlines** - 小说大纲表
8. **chapter_plans** - 章节规划表

---

**更新时间**: 2024年12月
**版本**: v1.0
