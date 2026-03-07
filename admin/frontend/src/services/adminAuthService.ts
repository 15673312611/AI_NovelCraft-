import request from './request'

export const adminAuthService = {
  login: (username: string, password: string) => {
    return request.post('/auth/login', { username, password })
  },

  logout: () => {
    return request.post('/auth/logout')
  },

  getCurrentUser: () => {
    return request.get('/auth/current')
  },
}
