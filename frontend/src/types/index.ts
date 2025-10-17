// 通用响应类型
export interface ApiResponse<T = any> {
  success: boolean
  data: T
  message?: string
  code?: number
}

// 分页响应类型
export interface PageResponse<T = any> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
  size: number
}

// 用户相关类型
export interface User {
  id: number
  username: string
  email: string
  status: string
  roles: string[]
  createdAt: string
  updatedAt: string
}

// 小说相关类型
export interface Novel {
  id: number
  title: string
  description: string
  genre: string
  status: string
  wordCount: number
  chapterCount: number
  createdBy: number
  createdAt: string
  updatedAt: string
}

// 章节相关类型
export interface Chapter {
  id: number
  novelId: number
  title: string
  content: string
  orderNumber: number
  status: string
  wordCount: number
  createdAt: string
  updatedAt: string
}

// 角色相关类型
export interface Character {
  id: number
  novelId: number
  name: string
  description: string
  personality: string
  background: string
  role: string
  createdAt: string
  updatedAt: string
}

// 世界观相关类型
export interface WorldView {
  id: number
  novelId: number
  name: string
  background: string
  rules: string
  timeline: string
  locations: string
  magicSystem: string
  technology: string
  socialStructure: string
  createdAt: string
  updatedAt: string
}

// 提示词相关类型
export interface Prompt {
  id: number
  name: string
  content: string
  category: string
  style: string
  description: string
  tags: string[]
  usageCount: number
  effectivenessScore: number
  examples: string[]
  author: number
  isPublic: boolean
  createdAt: string
  updatedAt: string
}

// AI任务相关类型
export interface AITask {
  id: number
  name: string
  type: string
  status: string
  input: string
  output: string
  error: string
  progress: number
  userId: number
  novelId: number
  parameters: Record<string, any>
  cost: number
  createdAt: string
  updatedAt: string
}

// 进度相关类型
export interface Progress {
  id: number
  novelId: number
  currentChapter: number
  totalChapters: number
  completionRate: number
  currentStage: string
  wordCount: number
  targetWordCount: number
  estimatedCompletion: string
  dailyGoal: number
  writingStreak: number
  timeSpent: number
  createdAt: string
  updatedAt: string
}

// 伏笔相关类型
export interface Foreshadowing {
  id: number
  novelId: number
  content: string
  type: string
  plannedChapter: number
  plantedChapter: number
  completedChapter: number
  status: string
  description: string
  importance: string
  relatedElements: string[]
  userId: number
  createdAt: string
  updatedAt: string
}

// 剧情点相关类型
export interface PlotPoint {
  id: number
  novelId: number
  title: string
  type: string
  plannedChapter: number
  completedChapter: number
  importance: string
  description: string
  requirements: string[]
  impact: string
  consequences: string[]
  userId: number
  createdAt: string
  updatedAt: string
}

// 参考小说相关类型
export interface ReferenceNovel {
  id: number
  title: string
  author: string
  genre: string
  filePath: string
  fileSize: number
  totalSegments: number
  style: string
  summary: string
  wordCount: number
  analysisStatus: string
  analysisProgress: number
  analysisTimestamp: string
  errorMessage: string
  userId: number
  createdAt: string
  updatedAt: string
}

// 写作技巧相关类型
export interface WritingTechnique {
  id: number
  name: string
  description: string
  category: string
  examples: string[]
  effectivenessScore: number
  usageCount: number
  difficultyLevel: string
  tips: string[]
  commonMistakes: string[]
  relatedTechniques: number[]
  author: number
  createdAt: string
  updatedAt: string
} 