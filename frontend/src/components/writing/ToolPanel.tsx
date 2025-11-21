import React, { useEffect, useState } from 'react'
import { Button, Checkbox, Input, Modal, Radio, Space, message } from 'antd'
import {
  CopyOutlined,
  SwapOutlined,
  LinkOutlined,
  FileTextOutlined,
  SearchOutlined,
  FolderOpenOutlined,
  BgColorsOutlined,
} from '@ant-design/icons'
import type { ReferenceFile } from '@/services/referenceFileService'
import type { NovelDocument } from '@/services/documentService'
import type { NovelFolder } from '@/services/folderService'
import { getWritingStyleTemplates, type PromptTemplate } from '@/services/promptTemplateService'
import type { AiGenerator } from '@/services/aiGeneratorService'
import './ToolPanel.css'

const { TextArea } = Input

export interface SimpleAIHistoryItem {
  id: number
  content: string
  createdAt: string
}

export interface ToolPanelProps {
  // 基本状态
  isGenerating: boolean
  thinkingDots?: number

  // 模板 / 写作风格
  writingStyleId?: number | null
  onWritingStyleChange?: (styleId: number | null) => void

  // 参考资料（目前未在面板内展示，但保留接口以兼容调用方）
  referenceFiles?: ReferenceFile[]
  onUploadReferenceFile?: (file: File) => Promise<void>
  onDeleteReferenceFile?: (id: number) => Promise<void>
  onSelectReferenceFiles?: (ids: number[]) => void
  selectedReferenceFileIds?: number[]

  // 关联内容（文档）
  linkedDocuments: NovelDocument[]
  onSelectLinkedDocuments?: (ids: number[]) => void
  selectedLinkedDocumentIds: number[]

  // AI 输入 / 输出
  aiInputValue: string
  onChangeAIInput: (value: string) => void
  onSendAIRequest: () => void
  aiOutput: string
  generationPhases?: string[]
  hasContentStarted?: boolean
  onCopyAIOutput?: () => void
  onReplaceWithAIOutput?: () => void

  // 文档结构（用于关联内容弹窗）
  folders?: NovelFolder[]
  documentsMap?: Record<number, NovelDocument[]>

  // 章纲入口
  onShowChapterOutline?: () => void

  // 可选：AI 历史与生成器（目前仅保留接口，不在 UI 中强制展示）
  aiHistory?: SimpleAIHistoryItem[]
  onClearAIHistory?: () => void
  generators?: AiGenerator[]
  generatorId?: number | null
  onGeneratorChange?: (id: number | null) => void

  // 额外上下文（目前不在本组件内直接使用）
  novelId?: number
  currentChapterNumber?: number | null
  currentVolumeId?: number | null
  currentVolumeNumber?: number | null

  // 搜索结果（保留接口以便后续扩展）
  searchResults?: NovelDocument[]
  onSelectSearchResult?: (document: NovelDocument) => void
}

