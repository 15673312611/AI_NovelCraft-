# 七猫小说爬虫系统架构

## 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                          前端层 (React)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           QimaoDashboard Component (看板)                │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │  • 统计卡片    • 分类图表    • 作者排行                  │  │
│  │  • 小说列表    • 任务监控    • 详情弹窗                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓ Axios HTTP                          │
└─────────────────────────────────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────┐
│                    后端层 (Spring Boot)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         QimaoScraperController (REST API)                │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │  POST /api/qimao/scrape/{code}  - 开始爬取              │  │
│  │  POST /api/qimao/scrape/all     - 批量爬取              │  │
│  │  GET  /api/qimao/statistics     - 获取统计              │  │
│  │  GET  /api/qimao/novels         - 获取小说列表          │  │
│  │  GET  /api/qimao/tasks          - 获取任务列表          │  │
│  │  GET  /api/qimao/categories     - 获取分类列表          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓                                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         QimaoScraperService (核心服务)                   │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │  • scrapeCategory()      - 爬取分类                      │  │
│  │  • scrapePage()          - 爬取单页                      │  │
│  │  • parseNovelElement()   - 解析元素                      │  │
│  │  • scrapeFirstChapter()  - 爬取章节                      │  │
│  │  • getStatistics()       - 统计分析                      │  │
│  │  • getNovels()           - 查询小说                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                    ↓ OkHttp + Jsoup                            │
└─────────────────────────────────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────┐
│                    外部网站 (七猫中文网)                         │
├─────────────────────────────────────────────────────────────────┤
│  https://www.qimao.com/shuku/...                               │
│  • HTML页面    • 小说列表    • 章节内容                         │
└─────────────────────────────────────────────────────────────────┘

                                  ↓
┌─────────────────────────────────────────────────────────────────┐
│                    数据持久层 (MyBatis Plus)                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    Mapper 接口层                          │  │
│  ├──────────────────────────────────────────────────────────┤  │
│  │  • QimaoNovelMapper      - 小说数据访问                  │  │
│  │  • QimaoCategoryMapper   - 分类数据访问                  │  │
│  │  • QimaoScraperTaskMapper - 任务数据访问                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          ↓ JDBC                                │
└─────────────────────────────────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────────┐
│                      数据库层 (MySQL)                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │  qimao_novels    │  │ qimao_categories │  │ qimao_scraper│ │
│  │                  │  │                  │  │    _tasks    │ │
│  ├──────────────────┤  ├──────────────────┤  ├──────────────┤ │
│  │ • id             │  │ • id             │  │ • id         │ │
│  │ • novel_id       │  │ • category_name  │  │ • task_name  │ │
│  │ • title          │  │ • category_code  │  │ • status     │ │
│  │ • author         │  │ • category_url   │  │ • progress   │ │
│  │ • category       │  │ • is_active      │  │ • start_time │ │
│  │ • description    │  │ • sort_order     │  │ • end_time   │ │
│  │ • word_count     │  └──────────────────┘  └──────────────┘ │
│  │ • status         │                                          │
│  │ • tags           │                                          │
│  │ • first_chapter  │                                          │
│  │ • ...            │                                          │
│  └──────────────────┘                                          │
└─────────────────────────────────────────────────────────────────┘
```

## 数据流转图

### 爬取流程

```
用户操作
   ↓
[点击"开始爬取"] → [选择分类] → [确认爬取]
   ↓
前端发送请求
   ↓
POST /api/qimao/scrape/ceo-mansion?maxPages=3
   ↓
Controller接收请求
   ↓
QimaoScraperController.startScraping()
   ↓
调用Service层
   ↓
QimaoScraperService.scrapeCategory()
   ↓
创建任务记录 → INSERT INTO qimao_scraper_tasks
   ↓
异步执行爬取 (@Async)
   ↓
