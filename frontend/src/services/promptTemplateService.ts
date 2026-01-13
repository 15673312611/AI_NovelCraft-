import api from './api'

export interface PromptTemplate {
  id: number
  name: string
  content: string
  type: string
  category: string
  description: string
  isActive: boolean
  isDefault?: boolean
  usageCount: number
  createdTime: string
  updatedTime: string
  isFavorited?: boolean
}

/**
 * 获取公开的写作风格模板（章节生成模板）
 */
export const getWritingStyleTemplates = async (category?: string): Promise<PromptTemplate[]> => {
  const response = await api.get('/prompt-templates/public', {
    params: {
      category,
    },
  })
  return response.data
}

/**
 * 获取用户自定义模板
 */
export const getUserCustomTemplates = async (category?: string): Promise<PromptTemplate[]> => {
  const response = await api.get('/prompt-templates/custom', {
    params: {
      category,
    },
  })
  return response.data
}

/**
 * 获取用户收藏的模板
 */
export const getUserFavoriteTemplates = async (category?: string): Promise<PromptTemplate[]> => {
  const response = await api.get('/prompt-templates/favorites', {
    params: {
      category,
    },
  })
  return response.data
}

/**
 * 收藏模板
 */
export const favoriteTemplate = async (id: number): Promise<void> => {
  await api.post(`/prompt-templates/${id}/favorite`)
}

/**
 * 取消收藏模板
 */
export const unfavoriteTemplate = async (id: number): Promise<void> => {
  await api.delete(`/prompt-templates/${id}/favorite`)
}

/**
 * 获取模板详情
 */
export const getTemplateById = async (id: number): Promise<PromptTemplate> => {
  const response = await api.get(`/prompt-templates/${id}`)
  return response.data
}

/**
 * 增加模板使用次数
 */
export const incrementTemplateUsage = async (id: number): Promise<void> => {
  await api.post(`/prompt-templates/${id}/usage`)
}

