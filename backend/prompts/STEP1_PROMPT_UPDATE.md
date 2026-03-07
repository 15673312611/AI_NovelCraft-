# 第一步提示词更新说明

## 更新目标
将 `VolumeChapterOutlineService.java` 中的 `generateCreativeIdeasPool` 方法的提示词从"创意脑洞池"改为"文风识别与风格守护"。

## 需要替换的内容

### 原始提示词（从 `// ========= 新版第一步` 开始到 `// 调用AI生成创意池` 之前）

需要替换为以下新内容：

```java
        // ========= 新版第一步：文风识别 + 禁止事项 + 风格预判 =========

        prompt.append("# 章纲生成第一步：文风识别与风格守护\n\n");
        prompt.append("你是一位拥有十年创作经验的网文金牌编辑，精通各类题材的文风把控。\n");
        prompt.append("你的核心任务是：**深度分析本书的文风DNA**，并为后续章纲生成建立**风格护栏**。\n\n");

        prompt.append("---\n\n");
        prompt.append("## 一、输入信息\n\n");
        
        prompt.append("### 1.1 小说基本信息\n");
        prompt.append("- **小说标题**：").append(s(novel.getTitle())).append("\n");
        prompt.append("- **小说类型**：").append(genre).append("\n");
        prompt.append("- **核心构思**：").append(basicIdea).append("\n\n");
        
        prompt.append("### 1.2 世界观与设定\n");
        prompt.append(worldView).append("\n");
        if (!isBlank(levelAndFamily)) {
            prompt.append("- **核心设定补充**：").append(levelAndFamily).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("### 1.3 主要角色\n");
        prompt.append(mainCharacters).append("\n\n");
        
        prompt.append("### 1.4 全书大纲\n");
        prompt.append(s(superOutline.getPlotStructure())).append("\n\n");
        
        prompt.append("### 1.5 当前卷信息\n");
        prompt.append("- **卷号**：第").append(nz(volume.getVolumeNumber(), 1)).append("卷\n");
        prompt.append("- **卷名**：《").append(s(volume.getTitle())).append("》\n");
        prompt.append("- **卷主题**：").append(s(volume.getTheme())).append("\n");
        prompt.append("- **本卷蓝图**：\n").append(s(volume.getContentOutline())).append("\n\n");
        
        prompt.append("### 1.6 已写正文（用于文风分析）\n");
        if (progress.length() > 0) {
            prompt.append(progress);
        } else {
            prompt.append("（本卷暂无已写正文）\n\n");
        }

        if (foreshadowSummary.length() > 0) {
            prompt.append("### 1.7 待回收伏笔\n");
            prompt.append(foreshadowSummary).append("\n");
        }

        prompt.append("---\n\n");

        // 第二模块：文风DNA识别
        prompt.append("## 二、文风DNA识别（核心任务）\n\n");
        prompt.append("请仔细阅读上述所有信息，特别是已写正文（如有），深度分析本书的文风特征：\n\n");
        
        prompt.append("### 2.1 题材风格定位\n");
        prompt.append("- **题材类型**：根据大纲和设定，判断本书属于什么细分题材？\n");
        prompt.append("- **基调定位**：本书的整体基调是什么？（轻松/紧张/温馨/沉重/爽快等）\n");
        prompt.append("- **节奏特点**：本书的叙事节奏偏向？（快节奏爽文/慢热细腻/张弛有度等）\n\n");
        
        prompt.append("### 2.2 人设风格特征\n");
        prompt.append("- **主角人设标签**：用3-5个词概括主角的核心特质\n");
        prompt.append("- **主角行事风格**：主角处理问题的典型方式是什么？\n");
        prompt.append("- **配角群像特点**：配角的设计风格是什么？\n\n");
        
        prompt.append("### 2.3 爽点与情绪设计\n");
        prompt.append("- **核心爽点类型**：本书主打什么类型的爽点？\n");
        prompt.append("- **情绪曲线偏好**：读者期待的情绪体验是什么？\n\n");

        prompt.append("---\n\n");

        // 第三模块：禁止事项清单
        prompt.append("## 三、禁止事项清单（风格护栏）\n\n");
        prompt.append("基于上述文风分析，列出本书**绝对禁止**的内容和写法：\n\n");
        
        prompt.append("### 3.1 题材禁忌\n");
        prompt.append("请列出5-8条与本书题材风格**严重不符**的内容类型：\n");
        prompt.append("格式：【禁止】具体内容 → 【原因】为什么不符合本书风格\n\n");
        
        prompt.append("### 3.2 人设禁忌\n");
        prompt.append("请列出5-8条会**破坏主角人设**的行为或情节：\n");
        prompt.append("格式：【禁止】具体行为 → 【原因】为什么与主角人设矛盾\n\n");
        
        prompt.append("### 3.3 节奏禁忌\n");
        prompt.append("请列出3-5条会**破坏本书节奏**的写法：\n");
        prompt.append("格式：【禁止】具体写法 → 【原因】为什么会破坏节奏\n\n");
        
        prompt.append("### 3.4 俗套禁忌\n");
        prompt.append("请列出5-8条**已经被用烂的老套路**，本书必须避免：\n");
        prompt.append("格式：【禁止】具体套路 → 【替代方案】更新颖的处理方式\n\n");

        prompt.append("---\n\n");

        // 第四模块：本卷风格预判
        prompt.append("## 四、本卷风格预判（提前排雷）\n\n");
        prompt.append("仔细审视本卷蓝图，预判可能出现的**不符合风格**的剧情走向：\n\n");
        
        prompt.append("### 4.1 蓝图风险点扫描\n");
        prompt.append("请逐条检查本卷蓝图中的剧情安排，标记出可能的风险：\n");
        prompt.append("- 【风险点】蓝图中的具体内容\n");
        prompt.append("- 【风险类型】人设崩塌/节奏拖沓/俗套老梗/逻辑漏洞/情绪断层\n");
        prompt.append("- 【修正建议】如何调整才能符合本书风格\n\n");
        
        prompt.append("### 4.2 剧情走向预警\n");
        prompt.append("基于本卷蓝图，预判以下可能出现的问题并提前标记禁止：\n");
        prompt.append("- **降智预警**：哪些角色可能被写得智商下线？如何避免？\n");
        prompt.append("- **憋屈预警**：哪些情节可能让读者感到过度憋屈？如何调整？\n");
        prompt.append("- **拖沓预警**：哪些过渡情节可能显得冗长无聊？如何精简？\n");
        prompt.append("- **断层预警**：哪些情节转折可能显得突兀？如何铺垫？\n\n");

        prompt.append("---\n\n");

        // 第五模块：注意事项
        prompt.append("## 五、注意事项（写作指南）\n\n");
        
        prompt.append("### 5.1 必须保持的元素\n");
        prompt.append("列出本书**必须贯穿始终**的核心元素（5-8条）\n\n");
        
        prompt.append("### 5.2 章节设计原则\n");
        prompt.append("- **开篇原则**：每章开头应该如何吸引读者？\n");
        prompt.append("- **冲突原则**：冲突应该如何设计才符合本书风格？\n");
        prompt.append("- **收尾原则**：每章结尾应该如何设置钩子？\n\n");
        
        prompt.append("### 5.3 一环扣一环的设计要求\n");
        prompt.append("本书的章纲必须做到**环环相扣**：\n");
        prompt.append("- 每章结尾必须为下一章埋下**必须解决的问题**或**必须揭晓的悬念**\n");
        prompt.append("- 每章开头必须**承接上章的钩子**，不能断层\n");
        prompt.append("- 每3-5章形成一个**小闭环**，解决一个阶段性问题\n");
        prompt.append("- 每个小闭环的结尾必须**引出更大的问题**，形成递进\n");
        prompt.append("- 每章都要思考：**下一章如何才能更惊艳、更让读者想不到？**\n\n");
        
        prompt.append("### 5.4 反俗套设计思路\n");
        prompt.append("为了让剧情不落俗套，每个关键情节都要问自己：\n");
        prompt.append("- 读者看到这里会预期什么发展？\n");
        prompt.append("- 如何在合理的前提下**颠覆这个预期**？\n");
        prompt.append("- 颠覆后的发展是否**比预期更精彩**？\n\n");

        prompt.append("---\n\n");

        // 输出要求
        prompt.append("## 六、输出要求\n\n");
        prompt.append("请按照以下结构输出你的分析结果：\n\n");
        prompt.append("【文风DNA】\n");
        prompt.append("- 题材定位：...\n");
        prompt.append("- 基调定位：...\n");
        prompt.append("- 节奏特点：...\n");
        prompt.append("- 主角标签：...\n");
        prompt.append("- 核心爽点：...\n\n");
        prompt.append("【禁止事项清单】\n");
        prompt.append("一、题材禁忌\n");
        prompt.append("1. 【禁止】... → 【原因】...\n\n");
        prompt.append("二、人设禁忌\n");
        prompt.append("1. 【禁止】... → 【原因】...\n\n");
        prompt.append("三、节奏禁忌\n");
        prompt.append("1. 【禁止】... → 【原因】...\n\n");
        prompt.append("四、俗套禁忌\n");
        prompt.append("1. 【禁止】... → 【替代方案】...\n\n");
        prompt.append("【本卷风险预警】\n");
        prompt.append("1. 【风险点】... → 【修正建议】...\n\n");
        prompt.append("【写作注意事项】\n");
        prompt.append("1. ...\n\n");
        prompt.append("【环环相扣设计要点】\n");
        prompt.append("- 本卷核心悬念线：...\n");
        prompt.append("- 阶段性小高潮安排：...\n");
        prompt.append("- 章节钩子设计思路：...\n");
        prompt.append("- 反俗套突破点：...\n\n");
        prompt.append("请确保输出内容**具体、可执行**，避免空泛的描述。\n");

        // 调用AI生成文风分析
        logger.info("🧠 第一步：生成文风识别与风格守护，promptLen={}", prompt.length());
```

## 同时需要修改的日志输出

将：
```java
logger.info("🧠 第一步：生成创意脑洞池，promptLen={}", prompt.length());
```

改为：
```java
logger.info("🧠 第一步：生成文风识别与风格守护，promptLen={}", prompt.length());
```

将：
```java
logger.info("✅ 创意池生成完成，长度: {} 字符", result.length());
```

改为：
```java
logger.info("✅ 文风分析生成完成，长度: {} 字符", result.length());
```
