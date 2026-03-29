import React, { useState } from 'react'
import { Form, Input, Button, Popover, Space, message, Typography } from 'antd'
import {
  ArrowLeftOutlined,
  ThunderboltOutlined,
  BulbOutlined,
  CompassOutlined,
  FireOutlined,
  DownOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { AppDispatch } from '@/store'
import { createNovel } from '@/store/slices/novelSlice'
import { novelCreationAssistantService, NovelCreationGenerateField } from '@/services/novelCreationAssistantService'

import './NovelCreateWizard.new.css'

const { TextArea } = Input
const { Title } = Typography

interface CreateNovelForm {
  title: string
  description: string
}

const NovelCreateWizard: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const [loading, setLoading] = useState(false)
  const [generatingField, setGeneratingField] = useState<NovelCreationGenerateField | null>(null)
  const [titleKeyword, setTitleKeyword] = useState('')
  const [descriptionKeyword, setDescriptionKeyword] = useState('')
  const [titleKeywordOpen, setTitleKeywordOpen] = useState(false)
  const [descriptionKeywordOpen, setDescriptionKeywordOpen] = useState(false)
  const [form] = Form.useForm<CreateNovelForm>()

  const onFinish = async (values: CreateNovelForm) => {
    try {
      setLoading(true)

      const result = await dispatch(createNovel({
        title: values.title,
        description: values.description,
        targetTotalChapters: 100,
        wordsPerChapter: 2200,
        plannedVolumeCount: 3,
        totalWordTarget: 300000,
      })).unwrap()

      message.success('创作空间已初始化')

      navigate(`/novels/${result.id}/volumes`, {
        state: { initialIdea: values.description, autoGenerate: true },
      })
    } catch (error: any) {
      message.error(error?.message || '创建失败，请重试')
      console.error('创建错误:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleGenerateField = async (field: NovelCreationGenerateField) => {
    try {
      setGeneratingField(field)

      const values = form.getFieldsValue()
      const keyword = field === 'title' ? titleKeyword : descriptionKeyword

      const response = await novelCreationAssistantService.generateField({
        field,
        keyword: keyword.trim() || undefined,
        currentTitle: values.title,
        currentDescription: values.description,
      })

      const generatedValue = response?.data?.value?.trim()
      if (!generatedValue) {
        throw new Error('未生成有效内容')
      }

      form.setFieldsValue({ [field]: generatedValue })
      if (field === 'title') {
        setTitleKeywordOpen(false)
        message.success('已生成作品名称')
      } else {
        setDescriptionKeywordOpen(false)
        message.success('已生成作品构思')
      }
    } catch (error: any) {
      message.error(error?.message || '生成失败，请稍后重试')
    } finally {
      setGeneratingField(null)
    }
  }

  const renderKeywordPopover = (
    field: NovelCreationGenerateField,
    keyword: string,
    onChange: (value: string) => void,
  ) => (
    <div className="zen-generate-popover">
      <div className="zen-generate-popover-title">关键词</div>
      <Input
        value={keyword}
        onChange={(e) => onChange(e.target.value)}
        placeholder={field === 'title' ? '例如：修仙、复仇、病娇' : '例如：双强、权谋、爽感升级'}
        allowClear
        autoComplete="off"
      />
      <div className="zen-generate-popover-hint">
        留空则完全随机。点击左侧“随机生成”时，会自动把这里的关键词一起带给 AI。
      </div>
    </div>
  )

  return (
    <div className="novel-create-zen">
      <div className="zen-container">
        <div className="zen-header">
          <button className="zen-back-btn" onClick={() => navigate('/novels')}>
            <ArrowLeftOutlined /> 返回列表
          </button>
          <Title level={1} className="zen-title">开启新的创作之旅</Title>
          <div className="zen-subtitle">
            每一个完整的故事，都始于一个念头。在这里先把名字和核心构思定下来，后面的卷纲和写作链路会更顺。
          </div>
        </div>

        <div className="zen-form-card">
          <Form
            form={form}
            layout="vertical"
            onFinish={onFinish}
            className="zen-form"
          >
            <div className="zen-form-item">
              <div className="zen-label-row">
                <label className="zen-label">作品名称</label>
                <Space.Compact className="zen-generator-compact">
                  <Button
                    className="zen-generate-btn"
                    icon={<ReloadOutlined />}
                    loading={generatingField === 'title'}
                    onClick={() => void handleGenerateField('title')}
                  >
                    随机生成
                  </Button>
                  <Popover
                    trigger="click"
                    placement="bottomRight"
                    open={titleKeywordOpen}
                    onOpenChange={(open) => {
                      setTitleKeywordOpen(open)
                      if (open) setDescriptionKeywordOpen(false)
                    }}
                    content={renderKeywordPopover('title', titleKeyword, setTitleKeyword)}
                  >
                    <Button className="zen-generate-arrow" icon={<DownOutlined />} />
                  </Popover>
                </Space.Compact>
              </div>

              <Form.Item
                name="title"
                rules={[{ required: true, message: '请输入作品名称' }]}
                style={{ marginBottom: 0 }}
              >
                <Input
                  placeholder="给你的故事起一个有记忆点的名字..."
                  className="zen-input"
                  autoComplete="off"
                />
              </Form.Item>
            </div>

            <div className="zen-form-item">
              <div className="zen-label-row">
                <label className="zen-label">核心构思</label>
                <Space.Compact className="zen-generator-compact">
                  <Button
                    className="zen-generate-btn"
                    icon={<ReloadOutlined />}
                    loading={generatingField === 'description'}
                    onClick={() => void handleGenerateField('description')}
                  >
                    随机生成
                  </Button>
                  <Popover
                    trigger="click"
                    placement="bottomRight"
                    open={descriptionKeywordOpen}
                    onOpenChange={(open) => {
                      setDescriptionKeywordOpen(open)
                      if (open) setTitleKeywordOpen(false)
                    }}
                    content={renderKeywordPopover('description', descriptionKeyword, setDescriptionKeyword)}
                  >
                    <Button className="zen-generate-arrow" icon={<DownOutlined />} />
                  </Popover>
                </Space.Compact>
              </div>

              <Form.Item
                name="description"
                rules={[
                  { required: true, message: '请输入作品构思' },
                  { min: 50, message: '请至少输入 50 个字，方便后续生成有效卷纲' },
                ]}
                style={{ marginBottom: 0 }}
              >
                <TextArea
                  placeholder="描述你的世界观、主角处境、核心冲突、题材钩子或最想写的那股劲..."
                  className="zen-textarea"
                  autoSize={{ minRows: 6, maxRows: 12 }}
                  showCount={{ formatter: ({ count }) => <span className="zen-count">{count}/3000</span> }}
                  maxLength={3000}
                />
              </Form.Item>
            </div>

            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              className="zen-submit-btn"
              icon={!loading && <ThunderboltOutlined />}
            >
              {loading ? '正在构建世界...' : '开始创作'}
            </Button>
          </Form>
        </div>

        <div className="zen-hints">
          <div className="zen-hint-item">
            <div className="zen-hint-icon"><BulbOutlined /></div>
            <span>世界观设定</span>
          </div>
          <div className="zen-hint-item">
            <div className="zen-hint-icon"><CompassOutlined /></div>
            <span>剧情结构</span>
          </div>
          <div className="zen-hint-item">
            <div className="zen-hint-icon"><FireOutlined /></div>
            <span>核心冲突</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default NovelCreateWizard
