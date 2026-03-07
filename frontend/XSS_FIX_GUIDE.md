# 前端XSS防护修复指南

## 1. 安装DOMPurify

在frontend目录下运行：

```bash
npm install dompurify
npm install --save-dev @types/dompurify
```

## 2. 修改 AnnouncementModal.tsx

将以下代码替换到 `frontend/src/components/AnnouncementModal.tsx`:

```tsx
import React, { useEffect, useState, useMemo } from 'react'
import DOMPurify from 'dompurify'
import { announcementService, Announcement } from '@/services/announcementService'
import './AnnouncementModal.css'

// 简单的文本格式化处理
const formatContent = (content: string): string => {
  if (!content) return ''
  
  // 如果已经是 HTML 格式，直接返回
  if (content.includes('<') && content.includes('>')) {
    return content
  }
  
  // 处理纯文本格式
  let formatted = content
    // 处理标题 (### 或 ##)
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    // 处理粗体 **text** 或 __text__
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.+?)__/g, '<strong>$1</strong>')
    // 处理斜体 *text* 或 _text_
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/_([^_]+)_/g, '<em>$1</em>')
    // 处理行内代码 `code`
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // 处理链接 [text](url)
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
    // 处理无序列表
    .replace(/^[•\-\*] (.+)$/gm, '<li>$1</li>')
    // 处理有序列表
    .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
    // 处理分割线
    .replace(/^---+$/gm, '<hr />')
    // 处理引用
    .replace(/^> (.+)$/gm, '<blockquote><p>$1</p></blockquote>')
    // 处理换行
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br />')
  
  // 包装列表项
  formatted = formatted.replace(/(<li>.*<\/li>)+/g, (match) => {
    return `<ul>${match}</ul>`
  })
  
  // 包装段落
  if (!formatted.startsWith('<')) {
    formatted = `<p>${formatted}</p>`
  }
  
  return formatted
}

const AnnouncementModal: React.FC = () => {
  const [visible, setVisible] = useState(false)
  const [announcement, setAnnouncement] = useState<Announcement | null>(null)
  const [isClosing, setIsClosing] = useState(false)

  useEffect(() => {
    checkAnnouncement()
  }, [])

  const checkAnnouncement = async () => {
    console.log('🔔 开始检查公告...')
    const data = await announcementService.getAnnouncement()
    console.log('🔔 获取到公告数据:', data)
    if (data && announcementService.shouldShowAnnouncement(data)) {
      console.log('🔔 需要显示公告')
      setAnnouncement(data)
      setVisible(true)
    } else {
      console.log('🔔 不需要显示公告, enabled:', data?.enabled)
    }
  }

  const handleClose = () => {
    setIsClosing(true)
    setTimeout(() => {
      if (announcement) {
        announcementService.markAsShown(announcement)
      }
      setVisible(false)
      setIsClosing(false)
    }, 200)
  }

  // 格式化并清理内容（XSS防护）
  const sanitizedContent = useMemo(() => {
    if (!announcement) return ''
    const formatted = formatContent(announcement.content)
    // 使用DOMPurify清理HTML，防止XSS攻击
    return DOMPurify.sanitize(formatted, {
      ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'code', 'a', 'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'hr', 'blockquote'],
      ALLOWED_ATTR: ['href', 'target', 'rel', 'class']
    })
  }, [announcement])

  if (!visible || !announcement) return null

  return (
    <div className={`announcement-overlay ${isClosing ? 'closing' : ''}`} onClick={handleClose}>
      <div className={`announcement-modal ${isClosing ? 'closing' : ''}`} onClick={e => e.stopPropagation()}>
        {/* 关闭按钮 */}
        <button className="announcement-close-btn" onClick={handleClose} aria-label="关闭公告">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6L6 18M6 6l12 12" />
          </svg>
        </button>

        {/* 装饰背景 */}
        <div className="announcement-decoration">
          <div className="decoration-circle circle-1"></div>
          <div className="decoration-circle circle-2"></div>
          <div className="decoration-circle circle-3"></div>
        </div>

        {/* 内容区域 */}
        <div className="announcement-content">
          {/* 图标 */}
          <div className="announcement-icon-wrapper">
            <div className="announcement-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
            </div>
          </div>

          {/* 标题 */}
          <h2 className="announcement-title">{announcement.title || '系统公告'}</h2>

          {/* 内容 - 使用DOMPurify清理后的内容 */}
          <div 
            className="announcement-body"
            dangerouslySetInnerHTML={{ __html: sanitizedContent }}
          />

          {/* 按钮 */}
          <button className="announcement-confirm-btn" onClick={handleClose}>
            <span>我知道了</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  )
}

export default AnnouncementModal
```

