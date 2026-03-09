import { useEffect, useState } from 'react'
import {
  Input,
  Space,
  Modal,
  Form,
  InputNumber,
  message,
  Row,
  Col,
  Select,
  Tabs,
  Typography,
  Button,
  Popconfirm,
  Switch,
  Divider,
} from 'antd'
import {
  GiftOutlined,
  DollarOutlined,
  EditOutlined,
  ThunderboltOutlined,
  WalletOutlined,
  PlusOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import styled from '@emotion/styled'
import {
  adminCreditService,
  UserCredit,
  CreditTransaction,
  CreditStatistics,
  ModelUsageStats,
  CreditPackage,
  YiPayConfig,
} from '@/services/adminCreditService'
import { PageContainer, DataTable, StatCard, StatusTag, ActionButton } from '@/components'

const { Text } = Typography

const shouldForwardProp = (prop: string) => !prop.startsWith('$')

const BalanceText = styled.span`
  color: #0ea5e9;
  font-weight: 700;
  font-size: 14px;
`

const AmountText = styled('span', { shouldForwardProp })<{ $positive?: boolean }>`
  color: ${props => (props.$positive ? '#22c55e' : '#ef4444')};
  font-weight: 600;
`

const CreditsPage = () => {
  const [activeTab, setActiveTab] = useState('users')
  const [loading, setLoading] = useState(false)
  const [userCredits, setUserCredits] = useState<UserCredit[]>([])
  const [transactions, setTransactions] = useState<CreditTransaction[]>([])
  const [statistics, setStatistics] = useState<CreditStatistics | null>(null)
  const [modelUsage, setModelUsage] = useState<ModelUsageStats[]>([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const [keyword, setKeyword] = useState('')
  const [transactionType, setTransactionType] = useState<string>('')

  const [packages, setPackages] = useState<CreditPackage[]>([])
  const [packageModalVisible, setPackageModalVisible] = useState(false)
  const [editingPackage, setEditingPackage] = useState<CreditPackage | null>(null)
  const [packageForm] = Form.useForm()

  const [bonusLoading, setBonusLoading] = useState(false)
  const [registrationBonus, setRegistrationBonus] = useState<string>('0')
  const [bonusForm] = Form.useForm()

  const [paymentLoading, setPaymentLoading] = useState(false)
  const [paymentVerifyLoading, setPaymentVerifyLoading] = useState(false)
  const [paymentForm] = Form.useForm<YiPayConfig>()

  const [modalVisible, setModalVisible] = useState(false)
  const [modalType, setModalType] = useState<'recharge' | 'gift' | 'adjust'>('recharge')
  const [selectedUser, setSelectedUser] = useState<UserCredit | null>(null)
  const [form] = Form.useForm()

  useEffect(() => {
    loadStatistics()
    loadModelUsage()
  }, [])

  useEffect(() => {
    if (activeTab === 'users') {
      loadUserCredits()
    } else if (activeTab === 'transactions') {
      loadTransactions()
    } else if (activeTab === 'packages') {
      loadPackages()
      loadBonus()
      loadPaymentConfig()
    }
  }, [activeTab, pagination.current, pagination.pageSize, keyword, transactionType])

  const loadPackages = async () => {
    setLoading(true)
    try {
      const res: any = await adminCreditService.getPackages()
      setPackages(res)
    } catch {
      message.error('加载套餐失败')
    } finally {
      setLoading(false)
    }
  }

  const loadBonus = async () => {
    try {
      const res: any = await adminCreditService.getRegistrationBonus()
      setRegistrationBonus(res)
      bonusForm.setFieldsValue({ amount: res })
    } catch {
      message.error('加载赠送配置失败')
    }
  }

  const loadPaymentConfig = async () => {
    try {
      const res: any = await adminCreditService.getPaymentConfig()
      paymentForm.setFieldsValue({
        enabled: !!res.enabled,
        gatewayUrl: res.gatewayUrl || '',
        pid: res.pid || '',
        key: res.key || '',
        notifyUrl: res.notifyUrl || '',
        returnUrl: res.returnUrl || '',
        orderExpireMinutes: Number(res.orderExpireMinutes || 30),
        supportedTypes: Array.isArray(res.supportedTypes) && res.supportedTypes.length > 0 ? res.supportedTypes : ['alipay', 'wxpay'],
      })
    } catch {
      message.error('加载支付配置失败')
    }
  }

  const loadStatistics = async () => {
    try {
      const res = await adminCreditService.getStatistics()
      setStatistics(res as any)
    } catch {
      message.error('加载统计失败')
    }
  }

  const loadModelUsage = async () => {
    try {
      const res = await adminCreditService.getModelUsageStats(30)
      setModelUsage(res as any)
    } catch {
      message.error('加载模型统计失败')
    }
  }

  const loadUserCredits = async () => {
    setLoading(true)
    try {
      const res: any = await adminCreditService.getUserCredits({
        page: pagination.current - 1,
        size: pagination.pageSize,
        keyword: keyword || undefined,
      })
      setUserCredits(res.content || [])
      setPagination(prev => ({ ...prev, total: res.totalElements || 0 }))
    } catch {
      message.error('加载用户字数点失败')
    } finally {
      setLoading(false)
    }
  }

  const loadTransactions = async () => {
    setLoading(true)
    try {
      const res: any = await adminCreditService.getTransactions({
        page: pagination.current - 1,
        size: pagination.pageSize,
        type: transactionType || undefined,
      })
      setTransactions(res.content || [])
      setPagination(prev => ({ ...prev, total: res.totalElements || 0 }))
    } catch {
      message.error('加载交易记录失败')
    } finally {
      setLoading(false)
    }
  }

  const handleOperation = (type: 'recharge' | 'gift' | 'adjust', user: UserCredit) => {
    setModalType(type)
    setSelectedUser(user)
    setModalVisible(true)
    form.resetFields()
  }

  const handleAddPackage = () => {
    setEditingPackage(null)
    packageForm.resetFields()
    packageForm.setFieldsValue({ isActive: true, sortOrder: 0 })
    setPackageModalVisible(true)
  }

  const handleEditPackage = (record: CreditPackage) => {
    setEditingPackage(record)
    packageForm.setFieldsValue(record)
    setPackageModalVisible(true)
  }

  const handleDeletePackage = async (id: number) => {
    try {
      await adminCreditService.deletePackage(id)
      message.success('删除成功')
      loadPackages()
    } catch {
      message.error('删除失败')
    }
  }

  const handlePackageModalOk = async () => {
    try {
      const values = await packageForm.validateFields()
      if (editingPackage) {
        await adminCreditService.updatePackage(editingPackage.id, values)
        message.success('更新成功')
      } else {
        await adminCreditService.createPackage(values)
        message.success('创建成功')
      }
      setPackageModalVisible(false)
      loadPackages()
    } catch {
      message.error('保存套餐失败')
    }
  }

  const handleSaveBonus = async () => {
    try {
      setBonusLoading(true)
      const values = await bonusForm.validateFields()
      await adminCreditService.updateRegistrationBonus(String(values.amount))
      setRegistrationBonus(String(values.amount))
      message.success('全局赠送配置已保存')
    } catch {
      message.error('保存失败')
    } finally {
      setBonusLoading(false)
    }
  }

  const handleSavePaymentConfig = async () => {
    try {
      const values = await paymentForm.validateFields()
      setPaymentLoading(true)
      await adminCreditService.updatePaymentConfig({
        enabled: !!values.enabled,
        gatewayUrl: values.gatewayUrl || '',
        pid: values.pid || '',
        key: values.key || '',
        notifyUrl: values.notifyUrl || '',
        returnUrl: values.returnUrl || '',
        orderExpireMinutes: Number(values.orderExpireMinutes || 30),
        supportedTypes: values.supportedTypes || [],
      })
      message.success('支付配置已保存')
      await loadPaymentConfig()
    } catch {
      message.error('支付配置保存失败')
    } finally {
      setPaymentLoading(false)
    }
  }

  const handleVerifyPaymentConfig = async () => {
    try {
      const values = await paymentForm.validateFields()
      setPaymentVerifyLoading(true)
      const res: any = await adminCreditService.verifyPaymentConfig({
        gatewayUrl: values.gatewayUrl || '',
        pid: values.pid || '',
        key: values.key || '',
        supportedTypes: values.supportedTypes || [],
      })
      if (res?.success) {
        message.success(res?.message || '连接测试成功')
      } else {
        message.error(res?.message || '连接测试失败')
      }
    } catch (error: any) {
      message.error(error?.message || '连接测试失败')
    } finally {
      setPaymentVerifyLoading(false)
    }
  }

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields()
      if (!selectedUser) return

      const data = { amount: values.amount, description: values.description }

      if (modalType === 'recharge') {
        await adminCreditService.recharge(selectedUser.userId, data)
        message.success('充值成功')
      } else if (modalType === 'gift') {
        await adminCreditService.gift(selectedUser.userId, data)
        message.success('赠送成功')
      } else {
        await adminCreditService.adjustBalance(selectedUser.userId, data)
        message.success('调整成功')
      }

      setModalVisible(false)
      loadUserCredits()
      loadStatistics()
    } catch {
      message.error('操作失败')
    }
  }

  const userColumns = [
    {
      title: '用户ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 90,
      render: (id: number) => <Text style={{ color: 'rgba(250, 250, 250, 0.5)', fontFamily: 'monospace' }}>#{id}</Text>,
    },
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      render: (text: string) => <Text style={{ fontWeight: 600, color: '#fafafa' }}>{text || '-'}</Text>,
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      render: (text: string) => <Text style={{ color: 'rgba(250, 250, 250, 0.65)' }}>{text || '-'}</Text>,
    },
    {
      title: '当前余额',
      dataIndex: 'balance',
      key: 'balance',
      render: (val: number) => <BalanceText>{(val || 0).toFixed(4)}</BalanceText>,
    },
    {
      title: '累计充值',
      dataIndex: 'totalRecharged',
      key: 'totalRecharged',
      render: (val: number) => <Text style={{ color: '#22c55e' }}>{(val || 0).toFixed(4)}</Text>,
    },
    {
      title: '累计消费',
      dataIndex: 'totalConsumed',
      key: 'totalConsumed',
      render: (val: number) => <Text style={{ color: '#ef4444' }}>{(val || 0).toFixed(4)}</Text>,
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_: any, record: UserCredit) => (
        <Space size={4}>
          <ActionButton variant="primary" icon={<DollarOutlined />} onClick={() => handleOperation('recharge', record)}>充值</ActionButton>
          <ActionButton variant="success" icon={<GiftOutlined />} onClick={() => handleOperation('gift', record)}>赠送</ActionButton>
          <ActionButton variant="warning" icon={<EditOutlined />} onClick={() => handleOperation('adjust', record)}>调整</ActionButton>
        </Space>
      ),
    },
  ]

  const transactionColumns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      render: (id: number) => <Text style={{ color: 'rgba(250, 250, 250, 0.5)', fontFamily: 'monospace' }}>#{id}</Text>,
    },
    { title: '用户', dataIndex: 'username', key: 'username' },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const typeMap: Record<string, { status: string; text: string }> = {
          RECHARGE: { status: 'success', text: '充值' },
          CONSUME: { status: 'error', text: '消费' },
          GIFT: { status: 'processing', text: '赠送' },
          REFUND: { status: 'warning', text: '退款' },
          ADMIN_ADJUST: { status: 'default', text: '调整' },
        }
        const info = typeMap[type] || { status: 'default', text: type }
        return <StatusTag status={info.status} text={info.text} showIcon={false} />
      },
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      render: (val: number) => (
        <AmountText $positive={(val || 0) >= 0}>{(val || 0) >= 0 ? '+' : ''}{(val || 0).toFixed(4)}</AmountText>
      ),
    },
    { title: '模型', dataIndex: 'modelId', key: 'modelId' },
    { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt' },
  ]

  const modelUsageColumns = [
    { title: '模型', dataIndex: 'modelId', key: 'modelId' },
    { title: '调用次数', dataIndex: 'callCount', key: 'callCount' },
    { title: '输入字数', dataIndex: 'totalInputTokens', key: 'totalInputTokens' },
    { title: '输出字数', dataIndex: 'totalOutputTokens', key: 'totalOutputTokens' },
    {
      title: '总消费',
      dataIndex: 'totalCost',
      key: 'totalCost',
      render: (val: number) => <Text style={{ color: '#ef4444', fontWeight: 600 }}>{(val || 0).toFixed(4)}</Text>,
    },
  ]

  const packageColumns = [
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    { title: '名称', dataIndex: 'name' },
    { title: '价格(元)', dataIndex: 'price', render: (val: number) => `¥${val}` },
    { title: '包含字数', dataIndex: 'credits', render: (val: number) => `${((val || 0) / 10000).toFixed(1)}万字` },
    { title: '描述', dataIndex: 'description' },
    {
      title: '状态',
      dataIndex: 'isActive',
      render: (val: boolean) => (val ? <StatusTag status="success" text="启用" /> : <StatusTag status="default" text="停用" />),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: CreditPackage) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEditPackage(record)}>编辑</Button>
          <Popconfirm title="确认删除该套餐?" onConfirm={() => handleDeletePackage(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const tabItems = [
    {
      key: 'users',
      label: '用户字数点',
      children: (
        <DataTable
          columns={userColumns}
          dataSource={userCredits}
          rowKey="id"
          loading={loading}
          searchPlaceholder="搜索用户名/邮箱"
          searchValue={keyword}
          onSearchChange={setKeyword}
          onSearch={loadUserCredits}
          onRefresh={loadUserCredits}
          pagination={{
            ...pagination,
            onChange: (page, pageSize) => setPagination(prev => ({ ...prev, current: page, pageSize })),
          }}
        />
      ),
    },
    {
      key: 'transactions',
      label: '交易记录',
      children: (
        <DataTable
          columns={transactionColumns}
          dataSource={transactions}
          rowKey="id"
          loading={loading}
          showSearch={false}
          onRefresh={loadTransactions}
          toolbar={
            <Select
              placeholder="交易类型"
              allowClear
              style={{ width: 140 }}
              value={transactionType || undefined}
              onChange={setTransactionType}
            >
              <Select.Option value="RECHARGE">充值</Select.Option>
              <Select.Option value="CONSUME">消费</Select.Option>
              <Select.Option value="GIFT">赠送</Select.Option>
              <Select.Option value="REFUND">退款</Select.Option>
              <Select.Option value="ADMIN_ADJUST">调整</Select.Option>
            </Select>
          }
          pagination={{
            ...pagination,
            onChange: (page, pageSize) => setPagination(prev => ({ ...prev, current: page, pageSize })),
          }}
          scroll={{ x: 1200 }}
        />
      ),
    },
    {
      key: 'modelUsage',
      label: '模型使用统计',
      children: (
        <DataTable
          title="近30天模型使用统计"
          columns={modelUsageColumns}
          dataSource={modelUsage}
          rowKey="modelId"
          showSearch={false}
          pagination={false}
        />
      ),
    },
    {
      key: 'packages',
      label: '套餐与支付配置',
      children: (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <div style={{ background: 'rgba(255,255,255,0.04)', padding: 24, borderRadius: 12, border: '1px solid rgba(255,255,255,0.08)' }}>
            <div style={{ fontSize: 16, fontWeight: 600, color: '#fafafa', marginBottom: 16 }}>全局配置</div>
            <Form layout="inline" form={bonusForm}>
              <Form.Item label="新用户注册送字数" name="amount" rules={[{ required: true, message: '请输入字数' }]}>
                <InputNumber min={0} step={1000} style={{ width: 220 }} addonAfter="字" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" onClick={handleSaveBonus} loading={bonusLoading}>保存配置</Button>
              </Form.Item>
            </Form>
            <div style={{ marginTop: 10, color: 'rgba(250, 250, 250, 0.45)', fontSize: 12 }}>
              当前值: {registrationBonus}
            </div>
          </div>

          <div style={{ background: 'rgba(255,255,255,0.04)', padding: 24, borderRadius: 12, border: '1px solid rgba(255,255,255,0.08)' }}>
            <div style={{ fontSize: 16, fontWeight: 600, color: '#fafafa', marginBottom: 16 }}>易支付配置</div>
            <Form form={paymentForm} layout="vertical">
              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="enabled" label="启用在线充值" valuePropName="checked">
                    <Switch checkedChildren="启用" unCheckedChildren="关闭" />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="orderExpireMinutes" label="订单过期(分钟)" rules={[{ required: true, message: '请输入过期分钟数' }]}>
                    <InputNumber min={5} max={180} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="pid" label="商户PID">
                    <Input placeholder="例如: 10001" />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="gatewayUrl" label="支付网关地址">
                    <Input placeholder="https://xxx.com/submit.php" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="key" label="商户密钥">
                    <Input.Password placeholder="留空或保持掩码将沿用已保存密钥" />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="supportedTypes" label="可用支付方式" rules={[{ required: true, message: '请至少选择一种支付方式' }]}>
                    <Select mode="multiple" placeholder="选择用户端可用支付方式">
                      <Select.Option value="alipay">支付宝</Select.Option>
                      <Select.Option value="wxpay">微信支付</Select.Option>
                      <Select.Option value="qqpay">QQ钱包</Select.Option>
                      <Select.Option value="cashier">收银台(不指定支付方式)</Select.Option>
                    </Select>
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="notifyUrl" label="异步回调地址(可选)">
                    <Input placeholder="留空则由后端自动拼接" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="returnUrl" label="支付完成跳转地址(可选)">
                    <Input placeholder="例如: https://your-domain.com/settings" />
                  </Form.Item>
                </Col>
              </Row>

              <Divider style={{ borderColor: 'rgba(255,255,255,0.1)' }} />
              <Space>
                <Button loading={paymentVerifyLoading} onClick={handleVerifyPaymentConfig}>测试连接</Button>
                <Button type="primary" loading={paymentLoading} onClick={handleSavePaymentConfig}>保存支付配置</Button>
              </Space>
            </Form>
          </div>

          <DataTable
            title="字数包套餐"
            columns={packageColumns}
            dataSource={packages}
            rowKey="id"
            loading={loading}
            showSearch={false}
            pagination={false}
            toolbar={<Button type="primary" icon={<PlusOutlined />} onClick={handleAddPackage}>新增套餐</Button>}
          />
        </div>
      ),
    },
  ]

  return (
    <PageContainer
      title="字数点管理"
      description="管理用户余额、交易记录、套餐和易支付充值配置"
      icon={<ThunderboltOutlined />}
      breadcrumb={[{ title: '字数点管理' }]}
    >
      <Row gutter={[20, 20]} style={{ marginBottom: 24 }}>
        <Col xs={12} sm={8} lg={4}>
          <StatCard title="总余额" value={(statistics?.totalBalance || 0).toFixed(2)} icon={<WalletOutlined />} gradient={['#0ea5e9', '#06b6d4']} />
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <StatCard title="总消费" value={(statistics?.totalConsumed || 0).toFixed(2)} icon={<ThunderboltOutlined />} gradient={['#ef4444', '#dc2626']} />
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <StatCard title="总充值" value={(statistics?.totalRecharged || 0).toFixed(2)} icon={<DollarOutlined />} gradient={['#22c55e', '#16a34a']} />
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <StatCard title="今日消费" value={(statistics?.todayConsumed || 0).toFixed(2)} icon={<ThunderboltOutlined />} gradient={['#f97316', '#ea580c']} />
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <StatCard title="本月消费" value={(statistics?.monthConsumed || 0).toFixed(2)} icon={<ThunderboltOutlined />} gradient={['#a855f7', '#9333ea']} />
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <StatCard title="本月充值" value={(statistics?.monthRecharged || 0).toFixed(2)} icon={<DollarOutlined />} gradient={['#06b6d4', '#0891b2']} />
        </Col>
      </Row>

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />

      <Modal
        title={modalType === 'recharge' ? '充值字数点' : modalType === 'gift' ? '赠送字数点' : '调整余额'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="当前余额">
            <Input value={selectedUser?.balance?.toFixed(4)} disabled />
          </Form.Item>
          <Form.Item
            name="amount"
            label={modalType === 'adjust' ? '调整金额(正数增加, 负数减少)' : '金额'}
            rules={[{ required: true, message: '请输入金额' }]}
          >
            <InputNumber
              style={{ width: '100%' }}
              precision={4}
              min={modalType === 'adjust' ? undefined : 0}
              size="large"
            />
          </Form.Item>
          <Form.Item name="description" label="备注">
            <Input.TextArea rows={3} placeholder="请输入备注信息" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingPackage ? '编辑套餐' : '新增套餐'}
        open={packageModalVisible}
        onOk={handlePackageModalOk}
        onCancel={() => setPackageModalVisible(false)}
        okText="确定"
        cancelText="取消"
      >
        <Form form={packageForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="套餐名称" name="name" rules={[{ required: true, message: '请输入套餐名称' }]}>
            <Input placeholder="例如: 体验包" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="价格(元)" name="price" rules={[{ required: true, message: '请输入价格' }]}>
                <InputNumber min={0} precision={2} style={{ width: '100%' }} prefix="¥" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="包含字数" name="credits" rules={[{ required: true, message: '请输入字数' }]}>
                <InputNumber min={0} step={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={2} placeholder="套餐描述" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="排序" name="sortOrder">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="isActive" valuePropName="checked" label="是否启用">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </PageContainer>
  )
}

export default CreditsPage
