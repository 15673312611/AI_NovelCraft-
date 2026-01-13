import api from './api'

export interface Chapter {
  id: number
  novelId: number
  chapterNumber: number | null
  title: string
  content: string
  wordCount: number
  status: string
  isPublic?: boolean | null
  createdAt?: string
  updatedAt?: string
  subtitle?: string | null
  summary?: string | null
  notes?: string | null
}

const normalizeChapter = (raw: any): Chapter => {
  if (!raw) {
    throw new Error('章节数据缺失')
  }

  const content = raw.content ?? ''

  return {
    id: Number(raw.id),
    novelId: Number(raw.novelId),
    chapterNumber: raw.chapterNumber ?? null,
    title: raw.title ?? '',
    content,
    wordCount: raw.wordCount ?? (content ? String(content).replace(/\s+/g, '').length : 0),
    status: raw.status ?? 'DRAFT',
    isPublic: raw.isPublic ?? null,
    createdAt: raw.createdAt ?? raw.createTime ?? undefined,
    updatedAt: raw.updatedAt ?? raw.updateTime ?? undefined,
    subtitle: raw.subtitle ?? null,
    summary: raw.summary ?? null,
    notes: raw.notes ?? null,
  }
}

const extractData = <T>(response: any, fallback: T): T => {
  if (response === undefined || response === null) {
    return fallback
  }

  if (Array.isArray(response)) {
    return response as unknown as T
  }

  if (typeof response === 'object') {
    if ('data' in response && response.data !== undefined) {
      return response.data as T
    }
    if ('content' in response && response.content !== undefined) {
      return response.content as T
    }
  }

  return response as T
}

export const getChaptersByNovel = async (novelId: number): Promise<Chapter[]> => {
  const response = await api.get(`/chapters/novel/${novelId}?summary=true`)
  const list = extractData<any[]>(response, [])
  return list.map(normalizeChapter)
}

export const getChaptersWithContentByNovel = async (novelId: number): Promise<Chapter[]> => {
  const response = await api.get(`/chapters/novel/${novelId}?summary=false`)
  const list = extractData<any[]>(response, [])
  return list.map(normalizeChapter)
}

export const getChapterById = async (chapterId: number): Promise<Chapter> => {
  const response = await api.get(`/chapters/${chapterId}`)
  return normalizeChapter(response)
}

export const createChapter = async (
  novelId: number,
  payload: Partial<Chapter>
): Promise<Chapter> => {
  const response = await api.post('/chapters', {
    novelId,
    ...payload,
  })
  return normalizeChapter(response)
}

export const updateChapter = async (
  chapterId: number,
  payload: Partial<Chapter>
): Promise<Chapter> => {
  const response = await api.put(`/chapters/${chapterId}`, payload)
  return normalizeChapter(response)
}

export const autoSaveChapter = async (chapterId: number, content: string): Promise<void> => {
  await api.put(`/chapters/${chapterId}`, { content })
}

export const deleteChapter = async (chapterId: number): Promise<void> => {
  await api.delete(`/chapters/${chapterId}`)
}

