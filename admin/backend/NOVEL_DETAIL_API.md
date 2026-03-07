# 小说详情页 API 接口文档

根据正确的数据库表结构设计的API接口。

## 数据库表关系

```
novels (小说表)
  ├── novel_outlines (大纲表) - 1:1
  ├── novel_volumes (分卷表) - 1:N
  │     └── volume_chapter_outlines (章纲表) - 1:N
  └── chapters (章节内容表) - 1:N
```

## API 接口列表

### 1. 获取小说基本信息

```
GET /api/admin/novels/{novelId}
```

**响应示例：**
```json
{
  "id": 176,
  "title": "修仙传",
  "subtitle": "一个少年的修仙之路",
  "coverImageUrl": "https://...",
  "description": "这是一个关于...",
  "genre": "玄幻",
  "tags": "修仙,热血,冒险",
  "targetAudience": "男性向",
  "estimatedCompletion": "2024-12-31T00:00:00",
  "startedAt": "2024-01-01T00:00:00",
  "completedAt": null,
  "isPublic": true,
  "rating": 4.5,
  "ratingCount": 100,
  "targetTotalChapters": 500,
  "wordsPerChapter": 3000,
  "plannedVolumeCount": 10,
  "totalWordTarget": 1500000,
  "status": "WRITING",
  "wordCount": 150000,
  "chapterCount": 50,
  "authorId": 1,
  "createdBy": 1,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-12-14T00:00:00",
  "outline": "整本书的大纲内容...",
  "creationStage": "WRITING"
}
```

### 2. 获取小说大纲

```
GET /api/admin/novels/{novelId}/outline
```

**响应示例：**
```json
{
  "id": 1,
  "novelId": 176,
  "title": "修仙传大纲",
  "genre": "玄幻",
  "basicIdea": "一个少年从凡人到仙人的成长历程",
  "coreTheme": "坚持与成长",
  "mainCharacters": "主角：张三\n配角：李四、王五",
  "plotStructure": "第一阶段：凡人修炼\n第二阶段：...",
  "coreSettings": "修炼体系：炼气、筑基、金丹...",
  "worldSetting": "修仙世界，分为凡界、灵界、仙界",
  "keyElements": ["修炼", "宗门", "法宝", "丹药"],
  "conflictTypes": ["个人成长", "宗门争斗", "正邪对立"],
  "targetWordCount": 1500000,
  "targetChapterCount": 500,
  "status": "CONFIRMED",
  "isAiGenerated": true,
  "lastModifiedByAi": "GPT-4",
  "createdBy": 1,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-05T00:00:00",
  "feedbackHistory": "第一次修订：增加配角描述\n第二次修订：...",
  "revisionCount": 2,
  "reactDecisionLog": "{\"prompt\": \"...\", \"context\": \"...\"}"
}
```

### 3. 获取小说分卷列表

```
GET /api/admin/novels/{novelId}/volumes
```

**响应示例：**
```json
[
  {
    "id": 1,
    "novelId": 176,
    "outlineId": 1,
    "title": "第一卷：初入修仙",
    "theme": "少年踏入修仙之路",
    "description": "主角获得机缘，开始修炼",
    "contentOutline": "1-50章的内容概要",
    "volumeNumber": 1,
    "chapterStart": 1,
    "chapterEnd": 50,
    "estimatedWordCount": 150000,
    "actualWordCount": 150000,
    "keyEvents": "获得传承、拜入宗门、首次战斗",
    "characterDevelopment": "主角从懵懂少年成长为修炼者",
    "plotThreads": "主线：修炼成长；支线：宗门关系",
    "status": "COMPLETED",
    "isAiGenerated": true,
    "lastModifiedByAi": "GPT-4",
    "createdBy": 1,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-03-01T00:00:00"
  }
]
```

### 4. 获取章纲列表

```
GET /api/admin/novels/{novelId}/chapter-outlines
GET /api/admin/novels/{novelId}/volumes/{volumeId}/chapter-outlines
```

**查询参数：**
- `volumeId` (可选): 按卷筛选
- `status` (可选): 按状态筛选 (PENDING/WRITTEN/REVISED)
- `page`: 页码
- `size`: 每页数量

