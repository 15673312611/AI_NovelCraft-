import React, { useState, useEffect } from 'react';
import { Card, Button, message, Modal, Form, Input, Select, Typography, Space, List, Tag, Statistic, Row, Col } from 'antd';
import { PlusOutlined, EyeOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { WorldViewBuilderComponent } from '../components/worldview/WorldViewBuilder';
import { worldViewBuilderService, WorldViewBuilder, BuilderStatistics } from '../services/worldViewBuilderService';
import { novelService } from '../services/novelService';
import './WorldViewBuilderPage.css';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;
const { Option } = Select;

export const WorldViewBuilderPage: React.FC = () => {
  const navigate = useNavigate();
  const { builderId } = useParams<{ builderId: string }>();
  const [builders, setBuilders] = useState<WorldViewBuilder[]>([]);
  const [statistics, setStatistics] = useState<BuilderStatistics | null>(null);
  const [novels, setNovels] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [buildersData, statisticsData, novelsData] = await Promise.all([
        worldViewBuilderService.getWorldViewBuilders(0, 100),
        worldViewBuilderService.getBuilderStatistics(),
        novelService.getNovels(0, 100)
      ]);
      
      setBuilders(buildersData.content);
      setStatistics(statisticsData);
      setNovels(novelsData);
    } catch (error) {
      message.error('加载数据失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateBuilder = async (values: any) => {
    setLoading(true);
    try {
      await worldViewBuilderService.createWorldViewBuilder(values);
      message.success('世界观构建器创建成功');
      setCreateModalVisible(false);
      form.resetFields();
      loadData();
    } catch (error) {
      message.error('创建失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteBuilder = async (id: number) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个世界观构建器吗？删除后无法恢复。',
      onOk: async () => {
        try {
          await worldViewBuilderService.deleteWorldViewBuilder(id);
          message.success('删除成功');
          loadData();
        } catch (error) {
          message.error('删除失败');
        }
      }
    });
  };

  const handleBuilderComplete = () => {
    message.success('世界观构建完成！');
    loadData();
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'INITIALIZED': return 'blue';
      case 'IN_PROGRESS': return 'orange';
      case 'COMPLETED': return 'green';
      case 'PAUSED': return 'yellow';
      case 'CANCELLED': return 'red';
      default: return 'default';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'INITIALIZED': return '已初始化';
      case 'IN_PROGRESS': return '进行中';
      case 'COMPLETED': return '已完成';
      case 'PAUSED': return '已暂停';
      case 'CANCELLED': return '已取消';
      default: return status;
    }
  };

  // 如果指定了构建器ID，显示构建器界面
  if (builderId) {
    return (
      <div className="world-view-builder-page">
        <div className="page-header">
          <Button 
            icon={<ReloadOutlined />} 
            onClick={() => navigate('/world-view-builder')}
            style={{ marginBottom: 16 }}
          >
            返回列表
          </Button>
        </div>
        <WorldViewBuilderComponent 
          builderId={parseInt(builderId)} 
          onComplete={handleBuilderComplete}
        />
      </div>
    );
  }

  return (
    <div className="world-view-builder-page">
      <div className="page-header">
        <Title level={2}>世界观构建器</Title>
        <Paragraph type="secondary">
          通过引导式问答和AI辅助，系统化地构建您小说的世界观
        </Paragraph>
      </div>

      {/* 统计信息 */}
      {statistics && (
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={8}>
            <Card>
              <Statistic
                title="总构建器"
                value={statistics.totalBuilders}
                valueStyle={{ color: '#3f8600' }}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic
                title="进行中"
                value={statistics.inProgressBuilders}
                valueStyle={{ color: '#cf1322' }}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic
                title="已完成"
                value={statistics.completedBuilders}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
        </Row>
      )}

      {/* 操作按钮 */}
      <div className="page-actions">
        <Space>
          <Button 
            type="primary" 
            icon={<PlusOutlined />}
            onClick={() => setCreateModalVisible(true)}
          >
            创建世界观构建器
          </Button>
          <Button 
            icon={<ReloadOutlined />}
            onClick={loadData}
            loading={loading}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 构建器列表 */}
      <Card title="世界观构建器列表" loading={loading}>
        {builders.length === 0 ? (
          <div className="empty-state">
            <Text type="secondary">暂无世界观构建器，点击上方按钮创建</Text>
          </div>
        ) : (
          <List
            dataSource={builders}
            renderItem={(builder) => (
              <List.Item
                actions={[
                  <Button
                    key="view"
                    type="link"
                    icon={<EyeOutlined />}
                    onClick={() => navigate(`/world-view-builder/${builder.id}`)}
                  >
                    查看
                  </Button>,
                  <Button
                    key="delete"
                    type="link"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => handleDeleteBuilder(builder.id)}
                  >
                    删除
                  </Button>
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <Text strong>{builder.name}</Text>
                      <Tag color={getStatusColor(builder.status)}>
                        {getStatusText(builder.status)}
                      </Tag>
                    </Space>
                  }
                  description={
                    <Space direction="vertical" size="small">
                      <Text type="secondary">{builder.description}</Text>
                      <Text type="secondary">
                        关联小说：{builder.novelTitle || '未知'}
                      </Text>
                      <Text type="secondary">
                        创建时间：{new Date(builder.createdAt).toLocaleString()}
                      </Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Card>

      {/* 创建构建器模态框 */}
      <Modal
        title="创建世界观构建器"
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        footer={null}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateBuilder}
        >
          <Form.Item
            name="name"
            label="构建器名称"
            rules={[{ required: true, message: '请输入构建器名称' }]}
          >
            <Input placeholder="请输入构建器名称" />
          </Form.Item>

          <Form.Item
            name="description"
            label="描述"
          >
            <TextArea 
              rows={3} 
              placeholder="请输入构建器描述（可选）" 
            />
          </Form.Item>

          <Form.Item
            name="novelId"
            label="关联小说"
            rules={[{ required: true, message: '请选择关联小说' }]}
          >
            <Select placeholder="请选择关联小说">
              {novels.map(novel => (
                <Option key={novel.id} value={novel.id}>
                  {novel.title}
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={loading}>
                创建
              </Button>
              <Button onClick={() => setCreateModalVisible(false)}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}; 