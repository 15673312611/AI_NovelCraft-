import React, { useState, useEffect } from 'react'
import {
  Layout,
  Typography,
  Row,
  Col,
  Card,
  Statistic,
  Progress,
  Space,
  Button,
  Tabs,
  message
} from 'antd'
import {
  RobotOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined
} from '@ant-design/icons'
import AITaskList from '@/components/ai/AITaskList'
import AITaskCreator from '@/components/ai/AITaskCreator'
import { AITask, AITaskStatistics, aiTaskService } from '@/services/aiTaskService'
import './AIControlPanelPage.css'

const { Header, Content } = Layout
const { Title, Text } = Typography


const AIControlPanelPage: React.FC = () => {
  const [statistics, setStatistics] = useState<AITaskStatistics | null>(null)
  const [creatorVisible, setCreatorVisible] = useState(false)
  const [activeTab, setActiveTab] = useState('all')
  const [refreshKey, setRefreshKey] = useState(0)

  // 加载统计数据
  const loadStatistics = async () => {
    try {
      const stats = await aiTaskService.getAITaskStatistics()
      setStatistics(stats)
    } catch (error) {
      message.error('加载统计数据失败')
    }
  }

  // 处理任务创建成功
  const handleTaskCreated = (task: AITask) => {
    setCreatorVisible(false)
    setRefreshKey(prev => prev + 1)
    loadStatistics()
    message.success(`AI任务"${task.title}"创建成功`)
  }

  // 处理任务变化
  const handleTasksChange = () => {
    loadStatistics()
  }

  // 打开创建任务模态框
  const handleCreateTask = () => {
    setCreatorVisible(true)
  }

  useEffect(() => {
    loadStatistics()
  }, [])

  return (
    <Layout className="ai-control-panel-layout">
      <Header className="panel-header">
        <div className="header-content">
          <Space>
            <RobotOutlined className="header-icon" />
            <Title level={3} className="header-title">
              AI控制面板
            </Title>
          </Space>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={handleCreateTask}
            size="large"
          >
            创建AI任务
          </Button>
        </div>
      </Header>

      <Content className="panel-content">
        {/* 统计卡片 */}
        <Row gutter={[16, 16]} className="statistics-row">
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card">
              <Statistic
                title="总任务数"
                value={statistics?.totalTasks || 0}
                prefix={<RobotOutlined />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card">
              <Statistic
                title="已完成"
                value={statistics?.completedTasks || 0}
                prefix={<CheckCircleOutlined />}
                valueStyle={{ color: '#52c41a' }}
                suffix={
                  <Text type="secondary">
                    ({statistics ? Math.round((statistics.completedTasks / statistics.totalTasks) * 100) : 0}%)
                  </Text>
                }
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card">
              <Statistic
                title="执行中"
                value={statistics?.runningTasks || 0}
                prefix={<ClockCircleOutlined />}
                valueStyle={{ color: '#faad14' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card className="stat-card">
              <Statistic
                title="总成本"
                value={statistics?.totalCost || 0}
                prefix={<DollarOutlined />}
                precision={4}
                valueStyle={{ color: '#f5222d' }}
              />
            </Card>
          </Col>
        </Row>

        {/* 进度和成功率 */}
        <Row gutter={[16, 16]} className="progress-row">
          <Col xs={24} lg={12}>
            <Card title="任务成功率" className="progress-card">
              <div className="progress-content">
                <Progress
                  type="circle"
                  percent={statistics?.successRate || 0}
                  format={(percent) => `${percent}%`}
                  strokeColor={{
                    '0%': '#108ee9',
                    '100%': '#87d068',
                  }}
                  size={120}
                />
                <div className="progress-info">
                  <Text>成功: {statistics?.completedTasks || 0}</Text>
                  <Text>失败: {statistics?.failedTasks || 0}</Text>
                  <Text>平均执行时间: {statistics?.averageExecutionTime || 0}s</Text>
                </div>
              </div>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="成本统计" className="progress-card">
              <div className="cost-stats">
                <div className="cost-item">
                  <Text>总成本</Text>
                  <Text strong className="cost-value">
                    ${statistics?.totalCost?.toFixed(4) || '0.0000'}
                  </Text>
                </div>
                <div className="cost-item">
                  <Text>平均成本</Text>
                  <Text strong className="cost-value">
                    ${statistics?.averageCost?.toFixed(4) || '0.0000'}
                  </Text>
                </div>
                <div className="cost-item">
                  <Text>执行中任务</Text>
                  <Text strong className="cost-value">
                    {statistics?.runningTasks || 0}
                  </Text>
                </div>
                <div className="cost-item">
                  <Text>等待中任务</Text>
                  <Text strong className="cost-value">
                    {statistics?.pendingTasks || 0}
                  </Text>
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        {/* 任务列表 */}
        <Card className="task-list-card">
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            className="task-tabs"
            items={[
              {
                key: 'all',
                label: '全部任务',
                children: (
                  <AITaskList
                    key={`all-${refreshKey}`}
                    onTasksChange={handleTasksChange}
                    onCreateTask={handleCreateTask}
                  />
                )
              },
              {
                key: 'running',
                label: '执行中',
                children: (
                  <AITaskList
                    key={`running-${refreshKey}`}
                    onTasksChange={handleTasksChange}
                    onCreateTask={handleCreateTask}
                  />
                )
              },
              {
                key: 'completed',
                label: '已完成',
                children: (
                  <AITaskList
                    key={`completed-${refreshKey}`}
                    onTasksChange={handleTasksChange}
                    onCreateTask={handleCreateTask}
                  />
                )
              },
              {
                key: 'failed',
                label: '失败',
                children: (
                  <AITaskList
                    key={`failed-${refreshKey}`}
                    onTasksChange={handleTasksChange}
                    onCreateTask={handleCreateTask}
                  />
                )
              },
              {
                key: 'pending',
                label: '等待中',
                children: (
                  <AITaskList
                    key={`pending-${refreshKey}`}
                    onTasksChange={handleTasksChange}
                    onCreateTask={handleCreateTask}
                  />
                )
              }
            ]}
          />
        </Card>
      </Content>

      {/* AI任务创建模态框：仅在可见时挂载，避免 useForm 未连接警告 */}
      {creatorVisible && (
        <AITaskCreator
          visible={true}
          onCancel={() => setCreatorVisible(false)}
          onSuccess={handleTaskCreated}
        />
      )}
    </Layout>
  )
}

export default AIControlPanelPage 