import React, { useCallback, useEffect, useMemo, useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { App, Layout, Spin, Modal, List, Button, Tag, Drawer, Progress, InputNumber, Tooltip } from 'antd'
import {
  ExportOutlined,
  FileTextOutlined,
  RocketOutlined,
  CompassOutlined,
  BookOutlined,
  DeleteOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SaveOutlined,
  AimOutlined,
  ReloadOutlined,
  UploadOutlined
} from '@ant-design/icons'
import FileTree from '@/components/writing/FileTree'
import ExportModal from '@/components/writing/ExportModal'
import EditorPanel from '@/components/writing/EditorPanel'
import ToolPanel from '@/components/writing/ToolPanel'
import MarkdownRenderer from '@/components/MarkdownRenderer'
import GraphDataModal from '@/components/graph/GraphDataModal'
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
  getChapterSummaryById,
  createChapter,
  updateChapter,
  autoSaveChapter,
  deleteChapter,
} from '@/services/chapterServiceForStudio'
import {
  getReferenceFiles,
  getReferenceFileById,
  uploadReferenceFile,
  deleteReferenceFile,
} from '@/services/referenceFileService'
import {
  getAIConversations,
  clearAIConversations,
} from '@/services/aiConversationService'
import type { AIConversation } from '@/services/aiConversationService'
import { getAllGenerators, AiGenerator } from '@/services/aiGeneratorService'
import novelService, { type OutlineContextMode } from '@/services/novelService'
import novelVolumeService, { NovelVolume } from '@/services/novelVolumeService'
import {
  getChapterOutline,
  getChapterOutlinesByVolume,
  updateChapterOutline as updateVolumeChapterOutline,
  createChapterOutline,
  generateVolumeChapterOutlines,
  regenerateVolumeChapterOutlines,
  type VolumeChapterOutline,
} from '@/services/volumeChapterOutlineService'
import {
  getVolumeCharacterProfiles,
  generateVolumeCharacterProfiles,
  createVolumeCharacterProfile,
  updateVolumeCharacterProfile,
  deleteVolumeCharacterProfile,
  type VolumeCharacterProfile,
} from '@/services/volumeCharacterProfileService'
import { getChapterHistory, getDocumentHistory, type WritingVersionHistory } from '@/services/writingHistoryService'
import api from '@/services/api'
import { withAIConfig, checkAIConfig, AI_CONFIG_ERROR_MESSAGE } from '@/utils/aiRequest'
import { extractHttpErrorMessage, formatAIErrorMessage } from '@/utils/errorHandler'
import { extractSseEvents } from '@/utils/sse'
import './WritingStudioPage.css'

const { Sider, Content } = Layout

