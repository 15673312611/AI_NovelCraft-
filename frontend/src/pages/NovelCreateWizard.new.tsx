import React, { useState } from 'react'
import { Typography, Form, Input, Button, Card, message } from 'antd'
import { ArrowLeftOutlined, RocketOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { AppDispatch } from '@/store'
import { createNovel } from '@/store/slices/novelSlice'
import { GENRE_OPTIONS, NOVEL_GENRES } from '@/constants/genres'
import './NovelCreateWizard.new.css'

const { Title, Text } = Typography
const { TextArea } = Input

interface CreateNovelForm {
  title: string
  genre: string
  description: string // 这里是构思
}

const NovelCreateWizard: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  const onFinish = async (values: CreateNovelForm) => {
    try {
      setLoading(true)
      
      // 使用 Redux 创建新小说
      const result = await dispatch(createNovel({
        title: values.title,
        genre: values.genre,
        description: values.description,
        targetTotalChapters: 100, // 默认值
        wordsPerChapter: 3000, // 默认值
        plannedVolumeCount: 3, // 默认值
        totalWordTarget: 300000, // 默认值
      })).unwrap()
      
      message.success('小说创建成功！')
      
      // 跳转到卷规划页面，并传递构思，自动触发大纲生成
      navigate(`/novels/${result.id}/volumes`, {
        state: { initialIdea: values.description, autoGenerate: true }
      })
    } catch (error) {
      message.error('创建失败，请重试')
      console.error('创建错误:', error)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="novel-create-wizard">
      {/* Hero区域 */}
      <div className="wizard-hero">
        <div className="hero-content">
          <Title level={2} className="hero-title">
            <RocketOutlined className="title-icon" />
            创建新小说
          </Title>
          <Text className="hero-subtitle">
            简单三步，开启您的创作之旅
          </Text>
        </div>
      </div>

      {/* 表单区域 */}
      <div className="wizard-container">
        <Button 
          icon={<ArrowLeftOutlined />} 
          onClick={() => navigate('/novels')}
          className="back-btn"
        >
          返回
        </Button>

        <Card className="wizard-card">
          <Form
            form={form}
            layout="vertical"
            onFinish={onFinish}
            className="wizard-form"
            initialValues={{
              genre: '玄幻',
            }}
          >
            <div className="form-row">
              <Form.Item
                name="title"
                label={<span className="form-label">作品名称</span>}
                rules={[
                  { required: true, message: '请输入作品名称' },
                  { max: 100, message: '名称长度不能超过100个字符' },
                ]}
              >
                <Input 
                  placeholder="例如：修仙之路" 
                  size="large"
                  className="form-input"
                />
              </Form.Item>

              <Form.Item
                name="genre"
                label={<span className="form-label">小说类型</span>}
                rules={[{ required: true, message: '请选择小说类型' }]}
              >
                <select className="form-select">
                  <option value="">请选择小说类型</option>
                  {GENRE_OPTIONS.map(option => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Form.Item>
            </div>

            <Form.Item
              name="description"
              label={<span className="form-label">创作构思</span>}
              rules={[
                { required: true, message: '请输入您的创作构思' },
                { min: 50, message: '构思至少需要50个字符，以便生成更好的大纲' },
                { max: 2000, message: '构思长度不能超过2000个字符' },
              ]}
              extra={<span className="form-extra">详细描述您的故事构思、主要情节、人物设定等，AI将根据这些信息生成大纲</span>}
            >
              <TextArea 
                placeholder="例如：一个现代青年穿越到修仙世界，从无灵根的废柴开始，通过获得神秘系统逐步崛起。故事主要讲述他如何在修仙界中历练成长，最终成为一代强者的故事..." 
                rows={8}
                showCount
                maxLength={2000}
                className="form-textarea"
              />
            </Form.Item>

            <Form.Item className="form-actions">
              <Button
                type="primary"
                htmlType="submit"
                icon={<RocketOutlined />}
                loading={loading}
                size="large"
                block
                className="submit-btn"
              >
                {loading ? '创建中...' : '确认并生成大纲'}
              </Button>
            </Form.Item>
          </Form>
        </Card>

        {/* 提示卡片 */}
        <Card className="tips-card">
          <div className="tips-content">
            <div className="tips-icon">💡</div>
            <div className="tips-text">
              <Text strong className="tips-title">提示</Text>
              <Text className="tips-desc">
                创作构思越详细，AI生成的大纲越精准。建议包含：故事背景、主角设定、核心冲突、大致情节等。
              </Text>
            </div>
          </div>
        </Card>
      </div>
    </div>
  )
}

export default NovelCreateWizard

