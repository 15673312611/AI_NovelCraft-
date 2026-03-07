import { useState, useEffect } from 'react'
import {
  Button, Space, Modal, Form, Input, InputNumber, Slider, 
  message, Row, Col, Empty, Switch, Typography
} from 'antd'
import {
  PlusOutlined, EditOutlined, DeleteOutlined, 
  ApiOutlined, KeyOutlined, CheckCircleOutlined, 
  ExclamationCircleOutlined, GiftOutlined
} from '@ant-design/icons'
import styled from '@emotion/styled'
import { motion } from 'framer-motion'
import { adminAIModelService, AIModel } from '@/services/adminAIModelService'
import { PageContainer, DataTable, InfoCard, ActionButton } from '@/components'

const { Text } = Typography

// Filter out transient props (starting with $) from being passed to DOM
const shouldForwardProp = (prop: string) => !prop.startsWith('$')

// Styled Components
const StatusAlert = styled('div', { shouldForwardProp })<{ $success?: boolean }>`
  padding: 14px 18px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  background: ${props => props.$success ? 'rgba(34, 197, 94, 0.1)' : 'rgba(249, 115, 22, 0.1)'};
  border: 1px solid ${props => props.$success ? 'rgba(34, 197, 94, 0.2)' : 'rgba(249, 115, 22, 0.2)'};
`

const ModelCode = styled.code`
  background: rgba(14, 165, 233, 0.1);
  padding: 4px 12px;
  border-radius: 6px;
  color: #0ea5e9;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 500;
`

const DefaultBadge = styled.span`
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
`

const MultiplierText = styled('span', { shouldForwardProp })<{ $value?: number }>`
  font-weight: 600;
  font-size: 14px;
  color: ${props => (props.$value ?? 1) > 1 ? '#f97316' : (props.$value ?? 1) < 1 ? '#22c55e' : 'rgba(250, 250, 250, 0.65)'};
`

const HintBox = styled.div`
  margin-top: 20px;
  padding: 14px 18px;
  border-radius: 12px;
  background: rgba(249, 115, 22, 0.1);
  border: 1px solid rgba(249, 115, 22, 0.2);
`

