import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getAllGenerators, AiGenerator } from '../services/aiGeneratorService';
import './GeneratorListPage.css';

const GeneratorListPage: React.FC = () => {
  const navigate = useNavigate();
  const [generators, setGenerators] = useState<AiGenerator[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadGenerators();
  }, []);

  const loadGenerators = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getAllGenerators();
      setGenerators(data);
    } catch (err: any) {
      console.error('Lỗi khi tải generator:', err);
      setError(err.message || 'Không thể tải danh sách generator');
    } finally {
      setLoading(false);
    }
  };

  const handleGeneratorClick = (generator: AiGenerator) => {
    // Chuyển đến trang AI chat với generator ID
    navigate(`/ai-chat?generatorId=${generator.id}`);
  };

  const getIconEmoji = (iconName: string): string => {
    const iconMap: { [key: string]: string } = {
      'document': '📄',
      'document-text': '📝',
      'cursor-click': '👆',
      'list-bullet': '📋',
      'rocket': '🚀',
      'user': '👤',
      'light-bulb': '💡',
      'document-duplicate': '📑',
      'chat-bubble': '💬',
    };
    return iconMap[iconName] || '✨';
  };

  const getCategoryLabel = (category: string): string => {
    const categoryMap: { [key: string]: string } = {
      'planning': '规划',
      'writing': '写作',
      'character': '角色',
      'general': '通用',
    };
    return categoryMap[category] || category;
  };

  if (loading) {
    return (
      <div className="generator-list-page">
        <div className="generator-container">
          <div className="generator-header">
            <h1>ai</h1>
            <p>让创作更简单</p>
          </div>
          <div className="loading-container">
            <div style={{ fontSize: '24px', marginBottom: '16px' }}>✨</div>
            <div>正在加载生成器...</div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="generator-list-page">
        <div className="generator-container">
          <div className="generator-header">
            <h1>ai</h1>
            <p>让创作更简单</p>
          </div>
          <div className="error-container">
            <div style={{ fontSize: '48px', marginBottom: '20px' }}>😔</div>
            <h3>加载失败</h3>
            <p>{error}</p>
            <button 
              onClick={loadGenerators}
              style={{
                marginTop: '24px',
                padding: '12px 32px',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                color: 'white',
                border: 'none',
                borderRadius: '12px',
                cursor: 'pointer',
                fontWeight: '500',
                fontSize: '15px',
                transition: 'all 0.3s ease',
                boxShadow: '0 4px 12px rgba(102, 126, 234, 0.3)',
              }}
              onMouseOver={(e) => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.4)';
              }}
              onMouseOut={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = '0 4px 12px rgba(102, 126, 234, 0.3)';
              }}
            >
              重新加载
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (generators.length === 0) {
    return (
      <div className="generator-list-page">
        <div className="generator-container">
          <div className="generator-header">
            <h1>ai</h1>
            <p>让创作更简单</p>
          </div>
          <div className="empty-container">
            <div style={{ fontSize: '64px', marginBottom: '20px' }}>📝</div>
            <div>暂无可用的生成器</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="generator-list-page">
      <div className="generator-container">
        <div className="generator-header">
          <h1>AI写作工坊</h1>
          <p>AI赋能创作，让灵感永不枯竭</p>
        </div>

        <div className="generator-grid">
          {generators.map((generator) => (
            <div
              key={generator.id}
              className="generator-card"
              onClick={() => handleGeneratorClick(generator)}
            >
              <div className="generator-icon">
                {getIconEmoji(generator.icon)}
              </div>
              <div className="generator-content">
                <h3>{generator.name}</h3>
                <p>{generator.description}</p>
                <span className={`generator-category-badge ${generator.category}`}>
                  {getCategoryLabel(generator.category)}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default GeneratorListPage;

