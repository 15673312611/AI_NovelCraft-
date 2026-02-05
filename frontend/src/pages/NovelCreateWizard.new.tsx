import React, { useState } from 'react'
import { Form, Input, Button, message, Typography } from 'antd'
import { ArrowLeftOutlined, ThunderboltOutlined, BulbOutlined, CompassOutlined, FireOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { AppDispatch } from '@/store'
import { createNovel } from '@/store/slices/novelSlice'

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
  const [form] = Form.useForm()

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
        state: { initialIdea: values.description, autoGenerate: true }
      })
    } catch (error: any) {
      message.error(error?.message || '创建失败，请重试')
      console.error('创建错误:', error)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="novel-create-zen">
      <div className="zen-container">
        {/* Header */}
        <div className="zen-header">
          <button className="zen-back-btn" onClick={() => navigate('/novels')}>
            <ArrowLeftOutlined /> 返回列表
          </button>
          <Title level={1} className="zen-title">开启新的创作之旅</Title>
          <div className="zen-subtitle">
            每一个伟大的故事，都始于一个简单的念头。在这里，让 AI 协助你将灵感编织成宏大的世界。
          </div>
        </div>

        {/* Central Form Card */}
        <div className="zen-form-card">
          <Form
            form={form}
            layout="vertical"
            onFinish={onFinish}
            className="zen-form"
          >
            {/* Title */}
            <div className="zen-form-item">
              <label className="zen-label">作品名称</label>
              <Form.Item
                name="title"
                rules={[{ required: true, message: '请输入标题' }]}
                style={{ marginBottom: 0 }}
              >
                <Input 
                  placeholder="给你的故事起个响亮的名字..." 
                  className="zen-input"
                  autoComplete="off"
                />
              </Form.Item>
            </div>

            {/* Description */}
            <div className="zen-form-item">
              <label className="zen-label">核心构思</label>
              <Form.Item
                name="description"
                rules={[
                  { required: true, message: '请输入构思' },
                  { min: 50, message: '请至少输入50个字以生成有效大纲' }
                ]}
                style={{ marginBottom: 0 }}
              >
                <TextArea 
                  placeholder="描述你的世界观、主角人设、核心冲突..." 
                  className="zen-textarea"
                  autoSize={{ minRows: 6, maxRows: 12 }}
                  showCount={{ formatter: ({ count }) => <span className="zen-count">{count}/3000</span> }}
                  maxLength={3000}
                />
              </Form.Item>
            </div>

            {/* Submit */}
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

        {/* Inspiration Hints */}
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