## 3. 修改 MarkdownRenderer.tsx

将以下代码替换到 `frontend/src/components/MarkdownRenderer.tsx`:

```tsx
import React, { useMemo } from 'react'
import DOMPurify from 'dompurify'
import './MarkdownRenderer.css'

interface MarkdownRendererProps {
  content: string
  compact?: boolean // 是否使用紧凑模式
}

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, compact = false }) => {
  const sanitizedContent = useMemo(() => {
    const rendered = renderMarkdown(content)
    // 使用DOMPurify清理HTML，防止XSS攻击
    return DOMPurify.sanitize(rendered, {
      ALLOWED_TAGS: [
        'div', 'pre', 'code', 'strong', 'em', 'br', 'hr',
        'span', 'p', 'ul', 'ol', 'li', 'blockquote'
      ],
      ALLOWED_ATTR: ['class']
    })
  }, [content])

  const renderMarkdown = (text: string) => {
    return text
      // 代码块
      .replace(/```([^`]+)```/g, '<pre class="md-code-block">$1</pre>')
      .replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')
      // 标题
      .replace(/^# (.+)$/gm, '<div class="md-h1">$1</div>')
      .replace(/^## (.+)$/gm, '<div class="md-h2">$1</div>')
      .replace(/^### (.+)$/gm, '<div class="md-h3">$1</div>')
      .replace(/^#### (.+)$/gm, '<div class="md-h4">$1</div>')
      // 引用块
      .replace(/^> (.+)$/gm, '<div class="md-blockquote">$1</div>')
      // 列表
      .replace(/^- (.+)$/gm, '<div class="md-list-item"><span class="md-bullet">•</span>$1</div>')
      .replace(/^\* (.+)$/gm, '<div class="md-list-item"><span class="md-bullet">•</span>$1</div>')
      .replace(/^\d+\. (.+)$/gm, (match, p1) => {
        const numMatch = match.match(/^(\d+)\./)
        const num = numMatch ? numMatch[1] : '1'
        return `<div class="md-list-item"><span class="md-number">${num}.</span>${p1}</div>`
      })
      // 粗体和斜体
      .replace(/\*\*(.+?)\*\*/g, '<strong class="md-bold">$1</strong>')
      .replace(/\*(.+?)\*/g, '<em class="md-italic">$1</em>')
      // 分隔线
      .replace(/^---$/gm, '<hr class="md-separator" />')
      // 处理换行：连续2个以上换行转为段落分隔，单个换行保持
      .replace(/\n{3,}/g, '<div class="md-paragraph-break"></div>')
      .replace(/\n{2}/g, '<div class="md-paragraph-break"></div>')
      .replace(/\n/g, '<br/>')
  }

  return (
    <div 
      className={`markdown-renderer ${compact ? 'compact' : ''}`}
      dangerouslySetInnerHTML={{ __html: sanitizedContent }}
    />
  )
}

export default MarkdownRenderer
```

## 4. 验证修复

安装完成后，测试XSS防护：

1. 在管理后台创建公告，尝试注入以下内容：
```html
<img src=x onerror="alert('XSS')">
<script>alert('XSS')</script>
```

2. 正常情况下，这些脚本会被DOMPurify过滤掉，不会执行。

## 5. 运行命令

```bash
cd frontend
npm install dompurify @types/dompurify
npm run dev
```

修复完成后，前端将具备XSS防护能力。
