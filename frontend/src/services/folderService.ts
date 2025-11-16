import api from './api'

export interface NovelFolder {
  id: number
  novelId: number
  folderName: string
  folderType: 'chapter' | 'custom'
  parentId?: number | null
  sortOrder: number
  isSystem?: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 获取小说的所有文件夹
 */
export const getFoldersByNovelId = async (novelId: number): Promise<NovelFolder[]> => {
  const response = await api.get(`/novels/${novelId}/folders`)
  if (!response.success) {
    throw new Error(response.message || '获取文件夹失败')
  }
  return response.data
}

/**
 * 创建文件夹
 */
export const createFolder = async (
  novelId: number,
  payload: Partial<NovelFolder>
): Promise<NovelFolder> => {
  const response = await api.post(`/novels/${novelId}/folders`, payload)
  if (!response.success) {
    throw new Error(response.message || '创建文件夹失败')
  }
  return response.data
}

/**
 * 更新文件夹
 */
export const updateFolder = async (
  novelId: number,
  folderId: number,
  payload: Partial<NovelFolder>
): Promise<NovelFolder> => {
  const response = await api.put(`/novels/${novelId}/folders/${folderId}`, payload)
  if (!response.success) {
    throw new Error(response.message || '更新文件夹失败')
  }
  return response.data
}

/**
 * 删除文件夹
 */
export const deleteFolder = async (novelId: number, folderId: number): Promise<void> => {
  const response = await api.delete(`/novels/${novelId}/folders/${folderId}`)
  if (!response.success) {
    throw new Error(response.message || '删除文件夹失败')
  }
}

