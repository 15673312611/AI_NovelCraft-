import React, { useEffect, useState, useMemo } from 'react'
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

  // 格式化内容
  const formattedContent = useMemo(() => {
    return announcement ? formatContent(announcement.content) : ''
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

          {/* 内容 - 支持富文本渲染 */}
          <div 
            className="announcement-body"
            dangerouslySetInnerHTML={{ __html: formattedContent }}
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
