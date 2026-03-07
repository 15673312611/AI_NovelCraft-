import React, { useState, useRef, useEffect } from 'react'
import { Button, Input, Select, Spin, message, Checkbox, Tag } from 'antd'
import {
  SendOutlined,
  ClearOutlined,
  FileTextOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons'
import type { NovelDocument } from '@/services/documentService'
import { checkAIConfig, withAIConfig } from '@/utils/aiRequest'
import api from '@/services/api'
import './AIChatAssistant.css'

const { TextArea } = Input

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

export interface AIChatAssistantProps {
  // 当前编辑的文档/章节
  currentDocument?: {
    id: number
    title: string
    content: string
  } | null
  
  // 可选的关联文档列表
  availableDocuments: NovelDocument[]
  
  // 小说ID（用于API调用）
  novelId: number
  
  // 模型选择
  selectedModel?: string
  onModelChange?: (model: string) => void
}

const AIChatAssistant: React.FC<AIChatAssistantProps> = ({
  currentDocument,
  availableDocuments,
  novelId,
  selectedModel = 'gemini-3-pro-preview',
  onModelChange,
}) => {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [includeCurrentDoc, setIncludeCurrentDoc] = useState(true)
  const [selectedDocIds, setSelectedDocIds] = useState<number[]>([])
  const [showDocSelector, setShowDocSelector] = useState(false)
  
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const abortControllerRef = useRef<AbortController | null>(null)

  // 自动滚动到底部
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  // 发送消息
  const handleSendMessage = async () => {
    if (!inputValue.trim() || isGenerating) return

    // 检查AI配置
    const configOk = checkAIConfig()
    if (!configOk) {
      message.error('AI服务不可用，请检查系统配置')
      return
    }

    const userMessage: ChatMessage = {
      role: 'user',
      content: inputValue.trim(),
      timestamp: new Date(),
    }

    setMessages((prev) => [...prev, userMessage])
    setInputValue('')
    setIsGenerating(true)

    // 准备上下文
    let context = ''
    
    if (includeCurrentDoc && currentDocument?.content) {
      context += `\n【当前文章】\n标题：${currentDocument.title}\n内容：\n${currentDocument.content}\n\n`
    }

    if (selectedDocIds.length > 0) {
      const selectedDocs = availableDocuments.filter(doc => 
        selectedDocIds.includes(doc.id)
      )
      selectedDocs.forEach(doc => {
        context += `\n【参考文档】\n标题：${doc.title}\n内容：\n${doc.content || '（无内容）'}\n\n`
      })
    }

    try {
      const abortController = new AbortController()
      abortControllerRef.current = abortController
      
      const response = await withAIConfig(() =>
        fetch(`${api.defaults.baseURL}/ai/chat/free`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...api.defaults.headers.common,
          },
          body: JSON.stringify({
            novelId,
            userMessage: userMessage.content,
            context: context || undefined,
            model: selectedModel,
            conversationHistory: messages.slice(-6).map(msg => ({
              role: msg.role,
              content: msg.content,
            })),
          }),
          signal: abortController.signal,
        })
      )

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.message || `HTTP ${response.status}`)
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (!reader) {
        throw new Error('无法读取响应流')
      }

      let assistantMessage = ''
      const assistantMsg: ChatMessage = {
        role: 'assistant',
        content: '',
        timestamp: new Date(),
      }

      setMessages((prev) => [...prev, assistantMsg])

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const chunk = decoder.decode(value, { stream: true })
        const lines = chunk.split('\n')

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6).trim()
            if (data === '[DONE]') continue
            
            try {
              const parsed = JSON.parse(data)
              if (parsed.content) {
                assistantMessage += parsed.content
                setMessages((prev) => {
                  const newMessages = [...prev]
                  newMessages[newMessages.length - 1] = {
                    ...assistantMsg,
                    content: assistantMessage,
                  }
                  return newMessages
                })
              }
            } catch (e) {
              console.warn('解析SSE数据失败:', e)
            }
          }
        }
      }

      if (!assistantMessage) {
        throw new Error('AI未返回任何内容')
      }

    } catch (error: any) {
      console.error('AI对话失败:', error)
      if (error.name !== 'AbortError') {
        message.error(`对话失败: ${error.message}`)
        // 移除未完成的助手消息
        setMessages((prev) => prev.filter(msg => msg.content))
      }
    } finally {
      setIsGenerating(false)
      abortControllerRef.current = null
    }
  }

  // 清空对话历史
  const handleClearMessages = () => {
    setMessages([])
    message.success('已清空对话历史')
  }

  // 停止生成
  const handleStopGeneration = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      setIsGenerating(false)
      message.info('已停止生成')
    }
  }

  return (
    <div className="ai-chat-assistant">
      {/* 顶部工具栏 */}
      <div className="ai-chat-header">
        <div className="ai-chat-title">
          <RobotOutlined style={{ fontSize: 18, color: '#1890ff' }} />
          <span>AI对话助手</span>
        </div>
        <div className="ai-chat-actions">
          <Button
            size="small"
            icon={<ClearOutlined />}
            onClick={handleClearMessages}
            disabled={messages.length === 0}
          >
            清空
          </Button>
        </div>
      </div>

      {/* 上下文选择区域 */}
      <div className="ai-chat-context">
        <div className="context-header">
          <FileTextOutlined style={{ marginRight: 6 }} />
          <span>参考内容</span>
        </div>
        
        <div className="context-options">
          {currentDocument && (
            <Checkbox
              checked={includeCurrentDoc}
              onChange={(e) => setIncludeCurrentDoc(e.target.checked)}
            >
              <Tag color="blue" style={{ margin: 0 }}>
                当前文章
              </Tag>
              <span style={{ marginLeft: 6, fontSize: 12, color: '#666' }}>
                {currentDocument.title}
              </span>
            </Checkbox>
          )}
          
          <Button
            size="small"
            type="link"
            onClick={() => setShowDocSelector(!showDocSelector)}
            style={{ padding: '0 4px' }}
          >
            {showDocSelector ? '收起' : '选择其他文档'}
          </Button>
        </div>

        {showDocSelector && (
          <div className="context-selector">
            <Select
              mode="multiple"
              placeholder="选择要关联的文档"
              style={{ width: '100%' }}
              value={selectedDocIds}
              onChange={setSelectedDocIds}
              maxTagCount={2}
              size="small"
            >
              {availableDocuments.map(doc => (
                <Select.Option key={doc.id} value={doc.id}>
                  {doc.title}
                </Select.Option>
              ))}
            </Select>
          </div>
        )}
      </div>

      {/* 对话消息区域 */}
      <div className="ai-chat-messages">
        {messages.length === 0 ? (
          <div className="ai-chat-empty">
            <RobotOutlined style={{ fontSize: 48, color: '#d9d9d9', marginBottom: 16 }} />
            <div style={{ color: '#999', fontSize: 14 }}>
              开始与AI自由对话吧！
            </div>
            <div style={{ color: '#bbb', fontSize: 12, marginTop: 8 }}>
              你可以询问任何问题，AI会根据你选择的参考内容给出回答
            </div>
          </div>
        ) : (
          messages.map((msg, index) => (
            <div
              key={index}
              className={`chat-message ${msg.role === 'user' ? 'user-message' : 'assistant-message'}`}
            >
              <div className="message-avatar">
                {msg.role === 'user' ? (
                  <UserOutlined />
                ) : (
                  <RobotOutlined />
                )}
              </div>
              <div className="message-content">
                <div className="message-text">
                  {msg.content}
                </div>
                <div className="message-time">
                  {msg.timestamp.toLocaleTimeString('zh-CN', {
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </div>
              </div>
            </div>
          ))
        )}
        {isGenerating && (
          <div className="chat-message assistant-message">
            <div className="message-avatar">
              <RobotOutlined />
            </div>
            <div className="message-content">
              <div className="message-text">
                <Spin size="small" />
                <span style={{ marginLeft: 12, color: '#999' }}>AI正在思考中...</span>
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* 输入区域 */}
      <div className="ai-chat-input">
        <TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault()
              handleSendMessage()
            }
          }}
          placeholder="输入你想问的问题... (Shift+Enter换行)"
          autoSize={{ minRows: 2, maxRows: 6 }}
          disabled={isGenerating}
        />
        <div className="input-actions">
          <div className="input-hint">
            <span style={{ fontSize: 12, color: '#999' }}>
              {isGenerating ? '生成中...' : 'Enter发送 · Shift+Enter换行'}
            </span>
          </div>
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={isGenerating ? handleStopGeneration : handleSendMessage}
            disabled={!inputValue.trim() && !isGenerating}
            danger={isGenerating}
          >
            {isGenerating ? '停止' : '发送'}
          </Button>
        </div>
      </div>
    </div>
  )
}

export default AIChatAssistant
