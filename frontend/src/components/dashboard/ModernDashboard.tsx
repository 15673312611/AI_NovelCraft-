import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { RootState, AppDispatch } from '@/store'
import { fetchNovels } from '@/store/slices/novelSlice'
import novelVolumeService, { NovelVolume } from '@/services/novelVolumeService'
import api from '@/services/api'
import { 
  PlusIcon, 
  ClockIcon,
  BookOpenIcon,
  ArrowRightIcon,
  BoltIcon,
  FilmIcon,
  SunIcon,
  MoonIcon,
  SparklesIcon,
  ChatBubbleLeftRightIcon,
  DocumentTextIcon
} from '@heroicons/react/24/outline'
import './ModernDashboard.css'

const ModernDashboard: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { isAuthenticated, user } = useSelector((state: RootState) => state.auth)
  const { novels } = useSelector((state: RootState) => state.novel)
  const [credits, setCredits] = useState<number>(0)
  const [totalWords, setTotalWords] = useState<number>(0)
  const [randomTip, setRandomTip] = useState('')

  const tips = [
    "灵感枯竭？试试点击 AI 助手进行头脑风暴。",
    "每天坚持写作 500 字，一年就是一部长篇。",
    "好的大纲是成功的一半，善用章纲功能。",
    "记得定期回顾你的人物设定，保持性格一致性。",
    "你可以随时修改大纲，AI 会根据最新的大纲生成内容。",
    "在写作页面，你可以选中一段文字让 AI 帮你润色。"
  ]

  useEffect(() => {
    if (isAuthenticated) {
      dispatch(fetchNovels({ page: 0, size: 40, append: false }))
      fetchCredits()
      fetchStatistics()
      setRandomTip(tips[Math.floor(Math.random() * tips.length)])
    }
  }, [dispatch, isAuthenticated])

  const fetchCredits = async () => {
    try {
      const res: any = await api.get('/credits/balance')
      if (res.data && res.data.balance !== undefined) {
        setCredits(res.data.balance)
      }
    } catch (error) {
      console.error('获取字数点失败:', error)
    }
  }

  const fetchStatistics = async () => {
    try {
      const res: any = await api.get('/auth/statistics')
      if (res.data && res.data.totalWords !== undefined) {
        setTotalWords(res.data.totalWords)
      }
    } catch (error) {
      console.error('获取统计信息失败:', error)
    }
  }

  // 直达写作逻辑
  const enterWritingDirectly = async (novelId: number) => {
    try {
      const volumes: NovelVolume[] = await novelVolumeService.getVolumesByNovelId(String(novelId))
      if (!volumes || volumes.length === 0) {
        navigate(`/novels/${novelId}/volumes`)
        return
      }
      const byInProgress = volumes.find(v => v.status === 'IN_PROGRESS')
      // @ts-ignore
      const byDetailed = volumes.find((v: any) => v?.contentOutline && v.contentOutline.length >= 100)
      const target = byInProgress || byDetailed || null
      if (target && target.id) {
        navigate(`/novels/${novelId}/writing-studio`, {
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
  
  const sortedNovels = (Array.isArray(novels) ? novels : [])
    .slice()
    .sort((a, b) => new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime())

  const lastEditedNovel = sortedNovels[0]
  const otherNovels = sortedNovels.slice(1, 5)

  if (!isAuthenticated) return null

  const getGreeting = () => {
    const hour = new Date().getHours()
    if (hour < 5) return { text: '夜深了', icon: <MoonIcon style={{ width: 32, color: '#818cf8' }} /> }
    if (hour < 11) return { text: '早上好', icon: <SunIcon style={{ width: 32, color: '#fbbf24' }} /> }
    if (hour < 13) return { text: '中午好', icon: <SunIcon style={{ width: 32, color: '#f59e0b' }} /> }
    if (hour < 18) return { text: '下午好', icon: <SunIcon style={{ width: 32, color: '#f59e0b' }} /> }
    return { text: '晚上好', icon: <MoonIcon style={{ width: 32, color: '#818cf8' }} /> }
  }

  const greeting = getGreeting()

  const formatDate = (dateString?: string) => {
    if (!dateString) return ''
    const date = new Date(dateString)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))
    
    if (days === 0) return '今天'
    if (days === 1) return '昨天'
    if (days < 7) return `${days}天前`
    return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
  }

  return (
    <div className="dashboard-container">
      {/* 1. Welcome Section */}
      <section className="welcome-section">
        <div className="welcome-header">
          <div className="greeting-wrapper">
            {greeting.icon}
            <div>
              <h1>{greeting.text}，{user?.username}</h1>
              <p className="daily-tip">💡 每日贴士：{randomTip}</p>
            </div>
          </div>
          <div className="header-stats-group">
            <div className="stat-card">
              <div className="stat-icon purple">
                <DocumentTextIcon />
              </div>
              <div className="stat-info">
                <span className="stat-label">总字数</span>
                <span className="stat-number">{(totalWords / 10000).toFixed(1)}<small>万</small></span>
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-icon yellow">
                <BoltIcon />
              </div>
              <div className="stat-info">
                <span className="stat-label">灵感点</span>
                <span className="stat-number">{credits}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 2. Hero Workspace - The "Focus" Area */}
      <section className="hero-section">
        {lastEditedNovel ? (
          <div className="focus-card">
            <div className="focus-bg-pattern"></div>
            <div className="focus-content">
              <div className="focus-tag">
                <SparklesIcon className="w-4 h-4" /> 正在创作
              </div>
              <h2 className="focus-title">{lastEditedNovel.title}</h2>
              <div className="focus-meta">
                <span><ClockIcon className="w-4 h-4 inline mr-1" /> 最近更新于 {formatDate(lastEditedNovel.updatedAt)}</span>
                <span className="divider">•</span>
                <span>{lastEditedNovel.wordCount || 0} 字</span>
              </div>
              <div className="focus-actions">
                <button className="action-btn primary" onClick={() => enterWritingDirectly(lastEditedNovel.id as any)}>
                  继续写作 <ArrowRightIcon className="w-5 h-5 ml-2" />
                </button>
                <button className="action-btn secondary" onClick={() => navigate(`/novels/${lastEditedNovel.id}/volumes`)}>
                  管理大纲
                </button>
              </div>
            </div>
            <div className="focus-visual">
              {/* Abstract Visual Representation of a Book/Paper */}
              <div className="book-stack">
                <div className="book-layer layer-1"></div>
                <div className="book-layer layer-2"></div>
                <div className="book-layer layer-3">
                   <div className="book-cover-content">
                      <BookOpenIcon className="book-icon" />
                   </div>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="focus-card empty">
            <div className="focus-bg-pattern"></div>
            <div className="empty-content">
              <div className="empty-icon-wrapper">
                <SparklesIcon />
              </div>
              <h2>开始你的第一部作品</h2>
              <p>万事开头难，但每一部伟大的小说都始于第一个字。<br/>让灵感汇聚成河，开启你的创作之旅。</p>
              <button className="action-btn primary large-btn" onClick={() => navigate('/novels/new')}>
                <PlusIcon /> 创建新书
              </button>
            </div>
          </div>
        )}
      </section>

      <div className="dashboard-columns">
        {/* 3. Left Column: Quick Tools */}
        <div className="dashboard-left">
          <div className="section-header">
            <h3>创意工坊</h3>
          </div>
          <div className="tools-grid-new">
            <div className="new-tool-card" onClick={() => navigate('/ai-chat')}>
              <div className="tool-icon-box blue"><ChatBubbleLeftRightIcon /></div>
              <div className="tool-info">
                <h4>AI 助手</h4>
                <p>头脑风暴与问答</p>
              </div>
            </div>
            <div className="new-tool-card" onClick={() => navigate('/ai-generators')}>
              <div className="tool-icon-box purple"><SparklesIcon /></div>
              <div className="tool-info">
                <h4>灵感生成</h4>
                <p>角色、名字、设定</p>
              </div>
            </div>
            <div className="new-tool-card" onClick={() => navigate('/short-stories')}>
              <div className="tool-icon-box orange"><BookOpenIcon /></div>
              <div className="tool-info">
                <h4>短篇工厂</h4>
                <p>快速生成短篇故事</p>
              </div>
            </div>
            <div className="new-tool-card" onClick={() => navigate('/video-scripts')}>
              <div className="tool-icon-box pink"><FilmIcon /></div>
              <div className="tool-info">
                <h4>剧本工厂</h4>
                <p>短视频剧本创作</p>
              </div>
            </div>
          </div>
        </div>

        {/* 4. Right Column: Recent Projects List */}
        <div className="dashboard-right">
          <div className="section-header">
            <h3>最近项目</h3>
            <button className="link-btn" onClick={() => navigate('/novels')}>全部</button>
          </div>
          <div className="recent-list-new">
            {otherNovels.map(novel => (
              <div className="recent-row" key={novel.id} onClick={() => enterWritingDirectly(novel.id as any)}>
                <div className="recent-row-icon">
                  <BookOpenIcon />
                </div>
                <div className="recent-row-info">
                  <div className="recent-row-title">{novel.title}</div>
                  <div className="recent-row-meta">{formatDate(novel.updatedAt)}</div>
                </div>
                <div className="recent-row-arrow">
                  <ArrowRightIcon />
                </div>
              </div>
            ))}
            {otherNovels.length === 0 && (
              <div className="empty-recent">暂无更多历史记录</div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default ModernDashboard
