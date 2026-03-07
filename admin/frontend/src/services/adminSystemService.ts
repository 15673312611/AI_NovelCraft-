import request from './request'

export const adminSystemService = {
  getConfig: () => {
    return request.get('/system/config')
  },

  saveConfig: (data: any) => {
    return request.post('/system/config', data)
  },
}
