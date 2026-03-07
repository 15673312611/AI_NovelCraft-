import { Card } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import { ReactNode } from 'react'

interface StatsCardProps {
  title: string
  value: string | number
  icon?: ReactNode
  trend?: 'up' | 'down'
  trendValue?: string | number
  gradient?: [string, string]
  suffix?: string
}

const StatsCard = ({ title, value, icon, trend, trendValue, gradient, suffix }: StatsCardProps) => {
  const defaultGradient: [string, string] = ['#0ea5e9', '#06b6d4']
  const colors = gradient || defaultGradient

  return (
    <Card
      style={{
        height: '100%',
        background: 'var(--bg-secondary)',
        border: '1px solid var(--border-primary)',
        borderRadius: '12px',
        position: 'relative',
        overflow: 'hidden',
        transition: 'all 0.2s',
      }}
      styles={{ body: { padding: '20px' } }}
      hoverable
    >
      {/* 背景渐变装饰 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          right: 0,
          width: '120px',
          height: '120px',
          background: `radial-gradient(circle at top right, ${colors[0]}15, transparent)`,
          pointerEvents: 'none',
        }}
      />

      <div style={{ position: 'relative', zIndex: 1 }}>
        {/* 图标 */}
        {icon && (
          <div
            style={{
              width: '44px',
              height: '44px',
              borderRadius: '10px',
              background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '16px',
              fontSize: '20px',
              color: 'white',
            }}
          >
            {icon}
          </div>
        )}

        {/* 标题 */}
        <div
          style={{
            fontSize: '12px',
            color: 'var(--text-tertiary)',
            marginBottom: '8px',
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '0.5px',
          }}
        >
          {title}
        </div>

        {/* 数值 */}
        <div
          style={{
            fontSize: '28px',
            fontWeight: 700,
            color: 'var(--text-primary)',
            marginBottom: '12px',
            letterSpacing: '-0.5px',
          }}
        >
          {value}
          {suffix && (
            <span style={{ fontSize: '16px', fontWeight: 600, marginLeft: '4px' }}>{suffix}</span>
          )}
        </div>

        {/* 趋势 */}
        {trend && trendValue && (
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '4px',
              padding: '4px 8px',
              borderRadius: '6px',
              fontSize: '12px',
              fontWeight: 600,
              background: trend === 'up' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)',
              color: trend === 'up' ? '#22c55e' : '#ef4444',
            }}
          >
            {trend === 'up' ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
            <span>{trendValue}%</span>
          </div>
        )}
      </div>
    </Card>
  )
}

export default StatsCard
