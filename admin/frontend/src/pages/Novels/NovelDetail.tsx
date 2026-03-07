import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Tabs,
  Descriptions,
  Table,
  Button,
  Space,
  Tag,
  Statistic,
  Row,
  Col,
  message,
  Modal,
  Form,
  Input,
  Select,
} from 'antd'
import {
  ArrowLeftOutlined,
  EditOutlined,
  FileTextOutlined,
  BookOutlined,
  ProjectOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  novelDetailAPI,
  NovelDetail as NovelDetailType,
  Volume,
  Chapter,
} from '../../services/novelDetail'

interface Outline {
  id: number
  novelId: number
  title: string
  status: string
  targetChapterCount: number
  coreTheme: string
  plotStructure: string
  createdAt: string
}

interface ChapterOutline {
  id: number
  novelId: number
  volumeId: number
  volumeNumber: number
  chapterInVolume: number
  globalChapterNumber: number
  direction: string
  keyPlotPoints: string // JSON 字符串
  emotionalTone: string
  foreshadowAction: string
  foreshadowDetail: string // JSON 字符串
  subplot: string
  antagonism: string
  status: string
  createdAt: string
  updatedAt: string
}

const NovelDetail = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [novel, setNovel] = useState<NovelDetailType | null>(null)
  const [outline, setOutline] = useState<Outline | null>(null)
  const [volumes, setVolumes] = useState<Volume[]>([])
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [chapterOutlines, setChapterOutlines] = useState<ChapterOutline[]>([])

  // 模态框状态
  const [previewModalVisible, setPreviewModalVisible] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [currentChapter, setCurrentChapter] = useState<Chapter | null>(null)
  const [form] = Form.useForm()

  useEffect(() => {
    if (id) {
      fetchAllData()
    }
  }, [id])

  const fetchAllData = async () => {
    if (!id) return

    setLoading(true)
    try {
      const novelId = parseInt(id)

      // 调用统一的详情接口
      const response: any = await novelDetailAPI.getNovelDetail(novelId)
      console.log('小说详情响应:', response)

      // 后端返回: { code, data: { novel, outline, volumes, chapterOutlines, chapters }, message }
      if (response && response.data) {
        const { novel, outline, volumes, chapterOutlines, chapters } = response.data

        if (novel) setNovel(novel)
        if (outline) setOutline(outline)
        if (volumes && Array.isArray(volumes)) setVolumes(volumes)
        if (chapters && Array.isArray(chapters)) setChapters(chapters)
        if (chapterOutlines && Array.isArray(chapterOutlines)) setChapterOutlines(chapterOutlines)

        message.success('加载成功')
      } else {
        message.error('数据格式错误')
      }
    } catch (error: any) {
      console.error('加载小说详情失败:', error)
      message.error(error.message || '加载失败')
    } finally {
      setLoading(false)
    }
  }

  // 预览章节
  const handlePreview = async (chapter: Chapter) => {
    try {
      message.loading({ content: '加载中...', key: 'loadChapter' })
      
      // 调用后端 API 获取完整章节内容
      const response: any = await fetch(`/admin/novels/${id}/chapters/${chapter.id}`)
      const result = await response.json()
      
      message.destroy('loadChapter')
      
      if (result.code === 200 && result.data) {
        setCurrentChapter(result.data)
        setPreviewModalVisible(true)
      } else {
        message.error(result.message || '加载章节失败')
      }
    } catch (error) {
      message.destroy('loadChapter')
      console.error('加载章节内容失败:', error)
      message.error('加载章节失败')
    }
  }

  // 编辑章节
  const handleEdit = async (chapter: Chapter) => {
    try {
      message.loading({ content: '加载中...', key: 'loadChapter' })
      
      // 调用后端 API 获取完整章节内容
      const response: any = await fetch(`/admin/novels/${id}/chapters/${chapter.id}`)
      const result = await response.json()
      
      message.destroy('loadChapter')
      
      if (result.code === 200 && result.data) {
        setCurrentChapter(result.data)
        form.setFieldsValue({
          title: result.data.title,
          subtitle: result.data.subtitle,
          content: result.data.content,
          summary: result.data.summary,
          status: result.data.status,
          wordCount: result.data.wordCount,
          isPublic: result.data.isPublic,
        })
        setEditModalVisible(true)
      } else {
        message.error(result.message || '加载章节失败')
      }
    } catch (error) {
      message.destroy('loadChapter')
      console.error('加载章节内容失败:', error)
      message.error('加载章节失败')
    }
  }

  // 保存编辑
  const handleSaveEdit = async () => {
    try {
      const values = await form.validateFields()
      
      // 自动计算字数（如果有内容且未手动输入）
      if (values.content && !values.wordCount) {
        values.wordCount = values.content.length
      }
      
      message.loading({ content: '保存中...', key: 'saveChapter' })
      
      // 调用后端 API 保存
      const response = await fetch(`/admin/novels/${id}/chapters/${currentChapter?.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(values),
      })
      
      const result = await response.json()
      
      message.destroy('saveChapter')
      
      if (result.code === 200) {
        message.success('保存成功')
        setEditModalVisible(false)
        
        // 刷新数据
        fetchAllData()
      } else {
        message.error(result.message || '保存失败')
      }
    } catch (error) {
      message.destroy('saveChapter')
      console.error('保存失败:', error)
      message.error('保存失败')
    }
  }

  const volumeColumns: ColumnsType<Volume> = [
    {
      title: '卷号',
      dataIndex: 'volumeNumber',
      key: 'volumeNumber',
      width: 80,
      render: (num: number) => (
        <span style={{ fontWeight: 600, color: 'var(--accent-primary)' }}>第{num}卷</span>
      ),
    },
    {
      title: '卷名',
      dataIndex: 'title',
      key: 'title',
      render: (text: string) => <span style={{ fontWeight: 500, fontSize: '14px' }}>{text}</span>,
    },
    {
      title: '章节范围',
      key: 'range',
      render: (_: any, record: Volume) => (
        <span style={{ color: 'var(--text-secondary)' }}>
          第{record.chapterStart}章 - 第{record.chapterEnd}章
        </span>
      ),
    },
    {
      title: '主题',
      dataIndex: 'theme',
      key: 'theme',
      render: (theme: string) => <Tag color="processing">{theme || '未设置'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const config: Record<string, { color: string; text: string }> = {
          PLANNED: { color: 'default', text: '计划中' },
          IN_PROGRESS: { color: 'processing', text: '进行中' },
          COMPLETED: { color: 'success', text: '已完成' },
        }
        const { color, text } = config[status] || { color: 'default', text: status }
        return <Tag color={color}>{text}</Tag>
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: () => (
        <Space size={8}>
          <Button type="text" size="small" icon={<EditOutlined />}>
            编辑
          </Button>
          <Button type="text" size="small" icon={<EyeOutlined />}>
            查看
          </Button>
        </Space>
      ),
    },
  ]

  const chapterColumns: ColumnsType<Chapter> = [
    {
      title: '序号',
      dataIndex: 'orderNum',
      key: 'orderNum',
      width: 80,
      render: (num: number) => (
        <span style={{ fontWeight: 600, color: 'var(--text-tertiary)' }}>#{num}</span>
      ),
    },
    {
      title: '章节名',
      dataIndex: 'title',
      key: 'title',
      render: (text: string) => <span style={{ fontWeight: 500, fontSize: '14px' }}>{text}</span>,
    },
    {
      title: '字数',
      dataIndex: 'wordCount',
      key: 'wordCount',
      width: 100,
      render: (count: number) => (
        <span style={{ fontWeight: 600, color: 'var(--text-secondary)' }}>
          {count?.toLocaleString() || 0}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const config: Record<string, { color: string; text: string }> = {
          DRAFT: { color: 'default', text: '草稿' },
          IN_PROGRESS: { color: 'processing', text: '创作中' },
          REVIEW: { color: 'warning', text: '待审核' },
          PUBLISHED: { color: 'success', text: '已发布' },
          ARCHIVED: { color: 'default', text: '已归档' },
        }
        const { color, text } = config[status] || { color: 'default', text: status }
        return <Tag color={color}>{text}</Tag>
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (date: string) => (
        <span style={{ color: 'var(--text-tertiary)', fontSize: '13px' }}>
          {date ? new Date(date).toLocaleString('zh-CN') : '-'}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      fixed: 'right',
      render: (_: any, record: Chapter) => (
        <Space size={4}>
          <Button
            type="text"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handlePreview(record)}
          >
            预览
          </Button>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
        </Space>
      ),
    },
  ]

  // 查看章纲详情
  const handleViewOutline = (outline: ChapterOutline) => {
    let plotPoints: string[] = []
    let foreshadowDetails: any = null

    try {
      plotPoints = outline.keyPlotPoints ? JSON.parse(outline.keyPlotPoints) : []
      if (!Array.isArray(plotPoints)) plotPoints = []
    } catch (e) {
      console.error('解析关键情节点失败:', e)
    }

    try {
      foreshadowDetails = outline.foreshadowDetail ? JSON.parse(outline.foreshadowDetail) : null
    } catch (e) {
      console.error('解析伏笔详情失败:', e)
    }

    Modal.info({
      title: `章纲详情 - 第${outline.globalChapterNumber}章`,
      width: 800,
      content: (
        <div style={{ marginTop: 20 }}>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="卷号">第 {outline.volumeNumber} 卷</Descriptions.Item>
            <Descriptions.Item label="卷内章节">第 {outline.chapterInVolume} 章</Descriptions.Item>
            <Descriptions.Item label="全局章节号" span={2}>
              第 {outline.globalChapterNumber} 章
            </Descriptions.Item>
          </Descriptions>

          <div style={{ marginTop: 20 }}>
            <div
              style={{
                fontSize: '14px',
                fontWeight: 600,
                color: 'var(--text-primary)',
                marginBottom: 12,
              }}
            >
              关键情节点
            </div>
            {plotPoints.length > 0 ? (
              <div
                style={{
                  background: 'var(--bg-hover)',
                  padding: '16px',
                  borderRadius: '8px',
                  border: '1px solid var(--border-primary)',
                }}
              >
                <ol style={{ margin: 0, paddingLeft: 20 }}>
                  {plotPoints.map((point: string, index: number) => (
                    <li
                      key={index}
                      style={{
                        marginBottom: 12,
                        color: 'var(--text-primary)',
                        lineHeight: 1.6,
                      }}
                    >
                      {point}
                    </li>
                  ))}
                </ol>
              </div>
            ) : (
              <div
                style={{
                  background: 'var(--bg-hover)',
                  padding: '20px',
                  borderRadius: '8px',
                  textAlign: 'center',
                  color: 'var(--text-tertiary)',
                }}
              >
                暂无情节点
              </div>
            )}
          </div>

          <div style={{ marginTop: 20 }}>
            <div
              style={{
                fontSize: '14px',
                fontWeight: 600,
                color: 'var(--text-primary)',
                marginBottom: 12,
              }}
            >
              伏笔信息
            </div>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="伏笔动作">
                <Tag color={outline.foreshadowAction === 'NONE' ? 'default' : 'processing'}>
                  {outline.foreshadowAction || 'NONE'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="伏笔详情">
                {foreshadowDetails ? (
                  <div
                    style={{
                      whiteSpace: 'pre-wrap',
                      maxHeight: 300,
                      overflow: 'auto',
                      padding: '8px',
                      background: 'var(--bg-hover)',
                      borderRadius: '6px',
                      fontFamily: 'monospace',
                      fontSize: '13px',
                    }}
                  >
                    {typeof foreshadowDetails === 'string'
                      ? foreshadowDetails
                      : JSON.stringify(foreshadowDetails, null, 2)}
                  </div>
                ) : (
                  <span style={{ color: 'var(--text-tertiary)' }}>暂无伏笔详情</span>
                )}
              </Descriptions.Item>
            </Descriptions>
          </div>

          {outline.subplot && (
            <div style={{ marginTop: 20 }}>
              <div
                style={{
                  fontSize: '14px',
                  fontWeight: 600,
                  color: 'var(--text-primary)',
                  marginBottom: 12,
                }}
              >
                副线情节
              </div>
              <div
                style={{
                  background: 'var(--bg-hover)',
                  padding: '16px',
                  borderRadius: '8px',
                  border: '1px solid var(--border-primary)',
                  color: 'var(--text-secondary)',
                  lineHeight: 1.6,
                }}
              >
                {outline.subplot}
              </div>
            </div>
          )}
        </div>
      ),
      okText: '关闭',
    })
  }

  // 编辑章纲
  const [editOutlineModalVisible, setEditOutlineModalVisible] = useState(false)
  const [currentOutline, setCurrentOutline] = useState<ChapterOutline | null>(null)
  const [plotPointsList, setPlotPointsList] = useState<string[]>([])
  const [outlineForm] = Form.useForm()

  const handleEditOutline = (outline: ChapterOutline) => {
    setCurrentOutline(outline)
    
    // 解析关键情节点
    let points: string[] = []
    try {
      points = outline.keyPlotPoints ? JSON.parse(outline.keyPlotPoints) : []
      if (!Array.isArray(points)) points = []
    } catch (e) {
      console.error('解析关键情节点失败:', e)
    }
    setPlotPointsList(points.length > 0 ? points : ['']) // 至少有一个空输入框

    // 解析伏笔详情
    let foreshadowDetail = ''
    try {
      const detail = outline.foreshadowDetail ? JSON.parse(outline.foreshadowDetail) : null
      foreshadowDetail = detail ? (typeof detail === 'string' ? detail : JSON.stringify(detail, null, 2)) : ''
    } catch (e) {
      foreshadowDetail = outline.foreshadowDetail || ''
    }

    outlineForm.setFieldsValue({
      foreshadowAction: outline.foreshadowAction || 'NONE',
      foreshadowDetail: foreshadowDetail,
    })
    setEditOutlineModalVisible(true)
  }

  const handleSaveOutline = async () => {
    try {
      const values = await outlineForm.validateFields()
      console.log('保存章纲:', {
        ...currentOutline,
        ...values,
        keyPlotPoints: JSON.stringify(plotPointsList),
      })
      message.success('保存成功')
      setEditOutlineModalVisible(false)
      // TODO: 调用后端 API 保存
    } catch (error) {
      console.error('保存失败:', error)
    }
  }

  const addPlotPoint = () => {
    setPlotPointsList([...plotPointsList, ''])
  }

  const updatePlotPoint = (index: number, value: string) => {
    const newList = [...plotPointsList]
    newList[index] = value
    setPlotPointsList(newList)
  }

  const removePlotPoint = (index: number) => {
    setPlotPointsList(plotPointsList.filter((_, i) => i !== index))
  }

  const chapterOutlineColumns: ColumnsType<ChapterOutline> = [
    {
      title: '章节',
      key: 'chapter',
      width: 100,
      fixed: 'left',
      render: (_: any, record: ChapterOutline) => (
        <div>
          <div style={{ fontWeight: 600, color: 'var(--accent-primary)', fontSize: '14px' }}>
            第 {record.globalChapterNumber} 章
          </div>
          <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: 2 }}>
            卷{record.volumeNumber}-{record.chapterInVolume}
          </div>
        </div>
      ),
    },
    {
      title: '关键情节点',
      dataIndex: 'keyPlotPoints',
      key: 'keyPlotPoints',
      width: 300,
      render: (text: string) => {
        if (!text) return <span style={{ color: 'var(--text-tertiary)' }}>-</span>
        try {
          const points = JSON.parse(text)
          if (Array.isArray(points) && points.length > 0) {
            return (
              <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                {points.slice(0, 2).map((point: string, idx: number) => (
                  <div key={idx} style={{ marginBottom: 4 }}>
                    • {point.length > 40 ? point.slice(0, 40) + '...' : point}
                  </div>
                ))}
                {points.length > 2 && (
                  <div style={{ color: 'var(--text-tertiary)', fontSize: '12px' }}>
                    还有 {points.length - 2} 个情节点...
                  </div>
                )}
              </div>
            )
          }
          return <span style={{ color: 'var(--text-tertiary)' }}>-</span>
        } catch {
          return <span style={{ color: 'var(--text-tertiary)' }}>解析失败</span>
        }
      },
    },
    {
      title: '伏笔',
      key: 'foreshadow',
      width: 120,
      render: (_: any, record: ChapterOutline) => (
        <div>
          <Tag
            color={
              record.foreshadowAction === 'NONE' || !record.foreshadowAction
                ? 'default'
                : 'processing'
            }
          >
            {record.foreshadowAction || 'NONE'}
          </Tag>
          {record.foreshadowDetail && record.foreshadowAction !== 'NONE' && (
            <div style={{ fontSize: '11px', color: 'var(--accent-primary)', marginTop: 4 }}>
              ✓ 有详情
            </div>
          )}
        </div>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      fixed: 'right',
      render: (_: any, record: ChapterOutline) => (
        <Space size={4}>
          <Button
            type="text"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewOutline(record)}
          >
            查看
          </Button>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEditOutline(record)}
          >
            编辑
          </Button>
        </Space>
      ),
    },
  ]

  const tabItems = [
    {
      key: 'outline',
      label: (
        <Space size={8}>
          <FileTextOutlined />
          <span>大纲</span>
        </Space>
      ),
      children: (
        <div>
          {outline ? (
            <Card>
              <Descriptions column={2} bordered>
                <Descriptions.Item label="大纲标题" span={2}>
                  <span style={{ fontSize: '16px', fontWeight: 600 }}>{outline.title}</span>
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color="success">{outline.status || '已完成'}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="目标章节数">
                  <span style={{ fontWeight: 600 }}>{outline.targetChapterCount} 章</span>
                </Descriptions.Item>
                <Descriptions.Item label="核心主题" span={2}>
                  {outline.coreTheme || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="情节结构" span={2}>
                  <div style={{ whiteSpace: 'pre-wrap' }}>{outline.plotStructure || '-'}</div>
                </Descriptions.Item>
                <Descriptions.Item label="创建时间">
                  {outline.createdAt ? new Date(outline.createdAt).toLocaleString('zh-CN') : '-'}
                </Descriptions.Item>
              </Descriptions>
              <div style={{ marginTop: 16, textAlign: 'right' }}>
                <Space>
                  <Button icon={<EyeOutlined />}>查看详情</Button>
                  <Button type="primary" icon={<EditOutlined />}>
                    编辑大纲
                  </Button>
                </Space>
              </div>
            </Card>
          ) : (
            <Card>
              <div style={{ textAlign: 'center', padding: '60px 0' }}>
                <FileTextOutlined style={{ fontSize: '48px', color: 'var(--text-quaternary)' }} />
                <p style={{ color: 'var(--text-tertiary)', marginTop: '16px', fontSize: '14px' }}>
                  暂无大纲数据
                </p>
                <Button type="primary" style={{ marginTop: 16 }}>
                  创建大纲
                </Button>
              </div>
            </Card>
          )}
        </div>
      ),
    },
    {
      key: 'volumes',
      label: (
        <Space size={8}>
          <BookOutlined />
          <span>分卷</span>
        </Space>
      ),
      children: (
        <div>
          <div style={{ marginBottom: 16, textAlign: 'right' }}>
            <Button type="primary">添加分卷</Button>
          </div>
          <Table columns={volumeColumns} dataSource={volumes} rowKey="id" pagination={false} />
        </div>
      ),
    },
    {
      key: 'chapters',
      label: (
        <Space size={8}>
          <FileTextOutlined />
          <span>章节</span>
        </Space>
      ),
      children: (
        <div>
          <div style={{ marginBottom: 16, textAlign: 'right' }}>
            <Button type="primary">添加章节</Button>
          </div>
          <Table columns={chapterColumns} dataSource={chapters} rowKey="id" pagination={{ pageSize: 20 }} />
        </div>
      ),
    },
    {
      key: 'chapterOutlines',
      label: (
        <Space size={8}>
          <ProjectOutlined />
          <span>章纲</span>
        </Space>
      ),
      children: (
        <div>
          <div style={{ marginBottom: 16, textAlign: 'right' }}>
            <Button type="primary">创建章纲</Button>
          </div>
          <Table
            columns={chapterOutlineColumns}
            dataSource={chapterOutlines}
            rowKey="id"
            pagination={{ pageSize: 20 }}
          />
        </div>
      ),
    },
  ]

  return (
    <div>
      {/* 顶部操作栏 */}
      <div style={{ marginBottom: 24 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/novels')}>
            返回列表
          </Button>
          <Button
            type="primary"
            icon={<EditOutlined />}
            onClick={() => {
              message.info('编辑小说功能开发中...')
              // TODO: 实现编辑小说功能
            }}
          >
            编辑小说
          </Button>
        </Space>
      </div>

      {/* 小说基本信息卡片 */}
      <Card title={novel?.title || '小说详情'} loading={loading} style={{ marginBottom: 20 }}>
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <Statistic
              title="总字数"
              value={novel?.wordCount || 0}
              suffix="字"
              valueStyle={{ color: '#0ea5e9', fontWeight: 700 }}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic
              title="章节数"
              value={novel?.chapterCount || 0}
              suffix="章"
              valueStyle={{ color: '#22c55e', fontWeight: 700 }}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic
              title="分卷数"
              value={volumes.length}
              suffix="卷"
              valueStyle={{ color: '#f97316', fontWeight: 700 }}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Statistic
              title="章纲数"
              value={chapterOutlines.length}
              suffix="个"
              valueStyle={{ color: '#a855f7', fontWeight: 700 }}
            />
          </Col>
        </Row>

        <Descriptions bordered column={2}>
          <Descriptions.Item label="作者">{novel?.author || '-'}</Descriptions.Item>
          <Descriptions.Item label="类型">{novel?.genre || '-'}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color="processing">{novel?.status || '-'}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {novel?.createdAt ? new Date(novel.createdAt).toLocaleDateString('zh-CN') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="简介" span={2}>
            {novel?.description || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 详细信息标签页 */}
      <Card styles={{ body: { padding: '20px' } }}>
        <Tabs items={tabItems} />
      </Card>

      {/* 预览模态框 */}
      <Modal
        title={`预览章节 - ${currentChapter?.title}`}
        open={previewModalVisible}
        onCancel={() => setPreviewModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setPreviewModalVisible(false)}>
            关闭
          </Button>,
          <Button
            key="edit"
            type="primary"
            icon={<EditOutlined />}
            onClick={() => {
              setPreviewModalVisible(false)
              handleEdit(currentChapter!)
            }}
          >
            编辑
          </Button>,
        ]}
        width={900}
      >
        <div style={{ padding: '20px 0' }}>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="章节序号">第 {currentChapter?.orderNum} 章</Descriptions.Item>
            <Descriptions.Item label="章节编号">#{currentChapter?.chapterNumber}</Descriptions.Item>
            <Descriptions.Item label="章节标题" span={2}>
              <span style={{ fontSize: '15px', fontWeight: 600 }}>{currentChapter?.title}</span>
            </Descriptions.Item>
            {currentChapter?.subtitle && (
              <Descriptions.Item label="副标题" span={2}>
                {currentChapter.subtitle}
              </Descriptions.Item>
            )}
            <Descriptions.Item label="字数">{currentChapter?.wordCount?.toLocaleString()} 字</Descriptions.Item>
            <Descriptions.Item label="阅读时长">
              {currentChapter?.readingTimeMinutes || '-'} 分钟
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color="success">{currentChapter?.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="是否公开">
              <Tag color={currentChapter?.isPublic ? 'success' : 'default'}>
                {currentChapter?.isPublic ? '公开' : '私密'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {currentChapter?.createdAt ? new Date(currentChapter.createdAt).toLocaleString('zh-CN') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="发布时间">
              {currentChapter?.publishedAt
                ? new Date(currentChapter.publishedAt).toLocaleString('zh-CN')
                : '-'}
            </Descriptions.Item>
          </Descriptions>

          {/* 章节摘要 */}
          {currentChapter?.summary && (
            <div style={{ marginTop: 20 }}>
              <div
                style={{
                  fontSize: '13px',
                  fontWeight: 600,
                  color: 'var(--text-secondary)',
                  marginBottom: 8,
                }}
              >
                章节摘要
              </div>
              <div
                style={{
                  padding: 16,
                  background: 'var(--bg-hover)',
                  borderRadius: '8px',
                  color: 'var(--text-secondary)',
                  fontSize: '14px',
                  lineHeight: 1.6,
                }}
              >
                {currentChapter.summary}
              </div>
            </div>
          )}

          {/* 章节内容 */}
          <div style={{ marginTop: 20 }}>
            <div
              style={{
                fontSize: '13px',
                fontWeight: 600,
                color: 'var(--text-secondary)',
                marginBottom: 8,
              }}
            >
              章节内容
            </div>
            <div
              style={{
                padding: 20,
                background: 'var(--bg-hover)',
                borderRadius: '8px',
                maxHeight: 400,
                overflow: 'auto',
              }}
            >
              {currentChapter?.content ? (
                <div
                  style={{
                    color: 'var(--text-primary)',
                    fontSize: '14px',
                    lineHeight: 1.8,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {currentChapter.content}
                </div>
              ) : (
                <p style={{ color: 'var(--text-tertiary)', textAlign: 'center', padding: '40px 0' }}>
                  暂无章节内容
                </p>
              )}
            </div>
          </div>

          {/* 章节备注 */}
          {currentChapter?.notes && (
            <div style={{ marginTop: 20 }}>
              <div
                style={{
                  fontSize: '13px',
                  fontWeight: 600,
                  color: 'var(--text-secondary)',
                  marginBottom: 8,
                }}
              >
                备注
              </div>
              <div
                style={{
                  padding: 16,
                  background: 'rgba(14, 165, 233, 0.1)',
                  border: '1px solid rgba(14, 165, 233, 0.2)',
                  borderRadius: '8px',
                  color: 'var(--text-secondary)',
                  fontSize: '13px',
                  lineHeight: 1.6,
                }}
              >
                {currentChapter.notes}
              </div>
            </div>
          )}
        </div>
      </Modal>

      {/* 编辑章节模态框 */}
      <Modal
        title={`编辑章节 - ${currentChapter?.title}`}
        open={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        onOk={handleSaveEdit}
        okText="保存"
        cancelText="取消"
        width={900}
        style={{ top: 20 }}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 20 }}>
          <Row gutter={16}>
            <Col span={16}>
              <Form.Item
                label="章节标题"
                name="title"
                rules={[{ required: true, message: '请输入章节标题' }]}
              >
                <Input placeholder="请输入章节标题" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="副标题" name="subtitle">
                <Input placeholder="请输入副标题（可选）" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="章节摘要" name="summary">
            <Input.TextArea rows={2} placeholder="请输入章节摘要" />
          </Form.Item>

          <Form.Item
            label="章节正文"
            name="content"
            extra="章节的完整内容"
          >
            <Input.TextArea
              rows={12}
              placeholder="请输入章节正文内容"
              style={{ fontFamily: 'inherit', lineHeight: 1.8 }}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="状态" name="status" rules={[{ required: true, message: '请选择状态' }]}>
                <Select>
                  <Select.Option value="DRAFT">草稿</Select.Option>
                  <Select.Option value="IN_PROGRESS">创作中</Select.Option>
                  <Select.Option value="REVIEW">待审核</Select.Option>
                  <Select.Option value="PUBLISHED">已发布</Select.Option>
                  <Select.Option value="ARCHIVED">已归档</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="字数" name="wordCount">
                <Input type="number" placeholder="自动计算" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="是否公开" name="isPublic" valuePropName="checked">
                <Select>
                  <Select.Option value={true}>公开</Select.Option>
                  <Select.Option value={false}>私密</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* 编辑章纲模态框 */}
      <Modal
        title={`编辑章纲 - 第${currentOutline?.globalChapterNumber}章`}
        open={editOutlineModalVisible}
        onCancel={() => setEditOutlineModalVisible(false)}
        onOk={handleSaveOutline}
        okText="保存"
        cancelText="取消"
        width={800}
        style={{ top: 20 }}
        styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      >
        <Form form={outlineForm} layout="vertical" style={{ marginTop: 20 }}>
          <Form.Item label="关键情节点">
            <div
              style={{
                maxHeight: 300,
                overflow: 'auto',
                marginBottom: 12,
                padding: '8px',
                background: 'var(--bg-hover)',
                borderRadius: '8px',
              }}
            >
              {plotPointsList.map((point, index) => (
                <div key={index} style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                  <div
                    style={{
                      width: 32,
                      height: 38,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      background: 'var(--accent-primary)',
                      color: 'white',
                      borderRadius: '6px',
                      fontWeight: 600,
                      fontSize: '13px',
                      flexShrink: 0,
                    }}
                  >
                    {index + 1}
                  </div>
                  <Input
                    value={point}
                    onChange={(e) => updatePlotPoint(index, e.target.value)}
                    placeholder={`请输入情节点内容`}
                    style={{ flex: 1 }}
                  />
                  <Button
                    danger
                    type="text"
                    icon={<span style={{ fontSize: '18px' }}>×</span>}
                    onClick={() => removePlotPoint(index)}
                    style={{ width: 38, flexShrink: 0 }}
                  />
                </div>
              ))}
              {plotPointsList.length === 0 && (
                <div
                  style={{
                    textAlign: 'center',
                    padding: '20px',
                    color: 'var(--text-tertiary)',
                    fontSize: '13px',
                  }}
                >
                  暂无情节点，点击下方按钮添加
                </div>
              )}
            </div>
            <Button type="dashed" onClick={addPlotPoint} block icon={<span>+</span>}>
              添加情节点
            </Button>
          </Form.Item>

          <Form.Item label="伏笔动作" name="foreshadowAction">
            <Select placeholder="请选择伏笔动作">
              <Select.Option value="NONE">无伏笔</Select.Option>
              <Select.Option value="PLANT">埋伏笔</Select.Option>
              <Select.Option value="REFERENCE">引用伏笔</Select.Option>
              <Select.Option value="DEEPEN">深化伏笔</Select.Option>
              <Select.Option value="RESOLVE">解决伏笔</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            label="伏笔详情"
            name="foreshadowDetail"
            extra="详细描述伏笔的内容、作用和相关信息"
          >
            <Input.TextArea
              rows={16}
              placeholder="请输入伏笔详情，可以是文本描述或 JSON 格式"
              style={{ 
                fontFamily: 'monospace', 
                fontSize: '13px',
                minHeight: '320px',
                resize: 'vertical'
              }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default NovelDetail
