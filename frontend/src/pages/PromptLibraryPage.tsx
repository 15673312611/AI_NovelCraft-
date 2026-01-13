import React, { useEffect, useState } from 'react';
import { 
  Button, Modal, Form, Input, Select, message, 
  Spin, Empty
} from 'antd';
import { 
  PlusOutlined, StarOutlined, StarFilled, 
  FileTextOutlined, HeartOutlined, GlobalOutlined,
  CheckOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import api from '@/services/api';
import './PromptLibraryPage.css';

const { TextArea } = Input;

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

  const handleFavorite = async (templateId: number, isFavorited: boolean, e?: React.MouseEvent) => {
    e?.stopPropagation();
    try {
      if (isFavorited) {
        await api.delete(`/prompt-templates/${templateId}/favorite`);
        message.success('取消收藏成功');
      } else {
        await api.post(`/prompt-templates/${templateId}/favorite`);
        message.success('收藏成功');
      }
      
      const updateTemplateStatus = (templates: PromptTemplate[]) => 
        templates.map(t => 
          t.id === templateId ? { ...t, isFavorited: !isFavorited } : t
        );
      
      setPublicTemplates(prev => updateTemplateStatus(prev));
      setFavoriteTemplates(prev => updateTemplateStatus(prev));
      setCustomTemplates(prev => updateTemplateStatus(prev));
      
      if (selectedTemplate && selectedTemplate.id === templateId) {
        setSelectedTemplate({ ...selectedTemplate, isFavorited: !isFavorited });
      }
      
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

  const handleDelete = (templateId: number, e?: React.MouseEvent) => {
    e?.stopPropagation();
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个提示词模板吗？',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
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

  const handleUseTemplate = async (template: PromptTemplate, e?: React.MouseEvent) => {
    e?.stopPropagation();
    setSelectedTemplate(template);
    setNovelSelectVisible(true);
    
    try {
      const response: any = await api.get('/novels/writable');
      const writableNovels = response || [];
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
      const volumes: any = await api.get(`/volumes/novel/${selectedNovelId}`);
      
      if (!volumes || volumes.length === 0) {
        message.warning('该书籍暂无可用的卷，请先生成卷规划');
        return;
      }
      
      const sortedVolumes = [...volumes].sort((a: any, b: any) => a.volumeNumber - b.volumeNumber);
      const firstVolume = sortedVolumes[0];
      
      navigate(`/novels/${selectedNovelId}/writing-studio?templateId=${selectedTemplate.id}`, {
        state: { initialVolumeId: firstVolume.id }
      });
      setNovelSelectVisible(false);
      setSelectedNovelId(null);
    } catch (error) {
      console.error('获取卷列表失败:', error);
      message.error('获取卷列表失败');
    }
  };

  const tabs = [
    { key: 'public', label: '公开模板', icon: <GlobalOutlined /> },
    { key: 'favorites', label: '我的收藏', icon: <HeartOutlined /> },
    { key: 'custom', label: '自定义模板', icon: <FileTextOutlined /> },
  ];

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
          <div className="prompt-card-meta">
            <div className={`prompt-card-badge ${template.type}`}>
              {template.type === 'official' ? '🏆 官方' : '✨ 自定义'}
            </div>
            <h3 className="prompt-card-title">{template.name}</h3>
          </div>
          <div 
            className="prompt-card-favorite"
            onClick={(e) => handleFavorite(template.id, isFavorited, e)}
          >
            {isFavorited ? (
              <StarFilled style={{ color: '#f59e0b' }} />
            ) : (
              <StarOutlined />
            )}
          </div>
        </div>

        <p className="prompt-card-description">
          {template.description || '暂无描述'}
        </p>

        <div className="prompt-card-footer">
          <Button 
            type="primary" 
            size="small"
            className="prompt-use-btn"
            onClick={(e) => handleUseTemplate(template, e)}
          >
            使用模板
          </Button>
          {isCustom && (
            <Button 
              type="link" 
              size="small" 
              className="prompt-delete-btn"
              onClick={(e) => handleDelete(template.id, e)}
            >
              删除
            </Button>
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
        <div className="prompt-loading">
          <Spin size="large" />
        </div>
      );
    }

    if (templates.length === 0) {
      if (activeTab === 'custom') {
        return (
          <div className="prompt-empty-state">
            <div className="prompt-empty-icon">✨</div>
            <h3>创建你的专属模板</h3>
            <p>自定义AI写作风格，打造独一无二的创作助手</p>
            <Button 
              type="primary" 
              size="large"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalVisible(true)}
              className="prompt-create-btn"
            >
              创建第一个模板
            </Button>
          </div>
        );
      }
      return (
        <div className="prompt-empty-state">
          <div className="prompt-empty-icon">
            <FileTextOutlined />
          </div>
          <h3>{activeTab === 'favorites' ? '还没有收藏任何模板' : '暂无公开模板'}</h3>
          <p>{activeTab === 'favorites' ? '浏览公开模板，收藏你喜欢的' : '敬请期待更多模板'}</p>
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
        {/* 页面头部 */}
        <div className="prompt-page-header">
          <div className="prompt-header-left">
            <h1>提示词模板库</h1>
            <p>选择或创建专属的AI写作提示词，提升创作效率</p>
          </div>
          <Button 
            type="primary" 
            icon={<PlusOutlined />}
            onClick={() => setCreateModalVisible(true)}
            className="prompt-create-btn"
          >
            创建模板
          </Button>
        </div>

        {/* Tab 切换 */}
        <div className="prompt-tab-section">
          <div className="prompt-tab-bar">
            {tabs.map(tab => (
              <button
                key={tab.key}
                className={`prompt-tab-item ${activeTab === tab.key ? 'active' : ''}`}
                onClick={() => setActiveTab(tab.key)}
              >
                {tab.icon}
                <span>{tab.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* 内容区域 */}
        {renderContent()}
      </div>

      {/* 创建模板弹窗 */}
      <Modal
        title="创建提示词模板"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          form.resetFields();
        }}
        onOk={handleCreate}
        width={640}
        okText="创建"
        cancelText="取消"
        className="prompt-modal"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 20 }}>
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
              rows={10}
              placeholder="输入完整的提示词内容..."
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看模板详情弹窗 */}
      <Modal
        title={null}
        open={viewModalVisible}
        onCancel={() => setViewModalVisible(false)}
        width={640}
        className="prompt-modal"
        footer={[
          <Button 
            key="favorite"
            icon={selectedTemplate?.isFavorited ? <StarFilled /> : <StarOutlined />}
            onClick={() => selectedTemplate && handleFavorite(selectedTemplate.id, selectedTemplate.isFavorited || false)}
          >
            {selectedTemplate?.isFavorited ? '取消收藏' : '收藏'}
          </Button>,
          <Button 
            key="use" 
            type="primary" 
            className="prompt-use-btn"
            onClick={() => {
              setViewModalVisible(false);
              selectedTemplate && handleUseTemplate(selectedTemplate);
            }}
          >
            使用此模板
          </Button>,
        ]}
      >
        {selectedTemplate && (
          <div className="prompt-detail-card">
            <div className={`prompt-detail-badge ${selectedTemplate.type}`}>
              {selectedTemplate.type === 'official' ? '🏆 官方模板' : '✨ 自定义模板'}
            </div>
            <h2 className="prompt-detail-title">{selectedTemplate.name}</h2>
            
            <div className="prompt-detail-section">
              <div className="prompt-detail-section-title">📝 简介</div>
              <div className="prompt-detail-description">
                {selectedTemplate.description || '暂无简介'}
              </div>
            </div>

            <div className="prompt-detail-tip">
              <span className="prompt-detail-tip-icon">💡</span>
              <div className="prompt-detail-tip-content">
                <div className="prompt-detail-tip-title">提示</div>
                <div className="prompt-detail-tip-text">
                  提示词核心内容为核心资产，仅在使用时应用于AI写作，不对外展示。
                </div>
              </div>
            </div>
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
        width={560}
        okText="确认"
        cancelText="取消"
        okButtonProps={{ disabled: !selectedNovelId }}
        className="prompt-modal"
      >
        {selectedTemplate && (
          <div style={{ marginTop: 16 }}>
            <div style={{ 
              padding: '14px 18px', 
              background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
              borderRadius: '12px',
              marginBottom: 20,
              fontSize: '14px',
              color: '#1d4ed8',
              fontWeight: 500
            }}>
              即将使用模板：{selectedTemplate.name}
            </div>
            
            {availableNovels.length === 0 ? (
              <Empty 
                description="暂无符合条件的书籍" 
                style={{ padding: '40px 0' }}
              >
                <p style={{ color: '#64748b', fontSize: '14px', marginTop: 8 }}>
                  书籍需要满足：已生成大纲、卷大纲，且处于写作状态
                </p>
              </Empty>
            ) : (
              <div>
                <div style={{ marginBottom: 12, fontSize: '14px', color: '#64748b' }}>
                  选择书籍（{availableNovels.length} 本可用）
                </div>
                <div style={{ maxHeight: 320, overflowY: 'auto' }}>
                  {availableNovels.map((novel: any) => (
                    <div
                      key={novel.id}
                      className={`novel-select-card ${selectedNovelId === novel.id ? 'selected' : ''}`}
                      onClick={() => setSelectedNovelId(novel.id)}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div>
                          <div className="novel-select-card-title">{novel.title}</div>
                          <div className="novel-select-card-meta">{novel.genre} · {novel.status}</div>
                        </div>
                        {selectedNovelId === novel.id && (
                          <div className="novel-select-check">
                            <CheckOutlined />
                          </div>
                        )}
                      </div>
                    </div>
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
