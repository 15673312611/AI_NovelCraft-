import { ReactNode } from 'react'
import { Button } from 'antd'

interface EmptyStateProps {
  icon?: ReactNode
  title: string
  description?: string
  action?: {
    text: string
    onClick: () => void
    icon?: ReactNode
  }
}

const EmptyState = ({ icon, title, description, action }: EmptyStateProps) => {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '60px 20px',
        textAlign: 'center',
      }}
    >
      {icon && (
        <div
          style={{
            fontSize: '64px',
            marginBottom: '20px',
            opacity: 0.4,
          }}
        >
          {icon}
        </div>
      )}
      <h3
        style={{
          fontSize: '16px',
          fontWeight: 600,
          color: 'var(--text-primary)',
          margin: 0,
          marginBottom: '8px',
        }}
      >
        {title}
      </h3>
      {description && (
        <p
          style={{
            fontSize: '14px',
            color: 'var(--text-tertiary)',
            margin: 0,
            marginBottom: action ? '24px' : 0,
            maxWidth: '400px',
          }}
        >
          {description}
        </p>
      )}
      {action && (
        <Button type="primary" onClick={action.onClick} icon={action.icon}>
          {action.text}
        </Button>
      )}
    </div>
  )
}

export default EmptyState
