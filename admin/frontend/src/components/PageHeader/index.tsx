import { ReactNode } from 'react'
import { Space } from 'antd'

interface PageHeaderProps {
  title: string
  description?: string
  extra?: ReactNode
  icon?: ReactNode
}

const PageHeader = ({ title, description, extra, icon }: PageHeaderProps) => {
  return (
    <div
      style={{
        marginBottom: '24px',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: '16px',
      }}
    >
      <div style={{ flex: 1 }}>
        <Space size={12} align="center" style={{ marginBottom: description ? '8px' : 0 }}>
          {icon && (
            <div
              style={{
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                background: 'linear-gradient(135deg, #0ea5e9, #06b6d4)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '18px',
                color: 'white',
              }}
            >
              {icon}
            </div>
          )}
          <h1
            style={{
              fontSize: '24px',
              fontWeight: 700,
              color: 'var(--text-primary)',
              margin: 0,
              letterSpacing: '-0.5px',
            }}
          >
            {title}
          </h1>
        </Space>
        {description && (
          <p
            style={{
              fontSize: '14px',
              color: 'var(--text-tertiary)',
              margin: 0,
              marginLeft: icon ? '52px' : 0,
            }}
          >
            {description}
          </p>
        )}
      </div>
      {extra && <div style={{ flexShrink: 0 }}>{extra}</div>}
    </div>
  )
}

export default PageHeader
