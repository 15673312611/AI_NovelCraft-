import { useState, useEffect } from 'react'
import { Button, Modal, Form, Input, Select, Row, Col, message, Space, Typography, Tabs, InputNumber } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, FileTextOutlined, ThunderboltOutlined, EditFilled, OrderedListOutlined, HighlightOutlined, ScissorOutlined, StarOutlined, StarFilled } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import styled from '@emotion/styled'
import { adminTemplateService } from '@/services/adminTemplateService'
import { PageContainer, DataTable, StatCard, StatusTag, ActionButton } from '@/components'

const { Text } = Typography

interface Template {
  id: number
  name: string
  category: string
  content: string
  usageCount: number
  createdAt: string
  isDefault?: boolean
  sortOrder?: number
}

const NameText = styled.span`
  font-weight: 500;
  color: #0ea5e9;
`

const CountText = styled.span`
  font-weight: 600;
  color: #a855f7;
`

const categoryConfig: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
  chapter: { color: 'success', text: '章节生成', icon: <EditFilled /> },
  outline: { color: 'processing', text: '大纲生成', icon: <OrderedListOutlined /> },
  polish: { color: 'warning', text: 'AI润色', icon: <HighlightOutlined /> },
  remove: { color: 'default', text: 'AI消痕', icon: <ScissorOutlined /> },
}

// 分类顺序：写作模板优先
const categoryOrder = ['chapter', 'outline', 'polish', 'remove']

