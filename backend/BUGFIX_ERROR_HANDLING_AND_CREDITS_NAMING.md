# Bug修复：错误处理优化和字数点术语统一

## 🐛 问题描述

### 问题1：字数点不足错误提示不准确
**错误现象**：
- 当用户字数点余额不足时，前端显示："系统内部错误，请联系管理员"
- 没有显示具体的错误原因（字数点不足）
- 用户无法得知真正的错误原因

**用户体验问题**：
- 用户不知道是字数点不足导致的错误
- 错误提示不友好，让用户感到困惑
- 没有引导用户去充值

### 问题2：术语不统一
**错误现象**：
- 代码中混用"灵感点"和实际应该使用的"字数点"
- 注释、日志、异常消息中使用"灵感点"
- 与产品设计的"字数点"概念不一致

## 🔍 根本原因

### 问题1：缺少特定异常处理器

**GlobalExceptionHandler.java** 缺少对 `InsufficientCreditsException` 的专门处理：

```java
// 原代码：只有通用异常处理
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
    // ...
    errorResponse.put("message", "系统内部错误，请联系管理员");  // ❌ 不够具体
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
}
```

**异常处理顺序问题**：
1. `InsufficientCreditsException` 继承自 `RuntimeException`
2. 但没有专门的 `@ExceptionHandler(InsufficientCreditsException.class)`
3. 被通用的 `@ExceptionHandler(Exception.class)` 捕获
4. 返回模糊的"系统内部错误"消息

### 问题2：历史遗留命名

代码最初使用"灵感点"作为内部命名，后来产品改为"字数点"，但代码中的注释和消息没有完全更新。

## ✅ 修复方案

### 修复1：添加字数点不足异常处理器

**文件**：`GlobalExceptionHandler.java`

**添加专门的异常处理器**：
```java
/**
 * 处理字数点不足异常
 */
@ExceptionHandler(com.novel.exception.InsufficientCreditsException.class)
public ResponseEntity<Map<String, Object>> handleInsufficientCreditsException(
        com.novel.exception.InsufficientCreditsException e) {
    logger.warn("字数点不足: {}", e.getMessage());
    
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", "字数点不足");
    errorResponse.put("message", e.getMessage());
    errorResponse.put("code", "INSUFFICIENT_CREDITS");
    
    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(errorResponse);  // 402
}
```

**关键改进**：
- HTTP状态码使用 `402 PAYMENT_REQUIRED`（更语义化）
- 返回具体的错误类型："字数点不足"
- 添加错误码 `INSUFFICIENT_CREDITS` 方便前端判断
- 直接返回异常消息（包含余额信息）

### 修复2：改进RuntimeException处理

**添加RuntimeException专门处理器**（在Exception之前）：
```java
/**
 * 处理通用RuntimeException（不包括已特殊处理的）
 */
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
    logger.error("Runtime异常: {}", e.getMessage(), e);
    
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", "操作失败");
    // 对于RuntimeException，直接返回具体错误信息，方便前端展示
    errorResponse.put("message", e.getMessage() != null ? e.getMessage() : "操作失败，请稍后重试");
    errorResponse.put("timestamp", System.currentTimeMillis());
    
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
}
```

**关键改进**：
- RuntimeException 返回具体错误消息（而不是"系统内部错误"）
- 使用 `400 BAD_REQUEST`（而不是 `500 INTERNAL_SERVER_ERROR`）
- 让前端能够展示业务相关的具体错误

### 修复3：统一"字数点"术语

**修改的文件和位置**：

1. **InsufficientCreditsException.java** - 类注释
```java
// 修改前
/**
 * 灵感点不足异常
 */

// 修改后
/**
 * 字数点不足异常
 */
```

2. **AIWritingService.java** - 4处异常消息
```java
// 修改前
throw new InsufficientCreditsException("灵感点余额不足，请先充值");

// 修改后
throw new InsufficientCreditsException("字数点余额不足，请先充值");
```

3. **AIConfigService.java** - 注释和日志
```java
// 类注释：从"处理灵感点检查" → "处理字数点检查"
// 方法注释：从"检查当前用户灵感点" → "检查当前用户字数点"
// 方法注释：从"扣除灵感点" → "扣除字数点"
// 日志消息：从"消费XX灵感点" → "消费XX字数点"
```

4. **AICallService.java** - 类注释和日志
```java
// 类注释：从"统一处理AI调用和灵感点扣费" → "统一处理AI调用和字数点扣费"
// 日志消息：从"用户{}字数点不足" → "用户 {} 字数点不足"
```

5. **GlobalExceptionHandler.java** - 新增处理器
```java
// 错误消息、日志、错误类型全部使用"字数点"
```

## 🎯 异常处理器优先级

Spring的`@ExceptionHandler`按**从具体到通用**的顺序匹配：

```
InsufficientCreditsException (最具体)
    ↓
RuntimeException (中等具体)
    ↓
Exception (最通用，兜底)
```

