import { Tag } from 'antd'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'

interface StatusBadgeProps {
  status: 'success' | 'processing' | 'error' | 'warning' | 'default' | 'pending'
  text?: string
}

const StatusBadge = ({ status, text }: StatusBadgeProps) => {
  const statusConfig = {
    success: {
      color: 'success',
      icon: <CheckCircleOutlined />,
      text: text || '成功',
    },
    processing: {
      color: 'processing',
      icon: <SyncOutlined spin />,
      text: text || '处理中',
    },
    error: {
      color: 'error',
      icon: <CloseCircleOutlined />,
      text: text || '失败',
    },
    warning: {
      color: 'warning',
      icon: <ExclamationCircleOutlined />,
      text: text || '警告',
    },
    pending: {
      color: 'default',
      icon: <ClockCircleOutlined />,
      text: text || '等待中',
    },
    default: {
      color: 'default',
      icon: null,
      text: text || '默认',
    },
  }

  const config = statusConfig[status] || statusConfig.default

  return (
    <Tag color={config.color} icon={config.icon}>
      {config.text}
    </Tag>
  )
}

export default StatusBadge
