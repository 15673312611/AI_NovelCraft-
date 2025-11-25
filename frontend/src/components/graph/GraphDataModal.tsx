import React, { useState, useEffect } from 'react'
import { Modal, Tabs, Table, Tag, Spin, message, Empty, Form, Input, InputNumber, Select, Button } from 'antd'
import { NodeIndexOutlined, LinkOutlined, AimOutlined, GlobalOutlined, ThunderboltOutlined, UserOutlined, PlusOutlined } from '@ant-design/icons'
import api from '@/services/api'
import './GraphDataModal.css'

interface GraphDataModalProps {
  visible: boolean
  novelId: number | null
  novelTitle: string
  onClose: () => void
}

interface GraphData {
  characterStates: any[]
  relationshipStates: any[]
  openQuests: any[]
  totalCharacterStates: number
  totalRelationshipStates: number
  totalOpenQuests: number
}

const toNumber = (value: any): number | null => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  const n = typeof value === 'string' ? parseFloat(value) : NaN
  return Number.isFinite(n) ? n : null
}

const numText = (value: any, digits = 2): string => {
  const n = toNumber(value)
  return n === null ? '-' : n.toFixed(digits)
}

const toStringArray = (value: any): string[] => {
  if (Array.isArray(value)) return value.filter(Boolean).map(String)
  if (typeof value === 'string') return value.split(/[，,;；\s]+/).map(s => s.trim()).filter(Boolean)
  return []
}

