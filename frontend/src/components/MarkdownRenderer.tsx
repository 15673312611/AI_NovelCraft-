import React from 'react'
import './MarkdownRenderer.css'

interface MarkdownRendererProps {
  content: string
  compact?: boolean // 是否使用紧凑模式
}

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, compact = false }) => {
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
      dangerouslySetInnerHTML={{ __html: renderMarkdown(content) }}
    />
  )
}

export default MarkdownRenderer

