import { FC, ReactNode } from 'react'
import { Card, Space, Typography } from 'antd'
import styled from '@emotion/styled'
import { motion } from 'framer-motion'

const { Text } = Typography

interface InfoCardProps {
  title: string
  description?: string
  icon?: ReactNode
  iconGradient?: string
  extra?: ReactNode
  children: ReactNode
  loading?: boolean
  hoverable?: boolean
}

// Filter out transient props (starting with $) from being passed to DOM
const shouldForwardProp = (prop: string) => !prop.startsWith('$')

const StyledCard = styled(Card, { shouldForwardProp })<{ $hoverable?: boolean }>`
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  
  ${props => props.$hoverable && `
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
    }
  `}
`

const HeaderWrapper = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
`

const TitleSection = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`

const IconWrapper = styled('div', { shouldForwardProp })<{ $gradient?: string }>`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: ${props => props.$gradient};
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
  flex-shrink: 0;
`

const InfoCard: FC<InfoCardProps> = ({
  title,
  description,
  icon,
  iconGradient = 'linear-gradient(135deg, #0ea5e9, #06b6d4)',
  extra,
  children,
  loading = false,
  hoverable = false,
}) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      <StyledCard 
        loading={loading} 
        $hoverable={hoverable}
        styles={{ body: { padding: 24 } }}
      >
        <HeaderWrapper>
          <TitleSection>
            {icon && (
              <IconWrapper $gradient={iconGradient}>
                {icon}
              </IconWrapper>
            )}
            <div>
              <Text 
                style={{ 
                  fontSize: 16, 
                  fontWeight: 600, 
                  color: '#fafafa',
                  display: 'block',
                  lineHeight: 1.4,
                }}
              >
                {title}
              </Text>
              {description && (
                <Text 
                  style={{ 
                    fontSize: 13, 
                    color: 'rgba(250, 250, 250, 0.45)',
                    display: 'block',
                    marginTop: 2,
                  }}
                >
                  {description}
                </Text>
              )}
            </div>
          </TitleSection>
          
          {extra && (
            <Space size={8}>
              {extra}
            </Space>
          )}
        </HeaderWrapper>
        
        {children}
      </StyledCard>
    </motion.div>
  )
}

export default InfoCard
