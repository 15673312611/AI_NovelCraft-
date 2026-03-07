# 七猫小说爬虫系统使用指南

## 功能概述

七猫小说爬虫系统是一个完整的数据采集和分析平台，用于爬取七猫中文网的小说数据，并提供可视化的数据分析看板。

### 主要功能

1. **数据爬取**
   - 支持多分类爬取（总裁豪门、古言种田、现代言情等）
   - 自动提取小说标题、作者、简介、字数、状态等信息
   - 可选爬取第一章内容
   - 支持批量爬取和单分类爬取

2. **数据存储**
   - 完整的数据库表结构
   - 支持小说数据、分类配置、任务记录
   - 自动去重和更新

3. **数据分析看板**
   - 实时统计数据展示
   - 分类统计图表
   - 作者排行榜
   - 小说列表查询和筛选
   - 任务执行监控

## 系统架构

### 后端组件

#### 1. 数据库表

**qimao_novels** - 小说数据表
- 存储小说的完整信息
- 包含标题、作者、分类、简介、字数、状态等
- 支持第一章内容存储

**qimao_categories** - 分类配置表
- 管理可爬取的分类
- 包含分类名称、代码、URL等
- 支持启用/禁用

**qimao_scraper_tasks** - 任务记录表
- 记录每次爬取任务
- 跟踪任务状态和进度
- 记录成功/失败数量

#### 2. 核心服务

**QimaoScraperService**
- 爬虫核心逻辑
- 异步任务执行
- HTML解析和数据提取
- 错误处理和重试

#### 3. API接口

```
POST /api/qimao/scrape/{categoryCode}    - 开始爬取指定分类
POST /api/qimao/scrape/all               - 批量爬取所有分类
GET  /api/qimao/categories               - 获取分类列表
GET  /api/qimao/statistics               - 获取统计数据
GET  /api/qimao/novels                   - 获取小说列表
GET  /api/qimao/tasks                    - 获取任务列表
GET  /api/qimao/health                   - 健康检查
```

### 前端组件

**QimaoDashboard** - 数据分析看板
- 统计卡片展示
- 分类统计图表
- 作者排行榜
- 小说列表表格
- 任务监控面板
- 小说详情弹窗

## 安装部署

### 1. 数据库初始化

执行数据库迁移脚本：

```bash
mysql -u root -p your_database < database/migrations/create_qimao_novels_table.sql
```

### 2. 后端配置

确保 `pom.xml` 包含以下依赖：
- Jsoup (HTML解析)
- OkHttp (HTTP客户端)
- Spring Boot Web
- MyBatis Plus

### 3. 启动服务

```bash
# 后端
cd backend
mvn clean install
mvn spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

## 使用说明

### 1. 访问看板

启动系统后，访问：`http://localhost:3000/qimao-dashboard`

### 2. 开始爬取

#### 方式一：单分类爬取
1. 点击"开始爬取"按钮
2. 选择要爬取的分类
3. 系统自动开始爬取（默认3页）

#### 方式二：批量爬取
1. 点击"批量爬取全部"按钮
2. 确认后系统将依次爬取所有分类

### 3. 查看数据

#### 统计概览
- 总小说数
- 总任务数
- 已完成任务
- 进行中任务

#### 分类统计
- 各分类小说数量柱状图
- 实时更新

#### 作者排行
- Top 10 作者及其作品数量
- 动态排序

#### 小说列表
- 支持按分类筛选
- 支持按状态筛选（完结/连载中）
- 分页展示
- 点击查看详情

#### 任务监控
- 查看所有爬取任务
- 实时进度显示
- 成功/失败统计

### 4. 查看小说详情

点击小说标题或"查看详情"按钮，可以看到：
- 完整的小说信息
- 标签列表
- 详细简介
- 第一章内容（如果已爬取）
- 访问原站链接

## API使用示例

### 开始爬取

```bash
# 爬取总裁豪门分类（3页）
curl -X POST "http://localhost:8080/api/qimao/scrape/ceo-mansion?maxPages=3"

# 批量爬取所有分类
curl -X POST "http://localhost:8080/api/qimao/scrape/all?maxPages=3"
```

### 获取统计数据

```bash
curl -X GET "http://localhost:8080/api/qimao/statistics"
```

