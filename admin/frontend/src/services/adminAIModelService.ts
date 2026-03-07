import request from './request'

export interface AIModel {
  id: number
  modelId: string
  displayName: string
  provider: string
  apiBaseUrl?: string
  apiKeyRef?: string
  maxTokens: number
  costPer1k?: number
  inputPricePer1k: number
  outputPricePer1k: number
  costMultiplier?: number
  temperature?: number
  available: boolean
  isDefault: boolean
  sortOrder: number
  description?: string
  createdAt: string
  updatedAt: string
}

export interface SystemAIConfig {
  id: number
  configKey: string
  configValue: string
  description?: string
  isEncrypted: boolean
  createdAt: string
  updatedAt: string
}

export interface APIConfig {
  apiKey: string
  baseUrl: string
  configured: string
}

export const adminAIModelService = {
  // 获取所有模型
  getAllModels: () => {
    return request.get<AIModel[]>('/ai-models')
  },

  // 获取可用模型
  getAvailableModels: () => {
    return request.get<AIModel[]>('/ai-models/available')
  },

  // 获取模型详情
  getModel: (id: number) => {
    return request.get<AIModel>(`/ai-models/${id}`)
  },

  // 创建模型
  createModel: (data: Partial<AIModel>) => {
    return request.post<AIModel>('/ai-models', data)
  },

  // 更新模型
  updateModel: (id: number, data: Partial<AIModel>) => {
    return request.put(`/ai-models/${id}`, data)
  },

  // 删除模型
  deleteModel: (id: number) => {
    return request.delete(`/ai-models/${id}`)
  },

  // 设置默认模型
  setDefaultModel: (id: number) => {
    return request.post(`/ai-models/${id}/set-default`)
  },

  // 切换模型可用状态
  toggleAvailable: (id: number) => {
    return request.post(`/ai-models/${id}/toggle-available`)
  },

  // 获取所有系统配置
  getAllConfigs: () => {
    return request.get<SystemAIConfig[]>('/ai-models/configs')
  },

  // 获取API配置
  getAPIConfigs: () => {
    return request.get<Record<string, APIConfig>>('/ai-models/api-configs')
  },

  // 更新API配置
  updateAPIConfig: (provider: string, data: { apiKey?: string; baseUrl?: string }) => {
    return request.post(`/ai-models/api-configs/${provider}`, data)
  },

  // 获取系统设置
  getSystemSettings: () => {
    return request.get<Record<string, string>>('/ai-models/system-settings')
  },

  // 更新系统设置
  updateSystemSettings: (settings: Record<string, string>) => {
    return request.post('/ai-models/system-settings', settings)
  },
}
