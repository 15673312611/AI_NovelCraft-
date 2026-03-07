import request from './request'

export const adminNovelService = {
  getNovels: (params?: { keyword?: string; page?: number; size?: number }) => {
    return request.get('/novels', { params })
  },

  getNovelById: (id: number) => {
    return request.get(`/novels/${id}`)
  },

  deleteNovel: (id: number) => {
    return request.delete(`/novels/${id}`)
  },

  getNovelStats: (id: number) => {
    return request.get(`/novels/${id}/stats`)
  },
}
