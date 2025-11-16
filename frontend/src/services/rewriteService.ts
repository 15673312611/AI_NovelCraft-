import api from './api'
import { withAIConfig } from '@/utils/aiRequest'

export interface RewriteRequest {
  content: string
  requirements?: string
  concise?: boolean
  chapterNumber?: number  // 章节号（用于获取上下文）
}

export interface RewriteResponse {
  rewrittenContent: string
}

export type StreamChunkHandler = (chunk: string) => void
export type StreamErrorHandler = (error: string) => void
export type StreamDoneHandler = () => void

const rewriteService = {
  /**
   * 流式重写章节
   * @param novelId 小说ID
   * @param req 重写请求
   * @param onChunk 接收内容块的处理器
   * @param onError 错误处理器
   * @param onDone 完成处理器
   */
  async rewriteChapterStream(
    novelId: number,
    req: RewriteRequest,
    onChunk: StreamChunkHandler,
    onError?: StreamErrorHandler,
    onDone?: StreamDoneHandler
  ): Promise<void> {
    const requestBody = withAIConfig(req)
    const token = localStorage.getItem('token')
    
    const response = await fetch(`/api/novels/${novelId}/rewrite/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify(requestBody)
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    
    if (!reader) {
      throw new Error('无法获取响应流')
    }
    
    let buffer = ''
    let accumulated = ''
    let hasReceivedContent = false
    
    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        if (onDone) {
          onDone()
        }
        break
      }
      
      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk
      
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      
      let currentEvent = ''
      
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
          continue
        }
        
        if (line.startsWith('data:')) {
          const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
          
          if (data === '[DONE]') {
            if (onDone) {
              onDone()
            }
            continue
          }
          
          // 处理错误事件
          if (currentEvent === 'error' || line.trim().startsWith('error')) {
            if (onError) {
              onError(data || line)
            }
            continue
          }
          
          // 解析JSON格式数据
          try {
            const parsed = JSON.parse(data)
            if (parsed && parsed.content) {
              if (!hasReceivedContent) {
                accumulated = ''
                hasReceivedContent = true
              }
              accumulated += parsed.content
              onChunk(parsed.content)
            } else if (parsed && typeof parsed === 'string') {
              // 如果直接是字符串内容
              if (!hasReceivedContent) {
                accumulated = ''
                hasReceivedContent = true
              }
              accumulated += parsed
              onChunk(parsed)
            }
          } catch (e) {
            // 如果不是JSON，直接作为内容（向后兼容）
            if (data && data !== '[DONE]') {
              if (!hasReceivedContent) {
                accumulated = ''
                hasReceivedContent = true
              }
              accumulated += data
              onChunk(data)
            }
          }
          
          // 重置事件类型
          currentEvent = ''
        }
      }
    }
  },

  /**
   * 非流式重写章节（保留作为备用）
   */
  async rewriteChapter(novelId: number, req: RewriteRequest): Promise<RewriteResponse> {
    const requestBody = withAIConfig(req)
    const res = await api.post(`/novels/${novelId}/rewrite`, requestBody)
    return res.data || res
  }
}

export default rewriteService


