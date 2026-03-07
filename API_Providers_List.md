# AI API 中转站与视频模型价格汇总 (Sora 2 & Veo 3.1)

本文档汇总了提供 Sora 2 和 Veo 3 系列视频模型 API 的中转站及相关平台。这些站点大多基于 NewAPI/OneAPI 或类似架构部署，提供聚合的模型服务。

> **注意**: 价格和可用性可能会随时间变化，建议在使用前访问具体网站确认。

## 🚀 核心推荐：高性价比中转站 (NewAPI/OneAPI 风格)

这些站点通常提供最具竞争力的价格，是许多下游站点的上游供应商。

| 站点名称 | 网址 | Sora 2 价格 (参考) | Veo 3.1 价格 (参考) | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| **老张 API (Laozhang.ai)** | https://api.laozhang.ai | ~$0.15 / 次 (约 ¥1.0) | ~$0.15 / 次 | 经常被提及的源头之一，价格非常有竞争力，支持高并发。 |
| **API 易 (APIYi)** | https://api.apiyi.com | ~$0.15 / 次 | ~$0.15 / 次 | 提供国内直连优化，支持支付宝/微信支付。 |
| **CometAPI** | https://www.cometapi.com | ~$0.1 / 次 (促销价) | 支持 | 聚合了所有主流 AI 模型。 |
| **Apixo** | https://apixo.ai | 按量计费 (较便宜) | 支持 | 宣称比市场价便宜 50%。 |
| **Juhe API (聚核)** | https://www.juheapi.com | 动态定价 | 支持 | 聚合平台，提供多种模型接入。 |
| **AIFreeAPI** | https://www.aifreeapi.com | - | - | 提供大量 API 评测和推荐资讯，不仅是中转，也是信息源。 |

## 📹 视频模型专用/垂直站点

这些站点专注于视频生成，界面可能更加图形化，但也提供 API 或类似积分的充值服务。

| 站点名称 | 网址 | 价格模式 | 特色 |
| :--- | :--- | :--- | :--- |
| **Sora2Hub** | https://www.sora2hub.org | 订阅制 ($7.99/月起) | 包含 Sora 2, Veo 3.1, Kling 等多个模型。 |
| **Sora2API** | https://sora2api.org | $6.9 / 10 个视频 | 专注于 Sora 2 模型。 |
| **Veo3API** | https://veo3api.com | 按 Credit 计费 | 专注于 Google Veo 3.1 模型。 |
| **VoraVideo** | https://voravideo.com | $0.15 / 视频 | 号称 3 分钟生成 4K 视频，聚合多个模型。 |
| **SotaVideo** | https://sotavideo.ai | 积分制 | 聚合 Sora 2, Veo 3, Seedance 等。 |
| **Veo3Gen** | https://www.veo3gen.app | $0.06 / 秒 (优惠价) | 专注于 Veo 3.1 的低价访问。 |
| **Veo3o1** | https://veo3o1.com | 积分制 | 提供免费试用 Credit。 |
| **Veo3-1.ai** | https://veo3-1.ai | 订阅制 | 专注于 Veo 3.1。 |
| **Sora2AI.ai** | https://sora2ai.ai | 免费试用/订阅 | 提供 Sora 2 的早期访问入口。 |
| **MaxVideoAI** | https://maxvideoai.com | 聚合比较 | 可以在一个工作区比较所有视频模型。 |
| **JSON to Video** | https://jsontovideo.org | 订阅制 | 支持通过 JSON 结构化提示词调用 Veo/Sora。 |
| **FlowVideo** | https://flowvideo.ai | $29.99/月 | 提供 Veo 3.1 和 Sora 2 的访问。 |
| **AIVeed** | https://aiveed.io | $0.60 / 视频 | 提供按次付费的视频生成服务。 |

## 🛠️ 其他 API 及工具类站点

虽然不完全是“模型广场”，但提供了相关的 API 服务，常用于视频编辑和处理。

*   **Cutout.pro**: https://www.cutout.pro (图像/视频背景移除 API)
*   **GhostCut (JollyToday)**: https://jollytoday.com (视频去字幕/翻译 API)
*   **BackgroundCut**: https://backgroundcut.co (背景移除 API)
*   **Pixelcut**: https://www.pixelcut.ai (图像编辑 API)
*   **CapCut API (非官方)**: https://capcutapi.apifox.cn (剪映/CapCut 功能 API 化)

## 💡 搜索技巧：如何找到更多同类站点

如果你需要继续寻找更多此类“NewAPI”部署的站点，可以使用以下 Google 搜索指令（Dorks）：

1.  **寻找 NewAPI/OneAPI 面板**:
    *   `intext:"Powered by One API" "sora" "pricing"`
    *   `intext:"Powered by New API" "veo" "充值"`
    *   `inurl:/pricing "模型广场" "sora"`
    *   `title:"One API" "Sora" "Veo"`

2.  **寻找特定模型分销商**:
    *   `"sora-2" "veo-3.1" "按量付费"`
    *   `"sora_video2" API` (这是特定渠道的模型命名代码，搜索这个能直接找到用同一套源的站点)
    *   `"veo-3.1-fast" API price`

3.  **寻找相关讨论**:
    *   在 GitHub 搜索 `NewAPI` 或 `OneAPI` 的 Issue 区，常有用户讨论可用的上游。
    *   在 Linux.do 等技术论坛搜索“API中转”或“Sora API”。

## 📊 价格参考 (Sora 2 & Veo 3)

根据收集到的数据，目前市场上的主流价格区间如下：

*   **Sora 2 (Standard/Pro)**:
    *   **源头/大户价**: 约 $0.10 - $0.15 / 次 (视频生成)
    *   **普通中转价**: 约 ¥2.0 - ¥5.0 / 次
    *   **官方价 (参考)**: 按秒计费，约 $0.10/秒 (即 $1.0/10秒)

*   **Veo 3.1**:
    *   **源头/大户价**: 约 $0.15 / 次
    *   **官方价 (参考)**: 约 $0.40 - $0.75 / 秒 (取决于配置)

*建议优先选择支持“按次计费”而非“按秒计费”的中转站，通常能节省大量成本。*