响应示例：
```json
{
  "totalNovels": 150,
  "categoryStats": [
    {"category": "总裁豪门", "count": 50},
    {"category": "古言种田", "count": 40}
  ],
  "statusStats": [
    {"status": "完结", "count": 80},
    {"status": "连载中", "count": 70}
  ],
  "authorStats": [
    {"author": "作者A", "count": 10},
    {"author": "作者B", "count": 8}
  ],
  "taskStats": {
    "total": 10,
    "completed": 8,
    "running": 1,
    "failed": 1
  }
}
```

### 获取小说列表

```bash
# 获取所有小说
curl -X GET "http://localhost:8080/api/qimao/novels?page=1&pageSize=20"

# 按分类筛选
curl -X GET "http://localhost:8080/api/qimao/novels?category=总裁豪门&page=1&pageSize=20"

# 按状态筛选
curl -X GET "http://localhost:8080/api/qimao/novels?status=完结&page=1&pageSize=20"
```

## 配置说明

### 爬虫参数

在 `QimaoScraperService` 中可以调整：

```java
// HTTP客户端超时时间
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(30, TimeUnit.SECONDS)

// 请求延迟（避免被封）
Thread.sleep(2000 + new Random().nextInt(3000)); // 2-5秒随机延迟
```

### 分类配置

在数据库 `qimao_categories` 表中添加新分类：

```sql
INSERT INTO qimao_categories 
(category_name, category_code, category_url, parent_category, sort_order) 
VALUES 
('新分类', 'new-category', 'https://www.qimao.com/shuku/...', '父分类', 10);
```

## 注意事项

### 1. 爬虫礼仪
- 默认设置了2-5秒的随机延迟
- 避免频繁请求导致IP被封
- 建议在非高峰时段爬取

### 2. 数据更新
- 爬虫会自动检测重复数据
- 已存在的小说会更新信息
- 第一章内容只在首次爬取时获取

### 3. 性能优化
- 异步执行爬取任务
- 批量爬取时有延迟控制
- 数据库索引优化查询

### 4. 错误处理
- 自动记录失败任务
- 详细的错误日志
- 支持手动重试

## 常见问题

### Q1: 爬取失败怎么办？
A: 检查任务列表中的错误信息，常见原因：
- 网络连接问题
- 目标网站结构变化
- IP被限制

### Q2: 如何增加爬取页数？
A: 在爬取时修改 `maxPages` 参数：
```bash
curl -X POST "http://localhost:8080/api/qimao/scrape/ceo-mansion?maxPages=10"
```

### Q3: 数据多久更新一次？
A: 看板每30秒自动刷新统计数据，也可以手动点击"刷新数据"按钮。

### Q4: 如何导出数据？
A: 可以直接查询数据库：
```sql
SELECT * FROM qimao_novels WHERE category = '总裁豪门';
```

## 技术栈

### 后端
- Spring Boot 2.7.18
- MyBatis Plus 3.5.3.1
- Jsoup 1.17.2
- OkHttp 4.12.0
- MySQL 8.0

### 前端
- React 18.2.0
- Ant Design 5.4.0
- TypeScript 5.0.2
- Axios 1.4.0

## 扩展开发

### 添加新的数据字段

1. 修改数据库表结构
2. 更新 `QimaoNovel` 实体类
3. 修改 `QimaoScraperService` 的解析逻辑
4. 更新前端 DTO 和展示组件

### 添加新的统计维度

1. 在 `QimaoNovelMapper` 添加统计查询
2. 在 `QimaoScraperService.getStatistics()` 中调用
3. 在前端看板中添加图表展示

### 自定义爬取规则

修改 `QimaoScraperService.parseNovelElement()` 方法中的选择器：

```java
Element titleElement = element.selectFirst("your-custom-selector");
```

## 维护建议

1. **定期清理数据**：删除过期或无效的小说记录
2. **监控任务状态**：及时处理失败的任务
3. **更新分类配置**：根据网站变化调整分类URL
4. **备份数据库**：定期备份重要数据
5. **日志分析**：查看日志发现潜在问题

## 联系支持

如有问题或建议，请提交 Issue 或联系开发团队。

---

**版本**: 1.0.0  
**更新日期**: 2024-12-09  
**作者**: Novel Creation System Team
