import request from './request'

export interface WechatConfig {
  // Open Platform (PC QR code)
  openEnabled: boolean
  openAppId: string
  openAppSecret: string
  // MP (H5 web auth)
  mpEnabled: boolean
  mpAppId: string
  mpAppSecret: string
  // Common
  redirectUri: string
}

export interface TestResult {
  valid: boolean
  type: string
  message: string
}

export const adminWechatConfigService = {
  /**
   * Get wechat login config
   */
  getConfig: async (): Promise<WechatConfig> => {
    const response = await request.get('/admin/wechat-config')
    return response.data
  },

  /**
   * Update wechat login config
   */
  updateConfig: async (config: Partial<WechatConfig>): Promise<void> => {
    await request.put('/admin/wechat-config', config)
  },

  /**
   * Test config
   */
  testConfig: async (type: 'open' | 'mp' = 'open'): Promise<TestResult> => {
    const response = await request.post('/admin/wechat-config/test', { type })
    return response.data
  }
}

export default adminWechatConfigService
