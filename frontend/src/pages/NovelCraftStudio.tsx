import React, { useState, useEffect } from 'react'
import { 
  Layout, 
  Typography, 
  Card, 
  Button, 
  Input,
  Row,
  Col,
  Spin,
  Space,
  Modal,
  Progress,
  Tabs,
  List,
  Divider,
  Alert,
  Table,
  Tag,
  Avatar,
  Collapse,
  Statistic,
  Affix,
  BackTop,
  App
} from 'antd'
import { 
  RobotOutlined,
  EditOutlined,
  BulbOutlined,
  BookOutlined,
  FileTextOutlined,
  SettingOutlined,
  MessageOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  SaveOutlined,
  ReloadOutlined,
  PlusOutlined,
  ThunderboltOutlined
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { Descriptions, InputNumber, Slider } from 'antd'
import './NovelCraftStudio.css'
import api from '@/services/api'
import { withAIConfig } from '../utils/aiRequest'
import novelOutlineService from '@/services/novelOutlineService'

const { Title, Text, Paragraph } = Typography
const { TextArea } = Input
const { Panel } = Collapse
// 工作流状态
interface WorkflowState {
  outline?: any
  memoryBank?: any  
  currentChapter: number
  totalChapters: number
  chapters: any[]
  aiSuggestions: any[]
  chatHistory: any[]
  taskInProgress?: string
}

// 组件接口
interface NovelCraftStudioProps {}

const NovelCraftStudio: React.FC<NovelCraftStudioProps> = () => {
  const { novelId } = useParams<{ novelId: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  
  // 主状态
  const [loading, setLoading] = useState(false)
  const [novel, setNovel] = useState<any>(null)
  const [workflow, setWorkflow] = useState<WorkflowState>({
    currentChapter: 0,
    totalChapters: 1000,
    chapters: [],
    aiSuggestions: [],
    chatHistory: []
  })
  
  // UI状态
  const [activeTab, setActiveTab] = useState('outline')
  const [sidePanel, setSidePanel] = useState('memory')
  const [taskModalOpen, setTaskModalOpen] = useState(false)
  const [currentTaskId, setCurrentTaskId] = useState('')
  const [taskProgress, setTaskProgress] = useState(0)
  
  // 表单状态
  const [basicIdea, setBasicIdea] = useState('')
  const [userMessage, setUserMessage] = useState('')
  const [selectedChapter, setSelectedChapter] = useState<any>(null)
  const [chapterContent, setChapterContent] = useState('')
  const [targetWordCount, setTargetWordCount] = useState(2000)
  const [targetChapterCount, setTargetChapterCount] = useState(100)
  const [outlineStatus, setOutlineStatus] = useState<'none' | 'generating' | 'draft' | 'confirmed'>('none')
  const [outlineAdjustment, setOutlineAdjustment] = useState('')
  const [suggestionsLoading, setSuggestionsLoading] = useState(false)

  useEffect(() => {
    if (novelId) {
      loadNovel()
      loadWorkflowData()
    }
  }, [novelId])

  // 数据加载
  const loadNovel = async () => {
    setLoading(true)
    try {
      // 加载小说基本信息
      const novelData = await api.get(`/novels/${novelId}`)
      setNovel((novelData as any).data || novelData)
      
    } catch (error: any) {
      message.error(error?.message || '加载小说信息失败')
    } finally {
      setLoading(false)
    }
  }

  // 加载工作流数据
  const loadWorkflowData = async () => {
    try {
      const savedData = localStorage.getItem(`novel_workflow_${novelId}`)
      if (savedData) {
        const parsedData = JSON.parse(savedData)
        setWorkflow(prev => ({ ...prev, ...parsedData.workflow }))
        setOutlineStatus(parsedData.outlineStatus || 'none')
        setBasicIdea(parsedData.basicIdea || '')
        setTargetChapterCount(parsedData.targetChapterCount || 100)
        setTargetWordCount(parsedData.targetWordCount || 2000)
        console.log('已恢复工作流数据:', parsedData)
      }
    } catch (error) {
      console.warn('加载工作流数据失败:', error)
    }
  }

  // 保存工作流数据
  const saveWorkflowData = () => {
    try {
      const dataToSave = {
        workflow,
        outlineStatus,
        basicIdea,
        targetChapterCount,
        targetWordCount,
        savedAt: Date.now()
      }
      localStorage.setItem(`novel_workflow_${novelId}`, JSON.stringify(dataToSave))
      console.log('工作流数据已保存')
    } catch (error) {
      console.warn('保存工作流数据失败:', error)
    }
  }

  // 自动保存工作流数据
  useEffect(() => {
    if (workflow.outline || workflow.memoryBank || workflow.chapters.length > 0) {
      const timer = setTimeout(() => {
        saveWorkflowData()
      }, 2000) // 2秒后保存
      
      return () => clearTimeout(timer)
    }
  }, [workflow, outlineStatus, basicIdea, targetChapterCount, targetWordCount])

  // 估算字数
  const estimateWordCount = (content: string) => {
    if (!content || content.trim().length === 0) return 0
    // 中文字数统计，去除空格和标点符号
    return content.replace(/[\s\p{P}]/gu, '').length
  }

  // 启动异步AI任务
  const startAsyncTask = async (endpoint: string, data: any, taskName: string, customTaskId?: string) => {
    try {
      const showModal = !customTaskId // 只有主要任务显示模态框
      if (showModal) {
        setTaskModalOpen(true)
        setTaskProgress(0)
      }
      
      const response: any = await api.post(`/async-task/${endpoint}`, { ...data, novelId })
      
      const result = response
      
      if (result && (result.success === true || result.code === 200)) {
        const payload = result.data || result
        const taskId = payload.taskId
        
        if (showModal) {
          setCurrentTaskId(taskId)
        }
        pollTaskStatus(taskId, taskName, showModal)
      } else {
        throw new Error(result?.message || `${taskName}接口返回异常`)
      }
    } catch (error: any) {
      message.error(error?.message || `启动${taskName}失败`)
      if (!customTaskId) {
        setTaskModalOpen(false)
      }
    }
  }

  // 轮询任务状态
  const pollTaskStatus = async (taskId: string, taskName: string, showModal: boolean = true) => {
    const poll = async () => {
      try {
        const result: any = await api.get(`/async-task/status/${taskId}`)
        const status = result.data
        
        if (showModal) {
          setTaskProgress(status.progress || 0)
        }
        
        if (status.status === 'completed') {
          if (showModal) {
            message.success(`${taskName}完成！`)
            setTaskModalOpen(false)
          } else {
            console.log(`${taskName}完成`)
          }
          handleTaskCompletion(taskId, status.result)
        } else if (status.status === 'error') {
          // 重置相关loading状态
          if (taskId.startsWith('suggestions_')) {
            setSuggestionsLoading(false)
          }
          
          if (showModal) {
            message.error(`${taskName}失败：${status.message}`)
            setTaskModalOpen(false)
          } else {
            console.warn(`${taskName}失败：${status.message}`)
          }
        } else {
          // 继续轮询
          setTimeout(poll, 2000)
        }
      } catch (error: any) {
        if (showModal) {
          message.error(error?.message || '查询任务状态失败')
          setTaskModalOpen(false)
        } else {
          console.warn('查询任务状态失败:', error)
        }
      }
    }
    
    poll()
  }

  // 处理任务完成
  const handleTaskCompletion = (taskId: string, result: any) => {
    if (taskId.startsWith('outline_')) {
      // 大纲生成完成后，设置为草稿状态等待确认
      setWorkflow(prev => ({ ...prev, ...result }))
      setOutlineStatus('draft')
      setActiveTab('outline') // 确保停留在大纲页面
      
      // 🔧 修复：保存记忆库到本地存储，确保卷写作页面能访问
      if (result.memoryBank && novelId) {
        try {
          const saved = localStorage.getItem(`novel_workflow_${novelId}`) || '{}'
          const data = JSON.parse(saved)
          data.workflow = { ...data.workflow, memoryBank: result.memoryBank }
          localStorage.setItem(`novel_workflow_${novelId}`, JSON.stringify(data))
          console.log('✅ 记忆库已保存到本地存储:', result.memoryBank)
        } catch (error) {
          console.error('保存记忆库到本地存储失败:', error)
        }
      }
      
      // 不自动跳转到章节页面，留在大纲页面等待确认
    } else if (taskId.startsWith('adjust_')) {
      // 大纲调整完成后，更新工作流状态
      const adjustedOutline = result.adjustedOutline
      setWorkflow(prev => ({ 
        ...prev, 
        outline: adjustedOutline,
        memoryBank: prev.memoryBank // 保持现有的记忆库
      }))
      setOutlineStatus('draft') // 调整后重新为草稿状态，可以继续调整
      message.success('大纲调整完成，请review后确认或继续调整')
    } else if (taskId.startsWith('chapters_')) {
      setWorkflow(prev => ({ ...prev, chapters: [...prev.chapters, ...result.chapters] }))
    } else if (taskId.startsWith('write_')) {
      // 更新章节内容和记忆库
      setWorkflow(prev => ({ ...prev, memoryBank: result.updatedMemoryBank }))
      
      // 🔧 修复：同步更新本地存储的记忆库
      if (result.updatedMemoryBank && novelId) {
        try {
          const saved = localStorage.getItem(`novel_workflow_${novelId}`) || '{}'
          const data = JSON.parse(saved)
          data.workflow = { ...data.workflow, memoryBank: result.updatedMemoryBank }
          localStorage.setItem(`novel_workflow_${novelId}`, JSON.stringify(data))
        } catch (error) {
          console.error('更新本地存储记忆库失败:', error)
        }
      }
      
      // 处理章节写作结果
      if (result.writingResult) {
        const writingResult = result.writingResult
        const chapterNumber = writingResult.chapterNumber
        const content = writingResult.content
        
        // 更新章节内容到前端状态
        setChapterContent(content)
        
        // 找到对应的章节并标记为已完成
        setWorkflow(prev => ({
          ...prev,
          chapters: prev.chapters.map(ch => 
            ch.chapterNumber === chapterNumber 
              ? { ...ch, content: content, status: 'completed', writtenAt: new Date() }
              : ch
          ),
          currentChapter: chapterNumber
        }))
        
        // 自动跳转到写作页面显示生成的内容
        setActiveTab('writing')
        
        // 设置选中的章节（使用更新后的章节状态）
        setTimeout(() => {
          const updatedChapter = workflow.chapters.find(ch => ch.chapterNumber === chapterNumber)
          if (updatedChapter) {
            setSelectedChapter({ ...updatedChapter, content: content, status: 'completed' })
          }
        }, 100) // 稍等一下让状态更新完成
        
        message.success(`第${chapterNumber}章写作完成！字数：${estimateWordCount(content)}`)
      }
      
      // 章节写作完成后，自动异步生成AI建议
      if (result.updatedMemoryBank && novelId) {
        const suggestionsTaskId = 'suggestions_' + novelId + '_' + Date.now()
        startAsyncTask('generate-suggestions', {
          memoryBank: result.updatedMemoryBank,
          currentChapter: workflow.currentChapter || 1
        }, 'AI建议生成', suggestionsTaskId)
      }
    } else if (taskId.startsWith('suggestions_')) {
      // AI建议生成完成
      setSuggestionsLoading(false)
      if (result.suggestions) {
        const suggestions = result.suggestions
        let processedSuggestions: any[] = []
        
        // 处理不同格式的建议数据
        if (Array.isArray(suggestions.suggestions)) {
          processedSuggestions = suggestions.suggestions
        } else if (suggestions.suggestions?.items) {
          processedSuggestions = suggestions.suggestions.items
        } else if (suggestions.suggestions) {
          processedSuggestions = [{
            type: '创作建议',
            content: suggestions.suggestions,
            title: 'AI建议'
          }]
        }
        
        setWorkflow(prev => ({
          ...prev,
          aiSuggestions: processedSuggestions
        }))
        console.log('AI建议更新成功:', processedSuggestions.length + ' 条建议')
      }
    }
  }

  // AI功能处理函数
  const initializeOutline = () => {
    if (!basicIdea.trim()) {
      message.warning('请先输入基本创作构思')
      return
    }
    
    setOutlineStatus('generating')
    startAsyncTask('init-outline', { 
      basicIdea, 
      targetChapterCount,
      targetWordCount
    }, '大纲初始化')
  }

  // 调整大纲
  const adjustOutline = () => {
    if (!outlineAdjustment.trim()) {
      message.warning('请输入大纲调整要求')
      return
    }
    
    if (!workflow.outline) {
      message.warning('请先生成大纲')
      return
    }
    
    setOutlineStatus('generating')
    startAsyncTask('adjust-outline', {
      currentOutline: workflow.outline,
      adjustmentRequest: outlineAdjustment,
      basicIdea: basicIdea
    }, '大纲调整')
  }

  // 确认大纲
  const confirmOutline = () => {
    if (!workflow.outline) {
      message.warning('没有可确认的大纲')
      return
    }

    setOutlineStatus('confirmed')
    message.success('大纲已确认！正在为你生成卷规划...')

    // 先启动后台异步：保存大纲 → 确认/兜底触发卷生成
    ;(async () => {
      try {
        // 1) 覆盖保存到 novels.outline
        if (novelId) {
          console.log('[confirmOutline] updateOutline start')
          await novelOutlineService.updateOutline(novelId, workflow.outline)
          console.log('[confirmOutline] updateOutline done')

          // 更新小说的创作阶段为"大纲已确认"
          try {
            await api.put(`/novels/${novelId}`, {
              creationStage: 'OUTLINE_CONFIRMED'
            });
            console.log('[confirmOutline] 创作阶段已更新为：大纲已确认');
          } catch (error) {
            console.warn('[confirmOutline] 更新创作阶段失败:', error);
          }
        }
        // 2) 优先使用大纲记录确认（触发后端异步拆分卷）
        let triggered = false
        if (novelId) {
          try {
            console.log('[confirmOutline] fetch outline record by novelId')
            const outlineRes: any = await api.get(`/outline/novel/${novelId}`)
            const outlineId = outlineRes?.id || outlineRes?.data?.id
            if (outlineId) {
              console.log('[confirmOutline] confirming outlineId=', outlineId)
              await api.put(`/outline/${outlineId}/confirm`)
              message.success('已触发卷规划生成任务（通过大纲确认）')
              triggered = true
            }
          } catch (err) {
            console.warn('[confirmOutline] 获取大纲记录失败，将走兜底触发', err)
          }
        }
        // 3) 若未触发，直接调用卷规划生成接口作为兜底
        if (!triggered && novelId) {
          const chapters = Number(targetChapterCount) || 100
          const wordsPerChapter = Number(targetWordCount) || 2000
          const totalWords = chapters * wordsPerChapter
          const volumeCount = Math.max(1, Math.min(10, Math.round(totalWords / 200000)))
          console.log('[confirmOutline] fallback trigger generate-from-outline, volumeCount=', volumeCount)
          await api.post(`/volumes/${novelId}/generate-from-outline`, withAIConfig({ volumeCount }))
          message.success(`已触发卷规划生成（兜底），约${volumeCount}卷`)
        }
      } catch (e) {
        console.warn('[confirmOutline] 触发卷规划失败，将在卷管理页手动生成', e)
        message.warning('自动触发卷规划失败，你可以在卷管理页手动生成')
      }
    })()

    // 然后立即跳转到卷管理页（乐观导航），避免卡住无反馈
    if (novelId) {
      console.log('[confirmOutline] navigate to volumes immediately, novelId=', novelId)
      navigate(`/novels/${novelId}/volumes`)
    }
  }

  const generateMoreChapters = () => {
    if (!workflow.outline) {
      message.warning('请先初始化大纲')
      return
    }
    
    if (outlineStatus !== 'confirmed') {
      message.warning('请先确认大纲后再进行章节生成')
      return
    }
    
    const startChapter = workflow.chapters.length + 1
    startAsyncTask('generate-chapters', { 
      outline: workflow.outline, 
      startChapter, 
      count: 20 
    }, '章节生成')
  }

  const writeChapter = (chapterPlan: any) => {
    if (!workflow.memoryBank) {
      message.warning('请先初始化大纲和记忆库')
      return
    }
    
    startAsyncTask('write-chapter', {
      chapterPlan,
      memoryBank: workflow.memoryBank
    }, '章节写作')
  }

  // 发送AI对话消息
  const sendChatMessage = async () => {
    if (!userMessage.trim()) return
    
    const newMessage = { role: 'user', content: userMessage }
    setWorkflow(prev => ({
      ...prev,
      chatHistory: [...prev.chatHistory, newMessage]
    }))
    
    setUserMessage('')
    
    try {
      const result: any = await api.post(`/novel-craft/${novelId}/dialogue`, {
          userMessage: newMessage.content,
          memoryBank: workflow.memoryBank,
          chatHistory: workflow.chatHistory
        })
      
      if (result && (result.success === true || result.code === 200)) {
        const payload = result.data || result
        const aiResponse = { 
          role: 'assistant', 
          content: payload.response || payload.aiMessage || '收到消息，正在处理中...' 
        }
        
        setWorkflow(prev => ({
          ...prev,
          chatHistory: [...prev.chatHistory, aiResponse]
        }))
      } else {
        throw new Error(result?.message || '对话接口返回异常')
      }
      
    } catch (error: any) {
      message.error(error?.message || 'AI对话失败')
      // 添加错误消息到聊天历史
      const errorMessage = { 
        role: 'assistant', 
        content: '抱歉，AI暂时无法响应，请稍后重试。' 
      }
      setWorkflow(prev => ({
        ...prev,
        chatHistory: [...prev.chatHistory, errorMessage]
      }))
    }
  }

  // 获取AI建议（异步版本）
  const fetchAISuggestions = async () => {
    if (!workflow.memoryBank || !novelId) return
    
    // 检查记忆库是否包含有效数据
    if (!workflow.memoryBank.characters && !workflow.memoryBank.worldSettings) {
      console.log('记忆库数据不完整，跳过建议生成')
      return
    }
    
    try {
      console.log('启动异步AI建议生成...')
      setSuggestionsLoading(true)
      const suggestionsTaskId = 'suggestions_' + novelId + '_' + Date.now()
      startAsyncTask('generate-suggestions', {
        memoryBank: workflow.memoryBank,
        currentChapter: workflow.currentChapter || 1
      }, 'AI建议生成', suggestionsTaskId)
      
    } catch (error) {
      console.error('启动AI建议生成失败:', error)
      setSuggestionsLoading(false)
    }
  }

  // 自动获取建议（当记忆库更新时）
  useEffect(() => {
    // 确保记忆库有实际内容才触发建议生成
    if (workflow.memoryBank && 
        (workflow.memoryBank.characters || workflow.memoryBank.worldSettings || 
         workflow.memoryBank.foreshadowing || workflow.memoryBank.chapterSummaries)) {
      
      // 延迟执行，避免过于频繁的API调用
      const timer = setTimeout(() => {
        fetchAISuggestions()
      }, 1000)
      
      return () => clearTimeout(timer)
    }
  }, [workflow.memoryBank])

  // 保存章节内容
  const saveChapterContent = async () => {
    if (!selectedChapter || !chapterContent.trim()) {
      message.warning('请输入章节内容')
      return
    }
    
    try {
      const result: any = await api.get(`/chapters/${selectedChapter.id || selectedChapter.chapterNumber}`)
      const chapterData = (result as any).data || result
      setChapterContent((chapterData && chapterData.content) ? chapterData.content : '')
    } catch (error) {
      console.error('加载章节内容失败:', error)
      setChapterContent('')
    }
  }

  // 选择章节
  const selectChapter = async (chapter: any) => {
    setSelectedChapter(chapter)
    setActiveTab('writing')
    
    // 优先使用前端状态中的内容，如果没有再尝试从后端加载
    if (chapter.content) {
      setChapterContent(chapter.content)
      console.log('使用前端缓存的章节内容')
    } else {
      // 尝试加载已有的章节内容
      try {
        console.log('尝试从后端加载章节内容:', chapter.chapterNumber)
        const result: any = await api.get(`/chapters/${chapter.id || chapter.chapterNumber}`)
        const chapterData = (result as any).data || result
        const content = (chapterData && chapterData.content) ? chapterData.content : ''
        setChapterContent(content)
        
        // 如果从后端加载到了内容，更新到前端状态
        if (content) {
          setWorkflow(prev => ({
            ...prev,
            chapters: prev.chapters.map(ch => 
              ch.chapterNumber === chapter.chapterNumber 
                ? { ...ch, content: content, status: 'completed' }
                : ch
            )
          }))
        }
      } catch (error) {
        console.warn('后端没有找到章节内容，显示空白页面用于预览章节计划')
        setChapterContent('')
      }
    }
  }

  // 渲染主要内容区域
  const renderMainContent = () => {
    const items = [
      {
        key: 'outline',
        label: (
          <span>
            <FileTextOutlined />
            动态大纲
          </span>
        ),
        children: renderOutlinePanel()
      },
      {
        key: 'chapters',
        label: (
          <span>
            <BookOutlined />
            章节管理
          </span>
        ),
        children: renderChaptersPanel()
      },
      {
        key: 'writing',
        label: (
          <span>
            <EditOutlined />
            内容创作
          </span>
        ),
        children: renderWritingPanel()
      },
      {
        key: 'suggestions',
        label: (
          <span>
            <BulbOutlined />
            AI建议
          </span>
        ),
        children: renderSuggestionsPanel()
      }
    ]

    return (
      <Tabs 
        activeKey={activeTab} 
        onChange={setActiveTab}
        items={items}
        size="large"
        className="main-tabs"
      />
    )
  }

  // 大纲面板
  const renderOutlinePanel = () => (
    <div className="outline-panel">
      <Card title="创作构思" className="idea-card" extra={
        <Button 
          type="primary" 
          icon={<ThunderboltOutlined />}
          onClick={initializeOutline}
          loading={outlineStatus === 'generating' && currentTaskId.startsWith('outline_')}
          disabled={outlineStatus === 'confirmed'}
        >
          {outlineStatus === 'none' ? '生成动态大纲' : outlineStatus === 'confirmed' ? '大纲已确认' : '重新生成大纲'}
        </Button>
      }>
        <TextArea
          rows={4}
          placeholder="请详细描述你的小说创作构思，例如：主角是谁？背景设定？核心冲突？独特元素？故事风格？"
          value={basicIdea}
          onChange={e => setBasicIdea(e.target.value)}
          maxLength={2000}
          showCount
          disabled={outlineStatus === 'confirmed'}
        />
        
        <div style={{ marginTop: 20 }}>
          <Row gutter={[16, 16]}>
            <Col span={12}>
              <div>
                <Text strong>目标章节数</Text>
                <div style={{ marginTop: 8 }}>
                  <Slider
                    min={10}
                    max={2000}
                    step={10}
                    value={targetChapterCount}
                    onChange={setTargetChapterCount}
                    disabled={outlineStatus === 'confirmed'}
                    marks={{
                      50: '短篇',
                      200: '中篇', 
                      500: '长篇',
                      1000: '超长篇',
                      2000: '史诗级'
                    }}
                  />
                  <InputNumber
                    min={10}
                    max={2000}
                    value={targetChapterCount}
                    onChange={value => setTargetChapterCount(value || 100)}
                    addonAfter="章"
                    style={{ width: '100%', marginTop: 8 }}
                    disabled={outlineStatus === 'confirmed'}
                  />
                </div>
              </div>
            </Col>
            
            <Col span={12}>
              <div>
                <Text strong>每章目标字数</Text>
                <div style={{ marginTop: 8 }}>
                  <Slider
                    min={500}
                    max={10000}
                    step={100}
                    value={targetWordCount}
                    onChange={setTargetWordCount}
                    disabled={outlineStatus === 'confirmed'}
                    marks={{
                      1000: '1K',
                      2000: '2K',
                      5000: '5K',
                      8000: '8K'
                    }}
                  />
                  <InputNumber
                    min={500}
                    max={10000}
                    value={targetWordCount}
                    onChange={value => setTargetWordCount(value || 2000)}
                    addonAfter="字"
                    style={{ width: '100%', marginTop: 8 }}
                    disabled={outlineStatus === 'confirmed'}
                  />
                </div>
              </div>
            </Col>
          </Row>
          
          <div style={{ marginTop: 16, padding: 12, background: '#f8f9ff', borderRadius: 8 }}>
            <Text type="secondary">
              📊 预估总字数：{(targetChapterCount * targetWordCount / 10000).toFixed(1)}万字 
              {targetChapterCount >= 1000 ? ' 🏆 史诗巨作' : targetChapterCount >= 500 ? ' 📚 超长篇' : targetChapterCount >= 200 ? ' 📖 长篇' : ' 📔 中短篇'}
            </Text>
          </div>
        </div>
      </Card>

      {/* 大纲生成状态提示 */}
      {outlineStatus === 'generating' && (
        <Card style={{ marginTop: 16 }}>
          <div style={{ textAlign: 'center', padding: 20 }}>
            <Spin size="large" />
            <div style={{ marginTop: 16 }}>
              <Text>AI正在生成动态大纲，请耐心等待...</Text>
            </div>
          </div>
        </Card>
      )}

      {/* 大纲确认和调整区域 */}
      {(outlineStatus === 'draft' || outlineStatus === 'confirmed' || outlineStatus === 'generating') && workflow.outline && (
        <>
          <Card 
            title={
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span>
                  📋 大纲预览
                  {outlineStatus === 'confirmed' && (
                    <Tag color="green" style={{ marginLeft: 8 }}>
                      <CheckCircleOutlined /> 已确认
                    </Tag>
                  )}
                  {outlineStatus === 'draft' && (
                    <Tag color="orange" style={{ marginLeft: 8 }}>
                      <ExclamationCircleOutlined /> 待确认
                    </Tag>
                  )}
                </span>
                {outlineStatus === 'draft' && (
                  <Space>
                    <Button 
                      type="primary" 
                      icon={<CheckCircleOutlined />}
                      onClick={confirmOutline}
                    >
                      确认大纲
                    </Button>
                  </Space>
                )}
              </div>
            }
            className="outline-structure"
            style={{ marginTop: 16 }}
          >
            {renderOutlineTree()}
            
            {/* 调整摘要显示 */}
            {workflow.outline.adjustmentSummary && (
              <div style={{ marginTop: 16, padding: 12, background: '#fff7e6', borderRadius: 6, borderLeft: '4px solid #faad14' }}>
                <Text strong>📝 最近调整：</Text>
                <div style={{ marginTop: 8 }}>
                  {workflow.outline.adjustmentSummary.changedAspects && 
                    workflow.outline.adjustmentSummary.changedAspects.map((aspect: string, index: number) => (
                      <Tag key={index} style={{ marginBottom: 4 }}>{aspect}</Tag>
                    ))
                  }
                </div>
                {workflow.outline.adjustmentSummary.reasonForChanges && (
                  <div style={{ marginTop: 8 }}>
                    <Text type="secondary">{workflow.outline.adjustmentSummary.reasonForChanges}</Text>
                  </div>
                )}
              </div>
            )}
          </Card>

          {/* 大纲调整区域 */}
          {(outlineStatus === 'draft' || outlineStatus === 'generating') && (
            <Card 
              title="💡 大纲调整" 
              style={{ marginTop: 16 }}
              extra={
                <Button 
                  type="default"
                  icon={<ReloadOutlined />}
                  onClick={adjustOutline}
                  loading={outlineStatus === 'generating' && currentTaskId.startsWith('adjust_')}
                  disabled={!outlineAdjustment.trim()}
                >
                  调整大纲
                </Button>
              }
            >
              <TextArea
                rows={3}
                placeholder="如果对大纲不满意，请描述您希望调整的方向，例如：&#10;- 主角背景太简单，希望增加更复杂的身世&#10;- 前期节奏太慢，希望提前引入冲突&#10;- 增加更多角色互动和情感线&#10;- 世界观设定需要更详细"
                value={outlineAdjustment}
                onChange={e => setOutlineAdjustment(e.target.value)}
                maxLength={500}
                showCount
              />
              <div style={{ marginTop: 12 }}>
                <Alert
                  message="调整说明"
                  description="AI会根据您的反馈优化大纲结构，保持故事逻辑的连贯性。您可以多次调整直到满意为止。"
                  type="info"
                  showIcon
                  style={{ fontSize: 12 }}
                />
              </div>
            </Card>
          )}
        </>
      )}
    </div>
  )

  // 章节面板
  const renderChaptersPanel = () => (
    <div className="chapters-panel">
      <div className="chapters-header">
        <Space>
          <Statistic title="总章节" value={workflow.totalChapters} />
          <Statistic title="已规划" value={workflow.chapters.length} />
          <Statistic title="已完成" value={workflow.chapters.filter(ch => ch.status === 'completed').length} />
          <Statistic title="当前进度" value={workflow.currentChapter} />
        </Space>
        
        <Button 
          type="primary" 
          icon={<PlusOutlined />}
          onClick={generateMoreChapters}
          disabled={!workflow.outline}
        >
          生成更多章节
        </Button>
      </div>

      <Table 
        dataSource={workflow.chapters}
        columns={[
          { title: '章节', dataIndex: 'chapterNumber', width: 80 },
          { title: '标题', dataIndex: 'title' },
          { title: '类型', dataIndex: 'type', render: (type) => <Tag>{type}</Tag> },
          { 
            title: '状态', 
            dataIndex: 'status',
            width: 90,
            render: (status) => {
              const chapterStatus: 'completed' | 'writing' | 'pending' =
                status === 'completed' || status === 'writing' ? status : 'pending'
              const colors = {
                completed: 'green',
                writing: 'blue',
                pending: 'default',
              }
              const labels = {
                completed: '已完成',
                writing: '写作中',
                pending: '待写作',
              }
              return <Tag color={colors[chapterStatus]}>{labels[chapterStatus]}</Tag>
            }
          },
          { 
            title: '字数', 
            render: (_, record) => {
              if (record.content) {
                return <span style={{ color: '#52c41a' }}>{estimateWordCount(record.content)}</span>
              }
              return <span style={{ color: '#999' }}>{record.estimatedWords || 0}</span>
            }
          },
          { 
            title: '操作', 
            render: (_, record) => (
              <Space>
                <Button 
                  size="small" 
                  icon={<EyeOutlined />} 
                  onClick={() => selectChapter(record)}
                  disabled={!record.content}
                >
                  {record.content ? '查看' : '预览'}
                </Button>
                <Button 
                  size="small" 
                  type={record.status === 'completed' ? 'default' : 'primary'} 
                  icon={<EditOutlined />} 
                  onClick={() => writeChapter(record)}
                >
                  {record.status === 'completed' ? '重写' : '写作'}
                </Button>
              </Space>
            )
          }
        ]}
        pagination={{ pageSize: 20 }}
        className="chapters-table"
      />
    </div>
  )

  // 写作面板
  const renderWritingPanel = () => (
    <div className="writing-panel">
      {selectedChapter ? (
        <Card title={`第${selectedChapter.chapterNumber}章：${selectedChapter.title}`}>
          {/* 章节详细信息和写作区域 */}
          <div className="chapter-details">
            <Descriptions column={2}>
              <Descriptions.Item label="核心事件">{selectedChapter.coreEvent}</Descriptions.Item>
              <Descriptions.Item label="类型">{selectedChapter.type}</Descriptions.Item>
              <Descriptions.Item label="氛围">{selectedChapter.mood}</Descriptions.Item>
              <Descriptions.Item label="预计字数">{selectedChapter.estimatedWords}</Descriptions.Item>
            </Descriptions>
          </div>
          
          <Divider />
          
          {chapterContent ? (
            <TextArea 
              rows={20}
              placeholder="章节内容将在这里显示，或者你可以手动编辑..."
              className="chapter-content-editor"
              value={chapterContent}
              onChange={e => setChapterContent(e.target.value)}
            />
          ) : (
            <div style={{ 
              border: '1px dashed #d9d9d9', 
              borderRadius: 6, 
              padding: 40, 
              textAlign: 'center',
              background: '#fafafa' 
            }}>
              <FileTextOutlined style={{ fontSize: 48, color: '#bbb', marginBottom: 16 }} />
              <div>
                <Title level={4} style={{ color: '#999' }}>章节尚未生成内容</Title>
                <Text type="secondary">点击下方"AI写作"按钮生成章节内容，或手动输入内容</Text>
              </div>
              <div style={{ marginTop: 20 }}>
                <Button 
                  size="large"
                  type="primary" 
                  icon={<RobotOutlined />} 
                  onClick={() => writeChapter(selectedChapter)}
                >
                  AI写作生成内容
                </Button>
              </div>
            </div>
          )}
          
          <div className="writing-actions">
            <Space>
              <Button icon={<SaveOutlined />} onClick={saveChapterContent}>保存草稿</Button>
              <Button type="primary" icon={<CheckCircleOutlined />}>完成章节</Button>
              <Button icon={<RobotOutlined />} onClick={() => writeChapter(selectedChapter)}>
                AI重写
              </Button>
            </Space>
          </div>
        </Card>
      ) : (
        <Card>
          <div className="empty-state">
            <BookOutlined style={{ fontSize: 48, color: '#ccc' }} />
            <Title level={4}>请选择一个章节开始写作</Title>
            <Text type="secondary">从左侧章节列表中选择要写作的章节</Text>
          </div>
        </Card>
      )}
    </div>
  )

  // AI建议面板
  const renderSuggestionsPanel = () => (
    <div className="suggestions-panel">
      <div style={{ marginBottom: 16 }}>
        <Button 
          type="primary" 
          icon={<BulbOutlined />} 
          onClick={fetchAISuggestions}
          disabled={!workflow.memoryBank}
          loading={suggestionsLoading}
        >
          {suggestionsLoading ? '生成中...' : '刷新建议'}
        </Button>
      </div>
      
      {suggestionsLoading && (
        <div style={{ textAlign: 'center', padding: 20 }}>
          <Spin size="large" />
          <div style={{ marginTop: 12 }}>
            <Text type="secondary">AI正在基于当前创作状态生成专业建议...</Text>
          </div>
        </div>
      )}
      
      {!suggestionsLoading && (
        <List
          dataSource={workflow.aiSuggestions}
          renderItem={item => (
            <List.Item
              actions={[
                <Button key="apply">采用</Button>,
                <Button key="ignore">忽略</Button>
              ]}
            >
              <List.Item.Meta
                avatar={<Avatar icon={<BulbOutlined />} />}
                title={item.title || item.type || '建议'}
                description={item.content || item.description || '暂无详细内容'}
              />
            </List.Item>
          )}
          locale={{ emptyText: workflow.memoryBank ? 'AI尚未生成建议，点击"刷新建议"获取' : '请先初始化大纲以获取AI建议' }}
        />
      )}
    </div>
  )

  // 侧边面板
  const renderSidePanel = () => {
    const panelItems = [
      {
        key: 'memory',
        label: '记忆库',
        children: renderMemoryBank()
      },
      {
        key: 'consistency',
        label: '一致性',
        children: renderConsistencyPanel()
      },
      {
        key: 'chat',
        label: 'AI对话',
        children: renderAIChat()
      }
    ]

    return (
      <Tabs
        activeKey={sidePanel}
        onChange={setSidePanel}
        items={panelItems}
        size="small"
        tabPosition="top"
      />
    )
  }

  // 记忆库面板
  const renderMemoryBank = () => (
    <Collapse size="small" className="memory-bank">
      <Panel header="角色档案" key="characters">
        {workflow.memoryBank?.characters && Object.keys(workflow.memoryBank.characters).length > 0 ? (
          <List 
            size="small"
            dataSource={Object.entries(workflow.memoryBank.characters)}
            renderItem={([name, info]: [string, any]) => (
              <List.Item>
                <List.Item.Meta title={name} description={info.role} />
              </List.Item>
            )}
          />
        ) : (
          <Text type="secondary">暂无角色信息</Text>
        )}
      </Panel>
      
      <Panel header="世界设定" key="world">
        {workflow.memoryBank?.worldSettings ? (
          <Paragraph>{JSON.stringify(workflow.memoryBank.worldSettings)}</Paragraph>
        ) : (
          <Text type="secondary">暂无世界设定</Text>
        )}
      </Panel>
      
      <Panel header="伏笔线索" key="foreshadowing">
        {workflow.memoryBank?.foreshadowing?.length > 0 ? (
          <List
            size="small"
            dataSource={workflow.memoryBank.foreshadowing}
            renderItem={(item: any) => (
              <List.Item>
                <Text>{item.content}</Text>
                <Tag color={item.status === 'active' ? 'processing' : 'success'}>
                  {item.status}
                </Tag>
              </List.Item>
            )}
          />
        ) : (
          <Text type="secondary">暂无伏笔信息</Text>
        )}
      </Panel>
    </Collapse>
  )

  // 一致性面板
  const renderConsistencyPanel = () => (
    <div className="consistency-panel">
      <Progress 
        percent={workflow.memoryBank?.consistency_score * 10 || 100}
        status="active"
        strokeColor="#52c41a"
      />
      <Text type="secondary">一致性评分</Text>
    </div>
  )

  // AI对话面板
  const renderAIChat = () => (
    <div className="ai-chat-panel">
      <div className="chat-messages">
        {workflow.chatHistory.map((msg, index) => (
          <div key={index} className={`chat-message ${msg.role}`}>
            <Avatar icon={msg.role === 'user' ? <EditOutlined /> : <RobotOutlined />} />
            <div className="message-content">{msg.content}</div>
          </div>
        ))}
      </div>
      
      <div className="chat-input">
        <Input.Group compact>
          <Input 
            value={userMessage}
            onChange={e => setUserMessage(e.target.value)}
            placeholder="与AI对话，获取创作建议..."
            onPressEnter={sendChatMessage}
          />
          <Button type="primary" icon={<MessageOutlined />} onClick={sendChatMessage}>
            发送
          </Button>
        </Input.Group>
      </div>
    </div>
  )

  // 渲染大纲树
  const renderOutlineTree = () => {
    if (!workflow.outline) return <div>暂无大纲数据</div>

    try {
      // 处理大纲结构
      const { mainStructure } = workflow.outline
      
      if (mainStructure?.phases) {
        return (
          <div className="outline-tree">
            {mainStructure.phases.map((phase: any, index: number) => (
              <div key={index} className="outline-phase">
                <Title level={5}>{phase.name}</Title>
                <Text type="secondary">{phase.description}</Text>
                <div style={{ marginTop: 8 }}>
                  <Tag color="blue">章节 {phase.chapterRange}</Tag>
                  <Tag color="green">阶段目标</Tag>
                </div>
                {phase.arcs && phase.arcs.map((arc: any, arcIndex: number) => (
                  <div key={arcIndex} className="outline-arc">
                    <Text strong>{arc.title}</Text>
                    <br />
                    <Text>{arc.description}</Text>
                  </div>
                ))}
              </div>
            ))}
          </div>
        )
      }
    } catch (error) {
      console.error('渲染大纲树出错:', error)
    }
    
    return (
      <div className="outline-tree">
        <Alert
          message="大纲数据格式异常"
          description="请重新生成大纲"
          type="warning"
          showIcon
        />
      </div>
    )
  }

  if (loading) {
    return (
      <div className="loading-container">
        <Spin size="large" />
        <Title level={4}>加载中...</Title>
      </div>
    )
  }

  return (
    <div className="novel-craft-studio">
      {/* 顶部工具栏 */}
      <Affix offsetTop={0}>
        <div className="studio-header">
          <div className="header-left">
            <Title level={3} className="novel-title">
              {novel?.title || '未命名小说'}
            </Title>
            <Tag color="blue">{novel?.genre}</Tag>
          </div>
          
          <div className="header-right">
            <Space>
              <Button icon={<SaveOutlined />} onClick={saveWorkflowData}>保存进度</Button>
              <Button icon={<SettingOutlined />}>设置</Button>
            </Space>
          </div>
        </div>
      </Affix>

      {/* 主体布局 */}
      <Layout className="studio-layout">
        <Layout.Content className="main-content">
          {renderMainContent()}
        </Layout.Content>
        
        <Layout.Sider width={350} className="side-panel">
          {renderSidePanel()}
        </Layout.Sider>
      </Layout>

      {/* 任务进度模态框 */}
      <Modal
        title="AI任务进行中"
        open={taskModalOpen}
        footer={null}
        closable={false}
        className="task-modal"
      >
        <div className="task-progress">
          <Progress percent={taskProgress} status="active" />
          <Text>AI正在处理中，请稍候...</Text>
        </div>
      </Modal>

      <BackTop />
    </div>
  )
}

export default NovelCraftStudio
