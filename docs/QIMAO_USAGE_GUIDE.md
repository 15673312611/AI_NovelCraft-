# 七猫爬虫使用指南 - 防重复爬取与定时任务

## 📋 功能说明

系统已升级，新增以下功能：

### 1. **防重复爬取机制**
- ✅ 自动记录每个分类的最后爬取时间
- ✅ 根据配置的间隔时间（默认24小时）判断是否需要爬取
- ✅ 避免频繁爬取同一分类
- ✅ 支持强制爬取选项

### 2. **定时任务系统**
- ✅ 每小时自动检查一次
- ✅ 在配置的时间窗口内执行（默认22:00-04:00）
- ✅ 随机选择需要爬取的分类
- ✅ 可手动启用/禁用自动爬取

### 3. **配置管理**
- ✅ 灵活的配置系统
- ✅ 支持在线修改配置
- ✅ 无需重启服务

## 🚀 使用方式

### 方式一：手动爬取（推荐首次使用）

#### 1. 初次爬取所有分类
```bash
# 访问看板，点击"批量爬取全部"按钮
# 或使用API
curl -X POST "http://localhost:8080/api/qimao/scrape/all?maxPages=3"
```

**说明**：
- 首次爬取会获取所有分类的数据
- 每个分类爬取3页（约60-90本小说）
- 爬取完成后会记录时间，24小时内不会重复爬取

#### 2. 单个分类爬取
```bash
# 通过看板选择分类
# 或使用API
curl -X POST "http://localhost:8080/api/qimao/scrape/ceo-mansion?maxPages=3"
```

**防重复机制**：
- 如果该分类在24小时内已爬取过，会自动跳过
- 任务状态显示为 `SKIPPED`
- 可查看任务列表确认

### 方式二：自动定时爬取（推荐日常使用）

#### 1. 启用自动爬取

```bash
# 修改配置启用自动爬取
curl -X PUT "http://localhost:8080/api/qimao/config/enable_auto_scrape?value=true"
```

#### 2. 配置爬取时间窗口

```bash
# 设置开始时间（晚上10点）
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_time_start?value=22:00"

# 设置结束时间（凌晨4点）
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_time_end?value=04:00"
```

#### 3. 设置爬取间隔

```bash
# 设置24小时爬取一次
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_interval_hours?value=24"
```

## ⚙️ 配置说明

### 可配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `enable_auto_scrape` | false | 是否启用自动爬取 |
| `scrape_interval_hours` | 24 | 爬取间隔时间（小时） |
| `scrape_time_start` | 22:00 | 每日爬取开始时间 |
| `scrape_time_end` | 04:00 | 每日爬取结束时间 |
| `max_pages_per_category` | 3 | 每个分类最大爬取页数 |
| `scrape_delay_min` | 2000 | 请求最小延迟（毫秒） |
| `scrape_delay_max` | 5000 | 请求最大延迟（毫秒） |

### 查看当前配置

```bash
curl -X GET "http://localhost:8080/api/qimao/config"
```

响应示例：
```json
[
  {
    "id": 1,
    "configKey": "scrape_interval_hours",
    "configValue": "24",
    "description": "爬取间隔时间（小时）"
  },
  {
    "id": 2,
    "configKey": "enable_auto_scrape",
    "configValue": "true",
    "description": "是否启用自动爬取"
  }
]
```

### 修改配置

```bash
# 格式：PUT /api/qimao/config/{配置键}?value={新值}
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_interval_hours?value=12"
```

## 🕐 定时任务工作原理

### 执行流程

```
每小时整点
   ↓
检查是否启用自动爬取
   ↓
检查当前时间是否在配置的时间窗口内
   ↓
获取需要爬取的分类列表
  （距离上次爬取超过配置间隔的分类）
   ↓
随机选择一个分类
   ↓
执行爬取
   ↓
更新分类的最后爬取时间
```

### 时间窗口说明

**跨天时间窗口**（如22:00-04:00）：
- 晚上22:00到次日凌晨04:00之间
- 系统会自动处理跨天逻辑

**同天时间窗口**（如09:00-17:00）：
- 当天上午9:00到下午17:00之间

### 随机性说明

- 每小时检查时，如果有多个分类需要爬取
- 系统会**随机选择一个**进行爬取
- 这样可以：
  - 分散爬取压力
  - 避免同时爬取多个分类
  - 更自然的访问模式

## 📊 使用场景示例

### 场景1：首次使用

```bash
# 1. 初始化数据库
mysql -u root -p your_database < database/migrations/create_qimao_novels_table.sql

# 2. 启动服务
cd backend && mvn spring-boot:run

# 3. 首次批量爬取
curl -X POST "http://localhost:8080/api/qimao/scrape/all?maxPages=3"

# 4. 等待爬取完成（约10-15分钟）

# 5. 启用自动爬取
curl -X PUT "http://localhost:8080/api/qimao/config/enable_auto_scrape?value=true"
```

