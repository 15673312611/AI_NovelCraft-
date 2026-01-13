import React, { useEffect, useState, useRef, useCallback } from 'react'
import { message, Modal, Input, Button, Spin } from 'antd'
import { SearchOutlined, EditOutlined, FormOutlined, HighlightOutlined, BarChartOutlined, BulbOutlined, FileTextOutlined, HistoryOutlined } from '@ant-design/icons'
import type { NovelDocument } from '@/services/documentService'
import rewriteService from '@/services/rewriteService'
import aiService from '@/services/aiService'
import smartSuggestionService, { type SmartSuggestion } from '@/services/smartSuggestionService'
import api from '@/services/api'
import { checkAIConfig, AI_CONFIG_ERROR_MESSAGE, withAIConfig, getAIConfigOrThrow } from '@/utils/aiRequest'
import './EditorPanel.css'


export interface EditorPanelProps {
  document?: NovelDocument | null
  loading?: boolean
  onChangeContent: (content: string) => void
  onSave?: (document: NovelDocument) => Promise<void>
  onTitleChange?: (title: string) => void
  onShowOutline?: () => void
  onShowVolumeOutline?: () => void
  onShowSummary?: () => void
  onShowHistory?: () => void
  onReviewManuscript?: () => void
  onRemoveAITrace?: () => void
  onStreamlineContent?: () => void
  lastSaveTime?: string
  isSaving?: boolean
  onSearchReplace?: () => void
  chapterNumber?: number | null
}

