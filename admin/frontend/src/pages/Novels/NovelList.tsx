import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Row, Col, message, Space, Typography } from 'antd'
import {
  PlusOutlined,
  EyeOutlined,
  EditOutlined,
  BookOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import styled from '@emotion/styled'
import { adminNovelService } from '@/services/adminNovelService'
import { 
  PageContainer, 
  DataTable, 
  StatCard, 
  StatusTag, 
  ActionButton 
} from '@/components'

const { Text } = Typography

interface Novel {
  id: number
  title: string
  author: string
  genre: string
  status: string
  chapterCount: number
  wordCount: number
  createdAt: string
}

const NovelIcon = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
`

const NovelInfo = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`

const IdText = styled.span`
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  color: rgba(250, 250, 250, 0.45);
  font-size: 13px;
`

const CountWrapper = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
`

const NovelList = () => {
  const navigate = useNavigate()
  const [novels, setNovels] = useState<Novel[]>([])
  const [loading, setLoading] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [total, setTotal] = useState(0)

  useEffect(() => {
    loadNovels()
  }, [])

  const loadNovels = async () => {
    setLoading(true)
    try {
      const response = await adminNovelService.getNovels({ keyword: searchText })
      let novelData: Novel[] = []
      if (Array.isArray(response)) {
        novelData = response
      } else if (response && Array.isArray((response as any).records)) {
        novelData = (response as any).records
        setTotal((response as any).total || 0)
      } else if (response && Array.isArray(response.data)) {
        novelData = response.data
      }
      setNovels(novelData)
      setTotal(novelData.length)
    } catch (error) {
      console.error('加载小说列表失败:', error)
      message.error('加载小说列表失败')
      setNovels([])
    } finally {
      setLoading(false)
    }
  }

  const columns: ColumnsType<Novel> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      render: (id: number) => <IdText>#{id}</IdText>,
    },
    {
      title: '小说',
      dataIndex: 'title',
      key: 'title',
      render: (text: string, record: Novel) => (
        <NovelInfo>
          <NovelIcon>📖</NovelIcon>
          <div>
            <Text style={{ fontWeight: 600, color: '#fafafa', display: 'block' }}>
              {text}
            </Text>
            <Text style={{ fontSize: 12, color: 'rgba(250, 250, 250, 0.45)' }}>
              作者: {record.author}
            </Text>
          </div>
        </NovelInfo>
      ),
    },
    {
      title: '类型',
      dataIndex: 'genre',
      key: 'genre',
      width: 100,
      render: (genre: string) => <StatusTag status="processing" text={genre} showIcon={false} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => <StatusTag status={status} />,
    },
    {
      title: '章节数',
      dataIndex: 'chapterCount',
      key: 'chapterCount',
      width: 100,
      render: (count: number) => (
        <CountWrapper>
          <FileTextOutlined style={{ color: 'rgba(250, 250, 250, 0.45)', fontSize: 14 }} />
          <Text style={{ fontWeight: 600, color: '#fafafa' }}>{count}</Text>
        </CountWrapper>
      ),
    },
    {
      title: '字数',
      dataIndex: 'wordCount',
      key: 'wordCount',
      width: 100,
      render: (count: number) => (
        <Text style={{ fontWeight: 600, color: 'rgba(250, 250, 250, 0.65)' }}>
          {(count / 10000).toFixed(1)}万
        </Text>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 140,
      render: (date: string) => (
        <Text style={{ color: 'rgba(250, 250, 250, 0.45)', fontSize: 13 }}>
          {new Date(date).toLocaleDateString('zh-CN')}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      fixed: 'right',
      render: (_: any, record: Novel) => (
        <Space size={4}>
          <ActionButton
            variant="primary"
            icon={<EyeOutlined />}
            tooltip="查看详情"
            onClick={() => navigate(`/novels/${record.id}`)}
          >
            查看
          </ActionButton>
          <ActionButton
            variant="default"
            icon={<EditOutlined />}
            tooltip="编辑"
          >
            编辑
          </ActionButton>
        </Space>
      ),
    },
  ]

  // 计算统计数据
  const totalChapters = novels.reduce((sum, novel) => sum + novel.chapterCount, 0)
  const totalWords = novels.reduce((sum, novel) => sum + novel.wordCount, 0)
  const ongoingCount = novels.filter((n) => n.status === 'ONGOING').length

  return (
    <PageContainer
      title="小说管理"
      description="管理所有小说内容，查看详情和统计数据"
      icon={<BookOutlined />}
      breadcrumb={[{ title: '小说管理' }]}
      extra={
        <Button type="primary" icon={<PlusOutlined />} size="large">
          新建小说
        </Button>
      }
    >
      {/* 统计卡片 */}
      <Row gutter={[20, 20]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="总小说数"
            value={total}
            icon={<BookOutlined />}
            gradient={['#0ea5e9', '#06b6d4']}
            loading={loading}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="连载中"
            value={ongoingCount}
            icon={<EditOutlined />}
            gradient={['#22c55e', '#16a34a']}
            loading={loading}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="总章节数"
            value={totalChapters}
            icon={<FileTextOutlined />}
            gradient={['#f97316', '#ea580c']}
            loading={loading}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="总字数"
            value={(totalWords / 10000).toFixed(1)}
            suffix="万"
            icon={<FileTextOutlined />}
            gradient={['#a855f7', '#9333ea']}
            loading={loading}
          />
        </Col>
      </Row>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        dataSource={novels}
        rowKey="id"
        loading={loading}
        searchPlaceholder="搜索小说标题、作者..."
        searchValue={searchText}
        onSearchChange={setSearchText}
        onSearch={loadNovels}
        onRefresh={loadNovels}
        pagination={{ total }}
      />
    </PageContainer>
  )
}

export default NovelList
