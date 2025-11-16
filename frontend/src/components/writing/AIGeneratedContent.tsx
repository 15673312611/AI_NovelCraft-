import React, { useMemo } from 'react'
import { Button, Space, Typography, Tooltip } from 'antd'
import { CopyOutlined, SwapOutlined } from '@ant-design/icons'
import './AIGeneratedContent.css'

const { Text, Paragraph } = Typography

export interface AIGeneratedContentProps {
  content: string
  isStreaming?: boolean
  onCopy?: () => void
  onReplace?: () => void
}

const AIGeneratedContent: React.FC<AIGeneratedContentProps> = ({
  content,
  isStreaming = false,
  onCopy,
  onReplace,
}) => {
  const wordCount = useMemo(() => {
    if (!content) return 0
    return content.replace(/\s+/g, '').length
  }, [content])

  return (
    <div className="ai-generated-container">
      <div className="ai-generated-header">
        <Space size="small">
          <Text strong>生成内容</Text>
          <Text type="secondary">{wordCount} 字</Text>
          {isStreaming && <span className="ai-streaming-indicator">生成中...</span>}
        </Space>
      </div>

      <div className="ai-generated-body">
        <Paragraph className="ai-content-text">
          {content}
          {isStreaming && <span className="ai-cursor">▋</span>}
        </Paragraph>
      </div>

      <div className="ai-generated-footer">
        <Space>
          <Tooltip title="复制到剪贴板">
            <Button
              icon={<CopyOutlined />}
              onClick={onCopy}
              size="small"
            >
              复制
            </Button>
          </Tooltip>
          <Tooltip title="替换到正文">
            <Button
              type="primary"
              icon={<SwapOutlined />}
              onClick={onReplace}
              size="small"
            >
              替换正文
            </Button>
          </Tooltip>
        </Space>
      </div>
    </div>
  )
}

export default AIGeneratedContent

