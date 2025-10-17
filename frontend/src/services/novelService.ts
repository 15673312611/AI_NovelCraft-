import api from './api'

export interface Novel {
  id: number
  title: string
  description: string
  genre: string
  status: string
  creationStage?: string
  wordCount: number
  chapterCount: number
  createdBy: number
  createdAt: string
  updatedAt: string
  // 新增创作配置字段
  targetTotalChapters?: number
  wordsPerChapter?: number
  plannedVolumeCount?: number
  totalWordTarget?: number
}

export interface CreateNovelRequest {
  title: string
  description: string
  genre: string
  // 新增创作配置字段
  targetTotalChapters?: number
  wordsPerChapter?: number
  plannedVolumeCount?: number
  totalWordTarget?: number
}

export interface UpdateNovelRequest {
  title?: string
  description?: string
  genre?: string
  status?: string
  // 新增创作配置字段
  targetTotalChapters?: number
  wordsPerChapter?: number
  plannedVolumeCount?: number
  totalWordTarget?: number
}

export interface NovelListResponse {
  content: Novel[]
  totalElements: number
  totalPages: number
  currentPage: number
  size: number
}

class NovelService {
  // 兼容方法：部分页面使用 getById，这里提供别名以保持兼容
  async getById(id: number | string): Promise<Novel> {
    const numericId = typeof id === 'string' ? parseInt(id, 10) : id
    return this.getNovelById(numericId)
  }

  async getNovels(page: number = 0, size: number = 10): Promise<Novel[]> {
    const response = await api.get(`/novels?page=${page}&size=${size}`)
    const data = response as unknown as any

    // 如果返回的是分页数据，提取content数组
    if (data && typeof data === 'object' && 'content' in data && Array.isArray(data.content)) {
      return data.content as Novel[]
    }

    // 如果直接是数组，返回数组
    if (Array.isArray(data)) {
      return data as Novel[]
    }

    // 其他情况返回空数组
    return []
  }

  async getNovelById(id: number): Promise<Novel> {
    const response = await api.get(`/novels/${id}`)
    return response as unknown as Novel
  }

  async createNovel(novelData: CreateNovelRequest): Promise<Novel> {
    const response = await api.post('/novels/simple', novelData)
    return response as unknown as Novel
  }

  async updateNovel(id: number, novelData: UpdateNovelRequest): Promise<Novel> {
    const response = await api.put(`/novels/${id}`, novelData)
    return response as unknown as Novel
  }

  async deleteNovel(id: number): Promise<void> {
    await api.delete(`/novels/${id}`)
  }

  async getNovelsByUser(userId: number): Promise<Novel[]> {
    const response = await api.get(`/users/${userId}/novels`)
    return response as unknown as Novel[]
  }

  async searchNovels(query: string, genre?: string, status?: string): Promise<Novel[]> {
    const params = new URLSearchParams({ query })
    if (genre) params.append('genre', genre)
    if (status) params.append('status', status)

    const response = await api.get(`/novels/search?${params.toString()}`)
    return response as unknown as Novel[]
  }

  async getNovelStatistics(id: number): Promise<{
    totalChapters: number
    totalWords: number
    averageWordsPerChapter: number
    lastUpdated: string
    completionRate: number
  }> {
    const response = await api.get(`/novels/${id}/statistics`)
    return response as unknown as {
      totalChapters: number
      totalWords: number
      averageWordsPerChapter: number
      lastUpdated: string
      completionRate: number
    }
  }
}

export const novelService = new NovelService()
export default novelService