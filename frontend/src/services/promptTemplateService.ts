import api from './api'

export interface PromptTemplate {
  id: number
  name: string
  content: string
  type: string
  category: string
  description: string
  isActive: boolean
  usageCount: number
  createdTime: string
  updatedTime: string
}

/**
 * 获取公开的写作风格模板
 */
export const getWritingStyleTemplates = async (): Promise<PromptTemplate[]> => {
  const response = await api.get('/prompt-templates', {
    params: {
      type: 'official',
      category: 'writing_style',
    },
  })
  return response.data
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

