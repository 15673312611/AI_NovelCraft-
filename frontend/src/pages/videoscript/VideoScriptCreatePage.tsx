import React, { useState } from 'react';
import { Card, Form, Input, Button, InputNumber, Typography, message, Radio, Row, Col, Switch, Space } from 'antd';
import { ArrowLeftOutlined, ThunderboltOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { videoScriptService } from '../../services/videoScriptService';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

type ScriptFormat = 'SCENE' | 'STORYBOARD' | 'NARRATION' | string;

const SCRIPT_FORMAT_OPTIONS: Array<{
  value: ScriptFormat;
  label: string;
  desc: string;
  example: string;
}> = [
  {
    value: 'SCENE',
    label: '集-场台本（真人短剧/影视）',
    desc: '按“第X集 → 场X-1/场X-2 → 内/外 + 日/夜 + 地点 → △动作 → 台词/OS/音效”组织。',
    example: `第01集 《标题》

场1-1  内  夜  地点：______
人物：A、B
道具：______
氛围：______

△（画面/动作：用镜头能拍出来的描述）
【SFX】风声（持续）
A（压低）：台词……
B：台词……

场1-2  外  夜  地点：______
△（动作/冲突升级）
A OS：内心独白（可选）

（提示：短剧拍摄落地时，可在每场补“景别/机位/道具清单/服化/备注”。）`,
  },
  {
    value: 'STORYBOARD',
    label: '分镜脚本（漫剧/动态漫）',
    desc: '按“镜头编号 → 时长/景别/运镜 → 画面 → 动效 → 音效/BGM → 台词”组织，便于动效/配音对轴。',
    example: `镜头01  时长：3s  景别：特写  运镜：静止
画面：______
动效：______
音效/BGM：【SFX】______
台词：无

镜头02  时长：5s  景别：中景  运镜：慢推
画面：______
动效：呼吸白雾（循环）
音效/BGM：【BGM】冷氛围（轻）
台词：
- 角色A：台词……
- 角色B（迟疑）：台词……

镜头03  时长：4s  景别：近景  运镜：轻微抖动
画面：______
音效/BGM：【SFX】警报滴滴（加速）
台词：
- 角色A：台词……

（提示：动态漫通常会额外标“嘴型字数/分层要素/特效层级”。）`,
  },
  {
    value: 'NARRATION',
    label: '解说口播文案（竖屏解说视频）',
    desc: '纯口播文案带时间戳，第一人称叙事，可直接念/配音，不包含拍摄指导。',
    example: `【本集标题】我在废弃医院见到了她
【时长】60秒
【解说文案】
00:00 - 00:03：凌晨三点，我被一阵剧烈的震动惊醒。
00:03 - 00:06：我猛地坐起来，发现整个房间都在晃动。
00:06 - 00:10：窗外传来尖叫声，我意识到不对劲，赶紧冲向窗边。
00:10 - 00:14：眼前的景象让我彻底愣住了——街道上，到处都是逃跑的人群。
00:14 - 00:17：这时室友冲进来，一把拉住我：“快跑！外面有东西！”
...
【结尾悬念】但我永远忘不了她最后看我的那个眼神。
【下一集引子】第二天，我们发现了一个更可怕的事实……`,
  },
];

const VideoScriptCreatePage: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      const result = await videoScriptService.create(values);
      message.success('创建成功！');

      const data = (result as any).data || result;
      if (data?.id) {
        navigate(`/video-scripts/${data.id}`);
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
        onClick={() => navigate('/video-scripts')}
        style={{ marginBottom: '16px', paddingLeft: 0 }}
      >
        返回列表
      </Button>

      <Card bordered={false} style={{ borderRadius: '16px', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <Title level={2}>
            <VideoCameraOutlined /> 一键生成短视频剧本
          </Title>
          <Paragraph type="secondary">
            输入构思，AI 自动完成：系列设定 → 系列大纲 → 每集看点 → 导语 → 循环生成每一集（含审稿/连续性分析/动态更新）
          </Paragraph>
        </div>

        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{
            targetSeconds: 60,
            episodeCount: 10,
            enableOutlineUpdate: true,
            minPassScore: 7,
            mode: 'HALF_NARRATION',
            scriptFormat: 'STORYBOARD',
          }}
        >
          <Form.Item name="title" label="剧本标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input size="large" placeholder="给你的短视频起个标题" />
          </Form.Item>

          <Form.Item
            name="idea"
            label="构思"
            rules={[{ required: true, message: '请输入构思' }]}
            help="描述越具体越好（题材、角色、冲突、反转、结局、想要的画面感）。"
          >
            <TextArea
              rows={6}
              placeholder="例如：一个穷小子为了救母亲，去给富豪当替身，结果发现富豪想让他替死..."
              showCount
              maxLength={2000}
            />
          </Form.Item>

          <Row gutter={24}>
            <Col span={12}>
              <Form.Item name="episodeCount" label="计划集数" help="可后续增加集数继续生成。">
                <InputNumber style={{ width: '100%' }} min={1} max={200} step={1} addonAfter="集" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="targetSeconds" label="每集目标时长">
                <InputNumber style={{ width: '100%' }} min={15} max={300} step={5} addonAfter="秒" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={24}>
            <Col span={12}>
              <Form.Item name="sceneCount" label="每集镜头数量" extra="可不填，默认按 3 秒一个镜头自动估算。">
                <InputNumber style={{ width: '100%' }} min={5} max={200} step={1} addonAfter="个" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="minPassScore" label="审稿通过分" help="1-10，分数越高越严格。">
                <InputNumber style={{ width: '100%' }} min={1} max={10} step={1} addonAfter="分" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="enableOutlineUpdate" label="动态更新大纲" valuePropName="checked">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item name="mode" label="输出模式">
            <Radio.Group>
              <Radio value="HALF_NARRATION">半解说（解说 + 人物对话）</Radio>
              <Radio value="PURE_NARRATION">纯解说（全程旁白）</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="scriptFormat"
            label="剧本格式"
            rules={[{ required: true, message: '请选择剧本格式' }]}
            help="决定“每集正文”的组织方式。"
          >
            <Radio.Group>
              <Radio value="SCENE">集-场台本（真人短剧/影视）</Radio>
              <Radio value="STORYBOARD">分镜脚本（漫剧/动态漫）</Radio>
              <Radio value="NARRATION">解说口播文案（竖屏解说视频）</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item shouldUpdate={(prev, cur) => prev.scriptFormat !== cur.scriptFormat} noStyle>
            {() => {
              const fmt = (form.getFieldValue('scriptFormat') || 'STORYBOARD') as ScriptFormat;
              const opt = SCRIPT_FORMAT_OPTIONS.find((o) => o.value === fmt) || SCRIPT_FORMAT_OPTIONS[1];

              const copyExample = async () => {
                try {
                  await navigator.clipboard.writeText(opt.example);
                  message.success('已复制格式示例');
                } catch {
                  message.warning('复制失败，请手动选择文本复制');
                }
              };

              return (
                <Form.Item label={`格式示例（${opt.label}）`} help={opt.desc + '（仅示意结构，不必照抄剧情内容）'}>
                  <TextArea rows={10} value={opt.example} readOnly style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }} />
                  <Space style={{ marginTop: 8 }}>
                    <Button size="small" onClick={copyExample}>
                      复制示例
                    </Button>
                  </Space>
                </Form.Item>
              );
            }}
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
              创建系列
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default VideoScriptCreatePage;
