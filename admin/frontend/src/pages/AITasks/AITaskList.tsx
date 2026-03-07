import { useEffect, useState } from 'react'
import { Table, Button, Space, Tag, Progress, Modal, message, Card, Row, Col, Statistic } from 'antd'
import { ReloadOutlined, EyeOutlined, CheckCircleOutlined, SyncOutlined, CloseCircleOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { adminAITaskService } from '@/services/adminAITaskService'

interface AITask {
  id: number
  name: string
  type: string
  status: string
  progress: number
  cost: number
  createdAt: string
  completedAt?: string
}

const AITaskList = () => {
  const [tasks, setTasks] = useState<AITask[]>([])
  const [loading, setLoading] = useState(false)
  const [, setTotal] = useState(0)

  useEffect(() => {
    loadTasks()
  }, [])

  const loadTasks = async () => {
    setLoading(true)
    try {
      const response = await adminAITaskService.getAITasks({})
      console.log('AI Tasks Response:', response)
      
      let taskData: AITask[] = []
      if (Array.isArray(response)) {
        taskData = response
      } else if (response && Array.isArray((response as any).records)) {
        taskData = (response as any).records
        setTotal((response as any).total || 0)
      } else if (response && Array.isArray(response.data)) {
        taskData = response.data
      }
      
      setTasks(taskData)
    } catch (error) {
      console.error('加载AI任务列表失败:', error)
      message.error('加载AI任务列表失败')
      setTasks([])
    } finally {
      setLoading(false)
    }
  }

  const handleRetry = (taskId: number) => {
    Modal.confirm({
      title: '确认重试',
      content: '确定要重试该任务吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await adminAITaskService.retryTask(taskId)
          message.success('任务已重新提交')
          loadTasks()
        } catch (error) {
          message.error('重试失败')
        }
      },
    })
  }

  const getStatusConfig = (status: string) => {
    const configs: Record<string, { color: string; text: string; icon: any }> = {
      PENDING: { color: 'default', text: '等待中', icon: <SyncOutlined spin /> },
      RUNNING: { color: 'processing', text: '运行中', icon: <SyncOutlined spin /> },
      COMPLETED: { color: 'success', text: '已完成', icon: <CheckCircleOutlined /> },
      FAILED: { color: 'error', text: '失败', icon: <CloseCircleOutlined /> },
    }
    return configs[status] || configs.PENDING
  }

  const columns: ColumnsType<AITask> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { 
      title: '任务名称', 
      dataIndex: 'name', 
      key: 'name',
      render: (text: string) => (
        <span style={{ fontWeight: 500 }}>{text}</span>
      ),
    },
    { 
      title: '类型', 
      dataIndex: 'type', 
      key: 'type',
      render: (type: string) => (
        <Tag color="purple">{type}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const { color, text, icon } = getStatusConfig(status)
        return (
          <Tag color={color} icon={icon}>
            {text}
          </Tag>
        )
      },
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      render: (progress: number, record) => (
        <Progress 
          percent={progress} 
          size="small"
          strokeColor={{
            '0%': '#667eea',
            '100%': '#764ba2',
          }}
          status={record.status === 'FAILED' ? 'exception' : undefined}
        />
      ),
    },
    {
      title: '成本',
      dataIndex: 'cost',
      key: 'cost',
      render: (cost: number | null) => (
        <span style={{ color: '#f5576c', fontWeight: 500 }}>
          ¥{cost != null ? cost.toFixed(2) : '0.00'}
        </span>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space>
          <Button 
            type="link" 
            icon={<EyeOutlined />}
            style={{ color: '#667eea' }}
          >
            详情
          </Button>
          {record.status === 'FAILED' && (
            <Button
              type="link"
              icon={<ReloadOutlined />}
              onClick={() => handleRetry(record.id)}
              style={{ color: '#f5576c' }}
            >
              重试
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const stats = {
    total: tasks.length,
    running: tasks.filter(t => t.status === 'RUNNING').length,
    completed: tasks.filter(t => t.status === 'COMPLETED').length,
    failed: tasks.filter(t => t.status === 'FAILED').length,
  }

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card 
            variant="borderless"
            style={{
              background: 'linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%)',
              borderLeft: '4px solid #667eea',
            }}
          >
            <Statistic
              title="总任务数"
              value={stats.total}
              valueStyle={{ color: '#667eea', fontWeight: 600 }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card 
            variant="borderless"
            style={{
              background: 'linear-gradient(135deg, rgba(79, 172, 254, 0.1) 0%, rgba(0, 242, 254, 0.1) 100%)',
              borderLeft: '4px solid #4facfe',
            }}
          >
            <Statistic
              title="运行中"
              value={stats.running}
              valueStyle={{ color: '#4facfe', fontWeight: 600 }}
              prefix={<SyncOutlined spin />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card 
            variant="borderless"
            style={{
              background: 'linear-gradient(135deg, rgba(67, 233, 123, 0.1) 0%, rgba(56, 249, 215, 0.1) 100%)',
              borderLeft: '4px solid #43e97b',
            }}
          >
            <Statistic
              title="已完成"
              value={stats.completed}
              valueStyle={{ color: '#43e97b', fontWeight: 600 }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card 
            variant="borderless"
            style={{
              background: 'linear-gradient(135deg, rgba(245, 87, 108, 0.1) 0%, rgba(255, 77, 79, 0.1) 100%)',
              borderLeft: '4px solid #f5576c',
            }}
          >
            <Statistic
              title="失败"
              value={stats.failed}
              valueStyle={{ color: '#f5576c', fontWeight: 600 }}
              prefix={<CloseCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Table
        columns={columns}
        dataSource={tasks}
        loading={loading}
        rowKey="id"
        pagination={{ 
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 个任务`,
        }}
        style={{
          background: 'white',
          borderRadius: '12px',
          overflow: 'hidden',
        }}
      />
    </div>
  )
}

export default AITaskList
