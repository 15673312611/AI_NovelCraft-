import React, { useCallback, useEffect, useMemo, useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { Layout, Spin, message, Modal } from 'antd'
import FileTree from '@/components/writing/FileTree'
import EditorPanel from '@/components/writing/EditorPanel'
import ToolPanel from '@/components/writing/ToolPanel'
import type { NovelFolder } from '@/services/folderService'
import type { NovelDocument } from '@/services/documentService'
import type { ReferenceFile } from '@/services/referenceFileService'
import type { Chapter } from '@/services/chapterServiceForStudio'
import { getFoldersByNovelId, createFolder, deleteFolder, updateFolder } from '@/services/folderService'
import {
  getDocumentsByFolder,
  getDocumentById,
  createDocument,
  updateDocument,
  autoSaveDocument,
  searchDocuments,
  deleteDocument,
  initDefaultFolders,
} from '@/services/documentService'
import {
  getChaptersByNovel,
  getChapterById,
  createChapter,
  updateChapter,
  autoSaveChapter,
  deleteChapter,
} from '@/services/chapterServiceForStudio'
import {
  getReferenceFiles,
  uploadReferenceFile,
  deleteReferenceFile,
} from '@/services/referenceFileService'
import {
  getAIConversations,
  clearAIConversations,
} from '@/services/aiConversationService'
import type { AIConversation } from '@/services/aiConversationService'
import { getAllGenerators, AiGenerator } from '@/services/aiGeneratorService'
import novelService from '@/services/novelService'
import novelVolumeService from '@/services/novelVolumeService'
import api from '@/services/api'
import { withAIConfig, checkAIConfig, AI_CONFIG_ERROR_MESSAGE } from '@/utils/aiRequest'
import './WritingStudioPage.css'

const { Sider, Content } = Layout

// 一键格式化函数
const formatChineseSentences = (input: string): string => {
  if (!input) return '';
  let text = input.replace(/\r\n?/g, '\n');
  // 优先处理：标点簇 + 右引号/右括号 + 左引号 -> 在右引号/右括号后空一行，再开始下一段
  text = text.replace(/([。？！]+)\s*([”’"'」』】])\s*([“‘"'「『])/g, '$1$2\n\n$3');
  // 其次：标点簇 + 右引号/右括号（后面不是左引号）-> 在右引号/右括号后换行
  text = text.replace(/([。？！]+)\s*([”’"'」』】])(?!\s*[“‘"'「『])\s*/g, '$1$2\n');
  // 再者：标点簇后直接换行（后面没有右引号/右括号）
  text = text.replace(/([。？！]+)(?!\s*[”’"'」』】])\s*/g, '$1\n');
  // 行级清理：去除每行首部的空白（含全角空格），以及行尾空白
  text = text
    .split('\n')
    .map(line => line.replace(/^[\t \u3000]+/g, '').replace(/\s+$/g, ''))
    .join('\n');
  return text;
};

const WritingStudioPage: React.FC = () => {
  const { novelId } = useParams<{ novelId: string }>()
  const novelIdNumber = Number(novelId)

  const [loading, setLoading] = useState(true)
  const [novelTitle, setNovelTitle] = useState('')
  
  // 章节相关状态
  const [chapters, setChapters] = useState<Chapter[]>([])
  const [selectedChapter, setSelectedChapter] = useState<Chapter | null>(null)
  
  // 文档相关状态
  const [folders, setFolders] = useState<NovelFolder[]>([])
  const [documentsMap, setDocumentsMap] = useState<Record<number, NovelDocument[]>>({})
  const [selectedDocument, setSelectedDocument] = useState<NovelDocument | null>(null)
  
  // 编辑类型：'chapter' 或 'document'
  const [editingType, setEditingType] = useState<'chapter' | 'document'>('chapter')
  
  // 其他状态
  const [referenceFiles, setReferenceFiles] = useState<ReferenceFile[]>([])
  const [selectedReferenceIds, setSelectedReferenceIds] = useState<number[]>([])
  const [selectedLinkedIds, setSelectedLinkedIds] = useState<number[]>([])
  const [aiHistory, setAIHistory] = useState<AIConversation[]>([])
  const [aiInput, setAIInput] = useState('')
  const [aiOutput, setAIOutput] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [generatorId, setGeneratorId] = useState<number | null>(null)
  const [generators, setGenerators] = useState<AiGenerator[]>([])
  const [searchResults, setSearchResults] = useState<NovelDocument[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null)
  const [selectedTreeKey, setSelectedTreeKey] = useState<string>('root')
  const hasInitialized = useRef<Record<number, boolean>>({})
  
  // 自动保存相关状态
  const autoSaveTimerRef = useRef<number | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [lastSaveTime, setLastSaveTime] = useState<string>('')
  
  // 大纲相关状态
  const [outlineDrawerVisible, setOutlineDrawerVisible] = useState(false)
  const [volumeOutlineDrawerVisible, setVolumeOutlineDrawerVisible] = useState(false)
  const [editingOutline, setEditingOutline] = useState<string>('')
  const [editingVolumeOutline, setEditingVolumeOutline] = useState<string>('')
  const [outlineLoading, setOutlineLoading] = useState(false)
  const [currentVolume, setCurrentVolume] = useState<any>(null)
  
  // AI审稿相关状态
  const [reviewDrawerVisible, setReviewDrawerVisible] = useState(false)
  const [reviewResult, setReviewResult] = useState<string>('')
  const [isReviewing, setIsReviewing] = useState(false)
  
  // AI消痕相关状态
  const [traceRemovalDrawerVisible, setTraceRemovalDrawerVisible] = useState(false)
  const [processedContent, setProcessedContent] = useState<string>('')
  const [isRemovingTrace, setIsRemovingTrace] = useState(false)

  useEffect(() => {
    const loadInitialData = async () => {
      if (!novelIdNumber) return
      
      try {
        setLoading(true)
        // 同时加载章节、文件夹、参考文件等
        const [chapterList, folderList, referenceList, history, generatorList] = await Promise.all([
          getChaptersByNovel(novelIdNumber),
          getFoldersByNovelId(novelIdNumber),
          getReferenceFiles(novelIdNumber),
          getAIConversations(novelIdNumber),
          getAllGenerators(),
        ])

        try {
          const novel = await novelService.getNovelById(novelIdNumber)
          setNovelTitle(novel.title)
        } catch (e) {
          console.warn('获取小说信息失败', e)
        }

        // 如果没有文件夹，初始化默认结构（使用 ref 防止重复调用）
        let finalFolders = folderList
        let finalChapters = chapterList
        if ((!folderList || folderList.length === 0) && !hasInitialized.current[novelIdNumber]) {
          hasInitialized.current[novelIdNumber] = true
          console.log('开始初始化写作工作室...')
          try {
            await initDefaultFolders(novelIdNumber)
            // 重新加载文件夹和章节
            finalFolders = await getFoldersByNovelId(novelIdNumber)
            finalChapters = await getChaptersByNovel(novelIdNumber)
            console.log('初始化完成，文件夹数量:', finalFolders.length, '章节数量:', finalChapters.length)
          } catch (err: any) {
            console.error('初始化写作工作室失败', err)
            hasInitialized.current[novelIdNumber] = false
          }
        }

        setChapters(finalChapters)
        setFolders(finalFolders)
        setReferenceFiles(referenceList)
        setAIHistory(history)
        setGenerators(generatorList)
        setIsSearching(false)
        setSearchResults([])

        // 自动加载最新的章节
        if (finalChapters && finalChapters.length > 0) {
          try {
            const sortedChapters = [...finalChapters].sort((a, b) => (b.chapterNumber || 0) - (a.chapterNumber || 0))
            const latestChapter = sortedChapters[0]
            const detail = await getChapterById(latestChapter.id)
            setSelectedChapter(detail)
            setEditingType('chapter')
            setSelectedTreeKey(`chapter-${latestChapter.id}`)
            console.log('自动加载最新章节:', latestChapter.title)
          } catch (err) {
            console.warn('自动加载章节失败', err)
          }
        }
      } catch (error: any) {
        message.error(error?.message || '加载数据失败')
      } finally {
        setLoading(false)
      }
    }

    loadInitialData()
  }, [novelIdNumber])

  // 加载小说大纲
  const loadNovelOutline = async () => {
    if (!novelIdNumber) return
    try {
      const response = await api.get(`/novels/${novelIdNumber}`)
      const data = response.data || response
      
      if (data && data.outline && typeof data.outline === 'string' && data.outline.trim().length > 0) {
        setEditingOutline(data.outline)
        message.success('大纲加载成功')
      } else {
        setEditingOutline('暂无大纲，请先在大纲页面生成')
        message.warning('暂无大纲内容')
      }
    } catch (error: any) {
      console.error('加载小说大纲失败:', error)
      message.error('加载小说大纲失败')
      setEditingOutline('加载失败，请重试')
    }
  }

  // 保存小说大纲
  const handleSaveNovelOutline = async () => {
    if (!novelIdNumber) return
    setOutlineLoading(true)
    try {
      await api.put(`/novels/${novelIdNumber}`, {
        outline: editingOutline
      })
      message.success('小说大纲已保存')
      setOutlineDrawerVisible(false)
    } catch (error: any) {
      console.error('保存小说大纲失败:', error)
      message.error('保存小说大纲失败')
    } finally {
      setOutlineLoading(false)
    }
  }

  // 加载卷大纲
  const loadVolumeOutline = async () => {
    if (!novelIdNumber) return
    try {
      const volumes = await novelVolumeService.getVolumesByNovelId(novelIdNumber.toString())
      if (volumes && volumes.length > 0) {
        const firstVolume = volumes[0]
        setCurrentVolume(firstVolume)
        setEditingVolumeOutline(firstVolume.contentOutline || '暂无卷大纲')
        message.success('卷大纲加载成功')
      } else {
        setEditingVolumeOutline('暂无卷信息')
        message.warning('暂无卷信息')
      }
    } catch (error: any) {
      console.error('加载卷大纲失败:', error)
      message.error('加载卷大纲失败')
      setEditingVolumeOutline('加载失败，请重试')
    }
  }

  // 保存卷大纲
  const handleSaveVolumeOutline = async () => {
    if (!currentVolume) return
    setOutlineLoading(true)
    try {
      await api.put(`/volumes/${currentVolume.id}`, {
        contentOutline: editingVolumeOutline
      })
      message.success('卷大纲已保存')
      setVolumeOutlineDrawerVisible(false)
    } catch (error: any) {
      console.error('保存卷大纲失败:', error)
      message.error('保存卷大纲失败')
    } finally {
      setOutlineLoading(false)
    }
  }

  // 所有辅助文档（不包括章节）
  const allDocuments = useMemo(() => {
    return Object.values(documentsMap).flat()
  }, [documentsMap])

  const loadFolderDocuments = useCallback(
    async (folderId: number) => {
      setDocumentsMap((prev) => {
        if (prev[folderId]) return prev
        
        getDocumentsByFolder(folderId)
          .then((docs) => {
            setDocumentsMap((current) => ({ ...current, [folderId]: docs }))
          })
          .catch((error: any) => {
            message.error(error?.message || '加载文件夹文档失败')
          })
        
        return prev
      })
    },
    []
  )

  // 选择章节
  const handleSelectChapter = async (chapter: Chapter) => {
    try {
      const detail = await getChapterById(chapter.id)
      setSelectedChapter(detail)
      setSelectedDocument(null)
      setEditingType('chapter')
      setSelectedTreeKey(`chapter-${chapter.id}`)
    } catch (error: any) {
      message.error(error?.message || '加载章节失败')
    }
  }

  // 选择文档
  const handleSelectDocument = async (doc: NovelDocument) => {
    try {
      await loadFolderDocuments(doc.folderId)
      const detail = await getDocumentById(doc.id)
      setSelectedDocument(detail)
      setSelectedChapter(null)
      setEditingType('document')
      setSelectedFolderId(doc.folderId)
      setSelectedTreeKey(`doc-${doc.id}`)
    } catch (error: any) {
      message.error(error?.message || '加载文档失败')
    }
  }

  const handleCreateFolder = useCallback(
    async (parentFolder: NovelFolder | null = null, folderName?: string) => {
      try {
        let finalFolderName = folderName
        if (!finalFolderName) {
          const inputName = window.prompt('输入文件夹名称', '新文件夹')
          if (!inputName || !inputName.trim()) return
          finalFolderName = inputName.trim()
        }
        
        const newFolder = await createFolder(novelIdNumber, {
          folderName: finalFolderName,
          parentId: parentFolder?.id ?? null,
        })
        setFolders((prev) => [...prev, newFolder])
        setSelectedFolderId(newFolder.id)
        setSelectedTreeKey(`folder-${newFolder.id}`)
        message.success('文件夹创建成功')
      } catch (error: any) {
        message.error(error?.message || '创建文件夹失败')
      }
    },
    [novelIdNumber]
  )

  const handleCreateDocument = useCallback(
    async (folder: NovelFolder) => {
      try {
        const title = window.prompt('输入文档标题', '新文档')
        if (!title || !title.trim()) return
        const newDocument = await createDocument(folder.id, {
          novelId: novelIdNumber,
          title: title.trim(),
          content: '',
        })
        setDocumentsMap((prev) => ({
          ...prev,
          [folder.id]: [...(prev[folder.id] || []), newDocument],
        }))
        setSelectedDocument(newDocument)
        setSelectedChapter(null)
        setEditingType('document')
        setSelectedFolderId(folder.id)
        setSelectedTreeKey(`doc-${newDocument.id}`)
      } catch (error: any) {
        message.error(error?.message || '创建文档失败')
      }
    },
    [novelIdNumber]
  )

  // 快速添加章节（主要内容文件夹的"+"按钮）
  const handleQuickAddChapter = useCallback(
    async () => {
      try {
        // 计算新章节序号
        const chapterNumbers = chapters
          .map(ch => ch.chapterNumber || 0)
          .filter(num => num > 0)
        
        const maxChapter = chapterNumbers.length > 0 ? Math.max(...chapterNumbers) : 0
        const newChapterNum = maxChapter + 1
        
        // 中文数字映射
        const numToChinese = (num: number): string => {
          if (num <= 10) {
            return ['', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'][num]
          } else if (num < 20) {
            return '十' + ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'][num - 10]
          } else if (num < 100) {
            const tens = Math.floor(num / 10)
            const ones = num % 10
            return ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'][tens] + 
                   '十' + 
                   (ones > 0 ? ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'][ones] : '')
          }
          return String(num)
        }
        
        // 只存储章节名称，不包含"第X章"
        const chapterName = `新章节${newChapterNum}`  // 或者让用户输入
        
        const newChapter = await createChapter(novelIdNumber, {
          title: chapterName,
          content: '',
          chapterNumber: newChapterNum,
        })
        
        setChapters((prev) => [...prev, newChapter])
        setSelectedChapter(newChapter)
        setSelectedDocument(null)
        setEditingType('chapter')
        setSelectedTreeKey(`chapter-${newChapter.id}`)
        message.success(`创建第${numToChinese(newChapterNum)}章成功`)
      } catch (error: any) {
        message.error(error?.message || '创建章节失败')
      }
    },
    [novelIdNumber, chapters]
  )

  // 内容改变（章节或文档）
  const handleContentChange = (content: string) => {
    if (editingType === 'chapter' && selectedChapter) {
      setSelectedChapter((prev) =>
        prev ? { ...prev, content, wordCount: content.replace(/\s+/g, '').length } : prev
      )
    } else if (editingType === 'document' && selectedDocument) {
      setSelectedDocument((prev) =>
        prev ? { ...prev, content, wordCount: content.replace(/\s+/g, '').length } : prev
      )
    }
  }

  const handleUploadReference = async (file: File) => {
    if (!novelIdNumber) return
    const result = await uploadReferenceFile(novelIdNumber, file)
    setReferenceFiles((prev) => [result, ...prev])
  }

  const handleDeleteReference = async (id: number) => {
    if (!novelIdNumber) return
    await deleteReferenceFile(novelIdNumber, id)
    setReferenceFiles((prev) => prev.filter((file) => file.id !== id))
    setSelectedReferenceIds((prev) => prev.filter((item) => item !== id))
  }

  const handleDeleteFolder = async (folder: NovelFolder) => {
    if (!novelIdNumber) return
    try {
      Modal.confirm({
        title: '确认删除',
        content: `确定要删除文件夹"${folder.folderName}"吗？这将同时删除其中的所有文档。`,
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: async () => {
          await deleteFolder(novelIdNumber, folder.id)
          setFolders((prev) => prev.filter((f) => f.id !== folder.id))
          setDocumentsMap((prev) => {
            const newMap = { ...prev }
            delete newMap[folder.id]
            return newMap
          })
          if (selectedFolderId === folder.id) {
            setSelectedFolderId(null)
            setSelectedDocument(null)
            setSelectedTreeKey('root')
          }
          message.success('文件夹删除成功')
        },
      })
    } catch (error: any) {
      message.error(error?.message || '删除文件夹失败')
    }
  }

  const handleDeleteDocument = async (document: NovelDocument) => {
    try {
      Modal.confirm({
        title: '确认删除',
        content: `确定要删除文档"${document.title}"吗？`,
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: async () => {
          await deleteDocument(document.id)
          setDocumentsMap((prev) => {
            const newMap = { ...prev }
            if (newMap[document.folderId]) {
              newMap[document.folderId] = newMap[document.folderId].filter((d) => d.id !== document.id)
            }
            return newMap
          })
          if (selectedDocument?.id === document.id) {
            setSelectedDocument(null)
            setSelectedTreeKey(`folder-${document.folderId}`)
          }
          message.success('文档删除成功')
        },
      })
    } catch (error: any) {
      message.error(error?.message || '删除文档失败')
    }
  }

  // 删除章节
  const handleDeleteChapter = async (chapter: Chapter) => {
    try {
      Modal.confirm({
        title: '确认删除',
        content: `确定要删除章节"${chapter.title}"吗？`,
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: async () => {
          await deleteChapter(chapter.id)
          setChapters((prev) => prev.filter((c) => c.id !== chapter.id))
          if (selectedChapter?.id === chapter.id) {
            setSelectedChapter(null)
            setSelectedTreeKey('root')
          }
          message.success('章节删除成功')
        },
      })
    } catch (error: any) {
      message.error(error?.message || '删除章节失败')
    }
  }

  const handleRenameFolder = async (folder: NovelFolder, newName: string) => {
    if (!novelIdNumber) return
    try {
      await updateFolder(novelIdNumber, folder.id, { folderName: newName })
      setFolders((prev) => prev.map((f) => (f.id === folder.id ? { ...f, folderName: newName } : f)))
      message.success('文件夹重命名成功')
    } catch (error: any) {
      message.error(error?.message || '重命名失败')
    }
  }

  const handleRenameDocument = async (document: NovelDocument, newName: string) => {
    try {
      await updateDocument(document.id, { title: newName })
      setDocumentsMap((prev) => {
        const newMap = { ...prev }
        if (newMap[document.folderId]) {
          newMap[document.folderId] = newMap[document.folderId].map((d) =>
            d.id === document.id ? { ...d, title: newName } : d
          )
        }
        return newMap
      })
      if (selectedDocument?.id === document.id) {
        setSelectedDocument((prev) => (prev ? { ...prev, title: newName } : prev))
      }
      message.success('文档重命名成功')
    } catch (error: any) {
      message.error(error?.message || '重命名失败')
    }
  }

  // 重命名章节
  const handleRenameChapter = async (chapter: Chapter, newName: string) => {
    try {
      await updateChapter(chapter.id, { title: newName })
      setChapters((prev) => prev.map((c) => (c.id === chapter.id ? { ...c, title: newName } : c)))
      if (selectedChapter?.id === chapter.id) {
        setSelectedChapter((prev) => (prev ? { ...prev, title: newName } : prev))
      }
      message.success('章节重命名成功')
    } catch (error: any) {
      message.error(error?.message || '重命名失败')
    }
  }

  const handleSendAIRequest = async () => {
    if (!selectedChapter && !selectedDocument) {
      message.warning('请选择要编辑的内容')
      return
    }

    if (!novelIdNumber) return

    // 检查AI配置
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    try {
      setIsGenerating(true)
      setAIOutput('思考中...')
      
      const userMessage = aiInput.trim() || '开始'
      const currentTitle = editingType === 'chapter' ? selectedChapter?.title : selectedDocument?.title
      const currentId = editingType === 'chapter' ? selectedChapter?.id : selectedDocument?.id
      
      const token = localStorage.getItem('token')
      const requestBody = withAIConfig({
        chapterPlan: {
          chapterNumber: currentId,
          title: currentTitle,
          type: '剧情',
          coreEvent: userMessage,
          estimatedWords: 3000,
          priority: 'high',
          mood: 'normal'
        },
        userAdjustment: userMessage
      })
      
      const response = await fetch(`/api/novel-craft/${novelIdNumber}/write-chapter-stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'Cache-Control': 'no-cache',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        body: JSON.stringify(requestBody)
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (!reader) {
        throw new Error('无法获取响应流')
      }

      let buffer = ''
      let accumulatedContent = ''
      let hasReceivedContent = false
      
      const filterRegex = /(开始写作章节|正在生成|生成中|开始创作|正在创作|创作中)/i

      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          setIsGenerating(false)
          if (accumulatedContent.trim()) {
            message.success('AI写作完成')
          }
          break
        }

        const chunk = decoder.decode(value, { stream: true })
        buffer += chunk

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
            
            if (data === '[DONE]') {
              continue
            }
            
            try {
              const parsed = JSON.parse(data)
              let contentToAdd = ''
              
              if (typeof parsed === 'string' || typeof parsed === 'number') {
                contentToAdd = String(parsed)
              } else if (Array.isArray(parsed)) {
                contentToAdd = parsed
                  .map((v) => (typeof v === 'string' || typeof v === 'number') ? String(v) : '')
                  .join('')
              } else if (parsed && typeof parsed === 'object' && parsed.content) {
                contentToAdd = String(parsed.content)
              }
              
              if (contentToAdd && !filterRegex.test(contentToAdd)) {
                accumulatedContent += contentToAdd
                hasReceivedContent = true
                const displayContent = formatChineseSentences(accumulatedContent)
                setAIOutput(displayContent)
              }
            } catch (e) {
              if (data && data !== '[DONE]' && !filterRegex.test(data)) {
                accumulatedContent += data
                hasReceivedContent = true
                const displayContent = formatChineseSentences(accumulatedContent)
                setAIOutput(displayContent)
              }
            }
          }
        }
        
        if (!hasReceivedContent) {
          setAIOutput('思考中...')
        }
      }
    } catch (error: any) {
      console.error('AI生成失败:', error)
      message.error(error?.message || '生成失败')
      setIsGenerating(false)
      setAIOutput('')
    }
  }

  const handleReplaceContent = () => {
    if (!selectedChapter && !selectedDocument) return
    const formatted = formatChineseSentences(aiOutput)
    
    if (editingType === 'chapter' && selectedChapter) {
      setSelectedChapter((prev) => (prev ? { ...prev, content: formatted } : prev))
    } else if (editingType === 'document' && selectedDocument) {
      setSelectedDocument((prev) => (prev ? { ...prev, content: formatted } : prev))
    }
    
    onContentChange(formatted)
    message.success('内容已替换到编辑器')
  }

  // 统一的内容改变处理（自动保存）
  const onContentChange = (content: string) => {
    const prevContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    handleContentChange(content)
    
    // 清除之前的定时器
    if (autoSaveTimerRef.current) {
      clearTimeout(autoSaveTimerRef.current)
      autoSaveTimerRef.current = null
    }
    
    // 只有内容真正改变时才触发自动保存
    const hasContent = editingType === 'chapter' ? selectedChapter : selectedDocument
    if (hasContent && prevContent !== content && prevContent !== undefined) {
      setIsSaving(false)
      
      // 设置1秒后自动保存
      const timer = window.setTimeout(async () => {
        try {
          setIsSaving(true)
          
          // 根据类型调用不同的保存接口
          if (editingType === 'chapter' && selectedChapter) {
            await autoSaveChapter(selectedChapter.id, content)
          } else if (editingType === 'document' && selectedDocument) {
            await autoSaveDocument(selectedDocument.id, content)
          }
          
          // 更新最后保存时间
          const now = new Date()
          const timeStr = `${now.getFullYear()}-${(now.getMonth() + 1).toString().padStart(2, '0')}-${now.getDate().toString().padStart(2, '0')} ${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
          setLastSaveTime(timeStr)
          setIsSaving(false)
        } catch (err: any) {
          console.error('自动保存失败:', err)
          setIsSaving(false)
        }
      }, 1000)
      
      autoSaveTimerRef.current = timer
    }
  }

  const handleSearchDocuments = async (keyword: string) => {
    if (!novelIdNumber || !keyword.trim()) {
      setIsSearching(false)
      setSearchResults([])
      return
    }
    try {
      setIsSearching(true)
      const results = await searchDocuments(novelIdNumber, keyword.trim())
      setSearchResults(results)
    } catch (error: any) {
      message.error(error?.message || '搜索失败')
    }
  }

  const clearSearchResults = () => {
    setIsSearching(false)
    setSearchResults([])
  }
  
  // AI消痕处理
  const handleRemoveAITrace = async () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再进行AI消痕')
      return
    }
    
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }
    
    try {
      setIsRemovingTrace(true)
      setProcessedContent('')
      setTraceRemovalDrawerVisible(true)
      
      const token = localStorage.getItem('token')
      const requestBody = withAIConfig({
        content: currentContent
      })
      
      const response = await fetch('/api/ai/remove-trace-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'Cache-Control': 'no-cache',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        body: JSON.stringify(requestBody)
      })
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      
      if (!reader) {
        throw new Error('无法获取响应流')
      }
      
      message.info('开始AI消痕处理...')
      
      let buffer = ''
      let accumulated = ''
      const progressRegex = /(正在AI消痕处理中\.?\.?\.?|处理中\.?\.?\.?|processing|progress|开始处理)/i
      
      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          setIsRemovingTrace(false)
          message.success('AI消痕完成')
          break
        }
        
        const chunk = decoder.decode(value, { stream: true })
        buffer += chunk
        
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
            
            if (data === '[DONE]') {
              continue
            }
            
            try {
              const parsed = JSON.parse(data)
              let contentToAdd = ''
              
              if (typeof parsed === 'string' || typeof parsed === 'number') {
                contentToAdd = String(parsed)
              } else if (Array.isArray(parsed)) {
                contentToAdd = parsed
                  .map((v) => (typeof v === 'string' || typeof v === 'number') ? String(v) : '')
                  .join('')
              } else if (parsed && typeof parsed === 'object') {
                if (parsed.content) {
                  contentToAdd = String(parsed.content)
                } else if (parsed.delta) {
                  contentToAdd = String(parsed.delta)
                } else if (parsed.text) {
                  contentToAdd = String(parsed.text)
                }
              }
              
              if (contentToAdd && !progressRegex.test(contentToAdd)) {
                accumulated += contentToAdd
                const sanitized = accumulated.replace(progressRegex, '')
                setProcessedContent(sanitized)
              }
            } catch (e) {
              if (data && data !== '[DONE]' && !progressRegex.test(data)) {
                accumulated += data
                const sanitized = accumulated.replace(progressRegex, '')
                setProcessedContent(sanitized)
              }
            }
          }
        }
      }
    } catch (error: any) {
      console.error('AI消痕失败:', error)
      message.error(error?.message || 'AI消痕失败')
      setIsRemovingTrace(false)
    }
  }
  
  // AI审稿处理
  const handleReviewManuscript = async () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再审稿')
      return
    }
    
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }
    
    try {
      setIsReviewing(true)
      setReviewResult('')
      setReviewDrawerVisible(true)
      
      const token = localStorage.getItem('token')
      const requestBody = withAIConfig({
        content: currentContent
      })
      
      const response = await fetch('/api/ai/review-manuscript-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'Cache-Control': 'no-cache',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        body: JSON.stringify(requestBody)
      })
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      
      if (!reader) {
        throw new Error('无法获取响应流')
      }
      
      message.info('开始AI审稿...')
      
      let buffer = ''
      let accumulated = ''
      
      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          setIsReviewing(false)
          message.success('AI审稿完成')
          break
        }
        
        const chunk = decoder.decode(value, { stream: true })
        buffer += chunk
        
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
            
            if (data === '[DONE]') {
              continue
            }
            
            try {
              const parsed = JSON.parse(data)
              let contentToAdd = ''
              
              if (typeof parsed === 'string' || typeof parsed === 'number') {
                contentToAdd = String(parsed)
              } else if (Array.isArray(parsed)) {
                contentToAdd = parsed
                  .map((v) => (typeof v === 'string' || typeof v === 'number') ? String(v) : '')
                  .join('')
              } else if (parsed && typeof parsed === 'object' && parsed.content) {
                contentToAdd = String(parsed.content)
              }
              
              if (contentToAdd) {
                accumulated += contentToAdd
                setReviewResult(accumulated)
              }
            } catch (e) {
              if (data && data !== '[DONE]') {
                accumulated += data
                setReviewResult(accumulated)
              }
            }
          }
        }
      }
    } catch (error: any) {
      console.error('AI审稿失败:', error)
      message.error(error?.message || '审稿失败')
      setIsReviewing(false)
    }
  }

  if (loading) {
    return (
      <div className="writing-studio-loading">
        <Spin size="large" />
      </div>
    )
  }

  // 获取当前编辑的内容（章节或文档）
  const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
  const currentTitle = editingType === 'chapter' ? selectedChapter?.title : selectedDocument?.title
  const currentWordCount = editingType === 'chapter' ? selectedChapter?.wordCount : selectedDocument?.wordCount

  return (
    <Layout className="writing-studio">
      <Sider width={240} className="writing-sidebar" theme="light">
        <FileTree
          novelTitle={novelTitle}
          folders={folders}
          chapters={chapters}
          documents={isSearching ? searchResults : allDocuments}
          selectedKey={selectedTreeKey}
          onSelectChapter={handleSelectChapter}
          onSelectDocument={handleSelectDocument}
          onSelectFolder={async (folder) => {
            if (!folder) {
              setSelectedFolderId(null)
              setSelectedTreeKey('root')
              return
            }
            setSelectedFolderId(folder.id)
            setSelectedTreeKey(`folder-${folder.id}`)
            await loadFolderDocuments(folder.id)
          }}
          onCreateFolder={handleCreateFolder}
          onCreateDocument={handleCreateDocument}
          onQuickAddChapter={handleQuickAddChapter}
          onSearch={handleSearchDocuments}
          onSearchClear={clearSearchResults}
          onToolbarCreateFolder={() => {
            if (selectedFolderId) {
              const folder = folders.find((f) => f.id === selectedFolderId)
              handleCreateFolder(folder ?? null)
            } else {
              handleCreateFolder(null)
            }
          }}
          onDeleteFolder={handleDeleteFolder}
          onDeleteDocument={handleDeleteDocument}
          onDeleteChapter={handleDeleteChapter}
          onRenameFolder={handleRenameFolder}
          onRenameDocument={handleRenameDocument}
          onRenameChapter={handleRenameChapter}
          onToolbarCreateDocument={() => {
            if (selectedFolderId) {
              const folder = folders.find((f) => f.id === selectedFolderId)
              if (folder) {
                handleCreateDocument(folder)
                return
              }
            }
            message.info('请先选择一个文件夹')
          }}
        />
      </Sider>
      <Content className="writing-editor">
        <EditorPanel
          document={
            editingType === 'chapter' && selectedChapter
              ? {
                  id: selectedChapter.id,
                  title: selectedChapter.title,
                  content: selectedChapter.content || '',
                  wordCount: selectedChapter.wordCount || 0,
                  novelId: selectedChapter.novelId,
                  folderId: 0,
                  documentType: 'chapter' as any,
                  sortOrder: 0,
                  createdAt: selectedChapter.createdAt,
                  updatedAt: selectedChapter.updatedAt,
                }
              : selectedDocument
          }
          loading={loading}
          onChangeContent={onContentChange}
          onSave={async (doc) => {
            // 根据类型调用不同的保存接口
            if (editingType === 'chapter' && selectedChapter) {
              await updateChapter(selectedChapter.id, {
                title: doc.title,
                content: doc.content,
              })
            } else if (editingType === 'document' && selectedDocument) {
              await updateDocument(selectedDocument.id, {
                title: doc.title,
                content: doc.content,
              })
            }
          }}
          lastSaveTime={lastSaveTime}
          isSaving={isSaving}
          onShowOutline={async () => {
            await loadNovelOutline()
            setOutlineDrawerVisible(true)
          }}
          onShowVolumeOutline={async () => {
            await loadVolumeOutline()
            setVolumeOutlineDrawerVisible(true)
          }}
          onReviewManuscript={handleReviewManuscript}
          onRemoveAITrace={handleRemoveAITrace}
        />
      </Content>
      <Sider width={600} className="writing-tools" theme="light">
        <ToolPanel
          isGenerating={isGenerating}
          generatorId={generatorId}
          onGeneratorChange={setGeneratorId}
          referenceFiles={referenceFiles}
          onUploadReferenceFile={handleUploadReference}
          onDeleteReferenceFile={handleDeleteReference}
          onSelectReferenceFiles={setSelectedReferenceIds}
          selectedReferenceFileIds={selectedReferenceIds}
          linkedDocuments={allDocuments}
          onSelectLinkedDocuments={setSelectedLinkedIds}
          selectedLinkedDocumentIds={selectedLinkedIds}
          aiInputValue={aiInput}
          onChangeAIInput={setAIInput}
          onSendAIRequest={handleSendAIRequest}
          aiOutput={aiOutput}
          folders={folders}
          documentsMap={documentsMap}
          onCopyAIOutput={() => {
            navigator.clipboard.writeText(aiOutput)
            message.success('已复制到剪贴板')
          }}
          onReplaceWithAIOutput={handleReplaceContent}
          aiHistory={aiHistory.map((item) => ({
            id: item.id,
            content: item.assistantMessage,
            createdAt: item.createdAt,
          }))}
          onClearAIHistory={() => clearAIConversations(novelIdNumber).then(() => {
            setAIHistory([])
            setAIOutput('')
          })}
          generators={generators}
          searchResults={searchResults}
          onSelectSearchResult={handleSelectDocument}
          novelId={novelIdNumber}
          currentChapterNumber={
            editingType === 'chapter' ? selectedChapter?.chapterNumber ?? null : null
          }
          currentVolumeId={currentVolume?.id ?? null}
          currentVolumeNumber={currentVolume?.volumeNumber ?? null}
        />
      </Sider>
      
      {/* 大纲弹窗 */}
      <Modal
        title="小说大纲"
        open={outlineDrawerVisible}
        onCancel={() => setOutlineDrawerVisible(false)}
        footer={[
          <button
            key="cancel"
            onClick={() => setOutlineDrawerVisible(false)}
            style={{
              padding: '8px 20px',
              border: '1px solid #d9d9d9',
              borderRadius: '6px',
              background: '#fff',
              cursor: 'pointer',
              marginRight: '8px'
            }}
          >
            取消
          </button>,
          <button
            key="save"
            onClick={handleSaveNovelOutline}
            disabled={outlineLoading}
            style={{
              padding: '8px 20px',
              border: 'none',
              borderRadius: '6px',
              background: '#1890ff',
              color: '#fff',
              cursor: outlineLoading ? 'not-allowed' : 'pointer'
            }}
          >
            {outlineLoading ? '保存中...' : '保存'}
          </button>
        ]}
        width={900}
      >
        <textarea
          value={editingOutline}
          onChange={(e) => setEditingOutline(e.target.value)}
          placeholder="请输入小说大纲..."
          style={{
            width: '100%',
            minHeight: '400px',
            padding: '16px',
            border: '1px solid #d9d9d9',
            borderRadius: '6px',
            fontSize: '14px',
            lineHeight: '1.8',
            resize: 'vertical'
          }}
        />
      </Modal>
      
      {/* 卷大纲弹窗 */}
      <Modal
        title={`卷大纲${currentVolume ? ` - 第${currentVolume.volumeNumber}卷` : ''}`}
        open={volumeOutlineDrawerVisible}
        onCancel={() => setVolumeOutlineDrawerVisible(false)}
        footer={[
          <button
            key="cancel"
            onClick={() => setVolumeOutlineDrawerVisible(false)}
            style={{
              padding: '8px 20px',
              border: '1px solid #d9d9d9',
              borderRadius: '6px',
              background: '#fff',
              cursor: 'pointer',
              marginRight: '8px'
            }}
          >
            取消
          </button>,
          <button
            key="save"
            onClick={handleSaveVolumeOutline}
            disabled={outlineLoading}
            style={{
              padding: '8px 20px',
              border: 'none',
              borderRadius: '6px',
              background: '#1890ff',
              color: '#fff',
              cursor: outlineLoading ? 'not-allowed' : 'pointer'
            }}
          >
            {outlineLoading ? '保存中...' : '保存'}
          </button>
        ]}
        width={900}
      >
        <textarea
          value={editingVolumeOutline}
          onChange={(e) => setEditingVolumeOutline(e.target.value)}
          placeholder="请输入卷大纲..."
          style={{
            width: '100%',
            minHeight: '400px',
            padding: '16px',
            border: '1px solid #d9d9d9',
            borderRadius: '6px',
            fontSize: '14px',
            lineHeight: '1.8',
            resize: 'vertical'
          }}
        />
      </Modal>
      
      {/* AI审稿弹窗 */}
      <Modal
        title="AI审稿报告"
        open={reviewDrawerVisible}
        onCancel={() => setReviewDrawerVisible(false)}
        footer={[
          <button
            key="close"
            onClick={() => setReviewDrawerVisible(false)}
            style={{
              padding: '8px 20px',
              border: '1px solid #d9d9d9',
              borderRadius: '6px',
              background: '#fff',
              cursor: 'pointer'
            }}
          >
            关闭
          </button>
        ]}
        width={1200}
      >
        <div style={{
          maxHeight: '70vh',
          overflow: 'auto',
          padding: '16px',
          background: '#fafafa',
          borderRadius: '8px'
        }}>
          {isReviewing ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
              <div style={{ marginTop: '16px', color: '#666' }}>AI正在审稿中，请稍候...</div>
            </div>
          ) : reviewResult ? (
            <div style={{
              whiteSpace: 'pre-wrap',
              fontSize: '14px',
              lineHeight: '1.8',
              color: '#333'
            }}>
              {reviewResult}
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              暂无审稿结果
            </div>
          )}
        </div>
      </Modal>
      
      {/* AI消痕弹窗 */}
      <Modal
        title="🧹 AI消痕处理"
        open={traceRemovalDrawerVisible}
        onCancel={() => setTraceRemovalDrawerVisible(false)}
        footer={[
          <button
            key="cancel"
            onClick={() => setTraceRemovalDrawerVisible(false)}
            style={{
              padding: '8px 20px',
              border: '1px solid #d9d9d9',
              borderRadius: '6px',
              background: '#fff',
              cursor: 'pointer',
              marginRight: '8px'
            }}
          >
            取消
          </button>,
          <button
            key="apply"
            onClick={() => {
              if (processedContent) {
                if (editingType === 'chapter' && selectedChapter) {
                  setSelectedChapter((prev) => prev ? { ...prev, content: processedContent } : prev)
                } else if (editingType === 'document' && selectedDocument) {
                  setSelectedDocument((prev) => prev ? { ...prev, content: processedContent } : prev)
                }
                onContentChange(processedContent)
                message.success('已应用AI消痕后的内容')
                setTraceRemovalDrawerVisible(false)
              }
            }}
            disabled={!processedContent || isRemovingTrace}
            style={{
              padding: '8px 20px',
              border: 'none',
              borderRadius: '6px',
              background: (!processedContent || isRemovingTrace) ? '#d9d9d9' : '#52c41a',
              color: '#fff',
              cursor: (!processedContent || isRemovingTrace) ? 'not-allowed' : 'pointer'
            }}
          >
            应用到正文
          </button>
        ]}
        width={1000}
      >
        <div style={{
          maxHeight: '70vh',
          overflow: 'auto',
          padding: '16px',
          background: '#fafafa',
          borderRadius: '8px'
        }}>
          {isRemovingTrace ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
              <div style={{ marginTop: '16px', color: '#666' }}>正在AI消痕处理中...</div>
            </div>
          ) : processedContent ? (
            <div>
              <div style={{ marginBottom: '12px', color: '#666', fontSize: '12px' }}>
                处理后内容（共 {processedContent.replace(/\s+/g, '').length} 字）：
              </div>
              <div style={{
                whiteSpace: 'pre-wrap',
                fontSize: '14px',
                lineHeight: '1.8',
                color: '#333',
                background: '#fff',
                padding: '16px',
                borderRadius: '6px',
                border: '1px solid #e8e8e8'
              }}>
                {processedContent}
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              暂无处理结果
            </div>
          )}
        </div>
      </Modal>
    </Layout>
  )
}

export default WritingStudioPage
