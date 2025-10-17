import React, { useState, useEffect } from 'react'
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Tooltip,
  Popconfirm,
  message,
  Badge,
  Typography,
  Progress,
  Modal,
  Descriptions,
  Divider
} from 'antd'
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  RobotOutlined,
  ClockCircleOutlined,
  DollarOutlined
} from '@ant-design/icons'
import { AITask, aiTaskService } from '@/services/aiTaskService'
import './AITaskList.css'

const { Text, Title } = Typography

interface AITaskListProps {
  novelId?: number
  onTasksChange?: () => void
  showCreateButton?: boolean
  onCreateTask?: () => void
}

const AITaskList: React.FC<AITaskListProps> = ({
  novelId,
  onTasksChange,
  showCreateButton = true,
  onCreateTask
}) => {
  const [tasks, setTasks] = useState<AITask[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedTask, setSelectedTask] = useState<AITask | null>(null)
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })

  // 加载任务列表
  const loadTasks = async (page: number = 1) => {
    try {
      setLoading(true)
      const response = await aiTaskService.getAITasks(
        page - 1,
        pagination.pageSize,
        undefined,
        undefined,
        novelId
      )
      setTasks(response.content || [])
      setPagination(prev => ({
        ...prev,
        current: page,
        total: response.totalElements || 0
      }))
    } catch (error) {
      console.error('加载AI任务失败:', error)
      message.error('加载AI任务失败')
      // 确保在错误情况下tasks仍然是数组
      setTasks([])
    } finally {
      setLoading(false)
    }
  }

  // 取消任务
  const handleCancelTask = async (taskId: number) => {
    try {
      await aiTaskService.cancelAITask(taskId)
      message.success('任务已取消')
      loadTasks(pagination.current)
      onTasksChange?.()
    } catch (error) {
      message.error('取消任务失败')
    }
  }

  // 重试任务
  const handleRetryTask = async (taskId: number) => {
    try {
      await aiTaskService.retryAITask(taskId)
      message.success('任务已重新开始')
      loadTasks(pagination.current)
      onTasksChange?.()
    } catch (error) {
      message.error('重试任务失败')
    }
  }

  // 删除任务
  const handleDeleteTask = async (taskId: number) => {
    try {
      await aiTaskService.deleteAITask(taskId)
      message.success('任务已删除')
      loadTasks(pagination.current)
      onTasksChange?.()
    } catch (error) {
      message.error('删除任务失败')
    }
  }

  // 查看任务详情
  const handleViewDetail = (task: AITask) => {
    setSelectedTask(task)
    setDetailModalVisible(true)
  }

  // 获取状态标签
  const getStatusTag = (status: string) => {
    const statusConfig = {
      pending: { color: 'default', text: '等待中' },
      running: { color: 'processing', text: '执行中' },
      completed: { color: 'success', text: '已完成' },
      failed: { color: 'error', text: '失败' },
      cancelled: { color: 'warning', text: '已取消' }
    }
    
    const config = statusConfig[status as keyof typeof statusConfig] || { color: 'default', text: status }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  // 获取任务类型标签
  const getTypeTag = (type: string) => {
    const typeConfig = {
      plot_generation: { color: 'blue', text: '剧情生成' },
      character_development: { color: 'green', text: '角色发展' },
      dialogue_generation: { color: 'purple', text: '对话生成' },
      scene_description: { color: 'orange', text: '场景描述' },
      story_outline: { color: 'cyan', text: '故事大纲' },
      writing_assistance: { color: 'magenta', text: '写作辅助' }
    }
    
    const config = typeConfig[type as keyof typeof typeConfig] || { color: 'default', text: type }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  // 表格列定义
  const columns = [
    {
      title: '任务',
      key: 'task',
      render: (record: AITask) => (
        <div className="task-info">
          <div className="task-title">
            <Text strong>{record.title}</Text>
            {getTypeTag(record.type)}
          </div>
          <div className="task-description">
            <Text type="secondary">{record.description}</Text>
          </div>
          <div className="task-meta">
            <Text type="secondary" className="task-novel">
              小说: {record.novelTitle}
            </Text>
            <Text type="secondary" className="task-user">
              创建者: {record.createdByUsername}
            </Text>
          </div>
        </div>
      )
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (record: AITask) => (
        <div className="task-status">
          {getStatusTag(record.status)}
          {record.status === 'running' && (
            <Progress
              percent={100}
              size="small"
              status="active"
              showInfo={false}
              className="task-progress"
            />
          )}
        </div>
      )
    },
    {
      title: '模型',
      key: 'model',
      width: 100,
      render: (record: AITask) => (
        <Text code>{record.model}</Text>
      )
    },
    {
      title: '成本',
      key: 'cost',
      width: 100,
      render: (record: AITask) => (
        <div className="task-cost">
          <DollarOutlined />
          <Text>{record.cost.toFixed(4)}</Text>
          <Text type="secondary" className="task-tokens">
            ({record.tokenCount} tokens)
          </Text>
        </div>
      )
    },
    {
      title: '创建时间',
      key: 'createdAt',
      width: 150,
      render: (record: AITask) => (
        <div className="task-time">
          <ClockCircleOutlined />
          <Text>{new Date(record.createdAt).toLocaleString()}</Text>
        </div>
      )
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (record: AITask) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button
              type="text"
              icon={<EyeOutlined />}
              onClick={() => handleViewDetail(record)}
              size="small"
            />
          </Tooltip>
          
          {record.status === 'running' && (
            <Tooltip title="取消任务">
              <Popconfirm
                title="确定要取消这个任务吗？"
                onConfirm={() => handleCancelTask(record.id)}
                okText="确定"
                cancelText="取消"
              >
                <Button
                  type="text"
                  icon={<PauseCircleOutlined />}
                  size="small"
                  danger
                />
              </Popconfirm>
            </Tooltip>
          )}
          
          {(record.status === 'failed' || record.status === 'cancelled') && (
            <Tooltip title="重试任务">
              <Button
                type="text"
                icon={<ReloadOutlined />}
                onClick={() => handleRetryTask(record.id)}
                size="small"
              />
            </Tooltip>
          )}
          
          <Tooltip title="删除任务">
            <Popconfirm
              title="确定要删除这个任务吗？"
              onConfirm={() => handleDeleteTask(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="text"
                icon={<DeleteOutlined />}
                size="small"
                danger
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      )
    }
  ]

  useEffect(() => {
    loadTasks()
  }, [novelId])

  return (
    <div className="ai-task-list">
      <Card
        title={
          <Space>
            <RobotOutlined />
            <span>AI任务管理</span>
            <Badge count={tasks?.length || 0} showZero />
          </Space>
        }
        extra={
          showCreateButton && onCreateTask && (
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={onCreateTask}
            >
              创建AI任务
            </Button>
          )
        }
        className="task-list-card"
      >
        <Table
          columns={columns}
          dataSource={tasks || []}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            onChange: loadTasks,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) =>
              `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
          }}
          className="task-table"
        />
      </Card>

      {/* 任务详情模态框 */}
      <Modal
        title="AI任务详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={800}
        destroyOnHidden
      >
        {selectedTask && (
          <div className="task-detail">
            <Descriptions column={2} bordered>
              <Descriptions.Item label="任务标题" span={2}>
                {selectedTask.title}
              </Descriptions.Item>
              <Descriptions.Item label="任务类型">
                {getTypeTag(selectedTask.type)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {getStatusTag(selectedTask.status)}
              </Descriptions.Item>
              <Descriptions.Item label="AI模型">
                {selectedTask.model}
              </Descriptions.Item>
              <Descriptions.Item label="最大Token数">
                {selectedTask.maxTokens}
              </Descriptions.Item>
              <Descriptions.Item label="温度参数">
                {selectedTask.temperature}
              </Descriptions.Item>
              <Descriptions.Item label="重试次数">
                {selectedTask.retryCount}/{selectedTask.maxRetries}
              </Descriptions.Item>
              <Descriptions.Item label="成本">
                ${selectedTask.cost.toFixed(4)}
              </Descriptions.Item>
              <Descriptions.Item label="Token数量">
                {selectedTask.tokenCount}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {new Date(selectedTask.createdAt).toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {new Date(selectedTask.updatedAt).toLocaleString()}
              </Descriptions.Item>
              {selectedTask.startedAt && (
                <Descriptions.Item label="开始时间">
                  {new Date(selectedTask.startedAt).toLocaleString()}
                </Descriptions.Item>
              )}
              {selectedTask.completedAt && (
                <Descriptions.Item label="完成时间">
                  {new Date(selectedTask.completedAt).toLocaleString()}
                </Descriptions.Item>
              )}
              {selectedTask.errorMessage && (
                <Descriptions.Item label="错误信息" span={2}>
                  <Text type="danger">{selectedTask.errorMessage}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>

            <Divider />

            <div className="task-prompt">
              <Title level={5}>提示词</Title>
              <div className="prompt-content">
                <Text>{selectedTask.prompt}</Text>
              </div>
            </div>

            {selectedTask.result && (
              <>
                <Divider />
                <div className="task-result">
                  <Title level={5}>生成结果</Title>
                  <div className="result-content">
                    <Text>{selectedTask.result}</Text>
                  </div>
                </div>
              </>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}

export default AITaskList 