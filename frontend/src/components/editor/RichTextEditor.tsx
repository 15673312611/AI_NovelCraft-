import React, { useEffect, useRef, useState } from 'react'
import ReactQuill from 'react-quill'
import 'react-quill/dist/quill.snow.css'
import { Card, Typography, Space, Button, message } from 'antd'
import { SaveOutlined, EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons'
import './RichTextEditor.css'

const { Text } = Typography

interface RichTextEditorProps {
  value: string
  onChange: (content: string) => void
  onSave?: () => void
  placeholder?: string
  readOnly?: boolean
  autoSave?: boolean
  autoSaveInterval?: number
  showToolbar?: boolean
  showWordCount?: boolean
  className?: string
}

const RichTextEditor: React.FC<RichTextEditorProps> = ({
  value,
  onChange,
  onSave,
  placeholder = '开始创作您的小说...',
  readOnly = false,
  autoSave = true,
  autoSaveInterval = 30000, // 30秒
  showToolbar = true,
  showWordCount = true,
  className = ''
}) => {
  const [isSaving, setIsSaving] = useState(false)
  const [lastSaved, setLastSaved] = useState<Date | null>(null)
  const [wordCount, setWordCount] = useState(0)
  const [showPreview, setShowPreview] = useState(false)
  const autoSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 计算字数
  const calculateWordCount = (content: string) => {
    const text = content.replace(/<[^>]*>/g, '') // 移除HTML标签
    return text.trim().length
  }

  // 自动保存
  const handleAutoSave = async () => {
    if (!autoSave || !onSave || isSaving) return
    
    try {
      setIsSaving(true)
      await onSave()
      setLastSaved(new Date())
      message.success('自动保存成功')
    } catch (error) {
      message.error('自动保存失败')
    } finally {
      setIsSaving(false)
    }
  }

  // 手动保存
  const handleManualSave = async () => {
    if (!onSave) return
    
    try {
      setIsSaving(true)
      await onSave()
      setLastSaved(new Date())
      message.success('保存成功')
    } catch (error) {
      message.error('保存失败')
    } finally {
      setIsSaving(false)
    }
  }

  // 内容变化处理
  const handleChange = (content: string) => {
    onChange(content)
    setWordCount(calculateWordCount(content))
    
    // 重置自动保存定时器
    if (autoSave && autoSaveTimerRef.current) {
      clearTimeout(autoSaveTimerRef.current)
    }
    
    if (autoSave) {
      autoSaveTimerRef.current = setTimeout(handleAutoSave, autoSaveInterval)
    }
  }

  // 清理定时器
  useEffect(() => {
    return () => {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
    }
  }, [])

  // 初始化字数统计
  useEffect(() => {
    setWordCount(calculateWordCount(value))
  }, [value])

  // Quill编辑器配置
  const modules = {
    toolbar: showToolbar ? [
      [{ 'header': [1, 2, 3, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      [{ 'color': [] }, { 'background': [] }],
      [{ 'list': 'ordered'}, { 'list': 'bullet' }],
      [{ 'indent': '-1'}, { 'indent': '+1' }],
      [{ 'align': [] }],
      ['link', 'image'],
      ['clean']
    ] : false,
    clipboard: {
      matchVisual: false
    }
  }

  const formats = [
    'header',
    'bold', 'italic', 'underline', 'strike',
    'color', 'background',
    'list', 'bullet',
    'indent',
    'align',
    'link', 'image'
  ]

  return (
    <div className={`rich-text-editor ${className}`}>
      <Card className="editor-card">
        <div className="editor-header">
          <Space>
            {onSave && (
              <Button
                type="primary"
                icon={<SaveOutlined />}
                onClick={handleManualSave}
                loading={isSaving}
                size="small"
              >
                保存
              </Button>
            )}
            <Button
              icon={showPreview ? <EyeInvisibleOutlined /> : <EyeOutlined />}
              onClick={() => setShowPreview(!showPreview)}
              size="small"
            >
              {showPreview ? '隐藏预览' : '预览'}
            </Button>
          </Space>
          
          <Space>
            {showWordCount && (
              <Text type="secondary" className="word-count">
                字数: {wordCount}
              </Text>
            )}
            {lastSaved && (
              <Text type="secondary" className="last-saved">
                最后保存: {lastSaved.toLocaleTimeString()}
              </Text>
            )}
          </Space>
        </div>

        <div className="editor-content">
          {showPreview ? (
            <div 
              className="preview-content"
              dangerouslySetInnerHTML={{ __html: value }}
            />
          ) : (
            <ReactQuill
              theme="snow"
              value={value}
              onChange={handleChange}
              modules={modules}
              formats={formats}
              placeholder={placeholder}
              readOnly={readOnly}
              className="quill-editor"
            />
          )}
        </div>
      </Card>
    </div>
  )
}

export default RichTextEditor 