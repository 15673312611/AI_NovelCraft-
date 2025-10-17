import React, { useState, useEffect } from 'react'
import { 
  Card, 
  List, 
  Button, 
  Input, 
  Modal, 
  Form, 
  message, 
  Popconfirm, 
  Space, 
  Typography,
  Tooltip,
  Badge,
  Divider
} from 'antd'
import { 
  PlusOutlined, 
  EditOutlined, 
  DeleteOutlined, 
  FileTextOutlined
} from '@ant-design/icons'
import { Chapter, chapterService } from '@/services/chapterService'
import './ChapterManager.css'

const { Text } = Typography
const { Search } = Input

interface ChapterManagerProps {
  novelId: number
  onChapterSelect: (chapter: Chapter) => void
  selectedChapterId?: number
  onChaptersChange?: () => void
}

interface ChapterFormData {
  title: string
  content: string
  chapterNumber: number
}

const ChapterManager: React.FC<ChapterManagerProps> = ({
  novelId,
  onChapterSelect,
  selectedChapterId,
  onChaptersChange
}) => {
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingChapter, setEditingChapter] = useState<Chapter | null>(null)
  const [form] = Form.useForm()

  const [filteredChapters, setFilteredChapters] = useState<Chapter[]>([])

  // 加载章节列表
  const loadChapters = async () => {
    try {
      setLoading(true)
      const chaptersData = await chapterService.getChaptersByNovel(novelId)
      const list = Array.isArray(chaptersData) ? chaptersData : []
      setChapters(list)
      setFilteredChapters(list)
    } catch (error) {
      message.error('加载章节失败')
      setChapters([])
      setFilteredChapters([])
    } finally {
      setLoading(false)
    }
  }

  // 搜索章节
  const handleSearch = (value: string) => {
    if (!value.trim()) {
      setFilteredChapters(chapters)
    } else {
      const q = value.toLowerCase()
      const filtered = chapters.filter(chapter =>
        (chapter.title || '').toLowerCase().includes(q) ||
        (chapter.content || '').toLowerCase().includes(q)
      )
      setFilteredChapters(filtered)
    }
  }

  // 打开创建章节模态框
  const showCreateModal = () => {
    setEditingChapter(null)
    form.resetFields()
    form.setFieldsValue({
      chapterNumber: chapters.length + 1
    })
    setModalVisible(true)
  }

  // 打开编辑章节模态框
  const showEditModal = (chapter: Chapter) => {
    setEditingChapter(chapter)
    form.setFieldsValue({
      title: chapter.title,
      content: chapter.content,
      chapterNumber: chapter.chapterNumber
    })
    setModalVisible(true)
  }

  // 保存章节
  const handleSaveChapter = async (values: ChapterFormData) => {
    try {
      if (editingChapter) {
        await chapterService.updateChapter(editingChapter.id, values)
        message.success('章节更新成功')
      } else {
        await chapterService.createChapter({
          ...values,
          novelId
        })
        message.success('章节创建成功')
      }
      
      setModalVisible(false)
      loadChapters()
      onChaptersChange?.()
    } catch (error) {
      message.error('保存失败')
    }
  }

  // 删除章节
  const handleDeleteChapter = async (chapterId: number) => {
    try {
      await chapterService.deleteChapter(chapterId)
      message.success('章节删除成功')
      loadChapters()
      onChaptersChange?.()
      
      // 如果删除的是当前选中的章节，清空选择
      if (selectedChapterId === chapterId) {
        onChapterSelect({} as Chapter)
      }
    } catch (error) {
      message.error('删除失败')
    }
  }



  // 计算字数
  const calculateWordCount = (content: string) => {
    return content.replace(/<[^>]*>/g, '').trim().length
  }

  // 格式化日期
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  useEffect(() => {
    if (novelId) {
      loadChapters()
    }
  }, [novelId])

  return (
    <div className="chapter-manager">
      <Card 
        title={
          <Space>
            <FileTextOutlined />
            <span>章节管理</span>
            <Badge count={chapters.length} showZero />
          </Space>
        }
        extra={
          <Button 
            type="primary" 
            icon={<PlusOutlined />}
            onClick={showCreateModal}
          >
            新建章节
          </Button>
        }
        className="chapter-manager-card"
      >
        <div className="chapter-search">
          <Search
            placeholder="搜索章节标题或内容..."
            onSearch={handleSearch}
            allowClear
            enterButton
          />
        </div>

        <Divider />

        <List
          loading={loading}
          dataSource={filteredChapters}
          renderItem={(chapter) => (
            <List.Item
              className={`chapter-item ${selectedChapterId === chapter.id ? 'selected' : ''}`}
              actions={[
                <Tooltip title="编辑章节">
                  <Button
                    type="text"
                    icon={<EditOutlined />}
                    onClick={() => showEditModal(chapter)}
                  />
                </Tooltip>,
                <Tooltip title="删除章节">
                  <Popconfirm
                    title="确定要删除这个章节吗？"
                    onConfirm={() => handleDeleteChapter(chapter.id)}
                    okText="确定"
                    cancelText="取消"
                  >
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                    />
                  </Popconfirm>
                </Tooltip>
              ]}
            >
              <List.Item.Meta
                avatar={
                  <div className="chapter-number">
                    <Text strong>{chapter.chapterNumber}</Text>
                  </div>
                }
                title={
                  <div className="chapter-title" onClick={() => onChapterSelect(chapter)}>
                    <Text strong>{chapter.title}</Text>
                    {chapter.status === 'draft' && (
                      <Badge status="default" text="草稿" />
                    )}
                    {chapter.status === 'writing' && (
                      <Badge status="processing" text="创作中" />
                    )}
                    {chapter.status === 'completed' && (
                      <Badge status="success" text="已完成" />
                    )}
                  </div>
                }
                description={
                  <div className="chapter-info">
                    <Space split={<Divider type="vertical" />}>
                      <Text type="secondary">
                        字数: {calculateWordCount(chapter.content)}
                      </Text>
                      <Text type="secondary">
                        更新: {formatDate(chapter.updatedAt)}
                      </Text>
                    </Space>
                  </div>
                }
              />
            </List.Item>
          )}
          locale={{
            emptyText: (
              <div className="empty-chapters">
                <FileTextOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />
                <Text type="secondary">暂无章节，点击上方按钮创建第一个章节</Text>
              </div>
            )
          }}
        />
      </Card>

      {/* 章节编辑模态框 */}
      <Modal
        title={editingChapter ? '编辑章节' : '新建章节'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={800}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSaveChapter}
          initialValues={{
            title: '',
            content: '',
            chapterNumber: 1
          }}
        >
          <Form.Item
            name="title"
            label="章节标题"
            rules={[
              { required: true, message: '请输入章节标题' },
              { max: 100, message: '标题长度不能超过100个字符' }
            ]}
          >
            <Input placeholder="请输入章节标题" />
          </Form.Item>

          <Form.Item
            name="chapterNumber"
            label="章节序号"
            rules={[
              { required: true, message: '请输入章节序号' },
              { type: 'number', min: 1, message: '章节序号必须大于0' }
            ]}
          >
            <Input type="number" placeholder="请输入章节序号" />
          </Form.Item>

          <Form.Item
            name="content"
            label="章节内容"
            rules={[
              { required: true, message: '请输入章节内容' }
            ]}
          >
            <Input.TextArea
              placeholder="请输入章节内容..."
              rows={10}
              showCount
              maxLength={50000}
            />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {editingChapter ? '更新' : '创建'}
              </Button>
              <Button onClick={() => setModalVisible(false)}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ChapterManager 