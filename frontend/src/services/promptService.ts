import api from './api';

export interface Prompt {
  id: string;
  name: string;
  category: string;
  content: string;
  description: string;
  difficulty: number;
  rating: number;
  usageCount: number;
  tags: string[];
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PromptQuery {
  page: number;
  size: number;
  category?: string;
  search?: string;
}

export interface PromptResponse {
  content: Prompt[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

class PromptService {
  /**
   * 获取提示词列表
   */
  async getPrompts(query: PromptQuery): Promise<PromptResponse> {
    const response = await api.get('/prompts', { params: query });
    return response as unknown as PromptResponse;
  }

  /**
   * 获取提示词详情
   */
  async getPrompt(id: string): Promise<Prompt> {
    const response = await api.get(`/prompts/${id}`);
    return response as unknown as Prompt;
  }

  /**
   * 创建提示词
   */
  async createPrompt(prompt: Partial<Prompt>): Promise<Prompt> {
    const response = await api.post('/prompts', prompt);
    return response as unknown as Prompt;
  }

  /**
   * 更新提示词
   */
  async updatePrompt(id: string, prompt: Partial<Prompt>): Promise<Prompt> {
    const response = await api.put(`/prompts/${id}`, prompt);
    return response as unknown as Prompt;
  }

  /**
   * 删除提示词
   */
  async deletePrompt(id: string): Promise<void> {
    await api.delete(`/prompts/${id}`);
  }

  /**
   * 增加使用次数
   */
  async incrementUsage(id: string): Promise<void> {
    await api.post(`/prompts/${id}/usage`);
  }

  /**
   * 评分
   */
  async rate(id: string, rating: number): Promise<void> {
    await api.post(`/prompts/${id}/rate`, { rating });
  }

  /**
   * 复制提示词
   */
  async copyPrompt(id: string): Promise<Prompt> {
    const response = await api.post(`/prompts/${id}/copy`);
    return response as unknown as Prompt;
  }
}

export const promptService = new PromptService(); 