import React from 'react'
import { BookOutlined, FileTextOutlined, EditOutlined } from '@ant-design/icons'
import './EnhancedStatsCard.css'

interface StatData {
  label: string
  value: number | string
  icon: React.ReactNode
  color: 'blue' | 'purple' | 'orange'
  suffix?: string
}

interface EnhancedStatsCardProps {
  totalNovels: number
  totalChapters: number
  totalWords: number
}

// 格式化数字显示
const formatNumber = (num: number): string => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toLocaleString()
}

const EnhancedStatsCard: React.FC<EnhancedStatsCardProps> = ({
  totalNovels,
  totalChapters,
  totalWords
}) => {
  const stats: StatData[] = [
    {
      label: '作品总数',
      value: totalNovels,
      icon: <BookOutlined />,
      color: 'blue',
      suffix: '部'
    },
    {
      label: '章节总数',
      value: totalChapters,
      icon: <FileTextOutlined />,
      color: 'purple',
      suffix: '章'
    },
    {
      label: '累计字数',
      value: formatNumber(totalWords),
      icon: <EditOutlined />,
      color: 'orange',
      suffix: '字'
    },
  ]

  return (
    <div className="enhanced-stats-grid">
      {stats.map((stat, index) => (
        <div key={index} className={`enhanced-stat-card stat-${stat.color}`}>
          {/* 背景装饰 */}
          <div className="stat-bg-decoration">
            <div className="decoration-dot dot-1"></div>
            <div className="decoration-dot dot-2"></div>
            <div className="decoration-dot dot-3"></div>
          </div>

          {/* 图标 */}
          <div className="stat-icon-wrapper">
            <div className={`stat-icon icon-${stat.color}`}>
              {stat.icon}
            </div>
          </div>

          {/* 内容 */}
          <div className="stat-content-wrapper">
            <div className="stat-value-wrapper">
              <span className="stat-value">{stat.value}</span>
              {stat.suffix && <span className="stat-suffix">{stat.suffix}</span>}
            </div>
            <div className="stat-label">{stat.label}</div>
          </div>

          {/* 悬停效果光泽 */}
          <div className="stat-shine"></div>
        </div>
      ))}
    </div>
  )
}

export default EnhancedStatsCard

