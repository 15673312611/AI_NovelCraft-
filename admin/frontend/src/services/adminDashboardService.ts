import request from './request'

export const adminDashboardService = {
  getStats: () => {
    return request.get('/dashboard/stats')
  },

  getUserTrend: (days: number = 30) => {
    return request.get('/dashboard/user-trend', { params: { days } })
  },

  getAITaskStats: () => {
    return request.get('/dashboard/ai-task-stats')
  },

  getRecentTasks: (limit: number = 10) => {
    return request.get('/dashboard/recent-tasks', { params: { limit } })
  },
}
