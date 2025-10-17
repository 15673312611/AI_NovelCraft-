import React, { useEffect, useState } from 'react'
import { Row, Col, Card, Button, Empty, Progress, Typography } from 'antd'
import {
  BookOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  RightOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { RootState, AppDispatch } from '@/store'
import { fetchNovels } from '@/store/slices/novelSlice'
import './HomePage.new.css'
import novelVolumeService, { NovelVolume } from '@/services/novelVolumeService'

const { Title, Text, Paragraph } = Typography

const HomePage: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { isAuthenticated } = useSelector((state: RootState) => state.auth)
  const { novels, loading } = useSelector((state: RootState) => state.novel)

  useEffect(() => {
    if (isAuthenticated) {
      dispatch(fetchNovels())
    }
  }, [dispatch, isAuthenticated])

  // 如果未登录，跳转到登录页
  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login')
    }
  }, [isAuthenticated, navigate])

  const novelsArray = Array.isArray(novels) ? novels : []
  
  // 直达写作：仅当卷已进入写作或具备详细大纲时才直达；否则退回卷页面
  const enterWritingDirectly = async (novelId: number) => {
    try {
      const volumes: NovelVolume[] = await novelVolumeService.getVolumesByNovelId(String(novelId))
      if (!volumes || volumes.length === 0) {
        navigate(`/novels/${novelId}/volumes`)
        return
      }
      const byInProgress = volumes.find(v => v.status === 'IN_PROGRESS')
      const byDetailed = volumes.find((v: any) => v?.contentOutline && v.contentOutline.length >= 100)
      const target = byInProgress || byDetailed || null
      if (target && target.id) {
        navigate(`/novels/${novelId}/volumes/${target.id}/writing`, {
          state: { initialVolumeId: target.id, sessionData: null }
        })
        return
      }
      navigate(`/novels/${novelId}/volumes`)
    } catch (e) {
      console.error('进入写作页失败:', e)
      navigate(`/novels/${novelId}/volumes`)
    }
  }
  
  // 计算统计数据
  const totalNovels = novelsArray.length
  const totalChapters = novelsArray.reduce((sum, n) => sum + (n.chapterCount || 0), 0)
  const totalWords = novelsArray.reduce((sum, n) => sum + (n.wordCount || 0), 0)

  // 获取最近编辑的小说（最多5个）
  const recentNovels = novelsArray
    .slice()
    .sort((a, b) => new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime())
    .slice(0, 5)

  if (!isAuthenticated) {
    return null
  }

  return (
    <div className="dashboard-page">
      {/* Hero区域 */}
      <div className="hero-section">
        <div className="hero-content">
          <div className="hero-text">
            <div className="hero-badge">✨ AI智能辅助</div>
            <Title level={1} className="hero-title">开始你的创作之旅</Title>
            <Paragraph className="hero-subtitle">
              AI智能辅助，从大纲到章节，让每一个故事都精彩绝伦
            </Paragraph>
          </div>
          <Button 
            type="primary" 
            size="large" 
            icon={<PlusOutlined />}
            onClick={() => navigate('/novels/new')}
            className="hero-cta-btn"
          >
            新建小说
          </Button>
        </div>
      </div>

      {/* 主要内容区域 */}
      <Row gutter={[20, 20]}>
        {/* 最近编辑 */}
        <Col xs={24} lg={17}>
          <Card 
            title="最近编辑"
            extra={
              <Button 
                type="link" 
                onClick={() => navigate('/novels')} 
                style={{ padding: 0, color: '#3b82f6', fontWeight: 500 }}
              >
                查看全部 <RightOutlined />
              </Button>
            }
            className="content-card"
          >
            {recentNovels.length > 0 ? (
              <div className="novel-list">
                {recentNovels.map(novel => (
                  <div 
                    key={novel.id} 
                    className="novel-item"
                    onClick={() => enterWritingDirectly(novel.id as any)}
                  >
                    <div className="novel-item-icon">
                      <BookOutlined />
                    </div>
                    <div className="novel-item-body">
                      <div className="novel-item-header">
                        <div className="novel-info">
                          <Text strong style={{ fontSize: 15 }}>{novel.title}</Text>
                          <Text type="secondary" className="novel-desc">
                            {novel.description}
                          </Text>
                        </div>
                        <RightOutlined style={{ color: '#cbd5e1', fontSize: 12 }} />
                      </div>
                      <div className="novel-item-footer">
                        <div className="novel-meta-tags">
                          <span className="meta-tag">
                            <FileTextOutlined style={{ fontSize: 12 }} />
                            {novel.chapterCount || 0} 章节
                          </span>
                          <span className="meta-tag">
                            <EditOutlined style={{ fontSize: 12 }} />
                            {((novel.wordCount || 0) / 10000).toFixed(1)}万字
                          </span>
                        </div>
                        {novel.wordCount > 0 && (
                          <Progress 
                            percent={Math.min(100, (novel.wordCount / 100000) * 100)} 
                            size="small"
                            strokeColor="#3b82f6"
                            showInfo={false}
                            style={{ marginTop: 6 }}
                          />
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <Empty 
                description="还没有创作任何小说"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              >
                <Button 
                  type="primary" 
                  icon={<PlusOutlined />}
                  onClick={() => navigate('/novels/new')}
                >
                  开始创作
                </Button>
              </Empty>
            )}
          </Card>
        </Col>

        {/* 快捷操作 */}
        <Col xs={24} lg={7}>
          <Card 
            title="快捷操作"
            className="content-card"
          >
            <div className="quick-action-list">
              <div 
                className="quick-action-item"
                onClick={() => navigate('/novels/new')}
              >
                <div className="action-icon" style={{ background: '#eff6ff', color: '#3b82f6' }}>
                  <PlusOutlined />
                </div>
                <div className="action-info">
                  <Text strong>新建小说</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    开始创作新故事
                  </Text>
                </div>
                <RightOutlined style={{ color: '#cbd5e1' }} />
              </div>

              <div 
                className="quick-action-item"
                onClick={() => navigate('/novels')}
              >
                <div className="action-icon" style={{ background: '#f0fdf4', color: '#10b981' }}>
                  <BookOutlined />
                </div>
                <div className="action-info">
                  <Text strong>我的作品</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    查看所有小说
                  </Text>
                </div>
                <RightOutlined style={{ color: '#cbd5e1' }} />
              </div>

            </div>
          </Card>

          {/* 写作提示 */}
          <Card 
            className="tips-card"
            style={{ marginTop: 16 }}
            bordered={false}
          >
            <div className="tip-content">
              <div className="tip-icon">💡</div>
              <div className="tip-text">
                <Text strong style={{ display: 'block', marginBottom: 6 }}>
                  写作小贴士
                </Text>
                <Text style={{ fontSize: 13, lineHeight: 1.6, color: '#92400e' }}>
                  坚持每天写作，即使只是几百字，也能保持创作的连贯性和灵感的流动
                </Text>
              </div>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default HomePage

