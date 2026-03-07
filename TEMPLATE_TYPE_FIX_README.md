# 提示词模板类型修复说明

## 问题描述

在后台管理系统中添加的官方模板（特别是 `category='chapter'` 的写作模板），其 `type` 字段被错误地设置为 `custom`（用户自定义），导致前端写作工作室无法查询到这些模板。

## 问题原因

后台管理系统在创建模板时，没有正确设置 `type` 字段为 `official`，而是默认或错误地设置为了 `custom`。

## 修复方案

### 1. 修复数据库中已有的错误数据

执行以下SQL脚本修复已存在的错误数据：

```sql
-- 文件位置：backend/sql/fix_template_type_to_official.sql

UPDATE prompt_templates 
SET type = 'official'
WHERE category = 'chapter' 
  AND type = 'custom'
  AND user_id IS NULL;  -- 只修复没有关联用户的模板（真正的官方模板）
```

### 2. 实体类添加详细注释

已在 `PromptTemplate.java` 实体类中为 `type` 和 `userId` 字段添加了详细的注释说明，防止后续开发时再次犯错。

```java
/**
 * 模板类型：official-官方，custom-用户自定义
 * 
 * 重要提示：
 * - official: 官方模板，所有用户可见，user_id 为 NULL
 * - custom: 用户自定义模板，仅创建者可见，user_id 不为 NULL
 * 
 * 注意：后台管理系统添加的模板应设置为 'official' 类型！
 */
private String type;

/**
 * 用户ID（官方模板为NULL）
 * 
 * 重要提示：
 * - 如果 type='official'，则 user_id 必须为 NULL
 * - 如果 type='custom'，则 user_id 必须有值
 */
private Long userId;
```

### 3. 分类字段完整说明

同时为 `category` 字段添加了所有支持的分类类型说明：

- `system_identity`: 系统身份模板
- `writing_style`: 写作风格模板
- `anti_ai`: 去AI味模板
- **`chapter`: 章节写作模板（用于AI辅助写作）** ← 写作工作室使用的分类
- `outline`: 大纲生成模板
- `character`: 角色设定模板
- `worldbuilding`: 世界设定模板
- `tool`: 工具类模板
- `brainstorm`: 脑洞创意模板
- `title_synopsis`: 书名简介模板
- `cover`: 封面模板
- `other`: 其他类型

## 后台管理系统注意事项

### ⚠️ 重要提醒

在后台管理系统添加提示词模板时，请确保：

1. **官方模板必须设置**：
   - `type = 'official'`
   - `user_id = NULL`

2. **用户自定义模板必须设置**：
   - `type = 'custom'`
   - `user_id = 实际用户ID`

3. **写作工作室的模板必须使用**：
   - `category = 'chapter'`
   - `type = 'official'`

### 验证方法

添加模板后，可以执行以下SQL验证：

```sql
-- 检查是否有错误的模板（type=custom 但 user_id=NULL）
SELECT id, name, type, category, user_id
FROM prompt_templates
WHERE type = 'custom' AND user_id IS NULL;

-- 检查 chapter 分类的模板是否正确
SELECT id, name, type, category, user_id, is_active
FROM prompt_templates
WHERE category = 'chapter'
ORDER BY id;
```

## 相关文件

- 数据库修复脚本：`backend/sql/fix_template_type_to_official.sql`
- 实体类：`backend/src/main/java/com/novel/domain/entity/PromptTemplate.java`
- 查询验证脚本：`backend/sql/check_template_categories.sql`
- 添加模板示例：`backend/sql/add_writing_content_templates.sql`

## 前端查询逻辑

前端写作工作室查询模板的逻辑：

```typescript
// GET /prompt-templates/public?category=chapter

// 后端会查询：
// WHERE type = 'official' 
//   AND is_active = true
//   AND category = 'chapter'
```

因此，只有同时满足以下条件的模板才会显示在写作工作室中：
- ✅ `type = 'official'`
- ✅ `category = 'chapter'`
- ✅ `is_active = true`

## 修复时间

2026-01-11

## 修复人员

AI Assistant (Claude)