**响应示例：**
```json
[
  {
    "id": 1,
    "novelId": 176,
    "volumeId": 1,
    "volumeNumber": 1,
    "chapterInVolume": 1,
    "globalChapterNumber": 1,
    "direction": "主角在拍卖会上竞拍神秘丹药，引发多方势力暗中角力",
    "keyPlotPoints": [
      "拍卖会开场，主角低调入场",
      "神秘丹药出现，引发哄抢",
      "主角出价，暴露部分实力"
    ],
    "emotionalTone": "紧张、期待、暗流涌动",
    "foreshadowAction": "PLANT",
    "foreshadowDetail": {
      "content": "神秘老者注意到主角",
      "targetResolveVolume": 3,
      "resolveWindow": { "min": 80, "max": 100 }
    },
    "subplot": "女主角暗中调查主角身份",
    "antagonism": {
      "opponent": "李家少主",
      "conflictType": "利益",
      "intensity": 7
    },
    "status": "WRITTEN",
    "reactDecisionLog": "{...}",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-02T00:00:00"
  }
]
```

### 5. 获取章节内容列表

```
GET /api/admin/novels/{novelId}/chapters
```

**查询参数：**
- `status` (可选): 按状态筛选
- `page`: 页码
- `size`: 每页数量

**响应示例：**
```json
[
  {
    "id": 1,
    "title": "第一章：觉醒",
    "subtitle": "命运的转折",
    "content": "章节正文内容...",
    "simpleContent": "简化版内容...",
    "orderNum": 1,
    "status": "PUBLISHED",
    "wordCount": 3000,
    "chapterNumber": 1,
    "summary": "主角觉醒修炼天赋",
    "notes": "作者备注",
    "isPublic": true,
    "publishedAt": "2024-01-10T00:00:00",
    "readingTimeMinutes": 10,
    "previousChapterId": null,
    "nextChapterId": 2,
    "novelId": 176,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-10T00:00:00",
    "generationContext": "写作上下文快照...",
    "reactDecisionLog": "ReAct决策日志..."
  }
]
```

### 6. 获取小说完整详情（一次性获取所有数据）

```
GET /api/admin/novels/{novelId}/detail-all
```

**响应示例：**
```json
{
  "novel": { /* 小说基本信息 */ },
  "outline": { /* 大纲信息 */ },
  "volumes": [ /* 分卷列表 */ ],
  "chapterOutlines": [ /* 章纲列表 */ ],
  "chapters": [ /* 章节列表 */ ],
  "statistics": {
    "totalWords": 150000,
    "totalChapters": 50,
    "totalVolumes": 3,
    "completionRate": 10,
    "averageChapterWords": 3000,
    "writtenChapterOutlines": 50,
    "pendingChapterOutlines": 450
  }
}
```

## Mapper 接口示例

```java
@Mapper
public interface NovelDetailMapper {
    
    // 小说基本信息
    Novel selectNovelById(@Param("novelId") Long novelId);
    
    // 大纲
    NovelOutline selectOutlineByNovelId(@Param("novelId") Long novelId);
    
    // 分卷
    List<NovelVolume> selectVolumesByNovelId(@Param("novelId") Long novelId);
    
    // 章纲
    List<VolumeChapterOutline> selectChapterOutlinesByNovelId(@Param("novelId") Long novelId);
    List<VolumeChapterOutline> selectChapterOutlinesByVolumeId(@Param("volumeId") Long volumeId);
    
    // 章节
    List<Chapter> selectChaptersByNovelId(
        @Param("novelId") Long novelId,
        @Param("status") String status,
        @Param("offset") Integer offset,
        @Param("limit") Integer limit
    );
    
    // 统计
    Map<String, Object> selectNovelStatistics(@Param("novelId") Long novelId);
}
```

## 实体类示例

