import { useState, useEffect, useRef, useCallback } from 'react'
import { Button, Switch, Input, message, Spin, Row, Col, Typography } from 'antd'
import {
  SaveOutlined, NotificationOutlined, CheckCircleOutlined, MobileOutlined,
  BoldOutlined, ItalicOutlined, UnorderedListOutlined, OrderedListOutlined,
  LinkOutlined
} from '@ant-design/icons'
import styled from '@emotion/styled'
import { motion } from 'framer-motion'
import request from '@/services/request'
import { PageContainer, InfoCard } from '@/components'

const { Text } = Typography
const { TextArea } = Input

// Filter out transient props (starting with $) from being passed to DOM
const shouldForwardProp = (prop: string) => !prop.startsWith('$')

// Styled Components
const EditorContainer = styled.div`
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.02);
`

const EditorToolbar = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-wrap: wrap;
`

const ToolbarButton = styled('button', { shouldForwardProp })<{ $active?: boolean }>`
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${props => props.$active ? 'rgba(14, 165, 233, 0.15)' : 'transparent'};
  border: 1px solid ${props => props.$active ? 'rgba(14, 165, 233, 0.3)' : 'transparent'};
  border-radius: 6px;
  color: ${props => props.$active ? '#0ea5e9' : 'rgba(250, 250, 250, 0.65)'};
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  
  &:hover {
    background: rgba(255, 255, 255, 0.08);
    color: #fafafa;
  }
`

const ToolbarDivider = styled.div`
  width: 1px;
  height: 20px;
  background: rgba(255, 255, 255, 0.1);
  margin: 0 8px;
`

const StyledTextArea = styled(TextArea)`
  &.ant-input {
    background: transparent !important;
    border: none !important;
    border-radius: 0 !important;
    resize: none !important;
    font-family: 'SF Mono', Monaco, Consolas, monospace;
    font-size: 14px;
    line-height: 1.8;
    padding: 16px;
    
    &:focus {
      box-shadow: none !important;
    }
  }
`

const PreviewContainer = styled.div`
  height: 100%;
  display: flex;
  flex-direction: column;
`

const DesktopFrame = styled.div`
  width: 100%;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 16px;
  padding: 20px;
  min-height: 580px;
  display: flex;
  align-items: center;
  justify-content: center;
`

const DesktopModal = styled.div`
  width: 100%;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  position: relative;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: linear-gradient(90deg, #6366f1 0%, #8b5cf6 50%, #a855f7 100%);
  }
`

const DesktopModalHeader = styled.div`
  padding: 24px 24px 0;
  text-align: center;
`

const DesktopModalTitle = styled.h2`
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
  padding: 0 24px;
`

const DesktopModalBody = styled.div`
  padding: 0 24px 20px;
  min-height: 320px;
  max-height: 380px;
  overflow-y: auto;
  
  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-track {
    background: #f1f5f9;
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 3px;
  }
  
  h1 {
    font-size: 17px;
    font-weight: 700;
    color: #0f172a;
    margin: 20px 0 10px;
    padding-bottom: 8px;
    border-bottom: 2px solid #e2e8f0;
  }
  
  h2 {
    font-size: 15px;
    font-weight: 600;
    color: #1e293b;
    margin: 16px 0 8px;
    padding-left: 10px;
    border-left: 3px solid #6366f1;
  }
  
  h3 {
    font-size: 14px;
    font-weight: 600;
    color: #334155;
    margin: 14px 0 6px;
  }
  
  p {
    color: #374151;
    line-height: 1.8;
    margin: 0 0 12px;
    font-size: 13px;
  }
  
  strong, b {
    color: #1e293b;
    font-weight: 600;
  }
  
  em, i {
    font-style: italic;
    color: #475569;
  }
  
  ul, ol {
    padding-left: 0;
    margin: 12px 0;
    list-style: none;
  }
  
  li {
    position: relative;
    padding-left: 20px;
    margin: 8px 0;
    color: #374151;
    font-size: 13px;
    
    &::before {
      content: '';
      position: absolute;
      left: 4px;
      top: 7px;
      width: 5px;
      height: 5px;
      background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
      border-radius: 50%;
    }
  }
  
  a {
    color: #6366f1;
    text-decoration: none;
    font-weight: 500;
    
    &:hover {
      text-decoration: underline;
    }
  }
  
  blockquote {
    margin: 14px 0;
    padding: 12px 14px;
    background: #f8fafc;
    border-left: 3px solid #6366f1;
    border-radius: 0 10px 10px 0;
    color: #475569;
    font-size: 13px;
    
    p {
      margin: 0;
    }
  }
  
  code {
    padding: 2px 5px;
    background: #f1f5f9;
    border: 1px solid #e2e8f0;
    border-radius: 4px;
    font-family: 'SF Mono', Monaco, Consolas, monospace;
    font-size: 11px;
    color: #7c3aed;
  }
