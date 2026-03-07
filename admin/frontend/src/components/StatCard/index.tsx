import { FC, ReactNode } from 'react'
import { Card, Space, Typography } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import { motion } from 'framer-motion'
import styled from '@emotion/styled'
import { designTokens } from '@/theme'

const { Text } = Typography

interface StatCardProps {
  title: string
  value: string | number
  icon: ReactNode
  trend?: 'up' | 'down'
  trendValue?: number
  gradient?: string[]
  suffix?: string
  loading?: boolean
}

const StyledCard = styled(Card)`
  position: relative;
  overflow: hidden;
  height: 100%;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 140px;
    height: 140px;
    background: radial-gradient(circle at top right, var(--glow-color, rgba(14, 165, 233, 0.08)), transparent 70%);
    pointer-events: none;
  }
`

const IconWrapper = styled.div<{ gradient: string }>`
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: ${props => props.gradient};
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: white;
  box-shadow: ${designTokens.shadows.md};
`

const TrendBadge = styled.div<{ isUp: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  background: ${props => props.isUp ? designTokens.colors.successLight : designTokens.colors.errorLight};
  color: ${props => props.isUp ? designTokens.colors.success : designTokens.colors.error};
`

const StatCard: FC<StatCardProps> = ({
  title,
  value,
  icon,
  trend,
  trendValue,
  gradient = ['#0ea5e9', '#06b6d4'],
  suffix,
  loading = false,
}) => {
  const gradientStyle = `linear-gradient(135deg, ${gradient[0]}, ${gradient[1]})`
  
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.4, 0, 0.2, 1] }}
      style={{ height: '100%' }}
    >
      <StyledCard
        loading={loading}
        style={{ 
          '--glow-color': `${gradient[0]}15`,
        } as React.CSSProperties}
        styles={{ body: { padding: 24 } }}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <IconWrapper gradient={gradientStyle}>
            {icon}
          </IconWrapper>
          
          <div>
            <Text 
              style={{ 
                fontSize: 13, 
                color: 'rgba(250, 250, 250, 0.45)',
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                display: 'block',
                marginBottom: 8,
              }}
            >
              {title}
            </Text>
            
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
              <Text 
                style={{ 
                  fontSize: 32, 
                  fontWeight: 700,
                  color: '#fafafa',
                  lineHeight: 1.2,
                  letterSpacing: '-0.5px',
                }}
              >
                {value}
              </Text>
              {suffix && (
                <Text style={{ fontSize: 16, color: 'rgba(250, 250, 250, 0.65)', fontWeight: 500 }}>
                  {suffix}
                </Text>
              )}
            </div>
          </div>
          
          {trend && trendValue !== undefined && (
            <TrendBadge isUp={trend === 'up'}>
              {trend === 'up' ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              <span>{trendValue}%</span>
            </TrendBadge>
          )}
        </Space>
      </StyledCard>
    </motion.div>
  )
}

export default StatCard