const TemplateList = () => {
  const [templates, setTemplates] = useState<Template[]>([])
  const [modalVisible, setModalVisible] = useState(false)
  const [loading, setLoading] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<Template | null>(null)
  const [activeCategory, setActiveCategory] = useState<string>('chapter')
  const [form] = Form.useForm()

  useEffect(() => {
    loadTemplates()
  }, [])

  const loadTemplates = async () => {
    setLoading(true)
    try {
      const response = await adminTemplateService.getTemplates({})
      let templateData: Template[] = []
      if (Array.isArray(response)) {
        templateData = response
      } else if (response && Array.isArray((response as any).records)) {
        templateData = (response as any).records
      } else if (response && Array.isArray(response.data)) {
        templateData = response.data
      }
      setTemplates(templateData)
    } catch (error) {
      console.error('加载模板列表失败:', error)
      message.error('加载模板列表失败')
      setTemplates([])
    } finally {
      setLoading(false)
    }
  }

  const handleEdit = (template: Template) => {
    setEditingTemplate(template)
    form.setFieldsValue(template)
    setModalVisible(true)
  }

  const handleDelete = async (id: number) => {
    try {
      await adminTemplateService.deleteTemplate(id)
      message.success('删除成功')
      loadTemplates()
    } catch (error) {
      message.error('删除失败')
    }
  }

  const handleSetDefault = async (id: number) => {
    try {
      await adminTemplateService.setDefaultTemplate(id)
      message.success('设置默认模板成功')
      loadTemplates()
    } catch (error) {
      message.error('设置默认模板失败')
    }
  }

  const handleUpdateSortOrder = async (id: number, sortOrder: number) => {
    try {
      await adminTemplateService.updateTemplateSortOrder(id, sortOrder)
      message.success('更新排序成功')
      loadTemplates()
    } catch (error) {
      message.error('更新排序失败')
    }
  }

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields()
      if (editingTemplate) {
        await adminTemplateService.updateTemplate(editingTemplate.id, values)
        message.success('更新成功')
      } else {
        await adminTemplateService.createTemplate(values)
        message.success('创建成功')
      }
      setModalVisible(false)
      form.resetFields()
      setEditingTemplate(null)
      loadTemplates()
    } catch (error) {
      message.error('操作失败')
    }
  }

  const columns: ColumnsType<Template> = [
    { 
      title: 'ID', 
      dataIndex: 'id', 
      key: 'id', 
      width: 80,
      render: (id: number) => (
        <Text style={{ color: 'rgba(250, 250, 250, 0.45)', fontFamily: 'monospace', fontSize: 13 }}>
          #{id}
        </Text>
      ),
    },
    { 
      title: '模板名称', 
      dataIndex: 'name', 
      key: 'name',
      render: (text: string, record: Template) => (
        <Space>
          <NameText>{text}</NameText>
          {record.isDefault && (
            <StatusTag status="warning" text="默认" showIcon={false} />
          )}
        </Space>
      ),
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      width: 120,
      render: (category: string) => {
        const config = categoryConfig[category] || { color: 'default', text: category }
        return <StatusTag status={config.color} text={config.text} showIcon={false} />
      },
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 120,
      align: 'center',
      render: (sortOrder: number, record: Template) => (
        <InputNumber
          size="small"
          min={0}
          value={sortOrder || 0}
          onChange={(value) => value !== null && handleUpdateSortOrder(record.id, value)}
          style={{ width: 80 }}
        />
      ),
    },
    { 
      title: '使用次数', 
      dataIndex: 'usageCount', 
      key: 'usageCount',
      width: 100,
      align: 'center',
      render: (count: number) => <CountText>{count}</CountText>,
    },
    { 
      title: '创建时间', 
      dataIndex: 'createdAt', 
      key: 'createdAt',
      width: 160,
      render: (text: string) => (
        <Text style={{ color: 'rgba(250, 250, 250, 0.45)', fontSize: 13 }}>{text}</Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size={4}>
          <ActionButton
            variant={record.isDefault ? 'default' : 'warning'}
            icon={record.isDefault ? <StarFilled /> : <StarOutlined />}
            tooltip={record.isDefault ? '已是默认' : '设为默认'}
            onClick={() => !record.isDefault && handleSetDefault(record.id)}
            disabled={record.isDefault}
          >
            {record.isDefault ? '默认' : '设为默认'}
          </ActionButton>
          <ActionButton
            variant="primary"
            icon={<EditOutlined />}
            tooltip="编辑"
            onClick={() => handleEdit(record)}
          >
            编辑
          </ActionButton>
          <ActionButton
            variant="danger"
            icon={<DeleteOutlined />}
            tooltip="删除"
            confirmTitle="确认删除"
            confirmDescription="确定要删除该模板吗？"
            onConfirm={() => handleDelete(record.id)}
          >
            删除
          </ActionButton>
        </Space>
      ),
    },
  ]

  // 按分类过滤模板
  const filteredTemplates = templates.filter(t => t.category === activeCategory)
  
  // 统计各分类数量
  const getCategoryCount = (category: string) => templates.filter(t => t.category === category).length
  const getCategoryUsage = (category: string) => templates.filter(t => t.category === category).reduce((sum, t) => sum + t.usageCount, 0)

  const totalUsage = templates.reduce((sum, t) => sum + t.usageCount, 0)

  // Tab项
  const tabItems = categoryOrder.map(cat => {
    const config = categoryConfig[cat]
    const count = getCategoryCount(cat)
    return {
      key: cat,
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {config.icon}
          {config.text}
          <span style={{ 
            background: activeCategory === cat ? 'rgba(14, 165, 233, 0.2)' : 'rgba(255,255,255,0.1)', 
            padding: '2px 8px', 
            borderRadius: 10, 
            fontSize: 12,
            color: activeCategory === cat ? '#0ea5e9' : 'rgba(255,255,255,0.45)'
          }}>
            {count}
          </span>
        </span>
      ),
    }
  })

  return (
    <PageContainer
      title="提示词模板"
      description="管理 AI 生成任务使用的提示词模板"
      icon={<FileTextOutlined />}
      breadcrumb={[{ title: '提示词模板' }]}
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="large"
          onClick={() => {
            setEditingTemplate(null)
            form.resetFields()
            form.setFieldsValue({ category: activeCategory })
            setModalVisible(true)
          }}
        >
          新增模板
        </Button>
      }
    >
      {/* 统计卡片 */}
      <Row gutter={[20, 20]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12}>
          <StatCard
            title="模板总数"
            value={templates.length}
            icon={<FileTextOutlined />}
            gradient={['#0ea5e9', '#06b6d4']}
            loading={loading}
          />
        </Col>
        <Col xs={24} sm={12}>
          <StatCard
            title="总使用次数"
            value={totalUsage}
            icon={<ThunderboltOutlined />}
            gradient={['#a855f7', '#9333ea']}
            loading={loading}
          />
        </Col>
      </Row>

      {/* 分类Tab */}
      <div style={{ 
        background: 'rgba(255,255,255,0.02)', 
        borderRadius: 12, 
        padding: '4px 16px 0',
        marginBottom: 16,
        border: '1px solid rgba(255,255,255,0.06)'
      }}>
        <Tabs
          activeKey={activeCategory}
          onChange={setActiveCategory}
          items={tabItems}
          style={{ marginBottom: 0 }}
        />
      </div>

      {/* 当前分类统计 */}
      <div style={{ 
        display: 'flex', 
        gap: 24, 
        marginBottom: 16, 
        padding: '12px 16px',
        background: 'rgba(255,255,255,0.02)',
        borderRadius: 8,
        border: '1px solid rgba(255,255,255,0.06)'
      }}>
        <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.65)' }}>
          当前分类：<span style={{ color: '#0ea5e9', fontWeight: 600 }}>{categoryConfig[activeCategory]?.text}</span>
        </div>
        <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.65)' }}>
          模板数量：<span style={{ color: '#a855f7', fontWeight: 600 }}>{filteredTemplates.length}</span>
        </div>
        <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.65)' }}>
          使用次数：<span style={{ color: '#22c55e', fontWeight: 600 }}>{getCategoryUsage(activeCategory)}</span>
        </div>
      </div>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        dataSource={filteredTemplates}
        loading={loading}
        rowKey="id"
        showSearch={false}
        onRefresh={loadTemplates}
      />

      {/* 模板编辑模态框 */}
      <Modal
        title={
          <Space size={12}>
            <div style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #0ea5e9, #06b6d4)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <FileTextOutlined style={{ color: '#fff', fontSize: 18 }} />
            </div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 600, color: '#fafafa' }}>
                {editingTemplate ? '编辑模板' : '新增模板'}
              </div>
              <div style={{ fontSize: 12, color: 'rgba(250, 250, 250, 0.45)' }}>
                {editingTemplate ? '修改模板内容' : '创建新的提示词模板'}
              </div>
            </div>
          </Space>
        }
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false)
          form.resetFields()
          setEditingTemplate(null)
        }}
        onOk={handleModalOk}
        width={700}
        okText="确定"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item 
            name="name" 
            label="模板名称" 
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input size="large" placeholder="请输入模板名称" />
          </Form.Item>
          <Form.Item 
            name="category" 
            label="分类" 
            rules={[{ required: true, message: '请选择分类' }]}
          >
            <Select size="large" placeholder="请选择分类">
              <Select.Option value="chapter">
                <Space><EditFilled />章节生成</Space>
              </Select.Option>
              <Select.Option value="outline">
                <Space><OrderedListOutlined />大纲生成</Space>
              </Select.Option>
              <Select.Option value="polish">
                <Space><HighlightOutlined />AI润色</Space>
              </Select.Option>
              <Select.Option value="remove">
                <Space><ScissorOutlined />AI消痕</Space>
              </Select.Option>
            </Select>
          </Form.Item>
          <Form.Item 
            name="content" 
            label="模板内容" 
            rules={[{ required: true, message: '请输入模板内容' }]}
            extra="支持变量替换，如 {{title}}、{{content}} 等"
          >
            <Input.TextArea 
              rows={10} 
              placeholder="请输入模板内容，支持变量替换"
              style={{ fontFamily: "'SF Mono', Monaco, Consolas, monospace" }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}

export default TemplateList
