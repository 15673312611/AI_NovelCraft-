import api from './api'

export interface AIConversation {
  id: number
  novelId: number
  documentId?: number | null
  generatorId?: number | null
  userMessage: string
  assistantMessage: string
  contextData?: any
  wordCount: number
  createdAt: string
}

/**
 * 获取小说的AI对话历史
 */
export const getAIConversations = async (novelId: number): Promise<AIConversation[]> => {
  const response = await api.get(`/novels/${novelId}/ai-history`)
  if (!response.success) {
    throw new Error(response.message || '获取AI对话历史失败')
  }
  return response.data
}

/**
 * 删除对话记录
 */
export const deleteAIConversation = async (novelId: number, id: number): Promise<void> => {
  const response = await api.delete(`/novels/${novelId}/ai-history/${id}`)
  if (!response.success) {
    throw new Error(response.message || '删除对话记录失败')
  }
}

/**
 * 清空对话历史
 */
export const clearAIConversations = async (novelId: number): Promise<void> => {
  const response = await api.delete(`/novels/${novelId}/ai-history`)
  if (!response.success) {
    throw new Error(response.message || '清空对话历史失败')
  }
}

