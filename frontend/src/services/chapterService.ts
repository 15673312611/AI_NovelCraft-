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

interface CreateChapterRequest {
  title: string
  content: string
  novelId: number
  chapterNumber: number
}

interface UpdateChapterRequest {
  title?: string
  content?: string
  chapterNumber?: number
  status?: string
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
}

export const chapterService = new ChapterService() 