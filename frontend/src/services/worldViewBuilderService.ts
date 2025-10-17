import api from './api';

export interface WorldViewBuilder {
  id: number;
  name: string;
  description?: string;
  status: 'INITIALIZED' | 'IN_PROGRESS' | 'COMPLETED' | 'PAUSED' | 'CANCELLED';
  currentStep?: string;
  worldType?: string;
  genre?: string;
  targetAudience?: string;
  theme?: string;
  tone?: string;
  complexity?: string;
  aiSuggestions?: string;
  userPreferences?: string;
  novelId: number;
  novelTitle?: string;
  createdById: number;
  createdByName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorldViewStep {
  id: number;
  name: string;
  description?: string;
  stepOrder: number;
  stepType: 'QUESTION' | 'AI_SUGGESTION' | 'USER_INPUT' | 'AI_GENERATION' | 'REVIEW';
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'FAILED';
  question?: string;
  options?: string;
  userAnswer?: string;
  aiSuggestion?: string;
  prompt?: string;
  result?: string;
  worldViewBuilderId: number;
  worldViewBuilderName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWorldViewBuilderRequest {
  name: string;
  description?: string;
  novelId: number;
}

export interface UpdateStepAnswerRequest {
  answer: string;
}

export interface BuilderStatistics {
  totalBuilders: number;
  inProgressBuilders: number;
  completedBuilders: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

/**
 * 世界观构建器服务
 */
export const worldViewBuilderService = {
  /**
   * 创建世界观构建器
   */
  async createWorldViewBuilder(data: CreateWorldViewBuilderRequest): Promise<WorldViewBuilder> {
    const response = await api.post('/world-view-builder', data);
    return response as unknown as WorldViewBuilder;
  },

  /**
   * 获取世界观构建器列表
   */
  async getWorldViewBuilders(page: number = 0, size: number = 10): Promise<PageResponse<WorldViewBuilder>> {
    const response = await api.get(`/world-view-builder?page=${page}&size=${size}`);
    return response as unknown as PageResponse<WorldViewBuilder>;
  },

  /**
   * 获取世界观构建器详情
   */
  async getWorldViewBuilder(id: number): Promise<WorldViewBuilder> {
    const response = await api.get(`/world-view-builder/${id}`);
    return response as unknown as WorldViewBuilder;
  },

  /**
   * 更新世界观构建器
   */
  async updateWorldViewBuilder(id: number, data: Partial<WorldViewBuilder>): Promise<WorldViewBuilder> {
    const response = await api.put(`/world-view-builder/${id}`, data);
    return response as unknown as WorldViewBuilder;
  },

  /**
   * 删除世界观构建器
   */
  async deleteWorldViewBuilder(id: number): Promise<void> {
    await api.delete(`/world-view-builder/${id}`);
  },

  /**
   * 获取构建步骤列表
   */
  async getBuilderSteps(builderId: number): Promise<WorldViewStep[]> {
    const response = await api.get(`/world-view-builder/${builderId}/steps`);
    return response as unknown as WorldViewStep[];
  },

  /**
   * 更新步骤答案
   */
  async updateStepAnswer(stepId: number, data: UpdateStepAnswerRequest): Promise<WorldViewStep> {
    const response = await api.put(`/world-view-builder/steps/${stepId}/answer`, data);
    return response as unknown as WorldViewStep;
  },

  /**
   * 完成世界观构建
   */
  async completeWorldViewBuilder(id: number): Promise<WorldViewBuilder> {
    const response = await api.post(`/world-view-builder/${id}/complete`);
    return response as unknown as WorldViewBuilder;
  },

  /**
   * 获取构建器统计信息
   */
  async getBuilderStatistics(): Promise<BuilderStatistics> {
    const response = await api.get('/world-view-builder/statistics');
    return response as unknown as BuilderStatistics;
  },

  /**
   * 获取构建器状态枚举
   */
  async getBuilderStatuses(): Promise<string[]> {
    const response = await api.get('/world-view-builder/builder-statuses');
    return response as unknown as string[];
  },

  /**
   * 获取步骤类型枚举
   */
  async getStepTypes(): Promise<string[]> {
    const response = await api.get('/world-view-builder/step-types');
    return response as unknown as string[];
  },

  /**
   * 获取步骤状态枚举
   */
  async getStepStatuses(): Promise<string[]> {
    const response = await api.get('/world-view-builder/step-statuses');
    return response as unknown as string[];
  }
}; 