const EditorPanel: React.FC<EditorPanelProps> = ({
  document,
  loading = false,
  onChangeContent,
  onSave: _onSave,
  onShowOutline,
  onShowHistory,
  onShowVolumeOutline,
  onShowSummary,
  onReviewManuscript,
  onRemoveAITrace,
  onStreamlineContent,
  lastSaveTime,
  isSaving = false,
  onSearchReplace,
  chapterNumber,
}) => {
  const [content, setContent] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const overlayRef = useRef<HTMLDivElement>(null)
  const searchButtonRef = useRef<HTMLButtonElement>(null)
  const [selectionInfo, setSelectionInfo] = useState<{ start: number; end: number; text: string } | null>(null)
  const [showPolishButton, setShowPolishButton] = useState(false)
  const isProgrammaticSelection = useRef(false) // 标记是否为程序自动选择
  const [polishModalVisible, setPolishModalVisible] = useState(false)
  const [polishInstructions, setPolishInstructions] = useState('')
  const [polishResult, setPolishResult] = useState<string | null>(null)
  const [isPolishing, setIsPolishing] = useState(false)

  // 章节取名相关状态
  const [nameModalVisible, setNameModalVisible] = useState(false)
  const [isGeneratingName, setIsGeneratingName] = useState(false)
  const [generatedNames, setGeneratedNames] = useState<string[]>([])

  // 搜索替换相关状态
  const [searchReplaceVisible, setSearchReplaceVisible] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [replaceText, setReplaceText] = useState('')
  const [currentMatchIndex, setCurrentMatchIndex] = useState(0)
  const [matches, setMatches] = useState<Array<{ start: number; end: number }>>([])
  const [caseSensitive, setCaseSensitive] = useState(false)
  const [searchPanelPosition, setSearchPanelPosition] = useState({ top: 0, left: 0 })

  // AI纠错相关状态
  const [proofreadModalVisible, setProofreadModalVisible] = useState(false)
  const [isProofreading, setIsProofreading] = useState(false)
  const [proofreadErrors, setProofreadErrors] = useState<Array<{
    type: string
    original: string
    corrected: string
    position: number
    context: string
    reason: string
    applied?: boolean
  }>>([])
  const [selectedErrorIndices, setSelectedErrorIndices] = useState<Set<number>>(new Set())

  const [smartEditModalVisible, setSmartEditModalVisible] = useState(false)
  const [smartEditInstructions, setSmartEditInstructions] = useState('')
  const [isSmartEditing, setIsSmartEditing] = useState(false)

  // AI智能建议相关状态
  const [suggestionModalVisible, setSuggestionModalVisible] = useState(false)
  const [isAnalyzingSuggestions, setIsAnalyzingSuggestions] = useState(false)
  const [suggestions, setSuggestions] = useState<Array<SmartSuggestion & { applied?: boolean }>>([])
  const [selectedSuggestionIndices, setSelectedSuggestionIndices] = useState<Set<number>>(new Set())

  useEffect(() => {
    if (document) {
      setContent(document.content || '')
    } else {
      setContent('')
    }
    setSelectionInfo(null)
    setShowPolishButton(false)
    setPolishModalVisible(false)
    setPolishInstructions('')
    setPolishResult(null)
  }, [document])

  const handleContentChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newContent = e.target.value
    setContent(newContent)
    onChangeContent(newContent)
    setSelectionInfo(null)
    setShowPolishButton(false)
  }

  const updateSelection = useCallback(() => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart ?? 0
    const end = textarea.selectionEnd ?? 0

    if (start === end) {
      setSelectionInfo(null)
      setShowPolishButton(false)
      return
    }

    const rawSelection = textarea.value.substring(start, end)
    if (!rawSelection || !rawSelection.trim()) {
      setSelectionInfo(null)
      setShowPolishButton(false)
      return
    }

    setSelectionInfo({ start, end, text: rawSelection })
    setShowPolishButton(true)
  }, [])

  // 同步滚动 - 使用 transform
  useEffect(() => {
    const textarea = textareaRef.current
    const overlay = overlayRef.current
    if (!textarea || !overlay) return

    const handleScroll = () => {
      const scrollTop = textarea.scrollTop
      const scrollLeft = textarea.scrollLeft

      // 使用 transform 移动 overlay 内容
      const overlayContent = overlay.firstChild as HTMLElement
      if (overlayContent) {
        overlayContent.style.transform = `translate(-${scrollLeft}px, -${scrollTop}px)`
      }
    }

    // 初始同步
    handleScroll()

    textarea.addEventListener('scroll', handleScroll)
    return () => {
      textarea.removeEventListener('scroll', handleScroll)
    }
  }, [selectionInfo, showPolishButton])

  const handleMouseUp = () => {
    // 如果是程序自动选择（如搜索），不处理
    if (isProgrammaticSelection.current) {
      return
    }
    updateSelection()
  }

  const handleKeyUp = () => {
    // 如果是程序自动选择（如搜索），不处理
    if (isProgrammaticSelection.current) {
      return
    }
    updateSelection()
  }

  const handleSelect = () => {
    // 如果是程序自动选择（如搜索），不处理
    if (isProgrammaticSelection.current) {
      return
    }
    updateSelection()
  }

  const handlePolishSubmit = async () => {
    if (!selectionInfo) {
      message.warning('请重新选中需要润色的内容')
      return
    }

    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    try {
      setIsPolishing(true)
      const response = await aiService.polishSelection({
        fullContent: content,
        selection: selectionInfo.text,
        instructions: polishInstructions.trim() || undefined,
        chapterTitle: document?.title,
      })
      if (!response || response.code !== 200) {
        throw new Error(response?.message || 'AI润色请求失败')
      }
      const polishedText = response.data?.polishedContent || response.polishedContent
      if (!polishedText || !polishedText.trim()) {
        throw new Error('AI未返回润色结果')
      }
      setPolishResult(polishedText.trim())
      message.success('润色完成，请确认修改效果')
    } catch (error: any) {
      console.error('AI润色失败:', error)
      message.error(error?.message || '润色失败，请稍后重试')
    } finally {
      setIsPolishing(false)
    }
  }

  const handleSmartEdit = async () => {
    if (!document || !content || !content.trim()) {
      message.warning('请先输入或加载需要修改的内容')
      return
    }

    const novelId = document.novelId
    if (!novelId) {
      message.error('缺少小说信息，无法进行智能修改')
      return
    }

    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    const baseRule =
      '【编辑模式】请在尽量保持原文不变的前提下，只根据“修改要求”对相关片段做最小必要修改。' +
      '未被要求修改的句子一个字都不要改（包括标点和换行），不要增加或删减剧情和信息。'
    const userReq = smartEditInstructions.trim()
    const finalRequirements = userReq ? `${baseRule}\n修改要求：${userReq}` : baseRule

    setIsSmartEditing(true)
    try {
      let accumulated = ''

      await rewriteService.rewriteChapterStream(
        novelId,
        {
          content,
          requirements: finalRequirements,
          chapterNumber: chapterNumber ?? undefined,
        },
        (chunk) => {
          accumulated += chunk
          setContent(accumulated)
          onChangeContent(accumulated)
        },
        (errorMessage) => {
          message.error(errorMessage || '智能修改失败')
          setIsSmartEditing(false)
        },
        () => {
          setIsSmartEditing(false)
          if (accumulated.trim()) {
            setContent(accumulated)
            onChangeContent(accumulated)
            message.success('智能修改完成')
          } else {
            message.warning('AI未返回修改结果')
          }
          setSmartEditModalVisible(false)
        }
      )
    } catch (error: any) {
      console.error('AI智能修改失败:', error)
      message.error(error?.message || '智能修改失败，请稍后重试')
      setIsSmartEditing(false)
    }
  }

  const handleApplyPolish = () => {
    if (!selectionInfo || !polishResult) {
      message.warning('暂无可替换的润色结果')
      return
    }

    const textarea = textareaRef.current
    const savedScrollTop = textarea?.scrollTop || 0

    const { start, end } = selectionInfo
    const replaced = content.substring(0, start) + polishResult + content.substring(end)
    setContent(replaced)
    onChangeContent(replaced)

    const newEnd = start + polishResult.length
    setSelectionInfo({ start, end: newEnd, text: polishResult })
    setShowPolishButton(false)
    setPolishModalVisible(false)
    setPolishResult(null)
    setPolishInstructions('')

    setTimeout(() => {
      if (textareaRef.current) {
        textareaRef.current.focus()
        textareaRef.current.setSelectionRange(start, newEnd)
        textareaRef.current.scrollTop = savedScrollTop
      }
    }, 0)

    message.success('已替换润色内容')
  }

  if (loading) {
    return (
      <div className="editor-panel" style={{ alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    )
  }

  const openPolishModal = () => {
    if (!selectionInfo) {
      message.info('请先选中文本内容')
      return
    }
    setShowPolishButton(false)
    setPolishResult(null)
    setPolishModalVisible(true)
  }

  const closePolishModal = () => {
    setPolishModalVisible(false)
    setIsPolishing(false)
    setPolishResult(null)
  }

  // AI纠错相关函数
  const openProofreadModal = () => {
    if (!content || content.trim().length < 10) {
      message.warning('内容太少，无需纠错')
      return
    }
    setProofreadErrors([])
    setSelectedErrorIndices(new Set())
    setProofreadModalVisible(true)
  }

  const closeProofreadModal = () => {
    setProofreadModalVisible(false)
    setIsProofreading(false)
  }

  const handleProofread = async () => {
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    try {
      setIsProofreading(true)
      const response = await aiService.proofread({
        content,
        characterNames: [],
      })

      if (!response || response.code !== 200) {
        throw new Error(response?.message || 'AI纠错请求失败')
      }

      const errors = response.data?.errors || []
      setProofreadErrors(errors.map((err: any) => ({ ...err, applied: false })))

      if (errors.length === 0) {
        message.success('未发现错误，文本质量良好！')
      } else {
        message.success(`发现 ${errors.length} 个可能的错误`)
      }
    } catch (error: any) {
      console.error('AI纠错失败:', error)
      message.error(error?.message || '纠错失败，请稍后重试')
    } finally {
      setIsProofreading(false)
    }
  }

  const findErrorPosition = (
    fullContent: string,
    error: { position: number; original: string }
  ): number => {
    const { position, original } = error
    if (!original) return -1

    let actualPosition = position

    // 优先尝试使用AI返回的position直接匹配
    if (
      actualPosition < 0 ||
      actualPosition + original.length > fullContent.length ||
      fullContent.substring(actualPosition, actualPosition + original.length) !== original
    ) {
      const searchStart = Math.max(0, position - 50)
      actualPosition = fullContent.indexOf(original, searchStart)

      if (actualPosition === -1) {
        // 兜底：全局搜索
        actualPosition = fullContent.indexOf(original)
      }
    }

    return actualPosition
  }

  const handleApplySingleError = (index: number) => {
    const error = proofreadErrors[index]
    if (!error || error.applied) return

    const { position, original, corrected } = error

    const actualPosition = findErrorPosition(content, { position, original })

    if (actualPosition === -1) {
      message.warning('未找到错误文本，可能已被修改')
      return
    }

    const newContent =
      content.substring(0, actualPosition) +
      corrected +
      content.substring(actualPosition + original.length)

    setContent(newContent)
    onChangeContent(newContent)

    const newErrors = [...proofreadErrors]
    newErrors[index] = { ...error, applied: true }
    setProofreadErrors(newErrors)

    message.success('已应用修改')
  }

  const handleApplyAllErrors = () => {
    if (proofreadErrors.length === 0) return

    let newContent = content
    let appliedCount = 0

    // 
    const sortedErrors = proofreadErrors
      .map((err, index) => ({ ...err, index }))
      .filter(err => !err.applied)
      .sort((a, b) => {
        const posA = findErrorPosition(newContent, { position: a.position, original: a.original })
        const posB = findErrorPosition(newContent, { position: b.position, original: b.original })
        return posB - posA
      })

    for (const error of sortedErrors) {
      const actualPosition = findErrorPosition(newContent, { position: error.position, original: error.original })

      if (actualPosition !== -1) {
        newContent =
          newContent.substring(0, actualPosition) +
          error.corrected +
          newContent.substring(actualPosition + error.original.length)
        appliedCount++
      }
    }

    if (appliedCount > 0) {
      setContent(newContent)
      onChangeContent(newContent)

      // 标记所有为已应用
      const newErrors = proofreadErrors.map(err => ({ ...err, applied: true }))
      setProofreadErrors(newErrors)

      message.success(`已应用 ${appliedCount} 处修改`)
    } else {
      message.warning('没有可应用的修改')
    }
  }

  // AI智能建议相关函数
  const openSuggestionModal = () => {
    if (!content || content.trim().length < 50) {
      message.warning('内容太少，无需智能建议')
      return
    }
    setSuggestions([])
    setSelectedSuggestionIndices(new Set())
    setSuggestionModalVisible(true)
  }

  const closeSuggestionModal = () => {
    setSuggestionModalVisible(false)
    setIsAnalyzingSuggestions(false)
  }

  const handleAnalyzeSuggestions = async () => {
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    try {
      setIsAnalyzingSuggestions(true)
      const response = await smartSuggestionService.getSmartSuggestions(content)
      
      // 处理 Result 格式的响应
      if (!response || (response as any).code !== 200) {
        throw new Error((response as any)?.message || 'AI智能建议请求失败')
      }

      const suggestionList = (response as any).data?.suggestions || []
      
      if (suggestionList.length > 0) {
        setSuggestions(suggestionList.map((s: any) => ({ ...s, applied: false })))
        message.success(`发现 ${suggestionList.length} 条建议`)
      } else {
        message.info('未发现需要改进的地方，内容很棒！')
      }
    } catch (error: any) {
      console.error('智能建议分析失败:', error)
      message.error('智能建议分析失败: ' + (error.response?.data?.message || error.message))
    } finally {
      setIsAnalyzingSuggestions(false)
    }
  }

  // 查找建议的准确位置
  const findSuggestionPosition = (
    fullContent: string,
    suggestion: { position: number; original: string; length: number; action: string }
  ): number => {
    const { position, original, action } = suggestion
    
    // insert 操作不需要查找 original
    if (action === 'insert') {
      return position >= 0 && position <= fullContent.length ? position : 0
    }
    
    if (!original) return -1

    let actualPosition = position

    // 优先尝试使用AI返回的position直接匹配
    if (
      actualPosition < 0 ||
      actualPosition + original.length > fullContent.length ||
      fullContent.substring(actualPosition, actualPosition + original.length) !== original
    ) {
      // 在position附近搜索（前后50个字符范围）
      const searchStart = Math.max(0, position - 50)
      actualPosition = fullContent.indexOf(original, searchStart)

      if (actualPosition === -1) {
        // 兜底：全局搜索
        actualPosition = fullContent.indexOf(original)
      }
    }

    return actualPosition
  }

  const handleApplySingleSuggestion = (index: number) => {
    const suggestion = suggestions[index]
    if (!suggestion || suggestion.applied) return

    const { position, length, action, original, suggested } = suggestion
    
    // 查找准确位置
    const actualPosition = findSuggestionPosition(content, suggestion)
    
    if (actualPosition === -1 && action !== 'insert') {
      message.warning('未找到原文，可能已被修改')
      return
    }

    let newContent = content

    if (action === 'replace' && suggested) {
      // 替换操作
      const actualLength = original ? original.length : length
      newContent = content.substring(0, actualPosition) + suggested + content.substring(actualPosition + actualLength)
    } else if (action === 'delete') {
      // 删除操作
      const actualLength = original ? original.length : length
      newContent = content.substring(0, actualPosition) + content.substring(actualPosition + actualLength)
    } else if (action === 'insert' && suggested) {
      // 插入操作
      newContent = content.substring(0, actualPosition) + suggested + content.substring(actualPosition)
    }

    setContent(newContent)
    onChangeContent(newContent)

    const newSuggestions = [...suggestions]
    newSuggestions[index] = { ...suggestion, applied: true }
    setSuggestions(newSuggestions)

    message.success('已应用建议')
  }

  const handleApplyAllSuggestions = () => {
    if (suggestions.length === 0) return

    let newContent = content
    let appliedCount = 0
    let skippedCount = 0

    // 按position从后往前排序，避免位置偏移
    const sortedSuggestions = suggestions
      .map((sug, index) => ({ ...sug, originalIndex: index }))
      .filter(sug => !sug.applied)
      .sort((a, b) => {
        // 先查找准确位置再排序
        const posA = findSuggestionPosition(newContent, a)
        const posB = findSuggestionPosition(newContent, b)
        return posB - posA
      })

    for (const suggestion of sortedSuggestions) {
      const { action, original, suggested, length } = suggestion
      
      // 查找准确位置
      const actualPosition = findSuggestionPosition(newContent, suggestion)
      
      if (actualPosition === -1 && action !== 'insert') {
        console.warn('跳过建议：未找到原文', suggestion)
        skippedCount++
        continue
      }

      if (action === 'replace' && suggested) {
        const actualLength = original ? original.length : length
        newContent = newContent.substring(0, actualPosition) + suggested + newContent.substring(actualPosition + actualLength)
        appliedCount++
      } else if (action === 'delete') {
        const actualLength = original ? original.length : length
        newContent = newContent.substring(0, actualPosition) + newContent.substring(actualPosition + actualLength)
        appliedCount++
      } else if (action === 'insert' && suggested) {
        newContent = newContent.substring(0, actualPosition) + suggested + newContent.substring(actualPosition)
        appliedCount++
      }
    }

    if (appliedCount > 0) {
      setContent(newContent)
      onChangeContent(newContent)

      // 标记所有为已应用
      const newSuggestions = suggestions.map(sug => ({ ...sug, applied: true }))
      setSuggestions(newSuggestions)

      if (skippedCount > 0) {
        message.success(`已应用 ${appliedCount} 条建议，跳过 ${skippedCount} 条（原文已变更）`)
      } else {
        message.success(`已应用 ${appliedCount} 条建议`)
      }
    } else {
      message.warning('没有可应用的建议')
    }
  }

  // 打开章节取名弹窗
  const openNameModal = () => {
    if (!content || content.trim().length < 100) {
      message.warning('章节内容太少，请先写一些内容再取名')
      return
    }
    setGeneratedNames([])
    setNameModalVisible(true)
  }

  // 生成章节名
  const handleGenerateNames = async () => {
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    if (!content || content.trim().length < 100) {
      message.warning('章节内容太少，无法生成合适的章节名')
      return
    }

    try {
      setIsGeneratingName(true)

      // 取章节开头和主要内容用于分析
      const contentPreview = content.slice(0, 1500)

      const prompt = `# 任务

请为下面这一章节内容创作 5 个高质量的中文标题。

# 输入章节内容

${contentPreview}${content.length > 1500 ? '\n...(内容较长，已截取前1500字)' : ''}

# 核心要求

1. 标题要大致概括本章的核心情节或情绪，并具有一定吸引力。
2. 风格不限，可以自由发挥，只需符合通俗小说的阅读习惯。
3. 字数一般控制在 4-15 个字左右即可，不必死板卡字数。

# 输出格式

只输出 5 行标题，每行一个标题。
不要编号，不要解释，不要附加其他任何文字或符号。`

      const aiConfig = getAIConfigOrThrow()

      let apiUrl = (aiConfig.baseUrl || '').trim()
      if (!apiUrl) {
        const providerUrls: Record<string, string> = {
          deepseek: 'https://api.deepseek.com/v1/chat/completions',
          qwen: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
          kimi: 'https://api.moonshot.cn/v1/chat/completions',
          openai: 'https://api.openai.com/v1/chat/completions',
          custom: ''
        }
        apiUrl = providerUrls[aiConfig.provider] || 'https://api.openai.com/v1/chat/completions'
      } else {
        if (!/chat\/completions/.test(apiUrl)) {
          apiUrl = apiUrl.replace(/\/v1\/?$/, '')
          apiUrl = apiUrl.replace(/\/+$/, '')
          apiUrl = `${apiUrl}/v1/chat/completions`
        }
      }

      const response = await fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${aiConfig.apiKey}`,
        },
        body: JSON.stringify({
          model: aiConfig.model,
          messages: [
            {
              role: 'system',
              content:
                '你是一个擅长为中文网络小说章节取名的助手，请根据用户提供的章节内容，给出5个有吸引力且贴合剧情的章节标题。',
            },
            {
              role: 'user',
              content: prompt,
            },
          ],
        }),
      })

      if (!response.ok) {
        throw new Error(`AI生成失败: ${response.status} ${response.statusText}`)
      }

      const data = await response.json()
      const aiResponse: string =
        data?.choices?.[0]?.message?.content ||
        data?.choices?.[0]?.text ||
        ''

      const names = aiResponse
        .split('\n')
        .map((line: string) => line.trim())
        .filter((line: string) => line && line.length > 0 && !line.match(/^[\d\.、]+/))
        .slice(0, 5)

      if (names.length === 0) {
        throw new Error('未能生成有效的章节标题')
      }

      setGeneratedNames(names)
      message.success(`成功生成${names.length}个章节标题`)
    } catch (error: any) {
      console.error('生成章节名失败:', error)
      message.error(error?.message || '生成失败，请稍后重试')
    } finally {
      setIsGeneratingName(false)
    }
  }

  const closeNameModal = () => {
    setNameModalVisible(false)
    setGeneratedNames([])
  }

  // 键盘快捷键
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.ctrlKey || e.metaKey) {
      switch (e.key.toLowerCase()) {
        case 'f':
          e.preventDefault()
          {
            const next = !searchReplaceVisible
            setSearchReplaceVisible(next)
            if (next) {
              // [31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m[31m[1m[0m[39m[22m[0m // 打开搜索时，隐藏AI润色
              setShowPolishButton(false)
              setSelectionInfo(null)
            }
          }
          break
        case 'b':
          e.preventDefault()
          handleFormat('bold')
          break
        case 'i':
          e.preventDefault()
          handleFormat('italic')
          break
        case 'k':
          e.preventDefault()
          handleFormat('link')
          break
        default:
          break
      }
    }
  }

  // 插入 Markdown 格式
  const insertMarkdown = (before: string, after: string = '') => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = content.substring(start, end)
    const newText = content.substring(0, start) + before + selectedText + after + content.substring(end)

    setContent(newText)
    onChangeContent(newText)

    // 恢复光标位置
    setTimeout(() => {
      textarea.focus()
      const newCursorPos = start + before.length + selectedText.length
      textarea.setSelectionRange(newCursorPos, newCursorPos)
    }, 0)
  }

  const handleFormat = (format: string) => {
    switch (format) {
      case 'bold':
        insertMarkdown('**', '**')
        break
      case 'italic':
        insertMarkdown('*', '*')
        break
      case 'strikethrough':
        insertMarkdown('~~', '~~')
        break
      case 'code':
        insertMarkdown('`', '`')
        break
      case 'link':
        insertMarkdown('[', '](url)')
        break
      case 'h1':
        insertMarkdown('# ')
        break
      case 'h2':
        insertMarkdown('## ')
        break
      case 'h3':
        insertMarkdown('### ')
        break
      case 'quote':
        insertMarkdown('> ')
        break
      case 'list':
        insertMarkdown('- ')
        break
      case 'ordered-list':
        insertMarkdown('1. ')
        break
      default:
        break
    }
  }

  const wordCount = content.replace(/\s+/g, '').length

  // 格式化工具函数
  const handleIndent = () => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = content.substring(start, end)

    if (selectedText) {
      // 为选中文本添加段落缩进
      const indentedText = selectedText.split('\n').map(line =>
        line.trim() ? '　　' + line.replace(/^　　/, '') : line
      ).join('\n')

      const newContent = content.substring(0, start) + indentedText + content.substring(end)
      setContent(newContent)
      onChangeContent(newContent)
    } else {
      // 在光标位置插入段落缩进
      const newContent = content.substring(0, start) + '　　' + content.substring(start)
      setContent(newContent)
      onChangeContent(newContent)
      // 设置光标位置
      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 2
        textarea.focus()
      }, 0)
    }
  }

  const handleDialogQuote = () => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = content.substring(start, end)

    if (selectedText) {
      const dialogText = `"${selectedText}"`
      const newContent = content.substring(0, start) + dialogText + content.substring(end)
      setContent(newContent)
      onChangeContent(newContent)
      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + dialogText.length
        textarea.focus()
      }, 0)
    } else {
      const dialogText = '""'
      const newContent = content.substring(0, start) + dialogText + content.substring(start)
      setContent(newContent)
      onChangeContent(newContent)
      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 1
        textarea.focus()
      }, 0)
    }
  }

  const handleNewParagraph = () => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const newContent = content.substring(0, start) + '\n\n　　' + content.substring(start)
    setContent(newContent)
    onChangeContent(newContent)
    setTimeout(() => {
      textarea.selectionStart = textarea.selectionEnd = start + 3
      textarea.focus()
    }, 0)
  }

  const handleOneKeyFormat = () => {
    if (!content || content.trim() === '') {
      message.warning('请先输入或生成内容')
      return
    }
    const textarea = textareaRef.current
    const source = content
    if (textarea) {
      const start = textarea.selectionStart
      const end = textarea.selectionEnd
      if (start !== end) {
        const selected = source.substring(start, end)
        const formatted = formatChineseSentences(selected)
        const newContent = source.substring(0, start) + formatted + source.substring(end)
        setContent(newContent)
        onChangeContent(newContent)
        setTimeout(() => {
          textarea.selectionStart = start
          textarea.selectionEnd = start + formatted.length
          textarea.focus()
        }, 0)
      } else {
        const formatted = formatChineseSentences(source)
        setContent(formatted)
        onChangeContent(formatted)
        setTimeout(() => {
          textarea.focus()
        }, 0)
      }
    } else {
      const formatted = formatChineseSentences(source)
      setContent(formatted)
      onChangeContent(formatted)
    }
    message.success('格式化完成')
  }

  // 一键格式化函数（优化版：对话内容不换行）
  const formatChineseSentences = (input: string): string => {
    if (!input) return ''
    
    let text = input.replace(/\r\n?/g, '\n')
    let result = ''
    let inQuote = false // 是否在引号内
    let currentLine = ''
    
    // 左引号字符集（只包括双引号和书名号）
    const leftQuotes = '\u201c\u2018\u300c\u300e'  // “‘「『
    // 右引号字符集  
    const rightQuotes = '\u201d\u2019\u300d\u300f' // ”’」』
    // 句子结尾标点
    const endMarks = '。？！'
    // 所有中文标点符号（用于检查引号后是否跟标点）
    const allPunctuation = '。？！，、；：…—'
    
    for (let i = 0; i < text.length; i++) {
      const char = text[i]
      currentLine += char
      
      // 检测左引号（进入引号）
      if (leftQuotes.includes(char)) {
        inQuote = true
      }
      // 检测右引号（离开引号）
      else if (rightQuotes.includes(char)) {
        inQuote = false
        // 检查右引号后是否紧跟任何标点符号
        const nextChar = i + 1 < text.length ? text[i + 1] : ''
        if (allPunctuation.includes(nextChar)) {
          // 如果后面是任何标点，不换行，继续累积
          // 例如：“降维打击”。 或 “你好”，
        } else {
          // 右引号后没有标点，才换行（独立对话）
          result += currentLine.trim() + '\n'
          currentLine = ''
        }
      }
      // 检测句子结尾标点
      else if (endMarks.includes(char)) {
        // 只有在引号外才换行
        if (!inQuote) {
          // 检查后面（跳过换行符和空格）是否有引号
          let j = i + 1
          while (j < text.length && (text[j] === '\n' || text[j] === ' ' || text[j] === '\r')) {
            j++
          }
          const nextNonWhitespace = j < text.length ? text[j] : ''
          
          // 如果后面紧跟引号，不换行，继续累积
          if (rightQuotes.includes(nextNonWhitespace)) {
            // 例如：这一档了吧？\n"
          } else {
            // 后面没有引号，正常换行
            result += currentLine.trim() + '\n'
            currentLine = ''
          }
        }
        // 引号内不换行，继续累积
      }
      // 检测省略号
      else if (char === '…') {
        // 检查是否是连续的省略号
        if (i + 1 < text.length && text[i + 1] === '…') {
          currentLine += text[i + 1]
          i++ // 跳过下一个省略号
        }
        // 只有在引号外才换行
        if (!inQuote) {
          // 检查后面（跳过换行符和空格）是否有引号
          let j = i + 1
          while (j < text.length && (text[j] === '\n' || text[j] === ' ' || text[j] === '\r')) {
            j++
          }
          const nextNonWhitespace = j < text.length ? text[j] : ''
          
          // 如果后面紧跟引号，不换行，继续累积
          if (rightQuotes.includes(nextNonWhitespace)) {
            // 例如：天啊…\n"
          } else {
            // 后面没有引号，正常换行
            result += currentLine.trim() + '\n'
            currentLine = ''
          }
        }
      }
      // 已有的换行符
      else if (char === '\n') {
        // 如果在引号内，将换行符替换为空格，不换行
        if (inQuote) {
          currentLine = currentLine.slice(0, -1) + ' ' // 移除换行符，添加空格
        } else {
          // 引号外，保留换行
          if (currentLine.trim().length > 1) { // 排除只有换行符的情况
            result += currentLine.trim() + '\n'
          }
          currentLine = ''
        }
      }
    }
    
    // 添加剩余内容
    if (currentLine.trim()) {
      result += currentLine.trim()
    }
    
    // 清理：移除多余的连续换行（超过2个）
    result = result.replace(/\n{3,}/g, '\n\n')
    
    return result.trim()
  }

  // 搜索替换功能
  const handleSearch = useCallback(() => {
    if (!searchText) {
      setMatches([])
      setCurrentMatchIndex(0)
      return
    }

    const searchStr = caseSensitive ? searchText : searchText.toLowerCase()
    const contentStr = caseSensitive ? content : content.toLowerCase()
    const foundMatches: Array<{ start: number; end: number }> = []

    let index = contentStr.indexOf(searchStr)
    while (index !== -1) {
      foundMatches.push({ start: index, end: index + searchText.length })
      index = contentStr.indexOf(searchStr, index + 1)
    }

    setMatches(foundMatches)
    setCurrentMatchIndex(0)

    if (foundMatches.length > 0 && textareaRef.current) {
      const first = foundMatches[0]
      isProgrammaticSelection.current = true // 标记为程序自动选择
      // 搜索定位时不显示AI润色按钮，也清空选区信息
      setShowPolishButton(false)
      setSelectionInfo(null)
      textareaRef.current.focus()
      textareaRef.current.setSelectionRange(first.start, first.end)
      
      // 延迟重置标记，确保所有事件处理完成
      setTimeout(() => {
        isProgrammaticSelection.current = false
      }, 100)
    }
  }, [searchText, caseSensitive, content])

  // 自动搜索
  useEffect(() => {
    if (searchReplaceVisible) {
      handleSearch()
    }
  }, [searchText, caseSensitive, searchReplaceVisible, handleSearch])

  const handleNextMatch = () => {
    if (matches.length === 0) return
    const nextIndex = (currentMatchIndex + 1) % matches.length
    setCurrentMatchIndex(nextIndex)

    if (textareaRef.current) {
      const match = matches[nextIndex]
      isProgrammaticSelection.current = true // 标记为程序自动选择
      // 搜索定位时不显示AI润色按钮，并清空选区
      setShowPolishButton(false)
      setSelectionInfo(null)
      textareaRef.current.focus()
      textareaRef.current.setSelectionRange(match.start, match.end)

      // 延迟重置标记
      setTimeout(() => {
        isProgrammaticSelection.current = false
      }, 100)
    }
  }

  const handlePrevMatch = () => {
    if (matches.length === 0) return
    const prevIndex = currentMatchIndex === 0 ? matches.length - 1 : currentMatchIndex - 1
    setCurrentMatchIndex(prevIndex)

    if (textareaRef.current) {
      const match = matches[prevIndex]
      isProgrammaticSelection.current = true // 标记为程序自动选择
      // 搜索定位时不显示AI润色按钮，并清空选区
      setShowPolishButton(false)
      setSelectionInfo(null)
      textareaRef.current.focus()
      textareaRef.current.setSelectionRange(match.start, match.end)

      // 延迟重置标记
      setTimeout(() => {
        isProgrammaticSelection.current = false
      }, 100)
    }
  }

  const handleReplace = () => {
    if (matches.length === 0) return
    const match = matches[currentMatchIndex]
    const newContent = content.substring(0, match.start) + replaceText + content.substring(match.end)
    setContent(newContent)
    onChangeContent(newContent)

    // 重新搜索
    setTimeout(() => handleSearch(), 100)
  }

  const handleReplaceAll = () => {
    if (!searchText || matches.length === 0) return

    // 从后往前替换，避免索引偏移
    let newContent = content
    for (let i = matches.length - 1; i >= 0; i--) {
      const match = matches[i]
      newContent = newContent.substring(0, match.start) + replaceText + newContent.substring(match.end)
    }

    setContent(newContent)
    onChangeContent(newContent)
    setMatches([])
    setCurrentMatchIndex(0)
    message.success(`已替换 ${matches.length} 处`)
  }

  const openSearchReplace = () => {
    setSearchReplaceVisible(true)
    setSearchText('')
    setReplaceText('')
    setMatches([])
    setCurrentMatchIndex(0)

    // 计算搜索面板位置
    setTimeout(() => {
      if (searchButtonRef.current) {
        const rect = searchButtonRef.current.getBoundingClientRect()
        setSearchPanelPosition({
          top: rect.bottom,
          left: rect.left
        })
      }
    }, 0)
  }

  const closeSearchReplace = () => {
    setSearchReplaceVisible(false)
    setSearchText('')
    setReplaceText('')
    setMatches([])
    setCurrentMatchIndex(0)
  }

  // 渲染覆盖层内容
  const renderOverlayContent = () => {
    // 优先级 1: 搜索高亮
    if (searchReplaceVisible && matches.length > 0) {
      const result = []
      let lastIndex = 0
      matches.forEach((match, index) => {
        // 匹配前的文本
        if (match.start > lastIndex) {
          result.push(content.substring(lastIndex, match.start))
        }
        // 匹配文本
        const isCurrent = index === currentMatchIndex
        result.push(
          <span
            key={`match-${index}`}
            className={`search-highlight ${isCurrent ? 'current' : ''}`}
            data-match-index={index}
          >
            {content.substring(match.start, match.end)}
          </span>
        )
        lastIndex = match.end
      })
      // 剩余文本
      if (lastIndex < content.length) {
        result.push(content.substring(lastIndex))
      }
      return <div className="editor-overlay-content">{result}</div>
    }

    // 优先级 2: 选区 (润色)
    if (selectionInfo && showPolishButton) {
      return (
        <div className="editor-overlay-content">
          {content.slice(0, selectionInfo.start)}
          <span className="selection-highlight">
            {selectionInfo.text}
          </span>
          <span className="selection-toolbar-inline">
            <span className="selection-word-count">已选 {selectionInfo.text.replace(/\s/g, '').length} 字</span>
            <button
              type="button"
              className="selection-polish-button"
              onClick={openPolishModal}
            >
              AI重写
            </button>
          </span>
          {content.slice(selectionInfo.end)}
        </div>
      )
    }

    return null
  }

  // 自动滚动到当前搜索匹配项
  useEffect(() => {
    if (!searchReplaceVisible || matches.length === 0) return

    // 使用 setTimeout 等待渲染更新
    const timer = setTimeout(() => {
      const overlay = overlayRef.current
      if (!overlay) return

      const currentHighlight = overlay.querySelector(`[data-match-index="${currentMatchIndex}"]`) as HTMLElement
      if (currentHighlight && textareaRef.current) {
        const textarea = textareaRef.current
        const textareaHeight = textarea.clientHeight
        // 计算高亮元素相对于覆盖层内容的偏移
        // 注意：overlayContent 受到 transform 影响，但 offsetTop 是相对于父级（overlayContent）的
        // 我们需要的是它在文档流中的相对位置
        const highlightTop = currentHighlight.offsetTop
        const highlightHeight = currentHighlight.offsetHeight

        // 计算目标滚动位置：将高亮元素置于可视区域中间
        let newScrollTop = highlightTop - (textareaHeight / 2) + (highlightHeight / 2)
        
        // 边界检查
        newScrollTop = Math.max(0, Math.min(newScrollTop, textarea.scrollHeight - textareaHeight))
        
        textarea.scrollTop = newScrollTop
      }
    }, 50)

    return () => clearTimeout(timer)
  }, [currentMatchIndex, searchReplaceVisible, matches])

  // 更新搜索面板位置
  useEffect(() => {
    if (searchReplaceVisible) {
      const updatePosition = () => {
        if (searchButtonRef.current) {
          const rect = searchButtonRef.current.getBoundingClientRect()
          setSearchPanelPosition({
            top: rect.bottom,
            left: rect.left
          })
        }
      }

      updatePosition()
      window.addEventListener('resize', updatePosition)
      window.addEventListener('scroll', updatePosition, true)

      return () => {
        window.removeEventListener('resize', updatePosition)
        window.removeEventListener('scroll', updatePosition, true)
      }
    }
  }, [searchReplaceVisible])

  return (
    <div className="editor-panel">
      {document ? (
        <div className="editor-container">
          {/* 标题栏 */}
          <div className="editor-header">
            <div className="editor-header-top">
              <h1 className="editor-title">{document.title}</h1>
              <div className="editor-header-right">
                <div className="editor-actions">
                  <span className="word-count">字数: {wordCount}</span>
                  <button className="action-btn ai-action-btn" onClick={openNameModal} title="AI生成章节标题">

                    <span>章节取名</span>
                  </button>
                  <button className="action-btn ai-action-btn" onClick={onStreamlineContent}>AI精简</button>
                  <button className="action-btn ai-action-btn" onClick={onReviewManuscript}>AI审稿</button>
                  <button className="action-btn ai-action-btn" onClick={onRemoveAITrace}>AI消痕</button>
                </div>
                <div className="outline-buttons"> 
                  <button className="outline-btn" onClick={onShowOutline}>
                    <FileTextOutlined style={{ marginRight: 4, fontSize: 14 }} />
                    <span>大纲</span>
                  </button>
                  <button className="outline-btn" onClick={onShowVolumeOutline}>
                    <BarChartOutlined style={{ marginRight: 4, fontSize: 14 }} />
                    <span>卷大纲</span>
                  </button>
                  <button className="outline-btn" onClick={onShowSummary}>
                    <FileTextOutlined style={{ marginRight: 4, fontSize: 14 }} />
                    <span>概要</span>
                  </button>
                  {onShowHistory && (
                    <button className="outline-btn" onClick={onShowHistory}>
                      <HistoryOutlined style={{ marginRight: 4, fontSize: 14 }} />
                      <span>历史</span>
                    </button>
                  )}
                </div>
                {lastSaveTime && (
                  <div className="save-time-display">
                    {isSaving ? '保存中...' : `最后保存: ${lastSaveTime}`}
                  </div>
                )}
              </div>
            </div>

          </div>

          {/* 格式化工具栏 */}
          <div className="editor-format-toolbar">
            <div className="toolbar-label">
              <FormOutlined style={{ marginRight: 6 }} />
              格式工具
            </div>
            <button className="format-btn" onClick={handleIndent}>段落缩进</button>
            <button className="format-btn" onClick={handleDialogQuote}>对话引号</button>
            <button className="format-btn" onClick={handleNewParagraph}>新段落</button>
            <button className="format-btn format-btn-primary" onClick={handleOneKeyFormat}>一键格式化</button>
            <button
              className="format-btn"
              onClick={openProofreadModal}
              title="AI智能纠错"
            >
              <SearchOutlined style={{ marginRight: 6 }} />
              智能纠错
            </button>
            <button
              className="format-btn"
              onClick={openSuggestionModal}
              title="AI智能建议"
            >
              <BulbOutlined style={{ marginRight: 6 }} />
              智能建议
            </button>
            <button
              className="format-btn"
              onClick={() => {
                if (!content || content.trim().length < 10) {
                  message.warning('内容太少，无需智能修改')
                  return
                }
                setSmartEditModalVisible(true)
              }}
            >
              智能修改
            </button>
            <button
              ref={searchButtonRef}
              className={`format-btn ${searchReplaceVisible ? 'format-btn-active' : ''}`}
              onClick={() => {
                const next = !searchReplaceVisible
                setSearchReplaceVisible(next)
                if (next) {
                  // 打开搜索时，隐藏AI润色按钮并清空选区信息
                  setShowPolishButton(false)
                  setSelectionInfo(null)
                }
              }}
              title="搜索替换 (Ctrl+F)"
            >
              <SearchOutlined />
            </button>
          </div>

          {/* 搜索替换浮动框 - 独立浮动层 */}
          {searchReplaceVisible && (
            <div
              className="search-replace-panel"
              style={{
                top: `${searchPanelPosition.top}px`,
                left: `${searchPanelPosition.left}px`
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="search-panel-header">
                <span className="search-panel-title">查找替换</span>
                <button className="search-close-btn" onClick={closeSearchReplace}>×</button>
              </div>
              <div className="search-panel-body">
                <div className="search-input-group">
                  <Input
                    size="small"
                    value={searchText}


                    onChange={(e) => setSearchText(e.target.value)}
                    onPressEnter={handleSearch}
                    placeholder="查找"
                    autoFocus
                  />
                  <div className="search-controls">
                    <Button size="small" onClick={handlePrevMatch} disabled={matches.length === 0} title="上一个">
                      ↑
                    </Button>
                    <Button size="small" onClick={handleNextMatch} disabled={matches.length === 0} title="下一个">
                      ↓
                    </Button>
                    <span className="search-count">
                      {matches.length > 0 ? `${currentMatchIndex + 1}/${matches.length}` : '0/0'}
                    </span>
                  </div>
                </div>
                <div className="search-input-group">
                  <Input
                    size="small"
                    value={replaceText}
                    onChange={(e) => setReplaceText(e.target.value)}
                    placeholder="替换为"
                  />
                  <div className="search-controls">
                    <Button size="small" onClick={handleReplace} disabled={matches.length === 0}>
                      替换
                    </Button>
                    <Button size="small" onClick={handleReplaceAll} disabled={matches.length === 0}>
                      全部
                    </Button>
                  </div>
                </div>
                <div className="search-options">
                  <label className="search-option-label">
                    <input
                      type="checkbox"
                      checked={caseSensitive}
                      onChange={(e) => setCaseSensitive(e.target.checked)}
                    />
                    <span>区分大小写</span>
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* 编辑区域 */}
          <div className="editor-content-wrapper">


            <textarea
              ref={textareaRef}
              className="editor-textarea"
              value={content}
              onChange={handleContentChange}
              onKeyDown={handleKeyDown}
              onKeyUp={handleKeyUp}
              onMouseUp={handleMouseUp}
              onSelect={handleSelect}
              placeholder="在此输入内容，支持 Markdown 格式...&#10;&#10;例如：&#10;# 标题&#10;**粗体** *斜体*&#10;- 列表项&#10;&#10;快捷键：Ctrl+B 粗体，Ctrl+I 斜体，Ctrl+K 链接"
              spellCheck={false}
            />
            {/* 文本覆盖层 - 显示选中文本和按钮 */}
            <div ref={overlayRef} className="editor-overlay">
              {renderOverlayContent()}
            </div>
          </div>
        </div>
      ) : (
        <div className="editor-empty-state">
          <div className="empty-placeholder">
            <svg className="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <p>请选择左侧的文档开始编辑</p>
          </div>
        </div>
      )}
      <Modal
        open={polishModalVisible}
        title={
          <div style={{ fontSize: '18px', fontWeight: 700, color: '#1e293b', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <HighlightOutlined style={{ fontSize: 20, color: '#7c3aed' }} />
            <span>AI重写助手</span>
          </div>
        }
        width={880}
        onCancel={closePolishModal}
        footer={[
          <Button key="cancel" onClick={closePolishModal} size="large" style={{ borderRadius: '8px' }}>
            取消
          </Button>,
          <Button
            key="polish"
            type="primary"
            loading={isPolishing}
            onClick={handlePolishSubmit}
            size="large"
            style={{
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
              border: 'none',
              fontWeight: 600
            }}
          >
            生成润色
          </Button>,
          <Button
            key="apply"
            type="primary"
            disabled={!polishResult || isPolishing}
            onClick={handleApplyPolish}
            size="large"
            style={{
              borderRadius: '8px',
              background: polishResult && !isPolishing ? 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)' : undefined,
              border: 'none',
              fontWeight: 600
            }}
          >
            应用到正文
          </Button>,
        ]}
        destroyOnClose
        centered
        maskClosable={false}
        styles={{
          body: { paddingTop: '24px', paddingBottom: '24px' },
          header: { paddingBottom: '20px', borderBottom: '2px solid #e2e8f0' }
        }}
      >
        <div className="polish-modal-body">
          <div className="polish-instruction">
            <div className="polish-section-title">修改要求</div>
            <Input.TextArea
              autoSize={{ minRows: 3, maxRows: 6 }}
              placeholder="希望怎么改？（AI将强制尝试换一种完全不同的写法，如有特殊要求请在此补充）"
              value={polishInstructions}
              onChange={(e) => setPolishInstructions(e.target.value)}
              style={{
                borderRadius: '10px',
                fontSize: '14px',
                padding: '12px 14px',
                border: '2px solid #e2e8f0',
                transition: 'all 0.25s ease'
              }}
            />
          </div>
          <div className="polish-result-columns">
            <div className="polish-column">
              <div className="polish-column-title">原文片段</div>
              <div className="polish-text-preview">{selectionInfo?.text || '（当前未选中文本）'}</div>
            </div>
            <div className="polish-column">
              <div className="polish-column-title">AI润色结果</div>
              <div className="polish-text-preview polish-result">
                {isPolishing ? (
                  <div className="polish-loading">
                    <Spin />
                    <span>AI正在润色...</span>
                  </div>
                ) : polishResult ? (
                  polishResult
                ) : (
                  '点击“生成润色”后展示结果'
                )}
              </div>
            </div>
          </div>
        </div>
      </Modal>

      <Modal
        title="AI智能修改"
        open={smartEditModalVisible}
        width={900}
        onCancel={() => {
          if (!isSmartEditing) {
            setSmartEditModalVisible(false)
          }
        }}
        footer={[
          <Button
            key="cancel"
            onClick={() => {
              if (!isSmartEditing) {
                setSmartEditModalVisible(false)
              }
            }}
            size="large"
            style={{ borderRadius: '8px' }}
          >
            取消
          </Button>,
          <Button
            key="smartEdit"
            type="primary"
            loading={isSmartEditing}
            onClick={handleSmartEdit}
            size="large"
            style={{
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #6366f1 0%, #4f46e5 100%)',
              border: 'none',
              fontWeight: 600,
            }}
          >
            开始智能修改
          </Button>,
        ]}
        centered
        maskClosable={!isSmartEditing}
        styles={{
          body: { padding: '24px', maxHeight: '600px', overflowY: 'auto' },
        }}
      >
        <div>
          <div
            style={{
              marginBottom: 16,
              fontSize: 14,
              color: '#64748b',
              lineHeight: 1.7,
            }}
          >
            只会根据你填写的「修改要求」对相关片段做最小修改，其他内容一个字都不会动（包括标点和换行），不新增也不删减剧情。
          </div>
          <Input.TextArea
            rows={4}
            value={smartEditInstructions}
            onChange={(e) => setSmartEditInstructions(e.target.value)}
            placeholder="示例：\n- 把第一人称改成第三人称，但保留原有剧情和对白\n- 只调整错别字和明显语病，不要改动句子结构\n- 保持人物称呼、世界观设定完全不变，只加强情绪表达"
          />
        </div>
      </Modal>

      {/* 章节取名弹窗 */}
      <Modal
        open={nameModalVisible}
        title={
          <div style={{ fontSize: '18px', fontWeight: 700, color: '#1e293b', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <FileTextOutlined style={{ fontSize: 20, color: '#2563eb' }} />
            <span>AI章节取名</span>
          </div>
        }
        width={680}
        onCancel={closeNameModal}
        footer={[
          <Button key="cancel" onClick={closeNameModal} size="large" style={{ borderRadius: '8px' }}>
            取消
          </Button>,
          <Button
            key="generate"
            type="primary"
            loading={isGeneratingName}
            onClick={handleGenerateNames}
            size="large"
            style={{
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
              border: 'none',
              fontWeight: 600
            }}
          >
            生成标题
          </Button>,
        ]}
        destroyOnClose
        centered
        maskClosable={false}
      >
        <div style={{ padding: '16px 0' }}>
          <div style={{ marginBottom: '24px' }}>
            <div style={{
              fontSize: '14px',
              color: '#64748b',
              marginBottom: '12px',
              lineHeight: '1.6'
            }}>
              AI 将根据章节内容，为您生成 5 个简洁有力的标题建议。<br/>
              标题将突出核心情节，符合网文风格。
            </div>
            <div style={{
              padding: '12px 16px',
              background: '#f8fafc',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              fontSize: '13px',
              color: '#475569'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <BarChartOutlined style={{ color: '#2563eb' }} />
                <span>章节字数：<strong>{content.length}</strong> 字</span>
              </div>
            </div>
          </div>

          {isGeneratingName && (
            <div style={{
              textAlign: 'center',
              padding: '40px 20px',
              color: '#64748b'
            }}>
              <Spin size="large" />
              <div style={{ marginTop: '16px', fontSize: '14px' }}>AI 正在分析章节内容...</div>
            </div>
          )}

          {!isGeneratingName && generatedNames.length > 0 && (
            <div>
              <div style={{
                fontSize: '14px',
                fontWeight: 600,
                color: '#1e293b',
                marginBottom: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}>
                <BulbOutlined style={{ color: '#f97316' }} />
                <span>生成的标题建议</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {generatedNames.map((name, index) => (
                  <div
                    key={index}
                    style={{
                      padding: '14px 16px',
                      background: 'linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)',
                      border: '2px solid #bae6fd',
                      borderRadius: '10px',
                      cursor: 'pointer',
                      transition: 'all 0.2s ease',
                      fontSize: '15px',
                      fontWeight: 500,
                      color: '#0c4a6e',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between'
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.transform = 'translateX(4px)'
                      e.currentTarget.style.borderColor = '#0ea5e9'
                      e.currentTarget.style.background = 'linear-gradient(135deg, #e0f2fe 0%, #bae6fd 100%)'
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'translateX(0)'
                      e.currentTarget.style.borderColor = '#bae6fd'
                      e.currentTarget.style.background = 'linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)'
                    }}
                    onClick={() => {
                      navigator.clipboard.writeText(name)
                      message.success(`已复制标题：${name}`)
                    }}
                  >
                    <span>{name}</span>
                    <span style={{ fontSize: '12px', color: '#0891b2' }}>点击复制</span>
                  </div>
                ))}
              </div>
              <div style={{
                marginTop: '16px',
                fontSize: '13px',
                color: '#64748b',
                textAlign: 'center'
              }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', justifyContent: 'center' }}>
                  <BulbOutlined />
                  <span>点击任意标题即可复制</span>
                </span>
              </div>
            </div>
          )}

          {!isGeneratingName && generatedNames.length === 0 && (
            <div style={{
              textAlign: 'center',
              padding: '40px 20px',
              color: '#94a3b8'
            }}>
              <div style={{ fontSize: '48px', marginBottom: '16px' }}><FileTextOutlined /></div>
              <div style={{ fontSize: '14px' }}>点击"生成标题"按钮开始</div>
            </div>
          )}
        </div>
      </Modal>

      {/* AI纠错弹窗 */}
      <Modal
        title={
          <div style={{
            fontSize: '18px',
            fontWeight: 600,
            color: '#1e293b',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <SearchOutlined style={{ color: '#0f172a' }} />
            <span>AI智能纠错</span>
          </div>
        }
        open={proofreadModalVisible}
        width={900}
        onCancel={closeProofreadModal}
        footer={[
          <Button key="cancel" onClick={closeProofreadModal} size="large" style={{ borderRadius: '8px' }}>
            关闭
          </Button>,
          <Button
            key="check"
            type="primary"
            loading={isProofreading}
            onClick={handleProofread}
            size="large"
            disabled={proofreadErrors.length > 0}
            style={{
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
              border: 'none',
              fontWeight: 600
            }}
          >
            开始检查
          </Button>,
          <Button
            key="applyAll"
            type="primary"
            disabled={proofreadErrors.length === 0 || proofreadErrors.every(e => e.applied)}
            onClick={handleApplyAllErrors}
            size="large"
            style={{
              borderRadius: '8px',
              background: proofreadErrors.length > 0 && !proofreadErrors.every(e => e.applied)
                ? 'linear-gradient(135deg, #10b981 0%, #059669 100%)'
                : undefined,
              border: 'none',
              fontWeight: 600
            }}
          >
            ✅ 应用全部
          </Button>,
        ]}
        centered
        maskClosable={false}
        styles={{
          body: { padding: '24px', maxHeight: '600px', overflowY: 'auto' }
        }}
      >
        <div>
          {isProofreading && (
            <div style={{
              textAlign: 'center',
              padding: '60px 20px',
              color: '#64748b'
            }}>
              <Spin size="large" />
              <div style={{ marginTop: '20px', fontSize: '15px' }}>
                AI正在检查文本，请稍候...
              </div>
            </div>
          )}

          {!isProofreading && proofreadErrors.length > 0 && (
            <div>
              <div style={{
                fontSize: '14px',
                fontWeight: 600,
                color: '#1e293b',
                marginBottom: '16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
                <span>发现 {proofreadErrors.filter(e => !e.applied).length} 个可能的错误</span>
                <span style={{ fontSize: '12px', color: '#64748b', fontWeight: 400 }}>
                  已修复 {proofreadErrors.filter(e => e.applied).length} 个
                </span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {proofreadErrors.map((error, index) => (
                  <div
                    key={index}
                    style={{
                      padding: '16px',
                      background: error.applied ? '#f0fdf4' : '#fef2f2',
                      border: `2px solid ${error.applied ? '#86efac' : '#fecaca'}`,
                      borderRadius: '10px',
                      opacity: error.applied ? 0.6 : 1,
                      transition: 'all 0.3s ease'
                    }}
                  >
                    <div style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      justifyContent: 'space-between',
                      marginBottom: '12px'
                    }}>
                      <div style={{ flex: 1 }}>
                        <div style={{
                          fontSize: '12px',
                          color: '#64748b',
                          marginBottom: '8px',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '8px'
                        }}>
                          <span style={{
                            padding: '2px 8px',
                            background: error.type === 'typo' ? '#dbeafe' :
                                       error.type === 'name' ? '#fce7f3' :
                                       error.type === 'garbled' ? '#fee2e2' :
                                       error.type === 'punctuation' ? '#fef3c7' : '#e0e7ff',
                            borderRadius: '4px',
                            fontSize: '11px',
                            fontWeight: 600
                          }}>
                            {error.type === 'typo' ? '错别字' :
                             error.type === 'name' ? '名称错误' :
                             error.type === 'garbled' ? '乱码' :
                             error.type === 'punctuation' ? '标点' : '其他'}
                          </span>
                          {error.applied && (
                            <span style={{ color: '#10b981', fontSize: '12px' }}>✓ 已修复</span>
                          )}
                        </div>

                        <div style={{ marginBottom: '8px' }}>
                          <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '4px' }}>
                            原文：
                          </div>
                          <div style={{
                            padding: '8px 12px',
                            background: '#fff',
                            borderRadius: '6px',
                            fontSize: '14px',
                            color: '#dc2626',
                            textDecoration: 'line-through'
                          }}>
                            {error.original}
                          </div>
                        </div>

                        <div style={{ marginBottom: '8px' }}>
                          <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '4px' }}>
                            建议修改为：
                          </div>
                          <div style={{
                            padding: '8px 12px',
                            background: '#fff',
                            borderRadius: '6px',
                            fontSize: '14px',
                            color: '#059669',
                            fontWeight: 500
                          }}>
                            {error.corrected}
                          </div>
                        </div>

                        <div style={{ marginBottom: '8px' }}>
                          <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '4px' }}>
                            上下文：
                          </div>
                          <div style={{
                            padding: '8px 12px',
                            background: '#f8fafc',
                            borderRadius: '6px',
                            fontSize: '13px',
                            color: '#475569',
                            lineHeight: '1.6'
                          }}>
                            {error.context}
                          </div>
                        </div>

                        {error.reason && (
                          <div style={{ fontSize: '12px', color: '#64748b', fontStyle: 'italic' }}>
                            <BulbOutlined /> {error.reason}
                          </div>
                        )}
                      </div>

                      <Button
                        type="primary"
                        size="small"
                        disabled={error.applied}
                        onClick={() => handleApplySingleError(index)}
                        style={{
                          marginLeft: '12px',
                          borderRadius: '6px',
                          background: error.applied ? undefined : 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                          border: 'none'
                        }}
                      >
                        {error.applied ? '已应用' : '应用'}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {!isProofreading && proofreadErrors.length === 0 && (
            <div style={{
              textAlign: 'center',
              padding: '60px 20px',
              color: '#94a3b8'
            }}>
              <div style={{ fontSize: '48px', marginBottom: '16px' }}><SearchOutlined /></div>
              <div style={{ fontSize: '15px', marginBottom: '8px' }}>点击"开始检查"按钮</div>
              <div style={{ fontSize: '13px', color: '#cbd5e1' }}>
                AI将检查错别字、名称错误、乱码等问题
              </div>
            </div>
          )}
        </div>
      </Modal>

      {/* AI智能建议模态框 */}
      <Modal
        title={
          <div style={{
            fontSize: '18px',
            fontWeight: 600,
            color: '#0f172a',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <BulbOutlined style={{ color: '#0f172a' }} />
            <span>AI智能建议</span>
          </div>
        }
        open={suggestionModalVisible}
        width={1000}
        onCancel={closeSuggestionModal}
        footer={[
          <Button
            key="close"
            onClick={closeSuggestionModal}
            size="large"
            style={{ borderRadius: '8px' }}
          >
            关闭
          </Button>,
          <Button
            key="analyze"
            type="primary"
            loading={isAnalyzingSuggestions}
            onClick={handleAnalyzeSuggestions}
            size="large"
            disabled={suggestions.length > 0}
            style={{
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
              border: 'none',
              boxShadow: '0 4px 12px rgba(59, 130, 246, 0.3)'
            }}
          >
            {isAnalyzingSuggestions ? '分析中...' : '开始分析'}
          </Button>,
          <Button
            key="applyAll"
            type="primary"
            disabled={suggestions.length === 0 || suggestions.every(s => s.applied)}
            onClick={handleApplyAllSuggestions}
            size="large"
            style={{
              borderRadius: '8px',
              background: suggestions.length > 0 && !suggestions.every(s => s.applied)
                ? 'linear-gradient(135deg, #10b981 0%, #059669 100%)'
                : undefined,
              border: 'none',
              boxShadow: suggestions.length > 0 && !suggestions.every(s => s.applied)
                ? '0 4px 12px rgba(16, 185, 129, 0.3)'
                : undefined
            }}
          >
            一键应用全部
          </Button>
        ]}
        styles={{
          body: { maxHeight: '600px', overflowY: 'auto', padding: '24px' }
        }}
      >
        <div>
          {isAnalyzingSuggestions && (
            <div style={{
              textAlign: 'center',
              padding: '60px 20px'
            }}>
              <Spin size="large" />
              <div style={{ marginTop: '20px', color: '#64748b', fontSize: '14px' }}>
                AI正在分析内容，寻找改进建议...
              </div>
            </div>
          )}

          {!isAnalyzingSuggestions && suggestions.length > 0 && (
            <div>
              <div style={{
                fontSize: '14px',
                fontWeight: 600,
                color: '#1e293b',
                marginBottom: '16px',
                padding: '12px 16px',
                background: 'linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)',
                borderRadius: '8px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
                <span>发现 {suggestions.filter(s => !s.applied).length} 条建议</span>
                <span style={{ fontSize: '12px', color: '#64748b', fontWeight: 400 }}>
                  已应用 {suggestions.filter(s => s.applied).length} 条
                </span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {suggestions.map((suggestion, index) => {
                  const typeColors: Record<string, { bg: string; text: string; label: string }> = {
                    grammar: { bg: '#fef3c7', text: '#92400e', label: '语法' },
                    logic: { bg: '#fecaca', text: '#991b1b', label: '逻辑' },
                    redundant: { bg: '#ddd6fe', text: '#5b21b6', label: '冗余' },
                    improvement: { bg: '#bfdbfe', text: '#1e40af', label: '改进' },
                    inconsistency: { bg: '#fecdd3', text: '#9f1239', label: '矛盾' },
                    style: { bg: '#d1fae5', text: '#065f46', label: '文风' }
                  }

                  const actionLabels: Record<string, string> = {
                    replace: '替换',
                    delete: '删除',
                    insert: '插入'
                  }

                  const severityColors: Record<string, { bg: string; text: string }> = {
                    high: { bg: '#fee2e2', text: '#dc2626' },
                    medium: { bg: '#fed7aa', text: '#ea580c' },
                    low: { bg: '#fef9c3', text: '#ca8a04' }
                  }

                  const typeInfo = typeColors[suggestion.type] || { bg: '#e2e8f0', text: '#475569', label: '其他' }
                  const severityInfo = severityColors[suggestion.severity] || { bg: '#e2e8f0', text: '#475569' }

                  return (
                    <div
                      key={index}
                      style={{
                        padding: '16px',
                        background: suggestion.applied ? '#f8fafc' : '#ffffff',
                        border: suggestion.applied ? '1px solid #e2e8f0' : '1px solid #cbd5e1',
                        borderRadius: '8px',
                        opacity: suggestion.applied ? 0.6 : 1,
                        transition: 'all 0.3s ease'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                            <span style={{
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '12px',
                              fontWeight: 500,
                              background: typeInfo.bg,
                              color: typeInfo.text
                            }}>
                              {typeInfo.label}
                            </span>
                            <span style={{
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '12px',
                              background: severityInfo.bg,
                              color: severityInfo.text
                            }}>
                              {suggestion.severity === 'high' ? '严重' : suggestion.severity === 'medium' ? '中等' : '轻微'}
                            </span>
                            <span style={{
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '12px',
                              background: '#f1f5f9',
                              color: '#475569'
                            }}>
                              {actionLabels[suggestion.action]}
                            </span>
                          </div>

                          <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '8px' }}>
                            {suggestion.context}
                          </div>

                          <div style={{ display: 'flex', gap: '12px', marginBottom: '8px' }}>
                            {suggestion.action !== 'insert' && (
                              <div style={{ flex: 1 }}>
                                <div style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '4px' }}>原文：</div>
                                <div style={{
                                  padding: '8px 12px',
                                  background: '#fef2f2',
                                  border: '1px solid #fecaca',
                                  borderRadius: '6px',
                                  fontSize: '13px',
                                  color: '#dc2626',
                                  textDecoration: suggestion.action === 'delete' ? 'line-through' : 'none'
                                }}>
                                  {suggestion.original}
                                </div>
                              </div>
                            )}
                            {suggestion.suggested && (
                              <div style={{ flex: 1 }}>
                                <div style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '4px' }}>
                                  {suggestion.action === 'insert' ? '插入内容：' : '建议：'}
                                </div>
                                <div style={{
                                  padding: '8px 12px',
                                  background: '#f0fdf4',
                                  border: '1px solid #bbf7d0',
                                  borderRadius: '6px',
                                  fontSize: '13px',
                                  color: '#16a34a'
                                }}>
                                  {suggestion.suggested}
                                </div>
                              </div>
                            )}
                          </div>

                          <div style={{
                            fontSize: '13px',
                            color: '#475569',
                            padding: '8px 12px',
                            background: '#f8fafc',
                            borderRadius: '6px',
                            borderLeft: '3px solid #3b82f6'
                          }}>
                            💡 {suggestion.reason}
                          </div>
                        </div>

                        <Button
                          type="primary"
                          size="small"
                          disabled={suggestion.applied}
                          onClick={() => handleApplySingleSuggestion(index)}
                          style={{
                            borderRadius: '6px',
                            background: suggestion.applied ? undefined : 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                            border: 'none',
                            minWidth: '80px'
                          }}
                        >
                          {suggestion.applied ? '已应用' : '应用'}
                        </Button>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          {!isAnalyzingSuggestions && suggestions.length === 0 && (
            <div style={{
              textAlign: 'center',
              padding: '60px 20px',
              color: '#94a3b8'
            }}>
              <div style={{ fontSize: '48px', marginBottom: '16px' }}><BulbOutlined /></div>
              <div style={{ fontSize: '15px', marginBottom: '8px' }}>点击"开始分析"按钮</div>
              <div style={{ fontSize: '13px', color: '#cbd5e1' }}>
                AI将分析内容并提供改进建议
              </div>
            </div>
          )}
        </div>
      </Modal>
    </div>
  )
}

export default EditorPanel
