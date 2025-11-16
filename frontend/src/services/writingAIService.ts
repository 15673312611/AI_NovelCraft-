import api from './api'

export interface GenerateContentRequest {
  novelId: number
  documentId?: number
  generatorId?: number | null
  userMessage?: string | null
  referenceFileIds?: number[]
  linkedDocumentIds?: number[]
  currentContent?: string
}

export interface GenerateContentResponse {
  success: boolean
  message?: string
  conversationId?: number
}

/**
 * 发起AI生成内容请求（非流式，使用SSE或WebSocket可在组件中实现）
 */
export const generateContent = async (
  payload: GenerateContentRequest
): Promise<GenerateContentResponse> => {
  const response = await api.post('/ai/generate-content', payload)
  if (!response.success) {
    throw new Error(response.message || '生成内容失败')
  }
  return response
}

