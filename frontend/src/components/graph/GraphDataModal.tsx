import React, { useState, useEffect } from 'react'
import { Modal, Tabs, Table, Tag, Spin, message, Empty } from 'antd'
import { NodeIndexOutlined, LinkOutlined, AimOutlined, GlobalOutlined, ThunderboltOutlined, UserOutlined } from '@ant-design/icons'
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

  useEffect(() => {
    if (visible && novelId) {
      fetchGraphData()
    }
  }, [visible, novelId])

  const fetchGraphData = async () => {
    if (!novelId) return

    setLoading(true)
    try {
      const resp = await api.get(`/agentic/graph/data/${novelId}`)
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
      render: (name: any) => name ? <Tag color="purple">{String(name)}</Tag> : '-',
    },
    {
      title: '位置',
      dataIndex: 'location',
      key: 'location',
      width: 150,
    },
    {
      title: '境界',
      dataIndex: 'realm',
      key: 'realm',
      width: 120,
    },
    {
      title: '状态',
      dataIndex: 'alive',
      key: 'alive',
      width: 80,
      render: (alive: any) => alive ? <Tag color="green">存活</Tag> : <Tag color="red">死亡</Tag>,
    },
    {
      title: '物品',
      dataIndex: 'inventory',
      key: 'inventory',
      ellipsis: true,
      render: (inventory: any) => {
        if (!inventory || !Array.isArray(inventory) || inventory.length === 0) return '-'
        return inventory.map((item: any, idx: number) => (
          <Tag key={idx} color="blue">{String(item)}</Tag>
        ))
      },
    },
    {
      title: '更新章节',
      dataIndex: 'chapter',
      key: 'chapter',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.chapter) || 0) - (toNumber(b.chapter) || 0),
    },
  ]

  const relationshipStateColumns = [
    {
      title: '角色A',
      dataIndex: 'a',
      key: 'a',
      width: 120,
      render: (name: any) => name ? <Tag color="blue">{String(name)}</Tag> : '-',
    },
    {
      title: '角色B',
      dataIndex: 'b',
      key: 'b',
      width: 120,
      render: (name: any) => name ? <Tag color="green">{String(name)}</Tag> : '-',
    },
    {
      title: '关系类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
    },
    {
      title: '强度',
      dataIndex: 'strength',
      key: 'strength',
      width: 100,
      render: (strength: any) => numText(strength),
    },
    {
      title: '更新章节',
      dataIndex: 'chapter',
      key: 'chapter',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.chapter) || 0) - (toNumber(b.chapter) || 0),
    },
  ]

  const openQuestColumns = [
    {
      title: '任务ID',
      dataIndex: 'id',
      key: 'id',
      width: 150,
      render: (id: any) => id ? <Tag color="orange">{String(id)}</Tag> : '-',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: any) => {
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
    },
    {
      title: '截止章节',
      dataIndex: 'due',
      key: 'due',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.due) || 0) - (toNumber(b.due) || 0),
    },
    {
      title: '最后更新',
      dataIndex: 'lastUpdated',
      key: 'lastUpdated',
      width: 100,
      sorter: (a: any, b: any) => (toNumber(a.lastUpdated) || 0) - (toNumber(b.lastUpdated) || 0),
    },
  ]

  const tabItems = [
    {
      key: 'characterStates',
      label: (
        <span>
          <UserOutlined /> 角色状态 ({graphData?.totalCharacterStates || 0})
        </span>
      ),
      children: graphData?.characterStates && graphData.characterStates.length > 0 ? (
        <Table
          columns={characterStateColumns}
          dataSource={graphData.characterStates}
          rowKey={(record, index) => `${record.name}_${index}`}
          pagination={{ pageSize: 10 }}
          size="small"
        />
      ) : (
        <Empty description="暂无角色状态数据" />
      ),
    },
    {
      key: 'relationshipStates',
      label: (
        <span>
          <LinkOutlined /> 关系状态 ({graphData?.totalRelationshipStates || 0})
        </span>
      ),
      children: graphData?.relationshipStates && graphData.relationshipStates.length > 0 ? (
        <Table
          columns={relationshipStateColumns}
          dataSource={graphData.relationshipStates}
          rowKey={(record, index) => index}
          pagination={{ pageSize: 10 }}
          size="small"
        />
      ) : (
        <Empty description="暂无关系状态数据" />
      ),
    },
    {
      key: 'openQuests',
      label: (
        <span>
          <AimOutlined /> 未决任务 ({graphData?.totalOpenQuests || 0})
        </span>
      ),
      children: graphData?.openQuests && graphData.openQuests.length > 0 ? (
        <Table
          columns={openQuestColumns}
          dataSource={graphData.openQuests}
          rowKey={(record) => record.id || Math.random()}
          pagination={{ pageSize: 10 }}
          size="small"
        />
      ) : (
        <Empty description="暂无未决任务数据" />
      ),
    },
  ]

  return (
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
  )
}

export default GraphDataModal