`

const DesktopModalFooter = styled.div`
  padding: 16px 24px 24px;
  display: flex;
  justify-content: flex-end;
`

const DesktopConfirmButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  border-radius: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 20px -4px rgba(99, 102, 241, 0.4);
  transition: all 0.2s;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 28px -4px rgba(99, 102, 241, 0.5);
  }
  
  svg {
    width: 14px;
    height: 14px;
  }
`

const StatusAlert = styled('div', { shouldForwardProp })<{ $enabled?: boolean }>`
  padding: 14px 18px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  background: ${props => props.$enabled ? 'rgba(34, 197, 94, 0.1)' : 'rgba(148, 163, 184, 0.1)'};
  border: 1px solid ${props => props.$enabled ? 'rgba(34, 197, 94, 0.2)' : 'rgba(148, 163, 184, 0.2)'};
`

const ActionBar = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
`

const HelpText = styled.div`
  margin-top: 12px;
  padding: 12px 16px;
  background: rgba(14, 165, 233, 0.08);
  border: 1px solid rgba(14, 165, 233, 0.15);
  border-radius: 8px;
  font-size: 12px;
  color: rgba(250, 250, 250, 0.65);
  line-height: 1.8;
  
  code {
    background: rgba(255, 255, 255, 0.1);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'SF Mono', Monaco, Consolas, monospace;
    color: #0ea5e9;
  }
