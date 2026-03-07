import { FC } from 'react'
import { Avatar, Space, Typography } from 'antd'
import styled from '@emotion/styled'
import { designTokens } from '@/theme'

const { Text } = Typography

interface UserAvatarProps {
  name: string
  subtitle?: string
  size?: 'small' | 'default' | 'large'
  showName?: boolean
  gradient?: string
}

const avatarSizes = {
  small: 28,
  default: 36,
  large: 44,
}

const fontSizes = {
  small: 11,
  default: 14,
  large: 16,
}

const StyledAvatar = styled(Avatar)<{ $gradient: string; $size: number }>`
  background: ${props => props.$gradient};
  font-weight: 600;
  font-size: ${props => props.$size * 0.4}px;
  flex-shrink: 0;
`

const NameWrapper = styled.div`
  display: flex;
  flex-direction: column;
  min-width: 0;
`

// 根据名字生成渐变色
const getGradientByName = (name: string): string => {
  const gradients = [
    designTokens.gradients.primary,
    designTokens.gradients.purple,
    designTokens.gradients.success,
    designTokens.gradients.warning,
    designTokens.gradients.blue,
  ]
  
  const index = name.charCodeAt(0) % gradients.length
  return gradients[index]
}

const UserAvatar: FC<UserAvatarProps> = ({
  name,
  subtitle,
  size = 'default',
  showName = true,
  gradient,
}) => {
  const avatarSize = avatarSizes[size]
  const fontSize = fontSizes[size]
  const avatarGradient = gradient || getGradientByName(name)
  const initial = name.charAt(0).toUpperCase()

  if (!showName) {
    return (
      <StyledAvatar 
        $gradient={avatarGradient} 
        $size={avatarSize}
        size={avatarSize}
      >
        {initial}
      </StyledAvatar>
    )
  }

  return (
    <Space size={10} align="center">
      <StyledAvatar 
        $gradient={avatarGradient} 
        $size={avatarSize}
        size={avatarSize}
      >
        {initial}
      </StyledAvatar>
      <NameWrapper>
        <Text 
          style={{ 
            fontWeight: 500, 
            color: '#fafafa',
            fontSize,
            lineHeight: 1.4,
          }}
          ellipsis
        >
          {name}
        </Text>
        {subtitle && (
          <Text 
            style={{ 
              fontSize: fontSize - 2, 
              color: 'rgba(250, 250, 250, 0.45)',
              lineHeight: 1.3,
            }}
            ellipsis
          >
            {subtitle}
          </Text>
        )}
      </NameWrapper>
    </Space>
  )
}

export default UserAvatar