┌─────────────────────────────────────┐
│  循环爬取每一页 (1 to maxPages)     │
│  ┌───────────────────────────────┐  │
│  │  1. 构建URL                   │  │
│  │  2. 发送HTTP请求 (OkHttp)     │  │
│  │  3. 获取HTML响应              │  │
│  │  4. 解析HTML (Jsoup)          │  │
│  │  5. 提取小说列表              │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │ 循环处理每个小说元素     │  │  │
│  │  │ • 提取标题               │  │  │
│  │  │ • 提取作者               │  │  │
│  │  │ • 提取简介               │  │  │
│  │  │ • 提取字数               │  │  │
│  │  │ • 提取状态               │  │  │
│  │  │ • 提取标签               │  │  │
│  │  │ • 爬取第一章(可选)       │  │  │
│  │  └─────────────────────────┘  │  │
│  │  6. 保存到数据库              │  │
│  │  7. 延迟2-5秒                 │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
   ↓
更新任务状态 → UPDATE qimao_scraper_tasks
   ↓
返回成功响应
   ↓
前端显示结果
```

### 查询流程

```
用户操作
   ↓
[访问看板] → [查看统计数据]
   ↓
前端发送请求
   ↓
GET /api/qimao/statistics
   ↓
Controller接收请求
   ↓
QimaoScraperController.getStatistics()
   ↓
调用Service层
   ↓
QimaoScraperService.getStatistics()
   ↓
执行多个查询
   ↓
┌─────────────────────────────────────┐
│  1. 查询总小说数                     │
│     SELECT COUNT(*) FROM qimao_novels│
│                                     │
│  2. 分类统计                         │
│     SELECT category, COUNT(*)       │
│     FROM qimao_novels               │
│     GROUP BY category               │
│                                     │
│  3. 状态统计                         │
│     SELECT status, COUNT(*)         │
│     FROM qimao_novels               │
│     GROUP BY status                 │
│                                     │
│  4. 作者统计                         │
│     SELECT author, COUNT(*)         │
│     FROM qimao_novels               │
│     GROUP BY author                 │
│     ORDER BY COUNT(*) DESC          │
│     LIMIT 20                        │
│                                     │
│  5. 任务统计                         │
│     SELECT task_status, COUNT(*)    │
│     FROM qimao_scraper_tasks        │
│     GROUP BY task_status            │
└─────────────────────────────────────┘
   ↓
组装统计数据
   ↓
返回JSON响应
   ↓
前端渲染图表
```

## 技术栈详解

### 前端技术栈

```
React 18.2.0
   ├── TypeScript 5.0.2          (类型安全)
   ├── Ant Design 5.4.0          (UI组件库)
   │   ├── Table                 (数据表格)
   │   ├── Card                  (卡片容器)
   │   ├── Chart                 (图表组件)
   │   ├── Modal                 (弹窗)
   │   └── Form                  (表单)
   ├── Axios 1.4.0               (HTTP客户端)
   ├── React Router 6.8.0        (路由管理)
   └── CSS3                      (样式动画)
```

### 后端技术栈

```
Spring Boot 2.7.18
   ├── Spring Web                (Web框架)
   ├── Spring Async              (异步任务)
   ├── MyBatis Plus 3.5.3.1      (ORM框架)
   ├── Jsoup 1.17.2              (HTML解析)
   │   ├── CSS Selector          (元素选择)
   │   ├── DOM Parser            (DOM解析)
   │   └── Data Extraction       (数据提取)
   ├── OkHttp 4.12.0             (HTTP客户端)
   │   ├── Connection Pool       (连接池)
   │   ├── Timeout Control       (超时控制)
   │   └── Retry Mechanism       (重试机制)
   ├── Jackson                   (JSON处理)
   └── Lombok                    (代码简化)
```

### 数据库技术栈

```
MySQL 8.0
   ├── InnoDB Engine             (存储引擎)
   ├── UTF8MB4 Charset           (字符集)
   ├── B-Tree Index              (索引结构)
   └── Transaction Support       (事务支持)
