import { useState, useEffect } from 'react'
import { Table, Button, Space, Tag, Card, Row, Col, Statistic, Input, message } from 'antd'
import { 
  DownloadOutlined, 
  EyeOutlined, 
  DatabaseOutlined, 
  FileTextOutlined,
  SearchOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { qimaoAPI, QimaoNovel, QimaoStats } from '../../services/qimao'

const QimaoList = () => {
  const [data, setData] = useState<QimaoNovel[]>([])
  const [loading, setLoading] = useState(false)
  const [stats, setStats] = useState<QimaoStats>({
    totalCount: 0,
    totalChapterCount: 0,
    todayCount: 0,
  })
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })
  const [keyword, setKeyword] = useState('')

  useEffect(() => {
    fetchData()
    fetchStats()
  }, [pagination.current, pagination.pageSize])

  const fetchData = async () => {
    try {
      setLoading(true)
      const response = await qimaoAPI.getQimaoNovels({
        keyword,
        page: pagination.current,
        size: pagination.pageSize,
      })
      
      // 智能解析响应数据
      let records: QimaoNovel[] = []
      let total = 0
      
      if (Array.isArray(response)) {
        records = response
        total = response.length
      } else if (response && typeof response === 'object') {
        if ((response as any).records) {
          records = (response as any).records
          total = (response as any).total || (response as any).records.length
        } else if (response.data) {
          if (Array.isArray(response.data)) {
            records = response.data
            total = response.data.length
          } else if ((response as any).data.records) {
            records = (response as any).data.records
            total = (response as any).data.total || (response as any).data.records.length
          }
        } else if ((response as any).list) {
          records = (response as any).list
          total = (response as any).total || (response as any).list.length
        }
      }
      
      setData(records)
      setPagination(prev => ({ ...prev, total }))
    } catch (error) {
      console.error('获取七猫数据失败:', error)
      message.error('获取数据失败')
    } finally {
      setLoading(false)
    }
  }

  const fetchStats = async () => {
    try {
      const response = await qimaoAPI.getQimaoStats()
      if (response && typeof response === 'object') {
        setStats(((response as any).data || response) as QimaoStats)
      }
    } catch (error) {
      console.error('获取统计数据失败:', error)
    }
  }

  const handleSync = () => {
    fetchData()
    fetchStats()
    message.success('数据已同步')
  }

  const handleSearch = () => {
    setPagination(prev => ({ ...prev, current: 1 }))
    fetchData()
  }

  const getStatusConfig = (status: string) => {
    const configs: Record<string, { color: string; text: string }> = {
      COMPLETED: { color: 'success', text: '已完成' },
      ONGOING: { color: 'processing', text: '连载中' },
      PENDING: { color: 'warning', text: '待爬取' },
    }
    return configs[status] || { color: 'default', text: status }
  }

  const columns: ColumnsType<QimaoNovel> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { 
      title: '小说标题', 
      dataIndex: 'novelTitle', 
      key: 'novelTitle',
      render: (text: string) => (
        <span style={{ fontWeight: 500, color: '#667eea' }}>{text}</span>
      ),
    },
    { title: '作者', dataIndex: 'author', key: 'author' },
    { 
      title: '分类', 
      dataIndex: 'category', 
      key: 'category',
      render: (category: string) => (
        <Tag color="purple">{category}</Tag>
      ),
    },
    { 
      title: '章节数', 
      dataIndex: 'chapterCount', 
      key: 'chapterCount',
      render: (count: number) => (
        <span style={{ fontWeight: 500 }}>{count}</span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const { color, text } = getStatusConfig(status)
        return <Tag color={color}>{text}</Tag>
      },
    },
    { title: '爬取时间', dataIndex: 'scrapedAt', key: 'scrapedAt' },
    {
      title: '操作',
      key: 'action',
      render: () => (
        <Space>
          <Button 
            type="link" 
            icon={<EyeOutlined />}
            style={{ color: '#667eea' }}
          >
            查看
          </Button>
          <Button 
            type="link" 
            icon={<DownloadOutlined />}
            style={{ color: '#43e97b' }}
          >
            导出
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card 
            variant="borderless"
            style={{
              background: 'linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%)',
              borderLeft: '4px solid #667eea',
            }}
          >
            <Statistic
              title="爬取小说总数"
              value={stats.totalCount}
              prefix={<DatabaseOutlined style={{ color: '#667eea' }} />}
              valueStyle={{ color: '#667eea', fontWeight: 600 }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card 
            variant="borderless"
            style={{
              background: 'linear-gradient(135deg, rgba(240, 147, 251, 0.1) 0%, rgba(245, 87, 108, 0.1) 100%)',
              borderLeft: '4px solid #f093fb',
            }}
          >
            <Statistic
              title="总章节数"
              value={stats.totalChapterCount}
              prefix={<FileTextOutlined style={{ color: '#f093fb' }} />}
              valueStyle={{ color: '#f093fb', fontWeight: 600 }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card 
            variant="borderless"
            style={{
              background: 'linear-gradient(135deg, rgba(67, 233, 123, 0.1) 0%, rgba(56, 249, 215, 0.1) 100%)',
              borderLeft: '4px solid #43e97b',
            }}
          >
            <Statistic
              title="今日爬取"
              value={stats.todayCount}
              prefix={<SyncOutlined style={{ color: '#43e97b' }} />}
              valueStyle={{ color: '#43e97b', fontWeight: 600 }}
            />
          </Card>
        </Col>
      </Row>

      <div style={{ 
        marginBottom: 24,
        padding: '16px 20px',
        background: 'white',
        borderRadius: '12px',
        boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <Space size="middle">
          <Input
            placeholder="搜索小说标题或作者"
            prefix={<SearchOutlined style={{ color: '#667eea' }} />}
            style={{ width: 350 }}
            size="large"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={handleSearch}
          />
          <Button 
            type="primary" 
            size="large"
            style={{ minWidth: 100 }}
            onClick={handleSearch}
          >
            搜索
          </Button>
        </Space>
        <Button
          type="primary"
          icon={<SyncOutlined spin={loading} />}
          onClick={handleSync}
          loading={loading}
          size="large"
          style={{
            background: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
            border: 'none',
            boxShadow: '0 4px 12px rgba(67, 233, 123, 0.4)',
          }}
        >
          同步数据
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        pagination={{ 
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条数据`,
          onChange: (page, pageSize) => {
            setPagination(prev => ({ ...prev, current: page, pageSize }))
          },
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

export default QimaoList
