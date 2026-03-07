# 小说详情页重新设计文档

## 问题说明

之前的详情页查询的表结构不正确，导致无法显示数据。现在根据正确的数据库表结构重新设计。

## 正确的数据库表结构

### 核心表关系

```
novels (小说表) - 主表
  ├── novel_outlines (大纲表) - 1:1 关系
  ├── novel_volumes (分卷表) - 1:N 关系
  │     └── volume_chapter_outlines (章纲表) - 1:N 关系
  └── chapters (章节内容表) - 1:N 关系
```

### 表说明

1. **novels** - 小说基本信息
   - 包含标题、简介、类型、状态、字数、章节数等
   - `outline` 字段：整本书大纲（确认后写入）
   - `creation_stage` 字段：创作阶段状态

2. **novel_outlines** - 大纲表
   - 存储详细的大纲信息
   - 包含核心主题、剧情结构、核心设定等
   - 支持AI生成和人工修订

3. **novel_volumes** - 分卷表
   - 按卷组织章节
   - 包含卷的主题、描述、章节范围等
   - 记录预估字数和实际字数

4. **volume_chapter_outlines** - 章纲表
   - 每章的详细大纲
   - 包含剧情方向、关键剧情点、情感基调等
   - 支持伏笔管理（埋、提、加深、揭露）

5. **chapters** - 章节内容表
   - 存储实际的章节正文
   - 包含标题、内容、字数、状态等

## 前端重新设计

### 页面结构

```
小说详情页
├── 顶部操作栏
│   ├── 返回列表按钮
│   └── 编辑/生成按钮
├── 小说概览卡片
│   ├── 封面图
│   ├── 标题和状态
│   └── 统计数据（字数、章节、分卷、评分）
└── 详细信息标签页
    ├── 基本信息 Tab
    │   ├── 小说基本信息
    │   ├── 创作目标
    │   └── 整本书大纲
    ├── 大纲表 Tab
    │   └── novel_outlines 表数据
    ├── 分卷 Tab
    │   └── novel_volumes 表数据
    ├── 章纲 Tab
    │   └── volume_chapter_outlines 表数据
    └── 章节内容 Tab
        └── chapters 表数据
```

### 主要功能

1. **基本信息展示**
   - 显示小说的所有基本字段
   - 展示创作目标和进度
   - 显示整本书大纲（如果已确认）

2. **大纲管理**
   - 查看详细大纲信息
   - 显示修订历史和次数
   - 支持编辑、确认、修订操作

3. **分卷管理**
   - 列表展示所有分卷
   - 显示字数进度条
   - 支持查看该卷的章纲

4. **章纲管理**
   - 列表展示所有章纲
   - 支持按卷筛选
   - 显示伏笔动作和状态
   - 支持批量生成

5. **章节管理**
   - 列表展示所有章节
   - 显示字数和状态
   - 支持编辑和预览

## 后端实现

### API 接口

```
GET /api/admin/novels/{novelId}                    - 获取小说基本信息
GET /api/admin/novels/{novelId}/outline            - 获取大纲
GET /api/admin/novels/{novelId}/volumes            - 获取分卷列表
GET /api/admin/novels/{novelId}/chapter-outlines   - 获取章纲列表
GET /api/admin/novels/{novelId}/chapters           - 获取章节列表
GET /api/admin/novels/{novelId}/detail-all         - 一次性获取所有数据
GET /api/admin/novels/{novelId}/statistics         - 获取统计信息
```

### 文件结构

```
admin/backend/src/main/java/com/novel/admin/
├── controller/
│   └── NovelDetailController.java       - 控制器
├── service/
│   └── NovelDetailService.java          - 服务层
├── mapper/
│   └── NovelDetailMapper.java           - 数据访问层
└── entity/
    ├── Novel.java                       - 小说实体
    ├── NovelOutline.java                - 大纲实体
    ├── NovelVolume.java                 - 分卷实体
    ├── VolumeChapterOutline.java        - 章纲实体
    └── Chapter.java                     - 章节实体
```

### 关键实现

1. **实体类映射**
   - 所有实体类严格对应数据库表结构
   - 使用 Lombok 简化代码
   - JSON 字段使用 String 类型存储

2. **Mapper 查询**
   - 使用 MyBatis 注解方式
   - 支持分页和条件筛选
   - 章节列表不返回 content 字段以提高性能

3. **性能优化**
   - 提供单独的接口获取章节详细内容
   - 统计信息使用 SQL 聚合查询
   - 支持按需加载数据

## 前端实现

### 文件位置

```
admin/frontend/src/pages/Novels/NovelDetail.tsx
```

### 主要特性

1. **类型定义**
   - 根据数据库表结构定义 TypeScript 接口
   - 严格的类型检查

2. **数据加载**
   - 并行请求所有数据
   - 错误处理和加载状态

3. **UI 组件**
   - 使用 Ant Design 组件
   - 响应式布局
   - 状态标签和进度条

4. **交互功能**
   - 支持按卷查看章纲
   - 支持状态筛选
   - 支持分页

## 使用说明

### 启动步骤

1. **后端**
   ```bash
   cd admin/backend
   mvn spring-boot:run
   ```

2. **前端**
   ```bash
   cd admin/frontend
   npm install
   npm run dev
   ```

3. **访问**
   - 打开浏览器访问 `http://localhost:5173`
   - 登录后进入小说列表
   - 点击小说进入详情页

### 测试

访问 `/novels/176` 查看 ID 为 176 的小说详情。

## 注意事项

1. **数据库字段映射**
   - 注意下划线命名和驼峰命名的转换
   - MyBatis 会自动处理，但需要配置 `mapUnderscoreToCamelCase`

2. **JSON 字段处理**
   - `keyElements`, `conflictTypes`, `keyPlotPoints`, `foreshadowDetail`, `antagonism`
   - 前端需要解析 JSON 字符串
   - 后端存储时需要序列化

3. **状态枚举**
   - 前端和后端保持一致
   - 使用统一的状态标签渲染

4. **性能考虑**
   - 章节内容字段较大，列表查询时不返回
   - 使用分页减少数据量
   - 考虑添加缓存

## 后续优化

1. **编辑功能**
   - 添加编辑小说基本信息的表单
   - 添加编辑大纲的功能
   - 添加编辑分卷和章纲的功能

2. **生成功能**
   - 集成 AI 生成大纲
   - 批量生成章纲
   - 生成章节内容

3. **可视化**
   - 添加创作进度图表
   - 添加字数统计图表
   - 添加伏笔关系图

4. **导出功能**
   - 导出完整小说
   - 导出大纲文档
   - 导出章纲列表

## 相关文档

- [API 接口文档](./backend/NOVEL_DETAIL_API.md)
- [数据库表结构](./database/数据库表.txt)
