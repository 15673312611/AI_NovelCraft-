import React, { useEffect, useState } from 'react'
import { Card, Typography, Button, message, Input, Space, Divider, Alert, Modal, Table, Popconfirm, Tag } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, CheckCircleOutlined, ApiOutlined } from '@ant-design/icons'
import { AI_PROVIDERS, AIConfig, getProviderInfo, CustomAPIConfig } from '../types/aiConfig'
import { saveAIConfig, loadAIConfig, isAIConfigValid } from '../utils/aiConfigStorage'
import {
	getCustomAPIConfigs,
	addCustomAPIConfig,
	updateCustomAPIConfig,
	deleteCustomAPIConfig,
	setActiveCustomConfigId,
	getActiveCustomConfigId,
	clearActiveCustomConfigId
} from '../utils/aiConfigStorage'
import { getCurrentConfigName } from '../utils/aiRequest'
import './SettingsPage.css'

const { Title, Text } = Typography

const SettingsPage: React.FC = () => {
	const [selectedProvider, setSelectedProvider] = useState<string>('deepseek')
	const [selectedModel, setSelectedModel] = useState<string>('deepseek-chat')
	const [apiKey, setApiKey] = useState<string>('')
	const [baseUrl, setBaseUrl] = useState<string>('')
	const [autosave, setAutosave] = useState<boolean>(true)
	const [availableModels, setAvailableModels] = useState<string[]>([])
	const [errors, setErrors] = useState<{[key: string]: string}>({})

	// 自定义API配置相关状态
	const [customConfigs, setCustomConfigs] = useState<CustomAPIConfig[]>([])
	const [activeConfigId, setActiveConfigIdState] = useState<string | null>(null)
	const [isModalVisible, setIsModalVisible] = useState(false)
	const [editingConfig, setEditingConfig] = useState<CustomAPIConfig | null>(null)
	const [modalForm, setModalForm] = useState({
		name: '',
		baseUrl: '',
		apiKey: '',
		model: '',
		description: ''
	})

	// 加载保存的配置
	useEffect(() => {
		const config = loadAIConfig()
		if (config) {
			setSelectedProvider(config.provider as string)
			setApiKey(config.apiKey)
			setSelectedModel(config.model)
			setBaseUrl(config.baseUrl || '')
			updateAvailableModels(config.provider)
		} else {
			// 设置默认值
			const defaultProvider = getProviderInfo('deepseek')
			if (defaultProvider) {
				setAvailableModels(defaultProvider.defaultModels)
				setBaseUrl(defaultProvider.defaultBaseUrl)
			}
		}

		// 加载自定义配置列表
		loadCustomConfigs()
	}, [])

	// 加载自定义配置
	const loadCustomConfigs = () => {
		const configs = getCustomAPIConfigs()
		setCustomConfigs(configs)
		setActiveConfigIdState(getActiveCustomConfigId())
	}

	// 更新可用模型列表
	const updateAvailableModels = (providerId: string) => {
		const provider = getProviderInfo(providerId)
		if (provider) {
			setAvailableModels(provider.defaultModels)
			// 如果当前选中的模型不在新列表中，设置为第一个模型
			if (!provider.defaultModels.includes(selectedModel)) {
				setSelectedModel(provider.defaultModels[0] || '')
			}
			// 自动设置baseUrl
			if (providerId !== 'custom') {
				setBaseUrl(provider.defaultBaseUrl)
			}
		}
	}

	// 切换服务商时的处理
	const handleProviderChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
		const value = e.target.value
		setSelectedProvider(value)
		updateAvailableModels(value)
	}

	// 验证表单
	const validate = (): boolean => {
		const newErrors: {[key: string]: string} = {}
		
		if (!selectedProvider) {
			newErrors.provider = '请选择AI服务商'
		}
		if (!apiKey) {
			newErrors.apiKey = '请输入API Key'
		}
		if (!selectedModel) {
			newErrors.model = '请选择模型'
		}
		
		setErrors(newErrors)
		return Object.keys(newErrors).length === 0
	}

	const onSave = async () => {
		if (!validate()) {
			message.error('请填写完整的AI配置信息')
			return
		}

		const aiConfig: AIConfig = {
			provider: selectedProvider as 'deepseek' | 'qwen' | 'kimi' | 'custom',
			apiKey: apiKey,
			model: selectedModel,
			baseUrl: baseUrl
		}
		
		if (!isAIConfigValid(aiConfig)) {
			message.error('请填写完整的AI配置信息')
			return
		}

		saveAIConfig(aiConfig)
		message.success('AI配置已保存到浏览器缓存')
	}

	const handleReload = () => {
		const config = loadAIConfig()
		if (config) {
			setSelectedProvider(config.provider as string)
			setApiKey(config.apiKey)
			setSelectedModel(config.model)
			setBaseUrl(config.baseUrl || '')
			updateAvailableModels(config.provider)
			message.info('已重新加载配置')
		}
	}

	// ========== 自定义API配置管理 ==========

	const showAddModal = () => {
		setEditingConfig(null)
		setModalForm({
			name: '',
			baseUrl: '',
			apiKey: '',
			model: '',
			description: ''
		})
		setIsModalVisible(true)
	}

	const showEditModal = (config: CustomAPIConfig) => {
		setEditingConfig(config)
		setModalForm({
			name: config.name,
			baseUrl: config.baseUrl,
			apiKey: config.apiKey,
			model: config.model,
			description: config.description || ''
		})
		setIsModalVisible(true)
	}

	const handleModalOk = () => {
		// 验证表单
		if (!modalForm.name || !modalForm.baseUrl || !modalForm.apiKey || !modalForm.model) {
			message.error('请填写完整的配置信息')
			return
		}

		try {
			if (editingConfig) {
				// 更新配置
				updateCustomAPIConfig(editingConfig.id, {
					name: modalForm.name,
					baseUrl: modalForm.baseUrl,
					apiKey: modalForm.apiKey,
					model: modalForm.model,
					description: modalForm.description
				})
				message.success('配置已更新')
			} else {
				// 添加新配置
				addCustomAPIConfig({
					name: modalForm.name,
					baseUrl: modalForm.baseUrl,
					apiKey: modalForm.apiKey,
					model: modalForm.model,
					description: modalForm.description
				})
				message.success('配置已添加')
			}
			loadCustomConfigs()
			setIsModalVisible(false)
		} catch (error) {
			message.error('操作失败')
			console.error(error)
		}
	}

	const handleModalCancel = () => {
		setIsModalVisible(false)
		setEditingConfig(null)
	}

	const handleDeleteConfig = (id: string) => {
		try {
			deleteCustomAPIConfig(id)
			message.success('配置已删除')
			loadCustomConfigs()
		} catch (error) {
			message.error('删除失败')
			console.error(error)
		}
	}

	const handleSetActive = (id: string) => {
		try {
			setActiveCustomConfigId(id)
			setActiveConfigIdState(id)
			message.success('已设置为当前使用的配置，所有AI功能将使用此配置')
			// 刷新组件状态
			loadCustomConfigs()
		} catch (error) {
			message.error('设置失败')
			console.error(error)
		}
	}

	const handleClearActive = () => {
		try {
			clearActiveCustomConfigId()
			setActiveConfigIdState(null)
			message.success('已切换到预设AI服务配置')
			// 刷新组件状态
			loadCustomConfigs()
		} catch (error) {
			message.error('操作失败')
			console.error(error)
		}
	}

	const columns = [
		{
			title: '配置名称',
			dataIndex: 'name',
			key: 'name',
			render: (text: string, record: CustomAPIConfig) => (
				<Space>
					<span className="config-name">{text}</span>
					{activeConfigId === record.id && (
						<CheckCircleOutlined style={{ color: '#52c41a' }} />
					)}
				</Space>
			)
		},
		{
			title: '基础URL',
			dataIndex: 'baseUrl',
			key: 'baseUrl',
			ellipsis: true
		},
		{
			title: '模型',
			dataIndex: 'model',
			key: 'model'
		},
		{
			title: '说明',
			dataIndex: 'description',
			key: 'description',
			ellipsis: true
		},
		{
			title: '操作',
			key: 'action',
			width: 200,
			render: (_: any, record: CustomAPIConfig) => (
				<Space size="small">
					{activeConfigId !== record.id && (
						<Button
							type="link"
							size="small"
							onClick={() => handleSetActive(record.id)}
						>
							设为当前
						</Button>
					)}
					<Button
						type="link"
						size="small"
						icon={<EditOutlined />}
						onClick={() => showEditModal(record)}
					>
						编辑
					</Button>
					<Popconfirm
						title="确定要删除这个配置吗？"
						onConfirm={() => handleDeleteConfig(record.id)}
						okText="确定"
						cancelText="取消"
					>
						<Button
							type="link"
							size="small"
							danger
							icon={<DeleteOutlined />}
						>
							删除
						</Button>
					</Popconfirm>
				</Space>
			)
		}
	]

	return (
		<div className="settings-page">
			<div className="page-header">
				<Title level={2}>设置</Title>
				<Space>
					<Text>AI服务配置</Text>
					<Tag icon={<ApiOutlined />} color="success">
						当前使用: {getCurrentConfigName()}
					</Tag>
				</Space>
			</div>

			{/* 自定义API配置管理 */}
			<Card 
				title="自定义API配置" 
				extra={
					<Space>
						{activeConfigId && (
							<Button 
								onClick={handleClearActive}
							>
								切换到预设配置
							</Button>
						)}
						<Button 
							type="primary" 
							icon={<PlusOutlined />} 
							onClick={showAddModal}
						>
							添加配置
						</Button>
					</Space>
				}
				style={{ marginBottom: 16 }}
			>
				<Alert
					message="说明"
					description="这里可以添加任何兼容OpenAI接口格式的API配置（如GPT、Claude、本地部署的模型等）。配置保存在浏览器本地，不会上传到服务器。设为当前后，所有AI功能将使用该配置。"
					type="info"
					showIcon
					style={{ marginBottom: 16 }}
				/>

				<Table
					columns={columns}
					dataSource={customConfigs}
					rowKey="id"
					pagination={false}
					locale={{ emptyText: '暂无配置，点击上方"添加配置"按钮开始添加' }}
				/>
			</Card>

			<Card title="预设AI服务配置" style={{ marginBottom: 16 }}>
				<Alert
					message="重要提示"
					description="所有AI配置信息仅保存在浏览器本地缓存中，不会上传到服务器。如果上方有激活的自定义配置，系统会优先使用自定义配置。"
					type="warning"
					showIcon
					style={{ marginBottom: 24 }}
				/>

				<div className="settings-form">
					<div className="form-group">
						<label className="form-label">
							AI服务商 <span className="required">*</span>
						</label>
						<select 
							className={`custom-select ${errors.provider ? 'error' : ''}`}
							value={selectedProvider}
							onChange={handleProviderChange}
						>
							{AI_PROVIDERS.map(provider => (
								<option key={provider.id} value={provider.id}>
									{provider.name}
								</option>
							))}
						</select>
						{errors.provider && <span className="error-text">{errors.provider}</span>}
					</div>

					<div className="form-group">
						<label className="form-label">
							API Key <span className="required">*</span>
						</label>
						<Input.Password 
							placeholder="请输入您的API密钥"
							value={apiKey}
							onChange={(e: React.ChangeEvent<HTMLInputElement>) => setApiKey(e.target.value)}
							status={errors.apiKey ? 'error' : ''}
						/>
						{errors.apiKey && <span className="error-text">{errors.apiKey}</span>}
					</div>

					<div className="form-group">
						<label className="form-label">
							模型 <span className="required">*</span>
						</label>
						<select 
							className={`custom-select ${errors.model ? 'error' : ''}`}
							value={selectedModel}
							onChange={(e) => setSelectedModel(e.target.value)}
						>
							{availableModels.length === 0 && (
								<option value="">请先选择服务商</option>
							)}
							{availableModels.map(model => (
								<option key={model} value={model}>
									{model}
								</option>
							))}
						</select>
						{errors.model && <span className="error-text">{errors.model}</span>}
					</div>

					<div className="form-group">
						<label className="form-label">
							API Base URL
							<span className="form-hint"> (通常使用默认值即可，除非您使用代理或自定义接口)</span>
						</label>
						<Input 
							placeholder="API基础URL"
							value={baseUrl}
							onChange={(e: React.ChangeEvent<HTMLInputElement>) => setBaseUrl(e.target.value)}
							disabled={selectedProvider !== 'custom'}
						/>
					</div>

					<Divider />

					<div className="form-group">
						<label className="form-label">自动保存</label>
						<div className="switch-wrapper">
							<label className="custom-switch">
								<input 
									type="checkbox" 
									checked={autosave}
									onChange={(e) => setAutosave(e.target.checked)}
								/>
								<span className="slider"></span>
							</label>
						</div>
					</div>

					<Space>
						<Button type="primary" onClick={onSave}>
							保存配置
						</Button>
						<Button onClick={handleReload}>
							重新加载
						</Button>
					</Space>
				</div>
			</Card>

			<Card title="服务商说明">
				<Space direction="vertical" style={{ width: '100%' }}>
					<div>
						<Text strong>DeepSeek:</Text> 
						<Text type="secondary"> 高性价比的中文AI模型，适合长文本生成</Text>
					</div>
					<div>
						<Text strong>通义千问:</Text> 
						<Text type="secondary"> 阿里云的AI服务，支持多种模型规格</Text>
					</div>
					<div>
						<Text strong>Kimi (月之暗面):</Text> 
						<Text type="secondary"> 提供K2系列高性能模型、Latest通用模型和长思考模型，支持最高262K上下文</Text>
					</div>
				</Space>
			</Card>

			{/* 添加/编辑配置弹窗 */}
			<Modal
				title={editingConfig ? '编辑配置' : '添加配置'}
				open={isModalVisible}
				onOk={handleModalOk}
				onCancel={handleModalCancel}
				width={600}
				okText="保存"
				cancelText="取消"
			>
				<div className="modal-form">
					<div className="form-group">
						<label className="form-label">
							配置名称 <span className="required">*</span>
						</label>
						<Input
							placeholder="例如：我的GPT-4配置"
							value={modalForm.name}
							onChange={(e: React.ChangeEvent<HTMLInputElement>) => setModalForm({ ...modalForm, name: e.target.value })}
						/>
					</div>

					<div className="form-group">
						<label className="form-label">
							基础URL <span className="required">*</span>
						</label>
						<Input
							placeholder="例如：https://api.openai.com/v1"
							value={modalForm.baseUrl}
							onChange={(e: React.ChangeEvent<HTMLInputElement>) => setModalForm({ ...modalForm, baseUrl: e.target.value })}
						/>
						<Text type="secondary" style={{ fontSize: 12 }}>
							请输入完整的API基础URL，通常以 /v1 结尾
						</Text>
					</div>

					<div className="form-group">
						<label className="form-label">
							API Key <span className="required">*</span>
						</label>
						<Input.Password
							placeholder="请输入API密钥"
							value={modalForm.apiKey}
							onChange={(e: React.ChangeEvent<HTMLInputElement>) => setModalForm({ ...modalForm, apiKey: e.target.value })}
						/>
					</div>

					<div className="form-group">
						<label className="form-label">
							模型名称 <span className="required">*</span>
						</label>
						<Input
							placeholder="例如：gpt-4、claude-3-opus-20240229"
							value={modalForm.model}
							onChange={(e: React.ChangeEvent<HTMLInputElement>) => setModalForm({ ...modalForm, model: e.target.value })}
						/>
						<Text type="secondary" style={{ fontSize: 12 }}>
							请输入准确的模型名称，与API文档保持一致
						</Text>
					</div>

					<div className="form-group">
						<label className="form-label">说明（可选）</label>
						<Input.TextArea
							placeholder="备注信息，如：用于测试环境、生产环境等"
							value={modalForm.description}
							onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setModalForm({ ...modalForm, description: e.target.value })}
							rows={3}
						/>
					</div>
				</div>
			</Modal>
		</div>
	)
}

export default SettingsPage
