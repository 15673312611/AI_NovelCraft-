import React, { useEffect, useState } from 'react'
import { Card, Form, Switch, Typography, Button, message, Select, Input, Space, Divider, Alert } from 'antd'
import { AI_PROVIDERS, AIConfig, getProviderInfo } from '../types/aiConfig'
import { saveAIConfig, loadAIConfig, isAIConfigValid } from '../utils/aiConfigStorage'

const { Title, Text } = Typography
const { Option } = Select

const SettingsPage: React.FC = () => {
	const [form] = Form.useForm()
	const [selectedProvider, setSelectedProvider] = useState<string>('deepseek')
	const [availableModels, setAvailableModels] = useState<string[]>([])

	// 加载保存的配置
	useEffect(() => {
		const config = loadAIConfig()
		if (config) {
			form.setFieldsValue({
				provider: config.provider,
				apiKey: config.apiKey,
				model: config.model,
				baseUrl: config.baseUrl,
				autosave: true
			})
			setSelectedProvider(config.provider)
			updateAvailableModels(config.provider)
		}
	}, [form])

	// 更新可用模型列表
	const updateAvailableModels = (providerId: string) => {
		const provider = getProviderInfo(providerId)
		if (provider) {
			setAvailableModels(provider.defaultModels)
			// 如果当前选中的模型不在新列表中，清空模型选择
			const currentModel = form.getFieldValue('model')
			if (!provider.defaultModels.includes(currentModel)) {
				form.setFieldValue('model', provider.defaultModels[0] || '')
			}
			// 自动设置baseUrl
			if (providerId !== 'custom') {
				form.setFieldValue('baseUrl', provider.defaultBaseUrl)
			}
		}
	}

	// 切换服务商时的处理
	const handleProviderChange = (value: string) => {
		setSelectedProvider(value)
		updateAvailableModels(value)
	}

	const onSave = async () => {
		try {
			const values = await form.validateFields()
			const aiConfig: AIConfig = {
				provider: values.provider,
				apiKey: values.apiKey,
				model: values.model,
				baseUrl: values.baseUrl
			}
			
			if (!isAIConfigValid(aiConfig)) {
				message.error('请填写完整的AI配置信息')
				return
			}

			saveAIConfig(aiConfig)
			message.success('AI配置已保存到浏览器缓存')
		} catch (error) {
			message.error('保存失败，请检查配置')
		}
	}

	return (
		<div className="settings-page">
			<div className="page-header">
				<Title level={2}>设置</Title>
				<Text>AI服务配置</Text>
			</div>

			<Card title="AI服务配置" style={{ marginBottom: 16 }}>
				<Alert
					message="重要提示"
					description="所有AI配置信息仅保存在浏览器本地缓存中，不会上传到服务器。请妥善保管您的API密钥。"
					type="info"
					showIcon
					style={{ marginBottom: 24 }}
				/>

				<Form 
					layout="vertical" 
					form={form} 
					initialValues={{
						provider: 'deepseek',
						model: 'deepseek-chat',
						autosave: true
					}}
				>
					<Form.Item 
						label="AI服务商" 
						name="provider" 
						rules={[{ required: true, message: '请选择AI服务商' }]}
					>
						<Select 
							style={{ width: '100%' }}
							onChange={handleProviderChange}
						>
							{AI_PROVIDERS.map(provider => (
								<Option key={provider.id} value={provider.id}>
									{provider.name}
								</Option>
							))}
						</Select>
					</Form.Item>

					<Form.Item 
						label="API Key" 
						name="apiKey" 
						rules={[{ required: true, message: '请输入API Key' }]}
					>
						<Input.Password 
							placeholder="请输入您的API密钥"
							style={{ width: '100%' }}
						/>
					</Form.Item>

					<Form.Item 
						label="模型" 
						name="model" 
						rules={[{ required: true, message: '请选择模型' }]}
					>
						<Select 
							style={{ width: '100%' }}
							placeholder="请选择模型"
						>
							{availableModels.map(model => (
								<Option key={model} value={model}>
									{model}
								</Option>
							))}
						</Select>
					</Form.Item>

					<Form.Item 
						label="API Base URL" 
						name="baseUrl"
						tooltip="通常使用默认值即可，除非您使用代理或自定义接口"
					>
						<Input 
							placeholder="API基础URL"
							style={{ width: '100%' }}
							disabled={selectedProvider !== 'custom'}
						/>
					</Form.Item>

					<Divider />

					<Form.Item label="自动保存" name="autosave" valuePropName="checked">
						<Switch />
					</Form.Item>

					<Space>
						<Button type="primary" onClick={onSave}>
							保存配置
						</Button>
						<Button 
							onClick={() => {
								const config = loadAIConfig()
								form.setFieldsValue(config)
								message.info('已重新加载配置')
							}}
						>
							重新加载
						</Button>
					</Space>
				</Form>
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
		</div>
	)
}

export default SettingsPage

