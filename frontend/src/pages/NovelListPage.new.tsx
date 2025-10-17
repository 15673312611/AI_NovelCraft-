import React, { useEffect, useState } from 'react'
import { Row, Col, Card, Button, Input, Select, Space, Empty, Spin, Modal, message, Tag, Statistic, Form } from 'antd'
import { PlusOutlined, SearchOutlined, EditOutlined, DeleteOutlined, BookOutlined, FileTextOutlined } from '@ant-design/icons'
import novelVolumeService, { NovelVolume } from '@/services/novelVolumeService'
import { useNavigate } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { AppDispatch, RootState } from '@/store'
import { fetchNovels, deleteNovel, updateNovel } from '@/store/slices/novelSlice'
import './NovelListPage.new.css'

const { Search } = Input
const { Option } = Select

const NovelListPage: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { novels, loading, hasMore, currentPage } = useSelector((state: RootState) => state.novel)
  
  const [searchQuery, setSearchQuery] = useState('')
  const [genreFilter, setGenreFilter] = useState<string>('all')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editingNovel, setEditingNovel] = useState<any | null>(null)
  const [editForm] = Form.useForm()

  // 初始加载
  useEffect(() => {
    dispatch(fetchNovels({ page: 0, size: 40, append: false }))
  }, [dispatch])

  // 无限滚动：监听滚动事件
  useEffect(() => {
    const handleScroll = () => {
      // 检查是否滚动到底部
      const scrollTop = window.pageYOffset || document.documentElement.scrollTop
      const scrollHeight = document.documentElement.scrollHeight
      const clientHeight = document.documentElement.clientHeight
      
      // 距离底部 200px 时触发加载
      if (scrollHeight - scrollTop - clientHeight < 200 && hasMore && !loading && !isLoadingMore) {
        loadMore()
      }
    }

    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [hasMore, loading, isLoadingMore, currentPage])

  // 加载更多数据
  const loadMore = async () => {
    if (isLoadingMore || !hasMore) return
    
    setIsLoadingMore(true)
    try {
      await dispatch(fetchNovels({ 
        page: currentPage + 1, 
        size: 40, 
        append: true 
      })).unwrap()
    } catch (error) {
      console.error('加载更多失败:', error)
    } finally {
      setIsLoadingMore(false)
    }
  }

  const novelsArray = Array.isArray(novels) ? novels : []

  const totalNovels = novelsArray.length
  const totalChapters = novelsArray.reduce((sum, n) => sum + (n.chapterCount || 0), 0)
  const totalWords = novelsArray.reduce((sum, n) => sum + (n.wordCount || 0), 0)

  const filteredNovels = novelsArray.filter(novel => {
    const matchesSearch = novel.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         novel.description.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesGenre = genreFilter === 'all' || novel.genre === genreFilter
    const matchesStatus = statusFilter === 'all' || novel.status === statusFilter

    return matchesSearch && matchesGenre && matchesStatus
  })

  // 根据小说ID直接进入对应卷的写作页
  const enterWritingDirectly = async (novelId: number) => {
    try {
      const volumes: NovelVolume[] = await novelVolumeService.getVolumesByNovelId(String(novelId))
      if (!volumes || volumes.length === 0) {
        navigate(`/novels/${novelId}/volumes`)
        return
      }
      // 仅当卷已进入写作或具备详细大纲时，才允许直达写作页
      const byInProgress = volumes.find(v => v.status === 'IN_PROGRESS')
      const byDetailed = volumes.find((v: any) => v?.contentOutline && v.contentOutline.length >= 100)
      const target = byInProgress || byDetailed || null
      if (target && target.id) {
        navigate(`/novels/${novelId}/volumes/${target.id}/writing`, {
          state: { initialVolumeId: target.id, sessionData: null }
        })
        return
      }
      navigate(`/novels/${novelId}/volumes`)
    } catch (e) {
      console.error('进入写作页失败:', e)
      navigate(`/novels/${novelId}/volumes`)
    }
  }

  const genres = Array.from(new Set(novelsArray.map(novel => novel.genre)))
  const statuses = Array.from(new Set(novelsArray.map(novel => novel.status)))

  const handleDeleteNovel = (novelId: number, novelTitle: string) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除小说「${novelTitle}」吗？删除后无法恢复。`,
      okText: '确定删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await dispatch(deleteNovel(novelId)).unwrap()
          message.success('小说删除成功')
          // 删除成功后重新加载列表，从第一页开始
          dispatch(fetchNovels({ page: 0, size: 40, append: false }))
        } catch (error: any) {
          console.error('删除失败:', error)
          message.error(error.message || '删除失败，请重试')
        }
      }
    })
  }

  return (
    <div className="modern-novel-list">
      {/* 顶部统计区 */}
      <div className="page-header">
        <div className="header-content">
          <div>
            <h1 className="page-title">我的作品</h1>
            <p className="page-subtitle">创作你的故事世界</p>
          </div>
          <Button 
            type="primary" 
            size="large" 
            icon={<PlusOutlined />} 
            onClick={() => navigate('/novels/new')}
            className="create-button"
          >
            新建小说
          </Button>
        </div>

        {/* 统计卡片 */}
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-icon">
              <BookOutlined />
            </div>
            <div className="stat-content">
              <div className="stat-value">{totalNovels}</div>
              <div className="stat-label">作品数</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">
              <FileTextOutlined />
            </div>
            <div className="stat-content">
              <div className="stat-value">{totalChapters}</div>
              <div className="stat-label">章节数</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">
              <EditOutlined />
            </div>
            <div className="stat-content">
              <div className="stat-value">{(totalWords / 10000).toFixed(1)}万</div>
              <div className="stat-label">总字数</div>
            </div>
          </div>
        </div>
      </div>

      {/* 搜索和筛选 */}
      <div className="filters-section">
        <Search
          placeholder="搜索小说标题或描述..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          prefix={<SearchOutlined />}
          allowClear
          size="large"
          className="search-input"
        />
        
        <div className="filter-group">
          <Select
            value={genreFilter}
            onChange={setGenreFilter}
            placeholder="类型"
            size="large"
            style={{ width: 140 }}
          >
            <Option value="all">所有类型</Option>
            {genres.map(genre => (
              <Option key={genre} value={genre}>{genre}</Option>
            ))}
          </Select>

          <Select
            value={statusFilter}
            onChange={setStatusFilter}
            placeholder="状态"
            size="large"
            style={{ width: 140 }}
          >
            <Option value="all">所有状态</Option>
            {statuses.map(status => (
              <Option key={status} value={status}>{status}</Option>
            ))}
          </Select>
        </div>
      </div>

      {/* 小说列表 */}
      {loading ? (
        <div className="loading-state">
          <Spin size="large" />
          <p>加载中...</p>
        </div>
      ) : filteredNovels.length > 0 ? (
        <Row gutter={[24, 24]} className="novels-grid">
          {filteredNovels.map(novel => (
            <Col xs={24} sm={12} lg={8} xl={6} key={novel.id}>
              <div
                className="novel-card"
                onClick={() => {
                  // 直接尝试进入写作页，失败再回退到卷/编辑
                  enterWritingDirectly(novel.id)
                }}
              >
                <div className="card-header">
                  <h3 className="card-title">{novel.title}</h3>
                  <div className="card-tags">
                    {novel.genre && (
                      <Tag className="genre-tag">{novel.genre}</Tag>
                    )}
                  </div>
                </div>

                <p className="card-description">{novel.description}</p>

                <div className="card-stats">
                  <div className="stat-item">
                    <span className="stat-number">{novel.chapterCount || 0}</span>
                    <span className="stat-text">章节</span>
                  </div>
                  <div className="stat-item">
                    <span className="stat-number">{((novel.wordCount || 0) / 10000).toFixed(1)}万</span>
                    <span className="stat-text">字数</span>
                  </div>
                </div>

                <div className="card-actions">
                  <Button
                    type="primary"
                    icon={<EditOutlined />}
                    onClick={(e) => { 
                      e.stopPropagation()
                      enterWritingDirectly(novel.id)
                    }}
                    block
                  >
                    开始写作
                  </Button>
                  <div className="action-buttons">
                    <Button
                      icon={<FileTextOutlined />}
                      onClick={(e) => { 
                        e.stopPropagation()
                        setEditingNovel(novel as any)
                        editForm.setFieldsValue({ title: (novel as any).title, description: (novel as any).description })
                        setEditModalVisible(true)
                      }}
                    >
                      编辑
                    </Button>
                    <Button
                      danger
                      icon={<DeleteOutlined />}
                      onClick={(e) => { 
                        e.stopPropagation()
                        handleDeleteNovel(novel.id, novel.title)
                      }}
                    >
                      删除
                    </Button>
                  </div>
                </div>
              </div>
            </Col>
          ))}
        </Row>
      ) : (
        <div className="empty-state">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <p className="empty-text">
                {searchQuery || genreFilter !== 'all' || statusFilter !== 'all' 
                  ? '没有找到匹配的小说' 
                  : '还没有创作任何小说'}
              </p>
            }
          />
        </div>
      )}

      {/* 加载更多提示 */}
      {filteredNovels.length > 0 && (
        <div style={{ 
          textAlign: 'center', 
          padding: '32px 0',
          marginTop: '24px'
        }}>
          {isLoadingMore ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px' }}>
              <Spin />
              <span style={{ color: '#64748b', fontSize: '14px' }}>加载中...</span>
            </div>
          ) : hasMore ? (
            <div style={{ color: '#94a3b8', fontSize: '13px' }}>
              向下滚动加载更多
            </div>
          ) : novelsArray.length > 0 ? (
            <div style={{ 
              color: '#94a3b8', 
              fontSize: '13px',
              padding: '12px 24px',
              background: '#f8fafc',
              borderRadius: '8px',
              display: 'inline-block'
            }}>
              已加载全部 {novelsArray.length} 部作品
            </div>
          ) : null}
        </div>
      )}

      {/* 编辑小说元数据弹窗 */}
      <Modal
        title="编辑小说"
        open={editModalVisible}
        onCancel={() => { setEditModalVisible(false); setEditingNovel(null); editForm.resetFields() }}
        onOk={async () => {
          try {
            const values = await editForm.validateFields()
            if (!editingNovel) return
            await dispatch(updateNovel({ id: editingNovel.id, data: { title: values.title, description: values.description } })).unwrap()
            message.success('保存成功')
            setEditModalVisible(false)
            setEditingNovel(null)
            editForm.resetFields()
          } catch (err: any) {
            if (err && err.message) message.error(err.message)
          }
        }}
        okText="保存"
        cancelText="取消"
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="title" label="小说名称" rules={[{ required: true, message: '请输入小说名称' }]}>
            <Input maxLength={80} placeholder="输入名称" />
          </Form.Item>
          <Form.Item name="description" label="小说描述">
            <Input.TextArea rows={4} maxLength={500} placeholder="输入描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default NovelListPage