const AIModelsPage = () => {
  const [loading, setLoading] = useState(false)
  const [models, setModels] = useState<AIModel[]>([])
  const [modelModalVisible, setModelModalVisible] = useState(false)
  const [editingModel, setEditingModel] = useState<AIModel | null>(null)
  const [modelForm] = Form.useForm()
  
  const [aiConfig, setAiConfig] = useState({ api_base_url: '', api_key: '' })
  const [savingConfig, setSavingConfig] = useState(false)
  const [configLoaded, setConfigLoaded] = useState(false)

  const [dailyFreeEnabled, setDailyFreeEnabled] = useState(true)
  const [dailyFreeAmount, setDailyFreeAmount] = useState(50000)
  const [savingDailyFree, setSavingDailyFree] = useState(false)

  useEffect(() => {
    loadModels()
    loadAIConfig()
  }, [])

  const loadModels = async () => {
    setLoading(true)
    try {
      const res: any = await adminAIModelService.getAllModels()
      setModels(res || [])
    } catch (error) {
      message.error('加载模型列表失败')
    } finally {
      setLoading(false)
    }
  }

  const loadAIConfig = async () => {
    try {
      const res: any = await adminAIModelService.getSystemSettings()
      setAiConfig({
        api_base_url: res?.ai_api_base_url || '',
        api_key: res?.ai_api_key || ''
      })
      setDailyFreeEnabled(res?.daily_free_credits_enabled === 'true')
      setDailyFreeAmount(parseInt(res?.daily_free_credits_amount || '50000', 10))
      setConfigLoaded(true)
    } catch (error) {
      console.error('加载AI配置失败:', error)
      setConfigLoaded(true)
    }
  }

  const handleSaveAIConfig = async () => {
    if (!aiConfig.api_base_url.trim()) {
      message.warning('请填写 API 链接')
      return
    }
    
    const needUpdateKey = aiConfig.api_key && !aiConfig.api_key.includes('***')
    
    if (!aiConfig.api_key.trim() && !needUpdateKey) {
      const currentKey = await adminAIModelService.getSystemSettings()
      if (!(currentKey as any)?.ai_api_key || (currentKey as any)?.ai_api_key === '') {
        message.warning('请填写 API Key')
        return
      }
    }
    
    setSavingConfig(true)
    try {
      const settings: Record<string, string> = { ai_api_base_url: aiConfig.api_base_url }
      if (needUpdateKey) settings.ai_api_key = aiConfig.api_key
      
      await adminAIModelService.updateSystemSettings(settings)
      message.success('配置保存成功')
      
      const res: any = await adminAIModelService.getSystemSettings()
      setAiConfig({
        api_base_url: res?.ai_api_base_url || '',
        api_key: res?.ai_api_key || ''
      })
    } catch (error) {
      message.error('保存失败')
    } finally {
      setSavingConfig(false)
    }
  }

  const handleSaveDailyFreeConfig = async () => {
    setSavingDailyFree(true)
    try {
      await adminAIModelService.updateSystemSettings({
        daily_free_credits_enabled: dailyFreeEnabled ? 'true' : 'false',
        daily_free_credits_amount: String(dailyFreeAmount)
      })
      message.success('每日免费字数配置保存成功')
    } catch (error) {
      message.error('保存失败')
    } finally {
      setSavingDailyFree(false)
    }
  }

  const handleAddModel = () => {
    setEditingModel(null)
    modelForm.resetFields()
    setModelModalVisible(true)
  }

  const handleEditModel = (model: AIModel) => {
    setEditingModel(model)
    modelForm.setFieldsValue({
      modelId: model.modelId,
      displayName: model.displayName,
      costMultiplier: model.costMultiplier || 1.0,
      temperature: model.temperature || 1.0,
      sortOrder: model.sortOrder || 0,
      description: model.description || ''
    })
    setModelModalVisible(true)
  }

  const handleDeleteModel = async (id: number) => {
    try {
      await adminAIModelService.deleteModel(id)
      message.success('删除成功')
      loadModels()
    } catch (error) {
      message.error('删除失败')
    }
  }

  const handleSetDefault = async (id: number) => {
    try {
      await adminAIModelService.setDefaultModel(id)
      message.success('已设为默认模型')
      loadModels()
    } catch (error) {
      message.error('设置失败')
    }
  }

  const handleModelSubmit = async () => {
    try {
      const values = await modelForm.validateFields()
      const modelData = {
        modelId: values.modelId.trim(),
        displayName: values.displayName.trim(),
        costMultiplier: values.costMultiplier || 1.0,
        temperature: values.temperature || 1.0,
        sortOrder: values.sortOrder || 0,
        description: values.description?.trim() || '',
        provider: 'OpenAI-Compatible',
        available: true
      }
      
      if (editingModel) {
        await adminAIModelService.updateModel(editingModel.id, modelData)
        message.success('更新成功')
      } else {
        await adminAIModelService.createModel(modelData)
        message.success('添加成功')
      }
      setModelModalVisible(false)
      loadModels()
    } catch (error) {
      message.error('操作失败')
    }
  }

  const isConfigComplete = aiConfig.api_base_url && (aiConfig.api_key && aiConfig.api_key.length > 0)

  const modelColumns = [
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 70,
      render: (val: number) => (
        <Text style={{ color: 'rgba(250, 250, 250, 0.45)', fontSize: 13 }}>{val ?? 0}</Text>
      )
    },
    {
      title: '模型名称',
      dataIndex: 'modelId',
      key: 'modelId',
      render: (val: string) => <ModelCode>{val}</ModelCode>
    },
    {
      title: '显示名称',
      dataIndex: 'displayName',
      key: 'displayName',
      render: (val: string, record: AIModel) => (
        <Space size={8}>
          <Text style={{ fontWeight: 600, color: '#fafafa' }}>{val}</Text>
          {record.isDefault && <DefaultBadge>默认</DefaultBadge>}
        </Space>
      )
    },
    {
      title: '倍率',
      dataIndex: 'costMultiplier',
      key: 'costMultiplier',
      width: 100,
      render: (val: number) => <MultiplierText $value={val || 1}>{val || 1.0}x</MultiplierText>
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_: any, record: AIModel) => (
        <Space size={4}>
          {!record.isDefault && (
            <ActionButton
              variant="success"
              icon={<CheckCircleOutlined />}
              tooltip="设为默认"
              onClick={() => handleSetDefault(record.id)}
            />
          )}
          <ActionButton
            variant="default"
            icon={<EditOutlined />}
            tooltip="编辑"
            onClick={() => handleEditModel(record)}
          />
          <ActionButton
            variant="danger"
            icon={<DeleteOutlined />}
            tooltip="删除"
            confirmTitle="确认删除"
            confirmDescription="确定要删除这个模型吗？"
            onConfirm={() => handleDeleteModel(record.id)}
          />
        </Space>
      )
    }
  ]

  return (
    <PageContainer
      title="AI 配置"
      description="配置 AI 服务的 API 连接和可用模型"
      icon={<ApiOutlined />}
      breadcrumb={[{ title: 'AI 配置' }]}
    >
      {/* API 配置卡片 */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <InfoCard
          title="API 配置"
          description="配置 OpenAI 兼容的 API 服务"
          icon={<ApiOutlined />}
          iconGradient="linear-gradient(135deg, #0ea5e9, #06b6d4)"
        >
          {configLoaded && (
            <StatusAlert $success={!!isConfigComplete}>
              {isConfigComplete ? (
                <CheckCircleOutlined style={{ fontSize: 18, color: '#22c55e' }} />
              ) : (
                <ExclamationCircleOutlined style={{ fontSize: 18, color: '#f97316' }} />
              )}
              <Text style={{ 
                fontSize: 13, 
                color: isConfigComplete ? '#22c55e' : '#f97316',
                fontWeight: 500
              }}>
                {isConfigComplete ? 'API 配置完成，可以正常使用 AI 功能' : '请完成 API 配置后才能使用 AI 功能'}
              </Text>
            </StatusAlert>
          )}

          <Row gutter={16} align="bottom">
            <Col flex="1">
              <Form.Item label={<Space><ApiOutlined /> API 链接</Space>} style={{ marginBottom: 0 }}>
                <Input
                  placeholder="https://api.openai.com/v1"
                  value={aiConfig.api_base_url}
                  onChange={(e) => setAiConfig({ ...aiConfig, api_base_url: e.target.value })}
                  size="large"
                />
              </Form.Item>
            </Col>
            <Col flex="1">
              <Form.Item label={<Space><KeyOutlined /> API Key</Space>} style={{ marginBottom: 0 }}>
                <Input.Password
                  placeholder="sk-..."
                  value={aiConfig.api_key}
                  onChange={(e) => setAiConfig({ ...aiConfig, api_key: e.target.value })}
                  onFocus={() => {
                    if (aiConfig.api_key.includes('***')) {
                      setAiConfig({ ...aiConfig, api_key: '' })
                    }
                  }}
                  size="large"
                />
              </Form.Item>
            </Col>
            <Col flex="none">
              <Button 
                type="primary" 
                onClick={handleSaveAIConfig} 
                loading={savingConfig}
                size="large"
              >
                保存配置
              </Button>
            </Col>
          </Row>
        </InfoCard>
      </motion.div>

      {/* 每日免费字数配置 */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1 }}
        style={{ marginTop: 20 }}
      >
        <InfoCard
          title="每日免费字数配置"
          description="配置用户每日免费使用的字数额度，每天0点自动重置"
          icon={<GiftOutlined />}
          iconGradient="linear-gradient(135deg, #f97316, #ea580c)"
        >
          <Row gutter={16} align="middle">
            <Col flex="none">
              <Form.Item label="功能开关" style={{ marginBottom: 0 }}>
                <Switch
                  checked={dailyFreeEnabled}
                  onChange={setDailyFreeEnabled}
                  checkedChildren="开启"
                  unCheckedChildren="关闭"
                />
              </Form.Item>
            </Col>
            <Col flex="1">
              <Form.Item label="每日免费字数" style={{ marginBottom: 0 }}>
                <InputNumber
                  value={dailyFreeAmount}
                  onChange={(val) => setDailyFreeAmount(val || 0)}
                  min={0}
                  max={10000000}
                  step={1000}
                  formatter={(value) => `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                  parser={(value) => parseInt(value?.replace(/,/g, '') || '0', 10)}
                  size="large"
                  style={{ width: '100%' }}
                  disabled={!dailyFreeEnabled}
                  addonAfter="字"
                />
              </Form.Item>
            </Col>
            <Col flex="none">
              <Button 
                type="primary" 
                onClick={handleSaveDailyFreeConfig} 
                loading={savingDailyFree}
                size="large"
                style={{ background: 'linear-gradient(135deg, #f97316, #ea580c)' }}
              >
                保存配置
              </Button>
            </Col>
          </Row>

          <HintBox>
            <Text style={{ fontSize: 13, color: '#ea580c', fontWeight: 500 }}>💡 扣费逻辑说明</Text>
            <Text style={{ fontSize: 12, color: '#9a3412', marginTop: 6, display: 'block', lineHeight: 1.8 }}>
              1. 用户消费时，优先扣除每日免费字数<br/>
              2. 每日免费字数用完后，再扣除字数包余额<br/>
              3. 每日免费字数在每天0点自动重置为设定值
            </Text>
          </HintBox>
        </InfoCard>
      </motion.div>

      {/* 模型列表 */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.2 }}
        style={{ marginTop: 20 }}
      >
        <DataTable
          title="模型列表"
          description="配置可供用户选择的 AI 模型"
          columns={modelColumns}
          dataSource={models}
          rowKey="id"
          loading={loading}
          showSearch={false}
          onRefresh={loadModels}
          extra={
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddModel}>
              添加模型
            </Button>
          }
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={<Text style={{ color: 'rgba(250, 250, 250, 0.45)' }}>暂无模型，点击上方按钮添加</Text>}
                style={{ padding: '48px 0' }}
              />
            ),
          }}
        />
      </motion.div>

      {/* 模型编辑模态框 */}
      <Modal
        title={
          <Space size={12}>
            <div style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #a855f7, #9333ea)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              {editingModel ? <EditOutlined style={{ color: '#fff', fontSize: 18 }} /> : <PlusOutlined style={{ color: '#fff', fontSize: 18 }} />}
            </div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 600, color: '#fafafa' }}>
                {editingModel ? '编辑模型' : '添加模型'}
              </div>
              <div style={{ fontSize: 12, color: 'rgba(250, 250, 250, 0.45)' }}>
                {editingModel ? '修改模型配置信息' : '添加新的 AI 模型到系统'}
              </div>
            </div>
          </Space>
        }
        open={modelModalVisible}
        onOk={handleModelSubmit}
        onCancel={() => setModelModalVisible(false)}
        width={640}
        okText={editingModel ? '保存修改' : '添加模型'}
        cancelText="取消"
      >
        <Form form={modelForm} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item 
                name="modelId" 
                label="模型名称"
                rules={[{ required: true, message: '请输入模型名称' }]}
                extra="API 请求时使用的模型标识"
              >
                <Input placeholder="gpt-4" size="large" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item 
                name="displayName" 
                label="显示名称"
                rules={[{ required: true, message: '请输入显示名称' }]}
                extra="用户界面显示的友好名称"
              >
                <Input placeholder="GPT-4" size="large" />
              </Form.Item>
            </Col>
          </Row>
          
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item 
                name="costMultiplier" 
                label="字数点倍率"
                initialValue={1.0}
                extra="计费公式：(输入/10 + 输出) × 倍率"
              >
                <InputNumber placeholder="1.0" size="large" min={0.1} max={100} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item 
                name="sortOrder" 
                label="排序顺序"
                initialValue={0}
                extra="数字越小排序越靠前"
              >
                <InputNumber placeholder="0" size="large" min={0} max={999} step={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item 
            name="temperature" 
            label="默认温度"
            initialValue={1.0}
            extra="范围 0-2。较低温度输出更确定，较高温度输出更随机有创意"
          >
            <Slider min={0} max={2} step={0.1} marks={{ 0: '精确', 1: '平衡', 2: '创意' }} />
          </Form.Item>

          <Form.Item 
            name="description" 
            label="模型描述"
            extra="描述模型特点、适用场景、优势等信息"
          >
            <Input.TextArea 
              placeholder="例如：响应速度快，适合日常对话..." 
              rows={4}
              showCount
              maxLength={500}
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}

export default AIModelsPage
