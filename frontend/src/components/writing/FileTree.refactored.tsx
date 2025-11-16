import React, { useMemo, useState, useEffect, useRef } from 'react'
import { Tree, Button, Input, Tooltip } from 'antd'
import { 
  FolderAddOutlined, 
  FileAddOutlined, 
  SearchOutlined, 
  DeleteOutlined, 
  EditOutlined,
  FolderOutlined,
  FileTextOutlined
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
  onSelectDocument: (document: NovelDocument) => void
  onSelectChapter: (chapter: Chapter) => void
  onSelectFolder?: (folder: NovelFolder | null) => void
  onCreateFolder?: (parentFolder?: NovelFolder | null, folderName?: string) => void
  onCreateDocument?: (folder: NovelFolder) => void
  onQuickAddChapter?: () => void
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
  onSelectDocument,
  onSelectChapter,
  onSelectFolder,
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
}) => {
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searchMode, setSearchMode] = useState(false)
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
  const contextMenuRef = useRef<HTMLDivElement>(null)
  const editInputRef = useRef<HTMLInputElement>(null)

  // 中文数字转换
  const numToChinese = (num: number): string => {
    if (num <= 10) {
      return ['', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'][num]
    } else if (num < 20) {
      return '十' + ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'][num - 10]
    } else if (num < 100) {
      const tens = Math.floor(num / 10)
      const ones = num % 10
      return ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'][tens] + 
             '十' + 
             (ones > 0 ? ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'][ones] : '')
    }
    return String(num)
  }

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
    
    setContextMenu({
      visible: true,
      x: e.clientX,
      y: e.clientY,
      type,
      ...(type === 'folder' ? { folder: item as NovelFolder } : 
         type === 'document' ? { document: item as NovelDocument } :
         { chapter: item as Chapter }),
    })
  }

  const handleMenuAction = (action: string) => {
    const { type, folder, document, chapter } = contextMenu
    setContextMenu({ visible: false, x: 0, y: 0, type: null })

    if (type === 'folder' && folder) {
      switch (action) {
        case 'newDoc':
          onCreateDocument?.(folder)
          break
        case 'newFolder':
          const tempKey = `temp-folder-${Date.now()}`
          setCreatingNewFolder({ parentId: folder.id, tempKey })
          setEditingKey(tempKey)
          setEditingName('新建文件夹')
          setEditingType('folder')
          setTimeout(() => editInputRef.current?.select(), 0)
          break
        case 'rename':
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
          setEditingKey(`doc-${document.id}`)
          setEditingName(document.title)
          setEditingType('document')
          setTimeout(() => editInputRef.current?.select(), 0)
          break
        case 'delete':
          onDeleteDocument?.(document)
          break
      }
    } else if (type === 'chapter' && chapter) {
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
      return
    }

    if (editingType === 'folder') {
      if (creatingNewFolder && editingKey === creatingNewFolder.tempKey) {
        const parentFolder = folders.find(f => f.id === creatingNewFolder.parentId)
        if (parentFolder) {
          onCreateFolder?.(parentFolder, editingName.trim())
        } else {
          onCreateFolder?.(null, editingName.trim())
        }
        setCreatingNewFolder(null)
      } else {
        const folderId = parseInt(editingKey.replace('folder-', ''))
        const folder = folders.find(f => f.id === folderId)
        if (folder && editingName !== folder.folderName) {
          onRenameFolder?.(folder, editingName.trim())
        }
      }
    } else if (editingType === 'document') {
      const docId = parseInt(editingKey.replace('doc-', ''))
      const doc = documents.find(d => d.id === docId)
      if (doc && editingName !== doc.title) {
        onRenameDocument?.(doc, editingName.trim())
      }
    } else if (editingType === 'chapter') {
      const chapterId = parseInt(editingKey.replace('chapter-', ''))
      const chapter = chapters.find(c => c.id === chapterId)
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
    setEditingKey(null)
    setEditingName('')
    setEditingType(null)
    setCreatingNewFolder(null)
  }

  const treeData: DataNode[] = useMemo(() => {
    const allKeys: React.Key[] = ['root', 'main-content']
    
    // 构建辅助文档文件夹树
    const buildFolderTree = (parentId: number | null): FileTreeNode[] => {
      const folderNodes = folders
        .filter((folder) => (folder.parentId ?? null) === parentId)
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map<FileTreeNode>((folder) => {
          const folderKey = `folder-${folder.id}`
          allKeys.push(folderKey)
          
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

          return {
            key: folderKey,
            title: folder.folderName,
            icon: <FolderOutlined style={{ color: '#f59e0b' }} />,
            data: {
              type: 'folder',
              folder,
            },
            children: [...childFolders, ...docNodes],
          }
        })
      
      if (creatingNewFolder && creatingNewFolder.parentId === parentId) {
        allKeys.push(creatingNewFolder.tempKey)
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

    // 构建章节节点
    const chapterNodes: FileTreeNode[] = chapters
      .sort((a, b) => (a.chapterNumber || 0) - (b.chapterNumber || 0))
      .map<FileTreeNode>((chapter) => {
        const chapterKey = `chapter-${chapter.id}`
        allKeys.push(chapterKey)
        
        return {
          key: chapterKey,
          title: `第${numToChinese(chapter.chapterNumber || 0)}章 ${chapter.title}`,
          isLeaf: true,
          icon: <FileTextOutlined style={{ color: '#10b981' }} />,
          data: {
            type: 'chapter',
            chapter,
          },
        }
      })

    // 主要内容节点（虚拟节点）
    const mainContentNode: FileTreeNode = {
      key: 'main-content',
      title: '主要内容',
      icon: <FolderOutlined style={{ color: '#8b5cf6' }} />,
      data: {
        type: 'folder',
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

    // 自动展开所有节点
    setExpandedKeys(allKeys)

    return [rootNode] as DataNode[]
  }, [folders, chapters, documents, novelTitle, creatingNewFolder])

  const handleSelect: TreeProps['onSelect'] = (_keys, info) => {
    const nodeData = (info.node as any)?.data as FileTreeNode['data'] | undefined
    const nodeKey = info.node.key as string
    
    if (nodeData?.type === 'chapter' && nodeData.chapter) {
      onSelectChapter(nodeData.chapter)
    } else if (nodeData?.type === 'document' && nodeData.document) {
      onSelectDocument(nodeData.document)
    } else if (nodeData?.type === 'folder' || nodeData?.type === 'root') {
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

  const handleExpand: TreeProps['onExpand'] = (keys) => {
    setExpandedKeys(keys as React.Key[])
  }

  return (
    <div className="file-tree-container">
      {/* 返回按钮 */}
      <div className="file-tree-header">
        <button 
          className="back-to-list-btn"
          onClick={() => window.history.back()}
          title="返回作品列表"
        >
          <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span>返回</span>
        </button>
      </div>
      
      <div className="file-tree-toolbar">
        {searchMode ? (
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
                if (value.trim()) {
                  onSearch?.(value.trim())
                } else {
                  onSearchClear?.()
                }
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
            <Tooltip title="搜索">
              <Button
                type="text"
                icon={<SearchOutlined />}
                onClick={() => setSearchMode(true)}
              />
            </Tooltip>
            <Tooltip title="新建文档">
              <Button
                type="text"
                icon={<FileAddOutlined />}
                onClick={() => onToolbarCreateDocument?.()}
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
        )}
      </div>
      {!searchMode && (
        <Tree
          className="file-tree"
          treeData={treeData}
          expandedKeys={expandedKeys}
          onExpand={handleExpand}
          selectedKeys={selectedKey ? [selectedKey] : []}
          onSelect={handleSelect}
          showIcon={true}
          titleRender={(nodeData: any) => {
            const node = nodeData as FileTreeNode
            
            if (node.data?.type === 'root') {
              return <span className="file-tree-root-title">{node.title}</span>
            }
            
            // 主要内容文件夹
            if (node.key === 'main-content') {
              return (
                <div className="file-tree-node">
                  <span className="file-tree-node-title">主要内容</span>
                  <span className="file-tree-node-actions">
                    <Tooltip title="新增章节">
                      <Button
                        type="text"
                        size="small"
                        icon={<FileAddOutlined />}
                        onClick={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          onQuickAddChapter?.()
                        }}
                      />
                    </Tooltip>
                  </span>
                </div>
              )
            }
            
            // 章节节点
            if (node.data?.type === 'chapter' && node.data.chapter) {
              const isEditing = editingKey === node.key
              const chapter = node.data.chapter
              
              return (
                <div 
                  className="file-tree-node"
                  onContextMenu={(e) => {
                    if (node.data?.chapter) {
                      handleContextMenu(e, 'chapter', node.data.chapter)
                    }
                  }}
                >
                  {isEditing ? (
                    <>
                      <span className="chapter-prefix">第{numToChinese(chapter.chapterNumber || 0)}章 </span>
                      <input
                        ref={editInputRef}
                        className="file-tree-inline-input"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        onBlur={handleEditComplete}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            handleEditComplete()
                          } else if (e.key === 'Escape') {
                            handleEditCancel()
                          }
                        }}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </>
                  ) : (
                    <span className="file-tree-node-title">{node.title}</span>
                  )}
                </div>
              )
            }
            
            // 文件夹
            if (node.data?.type === 'folder' && node.data.folder) {
              const isEditing = editingKey === node.key
              
              return (
                <div 
                  className="file-tree-node"
                  onContextMenu={(e) => {
                    if (node.data?.folder) {
                      handleContextMenu(e, 'folder', node.data.folder)
                    }
                  }}
                >
                  {isEditing ? (
                    <input
                      ref={editInputRef}
                      className="file-tree-inline-input"
                      value={editingName}
                      onChange={(e) => setEditingName(e.target.value)}
                      onBlur={handleEditComplete}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          handleEditComplete()
                        } else if (e.key === 'Escape') {
                          handleEditCancel()
                        }
                      }}
                      onClick={(e) => e.stopPropagation()}
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
                <div 
                  className="file-tree-node"
                  onContextMenu={(e) => {
                    if (node.data?.document) {
                      handleContextMenu(e, 'document', node.data.document)
                    }
                  }}
                >
                  {isEditing ? (
                    <input
                      ref={editInputRef}
                      className="file-tree-inline-input"
                      value={editingName}
                      onChange={(e) => setEditingName(e.target.value)}
                      onBlur={handleEditComplete}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          handleEditComplete()
                        } else if (e.key === 'Escape') {
                          handleEditCancel()
                        }
                      }}
                      onClick={(e) => e.stopPropagation()}
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
            return (
              <>
                <div className="context-menu-item" onClick={() => handleMenuAction('newDoc')}>
                  <FileAddOutlined className="context-menu-icon" />
                  <span>新建文档</span>
                </div>
                <div className="context-menu-item" onClick={() => handleMenuAction('newFolder')}>
                  <FolderAddOutlined className="context-menu-icon" />
                  <span>新建文件夹</span>
                </div>
                {!isSystemFolder && (
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
        </div>
      )}
    </div>
  )
}

export default FileTree

