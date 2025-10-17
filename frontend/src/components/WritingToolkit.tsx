import React, { useState } from 'react';
import { 
  Card, Button, Form, Input, Select, Typography, Space, 
  Modal, Tabs, List, Tag, Collapse, Tooltip, Spin, Alert, App
} from 'antd';
import { 
  BulbOutlined, TranslationOutlined, EditOutlined, SearchOutlined,
  ThunderboltOutlined, HeartOutlined, UserOutlined, BookOutlined,
  ExperimentOutlined, RocketOutlined
} from '@ant-design/icons';
import './WritingToolkit.css';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;
const { Option } = Select;
const { Panel } = Collapse;

interface WritingToolkitProps {
  currentContent: string;
  onContentUpdate: (content: string) => void;
  volumeInfo?: any;
}

interface AITool {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  category: 'content' | 'character' | 'plot' | 'style' | 'research';
  inputs: {
    name: string;
    type: 'text' | 'textarea' | 'select';
    placeholder?: string;
    options?: string[];
    required?: boolean;
  }[];
}

const WritingToolkit: React.FC<WritingToolkitProps> = ({ 
  currentContent, 
  onContentUpdate, 
  volumeInfo 
}) => {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [selectedTool, setSelectedTool] = useState<AITool | null>(null);
  const [toolResults, setToolResults] = useState<any[]>([]);
  
  const [form] = Form.useForm();

  // AI工具定义
  const aiTools: AITool[] = [
    {
      id: 'expand_scene',
      name: '场景扩写',
      description: '为简单描述生成丰富的场景细节',
      icon: <EditOutlined />,
      category: 'content',
      inputs: [
        { name: 'scene', type: 'textarea', placeholder: '简单描述一个场景...', required: true },
        { name: 'style', type: 'select', options: ['唯美', '紧张', '温馨', '神秘', '激烈'], required: true }
      ]
    },
    {
      id: 'dialogue_enhance',
      name: '对话优化',
      description: '让平淡的对话更生动有趣',
      icon: <TranslationOutlined />,
      category: 'content',
      inputs: [
        { name: 'dialogue', type: 'textarea', placeholder: '原始对话内容...', required: true },
        { name: 'emotion', type: 'select', options: ['愤怒', '喜悦', '悲伤', '紧张', '温柔', '调皮'], required: true }
      ]
    },
    {
      id: 'character_generator',
      name: '角色生成器',
      description: '创建有血有肉的角色档案',
      icon: <UserOutlined />,
      category: 'character',
      inputs: [
        { name: 'role', type: 'text', placeholder: '角色定位（如：主角、反派、配角）', required: true },
        { name: 'genre', type: 'select', options: ['现代都市', '古代武侠', '科幻未来', '奇幻魔法', '悬疑推理'] }
      ]
    },
    {
      id: 'conflict_creator',
      name: '冲突制造机',
      description: '为情节增加戏剧冲突',
      icon: <ThunderboltOutlined />,
      category: 'plot',
      inputs: [
        { name: 'situation', type: 'textarea', placeholder: '当前情节状况...', required: true },
        { name: 'intensity', type: 'select', options: ['轻微', '中等', '激烈', '爆炸性'], required: true }
      ]
    },
    {
      id: 'emotion_amplifier',
      name: '情感放大器',
      description: '增强文字的情感感染力',
      icon: <HeartOutlined />,
      category: 'style',
      inputs: [
        { name: 'text', type: 'textarea', placeholder: '需要增强情感的文字...', required: true },
        { name: 'target_emotion', type: 'select', options: ['感动', '紧张', '兴奋', '忧伤', '温暖'], required: true }
      ]
    },
    {
      id: 'plot_twist',
      name: '剧情转折',
      description: '生成意想不到的剧情转折',
      icon: <ExperimentOutlined />,
      category: 'plot',
      inputs: [
        { name: 'current_plot', type: 'textarea', placeholder: '当前剧情发展...', required: true },
        { name: 'twist_type', type: 'select', options: ['身份反转', '隐藏真相', '意外事件', '角色背叛', '时间倒错'] }
      ]
    },
    {
      id: 'rhythm_control',
      name: '节奏控制',
      description: '调整文本的写作节奏',
      icon: <RocketOutlined />,
      category: 'style',
      inputs: [
        { name: 'content', type: 'textarea', placeholder: '需要调整节奏的内容...', required: true },
        { name: 'target_rhythm', type: 'select', options: ['加快节奏', '放慢节奏', '制造悬念', '营造氛围'], required: true }
      ]
    },
    {
      id: 'world_building',
      name: '世界观构建',
      description: '丰富故事的世界观设定',
      icon: <BookOutlined />,
      category: 'research',
      inputs: [
        { name: 'world_type', type: 'select', options: ['现实世界', '架空古代', '未来科幻', '奇幻魔法', '平行宇宙'], required: true },
        { name: 'focus_aspect', type: 'select', options: ['政治制度', '经济体系', '文化习俗', '地理环境', '科技水平'] }
      ]
    }
  ];

  // 按类别分组工具
  const toolsByCategory = {
    content: aiTools.filter(tool => tool.category === 'content'),
    character: aiTools.filter(tool => tool.category === 'character'),
    plot: aiTools.filter(tool => tool.category === 'plot'),
    style: aiTools.filter(tool => tool.category === 'style'),
    research: aiTools.filter(tool => tool.category === 'research')
  };

  const categoryLabels = {
    content: '📝 内容创作',
    character: '👥 角色设计',
    plot: '🎭 情节构建',
    style: '🎨 风格调整',
    research: '🔍 世界构建'
  };

  // 打开工具弹窗
  const handleOpenTool = (tool: AITool) => {
    setSelectedTool(tool);
    setModalVisible(true);
    form.resetFields();
  };

  // 执行AI工具
  const handleExecuteTool = async (values: any) => {
    if (!selectedTool) return;
    
    setLoading(true);
    try {
      // 这里应该调用相应的AI服务
      const result = await executeAITool(selectedTool.id, values);
      
      // 添加到结果历史
      const newResult = {
        id: Date.now(),
        toolName: selectedTool.name,
        input: values,
        output: result,
        timestamp: new Date().toLocaleString()
      };
      
      setToolResults(prev => [newResult, ...prev]);
      setModalVisible(false);
      message.success(`${selectedTool.name}执行成功！`);
      
      // 如果工具产生了可插入的内容，询问是否插入
      if (result.insertableContent) {
        Modal.confirm({
          title: '插入生成的内容？',
          content: (
            <div>
              <Paragraph>生成的内容：</Paragraph>
              <TextArea 
                value={result.insertableContent} 
                rows={4} 
                readOnly 
                style={{ marginTop: 8 }}
              />
            </div>
          ),
          onOk() {
            // 在当前内容后追加
            onContentUpdate(currentContent + '\n\n' + result.insertableContent);
          }
        });
      }
      
    } catch (error: any) {
      message.error(error.message || `${selectedTool.name}执行失败`);
    } finally {
      setLoading(false);
    }
  };

  // 模拟AI工具执行（实际项目中应该调用真实的AI API）
  const executeAITool = async (toolId: string, input: any): Promise<any> => {
    // 模拟API调用延迟
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    // 根据工具类型返回模拟结果
    switch (toolId) {
      case 'expand_scene':
        return {
          insertableContent: `【扩写结果】基于"${input.scene}"生成的${input.style}风格场景描述：\n\n微风轻抚过湖面，泛起层层涟漪。夕阳西下，金辉洒向大地，将整个世界染成温暖的橘色。远山如黛，近水如镜，构成了一幅绝美的画卷...`,
          analysis: '场景扩写完成，增加了环境描写和氛围渲染'
        };
        
      case 'dialogue_enhance':
        return {
          insertableContent: `【优化对话】\n"你真的要这样做吗？"她的声音微微颤抖，眼中闪烁着不安的光芒。\n"这是我唯一的选择。"他缓缓转身，语气中带着难以察觉的痛苦。\n"但是...我们还有其他办法的，不是吗？"她向前一步，试图抓住他的手臂。`,
          analysis: '对话优化完成，增加了情感层次和肢体动作描写'
        };
        
      case 'character_generator':
        return {
          character: {
            name: '林墨轩',
            age: 25,
            personality: '外表冷漠但内心炽热，有强烈的正义感',
            background: '出身普通家庭，通过努力成为优秀的律师',
            abilities: ['敏锐的洞察力', '出色的辩论技巧', '坚强的意志力'],
            weaknesses: ['过于执着', '不善表达情感', '容易钻牛角尖']
          },
          analysis: '角色档案生成完成，包含了丰富的性格特征和背景设定'
        };
        
      default:
        return {
          insertableContent: `【${toolId}】生成的内容示例...`,
          analysis: `${toolId}工具执行完成`
        };
    }
  };

  // 应用工具结果
  const handleApplyResult = (result: any) => {
    if (result.output.insertableContent) {
      onContentUpdate(currentContent + '\n\n' + result.output.insertableContent);
      message.success('内容已插入到写作区域');
    }
  };

  return (
    <div className="writing-toolkit">
      <Card title="AI写作工具包" className="toolkit-card">
        <Tabs 
          defaultActiveKey="tools" 
          size="small"
          items={[
            {
              key: 'tools',
              label: '工具列表',
              children: (
                <Collapse 
                  defaultActiveKey={['content']} 
                  ghost 
                  size="small"
                  className="toolkit-collapse"
                >
                  {Object.entries(toolsByCategory).map(([category, tools]) => (
                    <Panel 
                      header={categoryLabels[category as keyof typeof categoryLabels]} 
                      key={category}
                    >
                      <div className="tools-grid">
                        {tools.map(tool => (
                          <Card 
                            key={tool.id}
                            size="small" 
                            className="tool-card"
                            hoverable
                            onClick={() => handleOpenTool(tool)}
                          >
                            <div className="tool-header">
                              {tool.icon}
                              <Text strong>{tool.name}</Text>
                            </div>
                            <Paragraph className="tool-description">
                              {tool.description}
                            </Paragraph>
                          </Card>
                        ))}
                      </div>
                    </Panel>
                  ))}
                </Collapse>
              )
            },
            {
              key: 'history',
              label: `历史记录 (${toolResults.length})`,
              children: (
                toolResults.length === 0 ? (
                  <div className="empty-history">
                    <Text type="secondary">暂无使用记录</Text>
                  </div>
                ) : (
                  <List
                    dataSource={toolResults}
                    renderItem={(result: any) => (
                      <List.Item
                        actions={[
                          <Button 
                            type="link" 
                            size="small"
                            onClick={() => handleApplyResult(result)}
                          >
                            应用
                          </Button>
                        ]}
                      >
                        <List.Item.Meta
                          title={
                            <Space>
                              <Text strong>{result.toolName}</Text>
                              <Text type="secondary" style={{ fontSize: '12px' }}>
                                {result.timestamp}
                              </Text>
                            </Space>
                          }
                          description={
                            <div>
                              <Text type="secondary">
                                {result.output.analysis || '工具执行完成'}
                              </Text>
                            </div>
                          }
                        />
                      </List.Item>
                    )}
                  />
                )
              )
            }
          ]}
        />
      </Card>

      {/* 工具执行弹窗 */}
      <Modal
        title={selectedTool ? `${selectedTool.name} - ${selectedTool.description}` : ''}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        confirmLoading={loading}
        width={600}
      >
        {selectedTool && (
          <Form
            form={form}
            layout="vertical"
            onFinish={handleExecuteTool}
          >
            {selectedTool.inputs.map(input => (
              <Form.Item
                key={input.name}
                name={input.name}
                label={input.placeholder}
                rules={input.required ? [{ required: true, message: '此字段为必填项' }] : []}
              >
                {input.type === 'textarea' ? (
                  <TextArea
                    rows={4}
                    placeholder={input.placeholder}
                    showCount
                    maxLength={500}
                  />
                ) : input.type === 'select' ? (
                  <Select placeholder={input.placeholder}>
                    {input.options?.map(option => (
                      <Option key={option} value={option}>{option}</Option>
                    ))}
                  </Select>
                ) : (
                  <Input placeholder={input.placeholder} />
                )}
              </Form.Item>
            ))}

            {currentContent && (
              <Alert
                message="当前写作内容会作为上下文参考"
                description={`已写字数：${currentContent.length}字`}
                type="info"
                showIcon
                style={{ marginTop: 16 }}
              />
            )}
          </Form>
        )}
      </Modal>
    </div>
  );
};

export default WritingToolkit;