const escapeRegExp = (value: string): string => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const CHAPTER_HEADING_LINE_PATTERN = /^第\s*(?:\d+|[零〇一二两三四五六七八九十百千万]+)\s*章(?:\s*[：:·\-—]\s*|\s+)[^\n]{1,40}$/u
const INLINE_CHAPTER_HEADING_PATTERN = /^(第\s*(?:\d+|[零〇一二两三四五六七八九十百千万]+)\s*章)(\s*[：:·\-—]\s*|\s+)(.+)$/u
const INLINE_CHAPTER_HEADING_SPLIT_PATTERN =
  /^(第\s*(?:\d+|[零〇一二两三四五六七八九十百千万]+)\s*章(?:\s*[：:·\-—]\s*|\s+).+?)(?:\s{2,}|\s+(?=[^\s]{1,8}[，。！？?!；：…])|\s+(?=[“‘「『"']))(.+)$/u
const INLINE_BODY_PUNCTUATION_PATTERN = /[，。！？?!；：…]/u
const INLINE_OPENING_QUOTE_PATTERN = /^[“‘「『"']/u
const VOLUME_PLANNING_MODAL_Z_INDEX = 1200
const VOLUME_PLANNING_DETAIL_MODAL_Z_INDEX = VOLUME_PLANNING_MODAL_Z_INDEX + 20
const VOLUME_PLANNING_REGEN_MODAL_Z_INDEX = VOLUME_PLANNING_DETAIL_MODAL_Z_INDEX + 20
const VOLUME_PLANNING_GENERATE_MODAL_Z_INDEX = VOLUME_PLANNING_REGEN_MODAL_Z_INDEX + 20

type OutlineGenerationSegment = {
  volumeId: number
  volumeNumber: number | null
  volumeTitle: string
  count: number
}

type VolumeProfileEditableKey =
  | 'currentPersona'
  | 'coreMotivation'
  | 'stanceRelationships'
  | 'unfinishedGoals'
  | 'behaviorBottomLine'

const VOLUME_PROFILE_EDITABLE_FIELDS: Array<{ key: VolumeProfileEditableKey; label: string }> = [
  { key: 'currentPersona', label: '当前人设' },
  { key: 'coreMotivation', label: '核心动机' },
  { key: 'stanceRelationships', label: '立场关系' },
  { key: 'unfinishedGoals', label: '未完成目标' },
  { key: 'behaviorBottomLine', label: '行为底线' },
]

const extractLeadingLine = (text: string): { line: string; rest: string } => {
  const normalized = text.replace(/\r\n?/g, '\n').trimStart()
  const newlineIndex = normalized.indexOf('\n')

  if (newlineIndex === -1) {
    return {
      line: normalized.trim(),
      rest: '',
    }
  }

  return {
    line: normalized.slice(0, newlineIndex).trim(),
    rest: normalized.slice(newlineIndex).replace(/^\n+/, '').trimStart(),
  }
}

const isGeneratedChapterHeadingLine = (line: string, currentTitle?: string | null): boolean => {
  if (!line) {
    return false
  }

  if (CHAPTER_HEADING_LINE_PATTERN.test(line)) {
    return true
  }

  return Boolean(currentTitle && line === currentTitle.trim())
}

const findInlineHeadingBodySplitIndex = (text: string): number => {
  const whitespacePattern = /\s+/g
  let match: RegExpExecArray | null

  while ((match = whitespacePattern.exec(text)) !== null) {
    const boundary = match.index ?? -1

    if (boundary <= 0) {
      continue
    }

    const titlePart = text.slice(0, boundary).trim()
    const bodyPart = text.slice(boundary + match[0].length).trim()

    if (!titlePart || !bodyPart || titlePart.length > 40) {
      continue
    }

    if (INLINE_OPENING_QUOTE_PATTERN.test(bodyPart)) {
      return boundary
    }

    const punctuationIndex = bodyPart.search(INLINE_BODY_PUNCTUATION_PATTERN)

    if (punctuationIndex >= 1 && punctuationIndex <= 8) {
      return boundary
    }
  }

  return -1
}

const splitInlineChapterHeading = (line: string): { heading: string; body: string } => {
  const directMatch = line.match(INLINE_CHAPTER_HEADING_SPLIT_PATTERN)

  if (directMatch) {
    const [, heading, body] = directMatch

    if (heading.trim() && body.trim()) {
      return {
        heading: heading.trim(),
        body: body.trim(),
      }
    }
  }

  const match = line.match(INLINE_CHAPTER_HEADING_PATTERN)

  if (!match) {
    return { heading: '', body: '' }
  }

  const [, prefix, separator, remainder] = match
  const splitIndex = findInlineHeadingBodySplitIndex(remainder)

  if (splitIndex === -1) {
    return { heading: '', body: '' }
  }

  const titlePart = remainder.slice(0, splitIndex).trim()
  const bodyPart = remainder.slice(splitIndex).trim()

  if (!titlePart || !bodyPart) {
    return { heading: '', body: '' }
  }

  return {
    heading: `${prefix}${separator}${titlePart}`.trim(),
    body: bodyPart,
  }
}

const extractChapterTitleFromHeading = (heading: string): string => {
  if (!heading) {
    return ''
  }

  const inlineMatch = heading.match(INLINE_CHAPTER_HEADING_PATTERN)

  if (inlineMatch) {
    return inlineMatch[3].trim()
  }

  return heading.trim()
}

const stripGeneratedChapterHeading = (content: string, currentTitle?: string | null): string => {
  if (!content) return ''

  const { line, rest } = extractLeadingLine(content)

  const inlineHeading = splitInlineChapterHeading(line)

  if (inlineHeading.heading) {
    return [inlineHeading.body, rest].filter(Boolean).join('\n').trimStart()
  }

  if (isGeneratedChapterHeadingLine(line, currentTitle)) {
    return rest
  }

  return content.trimStart()
}

const DIALOGUE_LEFT_QUOTES = new Set(['“', '‘', '「', '『'])
const DIALOGUE_RIGHT_QUOTES = new Set(['”', '’', '」', '』'])
const SENTENCE_ENDING_CHARS = new Set(['。', '！', '？', '!', '?', '…'])
const STRUCTURED_TEXT_PATTERN = /(^|\n)\s*(#{1,6}\s|[-*+]\s|>\s|\d+\.\s|```|~~~|\|.+\|)/m

const isWhitespace = (char: string | undefined): boolean =>
  !char || char === ' ' || char === '\n' || char === '\r' || char === '\t' || char === '\u3000'

const getPreviousNonWhitespaceChar = (text: string, fromIndex: number): string => {
  for (let i = fromIndex - 1; i >= 0; i -= 1) {
    if (!isWhitespace(text[i])) {
      return text[i]
    }
  }

  return ''
}

const getNextNonWhitespaceIndex = (text: string, fromIndex: number): number => {
  for (let i = fromIndex; i < text.length; i += 1) {
    if (!isWhitespace(text[i])) {
      return i
    }
  }

  return -1
}

const isLikelyOpeningAsciiQuote = (text: string, index: number): boolean => {
  const previousChar = getPreviousNonWhitespaceChar(text, index)

  if (!previousChar) {
    return true
  }

  return /[([{<（【《〈「『“‘，。！？?!；：、…,:;-]/u.test(previousChar)
}

const isLeftQuoteChar = (text: string, index: number): boolean => {
  const char = text[index]

  if (DIALOGUE_LEFT_QUOTES.has(char)) {
    return true
  }

  return (char === '"' || char === "'") && isLikelyOpeningAsciiQuote(text, index)
}

const isRightQuoteChar = (text: string, index: number): boolean => {
  const char = text[index]

  if (DIALOGUE_RIGHT_QUOTES.has(char)) {
    return true
  }

  return (char === '"' || char === "'") && !isLikelyOpeningAsciiQuote(text, index)
}

const isSentenceEndingChar = (char: string): boolean => SENTENCE_ENDING_CHARS.has(char)

const getPreviousSentenceContextChar = (text: string, fromIndex: number): string => {
  for (let i = fromIndex - 1; i >= 0; i -= 1) {
    if (isWhitespace(text[i]) || isRightQuoteChar(text, i)) {
      continue
    }

    return text[i]
  }

  return ''
}

const shouldPreserveStructuredText = (text: string): boolean => STRUCTURED_TEXT_PATTERN.test(text)

const normalizeNovelText = (text: string): string => {
  let normalized = text.replace(/\r\n?/g, '\n')

  normalized = normalized.replace(
    /[ \t\u3000]*\n+[ \t\u3000]*(?=[，。！？?!；：、…》」』】）〉”’"'])/gu,
    ''
  )
  normalized = normalized.replace(/\n{3,}/g, '\n\n')

  return normalized.trim()
}

const normalizeNovelBlock = (block: string): string =>
  block
    .replace(/[ \t\u3000]*\n+[ \t\u3000]*/gu, '')
    .replace(/[ \t\u3000]{2,}/gu, ' ')
    .trim()

const extractLeadingChapterHeading = (text: string): { heading: string; body: string } => {
  const { line, rest } = extractLeadingLine(text)

  const inlineHeading = splitInlineChapterHeading(line)

  if (inlineHeading.heading) {
    return {
      heading: inlineHeading.heading,
      body: [inlineHeading.body, rest].filter(Boolean).join('\n').trim(),
    }
  }

  if (CHAPTER_HEADING_LINE_PATTERN.test(line)) {
    return {
      heading: line,
      body: rest,
    }
  }

  return {
    heading: '',
    body: text,
  }
}

const splitNovelBlockIntoParagraphs = (block: string): string[] => {
  const flattenedBlock = normalizeNovelBlock(block)

  if (!flattenedBlock) {
    return []
  }

  const paragraphs: string[] = []
  let current = ''
  let quoteDepth = 0

  const pushCurrentParagraph = () => {
    const trimmed = current.trim()

    if (trimmed) {
      paragraphs.push(trimmed)
    }

    current = ''
  }

  for (let i = 0; i < flattenedBlock.length; i += 1) {
    const char = flattenedBlock[i]
    current += char

    if (isLeftQuoteChar(flattenedBlock, i)) {
      quoteDepth += 1
      continue
    }

    if (isRightQuoteChar(flattenedBlock, i)) {
      quoteDepth = Math.max(0, quoteDepth - 1)

      const previousContextChar = getPreviousSentenceContextChar(flattenedBlock, i)
      const nextIndex = getNextNonWhitespaceIndex(flattenedBlock, i + 1)

      if (
        isSentenceEndingChar(previousContextChar) &&
        (nextIndex === -1 || isLeftQuoteChar(flattenedBlock, nextIndex))
      ) {
        pushCurrentParagraph()
      }

      continue
    }

    if (!isSentenceEndingChar(char)) {
      continue
    }

    while (i + 1 < flattenedBlock.length && isSentenceEndingChar(flattenedBlock[i + 1])) {
      current += flattenedBlock[i + 1]
      i += 1
    }

    const nextIndex = getNextNonWhitespaceIndex(flattenedBlock, i + 1)

    if (quoteDepth > 0) {
      continue
    }

    if (nextIndex !== -1 && isRightQuoteChar(flattenedBlock, nextIndex)) {
      continue
    }

    pushCurrentParagraph()
  }

  pushCurrentParagraph()

  return paragraphs
}

const formatNovelParagraphs = (paragraphs: string[]): string =>
  paragraphs
    .map(paragraph => paragraph.trim())
    .filter(Boolean)
    .map(paragraph => `  ${paragraph}`)
    .join('\n\n')

/**
 * 格式化流式输出文本 - 按照小说段落格式
 * 每段之间有空行，每段开头有两个空格缩进
 */
const formatStreamOutput = (text: string): string => {
  if (!text) return ''

  const normalized = normalizeNovelText(text)

  if (!normalized) {
    return ''
  }

  if (shouldPreserveStructuredText(normalized)) {
    return normalized
  }

  const { heading, body } = extractLeadingChapterHeading(normalized)
  const paragraphs = body
    .split(/\n{2,}/)
    .map(splitNovelBlockIntoParagraphs)
    .flat()

  if (heading && !paragraphs.length) {
    return heading
  }

  if (!paragraphs.length) {
    return formatNovelParagraphs([normalizeNovelBlock(body)])
  }

  const formattedBody = formatNovelParagraphs(paragraphs)

  if (heading) {
    return `${heading}\n\n${formattedBody}`
  }

  return formattedBody
}

const safeFormatStreamOutput = (text: string): string => {
  try {
    return formatStreamOutput(text)
  } catch (error) {
    console.error('formatStreamOutput failed, fallback to raw text:', error)
    return text
  }
}

const decodeWritingMessageChunk = (data: string): string => {
  if (!data) {
    return ''
  }

  const trimmed = data.trim()
  if (!trimmed.startsWith('{')) {
    return data
  }

  try {
    const parsed = JSON.parse(trimmed) as { chunk?: unknown }
    if (typeof parsed.chunk === 'string') {
      return parsed.chunk
    }
  } catch (error) {
    console.warn('解析写作流消息失败，回退原始内容', error)
  }

  return data
}

const isUnexpectedStreamErrorChunk = (chunk: string): boolean => {
  const trimmed = chunk.trim()
  if (!trimmed) {
    return false
  }

  return (
    trimmed.includes('消息流出现异常') ||
    trimmed.includes('stream error') ||
    trimmed.includes('Stream error') ||
    /^❌\s*/.test(trimmed)
  )
}

const WRITING_STUDIO_PREFERENCES_KEY_PREFIX = 'writing-studio.preferences.'

type WritingStudioPreferences = {
  selectedModel?: string
  writingStyleId?: number | null
  temperature?: number
}

const getWritingStudioPreferencesKey = (novelId: number): string =>
  `${WRITING_STUDIO_PREFERENCES_KEY_PREFIX}${novelId}`

const readWritingStudioPreferences = (novelId: number): WritingStudioPreferences => {
  if (!Number.isFinite(novelId) || novelId <= 0 || typeof window === 'undefined') {
    return {}
  }

  try {
    const raw = window.localStorage.getItem(getWritingStudioPreferencesKey(novelId))
    if (!raw) {
      return {}
    }

    const parsed = JSON.parse(raw) as WritingStudioPreferences

    return {
      selectedModel: typeof parsed.selectedModel === 'string' ? parsed.selectedModel : undefined,
      writingStyleId:
        parsed.writingStyleId === null
          ? null
          : typeof parsed.writingStyleId === 'number' && Number.isFinite(parsed.writingStyleId)
          ? parsed.writingStyleId
          : undefined,
      temperature:
        typeof parsed.temperature === 'number' && Number.isFinite(parsed.temperature)
          ? parsed.temperature
          : undefined,
    }
  } catch (error) {
    console.warn('读取写作工作室偏好失败', error)
    return {}
  }
}

const writeWritingStudioPreferences = (
  novelId: number,
  preferences: WritingStudioPreferences
): void => {
  if (!Number.isFinite(novelId) || novelId <= 0 || typeof window === 'undefined') {
    return
  }

  try {
    const next: WritingStudioPreferences = {}

    if (typeof preferences.selectedModel === 'string' && preferences.selectedModel.trim()) {
      next.selectedModel = preferences.selectedModel.trim()
    }

    if (preferences.writingStyleId === null) {
      next.writingStyleId = null
    } else if (
      typeof preferences.writingStyleId === 'number' &&
      Number.isFinite(preferences.writingStyleId)
    ) {
      next.writingStyleId = preferences.writingStyleId
    }

    if (typeof preferences.temperature === 'number' && Number.isFinite(preferences.temperature)) {
      next.temperature = preferences.temperature
    }

    window.localStorage.setItem(getWritingStudioPreferencesKey(novelId), JSON.stringify(next))
  } catch (error) {
    console.warn('保存写作工作室偏好失败', error)
  }
}

const WritingStudioPage: React.FC = () => {
  const { novelId } = useParams<{ novelId: string }>()
  const novelIdNumber = Number(novelId)
  const { message, modal } = App.useApp()

  const [loading, setLoading] = useState(true)
  const [novelTitle, setNovelTitle] = useState('')
  const [viewportWidth, setViewportWidth] = useState(() =>
    typeof window === 'undefined' ? 1440 : window.innerWidth
  )
  const [toolsDrawerVisible, setToolsDrawerVisible] = useState(false)
  
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
  const [selectedModel, setSelectedModel] = useState<string>(
    () => readWritingStudioPreferences(novelIdNumber).selectedModel ?? ''
  )
  const [temperature, setTemperature] = useState<number>(
    () => readWritingStudioPreferences(novelIdNumber).temperature ?? 1.0
  )
  const [writingStyleId, setWritingStyleId] = useState<number | null>(
    () => readWritingStudioPreferences(novelIdNumber).writingStyleId ?? null
  )
  const [outlineContextMode, setOutlineContextMode] = useState<OutlineContextMode>('FULL')
  const [outlineContextModeSaving, setOutlineContextModeSaving] = useState(false)
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
  const isApplyingStudioPreferencesRef = useRef(false)
  const pendingAiOutputRef = useRef('')
  const aiOutputFlushTimerRef = useRef<number | null>(null)
  
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
  const [chapterOutlineList, setChapterOutlineList] = useState<VolumeChapterOutline[]>([])
  const [chapterOutlineListVisible, setChapterOutlineListVisible] = useState(false)
  const [editingChapterOutline, setEditingChapterOutline] = useState<{
    outlineId?: number
    globalChapterNumber?: number
    chapterInVolume?: number
    volumeNumber?: number
    direction: string
    foreshadowAction?: string
    foreshadowDetail?: string
    status?: string
    // 兼容字段：keyPlotPoints 作为剧情方向来源
    keyPlotPoints?: string
    emotionalTone?: string
    subplot?: string
    antagonism?: string
  } | null>(null)
  
  // 章纲缺失提醒弹窗状态
  const [outlineMissingModalVisible, setOutlineMissingModalVisible] = useState(false)
  // 标记提醒弹窗的来源：'generate' 从生成按钮进入（优先引导卷规划并保留“继续生成”），'outline' 从章纲按钮进入（直接引导生成细纲）
  const [outlineMissingSource, setOutlineMissingSource] = useState<'generate' | 'outline'>('generate')
  const [outlineGenerateModalVisible, setOutlineGenerateModalVisible] = useState(false)
  const [isGeneratingOutline, setIsGeneratingOutline] = useState(false)
  const [outlineGenerateProgress, setOutlineGenerateProgress] = useState<string>('')
  const [outlineGeneratePercent, setOutlineGeneratePercent] = useState<number>(0)
  const [outlineBatchSize, setOutlineBatchSize] = useState<number>(() => {
    try {
      const raw = localStorage.getItem('outlineBatchSize')
      const parsed = raw ? Number(raw) : 10
      if (Number.isFinite(parsed)) {
        return Math.min(15, Math.max(5, parsed))
      }
    } catch (e) {
      // ignore
    }
    return 10
  })
  const [outlineReminderInfo, setOutlineReminderInfo] = useState<{
    volumeId?: number
    lastOutlineChapterInVolume: number
    currentChapterInVolume?: number
    totalChapters?: number
  } | null>(null)
  const [profileGenerateModalVisible, setProfileGenerateModalVisible] = useState(false)
  const [profileGenerationRequirement, setProfileGenerationRequirement] = useState('')
  const [outlineGenerationRequirement, setOutlineGenerationRequirement] = useState('')
  const [planningVolumeId, setPlanningVolumeId] = useState<number | null>(null)
  const [manualPlanningVolumeId, setManualPlanningVolumeId] = useState<number | null>(null)
  const [profileCount, setProfileCount] = useState<number | null>(null)
  const [isGeneratingProfiles, setIsGeneratingProfiles] = useState(false)
  const [volumePlanningVisible, setVolumePlanningVisible] = useState(false)
  const [planningProfiles, setPlanningProfiles] = useState<VolumeCharacterProfile[]>([])
  const [planningProfilesLoading, setPlanningProfilesLoading] = useState(false)
  const [planningOutlinesLoading, setPlanningOutlinesLoading] = useState(false)
  const [profileDetailVisible, setProfileDetailVisible] = useState(false)
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileDeletingId, setProfileDeletingId] = useState<number | null>(null)
  const [editingProfile, setEditingProfile] = useState<VolumeCharacterProfile | null>(null)
  const [outlineDetailVisible, setOutlineDetailVisible] = useState(false)
  const [outlineDetailLoading, setOutlineDetailLoading] = useState(false)
  const [detailProfile, setDetailProfile] = useState<VolumeCharacterProfile | null>(null)
  const [detailOutline, setDetailOutline] = useState<VolumeChapterOutline | null>(null)
  const [detailOutlineEditing, setDetailOutlineEditing] = useState(false)
  const [detailOutlineSaving, setDetailOutlineSaving] = useState(false)
  const [editingDetailFields, setEditingDetailFields] = useState<Partial<VolumeChapterOutline>>({})
  // 重新生成细纲相关
  const [regenModalVisible, setRegenModalVisible] = useState(false)
  const [regenFrom, setRegenFrom] = useState<number>(1)
  const [regenTo, setRegenTo] = useState<number>(10)
  const [isRegenerating, setIsRegenerating] = useState(false)
  
  // AI审稿相关状态
  const [reviewDrawerVisible, setReviewDrawerVisible] = useState(false)
  const [reviewResult, setReviewResult] = useState<string>('')
  const [isReviewing, setIsReviewing] = useState(false)
  
  // 导出相关状态
  const [exportVisible, setExportVisible] = useState(false)
  const [exportModalTab, setExportModalTab] = useState<'export' | 'import'>('export')

  // AI消痕相关状态
  const [traceRemovalDrawerVisible, setTraceRemovalDrawerVisible] = useState(false)
  const [processedContent, setProcessedContent] = useState<string>('')
  const [isRemovingTrace, setIsRemovingTrace] = useState(false)

  // 概要相关状态
  const [summaryDrawerVisible, setSummaryDrawerVisible] = useState(false)
  const [summaryLoading, setSummaryLoading] = useState(false)
  const [summaryGenerating, setSummaryGenerating] = useState(false)
  const [summaryData, setSummaryData] = useState<any>(null)
  const [graphModalVisible, setGraphModalVisible] = useState(false)

  const scheduleAIOutputFlush = useCallback((content: string) => {
    pendingAiOutputRef.current = content

    if (aiOutputFlushTimerRef.current !== null) {
      return
    }

    aiOutputFlushTimerRef.current = window.setTimeout(() => {
      aiOutputFlushTimerRef.current = null
      setAIOutput(pendingAiOutputRef.current)
    }, 48)
  }, [])

  const flushAIOutput = useCallback((content: string) => {
    pendingAiOutputRef.current = content

    if (aiOutputFlushTimerRef.current !== null) {
      clearTimeout(aiOutputFlushTimerRef.current)
      aiOutputFlushTimerRef.current = null
    }

    setAIOutput(content)
  }, [])

  useEffect(() => {
    return () => {
      if (aiOutputFlushTimerRef.current !== null) {
        clearTimeout(aiOutputFlushTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    if (!novelIdNumber) {
      return
    }

    const preferences = readWritingStudioPreferences(novelIdNumber)
    isApplyingStudioPreferencesRef.current = true
    setSelectedModel(preferences.selectedModel ?? '')
    setWritingStyleId(preferences.writingStyleId ?? null)
    setTemperature(preferences.temperature ?? 1.0)
  }, [novelIdNumber])

  useEffect(() => {
    if (!novelIdNumber) {
      return
    }

    if (isApplyingStudioPreferencesRef.current) {
      const preferences = readWritingStudioPreferences(novelIdNumber)
      const matchesStoredPreferences =
        (preferences.selectedModel ?? '') === selectedModel &&
        (preferences.writingStyleId ?? null) === writingStyleId &&
        (preferences.temperature ?? 1.0) === temperature

      if (!matchesStoredPreferences) {
        return
      }

      isApplyingStudioPreferencesRef.current = false
      return
    }

    writeWritingStudioPreferences(novelIdNumber, {
      selectedModel,
      writingStyleId,
      temperature,
    })
  }, [novelIdNumber, selectedModel, temperature, writingStyleId])

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
          setOutlineContextMode(novel.outlineContextMode === 'SIMPLIFIED' ? 'SIMPLIFIED' : 'FULL')
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

        // 预加载所有文件夹的文档，确保 FileTree 能正确显示内容
        loadAllFoldersDocuments(finalFolders)
      } catch (error: any) {
        message.error(error?.message || '加载数据失败')
      } finally {
        setLoading(false)
      }
    }

    loadInitialData()
  }, [novelIdNumber])

  const handleOutlineContextModeChange = useCallback(async (mode: OutlineContextMode) => {
    if (!novelIdNumber || mode === outlineContextMode || outlineContextModeSaving) {
      return
    }

    const previousMode = outlineContextMode
    setOutlineContextMode(mode)
    setOutlineContextModeSaving(true)

    try {
      await novelService.updateNovel(novelIdNumber, { outlineContextMode: mode })
    } catch (error: any) {
      console.error('保存大纲上下文模式失败:', error)
      setOutlineContextMode(previousMode)
      message.error(error?.message || '保存大纲模式失败')
    } finally {
      setOutlineContextModeSaving(false)
    }
  }, [message, novelIdNumber, outlineContextMode, outlineContextModeSaving])

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

  const buildChapterSummaryViewData = (
    chapter: Chapter,
    extra?: { characterCount?: number; eventCount?: number; message?: string }
  ) => ({
    mode: 'chapter',
    chapterId: chapter.id,
    chapterNumber: chapter.chapterNumber,
    title: chapter.title || (chapter.chapterNumber != null ? `第 ${chapter.chapterNumber} 章` : '当前章节'),
    summary: chapter.summary || '',
    wordCount: chapter.wordCount || String(chapter.content || '').replace(/\s+/g, '').length,
    updatedAt: chapter.updatedAt || chapter.createdAt || '',
    characterCount: extra?.characterCount,
    eventCount: extra?.eventCount,
    message: extra?.message || '',
  })

  const syncChapterSummaryState = (chapterId: number, summary: string) => {
    setSelectedChapter((prev) => {
      if (!prev || prev.id !== chapterId) {
        return prev
      }

      return {
        ...prev,
        summary,
      }
    })

    setChapters((prev) =>
      prev.map((chapter) =>
        chapter.id === chapterId
          ? {
              ...chapter,
              summary,
            }
          : chapter
      )
    )
  }

  const loadSummary = async () => {
    setSummaryLoading(true)
    try {
      if (editingType === 'document') {
        if (!selectedDocument) {
          message.warning('请先选择一个文档')
          setSummaryData(null)
          return
        }

        setSummaryData({
          mode: 'document',
          title: selectedDocument.title || '当前文档',
          wordCount: String(selectedDocument.content || '').replace(/\s+/g, '').length,
          updatedAt: selectedDocument.updatedAt || selectedDocument.createdAt || '',
          preview: (selectedDocument.content || '').trim(),
          message: '当前“概要”仅支持章节内容。请切换到章节后使用。',
        })
        return
      }

      if (!selectedChapter?.id) {
        message.warning('请先选择一个章节')
        setSummaryData(null)
        return
      }

      const summaryResult = await getChapterSummaryById(selectedChapter.id)
      const chapterWithSummary = {
        ...selectedChapter,
        summary: summaryResult.summary || '',
      }

      syncChapterSummaryState(selectedChapter.id, chapterWithSummary.summary)
      setSummaryData(buildChapterSummaryViewData(chapterWithSummary))
    } catch (error: any) {
      console.error('加载概要失败:', error)
      message.error(error?.message || '加载概要失败')
      setSummaryData(null)
    } finally {
      setSummaryLoading(false)
    }
  }

  const handleGenerateSummary = async () => {
    if (editingType !== 'chapter' || !selectedChapter?.id) {
      message.warning('概要仅支持章节')
      return
    }

    if (!selectedChapter.content || !selectedChapter.content.trim()) {
      message.warning('当前章节没有内容，无法生成概要')
      return
    }

    setSummaryGenerating(true)
    try {
      const result: any = await api.post(`/chapters/${selectedChapter.id}/summarize`, withAIConfig({}))
      let latestSummary = typeof result?.summary === 'string' ? result.summary : ''

      if (!latestSummary.trim()) {
        const summaryResult = await getChapterSummaryById(selectedChapter.id)
        latestSummary = summaryResult.summary || ''
      }

      syncChapterSummaryState(selectedChapter.id, latestSummary)

      const chapterWithSummary = {
        ...selectedChapter,
        summary: latestSummary,
      }

      setSummaryData(
        buildChapterSummaryViewData(chapterWithSummary, {
          characterCount: Number(result?.characterCount ?? 0),
          eventCount: Number(result?.eventCount ?? 0),
          message: result?.message || '章节概要完成',
        })
      )

      message.success(result?.message || '章节概要完成')
    } catch (error: any) {
      console.error('生成概要失败:', error)
      message.error(error?.message || '生成概要失败')
    } finally {
      setSummaryGenerating(false)
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

  const resolveTotalChaptersInVolume = (volume: NovelVolume | null): number | null => {
    if (!volume) return null
    const count = Number((volume as any).chapterCount)
    if (Number.isFinite(count) && count > 0) return count
    const start = Number((volume as any).chapterStart)
    const end = Number((volume as any).chapterEnd)
    if (Number.isFinite(start) && Number.isFinite(end) && end >= start) {
      return end - start + 1
    }
    return null
  }

  const sortVolumesByRange = useCallback((volumeList: NovelVolume[]): NovelVolume[] => {
    return [...volumeList].sort((a, b) => {
      const startA = Number((a as any).chapterStart ?? 0)
      const startB = Number((b as any).chapterStart ?? 0)
      if (startA !== startB) return startA - startB
      const numA = Number((a as any).volumeNumber ?? 0)
      const numB = Number((b as any).volumeNumber ?? 0)
      return numA - numB
    })
  }, [])

  const findNextVolume = useCallback((volumeId: number, volumeList: NovelVolume[]): NovelVolume | null => {
    const sorted = sortVolumesByRange(volumeList)
    const index = sorted.findIndex((item) => Number(item.id) === Number(volumeId))
    if (index < 0 || index + 1 >= sorted.length) {
      return null
    }
    return sorted[index + 1] || null
  }, [sortVolumesByRange])

  useEffect(() => {
    setManualPlanningVolumeId(null)
  }, [editingType, selectedChapter?.id, selectedChapter?.chapterNumber])

  useEffect(() => {
    try {
      localStorage.setItem('outlineBatchSize', String(outlineBatchSize))
    } catch (e) {
      // ignore
    }
  }, [outlineBatchSize])

  useEffect(() => {
    let cancelled = false

    const refreshPlanningStatus = async () => {
      if (!novelIdNumber || editingType !== 'chapter' || !selectedChapter?.chapterNumber) {
        setOutlineReminderInfo(null)
        return
      }

      let volumeList = volumes
      if (!volumeList || volumeList.length === 0) {
        try {
          volumeList = await novelVolumeService.getVolumesByNovelId(novelIdNumber.toString())
          if (cancelled) return
          setVolumes(volumeList)
        } catch (error) {
          console.warn('加载卷列表失败，无法刷新细纲提醒', error)
          return
        }
      }

      const volumeForChapter = findVolumeForChapter(selectedChapter.chapterNumber)
      if (!volumeForChapter) {
        setOutlineReminderInfo(null)
        return
      }

      const volumeId = Number(volumeForChapter.id)
      setPlanningVolumeId(volumeId)
      setChapterOutlineVolumeId(volumeId)

      let outlineList: VolumeChapterOutline[] = []
      try {
        outlineList = await getChapterOutlinesByVolume(volumeId, true)
      } catch (error) {
        console.warn('获取细纲列表失败，继续写作流程', error)
      }
      if (cancelled) return
      setChapterOutlineList(outlineList)

      const volumeStart = Number((volumeForChapter as any).chapterStart ?? 1)
      const currentCiv = selectedChapter.chapterNumber - volumeStart + 1
      const lastOutlineCiv = outlineList.reduce((max, item) => {
        const civ = Number(item.chapterInVolume ?? 0)
        return civ > max ? civ : max
      }, 0)
      const hasOutline = outlineList.some((item) => {
        if (item.globalChapterNumber != null) {
          return Number(item.globalChapterNumber) === selectedChapter.chapterNumber
        }
        if (item.chapterInVolume != null) {
          return Number(item.chapterInVolume) === currentCiv
        }
        return false
      })

      setOutlineReminderInfo({
        volumeId,
        lastOutlineChapterInVolume: lastOutlineCiv,
        currentChapterInVolume: currentCiv,
        totalChapters: resolveTotalChaptersInVolume(volumeForChapter) ?? undefined,
      })

      try {
        const profileRes = await getVolumeCharacterProfiles(volumeId)
        if (cancelled) return
        setProfileCount(profileRes.count ?? 0)
      } catch (error) {
        console.warn('获取本卷人设失败', error)
      }
    }

    refreshPlanningStatus()

    return () => {
      cancelled = true
    }
  }, [novelIdNumber, editingType, selectedChapter?.chapterNumber, volumes, isGeneratingOutline])
  const formatKeyPlotPointsText = (raw: unknown): string => {
    if (raw == null) return ''
    const extractFromObject = (obj: any): string => {
      if (!obj || typeof obj !== 'object') return ''
      const content = typeof obj.content === 'string' ? obj.content.trim() : ''
      if (content) return content
      const direction = typeof obj.direction === 'string' ? obj.direction.trim() : ''
      if (direction) return direction
      const hardConstraints = obj.hard_constraints ?? obj.hardConstraints
      if (Array.isArray(hardConstraints)) {
        const joined = hardConstraints
          .map((item: any) => String(item ?? '').trim())
          .filter(Boolean)
          .join('\n')
        if (joined) return joined
      }
      const sceneBeats = obj.scene_beats ?? obj.sceneBeats
      if (Array.isArray(sceneBeats)) {
        const joined = sceneBeats
          .map((item: any) => String(item ?? '').trim())
          .filter(Boolean)
          .join('\n')
        if (joined) return joined
      }
      const hook = typeof obj.hook === 'string' ? obj.hook.trim() : ''
      if (hook) return hook
      return ''
    }
    if (Array.isArray(raw)) {
      return raw
        .map((item) => String(item ?? '').trim())
        .filter(Boolean)
        .join('\n')
    }
    if (typeof raw === 'string') {
      const trimmed = raw.trim()
      if (!trimmed) return ''
      try {
        const parsed = JSON.parse(trimmed)
        if (Array.isArray(parsed)) {
          return parsed
            .map((item) => String(item ?? '').trim())
            .filter(Boolean)
            .join('\n')
        }
        if (typeof parsed === 'string') {
          const inner = parsed.trim()
          if (inner.startsWith('[') || inner.startsWith('{')) {
            try {
              const innerParsed = JSON.parse(inner)
              if (Array.isArray(innerParsed)) {
                return innerParsed
                  .map((item) => String(item ?? '').trim())
                  .filter(Boolean)
                  .join('\n')
              }
              if (typeof innerParsed === 'string') {
                return innerParsed
              }
              if (innerParsed && typeof innerParsed === 'object') {
                return extractFromObject(innerParsed)
              }
            } catch (innerError) {
              return parsed
            }
          }
          return parsed
        }
        if (parsed && typeof parsed === 'object') {
          return extractFromObject(parsed)
        }
      } catch (error) {
        return trimmed
      }
      return trimmed
    }
    if (typeof raw === 'object') {
      const extracted = extractFromObject(raw as any)
      return extracted || ''
    }
    return String(raw)
  }

  const formatForeshadowDetailText = (raw: unknown): string => {
    if (raw == null) return ''
    
    // 处理对象类型，提取 content 字段
    if (typeof raw === 'object' && raw !== null) {
      const obj = raw as any
      if (obj.content) {
        return String(obj.content)
      }
      // 如果没有 content 字段，返回完整 JSON
      try {
        return JSON.stringify(raw, null, 2)
      } catch (error) {
        return String(raw)
      }
    }
    
    // 处理字符串类型
    if (typeof raw === 'string') {
      const trimmed = raw.trim()
      if (!trimmed) return ''
      
      try {
        const parsed = JSON.parse(trimmed)
        
        // 如果解析后是对象，尝试提取 content
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          if (parsed.content) {
            return String(parsed.content)
          }
          // 没有 content，返回完整 JSON
          return JSON.stringify(parsed, null, 2)
        }
        
        // 处理双重编码的情况
        if (typeof parsed === 'string') {
          const inner = parsed.trim()
          if (inner.startsWith('[') || inner.startsWith('{')) {
            try {
              const innerParsed = JSON.parse(inner)
              if (innerParsed && typeof innerParsed === 'object' && !Array.isArray(innerParsed)) {
                if (innerParsed.content) {
                  return String(innerParsed.content)
                }
                return JSON.stringify(innerParsed, null, 2)
              }
              if (typeof innerParsed === 'string') {
                return innerParsed
              }
              return JSON.stringify(innerParsed, null, 2)
            } catch (innerError) {
              return parsed
            }
          }
          return parsed
        }
        
        // 其他类型直接转字符串
        return String(parsed)
      } catch (error) {
        // JSON 解析失败，直接返回原文本
        return trimmed
      }
    }
    
    return String(raw)
  }

  const buildKeyPlotPointsPayload = (rawText?: string): string | undefined => {
    if (!rawText) return undefined
    const trimmed = rawText.trim()
    if (!trimmed) return undefined
    if (trimmed.startsWith('[') || trimmed.startsWith('{') || trimmed.startsWith('"')) {
      try {
        JSON.parse(trimmed)
        return trimmed
      } catch (error) {
        // fallthrough
      }
    }
    const lines = trimmed
      .split(/\r?\n+/)
      .map((line) => line.trim())
      .filter(Boolean)
    if (lines.length === 0) return undefined
    return JSON.stringify(lines)
  }

  const getOutlinePreview = (outline: VolumeChapterOutline): string => {
    const text = formatKeyPlotPointsText(outline.keyPlotPoints) || outline.direction || ''
    const flattened = text.replace(/\s+/g, ' ').trim()
    if (!flattened) return ''
    return flattened.length > 50 ? `${flattened.slice(0, 50)}...` : flattened
  }

  const normalizeVolumeCharacterProfile = (profile: VolumeCharacterProfile): VolumeCharacterProfile => {
    const raw = (profile.rawProfile || '').trim()

    const firstNonEmpty = (...candidates: any[]): string | undefined => {
      for (const item of candidates) {
        if (item === null || item === undefined) continue
        const text = String(item).trim()
        if (text.length > 0) return text
      }
      return undefined
    }

    const extractJsonObjectText = (text: string): string | null => {
      const start = text.indexOf('{')
      const end = text.lastIndexOf('}')
      if (start >= 0 && end > start) return text.slice(start, end + 1)
      return null
    }

    const readRawObject = (text: string): Record<string, any> | null => {
      const trimmed = text.trim()
      if (!trimmed) return null

      const attempts = [trimmed]
      const extracted = extractJsonObjectText(trimmed)
      if (extracted && extracted !== trimmed) attempts.unshift(extracted)

      for (const candidate of attempts) {
        const looksJson =
          candidate.startsWith('{') ||
          candidate.startsWith('[') ||
          candidate.startsWith('"') ||
          candidate.startsWith('`')
        if (!looksJson) continue

        let normalized = candidate
        if (normalized.startsWith('```')) {
          normalized = normalized.replace(/^```[a-zA-Z]*\s*/m, '').replace(/```$/m, '').trim()
        }

        try {
          const parsed = JSON.parse(normalized)
          if (parsed && typeof parsed === 'object') {
            if (Array.isArray(parsed)) {
              const first = parsed.find((item) => item && typeof item === 'object' && !Array.isArray(item))
              return first ? (first as Record<string, any>) : null
            }
            return parsed as Record<string, any>
          }
          if (typeof parsed === 'string') {
            try {
              const parsed2 = JSON.parse(parsed)
              if (parsed2 && typeof parsed2 === 'object' && !Array.isArray(parsed2)) {
                return parsed2 as Record<string, any>
              }
            } catch {
              // ignore
            }
          }
        } catch {
          // ignore and continue
        }
      }

      return null
    }

    const rawObj = raw ? readRawObject(raw) : null

    const getFromRaw = (...keys: string[]): string | undefined => {
      if (!rawObj) return undefined
      for (const key of keys) {
        if (!key) continue
        const value = (rawObj as any)[key]
        const text = value === null || value === undefined ? '' : String(value).trim()
        if (text.length > 0) return text
      }
      return undefined
    }

    const normalized: VolumeCharacterProfile = { ...profile }
    normalized.characterName = firstNonEmpty(
      profile.characterName,
      getFromRaw('character_name', 'characterName', '人物', '角色名', '角色名称', '姓名', 'name')
    )
    normalized.currentPersona = firstNonEmpty(
      profile.currentPersona,
      getFromRaw('current_persona', 'currentPersona', '当前人设', '人设', 'persona')
    )
    normalized.coreMotivation = firstNonEmpty(
      profile.coreMotivation,
      getFromRaw('core_motivation', 'coreMotivation', '核心动机', '动机', 'motivation')
    )
    normalized.stanceRelationships = firstNonEmpty(
      profile.stanceRelationships,
      getFromRaw('stance_relationships', 'stanceRelationships', '立场关系', '关系', 'stance')
    )
    normalized.unfinishedGoals = firstNonEmpty(
      profile.unfinishedGoals,
      getFromRaw('unfinished_goals', 'unfinishedGoals', '未竟目标', '未完成目标', '目标', 'goals')
    )
    normalized.behaviorBottomLine = firstNonEmpty(
      profile.behaviorBottomLine,
      getFromRaw('behavior_bottom_line', 'behaviorBottomLine', '行为底线', '底线', '原则', 'bottom_line')
    )

    return normalized
  }

  const getProfileInitial = (name?: string) => {
    const trimmed = (name || '').trim()
    if (!trimmed) return '角'
    return trimmed.slice(0, 1).toUpperCase()
  }

  const buildProfileSummary = (profile: VolumeCharacterProfile) => {
    const candidates = [
      profile.currentPersona,
      profile.coreMotivation,
      profile.stanceRelationships,
      profile.unfinishedGoals,
      profile.behaviorBottomLine,
    ]
    const found = candidates.find((item) => item && String(item).trim().length > 0)
    const text = String(found || '').replace(/\s+/g, ' ').trim()
    if (!text) return '暂无摘要'
    return text.length > 70 ? `${text.slice(0, 70)}...` : text
  }

  const getVolumeProfileDetailFields = (
    profile: VolumeCharacterProfile
  ): Array<{ key: VolumeProfileEditableKey; label: string; value?: string }> =>
    VOLUME_PROFILE_EDITABLE_FIELDS.map((field) => ({
      key: field.key,
      label: field.label,
      value: profile[field.key] ? String(profile[field.key]) : undefined,
    }))

  const buildOutlineSummary = (outline: VolumeChapterOutline) => {
    const text = formatKeyPlotPointsText(outline.keyPlotPoints) || outline.direction || ''
    const flattened = text.replace(/\s+/g, ' ').trim()
    if (!flattened) return '暂无细纲摘要'
    return flattened.length > 100 ? `${flattened.slice(0, 100)}...` : flattened
  }

  const mapOutlineToEditingForm = (
    outline: VolumeChapterOutline,
    fallbackChapterNumber?: number
  ) => {
    console.log('🔄 映射章纲数据，原始伏笔字段:', {
      foreshadowAction: outline.foreshadowAction,
      foreshadowDetail: outline.foreshadowDetail
    })
    
    const keyPlotPointsText = formatKeyPlotPointsText(outline.keyPlotPoints)
    const directionText = (outline.direction || '').trim() || keyPlotPointsText || ''
    const foreshadowDetailText = formatForeshadowDetailText(outline.foreshadowDetail)
    const mapped = {
      outlineId: outline.id,
      globalChapterNumber: outline.globalChapterNumber ?? fallbackChapterNumber,
      chapterInVolume: outline.chapterInVolume ?? undefined,
      volumeNumber: outline.volumeNumber ?? undefined,
      direction: directionText,
      foreshadowAction: outline.foreshadowAction || 'NONE',
      foreshadowDetail: foreshadowDetailText,
      status: outline.status || undefined,
      // 兼容字段：旧数据可能把整坨 JSON 塞在 keyPlotPoints；现在统一用 direction 存纯文本细纲
      keyPlotPoints: undefined,
      emotionalTone: outline.emotionalTone || '',
      subplot: outline.subplot || '',
      antagonism: outline.antagonism || '',
    }
    
    console.log('✅ 映射后的伏笔字段:', {
      foreshadowAction: mapped.foreshadowAction,
      foreshadowDetail: mapped.foreshadowDetail
    })
    
    return mapped
  }

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
      console.log('📋 章纲数据:', res)
      if (res.hasOutline && res.outline) {
        console.log('🔮 伏笔字段:', {
          foreshadowAction: res.outline.foreshadowAction,
          foreshadowDetail: res.outline.foreshadowDetail
        })
        setEditingChapterOutline(mapOutlineToEditingForm(res.outline, chapterNumber))
      } else {
        // 章纲不存在，弹出提示询问是否生成（标记来源为 'outline'，不显示“继续生成”按钮）
        console.log('[章纲] 章纲不存在，弹出生成提示')
        setChapterOutlineLoading(false)
        setOutlineMissingSource('outline')
        setOutlineMissingModalVisible(true)
        return
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
    if (!editingChapterOutline) {
      message.warning('没有可保存的章纲数据')
      return
    }

    // 新增章纲需要 volumeId，从当前卷获取
    if (!editingChapterOutline.outlineId) {
      // 新增模式
      if (!chapterOutlineVolumeId || !editingChapterOutline.globalChapterNumber) {
        message.warning('缺少必要信息（卷ID或章节号），无法创建章纲')
        return
      }
    }
    const directionText = (editingChapterOutline.direction ?? '').trim()

    setChapterOutlineLoading(true)
    try {
      let result: VolumeChapterOutline

      if (editingChapterOutline.outlineId) {
        // 更新模式
        const payload = {
          direction: directionText,
          // 细纲已改为 direction 单字段纯文本；清空遗留的 keyPlotPoints，避免前端预览出现 JSON。
          keyPlotPoints: '',
          foreshadowAction: editingChapterOutline.foreshadowAction,
          foreshadowDetail: editingChapterOutline.foreshadowDetail,
        }
        result = await updateVolumeChapterOutline(
          editingChapterOutline.outlineId,
          payload
        )
        message.success('章纲已保存')
      } else {
        // 新增模式
        const payload = {
          novelId: novelIdNumber,
          volumeId: chapterOutlineVolumeId!,
          globalChapterNumber: editingChapterOutline.globalChapterNumber!,
          chapterInVolume: editingChapterOutline.chapterInVolume,
          volumeNumber: editingChapterOutline.volumeNumber,
          direction: directionText,
          foreshadowAction: editingChapterOutline.foreshadowAction,
          foreshadowDetail: editingChapterOutline.foreshadowDetail,
        }
        result = await createChapterOutline(payload)
        message.success('章纲已创建')
      }

      setEditingChapterOutline(mapOutlineToEditingForm(result))
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

  const leftSidebarWidth = useMemo(() => {
    const adaptiveWidth = Math.round(viewportWidth * 0.17)
    return Math.min(Math.max(adaptiveWidth, 200), 240)
  }, [viewportWidth])

  const toolsSidebarWidth = useMemo(() => {
    const adaptiveWidth = Math.round(viewportWidth * 0.3)
    return Math.min(Math.max(adaptiveWidth, 340), 520)
  }, [viewportWidth])

  const shouldUseToolsDrawer = viewportWidth - leftSidebarWidth - toolsSidebarWidth < 600

  const toolsDrawerWidth = useMemo(() => {
    const adaptiveWidth = Math.min(Math.max(Math.round(viewportWidth * 0.42), 320), 420)
    return Math.min(adaptiveWidth, Math.max(viewportWidth - 24, 280))
  }, [viewportWidth])

  const aiHistoryItems = useMemo(
    () =>
      aiHistory.map((item) => ({
        id: item.id,
        content: item.assistantMessage,
        createdAt: item.createdAt,
      })),
    [aiHistory]
  )

  const handleCopyAIOutput = useCallback(() => {
    navigator.clipboard.writeText(aiOutput)
    message.success('已复制到剪贴板')
  }, [aiOutput])

  const handleClearAIHistory = useCallback(() => {
    return clearAIConversations(novelIdNumber).then(() => {
      setAIHistory([])
      setAIOutput('')
    })
  }, [novelIdNumber])

  useEffect(() => {
    const handleResize = () => {
      setViewportWidth(window.innerWidth)
    }

    handleResize()
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
    }
  }, [])

  useEffect(() => {
    if (!shouldUseToolsDrawer) {
      setToolsDrawerVisible(false)
    }
  }, [shouldUseToolsDrawer])

  const loadFolderDocuments = useCallback(
    async (folderId: number) => {
      setDocumentsMap((prev) => {
        // 如果已经有数据，不重复加载，但允许强制刷新（这里先保留旧逻辑）
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

  // 批量加载所有文件夹的文档
  const loadAllFoldersDocuments = useCallback(async (folderList: NovelFolder[]) => {
    if (!folderList || folderList.length === 0) return

    // 预加载所有文件夹（包含系统文件夹），主要内容为前端虚拟节点不在这里处理
    const targetFolders = folderList.filter(f => f.id !== -999)
    if (targetFolders.length === 0) return

    try {
      const results = await Promise.allSettled(
        targetFolders.map(folder => getDocumentsByFolder(folder.id))
      )

      setDocumentsMap(prev => {
        const next = { ...prev }
        targetFolders.forEach((folder, index) => {
          const r = results[index]
          if (r.status === 'fulfilled') {
            next[folder.id] = r.value
          }
        })
        return next
      })
    } catch (error) {
      console.warn('批量加载文档失败', error)
    }
  }, [])

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
    async (folder: NovelFolder, documentName?: string) => {
      try {
        let title = documentName
        if (!title) {
          title = window.prompt('输入文档标题', '新文档') || undefined
          if (!title || !title.trim()) return
        }
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
      modal.confirm({
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
      modal.confirm({
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
      modal.confirm({
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

  const hasCachedOutlineForCurrentChapter = () => {
    if (editingType !== 'chapter' || !selectedChapter?.chapterNumber) {
      return true
    }

    const chapterNumber = selectedChapter.chapterNumber
    const currentChapterInVolume = outlineReminderInfo?.currentChapterInVolume

    return chapterOutlineList.some((item) => {
      if (item.globalChapterNumber != null) {
        return Number(item.globalChapterNumber) === chapterNumber
      }

      if (currentChapterInVolume != null && item.chapterInVolume != null) {
        return Number(item.chapterInVolume) === currentChapterInVolume
      }

      return false
    })
  }

  const openVolumePlanningFromGeneratePrompt = async () => {
    setOutlineMissingModalVisible(false)
    await handleOpenVolumePlanning()
  }

  const executeAIRequest = async () => {
    let accumulatedContent = ''
    let hasStreamContent = false

    try {
      // 重置所有状态，确保每次生成都是全新的
      setIsGenerating(true)
      flushAIOutput('')
      setGenerationPhases([])
      setHasContentStarted(false)

      const userMessage = aiInput.trim() || '开始'
      const currentChapterNumber =
        editingType === 'chapter' ? selectedChapter?.chapterNumber : null

      // 构建参考内容：将选中的参考文件和关联文档合并
      const referenceContents: Record<string, string> = {}

      // 添加选中的参考文件
      if (selectedReferenceIds.length > 0) {
        for (const refId of selectedReferenceIds) {
          const refFile = referenceFiles.find(f => f.id === refId)
          if (!refFile) {
            continue
          }

          try {
            const fullReferenceFile = await getReferenceFileById(novelIdNumber, refId)
            if (fullReferenceFile.fileContent) {
              referenceContents[`参考文件: ${refFile.fileName}`] = fullReferenceFile.fileContent
            }
          } catch (err) {
            console.warn(`获取参考文件 ${refId} 内容失败`, err)
          }
        }
      }

      // 添加选中的关联文档（需要动态获取内容，因为列表可能只有摘要）
      if (selectedLinkedIds.length > 0) {
        for (const docId of selectedLinkedIds) {
          const doc = allDocuments.find(d => d.id === docId)
          if (doc) {
            // 如果已有内容则直接使用，否则获取完整文档
            if (doc.content) {
              referenceContents[`关联文档: ${doc.title}`] = doc.content
            } else {
              try {
                const fullDoc = await getDocumentById(docId)
                if (fullDoc && fullDoc.content) {
                  referenceContents[`关联文档: ${fullDoc.title}`] = fullDoc.content
                }
              } catch (err) {
                console.warn(`获取关联文档 ${docId} 内容失败`, err)
              }
            }
          }
        }
      }

      const token = localStorage.getItem('token')
      const requestBody = withAIConfig(
        {
          novelId: novelIdNumber,
          startChapter: currentChapterNumber,
          count: 1,
          userAdjustment: userMessage,
          promptTemplateId: writingStyleId,
          outlineContextMode,
          referenceContents: Object.keys(referenceContents).length > 0 ? referenceContents : undefined,
        },
        {
          model: selectedModel,
          temperature: temperature,
        }
      )

      const response = await fetch('/api/agentic/generate-chapters-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'Cache-Control': 'no-cache',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(requestBody),
      })

      if (!response.ok) {
        const errorMessage = await extractHttpErrorMessage(response, `HTTP error! status: ${response.status}`)
        throw new Error(errorMessage)
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (!reader) {
        throw new Error('无法获取响应流')
      }

      let buffer = ''
      let receivedComplete = false
      let receivedError = false
      const phases: string[] = []

      const processSseEvent = (eventName: string, data: string) => {
        const normalizedEvent = eventName || 'message'

        if (!data || data === '[DONE]' || normalizedEvent === 'keepalive') {
          return
        }

        if (normalizedEvent === 'error') {
          receivedError = true
          message.error(formatAIErrorMessage(data))
          return
        }

        if (normalizedEvent === 'complete') {
          receivedComplete = true
          return
        }

        if (normalizedEvent === 'warning') {
          phases.push(`⚠️ ${data}`)
          setGenerationPhases([...phases])
          message.warning(data)
          return
        }

        if (normalizedEvent === 'phase' || normalizedEvent === 'outline') {
          phases.push(data)
          setGenerationPhases([...phases])
          return
        }

        if (normalizedEvent === 'message') {
          const chunk = decodeWritingMessageChunk(data)
          if (isUnexpectedStreamErrorChunk(chunk)) {
            receivedError = true
            message.error(formatAIErrorMessage(chunk))
            return
          }

          accumulatedContent += chunk
          if (!hasStreamContent) {
            hasStreamContent = true
            setHasContentStarted(true)
          }
          scheduleAIOutputFlush(accumulatedContent)
        }
      }

      while (true) {
        const { done, value } = await reader.read()

        if (done) {
          break
        }

        if (!value) {
          continue
        }

        buffer += decoder.decode(value, { stream: true })

        const parsed = extractSseEvents(buffer)
        buffer = parsed.remaining

        for (const event of parsed.events) {
          processSseEvent(event.event, event.data)

          if (receivedError) {
            break
          }
        }

        if (receivedError) {
          try {
            await reader.cancel()
          } catch (cancelError) {
            console.warn('取消异常流读取失败', cancelError)
          }
          break
        }
      }

      if (!receivedError && buffer.trim()) {
        const parsed = extractSseEvents(`${buffer}\n\n`)
        for (const event of parsed.events) {
          processSseEvent(event.event, event.data)
          if (receivedError) {
            break
          }
        }
      }

      setIsGenerating(false)

      if (receivedError) {
        if (!hasStreamContent) {
          setHasContentStarted(false)
        }
        return
      }

      if (receivedComplete) {
        if (accumulatedContent.trim()) {
          flushAIOutput(accumulatedContent)
          message.success('AI写作完成')
        } else {
          setHasContentStarted(false)
          message.warning('生成已结束，但未收到正文内容')
        }
        return
      }

      if (accumulatedContent.trim()) {
        flushAIOutput(accumulatedContent)
        message.success('AI写作完成')
      } else {
        setHasContentStarted(false)
        message.error('流式生成已中断，请重试')
      }
    } catch (error: any) {
      console.error('AI生成失败:', error)
      message.error(formatAIErrorMessage(error))
      setIsGenerating(false)

      if (!hasStreamContent) {
        flushAIOutput('')
        setGenerationPhases([])
        setHasContentStarted(false)
      }
    }
  }

  const handleSendAIRequest = async (skipPlanningCheck?: boolean | React.MouseEvent) => {
    if (!selectedChapter && !selectedDocument) {
      message.warning('请选择要编辑的内容')
      return
    }

    if (!novelIdNumber) return

    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    const shouldSkipPlanningCheck = typeof skipPlanningCheck === 'boolean'
      ? skipPlanningCheck
      : false

    if (!shouldSkipPlanningCheck && editingType === 'chapter' && selectedChapter?.chapterNumber != null) {
      let hasChapterOutline = false

      try {
        const outlineRes = await getChapterOutline(novelIdNumber, selectedChapter.chapterNumber)
        hasChapterOutline = Boolean(outlineRes.hasOutline && outlineRes.outline)
      } catch (error) {
        console.warn('检查当前章节细纲失败，回退到本地缓存判断', error)
        hasChapterOutline = hasCachedOutlineForCurrentChapter()
      }

      if (!hasChapterOutline) {
        setOutlineMissingSource('generate')
        setOutlineMissingModalVisible(true)
        return
      }
    }

    await executeAIRequest()
  }

  const handleReplaceContent = async () => {
    if (!selectedChapter && !selectedDocument) return
    const replacementContent = aiOutput

    if (editingType === 'chapter' && selectedChapter) {
      const { heading } = extractLeadingChapterHeading(replacementContent)
      const generatedTitle = extractChapterTitleFromHeading(heading)

      setSelectedChapter((prev) => (
        prev
          ? {
              ...prev,
              content: replacementContent,
              title: generatedTitle || prev.title,
            }
          : prev
      ))
      setChapters((prev) => prev.map((chapter) => (
        chapter.id === selectedChapter.id
          ? {
              ...chapter,
              content: replacementContent,
              title: generatedTitle || chapter.title,
            }
          : chapter
      )))

      if (generatedTitle && generatedTitle !== selectedChapter.title) {
        try {
          await updateChapter(selectedChapter.id, { title: generatedTitle })
        } catch (error) {
          console.warn('同步章节标题失败，保留本地替换结果', error)
        }
      }
    } else if (editingType === 'document' && selectedDocument) {
      setSelectedDocument((prev) => (prev ? { ...prev, content: replacementContent } : prev))
    }

    onContentChange(replacementContent)
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
    setReviewDrawerVisible(false) // 关闭AI审稿抽屉
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
      // 直接传递内容，后端使用系统配置的AI模型
      const requestBody = {
        content: currentContent,
        model: selectedModel // 可选：传递选中的模型ID
      }
      
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
        const errorMessage = await extractHttpErrorMessage(response, `HTTP error! status: ${response.status}`)
        throw new Error(errorMessage)
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
                setProcessedContent(safeFormatStreamOutput(sanitized))
              }
            } catch (e) {
              if (data && data !== '[DONE]' && !progressRegex.test(data)) {
                accumulated += data
                const sanitized = accumulated.replace(progressRegex, '')
                setProcessedContent(safeFormatStreamOutput(sanitized))
              }
            }
          }
        }
      }
    } catch (error: any) {
      console.error('AI消痕失败:', error)
      message.error(formatAIErrorMessage(error))
      setIsRemovingTrace(false)
    }
  }
  
  // AI审稿处理 - 第一次点击打开弹窗
  const handleReviewManuscript = () => {
    const currentContent = editingType === 'chapter' ? selectedChapter?.content : selectedDocument?.content
    
    if (!currentContent) {
      message.warning('请先编辑内容后再审稿')
      return
    }
    
    // 第一次点击：打开弹窗，不请求接口
    setReviewResult('')
    setTraceRemovalDrawerVisible(false) // 关闭AI消痕抽屉
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
      // 直接传递内容，后端使用系统配置的AI模型
      const requestBody = {
        content: currentContent,
        model: selectedModel // 可选：传递选中的模型ID
      }
      
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
        const errorMessage = await extractHttpErrorMessage(response, `HTTP error! status: ${response.status}`)
        throw new Error(errorMessage)
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
                setReviewResult(safeFormatStreamOutput(accumulated))
              }
            } catch (e) {
              if (data && data !== '[DONE]') {
                accumulated += data
                setReviewResult(safeFormatStreamOutput(accumulated))
              }
            }
          }
        }
      }
    } catch (error: any) {
      console.error('AI审稿失败:', error)
      message.error(formatAIErrorMessage(error))
      setIsReviewing(false)
    }
  }

  // 处理章纲生成
  const handleGenerateChapterOutline = async (countOverride?: number) => {
    let volumeId = manualPlanningVolumeId || planningVolumeId || chapterOutlineVolumeId || Number(currentVolume?.id)
    
    // 如果还没有确定卷ID，尝试加载并确定
    if (!volumeId) {
      if (novelIdNumber) {
        try {
          let volumeList = volumes
          if (!volumeList || volumeList.length === 0) {
            volumeList = await novelVolumeService.getVolumesByNovelId(novelIdNumber.toString())
            setVolumes(volumeList)
          }
          
          if (volumeList && volumeList.length > 0) {
            if (selectedChapter?.chapterNumber) {
              // 按章节范围查找
              const matched = volumeList.find((v) => {
                const start = Number(v.chapterStart)
                const end = Number(v.chapterEnd)
                if (!Number.isFinite(start) || !Number.isFinite(end)) return false
                return selectedChapter.chapterNumber! >= start && selectedChapter.chapterNumber! <= end
              })
              volumeId = matched ? Number(matched.id) : Number(volumeList[0].id)
            } else {
              volumeId = Number(volumeList[0].id)
            }
            setChapterOutlineVolumeId(volumeId)
          }
        } catch (error) {
          console.error('加载卷列表失败:', error)
        }
      }
    }
    
    if (!volumeId) {
      message.error('无法确定卷ID，请刷新页面重试')
      return
    }

    // 检查AI配置
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    setIsGeneratingOutline(true)
    setOutlineGenerateProgress('正在初始化...')
    setOutlineGeneratePercent(0)

    // 模拟进度条（因为实际接口不返回进度）
    const progressMessages = [
      { percent: 5, msg: '正在分析卷蓝图...' },
      { percent: 15, msg: '正在规划细纲结构...' },
      { percent: 30, msg: '正在生成章节方向...' },
      { percent: 45, msg: '正在设计情节要点...' },
      { percent: 60, msg: '正在构建情感基调...' },
      { percent: 75, msg: '正在编排伏笔线索...' },
      { percent: 85, msg: '正在优化细纲内容...' },
      { percent: 92, msg: '即将完成...' },
    ]
    
    let progressIndex = 0
    const progressInterval = setInterval(() => {
      if (progressIndex < progressMessages.length) {
        const { percent, msg } = progressMessages[progressIndex]
        setOutlineGeneratePercent(percent)
        setOutlineGenerateProgress(msg)
        progressIndex++
      }
    }, 15000) // 每15秒更新一次进度

    try {
      const targetCount = typeof countOverride === 'number' && Number.isFinite(countOverride)
        ? countOverride
        : outlineBatchSize
      let volumeList = volumes
      if ((!volumeList || volumeList.length === 0) && novelIdNumber) {
        volumeList = await novelVolumeService.getVolumesByNovelId(novelIdNumber.toString())
        setVolumes(volumeList)
      }
      volumeList = sortVolumesByRange(volumeList)

      let remaining = targetCount
      let currentTargetVolumeId = volumeId
      let lastGeneratedVolumeId = volumeId
      const segments: OutlineGenerationSegment[] = []

      while (remaining > 0 && currentTargetVolumeId) {
        const targetVolume = volumeList.find((item) => Number(item.id) === Number(currentTargetVolumeId))
        if (!targetVolume) {
          break
        }

        const volumeLabel = targetVolume.volumeNumber ? `第${targetVolume.volumeNumber}卷` : '当前卷'
        setOutlineGenerateProgress(
          segments.length === 0
            ? `正在生成${volumeLabel}细纲...`
            : `当前卷已满，正在续接${volumeLabel}细纲...`
        )

        const result = await generateVolumeChapterOutlines(
          currentTargetVolumeId,
          remaining,
          selectedModel,
          outlineGenerationRequirement
        )

        const generatedCount = Math.max(0, Number(result.count ?? 0))
        if (generatedCount > 0) {
          segments.push({
            volumeId: Number(targetVolume.id),
            volumeNumber: targetVolume.volumeNumber ?? null,
            volumeTitle: targetVolume.title || volumeLabel,
            count: generatedCount,
          })
          lastGeneratedVolumeId = Number(targetVolume.id)
          remaining -= generatedCount
        }

        if (remaining <= 0) {
          break
        }

        const nextVolume = findNextVolume(currentTargetVolumeId, volumeList)
        if (!nextVolume) {
          break
        }

        currentTargetVolumeId = Number(nextVolume.id)
      }

      clearInterval(progressInterval)
      setOutlineGeneratePercent(100)
      setOutlineGenerateProgress('生成完成！')

      setTimeout(() => {
        const totalGenerated = segments.reduce((sum, item) => sum + item.count, 0)
        const summaryText = segments
          .map((item) => `${item.volumeNumber ? `第${item.volumeNumber}卷` : item.volumeTitle}${item.count}章`)
          .join('，')

        if (totalGenerated <= 0) {
          message.warning('当前卷及后续卷暂无可生成的细纲范围')
        } else if (segments.length > 1) {
          const lastSegment = segments[segments.length - 1]
          const switchedLabel = lastSegment?.volumeNumber ? `，已自动切到第${lastSegment.volumeNumber}卷` : ''
          message.success(`已连续生成 ${totalGenerated} 个细纲：${summaryText}${switchedLabel}`)
        } else {
          message.success(`成功生成 ${totalGenerated} 个细纲`)
        }

        if (remaining > 0 && totalGenerated > 0) {
          message.warning(`后续卷不足，本次还剩 ${remaining} 章未生成`)
        }

        setOutlineGenerateModalVisible(false)
        setOutlineMissingModalVisible(false)
        setOutlineGenerateProgress('')
        setOutlineGeneratePercent(0)
      }, 500)

      if (lastGeneratedVolumeId) {
        setManualPlanningVolumeId(lastGeneratedVolumeId)
        setPlanningVolumeId(lastGeneratedVolumeId)
        setChapterOutlineVolumeId(lastGeneratedVolumeId)
      }

      // 刷新最后一个实际生成卷的章纲列表，并把卷规划焦点切过去
      if (lastGeneratedVolumeId) {
        const list = await getChapterOutlinesByVolume(lastGeneratedVolumeId, true)
        setChapterOutlineList(list)
        refreshPlanningProfiles(lastGeneratedVolumeId)
      }
    } catch (error: any) {
      clearInterval(progressInterval)
      console.error('生成细纲失败:', error)
      message.error(formatAIErrorMessage(error))
      setOutlineGenerateProgress('')
      setOutlineGeneratePercent(0)
    } finally {
      setIsGeneratingOutline(false)
    }
  }

  // 重新生成指定范围的细纲
  const handleRegenerateChapterOutline = async (fromChapter: number, toChapter: number) => {
    const volumeId = manualPlanningVolumeId || planningVolumeId || chapterOutlineVolumeId || Number(currentVolume?.id)
    if (!volumeId) { message.error('无法确定卷ID'); return }
    if (!checkAIConfig()) { message.error(AI_CONFIG_ERROR_MESSAGE); return }

    const count = toChapter - fromChapter + 1
    setIsRegenerating(true)
    try {
      const result = await regenerateVolumeChapterOutlines(
        volumeId,
        count,
        selectedModel,
        outlineGenerationRequirement
      )
      message.success(`已重新生成第 ${fromChapter} - ${toChapter} 章细纲，共 ${result.count} 个`)
      setRegenModalVisible(false)
      const list = await getChapterOutlinesByVolume(volumeId, true)
      setChapterOutlineList(list)
    } catch (error: any) {
      message.error(formatAIErrorMessage(error))
    } finally {
      setIsRegenerating(false)
    }
  }

  // 保存细纲详情编辑
  const handleSaveDetailOutline = async () => {
    if (!detailOutline?.id) return
    setDetailOutlineSaving(true)
    try {
      const updated = await updateVolumeChapterOutline(detailOutline.id, {
        direction: editingDetailFields.direction,
      })
      setDetailOutline({ ...detailOutline, ...updated })
      setChapterOutlineList((prev) =>
        prev.map((o) => (o.id === detailOutline.id ? { ...o, ...updated } : o))
      )
      setDetailOutlineEditing(false)
      message.success('细纲已保存')
    } catch {
      message.error('保存失败，请重试')
    } finally {
      setDetailOutlineSaving(false)
    }
  }


  const handleGenerateVolumeProfiles = async () => {
    const volumeId = manualPlanningVolumeId || planningVolumeId || chapterOutlineVolumeId || Number(currentVolume?.id)
    if (!volumeId) {
      message.error('无法确定卷ID，请刷新页面重试')
      return
    }

    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE)
      return
    }

    setIsGeneratingProfiles(true)
    try {
      const result = await generateVolumeCharacterProfiles(
        volumeId,
        selectedModel,
        profileGenerationRequirement
      )
      const count = Number(result.count ?? 0)
      setProfileCount(count)
      if (Array.isArray(result.profiles)) {
        setPlanningProfiles(result.profiles.map((item) => normalizeVolumeCharacterProfile(item)))
      } else {
        setPlanningProfiles([])
      }
      setProfileGenerateModalVisible(false)
      message.success(count > 0 ? `生成人设完成（${count}人）` : '人设生成完成')
    } catch (error: any) {
      console.error('生成人设失败:', error)
      message.error(formatAIErrorMessage(error))
    } finally {
      setIsGeneratingProfiles(false)
    }
  }

  const handleOpenCreateProfile = () => {
    const volumeId = manualPlanningVolumeId || planningVolumeId || chapterOutlineVolumeId || Number(currentVolume?.id)
    if (!volumeId) {
      message.error('无法确定卷ID，请刷新页面重试')
      return
    }

    const currentPlanningVolume = volumes.find((item) => Number(item.id) === Number(volumeId)) || planningVolume || null
    const emptyProfile: VolumeCharacterProfile = {
      volumeId,
      volumeNumber: currentPlanningVolume?.volumeNumber,
      characterName: '',
      currentPersona: '',
      coreMotivation: '',
      stanceRelationships: '',
      unfinishedGoals: '',
      behaviorBottomLine: '',
      rawProfile: '',
    }

    setDetailProfile(emptyProfile)
    setEditingProfile(emptyProfile)
    setProfileDetailVisible(true)
  }

  const handleSaveDetailProfile = async () => {
    if (!detailProfile || !editingProfile) return

    const trimmedCharacterName = typeof editingProfile.characterName === 'string'
      ? editingProfile.characterName.trim()
      : ''
    if (!detailProfile.id && !trimmedCharacterName) {
      message.warning('请先填写角色名')
      return
    }

    const payload = VOLUME_PROFILE_EDITABLE_FIELDS.reduce((acc, field) => {
      const rawValue = editingProfile[field.key]
      acc[field.key] = typeof rawValue === 'string' ? rawValue.trim() : ''
      return acc
    }, {} as Partial<VolumeCharacterProfile>)

    if (!detailProfile.id) {
      payload.characterName = trimmedCharacterName
    }

    setProfileSaving(true)
    try {
      let savedProfile: VolumeCharacterProfile
      if (detailProfile.id) {
        savedProfile = await updateVolumeCharacterProfile(detailProfile.id, payload)
      } else {
        const volumeId = Number(detailProfile.volumeId ?? manualPlanningVolumeId ?? planningVolumeId ?? chapterOutlineVolumeId ?? currentVolume?.id)
        if (!Number.isFinite(volumeId) || volumeId <= 0) {
          throw new Error('无法确定卷ID，请刷新页面重试')
        }
        savedProfile = await createVolumeCharacterProfile(volumeId, payload)
      }

      const nextProfile = normalizeVolumeCharacterProfile({
        ...detailProfile,
        ...savedProfile,
        rawProfile: '',
      })

      setDetailProfile(nextProfile)
      setPlanningProfiles((prev) => {
        const exists = nextProfile.id != null && prev.some((item) => item.id === nextProfile.id)
        if (exists) {
          return prev.map((item) => (item.id === nextProfile.id ? nextProfile : item))
        }
        return [...prev, nextProfile]
      })
      setProfileCount((prev) => {
        if (detailProfile.id) {
          return prev
        }
        const nextCount = (prev ?? planningProfiles.length) + 1
        return nextCount
      })
      setEditingProfile(null)
      message.success(detailProfile.id ? '人设已保存' : '人设已新增')
    } catch (error: any) {
      message.error(formatAIErrorMessage(error))
    } finally {
      setProfileSaving(false)
    }
  }

  const handleDeletePlanningProfile = async (profile: VolumeCharacterProfile) => {
    const profileId = Number(profile.id)
    if (!Number.isFinite(profileId) || profileId <= 0) {
      message.warning('未保存的人设无法删除')
      return
    }

    const profileName = profile.characterName?.trim() || '未命名角色'
    modal.confirm({
      title: '确认删除',
      content: `确定要删除人设“${profileName}”吗？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      centered: true,
      onOk: async () => {
        setProfileDeletingId(profileId)
        try {
          await deleteVolumeCharacterProfile(profileId)
          let nextProfileCount = 0
          setPlanningProfiles((prev) => {
            const next = prev.filter((item) => item.id !== profileId)
            nextProfileCount = next.length
            return next
          })
          setProfileCount(nextProfileCount)

          if (detailProfile?.id === profileId) {
            setProfileDetailVisible(false)
            setDetailProfile(null)
            setEditingProfile(null)
          }

          message.success('人设删除成功')
        } catch (error: any) {
          message.error(formatAIErrorMessage(error))
        } finally {
          setProfileDeletingId(null)
        }
      },
    })
  }

  const refreshPlanningProfiles = async (volumeIdOverride?: number) => {
    const volumeId = volumeIdOverride || manualPlanningVolumeId || planningVolumeId || chapterOutlineVolumeId || Number(currentVolume?.id)
    if (!volumeId) return
    setPlanningProfilesLoading(true)
    try {
      const result = await getVolumeCharacterProfiles(volumeId)
      const count = Number(result.count ?? 0)
      setProfileCount(count)
      setPlanningProfiles(
        Array.isArray(result.profiles) ? result.profiles.map((item) => normalizeVolumeCharacterProfile(item)) : []
      )
    } catch (error: any) {
      console.warn('获取本卷人设失败', error)
      message.error(formatAIErrorMessage(error))
    } finally {
      setPlanningProfilesLoading(false)
    }
  }

  const refreshPlanningOutlines = async (volumeIdOverride?: number) => {
    const volumeId = volumeIdOverride || manualPlanningVolumeId || planningVolumeId || chapterOutlineVolumeId || Number(currentVolume?.id)
    if (!volumeId) return
    setPlanningOutlinesLoading(true)
    try {
      const list = await getChapterOutlinesByVolume(volumeId, true)
      setChapterOutlineList(list)
    } catch (error) {
      console.warn('获取细纲列表失败', error)
      message.error('获取细纲列表失败')
    } finally {
      setPlanningOutlinesLoading(false)
    }
  }

  const resolvePlanningVolumeId = async (): Promise<number | null> => {
    if (!novelIdNumber) return null
    let volumeList = volumes
    if (!volumeList || volumeList.length === 0) {
      try {
        volumeList = await novelVolumeService.getVolumesByNovelId(novelIdNumber.toString())
        setVolumes(volumeList)
      } catch (error) {
        console.warn('加载卷列表失败', error)
        return null
      }
    }
    if (!volumeList || volumeList.length === 0) return null
    const manualVolume = manualPlanningVolumeId
      ? volumeList.find((item) => Number(item.id) === Number(manualPlanningVolumeId))
      : null
    const volumeForChapter = manualVolume || findVolumeForChapter(selectedChapter?.chapterNumber ?? null)
    const volumeId = Number(volumeForChapter?.id ?? volumeList[0].id)
    if (!Number.isFinite(volumeId)) return null
    setPlanningVolumeId(volumeId)
    setChapterOutlineVolumeId(volumeId)
    return volumeId
  }

  const handleOpenVolumePlanning = async () => {
    if (editingType !== 'chapter' || !selectedChapter) {
      message.warning('请先在左侧选择一个章节')
      return
    }
    const volumeId = await resolvePlanningVolumeId()
    if (!volumeId) {
      message.error('未检测到当前章节所属卷，请先补充卷规划后再试')
      return
    }
    setDetailProfile(null)
    setDetailOutline(null)
    setVolumePlanningVisible(true)
    refreshPlanningProfiles(volumeId)
    refreshPlanningOutlines(volumeId)
  }

  const openProfileGenerateModal = async () => {
    const volumeId = await resolvePlanningVolumeId()
    if (!volumeId) {
      message.error('无法确定卷ID，请刷新页面重试')
      return
    }
    setProfileGenerateModalVisible(true)
  }

  // 打开章纲生成弹窗
  const openOutlineGenerateModal = async () => {
    const volumeId = await resolvePlanningVolumeId()
    if (!volumeId) {
      message.error('无法确定卷ID，请刷新页面重试')
      return
    }

    setOutlineMissingModalVisible(false)
    setOutlineGenerateModalVisible(true)
  }

  const outlineRangeText = useMemo(() => {
    if (!outlineReminderInfo) return ''
    const start = Math.max(1, (outlineReminderInfo.lastOutlineChapterInVolume || 0) + 1)
    const total = outlineReminderInfo.totalChapters
    const end = total
      ? Math.min(start + outlineBatchSize - 1, total)
      : start + outlineBatchSize - 1
    return `卷内第 ${start} - ${end} 章`
  }, [outlineReminderInfo, outlineBatchSize])

  const planningVolume = useMemo(() => {
    const candidateId = manualPlanningVolumeId || planningVolumeId || chapterOutlineVolumeId || currentVolume?.id
    if (candidateId && volumes && volumes.length > 0) {
      const matched = volumes.find((v) => Number(v.id) === Number(candidateId))
      if (matched) return matched
    }
    return currentVolume || volumes[0] || null
  }, [manualPlanningVolumeId, planningVolumeId, chapterOutlineVolumeId, currentVolume, volumes])

  const sortedOutlineList = useMemo(() => {
    if (!Array.isArray(chapterOutlineList)) return []
    return [...chapterOutlineList].sort((a, b) => {
      const aNum = Number(a.chapterInVolume ?? a.globalChapterNumber ?? 0)
      const bNum = Number(b.chapterInVolume ?? b.globalChapterNumber ?? 0)
      return aNum - bNum
    })
  }, [chapterOutlineList])

  const profileReady = (profileCount ?? 0) > 0
  const volumeTitle = planningVolume?.title || (planningVolume?.volumeNumber ? `第${planningVolume.volumeNumber}卷` : '当前卷')
  const volumeRangeText = planningVolume?.chapterStart && planningVolume?.chapterEnd
    ? `第 ${planningVolume.chapterStart} - ${planningVolume.chapterEnd} 章`
    : '章节范围待定'
  const outlineCoverageText = outlineReminderInfo
    ? (outlineReminderInfo.lastOutlineChapterInVolume > 0
      ? `已覆盖到卷内第 ${outlineReminderInfo.lastOutlineChapterInVolume} 章`
      : '尚未生成细纲')
    : '未获取到细纲信息'
  const progressText = outlineReminderInfo?.currentChapterInVolume && outlineReminderInfo?.totalChapters
    ? `当前进度 ${outlineReminderInfo.currentChapterInVolume}/${outlineReminderInfo.totalChapters}`
    : outlineReminderInfo?.currentChapterInVolume
    ? `当前进度 第 ${outlineReminderInfo.currentChapterInVolume} 章`
    : '当前进度待定'
  const outlineGenerationHint = useMemo(() => {
    if (!planningVolume) {
      return '若本次生成数量超出当前卷剩余范围，系统会自动续接下一卷并切换上下文。'
    }

    const totalChapters = resolveTotalChaptersInVolume(planningVolume)
    const lastOutlineChapter = sortedOutlineList.reduce((max, item) => {
      const civ = Number(item.chapterInVolume ?? 0)
      return civ > max ? civ : max
    }, 0)
    const nextChapter = Math.max(1, lastOutlineChapter + 1)

    if (!totalChapters || nextChapter > totalChapters) {
      const nextVolume = findNextVolume(Number(planningVolume.id), volumes)
      if (nextVolume) {
        return `当前卷已接近完成；若继续生成，系统会直接切换到第${nextVolume.volumeNumber || '?'}卷续接。`
      }
      return '当前卷已无可生成范围；若没有后续卷，本次不会继续外溢生成。'
    }

    const remainingInCurrentVolume = totalChapters - nextChapter + 1
    if (outlineBatchSize > remainingInCurrentVolume) {
      const nextVolume = findNextVolume(Number(planningVolume.id), volumes)
      if (nextVolume) {
        return `本次会先补完当前卷剩余 ${remainingInCurrentVolume} 章，再自动切换到第${nextVolume.volumeNumber || '?'}卷继续生成。`
      }
      return `当前卷只剩 ${remainingInCurrentVolume} 章可生成；若没有后续卷，超出部分不会生成。`
    }

    return '若本次生成数量超出当前卷剩余范围，系统会自动续接下一卷并切换上下文。'
  }, [planningVolume, sortedOutlineList, outlineBatchSize, volumes, findNextVolume])

  const toolPanelNode = (
    <ToolPanel
      isGenerating={isGenerating}
      generatorId={generatorId}
      onGeneratorChange={setGeneratorId}
      selectedModel={selectedModel}
      onModelChange={setSelectedModel}
      temperature={temperature}
      onTemperatureChange={setTemperature}
      referenceFiles={referenceFiles}
      onUploadReferenceFile={handleUploadReference}
      onDeleteReferenceFile={handleDeleteReference}
      onSelectReferenceFiles={setSelectedReferenceIds}
      selectedReferenceFileIds={selectedReferenceIds}
      linkedDocuments={allDocuments}
      onSelectLinkedDocuments={setSelectedLinkedIds}
      selectedLinkedDocumentIds={selectedLinkedIds}
      outlineContextMode={outlineContextMode}
      outlineContextModeSaving={outlineContextModeSaving}
      onOutlineContextModeChange={handleOutlineContextModeChange}
      writingStyleId={writingStyleId}
      onWritingStyleChange={setWritingStyleId}
      aiInputValue={aiInput}
      onChangeAIInput={setAIInput}
      onSendAIRequest={handleSendAIRequest}
      aiOutput={aiOutput}
      generationPhases={generationPhases}
      hasContentStarted={hasContentStarted}
      folders={folders}
      documentsMap={documentsMap}
      onCopyAIOutput={handleCopyAIOutput}
      onReplaceWithAIOutput={handleReplaceContent}
      aiHistory={aiHistoryItems}
      onClearAIHistory={handleClearAIHistory}
      generators={generators}
      searchResults={searchResults}
      onSelectSearchResult={handleSelectDocument}
      novelId={novelIdNumber}
      currentChapterNumber={
        editingType === 'chapter' ? selectedChapter?.chapterNumber ?? null : null
      }
      currentVolumeId={currentVolume ? Number(currentVolume.id) : null}
      currentVolumeNumber={currentVolume?.volumeNumber ?? null}
      onShowGraph={() => setGraphModalVisible(true)}
      onShowHistory={handleShowHistory}
    />
  )

  if (loading) {
    return (
      <div className="writing-studio-loading">
        <Spin size="large" />
      </div>
    )
  }

  return (
    <Layout className="writing-studio">
      <Sider width={leftSidebarWidth} className="writing-sidebar" theme="light">
        <div className="writing-sidebar-shell">
          <div className="writing-sidebar-tree">
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
          </div>
          <div className="writing-transfer-wrap">
            <div className="writing-transfer-panel">
              <div className="writing-transfer-actions">
                <button
                  type="button"
                  className="writing-transfer-btn writing-transfer-btn-import"
                  onClick={() => {
                    setExportModalTab('import')
                    setExportVisible(true)
                  }}
                >
                  <span className="writing-transfer-btn-icon">
                    <UploadOutlined />
                  </span>
                  <span className="writing-transfer-btn-title">导入</span>
                </button>
                <button
                  type="button"
                  className="writing-transfer-btn writing-transfer-btn-export"
                  onClick={() => {
                    setExportModalTab('export')
                    setExportVisible(true)
                  }}
                >
                  <span className="writing-transfer-btn-icon">
                    <ExportOutlined />
                  </span>
                  <span className="writing-transfer-btn-title">导出</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </Sider>
      <Content className="writing-editor">
        {shouldUseToolsDrawer && !toolsDrawerVisible && (
          <div className="writing-tools-trigger">
            <Button
              type="primary"
              icon={<RocketOutlined />}
              onClick={() => setToolsDrawerVisible(true)}
            >
              AI助手
            </Button>
          </div>
        )}
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
                  createdAt: selectedChapter.createdAt || '',
                  updatedAt: selectedChapter.updatedAt || '',
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
          onShowVolumePlanning={editingType === 'chapter' ? handleOpenVolumePlanning : undefined}
          onShowSummary={async () => {
            await loadSummary()
            setSummaryDrawerVisible(true)
          }}
          onReviewManuscript={handleReviewManuscript}
          onRemoveAITrace={handleRemoveAITrace}
          onTitleChange={async (newTitle) => {
            if (editingType === 'chapter' && selectedChapter) {
              await updateChapter(selectedChapter.id, { title: newTitle })
              setSelectedChapter((prev) => prev ? { ...prev, title: newTitle } : prev)
              setChapters((prev) => prev.map((c) => c.id === selectedChapter.id ? { ...c, title: newTitle } : c))
            } else if (editingType === 'document' && selectedDocument) {
              await updateDocument(selectedDocument.id, { title: newTitle })
              setSelectedDocument((prev) => prev ? { ...prev, title: newTitle } : prev)
            }
          }}
          chapterNumber={
            editingType === 'chapter' ? selectedChapter?.chapterNumber ?? null : null
          }
        />
      </Content>
      {!shouldUseToolsDrawer && (
        <Sider width={toolsSidebarWidth} className="writing-tools" theme="light">
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {toolPanelNode}
          </div>
        </Sider>
      )}
      {shouldUseToolsDrawer && (
        <Drawer
          title="AI写作助手"
          placement="right"
          width={toolsDrawerWidth}
          mask={false}
          open={toolsDrawerVisible}
          onClose={() => setToolsDrawerVisible(false)}
          className="writing-tools-drawer"
          styles={{ body: { padding: 0, height: '100%' } }}
        >
          <div className="writing-tools-drawer-body">
            {toolPanelNode}
          </div>
        </Drawer>
      )}

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

      {/* 卷规划弹窗 */}
      <Modal
        title={null}
        open={volumePlanningVisible}
        onCancel={() => setVolumePlanningVisible(false)}
        footer={null}
        width={1480}
        centered
        destroyOnClose
        className="volume-planning-modal"
        zIndex={VOLUME_PLANNING_MODAL_Z_INDEX}
        styles={{
          content: { padding: 0, borderRadius: '20px', overflow: 'hidden' },
          body: { padding: 0, height: '86vh' },
        }}
      >
        <div className="vpm-shell">
          <div className="vpm-header">
            <div className="vpm-title-block">
              <div className="vpm-badge">卷规划</div>
              <div className="vpm-title">{volumeTitle}</div>
              <div className="vpm-subtitle">{volumeRangeText}</div>
            </div>
            <div className="vpm-header-meta">
              <div className="vpm-meta-pill">{outlineCoverageText}</div>
              <div className="vpm-meta-pill">{progressText}</div>
              {outlineRangeText && (
                <div className="vpm-meta-pill vpm-meta-accent">{outlineRangeText}</div>
              )}
            </div>
            <button className="vpm-close-btn" onClick={() => setVolumePlanningVisible(false)}>
              关闭
            </button>
          </div>

          <div className={`vpm-tip ${profileReady ? 'ready' : 'warn'}`}>
            <span className="vpm-tip-dot" />
            {profileReady
              ? '已生成人设，细纲与正文生成都会自动引用本卷人设作为上下文。'
              : '建议先生成本卷人设，细纲与正文生成都会引用人设作为上下文，剧情一致性更好。'}
          </div>

          <div className="vpm-body">
            <div className="vpm-panel">
              <div className="vpm-panel-header">
                <div className="vpm-panel-head">
                  <div className="vpm-panel-title">本卷人设</div>
                  <div className="vpm-panel-sub">
                    {profileCount ?? 0} 人
                  </div>
                </div>
                <div className="vpm-panel-actions">
                  <div className="vpm-panel-main-actions">
                    <Button
                      type="primary"
                      onClick={openProfileGenerateModal}
                      loading={isGeneratingProfiles}
                    >
                      {profileReady ? '重新生成人设' : '生成人设'}
                    </Button>
                    <Button
                      onClick={handleOpenCreateProfile}
                      disabled={planningProfilesLoading || isGeneratingProfiles}
                    >
                      新增人设
                    </Button>
                  </div>
                  <div className="vpm-panel-icon-actions">
                    <Tooltip title="刷新本卷人设">
                      <Button
                        aria-label="刷新本卷人设"
                        className="vpm-panel-refresh-btn"
                        icon={<ReloadOutlined spin={planningProfilesLoading} />}
                        onClick={() => refreshPlanningProfiles()}
                        disabled={planningProfilesLoading || isGeneratingProfiles}
                      />
                    </Tooltip>
                  </div>
                </div>
              </div>

              <div className="vpm-panel-content">
                {planningProfilesLoading ? (
                  <div className="vpm-loading">
                    <Spin size="large" />
                    <div>加载人设中...</div>
                  </div>
                ) : planningProfiles.length > 0 ? (
                  <div className="vpm-profile-list">
                    {planningProfiles.map((profile, index) => {
                      const name = profile.characterName?.trim() || '未命名角色'
                      const profileKey = String(profile.id ?? `${name}-${index}`)
                      const summaryText = buildProfileSummary(profile)
                      return (
                        <div className="vpm-list-item" key={profileKey}>
                          <div className="vpm-li-left">
                            <div className="vpm-avatar">{getProfileInitial(profile.characterName)}</div>
                          </div>
                          <div className="vpm-li-main">
                            <div className="vpm-li-title-row">
                              <div className="vpm-li-title" title={name}>{name}</div>
                            </div>
                            <div className="vpm-li-sub" title={summaryText}>
                              {summaryText}
                            </div>
                          </div>
                          <div className="vpm-li-actions">
                            <button
                              className="vpm-link-btn vpm-link-btn-primary"
                              onClick={() => {
                                setDetailProfile(profile)
                                setEditingProfile(null)
                                setProfileDetailVisible(true)
                              }}
                            >
                              查看
                            </button>
                            <button
                              className="vpm-link-btn vpm-link-btn-danger"
                              onClick={() => handleDeletePlanningProfile(profile)}
                              disabled={profileDeletingId === profile.id}
                            >
                              {profileDeletingId === profile.id ? '删除中...' : '删除'}
                            </button>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                ) : (
                  <div className="vpm-empty">
                    <div className="vpm-empty-title">暂无人设</div>
                    <div className="vpm-empty-desc">生成本卷人设后，细纲和正文生成都会引用角色设定。</div>
                  </div>
                )}
              </div>
            </div>

            <div className="vpm-panel">
              <div className="vpm-panel-header">
                <div className="vpm-panel-head">
                  <div className="vpm-panel-title">本卷细纲</div>
                  <div className="vpm-panel-sub">
                    {outlineCoverageText}
                    {outlineReminderInfo && outlineReminderInfo.lastOutlineChapterInVolume > 0 && (
                      <span style={{ marginLeft: 8, color: '#94a3b8' }}>
                        · 下次将生成 {outlineRangeText}
                      </span>
                    )}
                  </div>
                </div>
                <div className="vpm-panel-actions">
                  <div className="vpm-panel-main-actions">
                    <div className="vpm-batch-input">
                      <span>批次</span>
                      <InputNumber
                        min={5}
                        max={15}
                        value={outlineBatchSize}
                        onChange={(val) => {
                          if (typeof val === 'number' && Number.isFinite(val)) {
                            setOutlineBatchSize(Math.min(15, Math.max(5, val)))
                          }
                        }}
                        size="small"
                      />
                    </div>
                    <Button
                      type="primary"
                      onClick={openOutlineGenerateModal}
                      loading={isGeneratingOutline}
                    >
                      生成细纲
                    </Button>
                    {sortedOutlineList.length > 0 && (
                      <Button
                        onClick={() => {
                          const last = outlineReminderInfo?.lastOutlineChapterInVolume || sortedOutlineList.length
                          const from = Math.max(1, last - 7)
                          setRegenFrom(from)
                          setRegenTo(last)
                          setRegenModalVisible(true)
                        }}
                        disabled={isGeneratingOutline || isRegenerating}
                      >
                        重新生成
                      </Button>
                    )}
                  </div>
                  <div className="vpm-panel-icon-actions">
                    <Tooltip title="刷新本卷细纲">
                      <Button
                        aria-label="刷新本卷细纲"
                        className="vpm-panel-refresh-btn"
                        icon={<ReloadOutlined spin={planningOutlinesLoading} />}
                        onClick={() => refreshPlanningOutlines()}
                        disabled={planningOutlinesLoading || isGeneratingOutline}
                      />
                    </Tooltip>
                  </div>
                </div>
              </div>

              <div className="vpm-panel-hint">
                {profileReady ? '细纲和正文生成都会自动引用本卷人设作为上下文。' : '建议先生成人设，再生成细纲或正文。'}
              </div>

              <div className="vpm-panel-content">
                {isGeneratingOutline && (
                  <div className="vpm-progress">
                    <div className="vpm-progress-text">
                      {outlineGenerateProgress || '细纲生成中...'}
                    </div>
                    <Progress percent={outlineGeneratePercent} size="small" />
                  </div>
                )}

                {planningOutlinesLoading ? (
                  <div className="vpm-loading">
                    <Spin size="large" />
                    <div>加载细纲中...</div>
                  </div>
                ) : sortedOutlineList.length > 0 ? (
                  <div className="vpm-outline-list">
                    {sortedOutlineList.map((item) => {
                      const chapterLabel = item.chapterInVolume ?? item.globalChapterNumber ?? '-'
                      const summaryText = buildOutlineSummary(item)
                      return (
                        <div className="vpm-list-item" key={item.id}>
                          <div className="vpm-li-left">
                            <div className="vpm-outline-index" aria-label={`第 ${chapterLabel} 章`}>
                              {chapterLabel}
                            </div>
                          </div>
                          <div className="vpm-li-main">
                            <div className="vpm-li-title-row">
                              <div className="vpm-li-title">第 {chapterLabel} 章</div>
                              {item.emotionalTone && (
                                <span className="vpm-outline-tag" title={item.emotionalTone}>
                                  {item.emotionalTone}
                                </span>
                              )}
                              {item.status && (
                                <span className="vpm-outline-status" title={item.status}>
                                  {item.status}
                                </span>
                              )}
                            </div>
                            <div className="vpm-li-sub" title={summaryText}>
                              {summaryText}
                            </div>
                          </div>
                          <div className="vpm-li-actions">
                            <button
                              className="vpm-link-btn"
                              onClick={async () => {
                                setDetailOutline(item)
                                setDetailOutlineEditing(false)
                                setEditingDetailFields({})
                                setOutlineDetailVisible(true)
                                // 拉完整数据（summary=false）
                                try {
                                  const detailVolumeId = Number(
                                    item.volumeId ?? manualPlanningVolumeId ?? planningVolumeId ?? chapterOutlineVolumeId ?? currentVolume?.id
                                  )
                                  if (!Number.isFinite(detailVolumeId) || detailVolumeId <= 0) {
                                    return
                                  }
                                  const full = await getChapterOutlinesByVolume(detailVolumeId, false)
                                  const found = full.find((o) => o.id === item.id)
                                  if (found) setDetailOutline(found)
                                } catch { /* 保持已有数据 */ }
                              }}
                            >
                              查看
                            </button>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                ) : (
                  <div className="vpm-empty">
                    <div className="vpm-empty-title">暂无细纲</div>
                    <div className="vpm-empty-desc">可先生成人设，再批量生成细纲。</div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </Modal>

      {/* 人设详情弹窗 */}
      <Modal
        title={null}
        open={profileDetailVisible}
        onCancel={() => {
          setProfileDetailVisible(false)
          setDetailProfile(null)
          setEditingProfile(null)
        }}
        footer={null}
        width={920}
        centered
        destroyOnClose
        className="volume-planning-modal vpm-detail-modal"
        zIndex={VOLUME_PLANNING_DETAIL_MODAL_Z_INDEX}
        styles={{
          content: { padding: 0, borderRadius: '20px', overflow: 'hidden' },
          body: { padding: 0, maxHeight: '80vh', overflow: 'auto' },
        }}
      >
        {detailProfile
          ? (() => {
              const profile = normalizeVolumeCharacterProfile(detailProfile)
              const profileDraft = editingProfile ?? profile
              const isCreatingProfile = !profile.id
              return (
                <div className="vpm-detail">
                  <div className="vpm-detail-header vpm-detail-header-space">
                    <div className="vpm-detail-title">
                      {isCreatingProfile ? '新增人设' : (profile.characterName || '未命名角色')}
                    </div>
                    <div className="vpm-detail-actions">
                      {editingProfile ? (
                        <>
                          <Button
                            size="small"
                            onClick={() => {
                              if (isCreatingProfile) {
                                setProfileDetailVisible(false)
                                setDetailProfile(null)
                              }
                              setEditingProfile(null)
                            }}
                          >
                            取消
                          </Button>
                          <Button size="small" type="primary" loading={profileSaving} onClick={handleSaveDetailProfile}>
                            {isCreatingProfile ? '创建' : '保存'}
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button size="small" onClick={() => setEditingProfile({ ...profile })}>编辑</Button>
                          {!isCreatingProfile && (
                            <Button
                              size="small"
                              danger
                              icon={<DeleteOutlined />}
                              loading={profileDeletingId === profile.id}
                              onClick={() => handleDeletePlanningProfile(profile)}
                            >
                              删除
                            </Button>
                          )}
                        </>
                      )}
                    </div>
                  </div>

                  <div className="vpm-profile-detail-grid">
                    <div className="vpm-detail-block">
                      <div className="vpm-detail-key">角色名</div>
                      {editingProfile ? (
                        <textarea
                          className="vpm-edit-textarea"
                          value={typeof profileDraft.characterName === 'string' ? profileDraft.characterName : ''}
                          onChange={(e) =>
                            setEditingProfile((prev) =>
                              prev ? { ...prev, characterName: e.target.value } : prev
                            )
                          }
                          rows={2}
                        />
                      ) : (
                        <div className="vpm-detail-value prewrap">{profile.characterName || '暂无'}</div>
                      )}
                    </div>
                    {getVolumeProfileDetailFields(profileDraft).map((field) => (
                      <div className="vpm-detail-block" key={field.key}>
                        <div className="vpm-detail-key">{field.label}</div>
                        {editingProfile ? (
                          <textarea
                            className="vpm-edit-textarea"
                            value={typeof profileDraft[field.key] === 'string' ? profileDraft[field.key] ?? '' : ''}
                            onChange={(e) =>
                              setEditingProfile((prev) =>
                                prev ? { ...prev, [field.key]: e.target.value } : prev
                              )
                            }
                            rows={field.key === 'currentPersona' || field.key === 'stanceRelationships' ? 5 : 4}
                          />
                        ) : (
                          <div className="vpm-detail-value prewrap">{field.value || '暂无'}</div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )
            })()
          : null}
      </Modal>

      {/* 细纲详情/编辑弹窗 */}
      <Modal
        title={null}
        open={outlineDetailVisible}
        onCancel={() => {
          setOutlineDetailVisible(false)
          setDetailOutlineEditing(false)
          setEditingDetailFields({})
        }}
        footer={null}
        width={800}
        centered
        destroyOnClose
        className="volume-planning-modal vpm-detail-modal"
        zIndex={VOLUME_PLANNING_DETAIL_MODAL_Z_INDEX}
        styles={{
          content: { padding: 0, borderRadius: '20px', overflow: 'hidden' },
          body: { padding: 0, maxHeight: '85vh', overflow: 'auto' },
        }}
      >
        {detailOutline ? (
          <div className="vpm-detail">
            <div className="vpm-detail-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div className="vpm-detail-title">
                  第 {detailOutline.chapterInVolume ?? detailOutline.globalChapterNumber ?? '-'} 章
                </div>
                {detailOutline.emotionalTone && (
                  <span className="vpm-outline-tag">{detailOutline.emotionalTone}</span>
                )}
                {detailOutline.status && (
                  <span className="vpm-outline-status">{detailOutline.status}</span>
                )}
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {detailOutlineEditing ? (
                  <>
                    <Button size="small" onClick={() => { setDetailOutlineEditing(false); setEditingDetailFields({}) }}>取消</Button>
                    <Button size="small" type="primary" loading={detailOutlineSaving} onClick={handleSaveDetailOutline}>保存</Button>
                  </>
                ) : (
                  <Button size="small" onClick={() => {
                    setEditingDetailFields({
                      direction: formatKeyPlotPointsText(detailOutline.keyPlotPoints) || detailOutline.direction || '',
                    })
                    setDetailOutlineEditing(true)
                  }}>编辑</Button>
                )}
              </div>
            </div>
            <div className="vpm-detail-body">
              <div className="vpm-detail-block">
                <div className="vpm-detail-key">章节细纲</div>
                {detailOutlineEditing ? (
                  <textarea
                    className="vpm-edit-textarea"
                    value={editingDetailFields.direction ?? ''}
                    onChange={(e) => setEditingDetailFields((p) => ({ ...p, direction: e.target.value }))}
                    rows={12}
                    placeholder="输入章节细纲内容..."
                  />
                ) : (
                  <div className="vpm-detail-value prewrap">
                    {formatKeyPlotPointsText(detailOutline.keyPlotPoints) || detailOutline.direction || '暂无'}
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : null}
      </Modal>

      {/* 重新生成细纲弹窗 */}
      <Modal
        title="重新生成细纲"
        open={regenModalVisible}
        onCancel={() => setRegenModalVisible(false)}
        centered
        zIndex={VOLUME_PLANNING_REGEN_MODAL_Z_INDEX}
        footer={[
          <Button key="cancel" onClick={() => setRegenModalVisible(false)}>取消</Button>,
          <Button key="ok" type="primary" loading={isRegenerating} onClick={() => handleRegenerateChapterOutline(regenFrom, regenTo)}>
            开始重新生成
          </Button>,
        ]}
      >
        <div style={{ padding: '8px 0 16px' }}>
          <div style={{ marginBottom: 16, padding: '10px 14px', background: '#f8fafc', borderRadius: 8, fontSize: 13, color: '#475569', lineHeight: 1.7 }}>
            <div>当前已生成：<strong style={{ color: '#1e293b' }}>{outlineCoverageText}</strong></div>
            {(outlineReminderInfo?.lastOutlineChapterInVolume ?? 0) > 0 && (
              <div style={{ marginTop: 4 }}>共 <strong style={{ color: '#1e293b' }}>{sortedOutlineList.length}</strong> 个细纲</div>
            )}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 13, color: '#334155', flexShrink: 0 }}>重新生成卷内第</span>
            <InputNumber
              min={1}
              max={regenTo}
              value={regenFrom}
              onChange={(v) => v && setRegenFrom(v)}
              size="small"
              style={{ width: 70 }}
            />
            <span style={{ fontSize: 13, color: '#334155' }}>章 到 第</span>
            <InputNumber
              min={regenFrom}
              max={outlineReminderInfo?.lastOutlineChapterInVolume || sortedOutlineList.length || 99}
              value={regenTo}
              onChange={(v) => v && setRegenTo(v)}
              size="small"
              style={{ width: 70 }}
            />
            <span style={{ fontSize: 13, color: '#334155' }}>章</span>
          </div>
          <div className="vpm-requirement-card" style={{ marginTop: 16, marginBottom: 0 }}>
            <div className="vpm-requirement-head">
              <div>
                <div className="vpm-requirement-title">重新生成要求</div>
                <div className="vpm-requirement-desc">可选，本次重新生成会参考这里的要求。</div>
              </div>
              {outlineGenerationRequirement.trim() ? (
                <button
                  className="vpm-clear-link"
                  onClick={() => setOutlineGenerationRequirement('')}
                  type="button"
                  disabled={isRegenerating}
                >
                  清空
                </button>
              ) : null}
            </div>
            <textarea
              className="vpm-requirement-input"
              value={outlineGenerationRequirement}
              onChange={(e) => setOutlineGenerationRequirement(e.target.value)}
              rows={4}
              disabled={isRegenerating}
              placeholder="例如：把这一段节奏再压紧一些；减少解释，多用冲突推动；这一批重点强化反派压迫感。"
            />
          </div>
          <div style={{ marginTop: 12, fontSize: 12, color: '#94a3b8' }}>
            将重新生成第 {regenFrom} - {regenTo} 章共 {regenTo - regenFrom + 1} 个细纲，会携带前序细纲、人设和当前填写的生成要求作为上下文。
          </div>
        </div>
      </Modal>


      {/* 章节章纲弹窗 (Modernized) */}
      <Modal
        title={null}
        open={chapterOutlineLoading || !!editingChapterOutline || chapterOutlineListVisible}
        onCancel={() => {
          setChapterOutlineListVisible(false)
          setChapterOutlineVolumeId(null)
          setEditingChapterOutline(null)
        }}
        footer={null}
        width={1100}
        centered
        destroyOnClose
        styles={{ 
          content: { padding: 0, borderRadius: '16px', overflow: 'hidden' },
          body: { padding: 0, height: '700px' } 
        }}
        closable={false}
      >
        <div style={{ display: 'flex', height: '100%', background: '#fff' }}>
          {/* 左侧列表 (Volume List) */}
          <div 
            className="co-list-sidebar"
            style={{ 
              width: chapterOutlineListVisible ? '280px' : '0', 
              opacity: chapterOutlineListVisible ? 1 : 0,
              padding: chapterOutlineListVisible ? '20px 12px 20px 20px' : '0',
              overflow: 'hidden'
            }}
          >
            <div className="co-list-header">
              <span className="co-list-title">
                <BookOutlined style={{ marginRight: 8, color: '#4f46e5' }} />
                本卷章纲
              </span>
              <button 
                className="co-icon-btn"
                onClick={() => setChapterOutlineListVisible(false)}
                title="收起列表"
              >
                <MenuFoldOutlined />
              </button>
            </div>
            
            <div className="co-list-scroll-area">
              {chapterOutlineListLoading ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: '#9ca3af' }}>
                  <Spin size="small" />
                  <div style={{ marginTop: 8, fontSize: 12 }}>加载中...</div>
                </div>
              ) : chapterOutlineVolumeId && chapterOutlineList.length > 0 ? (
                chapterOutlineList.map((item) => {
                  const isActive = editingChapterOutline && 
                    item.globalChapterNumber === editingChapterOutline.globalChapterNumber;
                  const preview = getOutlinePreview(item)
                  
                  return (
                    <div
                      key={item.id}
                      className={`co-list-item ${isActive ? 'active' : ''}`}
                      onClick={() => handleSelectOutlineChapter(item.globalChapterNumber)}
                    >
                      <div className="co-item-header">
                        <span className="co-item-title">
                          第 {item.chapterInVolume ?? item.globalChapterNumber ?? '-'} 章
                        </span>
                      </div>
                      {preview && (
                        <div className="co-item-tone" style={{ marginTop: 6 }}>
                          {preview}
                        </div>
                      )}
                    </div>
                  )
                })
              ) : (
                <div style={{ textAlign: 'center', padding: '40px 0', color: '#9ca3af', fontSize: 13 }}>
                  暂无章纲数据
                </div>
              )}
            </div>
          </div>

          {/* 右侧编辑区 (Editor) */}
          <div className="co-editor-area" style={{ padding: '24px 32px' }}>
            {/* Header */}
            <div className="co-editor-header">
              <div className="co-chapter-info">
                {!chapterOutlineListVisible && (
                  <button 
                    className="co-toggle-sidebar-btn"
                    onClick={() => {
                      setChapterOutlineListVisible(true)
                      // 每次展开都重新查询章纲列表，确保数据最新
                      // 使用 summary=true 只查询摘要字段，提升性能
                      if (chapterOutlineVolumeId) {
                        setChapterOutlineListLoading(true)
                        getChapterOutlinesByVolume(chapterOutlineVolumeId, true)
                          .then(list => setChapterOutlineList(list))
                          .finally(() => setChapterOutlineListLoading(false))
                      }
                    }}
                  >
                    <MenuUnfoldOutlined /> 展开列表
                  </button>
                )}
                <div className="co-chapter-title">
                  {editingChapterOutline ? (
                    <>
                      第 {editingChapterOutline.chapterInVolume ?? editingChapterOutline.globalChapterNumber ?? '-'} 章
                      <span style={{ fontWeight: 400, color: '#6b7280', marginLeft: 8, fontSize: 16 }}>
                        {editingChapterOutline.volumeNumber ? `· 第${editingChapterOutline.volumeNumber}卷` : ''}
                      </span>
                    </>
                  ) : '加载中...'}
                </div>
              </div>
              
              <div style={{ display: 'flex', gap: 12 }}>
                <button
                  className="co-close-btn"
                  onClick={() => {
                    setChapterOutlineListVisible(false)
                    setChapterOutlineVolumeId(null)
                    setEditingChapterOutline(null)
                  }}
                >
                  关闭
                </button>
                <Button
                  type="primary"
                  onClick={handleSaveChapterOutline}
                  loading={chapterOutlineLoading}
                  icon={<SaveOutlined />}
                  className="co-save-btn"
                >
                  {editingChapterOutline?.outlineId ? '保存章纲' : '创建章纲'}
                </Button>
              </div>
            </div>

            {/* Form Content */}
            {chapterOutlineLoading && !editingChapterOutline ? (
              <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                <Spin size="large" tip="正在加载章纲内容..." />
              </div>
            ) : editingChapterOutline ? (
              <div className="co-form-grid">
                {/* 核心字段 */}
                <div className="co-form-group">
                  <div className="co-label">
                    <CompassOutlined className="co-label-icon" />
                    章节细纲（纯文本）
                  </div>
                  <textarea
                    className="co-textarea"
                    value={editingChapterOutline.direction}
                    onChange={(e) =>
                      setEditingChapterOutline({
                        ...editingChapterOutline,
                        direction: e.target.value,
                      })
                    }
                    placeholder={`第N章：章节标题\n主要出场人物：角色A（...）、角色B（...）\n单元内作用：一句话说明本章作用。\n情节：\n（1）...\n（2）...\n（3）...\n（4）...\n（5）...（结尾留钩子）`}
                  />
                </div>

                <div className="co-form-group">
                  <div className="co-label">
                    <AimOutlined className="co-label-icon" />
                    伏笔动作 (Action)
                  </div>
                  <input
                    className="co-input"
                    value={editingChapterOutline.foreshadowAction || ''}
                    onChange={(e) =>
                      setEditingChapterOutline({
                        ...editingChapterOutline,
                        foreshadowAction: e.target.value,
                      })
                    }
                    placeholder="例如：NONE, BURY(埋伏笔), REVEAL(揭伏笔)..."
                  />
                </div>

                <div className="co-form-group">
                  <div className="co-label">
                    <FileTextOutlined className="co-label-icon" />
                    伏笔详情 (Detail)
                  </div>
                  <textarea
                    className="co-textarea large"
                    style={{ minHeight: '180px' }}
                    value={editingChapterOutline.foreshadowDetail || ''}
                    onChange={(e) =>
                      setEditingChapterOutline({
                        ...editingChapterOutline,
                        foreshadowDetail: e.target.value,
                      })
                    }
                    placeholder="描述具体的伏笔内容..."
                  />
                </div>
              </div>
            ) : null}
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

      {/* AI审稿抽屉 - 大厂级极简高级设计 */}
      <Drawer
        open={reviewDrawerVisible}
        onClose={() => setReviewDrawerVisible(false)}
        width={680}
        placement="right"
        closable={false}
        mask={false}
        headerStyle={{ display: 'none' }}
        bodyStyle={{ padding: 0, overflow: 'hidden' }}
        style={{ 
          boxShadow: '-1px 0 0 0 rgba(0,0,0,0.04), -16px 0 48px -12px rgba(0,0,0,0.12)',
        }}
      >
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          background: '#ffffff',
          position: 'relative'
        }}>
          {/* 顶部导航栏 */}
          <div style={{
            padding: '20px 24px',
            borderBottom: '1px solid rgba(0,0,0,0.06)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexShrink: 0,
            background: '#fff'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: '#000',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '16px'
              }}>
                <span style={{ filter: 'brightness(10)' }}>📝</span>
              </div>
              <div>
                <div style={{ 
                  fontSize: '15px', 
                  fontWeight: 600, 
                  color: '#0a0a0a',
                  letterSpacing: '-0.3px'
                }}>
                  AI 审稿
                </div>
                <div style={{ 
                  fontSize: '12px', 
                  color: '#737373', 
                  marginTop: '1px',
                  fontWeight: 400
                }}>
                  智能分析内容质量
                </div>
              </div>
            </div>

            <button
              onClick={() => setReviewDrawerVisible(false)}
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                border: 'none',
                background: 'transparent',
                color: '#a3a3a3',
                fontSize: '18px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = '#f5f5f5';
                e.currentTarget.style.color = '#525252';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.color = '#a3a3a3';
              }}
            >
              ×
            </button>
          </div>

          {/* 主内容区域 */}
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '24px 20px',
            display: 'flex',
            flexDirection: 'column',
            background: '#f0fdf4'
          }}>
            {isReviewing ? (
              /* ===== 流式输出状态：实时显示审稿内容 ===== */
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {/* 状态标签栏 */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0 4px'
                }}>
                  <div style={{ 
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px'
                  }}>
                    {/* 加载动画：内联小圆环 */}
                    <div style={{ position: 'relative', width: '14px', height: '14px' }}>
                      <div style={{
                        position: 'absolute',
                        inset: 0,
                        borderRadius: '50%',
                        border: '2px solid transparent',
                        borderTopColor: '#16a34a',
                        animation: 'spin 0.6s linear infinite'
                      }} />
                    </div>
                    <span style={{ 
                      fontSize: '13px', 
                      fontWeight: 500, 
                      color: '#166534'
                    }}>
                      正在分析
                    </span>
                  </div>
                  <span style={{ 
                    fontSize: '12px', 
                    color: '#6b7280',
                    fontFamily: 'SF Mono, Monaco, monospace'
                  }}>
                    {reviewResult.length.toLocaleString()} 字符
                  </span>
                </div>
                
                {/* 流式输出内容卡片 */}
                <div style={{
                  flex: 1,
                  background: '#ffffff',
                  borderRadius: '12px',
                  border: '1px solid rgba(22, 163, 74, 0.15)',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  overflow: 'hidden',
                  display: 'flex',
                  flexDirection: 'column'
                }}>
                  <div style={{
                    flex: 1,
                    padding: '20px 24px',
                    overflowY: 'auto'
                  }}>
                    <MarkdownRenderer content={reviewResult || '等待 AI 响应...'} />
                  </div>
                </div>
              </div>
            ) : reviewResult ? (
              /* ===== 结果展示：Markdown渲染 ===== */
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {/* 状态标签栏 */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0 4px'
                }}>
                  <div style={{ 
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px'
                  }}>
                    <div style={{
                      width: '6px',
                      height: '6px',
                      borderRadius: '50%',
                      background: '#22c55e'
                    }} />
                    <span style={{ 
                      fontSize: '13px', 
                      fontWeight: 500, 
                      color: '#166534'
                    }}>
                      分析完成
                    </span>
                  </div>
                  <span style={{ 
                    fontSize: '12px', 
                    color: '#6b7280',
                    fontFamily: 'SF Mono, Monaco, monospace'
                  }}>
                    {reviewResult.length.toLocaleString()} 字符
                  </span>
                </div>
                
                {/* 结果内容卡片 - 使用MarkdownRenderer */}
                <div style={{
                  flex: 1,
                  background: '#ffffff',
                  borderRadius: '12px',
                  border: '1px solid rgba(22, 163, 74, 0.15)',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  overflow: 'hidden',
                  display: 'flex',
                  flexDirection: 'column'
                }}>
                  <div style={{
                    flex: 1,
                    padding: '20px 24px',
                    overflowY: 'auto'
                  }}>
                    <MarkdownRenderer content={reviewResult} />
                  </div>
                </div>
              </div>
            ) : (
              /* ===== 初始状态：引导用户操作 ===== */
              <div style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '40px 20px'
              }}>
                <div style={{ 
                  width: '72px', 
                  height: '72px', 
                  borderRadius: '20px',
                  background: '#fff',
                  border: '1px solid rgba(0,0,0,0.08)',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginBottom: '24px'
                }}>
                  <span style={{ fontSize: '28px' }}>📝</span>
                </div>
                
                <div style={{ 
                  fontSize: '16px', 
                  fontWeight: 600, 
                  color: '#171717', 
                  marginBottom: '8px',
                  letterSpacing: '-0.3px'
                }}>
                  准备审稿
                </div>
                <div style={{ 
                  fontSize: '14px', 
                  color: '#737373', 
                  textAlign: 'center', 
                  maxWidth: '280px', 
                  lineHeight: '1.6', 
                  marginBottom: '32px'
                }}>
                  AI 将从多个维度分析您的内容，提供专业的改进建议
                </div>
                
                <button
                  onClick={executeReviewManuscript}
                  style={{
                    padding: '12px 24px',
                    border: 'none',
                    borderRadius: '8px',
                    background: '#171717',
                    color: '#fff',
                    cursor: 'pointer',
                    fontSize: '14px',
                    fontWeight: 500,
                    transition: 'all 0.15s ease',
                    boxShadow: '0 1px 2px rgba(0,0,0,0.1)'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = '#404040';
                    e.currentTarget.style.transform = 'translateY(-1px)';
                    e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = '#171717';
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 1px 2px rgba(0,0,0,0.1)';
                  }}
                >
                  开始审稿
                </button>
              </div>
            )}
          </div>

          {/* 底部操作栏 */}
          {reviewResult && (
            <div style={{
              padding: '16px 28px',
              background: '#fff',
              borderTop: '1px solid rgba(0,0,0,0.06)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexShrink: 0
            }}>
              {/* 左侧：辅助操作 */}
              <button
                onClick={() => {
                  navigator.clipboard.writeText(reviewResult);
                  message.success('已复制到剪贴板');
                }}
                style={{
                  padding: '8px 14px',
                  borderRadius: '6px',
                  border: 'none',
                  background: 'transparent',
                  color: '#525252',
                  fontWeight: 500,
                  fontSize: '13px',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#f5f5f5';
                  e.currentTarget.style.color = '#171717';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'transparent';
                  e.currentTarget.style.color = '#525252';
                }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
                复制
              </button>
              
              {/* 右侧：主操作 */}
              <button
                onClick={() => setReviewDrawerVisible(false)}
                style={{
                  padding: '8px 16px',
                  borderRadius: '6px',
                  border: 'none',
                  background: '#171717',
                  color: '#fff',
                  fontWeight: 500,
                  fontSize: '13px',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#404040';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = '#171717';
                }}
              >
                完成
              </button>
            </div>
          )}
          
          {/* 未处理时的底部栏 */}
          {!reviewResult && !isReviewing && (
            <div style={{
              padding: '16px 28px',
              background: '#fff',
              borderTop: '1px solid rgba(0,0,0,0.06)',
              display: 'flex',
              justifyContent: 'flex-end',
              flexShrink: 0
            }}>
              <button
                onClick={() => setReviewDrawerVisible(false)}
                style={{
                  padding: '8px 16px',
                  borderRadius: '6px',
                  border: '1px solid rgba(0,0,0,0.15)',
                  background: '#fff',
                  color: '#525252',
                  fontWeight: 500,
                  fontSize: '13px',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(0,0,0,0.3)';
                  e.currentTarget.style.color = '#171717';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(0,0,0,0.15)';
                  e.currentTarget.style.color = '#525252';
                }}
              >
                关闭
              </button>
            </div>
          )}
        </div>
      </Drawer>
      
      {/* AI消痕抽屉 - 大厂级极简高级设计 */}
      <Drawer
        open={traceRemovalDrawerVisible}
        onClose={() => setTraceRemovalDrawerVisible(false)}
        width={680}
        placement="right"
        closable={false}
        mask={false}
        headerStyle={{ display: 'none' }}
        bodyStyle={{ padding: 0, overflow: 'hidden' }}
        style={{ 
          boxShadow: '-1px 0 0 0 rgba(0,0,0,0.04), -16px 0 48px -12px rgba(0,0,0,0.12)',
        }}
      >
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          background: '#ffffff',
          position: 'relative'
        }}>
          {/* 
            ========== 顶部导航栏 ==========
            设计理念：参考 Linear/Notion 的极简导航
            - 纯白背景 + 极细分割线，干净利落
            - 去掉花哨的渐变，用留白和字重建立层次
            - 关闭按钮使用 ghost 风格，不抢视觉焦点
          */}
          <div style={{
            padding: '20px 24px',
            borderBottom: '1px solid rgba(0,0,0,0.06)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexShrink: 0,
            background: '#fff'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              {/* 图标：使用微妙的背景而非强烈渐变 */}
              <div style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: '#000',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '16px'
              }}>
                <span style={{ filter: 'brightness(10)' }}>✦</span>
              </div>
              <div>
                <div style={{ 
                  fontSize: '15px', 
                  fontWeight: 600, 
                  color: '#0a0a0a',
                  letterSpacing: '-0.3px'
                }}>
                  AI 消痕
                </div>
                <div style={{ 
                  fontSize: '12px', 
                  color: '#737373', 
                  marginTop: '1px',
                  fontWeight: 400
                }}>
                  智能优化文本自然度
                </div>
              </div>
            </div>

            {/* 关闭按钮：极简圆形 */}
            <button
              onClick={() => setTraceRemovalDrawerVisible(false)}
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                border: 'none',
                background: 'transparent',
                color: '#a3a3a3',
                fontSize: '18px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = '#f5f5f5';
                e.currentTarget.style.color = '#525252';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.color = '#a3a3a3';
              }}
            >
              ×
            </button>
          </div>

          {/* 
            ========== 主内容区域 ==========
            设计理念：
            - 大量留白，让内容有呼吸感
            - 使用卡片承载内容，但卡片本身极简
            - 状态切换使用优雅的过渡
          */}
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '24px 20px',
            display: 'flex',
            flexDirection: 'column',
            background: '#f0fdf4'
          }}>
            {isRemovingTrace ? (
              /* ===== 流式输出状态：实时显示处理内容 ===== */
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {/* 状态标签栏 */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0 4px'
                }}>
                  <div style={{ 
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px'
                  }}>
                    {/* 加载动画：内联小圆环 */}
                    <div style={{ position: 'relative', width: '14px', height: '14px' }}>
                      <div style={{
                        position: 'absolute',
                        inset: 0,
                        borderRadius: '50%',
                        border: '2px solid transparent',
                        borderTopColor: '#16a34a',
                        animation: 'spin 0.6s linear infinite'
                      }} />
                    </div>
                    <span style={{ 
                      fontSize: '13px', 
                      fontWeight: 500, 
                      color: '#166534'
                    }}>
                      正在处理
                    </span>
                  </div>
                  <span style={{ 
                    fontSize: '12px', 
                    color: '#6b7280',
                    fontFamily: 'SF Mono, Monaco, monospace'
                  }}>
                    {processedContent.length.toLocaleString()} 字符
                  </span>
                </div>
                
                {/* 流式输出内容卡片 */}
                <div style={{
                  flex: 1,
                  background: '#ffffff',
                  borderRadius: '12px',
                  border: '1px solid rgba(22, 163, 74, 0.15)',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  overflow: 'hidden',
                  display: 'flex',
                  flexDirection: 'column'
                }}>
                  <div style={{
                    flex: 1,
                    padding: '20px 24px',
                    fontSize: '14px',
                    lineHeight: '1.75',
                    color: '#262626',
                    whiteSpace: 'pre-wrap',
                    overflowY: 'auto',
                    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
                  }}>
                    {processedContent || '等待 AI 响应...'}
                  </div>
                </div>
              </div>
            ) : processedContent ? (
              /* ===== 结果展示：清晰的信息层级 ===== */
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {/* 状态标签栏 */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0 4px'
                }}>
                  <div style={{ 
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px'
                  }}>
                    <div style={{
                      width: '6px',
                      height: '6px',
                      borderRadius: '50%',
                      background: '#22c55e'
                    }} />
                    <span style={{ 
                      fontSize: '13px', 
                      fontWeight: 500, 
                      color: '#166534'
                    }}>
                      处理完成
                    </span>
                  </div>
                  <span style={{ 
                    fontSize: '12px', 
                    color: '#6b7280',
                    fontFamily: 'SF Mono, Monaco, monospace'
                  }}>
                    {processedContent.length.toLocaleString()} 字符
                  </span>
                </div>
                
                {/* 结果内容卡片 */}
                <div style={{
                  flex: 1,
                  background: '#ffffff',
                  borderRadius: '12px',
                  border: '1px solid rgba(22, 163, 74, 0.15)',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  overflow: 'hidden',
                  display: 'flex',
                  flexDirection: 'column'
                }}>
                  <div style={{
                    flex: 1,
                    padding: '20px 24px',
                    fontSize: '14px',
                    lineHeight: '1.75',
                    color: '#262626',
                    whiteSpace: 'pre-wrap',
                    overflowY: 'auto',
                    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
                  }}>
                    {processedContent}
                  </div>
                </div>
              </div>
            ) : (
              /* ===== 初始状态：引导用户操作 ===== */
              <div style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '40px 20px'
              }}>
                {/* 图标：使用简洁的线性图标风格 */}
                <div style={{ 
                  width: '72px', 
                  height: '72px', 
                  borderRadius: '20px',
                  background: '#fff',
                  border: '1px solid rgba(0,0,0,0.08)',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginBottom: '24px'
                }}>
                  <span style={{ fontSize: '28px' }}>✦</span>
                </div>
                
                <div style={{ 
                  fontSize: '16px', 
                  fontWeight: 600, 
                  color: '#171717', 
                  marginBottom: '8px',
                  letterSpacing: '-0.3px'
                }}>
                  准备优化
                </div>
                <div style={{ 
                  fontSize: '14px', 
                  color: '#737373', 
                  textAlign: 'center', 
                  maxWidth: '280px', 
                  lineHeight: '1.6', 
                  marginBottom: '32px'
                }}>
                  AI 将分析并优化您的文本，去除机械感，使表达更加自然流畅
                </div>
                
                {/* 主操作按钮：参考 Vercel 的黑色按钮风格 */}
                <button
                  onClick={executeRemoveAITrace}
                  style={{
                    padding: '12px 24px',
                    border: 'none',
                    borderRadius: '8px',
                    background: '#171717',
                    color: '#fff',
                    cursor: 'pointer',
                    fontSize: '14px',
                    fontWeight: 500,
                    transition: 'all 0.15s ease',
                    boxShadow: '0 1px 2px rgba(0,0,0,0.1)'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = '#404040';
                    e.currentTarget.style.transform = 'translateY(-1px)';
                    e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = '#171717';
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 1px 2px rgba(0,0,0,0.1)';
                  }}
                >
                  开始消痕
                </button>
              </div>
            )}
          </div>

          {/* 
            ========== 底部操作栏 ==========
            设计理念：
            - 固定在底部，不随内容滚动
            - 按钮使用明确的主次关系
            - 主按钮黑色，次要按钮 ghost 风格
          */}
          {processedContent && (
            <div style={{
              padding: '16px 28px',
              background: '#fff',
              borderTop: '1px solid rgba(0,0,0,0.06)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexShrink: 0
            }}>
              {/* 左侧：辅助操作 */}
              <button
                onClick={() => {
                  navigator.clipboard.writeText(processedContent);
                  message.success('已复制到剪贴板');
                }}
                style={{
                  padding: '8px 14px',
                  borderRadius: '6px',
                  border: 'none',
                  background: 'transparent',
                  color: '#525252',
                  fontWeight: 500,
                  fontSize: '13px',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#f5f5f5';
                  e.currentTarget.style.color = '#171717';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'transparent';
                  e.currentTarget.style.color = '#525252';
                }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
                复制
              </button>
              
              {/* 右侧：主操作 */}
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  onClick={() => setTraceRemovalDrawerVisible(false)}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '6px',
                    border: '1px solid rgba(0,0,0,0.15)',
                    background: '#fff',
                    color: '#525252',
                    fontWeight: 500,
                    fontSize: '13px',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'rgba(0,0,0,0.3)';
                    e.currentTarget.style.color = '#171717';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'rgba(0,0,0,0.15)';
                    e.currentTarget.style.color = '#525252';
                  }}
                >
                  取消
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
                      message.success('已应用到正文')
                      setTraceRemovalDrawerVisible(false)
                    }
                  }}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '6px',
                    border: 'none',
                    background: '#171717',
                    color: '#fff',
                    fontWeight: 500,
                    fontSize: '13px',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = '#404040';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = '#171717';
                  }}
                >
                  应用
                </button>
              </div>
            </div>
          )}
          
          {/* 未处理时的底部栏 */}
          {!processedContent && !isRemovingTrace && (
            <div style={{
              padding: '16px 28px',
              background: '#fff',
              borderTop: '1px solid rgba(0,0,0,0.06)',
              display: 'flex',
              justifyContent: 'flex-end',
              flexShrink: 0
            }}>
              <button
                onClick={() => setTraceRemovalDrawerVisible(false)}
                style={{
                  padding: '8px 16px',
                  borderRadius: '6px',
                  border: '1px solid rgba(0,0,0,0.15)',
                  background: '#fff',
                  color: '#525252',
                  fontWeight: 500,
                  fontSize: '13px',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(0,0,0,0.3)';
                  e.currentTarget.style.color = '#171717';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(0,0,0,0.15)';
                  e.currentTarget.style.color = '#525252';
                }}
              >
                关闭
              </button>
            </div>
          )}
        </div>
      </Drawer>

      <GraphDataModal
        visible={graphModalVisible}
        novelId={novelIdNumber}
        novelTitle={novelTitle || ''}
        onClose={() => setGraphModalVisible(false)}
      />

      {/* 概要抽屉 */}
      <Drawer
        title={
          <span style={{ fontSize: '16px', fontWeight: 600 }}>
            {summaryData?.mode === 'document' ? '📝 文档说明' : '📝 章节概要'}
          </span>
        }
        placement="right"
        width={620}
        mask={false}
        open={summaryDrawerVisible}
        onClose={() => {
          setSummaryDrawerVisible(false)
        }}
      >
        <div style={{ padding: 0 }}>
          {summaryLoading ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin size="large" />
              <div style={{ marginTop: '16px', color: '#666' }}>加载中...</div>
            </div>
          ) : summaryData ? (
            <div style={{ fontSize: 14, lineHeight: 1.8 }}>
              <div style={{ marginBottom: 20, background: '#f8fafc', padding: 16, borderRadius: 10, border: '1px solid #e2e8f0' }}>
                <div style={{ marginBottom: 8 }}>
                  <span style={{ fontWeight: 500, color: '#64748b' }}>标题：</span>
                  <span style={{ color: '#1f2937' }}>{summaryData.title || '未命名'}</span>
                </div>
                {summaryData.chapterNumber != null && (
                  <div style={{ marginBottom: 8 }}>
                    <span style={{ fontWeight: 500, color: '#64748b' }}>章节：</span>
                    <span style={{ color: '#1f2937' }}>第 {summaryData.chapterNumber} 章</span>
                  </div>
                )}
                <div style={{ marginBottom: summaryData.updatedAt ? 8 : 0 }}>
                  <span style={{ fontWeight: 500, color: '#64748b' }}>字数：</span>
                  <span style={{ color: '#1f2937' }}>{Number(summaryData.wordCount || 0).toLocaleString()} 字</span>
                </div>
                {summaryData.updatedAt && (
                  <div>
                    <span style={{ fontWeight: 500, color: '#64748b' }}>更新时间：</span>
                    <span style={{ color: '#1f2937' }}>{summaryData.updatedAt}</span>
                  </div>
                )}
              </div>

              {summaryData.mode === 'chapter' ? (
                <>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                    <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1677ff', margin: 0 }}>概要内容</h3>
                    <Button
                      type="primary"
                      loading={summaryGenerating}
                      onClick={handleGenerateSummary}
                    >
                      {summaryData.summary ? '重新生成概要' : '生成概要'}
                    </Button>
                  </div>

                  {summaryData.summary ? (
                    <div style={{ background: '#f0f7ff', padding: 16, borderRadius: 10, border: '1px solid #bae0ff', color: '#1f2937', whiteSpace: 'pre-wrap' }}>
                      {summaryData.summary}
                    </div>
                  ) : (
                    <div style={{ background: '#fff7e6', padding: 16, borderRadius: 10, border: '1px solid #ffd591', color: '#8c5a16' }}>
                      当前章节还没有概要。点击右上角“生成概要”即可生成。
                    </div>
                  )}

                  {(summaryData.characterCount || summaryData.eventCount || summaryData.message) && (
                    <div style={{ marginTop: 16, background: '#fafafa', padding: 14, borderRadius: 10, border: '1px solid #f0f0f0', color: '#475569' }}>
                      {summaryData.message && <div style={{ marginBottom: (summaryData.characterCount || summaryData.eventCount) ? 8 : 0 }}>{summaryData.message}</div>}
                      {(summaryData.characterCount || summaryData.eventCount) && (
                        <div>
                          提取结果：角色 {Number(summaryData.characterCount || 0)} 个，事件 {Number(summaryData.eventCount || 0)} 个
                        </div>
                      )}
                    </div>
                  )}
                </>
              ) : (
                <>
                  <div style={{ background: '#fff7e6', padding: 16, borderRadius: 10, border: '1px solid #ffd591', color: '#8c5a16', marginBottom: 16 }}>
                    {summaryData.message}
                  </div>
                  {summaryData.preview ? (
                    <div>
                      <h3 style={{ fontSize: 16, fontWeight: 600, color: '#1677ff', marginBottom: 12 }}>文档预览</h3>
                      <div style={{ background: '#f8fafc', padding: 16, borderRadius: 10, border: '1px solid #e2e8f0', color: '#1f2937', whiteSpace: 'pre-wrap' }}>
                        {String(summaryData.preview).slice(0, 1000)}
                        {String(summaryData.preview).length > 1000 ? '\n\n...' : ''}
                      </div>
                    </div>
                  ) : (
                    <div style={{ textAlign: 'center', padding: '32px 0', color: '#94a3b8' }}>
                      当前文档没有可预览内容
                    </div>
                  )}
                </>
              )}
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
              暂无数据
            </div>
          )}
        </div>
      </Drawer>

      <ExportModal
        visible={exportVisible}
        onCancel={() => setExportVisible(false)}
        novelId={novelIdNumber}
        novelTitle={novelTitle}
        chapters={chapters}
        initialTab={exportModalTab}
        onImportSuccess={async () => {
          const refreshedChapters = await getChaptersByNovel(novelIdNumber)
          setChapters(refreshedChapters)

          if (refreshedChapters.length > 0) {
            const latestChapter = [...refreshedChapters].sort(
              (a, b) => (b.chapterNumber || 0) - (a.chapterNumber || 0)
            )[0]
            const detail = await getChapterById(latestChapter.id)
            setEditingType('chapter')
            setSelectedChapter(detail)
          }
        }}
      />

      {/* 细纲缺失提醒弹窗 - 美化版 */}
      <Modal
        title={null}
        open={outlineMissingModalVisible}
        onCancel={() => setOutlineMissingModalVisible(false)}
        width={520}
        footer={null}
        centered
        className="outline-missing-modal"
      >
        <div style={{ padding: '32px 24px' }}>
          {/* 图标和标题 */}
          <div style={{ textAlign: 'center', marginBottom: 28 }}>
            <div style={{
              width: 72,
              height: 72,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 18px',
              boxShadow: '0 6px 16px rgba(250, 173, 20, 0.25), 0 0 0 4px rgba(250, 173, 20, 0.08)',
              position: 'relative' as const,
              overflow: 'hidden'
            }}>
              <div style={{
                position: 'absolute',
                top: -2,
                right: -2,
                width: 24,
                height: 24,
                borderRadius: '50%',
                background: 'rgba(255, 255, 255, 0.3)',
                filter: 'blur(8px)'
              }} />
              <FileTextOutlined style={{ fontSize: 32, color: '#fa8c16' }} />
            </div>
            <h3 style={{ 
              fontSize: 20, 
              fontWeight: 600, 
              color: '#1f2937', 
              margin: '0 0 8px 0',
              letterSpacing: '0.3px'
            }}>
              {outlineMissingSource === 'generate' ? '建议先做卷规划' : '细纲尚未生成'}
            </h3>
            <p style={{ 
              fontSize: 13, 
              color: '#94a3b8', 
              margin: 0,
              fontWeight: 400
            }}>
              {outlineMissingSource === 'generate' ? '卷规划和细纲会让正文生成更稳' : '当前章节缺少写作指导'}
            </p>
          </div>
          
          {/* 说明内容 */}
          <div style={{
            background: 'linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%)',
            borderRadius: 12,
            padding: '18px 20px',
            marginBottom: 28,
            border: '1px solid #fde68a',
            position: 'relative' as const,
            overflow: 'hidden'
          }}>
            <div style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: 4,
              height: '100%',
              background: 'linear-gradient(180deg, #f59e0b 0%, #d97706 100%)'
            }} />
            <p style={{ 
              marginBottom: 12, 
              fontSize: 14, 
              color: '#92400e',
              fontWeight: 500,
              paddingLeft: 8
            }}>
              {outlineMissingSource === 'generate' ? (
                <>
                  当前章节（<strong style={{ color: '#78350f' }}>第 {selectedChapter?.chapterNumber} 章</strong>）还没有可用的卷规划细纲。
                </>
              ) : (
                <>
                  当前章节（<strong style={{ color: '#78350f' }}>第 {selectedChapter?.chapterNumber} 章</strong>）尚未生成细纲。
                </>
              )}
            </p>
            <p style={{ 
              marginBottom: 0, 
              fontSize: 13, 
              color: '#a16207', 
              lineHeight: 1.7,
              paddingLeft: 8
            }}>
              {outlineMissingSource === 'generate'
                ? '建议先打开卷规划，人设与细纲会帮助 AI 更好地把握人物关系、情节推进和节奏，通常能明显提升正文质量。你也可以跳过，继续直接生成。'
                : '细纲可以帮助 AI 更好地理解章节的方向、情节要点和情感基调，生成更符合预期的内容。'}
            </p>
          </div>
          
          {/* 操作按钮 */}
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <Button 
              size="large"
              onClick={() => setOutlineMissingModalVisible(false)}
              className="outline-cancel-btn"
              style={{ 
                minWidth: 100,
                height: 44,
                borderRadius: 11,
                fontWeight: 500,
                fontSize: 14,
                border: '1px solid #e5e7eb',
                background: 'linear-gradient(180deg, #ffffff 0%, #f9fafb 100%)',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)',
                transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                color: '#64748b'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = '#cbd5e1'
                e.currentTarget.style.background = 'linear-gradient(180deg, #f9fafb 0%, #f1f5f9 100%)'
                e.currentTarget.style.color = '#475569'
                e.currentTarget.style.transform = 'translateY(-1px)'
                e.currentTarget.style.boxShadow = '0 2px 6px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.8)'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = '#e5e7eb'
                e.currentTarget.style.background = 'linear-gradient(180deg, #ffffff 0%, #f9fafb 100%)'
                e.currentTarget.style.color = '#64748b'
                e.currentTarget.style.transform = 'translateY(0)'
                e.currentTarget.style.boxShadow = '0 1px 3px rgba(0, 0, 0, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)'
              }}
            >
              取消
            </Button>
            <Button 
              type={outlineMissingSource === 'outline' ? 'primary' : 'default'}
              size="large"
              icon={outlineMissingSource === 'generate' ? <CompassOutlined /> : <FileTextOutlined />}
              onClick={() => {
                if (outlineMissingSource === 'generate') {
                  openVolumePlanningFromGeneratePrompt()
                  return
                }
                openOutlineGenerateModal()
              }}
              style={{ 
                minWidth: 130,
                height: 44,
                borderRadius: 11,
                fontWeight: outlineMissingSource === 'outline' ? 600 : 500,
                fontSize: outlineMissingSource === 'outline' ? 15 : 14,
                background: outlineMissingSource === 'outline'
                  ? 'linear-gradient(145deg, #667eea 0%, #5a67d8 50%, #764ba2 100%)'
                  : 'linear-gradient(180deg, #f0f5ff 0%, #e6edff 100%)',
                borderColor: outlineMissingSource === 'outline' ? 'transparent' : '#adc6ff',
                color: outlineMissingSource === 'outline' ? '#fff' : '#2f54eb',
                boxShadow: outlineMissingSource === 'outline'
                  ? '0 4px 16px rgba(102, 126, 234, 0.4), 0 2px 8px rgba(102, 126, 234, 0.25), inset 0 1px 2px rgba(255, 255, 255, 0.2)'
                  : '0 2px 8px rgba(47, 84, 235, 0.15), inset 0 1px 1px rgba(255, 255, 255, 0.8)',
                transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
              }}
              onMouseEnter={(e) => {
                if (outlineMissingSource === 'outline') {
                  e.currentTarget.style.transform = 'translateY(-2px)'
                  e.currentTarget.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.45), 0 3px 10px rgba(102, 126, 234, 0.3), inset 0 1px 2px rgba(255, 255, 255, 0.25)'
                } else {
                  e.currentTarget.style.background = 'linear-gradient(180deg, #e6edff 0%, #d6e4ff 100%)'
                  e.currentTarget.style.borderColor = '#91a7ff'
                  e.currentTarget.style.transform = 'translateY(-1px)'
                  e.currentTarget.style.boxShadow = '0 4px 12px rgba(47, 84, 235, 0.2), inset 0 1px 1px rgba(255, 255, 255, 0.8)'
                }
              }}
              onMouseLeave={(e) => {
                if (outlineMissingSource === 'outline') {
                  e.currentTarget.style.transform = 'translateY(0)'
                  e.currentTarget.style.boxShadow = '0 4px 16px rgba(102, 126, 234, 0.4), 0 2px 8px rgba(102, 126, 234, 0.25), inset 0 1px 2px rgba(255, 255, 255, 0.2)'
                } else {
                  e.currentTarget.style.background = 'linear-gradient(180deg, #f0f5ff 0%, #e6edff 100%)'
                  e.currentTarget.style.borderColor = '#adc6ff'
                  e.currentTarget.style.transform = 'translateY(0)'
                  e.currentTarget.style.boxShadow = '0 2px 8px rgba(47, 84, 235, 0.15), inset 0 1px 1px rgba(255, 255, 255, 0.8)'
                }
              }}
            >
              {outlineMissingSource === 'generate' ? '打开卷规划' : '生成细纲'}
            </Button>
            {/* 仅当从“生成”按钮进入时显示“继续生成”按钮 */}
            {outlineMissingSource === 'generate' && (
              <Button 
                type="primary"
                size="large"
                icon={<RocketOutlined />}
                onClick={() => {
                  setOutlineMissingModalVisible(false)
                  handleSendAIRequest(true)
                }}
                style={{ 
                  minWidth: 130,
                  height: 44,
                  borderRadius: 11,
                  fontWeight: 600,
                  fontSize: 15,
                  letterSpacing: '0.4px',
                  background: 'linear-gradient(145deg, #667eea 0%, #5a67d8 50%, #764ba2 100%)',
                  border: 'none',
                  boxShadow: '0 4px 16px rgba(102, 126, 234, 0.4), 0 2px 8px rgba(102, 126, 234, 0.25), inset 0 1px 2px rgba(255, 255, 255, 0.2)',
                  transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-2px)'
                  e.currentTarget.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.45), 0 3px 10px rgba(102, 126, 234, 0.3), inset 0 1px 2px rgba(255, 255, 255, 0.25)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)'
                  e.currentTarget.style.boxShadow = '0 4px 16px rgba(102, 126, 234, 0.4), 0 2px 8px rgba(102, 126, 234, 0.25), inset 0 1px 2px rgba(255, 255, 255, 0.2)'
                }}
              >
                继续生成
              </Button>
            )}
          </div>
        </div>
      </Modal>

      <Modal
        title={null}
        open={profileGenerateModalVisible}
        onCancel={() => !isGeneratingProfiles && setProfileGenerateModalVisible(false)}
        closable={!isGeneratingProfiles}
        maskClosable={!isGeneratingProfiles}
        width={480}
        footer={null}
        centered
        zIndex={VOLUME_PLANNING_GENERATE_MODAL_Z_INDEX}
        styles={{
          content: { borderRadius: '18px', overflow: 'hidden' },
          body: { padding: 0 },
        }}
      >
        <div style={{ padding: '24px 20px' }}>
          <div style={{ textAlign: 'center', marginBottom: 20 }}>
            <div style={{
              width: 60,
              height: 60,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 14px',
              boxShadow: '0 8px 24px rgba(249, 115, 22, 0.16), 0 0 0 4px rgba(251, 146, 60, 0.06)'
            }}>
              <BookOutlined style={{ fontSize: 26, color: '#ea580c' }} />
            </div>
            <h3 style={{ fontSize: 18, fontWeight: 600, color: '#1f2937', margin: '0 0 6px 0' }}>
              {profileReady ? '重新生成人设' : '生成人设'}
            </h3>
            <p style={{ fontSize: 13, color: '#94a3b8', margin: 0 }}>
              可补充本次要求，不填也可以直接生成
            </p>
          </div>

          <div style={{
            background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
            borderRadius: 14,
            padding: '14px 16px',
            marginBottom: 16,
            border: '1px solid #e2e8f0'
          }}>
            <div style={{ fontSize: 15, fontWeight: 600, color: '#1f2937', marginBottom: 4 }}>
              {volumeTitle}
            </div>
            <div style={{ fontSize: 12, color: '#64748b' }}>{volumeRangeText}</div>
          </div>

          <div className="vpm-requirement-card" style={{ marginBottom: 20 }}>
            <div className="vpm-requirement-head">
              <div>
                <div className="vpm-requirement-title">本次生成要求</div>
                <div className="vpm-requirement-desc">可选，适合补充人物数量、人物气质、关系重点等要求。</div>
              </div>
              {profileGenerationRequirement.trim() ? (
                <button
                  className="vpm-clear-link"
                  onClick={() => setProfileGenerationRequirement('')}
                  type="button"
                  disabled={isGeneratingProfiles}
                >
                  清空
                </button>
              ) : null}
            </div>
            <textarea
              className="vpm-requirement-input"
              value={profileGenerationRequirement}
              onChange={(e) => setProfileGenerationRequirement(e.target.value)}
              rows={4}
              disabled={isGeneratingProfiles}
              placeholder="例如：主角要更强势一些；核心反派必须保留压迫感；配角控制在 6 人内；突出暧昧张力。"
            />
          </div>

          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <Button
              size="large"
              onClick={() => setProfileGenerateModalVisible(false)}
              disabled={isGeneratingProfiles}
              style={{ minWidth: 110, height: 44, borderRadius: 11 }}
            >
              {isGeneratingProfiles ? '请等待...' : '取消'}
            </Button>
            <Button
              type="primary"
              size="large"
              icon={<RocketOutlined />}
              onClick={handleGenerateVolumeProfiles}
              loading={isGeneratingProfiles}
              style={{ minWidth: 150, height: 44, borderRadius: 11 }}
            >
              {profileReady ? '开始重新生成' : '开始生成'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* 细纲生成弹窗 - 美化版 */}
      <Modal
        title={null}
        open={outlineGenerateModalVisible}
        onCancel={() => !isGeneratingOutline && setOutlineGenerateModalVisible(false)}
        closable={!isGeneratingOutline}
        maskClosable={!isGeneratingOutline}
        width={500}
        footer={null}
        centered
        className="outline-generate-modal"
        zIndex={VOLUME_PLANNING_GENERATE_MODAL_Z_INDEX}
        styles={{
          content: { borderRadius: '18px', overflow: 'hidden' },
          body: { padding: 0 },
        }}
      >
        <div style={{ padding: '24px 20px' }}>
          {/* 图标和标题 */}
          <div style={{ textAlign: 'center', marginBottom: 22 }}>
            <div style={{
              width: 60,
              height: 60,
              borderRadius: '50%',
              background: isGeneratingOutline 
                ? 'linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%)'
                : 'linear-gradient(135deg, #f0f5ff 0%, #d6e4ff 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 14px',
              boxShadow: isGeneratingOutline 
                ? '0 8px 24px rgba(24, 144, 255, 0.25), 0 0 0 4px rgba(24, 144, 255, 0.08)'
                : '0 6px 16px rgba(24, 144, 255, 0.18), 0 0 0 4px rgba(24, 144, 255, 0.06)',
              animation: isGeneratingOutline ? 'pulse 2s ease-in-out infinite' : 'none',
              position: 'relative' as const,
              overflow: 'hidden',
              transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)'
            }}>
              {isGeneratingOutline ? (
                <>
                  <div style={{
                    position: 'absolute',
                    width: '100%',
                    height: '100%',
                    borderRadius: '50%',
                    background: 'linear-gradient(45deg, transparent 30%, rgba(255, 255, 255, 0.3) 50%, transparent 70%)',
                    animation: 'shimmer 2s linear infinite'
                  }} />
                  <Spin size="default" />
                </>
              ) : (
                <FileTextOutlined style={{
                  fontSize: 26,
                  color: '#1890ff',
                  transition: 'all 0.3s ease'
                }} />
              )}
            </div>
            <h3 style={{
              fontSize: 18,
              fontWeight: 600,
              color: '#1f2937',
              margin: '0 0 6px 0',
              letterSpacing: '0.3px'
            }}>
              {isGeneratingOutline ? '正在生成细纲' : '生成细纲'}
            </h3>
            {!isGeneratingOutline && (
              <p style={{ 
                fontSize: 13, 
                color: '#94a3b8', 
                margin: 0,
                fontWeight: 400
              }}>
                为本卷所有章节生成专业写作指导
              </p>
            )}
          </div>
          
          {/* 卷信息 */}
          {(chapterOutlineVolumeId || (volumes && volumes.length > 0)) && (
            <div style={{
              background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
              borderRadius: 14,
              padding: '14px 16px',
              marginBottom: 18,
              border: '1px solid #e2e8f0',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.04)',
              transition: 'all 0.3s ease'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <div style={{
                  width: 48,
                  height: 48,
                  borderRadius: 10,
                  background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#fff',
                  fontWeight: 700,
                  fontSize: 16,
                  boxShadow: '0 4px 12px rgba(102, 126, 234, 0.3), inset 0 1px 2px rgba(255, 255, 255, 0.2)',
                  position: 'relative' as const,
                  overflow: 'hidden'
                }}>
                  <div style={{
                    position: 'absolute',
                    top: -2,
                    right: -2,
                    width: 20,
                    height: 20,
                    borderRadius: '50%',
                    background: 'rgba(255, 255, 255, 0.2)',
                    filter: 'blur(8px)'
                  }} />
                  {(() => {
                    const focusVolumeId = manualPlanningVolumeId || chapterOutlineVolumeId
                    const vol = volumes.find(v => Number(v.id) === focusVolumeId) || volumes[0]
                    return vol?.volumeNumber || '1'
                  })()}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ 
                    fontSize: 15, 
                    fontWeight: 600, 
                    color: '#1f2937',
                    marginBottom: 4,
                    letterSpacing: '0.2px'
                  }}>
                    {(() => {
                      const focusVolumeId = manualPlanningVolumeId || chapterOutlineVolumeId
                      const vol = volumes.find(v => Number(v.id) === focusVolumeId) || volumes[0]
                      return vol?.title || `第${vol?.volumeNumber || 1}卷`
                    })()}
                  </div>
                  <div style={{ 
                    fontSize: 12, 
                    color: '#64748b',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }}>
                    <span style={{
                      display: 'inline-block',
                      width: 4,
                      height: 4,
                      borderRadius: '50%',
                      background: '#94a3b8'
                    }} />
                    {(() => {
                      const focusVolumeId = manualPlanningVolumeId || chapterOutlineVolumeId
                      const vol = volumes.find(v => Number(v.id) === focusVolumeId) || volumes[0]
                      if (vol?.chapterStart && vol?.chapterEnd) {
                        return `第 ${vol.chapterStart} - ${vol.chapterEnd} 章`
                      }
                      return '章节范围待定'
                    })()}
                  </div>
                </div>
              </div>
            </div>
          )}

          {!isGeneratingOutline && (
            <div className="vpm-requirement-card" style={{ marginBottom: 18 }}>
              <div className="vpm-requirement-head">
                <div>
                  <div className="vpm-requirement-title">本次生成要求</div>
                  <div className="vpm-requirement-desc">可选，生成细纲时会优先参考这里的要求。</div>
                </div>
                {outlineGenerationRequirement.trim() ? (
                  <button
                    className="vpm-clear-link"
                    onClick={() => setOutlineGenerationRequirement('')}
                    type="button"
                  >
                    清空
                  </button>
                ) : null}
              </div>
              <textarea
                className="vpm-requirement-input"
                value={outlineGenerationRequirement}
                onChange={(e) => setOutlineGenerationRequirement(e.target.value)}
                rows={4}
                placeholder="例如：前 3 章冲突拉满；少铺设新角色；强化悬念钩子；战斗节奏更快；感情线先压后扬。"
              />
            </div>
          )}
          
          {/* 进度显示 */}
          {isGeneratingOutline ? (
            <div style={{ marginBottom: 28 }}>
              <div style={{ 
                marginBottom: 16,
                padding: '0 8px'
              }}>
                <Progress 
                  percent={outlineGeneratePercent} 
                  status="active"
                  strokeColor={{
                    '0%': '#667eea',
                    '50%': '#5a67d8',
                    '100%': '#764ba2',
                  }}
                  trailColor="#e2e8f0"
                  strokeWidth={8}
                  showInfo={false}
                  style={{
                    lineHeight: 1
                  }}
                />
                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginTop: 8
                }}>
                  <span style={{ fontSize: 12, color: '#94a3b8', fontWeight: 500 }}>
                    {outlineGeneratePercent}%
                  </span>
                  <span style={{ fontSize: 12, color: '#94a3b8' }}>
                    预计 2-5 分钟
                  </span>
                </div>
              </div>
              <div style={{
                background: 'linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)',
                borderRadius: 12,
                padding: '14px 18px',
                border: '1px solid #bae6fd',
                display: 'flex',
                alignItems: 'center',
                gap: 12
              }}>
                <Spin size="small" />
                <div style={{ flex: 1 }}>
                  <div style={{
                    fontSize: 13,
                    color: '#0c4a6e',
                    fontWeight: 500,
                    lineHeight: 1.5
                  }}>
                    {outlineGenerateProgress || '正在生成...'}
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div style={{
              background: 'linear-gradient(135deg, #fafafa 0%, #f5f5f5 100%)',
              borderRadius: 12,
              padding: '14px 16px',
              marginBottom: 20,
              border: '1px solid #e8e8e8',
              position: 'relative' as const,
              overflow: 'hidden'
            }}>
              <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: 4,
                height: '100%',
                background: 'linear-gradient(180deg, #667eea 0%, #764ba2 100%)'
              }} />
              <p style={{ 
                marginBottom: 0, 
                fontSize: 13, 
                color: '#475569', 
                lineHeight: 1.7,
                paddingLeft: 8
              }}>
                细纲将为本卷的每个章节生成<strong style={{ color: '#334155' }}>详细的写作指导</strong>，包括章节方向、情节要点、情感基调等，帮助 AI 更好地把握故事走向。
              </p>
              <p style={{
                marginTop: 10,
                marginBottom: 0,
                fontSize: 12,
                color: '#6366f1',
                lineHeight: 1.7,
                paddingLeft: 8
              }}>
                {outlineGenerationHint}
              </p>
            </div>
          )}
          
          {/* 操作按钮 */}
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <Button 
              size="large"
              onClick={() => setOutlineGenerateModalVisible(false)}
              disabled={isGeneratingOutline}
              className="outline-cancel-btn"
              style={{ 
                minWidth: 110,
                height: 44,
                borderRadius: 11,
                fontWeight: 500,
                fontSize: 14,
                border: '1px solid #e5e7eb',
                background: 'linear-gradient(180deg, #ffffff 0%, #f9fafb 100%)',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)',
                transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                color: '#64748b'
              }}
              onMouseEnter={(e) => {
                if (!isGeneratingOutline) {
                  e.currentTarget.style.borderColor = '#cbd5e1'
                  e.currentTarget.style.background = 'linear-gradient(180deg, #f9fafb 0%, #f1f5f9 100%)'
                  e.currentTarget.style.color = '#475569'
                  e.currentTarget.style.transform = 'translateY(-1px)'
                  e.currentTarget.style.boxShadow = '0 2px 6px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.8)'
                }
              }}
              onMouseLeave={(e) => {
                if (!isGeneratingOutline) {
                  e.currentTarget.style.borderColor = '#e5e7eb'
                  e.currentTarget.style.background = 'linear-gradient(180deg, #ffffff 0%, #f9fafb 100%)'
                  e.currentTarget.style.color = '#64748b'
                  e.currentTarget.style.transform = 'translateY(0)'
                  e.currentTarget.style.boxShadow = '0 1px 3px rgba(0, 0, 0, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)'
                }
              }}
            >
              {isGeneratingOutline ? '请等待...' : '取消'}
            </Button>
            {!isGeneratingOutline && (
              <Button 
                type="primary"
                size="large"
                icon={<RocketOutlined />}
                onClick={() => handleGenerateChapterOutline()}
                className="outline-generate-btn"
                style={{ 
                  minWidth: 150,
                  height: 44,
                  borderRadius: 11,
                  fontWeight: 600,
                  fontSize: 15,
                  letterSpacing: '0.4px',
                  background: 'linear-gradient(145deg, #667eea 0%, #5a67d8 50%, #764ba2 100%)',
                  border: 'none',
                  boxShadow: '0 4px 16px rgba(102, 126, 234, 0.4), 0 2px 8px rgba(102, 126, 234, 0.25), inset 0 1px 2px rgba(255, 255, 255, 0.2)',
                  transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                  position: 'relative' as const,
                  overflow: 'hidden'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-2px)'
                  e.currentTarget.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.45), 0 3px 10px rgba(102, 126, 234, 0.3), inset 0 1px 2px rgba(255, 255, 255, 0.25)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)'
                  e.currentTarget.style.boxShadow = '0 4px 16px rgba(102, 126, 234, 0.4), 0 2px 8px rgba(102, 126, 234, 0.25), inset 0 1px 2px rgba(255, 255, 255, 0.2)'
                }}
              >
                <span style={{ position: 'relative', zIndex: 1 }}>
                  开始生成
                </span>
              </Button>
            )}
          </div>
        </div>
      </Modal>
    </Layout>
  )
}

export default WritingStudioPage
