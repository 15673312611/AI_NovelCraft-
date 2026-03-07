import request from './request'

export const adminLogService = {
  getLogs: (params?: { username?: string; startDate?: string; endDate?: string; page?: number; size?: number }) => {
    return request.get('/logs', { params })
  },

  getLogStats: () => {
    return request.get('/logs/stats')
  },
}
