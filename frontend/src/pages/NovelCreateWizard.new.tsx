import React, { useState } from 'react'
import { Form, Input, Button, message } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { AppDispatch } from '@/store'
import { createNovel } from '@/store/slices/novelSlice'

import './NovelCreateWizard.new.css'

const { TextArea } = Input

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

      message.success('小说创建成功！')
      
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
    <div className="novel-create-page">
      <div className="novel-create-container">
        {/* 返回按钮 */}
        <button className="novel-create-back" onClick={() => navigate('/novels')}>
          <ArrowLeftOutlined />
          <span>返回小说列表</span>
        </button>

        {/* 页面头部 */}
        <div className="novel-create-header">
          <h1>创建新小说</h1>
          <p>输入你的创意构思，AI 将帮你构建完整的故事框架和分卷规划</p>
        </div>

        {/* 表单 */}
        <div className="novel-create-form">
          <Form
            form={form}
            layout="vertical"
            onFinish={onFinish}
          >
            {/* 作品名称 */}
            <div className="novel-form-group">
              <label className="novel-form-label">作品名称</label>
              <Form.Item
                name="title"
                rules={[
                  { required: true, message: '请输入作品名称' },
                  { max: 100, message: '名称不能超过100个字符' },
                ]}
              >
                <Input 
                  placeholder="给你的故事起个名字" 
                  className="novel-input"
                />
              </Form.Item>
            </div>

            {/* 创作构思 */}
            <div className="novel-form-group">
              <label className="novel-form-label">创作构思</label>
              <Form.Item
                name="description"
                rules={[
                  { required: true, message: '请输入创作构思' },
                  { min: 50, message: '构思至少需要50个字符' },
                  { max: 3000, message: '构思不能超过3000个字符' },
                ]}
              >
                <TextArea 
                  placeholder="描述你的故事构思...

可以包含：
• 故事背景和世界观设定
• 主角人设和性格特点
• 核心冲突和矛盾
• 大致的情节走向" 
                  showCount
                  maxLength={3000}
                  className="novel-textarea"
                />
              </Form.Item>
              <p className="novel-form-hint">
                构思越详细，AI 生成的大纲和分卷规划越精准
              </p>
            </div>

            {/* 提交按钮 */}
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              className="novel-submit-btn"
            >
              {loading ? '创建中...' : '开始创作'}
            </Button>
          </Form>
        </div>
      </div>
    </div>
  )
}

export default NovelCreateWizard