`

const AnnouncementPage = () => {
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [enabled, setEnabled] = useState(false)
  const [title, setTitle] = useState('系统公告')
  const [content, setContent] = useState('')
  const [updatedAt, setUpdatedAt] = useState('')
  const textAreaRef = useRef<any>(null)

  useEffect(() => {
    loadAnnouncement()
  }, [])

  const loadAnnouncement = async () => {
    setLoading(true)
    try {
      const res: any = await request.get('/announcement')
      setEnabled(res?.enabled || false)
      setTitle(res?.title || '系统公告')
      setContent(res?.content || '')
      setUpdatedAt(res?.updatedAt || '')
    } catch (error) {
      message.error('加载公告配置失败')
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    if (!title.trim()) {
      message.warning('请输入公告标题')
      return
    }
    setSaving(true)
    try {
      await request.post('/announcement', { enabled, title, content })
      message.success('公告保存成功')
      loadAnnouncement()
    } catch (error) {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handleToggle = async (checked: boolean) => {
    try {
      await request.post('/announcement', { enabled: checked, title, content })
      setEnabled(checked)
      message.success(checked ? '公告已开启' : '公告已关闭')
    } catch (error) {
      message.error('操作失败')
    }
  }

  // 插入HTML标签
  const insertTag = useCallback((startTag: string, endTag: string) => {
    const textarea = textAreaRef.current?.resizableTextArea?.textArea
    if (!textarea) return
    
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = content.substring(start, end)
    const newText = content.substring(0, start) + startTag + selectedText + endTag + content.substring(end)
    
    setContent(newText)
    
    // 恢复光标位置
    setTimeout(() => {
      textarea.focus()
      const newCursorPos = start + startTag.length + selectedText.length + endTag.length
      textarea.setSelectionRange(newCursorPos, newCursorPos)
    }, 0)
  }, [content])

  const insertBold = () => insertTag('<strong>', '</strong>')
  const insertItalic = () => insertTag('<em>', '</em>')
  const insertUL = () => insertTag('<ul>\n  <li>', '</li>\n</ul>')
  const insertOL = () => insertTag('<ol>\n  <li>', '</li>\n</ol>')
  const insertLink = () => {
    const url = prompt('请输入链接地址:', 'https://')
    if (url) {
      insertTag(`<a href="${url}">`, '</a>')
    }
  }
  const insertH2 = () => insertTag('<h2>', '</h2>')
  const insertH3 = () => insertTag('<h3>', '</h3>')
  const insertParagraph = () => insertTag('<p>', '</p>')

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 400 }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <PageContainer
      title="公告管理"
      description="配置首页弹窗公告，用户每天首次访问时显示"
      icon={<NotificationOutlined />}
      breadcrumb={[{ title: '公告管理' }]}
    >
      <Row gutter={24}>
        {/* 左侧：编辑区域 */}
        <Col xs={24} lg={14}>
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4 }}
          >
            <InfoCard
              title="公告设置"
              description={updatedAt ? `最后更新: ${updatedAt}` : '尚未发布公告'}
              icon={<NotificationOutlined />}
              iconGradient="linear-gradient(135deg, #f97316, #ea580c)"
              extra={
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <Text style={{ fontSize: 14, color: 'rgba(250, 250, 250, 0.65)' }}>公告开关</Text>
                  <Switch 
                    checked={enabled} 
                    onChange={handleToggle}
                    checkedChildren="开启"
                    unCheckedChildren="关闭"
                  />
                </div>
              }
            >
              {/* 状态提示 */}
              <StatusAlert $enabled={enabled}>
                <CheckCircleOutlined style={{ 
                  fontSize: 18, 
                  color: enabled ? '#22c55e' : '#64748b' 
                }} />
                <Text style={{ 
                  fontSize: 13, 
                  color: enabled ? '#22c55e' : '#64748b',
                  fontWeight: 500
                }}>
                  {enabled ? '公告已开启，用户访问首页时会看到弹窗' : '公告已关闭，用户不会看到弹窗'}
                </Text>
              </StatusAlert>

              {/* 公告标题 */}
              <div style={{ marginBottom: 20 }}>
                <Text style={{ 
                  fontSize: 13, 
                  fontWeight: 500, 
                  color: 'rgba(250, 250, 250, 0.65)',
                  display: 'block',
                  marginBottom: 8
                }}>
                  公告标题
                </Text>
                <Input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="请输入公告标题"
                  size="large"
                  maxLength={50}
                  showCount
                />
              </div>

              {/* HTML编辑器 */}
              <div style={{ marginBottom: 20 }}>
                <Text style={{ 
                  fontSize: 13, 
                  fontWeight: 500, 
                  color: 'rgba(250, 250, 250, 0.65)',
                  display: 'block',
                  marginBottom: 8
                }}>
                  公告内容 (支持 HTML)
                </Text>
                <EditorContainer>
                  <EditorToolbar>
                    <ToolbarButton onClick={insertH2} title="标题2">
                      H2
                    </ToolbarButton>
                    <ToolbarButton onClick={insertH3} title="标题3">
                      H3
                    </ToolbarButton>
                    <ToolbarButton onClick={insertParagraph} title="段落">
                      P
                    </ToolbarButton>
                    <ToolbarDivider />
                    <ToolbarButton onClick={insertBold} title="粗体">
                      <BoldOutlined />
                    </ToolbarButton>
                    <ToolbarButton onClick={insertItalic} title="斜体">
                      <ItalicOutlined />
                    </ToolbarButton>
                    <ToolbarDivider />
                    <ToolbarButton onClick={insertUL} title="无序列表">
                      <UnorderedListOutlined />
                    </ToolbarButton>
                    <ToolbarButton onClick={insertOL} title="有序列表">
                      <OrderedListOutlined />
                    </ToolbarButton>
                    <ToolbarDivider />
                    <ToolbarButton onClick={insertLink} title="链接">
                      <LinkOutlined />
                    </ToolbarButton>
                  </EditorToolbar>
                  <StyledTextArea
                    ref={textAreaRef}
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    placeholder="<p>请输入公告内容...</p>"
                    rows={12}
                  />
                </EditorContainer>
                <HelpText>
                  💡 支持 HTML 标签：<code>&lt;p&gt;</code> 段落、<code>&lt;strong&gt;</code> 粗体、
                  <code>&lt;em&gt;</code> 斜体、<code>&lt;h2&gt;</code> <code>&lt;h3&gt;</code> 标题、
                  <code>&lt;ul&gt;</code> <code>&lt;ol&gt;</code> 列表、<code>&lt;a href=""&gt;</code> 链接
                </HelpText>
              </div>

              {/* 操作按钮 */}
              <ActionBar>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  onClick={handleSave}
                  loading={saving}
                  size="large"
                  style={{ 
                    background: 'linear-gradient(135deg, #f97316, #ea580c)',
                    minWidth: 120
                  }}
                >
                  保存公告
                </Button>
              </ActionBar>
            </InfoCard>
          </motion.div>
        </Col>

        {/* 右侧：手机预览 */}
        <Col xs={24} lg={10}>
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.1 }}
          >
            <InfoCard
              title="电脑端预览"
              description="实时预览公告在客户端的显示效果"
              icon={<MobileOutlined />}
              iconGradient="linear-gradient(135deg, #0ea5e9, #06b6d4)"
            >
              <PreviewContainer>
                <DesktopFrame>
                  <DesktopModal>
                    <DesktopModalHeader>
                      <DesktopModalTitle>
                        {title || '系统公告'}
                      </DesktopModalTitle>
                    </DesktopModalHeader>
                    
                    <DesktopModalBody 
                      dangerouslySetInnerHTML={{ 
                        __html: content || '<p style="color: #94a3b8; text-align: center;">暂无内容</p>' 
                      }} 
                    />
                    
                    <DesktopModalFooter>
                      <DesktopConfirmButton>
                        <span>我知道了</span>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M5 12h14M12 5l7 7-7 7" />
                        </svg>
                      </DesktopConfirmButton>
                    </DesktopModalFooter>
                  </DesktopModal>
                </DesktopFrame>
              </PreviewContainer>
            </InfoCard>
          </motion.div>
        </Col>
      </Row>
    </PageContainer>
  )
}

export default AnnouncementPage
