import api from './api'

export interface Announcement {
  enabled: boolean
  title: string
  content: string
  updatedAt: string
}

const STORAGE_KEY = 'announcement_shown'

export const announcementService = {
  // 获取公告
  async getAnnouncement(): Promise<Announcement | null> {
    try {
      console.log('📡 请求公告接口: /announcement')
      const response: any = await api.get('/announcement')
      console.log('📡 公告接口响应:', response)
      // 后端返回格式: { code: 200, data: {...}, message: "success" }
      return response?.data || null
    } catch (error) {
      console.error('获取公告失败:', error)
      return null
    }
  },

  // 检查是否需要显示公告
  shouldShowAnnouncement(announcement: Announcement): boolean {
    if (!announcement.enabled) return false
    
    const stored = localStorage.getItem(STORAGE_KEY)
    if (!stored) return true
    
    try {
      const { date, updatedAt } = JSON.parse(stored)
      const today = new Date().toDateString()
      
      // 如果是今天已经显示过，且公告没有更新，则不显示
      if (date === today && updatedAt === announcement.updatedAt) {
        return false
      }
      
      // 如果公告更新了，或者是新的一天，则显示
      return true
    } catch {
      return true
    }
  },

  // 标记公告已显示
  markAsShown(announcement: Announcement): void {
    const data = {
      date: new Date().toDateString(),
      updatedAt: announcement.updatedAt
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  }
}