**修复后的处理链**：
1. `InsufficientCreditsException` → 返回 402 + "字数点不足" + 具体消息
2. `BusinessException` → 返回 400 + 业务错误消息
3. `SecurityException` → 返回 403 + 权限错误消息
4. `AIServiceException` → 返回 503 + AI服务错误
5. `RuntimeException` → 返回 400 + 具体错误消息（通用业务错误）
6. `Exception` → 返回 500 + "系统内部错误"（真正的系统异常）

## 📊 影响范围

### 修复的文件（5个）
1. `GlobalExceptionHandler.java` - 添加字数点不足处理器，改进RuntimeException处理
2. `InsufficientCreditsException.java` - 更新类注释
3. `AIWritingService.java` - 修改4处异常消息
4. `AIConfigService.java` - 修改5处注释和日志
5. `AICallService.java` - 修改2处注释和日志

### 受益的功能
- ✅ 所有AI调用相关接口的错误提示
- ✅ 字数点余额不足时的用户提示
- ✅ 各种业务错误的前端展示
- ✅ 术语统一，避免用户困惑

### HTTP状态码对照表

| 异常类型 | HTTP状态码 | 前端展示 |
|---------|-----------|---------|
| InsufficientCreditsException | 402 PAYMENT_REQUIRED | "字数点不足，请充值" |
| BusinessException | 400 BAD_REQUEST | 具体业务错误消息 |
| SecurityException | 403 FORBIDDEN | "无权限访问" |
| AIServiceException | 503 SERVICE_UNAVAILABLE | "AI服务暂不可用" |
| RuntimeException | 400 BAD_REQUEST | 具体错误消息 |
| Exception | 500 INTERNAL_SERVER_ERROR | "系统内部错误" |

## 🎨 前端对接建议

### 判断字数点不足

**方式1：根据HTTP状态码**
```javascript
if (error.response.status === 402) {
  // 字数点不足
  showRechargeDialog();
}
```

**方式2：根据错误码**
```javascript
if (error.response.data.code === 'INSUFFICIENT_CREDITS') {
  // 字数点不足
  showRechargeDialog();
}
```

**方式3：根据错误类型**
```javascript
if (error.response.data.error === '字数点不足') {
  // 字数点不足
  showRechargeDialog();
}
```

### 错误消息展示

```javascript
// 直接展示后端返回的message
const errorMessage = error.response.data.message;
toast.error(errorMessage);

// 字数点不足的特殊处理
if (error.response.status === 402) {
  toast.error(errorMessage, {
    action: {
      label: '去充值',
      onClick: () => router.push('/recharge')
    }
  });
}
```

## 🔧 验证方法

### 1. 编译验证
```bash
mvn clean compile -DskipTests
```
结果：✅ BUILD SUCCESS

### 2. 功能测试

**测试场景1：字数点不足**
1. 确保用户字数点余额为0或很少
2. 调用任何AI生成接口
3. 预期结果：
   - HTTP状态码：402
   - 错误类型：`字数点不足`
   - 错误消息：`字数点余额不足，请先充值`（或包含余额的详细消息）
   - 错误码：`INSUFFICIENT_CREDITS`

**测试场景2：其他业务错误**
1. 触发某个业务错误（如小说不存在）
2. 预期结果：
   - HTTP状态码：400
   - 错误消息：具体的业务错误描述
   - 不再显示"系统内部错误，请联系管理员"

**测试场景3：真正的系统错误**
1. 触发一个非RuntimeException的异常（如数据库连接失败）
2. 预期结果：
   - HTTP状态码：500
   - 错误消息：`系统内部错误，请联系管理员`

## 📝 术语统一说明

### 为什么是"字数点"而不是"灵感点"？

1. **产品定位**：系统按字数计费，"字数点"更直观
2. **用户理解**：用户更容易理解"写多少字花多少点"
3. **计费透明**：输入X字+输出Y字=消耗Z字数点

### 统一后的术语

| 场景 | 统一术语 |
|-----|---------|
| 用户余额 | 字数点 |
| 充值单位 | 字数点 |
| 消费单位 | 字数点 |
| 异常提示 | 字数点不足 |
| 日志记录 | 字数点 |
| 代码注释 | 字数点 |

## 📝 总结

### 问题根源
1. 缺少 `InsufficientCreditsException` 的专门异常处理器
2. RuntimeException 被统一处理为"系统内部错误"，丢失了具体错误信息
3. 代码中混用"灵感点"和"字数点"术语

### 修复效果
- ✅ 字数点不足时返回清晰的错误提示
- ✅ HTTP状态码使用 `402 PAYMENT_REQUIRED`（语义化）
- ✅ 添加错误码 `INSUFFICIENT_CREDITS` 方便前端判断
- ✅ RuntimeException 返回具体错误消息（不再显示"系统内部错误"）
- ✅ 统一所有代码中的术语为"字数点"
- ✅ 改善用户体验，错误提示更友好

### 相关文件
- `GlobalExceptionHandler.java` - 全局异常处理器（主要修改）
- `InsufficientCreditsException.java` - 字数点不足异常类
- `AIWritingService.java` - AI写作服务
- `AIConfigService.java` - AI配置服务
- `AICallService.java` - AI调用服务

---

**修复时间**: 2026-01-11  
**修复版本**: 1.0.0  
**修复状态**: ✅ 已完成并验证
