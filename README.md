# 📚 AI网文创作系统

<div align="center">

![License](https://img.shields.io/badge/license-Personal%20Use%20Only-blue.svg)
![Java](https://img.shields.io/badge/Java-17+-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7+-green.svg)
![React](https://img.shields.io/badge/React-18+-61dafb.svg)
![TypeScript](https://img.shields.io/badge/TypeScript-5+-blue.svg)

一个智能化的网络小说创作辅助系统，结合多种AI大模型，为作者提供从构思到成稿的全流程创作支持。

[功能特性](#-功能特性) • [技术栈](#-技术栈) • [快速开始](#-快速开始) • [使用说明](#-使用说明) • [开发指南](#-开发指南)

</div>

作者微信:soe303  可以探讨

---

## ✨ 功能特性

### 🎯 核心功能

- **📝 智能大纲生成**
  - AI辅助生成小说超级大纲
  - 支持卷级规划和章节拆分
  - 自动生成卷标题和主题
  - 详细的情节线、角色发展和伏笔设计

- **📖 分卷创作管理**
  - 灵活的卷规划系统
  - 每卷独立的主题和大纲
  - 章节范围自动管理
  - 字数估算和进度追踪

- **✍️ AI流式写作**
  - 实时流式内容生成
  - 支持多轮对话式创作
  - 上下文记忆保持
  - 自动保存和版本管理

- **🎨 AI消痕处理**
  - 智能识别AI痕迹
  - 流式消痕处理
  - 保持文风一致性
  - 提升内容自然度

- **🔍 形容词挖掘**
  - 批量挖掘高质量形容词
  - 按类别分类（外貌、性格、场景等）
  - AI生成、人工审核
  - 词库管理和复用

- **🤖 多AI模型支持**
  - **DeepSeek**：高性价比，适合长文本
  - **通义千问**：阿里云服务，多种规格
  - **Kimi (月之暗面)**：超长上下文（最高262K）
  - 前端配置，灵活切换
  - 支持自定义API地址
  - 
 ### 作者微信:soe303
 
 ### 项目演示
 <img width="2560" height="1398" alt="image" src="https://github.com/user-attachments/assets/3aa8f44a-094c-42f2-99e7-573952ce6b2a" />
 <img width="2560" height="1398" alt="image" src="https://github.com/user-attachments/assets/8e052d39-11f8-4776-b69a-b27b2a72f3ff" />
<img width="2560" height="1398" alt="image" src="https://github.com/user-attachments/assets/90daaaa2-8695-4bba-bad5-457cdce98f98" />


### 🌟 特色亮点

- ✅ **纯前端AI配置**：API密钥仅存储在浏览器，安全可控
- ✅ **实时进度显示**：真实的任务轮询状态，进度一目了然
- ✅ **异步任务管理**：后台AI任务，支持并发处理
- ✅ **智能URL适配**：自动处理不同AI服务商的API格式差异
- ✅ **流式实时输出**：边生成边显示，提供即时反馈
- ✅ **完整的创作流程**：从构思到成稿的全流程支持

---

## 🛠 技术栈

### 后端技术

- **核心框架**：Spring Boot 2.7.x
- **开发语言**：Java 17+
- **数据库**：MySQL 5.7+
- **持久层**：MyBatis Plus
- **AI集成**：RestTemplate + SSE (Server-Sent Events)
- **异步处理**：Spring Async + CompletableFuture

### 前端技术

- **核心框架**：React 18.x
- **开发语言**：TypeScript 5.x
- **UI组件库**：Ant Design 5.x
- **路由管理**：React Router 6.x
- **状态管理**：React Hooks (useState, useEffect)
- **HTTP客户端**：Fetch API
- **构建工具**：Vite

### AI服务支持

- DeepSeek API
- 通义千问（DashScope）API
- Kimi（Moonshot）API
- OpenAI兼容接口

---

## 🚀 快速开始

### 环境要求

- **JDK**: 17 或更高版本
- **Node.js**: 16 或更高版本
- **MySQL**: 5.7 或更高版本
- **Maven**: 3.6 或更高版本

### 安装步骤

#### 1. 克隆项目

```bash
git clone https://github.com/yourusername/novel-creation-system.git
cd novel-creation-system
```

#### 2. 数据库初始化

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE novel_creation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 导入数据库脚本
mysql -u root -p novel_creation < database/schema.sql
```

#### 3. 配置后端

编辑 `backend/src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/novel_creation?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

#### 4. 启动后端

```bash
cd backend
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端将在 `http://localhost:8080` 启动。

#### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端将在 `http://localhost:5173` 启动。

---

## 📖 使用说明

### 1. 配置AI服务

首次使用前，需要在设置页面配置AI服务：

1. 访问 **设置** 页面
2. 选择AI服务商（DeepSeek/通义千问/Kimi）
3. 输入你的 **API Key**
4. 选择使用的 **模型**
5. （可选）自定义 **API Base URL**
6. 点击 **保存配置**

> 💡 **提示**：配置仅保存在浏览器本地缓存，不会上传到服务器。

### 2. 创建小说项目

1. 在首页点击 **创建小说**
2. 填写小说基本信息（标题、类型、简介等）
3. 输入初步构思
4. 点击 **生成大纲**，AI将自动生成详细大纲
5. 审阅并确认大纲

### 3. 生成卷规划

1. 进入 **卷规划** 页面
2. 设置卷数（建议3-6卷）
3. 点击 **按原主题生成所有卷大纲**
4. AI将根据超级大纲自动拆分为各卷，并生成卷标题和主题
5. 查看各卷的实时生成进度

### 4. 开始写作

1. 选择要写作的卷
2. 点击 **查看/写作**
3. 在弹出的卷详情中，点击 **生成卷大纲**（如果还没有）
4. 点击 **开始写作**
5. 在写作页面使用AI辅助功能：
   - **AI续写**：基于上下文智能续写
   - **AI消痕**：优化文本，减少AI痕迹
   - **手动编辑**：随时修改和调整

### 5. 高级功能

- **形容词挖掘**：在工具页面批量生成优质形容词，构建自己的词库
- **任务管理**：查看所有AI任务的状态和历史
- **进度追踪**：实时监控各卷的写作进度和字数统计

---

## 🎨 界面预览

### 主要页面

- **首页**：小说列表和快速创建入口
- **大纲生成**：流式大纲生成，实时查看
- **卷规划**：可视化卷结构，拖拽调整
- **写作工作室**：沉浸式写作环境
- **设置中心**：AI配置和系统设置

---

## 🔧 开发指南

### 项目结构

```
novel-creation-system/
├── backend/                    # 后端项目
│   ├── src/main/java/
│   │   └── com/novel/
│   │       ├── config/        # 配置类
│   │       ├── controller/    # 控制器
│   │       ├── service/       # 业务逻辑
│   │       ├── dto/          # 数据传输对象
│   │       ├── domain/       # 实体类
│   │       └── mapper/       # 数据访问层
│   └── src/main/resources/
│       ├── application.yml    # 主配置文件
│       └── mapper/           # MyBatis映射文件
│
├── frontend/                  # 前端项目
│   ├── src/
│   │   ├── pages/           # 页面组件
│   │   ├── services/        # API服务
│   │   ├── types/          # TypeScript类型定义
│   │   ├── utils/          # 工具函数
│   │   └── App.tsx         # 应用入口
│   └── package.json
│
└── README.md
```

### 本地开发

#### 后端开发

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- 默认端口：8080
- 热重载：使用Spring DevTools
- API文档：访问 `/swagger-ui.html`（如果已配置）

#### 前端开发

```bash
cd frontend
npm run dev
```

- 默认端口：5173
- 热重载：Vite自动HMR
- API代理：已配置代理到 `http://localhost:8080`

### 代码规范

- **Java**：遵循阿里巴巴Java开发手册
- **TypeScript**：使用ESLint + Prettier
- **提交规范**：建议使用Conventional Commits

---

## 🎯 AI模型推荐

### 日常写作推荐

| 服务商 | 推荐模型 | 特点 | 适用场景 |
|--------|---------|------|---------|
| DeepSeek | `deepseek-chat` | 高性价比，质量稳定 | 日常写作、大纲生成 |
| 通义千问 | `qwen-plus` | 平衡性能和速度 | 通用创作 |
| Kimi | `kimi-latest` | 上下文长 | 长篇连贯创作 |

### 长文本/超长上下文

| 服务商 | 推荐模型 | 上下文长度 | 适用场景 |
|--------|---------|-----------|---------|
| DeepSeek | `deepseek-v3-1-250821-thinking` | 128K | 复杂推理 |
| 通义千问 | `qwen-max-longcontext` | 长上下文 | 大纲整体规划 |
| Kimi | `kimi-k2-turbo-preview` | 262K | 全卷上下文写作 |

### 成本控制建议

- ✅ 大纲生成：使用标准模型即可
- ✅ 章节写作：根据预算选择合适模型
- ✅ AI消痕：可使用经济型模型
- ⚠️ 批量操作：注意API调用频率和成本

---

## ❓ 常见问题

### Q: 提示"AI配置无效"怎么办？

**A**: 检查以下几点：
1. 是否在设置页面配置了AI服务？
2. API Key是否正确？
3. 是否选择了服务商和模型？
4. 尝试重新保存配置

### Q: 批量生成卷大纲没有反应？

**A**: 
1. 检查AI配置是否有效
2. 查看浏览器控制台是否有错误
3. 查看后端日志
4. 确保网络连接正常

### Q: 进度条一直不动？

**A**: 
- 现在使用的是真实轮询状态，如果进度不动，说明后端任务可能遇到问题
- 检查后端日志查看具体错误
- 可能是AI服务调用失败，检查API密钥和网络

### Q: 如何更换AI服务商？

**A**: 在设置页面重新选择服务商，输入对应的API Key并保存即可。

---

## 🤝 贡献指南

由于本项目采用个人使用许可，暂不接受外部贡献。如有建议或发现问题，欢迎提Issue讨论。

---

## 📄 开源协议

本项目采用 **个人使用许可证**（Personal Use License）。

- ✅ **允许**：个人学习、研究和非商业使用
- ❌ **禁止**：商业使用、二次分发、售卖或用于盈利目的
- ℹ️ 详见 [LICENSE](./LICENSE) 文件

---

## 📮 联系方式

- **问题反馈**：通过GitHub Issues
- **功能建议**：通过GitHub Issues

---

## 🙏 致谢

感谢以下开源项目和服务：

- [Spring Boot](https://spring.io/projects/spring-boot) - 强大的Java应用框架
- [React](https://react.dev/) - 优秀的前端框架
- [Ant Design](https://ant.design/) - 企业级UI设计语言
- [MyBatis Plus](https://baomidou.com/) - 强大的持久层框架
- [DeepSeek](https://www.deepseek.com/) - 高性价比AI服务
- [通义千问](https://tongyi.aliyun.com/) - 阿里云AI服务
- [Kimi](https://www.moonshot.cn/) - 超长上下文AI服务

---

## ⚠️ 免责声明

1. 本项目仅供个人学习和研究使用
2. 使用本系统创作的内容，版权归内容创作者所有
3. 使用第三方AI服务产生的费用由用户自行承担
4. 请遵守各AI服务商的使用条款和政策
5. 不建议完全依赖AI生成内容，应结合人工创作和修改

---

<div align="center">

**如果这个项目对你有帮助，请给个⭐Star支持一下！**

Made with ❤️ for novel writers

</div>
