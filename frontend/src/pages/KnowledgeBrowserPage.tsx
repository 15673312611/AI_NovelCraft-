import React, { useEffect, useState } from 'react'
import { Card, Input, Select, Typography, Row, Col, Tag, Space, Spin, Empty, message, Button, Rate } from 'antd'
import { SearchOutlined, FireOutlined } from '@ant-design/icons'
import { writingTechniqueService, WritingTechnique } from '@/services/writingTechniqueService'
import './KnowledgeBrowserPage.css'

const { Title, Text } = Typography
const { Option } = Select

const KnowledgeBrowserPage: React.FC = () => {
	const [loading, setLoading] = useState(false)
	const [techniques, setTechniques] = useState<WritingTechnique[]>([])
	const [category, setCategory] = useState<string | undefined>(undefined)
	const [search, setSearch] = useState('')

	const fetchData = async () => {
		setLoading(true)
		try {
			const response = await writingTechniqueService.getTechniques({ page: 0, size: 20, category, search })
			setTechniques(response.content || [])
		} catch (err) {
			message.warning('后端接口暂不可用，已显示示例数据')
			setTechniques([
				{
					id: 'demo-1',
					name: '细腻的心理描写',
					category: 'EMOTION',
					description: '通过内心独白、情绪变化细节展现人物内在冲突',
					effectiveness: 4.6,
					tags: ['心理', '情绪', '内心独白'],
					usageCount: 126
				},
				{
					id: 'demo-2',
					name: '对话推进剧情',
					category: 'DIALOGUE',
					description: '用对话信息差与冲突推动故事发展',
					effectiveness: 4.3,
					tags: ['对话', '冲突', '信息差'],
					usageCount: 92
				}
			])
		} finally {
			setLoading(false)
		}
	}

	useEffect(() => {
		fetchData()
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [category])

	return (
		<div className="knowledge-browser-page">
			<div className="page-header">
				<Title level={2}>
					<FireOutlined /> 知识库浏览
				</Title>
				<Text>浏览与搜索写作技巧与参考标签</Text>
			</div>

			<Card className="toolbar-card">
				<Space wrap>
					<Input
						allowClear
						placeholder="搜索技巧名称或标签"
						prefix={<SearchOutlined />}
						value={search}
						onChange={(e) => setSearch(e.target.value)}
						style={{ width: 280 }}
					/>
					<Select
						allowClear
						placeholder="选择分类"
						style={{ width: 200 }}
						value={category}
						onChange={(val) => setCategory(val)}
					>
						<Option value="NARRATION">叙述</Option>
						<Option value="DIALOGUE">对话</Option>
						<Option value="DESCRIPTION">描写</Option>
						<Option value="EMOTION">情感</Option>
						<Option value="ACTION">动作</Option>
						<Option value="ATMOSPHERE">氛围</Option>
						<Option value="CHARACTER">角色</Option>
						<Option value="PLOT">情节</Option>
					</Select>
					<Button type="primary" onClick={fetchData}>搜索</Button>
				</Space>
			</Card>

			{loading ? (
				<div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
			) : techniques.length === 0 ? (
				<Empty description="暂无数据" />
			) : (
				<Row gutter={[16, 16]}>
					{techniques.map((t) => (
						<Col xs={24} sm={12} md={8} lg={6} key={t.id}>
							<Card
								title={t.name}
								extra={<Tag color="blue">{t.category}</Tag>}
								hoverable
							>
								<div style={{ minHeight: 60 }}>{t.description}</div>
								<div style={{ marginTop: 8 }}>
									<Space wrap>
										{(t.tags || []).map((tag) => (
											<Tag key={tag}>{tag}</Tag>
										))}
									</Space>
								</div>
								<div style={{ marginTop: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
									<Space>
										<Text type="secondary">效果</Text>
										<Rate disabled allowHalf defaultValue={(t.effectiveness || 0) / 1} count={5} />
									</Space>
									<Text type="secondary">使用 {t.usageCount || 0}</Text>
								</div>
							</Card>
						</Col>
					))}
				</Row>
			)}
		</div>
	)
}

export default KnowledgeBrowserPage

