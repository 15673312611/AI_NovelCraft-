import React, { useEffect, useMemo, useState } from 'react'
import DOMPurify from 'dompurify'
import { announcementService, Announcement } from '@/services/announcementService'
import './AnnouncementModal.css'

const formatContent = (content: string): string => {
  if (!content) return ''

  if (content.includes('<') && content.includes('>')) {
    return content
  }

  let formatted = content
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.+?)__/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/_([^_]+)_/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
    .replace(/^[•\-\*] (.+)$/gm, '<li>$1</li>')
    .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
    .replace(/^---+$/gm, '<hr />')
    .replace(/^> (.+)$/gm, '<blockquote><p>$1</p></blockquote>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br />')

  formatted = formatted.replace(/(<li>.*<\/li>)+/g, (match) => {
    return `<ul>${match}</ul>`
  })

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
    console.log('Checking announcement...')
    const data = await announcementService.getAnnouncement()
    console.log('Announcement payload:', data)
    if (data && announcementService.shouldShowAnnouncement(data)) {
      setAnnouncement(data)
      setVisible(true)
    } else {
      console.log('Announcement hidden, enabled:', data?.enabled)
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

  const formattedContent = useMemo(() => {
    if (!announcement) return ''

    return DOMPurify.sanitize(formatContent(announcement.content), {
      USE_PROFILES: { html: true },
      ADD_ATTR: ['target', 'rel', 'class']
    })
  }, [announcement])

  if (!visible || !announcement) return null

  return (
    <div className={`announcement-overlay ${isClosing ? 'closing' : ''}`} onClick={handleClose}>
      <div className={`announcement-modal ${isClosing ? 'closing' : ''}`} onClick={e => e.stopPropagation()}>
        <button className="announcement-close-btn" onClick={handleClose} aria-label="关闭公告">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6L6 18M6 6l12 12" />
          </svg>
        </button>

        <div className="announcement-decoration">
          <div className="decoration-circle circle-1"></div>
          <div className="decoration-circle circle-2"></div>
          <div className="decoration-circle circle-3"></div>
        </div>

        <div className="announcement-content">
          <div className="announcement-icon-wrapper">
            <div className="announcement-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
            </div>
          </div>

          <h2 className="announcement-title">{announcement.title || '系统公告'}</h2>

          <div
            className="announcement-body"
            dangerouslySetInnerHTML={{ __html: formattedContent }}
          />

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