```java
// Novel.java
@Data
public class Novel {
    private Long id;
    private String title;
    private String subtitle;
    private String coverImageUrl;
    private String description;
    private String genre;
    private String tags;
    private String targetAudience;
    private LocalDateTime estimatedCompletion;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Boolean isPublic;
    private BigDecimal rating;
    private Integer ratingCount;
    private Integer targetTotalChapters;
    private Integer wordsPerChapter;
    private Integer plannedVolumeCount;
    private Integer totalWordTarget;
    private String status; // DRAFT, WRITING, REVIEWING, COMPLETED
    private Integer wordCount;
    private Integer chapterCount;
    private Long authorId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String outline;
    private String creationStage;
}

// NovelOutline.java
@Data
public class NovelOutline {
    private Long id;
    private Long novelId;
    private String title;
    private String genre;
    private String basicIdea;
    private String coreTheme;
    private String mainCharacters;
    private String plotStructure;
    private String coreSettings;
    private String worldSetting;
    private String keyElements; // JSON
    private String conflictTypes; // JSON
    private Integer targetWordCount;
    private Integer targetChapterCount;
    private String status; // DRAFT, CONFIRMED, REVISED, REVISING
    private Boolean isAiGenerated;
    private String lastModifiedByAi;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String feedbackHistory;
    private Integer revisionCount;
    private String reactDecisionLog;
}

// NovelVolume.java
@Data
public class NovelVolume {
    private Long id;
    private Long novelId;
    private Long outlineId;
    private String title;
    private String theme;
    private String description;
    private String contentOutline;
    private Integer volumeNumber;
    private Integer chapterStart;
    private Integer chapterEnd;
    private Integer estimatedWordCount;
    private Integer actualWordCount;
    private String keyEvents;
    private String characterDevelopment;
    private String plotThreads;
    private String status; // PLANNED, IN_PROGRESS, COMPLETED, REVISED
    private Boolean isAiGenerated;
    private String lastModifiedByAi;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// VolumeChapterOutline.java
@Data
public class VolumeChapterOutline {
    private Long id;
    private Long novelId;
    private Long volumeId;
    private Integer volumeNumber;
    private Integer chapterInVolume;
    private Integer globalChapterNumber;
    private String direction;
    private String keyPlotPoints; // JSON
    private String emotionalTone;
    private String foreshadowAction; // NONE, PLANT, REFERENCE, DEEPEN, RESOLVE
    private String foreshadowDetail; // JSON
    private String subplot;
    private String antagonism; // JSON
    private String status; // PENDING, WRITTEN, REVISED
    private String reactDecisionLog;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// Chapter.java
@Data
public class Chapter {
    private Long id;
    private String title;
    private String subtitle;
    private String content;
    private String simpleContent;
    private Integer orderNum;
    private String status; // DRAFT, IN_PROGRESS, WRITING, REVIEW, REVIEWING, PUBLISHED, COMPLETED, ARCHIVED
    private Integer wordCount;
    private Integer chapterNumber;
    private String summary;
    private String notes;
    private Boolean isPublic;
    private LocalDateTime publishedAt;
    private Integer readingTimeMinutes;
    private Long previousChapterId;
    private Long nextChapterId;
    private Long novelId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String generationContext;
    private String reactDecisionLog;
}
```

## 注意事项

1. **表关系**：
   - `novels` 是主表
   - `novel_outlines` 与 `novels` 是 1:1 关系
   - `novel_volumes` 与 `novels` 是 1:N 关系
   - `volume_chapter_outlines` 与 `novel_volumes` 是 1:N 关系
   - `chapters` 与 `novels` 是 1:N 关系

2. **状态枚举**：
   - 小说状态：DRAFT, WRITING, REVIEWING, COMPLETED
   - 大纲状态：DRAFT, CONFIRMED, REVISED, REVISING
   - 卷状态：PLANNED, IN_PROGRESS, COMPLETED, REVISED
   - 章纲状态：PENDING, WRITTEN, REVISED
   - 章节状态：DRAFT, IN_PROGRESS, WRITING, REVIEW, REVIEWING, PUBLISHED, COMPLETED, ARCHIVED

3. **JSON 字段**：
   - `keyElements`, `conflictTypes` (大纲表)
   - `keyPlotPoints`, `foreshadowDetail`, `antagonism` (章纲表)
   - 需要在后端进行 JSON 序列化/反序列化

4. **分页**：
   - 章节列表和章纲列表建议使用分页
   - 使用 MyBatis PageHelper 或手动分页

5. **性能优化**：
   - 章节内容 `content` 字段较大，列表查询时可以不返回
   - 可以提供单独的接口获取章节详细内容
