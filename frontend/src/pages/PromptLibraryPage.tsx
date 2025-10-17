import React, { useEffect, useState } from 'react';
import { 
  Tabs, Button, Modal, Form, Input, Select, message, 
  Empty, Spin, Card
} from 'antd';
import { 
  PlusOutlined, StarOutlined, StarFilled, 
  FileTextOutlined, HeartOutlined, GlobalOutlined 
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import api from '@/services/api';
import './PromptLibraryPage.css';

const { TextArea } = Input;
const { TabPane } = Tabs;

interface PromptTemplate {
  id: number;
  name: string;
  content: string;
  type: 'official' | 'custom';
  category: string;
  description: string;
  usageCount: number;
  isFavorited?: boolean;
}

const PromptLibraryPage: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('public');
  const [publicTemplates, setPublicTemplates] = useState<PromptTemplate[]>([]);
  const [favoriteTemplates, setFavoriteTemplates] = useState<PromptTemplate[]>([]);
  const [customTemplates, setCustomTemplates] = useState<PromptTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [viewModalVisible, setViewModalVisible] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<PromptTemplate | null>(null);
  const [form] = Form.useForm();
  const [novelSelectVisible, setNovelSelectVisible] = useState(false);
  const [availableNovels, setAvailableNovels] = useState<any[]>([]);
  const [selectedNovelId, setSelectedNovelId] = useState<number | null>(null);

  useEffect(() => {
    loadTemplates();
  }, [activeTab]);

  const loadTemplates = async () => {
    setLoading(true);
    try {
      if (activeTab === 'public') {
        const response: any = await api.get('/prompt-templates/public');
        setPublicTemplates(response?.data || []);
      } else if (activeTab === 'favorites') {
        const response: any = await api.get('/prompt-templates/favorites');
        setFavoriteTemplates(response?.data || []);
      } else if (activeTab === 'custom') {
        const response: any = await api.get('/prompt-templates/custom');
        setCustomTemplates(response?.data || []);
      }
    } catch (error) {
      console.error('加载模板失败:', error);
      message.error('加载模板失败');
    } finally {
      setLoading(false);
    }
  };

  const handleFavorite = async (templateId: number, isFavorited: boolean) => {
    try {
      if (isFavorited) {
        await api.delete(`/prompt-templates/${templateId}/favorite`);
        message.success('取消收藏成功');
      } else {
        await api.post(`/prompt-templates/${templateId}/favorite`);
        message.success('收藏成功');
      }
      
      // 更新模板的收藏状态
      const updateTemplateStatus = (templates: PromptTemplate[]) => 
        templates.map(t => 
          t.id === templateId ? { ...t, isFavorited: !isFavorited } : t
        );
      
      // 更新所有列表中的状态
      setPublicTemplates(prev => updateTemplateStatus(prev));
      setFavoriteTemplates(prev => updateTemplateStatus(prev));
      setCustomTemplates(prev => updateTemplateStatus(prev));
      
      // 如果当前打开了详情弹窗，也要更新
      if (selectedTemplate && selectedTemplate.id === templateId) {
        setSelectedTemplate({ ...selectedTemplate, isFavorited: !isFavorited });
      }
      
      // 如果是在收藏tab且取消了收藏，重新加载列表
      if (activeTab === 'favorites' && isFavorited) {
        loadTemplates();
      }
    } catch (error) {
      console.error('收藏操作失败:', error);
      message.error('操作失败');
    }
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await api.post('/prompt-templates', values);
      message.success('创建成功');
      setCreateModalVisible(false);
      form.resetFields();
      if (activeTab === 'custom') {
        loadTemplates();
      } else {
        setActiveTab('custom');
      }
    } catch (error) {
      console.error('创建失败:', error);
      message.error('创建失败');
    }
  };

  const handleDelete = (templateId: number) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个提示词模板吗？',
      onOk: async () => {
        try {
          await api.delete(`/prompt-templates/${templateId}`);
          message.success('删除成功');
          loadTemplates();
        } catch (error) {
          console.error('删除失败:', error);
          message.error('删除失败');
        }
      }
    });
  };

  const handleView = (template: PromptTemplate) => {
    setSelectedTemplate(template);
    setViewModalVisible(true);
  };

  const handleUseTemplate = async (template: PromptTemplate) => {
    setSelectedTemplate(template);
    setNovelSelectVisible(true);
    
    // 加载可用于写作的书籍（由后端筛选）
    try {
      const response: any = await api.get('/novels/writable');
      const writableNovels = response || [];
      
      console.log('✅ 可写作书籍数量:', writableNovels.length);
      console.log('✅ 可写作书籍:', writableNovels);
      
      setAvailableNovels(writableNovels);
      
      if (writableNovels.length === 0) {
        message.warning('暂无符合条件的书籍，请先创建书籍并生成大纲、卷大纲');
      }
    } catch (error) {
      console.error('加载书籍列表失败:', error);
      message.error('加载书籍列表失败');
    }
  };

  const handleBindTemplate = async () => {
    if (!selectedNovelId || !selectedTemplate) {
      message.warning('请选择书籍');
      return;
    }
    
    try {
      // 获取该书籍的卷列表
      const volumes: any = await api.get(`/volumes/novel/${selectedNovelId}`);
      
      if (!volumes || volumes.length === 0) {
        message.warning('该书籍暂无可用的卷，请先生成卷规划');
        return;
      }
      
      // 找到第一卷（按volumeNumber排序）
      const sortedVolumes = [...volumes].sort((a: any, b: any) => a.volumeNumber - b.volumeNumber);
      const firstVolume = sortedVolumes[0];
      
      // 模仿卷规划页面的跳转逻辑，从第一卷开始创作，通过URL参数传递templateId
      navigate(`/novels/${selectedNovelId}/volumes/${firstVolume.id}/writing?templateId=${selectedTemplate.id}`);
      setNovelSelectVisible(false);
      setSelectedNovelId(null);
    } catch (error) {
      console.error('获取卷列表失败:', error);
      message.error('获取卷列表失败');
    }
  };

  const renderTemplateCard = (template: PromptTemplate) => {
    const isCustom = template.type === 'custom';
    const isFavorited = template.isFavorited || false;

    return (
      <div 
        key={template.id} 
        className={`prompt-card ${template.type}`}
        onClick={() => handleView(template)}
      >
        <div className="prompt-card-header">
          <div style={{ flex: 1 }}>
            <div className={`prompt-card-badge ${template.type}`}>
              {template.type === 'official' ? '🏆 官方模板' : '✨ 自定义模板'}
            </div>
            <div className="prompt-card-title">{template.name}</div>
          </div>
          <div 
            onClick={(e) => {
              e.stopPropagation();
              handleFavorite(template.id, isFavorited);
            }}
            style={{ 
              cursor: 'pointer', 
              fontSize: '20px',
              marginLeft: '12px'
            }}
          >
            {isFavorited ? (
              <StarFilled style={{ color: '#f59e0b' }} />
            ) : (
              <StarOutlined style={{ color: '#cbd5e0' }} />
            )}
          </div>
        </div>

        <div className="prompt-card-description">
          {template.description || '暂无描述'}
        </div>

        <div className="prompt-card-footer">
          <Button 
            type="primary" 
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              handleUseTemplate(template);
            }}
            style={{
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              border: 'none',
              borderRadius: '6px',
              fontWeight: 500,
              boxShadow: '0 2px 8px rgba(102, 126, 234, 0.3)',
              transition: 'all 0.3s ease'
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-1px)';
              e.currentTarget.style.boxShadow = '0 4px 12px rgba(102, 126, 234, 0.4)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = '0 2px 8px rgba(102, 126, 234, 0.3)';
            }}
          >
            ✨ 使用此模板
          </Button>
          <div style={{ flex: 1 }}></div>
          {isCustom && (
            <div className="prompt-card-actions">
              <Button 
                type="link" 
                size="small" 
                danger
                onClick={(e) => {
                  e.stopPropagation();
                  handleDelete(template.id);
                }}
              >
                删除
              </Button>
            </div>
          )}
        </div>
      </div>
    );
  };

  const renderContent = () => {
    let templates: PromptTemplate[] = [];
    if (activeTab === 'public') templates = publicTemplates;
    else if (activeTab === 'favorites') templates = favoriteTemplates;
    else if (activeTab === 'custom') templates = customTemplates;

    if (loading) {
      return (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" />
        </div>
      );
    }

    if (templates.length === 0) {
      if (activeTab === 'custom') {
        return (
          <div style={{
            textAlign: 'center',
            padding: '80px 40px',
            background: 'linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%)',
            borderRadius: '12px',
            border: '2px dashed #cbd5e0'
          }}>
            <div style={{
              fontSize: '72px',
              marginBottom: '24px',
              filter: 'grayscale(0.3)',
              opacity: 0.6
            }}>
              ✨
            </div>
            <h3 style={{
              fontSize: '20px',
              fontWeight: 600,
              color: '#2d3748',
              marginBottom: '12px'
            }}>
              创建你的专属模板
            </h3>
            <p style={{
              fontSize: '15px',
              color: '#718096',
              marginBottom: '32px',
              lineHeight: 1.6,
              maxWidth: '400px',
              margin: '0 auto 32px'
            }}>
              自定义AI写作风格，打造独一无二的创作助手
            </p>
            <Button 
              type="primary" 
              size="large"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalVisible(true)}
              style={{
                height: '44px',
                padding: '0 32px',
                fontSize: '15px',
                fontWeight: 600,
                borderRadius: '8px',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                border: 'none',
                boxShadow: '0 4px 12px rgba(102, 126, 234, 0.3)'
              }}
            >
              创建第一个模板
            </Button>
          </div>
        );
      }
      return (
        <div className="empty-state">
          <FileTextOutlined />
          <p>
            {activeTab === 'favorites' && '还没有收藏任何模板'}
            {activeTab === 'public' && '暂无公开模板'}
          </p>
        </div>
      );
    }

    return (
      <div className="prompt-grid">
        {activeTab === 'custom' && (
          <div className="prompt-card create-prompt-card" onClick={() => setCreateModalVisible(true)}>
            <PlusOutlined />
            <span>创建新模板</span>
          </div>
        )}
        {templates.map(renderTemplateCard)}
      </div>
    );
  };

  return (
    <div className="prompt-library-page">
      <div className="prompt-library-container">
        <div className="prompt-library-header">
          <h1>💎 提示词模板库</h1>
          <p>选择或创建专属的AI写作提示词</p>
        </div>

        <div className="prompt-tabs">
          <Tabs 
            activeKey={activeTab} 
            onChange={setActiveTab}
            size="large"
            tabBarExtraContent={
              activeTab === 'custom' && (
                <Button 
                  type="primary" 
                  icon={<PlusOutlined />}
                  onClick={() => setCreateModalVisible(true)}
                >
                  创建模板
                </Button>
              )
            }
          >
            <TabPane 
              tab={<span><GlobalOutlined /> 公开模板</span>} 
              key="public"
            />
            <TabPane 
              tab={<span><HeartOutlined /> 我的收藏</span>} 
              key="favorites"
            />
            <TabPane 
              tab={<span><FileTextOutlined /> 自定义模板</span>} 
              key="custom"
            />
          </Tabs>

          {renderContent()}
        </div>
      </div>

      {/* 创建/编辑模板弹窗 */}
      <Modal
        title="创建提示词模板"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          form.resetFields();
        }}
        onOk={handleCreate}
        width={700}
        okText="创建"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input placeholder="例如：网文大神风格" />
          </Form.Item>

          <Form.Item
            name="category"
            label="分类"
            rules={[{ required: true, message: '请选择分类' }]}
          >
            <Select placeholder="选择分类">
              <Select.Option value="system_identity">系统身份</Select.Option>
              <Select.Option value="writing_style">写作风格</Select.Option>
              <Select.Option value="anti_ai">去AI味</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="description"
            label="模板描述"
            rules={[{ required: true, message: '请输入模板描述' }]}
          >
            <Input placeholder="简要描述这个模板的特点和用途" />
          </Form.Item>

          <Form.Item
            name="content"
            label="提示词内容"
            rules={[{ required: true, message: '请输入提示词内容' }]}
          >
            <TextArea
              rows={12}
              placeholder="输入完整的提示词内容..."
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看模板详情弹窗 */}
      <Modal
        title="模板详情"
        open={viewModalVisible}
        onCancel={() => setViewModalVisible(false)}
        width={700}
        footer={[
          <Button 
            key="use" 
            type="primary" 
            size="large"
            onClick={() => {
              setViewModalVisible(false);
              selectedTemplate && handleUseTemplate(selectedTemplate);
            }}
            style={{
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              border: 'none',
              borderRadius: '8px',
              fontWeight: 600,
              height: '44px',
              padding: '0 32px',
              fontSize: '15px',
              boxShadow: '0 4px 12px rgba(102, 126, 234, 0.3)'
            }}
          >
            ✨ 使用此模板
          </Button>,
          <Button 
            key="close" 
            size="large"
            onClick={() => setViewModalVisible(false)}
            style={{
              height: '44px',
              borderRadius: '8px'
            }}
          >
            关闭
          </Button>
        ]}
      >
        {selectedTemplate && (
          <div>
            <Card>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ marginBottom: 12 }}>
                    <span style={{ 
                      padding: '4px 10px',
                      borderRadius: '4px',
                      fontSize: '12px',
                      fontWeight: 500,
                      background: selectedTemplate.type === 'official' ? '#fef3c7' : '#ede9fe',
                      color: selectedTemplate.type === 'official' ? '#92400e' : '#5b21b6'
                    }}>
                      {selectedTemplate.type === 'official' ? '🏆 官方模板' : '✨ 自定义模板'}
                    </span>
                  </div>
                  <h3 style={{ marginBottom: 16, fontSize: '22px', fontWeight: 600, color: '#2d3748' }}>
                    {selectedTemplate.name}
                  </h3>
                  
                  <div style={{ marginBottom: 20 }}>
                    <div style={{ 
                      fontSize: '14px', 
                      fontWeight: 600, 
                      color: '#4a5568', 
                      marginBottom: 8 
                    }}>
                      📝 简介
                    </div>
                    <p style={{ 
                      color: '#718096', 
                      fontSize: '15px', 
                      lineHeight: 1.8,
                      margin: 0,
                      padding: '12px',
                      background: '#f7fafc',
                      borderRadius: '6px',
                      border: '1px solid #e2e8f0'
                    }}>
                      {selectedTemplate.description || '暂无简介'}
                    </p>
                  </div>

                  <div style={{ 
                    padding: '12px', 
                    background: '#fff8e1', 
                    borderRadius: '6px',
                    border: '1px solid #ffe082'
                  }}>
                    <div style={{ fontSize: '13px', color: '#f57c00', fontWeight: 500 }}>
                      💡 提示
                    </div>
                    <div style={{ fontSize: '13px', color: '#e65100', marginTop: 6, lineHeight: 1.6 }}>
                      提示词核心内容为核心资产，仅在使用时应用于AI写作，不对外展示。
                    </div>
                  </div>
                </div>
                <Button
                  type={selectedTemplate.isFavorited ? 'default' : 'primary'}
                  icon={selectedTemplate.isFavorited ? <StarFilled /> : <StarOutlined />}
                  onClick={() => {
                    handleFavorite(selectedTemplate.id, selectedTemplate.isFavorited || false);
                  }}
                  style={{ marginLeft: 16 }}
                >
                  {selectedTemplate.isFavorited ? '取消收藏' : '收藏'}
                </Button>
              </div>
            </Card>
          </div>
        )}
      </Modal>

      {/* 选择书籍弹窗 */}
      <Modal
        title="选择要绑定的书籍"
        open={novelSelectVisible}
        onCancel={() => {
          setNovelSelectVisible(false);
          setSelectedNovelId(null);
        }}
        onOk={handleBindTemplate}
        width={600}
        okButtonProps={{ disabled: !selectedNovelId }}
      >
        {selectedTemplate && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ 
              padding: '12px', 
              background: '#f0f9ff', 
              borderRadius: '6px',
              marginBottom: 16
            }}>
              <div style={{ fontSize: '14px', color: '#1e40af', fontWeight: 500 }}>
                即将使用模板：{selectedTemplate.name}
              </div>
            </div>
            
            {availableNovels.length === 0 ? (
              <Empty 
                description="暂无符合条件的书籍" 
                style={{ padding: '40px 0' }}
              >
                <p style={{ color: '#718096', fontSize: '14px', marginTop: 8 }}>
                  书籍需要满足：已生成大纲、卷大纲，且处于写作状态
                </p>
              </Empty>
            ) : (
              <div>
                <div style={{ marginBottom: 12, fontSize: '14px', color: '#4a5568' }}>
                  选择书籍（{availableNovels.length} 本可用）：
                </div>
                <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                  {availableNovels.map((novel: any) => (
                    <Card
                      key={novel.id}
                      style={{ 
                        marginBottom: 12, 
                        cursor: 'pointer',
                        border: selectedNovelId === novel.id ? '2px solid #5a67d8' : '1px solid #e2e8f0',
                        background: selectedNovelId === novel.id ? '#f0f4ff' : 'white'
                      }}
                      onClick={() => setSelectedNovelId(novel.id)}
                      hoverable
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div>
                          <div style={{ fontSize: '16px', fontWeight: 600, color: '#2d3748' }}>
                            {novel.title}
                          </div>
                          <div style={{ fontSize: '13px', color: '#718096', marginTop: 4 }}>
                            {novel.genre} · {novel.status}
                          </div>
                        </div>
                        {selectedNovelId === novel.id && (
                          <div style={{ color: '#5a67d8', fontSize: '20px' }}>✓</div>
                        )}
                      </div>
                    </Card>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PromptLibraryPage;
