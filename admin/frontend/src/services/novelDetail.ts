import request from './request'

export interface NovelDetail {
  id: number
  title: string
  author: string
  genre: string
  status: string
  wordCount: number
  chapterCount: number
  description: string
  createdAt: string
  updatedAt: string
}

export interface Outline {
  id: number
  novelId: number
  title: string
  status: string
  targetChapterCount: number
  coreTheme: string
  plotStructure: string
  createdAt: string
}

export interface Volume {
  id: number
  novelId: number
  title: string
  volumeNumber: number
  chapterStart: number
  chapterEnd: number
  status: string
  theme: string
}

export interface Chapter {
  id: number
  novelId: number
  title: string
  subtitle?: string
  content?: string
  simpleContent?: string
  orderNum: number
  status: string
  wordCount: number
  chapterNumber?: number
  summary?: string
  notes?: string
  isPublic?: boolean
  publishedAt?: string
  readingTimeMinutes?: number
  previousChapterId?: number
  nextChapterId?: number
  createdAt: string
  updatedAt?: string
  generationContext?: string
  reactDecisionLog?: string
}

export interface Character {
  id: number
  novelId: number
  name: string
  characterType: string
  status: string
  appearanceCount: number
  description: string
}

export interface ChapterPlan {
  id: number
  novelId: number
  chapterNumber: number
  title: string
  phase: string
  status: string
  priority: string
  mainGoal: string
}

export interface Worldview {
  id: number
  novelId: number
  term: string
  type: string
  firstMention: number
  description: string
  contextInfo: string
  usageCount: number
  isImportant: boolean
  createdTime: string
  updatedTime: string
}

export const novelDetailAPI = {
  // 获取小说详情
  getNovelDetail: (id: number) => 
    request.get<NovelDetail>(`/novels/${id}/detail`),

  // 获取最新大纲
  getLatestOutline: (id: number) => 
    request.get<Outline>(`/novels/${id}/outline`),

  // 获取卷列表
  getVolumes: (id: number) => 
    request.get<Volume[]>(`/novels/${id}/volumes`),

  // 获取章节列表
  getChapters: (id: number) => 
    request.get<Chapter[]>(`/novels/${id}/chapters`),

  // 获取角色列表
  getCharacters: (id: number) => 
    request.get<Character[]>(`/novels/${id}/characters`),

  // 获取世界观
  getWorldview: (id: number) => 
    request.get<Worldview[]>(`/novels/${id}/worldview`),

  // 获取所有数据
  getAllData: (id: number) => 
    request.get(`/novels/${id}/all`),
}
