import request from './request'

export interface QimaoNovel {
  id: number
  novelTitle: string
  author: string
  category: string
  chapterCount: number
  status: string
  scrapedAt: string
  coverUrl?: string
  description?: string
  wordCount?: string // 字符串类型，如"123.4万字"
}

export interface QimaoStats {
  totalCount: number
  totalChapterCount: number
  todayCount: number
}

export const qimaoAPI = {
  // 获取七猫小说列表
  getQimaoNovels: (params: {
    keyword?: string
    category?: string
    status?: string
    page?: number
    size?: number
  }) => request.get('/qimao/novels', { params }),

  // 获取七猫小说详情
  getQimaoNovelById: (id: number) => request.get(`/qimao/novels/${id}`),

  // 获取七猫统计数据
  getQimaoStats: () => request.get<QimaoStats>('/qimao/stats'),
}
