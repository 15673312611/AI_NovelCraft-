import api from './api'

export interface WechatConfig {
  enabled: boolean
  mpEnabled: boolean
  mpAppId?: string
  redirectUri?: string
}

export interface WechatAuthUrl {
  authUrl: string
  state: string
  type: string
}

export const wechatAuthService = {
  /**
   * 获取微信登录配置
   */
  getConfig: async (): Promise<WechatConfig> => {
    const response: any = await api.get('/auth/wechat/config')
    return response.data
  },

  /**
   * 获取微信授权URL
   */
  getAuthUrl: async (type: 'mp' = 'mp'): Promise<WechatAuthUrl> => {
    const response: any = await api.get('/auth/wechat/auth-url', { params: { type } })
    return response.data
  },

  /**
   * 微信登录
   */
  login: async (code: string, state: string, type: 'mp' = 'mp') => {
    const response: any = await api.post('/auth/wechat/login', { code, state, type })
    return response.data
  },

  /**
   * 绑定微信
   */
  bind: async (code: string, state: string) => {
    const response: any = await api.post('/auth/wechat/bind', { code, state, type: 'mp' })
    return response.data
  },

  /**
   * 解绑微信
   */
  unbind: async () => {
    const response: any = await api.post('/auth/wechat/unbind')
    return response.data
  }
}

export default wechatAuthService
