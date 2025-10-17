import React, { useState, useEffect } from 'react'
import { Layout, Typography, Space, Button, message, Spin, Divider } from 'antd'
import { 
  ArrowLeftOutlined, 
  SettingOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import RichTextEditor from '@/components/editor/RichTextEditor'
import ChapterManager from '@/components/editor/ChapterManager'
import { Chapter, chapterService } from '@/services/chapterService'
import { Novel, novelService } from '@/services/novelService'
import './NovelEditorPage.css'

const { Header, Content, Sider } = Layout
const { Title, Text } = Typography

const NovelEditorPage: React.FC = () => {
  const navigate = useNavigate()
  const { novelId } = useParams<{ novelId: string }>()
  const [novel, setNovel] = useState<Novel | null>(null)
  const [currentChapter, setCurrentChapter] = useState<Chapter | null>(null)
  const [chapterContent, setChapterContent] = useState('')
  const [loading, setLoading] = useState(false)

  const [fullscreen, setFullscreen] = useState(false)
  const [siderCollapsed, setSiderCollapsed] = useState(false)

  // 加载小说信息
  const loadNovel = async () => {
    if (!novelId) return
    
    try {
      setLoading(true)
      const novelData = await novelService.getNovelById(parseInt(novelId))
      setNovel(novelData)
    } catch (error) {
      message.error('加载小说失败')
      navigate('/novels')
    } finally {
      setLoading(false)
    }
  }

  // 加载章节内容
  const loadChapter = async (chapter: Chapter) => {
    try {
      setLoading(true)
      const chapterData = await chapterService.getChapterById(chapter.id)
      setCurrentChapter(chapterData)
      setChapterContent(chapterData.content)
    } catch (error) {
      message.error('加载章节失败')
    } finally {
      setLoading(false)
    }
  }

  // 保存章节内容
  const handleSaveChapter = async () => {
    if (!currentChapter) return
    
    try {
      await chapterService.updateChapter(currentChapter.id, {
        content: chapterContent
      })
      message.success('保存成功')
    } catch (error) {
      message.error('保存失败')
    }
  }



  // 章节选择处理
  const handleChapterSelect = (chapter: Chapter) => {
    if (chapter.id) {
      loadChapter(chapter)
    } else {
      setCurrentChapter(null)
      setChapterContent('')
    }
  }

  // 章节变化处理
  const handleChaptersChange = () => {
    // 章节列表发生变化时的处理
    // 可以在这里重新加载章节列表或更新统计信息
  }

  // 切换全屏模式
  const toggleFullscreen = () => {
    setFullscreen(!fullscreen)
  }

  // 切换侧边栏
  const toggleSider = () => {
    setSiderCollapsed(!siderCollapsed)
  }

  useEffect(() => {
    loadNovel()
  }, [novelId])

  if (loading && !novel) {
    return (
      <div className="loading-container">
        <Spin size="large" />
        <Text>加载中...</Text>
      </div>
    )
  }

  return (
    <Layout className={`novel-editor-layout ${fullscreen ? 'fullscreen' : ''}`}>
      <Header className="editor-header">
        <div className="header-left">
          <Space>
            <Button 
              icon={<ArrowLeftOutlined />} 
              onClick={() => navigate('/novels')}
              type="text"
            >
              返回
            </Button>
            <Divider type="vertical" />
            <Title level={4} className="novel-title">
              {novel?.title || '小说编辑器'}
            </Title>
          </Space>
        </div>
        
        <div className="header-center">
          {currentChapter && (
            <Text strong className="chapter-title">
              第{currentChapter.chapterNumber}章：{currentChapter.title}
            </Text>
          )}
        </div>
        
        <div className="header-right">
          <Space>
            <Button
              icon={<SettingOutlined />}
              onClick={toggleSider}
              type="text"
            >
              章节管理
            </Button>
            <Button
              icon={fullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
              onClick={toggleFullscreen}
              type="text"
            >
              {fullscreen ? '退出全屏' : '全屏'}
            </Button>
          </Space>
        </div>
      </Header>

      <Layout className="editor-content-layout">
        <Sider 
          width={300} 
          collapsed={siderCollapsed}
          collapsible
          onCollapse={setSiderCollapsed}
          className="chapter-sider"
          theme="light"
        >
          <ChapterManager
            novelId={parseInt(novelId || '0')}
            onChapterSelect={handleChapterSelect}
            selectedChapterId={currentChapter?.id}
            onChaptersChange={handleChaptersChange}
          />
        </Sider>

        <Content className="editor-main-content">
          {currentChapter ? (
            <RichTextEditor
              value={chapterContent}
              onChange={setChapterContent}
              onSave={handleSaveChapter}
              placeholder="开始创作这一章的内容..."
              autoSave={true}
              autoSaveInterval={30000}
              showToolbar={true}
              showWordCount={true}
              className="main-editor"
            />
          ) : (
            <div className="no-chapter-selected">
              <div className="no-chapter-content">
                <Text type="secondary" style={{ fontSize: 16 }}>
                  请从左侧选择一个章节开始创作
                </Text>
                <Text type="secondary" style={{ fontSize: 14, marginTop: 8 }}>
                  或者点击"新建章节"创建新的章节
                </Text>
              </div>
            </div>
          )}
        </Content>
      </Layout>
    </Layout>
  )
}

export default NovelEditorPage 