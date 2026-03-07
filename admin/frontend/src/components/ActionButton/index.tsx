import { FC } from 'react'
import { Button, Tooltip, Popconfirm } from 'antd'
import type { ButtonProps } from 'antd'
import styled from '@emotion/styled'

interface ActionButtonProps extends Omit<ButtonProps, 'type' | 'variant'> {
  tooltip?: string
  variant?: 'primary' | 'success' | 'warning' | 'danger' | 'default' | 'ghost'
  confirmTitle?: string
  confirmDescription?: string
  onConfirm?: () => void
}

const variantStyles = {
  primary: {
    color: '#0ea5e9',
    hoverBg: 'rgba(14, 165, 233, 0.1)',
  },
  success: {
    color: '#22c55e',
    hoverBg: 'rgba(34, 197, 94, 0.1)',
  },
  warning: {
    color: '#f97316',
    hoverBg: 'rgba(249, 115, 22, 0.1)',
  },
  danger: {
    color: '#ef4444',
    hoverBg: 'rgba(239, 68, 68, 0.1)',
  },
  default: {
    color: 'rgba(250, 250, 250, 0.65)',
    hoverBg: 'rgba(255, 255, 255, 0.06)',
  },
  ghost: {
    color: 'rgba(250, 250, 250, 0.45)',
    hoverBg: 'rgba(255, 255, 255, 0.04)',
  },
}

const StyledButton = styled(Button)<{ $variant: keyof typeof variantStyles }>`
  color: ${props => variantStyles[props.$variant].color} !important;
  padding: 4px 8px;
  height: auto;
  
  &:hover {
    background: ${props => variantStyles[props.$variant].hoverBg} !important;
    color: ${props => variantStyles[props.$variant].color} !important;
  }
  
  &:active {
    background: ${props => variantStyles[props.$variant].hoverBg} !important;
  }
`

const ActionButton: FC<ActionButtonProps> = ({
  tooltip,
  variant = 'default',
  confirmTitle,
  confirmDescription,
  onConfirm,
  children,
  onClick,
  ...props
}) => {
  const button = (
    <StyledButton
      type="text"
      $variant={variant}
      onClick={confirmTitle ? undefined : onClick}
      {...props}
    >
      {children}
    </StyledButton>
  )

  const wrappedButton = tooltip ? (
    <Tooltip title={tooltip} placement="top">
      {button}
    </Tooltip>
  ) : button

  if (confirmTitle) {
    return (
      <Popconfirm
        title={confirmTitle}
        description={confirmDescription}
        onConfirm={onConfirm || (onClick as () => void)}
        okText="确定"
        cancelText="取消"
        okButtonProps={{ danger: variant === 'danger' }}
      >
        {wrappedButton}
      </Popconfirm>
    )
  }

  return wrappedButton
}

export default ActionButton
