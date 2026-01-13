import React, { useEffect, useState } from 'react'
import { Card, Typography, Space, Divider, Table, Tag, Statistic, Row, Col, Spin, Empty, message } from 'antd'
import { ThunderboltOutlined, HistoryOutlined, RobotOutlined, InfoCircleOutlined } from '@ant-design/icons'
import { creditService, UserCreditInfo, CreditTransaction } from '../services/creditService'
import './SettingsPage.css'

const { Title, Text } = Typography

const SettingsPage: React.FC = () => {
	const [creditInfo, setCreditInfo] = useState<UserCreditInfo | null>(null)
	const [transactions, setTransactions] = useState<CreditTransaction[]>([])
	const [loading, setLoading] = useState(true)
	const [transactionLoading, setTransactionLoading] = useState(false)
	const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })

	useEffect(() => {
		loadData()
	}, [])

	const loadData = async () => {
		setLoading(true)
		try {
			const credit = await creditService.getBalance()
			setCreditInfo(credit)
			await loadTransactions(0)
		} catch (error: any) {
			console.error('加载数据失败:', error)
			message.error(error?.message || '加载数据失败')
		} finally {
			setLoading(false)
		}
	}

	const loadTransactions = async (page: number) => {
		setTransactionLoading(true)
		try {
			const result = await creditService.getTransactions(page, pagination.pageSize)
			setTransactions(result.content)
			setPagination(prev => ({
				...prev,
				current: page + 1,
				total: result.totalElements
			}))
		} catch (error) {
			console.error('加载交易记录失败:', error)
		} finally {
			setTransactionLoading(false)
		}
	}

	const handleTableChange = (paginationConfig: any) => {
		loadTransactions(paginationConfig.current - 1)
	}

	const transactionColumns = [
		{
			title: '时间',
			dataIndex: 'createdAt',
			key: 'createdAt',
			width: 180,
			render: (val: string) => new Date(val).toLocaleString('zh-CN')
		},
		{
			title: '类型',
			dataIndex: 'type',
			key: 'type',
			width: 100,
			render: (type: string) => {
				const typeMap: Record<string, { color: string; text: string }> = {
					RECHARGE: { color: 'green', text: '充值' },
					CONSUME: { color: 'red', text: '消费' },
					GIFT: { color: 'blue', text: '赠送' },
					REFUND: { color: 'orange', text: '退款' },
					ADMIN_ADJUST: { color: 'purple', text: '调整' }
				}
				const info = typeMap[type] || { color: 'default', text: type }
				return <Tag color={info.color}>{info.text}</Tag>
			}
		},
		{
			title: '金额',
			dataIndex: 'amount',
			key: 'amount',
			width: 120,
			render: (val: number) => (
				<span style={{ color: val >= 0 ? '#52c41a' : '#ff4d4f', fontWeight: 500 }}>
					{val >= 0 ? '+' : ''}{val.toFixed(4)}
				</span>
			)
		},
		{
			title: '余额',
			dataIndex: 'balanceAfter',
			key: 'balanceAfter',
			width: 120,
			render: (val: number) => val.toFixed(4)
		},
		{
			title: '模型',
			dataIndex: 'modelId',
			key: 'modelId',
			width: 150,
			render: (modelId: string) => modelId || '-'
		},
		{
			title: '字数',
			key: 'tokens',
			width: 150,
			render: (_: any, record: CreditTransaction) => {
				if (record.inputTokens || record.outputTokens) {
					return (
						<span>
							入:{record.inputTokens || 0} / 出:{record.outputTokens || 0}
						</span>
					)
				}
				return '-'
			}
		},
		{
			title: '描述',
			dataIndex: 'description',
			key: 'description',
			ellipsis: true
		}
	]

	if (loading) {
		return (
			<div className="settings-page loading">
				<Spin size="large" tip="加载中..." />
			</div>
		)
	}

	return (
		<div className="settings-page">
			<div className="page-header">
				<Title level={2}>账户设置</Title>
				<Text type="secondary">查看您的字数点余额和使用记录</Text>
			</div>

			{/* 字数点概览 */}
			<Card 
				title={
					<Space>
						<ThunderboltOutlined style={{ color: '#faad14' }} />
						<span>字数点余额</span>
					</Space>
				}
				style={{ marginBottom: 16 }}
			>
				{creditInfo ? (
					<Row gutter={24}>
						<Col span={6}>
							<Statistic
								title="当前余额"
								value={creditInfo.balance}
								precision={2}
								valueStyle={{ color: '#faad14', fontSize: 28 }}
								suffix="点"
							/>
						</Col>
						<Col span={6}>
							<Statistic
								title="可用余额"
								value={creditInfo.availableBalance}
								precision={2}
								suffix="点"
							/>
						</Col>
						<Col span={6}>
							<Statistic
								title="今日消费"
								value={creditInfo.todayConsumption}
								precision={2}
								valueStyle={{ color: '#ff4d4f' }}
								suffix="点"
							/>
						</Col>
						<Col span={6}>
							<Statistic
								title="本月消费"
								value={creditInfo.monthConsumption}
								precision={2}
								valueStyle={{ color: '#ff4d4f' }}
								suffix="点"
							/>
						</Col>
					</Row>
				) : (
					<Empty description="暂无数据" />
				)}

				<Divider />

				<Row gutter={24}>
					<Col span={8}>
						<Statistic
							title="累计充值"
							value={creditInfo?.totalRecharged || 0}
							precision={2}
							valueStyle={{ color: '#52c41a' }}
							suffix="点"
						/>
					</Col>
					<Col span={8}>
						<Statistic
							title="累计消费"
							value={creditInfo?.totalConsumed || 0}
							precision={2}
							suffix="点"
						/>
					</Col>
					<Col span={8}>
						<Statistic
							title="累计赠送"
							value={creditInfo?.totalGifted || 0}
							precision={2}
							valueStyle={{ color: '#1890ff' }}
							suffix="点"
						/>
					</Col>
				</Row>
			</Card>

			{/* 交易记录 */}
			<Card 
				title={
					<Space>
						<HistoryOutlined />
						<span>交易记录</span>
					</Space>
				}
			>
				<Table
					columns={transactionColumns}
					dataSource={transactions}
					rowKey="id"
					loading={transactionLoading}
					pagination={{
						...pagination,
						showSizeChanger: false,
						showTotal: total => `共 ${total} 条记录`
					}}
					onChange={handleTableChange}
					scroll={{ x: 900 }}
				/>
			</Card>
		</div>
	)
}

export default SettingsPage
