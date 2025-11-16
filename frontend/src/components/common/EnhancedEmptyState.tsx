import React from 'react'
import { Button } from 'antd'
import { PlusOutlined, BookOutlined } from '@ant-design/icons'
import './EnhancedEmptyState.css'

interface EnhancedEmptyStateProps {
  onCreateNovel: () => void
}

const EnhancedEmptyState: React.FC<EnhancedEmptyStateProps> = ({ onCreateNovel }) => {
  return (
    <div className="enhanced-empty-state">
      {/* 装饰性背景 */}
      <div className="empty-bg-decoration">
        <div className="decoration-circle circle-1"></div>
        <div className="decoration-circle circle-2"></div>
        <div className="decoration-circle circle-3"></div>
      </div>

      {/* 主要内容 */}
      <div className="empty-content">
        {/* 插图/图标 */}
        <div className="empty-illustration">
          <div className="book-icon-wrapper">
            <BookOutlined className="book-icon" />
            <div className="icon-glow"></div>
          </div>
        </div>

        {/* 文案 */}
        <h2 className="empty-title">开启你的创作之旅</h2>
        <p className="empty-description">
          还没有任何作品？不要担心，每个伟大的故事都始于一个想法。<br />
          让我们一起创造属于你的精彩故事世界！
        </p>

        {/* 主要操作 */}
        <Button
          type="primary"
          size="large"
          icon={<PlusOutlined />}
          onClick={onCreateNovel}
          className="empty-primary-action"
        >
          创建我的第一部小说
        </Button>
      </div>
    </div>
  )
}

export default EnhancedEmptyState

