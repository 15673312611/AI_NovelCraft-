import React, { useState, useEffect } from 'react';
import { Card, Steps, Button, message, Spin, Form, Input, Select, Typography, Space, Divider } from 'antd';
import { CheckCircleOutlined, LoadingOutlined, UserOutlined, RobotOutlined } from '@ant-design/icons';
import { worldViewBuilderService, WorldViewBuilder, WorldViewStep } from '../../services/worldViewBuilderService';
import './WorldViewBuilder.css';

const { Title, Text, Paragraph } = Typography;
const { Step } = Steps;
const { TextArea } = Input;
const { Option } = Select;

interface WorldViewBuilderProps {
  builderId?: number;
  onComplete?: (builder: WorldViewBuilder) => void;
}

export const WorldViewBuilderComponent: React.FC<WorldViewBuilderProps> = ({ 
  builderId, 
  onComplete 
}) => {
  const [builder, setBuilder] = useState<WorldViewBuilder | null>(null);
  const [steps, setSteps] = useState<WorldViewStep[]>([]);
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (builderId) {
      loadBuilder();
    }
  }, [builderId]);

  const loadBuilder = async () => {
    if (!builderId) return;
    
    setLoading(true);
    try {
      const [builderData, stepsData] = await Promise.all([
        worldViewBuilderService.getWorldViewBuilder(builderId),
        worldViewBuilderService.getBuilderSteps(builderId)
      ]);
      
      setBuilder(builderData);
      setSteps(stepsData);
      
      // 找到当前进行中的步骤
      const currentStepIndex = stepsData.findIndex(step => step.status === 'IN_PROGRESS');
      setCurrentStep(currentStepIndex >= 0 ? currentStepIndex : 0);
    } catch (error) {
      message.error('加载构建器失败');
    } finally {
      setLoading(false);
    }
  };

  const handleStepAnswer = async (stepId: number, answer: string) => {
    setLoading(true);
    try {
      await worldViewBuilderService.updateStepAnswer(stepId, { answer });
      message.success('答案已保存');
      
      // 重新加载数据
      await loadBuilder();
      
      // 移动到下一步
      if (currentStep < steps.length - 1) {
        setCurrentStep(currentStep + 1);
      }
    } catch (error) {
      message.error('保存答案失败');
    } finally {
      setLoading(false);
    }
  };

  const handleComplete = async () => {
    if (!builder) return;
    
    setLoading(true);
    try {
      const completedBuilder = await worldViewBuilderService.completeWorldViewBuilder(builder.id);
      message.success('世界观构建完成！');
      onComplete?.(completedBuilder);
    } catch (error) {
      message.error('完成构建失败');
    } finally {
      setLoading(false);
    }
  };

  const renderStepContent = (step: WorldViewStep) => {
    switch (step.stepType) {
      case 'QUESTION':
        return (
          <Card className="step-card">
            <Title level={4}>{step.name}</Title>
            <Paragraph>{step.description}</Paragraph>
            <Divider />
            <Paragraph strong>{step.question}</Paragraph>
            
            {step.options && (
              <Select
                style={{ width: '100%', marginBottom: 16 }}
                placeholder="请选择答案"
                onChange={(value) => {
                  if (step.userAnswer !== value) {
                    handleStepAnswer(step.id, value);
                  }
                }}
                value={step.userAnswer}
              >
                {step.options.split(',').map((option, index) => (
                  <Option key={index} value={option.trim()}>
                    {option.trim()}
                  </Option>
                ))}
              </Select>
            )}
            
            {step.status === 'COMPLETED' && (
              <div className="step-completed">
                <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />
                <Text type="success">已完成</Text>
              </div>
            )}
          </Card>
        );

      case 'USER_INPUT':
        return (
          <Card className="step-card">
            <Title level={4}>{step.name}</Title>
            <Paragraph>{step.description}</Paragraph>
            <Divider />
            <Paragraph strong>{step.question}</Paragraph>
            
            <Form layout="vertical" initialValues={{ answer: step.userAnswer }}>
              <Form.Item name="answer">
                <TextArea
                  rows={4}
                  placeholder="请输入您的答案..."
                  onChange={(e) => {
                    if (step.userAnswer !== e.target.value) {
                      handleStepAnswer(step.id, e.target.value);
                    }
                  }}
                />
              </Form.Item>
            </Form>
            
            {step.status === 'COMPLETED' && (
              <div className="step-completed">
                <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />
                <Text type="success">已完成</Text>
              </div>
            )}
          </Card>
        );

      case 'AI_SUGGESTION':
        return (
          <Card className="step-card">
            <Title level={4}>
              <RobotOutlined style={{ marginRight: 8 }} />
              {step.name}
            </Title>
            <Paragraph>{step.description}</Paragraph>
            <Divider />
            
            {step.status === 'PENDING' && (
              <div className="ai-loading">
                <Spin indicator={<LoadingOutlined style={{ fontSize: 24 }} spin />} />
                <Text>AI正在生成世界观建议...</Text>
              </div>
            )}
            
            {step.status === 'COMPLETED' && step.aiSuggestion && (
              <div className="ai-suggestion">
                <Paragraph strong>AI生成的世界观：</Paragraph>
                <div className="suggestion-content">
                  {step.aiSuggestion.split('\n').map((line, index) => (
                    <Paragraph key={index}>{line}</Paragraph>
                  ))}
                </div>
              </div>
            )}
            
            {step.status === 'FAILED' && (
              <div className="ai-failed">
                <Text type="danger">AI生成失败，请重试</Text>
              </div>
            )}
          </Card>
        );

      case 'REVIEW':
        return (
          <Card className="step-card">
            <Title level={4}>
              <UserOutlined style={{ marginRight: 8 }} />
              {step.name}
            </Title>
            <Paragraph>{step.description}</Paragraph>
            <Divider />
            
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button type="primary" size="large" onClick={handleComplete}>
                确认完成世界观构建
              </Button>
              <Text type="secondary">
                请仔细检查AI生成的世界观是否符合您的期望，确认后将完成构建。
              </Text>
            </Space>
          </Card>
        );

      default:
        return <div>未知步骤类型</div>;
    }
  };

  if (loading && !builder) {
    return (
      <div className="world-view-builder-loading">
        <Spin size="large" />
        <Text>加载中...</Text>
      </div>
    );
  }

  if (!builder || steps.length === 0) {
    return (
      <Card>
        <Text>未找到世界观构建器或步骤</Text>
      </Card>
    );
  }

  return (
    <div className="world-view-builder">
      <Card>
        <Title level={3}>世界观构建器</Title>
        <Text type="secondary">{builder.name}</Text>
        
        <Divider />
        
        <Steps current={currentStep} direction="vertical" size="small">
          {steps.map((step) => (
            <Step
              key={step.id}
              title={step.name}
              description={step.description}
              status={
                step.status === 'COMPLETED' ? 'finish' :
                step.status === 'IN_PROGRESS' ? 'process' :
                step.status === 'FAILED' ? 'error' : 'wait'
              }
            />
          ))}
        </Steps>
        
        <Divider />
        
        <div className="step-content">
          {renderStepContent(steps[currentStep])}
        </div>
        
        <Divider />
        
        <div className="step-navigation">
          <Space>
            <Button
              disabled={currentStep === 0}
              onClick={() => setCurrentStep(currentStep - 1)}
            >
              上一步
            </Button>
            <Button
              type="primary"
              disabled={currentStep === steps.length - 1}
              onClick={() => setCurrentStep(currentStep + 1)}
            >
              下一步
            </Button>
          </Space>
        </div>
      </Card>
    </div>
  );
}; 