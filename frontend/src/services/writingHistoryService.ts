import api from './api'

export type WritingHistorySourceType = 'AUTO_SAVE' | 'MANUAL_SAVE' | 'AI_REPLACE' | string

export interface WritingVersionHistory {
  id: number
  novelId: number
  chapterId?: number | null
  documentId?: number | null
  sourceType: WritingHistorySourceType
  content: string
  wordCount: number
  diffRatio?: number | null
  createdAt: string
}

export const getChapterHistory = async (chapterId: number, limit: number = 50): Promise<WritingVersionHistory[]> => {
  const response = await api.get(`/chapters/${chapterId}/history`, { params: { limit } })
  if (response && response.success === false) {
    throw new Error(response.message || '获取章节历史版本失败')
  }
  // 后端返回 { success, data } 或直接返回数组，这里统一兼容
  if (response && Array.isArray(response.data)) {
    return response.data as WritingVersionHistory[]
  }
  if (Array.isArray(response)) {
    return response as WritingVersionHistory[]
  }
  return (response?.data || []) as WritingVersionHistory[]
}

export const getDocumentHistory = async (
  documentId: number,
  limit: number = 50
): Promise<WritingVersionHistory[]> => {
  const response = await api.get(`/documents/${documentId}/history`, { params: { limit } })
  if (response && response.success === false) {
    throw new Error(response.message || '获取文档历史版本失败')
  }
  if (response && Array.isArray(response.data)) {
    return response.data as WritingVersionHistory[]
  }
  if (Array.isArray(response)) {
    return response as WritingVersionHistory[]
  }
  return (response?.data || []) as WritingVersionHistory[]
}

