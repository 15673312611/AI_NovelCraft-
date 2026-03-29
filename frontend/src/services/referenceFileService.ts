import api from './api'

export interface ReferenceFileSummary {
  id: number
  novelId: number
  fileName: string
  fileType: 'txt' | 'docx'
  fileSize: number
  wordCount: number
  createdAt: string
  updatedAt: string
}

export interface ReferenceFileDetail extends ReferenceFileSummary {
  fileContent: string
}

export type ReferenceFile = ReferenceFileSummary

export const getReferenceFiles = async (novelId: number): Promise<ReferenceFileSummary[]> => {
  const response = await api.get(`/novels/${novelId}/references`)
  if (!response.success) {
    throw new Error(response.message || '获取参考文件失败')
  }
  return response.data
}

export const uploadReferenceFile = async (
  novelId: number,
  file: File
): Promise<ReferenceFileSummary> => {
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

export const deleteReferenceFile = async (novelId: number, id: number): Promise<void> => {
  const response = await api.delete(`/novels/${novelId}/references/${id}`)
  if (!response.success) {
    throw new Error(response.message || '删除参考文件失败')
  }
}

export const getReferenceFileById = async (novelId: number, id: number): Promise<ReferenceFileDetail> => {
  const response = await api.get(`/novels/${novelId}/references/${id}`)
  if (!response.success) {
    throw new Error(response.message || '获取参考文件失败')
  }
  return response.data
}
