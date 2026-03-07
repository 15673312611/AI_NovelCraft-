import React, { useEffect, useState } from 'react';
import { Card, Button, List, Tag, Progress, Typography, Space } from 'antd';
import { PlusOutlined, RocketOutlined, RightOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { shortStoryService, ShortNovel } from '../../services/shortStoryService';
import dayjs from 'dayjs';

const { Title, Text } = Typography;

const ShortStoryListPage: React.FC = () => {
  const navigate = useNavigate();
  const [novels, setNovels] = useState<ShortNovel[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const response = await shortStoryService.list();
      setNovels(Array.isArray(response) ? response : (response as any).data || []);
    } catch (error) {
      console.error('加载列表失败', error);
    } finally {
      setLoading(false);
    }
  };

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'DRAFT': return <Tag>草稿</Tag>;
      case 'WORKFLOW_RUNNING': return <Tag color="processing" icon={<RocketOutlined spin />}>生成中</Tag>;
      case 'WORKFLOW_PAUSED': return <Tag color="warning">已暂停</Tag>;
      case 'COMPLETED': return <Tag color="success">已完成</Tag>;
      case 'FAILED': return <Tag color="error">失败</Tag>;
      default: return <Tag>{status}</Tag>;
    }
  };

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <Title level={2}>短篇小说工厂</Title>
        <Button 
          type="primary" 
          icon={<PlusOutlined />} 
          size="large"
          onClick={() => navigate('/short-stories/create')}
        >
          新建短篇
        </Button>
      </div>

      <List
        grid={{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 3, xl: 4, xxl: 4 }}
        dataSource={novels}
        loading={loading}
        renderItem={item => (
          <List.Item>
            <Card 
              hoverable
              onClick={() => navigate(`/short-stories/${item.id}`)}
              actions={[
                <div key="status" style={{ padding: '0 12px', textAlign: 'left' }}>
                  {getStatusTag(item.status)}
                </div>,
                <Button 
                  type="link" 
                  key="enter"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate(`/short-stories/${item.id}`);
                  }}
                >
                  进入工作台 <RightOutlined />
                </Button>
              ]}
            >
              <Card.Meta
                title={<div style={{ fontSize: '16px', fontWeight: 'bold' }}>{item.title}</div>}
                description={
                  <div style={{ height: '80px', overflow: 'hidden' }}>
                    <div style={{ marginBottom: '8px', color: '#666', fontSize: '12px', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                      {item.idea}
                    </div>
                    <div style={{ marginTop: 'auto' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
                        <span>进度: {item.currentChapter}/{item.chapterCount}章</span>
                        <span>{Math.round((item.currentChapter / item.chapterCount) * 100)}%</span>
                      </div>
                      <Progress 
                        percent={Math.round((item.currentChapter / item.chapterCount) * 100)} 
                        showInfo={false} 
                        size="small" 
                        status={item.status === 'WORKFLOW_RUNNING' ? 'active' : 'normal'}
                      />
                    </div>
                  </div>
                }
              />
              <div style={{ marginTop: '12px', fontSize: '12px', color: '#999' }}>
                更新于: {dayjs(item.updatedAt).format('MM-DD HH:mm')}
              </div>
            </Card>
          </List.Item>
        )}
      />
    </div>
  );
};

export default ShortStoryListPage;
