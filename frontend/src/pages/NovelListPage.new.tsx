import React, { useEffect, useRef, useState } from 'react'
import { Row, Col, Button, Input, Empty, Spin, Modal, message, Tag, Form } from 'antd'
import { PlusOutlined, SearchOutlined, EditOutlined, DeleteOutlined, FileTextOutlined, DownOutlined, CheckOutlined, ClockCircleOutlined, NodeIndexOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { AppDispatch, RootState } from '@/store'
import { fetchNovels, deleteNovel, updateNovel } from '@/store/slices/novelSlice'
import EnhancedEmptyState from '@/components/common/EnhancedEmptyState'
import EnhancedStatsCard from '@/components/common/EnhancedStatsCard'
import PageBackground from '@/components/common/PageBackground'
import GraphDataModal from '@/components/graph/GraphDataModal'
import novelVolumeService, { NovelVolume } from '@/services/novelVolumeService'
import './NovelListPage.new.css'

const sortOptions = [
  { key: 'updatedAt', label: '最近更新' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'title', label: '书名' },
  { key: 'wordCount', label: '字数' }
]

const NovelListPage: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { novels, loading, hasMore, currentPage } = useSelector((state: RootState) => state.novel)
  
  const [searchQuery, setSearchQuery] = useState('')
  const [sortBy, setSortBy] = useState<string>('updatedAt') // 排序方式：updatedAt, createdAt, title, wordCount
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editingNovel, setEditingNovel] = useState<any | null>(null)
  const [editForm] = Form.useForm()

  const [sortMenuOpen, setSortMenuOpen] = useState(false)
  const sortDropdownRef = useRef<HTMLDivElement | null>(null)
  
  const [graphModalVisible, setGraphModalVisible] = useState(false)
  const [selectedNovelForGraph, setSelectedNovelForGraph] = useState<{ id: number; title: string } | null>(null)

  const handleToggleSortMenu = () => {
    setSortMenuOpen((prev) => !prev)
  }

  const handleSelectSort = (key: string) => {
    setSortBy(key)
    setSortMenuOpen(false)
  }

  useEffect(() => {
    if (!sortMenuOpen) return

    const handleClickOutside = (event: MouseEvent) => {
      if (sortDropdownRef.current && !sortDropdownRef.current.contains(event.target as Node)) {
        setSortMenuOpen(false)
      }
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setSortMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [sortMenuOpen])

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

  // 快捷键支持
  useEffect(() => {
    const handleKeyPress = (e: KeyboardEvent) => {
      // Ctrl/Cmd + N 创建新小说
      if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
        e.preventDefault()
        navigate('/novels/new')
      }
    }

    window.addEventListener('keydown', handleKeyPress)
    return () => window.removeEventListener('keydown', handleKeyPress)
  }, [navigate])

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

  // 筛选和排序
  const filteredAndSortedNovels = novelsArray
    .filter(novel => {
      const matchesSearch = novel.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           novel.description.toLowerCase().includes(searchQuery.toLowerCase())
      return matchesSearch
    })
    .sort((a, b) => {
      switch (sortBy) {
        case 'updatedAt':
          return new Date(b.updatedAt || b.createdAt).getTime() - new Date(a.updatedAt || a.createdAt).getTime()
        case 'createdAt':
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        case 'title':
          return a.title.localeCompare(b.title, 'zh-CN')
        case 'wordCount':
          return (b.wordCount || 0) - (a.wordCount || 0)
        default:
          return 0
      }
    })

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
  
  const handleViewGraph = (novelId: number, novelTitle: string) => {
    setSelectedNovelForGraph({ id: novelId, title: novelTitle })
    setGraphModalVisible(true)
  }

  /**
   * 智能跳转到写作页面
   * 根据小说的创作阶段(creationStage)和卷的状态，决定跳转到哪个页面
   */
  const handleStartWriting = async (novel: any) => {
    try {
      const novelId = novel.id
      const creationStage = novel.creationStage || 'OUTLINE_PENDING'

      console.log(`[handleStartWriting] 小说ID: ${novelId}, 创作阶段: ${creationStage}`)

      // 1. 如果小说刚创建，还没有生成大纲 → 跳转到卷管理页面（会引导生成大纲）
      if (creationStage === 'OUTLINE_PENDING') {
        message.info('请先生成小说大纲')
        navigate(`/novels/${novelId}/volumes`)
        return
      }

      // 2. 如果已生成大纲，但还没有生成卷 → 跳转到卷管理页面（会引导生成卷）
      if (creationStage === 'OUTLINE_CONFIRMED') {
        message.info('请先生成卷规划')
        navigate(`/novels/${novelId}/volumes`)
        return
      }

      // 3. 如果已生成卷，检查卷的详细大纲状态
      if (creationStage === 'VOLUMES_GENERATED' ||
          creationStage === 'DETAILED_OUTLINE_GENERATED' ||
          creationStage === 'WRITING_IN_PROGRESS' ||
          creationStage === 'WRITING_COMPLETED') {

        // 获取该小说的卷列表
        const volumes: NovelVolume[] = await novelVolumeService.getVolumesByNovelId(String(novelId))

        if (!volumes || volumes.length === 0) {
          message.warning('未找到卷信息，请先生成卷规划')
          navigate(`/novels/${novelId}/volumes`)
          return
        }

        // 查找正在进行中的卷，或者直接使用第一个卷（因为卷已经从大纲拆分出来，包含了必要的信息）
        const byInProgress = volumes.find(v => v.status === 'IN_PROGRESS')
        const target = byInProgress || volumes[0] || null

        if (target && target.id) {
          // 找到可以写作的卷，跳转到写作工作室
          console.log(`[handleStartWriting] 跳转到写作工作室，卷ID: ${target.id}`)
          navigate(`/novels/${novelId}/writing-studio`, {
            state: { initialVolumeId: target.id, sessionData: null }
          })
          return
        }

        // 如果没有找到卷，跳转到卷管理页面
        message.warning('未找到可用的卷')
        navigate(`/novels/${novelId}/volumes`)
        return
      }

      // 默认情况：跳转到卷管理页面
      navigate(`/novels/${novelId}/volumes`)

    } catch (error: any) {
      console.error('[handleStartWriting] 错误:', error)
      message.error('跳转失败，请重试')
      // 出错时跳转到卷管理页面作为兜底
      navigate(`/novels/${novel.id}/volumes`)
    }
  }

  return (
    <div className="modern-novel-list">
      {/* 装饰性背景 */}
      <PageBackground />
      
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

        {/* 统计卡片 - 使用增强版本 */}
        <EnhancedStatsCard 
          totalNovels={totalNovels}
          totalChapters={totalChapters}
          totalWords={totalWords}
        />
      </div>

      {/* 搜索和排序 */}
      <div className="filters-section">
        <Input
          placeholder="搜索小说标题或描述..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          prefix={<SearchOutlined />}
          allowClear
          size="large"
          className="search-input"
        />

        <div className="sort-dropdown-wrapper" ref={sortDropdownRef}>
          <Button className="sort-button" onClick={handleToggleSortMenu}>
            <span>排序方式</span>
            <DownOutlined />
          </Button>

          {sortMenuOpen && (
            <div className="sort-dropdown">
              {sortOptions.map(({ key, label }) => (
                <button
                  key={key}
                  className={`sort-dropdown-item ${sortBy === key ? 'selected' : ''}`}
                  onClick={() => handleSelectSort(key)}
                >
                  {sortBy === key ? <CheckOutlined className="sort-dropdown-check" /> : <span className="sort-dropdown-placeholder" />}
                  <span>{label}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 小说列表 */}
      {loading ? (
        <div className="loading-state">
          <Spin size="large" />
          <p>加载中...</p>
        </div>
      ) : filteredAndSortedNovels.length > 0 ? (
        <Row gutter={[24, 24]} className="novels-grid">
          {filteredAndSortedNovels.map(novel => (
            <Col xs={24} sm={12} lg={8} xl={6} key={novel.id}>
              <div
                className="novel-card"
                onClick={(e) => {
                  // 点击卡片时使用智能跳转
                  e.preventDefault()
                  handleStartWriting(novel)
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
                  <div className="stat-item stat-item-full">
                    <span className="stat-label">
                      <ClockCircleOutlined style={{ fontSize: 14, marginRight: 6 }} />
                      最近更新
                    </span>
                    <span className="stat-time">
                      {new Date(novel.updatedAt || novel.createdAt).toLocaleString('zh-CN', {
                        year: 'numeric',
                        month: '2-digit',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit'
                      })}
                    </span>
                  </div>
                </div>

                <div className="card-actions">
                  <Button
                    type="primary"
                    icon={<EditOutlined />}
                    onClick={(e) => {
                      e.stopPropagation()
                      handleStartWriting(novel)
                    }}
                    block
                  >
                    开始写作
                  </Button>
                  <div className="action-buttons">
                    <Button
                      icon={<NodeIndexOutlined />}
                      onClick={(e) => { 
                        e.stopPropagation()
                        handleViewGraph(novel.id, novel.title)
                      }}
                    >
                      图谱
                    </Button>
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
        // 空状态 - 区分搜索和无数据
        searchQuery ? (
          <div className="empty-state">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={<p className="empty-text">没有找到匹配的小说</p>}
            />
          </div>
        ) : (
          <EnhancedEmptyState onCreateNovel={() => navigate('/novels/new')} />
        )
      )}

      {/* 加载更多提示 */}
      {filteredAndSortedNovels.length > 0 && (
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
      
      {/* 图谱数据弹窗 */}
      <GraphDataModal
        visible={graphModalVisible}
        novelId={selectedNovelForGraph?.id || null}
        novelTitle={selectedNovelForGraph?.title || ''}
        onClose={() => {
          setGraphModalVisible(false)
          setSelectedNovelForGraph(null)
        }}
      />
    </div>
  )
}

export default NovelListPage

