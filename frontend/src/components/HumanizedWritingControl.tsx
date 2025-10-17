import React, { useState } from 'react';
import { Card, Button, Space, Typography, Switch, Slider, InputNumber, Form, Select, message } from 'antd';
import { 
  UserOutlined, 
  RobotOutlined, 
  SettingOutlined,
  BulbOutlined,
  EditOutlined,
  MagicOutlined
} from '@ant-design/icons';

const { Title, Text } = Typography;
const { Option } = Select;

interface HumanizedWritingControlProps {
  onSettingsChange?: (settings: any) => void;
  initialSettings?: any;
}

const HumanizedWritingControl: React.FC<HumanizedWritingControlProps> = ({
  onSettingsChange,
  initialSettings = {}
}) => {
  const [form] = Form.useForm();
  const [isEnabled, setIsEnabled] = useState(initialSettings.enabled || false);
  const [writingStyle, setWritingStyle] = useState(initialSettings.writingStyle || 'balanced');
  const [creativityLevel, setCreativityLevel] = useState(initialSettings.creativityLevel || 50);
  const [humanizationLevel, setHumanizationLevel] = useState(initialSettings.humanizationLevel || 70);

  const handleSettingsChange = (values: any) => {
    const settings = {
      enabled: isEnabled,
      writingStyle,
      creativityLevel,
      humanizationLevel,
      ...values
    };
    
    if (onSettingsChange) {
      onSettingsChange(settings);
    }
    
    message.success('写作设置已更新');
  };

  const writingStyles = [
    { value: 'balanced', label: '平衡型', description: 'AI辅助与人工创作平衡' },
    { value: 'ai_enhanced', label: 'AI增强', description: 'AI提供创意，人工主导' },
    { value: 'human_centric', label: '人工主导', description: '人工创作，AI辅助润色' },
    { value: 'collaborative', label: '协作模式', description: 'AI与人工深度协作' }
  ];

  const handleStyleChange = (style: string) => {
    setWritingStyle(style);
    handleSettingsChange({ writingStyle: style });
  };

  const handleCreativityChange = (value: number) => {
    setCreativityLevel(value);
    handleSettingsChange({ creativityLevel: value });
  };

  const handleHumanizationChange = (value: number) => {
    setHumanizationLevel(value);
    handleSettingsChange({ humanizationLevel: value });
  };

  const toggleEnabled = (checked: boolean) => {
    setIsEnabled(checked);
    handleSettingsChange({ enabled: checked });
  };

  return (
    <Card 
      title={
        <Space>
          <UserOutlined />
          <span>人性化写作控制</span>
        </Space>
      }
      className="humanized-writing-control"
      extra={
        <Switch
          checked={isEnabled}
          onChange={toggleEnabled}
          checkedChildren="启用"
          unCheckedChildren="禁用"
        />
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSettingsChange}
        initialValues={initialSettings}
      >
        <Form.Item label="写作风格模式">
          <Select
            value={writingStyle}
            onChange={handleStyleChange}
            placeholder="选择写作风格"
          >
            {writingStyles.map(style => (
              <Option key={style.value} value={style.value}>
                <div>
                  <Text strong>{style.label}</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: '12px' }}>
                    {style.description}
                  </Text>
                </div>
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item label="创意水平">
          <div style={{ padding: '0 16px' }}>
            <Slider
              min={0}
              max={100}
              value={creativityLevel}
              onChange={handleCreativityChange}
              marks={{
                0: '保守',
                25: '传统',
                50: '平衡',
                75: '创新',
                100: '大胆'
              }}
            />
            <div style={{ textAlign: 'center', marginTop: 8 }}>
              <Text type="secondary">当前值: {creativityLevel}</Text>
            </div>
          </div>
        </Form.Item>

        <Form.Item label="人性化程度">
          <div style={{ padding: '0 16px' }}>
            <Slider
              min={0}
              max={100}
              value={humanizationLevel}
              onChange={handleHumanizationChange}
              marks={{
                0: '机械化',
                25: '标准化',
                50: '自然',
                75: '人性化',
                100: '个性化'
              }}
            />
            <div style={{ textAlign: 'center', marginTop: 8 }}>
              <Text type="secondary">当前值: {humanizationLevel}</Text>
            </div>
          </div>
        </Form.Item>

        <Form.Item>
          <Space>
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => form.submit()}
            >
              应用设置
            </Button>
            <Button 
              icon={<BulbOutlined />}
              onClick={() => {
                // 重置为默认设置
                setWritingStyle('balanced');
                setCreativityLevel(50);
                setHumanizationLevel(70);
                form.resetFields();
                handleSettingsChange({
                  writingStyle: 'balanced',
                  creativityLevel: 50,
                  humanizationLevel: 70
                });
              }}
            >
              重置默认
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <div style={{ marginTop: 16, padding: 16, backgroundColor: '#f5f5f5', borderRadius: 6 }}>
        <Text type="secondary">
          <BulbOutlined /> 提示：人性化写作控制可以帮助你调整AI写作的风格和创意水平，
          让AI更好地配合你的创作需求。
        </Text>
      </div>
    </Card>
  );
};

export default HumanizedWritingControl; 