import React, { useMemo } from 'react'
import DOMPurify from 'dompurify'
import './MarkdownRenderer.css'

interface MarkdownRendererProps {
  content: string
  compact?: boolean
}

const escapeHtml = (text: string) => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const renderMarkdown = (text: string) => {
  const safeText = escapeHtml(text)

  return safeText
    .replace(/```([^`]+)```/g, '<pre class="md-code-block">$1</pre>')
    .replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')
    .replace(/^# (.+)$/gm, '<div class="md-h1">$1</div>')
    .replace(/^## (.+)$/gm, '<div class="md-h2">$1</div>')
    .replace(/^### (.+)$/gm, '<div class="md-h3">$1</div>')
    .replace(/^#### (.+)$/gm, '<div class="md-h4">$1</div>')
    .replace(/^> (.+)$/gm, '<div class="md-blockquote">$1</div>')
    .replace(/^- (.+)$/gm, '<div class="md-list-item"><span class="md-bullet">&bull;</span>$1</div>')
    .replace(/^\* (.+)$/gm, '<div class="md-list-item"><span class="md-bullet">&bull;</span>$1</div>')
    .replace(/^\d+\. (.+)$/gm, (match, p1) => {
      const numMatch = match.match(/^(\d+)\./)
      const num = numMatch ? numMatch[1] : '1'
      return `<div class="md-list-item"><span class="md-number">${num}.</span>${p1}</div>`
    })
    .replace(/\*\*(.+?)\*\*/g, '<strong class="md-bold">$1</strong>')
    .replace(/\*(.+?)\*/g, '<em class="md-italic">$1</em>')
    .replace(/^---$/gm, '<hr class="md-separator" />')
    .replace(/\n{3,}/g, '<div class="md-paragraph-break"></div>')
    .replace(/\n{2}/g, '<div class="md-paragraph-break"></div>')
    .replace(/\n/g, '<br/>')
}

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, compact = false }) => {
  const sanitizedContent = useMemo(() => {
    return DOMPurify.sanitize(renderMarkdown(content), {
      ALLOWED_TAGS: ['div', 'pre', 'code', 'strong', 'em', 'span', 'hr', 'br'],
      ALLOWED_ATTR: ['class']
    })
  }, [content])

  return (
    <div
      className={`markdown-renderer ${compact ? 'compact' : ''}`}
      dangerouslySetInnerHTML={{ __html: sanitizedContent }}
    />
  )
}

export default MarkdownRenderer