const GraphDataModal: React.FC<GraphDataModalProps> = ({ visible, novelId, novelTitle, onClose }) => {
  const [loading, setLoading] = useState(false)
  const [graphData, setGraphData] = useState<GraphData | null>(null)
  const [addCharacterModalVisible, setAddCharacterModalVisible] = useState(false)
  const [addRelationshipModalVisible, setAddRelationshipModalVisible] = useState(false)
  const [addQuestModalVisible, setAddQuestModalVisible] = useState(false)
  const [characterForm] = Form.useForm()
  const [relationshipForm] = Form.useForm()
  const [questForm] = Form.useForm()
  const [editingCharacterKey, setEditingCharacterKey] = useState<string | null>(null)
  const [editingRelationshipKey, setEditingRelationshipKey] = useState<string | null>(null)
  const [editingQuestKey, setEditingQuestKey] = useState<string | null>(null)
  const [editCharacterForm] = Form.useForm()
  const [editRelationshipForm] = Form.useForm()
  const [editQuestForm] = Form.useForm()

  useEffect(() => {
    if (visible && novelId) {
      fetchGraphData()
    }
  }, [visible, novelId])

  const fetchGraphData = async () => {
    if (!novelId) return

    setLoading(true)
    try {
      const resp: any = await api.get(`/agentic/graph/data/${novelId}`)
      if (resp && resp.status === 'success') {
        setGraphData(resp.data)
      } else {
        message.error(resp?.error || '获取图谱数据失败')
      }
    } catch (error: any) {
      console.error('获取图谱数据失败:', error)
      message.error('获取图谱数据失败')
    } finally {
      setLoading(false)
    }
  }

  const characterStateColumns = [
    {
      title: '角色名',
      dataIndex: 'name',
      key: 'name',
      width: 120,
      render: (name: any, record: any) => {
        const key = `${record.name}_${record.chapter}`
        const isEditing = editingCharacterKey === key
        return isEditing ? (
          <Form.Item name="name" style={{ margin: 0 }}>
            <Input size="small" />
          </Form.Item>
        ) : (
          <Tag color="purple">{String(name)}</Tag>
        )
      },
    },
    {
      title: '位置',
      dataIndex: 'location',
      key: 'location',
      width: 150,
      render: (location: any, record: any) => {
        const key = `${record.name}_${record.chapter}`
        const isEditing = editingCharacterKey === key
        return isEditing ? (
          <Form.Item name="location" style={{ margin: 0 }}>
            <Input size="small" placeholder="位置" />
          </Form.Item>
        ) : (
          location || '-'
        )
      },
    },
    {
      title: '境界',
      dataIndex: 'realm',
      key: 'realm',
      width: 120,
      render: (realm: any, record: any) => {
        const key = `${record.name}_${record.chapter}`
        const isEditing = editingCharacterKey === key
        return isEditing ? (
          <Form.Item name="realm" style={{ margin: 0 }}>
            <Input size="small" placeholder="境界" />
          </Form.Item>
        ) : (
          realm || '-'
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'alive',
      key: 'alive',
      width: 100,
      render: (alive: any, record: any) => {
        const key = `${record.name}_${record.chapter}`
        const isEditing = editingCharacterKey === key
        return isEditing ? (
          <Form.Item name="alive" style={{ margin: 0 }}>
            <Select size="small" style={{ width: '100%' }}>
              <Select.Option value={true}>存活</Select.Option>
              <Select.Option value={false}>死亡</Select.Option>
            </Select>
          </Form.Item>
        ) : (
          alive ? <Tag color="green">存活</Tag> : <Tag color="red">死亡</Tag>
        )
      },
    },
    {
      title: '更新章节',
      dataIndex: 'chapter',
      key: 'chapter',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.chapter) || 0) - (toNumber(b.chapter) || 0),
      render: (chapter: any, record: any) => {
        const key = `${record.name}_${record.chapter}`
        const isEditing = editingCharacterKey === key
        return isEditing ? (
          <Form.Item name="chapter" style={{ margin: 0 }}>
            <InputNumber size="small" min={0} style={{ width: '100%' }} />
          </Form.Item>
        ) : (
          chapter ?? '-'
        )
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right' as const,
      render: (_: any, record: any) => {
        const key = `${record.name}_${record.chapter}`
        const isEditing = editingCharacterKey === key
        return isEditing ? (
          <span>
            <a onClick={handleSaveCharacterState} style={{ marginRight: 8, color: '#52c41a' }}>
              保存
            </a>
            <a onClick={handleCancelEditCharacter} style={{ color: '#999' }}>
              取消
            </a>
          </span>
        ) : (
          <span>
            <a onClick={() => handleEditCharacterState(record)} style={{ marginRight: 8 }}>
              编辑
            </a>
            <a onClick={() => handleDeleteCharacterState(record)} style={{ color: '#ff4d4f' }}>
              删除
            </a>
          </span>
        )
      },
    },
  ]

  const handleDeleteCharacterState = (record: any) => {
    if (!novelId) {
      message.warning('缺少小说ID，无法删除角色状态')
      return
    }

    Modal.confirm({
      title: '确认删除角色状态',
      content: `确定要删除角色「${record.name || '-'}」的状态吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const resp: any = await api.delete('/agentic/graph/character-state', {
            params: {
              novelId,
              characterName: record.name,
            },
          })
          if (resp && resp.status === 'success') {
            message.success('角色状态已删除')
            fetchGraphData()
          } else {
            message.error(resp?.message || '删除角色状态失败')
          }
        } catch (error: any) {
          console.error('删除角色状态失败:', error)
          message.error(error?.message || '删除角色状态失败')
        }
      },
    })
  }

  const handleEditCharacterState = (record: any) => {
    const key = `${record.name}_${record.chapter}`
    setEditingCharacterKey(key)
    editCharacterForm.setFieldsValue({
      name: record.name,
      location: record.location,
      realm: record.realm,
      alive: record.alive,
      chapter: record.chapter,
    })
  }

  const handleSaveCharacterState = async () => {
    if (!novelId) return
    try {
      const values = await editCharacterForm.validateFields()
      const payload = {
        novelId,
        name: values.name,
        location: values.location,
        realm: values.realm,
        alive: values.alive,
        chapter: values.chapter,
      }
      const resp: any = await api.put('/agentic/graph/character-state', payload)
      if (resp && resp.status === 'success') {
        message.success('角色状态已更新')
        setEditingCharacterKey(null)
        fetchGraphData()
      } else {
        message.error(resp?.message || '更新角色状态失败')
      }
    } catch (error: any) {
      console.error('更新角色状态失败:', error)
    }
  }

  const handleCancelEditCharacter = () => {
    setEditingCharacterKey(null)
  }

  const handleAddCharacterState = () => {
    if (!novelId) {
      message.warning('缺少小说ID，无法新增角色状态')
      return
    }
    characterForm.resetFields()
    setAddCharacterModalVisible(true)
  }

  const handleCharacterFormSubmit = async () => {
    try {
      const values = await characterForm.validateFields()
      const payload = {
        novelId,
        name: values.name,
        location: values.location,
        realm: values.realm,
        alive: values.alive ?? true,
        chapter: values.chapter ?? 0,
      }
      const resp: any = await api.put('/agentic/graph/character-state', payload)
      if (resp && resp.status === 'success') {
        message.success('角色状态已新增')
        setAddCharacterModalVisible(false)
        characterForm.resetFields()
        fetchGraphData()
      } else {
        message.error(resp?.message || '新增角色状态失败')
      }
    } catch (error: any) {
      console.error('新增角色状态失败:', error)
      if (error?.errorFields) {
        // 表单验证错误，不显示额外提示
        return
      }
      message.error(error?.message || '新增角色状态失败')
    }
  }

  const handleDeleteRelationship = (record: any) => {
    if (!novelId) {
      message.warning('缺少小说ID，无法删除关系')
      return
    }

    Modal.confirm({
      title: '确认删除关系',
      content: `确定要删除「${record.a || '-'}」与「${record.b || '-'}」之间的关系吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const resp: any = await api.delete('/agentic/graph/relationship-state', {
            params: {
              novelId,
              a: record.a,
              b: record.b,
            },
          })
          if (resp && resp.status === 'success') {
            message.success('关系已删除')
            fetchGraphData()
          } else {
            message.error(resp?.message || '删除关系失败')
          }
        } catch (error: any) {
          console.error('删除关系失败:', error)
          message.error(error?.message || '删除关系失败')
        }
      },
    })
  }

  const handleEditRelationshipType = (record: any) => {
    const key = `${record.a}_${record.b}_${record.chapter}`
    setEditingRelationshipKey(key)
    editRelationshipForm.setFieldsValue({
      a: record.a,
      b: record.b,
      type: record.type,
      strength: record.strength,
      chapter: record.chapter,
    })
  }

  const handleSaveRelationship = async () => {
    if (!novelId) return
    try {
      const values = await editRelationshipForm.validateFields()
      const payload = {
        novelId,
        a: values.a,
        b: values.b,
        type: values.type,
        strength: values.strength,
        chapter: values.chapter,
      }
      const resp: any = await api.put('/agentic/graph/relationship-state', payload)
      if (resp && resp.status === 'success') {
        message.success('关系已更新')
        setEditingRelationshipKey(null)
        fetchGraphData()
      } else {
        message.error(resp?.message || '更新关系失败')
      }
    } catch (error: any) {
      console.error('更新关系失败:', error)
    }
  }

  const handleCancelEditRelationship = () => {
    setEditingRelationshipKey(null)
  }

  const handleAddRelationshipState = () => {
    if (!novelId) {
      message.warning('缺少小说ID，无法新增关系')
      return
    }
    relationshipForm.resetFields()
    setAddRelationshipModalVisible(true)
  }

  const handleRelationshipFormSubmit = async () => {
    try {
      const values = await relationshipForm.validateFields()
      const payload = {
        novelId,
        a: values.a,
        b: values.b,
        type: values.type,
        strength: values.strength ?? 0.5,
        chapter: values.chapter ?? 0,
      }
      const resp: any = await api.put('/agentic/graph/relationship-state', payload)
      if (resp && resp.status === 'success') {
        message.success('关系已新增')
        setAddRelationshipModalVisible(false)
        relationshipForm.resetFields()
        fetchGraphData()
      } else {
        message.error(resp?.message || '新增关系失败')
      }
    } catch (error: any) {
      console.error('新增关系失败:', error)
      if (error?.errorFields) {
        return
      }
      message.error(error?.message || '新增关系失败')
    }
  }

  const relationshipStateColumns = [
    {
      title: '角色A',
      dataIndex: 'a',
      key: 'a',
      width: 120,
      render: (name: any, record: any) => {
        const key = `${record.a}_${record.b}_${record.chapter}`
        const isEditing = editingRelationshipKey === key
        return isEditing ? (
          <Form.Item name="a" style={{ margin: 0 }}>
            <Input size="small" />
          </Form.Item>
        ) : (
          <Tag color="blue">{String(name)}</Tag>
        )
      },
    },
    {
      title: '角色B',
      dataIndex: 'b',
      key: 'b',
      width: 120,
      render: (name: any, record: any) => {
        const key = `${record.a}_${record.b}_${record.chapter}`
        const isEditing = editingRelationshipKey === key
        return isEditing ? (
          <Form.Item name="b" style={{ margin: 0 }}>
            <Input size="small" />
          </Form.Item>
        ) : (
          <Tag color="green">{String(name)}</Tag>
        )
      },
    },
    {
      title: '关系类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: any, record: any) => {
        const key = `${record.a}_${record.b}_${record.chapter}`
        const isEditing = editingRelationshipKey === key
        return isEditing ? (
          <Form.Item name="type" style={{ margin: 0 }}>
            <Input size="small" placeholder="关系类型" />
          </Form.Item>
        ) : (
          type || '-'
        )
      },
    },
    {
      title: '强度',
      dataIndex: 'strength',
      key: 'strength',
      width: 100,
      render: (strength: any, record: any) => {
        const key = `${record.a}_${record.b}_${record.chapter}`
        const isEditing = editingRelationshipKey === key
        return isEditing ? (
          <Form.Item name="strength" style={{ margin: 0 }}>
            <InputNumber size="small" min={0} max={1} step={0.1} style={{ width: '100%' }} />
          </Form.Item>
        ) : (
          numText(strength)
        )
      },
    },
    {
      title: '更新章节',
      dataIndex: 'chapter',
      key: 'chapter',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.chapter) || 0) - (toNumber(b.chapter) || 0),
      render: (chapter: any, record: any) => {
        const key = `${record.a}_${record.b}_${record.chapter}`
        const isEditing = editingRelationshipKey === key
        return isEditing ? (
          <Form.Item name="chapter" style={{ margin: 0 }}>
            <InputNumber size="small" min={0} style={{ width: '100%' }} />
          </Form.Item>
        ) : (
          chapter ?? '-'
        )
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right' as const,
      render: (_: any, record: any) => {
        const key = `${record.a}_${record.b}_${record.chapter}`
        const isEditing = editingRelationshipKey === key
        return isEditing ? (
          <span>
            <a onClick={handleSaveRelationship} style={{ marginRight: 8, color: '#52c41a' }}>
              保存
            </a>
            <a onClick={handleCancelEditRelationship} style={{ color: '#999' }}>
              取消
            </a>
          </span>
        ) : (
          <span>
            <a onClick={() => handleEditRelationshipType(record)} style={{ marginRight: 8 }}>
              编辑
            </a>
            <a onClick={() => handleDeleteRelationship(record)} style={{ color: '#ff4d4f' }}>
              删除
            </a>
          </span>
        )
      },
    },
  ]

  const openQuestColumns = [
    {
      title: '任务ID',
      dataIndex: 'id',
      key: 'id',
      width: 150,
      render: (id: any, record: any) => {
        const isEditing = editingQuestKey === record.id
        return isEditing ? (
          <Form.Item name="id" style={{ margin: 0 }}>
            <Input size="small" disabled />
          </Form.Item>
        ) : (
          <Tag color="orange">{String(id)}</Tag>
        )
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (description: any, record: any) => {
        const isEditing = editingQuestKey === record.id
        return isEditing ? (
          <Form.Item name="description" style={{ margin: 0 }}>
            <Input.TextArea size="small" rows={1} placeholder="任务描述" />
          </Form.Item>
        ) : (
          description || '-'
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: any, record: any) => {
        const isEditing = editingQuestKey === record.id
        if (isEditing) {
          return (
            <Form.Item name="status" style={{ margin: 0 }}>
              <Select size="small" style={{ width: '100%' }}>
                <Select.Option value="OPEN">OPEN</Select.Option>
                <Select.Option value="RESOLVED">RESOLVED</Select.Option>
              </Select>
            </Form.Item>
          )
        }
        const color = status === 'OPEN' ? 'green' : status === 'RESOLVED' ? 'blue' : 'default'
        return <Tag color={color}>{String(status)}</Tag>
      },
    },
    {
      title: '引入章节',
      dataIndex: 'introduced',
      key: 'introduced',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.introduced) || 0) - (toNumber(b.introduced) || 0),
      render: (introduced: any, record: any) => {
        const isEditing = editingQuestKey === record.id
        return isEditing ? (
          <Form.Item name="introduced" style={{ margin: 0 }}>
            <InputNumber size="small" min={0} style={{ width: '100%' }} />
          </Form.Item>
        ) : (
          introduced ?? '-'
        )
      },
    },
    {
      title: '截止章节',
      dataIndex: 'due',
      key: 'due',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.due) || 0) - (toNumber(b.due) || 0),
      render: (due: any, record: any) => {
        const isEditing = editingQuestKey === record.id
        return isEditing ? (
          <Form.Item name="due" style={{ margin: 0 }}>
            <InputNumber size="small" min={0} style={{ width: '100%' }} />
          </Form.Item>
        ) : (
          due ?? '-'
        )
      },
    },
    {
      title: '最后更新',
      dataIndex: 'lastUpdated',
      key: 'lastUpdated',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.lastUpdated) || 0) - (toNumber(b.lastUpdated) || 0),
      render: (lastUpdated: any, record: any) => {
        const isEditing = editingQuestKey === record.id
        return isEditing ? (
          <Form.Item name="lastUpdated" style={{ margin: 0 }}>
            <InputNumber size="small" min={0} style={{ width: '100%' }} />
          </Form.Item>
        ) : (
          lastUpdated ?? '-'
        )
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right' as const,
      render: (_: any, record: any) => {
        const isEditing = editingQuestKey === record.id
        return isEditing ? (
          <span>
            <a onClick={handleSaveQuest} style={{ marginRight: 8, color: '#52c41a' }}>
              保存
            </a>
            <a onClick={handleCancelEditQuest} style={{ color: '#999' }}>
              取消
            </a>
          </span>
        ) : (
          <span>
            <a onClick={() => handleEditOpenQuest(record)} style={{ marginRight: 8 }}>
              编辑
            </a>
            <a onClick={() => handleDeleteOpenQuest(record)} style={{ color: '#ff4d4f' }}>
              删除
            </a>
          </span>
        )
      },
    },
  ]

  const handleDeleteOpenQuest = (record: any) => {
    if (!novelId) {
      message.warning('缺少小说ID，无法删除任务')
      return
    }

    Modal.confirm({
      title: '确认删除任务',
      content: `确定要删除任务「${record.id || '-'}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const resp: any = await api.delete('/agentic/graph/open-quest', {
            params: {
              novelId,
              id: record.id,
            },
          })
          if (resp && resp.status === 'success') {
            message.success('任务已删除')
            fetchGraphData()
          } else {
            message.error(resp?.message || '删除任务失败')
          }
        } catch (error: any) {
          console.error('删除任务失败:', error)
          message.error(error?.message || '删除任务失败')
        }
      },
    })
  }

  const handleEditOpenQuest = (record: any) => {
    setEditingQuestKey(record.id)
    editQuestForm.setFieldsValue({
      id: record.id,
      description: record.description,
      status: record.status,
      introduced: record.introduced,
      due: record.due,
      lastUpdated: record.lastUpdated,
    })
  }

  const handleSaveQuest = async () => {
    if (!novelId) return
    try {
      const values = await editQuestForm.validateFields()
      const payload = {
        novelId,
        id: values.id,
        description: values.description,
        status: values.status,
        introduced: values.introduced,
        due: values.due,
        lastUpdated: values.lastUpdated,
      }
      const resp: any = await api.put('/agentic/graph/open-quest', payload)
      if (resp && resp.status === 'success') {
        message.success('任务已更新')
        setEditingQuestKey(null)
        fetchGraphData()
      } else {
        message.error(resp?.message || '更新任务失败')
      }
    } catch (error: any) {
      console.error('更新任务失败:', error)
    }
  }

  const handleCancelEditQuest = () => {
    setEditingQuestKey(null)
  }

  const handleAddOpenQuest = () => {
    if (!novelId) {
      message.warning('缺少小说ID，无法新增任务')
      return
    }
    questForm.resetFields()
    setAddQuestModalVisible(true)
  }

  const handleQuestFormSubmit = async () => {
    try {
      const values = await questForm.validateFields()
      const payload = {
        novelId,
        id: values.id,
        description: values.description,
        status: values.status ?? 'OPEN',
        introduced: values.introduced,
        due: values.due,
        lastUpdated: values.due ?? values.introduced ?? 0,
      }
      const resp: any = await api.put('/agentic/graph/open-quest', payload)
      if (resp && resp.status === 'success') {
        message.success('任务已新增')
        setAddQuestModalVisible(false)
        questForm.resetFields()
        fetchGraphData()
      } else {
        message.error(resp?.message || '新增任务失败')
      }
    } catch (error: any) {
      console.error('新增任务失败:', error)
      if (error?.errorFields) {
        return
      }
      message.error(error?.message || '新增任务失败')
    }
  }

  const tabItems = [
    {
      key: 'characterStates',
      label: (
        <span>
          <UserOutlined /> 角色状态 ({graphData?.totalCharacterStates || 0})
        </span>
      ),
      children: (
        <div>
          <div style={{ marginBottom: 8, textAlign: 'right' }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddCharacterState}>
              新增角色状态
            </Button>
          </div>
          {graphData?.characterStates && graphData.characterStates.length > 0 ? (
            <Form form={editCharacterForm} component={false}>
              <Table
                columns={characterStateColumns}
                dataSource={graphData.characterStates}
                rowKey={(record, index) => `${record.name}_${index}`}
                pagination={{ pageSize: 10 }}
                size="small"
                bordered
              />
            </Form>
          ) : (
            <Empty description="暂无角色状态数据" />
          )}
        </div>
      ),
    },
    {
      key: 'relationshipStates',
      label: (
        <span>
          <LinkOutlined /> 关系状态 ({graphData?.totalRelationshipStates || 0})
        </span>
      ),
      children: (
        <div>
          <div style={{ marginBottom: 8, textAlign: 'right' }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddRelationshipState}>
              新增关系
            </Button>
          </div>
          {graphData?.relationshipStates && graphData.relationshipStates.length > 0 ? (
            <Form form={editRelationshipForm} component={false}>
              <Table
                columns={relationshipStateColumns}
                dataSource={graphData.relationshipStates}
                rowKey={(record) => `${record.a || ''}_${record.b || ''}_${record.chapter || ''}`}
                pagination={{ pageSize: 10 }}
                size="small"
                bordered
              />
            </Form>
          ) : (
            <Empty description="暂无关系状态数据" />
          )}
        </div>
      ),
    },
    {
      key: 'openQuests',
      label: (
        <span>
          <AimOutlined /> 未决任务 ({graphData?.totalOpenQuests || 0})
        </span>
      ),
      children: (
        <div>
          <div style={{ marginBottom: 8, textAlign: 'right' }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddOpenQuest}>
              新增任务
            </Button>
          </div>
          {graphData?.openQuests && graphData.openQuests.length > 0 ? (
            <Form form={editQuestForm} component={false}>
              <Table
                columns={openQuestColumns}
                dataSource={graphData.openQuests}
                rowKey={(record) => record.id || Math.random()}
                pagination={{ pageSize: 10 }}
                size="small"
                bordered
              />
            </Form>
          ) : (
            <Empty description="暂无未决任务数据" />
          )}
        </div>
      ),
    },
  ]

  return (
    <>
      <Modal
        title={`图谱数据 - ${novelTitle}`}
        open={visible}
        onCancel={onClose}
        footer={null}
        width={1200}
        className="graph-data-modal"
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <Spin size="large" />
            <p style={{ marginTop: 16, color: '#999' }}>加载图谱数据中...</p>
          </div>
        ) : (
          <Tabs items={tabItems} defaultActiveKey="characterStates" />
        )}
      </Modal>

      {/* 新增角色状态弹窗 */}
      <Modal
        title="新增角色状态"
        open={addCharacterModalVisible}
        onCancel={() => setAddCharacterModalVisible(false)}
        onOk={handleCharacterFormSubmit}
        width={500}
      >
        <Form form={characterForm} layout="vertical">
          <Form.Item
            label="角色名"
            name="name"
            rules={[{ required: true, message: '请输入角色名' }]}
          >
            <Input placeholder="请输入角色名" />
          </Form.Item>
          <Form.Item label="位置" name="location">
            <Input placeholder="请输入位置（可选）" />
          </Form.Item>
          <Form.Item label="境界" name="realm">
            <Input placeholder="请输入境界（可选）" />
          </Form.Item>
          <Form.Item label="是否存活" name="alive" initialValue={true}>
            <Select>
              <Select.Option value={true}>存活</Select.Option>
              <Select.Option value={false}>死亡</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="更新章节" name="chapter" initialValue={0}>
            <InputNumber min={0} style={{ width: '100%' }} placeholder="请输入章节号" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 新增关系状态弹窗 */}
      <Modal
        title="新增关系状态"
        open={addRelationshipModalVisible}
        onCancel={() => setAddRelationshipModalVisible(false)}
        onOk={handleRelationshipFormSubmit}
        width={500}
      >
        <Form form={relationshipForm} layout="vertical">
          <Form.Item
            label="角色A"
            name="a"
            rules={[{ required: true, message: '请输入角色A名称' }]}
          >
            <Input placeholder="请输入角色A名称" />
          </Form.Item>
          <Form.Item
            label="角色B"
            name="b"
            rules={[{ required: true, message: '请输入角色B名称' }]}
          >
            <Input placeholder="请输入角色B名称" />
          </Form.Item>
          <Form.Item label="关系类型" name="type">
            <Input placeholder="例如：好友、敌对、暧昧..." />
          </Form.Item>
          <Form.Item label="关系强度" name="strength" initialValue={0.5}>
            <InputNumber min={0} max={1} step={0.1} style={{ width: '100%' }} placeholder="0-1之间" />
          </Form.Item>
          <Form.Item label="更新章节" name="chapter" initialValue={0}>
            <InputNumber min={0} style={{ width: '100%' }} placeholder="请输入章节号" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 新增任务弹窗 */}
      <Modal
        title="新增未决任务"
        open={addQuestModalVisible}
        onCancel={() => setAddQuestModalVisible(false)}
        onOk={handleQuestFormSubmit}
        width={500}
      >
        <Form form={questForm} layout="vertical">
          <Form.Item
            label="任务ID"
            name="id"
            rules={[{ required: true, message: '请输入任务ID' }]}
          >
            <Input placeholder="例如：quest_001" />
          </Form.Item>
          <Form.Item label="任务描述" name="description">
            <Input.TextArea rows={3} placeholder="请输入任务描述（可选）" />
          </Form.Item>
          <Form.Item label="任务状态" name="status" initialValue="OPEN">
            <Select>
              <Select.Option value="OPEN">OPEN</Select.Option>
              <Select.Option value="RESOLVED">RESOLVED</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="引入章节" name="introduced">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="请输入引入章节号" />
          </Form.Item>
          <Form.Item label="截止章节" name="due">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="请输入截止章节号" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

export default GraphDataModal