```

## 核心算法

### HTML解析算法

```java
// 多级选择器策略
Element titleElement = element.selectFirst(
    "a[href*='/book/'], " +      // 优先匹配
    "h3 a, " +                   // 备选方案1
    ".book-title a, " +          // 备选方案2
    ".title a"                   // 备选方案3
);

// 容错处理
if (titleElement != null) {
    novel.setTitle(titleElement.text().trim());
} else {
    log.warn("未找到标题元素");
}
```

### 去重算法

```java
// 基于novel_id的唯一性约束
QueryWrapper<QimaoNovel> queryWrapper = new QueryWrapper<>();
queryWrapper.eq("novel_id", novelDTO.getNovelId());
QimaoNovel existingNovel = qimaoNovelMapper.selectOne(queryWrapper);

if (existingNovel != null) {
    // 更新已存在的记录
    novel = existingNovel;
    qimaoNovelMapper.updateById(novel);
} else {
    // 插入新记录
    qimaoNovelMapper.insert(novel);
}
```

### 延迟控制算法

```java
// 随机延迟 2-5秒
int baseDelay = 2000;  // 基础延迟2秒
int randomDelay = new Random().nextInt(3000);  // 随机0-3秒
Thread.sleep(baseDelay + randomDelay);
```

## 性能优化策略

### 数据库优化

```sql
-- 索引优化
CREATE INDEX idx_category ON qimao_novels(category);
CREATE INDEX idx_author ON qimao_novels(author);
CREATE INDEX idx_status ON qimao_novels(status);
CREATE INDEX idx_created_at ON qimao_novels(created_at);

-- 分页查询优化
SELECT * FROM qimao_novels 
WHERE category = '总裁豪门'
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

### 异步处理

```java
@Async
@Transactional(rollbackFor = Exception.class)
public void scrapeCategory(String categoryCode, int maxPages) {
    // 异步执行，不阻塞主线程
    // 支持多任务并行
}
```

### 前端优化

```javascript
// 定时刷新
useEffect(() => {
    const interval = setInterval(() => {
        loadStatistics();
        loadTasks();
    }, 30000); // 30秒刷新一次
    
    return () => clearInterval(interval);
}, []);

// 分页加载
pagination={{
    pageSize: 20,
    showSizeChanger: true,
    showTotal: (total) => `共 ${total} 条记录`
}}
```

## 安全机制

### 防爬虫封禁

```java
// 1. User-Agent设置
.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

// 2. 请求延迟
Thread.sleep(2000 + new Random().nextInt(3000));

// 3. 异常处理
try {
    // 爬取逻辑
} catch (Exception e) {
    log.error("爬取失败: {}", e.getMessage());
    task.setFailedCount(task.getFailedCount() + 1);
}
```

### 数据验证

```java
// 必填字段验证
if (novel.getTitle() == null || novel.getTitle().isEmpty()) {
    throw new IllegalArgumentException("标题不能为空");
}

// URL格式验证
if (!href.startsWith("http")) {
    href = "https://www.qimao.com" + href;
}
```

## 扩展接口

### 自定义分类

```java
// 添加新分类
QimaoCategory category = new QimaoCategory();
category.setCategoryName("新分类");
category.setCategoryCode("new-category");
category.setCategoryUrl("https://...");
qimaoCategoryMapper.insert(category);
```

### 自定义解析规则

```java
// 继承并重写解析方法
public class CustomScraperService extends QimaoScraperService {
    @Override
    protected QimaoNovelDTO parseNovelElement(Element element) {
        // 自定义解析逻辑
        return customParse(element);
    }
}
```

## 监控指标

### 系统监控

- 任务执行状态
- 成功/失败率
- 平均爬取时间
- 数据增长趋势

### 性能监控

- API响应时间
- 数据库查询时间
- 内存使用情况
- 线程池状态

---

**架构版本**: 1.0.0  
**最后更新**: 2024-12-09
