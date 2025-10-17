import api from './api'

export interface AITask {
  id: number
  type: string
  status: string
  title: string
  description: string
  prompt: string
  result: string
  novelId: number
  chapterId?: number
  model: string
  maxTokens: number
  temperature: number
  cost: number
  tokenCount: number
  retryCount: number
  maxRetries: number
  timeout: number
  createdAt: string
  updatedAt: string
  startedAt?: string
  completedAt?: string
  errorMessage?: string
  createdBy: number
  createdByUsername: string
  novelTitle: string
}

export interface CreateAITaskRequest {
  type: string
  title: string
  description: string
  prompt: string
  novelId: number
  chapterId?: number
  model: string
  maxTokens: number
  temperature: number
  maxRetries: number
  timeout: number
}

export interface UpdateAITaskRequest {
  title?: string
  description?: string
  prompt?: string
  model?: string
  maxTokens?: number
  temperature?: number
  maxRetries?: number
  timeout?: number
}

export interface AITaskListResponse {
  content: AITask[]
  totalElements: number
  totalPages: number
  currentPage: number
  size: number
}

export interface AITaskStatistics {
  totalTasks: number
  completedTasks: number
  failedTasks: number
  runningTasks: number
  pendingTasks: number
  totalCost: number
  averageCost: number
  successRate: number
  averageExecutionTime: number
}

export interface AIModel {
  id: string
  name: string
  description: string
  maxTokens: number
  costPer1kTokens: number
  available: boolean
}

export interface CostEstimate {
  estimatedTokens: number
  estimatedCost: number
  model: string
  maxTokens: number
}

