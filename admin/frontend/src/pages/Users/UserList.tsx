import { useEffect, useState } from 'react'
import { Button, Modal, Form, Input, Select, message, Space } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, UserOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import styled from '@emotion/styled'
import { adminUserService } from '@/services/adminUserService'
import { 
  PageContainer, 
  DataTable, 
  StatusTag, 
  UserAvatar, 
  ActionButton 
} from '@/components'

interface User {
  id: number
  username: string
  email: string
  role: string
  status: string
  createdAt: string
  novelCount: number
  aiTaskCount: number
}

// Filter out transient props (starting with $) from being passed to DOM
const shouldForwardProp = (prop: string) => !prop.startsWith('$')

const IdText = styled.span`
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  color: rgba(250, 250, 250, 0.45);
  font-size: 13px;
`

const CountText = styled('span', { shouldForwardProp })<{ $color?: string }>`
  color: ${props => props.$color};
  font-weight: 600;
  font-size: 14px;
`

const TimeText = styled.span`
  color: rgba(250, 250, 250, 0.45);
  font-size: 13px;
`

const UserList = () => {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [modalVisible, setModalVisible] = useState(false)
  const [editingUser, setEditingUser] = useState<User | null>(null)
  const [form] = Form.useForm()

  useEffect(() => {
    loadUsers()
  }, [])

  const loadUsers = async () => {
    setLoading(true)
    try {
      const response = await adminUserService.getUsers({ keyword: searchText })
      let userData: User[] = []
      if (Array.isArray(response)) {
        userData = response
      } else if (response && Array.isArray(response.data)) {
        userData = response.data
      } else if (response && Array.isArray((response as any).list)) {
        userData = (response as any).list
      } else if (response && Array.isArray((response as any).records)) {
        userData = (response as any).records
      }
      setUsers(userData)
    } catch (error) {
      console.error('加载用户列表失败:', error)
      message.error('加载用户列表失败')
      setUsers([])
    } finally {
      setLoading(false)
    }
  }

  const handleEdit = (user: User) => {
    setEditingUser(user)
    form.setFieldsValue(user)
    setModalVisible(true)
  }

  const handleDelete = async (userId: number) => {
    try {
      await adminUserService.deleteUser(userId)
      message.success('删除成功')
      loadUsers()
    } catch (error) {
      message.error('删除失败')
    }
  }

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields()
      if (editingUser) {
        await adminUserService.updateUser(editingUser.id, values)
        message.success('更新成功')
      } else {
        await adminUserService.createUser(values)
        message.success('创建成功')
      }
      setModalVisible(false)
      form.resetFields()
      setEditingUser(null)
      loadUsers()
    } catch (error) {
      message.error('操作失败')
    }
  }

  const columns: ColumnsType<User> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      render: (id: number) => <IdText>#{id}</IdText>,
    },
    {
      title: '用户',
      dataIndex: 'username',
      key: 'username',
      render: (text: string, record: User) => (
        <UserAvatar name={text} subtitle={record.email} />
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 100,
      render: (role: string) => <StatusTag status={role} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => <StatusTag status={status} />,
    },
    {
      title: '小说数',
      dataIndex: 'novelCount',
      key: 'novelCount',
      width: 100,
      align: 'center',
      render: (count: number) => <CountText $color="#0ea5e9">{count}</CountText>,
    },
    {
      title: 'AI任务数',
      dataIndex: 'aiTaskCount',
      key: 'aiTaskCount',
      width: 100,
      align: 'center',
      render: (count: number) => <CountText $color="#a855f7">{count}</CountText>,
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (text: string) => <TimeText>{text}</TimeText>,
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, record) => (
        <Space size={4}>
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
            confirmDescription="确定要删除该用户吗？此操作不可恢复。"
            onConfirm={() => handleDelete(record.id)}
          >
            删除
          </ActionButton>
        </Space>
      ),
    },
  ]

  return (
    <PageContainer
      title="用户管理"
      description="管理系统中的所有用户账户"
      icon={<UserOutlined />}
      breadcrumb={[{ title: '用户管理' }]}
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="large"
          onClick={() => {
            setEditingUser(null)
            form.resetFields()
            setModalVisible(true)
          }}
        >
          新增用户
        </Button>
      }
    >
      <DataTable
        columns={columns}
        dataSource={users}
        loading={loading}
        rowKey="id"
        searchPlaceholder="搜索用户名或邮箱..."
        searchValue={searchText}
        onSearchChange={setSearchText}
        onSearch={loadUsers}
        onRefresh={loadUsers}
      />

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
              <UserOutlined style={{ color: '#fff', fontSize: 18 }} />
            </div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 600, color: '#fafafa' }}>
                {editingUser ? '编辑用户' : '新增用户'}
              </div>
              <div style={{ fontSize: 12, color: 'rgba(250, 250, 250, 0.45)' }}>
                {editingUser ? '修改用户信息' : '创建新的用户账户'}
              </div>
            </div>
          </Space>
        }
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => {
          setModalVisible(false)
          form.resetFields()
          setEditingUser(null)
        }}
        width={560}
        okText="确定"
        cancelText="取消"
        styles={{
          body: { padding: '24px 28px' },
          header: { padding: '20px 28px', borderBottom: '1px solid rgba(255, 255, 255, 0.06)' },
          footer: { padding: '16px 28px', borderTop: '1px solid rgba(255, 255, 255, 0.06)' },
        }}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input size="large" placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '请输入有效的邮箱' },
            ]}
          >
            <Input size="large" placeholder="请输入邮箱" />
          </Form.Item>
          {!editingUser && (
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password size="large" placeholder="请输入密码" />
            </Form.Item>
          )}
          <Form.Item 
            name="role" 
            label="角色" 
            rules={[{ required: true, message: '请选择角色' }]}
          >
            <Select size="large" placeholder="请选择角色">
              <Select.Option value="USER">普通用户</Select.Option>
              <Select.Option value="ADMIN">管理员</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item 
            name="status" 
            label="状态" 
            rules={[{ required: true, message: '请选择状态' }]}
          >
            <Select size="large" placeholder="请选择状态">
              <Select.Option value="ACTIVE">启用</Select.Option>
              <Select.Option value="INACTIVE">禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}

export default UserList
