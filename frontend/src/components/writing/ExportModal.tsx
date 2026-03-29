import React, { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Empty,
  List,
  Modal,
  Row,
  Tabs,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd'
import type { CheckboxChangeEvent } from 'antd/es/checkbox'
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  DownloadOutlined,
  FileTextOutlined,
  FileWordOutlined,
  InboxOutlined,
  PartitionOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { Document, Paragraph, TextRun, AlignmentType, HeadingLevel, Packer } from 'docx'
import { saveAs } from 'file-saver'
import {
  ChapterImportPreviewChapter,
  ChapterImportPreviewResponse,
  ChapterImportResult,
  getChaptersWithContentByNovel,
  importChaptersFromPreview,
  postProcessImportedChapters,
  previewChapterImport,
  type Chapter,
} from '@/services/chapterServiceForStudio'

const { Dragger } = Upload
const { Text, Title } = Typography

interface ExportModalProps {
  visible: boolean
  onCancel: () => void
  novelId: number
  novelTitle: string
  chapters: Chapter[]
  initialTab?: 'export' | 'import'
  onImportSuccess?: (result: ChapterImportResult) => Promise<void> | void
}

const FILE_ACCEPT =
  '.txt,.md,.markdown,.json,.html,.htm,.doc,.docx,.docm,.pdf,.rtf'

const ExportModal: React.FC<ExportModalProps> = ({
  visible,
  onCancel,
  novelId,
  novelTitle,
  chapters,
  initialTab = 'export',
  onImportSuccess,
}) => {
  const [activeTab, setActiveTab] = useState<'export' | 'import'>('export')

  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [format, setFormat] = useState<'txt' | 'docx'>('txt')
  const [loading, setLoading] = useState(false)
  const [checkAll, setCheckAll] = useState(false)
  const [indeterminate, setIndeterminate] = useState(false)

  const [importPreview, setImportPreview] = useState<ChapterImportPreviewResponse | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [importing, setImporting] = useState(false)
  const [selectedImportFile, setSelectedImportFile] = useState<string>('')

  useEffect(() => {
    if (!visible) {
      return
    }

    const allIds = chapters.map(c => c.id)
    setSelectedIds(allIds)
    setCheckAll(true)
    setIndeterminate(false)
    setActiveTab(initialTab)
    setImportPreview(null)
    setSelectedImportFile('')
  }, [visible, chapters, initialTab])

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

  const totalSelectedWordCount = useMemo(
    () =>
      chapters
        .filter(c => selectedIds.includes(c.id))
        .reduce((sum, c) => sum + (c.wordCount || 0), 0),
    [chapters, selectedIds]
  )

  const sortedChapters = useMemo(
    () => [...chapters].sort((a, b) => (a.chapterNumber || 0) - (b.chapterNumber || 0)),
    [chapters]
  )

  const handleExport = async () => {
    if (selectedIds.length === 0) {
      message.warning('请至少选择一个章节')
      return
    }

    setLoading(true)
    try {
      const allChaptersWithContent = await getChaptersWithContentByNovel(novelId)
      const selectedChapters = allChaptersWithContent
        .filter(c => selectedIds.includes(c.id))
        .sort((a, b) => (a.chapterNumber || 0) - (b.chapterNumber || 0))

      const fileName = `${novelTitle}.${format}`

      if (format === 'txt') {
        let fileContent = `${novelTitle}\n\n`
        selectedChapters.forEach(c => {
          const header = `第${c.chapterNumber || '?'}章 ${c.title || ''}`.trim()
          fileContent += `${header}\n\n${c.content || ''}\n\n`
        })

        const blob = new Blob([`\uFEFF${fileContent}`], {
          type: 'text/plain;charset=utf-8',
        })
        downloadBlob(blob, fileName)
      } else {
        const sections: Paragraph[] = [
          new Paragraph({
            text: novelTitle,
            heading: HeadingLevel.TITLE,
            alignment: AlignmentType.CENTER,
            spacing: { after: 400 },
          }),
        ]

        selectedChapters.forEach(c => {
          const header = `第${c.chapterNumber || '?'}章 ${c.title || ''}`.trim()
          sections.push(
            new Paragraph({
              text: header,
              heading: HeadingLevel.HEADING_1,
              alignment: AlignmentType.CENTER,
              spacing: { before: 400, after: 200 },
            })
          )

          const paragraphs = (c.content || '').split('\n').filter(p => p.trim())
          paragraphs.forEach(p => {
            sections.push(
              new Paragraph({
                children: [new TextRun(p.trim())],
                alignment: AlignmentType.JUSTIFIED,
                indent: { firstLine: 480 },
                spacing: { after: 120 },
              })
            )
          })

          sections.push(
            new Paragraph({
              text: '',
              spacing: { after: 200 },
            })
          )
        })

        const doc = new Document({
          sections: [
            {
              properties: {},
              children: sections,
            },
          ],
        })

        const blob = await Packer.toBlob(doc)
        saveAs(blob, fileName)
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

  const handlePreviewImport = async (file: File) => {
    setPreviewLoading(true)
    try {
      const preview = await previewChapterImport(novelId, file)
      setImportPreview(preview)
      setSelectedImportFile(file.name)
      setActiveTab('import')
      message.success(`已识别 ${preview.detectedChapterCount} 章`)
    } catch (error: any) {
      console.error('Preview import failed:', error)
      message.error(error.message || '导入预解析失败')
    } finally {
      setPreviewLoading(false)
    }
  }

  const executeImport = async (autoSplitLongChapters: boolean) => {
    if (!importPreview) {
      message.warning('请先上传文件并完成预解析')
      return
    }

    setImporting(true)
    try {
      const result = await importChaptersFromPreview(
        novelId,
        importPreview.chapters,
        autoSplitLongChapters
      )

      if (onImportSuccess) {
        await onImportSuccess(result)
      }

      message.success(result.message || `已导入 ${result.importedCount} 章`)
      onCancel()

      Modal.confirm({
        title: '导入完成，是否继续建立图谱关系等 AI 抽取？',
        width: 560,
        okText: '继续抽取',
        cancelText: '先不处理',
        content: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Alert
              type="info"
              showIcon
              message={`本次已导入 ${result.importedCount} 章`}
              description="继续后会执行章节摘要、记忆提取和图谱关系抽取，逻辑沿用你现有的生成后处理链路。"
            />
            <Alert
              type="warning"
              showIcon
              message="该操作会调用 AI，并消耗字数点"
              description="实际消耗取决于章节长度、模型输入输出与图谱抽取结果。"
            />
          </div>
        ),
        onOk: async () => {
          const loadingKey = 'chapter-import-post-process'
          message.loading({
            key: loadingKey,
            content: '正在执行导入后 AI 抽取...',
            duration: 0,
          })
          try {
            const postProcess = await postProcessImportedChapters(
              novelId,
              result.importedChapterIds,
              {
                buildMemory: true,
                buildGraph: true,
              }
            )

            message.success({
              key: loadingKey,
              content: `AI 抽取完成：摘要/记忆 ${postProcess.memoryProcessedCount} 章，图谱 ${postProcess.graphProcessedCount} 章`,
            })

            if (postProcess.warnings?.length) {
              Modal.warning({
                title: '部分处理未完成',
                content: (
                  <List
                    size="small"
                    dataSource={postProcess.warnings}
                    renderItem={item => <List.Item>{item}</List.Item>}
                  />
                ),
              })
            }
          } catch (error: any) {
            console.error('Post process import failed:', error)
            message.error({
              key: loadingKey,
              content: error.message || '导入后 AI 抽取失败',
            })
          }
        },
      })
    } catch (error: any) {
      console.error('Import failed:', error)
      message.error(error.message || '导入失败')
    } finally {
      setImporting(false)
    }
  }

  const handleImport = async () => {
    if (!importPreview) {
      message.warning('请先上传文件')
      return
    }

    if (importPreview.requiresSplitConfirmation) {
      Modal.confirm({
        title: '检测到超长章节',
        okText: '确认拆分并导入',
        cancelText: '取消',
        content: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Alert
              type="warning"
              showIcon
              message={`有 ${importPreview.oversizedCount} 章超过 ${importPreview.maxChapterLength} 字`}
              description="继续后会按段落和句子智能拆分成多章，保证每章不超过 10000 字。"
            />
          </div>
        ),
        onOk: async () => {
          await executeImport(true)
        },
      })
      return
    }

    await executeImport(false)
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

  const renderExportTab = () => (
    <Row gutter={24}>
      <Col span={9}>
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 24 }}>
          <div>
            <Title level={5} style={{ marginBottom: 16 }}>
              导出格式
            </Title>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div
                onClick={() => setFormat('txt')}
                style={selectCardStyle(format === 'txt')}
              >
                <FileTextOutlined style={{ fontSize: 24, color: '#595959' }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600 }}>TXT 文本</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                    纯文本格式，兼容性最好
                  </div>
                </div>
                {format === 'txt' && <CheckCircleOutlined style={{ color: '#1890ff' }} />}
              </div>

              <div
                onClick={() => setFormat('docx')}
                style={selectCardStyle(format === 'docx')}
              >
                <FileWordOutlined style={{ fontSize: 24, color: '#1890ff' }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600 }}>Word 文档</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                    `.docx` 格式，排版更适合交稿或存档
                  </div>
                </div>
                {format === 'docx' && <CheckCircleOutlined style={{ color: '#1890ff' }} />}
              </div>
            </div>
          </div>

          <Card size="small" style={{ background: '#f9f9f9', borderRadius: 8 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={statRowStyle}>
                <Text type="secondary">选中章节</Text>
                <Text strong>{selectedIds.length} 章</Text>
              </div>
              <div style={statRowStyle}>
                <Text type="secondary">预估字数</Text>
                <Text>{(totalSelectedWordCount / 10000).toFixed(1)} 万字</Text>
              </div>
            </div>
          </Card>

          <div style={{ marginTop: 'auto', display: 'flex', gap: 12 }}>
            <Button onClick={onCancel} style={{ flex: 1 }}>
              取消
            </Button>
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

      <Col span={15}>
        <div style={chapterPanelStyle}>
          <div style={chapterPanelHeaderStyle}>
            <Text strong>选择导出章节</Text>
            <Checkbox
              indeterminate={indeterminate}
              onChange={onCheckAllChange}
              checked={checkAll}
            >
              全选 ({chapters.length})
            </Checkbox>
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: '12px' }}>
            {chapters.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无章节" />
            ) : (
              <Checkbox.Group
                style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 8 }}
                value={selectedIds}
                onChange={onChapterCheckChange}
              >
                {sortedChapters.map(chapter => (
                  <div
                    key={chapter.id}
                    style={{
                      padding: '8px 12px',
                      borderRadius: 4,
                      background: selectedIds.includes(chapter.id) ? '#e6f7ff' : 'transparent',
                      display: 'flex',
                      alignItems: 'center',
                    }}
                  >
                    <Checkbox value={chapter.id} style={{ width: '100%' }}>
                      <span style={{ display: 'inline-block', width: 90, color: '#8c8c8c' }}>
                        第{chapter.chapterNumber}章
                      </span>
                      <span style={{ fontWeight: 500 }}>{chapter.title}</span>
                      {chapter.wordCount ? (
                        <span style={{ float: 'right', fontSize: 12, color: '#bfbfbf' }}>
                          {chapter.wordCount} 字
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
  )

  const renderImportPreviewItem = (chapter: ChapterImportPreviewChapter, index: number) => (
    <List.Item
      key={`${chapter.title}-${index}`}
      style={{ alignItems: 'flex-start' }}
      extra={
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          <Tag color={chapter.tooLong ? 'volcano' : 'blue'}>{chapter.wordCount} 字</Tag>
          {chapter.tooLong ? <Tag color="red">超长</Tag> : null}
          {chapter.wasSplit ? <Tag color="purple">已拆分</Tag> : null}
        </div>
      }
    >
      <List.Item.Meta
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span>{chapter.title || `导入章节 ${index + 1}`}</span>
            {chapter.detectedChapterNumber ? (
              <Tag>识别章号 {chapter.detectedChapterNumber}</Tag>
            ) : null}
          </div>
        }
        description={
          <div style={{ color: '#666', whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>
            {(chapter.content || '').slice(0, 180)}
            {(chapter.content || '').length > 180 ? '...' : ''}
          </div>
        }
      />
    </List.Item>
  )

  const renderImportTab = () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Alert
        type="info"
        showIcon
        message="支持导入常见正文格式"
        description="支持 txt / md / json / html / doc / docx / pdf / rtf，并尝试自动识别章节标题。"
      />

      <Dragger
        accept={FILE_ACCEPT}
        multiple={false}
        showUploadList={false}
        disabled={previewLoading || importing}
        beforeUpload={file => {
          void handlePreviewImport(file as File)
          return false
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽文件到这里导入章节</p>
        <p className="ant-upload-hint">
          系统会先做智能预解析，不会直接落库
        </p>
      </Dragger>

      <Card size="small">
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <Text type="secondary">当前文件</Text>
            <div style={{ fontWeight: 600, marginTop: 4 }}>
              {selectedImportFile || '未选择文件'}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <Stat label="已识别章节" value={importPreview?.detectedChapterCount || 0} />
            <Stat label="总字数" value={importPreview?.totalWordCount || 0} />
            <Stat label="超长章节" value={importPreview?.oversizedCount || 0} />
          </div>
        </div>
      </Card>

      {importPreview?.warnings?.length ? (
        <Alert
          type="warning"
          showIcon
          message="预解析提示"
          description={
            <List
              size="small"
              dataSource={importPreview.warnings}
              renderItem={item => <List.Item>{item}</List.Item>}
            />
          }
        />
      ) : null}

      {importPreview?.requiresSplitConfirmation ? (
        <Alert
          type="warning"
          showIcon
          message={`检测到 ${importPreview.oversizedCount} 章超过 ${importPreview.maxChapterLength} 字`}
          description="导入时会先弹确认，确认后按段落和句子智能拆分成多章，保证单章不超过 10000 字。"
        />
      ) : null}

      {importPreview?.creditNotice ? (
        <Alert
          type="info"
          showIcon
          message="AI 后处理说明"
          description={importPreview.creditNotice}
        />
      ) : null}

      <div style={chapterPanelStyle}>
        <div style={chapterPanelHeaderStyle}>
          <Text strong>导入预览</Text>
          {importPreview ? (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Tag icon={<PartitionOutlined />} color="blue">
                预解析
              </Tag>
              <Tag icon={<UploadOutlined />} color="processing">
                待导入 {importPreview.detectedChapterCount} 章
              </Tag>
            </div>
          ) : null}
        </div>
        <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
          {!importPreview ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={previewLoading ? '正在解析文件...' : '上传文件后查看导入预览'}
            />
          ) : (
            <List
              dataSource={importPreview.chapters}
              renderItem={renderImportPreviewItem}
              split
            />
          )}
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
        <Button onClick={onCancel}>关闭</Button>
        <Button
          type="primary"
          icon={<ApartmentOutlined />}
          loading={importing}
          disabled={!importPreview}
          onClick={handleImport}
        >
          开始导入
        </Button>
      </div>
    </div>
  )

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 18 }}>
          {activeTab === 'export' ? (
            <DownloadOutlined style={{ color: '#1890ff' }} />
          ) : (
            <UploadOutlined style={{ color: '#52c41a' }} />
          )}
          <span>作品传输</span>
        </div>
      }
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={960}
      styles={{ body: { padding: 24 } }}
      centered
      destroyOnClose
    >
      <Tabs
        activeKey={activeTab}
        onChange={key => setActiveTab(key as 'export' | 'import')}
        items={[
          {
            key: 'export',
            label: '导出',
            children: renderExportTab(),
          },
          {
            key: 'import',
            label: '导入',
            children: renderImportTab(),
          },
        ]}
      />
    </Modal>
  )
}

const Stat: React.FC<{ label: string; value: number }> = ({ label, value }) => (
  <div style={{ minWidth: 92 }}>
    <Text type="secondary">{label}</Text>
    <div style={{ fontSize: 18, fontWeight: 600, marginTop: 4 }}>{value}</div>
  </div>
)

const statRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
}

const chapterPanelStyle: React.CSSProperties = {
  border: '1px solid #f0f0f0',
  borderRadius: 8,
  height: 420,
  display: 'flex',
  flexDirection: 'column',
  background: '#fff',
}

const chapterPanelHeaderStyle: React.CSSProperties = {
  padding: '12px 16px',
  borderBottom: '1px solid #f0f0f0',
  background: '#fafafa',
  borderTopLeftRadius: 8,
  borderTopRightRadius: 8,
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
}

const selectCardStyle = (selected: boolean): React.CSSProperties => ({
  padding: '12px 16px',
  border: `1px solid ${selected ? '#1890ff' : '#d9d9d9'}`,
  borderRadius: 8,
  background: selected ? '#e6f7ff' : '#fff',
  cursor: 'pointer',
  display: 'flex',
  alignItems: 'center',
  gap: 12,
  transition: 'all 0.3s',
})

export default ExportModal
