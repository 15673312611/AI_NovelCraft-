# 后台管理系统图谱数据集成

## 📋 修改概述

根据客户端前端的实现，对后台管理系统的小说详情页面进行了全面升级，主要包括：

1. **增强卷章节数据展示** - 补充了缺失的字段和信息
2. **移除角色世界观** - 替换为更强大的图谱数据
3. **集成图谱数据** - 展示角色状态、关系、任务、事件等

## 🔧 后端修改

### 1. DTO增强

#### VolumeDTO.java
新增字段：
- `description` - 卷描述
- `contentOutline` - 内容大纲
- `estimatedWordCount` - 预估字数
- `actualWordCount` - 实际字数
- `createdAt` - 创建时间
- `updatedAt` - 更新时间

#### ChapterDTO.java
新增字段：
- `updatedAt` - 更新时间

### 2. Mapper增强

#### NovelDetailMapper.java
- 增强了卷列表查询，包含更多字段
- 增强了章节列表查询，包含更新时间

### 3. 新增图谱数据服务

#### GraphDataDTO.java
定义了图谱数据结构：
- `CharacterStateDTO` - 角色状态（name, location, realm, characterInfo, alive, chapter）
- `RelationshipStateDTO` - 关系状态（a, b, type, strength, chapter）
- `OpenQuestDTO` - 未决任务（id, description, status, introduced, due, lastUpdated）
- `EventDTO` - 事件（chapter, summary, location, participants, importance, emotionalTone, tags）

#### GraphDataService.java
通过RestTemplate调用客户端后端的图谱API：
- 接口地址：`http://localhost:8080/agentic/graph/data/{novelId}`
- 返回完整的图谱数据

#### RestTemplateConfig.java
配置RestTemplate Bean，用于HTTP调用

### 4. Controller增强

#### AdminNovelController.java
新增接口：
- `GET /novels/{id}/detail` - 获取小说完整详情（包含大纲、卷、章节、角色、世界观）
- `GET /novels/{id}/graph` - 获取图谱数据

### 5. Service增强

#### AdminNovelService.java
新增方法：
- `getNovelDetail(Long id)` - 一次性查询所有详情数据

## 🎨 前端修改

### 1. 类型定义增强

#### novelDetail.ts
新增接口：
- `GraphData` - 图谱数据主接口
- `CharacterState` - 角色状态
- `RelationshipState` - 关系状态
- `OpenQuest` - 未决任务
- `Event` - 事件

新增API方法：
- `getNovelDetailAll(id)` - 一次性获取所有详情
- `getGraphData(id)` - 获取图谱数据

### 2. 组件增强

#### NovelDetail.tsx
新增状态：
- `graphData` - 图谱数据
- `graphLoading` - 图谱加载状态

新增方法：
- `fetchGraphData()` - 加载图谱数据

新增Tab页：
- **图谱数据** - 包含4个子Tab：
  - 角色状态 - 展示角色的位置、境界、存活状态等
  - 关系状态 - 展示角色之间的关系类型和强度
  - 未决任务 - 展示开放的任务和伏笔
  - 事件 - 展示章节事件、参与者、重要性等

## 📊 数据流

```
客户端前端 → 客户端后端 (localhost:8080)
                ↓
        /agentic/graph/data/{novelId}
                ↓
        返回图谱数据
                ↓
后台管理系统后端 (localhost:8081) → GraphDataService
                ↓
        通过RestTemplate调用
                ↓
后台管理系统前端 → 展示图谱数据
```

## 🎯 图谱数据说明

### 角色状态 (CharacterState)
- **name**: 角色名称
- **location**: 当前位置
- **realm**: 境界/等级
- **characterInfo**: 人物信息（如：黑化值、好感度等）
- **alive**: 存活状态
- **chapter**: 最后更新章节

### 关系状态 (RelationshipState)
- **a**: 角色A
- **b**: 角色B
- **type**: 关系类型（如：师徒、敌对、盟友等）
- **strength**: 关系强度（0-1）
- **chapter**: 最后更新章节

### 未决任务 (OpenQuest)
- **id**: 任务ID
- **description**: 任务描述
- **status**: 状态（OPEN/RESOLVED）
- **introduced**: 引入章节
- **due**: 截止章节
- **lastUpdated**: 最后更新章节

### 事件 (Event)
- **chapter**: 章节号
- **summary**: 事件摘要
- **location**: 发生地点
- **participants**: 参与者列表
- **importance**: 重要性（0-1）
- **emotionalTone**: 情感基调（positive/negative/neutral/tense）
- **tags**: 标签列表

## 🚀 使用方式

1. 启动客户端后端（端口8080）
2. 启动后台管理系统后端（端口8081）
3. 启动后台管理系统前端
4. 访问小说详情页面
5. 点击"图谱数据"Tab
6. 点击"加载图谱数据"按钮

## ⚠️ 注意事项

1. **端口配置**: 确保客户端后端运行在8080端口
2. **跨域配置**: 已在Controller中配置CORS
3. **数据依赖**: 图谱数据依赖客户端后端的Neo4j服务
4. **错误处理**: 如果图谱服务未启用，会显示友好的错误提示

## 📝 后续优化建议

1. **配置化**: 将客户端后端地址配置到application.yml
2. **缓存**: 添加图谱数据缓存，减少重复请求
3. **实时更新**: 考虑使用WebSocket实现图谱数据实时更新
4. **可视化**: 添加图谱可视化展示（如关系图、时间线等）
5. **编辑功能**: 支持在后台管理系统中直接编辑图谱数据
