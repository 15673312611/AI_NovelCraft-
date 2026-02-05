import React, { useEffect, useState } from 'react'
import { Button, Input, Empty, Spin, Modal, message, Tag, Form } from 'antd'
import { PlusOutlined, SearchOutlined, EditOutlined, DeleteOutlined, FileTextOutlined, DownOutlined, ClockCircleOutlined, NodeIndexOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { AppDispatch, RootState } from '@/store'
import { fetchNovels, deleteNovel, updateNovel } from '@/store/slices/novelSlice'
import NovelCardIcon from '@/components/common/NovelCardIcon'
import GraphDataModal from '@/components/graph/GraphDataModal'
import novelVolumeService, { NovelVolume } from '@/services/novelVolumeService'
import './NovelListPage.modern.css'

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
  const [isFirstLoad, setIsFirstLoad] = useState(true) // 新增：是否首次加载
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editingNovel, setEditingNovel] = useState<any | null>(null)
  const [editForm] = Form.useForm()

  const [graphModalVisible, setGraphModalVisible] = useState(false)
  const [selectedNovelForGraph, setSelectedNovelForGraph] = useState<{ id: number; title: string } | null>(null)


  // 初始加载
  useEffect(() => {
    const init = async () => {
      try {
        await dispatch(fetchNovels({ page: 0, size: 40, append: false })).unwrap()
      } finally {
        setIsFirstLoad(false)
      }
    }
    init()
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
      message.error(error?.message || '跳转失败，请重试')
      // 出错时跳转到卷管理页面作为兜底
      navigate(`/novels/${novel.id}/volumes`)
    }
  }

  return (
    <div className="modern-novel-list">
      <div className="page-bg-decoration"></div>
      
      {/* 顶部标题区 */}
      <div className="page-header-modern">
        <div className="page-title-group">
          <h1>我的作品</h1>
          <p>这里是你创造的所有世界，继续书写传奇。</p>
        </div>
      </div>

      {/* 工具栏：搜索、排序、新建 */}
      <div className="toolbar-container">
        <div className="search-section">
          <Input
            placeholder="搜索你的故事..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            prefix={<SearchOutlined style={{ color: '#94a3b8', fontSize: 18 }} />}
            allowClear
            className="modern-search-input-large"
          />
        </div>

        <div className="actions-section">
          {/* 排序下拉（使用原生 select 覆盖，避免自定义下拉在某些浏览器/布局下无法展开） */}
          <div className="sort-native-wrapper">
            <select
              className="sort-native-select"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              aria-label="排序方式"
            >
              {sortOptions.map(({ key, label }) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>

            <div className="sort-trigger-modern">
              <span className="sort-icon">
                {sortBy === 'updatedAt' || sortBy === 'createdAt' ? <ClockCircleOutlined /> : <NodeIndexOutlined />}
              </span>
              <span className="sort-label">{sortOptions.find(o => o.key === sortBy)?.label || '排序'}</span>
              <DownOutlined className="dropdown-arrow" style={{ fontSize: 10, marginLeft: 4 }} />
            </div>
          </div>

          {/* 新建按钮 */}
          <Button 
            type="primary" 
            className="create-novel-btn-modern"
            icon={<PlusOutlined />} 
            onClick={() => navigate('/novels/new')}
          >
            新建小说
          </Button>
        </div>
      </div>

      {/* 小说列表 */}
      {loading || isFirstLoad ? (
        <div className="novels-grid-modern">
          {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
            <div key={i} className="novel-card-modern" style={{ pointerEvents: 'none', border: '1px solid #f1f5f9', boxShadow: 'none' }}>
              <div className="card-cover-area" style={{ background: '#f8fafc' }}>
                 {/* 骨架屏占位 */}
              </div>
              <div className="card-content">
                <div style={{ height: 24, background: '#f1f5f9', marginBottom: 12, borderRadius: 6, width: '70%' }}></div>
                <div style={{ height: 16, background: '#f1f5f9', marginBottom: 8, borderRadius: 4 }}></div>
                <div style={{ height: 16, background: '#f1f5f9', width: '60%', borderRadius: 4 }}></div>
                <div style={{ marginTop: 'auto', paddingTop: 12, borderTop: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between' }}>
                   <div style={{ width: 60, height: 16, background: '#f1f5f9', borderRadius: 4 }}></div>
                   <div style={{ width: 40, height: 16, background: '#f1f5f9', borderRadius: 4 }}></div>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : filteredAndSortedNovels.length > 0 ? (
        <div className="novels-grid-modern">
          {filteredAndSortedNovels.map(novel => (
            <div
              key={novel.id}
              className="novel-card-modern"
              onClick={(e) => {
                e.preventDefault()
                handleStartWriting(novel)
              }}
            >
              <div className="card-cover-area">
                {/* 状态标签 */}
                {novel.creationStage === 'WRITING_IN_PROGRESS' && (
                  <div className="status-badge in-progress">进行中</div>
                )}
                {novel.creationStage === 'WRITING_COMPLETED' && (
                  <div className="status-badge completed">已完成</div>
                )}
                {(novel.creationStage === 'OUTLINE_PENDING' || novel.creationStage === 'OUTLINE_CONFIRMED') && (
                  <div className="status-badge draft">草稿</div>
                )}
                
                <span className="novel-icon-placeholder" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <NovelCardIcon />
                </span>
              </div>

              {/* 悬浮操作栏 */}
              <div className="card-hover-actions" onClick={(e) => e.stopPropagation()}>
                <div 
                  className="action-btn-mini" 
                  onClick={(e) => { 
                    e.preventDefault()
                    e.stopPropagation()
                    console.log('编辑按钮被点击', novel)
                    setEditingNovel(novel)
                    editForm.setFieldsValue({ title: novel.title, description: novel.description })
                    setEditModalVisible(true)
                  }}
                  title="编辑信息"
                >
                  <EditOutlined />
                </div>
                <div 
                  className="action-btn-mini" 
                  onClick={(e) => { 
                    e.preventDefault()
                    e.stopPropagation()
                    handleViewGraph(novel.id, novel.title)
                  }}
                  title="查看图谱"
                >
                  <NodeIndexOutlined />
                </div>
                <div 
                  className="action-btn-mini danger" 
                  onClick={(e) => { 
                    e.preventDefault()
                    e.stopPropagation()
                    handleDeleteNovel(novel.id, novel.title)
                  }}
                  title="删除小说"
                >
                  <DeleteOutlined />
                </div>
              </div>

              <div className="card-content">
                <h3 className="card-title">{novel.title}</h3>
                <p className="card-desc">{novel.description || '暂无简介，点击编辑添加...'}</p>

                <div className="card-meta">
                  <div className="meta-item">
                    <ClockCircleOutlined style={{ fontSize: 11 }} />
                    {new Date(novel.updatedAt || novel.createdAt).toLocaleDateString()}
                  </div>
                  {novel.wordCount > 0 && (
                    <Tag style={{ margin: 0, border: 'none', background: '#f1f5f9', color: '#64748b', fontSize: '11px' }}>
                      {(novel.wordCount / 1000).toFixed(1)}k字
                    </Tag>
                  )}
                </div>
                
                {/* 进度条 */}
                {novel.wordCount > 0 && (
                  <div className="progress-bar-wrapper">
                    <div className="progress-label">
                      <span>创作进度</span>
                      <span>{Math.min(100, Math.floor((novel.wordCount / 100000) * 100))}%</span>
                    </div>
                    <div className="progress-bar">
                      <div 
                        className="progress-fill" 
                        style={{ width: `${Math.min(100, (novel.wordCount / 100000) * 100)}%` }}
                      />
                    </div>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        // 空状态
        searchQuery ? (
          <div style={{ textAlign: 'center', padding: '60px 0', color: '#94a3b8' }}>
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到相关小说" />
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <Empty
              image="https://gw.alipayobjects.com/zos/antfincdn/ZHrcdLPrvN/empty.svg"
              imageStyle={{ height: 100 }}
              description={
                <div style={{ color: '#64748b' }}>
                  <p style={{ fontSize: 16, fontWeight: 500, marginBottom: 8 }}>还没有开始创作</p>
                  <p>创建一个新的小说，开始你的故事之旅</p>
                </div>
              }
            >
              <Button type="primary" onClick={() => navigate('/novels/new')}>立即创建</Button>
            </Empty>
          </div>
        )
      )}

      {/* 加载更多 */}
      {filteredAndSortedNovels.length > 0 && (
        <div style={{ textAlign: 'center', marginTop: 40, marginBottom: 20 }}>
           {isLoadingMore ? <Spin /> : hasMore && (
             <span style={{ color: '#cbd5e1', fontSize: 12 }}>滚动加载更多</span>
           )}
        </div>
      )}

      {/* 编辑小说元数据弹窗 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <FileTextOutlined style={{ color: '#3b82f6', fontSize: '18px' }} />
            <span style={{ fontWeight: 600 }}>编辑小说信息</span>
          </div>
        }
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
        okText="保存修改"
        cancelText="取消"
        okButtonProps={{ 
          style: { 
            background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
            border: 'none',
            borderRadius: '8px',
            fontWeight: 600,
            height: '40px',
            paddingLeft: '24px',
            paddingRight: '24px'
          } 
        }}
        cancelButtonProps={{
          style: {
            borderRadius: '8px',
            height: '40px'
          }
        }}
        width={520}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: '24px' }}>
          <Form.Item 
            name="title" 
            label="小说名称"
            rules={[{ required: true, message: '请输入小说名称' }]}
          >
            <Input 
              maxLength={80} 
              placeholder="请输入小说名称" 
              style={{ borderRadius: '8px', height: '40px' }}
              showCount
            />
          </Form.Item>
          <Form.Item 
            name="description" 
            label="小说简介"
          >
            <Input.TextArea 
              rows={4} 
              maxLength={1000} 
              placeholder="请输入小说简介..." 
              showCount
              style={{ borderRadius: '8px', resize: 'none' }}
            />
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

