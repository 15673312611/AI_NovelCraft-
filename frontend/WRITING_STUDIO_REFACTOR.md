# 写作工作室重构说明

## 核心变更

### 数据分离
- **章节数据** → `chapters` 表（通过 `/api/novels/{novelId}/chapters` 获取）
- **辅助文档** → `novel_document` 表（通过 `/api/folders/{folderId}/documents` 获取）

### 状态管理
```typescript
// 旧状态（统一管理）
const [documentsMap, setDocumentsMap] = useState<Record<number, NovelDocument[]>>({})
const [selectedDocument, setSelectedDocument] = useState<NovelDocument | null>(null)

// 新状态（分开管理）
const [chapters, setChapters] = useState<Chapter[]>([])  // 主要内容下的章节
const [documentsMap, setDocumentsMap] = useState<Record<number, NovelDocument[]>>({})  // 辅助文档
const [selectedChapter, setSelectedChapter] = useState<Chapter | null>(null)  // 当前选中的章节
const [selectedDocument, setSelectedDocument] = useState<NovelDocument | null>(null)  // 当前选中的文档
const [editingType, setEditingType] = useState<'chapter' | 'document'>('chapter')  // 当前编辑类型
```

### 接口调用映射

| 操作 | 章节 API | 文档 API |
|------|---------|---------|
| 获取列表 | `GET /api/novels/{novelId}/chapters` | `GET /api/folders/{folderId}/documents` |
| 创建 | `POST /api/novels/{novelId}/chapters` | `POST /api/folders/{folderId}/documents` |
| 更新 | `PUT /api/chapters/{id}` | `PUT /api/documents/{id}` |
| 删除 | `DELETE /api/chapters/{id}` | `DELETE /api/documents/{id}` |
| 自动保存 | `POST /api/chapters/{id}/auto-save` | `POST /api/documents/{id}/auto-save` |

### 文件树结构

```
小说标题
├── 主要内容（虚拟节点，不存数据库）
│   ├── 第一章（来自 chapters 表）
│   ├── 第二章（来自 chapters 表）
│   └── ...
├── 设定（novel_folder 表）
│   └── 世界观.txt（novel_document 表）
├── 角色（novel_folder 表）
│   └── 主角设定.txt（novel_document 表）
└── 知识库（novel_folder 表）
    └── 修炼体系.txt（novel_document 表）
```

### 关键修改点

#### 1. `loadInitialData`
- 同时加载 `chapters` 和 `folders/documents`
- "主要内容"文件夹仅用于前端显示，不存储实际章节

#### 2. `FileTree`
- "主要内容"节点标记为 `isChapterContainer: true`
- 章节节点标记为 `type: 'chapter'`，包含 `chapterId`
- 文档节点标记为 `type: 'document'`，包含 `documentId`

#### 3. `onSelectTreeNode`
- 判断节点类型
- 如果是章节：`setEditingType('chapter')`, `setSelectedChapter()`, 调用 `getChapterById`
- 如果是文档：`setEditingType('document')`, `setSelectedDocument()`, 调用 `getDocumentById`

#### 4. `handleQuickAddChapter`
- 调用 `createChapter` API
- 自动计算 `chapterNumber`（当前最大章节号 + 1）

#### 5. `onDocumentContentChange`（重命名为 `onContentChange`）
- 根据 `editingType` 决定调用 `autoSaveChapter` 或 `autoSaveDocument`

#### 6. `handleSendAIRequest`
- 生成内容后，根据 `editingType` 替换到 `selectedChapter` 或 `selectedDocument`

#### 7. 关联内容
- 只显示 `novel_document` 表的内容（不包括章节）
- 章节始终通过"最新30章概要 + 前3章正文"默认传入

