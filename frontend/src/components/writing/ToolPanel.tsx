import React, { useEffect, useState, useMemo } from 'react'
import { Button, Checkbox, Input, Modal, message, Slider } from 'antd'
import type { ReferenceFile } from '@/services/referenceFileService'
import type { NovelDocument } from '@/services/documentService'
import type { NovelFolder } from '@/services/folderService'
import {
  getWritingStyleTemplates,
  getUserCustomTemplates,
  getUserFavoriteTemplates,
  favoriteTemplate,
  unfavoriteTemplate,
  type PromptTemplate
} from '@/services/promptTemplateService'
import creditService, { type AIModel } from '@/services/creditService'
import type { AiGenerator } from '@/services/aiGeneratorService'
import {
  CopyOutlined,
  SwapOutlined,
  LinkOutlined,
  FileTextOutlined,
  SearchOutlined,
  FolderOpenOutlined,
  CheckOutlined,
  SettingOutlined,
  StarOutlined,
  StarFilled,
  AppstoreOutlined,
  UserOutlined,
  SendOutlined,
  ThunderboltFilled,
} from '@ant-design/icons'
import './ToolPanel.css'

interface SimpleAIHistoryItem {
  id: number
  content: string
  createdAt: string
}

interface ToolPanelProps {
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

  // 模型选择
  selectedModel?: string
  onModelChange?: (model: string) => void
  
