import { useEffect, useState } from 'react'
import { Row, Col, Card, Space, Progress, Typography } from 'antd'
import {
  UserOutlined,
  BookOutlined,
  RobotOutlined,
  DollarOutlined,
  LineChartOutlined,
  PieChartOutlined,
} from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import { motion } from 'framer-motion'
import styled from '@emotion/styled'
import { adminDashboardService } from '@/services/adminDashboardService'
import { StatCard, DataTable, StatusTag, PageContainer } from '@/components'

const { Text } = Typography

// Styled Components
const ChartCard = styled(Card)`
  height: 100%;
  
  .ant-card-head {
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  }
`

const LiveBadge = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(14, 165, 233, 0.1);
  border-radius: 20px;
  font-size: 12px;
  color: #0ea5e9;
  font-weight: 500;
`

const PulseDot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #0ea5e9;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  
  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
  }
`

const Dashboard = () => {
  const [stats, setStats] = useState({
    totalUsers: 0,
    totalNovels: 0,
    totalAITasks: 0,
    totalCost: 0,
  })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadDashboardData()
  }, [])

  const loadDashboardData = async () => {
    try {
      const data = await adminDashboardService.getStats()
      setStats(data.data || data)
    } catch (error) {
      console.error('加载数据失败', error)
    } finally {
      setLoading(false)
    }
  }

  // 用户增长趋势图表配置
  const userTrendOption = {
    backgroundColor: 'transparent',
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true,
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(23, 23, 23, 0.95)',
      borderColor: 'rgba(14, 165, 233, 0.3)',
      borderWidth: 1,
      textStyle: { color: '#fafafa', fontSize: 13 },
      padding: [12, 16],
      borderRadius: 10,
      axisPointer: {
        type: 'line',
        lineStyle: {
          color: 'rgba(14, 165, 233, 0.3)',
          type: 'solid',
          width: 2,
        },
      },
    },
    xAxis: {
      type: 'category',
      data: ['1月', '2月', '3月', '4月', '5月', '6月'],
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.06)' } },
      axisLabel: { color: 'rgba(250, 250, 250, 0.45)', fontSize: 12 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: 'rgba(250, 250, 250, 0.45)', fontSize: 12 },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.04)' } },
    },
    series: [{
      data: [120, 200, 150, 280, 350, 420],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { width: 3, color: '#0ea5e9' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(14, 165, 233, 0.25)' },
            { offset: 1, color: 'rgba(14, 165, 233, 0.02)' },
          ],
        },
      },
      itemStyle: {
        color: '#0ea5e9',
        borderWidth: 2,
        borderColor: '#171717',
      },
    }],
  }

  // AI任务分布图表配置
  const aiTaskOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(23, 23, 23, 0.95)',
      borderColor: 'rgba(14, 165, 233, 0.3)',
      borderWidth: 1,
      textStyle: { color: '#fafafa', fontSize: 13 },
      padding: [12, 16],
      borderRadius: 10,
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: { color: 'rgba(250, 250, 250, 0.65)', fontSize: 12 },
      itemGap: 16,
      itemWidth: 12,
      itemHeight: 12,
    },
    series: [{
      type: 'pie',
      radius: ['50%', '75%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 8,
        borderColor: '#171717',
        borderWidth: 3,
      },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold', color: '#fafafa' },
        itemStyle: { shadowBlur: 20, shadowColor: 'rgba(0, 0, 0, 0.5)' },
        scale: true,
        scaleSize: 6,
      },
      data: [
        { value: 1048, name: '章节生成', itemStyle: { color: '#0ea5e9' } },
        { value: 735, name: '大纲生成', itemStyle: { color: '#06b6d4' } },
        { value: 580, name: 'AI润色', itemStyle: { color: '#22c55e' } },
        { value: 484, name: 'AI消痕', itemStyle: { color: '#f97316' } },
        { value: 300, name: '其他', itemStyle: { color: '#a855f7' } },
      ],
    }],
  }

  // 最近任务数据
  const recentTasks = [
    { key: '1', taskName: '章节生成 - 第15章', user: '张三', status: 'COMPLETED', progress: 100, time: '2分钟前' },
    { key: '2', taskName: 'AI润色 - 全文优化', user: '李四', status: 'RUNNING', progress: 65, time: '5分钟前' },
    { key: '3', taskName: '大纲生成 - 玄幻小说', user: '王五', status: 'FAILED', progress: 0, time: '10分钟前' },
    { key: '4', taskName: 'AI消痕 - 章节检测', user: '赵六', status: 'PENDING', progress: 0, time: '15分钟前' },
  ]

  const taskColumns = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      key: 'taskName',
      render: (text: string) => (
        <Text style={{ fontWeight: 500, color: '#fafafa' }}>{text}</Text>
      ),
    },
    {
      title: '用户',
      dataIndex: 'user',
      key: 'user',
      render: (text: string) => (
        <Text style={{ color: 'rgba(250, 250, 250, 0.65)' }}>{text}</Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => <StatusTag status={status} />,
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      width: 140,
      render: (progress: number, record: any) => (
        <Progress
          percent={progress}
          size="small"
          strokeColor="#0ea5e9"
          trailColor="rgba(255, 255, 255, 0.06)"
          status={record.status === 'FAILED' ? 'exception' : undefined}
          showInfo={false}
        />
      ),
    },
    {
      title: '时间',
      dataIndex: 'time',
      key: 'time',
      render: (text: string) => (
        <Text style={{ color: 'rgba(250, 250, 250, 0.45)', fontSize: 13 }}>{text}</Text>
      ),
    },
  ]

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: { staggerChildren: 0.1 },
    },
  }

  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: { opacity: 1, y: 0 },
  }

  return (
    <PageContainer
      title="仪表盘"
      description="欢迎回来，这是您的系统概览"
    >
      <motion.div
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {/* 统计卡片 */}
        <Row gutter={[20, 20]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="总用户数"
              value={stats.totalUsers || 1248}
              icon={<UserOutlined />}
              gradient={['#0ea5e9', '#06b6d4']}
              trend="up"
              trendValue={12.5}
              loading={loading}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="总小说数"
              value={stats.totalNovels || 856}
              icon={<BookOutlined />}
              gradient={['#22c55e', '#16a34a']}
              trend="up"
              trendValue={8.3}
              loading={loading}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="AI任务数"
              value={stats.totalAITasks || 3147}
              icon={<RobotOutlined />}
              gradient={['#f97316', '#ea580c']}
              trend="down"
              trendValue={3.2}
              loading={loading}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="总成本"
              value={`¥${(stats.totalCost || 2847).toFixed(0)}`}
              icon={<DollarOutlined />}
              gradient={['#a855f7', '#9333ea']}
              trend="up"
              trendValue={5.7}
              loading={loading}
            />
          </Col>
        </Row>

        {/* 图表区域 */}
        <Row gutter={[20, 20]} style={{ marginBottom: 24 }}>
          <Col xs={24} lg={14}>
            <motion.div variants={itemVariants}>
              <ChartCard
                title={
                  <Space size={10}>
                    <LineChartOutlined style={{ color: '#0ea5e9', fontSize: 18 }} />
                    <span style={{ fontWeight: 600 }}>用户增长趋势</span>
                  </Space>
                }
                styles={{ body: { padding: 20 } }}
              >
                <ReactECharts option={userTrendOption} style={{ height: 320 }} />
              </ChartCard>
            </motion.div>
          </Col>
          <Col xs={24} lg={10}>
            <motion.div variants={itemVariants}>
              <ChartCard
                title={
                  <Space size={10}>
                    <PieChartOutlined style={{ color: '#06b6d4', fontSize: 18 }} />
                    <span style={{ fontWeight: 600 }}>AI任务分布</span>
                  </Space>
                }
                styles={{ body: { padding: 20 } }}
              >
                <ReactECharts option={aiTaskOption} style={{ height: 320 }} />
              </ChartCard>
            </motion.div>
          </Col>
        </Row>

        {/* 最近任务表格 */}
        <motion.div variants={itemVariants}>
          <DataTable
            title="最近任务"
            columns={taskColumns}
            dataSource={recentTasks}
            pagination={false}
            showSearch={false}
            extra={
              <LiveBadge>
                <PulseDot />
                实时更新
              </LiveBadge>
            }
          />
        </motion.div>
      </motion.div>
    </PageContainer>
  )
}

export default Dashboard
