import api from './api'
import { withAIConfig } from '../utils/aiRequest'

export interface VolumeChapterOutline {
  id: number
  novelId: number
  volumeId: number
  volumeNumber?: number
  chapterInVolume?: number
  globalChapterNumber?: number
  direction?: string
  keyPlotPoints?: string
  emotionalTone?: string
  foreshadowAction?: string
  foreshadowDetail?: string
  subplot?: string
  antagonism?: string
  status?: string
  createdAt?: string
  updatedAt?: string
}


export interface ChapterOutlineResponse {
  hasOutline: boolean
  outline?: VolumeChapterOutline
  message?: string
}

/**
 * 获取单章章纲（按小说ID + 全局章节号）
 */
export const getChapterOutline = async (
  novelId: number,
  chapterNumber: number
): Promise<ChapterOutlineResponse> => {
  const res: any = await api.get('/volumes/chapter-outline', {
    params: { novelId, chapterNumber }
  })

  // 后端约定结构：{ hasOutline: boolean, outline?: VolumeChapterOutline }
  if (res && typeof res.hasOutline === 'boolean') {
    return res as ChapterOutlineResponse
  }

  // 兼容直接返回实体的情况
  if (res && typeof res === 'object' && 'id' in res) {
    return {
      hasOutline: true,
      outline: res as VolumeChapterOutline
    }
  }

  return { hasOutline: false }
}

/**
 * 获取某一卷下的章纲列表（默认仅返回摘要字段）
 */
export const getChapterOutlinesByVolume = async (
  volumeId: number,
  summary: boolean = true
): Promise<VolumeChapterOutline[]> => {
  const res: any = await api.get(`/volumes/${volumeId}/chapter-outlines`, {
    params: { summary }
  })

  if (Array.isArray(res)) {
    return res as VolumeChapterOutline[]
  }

  if (res && Array.isArray(res.data)) {
    return res.data as VolumeChapterOutline[]
  }

  return []
}

/**
 * 更新单章章纲
 */
export const updateChapterOutline = async (
  outlineId: number,
  payload: Partial<VolumeChapterOutline>
): Promise<VolumeChapterOutline> => {
  const res: any = await api.put(`/volumes/chapter-outline/${outlineId}`, payload)
  return res as VolumeChapterOutline
}

/**
 * 创建单章章纲
 */
export const createChapterOutline = async (
  payload: {
    novelId: number
    volumeId: number
    globalChapterNumber: number
    chapterInVolume?: number
    volumeNumber?: number
    direction?: string
    keyPlotPoints?: string
    foreshadowAction?: string
    foreshadowDetail?: string
  }
): Promise<VolumeChapterOutline> => {
  const res: any = await api.post('/volumes/chapter-outline', payload)
  return res as VolumeChapterOutline
}

/**
 * 为指定卷批量生成章纲
 * 实际上是“本卷章纲批量生成”，生成后需要重新拉取列表/当前章纲
 */
export const generateVolumeChapterOutlines = async (
  volumeId: number,
  count?: number
): Promise<{
  volumeId: number
  novelId: number
  count: number
}> => {
  const body: any = {}
  if (typeof count === 'number' && Number.isFinite(count)) {
    body.count = count
  }

  const requestBody = withAIConfig(body)
  const res: any = await api.post(
    `/volumes/${volumeId}/chapter-outlines/generate`,
    requestBody
  )

  // 后端返回 { volumeId, novelId, count, outlines, react_decision_log? }
  if (res && typeof res === 'object') {
    const { volumeId: vid, novelId: nid, count: c } = res
    return {
      volumeId: Number(vid ?? volumeId),
      novelId: Number(nid ?? 0),
      count: Number(c ?? 0)
    }
  }

  return {
    volumeId,
    novelId: 0,
    count: 0
  }
}
