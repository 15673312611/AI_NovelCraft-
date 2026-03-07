# AI小说创作系统 - 后台管理系统

## 技术栈

### 前端
- React 18 + TypeScript
- Ant Design 5.x (UI组件库)
- Redux Toolkit (状态管理)
- React Router v6 (路由)
- Axios (HTTP客户端)
- ECharts (数据可视化)

### 后端
- Spring Boot 2.7.18
- Spring Security + JWT
- MyBatis Plus
- MySQL 8.0
- Redis

## 功能模块

### 1. 用户管理
- 用户列表（分页、搜索、筛选）
- 用户详情（基本信息、创作统计、AI使用记录）
- 角色权限管理
- 用户状态管理（启用/禁用）

### 2. 小说管理
- 小说列表（全站小说）
- 小说详情（章节、角色、统计）
- 小说审核
- 批量操作

### 3. AI任务监控
- 任务列表（实时状态）
- 任务详情（输入/输出/错误）
- 失败任务重试
- 成本统计

### 4. 提示词模板管理
- 模板分类管理
- 模板CRUD
- 版本历史
- 使用统计

### 5. 七猫数据管理
- 爬虫任务管理
- 数据查看与导出
- 爬取日志

### 6. 系统配置
- AI配置（API Key、模型选择）
- 爬虫配置
- 系统参数

### 7. 数据统计
- 用户活跃度
- AI使用量统计
- 成本分析
- 创作数据统计

### 8. 日志管理
- 操作日志
- 错误日志
- 登录日志

## 目录结构

```
admin/
├── frontend/                 # 前端项目
│   ├── src/
│   │   ├── components/      # 通用组件
│   │   ├── pages/           # 页面组件
│   │   │   ├── Dashboard/   # 仪表盘
│   │   │   ├── Users/       # 用户管理
│   │   │   ├── Novels/      # 小说管理
│   │   │   ├── AITasks/     # AI任务
│   │   │   ├── Templates/   # 模板管理
│   │   │   ├── Qimao/       # 七猫数据
│   │   │   ├── System/      # 系统配置
│   │   │   └── Logs/        # 日志管理
│   │   ├── services/        # API服务
│   │   ├── store/           # Redux store
│   │   ├── utils/           # 工具函数
│   │   ├── types/           # TypeScript类型
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
│
└── backend/                  # 后端项目
    └── src/main/java/com/novel/admin/
        ├── controller/       # 控制器
        ├── service/          # 服务层
        ├── mapper/           # 数据访问层
        ├── entity/           # 实体类
        ├── dto/              # 数据传输对象
        └── config/           # 配置类
```

## 快速开始

### 前端
```bash
cd admin/frontend
npm install
npm run dev
```

### 后端
```bash
cd admin/backend
mvn spring-boot:run
```

## 访问地址
- 前端：http://localhost:5174
- 后端API：http://localhost:8080/admin
