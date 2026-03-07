# 前端设计系统文档 v2.0

## 🎨 设计理念

本次重新设计采用现代化、简洁、高级的设计风格，参考了 Vercel、Linear、Stripe 等大厂的设计语言，打造企业级的管理后台界面。

## 🚀 技术栈升级

### 核心依赖
- **React 18** - 最新的 React 版本
- **Ant Design 5.13** - 企业级 UI 组件库
- **@ant-design/pro-components** - 阿里巴巴企业级组件
- **@emotion/react & @emotion/styled** - CSS-in-JS 解决方案
- **framer-motion** - 流畅的动画库
- **ahooks** - 高质量 React Hooks 库

### 主题系统
使用 Ant Design 5.x 的 ConfigProvider 主题定制功能，统一管理所有组件样式。

## 🌈 配色方案

### 主色调 - 蓝绿色系
- **主色**: `#0ea5e9` (Sky Blue)
- **辅助色**: `#06b6d4` (Cyan)
- **成功色**: `#22c55e` (Green)
- **警告色**: `#f97316` (Orange)
- **错误色**: `#ef4444` (Red)
- **信息色**: `#3b82f6` (Blue)
- **紫色**: `#a855f7` (Purple)

### 背景色
- **主背景**: `#0a0a0a` (Pure Black)
- **次级背景**: `#171717` (Dark Gray)
- **三级背景**: `#262626` (Medium Gray)
- **悬浮背景**: `#1c1c1c` (Elevated)

### 文字颜色
- **主文字**: `#fafafa` (Almost White)
- **次级文字**: `rgba(250, 250, 250, 0.65)`
- **三级文字**: `rgba(250, 250, 250, 0.45)`
- **四级文字**: `rgba(250, 250, 250, 0.25)`

### 边框颜色
- **主边框**: `rgba(255, 255, 255, 0.08)`
- **次级边框**: `rgba(255, 255, 255, 0.06)`
- **悬停边框**: `rgba(14, 165, 233, 0.3)`

## 📐 设计规范

### 圆角
- **xs**: 4px
- **sm**: 6px
- **md**: 8px (默认)
- **lg**: 10px
- **xl**: 12px
- **2xl**: 16px

### 间距
- **xs**: 4px
- **sm**: 8px
- **md**: 12px
- **lg**: 16px
- **xl**: 20px
- **2xl**: 24px
- **3xl**: 32px

### 阴影
- **sm**: `0 2px 4px rgba(0, 0, 0, 0.3)`
- **md**: `0 4px 12px rgba(0, 0, 0, 0.4)`
- **lg**: `0 8px 24px rgba(0, 0, 0, 0.5)`
- **xl**: `0 16px 48px rgba(0, 0, 0, 0.6)`
- **glow**: `0 0 20px rgba(14, 165, 233, 0.4)`

### 渐变
```css
--gradient-primary: linear-gradient(135deg, #0ea5e9, #06b6d4);
--gradient-success: linear-gradient(135deg, #22c55e, #16a34a);
--gradient-warning: linear-gradient(135deg, #f97316, #ea580c);
--gradient-error: linear-gradient(135deg, #ef4444, #dc2626);
--gradient-purple: linear-gradient(135deg, #a855f7, #9333ea);
```

## 🧩 组件库

### 核心组件

#### PageContainer
页面容器组件，提供统一的页面布局和面包屑导航。

```tsx
<PageContainer
  title="用户管理"
  description="管理系统中的所有用户账户"
  icon={<UserOutlined />}
  breadcrumb={[{ title: '用户管理' }]}
  extra={<Button type="primary">新增用户</Button>}
>
  {children}
</PageContainer>
```

#### StatCard
统计卡片组件，用于展示关键指标。

```tsx
<StatCard
  title="总用户数"
  value={1248}
  icon={<UserOutlined />}
  gradient={['#0ea5e9', '#06b6d4']}
  trend="up"
  trendValue={12.5}
/>
```

#### DataTable
数据表格组件，封装了搜索、刷新、分页等功能。

```tsx
<DataTable
  columns={columns}
  dataSource={data}
  loading={loading}
  searchPlaceholder="搜索..."
  onSearch={handleSearch}
  onRefresh={handleRefresh}
/>
```

#### StatusTag
状态标签组件，支持多种预设状态。

```tsx
<StatusTag status="COMPLETED" />
<StatusTag status="RUNNING" />
<StatusTag status="FAILED" />
```

#### ActionButton
操作按钮组件，支持确认弹窗。

```tsx
<ActionButton
  variant="danger"
  icon={<DeleteOutlined />}
  confirmTitle="确认删除"
  onConfirm={handleDelete}
>
  删除
</ActionButton>
```

#### InfoCard
信息卡片组件，用于配置页面。

```tsx
<InfoCard
  title="API 配置"
  description="配置 OpenAI 兼容的 API 服务"
  icon={<ApiOutlined />}
  iconGradient="linear-gradient(135deg, #0ea5e9, #06b6d4)"
>
  {children}
</InfoCard>
```

#### UserAvatar
用户头像组件，自动生成渐变色。

```tsx
<UserAvatar name="张三" subtitle="admin@example.com" />
```

## 🎭 动画效果

使用 framer-motion 实现流畅的页面过渡和组件动画。

### 页面进入动画
```tsx
<motion.div
  initial={{ opacity: 0, y: 16 }}
  animate={{ opacity: 1, y: 0 }}
  transition={{ duration: 0.4 }}
>
  {content}
</motion.div>
```

### 列表交错动画
```tsx
const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.1 },
  },
}

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
}
```

## 📱 响应式设计

### 断点
- **xs**: < 576px
- **sm**: ≥ 576px
- **md**: ≥ 768px
- **lg**: ≥ 992px
- **xl**: ≥ 1200px
- **xxl**: ≥ 1600px

### 移动端适配
- 侧边栏可折叠
- 表格支持横向滚动
- 卡片自适应宽度

## 🎯 最佳实践

### 代码规范
- 使用 TypeScript 类型定义
- 使用 @emotion/styled 编写样式
- 组件拆分合理，职责单一
- 遵循 React Hooks 规范

### 命名规范
- 组件名: PascalCase
- 函数名: camelCase
- 常量名: UPPER_SNAKE_CASE
- Styled 组件: PascalCase

### 主题使用
```tsx
import { darkTheme, designTokens } from '@/theme'

// 使用主题配置
<ConfigProvider theme={darkTheme}>
  <App />
</ConfigProvider>

// 使用设计令牌
const StyledDiv = styled.div`
  background: ${designTokens.gradients.primary};
  box-shadow: ${designTokens.shadows.md};
`
```

## 🔄 更新日志

### v2.0.0 (2026-01-08)
- ✨ 全面升级组件库架构
- 🎨 引入 @emotion/styled CSS-in-JS
- 🚀 添加 framer-motion 动画
- 📦 新增企业级组件：PageContainer、StatCard、DataTable、InfoCard
- 🎯 统一主题配置，使用 Ant Design 5.x 主题系统
- 💅 优化所有页面的视觉效果和交互体验
- 📱 改进响应式布局
- 🧹 移除冗余的 CSS 文件，统一使用 CSS-in-JS

---

**设计团队**: AI Novel Platform Design Team  
**最后更新**: 2026-01-08
