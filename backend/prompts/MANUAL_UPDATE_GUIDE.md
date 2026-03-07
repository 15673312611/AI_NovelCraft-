# 章纲生成第一步提示词更新指南

## 已完成的修改

1. **方法注释已更新**（第888-890行）：
   - 从"深度人设分析 + 脑洞发散"改为"文风识别 + 禁止事项 + 风格预判"

2. **新增变量**（第903-905行）：
   - 添加了 `genre`、`basicIdea`、`mainCharacters` 变量

3. **新增辅助方法** `buildStyleGuardPrompt`（约第1070行之后）：
   - 这是新的第一步提示词构建方法，包含完整的文风识别与风格守护逻辑

4. **日志输出已更新**（第1065行）：
   - 从"创意池生成完成"改为"文风分析生成完成"

5. **创建了提示词模板文件**：
   - `backend/prompts/chapter_outline_step1_style_guard.txt`

## 需要手动完成的修改

由于文件编码问题，以下修改需要手动完成：

### 在 `generateCreativeIdeasPool` 方法中（约第943-1060行）

将从 `// ========= 新版第一步：文风识别 + 禁止事项 + 风格预判 =========` 开始，
到 `// 调用AI生成创意池` 之前的所有 `prompt.append(...)` 语句，

替换为以下代码：

```java
        // ========= 新版第一步：文风识别 + 禁止事项 + 风格预判 =========
        // 使用新的辅助方法构建提示词
        String promptContent = buildStyleGuardPrompt(novel, volume, superOutline, worldView, levelAndFamily, 
                                                      genre, basicIdea, mainCharacters, progress, foreshadowSummary);
        prompt.append(promptContent);

        // 调用AI生成文风分析
        logger.info("🧠 第一步：生成文风识别与风格守护，promptLen={}", prompt.length());
```

### 同时修改日志输出（约第1055行）

将：
```java
logger.info("🧠 第一步：生成创意脑洞池，promptLen={}", prompt.length());
```

改为：
```java
logger.info("🧠 第一步：生成文风识别与风格守护，promptLen={}", prompt.length());
```

## 新提示词的核心变化

### 原来的第一步（创意脑洞池）：
- 人设"皮下"重构
- 脑洞风暴与破局方案
- 世界观与礼法规则的武器化
- 读者嗨点自检
- 输出：编剧备忘录

### 新的第一步（文风识别与风格守护）：
1. **文风DNA识别**：题材风格定位、人设风格特征、爽点与情绪设计
2. **禁止事项清单**：题材禁忌、人设禁忌、节奏禁忌、俗套禁忌
3. **本卷风格预判**：蓝图风险点扫描、剧情走向预警
4. **注意事项**：必须保持的元素、章节设计原则、一环扣一环的设计要求、反俗套设计思路

### 核心理念变化：
- 从"脑洞发散"转向"风格守护"
- 从"创意生成"转向"风险预判"
- 强调"禁止事项"和"风格护栏"
- 强调"一环扣一环"和"反俗套"