class AITaskService {
  async getAITasks(
    page: number = 0,
    size: number = 10,
    status?: string,
    type?: string,
    novelId?: number
  ): Promise<AITaskListResponse> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString()
    })
    
    if (status) params.append('status', status)
    if (type) params.append('type', type)
    if (novelId) params.append('novelId', novelId.toString())
    
    const response = await api.get(`/ai-tasks?${params.toString()}`)
    return response as unknown as AITaskListResponse
  }

  async getAITaskById(id: number): Promise<AITask> {
    const response = await api.get(`/ai-tasks/${id}`)
    return response as unknown as AITask
  }

  async createAITask(taskData: CreateAITaskRequest): Promise<AITask> {
    const response = await api.post('/ai-tasks', taskData)
    return response as unknown as AITask
  }

  async updateAITask(id: number, taskData: UpdateAITaskRequest): Promise<AITask> {
    const response = await api.put(`/ai-tasks/${id}`, taskData)
    return response as unknown as AITask
  }

  async deleteAITask(id: number): Promise<void> {
    await api.delete(`/ai-tasks/${id}`)
  }

  async cancelAITask(id: number): Promise<void> {
    // 后端使用的是 stop 而不是 cancel
    await api.post(`/ai-tasks/${id}/stop`)
  }

  async retryAITask(id: number): Promise<void> {
    await api.post(`/ai-tasks/${id}/retry`)
  }

  async getAITaskStatistics(): Promise<AITaskStatistics> {
    const response = await api.get('/ai-tasks/statistics')
    return response as unknown as AITaskStatistics
  }

  async getAvailableModels(): Promise<AIModel[]> {
    const response = await api.get('/ai-tasks/models')
    return response as unknown as AIModel[]
  }

  async validateApiKey(): Promise<{ valid: boolean; message?: string }> {
    const response = await api.post('/ai-tasks/validate-api-key')
    return response as unknown as { valid: boolean; message?: string }
  }

  async estimateCost(
    prompt: string,
    model: string,
    maxTokens: number
  ): Promise<CostEstimate> {
    const response = await api.post('/ai-tasks/estimate-cost', {
      prompt,
      model,
      maxTokens
    })
    return response as unknown as CostEstimate
  }

  async getTaskTypes(): Promise<string[]> {
    const response = await api.get('/ai-tasks/types')
    return response as unknown as string[]
  }

  async getTaskStatuses(): Promise<string[]> {
    const response = await api.get('/ai-tasks/statuses')
    return response as unknown as string[]
  }

  async searchAITasks(query: string): Promise<AITask[]> {
    const response = await api.get(`/ai-tasks/search?query=${encodeURIComponent(query)}`)
    return response as unknown as AITask[]
  }

  async getTasksByNovel(novelId: number): Promise<AITask[]> {
    const response = await api.get(`/novels/${novelId}/ai-tasks`)
    return response as unknown as AITask[]
  }

  async getTasksByChapter(chapterId: number): Promise<AITask[]> {
    const response = await api.get(`/chapters/${chapterId}/ai-tasks`)
    return response as unknown as AITask[]
  }

  /**
   * 获取任务进度
   */
  async getTaskProgress(taskId: number): Promise<any> {
    const response = await api.get(`/ai-tasks/${taskId}/progress`);
    return response;
  }

  /**
   * 启动轮询任务进度
   * @param taskId 任务ID
   * @param onProgress 进度更新回调
   * @param onComplete 任务完成回调
   * @param onError 错误回调
   * @param interval 轮询间隔（毫秒），默认2秒
   * @returns 返回停止轮询的函数
   */
  startPolling(
    taskId: number,
    onProgress: (progress: any) => void,
    onComplete: (task: AITask) => void,
    onError: (error: string) => void,
    interval: number = 2000
  ): () => void {
    let isPolling = true;
    let timeoutId: NodeJS.Timeout;

    const poll = async () => {
      if (!isPolling) return;

      try {
        const progress = await this.getTaskProgress(taskId);
        onProgress(progress);

        if (progress.status === 'COMPLETED') {
          isPolling = false;
          const task = await this.getAITaskById(taskId);
          onComplete(task);
        } else if (progress.status === 'FAILED' || progress.status === 'CANCELLED') {
          isPolling = false;
          onError(progress.error || `任务${progress.status === 'FAILED' ? '失败' : '已取消'}`);
        } else {
          // 继续轮询
          timeoutId = setTimeout(poll, interval);
        }
      } catch (error: any) {
        console.error('轮询任务进度失败:', error);
        
        // 如果是网络错误，继续尝试
        if (error.message?.includes('网络') || error.message?.includes('Network')) {
          timeoutId = setTimeout(poll, interval * 2); // 网络错误时延长间隔
        } else {
          isPolling = false;
          onError(error.message || '获取任务进度失败');
        }
      }
    };

    // 立即开始第一次轮询
    poll();

    // 返回停止轮询的函数
    return () => {
      isPolling = false;
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    };
  }

  /**
   * 从本地存储中恢复进行中的任务
   * 用于页面刷新后恢复任务状态
   */
  getStoredTasks(): Array<{taskId: number, type: string, novelId: number}> {
    try {
      const stored = localStorage.getItem('aiTasks');
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  }

  /**
   * 将任务信息存储到本地
   */
  storeTask(taskId: number, type: string, novelId: number): void {
    try {
      const tasks = this.getStoredTasks();
      const existingIndex = tasks.findIndex(t => t.taskId === taskId);
      
      if (existingIndex >= 0) {
        tasks[existingIndex] = { taskId, type, novelId };
      } else {
        tasks.push({ taskId, type, novelId });
      }
      
      localStorage.setItem('aiTasks', JSON.stringify(tasks));
    } catch (error) {
      console.warn('存储任务信息失败:', error);
    }
  }

  /**
   * 从本地存储中移除任务
   */
  removeStoredTask(taskId: number): void {
    try {
      const tasks = this.getStoredTasks();
      const filtered = tasks.filter(t => t.taskId !== taskId);
      localStorage.setItem('aiTasks', JSON.stringify(filtered));
    } catch (error) {
      console.warn('移除任务信息失败:', error);
    }
  }

  /**
   * 清理已完成或失败的任务
   */
  async cleanupStoredTasks(): Promise<void> {
    const tasks = this.getStoredTasks();
    const activeTasks: Array<{taskId: number, type: string, novelId: number}> = [];

    for (const task of tasks) {
      try {
        const taskDetail = await this.getAITaskById(task.taskId);
        if (taskDetail.status === 'RUNNING' || taskDetail.status === 'PENDING') {
          activeTasks.push(task);
        }
      } catch {
        // 任务可能已删除，忽略错误
      }
    }

    localStorage.setItem('aiTasks', JSON.stringify(activeTasks));
  }
}

export const aiTaskService = new AITaskService()
export default aiTaskService 