import React, { useEffect, useState } from 'react';
import { Card, Button, List, Tag, Progress, Typography } from 'antd';
import { PlusOutlined, RocketOutlined, RightOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { videoScriptService, VideoScript } from '../../services/videoScriptService';
import dayjs from 'dayjs';

const { Title } = Typography;

const VideoScriptListPage: React.FC = () => {
  const navigate = useNavigate();
  const [scripts, setScripts] = useState<VideoScript[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const response = await videoScriptService.list();
      setScripts(Array.isArray(response) ? response : (response as any).data || []);
    } catch (error) {
      console.error('加载列表失败', error);
    } finally {
      setLoading(false);
    }
  };

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'DRAFT':
        return <Tag>草稿</Tag>;
      case 'WORKFLOW_RUNNING':
        return (
          <Tag color="processing" icon={<RocketOutlined spin />}>
            生成中
          </Tag>
        );
      case 'WORKFLOW_PAUSED':
        return <Tag color="warning">已暂停</Tag>;
      case 'COMPLETED':
        return <Tag color="success">已完成</Tag>;
      case 'FAILED':
        return <Tag color="error">失败</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const getProgress = (s: VideoScript) => {
    // 基础四步：设定/大纲/看点/导语
    const baseTotal = 4;
    let baseDone = 0;
    if (s.scriptSetting && s.scriptSetting.trim()) baseDone += 1;
    if (s.outline && s.outline.trim()) baseDone += 1;
    if (s.hooksJson && s.hooksJson.trim()) baseDone += 1;
    if (s.prologue && s.prologue.trim()) baseDone += 1;

    const epCount = s.episodeCount ?? 0;
    const epDone = Math.min(s.currentEpisode ?? 0, epCount);

    const total = baseTotal + (epCount > 0 ? epCount : 0);
    const done = baseDone + epDone;
    return total > 0 ? Math.round((done / total) * 100) : 0;
  };

  const modeLabel = (mode?: string) => {
    if (mode === 'PURE_NARRATION') return '纯解说';
    if (mode === 'HALF_NARRATION') return '半解说';
    return mode || '-';
  };

  const formatLabel = (fmt?: string) => {
    if (fmt === 'SCENE') return '集-场台本';
    if (fmt === 'STORYBOARD') return '分镜脚本';
    if (fmt === 'NARRATION') return '解说口播';
    return fmt || '-';
  };

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <Title level={2}>
          <VideoCameraOutlined /> 剧本工厂
        </Title>
        <Button type="primary" icon={<PlusOutlined />} size="large" onClick={() => navigate('/video-scripts/create')}>
          新建系列
        </Button>
      </div>

      <List
        grid={{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 3, xl: 4, xxl: 4 }}
        dataSource={scripts}
        loading={loading}
        renderItem={(item) => {
          const percent = getProgress(item);
          return (
            <List.Item>
              <Card
                hoverable
                onClick={() => navigate(`/video-scripts/${item.id}`)}
                actions={[
                  <div key="status" style={{ padding: '0 12px', textAlign: 'left' }}>
                    {getStatusTag(item.status)}
                  </div>,
                  <Button
                    type="link"
                    key="enter"
                    onClick={(e) => {
                      e.stopPropagation();
                      navigate(`/video-scripts/${item.id}`);
                    }}
                  >
                    进入工作台 <RightOutlined />
                  </Button>,
                ]}
              >
                <Card.Meta
                  title={<div style={{ fontSize: '16px', fontWeight: 'bold' }}>{item.title}</div>}
                  description={
                    <div style={{ height: '90px', overflow: 'hidden' }}>
                      <div
                        style={{
                          marginBottom: '8px',
                          color: '#666',
                          fontSize: '12px',
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                        }}
                      >
                        {item.idea}
                      </div>
                      <div style={{ fontSize: '12px', color: '#999', marginBottom: 6 }}>
                        模式：{modeLabel(item.mode)} · 格式：{formatLabel(item.scriptFormat)} · 每集时长：{item.targetSeconds || 0}s · 每集镜头：{item.sceneCount || 0} · 进度：{item.currentEpisode || 0}/{item.episodeCount || 0} 集
                      </div>
                      <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
                          <span>进度</span>
                          <span>{percent}%</span>
                        </div>
                        <Progress
                          percent={percent}
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
          );
        }}
      />
    </div>
  );
};

export default VideoScriptListPage;
