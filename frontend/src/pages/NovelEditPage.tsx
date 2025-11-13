import React, { useEffect, useState } from 'react'
import { Typography, Form, Input, InputNumber, Button, Card, Select, message, Spin, Space } from 'antd'
import { SaveOutlined, ArrowLeftOutlined, DeleteOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { AppDispatch } from '@/store'
import { updateNovel, setCurrentNovel } from '@/store/slices/novelSlice'
import { novelService } from '@/services/novelService'

import './NovelEditPage.css'

const { Title, Text } = Typography
const { TextArea } = Input
const { Option } = Select

interface NovelForm {
  title: string
  description: string
  status: string
  // 新增：创作配置字段
  targetTotalChapters?: number
  wordsPerChapter?: number
  plannedVolumeCount?: number
  totalWordTarget?: number
}

const NovelEditPage: React.FC = () => {
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()
  const dispatch = useDispatch<AppDispatch>()
  // const { currentNovel } = useSelector((state: RootState) => state.novel)
  
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    if (id && id !== 'new') {
      loadNovel(parseInt(id))
    }
  }, [id])

  const loadNovel = async (novelId: number) => {
    try {
      setLoading(true)
      const novel = await novelService.getNovelById(novelId)
      dispatch(setCurrentNovel(novel))
      form.setFieldsValue({
        title: novel.title,
        description: novel.description,
        status: novel.status,
        targetTotalChapters: Number(novel.targetTotalChapters) || 100,
        wordsPerChapter: Number(novel.wordsPerChapter) || 3000,
        plannedVolumeCount: Number(novel.plannedVolumeCount) || 3,
        totalWordTarget: Number(novel.totalWordTarget) || 300000,
      })
    } catch (error) {
      message.error('加载小说失败')
      navigate('/novels')
    } finally {
      setLoading(false)
    }
  }

  const onFinish = async (values: NovelForm) => {
    try {
      setLoading(true)
      if (id && id !== 'new') {
        await dispatch(updateNovel({ id: parseInt(id), data: values })).unwrap()
        message.success('小说更新成功！')
      } else {
        // 创建新小说 - 确保数值类型正确转换
        const newNovel = await novelService.createNovel({
          title: values.title,
          description: values.description,
          targetTotalChapters: Number(values.targetTotalChapters),
          wordsPerChapter: Number(values.wordsPerChapter),
          plannedVolumeCount: Number(values.plannedVolumeCount),
          totalWordTarget: Number(values.totalWordTarget)
        })
        message.success('小说创建成功！')
        // 创建成功后跳转到卷管理页面
        navigate(`/novels/${newNovel.id}/volumes`)
        return
      }
      navigate('/novels')
    } catch (error) {
      message.error('保存失败，请重试')
      console.error('保存错误:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!id || id === 'new') return
    
    try {
      await novelService.deleteNovel(parseInt(id))
      message.success('小说删除成功！')
      navigate('/novels')
    } catch (error) {
      message.error('删除失败，请重试')
    }
  }

  if (loading) {
    return (
      <div className="loading-container">
        <Spin size="large" />
        <Text>加载中...</Text>
      </div>
    )
  }

  return (
    <div className="novel-edit-page">
      <div className="page-header">
        <Space>
          <Button 
            icon={<ArrowLeftOutlined />} 
            onClick={() => navigate('/novels')}
          >
            返回
          </Button>
          <Title level={2} className="page-title">
            {id === 'new' ? '新建小说' : '编辑小说'}
          </Title>
        </Space>
        {id !== 'new' && (
          <Button 
            danger 
            icon={<DeleteOutlined />}
            onClick={handleDelete}
          >
            删除
          </Button>
        )}
      </div>

      <Card className="edit-form-card">
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{
            title: '',
            description: '',

            status: 'draft',
            targetTotalChapters: 100,
            wordsPerChapter: 3000,
            plannedVolumeCount: 3,
            totalWordTarget: 300000,
          }}
        >
          <Form.Item
            name="title"
            label="小说标题"
            rules={[
              { required: true, message: '请输入小说标题' },
              { max: 100, message: '标题长度不能超过100个字符' },
            ]}
          >
            <Input placeholder="请输入小说标题" size="large" />
          </Form.Item>

          <Form.Item
            name="description"
            label="小说简介"
            rules={[
              { required: true, message: '请输入小说简介' },
              { max: 500, message: '简介长度不能超过500个字符' },
            ]}
          >
            <TextArea 
              placeholder="请输入小说简介" 
              rows={4}
              showCount
              maxLength={500}
            />
          </Form.Item>



          <Form.Item
            name="status"
            label="创作状态"
            rules={[{ required: true, message: '请选择创作状态' }]}
          >
            <Select placeholder="请选择创作状态" size="large">
              <Option value="draft">草稿</Option>
              <Option value="writing">创作中</Option>
              <Option value="reviewing">审核中</Option>
              <Option value="completed">已完成</Option>
              <Option value="paused">暂停</Option>
            </Select>
          </Form.Item>

          {/* 新增：创作配置部分 */}
          <div className="config-section">
            <Title level={4} style={{ marginBottom: '16px', color: '#1890ff' }}>
              📊 创作配置
            </Title>
            
            <Form.Item
              name="targetTotalChapters"
              label="目标总章数"
              rules={[
                { required: true, message: '请输入目标总章数' },
                { type: 'number', min: 10, max: 1000, message: '章数应在10-1000之间' },
              ]}
            >
              <InputNumber 
                placeholder="例如：100" 
                addonAfter="章"
                size="large"
                min={10}
                max={1000}
                style={{ width: '100%' }}
              />
            </Form.Item>

            <Form.Item
              name="wordsPerChapter"
              label="每章字数"
              rules={[
                { required: true, message: '请输入每章字数' },
                { type: 'number', min: 1000, max: 10000, message: '每章字数应在1000-10000之间' },
              ]}
            >
              <InputNumber 
                placeholder="例如：3000" 
                addonAfter="字"
                size="large"
                min={1000}
                max={10000}
                style={{ width: '100%' }}
              />
            </Form.Item>

            <Form.Item
              name="plannedVolumeCount"
              label="计划卷数"
              rules={[
                { required: true, message: '请输入计划卷数' },
                { type: 'number', min: 1, max: 20, message: '卷数应在1-20之间' },
              ]}
            >
              <InputNumber 
                placeholder="例如：3" 
                addonAfter="卷"
                size="large"
                min={1}
                max={20}
                style={{ width: '100%' }}
              />
            </Form.Item>

            <Form.Item
              name="totalWordTarget"
              label="总字数目标"
              rules={[
                { required: true, message: '请输入总字数目标' },
                { type: 'number', min: 10000, max: 5000000, message: '总字数应在1万-500万之间' },
              ]}
            >
              <InputNumber 
                placeholder="例如：300000" 
                addonAfter="字"
                size="large"
                min={10000}
                max={5000000}
                step={1000}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </div>

          <Form.Item>
            <Space>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={loading}
                size="large"
              >
                保存
              </Button>
              <Button
                onClick={() => navigate('/novels')}
                size="large"
              >
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}

export default NovelEditPage 