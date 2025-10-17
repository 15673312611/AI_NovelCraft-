# AI配置改造说明

## 改造概述

本次改造将AI配置从后端移至前端，支持多种AI服务商（DeepSeek、通义千问、Kimi），用户可以在前端设置页面配置不同的AI服务，配置信息保存在浏览器本地缓存中。

## 改造内容

### 1. 后端改造

#### 1.1 新增DTO类
- **AIConfigRequest.java** - AI配置请求DTO
  - 包含字段：provider（服务商）、apiKey（API密钥）、model（模型名称）、baseUrl（API基础URL）
  - 提供配置验证和默认URL获取功能

#### 1.2 修改的配置文件
- **application.yml** - 移除AI配置部分
- **application-test.yml** - 移除AI配置部分

#### 1.3 修改的Service类
- **AITraceRemovalService.java** - AI消痕服务
  - 移除AIClientConfig依赖
  - 所有方法改为接收AIConfigRequest参数
  
- **AdjectiveMiningService.java** - 形容词挖掘服务
  - 移除AIClientConfig依赖
  - mineTerms方法改为接收AIConfigRequest参数
  
- **NovelCraftAIService.java** - 小说创作AI服务
  - 移除AIClientConfig依赖
  - executeStreamingChapterWriting方法改为接收AIConfigRequest参数
  - callStreamingAIWithContext方法改为接收AIConfigRequest参数

#### 1.4 修改的Controller类
- **AIController.java** - AI工具控制器
  - removeAITraceStream：从请求体解析aiConfig参数
  - removeAITrace：从请求体解析aiConfig参数
  
- **AdjectiveMiningController.java** - 形容词挖掘控制器
  - mine：从请求体解析aiConfig参数
  
- **NovelCraftController.java** - 小说创作控制器
  - executeStreamingChapterWriting：从请求体解析aiConfig参数

### 2. 前端改造

#### 2.1 新增类型定义
- **types/aiConfig.ts** - AI配置类型定义
  - AIConfig接口
  - AIProvider接口
  - AI_PROVIDERS常量（DeepSeek、通义千问、Kimi配置）

#### 2.2 新增工具函数
- **utils/aiConfigStorage.ts** - AI配置存储工具
  - saveAIConfig：保存配置到localStorage
  - loadAIConfig：从localStorage读取配置
  - clearAIConfig：清除配置
  - isAIConfigValid：验证配置有效性
  
- **utils/aiRequest.ts** - AI请求工具
  - withAIConfig：为请求体添加AI配置
  - getAIConfigOrThrow：获取配置或抛出错误
  - checkAIConfig：检查配置是否已设置

#### 2.3 新增服务类
- **services/aiService.ts** - AI服务封装
  - 提供统一的AI接口调用
  - 自动附加AI配置到请求

#### 2.4 修改的页面
- **SettingsPage.tsx** - 设置页面
  - 完全重构，支持多AI服务商配置
  - 支持选择服务商、输入API Key、选择模型
  - 配置保存到浏览器缓存
  - 提供服务商说明
  
- **VolumeWritingStudio.tsx** - 写作工作室
  - 导入AI配置工具函数
  - AI写作和AI消痕调用时自动附加AI配置
  - 添加配置检查

## 使用说明

### 前端使用

1. **配置AI服务**
   - 访问设置页面
   - 选择AI服务商（DeepSeek、通义千问、Kimi或自定义）
   - 输入API密钥
   - 选择模型
   - 点击保存配置

2. **使用AI功能**
   - 配置保存后，所有AI功能会自动使用保存的配置
   - 如果未配置，调用AI接口时会提示"请先在设置页面配置AI服务"

### API调用示例

#### 前端调用示例
```typescript
// 方法1：使用withAIConfig工具函数
const requestBody = withAIConfig({
  content: '需要处理的内容'
});
await api.post('/ai/remove-trace', requestBody);

// 方法2：使用aiService封装
import aiService from '@/services/aiService';
await aiService.removeTrace(content);
```

