# 📋 写作工作室重构 - 迁移指南

## ✅ 已完成的工作

### 1. 后端重构 (100%)

#### SQL 变更
- ✅ `backend/sql/simple_refactor.sql` - 简化的表结构调整（测试环境直接删除重建）

#### Java 服务层
- ✅ `WritingStudioService.java` - 统一管理初始化
- ✅ `NovelDocumentService.java` - 只处理辅助文档
- ✅ `ChapterService.java` - 添加 `initFirstChapter` 方法
- ✅ `NovelFolderService.java` - 文件夹管理

#### Java 控制器
- ✅ `NovelDocumentController.java` - 新增所有章节相关API
  - GET `/api/novels/{novelId}/chapters` - 获取所有章节
  - POST `/api/novels/{novelId}/chapters` - 创建章节
  - GET `/api/chapters/{id}` - 获取章节详情
  - PUT `/api/chapters/{id}` - 更新章节
  - POST `/api/chapters/{id}/auto-save` - 自动保存章节
  - DELETE `/api/chapters/{id}` - 删除章节

### 2. 前端重构 (80%)

#### 新增服务
- ✅ `frontend/src/services/chapterServiceForStudio.ts` - 章节操作API

#### 核心页面
- ✅ `frontend/src/pages/WritingStudioPage.refactored.tsx` - 完全重构版本
  - 分离章节和文档状态
  - 添加 `editingType` 区分编辑类型
  - 统一的内容变更和自动保存逻辑
  - 支持章节和文档的增删改查

---

## 🔄 待完成的工作

### 1. 替换旧文件 (重要!)

```bash
# 备份旧文件
mv frontend/src/pages/WritingStudioPage.tsx frontend/src/pages/WritingStudioPage.old.tsx

# 使用新版本
mv frontend/src/pages/WritingStudioPage.refactored.tsx frontend/src/pages/WritingStudioPage.tsx
```

### 2. 修改 `FileTree.tsx` (未完成)

需要添加对章节的支持：

```typescript
// 在 FileTreeProps 中添加
chapters: Chapter[]  // 新增
onSelectChapter: (chapter: Chapter) => void  // 新增
onDeleteChapter?: (chapter: Chapter) => void  // 新增
onRenameChapter?: (chapter: Chapter, newName: string) => void  // 新增

// 在 treeData 生成逻辑中
// "主要内容"文件夹下，不再从 documentsMap 读取，而是从 chapters 数组读取
```

具体修改点：
1. 修改 `FileTreeProps` 接口，添加 `chapters` prop
2. 修改 `buildFolderTree` 函数，在"主要内容"节点下显示 `chapters` 而非 `documentsMap`
3. 章节节点的 key 格式：`chapter-${chapter.id}`
4. 章节的右键菜单：只显示"删除"和"重命名"
5. `handleSelect` 函数，识别章节节点并调用 `onSelectChapter`

### 3. 数据库迁移 (测试环境)

执行SQL脚本：

```bash
# 在 MySQL 中执行
mysql -u your_user -p your_database < backend/sql/simple_refactor.sql
```

⚠️ **注意**：
- 测试环境，会**清空** `novel_document` 和 `novel_folder` 表
- 会删除 `novel_document` 表的 `document_type` 和 `word_count` 字段
- 会删除 `novel_folder` 表的 `folder_type` 字段
- **不需要迁移数据**，重新初始化即可

### 4. 测试清单

- [ ] 初始化：进入写作工作室，自动创建"主要内容"、"第一章"和辅助文件夹
- [ ] 章节CRUD：
  - [ ] 点击"主要内容"的"+"号，能创建新章节
  - [ ] 点击章节，能加载内容到编辑器
  - [ ] 编辑章节，自动保存
  - [ ] 右键章节，能重命名和删除
- [ ] 文档CRUD：
  - [ ] 在"设定/角色/知识库"文件夹下，能创建文档
  - [ ] 点击文档，能加载内容到编辑器
  - [ ] 编辑文档，自动保存
  - [ ] 右键文档，能重命名和删除
- [ ] AI生成：
  - [ ] 在章节中生成内容，能替换到编辑器
  - [ ] 在文档中生成内容，能替换到编辑器
- [ ] 关联内容：
  - [ ] 只显示辅助文档（不包括章节）
  - [ ] 章节通过"最新30章概要+前3章正文"默认传入

---

## 📊 架构对比

### 旧架构
```
novel_document 表
├── document_type='chapter' (章节)
└── document_type='custom' (文档)

问题：
- 查询章节需要过滤 document_type
- 章节没有专用字段（chapter_number, status等）
- 关联内容会混入章节
```

### 新架构
```
chapters 表 (专业管理章节)
├── chapter_number
├── status
├── word_count
└── ...

novel_document 表 (只存辅助文档)
├── 设定
├── 角色
└── 知识库

优势：
- 查询高效，职责清晰
- 章节有专业字段管理
- 关联内容只显示辅助文档
```

---

## 🔗 相关文件

### 后端
- `backend/src/main/java/com/novel/service/WritingStudioService.java`
- `backend/src/main/java/com/novel/service/ChapterService.java`
- `backend/src/main/java/com/novel/service/NovelDocumentService.java`
- `backend/src/main/java/com/novel/controller/NovelDocumentController.java`

### 前端
- `frontend/src/pages/WritingStudioPage.refactored.tsx` (待替换)
- `frontend/src/services/chapterServiceForStudio.ts`
- `frontend/src/components/writing/FileTree.tsx` (待修改)

### SQL
- `backend/sql/simple_refactor.sql`

---

## ❓ FAQ

### Q1: 旧的章节数据会丢失吗？
A: 测试环境会清空所有文档和文件夹数据，但章节数据本来就在 `chapters` 表，不受影响。

### Q2: 需要重新编译后端吗？
A: 是的，修改了 Java 代码后需要重新编译：
```bash
cd backend
mvn clean install
```

### Q3: 前端需要重新安装依赖吗？
A: 不需要，没有新增依赖。

### Q4: 为什么不给 chapters 表添加 folder_id？
A: 不需要！"主要内容"是前端虚拟节点，章节直接通过 `novel_id` 关联小说即可。

---

## 🎯 下一步

1. **测试后端API** - 使用 Postman/curl 测试新的章节API
2. **修改 FileTree.tsx** - 添加章节支持
3. **替换 WritingStudioPage.tsx** - 使用重构版本
4. **运行数据库迁移** - 执行SQL脚本
5. **全面测试** - 按照测试清单逐项验证

