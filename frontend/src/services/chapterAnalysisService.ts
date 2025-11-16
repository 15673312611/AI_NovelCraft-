import api from './api'

export interface AnalysisRequest {
  analysisTypes: string[]
  startChapter: number
  endChapter: number
}

export interface ChapterAnalysis {
  id: number
  novelId: number
  analysisType: string
  startChapter: number
  endChapter: number
  analysisContent: string
  wordCount: number
  createdAt: string
  updatedAt: string
}

export const chapterAnalysisService = {
  // 创建章节分析
  async createAnalysis(novelId: number, request: AnalysisRequest): Promise<ChapterAnalysis> {
    const response = await api.post(`/novels/${novelId}/analysis`, request)
    return response.data
  },

  // 获取小说的所有分析记录
  async getAnalysesByNovelId(novelId: number): Promise<ChapterAnalysis[]> {
    const response = await api.get(`/novels/${novelId}/analysis`)
    return response.data
  },

  // 获取单个分析记录
  async getAnalysisById(novelId: number, analysisId: number): Promise<ChapterAnalysis> {
    const response = await api.get(`/novels/${novelId}/analysis/${analysisId}`)
    return response.data
  },

  // 删除分析记录
  async deleteAnalysis(novelId: number, analysisId: number): Promise<void> {
    await api.delete(`/novels/${novelId}/analysis/${analysisId}`)
  },
}

export default chapterAnalysisService

