import React, { useState, useEffect } from 'react';
import {
  Card,
  Space,
  Button,
  Switch,
  Slider,
  Select,
  Typography,
  Tooltip,
  Alert,
  Progress,
  Badge,
  Row,
  Col,
  Divider,
  message
} from 'antd';
import {
  RobotOutlined,
  UserOutlined,
  FireOutlined,
  HeartOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  ExclamationTriangleOutlined
} from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;
const { Option } = Select;

interface HumanizedWritingPanelProps {
  onSettingsChange: (settings: WritingSettings) => void;
  currentContent?: string;
  aiScore?: number;
  genre?: string;
}

interface WritingSettings {
  humanizationLevel: number;
  authorPersona: string;
  creativeSituation: string;
  antiAIMode: boolean;
  coherenceCheck: boolean;
  styleConsistency: boolean;
  emotionalDepth: number;
  dialogueNaturalness: number;
  descriptionVividness: number;
}

const HumanizedWritingPanel: React.FC<HumanizedWritingPanelProps> = ({
  onSettingsChange,
  currentContent,
  aiScore = 0.5,
  genre = '都市异能'
}) => {
  const [settings, setSettings] = useState<WritingSettings>({
    humanizationLevel: 8,
    authorPersona: 'experienced',
    creativeSituation: 'focused',
    antiAIMode: true,
    coherenceCheck: true,
    styleConsistency: true,
    emotionalDepth: 7,
    dialogueNaturalness: 8,
    descriptionVividness: 6
  });

  const [analysisResult, setAnalysisResult] = useState<any>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  // 作者人格选项
  const authorPersonas = [
    { value: 'experienced', label: '资深作者', desc: '十年创作经验，风格成熟稳重' },
    { value: 'passionate', label: '热血作者', desc: '充满激情，擅长燃向情节' },
    { value: 'delicate', label: '细腻作者', desc: '注重情感描写和心理刻画' },
    { value: 'humorous', label: '幽默作者', desc: '擅长轻松诙谐的叙述风格' },
    { value: 'mysterious', label: '悬疑作者', desc: '善于营造神秘氛围和悬念' }
  ];

  // 创作情境选项
  const creativeSituations = [
    { value: 'focused', label: '专注状态', desc: '安静环境，全神贯注创作' },
    { value: 'inspired', label: '灵感迸发', desc: '思维活跃，创意源源不断' },
    { value: 'relaxed', label: '放松随性', desc: '轻松氛围，自然流淌文字' },
    { value: 'passionate', label: '激情满怀', desc: '情绪高昂，笔下生花' },
    { value: 'contemplative', label: '沉思状态', desc: '深度思考，字字珠玑' }
  ];

  useEffect(() => {
    onSettingsChange(settings);
  }, [settings, onSettingsChange]);

  const handleSettingChange = (key: keyof WritingSettings, value: any) => {
    setSettings(prev => ({
      ...prev,
      [key]: value
    }));
  };

  const analyzeCurrentContent = async () => {
    if (!currentContent) {
      message.warning('没有内容可分析');
      return;
    }

    setIsAnalyzing(true);
    try {
      // 模拟AI分析请求
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      setAnalysisResult({
        aiScore: Math.random() * 0.4 + 0.1, // 0.1-0.5之间
        humanizationScore: Math.random() * 0.4 + 0.6, // 0.6-1.0之间
        issues: [
          '检测到部分AI句式',
          '对话略显生硬',
          '描写过于程式化'
        ],
        strengths: [
          '情节推进自然',
          '角色性格鲜明',
          '氛围营造到位'
        ],
        suggestions: [
          '增加更多生活化细节',
          '让对话更贴近真实场景',
          '适当增加情感描写'
        ]
      });

      message.success('内容分析完成');
    } catch (error) {
      message.error('分析失败，请重试');
    } finally {
      setIsAnalyzing(false);
    }
  };

  const getAIScoreColor = (score: number) => {
    if (score < 0.3) return '#52c41a'; // 绿色 - 很好
    if (score < 0.5) return '#faad14'; // 黄色 - 一般
    return '#ff4d4f'; // 红色 - 需要改进
  };

  const getAIScoreStatus = (score: number) => {
    if (score < 0.3) return 'success';
    if (score < 0.5) return 'warning';
    return 'error';
  };

  const getAIScoreText = (score: number) => {
    if (score < 0.3) return '人性化程度高';
    if (score < 0.5) return 'AI痕迹轻微';
    return 'AI痕迹明显';
  };

  return (
    <div className="humanized-writing-panel">
      <Card 
        title={
          <Space>
            <UserOutlined />
            <span>人性化写作设置</span>
            <Badge 
              count={`AI痕迹: ${(aiScore * 100).toFixed(0)}%`}
              style={{ 
                backgroundColor: getAIScoreColor(aiScore),
                color: '#fff'
              }}
            />
          </Space>
        }
        size="small"
      >
        {/* AI检测结果 */}
        {analysisResult && (
          <Alert
            message="内容分析结果"
            description={
              <div>
                <Row gutter={16} style={{ marginBottom: 12 }}>
                  <Col span={12}>
                    <Statistic
                      title="AI痕迹评分"
                      value={analysisResult.aiScore}
                      precision={2}
                      valueStyle={{ color: getAIScoreColor(analysisResult.aiScore) }}
                      suffix="/ 1.0"
                    />
                  </Col>
                  <Col span={12}>
                    <Statistic
                      title="人性化评分"
                      value={analysisResult.humanizationScore}
                      precision={2}
                      valueStyle={{ color: '#52c41a' }}
                      suffix="/ 1.0"
                    />
                  </Col>
                </Row>
                
                <div style={{ marginBottom: 8 }}>
                  <Text strong>检测问题：</Text>
                  {analysisResult.issues.map((issue: string, index: number) => (
                    <div key={index}>• {issue}</div>
                  ))}
                </div>
                
                <div>
                  <Text strong>优化建议：</Text>
                  {analysisResult.suggestions.map((suggestion: string, index: number) => (
                    <div key={index}>• {suggestion}</div>
                  ))}
                </div>
              </div>
            }
            type={getAIScoreStatus(analysisResult.aiScore)}
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {/* 人性化等级 */}
          <div>
            <Row justify="space-between" align="middle">
              <Col>
                <Text strong>
                  <HeartOutlined /> 人性化程度
                </Text>
              </Col>
              <Col>
                <Text type="secondary">{settings.humanizationLevel}/10</Text>
              </Col>
            </Row>
            <Slider
              min={1}
              max={10}
              value={settings.humanizationLevel}
              onChange={(value) => handleSettingChange('humanizationLevel', value)}
              marks={{
                1: '轻微',
                5: '适中', 
                10: '深度'
              }}
              tooltip={{ 
                formatter: (value) => `人性化程度: ${value}/10`
              }}
            />
          </div>

          <Divider />

          {/* 作者人格 */}
          <div>
            <Text strong style={{ marginBottom: 8, display: 'block' }}>
              <UserOutlined /> 作者人格设定
            </Text>
            <Select
              value={settings.authorPersona}
              onChange={(value) => handleSettingChange('authorPersona', value)}
              style={{ width: '100%' }}
              placeholder="选择作者人格"
            >
              {authorPersonas.map(persona => (
                <Option key={persona.value} value={persona.value}>
                  <div>
                    <div>{persona.label}</div>
                    <Text type="secondary" style={{ fontSize: '12px' }}>
                      {persona.desc}
                    </Text>
                  </div>
                </Option>
              ))}
            </Select>
          </div>

          {/* 创作情境 */}
          <div>
            <Text strong style={{ marginBottom: 8, display: 'block' }}>
              <BulbOutlined /> 创作情境
            </Text>
            <Select
              value={settings.creativeSituation}
              onChange={(value) => handleSettingChange('creativeSituation', value)}
              style={{ width: '100%' }}
              placeholder="选择创作情境"
            >
              {creativeSituations.map(situation => (
                <Option key={situation.value} value={situation.value}>
                  <div>
                    <div>{situation.label}</div>
                    <Text type="secondary" style={{ fontSize: '12px' }}>
                      {situation.desc}
                    </Text>
                  </div>
                </Option>
              ))}
            </Select>
          </div>

          <Divider />

          {/* 详细设置 */}
          <div>
            <Text strong style={{ marginBottom: 12, display: 'block' }}>
              🎨 写作风格微调
            </Text>
            
            {/* 情感深度 */}
            <div style={{ marginBottom: 16 }}>
              <Row justify="space-between" align="middle">
                <Col>
                  <Text>情感深度</Text>
                </Col>
                <Col>
                  <Text type="secondary">{settings.emotionalDepth}/10</Text>
                </Col>
              </Row>
              <Slider
                min={1}
                max={10}
                value={settings.emotionalDepth}
                onChange={(value) => handleSettingChange('emotionalDepth', value)}
                marks={{ 1: '平淡', 10: '深刻' }}
              />
            </div>

            {/* 对话自然度 */}
            <div style={{ marginBottom: 16 }}>
              <Row justify="space-between" align="middle">
                <Col>
                  <Text>对话自然度</Text>
                </Col>
                <Col>
                  <Text type="secondary">{settings.dialogueNaturalness}/10</Text>
                </Col>
              </Row>
              <Slider
                min={1}
                max={10}
                value={settings.dialogueNaturalness}
                onChange={(value) => handleSettingChange('dialogueNaturalness', value)}
                marks={{ 1: '生硬', 10: '自然' }}
              />
            </div>

            {/* 描写生动度 */}
            <div style={{ marginBottom: 16 }}>
              <Row justify="space-between" align="middle">
                <Col>
                  <Text>描写生动度</Text>
                </Col>
                <Col>
                  <Text type="secondary">{settings.descriptionVividness}/10</Text>
                </Col>
              </Row>
              <Slider
                min={1}
                max={10}
                value={settings.descriptionVividness}
                onChange={(value) => handleSettingChange('descriptionVividness', value)}
                marks={{ 1: '简洁', 10: '生动' }}
              />
            </div>
          </div>

          <Divider />

          {/* 智能检测开关 */}
          <div>
            <Text strong style={{ marginBottom: 12, display: 'block' }}>
              🤖 智能检测设置
            </Text>

            <Space direction="vertical" style={{ width: '100%' }}>
              <Row justify="space-between" align="middle">
                <Col>
                  <Tooltip title="自动检测并优化AI痕迹">
                    <Text>反AI检测模式</Text>
                  </Tooltip>
                </Col>
                <Col>
                  <Switch
                    checked={settings.antiAIMode}
                    onChange={(checked) => handleSettingChange('antiAIMode', checked)}
                    checkedChildren="开"
                    unCheckedChildren="关"
                  />
                </Col>
              </Row>

              <Row justify="space-between" align="middle">
                <Col>
                  <Tooltip title="检查章节间连贯性">
                    <Text>连贯性检查</Text>
                  </Tooltip>
                </Col>
                <Col>
                  <Switch
                    checked={settings.coherenceCheck}
                    onChange={(checked) => handleSettingChange('coherenceCheck', checked)}
                    checkedChildren="开"
                    unCheckedChildren="关"
                  />
                </Col>
              </Row>

              <Row justify="space-between" align="middle">
                <Col>
                  <Tooltip title="保持整本书风格一致">
                    <Text>风格一致性</Text>
                  </Tooltip>
                </Col>
                <Col>
                  <Switch
                    checked={settings.styleConsistency}
                    onChange={(checked) => handleSettingChange('styleConsistency', checked)}
                    checkedChildren="开"
                    unCheckedChildren="关"
                  />
                </Col>
              </Row>
            </Space>
          </div>

          <Divider />

          {/* 分析按钮 */}
          <Button
            type="primary"
            icon={<FireOutlined />}
            loading={isAnalyzing}
            onClick={analyzeCurrentContent}
            disabled={!currentContent}
            block
          >
            {isAnalyzing ? '分析中...' : '分析当前内容'}
          </Button>

          {/* AI评分显示 */}
          <Card size="small" style={{ backgroundColor: '#f6f6f6' }}>
            <Row justify="space-between" align="middle">
              <Col>
                <Text strong>当前AI痕迹评分</Text>
              </Col>
              <Col>
                <Progress
                  type="circle"
                  size={60}
                  percent={Math.round((1 - aiScore) * 100)}
                  format={() => getAIScoreText(aiScore)}
                  strokeColor={getAIScoreColor(aiScore)}
                />
              </Col>
            </Row>
          </Card>
        </Space>
      </Card>
    </div>
  );
};

export default HumanizedWritingPanel;