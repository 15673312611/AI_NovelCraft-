import React, { useMemo, useState, useEffect, useRef } from 'react'
import { Tree, Button, Input, Tooltip, Checkbox, Slider } from 'antd'
import {
  FolderAddOutlined,
  FileAddOutlined,
  SearchOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import type { DataNode, TreeProps } from 'antd/es/tree'
import type { NovelFolder } from '@/services/folderService'
import type { NovelDocument } from '@/services/documentService'
import type { Chapter } from '@/services/chapterServiceForStudio'
import './FileTree.css'

export interface FileTreeNode {
  key: string
  title: React.ReactNode
  isLeaf?: boolean
  icon?: React.ReactNode
  children?: FileTreeNode[]
  data?: {
    type: 'root' | 'folder' | 'document' | 'chapter'
    folder?: NovelFolder
    document?: NovelDocument
    chapter?: Chapter
  }
}

export interface FileTreeProps {
  novelTitle?: string
  folders: NovelFolder[]
  chapters: Chapter[]
  documents: NovelDocument[]
  selectedKey?: string
  onSelectChapter?: (chapter: Chapter) => void
  onSelectDocument: (document: NovelDocument) => void
  onSelectFolder?: (folder: NovelFolder | null) => void
  onExpandFolder?: (folder: NovelFolder) => void
  onCreateFolder?: (parentFolder?: NovelFolder | null, folderName?: string) => void
  onCreateDocument?: (folder: NovelFolder, documentName?: string) => void
  onQuickAddChapter?: (folder: NovelFolder) => void
  onSearch?: (keyword: string) => void
  onSearchClear?: () => void
  onToolbarCreateFolder?: () => void
  onToolbarCreateDocument?: () => void
  onDeleteFolder?: (folder: NovelFolder) => void
  onDeleteDocument?: (document: NovelDocument) => void
  onDeleteChapter?: (chapter: Chapter) => void
  onRenameFolder?: (folder: NovelFolder, newName: string) => void
  onRenameDocument?: (document: NovelDocument, newName: string) => void
  onRenameChapter?: (chapter: Chapter, newName: string) => void
  onChapterAnalysis?: () => void
  analysisMode?: boolean
  onAnalysisModeClose?: () => void
  onAnalysisTypeSelect?: (analysisType: string) => void
  totalChapters?: number
  onStartAnalysis?: (params: { analysisType: string; startChapter: number; endChapter: number }) => void
  onBackToList?: () => void
}

interface ContextMenuState {
  visible: boolean
  x: number
  y: number
  type: 'folder' | 'document' | 'chapter' | null
  folder?: NovelFolder
  document?: NovelDocument
  chapter?: Chapter
}

const FileTree: React.FC<FileTreeProps> = ({
  novelTitle,
  folders,
  chapters,
  documents,
  selectedKey,
  onSelectChapter,
  onSelectDocument,
  onSelectFolder,
  onExpandFolder,
  onCreateFolder,
  onCreateDocument,
  onQuickAddChapter,
  onSearch,
  onSearchClear,
  onToolbarCreateFolder,
  onToolbarCreateDocument,
  onDeleteFolder,
  onDeleteDocument,
  onDeleteChapter,
  onRenameFolder,
  onRenameDocument,
  onRenameChapter,
  onChapterAnalysis,
  analysisMode = false,
  onAnalysisModeClose,
  onAnalysisTypeSelect,
  totalChapters,
  onStartAnalysis,
  onBackToList,
}) => {
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searchMode, setSearchMode] = useState(false)
  const isSearchComposingRef = useRef(false)

  const triggerSearch = (value: string) => {
    const trimmed = value.trim()
    if (trimmed) {
      onSearch?.(trimmed)
    } else {
      onSearchClear?.()
    }
  }

  const ANALYSIS_TYPES = [
    { key: 'golden_three', name: '黄金三章', description: '黄金三章分析' },
    { key: 'main_plot', name: '主线剧情', description: '核心故事线发展' },
    { key: 'sub_plot', name: '支线剧情', description: '辅助故事线分析' },
    { key: 'theme', name: '主题分析', description: '深层主题与意义' },
    { key: 'character', name: '角色分析', description: '人物塑造与发展' },
    { key: 'worldbuilding', name: '世界设定', description: '背景环境与规则' },
    { key: 'writing_style', name: '写作风格与技巧', description: '文笔风格和叙事技法' },
  ]
  // 拆解设置状态
  const maxChapters = Math.max(1, (totalChapters as number | undefined) || chapters.length || 1)
  const [range, setRange] = useState<[number, number]>([1, Math.min(2, maxChapters)])
  const [selectedAnalysisTypeKey, setSelectedAnalysisTypeKey] = useState<string>('golden_three')
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([])
  const [contextMenu, setContextMenu] = useState<ContextMenuState>({
    visible: false,
    x: 0,
    y: 0,
    type: null,
  })
  const [editingKey, setEditingKey] = useState<string | null>(null)
  const [editingName, setEditingName] = useState('')
  const [editingType, setEditingType] = useState<'folder' | 'document' | 'chapter' | null>(null)
  const [creatingNewFolder, setCreatingNewFolder] = useState<{ parentId: number | null, tempKey: string } | null>(null)
  const [creatingNewDocument, setCreatingNewDocument] = useState<{ folderId: number, tempKey: string } | null>(null)
  const contextMenuRef = useRef<HTMLDivElement>(null)
  const editInputRef = useRef<HTMLInputElement>(null)
  const blurTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 点击其他地方关闭右键菜单
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (contextMenuRef.current && !contextMenuRef.current.contains(e.target as Node)) {
        setContextMenu({ visible: false, x: 0, y: 0, type: null })
      }
    }

    if (contextMenu.visible) {
      document.addEventListener('click', handleClickOutside)
      document.addEventListener('contextmenu', handleClickOutside)
      return () => {
        document.removeEventListener('click', handleClickOutside)
        document.removeEventListener('contextmenu', handleClickOutside)
      }
    }
  }, [contextMenu.visible])

  const handleContextMenu = (
    e: React.MouseEvent,
    type: 'folder' | 'document' | 'chapter',
    item: NovelFolder | NovelDocument | Chapter
  ) => {
    e.preventDefault()
    e.stopPropagation()
    
    if (type === 'folder') {
      const folder = item as NovelFolder
      console.log('右键文件夹:', folder.folderName, 'isSystem:', folder.isSystem)
    }
    
    setContextMenu({
      visible: true,
      x: e.clientX,
      y: e.clientY,
      type,
      ...(type === 'folder'
        ? { folder: item as NovelFolder }
        : type === 'document'
          ? { document: item as NovelDocument }
          : { chapter: item as Chapter }),
    })
  }

  const handleMenuAction = (action: string) => {
    const { type, folder, document } = contextMenu
    setContextMenu({ visible: false, x: 0, y: 0, type: null })

    if (type === 'folder' && folder) {
      switch (action) {
        case 'newDoc':
          // 检查是否是"主要内容"文件夹（虚拟节点）
          const isMainContent = folder.id === -999
          if (isMainContent) {
            // 主要内容文件夹，添加章节
            onQuickAddChapter?.(folder)
          } else {
            // 其他文件夹，创建临时文档节点，进入编辑状态
            const tempDocKey = `temp-doc-${Date.now()}`
            setCreatingNewDocument({ folderId: folder.id, tempKey: tempDocKey })
            setEditingKey(tempDocKey)
            setEditingName('新建文档')
            setEditingType('document')
            // 自动展开父文件夹
            const folderKey = `folder-${folder.id}`
            if (!expandedKeys.includes(folderKey)) {
              setExpandedKeys([...expandedKeys, folderKey])
            }
            setTimeout(() => editInputRef.current?.select(), 0)
          }
          break
        case 'newFolder':
          // 主要内容文件夹不允许创建子文件夹
          if (folder.id === -999) {
            alert('主要内容文件夹只能添加章节，不能添加子文件夹')
            return
          }
          // 创建临时文件夹节点，进入编辑状态
          const tempKey = `temp-folder-${Date.now()}`
          setCreatingNewFolder({ parentId: folder.id, tempKey })
          setEditingKey(tempKey)
          setEditingName('新建文件夹')
          setEditingType('folder')
          // 自动展开父文件夹
          const folderKey = `folder-${folder.id}`
          if (!expandedKeys.includes(folderKey)) {
            setExpandedKeys([...expandedKeys, folderKey])
          }
          setTimeout(() => editInputRef.current?.select(), 0)
          break
        case 'rename':
          // 启用内联编辑
          setEditingKey(`folder-${folder.id}`)
          setEditingName(folder.folderName)
          setEditingType('folder')
          setTimeout(() => editInputRef.current?.select(), 0)
          break
        case 'delete':
          onDeleteFolder?.(folder)
          break
      }
    } else if (type === 'document' && document) {
      switch (action) {
        case 'rename':
          // 启用内联编辑
          setEditingKey(`doc-${document.id}`)
          setEditingName(document.title)
          setEditingType('document')
          setTimeout(() => editInputRef.current?.select(), 0)
          break
        case 'delete':
          onDeleteDocument?.(document)
          break
      }
    } else if (type === 'chapter' && contextMenu.chapter) {
      const chapter = contextMenu.chapter
      switch (action) {
        case 'rename':
          setEditingKey(`chapter-${chapter.id}`)
          setEditingName(chapter.title)
          setEditingType('chapter')
          setTimeout(() => editInputRef.current?.select(), 0)
          break
        case 'delete':
          onDeleteChapter?.(chapter)
          break
      }
    }
  }

  // 处理内联编辑完成
  const handleEditComplete = () => {
    if (!editingKey || !editingName.trim()) {
      setEditingKey(null)
      setEditingName('')
      setEditingType(null)
      setCreatingNewFolder(null)
      setCreatingNewDocument(null)
      return
    }

    if (editingType === 'folder') {
      // 检查是否是创建新文件夹
      if (creatingNewFolder && editingKey === creatingNewFolder.tempKey) {
        // 创建新文件夹
        const parentFolder = folders.find(f => f.id === creatingNewFolder.parentId)
        if (parentFolder) {
          onCreateFolder?.(parentFolder, editingName.trim())
        } else {
          onCreateFolder?.(null, editingName.trim())
        }
        setCreatingNewFolder(null)
      } else {
        // 重命名现有文件夹
        const folderId = parseInt(editingKey.replace('folder-', ''))
        const folder = folders.find(f => f.id === folderId)
        if (folder && editingName !== folder.folderName) {
          onRenameFolder?.(folder, editingName.trim())
        }
      }
    } else if (editingType === 'document') {
      // 检查是否是创建新文档
      if (creatingNewDocument && editingKey === creatingNewDocument.tempKey) {
        // 创建新文档
        const folder = folders.find(f => f.id === creatingNewDocument.folderId)
        if (folder) {
          onCreateDocument?.(folder, editingName.trim())
        }
        setCreatingNewDocument(null)
      } else {
        // 重命名现有文档
        const docId = parseInt(editingKey.replace('doc-', ''))
        const doc = documents.find(d => d.id === docId)
        if (doc && editingName !== doc.title) {
          onRenameDocument?.(doc, editingName.trim())
        }
      }
    } else if (editingType === 'chapter') {
      const chapterId = parseInt(editingKey.replace('chapter-', ''))
      const chapter = chapters.find((c) => c.id === chapterId)
      if (chapter && editingName !== chapter.title) {
        onRenameChapter?.(chapter, editingName.trim())
      }
    }

    setEditingKey(null)
    setEditingName('')
    setEditingType(null)
  }

  // 处理内联编辑取消
  const handleEditCancel = () => {
    if (blurTimeoutRef.current) {
      clearTimeout(blurTimeoutRef.current)
      blurTimeoutRef.current = null
    }
    setEditingKey(null)
    setEditingName('')
    setEditingType(null)
    setCreatingNewFolder(null)
    setCreatingNewDocument(null)
  }

  // 延迟处理失焦，防止因展开/收起导致的意外失焦
  const handleEditBlur = () => {
    if (blurTimeoutRef.current) {
      clearTimeout(blurTimeoutRef.current)
    }
    blurTimeoutRef.current = setTimeout(() => {
      handleEditComplete()
    }, 150)
  }

  // 输入框获得焦点时选中所有文本
  const handleEditFocus = (e: React.FocusEvent<HTMLInputElement>) => {
    e.target.select()
  }

  const treeData: DataNode[] = useMemo(() => {
    // 递归构建文件夹树
    const defaultExpandKeys: React.Key[] = ['root', 'main-content'] // 默认展开根节点与主要内容
    
    const buildFolderTree = (parentId: number | null): FileTreeNode[] => {
      const folderNodes = folders
        .filter((folder) => (folder.parentId ?? null) === parentId)
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map<FileTreeNode>((folder) => {
          const folderKey = `folder-${folder.id}`

          const folderDocuments = documents
            .filter((doc) => doc.folderId === folder.id)
            .sort((a, b) => {
              const orderA = a.sortOrder ?? a.id ?? 0
              const orderB = b.sortOrder ?? b.id ?? 0
              return orderA - orderB
            })

          const childFolders = buildFolderTree(folder.id)

          const docNodes: FileTreeNode[] = folderDocuments.map<FileTreeNode>((doc) => ({
            key: `doc-${doc.id}`,
            title: doc.title,
            isLeaf: true,
            icon: <FileTextOutlined style={{ color: '#3b82f6' }} />,
            data: {
              type: 'document',
              document: doc,
            },
          }))

          if (creatingNewDocument && creatingNewDocument.folderId === folder.id) {
            defaultExpandKeys.push(creatingNewDocument.tempKey)
            docNodes.push({
              key: creatingNewDocument.tempKey,
              title: '新建文档',
              isLeaf: true,
              icon: <FileTextOutlined style={{ color: '#3b82f6' }} />,
              data: {
                type: 'document',
                document: {
                  id: -1,
                  novelId: 0,
                  folderId: folder.id,
                  title: '新建文档',
                  content: '',
                  sortOrder: 999,
                } as NovelDocument,
              },
            })
          }

          const children = [...childFolders, ...docNodes]

          return {
            key: folderKey,
            title: folder.folderName,
            icon: <FolderOutlined style={{ color: '#f59e0b' }} />,
            data: {
              type: 'folder',
              folder,
            },
            children: children.length > 0 ? children : undefined,
            isLeaf: false, // 始终显示展开图标，让用户知道这是一个文件夹（内容可能懒加载）
          }
        })
      
      // 如果正在创建新文件夹，且父ID匹配，添加临时节点
      if (creatingNewFolder && creatingNewFolder.parentId === parentId) {
        defaultExpandKeys.push(creatingNewFolder.tempKey)
        folderNodes.push({
          key: creatingNewFolder.tempKey,
          title: '新建文件夹',
          icon: <FolderOutlined style={{ color: '#f59e0b' }} />,
          data: {
            type: 'folder',
            folder: {
              id: -1,
              novelId: 0,
              folderName: '新建文件夹',
              folderType: 'custom',
              parentId: parentId,
              sortOrder: 999,
              isSystem: false,
            } as NovelFolder,
          },
          children: [],
        })
      }
      
      return folderNodes
    }

    // 构建章节节点（作为"主要内容"的子节点）
    const chapterNodes: FileTreeNode[] = chapters
      .slice()
      .sort((a, b) => {
        const orderA = a.chapterNumber ?? a.id ?? 0
        const orderB = b.chapterNumber ?? b.id ?? 0
        return orderA - orderB
      })
      .map<FileTreeNode>((chapter) => {
        const hasNumber = typeof chapter.chapterNumber === 'number' && !Number.isNaN(chapter.chapterNumber)
        const displayTitle = hasNumber
          ? `第${chapter.chapterNumber}章 ${chapter.title || ''}`.trim()
          : chapter.title || `章节 ${chapter.id}`

        return {
          key: `chapter-${chapter.id}`,
          title: displayTitle,
          isLeaf: true,
          icon: <FileTextOutlined style={{ color: '#10b981' }} />,
          data: {
            type: 'chapter',
            chapter,
          },
        }
      })

    // 虚拟创建"主要内容"节点
    const mainContentNode: FileTreeNode = {
      key: 'main-content',
      title: '主要内容',
      icon: <FolderOutlined style={{ color: '#8b5cf6' }} />,
      data: {
        type: 'folder',
        folder: {
          id: -999,
          novelId: 0,
          folderName: '主要内容',
          folderType: 'chapter',
          parentId: null,
          sortOrder: 0,
          isSystem: true,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        } as NovelFolder,
      },
      children: chapterNodes,
    }

    const topLevelFolders = buildFolderTree(null)
    const rootNode: FileTreeNode = {
      key: 'root',
      title: novelTitle || '作品目录',
      icon: <FolderOutlined style={{ color: '#6366f1', fontSize: '16px' }} />,
      data: {
        type: 'root',
      },
      children: [mainContentNode, ...topLevelFolders],
    }

    // 只在初始化时设置默认展开项（仅root）
    // 如果 expandedKeys 为空，则初始化
    if (expandedKeys.length === 0) {
      setExpandedKeys(defaultExpandKeys)
    }

    return [rootNode] as DataNode[]
  }, [folders, documents, chapters, novelTitle, creatingNewFolder, creatingNewDocument, expandedKeys.length])

  const handleSelect: TreeProps['onSelect'] = (_keys, info) => {
    const nodeData = (info.node as any)?.data as FileTreeNode['data'] | undefined
    const nodeKey = info.node.key as string
    
    if (nodeData?.type === 'chapter' && nodeData.chapter) {
      onSelectChapter?.(nodeData.chapter)
    } else if (nodeData?.type === 'document' && nodeData.document) {
      onSelectDocument(nodeData.document)
    } else if (nodeData?.type === 'folder' || nodeData?.type === 'root') {
      // 点击文件夹或根节点时，切换展开/收起状态
      if (expandedKeys.includes(nodeKey)) {
        setExpandedKeys(expandedKeys.filter(key => key !== nodeKey))
      } else {
        setExpandedKeys([...expandedKeys, nodeKey])
      }
      
      if (nodeData?.type === 'folder' && nodeData.folder) {
        onSelectFolder?.(nodeData.folder)
      } else if (nodeData?.type === 'root') {
        onSelectFolder?.(null)
      }
    }
  }

  const handleExpand: TreeProps['onExpand'] = (keys, info) => {
    setExpandedKeys(keys as React.Key[])
    
    // 如果是展开操作（新增的key），触发文件夹文档加载
    if (info.expanded) {
      const nodeData = (info.node as any)?.data as FileTreeNode['data'] | undefined
      if (nodeData?.type === 'folder' && nodeData.folder) {
        onExpandFolder?.(nodeData.folder)
      }
    }
  }

  return (
    <div className="file-tree-container">
      {/* 返回按钮 */}
      <div className="file-tree-header">
        <button 
          className="back-to-list-btn"
          onClick={() => {
            if (onBackToList) {
              onBackToList()
            } else {
              window.location.href = '/novels'
            }
          }}
          title="返回作品列表"
        >
          <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span>返回</span>
        </button>
      </div>
      
      <div className="file-tree-toolbar">
        {analysisMode ? (
          <div className="analysis-config-panel">
            <div className="analysis-header">
              <div className="analysis-title">小说章节拆解设置</div>
              <Button type="text" size="small" onClick={() => onAnalysisModeClose?.()}>关闭</Button>
            </div>
            <div className="analysis-note">
              <div className="note-title">建议拆解100万字以内，字数越多，分析越耗时。</div>
              <div className="note-sub">每个维度每拆解10万字约耗费5000字</div>
            </div>
            <div className="chapter-range">
              <div className="range-title">章节拆解范围</div>
              <div className="range-inputs">
                <Input
                  value={range[0]}
                  onChange={(e) => {
                    const v = Math.max(1, Math.min(Number(e.target.value) || 1, range[1]))
                    setRange([v, range[1]])
                  }}
                />
                <div className="range-slider">
                  <Slider
                    range
                    min={1}
                    max={maxChapters}
                    value={range as any}
                    onChange={(val: any) => setRange(val as [number, number])}
                  />
                </div>
                <Input
                  value={range[1]}
                  onChange={(e) => {
                    const v = Math.min(maxChapters, Math.max(Number(e.target.value) || 1, range[0]))
                    setRange([range[0], v])
                  }}
                />
              </div>
              <div className="range-summary">选择范围：第 {range[0]} 章 - 第 {range[1]} 章（总计 {range[1]-range[0]+1} 章）</div>
            </div>
            <div className="analysis-actions">
              <div className="analysis-hint">一次仅拆解一个维度，结果更加聚焦</div>
              <Button
                type="primary"
                disabled={!selectedAnalysisTypeKey}
                onClick={() => {
                  if (!selectedAnalysisTypeKey) return
                  onStartAnalysis?.({
                    analysisType: selectedAnalysisTypeKey,
                    startChapter: range[0],
                    endChapter: range[1],
                  })
                }}
              >
                开始拆解
              </Button>
            </div>
          </div>
        ) : searchMode ? (
          <div className="file-tree-search-wrapper">
            <Input
              className="file-tree-search-input"
              placeholder="搜索文档内容..."
              prefix={<SearchOutlined />}
              value={searchKeyword}
              allowClear
              autoFocus
              onChange={(e) => {
                const value = e.target.value
                setSearchKeyword(value)
                if (isSearchComposingRef.current) {
                  return
                }
                triggerSearch(value)
              }}
              onCompositionStart={() => {
                isSearchComposingRef.current = true
              }}
              onCompositionEnd={(e) => {
                isSearchComposingRef.current = false
                const value = e.currentTarget.value
                setSearchKeyword(value)
                triggerSearch(value)
              }}
            />
            <Button
              type="text"
              size="small"
              onClick={() => {
                setSearchMode(false)
                setSearchKeyword('')
                onSearchClear?.()
              }}
            >
              取消
            </Button>
          </div>
          ) : (
            <div className="file-tree-toolbar-actions">
              <div className="file-tree-toolbar-secondary">
                <Tooltip title="搜索">
                  <Button
                    type="text"
                    icon={<SearchOutlined />}
                    onClick={() => setSearchMode(true)}
                  />
                </Tooltip>
                <Button
                  type="text"
                  size="small"
                  className="file-tree-new-chapter-btn"
                  icon={<FileTextOutlined />}
                  onClick={() => {
                    // 针对“主要内容”虚拟文件夹，优先使用 onQuickAddChapter
                    const mainFolder: NovelFolder = {
                      id: -999,
                      novelId: 0,
                      folderName: '主要内容',
                      parentId: null,
                      sortOrder: 0,
                      isSystem: true,
                    } as any
                    onQuickAddChapter?.(mainFolder)
                  }}
                >
                  新增章节
                </Button>
                <Tooltip title="新建文档">
                  <Button
                    type="text"
                    icon={<FileAddOutlined />}
                    onClick={() => {
                      // 尝试确定目标文件夹
                      let targetFolderId: number | null = null
                      
                      if (selectedKey && selectedKey.startsWith('folder-')) {
                        targetFolderId = parseInt(selectedKey.replace('folder-', ''))
                      } else if (selectedKey && selectedKey.startsWith('doc-')) {
                        const docId = parseInt(selectedKey.replace('doc-', ''))
                        const doc = documents.find(d => d.id === docId)
                        if (doc) targetFolderId = doc.folderId
                      }
                      
                      // 如果没有选中或选中无效，尝试使用第一个普通文件夹
                      if (targetFolderId === null || targetFolderId === -999) { // -999 is Main Content
                         const firstFolder = folders.find(f => !f.isSystem && f.id !== -999)
                         if (firstFolder) targetFolderId = firstFolder.id
                      }

                      if (targetFolderId !== null && targetFolderId !== -999) {
                        const tempDocKey = `temp-doc-${Date.now()}`
                        setCreatingNewDocument({ folderId: targetFolderId, tempKey: tempDocKey })
                        setEditingKey(tempDocKey)
                        setEditingName('新建文档')
                        setEditingType('document')
                        
                        const folderKey = `folder-${targetFolderId}`
                        if (!expandedKeys.includes(folderKey)) {
                          setExpandedKeys([...expandedKeys, folderKey])
                        }
                        setTimeout(() => editInputRef.current?.select(), 0)
                      } else {
                        onToolbarCreateDocument?.()
                      }
                    }}
                  />
                </Tooltip>
                <Tooltip title="新建文件夹">
                  <Button
                    type="text"
                    icon={<FolderAddOutlined />}
                    onClick={() => onToolbarCreateFolder?.()}
                  />
                </Tooltip>
              </div>
            </div>
          )}
      </div>
      {!searchMode && !analysisMode && (
        <Tree
          className="file-tree"
          treeData={treeData}
          expandedKeys={expandedKeys}
          onExpand={handleExpand}
          selectedKeys={selectedKey ? [selectedKey] : []}
          onSelect={handleSelect}
          showIcon={true}
          onRightClick={({ event, node }) => {
            event.preventDefault()
            const nodeData = (node as any)?.data as FileTreeNode['data'] | undefined
            if (nodeData?.type === 'folder' && nodeData.folder) {
              handleContextMenu(event as any, 'folder', nodeData.folder)
            } else if (nodeData?.type === 'document' && nodeData.document) {
              handleContextMenu(event as any, 'document', nodeData.document)
            } else if (nodeData?.type === 'chapter' && nodeData.chapter) {
              handleContextMenu(event as any, 'chapter', nodeData.chapter)
            }
          }}
          titleRender={(nodeData: any) => {
            const node = nodeData as FileTreeNode
            
            if (node.data?.type === 'root') {
              return <span className="file-tree-root-title">{node.title}</span>
            }
            
            if (node.data?.type === 'folder' && node.data.folder) {
              const isMainContent = node.key === 'main-content'
              const isEditing = editingKey === node.key

              return (
                <div className="file-tree-node">
                  {isEditing ? (
                    <input
                      ref={editInputRef}
                      className="file-tree-inline-input"
                      value={editingName}
                      autoFocus
                      onChange={(e) => setEditingName(e.target.value)}
                      onBlur={handleEditBlur}
                      onFocus={handleEditFocus}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          if (blurTimeoutRef.current) {
                            clearTimeout(blurTimeoutRef.current)
                            blurTimeoutRef.current = null
                          }
                          handleEditComplete()
                        } else if (e.key === 'Escape') {
                          e.preventDefault()
                          handleEditCancel()
                        }
                      }}
                      onClick={(e) => e.stopPropagation()}
                      onMouseDown={(e) => e.stopPropagation()}
                    />
                  ) : (
                    <span className="file-tree-node-title">{node.title}</span>
                  )}
                  {!isEditing && (
                    <span className="file-tree-node-actions">
                      <Tooltip title={isMainContent ? "新增章节" : "新增文档"}>
                        <Button
                          type="text"
                          size="small"
                          icon={<FileAddOutlined />}
                          onClick={(e) => {
                            e.preventDefault()
                            e.stopPropagation()
                            if (node.data?.folder) {
                              if (isMainContent) {
                                onQuickAddChapter?.(node.data.folder)
                              } else {
                                // 创建临时文档节点，进入编辑状态
                                const tempDocKey = `temp-doc-${Date.now()}`
                                setCreatingNewDocument({ folderId: node.data.folder.id, tempKey: tempDocKey })
                                setEditingKey(tempDocKey)
                                setEditingName('新建文档')
                                setEditingType('document')
                                // 自动展开父文件夹
                                const folderKey = `folder-${node.data.folder.id}`
                                if (!expandedKeys.includes(folderKey)) {
                                  setExpandedKeys([...expandedKeys, folderKey])
                                }
                                setTimeout(() => editInputRef.current?.select(), 0)
                              }
                            }
                          }}
                        />
                      </Tooltip>
                    </span>
                  )}
                </div>
              )
            }
            
            if (node.data?.type === 'chapter' && node.data.chapter) {
              const isEditing = editingKey === node.key

              return (
                <div className="file-tree-node">
                  {isEditing ? (
                    <input
                      ref={editInputRef}
                      className="file-tree-inline-input"
                      value={editingName}
                      autoFocus
                      onChange={(e) => setEditingName(e.target.value)}
                      onBlur={handleEditBlur}
                      onFocus={handleEditFocus}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          if (blurTimeoutRef.current) {
                            clearTimeout(blurTimeoutRef.current)
                            blurTimeoutRef.current = null
                          }
                          handleEditComplete()
                        } else if (e.key === 'Escape') {
                          e.preventDefault()
                          handleEditCancel()
                        }
                      }}
                      onClick={(e) => e.stopPropagation()}
                      onMouseDown={(e) => e.stopPropagation()}
                    />
                  ) : (
                    <span className="file-tree-node-title">{node.title}</span>
                  )}
                </div>
              )
            }

            // 文档
            if (node.data?.type === 'document' && node.data.document) {
              const isEditing = editingKey === node.key
              
              return (
                <div className="file-tree-node">
                  {isEditing ? (
                    <input
                      ref={editInputRef}
                      className="file-tree-inline-input"
                      value={editingName}
                      autoFocus
                      onChange={(e) => setEditingName(e.target.value)}
                      onBlur={handleEditBlur}
                      onFocus={handleEditFocus}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          if (blurTimeoutRef.current) {
                            clearTimeout(blurTimeoutRef.current)
                            blurTimeoutRef.current = null
                          }
                          handleEditComplete()
                        } else if (e.key === 'Escape') {
                          e.preventDefault()
                          handleEditCancel()
                        }
                      }}
                      onClick={(e) => e.stopPropagation()}
                      onMouseDown={(e) => e.stopPropagation()}
                    />
                  ) : (
                    <span className="file-tree-node-title">{node.title}</span>
                  )}
                </div>
              )
            }
            
            return <span className="file-tree-node-title">{node.title}</span>
          }}
        />
      )}
      {analysisMode && (
        <div className="analysis-types-list">
          {ANALYSIS_TYPES.map((type) => {
            const checked = selectedAnalysisTypeKey === type.key
            return (
              <div
                key={type.key}
                className={`analysis-type-card ${checked ? 'selected' : ''}`}
                onClick={() => {
                  setSelectedAnalysisTypeKey(type.key)
                  onAnalysisTypeSelect?.(type.key)
                }}
              >
                <Checkbox
                  checked={checked}
                  onChange={(e) => {
                    e.stopPropagation()
                    setSelectedAnalysisTypeKey(type.key)
                    onAnalysisTypeSelect?.(type.key)
                  }}
                />
                <div className="analysis-type-info">
                  <div className="analysis-type-name">{type.name}</div>
                  <div className="analysis-type-desc">{type.description}</div>
                </div>
                <div className="analysis-type-arrow">→</div>
              </div>
            )
          })}
        </div>
      )}
      {searchMode && (
        <div className="file-tree-search-results">
          {documents.length === 0 ? (
            <div className="search-empty">请输入关键词进行搜索</div>
          ) : (
            <div className="search-result-list">
              {documents.map((doc) => (
                <div
                  key={doc.id}
                  className="search-result-item"
                  onClick={() => onSelectDocument(doc)}
                  onContextMenu={(e) => handleContextMenu(e, 'document', doc)}
                >
                  <FileTextOutlined className="search-result-icon" />
                  <div className="search-result-content">
                    <div className="search-result-title">{doc.title}</div>
                    <div className="search-result-meta">
                      {doc.wordCount}字 · {new Date(doc.updatedAt).toLocaleDateString()}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 自定义右键菜单 */}
      {contextMenu.visible && (
        <div
          ref={contextMenuRef}
          className="custom-context-menu"
          style={{
            left: `${contextMenu.x}px`,
            top: `${contextMenu.y}px`,
          }}
        >
          {contextMenu.type === 'folder' && contextMenu.folder && (() => {
            const isSystemFolder = contextMenu.folder.isSystem === true
            const isMainContentFolder = contextMenu.folder.id === -999
            console.log('显示菜单:', contextMenu.folder.folderName, 'isSystem:', contextMenu.folder.isSystem, 'isSystemFolder:', isSystemFolder, 'isMainContent:', isMainContentFolder)
            return (
              <>
                <div className="context-menu-item" onClick={() => handleMenuAction('newDoc')}>
                  <FileAddOutlined className="context-menu-icon" />
                  <span>{isMainContentFolder ? '新建章节' : '新建文档'}</span>
                </div>
                {!isMainContentFolder && (
                  <div className="context-menu-item" onClick={() => handleMenuAction('newFolder')}>
                    <FolderAddOutlined className="context-menu-icon" />
                    <span>新建文件夹</span>
                  </div>
                )}
                {!isSystemFolder && !isMainContentFolder && (
                  <>
                    <div className="context-menu-divider" />
                    <div className="context-menu-item" onClick={() => handleMenuAction('rename')}>
                      <EditOutlined className="context-menu-icon" />
                      <span>重命名</span>
                    </div>
                    <div className="context-menu-item context-menu-item-danger" onClick={() => handleMenuAction('delete')}>
                      <DeleteOutlined className="context-menu-icon" />
                      <span>删除</span>
                    </div>
                  </>
                )}
              </>
            )
          })()}
          {contextMenu.type === 'document' && (
            <>
              <div className="context-menu-item" onClick={() => handleMenuAction('rename')}>
                <EditOutlined className="context-menu-icon" />
                <span>重命名</span>
              </div>
              <div className="context-menu-item context-menu-item-danger" onClick={() => handleMenuAction('delete')}>
                <DeleteOutlined className="context-menu-icon" />
                <span>删除</span>
              </div>
            </>
          )}
          {contextMenu.type === 'chapter' && (
            <>
              <div className="context-menu-item" onClick={() => handleMenuAction('rename')}>
                <EditOutlined className="context-menu-icon" />
                <span>重命名</span>
              </div>
              <div className="context-menu-item context-menu-item-danger" onClick={() => handleMenuAction('delete')}>
                <DeleteOutlined className="context-menu-icon" />
                <span>删除</span>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}

export default FileTree
