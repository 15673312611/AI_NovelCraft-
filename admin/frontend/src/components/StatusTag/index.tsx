import { FC } from 'react'
import { Tag } from 'antd'
import { 
  CheckCircleOutlined, 
  ClockCircleOutlined, 
  SyncOutlined, 
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons'
import styled from '@emotion/styled'

type StatusType = 
  | 'success' | 'processing' | 'pending' | 'error' | 'warning' | 'default'
  | 'COMPLETED' | 'RUNNING' | 'PENDING' | 'FAILED' | 'CANCELLED'
  | 'ACTIVE' | 'INACTIVE' | 'DISABLED'
  | 'ONGOING' | 'DRAFT'
  | 'ADMIN' | 'USER'

interface StatusTagProps {
  status: StatusType | string
  text?: string
  showIcon?: boolean
  size?: 'small' | 'default'
}

const statusConfig: Record<string, { 
  color: string
  bgColor: string
  text: string
  icon: React.ReactNode
}> = {
  // 通用状态
  success: { 
    color: '#22c55e', 
    bgColor: 'rgba(34, 197, 94, 0.15)', 
    text: '成功',
    icon: <CheckCircleOutlined />,
  },
  processing: { 
    color: '#0ea5e9', 
    bgColor: 'rgba(14, 165, 233, 0.15)', 
    text: '处理中',
    icon: <SyncOutlined spin />,
  },
  pending: { 
    color: '#f97316', 
    bgColor: 'rgba(249, 115, 22, 0.15)', 
    text: '等待中',
    icon: <ClockCircleOutlined />,
  },
  error: { 
    color: '#ef4444', 
    bgColor: 'rgba(239, 68, 68, 0.15)', 
    text: '失败',
    icon: <CloseCircleOutlined />,
  },
  warning: { 
    color: '#f97316', 
    bgColor: 'rgba(249, 115, 22, 0.15)', 
    text: '警告',
    icon: <ExclamationCircleOutlined />,
  },
  default: { 
    color: 'rgba(250, 250, 250, 0.65)', 
    bgColor: 'rgba(255, 255, 255, 0.06)', 
    text: '默认',
    icon: <MinusCircleOutlined />,
  },
  
  // 任务状态
  COMPLETED: { 
    color: '#22c55e', 
    bgColor: 'rgba(34, 197, 94, 0.15)', 
    text: '已完成',
    icon: <CheckCircleOutlined />,
  },
  RUNNING: { 
    color: '#0ea5e9', 
    bgColor: 'rgba(14, 165, 233, 0.15)', 
    text: '运行中',
    icon: <SyncOutlined spin />,
  },
  PENDING: { 
    color: '#f97316', 
    bgColor: 'rgba(249, 115, 22, 0.15)', 
    text: '等待中',
    icon: <ClockCircleOutlined />,
  },
  FAILED: { 
    color: '#ef4444', 
    bgColor: 'rgba(239, 68, 68, 0.15)', 
    text: '失败',
    icon: <CloseCircleOutlined />,
  },
  CANCELLED: { 
    color: 'rgba(250, 250, 250, 0.45)', 
    bgColor: 'rgba(255, 255, 255, 0.06)', 
    text: '已取消',
    icon: <MinusCircleOutlined />,
  },
  
  // 用户状态
  ACTIVE: { 
    color: '#22c55e', 
    bgColor: 'rgba(34, 197, 94, 0.15)', 
    text: '启用',
    icon: <CheckCircleOutlined />,
  },
  INACTIVE: { 
    color: 'rgba(250, 250, 250, 0.45)', 
    bgColor: 'rgba(255, 255, 255, 0.06)', 
    text: '禁用',
    icon: <MinusCircleOutlined />,
  },
  DISABLED: { 
    color: 'rgba(250, 250, 250, 0.45)', 
    bgColor: 'rgba(255, 255, 255, 0.06)', 
    text: '禁用',
    icon: <MinusCircleOutlined />,
  },
  
  // 小说状态
  ONGOING: { 
    color: '#0ea5e9', 
    bgColor: 'rgba(14, 165, 233, 0.15)', 
    text: '连载中',
    icon: <SyncOutlined />,
  },
  DRAFT: { 
    color: 'rgba(250, 250, 250, 0.45)', 
    bgColor: 'rgba(255, 255, 255, 0.06)', 
    text: '草稿',
    icon: <MinusCircleOutlined />,
  },
  
  // 角色
  ADMIN: { 
    color: '#ef4444', 
    bgColor: 'rgba(239, 68, 68, 0.15)', 
    text: '管理员',
    icon: null,
  },
  USER: { 
    color: '#0ea5e9', 
    bgColor: 'rgba(14, 165, 233, 0.15)', 
    text: '用户',
    icon: null,
  },
}

const StyledTag = styled(Tag)<{ $bgColor: string; $color: string; $size: string }>`
  border: none;
  background: ${props => props.$bgColor};
  color: ${props => props.$color};
  font-weight: 500;
  font-size: ${props => props.$size === 'small' ? '11px' : '12px'};
  padding: ${props => props.$size === 'small' ? '2px 6px' : '4px 10px'};
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  
  .anticon {
    font-size: ${props => props.$size === 'small' ? '10px' : '12px'};
  }
`

const StatusTag: FC<StatusTagProps> = ({ 
  status, 
  text, 
  showIcon = true,
  size = 'default',
}) => {
  const config = statusConfig[status] || statusConfig.default
  const displayText = text || config.text
  
  return (
    <StyledTag 
      $bgColor={config.bgColor} 
      $color={config.color}
      $size={size}
    >
      {showIcon && config.icon}
      {displayText}
    </StyledTag>
  )
}

export default StatusTag
