import React from 'react'
import { BookOutlined, FileTextOutlined, EditOutlined } from '@ant-design/icons'
import './EnhancedStatsCard.css'

interface StatData {
  label: string
  value: number | string
  icon: React.ReactNode
  color: 'blue' | 'purple' | 'orange'
  trend?: {
    value: number
    isPositive: boolean
  }
}

interface EnhancedStatsCardProps {
  totalNovels: number
  totalChapters: number
  totalWords: number
}

const EnhancedStatsCard: React.FC<EnhancedStatsCardProps> = ({
  totalNovels,
  totalChapters,
  totalWords
}) => {
  const stats: StatData[] = [
    {
      label: '作品数',
      value: totalNovels,
      icon: <BookOutlined />,
      color: 'blue',
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
              {stat.trend && (
                <span className={`stat-trend ${stat.trend.isPositive ? 'positive' : 'negative'}`}>
                  {stat.trend.isPositive ? '↑' : '↓'} {stat.trend.value}%
                </span>
              )}
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

