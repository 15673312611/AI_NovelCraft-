# 写作模板功能修复

## 问题

1. 后台管理添加了写作模板，但客户端前端 `/novels/115/writing-studio` 查询不到
2. 需要添加排序功能
3. 需要添加设置默认模板功能

## 根本原因

前端查询使用 `category: 'writing_style'`，但后台管理创建模板时使用 `category: 'chapter'`，导致分类不匹配。

## 解决方案

### 1. 数据库更新

执行SQL添加 `sort_order` 字段：

```bash
mysql -u root -p ai_novel < backend/sql/add_sort_order_to_prompt_templates.sql
```

### 2. 修改前端查询分类

已修改 `frontend/src/services/promptTemplateService.ts`，将 `category` 从 `'writing_style'` 改为 `'chapter'`。

### 3. 后端添加排序和默认模板功能

- 添加 `sort_order` 字段到实体类
- 更新查询排序逻辑：`is_default DESC, sort_order ASC, type DESC, created_time DESC`
- 添加设置默认模板接口：`POST /prompt-templates/{id}/set-default`
- 添加更新排序接口：`PUT /prompt-templates/{id}/sort-order`

### 4. 管理后台前端更新

- 显示默认模板标识
- 添加"设为默认"按钮
- 添加排序输入框

## 部署步骤

1. 执行数据库脚本
2. 重新编译后端：`cd backend && mvn clean package`
3. 重新编译管理后台后端：`cd admin/backend && mvn clean package`
4. 重启服务

## 测试

1. 在管理后台创建一个 `category='chapter'` 的模板
2. 设置为默认，调整排序
3. 访问 `/novels/115/writing-studio`，点击"模板"按钮
4. 应该能看到模板列表，默认模板在最前面

## 注意

- 模板内容就是系统提示词，直接发给AI，不需要占位符
- 每个分类只能有一个默认模板
- 排序值越小越靠前
