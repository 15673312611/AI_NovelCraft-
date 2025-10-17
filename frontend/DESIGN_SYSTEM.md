# 🎨 现代化设计系统

## 概述

全新的现代化、简约、专业的设计系统，灵感来源于 Notion、Linear、Vercel 等现代 SaaS 产品。

## 核心原则

1. **少即是多** - 避免过度装饰和花哨效果
2. **内容优先** - 界面退后，让内容成为焦点
3. **一致性** - 统一的设计语言贯穿全站
4. **性能优先** - 轻量级阴影和动画

## 🎯 设计令牌 (Design Tokens)

所有设计变量定义在 `src/styles/design-tokens.css`

### 配色方案

#### 主色系 (Primary)
```css
--primary-500: #a855f7  /* 主色 - 优雅紫色 */
--primary-600: #9333ea  /* 悬停状态 */
--primary-50:  #faf5ff  /* 浅色背景 */
```

#### 中性灰 (Neutrals)
```css
--gray-50:  #fafaf9  /* 最浅 */
--gray-100: #f5f5f4
--gray-200: #e7e5e4  /* 边框 */
--gray-500: #78716c  /* 次要文本 */
--gray-900: #1c1917  /* 主要文本 */
```

#### 功能色 (Semantic)
```css
--success: #10b981  /* 成功 - 绿色 */
--warning: #f59e0b  /* 警告 - 橙色 */
--error:   #ef4444  /* 错误 - 红色 */
--info:    #3b82f6  /* 信息 - 蓝色 */
```

### 阴影系统（轻量化）

```css
--shadow-xs: 0 1px 2px rgba(0, 0, 0, 0.04)
--shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.06)
--shadow-md: 0 4px 12px rgba(0, 0, 0, 0.08)
--shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.10)
```

### 间距系统（8px网格）

```css
--space-1: 0.25rem  /* 4px */
--space-2: 0.5rem   /* 8px */
--space-4: 1rem     /* 16px */
--space-6: 1.5rem   /* 24px */
--space-8: 2rem     /* 32px */
```

### 字体系统

```css
--font-sans: 'Inter', -apple-system, sans-serif
--font-mono: 'JetBrains Mono', monospace

/* 字号 */
--text-xs:   0.75rem   /* 12px */
--text-sm:   0.875rem  /* 14px */
--text-base: 1rem      /* 16px */
--text-lg:   1.125rem  /* 18px */
--text-xl:   1.25rem   /* 20px */
```

## 📦 组件使用示例

### 按钮

```tsx
// 主要按钮
<button className="header-btn btn-primary">
  确认
</button>

// 次要按钮
<button className="header-btn btn-ghost">
  取消
</button>
```

### 卡片

```tsx
<div className="novel-card">
  <h3 className="card-title">标题</h3>
  <p className="card-description">描述</p>
</div>
```

**CSS 样式：**
```css
.novel-card {
  background: var(--bg-base);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  transition: all var(--transition-base);
}

.novel-card:hover {
  border-color: var(--primary-300);
  box-shadow: var(--shadow-md);
  transform: translateY(-4px);
}
```

### 标签（克制使用颜色）

```tsx
// 只使用4种标签颜色
<Tag className="genre-tag">类型</Tag>      // 紫色
<Tag className="success-tag">成功</Tag>    // 绿色
<Tag className="warning-tag">警告</Tag>    // 橙色
<Tag className="error-tag">错误</Tag>      // 红色
```

## 🚀 迁移指南

### 已完成迁移

✅ **导航栏** (`AppHeader.new.tsx`)
- 去除花哨渐变和过度阴影
- 扁平化设计
- 统一圆角和间距

✅ **小说列表页** (`NovelListPage.new.tsx`)
- 简约卡片设计
- 统一配色方案
- 轻量级交互动效

### 待迁移页面

⏳ **卷管理页** (`VolumeManagementPage.tsx`)
- 步骤指示器简化
- 统计卡片去除渐变
- 卷卡片扁平化

⏳ **写作工作室** (`VolumeWritingStudio.tsx`)
- 沉浸式编辑器
- 侧边栏优化
- 工具栏扁平化

## 📋 设计检查清单

在创建新页面或组件时，请检查：

- [ ] 使用设计令牌变量（不直接写颜色值）
- [ ] 遵循8px间距系统
- [ ] 使用统一圆角 (var(--radius-md) / var(--radius-lg))
- [ ] 阴影不超过 --shadow-lg
- [ ] 颜色只使用主色系+4种功能色
- [ ] 过渡动画不超过300ms
- [ ] 文本使用语义化颜色变量

## 🎨 颜色使用规则

### ✅ 好的做法

```css
/* 使用设计令牌 */
background: var(--primary-500);
color: var(--text-primary);
border: 1px solid var(--border-base);
```

### ❌ 避免做法

```css
/* 不要直接写颜色值 */
background: #6366f1;
color: #1c1917;

/* 不要使用复杂渐变 */
background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

/* 不要使用过重阴影 */
box-shadow: 0 24px 48px rgba(40, 54, 90, 0.12);
```

## 📐 布局规范

### 页面最大宽度
```css
max-width: 1440px;
margin: 0 auto;
```

### 内容间距
```css
padding: var(--space-8) var(--space-6);  /* 桌面端 */
padding: var(--space-6) var(--space-4);  /* 移动端 */
```

### 组件间距
```css
gap: var(--space-4);  /* 默认 */
gap: var(--space-6);  /* 较大 */
```

## 🎭 动画规范

### 过渡动画
```css
transition: all var(--transition-fast);  /* 150ms - 按钮 */
transition: all var(--transition-base);  /* 200ms - 卡片 */
transition: all var(--transition-slow);  /* 300ms - 模态框 */
```

### 悬停效果（轻量级）
```css
.card:hover {
  transform: translateY(-4px);  /* 不超过4px */
  box-shadow: var(--shadow-md);  /* 轻量阴影 */
}
```

## 📱 响应式断点

```css
/* 移动端 */
@media (max-width: 768px) { }

/* 平板 */
@media (min-width: 769px) and (max-width: 1024px) { }

/* 桌面端 */
@media (min-width: 1025px) { }
```

## 🔧 开发工具

### VS Code 插件推荐
- **CSS Variable Autocomplete** - CSS变量自动补全
- **Color Highlight** - 颜色高亮显示
- **Tailwind CSS IntelliSense** - 如果使用Tailwind

### 浏览器插件
- **React Developer Tools**
- **Lighthouse** - 性能检测

## 📚 参考资源

- [Notion Design](https://www.notion.so/)
- [Linear Design](https://linear.app/)
- [Vercel Design](https://vercel.com/)
- [Shadcn UI](https://ui.shadcn.com/)
- [Radix Colors](https://www.radix-ui.com/colors)

## 🤝 贡献指南

1. 新增组件前先查看是否有类似设计令牌
2. 保持设计一致性
3. 避免引入新的颜色值
4. 确保无障碍访问（ARIA、键盘导航）
5. 测试响应式布局

---

**最后更新：** 2024-10-10
**维护者：** 小说创作系统开发团队

