import api from './api'

export interface WritingTechnique {
  id: string
  name: string
  category: string
  description: string
  examples?: string[]
  effectiveness?: number
  tags?: string[]
  usageCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface WritingTechniqueQuery {
  page?: number
  size?: number
  category?: string
  search?: string
}

export interface WritingTechniqueListResponse {
  content: WritingTechnique[]
  totalElements: number
  totalPages: number
  currentPage?: number
  size: number
}

class WritingTechniqueService {
  async getTechniques(query: WritingTechniqueQuery = {}): Promise<WritingTechniqueListResponse> {
    const params = new URLSearchParams()
    if (query.page !== undefined) params.append('page', String(query.page))
    if (query.size !== undefined) params.append('size', String(query.size))
    if (query.category) params.append('category', query.category)
    if (query.search) params.append('search', query.search)
    const qs = params.toString()
    const url = qs ? `/writing-techniques?${qs}` : '/writing-techniques'
    const response = await api.get(url)
    return response as unknown as WritingTechniqueListResponse
  }

  async getTechnique(id: string): Promise<WritingTechnique> {
    const response = await api.get(`/writing-techniques/${id}`)
    return response as unknown as WritingTechnique
  }

  async createTechnique(technique: Partial<WritingTechnique>): Promise<WritingTechnique> {
    const response = await api.post('/writing-techniques', technique)
    return response as unknown as WritingTechnique
  }

  async updateTechnique(id: string, technique: Partial<WritingTechnique>): Promise<WritingTechnique> {
    const response = await api.put(`/writing-techniques/${id}`, technique)
    return response as unknown as WritingTechnique
  }

  async deleteTechnique(id: string): Promise<void> {
    await api.delete(`/writing-techniques/${id}`)
  }

  async rateTechnique(id: string, rating: number): Promise<void> {
    await api.post(`/writing-techniques/${id}/rate`, { rating })
  }

  async incrementUsage(id: string): Promise<void> {
    await api.post(`/writing-techniques/${id}/use`)
  }

  async getPopular(): Promise<WritingTechnique[]> {
    const response = await api.get('/writing-techniques/popular')
    return response as unknown as WritingTechnique[]
  }

  async getEffective(): Promise<WritingTechnique[]> {
    const response = await api.get('/writing-techniques/effective')
    return response as unknown as WritingTechnique[]
  }
}

export const writingTechniqueService = new WritingTechniqueService()
 