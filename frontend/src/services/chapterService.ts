import api from './api'

export interface Chapter {
  id: number
  title: string
  content: string
  novelId: number
  chapterNumber: number
  wordCount: number
  status: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface CreateChapterRequest {
  title: string
  content: string
  novelId: number
  chapterNumber: number
}

export interface UpdateChapterRequest {
  title?: string
  content?: string
  chapterNumber?: number
  status?: string
}

export interface ChapterListResponse {
  content: Chapter[]
  totalElements: number
  totalPages: number
  currentPage: number
  size: number
}

class ChapterService {
  async getChaptersByNovel(novelId: number, page: number = 0, size: number = 10): Promise<Chapter[]> {
    const response = await api.get(`/novels/${novelId}/chapters?page=${page}&size=${size}`)
    const data = response as any
    if (Array.isArray(data)) return data as Chapter[]
    if (data && Array.isArray(data.content)) return data.content as Chapter[]
    return []
  }

  async getChapterById(id: number): Promise<Chapter> {
    const response = await api.get(`/chapters/${id}`)
    return response as unknown as Chapter
  }

  async createChapter(chapterData: CreateChapterRequest): Promise<Chapter> {
    const response = await api.post('/chapters', chapterData)
    return response as unknown as Chapter
  }

  async updateChapter(id: number, chapterData: UpdateChapterRequest): Promise<Chapter> {
    const response = await api.put(`/chapters/${id}`, chapterData)
    return response as unknown as Chapter
  }

  async deleteChapter(id: number): Promise<void> {
    await api.delete(`/chapters/${id}`)
  }

  async getChapterByNumber(novelId: number, chapterNumber: number): Promise<Chapter> {
    const response = await api.get(`/novels/${novelId}/chapters/${chapterNumber}`)
    return response as unknown as Chapter
  }

  async reorderChapters(novelId: number, chapterIds: number[]): Promise<void> {
    await api.put(`/novels/${novelId}/chapters/reorder`, { chapterIds })
  }

  async getChapterStatistics(novelId: number): Promise<{
    totalChapters: number
    totalWords: number
    averageWordsPerChapter: number
    lastUpdated: string
    completionRate: number
  }> {
    const response = await api.get(`/novels/${novelId}/chapters/statistics`)
    return response as unknown as {
      totalChapters: number
      totalWords: number
      averageWordsPerChapter: number
      lastUpdated: string
      completionRate: number
    }
  }

  async searchChapters(novelId: number, query: string): Promise<Chapter[]> {
    const response = await api.get(`/novels/${novelId}/chapters/search?query=${encodeURIComponent(query)}`)
    return response as unknown as Chapter[]
  }

  async autoSaveChapter(id: number, content: string): Promise<void> {
    await api.patch(`/chapters/${id}/autosave`, { content })
  }
}

export const chapterService = new ChapterService() 