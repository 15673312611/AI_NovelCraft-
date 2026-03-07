# 灵感点系统实施指南

## 概述

本文档描述了灵感点（Credit）系统的设计和实施方案，用于管理AI功能的使用计费。

## 已完成的工作

### 1. 数据库设计 (`database/credit_system.sql`)

新增表：
- `user_credits` - 用户灵感点余额表
- `credit_transactions` - 灵感点交易记录表
- `system_ai_config` - 系统AI配置表

扩展表：
- `ai_model` - 添加了输入/输出分开计费字段

### 2. 后端实体类

- `UserCredit.java` - 用户灵感点余额实体
- `CreditTransaction.java` - 交易记录实体
- `SystemAIConfig.java` - 系统配置实体
- `AIModel.java` - 更新了计费相关字段

### 3. 后端Repository

- `UserCreditRepository.java` - 用户灵感点数据访问
- `CreditTransactionRepository.java` - 交易记录数据访问
- `SystemAIConfigRepository.java` - 系统配置数据访问

### 4. 后端Service

- `CreditService.java` - 灵感点核心服务（余额查询、扣费、充值等）
- `SystemAIConfigService.java` - 系统AI配置服务
- `AIConfigService.java` - AI配置服务（从系统配置构建AIConfigRequest）
- `AICallService.java` - AI调用服务（统一处理调用和扣费）

### 5. 后端Controller

- `CreditController.java` - 客户端灵感点API

### 6. 后端异常类

- `InsufficientCreditsException.java` - 灵感点不足异常

### 7. Admin管理端后端

实体类：
- `UserCredit.java`
- `CreditTransaction.java`
- `AIModel.java`
- `SystemAIConfig.java`

Mapper：
- `UserCreditMapper.java`
- `CreditTransactionMapper.java`
- `AIModelMapper.java`
- `SystemAIConfigMapper.java`

Service：
- `AdminCreditService.java`
- `AdminAIModelService.java`

Controller：
- `AdminCreditController.java`
- `AdminAIModelController.java`

### 8. Admin管理端前端

服务：
- `adminCreditService.ts`
- `adminAIModelService.ts`

页面：
- `Credits/index.tsx` - 灵感点管理页面
- `AIModels/index.tsx` - AI模型配置页面

路由和菜单已更新。

### 9. 客户端前端

服务：
- `creditService.ts` - 灵感点服务

组件：
- `CreditBalance.tsx` - 灵感点余额显示组件

页面：
- `SettingsPage.tsx` - 重写为显示灵感点信息（移除AI配置）

工具：
- `aiRequest.ts` - 简化版，不再需要前端配置

## 待完成的工作

### 1. 后端AI服务改造

需要修改以下服务，使用`AIConfigService`获取系统配置并处理扣费：

- `AIWritingService.java` - 核心写作服务
- `AIPolishService.java` - 润色服务
- `AIProofreadService.java` - 纠错服务
- `AITraceRemovalService.java` - 消痕服务
- `AIManuscriptReviewService.java` - 审稿服务
- `AIStreamlineService.java` - 精简服务
- `AISmartSuggestionService.java` - 智能建议服务
- `NovelCraftAIService.java` - AI Agent服务

改造方式：
```java
@Autowired
private AIConfigService aiConfigService;

// 在调用AI前
public String someAIMethod(String content, String taskDescription) {
    // 1. 获取系统AI配置
    AIConfigRequest aiConfig = aiConfigService.getDefaultAIConfig();
    
    // 2. 检查灵感点
    aiConfigService.checkCurrentUserCredits(content, 2000);
    
    // 3. 调用AI
    String result = callAI(content, aiConfig);
    
    // 4. 扣除灵感点（需要从响应中获取实际token数）
    aiConfigService.deductCurrentUserCredits(inputTokens, outputTokens, 
        aiConfig.getModel(), taskDescription);
    
    return result;
}
```

### 2. 前端改造

需要移除以下文件中对AI配置的依赖：

- 所有使用`withAIConfig`的地方，改为直接发送请求
- 移除`aiConfigStorage.ts`的使用
- 在需要的地方添加`CreditBalance`组件显示余额

### 3. 数据库迁移

执行`database/credit_system.sql`脚本：
```bash
mysql -u root -p ai_novel < database/credit_system.sql
```

### 4. 配置初始化

在Admin管理端配置：
1. 设置各AI服务商的API Key
2. 配置模型计费标准
3. 设置新用户赠送灵感点数量

## API接口

### 客户端API

| 接口 | 方法 | 描述 |
|------|------|------|
| /credits/balance | GET | 获取当前用户灵感点信息 |
| /credits/transactions | GET | 获取交易记录 |
| /credits/estimate | POST | 预估消费 |
| /credits/check | POST | 检查余额是否足够 |
| /credits/models | GET | 获取可用模型列表 |
| /credits/models/default | GET | 获取默认模型 |

### Admin API

| 接口 | 方法 | 描述 |
|------|------|------|
| /credits/users | GET | 获取用户灵感点列表 |
| /credits/users/{userId} | GET | 获取用户灵感点详情 |
| /credits/users/{userId}/recharge | POST | 充值 |
| /credits/users/{userId}/gift | POST | 赠送 |
| /credits/users/{userId}/adjust | POST | 调整余额 |
| /credits/transactions | GET | 获取交易记录 |
| /credits/statistics | GET | 获取统计数据 |
| /credits/model-usage | GET | 获取模型使用统计 |
| /ai-models | GET/POST | 模型管理 |
| /ai-models/{id} | GET/PUT/DELETE | 模型CRUD |
| /ai-models/{id}/set-default | POST | 设置默认模型 |
| /ai-models/api-configs | GET | 获取API配置 |
| /ai-models/api-configs/{provider} | POST | 更新API配置 |
| /ai-models/system-settings | GET/POST | 系统设置 |

## 计费规则

1. 按token计费，输入和输出分开计价
2. 计费公式：`cost = (inputTokens / 1000) * inputPrice + (outputTokens / 1000) * outputPrice`
3. 预扣费机制：调用前预估并冻结金额，完成后按实际使用扣除
4. 余额不足时拒绝调用，返回友好提示

## 注意事项

1. API Key等敏感信息需要加密存储
2. 交易记录需要保证原子性
3. 需要处理并发扣费的情况
4. 建议添加日消费限额功能
5. 需要监控异常消费情况
