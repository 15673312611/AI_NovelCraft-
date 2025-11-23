import React, { useCallback, useEffect, useMemo, useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { Layout, Spin, message, Modal, List, Button, Tag, Drawer } from 'antd'
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
import novelVolumeService, { NovelVolume } from '@/services/novelVolumeService'
import {
  getChapterOutline,
  getChapterOutlinesByVolume,
  updateChapterOutline as updateVolumeChapterOutline,
  type VolumeChapterOutlineSummary,
  type VolumeChapterOutline,
} from '@/services/volumeChapterOutlineService'
import { getChapterHistory, getDocumentHistory, type WritingVersionHistory } from '@/services/writingHistoryService'
import api from '@/services/api'
import { withAIConfig, checkAIConfig, AI_CONFIG_ERROR_MESSAGE } from '@/utils/aiRequest'
import './WritingStudioPage.css'

const { Sider, Content } = Layout

/**
 * 智能排版系统 - 一键格式化
 * 
 * 功能：
 * 1. 段落智能识别（对话、叙述、心理描写）
 * 2. 自动空行（对话段落、场景切换）
 * 3. 标点优化（修复常见错误）
 * 4. 段首缩进（可选）
 * 5. 特殊格式处理（章节标题、分隔线）
 */
const formatChineseSentences = (input: string): string => {
  if (!input) return '';
  
  let text = input.replace(/\r\n?/g, '\n');
  let result = '';
  let inQuote = false; // 是否在引号内
  let currentLine = '';
  
  // 左引号字符集（只包括双引号和书名号）
  const leftQuotes = '\u201c\u2018\u300c\u300e';  // "'「『
  // 右引号字符集  
  const rightQuotes = '\u201d\u2019\u300d\u300f'; // "'」』
  // 句子结尾标点
  const endMarks = '。？！';
  // 所有中文标点符号（用于检查引号后是否跟标点）
  const allPunctuation = '。？！，、；：…—';
  
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    currentLine += char;
    
    // 检测左引号（进入引号）
    if (leftQuotes.includes(char)) {
      inQuote = true;
    }
    // 检测右引号（离开引号）
    else if (rightQuotes.includes(char)) {
      inQuote = false;
      // 检查右引号后是否紧跟任何标点符号
      const nextChar = i + 1 < text.length ? text[i + 1] : '';
      if (allPunctuation.includes(nextChar)) {
        // 如果后面是任何标点，不换行，继续累积
        // 例如：“降维打击”。 或 “你好”，
      } else {
        // 右引号后没有标点，才换行（独立对话）
        result += currentLine.trim() + '\n';
        currentLine = '';
      }
    }
    // 检测句子结尾标点
    else if (endMarks.includes(char)) {
      // 只有在引号外才换行
      if (!inQuote) {
        // 检查后面（跳过换行符和空格）是否有引号
        let j = i + 1;
        while (j < text.length && (text[j] === '\n' || text[j] === ' ' || text[j] === '\r')) {
          j++;
        }
        const nextNonWhitespace = j < text.length ? text[j] : '';
        
        // 如果后面紧跟引号，不换行，继续累积
        if (rightQuotes.includes(nextNonWhitespace)) {
          // 例如：这一档了吧？\n”
        } else {
          // 后面没有引号，正常换行
          result += currentLine.trim() + '\n';
          currentLine = '';
        }
      }
      // 引号内不换行，继续累积
    }
    // 检测省略号
    else if (char === '…') {
      // 检查是否是连续的省略号
      if (i + 1 < text.length && text[i + 1] === '…') {
        currentLine += text[i + 1];
        i++; // 跳过下一个省略号
      }
      // 只有在引号外才换行
      if (!inQuote) {
        // 检查后面（跳过换行符和空格）是否有引号
        let j = i + 1;
        while (j < text.length && (text[j] === '\n' || text[j] === ' ' || text[j] === '\r')) {
          j++;
        }
        const nextNonWhitespace = j < text.length ? text[j] : '';
        
        // 如果后面紧跟引号，不换行，继续累积
        if (rightQuotes.includes(nextNonWhitespace)) {
          // 例如：天啊…\n”
        } else {
          // 后面没有引号，正常换行
          result += currentLine.trim() + '\n';
          currentLine = '';
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
    result += currentLine.trim();
  }
  
  // 清理：移除多余的连续换行（超过2个）
  result = result.replace(/\n{3,}/g, '\n\n');
  
  return result.trim();
};

/**
 * 修复常见标点错误
 */
function fixPunctuation(text: string): string {
  let result = text;
  
  // 修复中英文标点混用
  result = result.replace(/,(?=[^a-zA-Z0-9])/g, '，'); // 逗号
  result = result.replace(/\.(?=[^a-zA-Z0-9\s])/g, '。'); // 句号（但保留英文句号）
  result = result.replace(/\?(?=[^a-zA-Z0-9])/g, '？'); // 问号
  result = result.replace(/!(?=[^a-zA-Z0-9])/g, '！'); // 感叹号
  result = result.replace(/;(?=[^a-zA-Z0-9])/g, '；'); // 分号
  result = result.replace(/:(?=[^a-zA-Z0-9])/g, '：'); // 冒号
  
  // 修复引号（统一使用中文引号）
  result = result.replace(/"/g, '\u201c').replace(/"/g, '\u201d'); // 英文双引号 -> 中文双引号
  result = result.replace(/'/g, '\u2018').replace(/'/g, '\u2019'); // 英文单引号 -> 中文单引号
  
  // 修复省略号
  result = result.replace(/\.{3,}/g, '……'); // ... -> ……
  result = result.replace(/。{3,}/g, '……'); // 。。。 -> ……
  
  // 修复破折号
  result = result.replace(/--+/g, '——'); // -- -> ——
  
  // 移除标点前的空格
  result = result.replace(/\s+([，。？！；：、])/g, '$1');
  
  // 移除标点后的多余空格（但保留一个空格用于英文）
  result = result.replace(/([，。？！；：、])\s{2,}/g, '$1 ');
  
  return result;
}

/**
 * 判断是否是句子结尾
 */
function isSentenceEnd(text: string, index: number): boolean {
  const char = text[index];
  
  // 句子结尾标点
  const endMarks = '。？！…';
  if (!endMarks.includes(char)) {
    return false;
  }
  
  // 省略号需要连续
  if (char === '…') {
    return text[index + 1] !== '…';
  }
  
  return true;
}

/**
 * 判断是否是结束标记（引号、括号等）
 */
function isClosingMark(char: string): boolean {
  const closingMarks = '\u201d\u2019\u300d\u300f\u3011)\uff09';
  return closingMarks.includes(char) || char === ' ';
}

/**
 * 检测行类型
 */
function detectLineType(line: string): string {
  if (!line || !line.trim()) return 'empty';
  
  const trimmed = line.trim();
  
  // 章节标题（第X章、第X节等）
  if (/^第[一二三四五六七八九十百千万\d]+[章节回]/i.test(trimmed)) {
    return 'chapter_title';
  }
  
  // 分隔线
  if (/^[=\-*]{3,}$/.test(trimmed)) {
    return 'separator';
  }
  
  // 对话（以引号开头）
  if (/^["'"'「『]/.test(trimmed)) {
    return 'dialogue';
  }
  
  // 对话（包含引号）
  if (/["'"'「『].*["'"'」』]/.test(trimmed)) {
    return 'dialogue';
  }
  
  // 心理描写（常见模式）
  if (/[想道]：/.test(trimmed) || /心[中里想]/.test(trimmed)) {
    return 'thought';
  }
  
  // 叙述
  return 'narrative';
}

/**
 * 判断是否应该分段
 */
function shouldParagraphBreak(currentType: string, lastType: string, currentLine: string, lastLine: string): boolean {
  // 第一行不分段
  if (!lastType) return false;
  
  // 章节标题前后必须分段
  if (currentType === 'chapter_title' || lastType === 'chapter_title') {
    return true;
  }
  
  // 分隔线前后必须分段
  if (currentType === 'separator' || lastType === 'separator') {
    return true;
  }
  
  // 对话之间不分段（连续对话）
  if (currentType === 'dialogue' && lastType === 'dialogue') {
    // 但如果是不同人的对话，可能需要分段
    // 这里简化处理：如果上一句以引号结尾，下一句以引号开头，不分段
    return false;
  }
  
  // 对话和叙述之间分段
  if ((currentType === 'dialogue' && lastType === 'narrative') ||
      (currentType === 'narrative' && lastType === 'dialogue')) {
    return true;
  }
  
  // 叙述之间：如果上一句很长（超过50字），可能是新段落
  if (currentType === 'narrative' && lastType === 'narrative') {
    if (lastLine.length > 50) {
      return true;
    }
  }
  
  return false;
}

/**
 * 判断段落之间是否需要空行
 */
function shouldAddEmptyLine(currentType: string, nextType: string): boolean {
  // 章节标题后空行
  if (currentType === 'chapter_title') {
    return true;
  }
  
  // 分隔线前后空行
  if (currentType === 'separator' || nextType === 'separator') {
    return true;
  }
  
  // 对话段落和叙述段落之间空行
  if ((currentType === 'dialogue' && nextType === 'narrative') ||
      (currentType === 'narrative' && nextType === 'dialogue')) {
    return true;
  }
  
  // 连续对话之间不空行
  if (currentType === 'dialogue' && nextType === 'dialogue') {
    return false;
  }
  
  // 连续叙述之间不空行
  if (currentType === 'narrative' && nextType === 'narrative') {
    return false;
  }
  
  return false;
}

/**
 * 轻量级实时换行函数（用于流式输出）
 * 
 * 规则：
 * 1. 引号内不换行：引号内的句号（。？！）不换行
 * 2. 引号结束后换行：遇到右引号（"」』）后换行
 * 3. 引号外换行：引号外的句号直接换行
 * 
 * 示例：
 * "夫人，我是周毅。今天负责您母亲遗产交接的团队已经全部到齐。" -> 不换行，等右引号
 * "夫人，我是周毅。" -> "夫人，我是周毅。"\n
 * 他说完就走了。 -> 他说完就走了。\n
 */
const applyRealtimeLineBreaks = (input: string): string => {
  if (!input) return '';
  
  let result = '';
  let inQuote = false; // 是否在引号内
  
  // 左引号字符集（只包括双引号和书名号）
  const leftQuotes = '\u201c\u2018\u300c\u300e';  // "'「『
  // 右引号字符集  
  const rightQuotes = '\u201d\u2019\u300d\u300f'; // "'」』
  // 句子结尾标点
  const endMarks = '。？！';
  
  for (let i = 0; i < input.length; i++) {
    const char = input[i];
    result += char;
    
    // 检测左引号（进入引号）
    if (leftQuotes.includes(char)) {
      inQuote = true;
    }
    // 检测右引号（离开引号）
    else if (rightQuotes.includes(char)) {
      inQuote = false;
      // 右引号后换行
      result += '\n';
    }
    // 检测句子结尾标点
    else if (endMarks.includes(char)) {
      // 只有在引号外才换行
      if (!inQuote) {
        result += '\n';
      }
      // 引号内不换行，继续累积
    }
    // 检测省略号
    else if (char === '…') {
      // 检查是否是连续的省略号
      if (i + 1 < input.length && input[i + 1] === '…') {
        // 跳过，等待第二个省略号
        continue;
      }
      // 只有在引号外才换行
      if (!inQuote) {
        result += '\n';
      }
    }
  }
  
  // 清理：移除多余的连续换行（超过2个）
  result = result.replace(/\n{3,}/g, '\n\n');
  
  return result;
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
  const [selectedModel, setSelectedModel] = useState<string>('gemini-3-pro-preview')
  const [aiHistory, setAIHistory] = useState<AIConversation[]>([])
  const [aiInput, setAIInput] = useState('')
  const [aiOutput, setAIOutput] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [generatorId, setGeneratorId] = useState<number | null>(null)
  const [generators, setGenerators] = useState<AiGenerator[]>([])
  const [searchResults, setSearchResults] = useState<NovelDocument[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const [generationPhases, setGenerationPhases] = useState<string[]>([])
  const [hasContentStarted, setHasContentStarted] = useState(false)
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null)
  const [selectedTreeKey, setSelectedTreeKey] = useState<string>('root')
  const hasInitialized = useRef<Record<number, boolean>>({})
  
  // 自动保存相关状态
  const autoSaveTimerRef = useRef<number | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [lastSaveTime, setLastSaveTime] = useState<string>('')

  // 正文历史版本相关状态
  const [historyModalVisible, setHistoryModalVisible] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [versionHistory, setVersionHistory] = useState<WritingVersionHistory[]>([])
  const [historyPreviewVisible, setHistoryPreviewVisible] = useState(false)
  const [historyPreviewItem, setHistoryPreviewItem] = useState<WritingVersionHistory | null>(null)
  
  // 大纲相关状态
  const [outlineDrawerVisible, setOutlineDrawerVisible] = useState(false)
  const [volumeOutlineDrawerVisible, setVolumeOutlineDrawerVisible] = useState(false)
  const [editingOutline, setEditingOutline] = useState<string>('')
  const [editingVolumeOutline, setEditingVolumeOutline] = useState<string>('')
  const [outlineLoading, setOutlineLoading] = useState(false)
  const [currentVolume, setCurrentVolume] = useState<NovelVolume | null>(null)
  const [volumes, setVolumes] = useState<NovelVolume[]>([])

  // 章纲弹窗相关状态（使用编辑内容/加载状态作为显隐条件）
  const [chapterOutlineLoading, setChapterOutlineLoading] = useState(false)
  const [chapterOutlineListLoading, setChapterOutlineListLoading] = useState(false)
  const [chapterOutlineVolumeId, setChapterOutlineVolumeId] = useState<number | null>(null)
  const [chapterOutlineList, setChapterOutlineList] = useState<VolumeChapterOutlineSummary[]>([])
  const [chapterOutlineListVisible, setChapterOutlineListVisible] = useState(false)
  const [editingChapterOutline, setEditingChapterOutline] = useState<{
    outlineId?: number
    globalChapterNumber?: number
    chapterInVolume?: number
    volumeNumber?: number
    direction: string
    keyPlotPoints: string
    emotionalTone: string
    subplot: string
    antagonism: string
    status?: string
  } | null>(null)
  
  // AI审稿相关状态
  const [reviewDrawerVisible, setReviewDrawerVisible] = useState(false)
  const [reviewResult, setReviewResult] = useState<string>('')
  const [isReviewing, setIsReviewing] = useState(false)
  
  // AI消痕相关状态
  const [traceRemovalDrawerVisible, setTraceRemovalDrawerVisible] = useState(false)
  const [processedContent, setProcessedContent] = useState<string>('')
  const [isRemovingTrace, setIsRemovingTrace] = useState(false)
  
  // AI精简相关状态
  const [streamlineDrawerVisible, setStreamlineDrawerVisible] = useState(false)
  const [streamlinedContent, setStreamlinedContent] = useState<string>('')
  const [isStreamlining, setIsStreamlining] = useState(false)
  const [streamlineTargetLength, setStreamlineTargetLength] = useState<string>('')

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
      const volumeList = await novelVolumeService.getVolumesByNovelId(novelIdNumber.toString())
      if (volumeList && volumeList.length > 0) {
        setVolumes(volumeList)
        const firstVolume = volumeList[0]
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

  const findVolumeForChapter = (chapterNumber: number | null): NovelVolume | null => {
    if (!chapterNumber || !volumes || volumes.length === 0) {
      return currentVolume || null
    }
    const matched = volumes.find((v) => {
      const start = Number((v as any).chapterStart)
      const end = Number((v as any).chapterEnd)
      if (!Number.isFinite(start) || !Number.isFinite(end)) return false
      return chapterNumber >= start && chapterNumber <= end
    })
    return matched || currentVolume || volumes[0] || null
  }

  const mapOutlineToEditingForm = (
    outline: VolumeChapterOutline,
    fallbackChapterNumber?: number
  ) => {
    return {
      outlineId: outline.id,
      globalChapterNumber: outline.globalChapterNumber ?? fallbackChapterNumber,
      chapterInVolume: outline.chapterInVolume ?? undefined,
      volumeNumber: outline.volumeNumber ?? undefined,
      direction: outline.direction || '',
      keyPlotPoints: outline.keyPlotPoints || '',
      emotionalTone: outline.emotionalTone || '',
      subplot: outline.subplot || '',
      antagonism: outline.antagonism || '',
      status: outline.status || undefined,
    }
  }

  const getOutlineStatusText = (status?: string) => {
    if (!status) return '未设置'
    switch (status) {
      case 'PENDING':
        return '待写'
      case 'WRITTEN':
        return '已写'
      case 'REVISED':
        return '已修订'
      default:
        return status
    }
  }

  // 打开章纲弹窗（当前选中章节）
  const handleShowChapterOutline = async () => {
    if (!novelIdNumber) return
    if (editingType !== 'chapter' || !selectedChapter || selectedChapter.chapterNumber == null) {
      message.warning('请先在左侧选择一个章节')
      return
    }

    const chapterNumber = selectedChapter.chapterNumber

    setChapterOutlineLoading(true)
    try {
      let volumeList = volumes
      if (!volumeList || volumeList.length === 0) {
        volumeList = await novelVolumeService.getVolumesByNovelId(novelIdNumber.toString())
        setVolumes(volumeList)
      }

      const volumeForChapter = findVolumeForChapter(chapterNumber)
      const volumeId = volumeForChapter ? Number(volumeForChapter.id) : null
      setChapterOutlineVolumeId(volumeId)
      // 列表数据改为在点击“展开列表”按钮时按需加载，这里只确定所属卷

      // 当前章节章纲详情
      const res = await getChapterOutline(novelIdNumber, chapterNumber)
      if (res.hasOutline && res.outline) {
        setEditingChapterOutline(mapOutlineToEditingForm(res.outline, chapterNumber))
      } else {
        const inferredVolume = volumeForChapter
        const chapterInVolume =
          inferredVolume && (inferredVolume as any).chapterStart != null
            ? chapterNumber - Number((inferredVolume as any).chapterStart) + 1
            : undefined
        setEditingChapterOutline({
          outlineId: undefined,
          globalChapterNumber: chapterNumber,
          chapterInVolume,
          volumeNumber: inferredVolume?.volumeNumber,
          direction: '',
          keyPlotPoints: '',
          emotionalTone: '',
          subplot: '',
          antagonism: '',
          status: undefined,
        })
      }

      // 此处不再单独维护显隐状态，由 editingChapterOutline / loading 状态驱动
    } catch (error: any) {
      console.error('加载章节章纲失败:', error)
      message.error(error?.message || '加载章节章纲失败')
    } finally {
      setChapterOutlineLoading(false)
    }
  }

  const handleSelectOutlineChapter = async (globalChapterNumber: number | undefined) => {
    if (!novelIdNumber || !globalChapterNumber) return

    setChapterOutlineLoading(true)
    try {
      const res = await getChapterOutline(novelIdNumber, globalChapterNumber)
      if (res.hasOutline && res.outline) {
        setEditingChapterOutline(mapOutlineToEditingForm(res.outline, globalChapterNumber))
      } else {
        message.info('该章节暂未生成章纲')
      }
    } catch (error: any) {
      console.error('切换章纲失败:', error)
      message.error(error?.message || '切换章纲失败')
    } finally {
      setChapterOutlineLoading(false)
    }
  }

  const handleSaveChapterOutline = async () => {
    if (!editingChapterOutline || !editingChapterOutline.outlineId) {
      message.warning('当前章节暂无可保存的章纲，请先生成')
      return
    }

    setChapterOutlineLoading(true)
    try {
      const payload = {
        direction: editingChapterOutline.direction,
        keyPlotPoints: editingChapterOutline.keyPlotPoints,
        emotionalTone: editingChapterOutline.emotionalTone,
        subplot: editingChapterOutline.subplot,
        antagonism: editingChapterOutline.antagonism,
      }
      const updated = await updateVolumeChapterOutline(
        editingChapterOutline.outlineId,
        payload
      )
      setEditingChapterOutline(mapOutlineToEditingForm(updated))
      message.success('章纲已保存')
    } catch (error: any) {
      console.error('保存章纲失败:', error)
      message.error(error?.message || '保存章纲失败')
    } finally {
      setChapterOutlineLoading(false)
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

  // 选择文档或搜索结果（当前搜索结果实际为章节列表）
  const handleSelectDocument = async (doc: NovelDocument) => {
    // 如果没有 folderId，说明这是从左侧搜索返回的“章节”结果，
    // 需要走章节详情接口而不是文档接口
    if (doc.folderId == null) {
      try {
        const detail = await getChapterById(doc.id)
        setSelectedChapter(detail)
        setSelectedDocument(null)
        setEditingType('chapter')
        setSelectedTreeKey(`chapter-${doc.id}`)
      } catch (error: any) {
        message.error(error?.message || '加载章节失败')
      }
      return
    }

    // 正常文档节点
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
      // 重置所有状态，确保每次生成都是全新的
      setIsGenerating(true)
      setAIOutput('')
      setGenerationPhases([])
      setHasContentStarted(false)
      
      const userMessage = aiInput.trim() || '开始'
      const currentChapterNumber = editingType === 'chapter' ? selectedChapter?.chapterNumber : null
      
      const token = localStorage.getItem('token')
      const requestBody = withAIConfig({
        novelId: novelIdNumber,
        startChapter: currentChapterNumber,
        count: 1,
        userAdjustment: userMessage
      }, {
        model: selectedModel
      })
      
      const response = await fetch('/api/agentic/generate-chapters-stream', {
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
      let currentEventName = ''
      let currentDataLines: string[] = []
      const phases: string[] = []

      const processSSEEvent = (eventName: string, data: string) => {
        if (!data || data === '[DONE]') {
          return
        }

        // 按事件类型处理：后端已保证使用 event 类型区分内容与状态
        if (eventName === 'error') {
          message.error(data || '生成失败')
          setIsGenerating(false)
          return
        }

        if (eventName === 'phase') {
          phases.push(data)
          setGenerationPhases([...phases])
          console.log('phase', data)
          return
        }

        if (eventName === 'outline') {
          phases.push(data)
          setGenerationPhases([...phases])
          console.log('outline', data)
          return
        }

        if (eventName === 'message') {
          // 处理正文内容流式输出（message事件），按一键格式化逻辑实时排版
          console.log('[流式输出] 接收到 message 事件，纯文本:', data)
          setHasContentStarted(true)
          accumulatedContent += data
          console.log('[流式输出] 累积内容长度:', accumulatedContent.length)
          const formattedContent = formatChineseSentences(accumulatedContent)
          setAIOutput(formattedContent)
          return
        }

        // 其他事件类型（keepalive、batch_complete等）不写入正文
      }

      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          // 生成完成，重置状态
          setIsGenerating(false)
          if (accumulatedContent.trim()) {
            // 生成完成后格式化内容
            const formatted = formatChineseSentences(accumulatedContent)
            setAIOutput(formatted)
            message.success('AI写作完成')
          } else {
            // 如果没有内容，也要确保状态正确
            setHasContentStarted(false)
          }
          break
        }

        const chunk = decoder.decode(value, { stream: true })
        buffer += chunk

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()

          // 空行表示一个事件块结束：处理当前块
          if (!trimmed) {
            if (currentDataLines.length > 0) {
              const data = currentDataLines.join('\n')
              processSSEEvent(currentEventName, data)
              currentDataLines = []
              currentEventName = ''
            }
            continue
          }

          // 事件名称可以出现在 data 之前或之后，取同一块中最后一次出现的名称
          if (line.startsWith('event:')) {
            currentEventName = line.startsWith('event: ') ? line.slice(7).trim() : line.slice(6).trim()
            continue
          }
          
          if (line.startsWith('data:')) {
            const payload = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
            if (payload === '[DONE]') {
              // 单独处理 DONE：结束当前块并重置状态
              if (currentDataLines.length > 0) {
                const data = currentDataLines.join('\n')
                processSSEEvent(currentEventName, data)
                currentDataLines = []
                currentEventName = ''
              }
              continue
            }
            currentDataLines.push(payload)
            continue
          }

          // 其他行忽略
        }
      }
    } catch (error: any) {
      console.error('AI生成失败:', error)
      message.error(error?.message || '生成失败')
      // 确保错误时所有状态都被重置
      setIsGenerating(false)
      setAIOutput('')
      setGenerationPhases([])
      setHasContentStarted(false)
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

  // 打开历史版本弹窗
  const handleShowHistory = async () => {
    if (editingType === 'chapter' && selectedChapter) {
      try {
        setHistoryLoading(true)
        const list = await getChapterHistory(selectedChapter.id)
        setVersionHistory(list)
        setHistoryModalVisible(true)
      } catch (error: any) {
        message.error(error?.message || '加载章节历史版本失败')
      } finally {
        setHistoryLoading(false)
      }
    } else if (editingType === 'document' && selectedDocument) {
      try {
        setHistoryLoading(true)
        const list = await getDocumentHistory(selectedDocument.id)
        setVersionHistory(list)
        setHistoryModalVisible(true)
      } catch (error: any) {
        message.error(error?.message || '加载文档历史版本失败')
      } finally {
        setHistoryLoading(false)
      }
    } else {
      message.warning('请先选择要编辑的章节或文档')
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
  
  // AI消痕处理 - 第一次点击打开抽屉
  const handleRemoveAITrace = () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再进行AI消痕')
      return
    }
    
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }
    
    // 第一次点击：打开抽屉，不请求接口
    setProcessedContent('') // 清空之前的结果
    setTraceRemovalDrawerVisible(true)
  }
  
  // 执行AI消痕的实际逻辑
  const executeRemoveAITrace = async () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再进行AI消痕')
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
  
  // AI精简处理 - 第一次点击打开抽屉
  const handleStreamlineContent = () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再进行AI精简')
      return
    }
    
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }
    
    // 第一次点击：打开抽屉，不请求接口
    setStreamlinedContent('')
    setStreamlineTargetLength('')
    setStreamlineDrawerVisible(true)
  }
  
  // 执行AI精简的实际逻辑
  const executeStreamlineContent = async () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再进行AI精简')
      return
    }
    
    try {
      setIsStreamlining(true)
      setStreamlinedContent('')
      
      const token = localStorage.getItem('token')
      const requestBody = withAIConfig({
        content: currentContent
      })
      
      const response = await fetch('/api/ai/streamline-content-stream', {
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
      
      message.info('开始AI精简处理...')
      
      let buffer = ''
      let accumulated = ''
      
      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          setIsStreamlining(false)
          message.success('AI精简完成')
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
              
              if (contentToAdd) {
                accumulated += contentToAdd
                setStreamlinedContent(accumulated)
              }
            } catch (e) {
              if (data && data !== '[DONE]') {
                accumulated += data
                setStreamlinedContent(accumulated)
              }
            }
          }
        }
      }
    } catch (error: any) {
      console.error('AI精简失败:', error)
      message.error(error?.message || 'AI精简失败')
      setIsStreamlining(false)
    }
  }
  
  // AI审稿处理 - 第一次点击打开弹窗
  const handleReviewManuscript = () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再审稿')
      return
    }
    
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }
    
    // 第一次点击：打开弹窗，不请求接口
    setReviewResult('')
    setReviewDrawerVisible(true)
  }
  
  // 执行AI审稿的实际逻辑
  const executeReviewManuscript = async () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再审稿')
      return
    }
    
    try {
      setIsReviewing(true)
      setReviewResult('')
      
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
          onShowHistory={handleShowHistory}
          onShowOutline={async () => {
            await loadNovelOutline()
            setOutlineDrawerVisible(true)
          }}
          onShowVolumeOutline={async () => {
            await loadVolumeOutline()
            setVolumeOutlineDrawerVisible(true)
          }}
          onStreamlineContent={handleStreamlineContent}
          onReviewManuscript={handleReviewManuscript}
          onRemoveAITrace={handleRemoveAITrace}
        />
      </Content>
      <Sider width={600} className="writing-tools" theme="light">
        <ToolPanel
          isGenerating={isGenerating}
          generatorId={generatorId}
          onGeneratorChange={setGeneratorId}
          selectedModel={selectedModel}
          onModelChange={setSelectedModel}
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
          generationPhases={generationPhases}
          hasContentStarted={hasContentStarted}
          folders={folders}
          documentsMap={documentsMap}
          onShowChapterOutline={handleShowChapterOutline}
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

      {/* 正文历史版本弹窗 */}
      <Modal
        title={editingType === 'chapter' ? '章节历史版本' : '文档历史版本'}
        open={historyModalVisible}
        onCancel={() => setHistoryModalVisible(false)}
        footer={null}
        width={900}
      >
        <div
          style={{
            maxHeight: '70vh',
            overflow: 'auto',
          }}
        >
          {historyLoading ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
            </div>
          ) : versionHistory.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              暂无历史版本
            </div>
          ) : (
            <List
              dataSource={versionHistory}
              renderItem={(item) => (
                <List.Item
                  key={item.id}
                  actions={[
                    <Button
                      key="preview"
                      type="link"
                      onClick={() => {
                        setHistoryPreviewItem(item)
                        setHistoryPreviewVisible(true)
                        setHistoryModalVisible(false)
                      }}
                    >
                      查看
                    </Button>,
                    <Button
                      key="apply"
                      type="link"
                      onClick={() => {
                        const content = item.content || ''
                        if (editingType === 'chapter' && selectedChapter) {
                          setSelectedChapter((prev) =>
                            prev ? { ...prev, content, wordCount: content.replace(/\s+/g, '').length } : prev
                          )
                        } else if (editingType === 'document' && selectedDocument) {
                          setSelectedDocument((prev) =>
                            prev ? { ...prev, content, wordCount: content.replace(/\s+/g, '').length } : prev
                          )
                        }
                        onContentChange(content)
                        setHistoryModalVisible(false)
                        message.success('已应用该历史版本内容')
                      }}
                    >
                      应用此版本
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span>{new Date(item.createdAt).toLocaleString()}</span>
                        <Tag>
                          {item.sourceType === 'AUTO_SAVE'
                            ? '自动保存'
                            : item.sourceType === 'MANUAL_SAVE'
                            ? '手动保存'
                            : item.sourceType === 'AI_REPLACE'
                            ? 'AI替换正文'
                            : item.sourceType}
                        </Tag>
                        {typeof item.diffRatio === 'number' && (
                          <span style={{ color: '#999', fontSize: 12 }}>
                            变动约 {item.diffRatio.toFixed(1)}%
                          </span>
                        )}
                      </div>
                    }
                    description={
                      <div
                        style={{
                          marginTop: 8,
                          maxHeight: 80,
                          overflow: 'hidden',
                          whiteSpace: 'pre-wrap',
                          fontSize: 12,
                          color: '#666',
                          background: '#fafafa',
                          padding: 8,
                          borderRadius: 4,
                        }}
                      >
                        {(item.content || '').slice(0, 300)}
                        {(item.content || '').length > 300 ? '...' : ''}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          )}
        </div>
      </Modal>
      
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
      
      {/* 章节章纲弹窗 */}
      <Modal
        title={<span style={{ fontSize: '16px', fontWeight: 600 }}>📋 章节章纲</span>}
        open={chapterOutlineLoading || !!editingChapterOutline || chapterOutlineListVisible}
        onCancel={() => {
          setChapterOutlineListVisible(false)
          setChapterOutlineVolumeId(null)
          setEditingChapterOutline(null)
        }}
        footer={[
          <button
            key="close"
            onClick={() => {
              setChapterOutlineListVisible(false)
              setChapterOutlineVolumeId(null)
              setEditingChapterOutline(null)
            }}
            style={{
              padding: '8px 20px',
              border: '1px solid #d9d9d9',
              borderRadius: '6px',
              background: '#fff',
              cursor: 'pointer',
              marginRight: '8px',
              fontSize: '14px',
              transition: 'all 0.3s'
            }}
          >
            关闭
          </button>,
          <button
            key="save"
            onClick={handleSaveChapterOutline}
            disabled={
              chapterOutlineLoading || !editingChapterOutline?.outlineId
            }
            style={{
              padding: '8px 20px',
              border: 'none',
              borderRadius: '6px',
              background:
                chapterOutlineLoading || !editingChapterOutline?.outlineId ? '#d9d9d9' : '#52c41a',
              color: '#fff',
              cursor:
                chapterOutlineLoading || !editingChapterOutline?.outlineId ? 'not-allowed' : 'pointer',
              fontSize: '14px',
              fontWeight: 500,
              transition: 'all 0.3s'
            }}
          >
            {chapterOutlineLoading ? '保存中...' : '💾 保存章纲'}
          </button>,
        ]}
        width={1000}
      >
        <div
          style={{
            display: 'flex',
            gap: '16px',
            alignItems: 'stretch',
            minHeight: '320px',
          }}
        >
          {chapterOutlineListVisible && (
            <div
              style={{
                width: '260px',
                paddingRight: '16px',
                borderRight: '1px solid #f0f0f0',
                transition: 'all 0.2s ease',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 8,
                }}
              >
                <span style={{ fontSize: 13, fontWeight: 500 }}>本卷章纲列表</span>
                <button
                  onClick={() => setChapterOutlineListVisible(false)}
                  style={{
                    padding: '4px 10px',
                    borderRadius: '4px',
                    border: '1px solid #d9d9d9',
                    background: '#fff',
                    cursor: 'pointer',
                    fontSize: 12,
                  }}
                >
                  收起
                </button>
              </div>
              <div
                style={{
                  maxHeight: '360px',
                  overflowY: 'auto',
                  border: '1px solid #f0f0f0',
                  borderRadius: '4px',
                  padding: '8px',
                  background: '#fafafa',
                }}
              >
                {chapterOutlineListLoading ? (
                  <div style={{ textAlign: 'center', padding: '24px 0' }}>
                    <Spin size="small" />
                    <div style={{ marginTop: 8, fontSize: 12, color: '#666' }}>
                      正在加载本卷章纲...
                    </div>
                  </div>
                ) : chapterOutlineVolumeId ? (
                  chapterOutlineList.length > 0 ? (
                    chapterOutlineList.map((item) => {
                      const isActive =
                        editingChapterOutline &&
                        item.globalChapterNumber &&
                        editingChapterOutline.globalChapterNumber === item.globalChapterNumber
                      return (
                        <div
                          key={item.id}
                          onClick={() => handleSelectOutlineChapter(item.globalChapterNumber)}
                          style={{
                            padding: '6px 8px',
                            borderRadius: '4px',
                            marginBottom: '6px',
                            cursor: 'pointer',
                            background: isActive ? '#e6f7ff' : '#fff',
                            border: isActive
                              ? '1px solid #1890ff'
                              : '1px solid #f0f0f0',
                          }}
                        >
                          <div
                            style={{
                              fontSize: 13,
                              fontWeight: 500,
                              marginBottom: 2,
                            }}
                          >
                            第
                            {item.chapterInVolume ??
                              item.globalChapterNumber ??
                              '-'}
                            章
                          </div>
                          <div
                            style={{
                              fontSize: 12,
                              color: '#666',
                              marginBottom: 2,
                            }}
                          >
                            {item.emotionalTone || '情感基调未设定'}
                          </div>
                          <div style={{ fontSize: 12, color: '#999' }}>
                            状态：{getOutlineStatusText(item.status)}
                          </div>
                        </div>
                      )
                    })
                  ) : (
                    <div style={{ fontSize: 12, color: '#999' }}>
                      当前卷尚未生成任何章纲
                    </div>
                  )
                ) : (
                  <div style={{ fontSize: 12, color: '#999' }}>
                    暂未识别当前章节所属的卷
                  </div>
                )}
              </div>
            </div>
          )}
          <div
            style={{
              flex: 1,
              paddingLeft: chapterOutlineListVisible ? '16px' : 0,
              transition: 'all 0.2s ease',
            }}
          >
            {chapterOutlineLoading && !editingChapterOutline ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}>
                <Spin size="large" />
                <div style={{ marginTop: 16, color: '#666' }}>正在加载章纲...</div>
              </div>
            ) : editingChapterOutline ? (
              <>
                <div
                  style={{
                    marginBottom: 16,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <div
                      style={{
                        fontWeight: 600,
                        fontSize: 14,
                        marginBottom: 4,
                      }}
                    >
                      第{' '}
                      {editingChapterOutline.globalChapterNumber ??
                        editingChapterOutline.chapterInVolume ??
                        '-'}{' '}
                      章
                      {editingChapterOutline.volumeNumber
                        ? ` · 第${editingChapterOutline.volumeNumber}卷`
                        : ''}
                    </div>
                    <div style={{ fontSize: 12, color: '#666' }}>
                      情感基调：
                      <span>
                        {editingChapterOutline.emotionalTone
                          ? editingChapterOutline.emotionalTone
                          : '未设置'}
                      </span>
                      <span style={{ marginLeft: 12 }}>
                        状态：{getOutlineStatusText(editingChapterOutline.status)}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={async () => {
                      const nextVisible = !chapterOutlineListVisible
                      setChapterOutlineListVisible(nextVisible)

                      if (
                        nextVisible &&
                        chapterOutlineVolumeId &&
                        chapterOutlineList.length === 0
                      ) {
                        try {
                          setChapterOutlineListLoading(true)
                          const list = await getChapterOutlinesByVolume(
                            chapterOutlineVolumeId,
                            true
                          )
                          setChapterOutlineList(list)
                        } catch (e) {
                          console.error('加载卷章纲列表失败:', e)
                          message.error('加载本卷章纲列表失败')
                        } finally {
                          setChapterOutlineListLoading(false)
                        }
                      }
                    }}
                    style={{
                      padding: '4px 10px',
                      borderRadius: '4px',
                      border: '1px solid #d9d9d9',
                      background: '#fff',
                      cursor: 'pointer',
                      fontSize: 12,
                    }}
                  >
                    {chapterOutlineListVisible ? '收起列表' : '本卷章纲列表'}
                  </button>
                </div>
                <div style={{ marginBottom: 12 }}>
                  <div
                    style={{
                      fontSize: 12,
                      color: '#999',
                      marginBottom: 4,
                    }}
                  >
                    本章剧情方向
                  </div>
                  <textarea
                    value={editingChapterOutline.direction}
                    onChange={(e) =>
                      setEditingChapterOutline((prev) =>
                        prev ? { ...prev, direction: e.target.value } : prev
                      )
                    }
                    placeholder="简要说明本章要写什么、走向如何"
                    style={{
                      width: '100%',
                      minHeight: '100px',
                      padding: '8px',
                      border: '1px solid #d9d9d9',
                      borderRadius: '4px',
                      fontSize: '13px',
                      lineHeight: 1.6,
                      resize: 'vertical',
                    }}
                  />
                </div>
                <div style={{ marginBottom: 12 }}>
                  <div
                    style={{
                      fontSize: 12,
                      color: '#999',
                      marginBottom: 4,
                    }}
                  >
                    关键剧情点（每行一个）
                  </div>
                  <textarea
                    value={editingChapterOutline.keyPlotPoints}
                    onChange={(e) =>
                      setEditingChapterOutline((prev) =>
                        prev ? { ...prev, keyPlotPoints: e.target.value } : prev
                      )
                    }
                    placeholder="例如：主角做出关键决定；反派伏笔出现；冲突升级等"
                    style={{
                      width: '100%',
                      minHeight: '120px',
                      padding: '8px',
                      border: '1px solid #d9d9d9',
                      borderRadius: '4px',
                      fontSize: '13px',
                      lineHeight: 1.6,
                      resize: 'vertical',
                    }}
                  />
                </div>
                <div style={{ display: 'flex', gap: 12 }}>
                  <div style={{ flex: 1 }}>
                    <div
                      style={{
                        fontSize: 12,
                        color: '#999',
                        marginBottom: 4,
                      }}
                    >
                      支线 / 人物刻画
                    </div>
                    <textarea
                      value={editingChapterOutline.subplot}
                      onChange={(e) =>
                        setEditingChapterOutline((prev) =>
                          prev ? { ...prev, subplot: e.target.value } : prev
                        )
                      }
                      placeholder="可选：本章想强化的支线或人设"
                      style={{
                        width: '100%',
                        minHeight: '80px',
                        padding: '8px',
                        border: '1px solid #d9d9d9',
                        borderRadius: '4px',
                        fontSize: '13px',
                        lineHeight: 1.6,
                        resize: 'vertical',
                      }}
                    />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div
                      style={{
                        fontSize: 12,
                        color: '#999',
                        marginBottom: 4,
                      }}
                    >
                      对手与赌注
                    </div>
                    <textarea
                      value={editingChapterOutline.antagonism}
                      onChange={(e) =>
                        setEditingChapterOutline((prev) =>
                          prev ? { ...prev, antagonism: e.target.value } : prev
                        )
                      }
                      placeholder="可选：本章的阻力、风险和代价"
                      style={{
                        width: '100%',
                        minHeight: '80px',
                        padding: '8px',
                        border: '1px solid #d9d9d9',
                        borderRadius: '4px',
                        fontSize: '13px',
                        lineHeight: 1.6,
                        resize: 'vertical',
                      }}
                    />
                  </div>
                </div>
              </>
            ) : (
              <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
                暂无章纲内容，请先选择左侧章节，或在其它规划页面生成本卷章纲后再查看。
              </div>
            )}
          </div>
        </div>
      </Modal>
      
      {/* 历史版本预览抽屉 */}
      <Drawer
        title={
          <span style={{ fontSize: '16px', fontWeight: 600 }}>
            历史版本预览
          </span>
        }
        placement="right"
        width={600}
        mask={false}
        open={historyPreviewVisible}
        onClose={() => {
          setHistoryPreviewVisible(false)
          setHistoryPreviewItem(null)
        }}
      >
        <div style={{ padding: 0 }}>
          {historyPreviewItem ? (
            <>
              <div
                style={{
                  marginBottom: 12,
                  fontSize: 13,
                  color: '#666',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <span>{new Date(historyPreviewItem.createdAt).toLocaleString()}</span>
                <Tag>
                  {historyPreviewItem.sourceType === 'AUTO_SAVE'
                    ? '自动保存'
                    : historyPreviewItem.sourceType === 'MANUAL_SAVE'
                    ? '手动保存'
                    : historyPreviewItem.sourceType === 'AI_REPLACE'
                    ? 'AI替换正文'
                    : historyPreviewItem.sourceType}
                </Tag>
                {typeof historyPreviewItem.diffRatio === 'number' && (
                  <span style={{ color: '#999', fontSize: 12 }}>
                    变动约 {historyPreviewItem.diffRatio.toFixed(1)}%
                  </span>
                )}
              </div>
              <div
                style={{
                  whiteSpace: 'pre-wrap',
                  fontSize: '14px',
                  lineHeight: '1.8',
                  color: '#333',
                  background: '#fafafa',
                  padding: '16px',
                  borderRadius: '6px',
                  border: '1px solid #f0f0f0',
                  maxHeight: 'calc(100vh - 220px)',
                  overflowY: 'auto',
                }}
              >
                {historyPreviewItem.content || ''}
              </div>
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              请选择要查看的历史版本
            </div>
          )}
        </div>
      </Drawer>

      {/* AI审稿弹窗 */}
      <Drawer
        title={<span style={{ fontSize: '16px', fontWeight: 600 }}>📝 AI审稿报告</span>}
        placement="right"
        width={600}
        mask={false}
        open={reviewDrawerVisible}
        onClose={() => {
          setReviewDrawerVisible(false)
          setReviewResult('')
        }}
        footer={
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              onClick={() => {
                setReviewDrawerVisible(false)
                setReviewResult('')
              }}
              style={{
                padding: '8px 20px',
                border: '1px solid #d9d9d9',
                borderRadius: '6px',
                background: '#fff',
                cursor: 'pointer',
                fontSize: '14px'
              }}
            >
              关闭
            </button>
          </div>
        }
      >
        <div style={{ padding: '0' }}>
          {!reviewResult && !isReviewing ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <div style={{ marginBottom: '16px', color: '#666', fontSize: '14px' }}>
                点击下方按钮开始AI审稿分析
              </div>
              <button
                onClick={executeReviewManuscript}
                style={{
                  padding: '10px 24px',
                  border: 'none',
                  borderRadius: '6px',
                  background: '#1890ff',
                  color: '#fff',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: 500
                }}
              >
                开始AI审稿
              </button>
            </div>
          ) : isReviewing && !reviewResult ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
              <div style={{ marginTop: '16px', color: '#666' }}>AI正在审稿中，请稍候...</div>
            </div>
          ) : reviewResult ? (
            <div>
              <div style={{ marginBottom: '12px', color: '#1890ff', fontSize: '13px', fontWeight: 500 }}>
                ✓ 审稿完成
              </div>
              <div style={{
                whiteSpace: 'pre-wrap',
                fontSize: '14px',
                lineHeight: '1.8',
                color: '#333',
                background: '#f0f5ff',
                padding: '16px',
                borderRadius: '6px',
                border: '1px solid #adc6ff',
                maxHeight: 'calc(100vh - 250px)',
                overflowY: 'auto'
              }}>
                {reviewResult}
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              暂无审稿结果
            </div>
          )}
        </div>
      </Drawer>
      
      {/* AI消痕抽屉 */}
      <Drawer
        title={<span style={{ fontSize: '16px', fontWeight: 600 }}>🧹 AI消痕处理</span>}
        placement="right"
        width={600}
        mask={false}
        open={traceRemovalDrawerVisible}
        onClose={() => {
          setTraceRemovalDrawerVisible(false)
          setProcessedContent('')
        }}
        footer={
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              onClick={() => {
                setTraceRemovalDrawerVisible(false)
                setProcessedContent('')
              }}
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
            <button
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
                  setProcessedContent('')
                }
              }}
              disabled={!processedContent}
              style={{
                padding: '8px 20px',
                border: 'none',
                borderRadius: '6px',
                background: processedContent ? '#52c41a' : '#d9d9d9',
                color: '#fff',
                cursor: processedContent ? 'pointer' : 'not-allowed'
              }}
            >
              应用到正文
            </button>
          </div>
        }
      >
        <div style={{ padding: '0' }}>
          {!processedContent && !isRemovingTrace ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <div style={{ marginBottom: '16px', color: '#666', fontSize: '14px' }}>
                点击下方按钮开始AI消痕处理
              </div>
              <button
                onClick={executeRemoveAITrace}
                style={{
                  padding: '10px 24px',
                  border: 'none',
                  borderRadius: '6px',
                  background: '#1890ff',
                  color: '#fff',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: 500
                }}
              >
                开始AI消痕
              </button>
            </div>
          ) : isRemovingTrace && !processedContent ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
              <div style={{ marginTop: '16px', color: '#666' }}>正在AI消痕处理中...</div>
            </div>
          ) : processedContent ? (
            <div>
              <div style={{ marginBottom: '12px', color: '#52c41a', fontSize: '13px', fontWeight: 500 }}>
                ✓ 处理后内容（共 {processedContent.replace(/\s+/g, '').length} 字）
              </div>
              <div style={{
                whiteSpace: 'pre-wrap',
                fontSize: '14px',
                lineHeight: '1.8',
                color: '#333',
                background: '#f6ffed',
                padding: '16px',
                borderRadius: '6px',
                border: '1px solid #b7eb8f',
                maxHeight: 'calc(100vh - 250px)',
                overflowY: 'auto'
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
      </Drawer>
      
      {/* AI精简抽屉 */}
      <Drawer
        title={<span style={{ fontSize: '16px', fontWeight: 600 }}>✂️ AI精简优化</span>}
        placement="right"
        width={600}
        mask={false}
        open={streamlineDrawerVisible}
        onClose={() => {
          setStreamlineDrawerVisible(false)
          setStreamlinedContent('')
          setStreamlineTargetLength('')
        }}
        footer={
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
            <button
              onClick={() => {
                setStreamlineDrawerVisible(false)
                setStreamlinedContent('')
              }}
              style={{
                padding: '8px 20px',
                border: '1px solid #d9d9d9',
                borderRadius: '6px',
                background: '#fff',
                cursor: 'pointer',
                fontSize: '14px'
              }}
            >
              关闭
            </button>
            <button
              onClick={() => {
                if (streamlinedContent) {
                  if (editingType === 'chapter' && selectedChapter) {
                    setSelectedChapter((prev) => prev ? { ...prev, content: streamlinedContent } : prev)
                  } else if (editingType === 'document' && selectedDocument) {
                    setSelectedDocument((prev) => prev ? { ...prev, content: streamlinedContent } : prev)
                  }
                  onContentChange(streamlinedContent)
                  message.success('已应用AI精简后的内容')
                  setStreamlineDrawerVisible(false)
                  setStreamlinedContent('')
                }
              }}
              disabled={!streamlinedContent}
              style={{
                padding: '8px 20px',
                border: 'none',
                borderRadius: '6px',
                background: streamlinedContent ? '#ff9800' : '#d9d9d9',
                color: '#fff',
                cursor: streamlinedContent ? 'pointer' : 'not-allowed',
                fontSize: '14px',
                fontWeight: 500
              }}
            >
              应用到正文
            </button>
          </div>
        }
      >
        <div style={{ padding: '0' }}>
          {!streamlinedContent && !isStreamlining ? (
            <div style={{ padding: '24px 16px' }}>
              <div style={{ marginBottom: '12px', color: '#666', fontSize: '14px', lineHeight: '1.6' }}>
                AI将分析文章内容，在不改变主要情节和爽感的前提下，
                精简无意义或拖沓的描写，适当加快节奏。
              </div>
              <div style={{ marginBottom: '16px', display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: '13px', color: '#666' }}>目标字数：</span>
                <input
                  type="number"
                  min={1}
                  placeholder="请输入精简后的字数，如 1500"
                  value={streamlineTargetLength}
                  onChange={(e) => setStreamlineTargetLength(e.target.value)}
                  style={{
                    width: 180,
                    padding: '6px 8px',
                    fontSize: '13px',
                    borderRadius: 4,
                    border: '1px solid #d9d9d9'
                  }}
                />
                <span style={{ fontSize: '12px', color: '#999' }}>不填则按系统默认比例精简</span>
              </div>
              <div style={{ textAlign: 'center' }}>
                <button
                  onClick={executeStreamlineContent}
                  style={{
                    padding: '10px 24px',
                    border: 'none',
                    borderRadius: '6px',
                    background: '#ff9800',
                    color: '#fff',
                    cursor: 'pointer',
                    fontSize: '14px',
                    fontWeight: 500
                  }}
                >
                  开始AI精简
                </button>
              </div>
            </div>
          ) : isStreamlining && !streamlinedContent ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
              <div style={{ marginTop: '16px', color: '#666' }}>AI正在精简优化中，请稍候...</div>
            </div>
          ) : streamlinedContent ? (
            <div>
              <div style={{ marginBottom: '12px', color: '#ff9800', fontSize: '13px', fontWeight: 500 }}>
                ✓ 精简后内容（共 {streamlinedContent.replace(/\s+/g, '').length} 字）
              </div>
              <div style={{
                whiteSpace: 'pre-wrap',
                fontSize: '14px',
                lineHeight: '1.8',
                color: '#333',
                background: '#fff8e1',
                padding: '16px',
                borderRadius: '6px',
                border: '1px solid #ffcc80',
                maxHeight: 'calc(100vh - 250px)',
                overflowY: 'auto'
              }}>
                {streamlinedContent}
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              暂无处理结果
            </div>
          )}
        </div>
      </Drawer>
    </Layout>
  )
}

export default WritingStudioPage
