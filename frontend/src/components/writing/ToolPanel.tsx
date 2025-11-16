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
        <div className="ai-chat-history">
          {/* 生成阶段动画（正文出现前） */}
          {isGenerating && !hasContentStarted ? (
            <div
              style={{
                padding: '20px',
                background: '#fafafa',
                borderRadius: '8px',
                border: '1px solid #e8e8e8',
              }}
            >
              {/* 加载动画 */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  marginBottom: generationPhases.length > 0 ? '16px' : 0,
                }}
              >
                <div
                  style={{
                    width: '20px',
                    height: '20px',
                    position: 'relative',
                  }}
                >
                  <div
                    style={{
                      position: 'absolute',
                      width: '100%',
                      height: '100%',
                      border: '3px solid #e8e8e8',
                      borderTop: '3px solid #1890ff',
                      borderRadius: '50%',
                      animation: 'spin 0.8s linear infinite',
                    }}
                  ></div>
                </div>
                <span
                  style={{
                    fontSize: '15px',
                    fontWeight: 500,
                    color: '#1890ff',
                  }}
                >
                  AI 正在生成内容...
                </span>
              </div>

              {/* 阶段列表 */}
              {generationPhases.length > 0 && (
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px',
                    paddingLeft: '32px',
                  }}
                >
                  {generationPhases.map((phase, index) => (
                    <div
                      key={index}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px',
                        fontSize: '14px',
                        color: '#595959',
                        opacity: index === generationPhases.length - 1 ? 1 : 0.6,
                        transition: 'opacity 0.3s ease',
                      }}
                    >
                      <div
                        style={{
                          width: '6px',
                          height: '6px',
                          borderRadius: '50%',
                          background: index === generationPhases.length - 1 ? '#52c41a' : '#d9d9d9',
                          flexShrink: 0,
                        }}
                      ></div>
                      <span>{phase}</span>
                    </div>
                  ))}
                </div>
              )}

              <style
                dangerouslySetInnerHTML={{
                  __html: `
                    @keyframes spin {
                      from { transform: rotate(0deg); }
                      to { transform: rotate(360deg); }
                    }
                  `,
                }}
              ></style>
            </div>
          ) : null}

          {/* AI 生成内容 */}
          {hasContentStarted && aiOutput ? (
            <div className="ai-response-box">
              <div className="ai-response-header">
                <div className="ai-response-info">
                  <span className="ai-label">生成结果</span>
                  <span className="ai-word-count">{wordCount} 字</span>
                </div>
                <div className="ai-response-actions">
                  <Button
                    type="text"
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={onCopyAIOutput}
                    disabled={isGenerating}
                  >
                    复制
                  </Button>
                  <Button
                    type="text"
                    size="small"
                    icon={<SwapOutlined />}
                    onClick={onReplaceWithAIOutput}
                    disabled={isGenerating}
                  >
                    替换正文
                  </Button>
                </div>
              </div>
              <div className="ai-response-content">
                <div className="ai-text-display">{aiOutput}</div>
              </div>
            </div>
          ) : !isGenerating && !aiOutput ? (
            <div className="ai-chat-empty">
              <div className="empty-visual-container">
                <div className="floating-orb orb-1"></div>
                <div className="floating-orb orb-2"></div>
                <div className="floating-orb orb-3"></div>
                <div className="floating-orb orb-4"></div>
                <div className="central-icon">
                  <svg viewBox="0 0 100 100" className="ai-icon-svg">
                    <defs>
                      <linearGradient id="iconGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" style={{ stopColor: '#1890ff', stopOpacity: 1 }} />
                        <stop offset="100%" style={{ stopColor: '#52c41a', stopOpacity: 1 }} />
                      </linearGradient>
                    </defs>
                    <circle cx="50" cy="50" r="35" className="icon-circle" />
                    <path d="M35 40 L50 25 L65 40" className="icon-arrow" />
                    <path d="M35 60 L50 75 L65 60" className="icon-arrow" />
                  </svg>
                </div>
              </div>
              <div className="empty-title">在这里输入提示，让助手帮你写作</div>
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
            placeholder="请输入你的写作需求，例如续写本章、重写一段对话等"
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
            >
              生成
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

