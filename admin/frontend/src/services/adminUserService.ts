import request from './request'

export const adminUserService = {
  getUsers: (params?: { keyword?: string; page?: number; size?: number }) => {
    return request.get('/users', { params })
  },

  getUserById: (id: number) => {
    return request.get(`/users/${id}`)
  },

  createUser: (data: any) => {
    return request.post('/users', data)
  },

  updateUser: (id: number, data: any) => {
    return request.put(`/users/${id}`, data)
  },

  deleteUser: (id: number) => {
    return request.delete(`/users/${id}`)
  },

  getUserStats: (id: number) => {
    return request.get(`/users/${id}/stats`)
  },
}
