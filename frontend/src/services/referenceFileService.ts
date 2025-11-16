import api from './api'

export interface ReferenceFile {
  id: number
  novelId: number
  fileName: string
  fileType: 'txt' | 'docx'
  fileContent: string
  fileSize: number
  originalPath?: string | null
  wordCount: number
  createdAt: string
  updatedAt: string
}

/**
 * 获取小说的参考文件
 */
export const getReferenceFiles = async (novelId: number): Promise<ReferenceFile[]> => {
  const response = await api.get(`/novels/${novelId}/references`)
  if (!response.success) {
    throw new Error(response.message || '获取参考文件失败')
  }
  return response.data
}

/**
 * 上传参考文件
 */
export const uploadReferenceFile = async (
  novelId: number,
  file: File
): Promise<ReferenceFile> => {
  const formData = new FormData()
  formData.append('file', file)

  const response = await api.post(`/novels/${novelId}/references/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })

  if (!response.success) {
    throw new Error(response.message || '上传参考文件失败')
  }
  return response.data
}

/**
 * 删除参考文件
 */
export const deleteReferenceFile = async (novelId: number, id: number): Promise<void> => {
  const response = await api.delete(`/novels/${novelId}/references/${id}`)
  if (!response.success) {
    throw new Error(response.message || '删除参考文件失败')
  }
}

/**
 * 根据ID查询参考文件
 */
export const getReferenceFileById = async (novelId: number, id: number): Promise<ReferenceFile> => {
  const response = await api.get(`/novels/${novelId}/references/${id}`)
  if (!response.success) {
    throw new Error(response.message || '获取参考文件失败')
  }
  return response.data
}

