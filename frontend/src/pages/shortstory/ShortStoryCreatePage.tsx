import React, { useState } from 'react';
import { Card, Form, Input, Button, InputNumber, Switch, Typography, Row, Col, message } from 'antd';
import { ArrowLeftOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { shortStoryService } from '../../services/shortStoryService';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

const ShortStoryCreatePage: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      const result = await shortStoryService.create({
        ...values,
        minPassScore: 7, // 默认7分
      });
      message.success('创建成功！');
      
      // 跳转到工作流页面，由用户手动启动
      const novelData = (result as any).data || result;
      if (novelData?.id) {
        navigate(`/short-stories/${novelData.id}`);
      }
    } catch (error: any) {
      message.error(error.message || '创建失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: '24px', maxWidth: '800px', margin: '0 auto' }}>
      <Button 
        type="link" 
        icon={<ArrowLeftOutlined />} 
        onClick={() => navigate('/short-stories')}
        style={{ marginBottom: '16px', paddingLeft: 0 }}
      >
        返回列表
      </Button>

      <Card bordered={false} style={{ borderRadius: '16px', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <Title level={2}>一键生成短篇小说</Title>
          <Paragraph type="secondary">
            输入你的创意，AI 将全自动完成：大纲生成 → 章节拆分 → 逐章写作 → 智能审稿 → 动态修正
          </Paragraph>
        </div>

        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{
            targetWords: 30000,
            chapterCount: 10,
            enableOutlineUpdate: true,
          }}
        >
          <Form.Item
            name="title"
            label="小说标题"
            rules={[{ required: true, message: '请输入标题' }]}
          >
            <Input size="large" placeholder="给你的故事起个名字" />
          </Form.Item>

          <Form.Item
            name="idea"
            label="创意构思"
            rules={[{ required: true, message: '请输入你的创意' }]}
            help="描述越详细，生成效果越好。可以包含：核心梗、主角设定、结局走向等。"
          >
            <TextArea 
              rows={6} 
              placeholder="例如：一个能听到物品心声的古董店老板，意外收到了一个来自未来的怀表，卷入了一场跨越时空的谋杀案..." 
              showCount
              maxLength={2000}
            />
          </Form.Item>

          <Row gutter={24}>
            <Col span={12}>
              <Form.Item
                name="targetWords"
                label="目标字数"
              >
                <InputNumber<number>
                  style={{ width: '100%' }}
                  min={5000}
                  max={100000}
                  step={1000}
                  formatter={(value) => (value == null ? '' : `${value}`).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                  parser={(value) => Number((value ?? '').replace(/\$\s?|(,*)/g, ''))}
                  addonAfter="字"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="chapterCount"
                label="章节数量"
              >
                <InputNumber 
                  style={{ width: '100%' }} 
                  min={3} 
                  max={30} 
                  addonAfter="章"
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="enableOutlineUpdate"
            label="动态大纲"
            valuePropName="checked"
            extra="开启后，AI会在写作过程中根据剧情发展自动调整后续大纲，使故事更连贯。"
          >
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item style={{ marginTop: '24px' }}>
            <Button 
              type="primary" 
              htmlType="submit" 
              size="large" 
              block
              loading={loading}
              icon={<ThunderboltOutlined />}
              style={{ height: '48px', fontSize: '18px', borderRadius: '8px' }}
            >
              创建短篇
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ShortStoryCreatePage;