const ToolPanel: React.FC<ToolPanelProps> = ({
  isGenerating,
  writingStyleId,
  onWritingStyleChange,
  linkedDocuments,
  onSelectLinkedDocuments,
  selectedLinkedDocumentIds,
  aiInputValue,
  onChangeAIInput,
  onSendAIRequest,
  aiOutput,
  generationPhases = [],
  hasContentStarted = false,
  onCopyAIOutput,
  onReplaceWithAIOutput,
  folders = [],
  documentsMap = {},
  onShowChapterOutline,
  aiHistory = [],
  onClearAIHistory,
}) => {
  const [isLinkedModalVisible, setIsLinkedModalVisible] = useState(false)
  const [isWritingStyleModalVisible, setIsWritingStyleModalVisible] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [defaultContextSize, setDefaultContextSize] = useState({ summaries: 30, fullTexts: 3 })
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null)
  const [writingStyles, setWritingStyles] = useState<PromptTemplate[]>([])
  const [selectedWritingStyleId, setSelectedWritingStyleId] = useState<number | null>(writingStyleId ?? null)

  // 同步外部写作风格 ID
  useEffect(() => {
    setSelectedWritingStyleId(writingStyleId ?? null)
  }, [writingStyleId])

  // 加载写作模板（写作风格）
  useEffect(() => {
    const loadWritingStyles = async () => {
      try {
        const styles = await getWritingStyleTemplates()
        setWritingStyles(styles)
      } catch (error) {
        console.error('加载写作模板失败:', error)
      }
    }
    loadWritingStyles()
  }, [])

  const handleLinkedDocumentToggle = (checked: boolean, id: number) => {
    const current = new Set(selectedLinkedDocumentIds)
    if (checked) {
      current.add(id)
    } else {
      current.delete(id)
    }
    onSelectLinkedDocuments?.(Array.from(current))
  }

  // 当前文件夹下的文档（未选文件夹则为全部文档）
  const getCurrentFolderDocuments = () => {
    if (!documentsMap) return []
    if (selectedFolderId === null) {
      return Object.values(documentsMap).flat()
    }
    return documentsMap[selectedFolderId] || []
  }

  // 过滤文档（按标题和内容简单搜索）
  const filteredDocuments = getCurrentFolderDocuments().filter((doc) => {
    if (!searchKeyword.trim()) return true
    const kw = searchKeyword.toLowerCase()
    return (
      (doc.title || '').toLowerCase().includes(kw) ||
      (doc.content || '').toLowerCase().includes(kw)
    )
  })

  const wordCount = aiOutput.replace(/\s+/g, '').length

  const allDocumentsCount = Object.values(documentsMap).reduce(
    (acc, docs) => acc + (docs?.length || 0),
    0
  )

  return (
    <div className="tool-panel-container">
      {/* <div className="panel-header">
        <div className={`panel-status ${isGenerating ? 'active' : ''}`}>
          <span className="status-dot" />
          <span>{isGenerating ? '生成中' : '就绪'}</span>
        </div>
      </div> */}

      {/* AI 对话区域 */}
      <div className="ai-chat-area">
        {/* 历史生成入口 */}
        {aiHistory.length > 0 && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 8,
            }}
          >
            <div style={{ fontSize: 13, color: '#595959' }}>
              历史生成（{aiHistory.length}）
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                type="link"
                size="small"
                onClick={() => {
                  if (aiHistory.length === 0) return
                  const latest = aiHistory[0]
                  navigator.clipboard.writeText(latest.content)
                  message.success('已复制最新一次生成内容')
                }}
              >
                复制最新
              </Button>
              {onClearAIHistory && (
                <Button type="link" size="small" danger onClick={onClearAIHistory}>
                  清空历史
                </Button>
              )}
            </div>
          </div>
        )}

        <div className="ai-chat-history">
          {/* AI生成中动画（仅在未开始内容时显示） */}
          {isGenerating && !hasContentStarted ? (
            <div className="ai-generating-box">
              <div className="generating-spinner">
                <div className="spinner-outer"></div>
                <div className="spinner-middle"></div>
                <div className="spinner-inner"></div>
              </div>
              <div className="generating-message">
                <span className="message-text">AI 生成中</span>
                <span className="message-dots">
                  <i></i><i></i><i></i>
                </span>
              </div>
            </div>
          ) : null}

          {/* AI 生成内容 */}
          {hasContentStarted ? (
            <div className="ai-response-box">
              <div className="ai-response-header">
                <div className="ai-response-info">
                  <span className="ai-label" style={{
                    color: isGenerating ? '#595959' : '#262626',
                    fontWeight: 600,
                  }}>
                    {isGenerating ? 'AI 生成中...' : '生成结果'}
                  </span>
                  <span className="ai-word-count" style={{
                    background: '#f0f2f5',
                    padding: '4px 12px',
                    borderRadius: '12px',
                    fontSize: '12px',
                    fontWeight: 500,
                    color: '#595959',
                  }}>{wordCount} 字</span>
                </div>
                <div className="ai-response-actions">
                  <Button
                    type="text"
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={onCopyAIOutput}
                    disabled={isGenerating}
                    style={{
                      borderRadius: '8px',
                      fontWeight: 500,
                    }}
                  >
                    复制
                  </Button>
                  <Button
                    type="text"
                    size="small"
                    icon={<SwapOutlined />}
                    onClick={onReplaceWithAIOutput}
                    disabled={isGenerating}
                    style={{
                      borderRadius: '8px',
                      fontWeight: 500,
                    }}
                  >
                    替换正文
                  </Button>
                </div>
              </div>
              <div className="ai-response-content">
                <div className="ai-text-display" style={{
                  lineHeight: '1.8',
                  fontSize: '14px',
                }}>{aiOutput || '正在接收内容...'}</div>
              </div>
            </div>
          ) : !isGenerating && !aiOutput ? (
            <div className="ai-chat-empty" style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              minHeight: '300px',
              padding: '40px 20px',
            }}>
              <div style={{
                width: '80px',
                height: '80px',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                borderRadius: '20px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '24px',
                boxShadow: '0 8px 24px rgba(102, 126, 234, 0.25)',
              }}>
                <svg viewBox="0 0 100 100" style={{ width: '50px', height: '50px' }}>
                  <defs>
                    <linearGradient id="iconGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" style={{ stopColor: '#ffffff', stopOpacity: 1 }} />
                      <stop offset="100%" style={{ stopColor: '#ffffff', stopOpacity: 0.8 }} />
                    </linearGradient>
                  </defs>
                  <circle cx="50" cy="50" r="35" fill="none" stroke="url(#iconGradient)" strokeWidth="3" />
                  <path d="M35 40 L50 25 L65 40" fill="none" stroke="url(#iconGradient)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M35 60 L50 75 L65 60" fill="none" stroke="url(#iconGradient)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>
              <div style={{
                fontSize: '18px',
                fontWeight: 600,
                color: '#262626',
                marginBottom: '8px',
              }}>AI 写作助手</div>
              <div style={{
                fontSize: '14px',
                color: '#8c8c8c',
                textAlign: 'center',
                lineHeight: '1.6',
              }}>
                输入你的构思，让 AI 帮你创作精彩内容
              </div>
            </div>
          ) : null}
        </div>
      </div>

      {/* 底部输入区域 */}
      <div className="ai-input-section">
        {/* 顶部操作（在输入框上方） */}
        <div className="top-actions">
          <div className="top-actions-left">
            <button
              onClick={() => setIsLinkedModalVisible(true)}
              className="custom-toolbar-btn"
            >
              <span className="btn-icon">
                <LinkOutlined />
              </span>
              <span className="btn-text">
                关联内容
                {selectedLinkedDocumentIds.length > 0 &&
                  ` (${selectedLinkedDocumentIds.length})`}
              </span>
            </button>
            {onShowChapterOutline && (
              <button onClick={onShowChapterOutline} className="custom-toolbar-btn">
                <span className="btn-icon">
                  <FileTextOutlined />
                </span>
                <span className="btn-text">章纲</span>
              </button>
            )}
          </div>
        </div>

        {/* 输入框 */}
        <div className="ai-input-wrapper">
          <TextArea
            value={aiInputValue}
            onChange={(e: any) => onChangeAIInput(e.target.value)}
            placeholder="请输入你的构思或提示，例如：根据本章大纲继续生成故事"
            autoSize={{ minRows: 2, maxRows: 4 }}
            className="ai-input-textarea"
          />
        </div>

        {/* 底部工具栏（输入框下方：模板 / 生成） */}
        <div className="ai-toolbar">
          <div className="toolbar-left">
            <button
              onClick={() => setIsWritingStyleModalVisible(true)}
              className="custom-toolbar-btn writing-style-btn"
            >
              <span className="btn-icon">
                <BgColorsOutlined />
              </span>
              <span className="btn-text">
                {selectedWritingStyleId
                  ? writingStyles.find((s) => s.id === selectedWritingStyleId)?.name ||
                    '选择模板'
                  : '选择模板'}
              </span>
            </button>
          </div>
          <div className="toolbar-right">
            <Button
              type="primary"
              onClick={onSendAIRequest}
              loading={isGenerating}
              className="send-button"
              style={{
                background: isGenerating ? '#d9d9d9' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                border: 'none',
                borderRadius: '8px',
                height: '40px',
                padding: '0 24px',
                fontSize: '15px',
                fontWeight: 600,
                boxShadow: isGenerating ? 'none' : '0 4px 12px rgba(102, 126, 234, 0.3)',
                transition: 'all 0.3s ease',
              }}
            >
              {isGenerating ? 'AI 生成中...' : '生成'}
            </Button>
          </div>
        </div>
      </div>

      {/* 关联内容弹窗 */}
      <Modal
        title="关联内容"
        open={isLinkedModalVisible}
        onCancel={() => {
          setIsLinkedModalVisible(false)
          setSearchKeyword('')
          setSelectedFolderId(null)
        }}
        footer={null}
        width={900}
        className="linked-content-modal"
      >
        <div className="linked-modal-content">
          {/* 默认上下文说明 */}
          <div className="default-context-config">
            <div className="config-title">默认章节上下文设置</div>
            <div className="config-controls">
              <div className="config-item">
                <span>最近章节概要数量</span>
                <Input
                  type="number"
                  value={defaultContextSize.summaries}
                  onChange={(e) =>
                    setDefaultContextSize({
                      ...defaultContextSize,
                      summaries: Number.parseInt(e.target.value) || 0,
                    })
                  }
                  style={{ width: '80px' }}
                  min={0}
                  max={100}
                />
                <span> 章</span>
              </div>
              <div className="config-item">
                <span>最近章节正文数量</span>
                <Input
                  type="number"
                  value={defaultContextSize.fullTexts}
                  onChange={(e) =>
                    setDefaultContextSize({
                      ...defaultContextSize,
                      fullTexts: Number.parseInt(e.target.value) || 0,
                    })
                  }
                  style={{ width: '80px' }}
                  min={0}
                  max={20}
                />
                <span> 章</span>
              </div>
            </div>
            <div className="config-hint">
              系统会自动携带最近 {defaultContextSize.summaries} 章概要
              + {defaultContextSize.fullTexts} 章正文作为默认上下文，你可以在下方额外勾选需要重点参考的文档。
            </div>
          </div>

          {/* 左侧文件夹 + 右侧文档列表 */}
          <div className="modal-main-content">
            {/* 文件夹侧栏 */}
            <div className="folders-sidebar">
              <div className="sidebar-title">文档分类</div>
              <div
                className={`folder-item ${selectedFolderId === null ? 'active' : ''}`}
                onClick={() => setSelectedFolderId(null)}
              >
                <FolderOpenOutlined className="folder-icon" />
                <span className="folder-name">全部文档</span>
                <span className="folder-count">({allDocumentsCount})</span>
              </div>
              {folders.map((folder) => (
                <div
                  key={folder.id}
                  className={`folder-item ${selectedFolderId === folder.id ? 'active' : ''}`}
                  onClick={() => setSelectedFolderId(folder.id)}
                >
                  <FolderOpenOutlined className="folder-icon" />
                  <span className="folder-name">{folder.folderName}</span>
                  <span className="folder-count">
                    ({documentsMap[folder.id]?.length || 0})
                  </span>
                </div>
              ))}
            </div>

            {/* 文档列表 */}
            <div className="documents-section">
              {/* 搜索框 */}
              <div className="search-box">
                <Input
                  placeholder="按标题或内容搜索..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  allowClear
                  prefix={<SearchOutlined />}
                />
              </div>

              {/* 文档列表内容 */}
              <div className="documents-list">
                {filteredDocuments.length > 0 ? (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {filteredDocuments.map((doc) => (
                      <div key={doc.id} className="document-item">
                        <Checkbox
                          checked={selectedLinkedDocumentIds.includes(doc.id || 0)}
                          onChange={(e) =>
                            handleLinkedDocumentToggle(e.target.checked, doc.id || 0)
                          }
                        >
                          <span className="doc-title">
                            {doc.title || '(未命名文档)'}
                          </span>
                        </Checkbox>
                      </div>
                    ))}
                  </Space>
                ) : (
                  <div className="empty-result">
                    {searchKeyword ? '未找到匹配的文档' : '当前分类下暂无文档'}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </Modal>

      {/* 写作模板选择弹窗 */}
      <Modal
        title="选择模板"
        open={isWritingStyleModalVisible}
        onCancel={() => setIsWritingStyleModalVisible(false)}
        footer={null}
        width={650}
      >
        <div style={{ maxHeight: '500px', overflowY: 'auto', padding: '8px' }}>
          <Radio.Group
            value={selectedWritingStyleId}
            onChange={(e) => {
              const styleId = e.target.value as number | null
              setSelectedWritingStyleId(styleId)
              onWritingStyleChange?.(styleId)
              message.success('已选择模板')
              setIsWritingStyleModalVisible(false)
            }}
            style={{ width: '100%' }}
          >
            <Space direction="vertical" style={{ width: '100%', gap: '12px' }}>
              <Radio
                value={null}
                style={{
                  display: 'block',
                  padding: '16px',
                  border: '2px solid #e0e0e0',
                  borderRadius: '12px',
                  width: '100%',
                }}
              >
                <div>
                  <div
                    style={{
                      fontWeight: 600,
                      fontSize: '14px',
                      color: '#000',
                      marginBottom: '6px',
                    }}
                  >
                    默认模板
                  </div>
                  <div
                    style={{
                      fontSize: '12px',
                      color: '#666',
                      lineHeight: '1.6',
                    }}
                  >
                    使用系统默认模板，适合大多数创作场景。
                  </div>
                </div>
              </Radio>
              {writingStyles.map((style) => (
                <Radio
                  key={style.id}
                  value={style.id}
                  style={{
                    display: 'block',
                    padding: '16px',
                    border: '2px solid #e0e0e0',
                    borderRadius: '12px',
                    width: '100%',
                  }}
                >
                  <div>
                    <div
                      style={{
                        fontWeight: 600,
                        fontSize: '14px',
                        color: '#000',
                        marginBottom: '6px',
                      }}
                    >
                      {style.name}
                    </div>
                    <div
                      style={{
                        fontSize: '12px',
                        color: '#666',
                        lineHeight: '1.6',
                      }}
                    >
                      {style.description}
                    </div>
                  </div>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        </div>
      </Modal>
    </div>
  )
}

export default ToolPanel