#### 后端接口示例
```java
@PostMapping("/some-ai-endpoint")
public Result<?> someAIEndpoint(@RequestBody Map<String, Object> request) {
    // 解析AI配置
    AIConfigRequest aiConfig = new AIConfigRequest();
    if (request.get("aiConfig") instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, String> aiConfigMap = (Map<String, String>) request.get("aiConfig");
        aiConfig.setProvider(aiConfigMap.get("provider"));
        aiConfig.setApiKey(aiConfigMap.get("apiKey"));
        aiConfig.setModel(aiConfigMap.get("model"));
        aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
    }
    
    if (!aiConfig.isValid()) {
        return Result.error("AI配置无效");
    }
    
    // 调用AI服务
    someAIService.doSomething(aiConfig);
}
```

## 支持的AI服务商

### DeepSeek
- 默认URL：https://api.deepseek.com
- 推荐模型：deepseek-chat, deepseek-coder, deepseek-v3-1-250821-thinking
- 特点：高性价比，适合长文本生成

### 通义千问
- 默认URL：https://dashscope.aliyuncs.com/compatible-mode/v1
- 推荐模型：qwen-turbo, qwen-plus, qwen-max, qwen-max-longcontext
- 特点：阿里云服务，OpenAI兼容接口，支持多种规格
- 说明：使用DashScope的OpenAI兼容模式

### Kimi（月之暗面）
- 默认URL：https://api.moonshot.cn
- 推荐模型（仅包含128K+长上下文模型）：
  - **K2系列（最新高性能）**：
    - kimi-k2-turbo-preview（推荐，262K上下文）
    - kimi-k2-0905-preview（262K上下文）
    - kimi-k2-0711-preview（131K上下文）
  - **Latest系列（通用模型）**：
    - kimi-latest（自动选择）
    - kimi-latest-128k（131K上下文）
  - **长思考模型**：
    - kimi-thinking-preview（深度推理，131K上下文）
  - **V1系列（经典模型，兼容）**：
    - moonshot-v1-128k（128K上下文）
    - moonshot-v1-auto（自动选择）
- 特点：专注于长上下文场景，K2系列提供最高262K上下文窗口，适合处理超长文本和复杂推理
- 官方文档：https://platform.moonshot.cn/docs

## 安全说明

1. **配置存储**
   - 所有AI配置仅保存在浏览器本地localStorage中
   - 不会上传到服务器数据库
   - 更换浏览器或清除缓存需要重新配置

2. **API密钥安全**
   - API密钥通过HTTPS传输
   - 后端不保存API密钥
   - 建议使用有额度限制的API密钥

## 注意事项

1. 首次使用需要先在设置页面配置AI服务
2. 不同浏览器需要分别配置
3. 清除浏览器缓存会删除配置
4. 切换AI服务商需要重新配置API密钥

## 迁移指南

对于已有的后端AI调用代码，需要进行以下修改：

1. **Controller层**
   - 添加AIConfigRequest导入
   - 从请求体解析aiConfig参数
   - 验证配置有效性
   - 将aiConfig传递给Service层

2. **Service层**
   - 移除AIClientConfig依赖
   - 修改方法签名，接收AIConfigRequest参数
   - 使用aiConfig.getEffectiveBaseUrl()获取URL
   - 使用aiConfig.getApiKey()获取密钥
   - 使用aiConfig.getModel()获取模型

3. **前端调用**
   - 使用withAIConfig包装请求体
   - 或使用aiService封装的方法
   - 调用前检查配置有效性

## 改造完成的接口

- ✅ `/api/ai/remove-trace-stream` - AI消痕（流式）
- ✅ `/api/ai/remove-trace` - AI消痕（非流式）
- ✅ `/api/ai-adjectives/mine` - 形容词挖掘
- ✅ `/api/novel-craft/{novelId}/write-chapter-stream` - 章节写作（流式）

## 待改造的接口

其他使用AI的接口需要按照相同的模式进行改造：
- 大纲生成接口
- 角色生成接口
- 其他AI辅助写作接口

如有需要，可以参考已改造的接口进行类似修改。
