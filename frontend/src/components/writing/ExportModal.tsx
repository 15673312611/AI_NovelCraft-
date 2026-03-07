import React, { useState, useEffect } from 'react'
import { Modal, Checkbox, Button, message, Row, Col, Card, Typography, Empty } from 'antd'
import type { CheckboxChangeEvent } from 'antd/es/checkbox'
import { 
  DownloadOutlined, 
  FileTextOutlined, 
  FileWordOutlined,
  CheckCircleOutlined
} from '@ant-design/icons'
import { Document, Paragraph, TextRun, AlignmentType, HeadingLevel, Packer } from 'docx'
import { saveAs } from 'file-saver'
import { getChaptersWithContentByNovel, type Chapter } from '@/services/chapterServiceForStudio'

const { Text, Title } = Typography

interface ExportModalProps {
  visible: boolean
  onCancel: () => void
  novelId: number
  novelTitle: string
  chapters: Chapter[]
}

const ExportModal: React.FC<ExportModalProps> = ({
  visible,
  onCancel,
  novelId,
  novelTitle,
  chapters,
}) => {
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [format, setFormat] = useState<'txt' | 'docx'>('txt')
  const [loading, setLoading] = useState(false)
  const [checkAll, setCheckAll] = useState(false)
  const [indeterminate, setIndeterminate] = useState(false)

  useEffect(() => {
    if (visible) {
      // Default select all
      const allIds = chapters.map(c => c.id)
      setSelectedIds(allIds)
      setCheckAll(true)
      setIndeterminate(false)
    }
  }, [visible, chapters])

  const onCheckAllChange = (e: CheckboxChangeEvent) => {
    const allIds = chapters.map(c => c.id)
    setSelectedIds(e.target.checked ? allIds : [])
    setIndeterminate(false)
    setCheckAll(e.target.checked)
  }

  const onChapterCheckChange = (checkedValues: any[]) => {
    const list = checkedValues as number[]
    setSelectedIds(list)
    setIndeterminate(!!list.length && list.length < chapters.length)
    setCheckAll(list.length === chapters.length)
  }

  // Calculate total estimated word count for selected chapters
  const totalSelectedWordCount = chapters
    .filter(c => selectedIds.includes(c.id))
    .reduce((sum, c) => sum + (c.wordCount || 0), 0)

  const handleExport = async () => {
    if (selectedIds.length === 0) {
      message.warning('请至少选择一个章节')
      return
    }

    setLoading(true)
    try {
      // Fetch full content
      const allChaptersWithContent = await getChaptersWithContentByNovel(novelId)
      
      // Filter and sort
      const selectedChapters = allChaptersWithContent
        .filter(c => selectedIds.includes(c.id))
        .sort((a, b) => (a.chapterNumber || 0) - (b.chapterNumber || 0))

      const fileName = `${novelTitle}.${format}`

      if (format === 'txt') {
        let fileContent = `${novelTitle}\n\n`
        selectedChapters.forEach(c => {
          const header = `第${c.chapterNumber || '?'}章`
          fileContent += `${header}\n`
          // Add a separator line or spacing
          fileContent += `\n`
          fileContent += `${c.content || ''}\n\n`
          // Optional: Add page break marker or separator
        })
        
        // Add UTF-8 BOM to prevent encoding issues
        const BOM = '\uFEFF'
        const blob = new Blob([BOM + fileContent], { type: 'text/plain;charset=utf-8' })
        downloadBlob(blob, fileName)
      } else {
        // Export as Word (.docx) using docx library
        const sections = []
        
        // Title
        sections.push(
          new Paragraph({
            text: novelTitle,
            heading: HeadingLevel.TITLE,
            alignment: AlignmentType.CENTER,
            spacing: { after: 400 }
          })
        )
        
        // Chapters
        selectedChapters.forEach(c => {
          const header = `第${c.chapterNumber || '?'}章`
          
          // Chapter title
          sections.push(
            new Paragraph({
              text: header,
              heading: HeadingLevel.HEADING_1,
              alignment: AlignmentType.CENTER,
              spacing: { before: 400, after: 200 }
            })
          )
          
          // Chapter content
          const paragraphs = (c.content || '').split('\n').filter(p => p.trim())
          paragraphs.forEach(p => {
            sections.push(
              new Paragraph({
                children: [new TextRun(p.trim())],
                alignment: AlignmentType.JUSTIFIED,
                indent: { firstLine: 480 }, // 2 characters indent
                spacing: { after: 120 }
              })
            )
          })
          
          // Add spacing after chapter
          sections.push(
            new Paragraph({
              text: '',
              spacing: { after: 200 }
            })
          )
        })
        
        const doc = new Document({
          sections: [{
            properties: {},
            children: sections
          }]
        })
        
        Packer.toBlob(doc).then(blob => {
          saveAs(blob, fileName)
        })
      }

      message.success('导出成功')
      onCancel()
    } catch (error) {
      console.error('Export failed:', error)
      message.error('导出失败，请重试')
    } finally {
      setLoading(false)
    }
  }

  const downloadBlob = (blob: Blob, fileName: string) => {
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
  }

  const sortedChapters = [...chapters].sort((a, b) => (a.chapterNumber || 0) - (b.chapterNumber || 0))

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 18 }}>
          <DownloadOutlined style={{ color: '#1890ff' }} />
          <span>导出作品</span>
        </div>
      }
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={800}
      bodyStyle={{ padding: '24px' }}
      centered
    >
      <Row gutter={24}>
        {/* Left Column: Settings */}
        <Col span={9}>
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 24 }}>
            {/* Format Selection */}
            <div>
              <Title level={5} style={{ marginBottom: 16 }}>导出格式</Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div 
                  onClick={() => setFormat('txt')}
                  style={{
                    padding: '12px 16px',
                    border: `1px solid ${format === 'txt' ? '#1890ff' : '#d9d9d9'}`,
                    borderRadius: '8px',
                    background: format === 'txt' ? '#e6f7ff' : '#fff',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    transition: 'all 0.3s'
                  }}
                >
                  <FileTextOutlined style={{ fontSize: 24, color: '#595959' }} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600 }}>TXT 文本</div>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>纯文本格式，兼容性最好</div>
                  </div>
                  {format === 'txt' && <CheckCircleOutlined style={{ color: '#1890ff' }} />}
                </div>

                <div 
                  onClick={() => setFormat('docx')}
                  style={{
                    padding: '12px 16px',
                    border: `1px solid ${format === 'docx' ? '#1890ff' : '#d9d9d9'}`,
                    borderRadius: '8px',
                    background: format === 'docx' ? '#e6f7ff' : '#fff',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    transition: 'all 0.3s'
                  }}
                >
                  <FileWordOutlined style={{ fontSize: 24, color: '#1890ff' }} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600 }}>Word 文档</div>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>.docx 格式，排版美观</div>
                  </div>
                  {format === 'docx' && <CheckCircleOutlined style={{ color: '#1890ff' }} />}
                </div>
              </div>
            </div>

            {/* Stats */}
            <Card size="small" style={{ background: '#f9f9f9', borderRadius: 8 }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text type="secondary">选中章节：</Text>
                  <Text strong>{selectedIds.length} 章</Text>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text type="secondary">预估字数：</Text>
                  <Text>{(totalSelectedWordCount / 10000).toFixed(1)} 万字</Text>
                </div>
              </div>
            </Card>

            <div style={{ marginTop: 'auto', display: 'flex', gap: 12 }}>
               <Button onClick={onCancel} style={{ flex: 1 }}>取消</Button>
               <Button 
                 type="primary" 
                 onClick={handleExport} 
                 loading={loading} 
                 icon={<DownloadOutlined />}
                 style={{ flex: 2 }}
               >
                 确认导出
               </Button>
            </div>
          </div>
        </Col>

        {/* Right Column: Chapter Selection */}
        <Col span={15}>
          <div style={{ 
            border: '1px solid #f0f0f0', 
            borderRadius: '8px', 
            height: '400px', 
            display: 'flex', 
            flexDirection: 'column',
            background: '#fff'
          }}>
            <div style={{ 
              padding: '12px 16px', 
              borderBottom: '1px solid #f0f0f0',
              background: '#fafafa',
              borderTopLeftRadius: '8px',
              borderTopRightRadius: '8px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <Text strong>选择导出章节</Text>
              <Checkbox
                indeterminate={indeterminate}
                onChange={onCheckAllChange}
                checked={checkAll}
              >
                全选 ({chapters.length})
              </Checkbox>
            </div>
            
            <div style={{ 
              flex: 1, 
              overflowY: 'auto', 
              padding: '12px'
            }}>
              {chapters.length === 0 ? (
                 <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无章节" />
              ) : (
                <Checkbox.Group 
                  style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '8px' }} 
                  value={selectedIds} 
                  onChange={onChapterCheckChange}
                >
                  {sortedChapters.map(chapter => (
                    <div 
                      key={chapter.id}
                      style={{
                        padding: '8px 12px',
                        borderRadius: '4px',
                        background: selectedIds.includes(chapter.id) ? '#e6f7ff' : 'transparent',
                        transition: 'background 0.2s',
                        display: 'flex',
                        alignItems: 'center'
                      }}
                      className="chapter-item"
                    >
                      <Checkbox value={chapter.id} style={{ width: '100%' }}>
                        <span style={{ display: 'inline-block', width: '80px', color: '#8c8c8c' }}>
                          第 {chapter.chapterNumber} 章
                        </span>
                        <span style={{ fontWeight: 500 }}>{chapter.title}</span>
                        {chapter.wordCount ? (
                          <span style={{ float: 'right', fontSize: 12, color: '#bfbfbf' }}>
                            {chapter.wordCount}字
                          </span>
                        ) : null}
                      </Checkbox>
                    </div>
                  ))}
                </Checkbox.Group>
              )}
            </div>
          </div>
        </Col>
      </Row>
    </Modal>
  )
}

export default ExportModal
