import React from 'react'
import { Card, Form, Input, Button, Typography, message } from 'antd'

const { Title, Text } = Typography

const ProfilePage: React.FC = () => {
	const [form] = Form.useForm()

	const onSave = async () => {
		try {
			await form.validateFields()
			message.success('已保存')
		} catch {}
	}

	return (
		<div className="profile-page">
			<div className="page-header">
				<Title level={2}>个人资料</Title>
				<Text>维护您的基本信息</Text>
			</div>
			<Card>
				<Form form={form} layout="vertical" initialValues={{ username: '', email: '' }}>
					<Form.Item label="用户名" name="username" rules={[{ required: true }]}>
						<Input placeholder="输入用户名" />
					</Form.Item>
					<Form.Item label="邮箱" name="email" rules={[{ required: true, type: 'email' }]}>
						<Input placeholder="输入邮箱" />
					</Form.Item>
					<Button type="primary" onClick={onSave}>保存</Button>
				</Form>
			</Card>
		</div>
	)
}

export default ProfilePage

