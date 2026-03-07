import request from './request'

export const adminAITaskService = {
  getAITasks: (params?: { status?: string; page?: number; size?: number }) => {
    return request.get('/ai-tasks', { params })
  },

  getAITaskById: (id: number) => {
    return request.get(`/ai-tasks/${id}`)
  },

  retryTask: (id: number) => {
    return request.post(`/ai-tasks/${id}/retry`)
  },

  deleteTask: (id: number) => {
    return request.delete(`/ai-tasks/${id}`)
  },

  getTaskStats: () => {
    return request.get('/ai-tasks/stats')
  },
}