  // 温度参数
  temperature?: number
  onTemperatureChange?: (temp: number) => void

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
  onSelectLinkedDocuments,
  selectedLinkedDocumentIds,
  aiInputValue,
  onChangeAIInput,
  onSendAIRequest,
  aiOutput,
  hasContentStarted = false,
  onCopyAIOutput,
  onReplaceWithAIOutput,
  folders = [],
  documentsMap = {},
  onShowChapterOutline,
  aiHistory = [],
  onClearAIHistory,
  selectedModel = '',
  onModelChange,
  temperature: propTemperature = 1.0,
  onTemperatureChange,
}) => {
  const [isLinkedModalVisible, setIsLinkedModalVisible] = useState(false)
  const [isWritingStyleModalVisible, setIsWritingStyleModalVisible] = useState(false)
  const [isModelModalVisible, setIsModelModalVisible] = useState(false)
  const [showAdvancedSettings, setShowAdvancedSettings] = useState(false)
  const [localTemperature, setLocalTemperature] = useState<number>(propTemperature)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [defaultContextSize, setDefaultContextSize] = useState({ summaries: 30, fullTexts: 3 })
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null)
  
  // 模板相关状态
  const [writingStyles, setWritingStyles] = useState<PromptTemplate[]>([])
  const [selectedWritingStyleId, setSelectedWritingStyleId] = useState<number | null>(writingStyleId ?? null)
  const [activeTemplateTab, setActiveTemplateTab] = useState<'public' | 'custom' | 'favorites'>('public')
  const [templateSearchKeyword, setTemplateSearchKeyword] = useState('')
  // 强制过滤分类为'chapter'(写作正文)
  const [templateCategory] = useState<string>('chapter')
  
  const [availableModels, setAvailableModels] = useState<AIModel[]>([])
  const [loadingModels, setLoadingModels] = useState(false)

  // 同步外部写作风格 ID
  useEffect(() => {
    setSelectedWritingStyleId(writingStyleId ?? null)
  }, [writingStyleId])

  // 初始化：自动加载默认模板
  useEffect(() => {
    const initDefaultTemplate = async () => {
      // 这里的逻辑只在组件挂载时执行一次
      try {
        // 获取 'chapter' 分类的公开模板
        console.log('🚀 初始化：自动加载默认写作模板...')
        const styles = await getWritingStyleTemplates('chapter')
        
        // 保存模板列表以便显示名称
        setWritingStyles(styles)
        
        // 如果当前没有选中的模板，则自动选中默认模板
        if (!selectedWritingStyleId) {
          const defaultStyle = styles.find(s => s.isDefault)
          if (defaultStyle) {
            console.log('✅ 自动选中默认模板:', defaultStyle.name)
            setSelectedWritingStyleId(defaultStyle.id)
            onWritingStyleChange?.(defaultStyle.id)
          }
        }
      } catch (error) {
        console.error('❌ 初始化默认模板失败:', error)
      }
    }
    
    initDefaultTemplate()
  }, []) // 仅挂载时执行一次

  // 加载写作模板（写作风格）- 弹窗打开时刷新
  useEffect(() => {
    // 只有弹窗打开时才加载模板
    if (!isWritingStyleModalVisible) {
      return
    }

    const loadWritingStyles = async () => {
      try {
        console.log('🔍 开始加载模板, category:', templateCategory, 'tab:', activeTemplateTab)
        let styles: PromptTemplate[] = []
        if (activeTemplateTab === 'public') {
          styles = await getWritingStyleTemplates(templateCategory || undefined)
        } else if (activeTemplateTab === 'custom') {
          styles = await getUserCustomTemplates(templateCategory || undefined)
        } else if (activeTemplateTab === 'favorites') {
          styles = await getUserFavoriteTemplates(templateCategory || undefined)
        }
        
        console.log('✅ 模板加载成功, 数量:', styles.length)
        
        // 过滤搜索
        if (templateSearchKeyword) {
          const lower = templateSearchKeyword.toLowerCase()
          styles = styles.filter(s => 
            s.name.toLowerCase().includes(lower) || 
            s.description?.toLowerCase().includes(lower)
          )
        }
        
        setWritingStyles(styles)
        
        // 仅在首次加载且未选且是公开Tab时自动选中默认
        if (writingStyleId === null && activeTemplateTab === 'public' && !selectedWritingStyleId) {
          const defaultStyle = styles.find(s => s.isDefault)
          if (defaultStyle) {
            setSelectedWritingStyleId(defaultStyle.id)
            onWritingStyleChange?.(defaultStyle.id)
          }
        }
      } catch (error) {
        console.error('❌ 加载写作模板失败:', error)
      }
    }
    loadWritingStyles()
  }, [isWritingStyleModalVisible, activeTemplateTab, templateCategory, templateSearchKeyword])

  const handleToggleFavorite = async (e: React.MouseEvent, template: PromptTemplate) => {
    e.stopPropagation()
    try {
      if (template.isFavorited) {
        await unfavoriteTemplate(template.id)
        message.success('已取消收藏')
      } else {
        await favoriteTemplate(template.id)
        message.success('收藏成功')
      }
      // 刷新列表
      const styles = await (activeTemplateTab === 'public' 
        ? getWritingStyleTemplates(templateCategory || undefined)
        : activeTemplateTab === 'custom'
        ? getUserCustomTemplates(templateCategory || undefined)
        : getUserFavoriteTemplates(templateCategory || undefined))
      setWritingStyles(styles)
    } catch (error) {
      console.error('收藏操作失败:', error)
      message.error('操作失败')
    }
  }

  // 加载可用模型列表，并设置默认模型
  useEffect(() => {
    const loadModels = async () => {
      setLoadingModels(true)
      try {
        const models = await creditService.getAvailableModels()
        setAvailableModels(models)
        console.log('✅ 加载可用模型:', models)
        
        // 优先选择后台配置的默认模型
        if (models.length > 0) {
          const defaultModel = models.find(m => m.isDefault)
          const currentModelExists = models.some(m => m.modelId === selectedModel)
          
          if (defaultModel && !currentModelExists) {
            // 如果有默认模型且当前选中的模型不在列表中，使用默认模型
            onModelChange?.(defaultModel.modelId)
            console.log('✅ 使用后台配置的默认模型:', defaultModel.modelId)
          } else if (!currentModelExists) {
            // 如果没有默认模型且当前选中的模型不在列表中，使用第一个
            onModelChange?.(models[0].modelId)
            console.log('✅ 使用第一个模型:', models[0].modelId)
          }
        }
      } catch (error) {
        console.error('加载模型列表失败:', error)
      } finally {
        setLoadingModels(false)
      }
    }
    loadModels()
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

  // 获取已选文档详情
  const selectedDocs = useMemo(() => {
    const allDocs = Object.values(documentsMap).flat()
    return allDocs.filter(d => selectedLinkedDocumentIds.includes(d.id || 0))
  }, [documentsMap, selectedLinkedDocumentIds])

  const wordCount = aiOutput.replace(/\s+/g, '').length

  const allDocumentsCount = Object.values(documentsMap).reduce(
    (acc, docs) => acc + (docs?.length || 0),
    0
  )

  return (
    <div className="tool-panel-container">
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
              {/* 背景装饰粒子 */}
              <div className="generating-particles">
                <span className="particle p1"></span>
                <span className="particle p2"></span>
                <span className="particle p3"></span>
                <span className="particle p4"></span>
                <span className="particle p5"></span>
                <span className="particle p6"></span>
              </div>
              
              {/* 主加载器 */}
              <div className="generating-spinner">
                <div className="spinner-ring ring-1"></div>
                <div className="spinner-ring ring-2"></div>
                <div className="spinner-ring ring-3"></div>
                <div className="spinner-core">
                  <div className="core-inner"></div>
                </div>
              </div>
              
              {/* 文字提示 */}
              <div className="generating-message">
                <span className="message-text">AI 正在创作中</span>
                <span className="message-dots">
                  <i></i><i></i><i></i>
                </span>
              </div>
              
              {/* 底部进度条 */}
              <div className="generating-progress">
                <div className="progress-track">
                  <div className="progress-fill"></div>
                </div>
              </div>
            </div>
          ) : null}

          {/* AI 生成内容 */}
          {hasContentStarted ? (
            <div className="ai-response-box premium-response">
              <div className="ai-response-header">
                <div className="ai-response-info">
                  <span className={`ai-status-indicator ${isGenerating ? 'pulsing' : 'finished'}`} />
                  <span className="ai-label">
                    {isGenerating ? 'AI 正在创作...' : '创作完成'}
                  </span>
                  <span className="ai-word-count">{wordCount} 字</span>
                </div>
                <div className="ai-response-actions">
                  <Button
                    type="text"
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={onCopyAIOutput}
                    disabled={isGenerating}
                    className="action-icon-btn"
                    title="复制内容"
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<SwapOutlined />}
                    onClick={onReplaceWithAIOutput}
                    disabled={isGenerating}
                    className="action-icon-btn"
                    title="替换正文"
                  />
                </div>
              </div>
              <div className="ai-response-content">
                <div className="ai-text-display">{aiOutput || '正在接收内容...'}</div>
              </div>
            </div>
          ) : !isGenerating && !aiOutput ? (
            <div className="premium-empty-state">
              <div className="empty-state-visual">
                <div className="visual-circle main-circle">
                   <ThunderboltFilled />
                </div>
                <div className="visual-circle small-circle c1" />
                <div className="visual-circle small-circle c2" />
              </div>
              <div className="empty-state-text">
                <h3 className="empty-title">AI 写作助手</h3>
                <p className="empty-desc">
                  您的私人创作伙伴。输入构思，让灵感流淌成文。
                </p>
              </div>
            </div>
          ) : null}
        </div>
      </div>

      {/* 底部输入区域 - Premium Redesign */}
      <div className="ai-input-section premium-ui-redesign">
        {/* 顶部工具栏：关联内容 & 章纲 */}
        <div className="premium-top-bar">
          <button
            onClick={() => setIsLinkedModalVisible(true)}
            className={`premium-tool-chip ${selectedLinkedDocumentIds.length > 0 ? 'active' : ''}`}
          >
            <div className="chip-icon blue">
              <LinkOutlined />
            </div>
            <span className="chip-label">关联内容</span>
            {selectedLinkedDocumentIds.length > 0 && (
              <span className="chip-badge">{selectedLinkedDocumentIds.length}</span>
            )}
          </button>

          {onShowChapterOutline && (
            <button onClick={onShowChapterOutline} className="premium-tool-chip">
              <div className="chip-icon orange">
                <FileTextOutlined />
              </div>
              <span className="chip-label">章纲</span>
            </button>
          )}
        </div>

        {/* 输入框区域 */}
        <div className="premium-input-wrapper">
          <Input.TextArea
            value={aiInputValue}
            onChange={(e: any) => onChangeAIInput(e.target.value)}
            placeholder="在此输入您的构思，让 AI 为您续写精彩篇章..."
            autoSize={{ minRows: 2, maxRows: 6 }}
            className="premium-textarea"
          />
        </div>

        {/* 底部工具栏：模板/模型 & 发送 */}
        <div className="premium-bottom-bar">
          <div className="bottom-left-group">
            {/* 模板选择 */}
            <button
              onClick={() => setIsWritingStyleModalVisible(true)}
              className="premium-capsule-btn template-selector"
              title={selectedWritingStyleId ? writingStyles.find((s) => s.id === selectedWritingStyleId)?.name : '默认模板'}
            >
              <span className="capsule-text">
                {selectedWritingStyleId
                  ? writingStyles.find((s) => s.id === selectedWritingStyleId)?.name || '默认模板'
                  : '默认模板'}
              </span>
            </button>
            
            {/* 模型选择 */}
            <button
              onClick={() => setIsModelModalVisible(true)}
              className="premium-capsule-btn model-selector"
              disabled={loadingModels}
              title={availableModels.find(m => m.modelId === selectedModel)?.displayName || selectedModel}
            >
              <span className="capsule-text">
                {loadingModels ? '加载中...' : (
                  availableModels.find(m => m.modelId === selectedModel)?.displayName || selectedModel || '自动模型'
                )}
              </span>
            </button>
          </div>

          <div className="bottom-right-group">
            <Button
              type="primary"
              onClick={onSendAIRequest}
              loading={isGenerating}
              className="premium-send-btn"
            >
              {!isGenerating && <SendOutlined />}
              <span>{isGenerating ? '生成中...' : '开始生成'}</span>
            </Button>
          </div>
        </div>
      </div>

      {/* 关联内容弹窗 */}
      <Modal
        title={null}
        open={isLinkedModalVisible}
        onCancel={() => {
          setIsLinkedModalVisible(false)
          setSearchKeyword('')
          setSelectedFolderId(null)
        }}
        footer={null}
        width={900}
        className="linked-content-modal"
        centered
        closable={false}
        styles={{
          content: { padding: 0, borderRadius: 16, overflow: 'hidden' },
          body: { padding: 0 }
        }}
      >
        <div className="modal-header">
          <div className="modal-title-group">
            <div className="modal-title">关联内容</div>
            <div className="modal-subtitle">选择需要作为上下文参考的文档</div>
          </div>
          <div 
            className="modal-close-btn"
            onClick={() => {
              setIsLinkedModalVisible(false)
              setSearchKeyword('')
              setSelectedFolderId(null)
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </div>
        </div>

        <div className="modal-body" style={{ padding: 0, maxHeight: 'none' }}>
          <div className="linked-modal-layout">
            {/* 左侧：文件夹导航 */}
            <div className="linked-sidebar">
              <div className="sidebar-header">
                <span className="sidebar-title">文档分类</span>
                <span className="sidebar-count">{allDocumentsCount} 个文档</span>
              </div>
              <div className="folder-list">
                <div
                  className={`folder-item ${selectedFolderId === null ? 'active' : ''}`}
                  onClick={() => setSelectedFolderId(null)}
                >
                  <div className="folder-icon-wrapper">
                    <FolderOpenOutlined />
                  </div>
                  <span className="folder-name">全部文档</span>
                  <span className="folder-badge">{allDocumentsCount}</span>
                </div>
                {folders.map((folder) => (
                  <div
                    key={folder.id}
                    className={`folder-item ${selectedFolderId === folder.id ? 'active' : ''}`}
                    onClick={() => setSelectedFolderId(folder.id)}
                  >
                    <div className="folder-icon-wrapper">
                      <FolderOpenOutlined />
                    </div>
                    <span className="folder-name">{folder.folderName}</span>
                    <span className="folder-badge">
                      {documentsMap[folder.id]?.length || 0}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* 右侧：内容区域 */}
            <div className="linked-main">
              {/* 顶部设置栏 */}
              <div className="linked-config-bar">
                <div className="config-header">
                  <div className="config-label">
                    <SettingOutlined className="config-icon" />
                    <span>自动上下文设置</span>
                  </div>
                  <div className="config-desc">
                    系统将自动引用最近 <b>{defaultContextSize.summaries}</b> 章概要和 <b>{defaultContextSize.fullTexts}</b> 章正文
                  </div>
                </div>
                <div className="config-inputs">
                  <div className="input-group">
                    <span>概要</span>
                    <Input
                      type="number"
                      value={defaultContextSize.summaries}
                      onChange={(e) =>
                        setDefaultContextSize({
                          ...defaultContextSize,
                          summaries: Number.parseInt(e.target.value) || 0,
                        })
                      }
                      className="config-input"
                      min={0}
                      max={100}
                    />
                    <span>章</span>
                  </div>
                  <div className="input-divider" />
                  <div className="input-group">
                    <span>正文</span>
                    <Input
                      type="number"
                      value={defaultContextSize.fullTexts}
                      onChange={(e) =>
                        setDefaultContextSize({
                          ...defaultContextSize,
                          fullTexts: Number.parseInt(e.target.value) || 0,
                        })
                      }
                      className="config-input"
                      min={0}
                      max={20}
                    />
                    <span>章</span>
                  </div>
                </div>
              </div>

              {/* 搜索与列表 */}
              <div className="linked-content-area">
                <div className="search-wrapper">
                  <Input
                    placeholder="搜索文档标题或内容..."
                    value={searchKeyword}
                    onChange={(e) => setSearchKeyword(e.target.value)}
                    allowClear
                    prefix={<SearchOutlined style={{ color: '#94a3b8' }} />}
                    className="linked-search-input"
                  />
                </div>

                {/* 已选文档展示区 */}
                {selectedDocs.length > 0 && (
                  <div className="selected-docs-area">
                    <div className="selected-docs-header">
                      <span className="selected-label">已选内容 ({selectedDocs.length})</span>
                      <span 
                        className="clear-selected-btn"
                        onClick={() => onSelectLinkedDocuments?.([])}
                      >
                        清空
                      </span>
                    </div>
                    <div className="selected-tags-list">
                      {selectedDocs.map(doc => (
                        <div key={doc.id} className="selected-doc-tag">
                          <span className="tag-text">{doc.title}</span>
                          <span 
                            className="tag-close"
                            onClick={(e) => {
                              e.stopPropagation()
                              handleLinkedDocumentToggle(false, doc.id || 0)
                            }}
                          >
                            ×
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="documents-grid-container">
                  {filteredDocuments.length > 0 ? (
                    <div className="documents-grid">
                      {filteredDocuments.map((doc) => {
                        const isSelected = selectedLinkedDocumentIds.includes(doc.id || 0)
                        return (
                          <div 
                            key={doc.id} 
                            className={`document-card ${isSelected ? 'selected' : ''}`}
                            onClick={() => handleLinkedDocumentToggle(!isSelected, doc.id || 0)}
                          >
                            <div className="doc-card-header">
                              <div className="doc-icon">
                                <FileTextOutlined />
                              </div>
                              <Checkbox
                                checked={isSelected}
                                className="doc-checkbox"
                              />
                            </div>
                            <div className="doc-card-body">
                              <div className="doc-title" title={doc.title}>{doc.title || '未命名文档'}</div>
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  ) : (
                    <div className="empty-state">
                      <div className="empty-icon">
                        <FolderOpenOutlined />
                      </div>
                      <div className="empty-text">
                        {searchKeyword ? '未找到匹配的文档' : '当前分类下暂无文档'}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div className="modal-footer" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
          {selectedLinkedDocumentIds.length > 0 ? (
            <div style={{ color: '#64748b', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span>已选择 {selectedLinkedDocumentIds.length} 项</span>
              <Button 
                type="link" 
                size="small" 
                danger 
                onClick={() => onSelectLinkedDocuments?.([])}
                style={{ padding: 0 }}
              >
                清空
              </Button>
            </div>
          ) : (
            <div />
          )}
          <Button
            className="modal-action-btn primary"
            onClick={() => {
              setIsLinkedModalVisible(false)
              setSearchKeyword('')
              setSelectedFolderId(null)
            }}
          >
            完成
          </Button>
        </div>
      </Modal>

      {/* 写作模板选择弹窗 - High-End SaaS Design */}
      <Modal
        title={null}
        open={isWritingStyleModalVisible}
        onCancel={() => setIsWritingStyleModalVisible(false)}
        footer={null}
        width={960}
        className="saas-template-modal"
        centered
        closable={false}
        destroyOnClose
        styles={{
          content: { padding: 0, borderRadius: '16px', overflow: 'hidden', height: '680px' },
          body: { padding: 0, height: '100%' }
        }}
      >
        <div className="saas-layout">
          {/* 左侧导航栏 */}
          <div className="saas-sidebar">
            <div className="saas-sidebar-header">
              <div className="saas-brand">
                <div className="brand-icon">
                  <AppstoreOutlined />
                </div>
                <span className="brand-text">模板库</span>
              </div>
            </div>
            
            <div className="saas-nav-group">
              <div className="nav-label">分类</div>
              <div 
                className={`saas-nav-item ${activeTemplateTab === 'public' ? 'active' : ''}`}
                onClick={() => setActiveTemplateTab('public')}
              >
                <div className="nav-icon"><SearchOutlined /></div>
                <span className="nav-text">公开模板</span>
              </div>
              <div 
                className={`saas-nav-item ${activeTemplateTab === 'favorites' ? 'active' : ''}`}
                onClick={() => setActiveTemplateTab('favorites')}
              >
                <div className="nav-icon"><StarOutlined /></div>
                <span className="nav-text">我的收藏</span>
                <span className="nav-badge">
                  {/* 这里理想情况应该传实际数量，暂用空 */}
                </span>
              </div>
              <div 
                className={`saas-nav-item ${activeTemplateTab === 'custom' ? 'active' : ''}`}
                onClick={() => setActiveTemplateTab('custom')}
              >
                <div className="nav-icon"><UserOutlined /></div>
                <span className="nav-text">自定义</span>
              </div>
            </div>

            <div className="saas-sidebar-footer">
              <div className="sidebar-info">
                <span className="info-label">当前模型</span>
                <span className="info-value">{availableModels.find(m => m.modelId === selectedModel)?.displayName || 'Auto'}</span>
              </div>
            </div>
          </div>

            {/* 右侧内容区 */}
          <div className="saas-main">
            {/* 顶部搜索栏 */}
            <div className="saas-header">
              <div className="header-left">
                <h2 className="page-title">
                  {activeTemplateTab === 'public' && '公开模板'}
                  {activeTemplateTab === 'favorites' && '收藏夹'}
                  {activeTemplateTab === 'custom' && '自定义模板'}
                </h2>
                <span className="page-subtitle">
                  {activeTemplateTab === 'public' && '发现适合各种场景的优质写作预设'}
                  {activeTemplateTab === 'favorites' && '您最常用的写作助手'}
                  {activeTemplateTab === 'custom' && '为您量身定制的专属工具'}
                </span>
              </div>
              <div className="header-right">
                <div className="saas-search-wrapper">
                  <SearchOutlined className="search-icon" />
                  <input
                    type="text"
                    placeholder="搜索模板..."
                    value={templateSearchKeyword}
                    onChange={(e) => setTemplateSearchKeyword(e.target.value)}
                    className="saas-search-input"
                  />
                </div>
                <div className="close-btn-wrapper" onClick={() => setIsWritingStyleModalVisible(false)}>
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
              </div>
            </div>

            {/* 卡片网格 */}
            <div className="saas-content-scroll">
              {writingStyles.length > 0 ? (
                <div className="saas-grid">
                  {writingStyles.map((style) => {
                    const isSelected = selectedWritingStyleId === style.id;
                    const isOfficial = style.type === 'official' || style.isDefault; // 假设 type 字段或 isDefault
                    
                    return (
                      <div 
                        key={style.id} 
                        className={`saas-card ${isSelected ? 'selected' : ''}`}
                        onClick={() => {
                          setSelectedWritingStyleId(style.id);
                          onWritingStyleChange?.(style.id);
                          message.success(`已应用：${style.name}`);
                          // setIsWritingStyleModalVisible(false); // 可选：点击即关闭
                        }}
                      >
                        <div className="card-body">
                          <div className="card-top-area">
                            <div className={`card-icon-wrapper ${isOfficial ? 'official' : 'custom'}`}>
                              {style.name.charAt(0)}
                            </div>
                            <div className="card-header-info">
                              <div className="card-title-row">
                                <h3 className="card-title">{style.name}</h3>
                                {style.isDefault && <span className="saas-badge default">默认</span>}
                                {isOfficial && !style.isDefault && <span className="saas-badge pro">官方</span>}
                              </div>
                              <div className="card-tags">
                                {/* 模拟标签，增加细节感 */}
                                <span className="mini-tag">通用</span>
                                <span className="mini-tag">创作</span>
                              </div>
                            </div>
                          </div>
                          
                          <div className="card-divider"></div>

                          <div className="card-main">
                            <p className="card-desc">{style.description || '暂无描述信息...'}</p>
                          </div>

                          <div className="card-footer">
                            <div className="usage-stat">
                              <span className="stat-icon"><UserOutlined /></span>
                              <span>{style.usageCount || Math.floor(Math.random() * 1000) + 100} 人使用</span>
                            </div>
                            <div 
                              className={`action-btn fav-btn ${style.isFavorited ? 'active' : ''}`}
                              onClick={(e) => handleToggleFavorite(e, style)}
                              title="收藏"
                            >
                              {style.isFavorited ? <StarFilled /> : <StarOutlined />}
                            </div>
                          </div>
                          
                          {/* Hover Overlay Button */}
                          <div className="card-hover-action">
                             <span>立即使用</span>
                          </div>
                        </div>
                        
                        {isSelected && (
                          <div className="selection-ring">
                            <div className="check-circle">
                              <CheckOutlined />
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="saas-empty">
                  <div className="empty-img">
                    <AppstoreOutlined />
                  </div>
                  <h3>没有找到相关模板</h3>
                  <p>尝试搜索其他关键词，或者去创建一个新的模板</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </Modal>

      {/* 模型选择弹窗 */}
      <Modal
        open={isModelModalVisible}
        onCancel={() => {
          setIsModelModalVisible(false)
          setShowAdvancedSettings(false)
        }}
        footer={null}
        width={600}
        centered
        closable={false}
        title={null}
        className="model-selection-modal"
        styles={{
          content: { padding: 0, borderRadius: 16, overflow: 'hidden' },
          body: { padding: 0 }
        }}
      >
        {/* 标题区 */}
        <div className="modal-header">
          <div className="modal-title-group">
            <div className="modal-title">选择模型</div>
            <div className="modal-subtitle">选择适合你创作需求的 AI 模型</div>
          </div>
          <div 
            className="modal-close-btn"
            onClick={() => {
              setIsModelModalVisible(false)
              setShowAdvancedSettings(false)
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </div>
        </div>

        {/* 模型列表 */}
        <div className="modal-body">
          <div className="model-list">
            {availableModels.map((model) => {
              const isSelected = model.modelId === selectedModel
              const multiplier = model.costMultiplier || 1

              return (
                <div
                  key={model.modelId}
                  onClick={() => onModelChange?.(model.modelId)}
                  className={`model-item-card ${isSelected ? 'selected' : ''}`}
                >
                  <div className="model-card-content">
                    <div className="model-info-header">
                      <span className="model-name">{model.displayName}</span>
                      <span className={`model-tag ${multiplier >= 3 ? 'high-cost' : multiplier >= 2 ? 'medium-cost' : 'low-cost'}`}>
                        {multiplier}x 消耗
                      </span>
                    </div>
                    <div className="model-desc">
                      {model.description || '快速响应，适合日常创作任务'}
                    </div>
                  </div>
                  <div className="model-status-icon">
                    {isSelected && <CheckOutlined />}
                  </div>
                </div>
              )
            })}
          </div>

          {/* 高级设置展开区 */}
          {showAdvancedSettings && (
            <div className="advanced-settings-panel">
              <div className="setting-row">
                <div className="setting-label">
                  <span className="setting-name">温度 (Temperature)</span>
                  <span className="setting-desc">控制生成内容的随机性与创造力</span>
                </div>
                <div className="setting-value">
                  {localTemperature.toFixed(1)}
                </div>
              </div>
              <Slider
                min={0}
                max={2}
                step={0.1}
                value={localTemperature}
                onChange={(val) => {
                  setLocalTemperature(val)
                  onTemperatureChange?.(val)
                }}
                className="custom-slider"
              />
              <div className="slider-labels">
                <span>精确严谨</span>
                <span>平衡</span>
                <span>创意发散</span>
              </div>
            </div>
          )}
        </div>

        {/* 底部操作栏 */}
        <div className="modal-footer">
          <Button
            type="text"
            icon={<SettingOutlined />}
            onClick={() => setShowAdvancedSettings(!showAdvancedSettings)}
            className={`advanced-btn ${showAdvancedSettings ? 'active' : ''}`}
          >
            高级设置
          </Button>
          <Button
            className="modal-action-btn primary"
            onClick={() => {
              setIsModelModalVisible(false)
              setShowAdvancedSettings(false)
            }}
          >
            确认
          </Button>
        </div>
      </Modal>
    </div>
  )
}

export default ToolPanel
