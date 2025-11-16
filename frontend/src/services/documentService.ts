import api from './api'

export interface NovelDocument {
  id: number
  novelId: number
  folderId: number
  title: string
  content: string
  documentType: 'chapter' | 'custom'
  wordCount: number
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/**
 * 获取文件夹下的文档列表
 * @param folderId 文件夹ID
 * @param summary 是否只获取元数据（不包含content，性能优化）
 */
export const getDocumentsByFolder = async (folderId: number, summary: boolean = true): Promise<NovelDocument[]> => {
  const response = await api.get(`/folders/${folderId}/documents?summary=${summary}`)
  if (!response.success) {
    throw new Error(response.message || '获取文档列表失败')
  }
  return response.data
}

/**
 * 获取文档详情
 */
export const getDocumentById = async (documentId: number): Promise<NovelDocument> => {
  const response = await api.get(`/documents/${documentId}`)
  if (!response.success) {
    throw new Error(response.message || '获取文档失败')
  }
  return response.data
}

/**
 * 创建文档
 */
export const createDocument = async (
  folderId: number,
  payload: Partial<NovelDocument>
): Promise<NovelDocument> => {
  const response = await api.post(`/folders/${folderId}/documents`, payload)
  if (!response.success) {
    throw new Error(response.message || '创建文档失败')
  }
  return response.data
}

/**
 * 更新文档
 */
export const updateDocument = async (
  documentId: number,
  payload: Partial<NovelDocument>
): Promise<NovelDocument> => {
  const response = await api.put(`/documents/${documentId}`, payload)
  if (!response.success) {
    throw new Error(response.message || '更新文档失败')
  }
  return response.data
}

/**
 * 自动保存文档
 */
export const autoSaveDocument = async (documentId: number, content: string): Promise<void> => {
  const response = await api.post(`/documents/${documentId}/auto-save`, { content })
  if (!response.success) {
    throw new Error(response.message || '自动保存失败')
  }
}

/**
 * 删除文档
 */
export const deleteDocument = async (documentId: number): Promise<void> => {
  const response = await api.delete(`/documents/${documentId}`)
  if (!response.success) {
    throw new Error(response.message || '删除文档失败')
  }
}

/**
 * 搜索文档
 */
export const searchDocuments = async (novelId: number, keyword: string): Promise<NovelDocument[]> => {
  const response = await api.get(`/novels/${novelId}/documents/search`, {
    params: { keyword },
  })
  if (!response.success) {
    throw new Error(response.message || '搜索文档失败')
  }
  return response.data
}

/**
 * 初始化默认文件夹结构
 */
export const initDefaultFolders = async (novelId: number): Promise<void> => {
  const response = await api.post(`/novels/${novelId}/init-folders`)
  if (!response.success) {
    throw new Error(response.message || '初始化默认文件夹失败')
  }
}