### 场景2：每日自动更新

```bash
# 配置为每晚自动爬取
curl -X PUT "http://localhost:8080/api/qimao/config/enable_auto_scrape?value=true"
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_time_start?value=22:00"
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_time_end?value=04:00"
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_interval_hours?value=24"
```

**效果**：
- 每晚22:00-04:00之间
- 系统每小时检查一次
- 自动爬取距离上次爬取超过24小时的分类
- 无需人工干预

### 场景3：手动触发检查

```bash
# 不等定时任务，立即检查并爬取
curl -X POST "http://localhost:8080/api/qimao/trigger-schedule"
```

**说明**：
- 手动触发定时任务逻辑
- 会立即检查是否需要爬取
- 仍然遵循时间窗口和间隔限制

### 场景4：强制重新爬取某个分类

如果需要立即重新爬取某个分类（忽略间隔限制）：

```bash
# 通过前端看板的"开始爬取"按钮
# 或直接调用API（会检查间隔）
curl -X POST "http://localhost:8080/api/qimao/scrape/ceo-mansion?maxPages=3"
```

**注意**：
- 当前API会检查间隔时间
- 如果需要强制爬取功能，可以联系开发团队添加

## 🔍 监控与查看

### 查看分类爬取状态

```bash
curl -X GET "http://localhost:8080/api/qimao/categories"
```

响应包含：
- `lastScrapeTime`: 最后爬取时间
- `scrapeCount`: 爬取次数

### 查看任务历史

```bash
curl -X GET "http://localhost:8080/api/qimao/tasks?page=1&pageSize=20"
```

任务状态：
- `PENDING`: 等待执行
- `RUNNING`: 正在执行
- `COMPLETED`: 已完成
- `FAILED`: 失败
- `SKIPPED`: 跳过（未到爬取间隔）

### 查看统计数据

```bash
curl -X GET "http://localhost:8080/api/qimao/statistics"
```

## ⚠️ 注意事项

### 1. 首次使用建议

- ✅ 先手动爬取一次所有分类
- ✅ 确认数据正常后再启用自动爬取
- ✅ 观察任务执行情况

### 2. 时间窗口设置

- ✅ 建议设置在深夜（如22:00-04:00）
- ✅ 避开网站访问高峰期
- ✅ 降低被封IP的风险

### 3. 爬取间隔

- ✅ 默认24小时合理
- ✅ 不建议设置过短（如<12小时）
- ✅ 根据实际需求调整

### 4. 数据更新频率

- 七猫网站的小说数据不会频繁变化
- 每天爬取一次足够
- 过于频繁爬取可能被限制

### 5. 服务器要求

- 确保服务器在配置的时间窗口内运行
- 如果服务器重启，定时任务会自动恢复
- 建议使用进程守护工具（如systemd）

## 🛠️ 故障排除

### 问题1：自动爬取没有执行

**检查清单**：
1. 确认 `enable_auto_scrape` 为 `true`
2. 确认当前时间在配置的时间窗口内
3. 确认有分类需要爬取（距离上次爬取超过间隔）
4. 查看日志是否有错误信息

### 问题2：所有分类都被跳过

**原因**：所有分类的最后爬取时间都在间隔时间内

**解决方案**：
- 等待间隔时间过后
- 或调整 `scrape_interval_hours` 配置
- 或手动触发爬取

### 问题3：想要立即重新爬取

**方案1**：手动触发
```bash
curl -X POST "http://localhost:8080/api/qimao/scrape/ceo-mansion?maxPages=3"
```

**方案2**：清空最后爬取时间（数据库操作）
```sql
UPDATE qimao_categories SET last_scrape_time = NULL WHERE category_code = 'ceo-mansion';
```

## 📈 最佳实践

### 推荐配置

```bash
# 启用自动爬取
curl -X PUT "http://localhost:8080/api/qimao/config/enable_auto_scrape?value=true"

# 每天晚上10点到凌晨4点之间爬取
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_time_start?value=22:00"
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_time_end?value=04:00"

# 24小时爬取一次
curl -X PUT "http://localhost:8080/api/qimao/config/scrape_interval_hours?value=24"

# 每个分类爬取3页
curl -X PUT "http://localhost:8080/api/qimao/config/max_pages_per_category?value=3"
```

### 使用流程

```
首次使用
   ↓
手动批量爬取所有分类
   ↓
查看数据是否正常
   ↓
启用自动爬取
   ↓
配置时间窗口和间隔
   ↓
系统自动维护数据
   ↓
定期查看统计数据
```

## 📞 技术支持

如有问题，请查看：
- 系统日志：`logs/spring.log`
- 任务列表：看板 -> 爬取任务标签页
- API文档：`http://localhost:8080/swagger-ui.html`

---

**版本**: 2.0.0  
**更新日期**: 2024-12-09  
**新增功能**: 防重复爬取、定时任务、配置管理
