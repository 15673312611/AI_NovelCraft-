import api from './api';

export interface ShortNovel {
  id: number;
  title: string;
  idea: string;
  storySetting?: string;
  outline?: string;
  prologue?: string;
  hooksJson?: string;
  activeStep?: string;
  workflowConfig?: string;

  targetWords: number;
  chapterCount: number;
  wordsPerChapter?: number;
  minPassScore?: number;
  enableOutlineUpdate?: boolean;

  status: string;
  currentChapter: number;
  currentRetryCount?: number;
  totalWords: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ShortChapter {
  id: number;
  chapterNumber: number;
  title: string;
  brief: string;
  lastAdjustment?: string;
  content: string;
  status: string;
  wordCount: number;
  reviewResult?: string;
  analysisResult?: string;
  generationTime?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowLog {
  id: number;
  chapterNumber: number | null;
  type: 'INFO' | 'THOUGHT' | 'ACTION' | 'REVIEW' | 'ERROR' | 'SUCCESS';
  content: string;
  createdAt: string;
}

export type WorkflowStepStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface WorkflowStep {
  key: string;
  name: string;
  status: WorkflowStepStatus;
  chapterNumber?: number | null;
  description?: string;
}

export interface WorkflowStateResponse {
  novelId: number;
  status: string;
  activeStep?: string | null;
  chapterCount?: number;
  currentChapter?: number;
  steps: WorkflowStep[];
}

export const shortStoryService = {
  // 创建
  create: async (data: any) => {
    return api.post<ShortNovel>('/short-stories', data);
  },
  
  // 列表
  list: async () => {
    return api.get<ShortNovel[]>('/short-stories');
  },
  
  // 详情
  get: async (id: number) => {
    return api.get<ShortNovel>(`/short-stories/${id}`);
  },
  
  // 章节列表
  getChapters: async (id: number) => {
    return api.get<ShortChapter[]>(`/short-stories/${id}/chapters`);
  },
  
  // 启动工作流
  start: async (id: number) => {
    return api.post(`/short-stories/${id}/start`);
  },
  
  // 暂停工作流
  pause: async (id: number) => {
    return api.post(`/short-stories/${id}/pause`);
  },
  
  // 重试章节
  retry: async (id: number, chapterNumber: number) => {
    return api.post(`/short-stories/${id}/retry/${chapterNumber}`);
  },
  
  // 获取日志
  getLogs: async (id: number, page: number = 0, size: number = 50) => {
    return api.get<WorkflowLog[]>(`/short-stories/${id}/logs?page=${page}&size=${size}`);
  },

  // 工作流状态（画布用）
  getWorkflowState: async (id: number) => {
    return api.get<WorkflowStateResponse>(`/short-stories/${id}/workflow/state`);
  },
  
  // 更新工作流配置（模型等）
  updateConfig: async (id: number, config: { modelId?: string }) => {
    return api.put(`/short-stories/${id}/config`, config);
  },
  
  // 更新章节内容
  updateChapterContent: async (novelId: number, chapterNumber: number, content: string) => {
    return api.put(`/short-stories/${novelId}/chapters/${chapterNumber}/content`, { content });
  }
};
