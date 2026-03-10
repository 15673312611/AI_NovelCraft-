import api from './api';

export interface VideoScript {
  id: number;
  title: string;
  idea: string;

  mode: 'HALF_NARRATION' | 'PURE_NARRATION' | string;

  /** episode script output format */
  scriptFormat?: 'SCENE' | 'STORYBOARD' | 'NARRATION' | string;

  /** per-episode settings */
  targetSeconds: number;
  sceneCount: number;

  /** series workflow outputs */
  scriptSetting?: string;
  outline?: string;
  hooksJson?: string;
  prologue?: string;

  /** legacy single-episode outputs (kept for backward compatibility) */
  storyboard?: string;
  finalScript?: string;

  /** multi-episode controls */
  episodeCount?: number;
  currentEpisode?: number;
  currentRetryCount?: number;
  maxRetryPerEpisode?: number;
  minPassScore?: number;
  enableOutlineUpdate?: boolean;

  activeStep?: string;
  workflowConfig?: string;

  status: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface VideoScriptEpisode {
  id: number;
  scriptId: number;
  episodeNumber: number;
  title: string;
  brief?: string;
  lastAdjustment?: string;
  storyboard?: string;
  content?: string;
  wordCount?: number;
  status: string;
  reviewResult?: string;
  analysisResult?: string;
  generationTime?: number;
  createdAt: string;
  updatedAt: string;
}

export interface VideoScriptLog {
  id: number;
  type: 'INFO' | 'THOUGHT' | 'ACTION' | 'REVIEW' | 'ERROR' | 'SUCCESS' | string;
  content: string;
  episodeNumber?: number | null;
  createdAt: string;
}

export type WorkflowStepStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface WorkflowStep {
  key: string;
  name: string;
  status: WorkflowStepStatus;
  episodeNumber?: number | null;
  description?: string;
}

export interface VideoScriptWorkflowStateResponse {
  scriptId: number;
  status: string;
  activeStep?: string | null;
  episodeCount?: number;
  currentEpisode?: number;
  steps: WorkflowStep[];
}

export const videoScriptService = {
  create: async (data: any) => {
    return api.post<VideoScript>('/video-scripts', data);
  },

  list: async () => {
    return api.get<VideoScript[]>('/video-scripts');
  },

  get: async (id: number) => {
    return api.get<VideoScript>(`/video-scripts/${id}`);
  },

  start: async (id: number) => {
    return api.post(`/video-scripts/${id}/start`);
  },

  pause: async (id: number) => {
    return api.post(`/video-scripts/${id}/pause`);
  },

  getLogs: async (id: number, params?: { episodeNumber?: number; page?: number; size?: number }) => {
    const page = params?.page ?? 0;
    const size = params?.size ?? 50;
    const episodeNumber = params?.episodeNumber;
    const epPart = typeof episodeNumber === 'number' ? `&episodeNumber=${episodeNumber}` : '';
    return api.get<VideoScriptLog[]>(`/video-scripts/${id}/logs?page=${page}&size=${size}${epPart}`);
  },

  getEpisodes: async (id: number) => {
    return api.get<VideoScriptEpisode[]>(`/video-scripts/${id}/episodes`);
  },

  updateEpisodeContent: async (id: number, episodeNumber: number, content: string) => {
    return api.put<VideoScriptEpisode>(`/video-scripts/${id}/episodes/${episodeNumber}/content`, { content });
  },

  retry: async (id: number, episodeNumber: number) => {
    return api.post(`/video-scripts/${id}/retry/${episodeNumber}`);
  },

  getWorkflowState: async (id: number) => {
    return api.get<VideoScriptWorkflowStateResponse>(`/video-scripts/${id}/workflow/state`);
  },

  updateConfig: async (id: number, config: { modelId?: string; scriptFormat?: 'SCENE' | 'STORYBOARD' | 'NARRATION' | string }) => {
    return api.put(`/video-scripts/${id}/config`, config);
  },
};
