package com.novel.service;

import com.novel.domain.entity.Novel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 网文专用提示词服务
 * 专门为网络小说创作优化的AI提示词管理
 */
@Service
public class WebNovelPromptService {
    
    @Autowired
    private CharacterManagementService characterManagementService;

    /**
     * 获取网文风格的写作提示词
     * 
     * @param novel 小说信息
     * @param chapterPlan 章节计划
     * @param wordCount 目标字数
     * @return 网文风格提示词
     */
    public String getWebNovelWritingPrompt(Novel novel, Map<String, Object> chapterPlan, Integer wordCount) {
        String genrePrompt = getGenreSpecificPrompt(novel.getGenre());
        String coolPointsPrompt = getCoolPointsPrompt(novel.getGenre());
        String pacePrompt = getPaceControlPrompt();
        
        return String.format(
            "你是【网文创作AI】，专门写作符合起点、晋江、飞卢等平台风格的网络小说。\\n\\n" +
            
            "## 📚 网文核心理念\\n" +
            "- **爽感第一**: 每章都要有让读者爽的点\\n" +
            "- **节奏紧凑**: 快节奏，避免拖沓\\n" +
            "- **钩子结尾**: 章末必须有悬念，让读者想看下一章\\n" +
            "- **读者导向**: 时刻考虑读者的阅读体验\\n\\n" +
            
            "## 🎯 当前任务\\n" +
            "- **小说**: 《%s》(%s类)\\n" +
            "- **章节**: 第%d章《%s》\\n" +
            "- **类型**: %s\\n" +
            "- **目标字数**: %d字(网文标准长度)\\n" +
            "- **核心事件**: %s\\n\\n" +
            
            "%s\\n\\n" +  // 类型特色提示词
            
            "## ⚡ 网文写作要求\\n" +
            "### 1. 节奏控制\\n" +
            "- 开头要有吸引力，直接切入冲突或爽点\\n" +
            "- 中间要有起伏，不能平铺直叙\\n" +
            "- 结尾要有钩子，制造悬念或期待\\n\\n" +
            
            "### 2. 对话技巧\\n" +
            "- 多用对话推进情节，减少大段独白\\n" +
            "- 对话要符合角色身份和性格\\n" +
            "- 适当加入内心OS，但不要过多\\n\\n" +
            
            "### 3. 描写风格\\n" +
            "- 简洁直接，避免过于文艺的修辞\\n" +
            "- 重点突出，不要面面俱到\\n" +
            "- 适当的细节描写增强代入感\\n\\n" +
            
            "%s\\n\\n" +  // 爽点设计
            
            "%s\\n\\n" +  // 节奏控制
            
            "## 📝 输出格式\\n" +
            "```\\n" +
            "### 第%d章：%s\\n\\n" +
            "[正文内容 - %d字左右]\\n\\n" +
            "---\\n" +
            "【本章要点】\\n" +
            "- 爽点：[本章的爽点是什么]\\n" +
            "- 推进：[剧情如何推进]\\n" +
            "- 钩子：[章末悬念设置]\\n" +
            "```\\n\\n" +
            
            "**重要**: 一定要写出让读者想继续看下去的内容！",
            
            novel.getTitle(), novel.getGenre(),
            (Integer) chapterPlan.get("chapterNumber"), chapterPlan.get("title"),
            chapterPlan.get("type"), wordCount, chapterPlan.get("coreEvent"),
            genrePrompt, coolPointsPrompt, pacePrompt,
            (Integer) chapterPlan.get("chapterNumber"), chapterPlan.get("title"), wordCount
        );
    }

    /**
     * 获取网文风格的大纲生成提示词
     */
    public String getWebNovelOutlinePrompt(Novel novel, String basicIdea) {
        return String.format(
            "你是【网文大纲策划师】，专门为网络小说设计大纲。\\n\\n" +
            
            "## 📖 小说信息\\n" +
            "- 标题：%s\\n" +
            "- 类型：%s\\n" +
            "- 基本构思：%s\\n\\n" +
            
            "## 🎯 网文大纲设计原则\\n" +
            "1. **黄金三章法则**: 前3章要抓住读者\\n" +
            "2. **爽点布局**: 每10章左右要有一个大爽点\\n" +
            "3. **节奏把控**: 快慢结合，张弛有度\\n" +
            "4. **悬念管理**: 解决一个悬念，埋下新的悬念\\n" +
            "5. **角色成长**: 主角要有明显的成长轨迹\\n\\n" +
            
            "%s\\n\\n" +  // 类型特色
            
            "## 📋 大纲结构要求\\n" +
            "```json\\n" +
            "{\\n" +
            "  \\\"overallStructure\\\": {\\n" +
            "    \\\"totalVolumes\\\": 3,\\n" +
            "    \\\"estimatedChapters\\\": 300,\\n" +
            "    \\\"targetWords\\\": 600000\\n" +
            "  },\\n" +
            "  \\\"volumePlanning\\\": [\\n" +
            "    {\\n" +
            "      \\\"volumeNumber\\\": 1,\\n" +
            "      \\\"title\\\": \\\"卷名\\\",\\n" +
            "      \\\"theme\\\": \\\"核心主题\\\",\\n" +
            "      \\\"chapters\\\": \\\"1-100\\\",\\n" +
            "      \\\"mainConflict\\\": \\\"主要冲突\\\",\\n" +
            "      \\\"coolPoints\\\": [\\\"爽点1\\\", \\\"爽点2\\\"],\\n" +
            "      \\\"climax\\\": \\\"高潮设置\\\"\\n" +
            "    }\\n" +
            "  ],\\n" +
            "  \\\"characterSystem\\\": {\\n" +
            "    \\\"protagonist\\\": \\\"主角设定\\\",\\n" +
            "    \\\"powerSystem\\\": \\\"力量体系\\\",\\n" +
            "    \\\"relationships\\\": \\\"重要关系\\\"\\n" +
            "  },\\n" +
            "  \\\"hookPoints\\\": [\\n" +
            "    {\\\"chapter\\\": 1, \\\"hook\\\": \\\"开篇钩子\\\"},\\n" +
            "    {\\\"chapter\\\": 10, \\\"hook\\\": \\\"第一个大钩子\\\"}\\n" +
            "  ]\\n" +
            "}\\n" +
            "```\\n\\n" +
            
            "确保大纲符合%s类网文的特点，有足够的爽点和悬念！",
            
            novel.getTitle(), novel.getGenre(), basicIdea,
            getGenreOutlinePrompt(novel.getGenre()), novel.getGenre()
        );
    }

    /**
     * 获取网文风格的建议生成提示词
     */
    public String getWebNovelSuggestionsPrompt(Novel novel, Map<String, Object> memoryBank, Integer currentChapter) {
        return String.format(
            "你是【网文创作顾问】，专门为网络小说作者提供创作建议。\\n\\n" +
            
            "## 📊 当前状态\\n" +
            "- 小说：《%s》(%s类)\\n" +
            "- 当前章节：第%d章\\n" +
            "- 创作状态：正在连载\\n\\n" +
            
            "## 🎯 网文建议重点\\n" +
            "### 1. 爽点分析 🔥\\n" +
            "- 最近10章的爽点密度如何？\\n" +
            "- 下一个爽点应该设置在哪里？\\n" +
            "- 什么类型的爽点最适合当前剧情？\\n\\n" +
            
            "### 2. 节奏诊断 ⚡\\n" +
            "- 当前节奏是否合适？太快或太慢？\\n" +
            "- 是否需要调整写作节奏？\\n" +
            "- 读者可能的疲劳点在哪里？\\n\\n" +
            
            "### 3. 悬念管理 🔗\\n" +
            "- 当前有哪些未解决的悬念？\\n" +
            "- 哪些悬念该解决了？\\n" +
            "- 需要埋设新的悬念吗？\\n\\n" +
            
            "### 4. 角色发展 👥\\n" +
            "- 主角的成长轨迹是否清晰？\\n" +
            "- 配角是否需要更多戏份？\\n" +
            "- 角色关系是否需要新的发展？\\n\\n" +
            
            "### 5. 类型特色 🏷️\\n" +
            "%s\\n\\n" +
            
            "## 📝 输出格式\\n" +
            "```json\\n" +
            "{\\n" +
            "  \\\"urgentSuggestions\\\": [\\n" +
            "    {\\\"type\\\": \\\"爽点\\\", \\\"content\\\": \\\"具体建议\\\", \\\"priority\\\": \\\"high\\\"}\\n" +
            "  ],\\n" +
            "  \\\"rhythmAnalysis\\\": {\\n" +
            "    \\\"currentPace\\\": \\\"适中/偏快/偏慢\\\",\\n" +
            "    \\\"suggestion\\\": \\\"具体建议\\\"\\n" +
            "  },\\n" +
            "  \\\"nextChapterFocus\\\": \\\"下一章重点关注什么\\\",\\n" +
            "  \\\"readerRetention\\\": \\\"如何提高读者留存\\\",\\n" +
            "  \\\"warningPoints\\\": [\\\"需要注意的问题\\\"]\\n" +
            "}\\n" +
            "```\\n\\n" +
            
            "重点关注读者体验和追读率！",
            
            novel.getTitle(), novel.getGenre(), currentChapter,
            getGenreAdvicePrompt(novel.getGenre())
        );
    }

    // ================================
    // 分类型特色提示词
    // ================================

    /**
     * 获取类型特色提示词
     */
    private String getGenreSpecificPrompt(String genre) {
        Map<String, String> genrePrompts = new HashMap<>();
        
        genrePrompts.put("玄幻", 
            "## 🔮 玄幻网文特色\\n" +
            "- **等级体系**: 境界要清晰，升级要有仪式感\\n" +
            "- **装逼打脸**: 适度装逼，狠狠打脸\\n" +
            "- **宝物功法**: 让读者眼前一亮的好东西\\n" +
            "- **美女配角**: 倾国倾城但不能抢主角风头\\n" +
            "- **反派设计**: 要让读者恨得牙痒痒\\n" +
            "- **避免**: 过度修仙哲理，枯燥的等级介绍"
        );
        
        genrePrompts.put("都市", 
            "## 🏢 都市网文特色\\n" +
            "- **装逼要自然**: 不刻意，顺其自然地展示实力\\n" +
            "- **金钱地位**: 豪车名表要恰到好处\\n" +
            "- **美女要现代**: 符合都市人设，不要太夸张\\n" +
            "- **反派现实感**: 不能太脸谱化\\n" +
            "- **专业领域**: 医术/商战/科技要有专业感\\n" +
            "- **避免**: 过度炫富，脱离现实的剧情"
        );
        
        genrePrompts.put("系统", 
            "## 💻 系统流特色\\n" +
            "- **系统奖励**: 要让读者眼馋的好东西\\n" +
            "- **任务设计**: 有挑战但能完成\\n" +
            "- **升级仪式感**: 数据变化要爽\\n" +
            "- **系统互动**: 有个性，不死板\\n" +
            "- **商城系统**: 好东西要多，但不能太容易得到\\n" +
            "- **避免**: 纯数据堆砌，忽略剧情"
        );
        
        genrePrompts.put("重生", 
            "## 🔄 重生文特色\\n" +
            "- **先知优势**: 合理利用重生优势\\n" +
            "- **改变命运**: 要有强烈的对比感\\n" +
            "- **复仇爽感**: 报仇要报得解气\\n" +
            "- **情感纠葛**: 前世今生的情感处理\\n" +
            "- **蝴蝶效应**: 改变要合理\\n" +
            "- **避免**: 过度依赖先知，缺少挑战"
        );

        return genrePrompts.getOrDefault(genre, getDefaultGenrePrompt());
    }

    /**
     * 获取爽点设计提示词
     */
    private String getCoolPointsPrompt(String genre) {
        return "## 🔥 爽点设计清单\\n" +
               "### 升级爽点\\n" +
               "- 实力突破，获得新能力\\n" +
               "- 地位提升，获得认可\\n" +
               "- 财富增长，生活改善\\n\\n" +
               
               "### 打脸爽点\\n" +
               "- 被看不起后的强势反击\\n" +
               "- 隐藏实力的震撼展现\\n" +
               "- 预言成真，打脸质疑者\\n\\n" +
               
               "### 情感爽点\\n" +
               "- 美女的青睐和示好\\n" +
               "- 朋友的真心认可\\n" +
               "- 家人的骄傲和支持\\n\\n" +
               
               "**本章必须包含至少一个明确的爽点！**";
    }

    /**
     * 获取节奏控制提示词
     */
    private String getPaceControlPrompt() {
        return "## ⚡ 节奏控制技巧\\n" +
               "### 开头(前20%)\\n" +
               "- 快速进入状况，抓住读者\\n" +
               "- 直接展示冲突或悬念\\n" +
               "- 避免过多背景铺垫\\n\\n" +
               
               "### 中段(中60%)\\n" +
               "- 推进主线，发展支线\\n" +
               "- 适当的转折和变化\\n" +
               "- 保持读者的期待感\\n\\n" +
               
               "### 结尾(后20%)\\n" +
               "- 解决本章冲突\\n" +
               "- 埋设下章悬念\\n" +
               "- 给读者继续阅读的动力\\n\\n" +
               
               "**章末钩子是关键，一定要让读者想看下一章！**";
    }

    /**
     * 获取类型大纲提示词
     */
    private String getGenreOutlinePrompt(String genre) {
        Map<String, String> outlinePrompts = new HashMap<>();
        
        outlinePrompts.put("玄幻", 
            "### 玄幻大纲要点\\n" +
            "- 修炼体系要完整：练气→筑基→金丹→元婴...\\n" +
            "- 门派势力要分明：正派、魔道、散修\\n" +
            "- 宝物等级要清晰：凡器→宝器→灵器→仙器\\n" +
            "- 地图要分层：小村→城池→王朝→大陆→星域"
        );
        
        outlinePrompts.put("都市",
            "### 都市大纲要点\\n" +
            "- 身份要有层次：普通人→小有成就→行业翘楚→顶级大佬\\n" +
            "- 势力要现实：家族、公司、官场、黑白两道\\n" +
            "- 能力要合理：医术、商战、功夫、异能\\n" +
            "- 场景要丰富：学校、职场、商场、上流社会"
        );

        return outlinePrompts.getOrDefault(genre, "### 通用大纲要点\\n- 角色成长清晰\\n- 冲突层层递进\\n- 悬念环环相扣");
    }

    /**
     * 获取类型建议提示词
     */
    private String getGenreAdvicePrompt(String genre) {
        Map<String, String> advicePrompts = new HashMap<>();
        
        advicePrompts.put("玄幻",
            "### 玄幻创作要点\\n" +
            "- 境界提升的频率是否合适？\\n" +
            "- 打脸情节是否够爽？\\n" +
            "- 宝物功法是否吸引人？\\n" +
            "- 敌人是否足够强大？"
        );
        
        advicePrompts.put("都市",
            "### 都市创作要点\\n" +
            "- 装逼是否自然不做作？\\n" +
            "- 专业知识是否到位？\\n" +
            "- 情感戏是否恰当？\\n" +
            "- 反派是否有现实感？"
        );

        return advicePrompts.getOrDefault(genre, "### 通用创作要点\\n- 节奏是否合适？\\n- 角色是否立体？\\n- 冲突是否激烈？");
    }

    /**
     * 获取默认类型提示词
     */
    private String getDefaultGenrePrompt() {
        return "## 📝 通用网文特色\\n" +
               "- **快节奏**: 避免拖沓，每章都要有推进\\n" +
               "- **强代入**: 让读者有代入感\\n" +
               "- **爽点**: 适当的爽点和成就感\\n" +
               "- **悬念**: 保持读者的好奇心\\n" +
               "- **避免**: 过于文艺，脱离读者期待";
    }

    /**
     * 构建高质量长篇小说创作提示词
     * 基于专业评价完全重构，解决AI流水线问题  
     * 核心理念：克制、延迟、模糊、代价
     */
    public String buildEnhancedStreamingPrompt(
            Novel novel,
            Map<String, Object> chapterPlan,
            Map<String, Object> memoryBank,
            String userAdjustment) {
        
        Integer chapterNumber = (Integer) chapterPlan.get("chapterNumber");
        String chapterTitle = (String) chapterPlan.get("title");
        Integer estimatedWords = (Integer) chapterPlan.get("estimatedWords");
        
        StringBuilder prompt = new StringBuilder();
        
        // 动态的创作者身份 - 基于小说类型和设定
        String creatorIdentity = buildDynamicCreatorIdentity(novel.getGenre());
        prompt.append(creatorIdentity).append("\\n\\n")
              
              .append("**【网文长篇创作核心理念】**\\n")
              .append("• **克制原则**：不解释，只呈现；不定义，只暗示\\n")
              .append("• **延迟原则**：信息逐步透露，神秘感层层深入\\n")
              .append("• **模糊原则**：异常通过感觉暗示，避免直接描述\\n")
              .append("• **代价原则**：力量获取必须有痛苦、风险、失控\\n")
              .append("• **视角统一**：明确聚焦主角，避免视角混乱\\n\\n")
              
              .append(buildAntiAIDetectionRules(novel, chapterNumber)).append("\\n\\n")
              
              .append("**【深度优化·可生长开头法则】**\\n")
              .append("### 1. 视角聚焦技巧\\n")
              .append("- 明确主角视角，避免多角色视角混合\\n")
              .append("- 配角作为背景存在，通过主角视角观察\\n")
              .append("- 避免镜头乱切，确保叙事焦点明确\\n\\n")
              
              .append("### 2. 梦境改造技巧\\n")
              .append("- 改为：梦中只出现符号、低语、残像，无法理解\\n")
              .append("- 不要白发老者直接宣布设定\\n")
              .append("- 信息碎片化，甚至可能误导主角\\n")
              .append("- 伴随痛苦：失忆、幻觉、被附身感\\n\\n")
              
              .append("### 3. 觉醒真实化技巧\\n")
              .append("- 逐渐异常：耳鸣、幻视、怕光，被家人怀疑\\n")
              .append("- 力量不稳定：时有时无、难以控制\\n")
              .append("- 社会隔离：邻居疏远、被当作病人\\n")
              .append("- 自我怀疑：怀疑自己疯了，恐惧被发现\\n\\n")
              
              .append("### 4. 超自然模糊化\\n")
              .append("- 只觉佩刀\"不对劲\"，像有东西在动，但看不清\\n")
              .append("- 镜面浮现无法辨认的古字，主角误读误解\\n")
              .append("- 用环境细节暗示：动物逃走、风向异常\\n")
              .append("- 让主角（和读者）猜测而非直接告知\\n\\n")
              
              .append(buildAdvancedAntiAITechniques(novel, chapterNumber)).append("\\n\\n")
              
              .append("**【").append(novel.getGenre()).append("类深度重构】**\\n");
              
        // 添加优化后的类型特定指导
        String genreSpecific = getAdvancedGenreGuidance(novel.getGenre(), chapterNumber);
        prompt.append(genreSpecific).append("\\n");
        
        // 当前任务信息 - 简化但更聚焦
        prompt.append("📖 **本章创作目标**\\n")
              .append("- 作品：《").append(novel.getTitle()).append("》第").append(chapterNumber).append("章");
        if (chapterTitle != null && !chapterTitle.isEmpty()) {
            prompt.append("《").append(chapterTitle).append("》");
        }
        prompt.append("\\n")
              .append("- 字数：").append(estimatedWords).append("字（严格控制）\\n")
              .append("- 核心事件：").append(chapterPlan.get("coreEvent")).append("\\n\\n");
        
        // 动态核心上下文（从记忆库智能提取）
        if (memoryBank != null) {
            String dynamicContext = buildDynamicContextFromMemory(novel, memoryBank, chapterNumber);
            if (!dynamicContext.isEmpty()) {
                prompt.append("🧠 **创作记忆要点**\\n").append(dynamicContext).append("\\n");
            }
        }
        
        // 用户特殊要求
        if (userAdjustment != null && !userAdjustment.trim().isEmpty()) {
            prompt.append("✨ **创作者要求**：").append(userAdjustment).append("\\n\\n");
        }
        
        // 章节特定指导
        if (chapterNumber <= 10) {
            prompt.append("**【开篇章节核心原则】**\\n")
                  .append("• 慢启动建立：日常感 + 一个令人疑惑的细节\\n")
                  .append("• 悬念营造：3个疑问 + 1个情感 + 0个解答\\n")
                  .append("• 情感投入：让读者先关心角色，再引入神秘\\n")
                  .append("• 禁止直接：觉醒、传承、系统、反派登场\\n\\n");
        }
        
        // 最终执行标准
        prompt.append("**【输出标准】**\\n")
              .append("• **首行格式**：严格以 #爆款标题# 开头（必须使用#号包裹，不要使用书名号）\\n")
              .append("  示例格式：#血夜惊魂，古刀异动# 或 #暗潮涌动，真相浮现#\\n")
              .append("  - 标题要有悬念感和吸引力\\n")
              .append("  - 标题字数控制在6-12字\\n")
              .append("  - 标题不要包含\"第X章\"字样\\n")
              .append("• 第二行：空行\\n")
              .append("• 第三行开始：正文内容（无其他说明、注释）\\n")
              .append("• 字数控制：").append(estimatedWords).append("字左右\\n")
              .append("• 章末钩子：必须留下让读者想继续看的悬念\\n")
              .append("• 人类质感：避免AI流水线产品的痕迹\\n\\n")
              
              .append("🚀 **现在开始创作（严格按照格式，首行必须是#标题#）：**");
        
        return prompt.toString();
    }
    
    /**
     * 压缩角色信息为提示词核心要点
     */
    private String compressCharacterInfoForPrompt(String fullCharacterInfo) {
        if (fullCharacterInfo == null || fullCharacterInfo.trim().isEmpty()) {
            return "";
        }
        
        // 简化角色信息，只保留状态关键词
        String[] lines = fullCharacterInfo.split("\\n");
        StringBuilder compressed = new StringBuilder();
        compressed.append("李良（16岁主角）现状：");
        
        for (String line : lines) {
            if (line.contains("李良") && line.contains("状态")) {
                // 提取关键状态信息
                String statusInfo = line.replaceAll(".*状态[：:]", "").trim();
                if (!statusInfo.isEmpty()) {
                    compressed.append(statusInfo).append("；");
                    break;
                }
            }
        }
        
        return compressed.toString();
    }
    
    /**
     * 从记忆库动态构建核心上下文信息
     */
    @SuppressWarnings("unchecked")
    private String buildDynamicContextFromMemory(Novel novel, Map<String, Object> memoryBank, int chapterNumber) {
        StringBuilder context = new StringBuilder();
        
        // 1. 角色信息（动态压缩）
        String characterSummary = characterManagementService.buildCharacterSummaryForWriting(
            novel.getId(), memoryBank, chapterNumber);
        if (!characterSummary.isEmpty()) {
            String compressedCharacters = compressCharacterInfoForPrompt(characterSummary);
            if (!compressedCharacters.isEmpty()) {
                context.append("👥 ").append(compressedCharacters).append("\\n");
            }
        }
        
        // 2. 当前卷信息（如果存在）
        Object currentVolume = memoryBank.get("currentVolumeOutline");
        if (currentVolume instanceof Map) {
            Map<String, Object> volumeData = (Map<String, Object>) currentVolume;
            Object volumeTitle = volumeData.get("title");
            Object volumeTheme = volumeData.get("theme");
            if (volumeTitle != null || volumeTheme != null) {
                context.append("📖 当前卷：");
                if (volumeTitle != null) context.append(volumeTitle);
                if (volumeTheme != null) context.append(" - ").append(volumeTheme);
                context.append("\\n");
            }
        }
        
        // 3. 世界设定（关键要点）
        Object worldSettings = memoryBank.get("worldSettings");
        if (worldSettings instanceof Map) {
            Map<String, Object> settings = (Map<String, Object>) worldSettings;
            StringBuilder worldInfo = new StringBuilder();
            Object powerSystem = settings.get("powerSystem");
            Object geography = settings.get("geography");
            if (powerSystem != null) worldInfo.append("力量体系: ").append(powerSystem).append("; ");
            if (geography != null) worldInfo.append("环境: ").append(geography).append("; ");
            
            if (worldInfo.length() > 0) {
                context.append("🌍 ").append(worldInfo.toString()).append("\\n");
            }
        }
        
        // 4. 活跃情节线（简化）
        Object plotThreads = memoryBank.get("plotThreads");
        if (plotThreads instanceof List) {
            List<Map<String, Object>> threads = (List<Map<String, Object>>) plotThreads;
            if (!threads.isEmpty()) {
                StringBuilder plotInfo = new StringBuilder("🧵 活跃线索: ");
                int count = 0;
                for (Map<String, Object> thread : threads) {
                    if (count >= 3) break; // 只保留前3个
                    Object description = thread.get("description");
                    if (description != null) {
                        plotInfo.append(description).append("; ");
                        count++;
                    }
                }
                if (count > 0) {
                    context.append(plotInfo.toString()).append("\\n");
                }
            }
        }
        
        // 5. 关键伏笔（当前章节相关）
        Object foreshadowing = memoryBank.get("foreshadowing");
        if (foreshadowing instanceof Map) {
            Map<String, Object> foreshadowData = (Map<String, Object>) foreshadowing;
            Object activeHints = foreshadowData.get("activeHints");
            if (activeHints instanceof List) {
                List<Map<String, Object>> hints = (List<Map<String, Object>>) activeHints;
                StringBuilder hintInfo = new StringBuilder("🎭 当前伏笔: ");
                int count = 0;
                for (Map<String, Object> hint : hints) {
                    if (count >= 2) break; // 只保留前2个
                    Object description = hint.get("description");
                    if (description != null) {
                        hintInfo.append(description).append("; ");
                        count++;
                    }
                }
                if (count > 0) {
                    context.append(hintInfo.toString()).append("\\n");
                }
            }
        }
        
        return context.toString();
    }
    
    /**
     * 动态构建创作者身份（基于小说类型）
     */
    private String buildDynamicCreatorIdentity(String genre) {
        StringBuilder identity = new StringBuilder();
        
        identity.append("你是一位资深网络文学创作者，专注").append(genre).append("小说领域十年以上。\\n");
        
        // 根据类型选择风格参考
        switch (genre) {
            case "玄幻":
                identity.append("你的写作风格融合烽火戏诸侯的沉郁、忘语的细腻、辰东的宏大。\\n")
                        .append("你擅长：埋设伏笔、控制节奏、塑造真实人物、营造命运感。\\n")
                        .append("你反对：系统流、无脑爽文、金手指秒生效、角色工具化。");
                break;
            case "都市":
                identity.append("你的写作风格融合唐家三少的流畅、辰东的爽快、天蚕土豆的节奏感。\\n")
                        .append("你擅长：现实感描写、情感细腻刻画、商战智斗、都市生活质感。\\n")
                        .append("你反对：过度脱离现实、装逼过度、金手指太假、感情戏拖沓。");
                break;
            case "仙侠":
                identity.append("你的写作风格融合我吃西红柿的洒脱、梦入神机的深度、忘语的细腻。\\n")
                        .append("你擅长：修仙哲理、剑道意境、情感克制、古风韵味。\\n")
                        .append("你反对：修仙变修真、境界混乱、感情现代化、古风不纯。");
                break;
            case "科幻":
                identity.append("你的写作风格融合刘慈欣的宏大、王晋康的思辨、何夕的人文关怀。\\n")
                        .append("你擅长：科技设定、逻辑推理、未来想象、人性思考。\\n")
                        .append("你反对：科学Bug、逻辑矛盾、技术堆砌、忽视人文。");
                break;
            case "历史":
                identity.append("你的写作风格融合月关的考证、酒徒的深度、孑与2的幽默。\\n")
                        .append("你擅长：历史还原、政治智谋、人物刻画、时代氛围。\\n")
                        .append("你反对：历史错误、现代思维、人物脸谱化、情节狗血。");
                break;
            case "军事":
                identity.append("你的写作风格融合纵横中文的热血、骠骑的专业、华表的激情。\\n")
                        .append("你擅长：战术描写、军事专业、团队合作、爱国情怀。\\n")
                        .append("你反对：军事常识错误、个人英雄主义、脱离实际、政治不当。");
                break;
            default:
                identity.append("你的写作风格注重情节紧凑、人物立体、逻辑清晰。\\n")
                        .append("你擅长：节奏控制、悬念设置、角色刻画、情感渲染。\\n")
                        .append("你反对：拖沓冗长、人物扁平、逻辑混乱、情感虚假。");
        }
        
        return identity.toString();
    }
    
    /**
     * 动态构建反AI检测要求（基于小说具体信息）
     */
    private String buildAntiAIDetectionRules(Novel novel, int chapterNumber) {
        StringBuilder rules = new StringBuilder();
        
        rules.append("**【严格禁止·伪质感陷阱】**\\n");
        
        // 通用反AI检测规则
        rules.append("• 禁止开局多视角：确保视角聚焦明确，避免混乱跳转\\n")
             .append("• 禁止信息轰炸：避免一次性倾倒所有设定\\n")
             .append("• 禁止能力秒得：力量获取必须有过程、代价、不稳定性\\n")
             .append("• 禁止直接命名：避免专业术语满天飞\\n")
             .append("• 禁止反派脸谱化：要有现实逻辑和复杂动机\\n");
        
        // 基于类型的特定禁忌
        String genre = novel.getGenre();
        switch (genre) {
            case "玄幻":
                rules.append("• 禁止系统类词汇：\"叮\"\"宿主\"\"任务\"\"奖励\"等\\n")
                     .append("• 禁止境界直接说：用暗示而非明确等级\\n");
                break;
            case "都市":
                rules.append("• 禁止过度装逼：成功要有合理过程\\n")
                     .append("• 禁止脱离现实：保持都市生活真实感\\n");
                break;
            case "仙侠":
                rules.append("• 禁止现代用语：保持古风韵味\\n")
                     .append("• 禁止修真术语：用传统仙侠概念\\n");
                break;
        }
        
        // 基于章节数的特定要求
        if (chapterNumber <= 3) {
            rules.append("• 开篇禁忌：避免金手指直接显现、反派立即登场\\n");
        } else if (chapterNumber <= 10) {
            rules.append("• 初期禁忌：避免力量暴涨、设定全盘曝光\\n");
        }
        
        return rules.toString();
    }
    
    /**
     * 构建深度反AI检测技术（基于专业指导核心）
     */
    private String buildAdvancedAntiAITechniques(Novel novel, int chapterNumber) {
        StringBuilder techniques = new StringBuilder();
        
        techniques.append("**【深度反AI检测技术】**\n")
                 .append("### 1. 真实感营造技巧\n")
                 .append("- 身体反应具体化：不说'紧张'，而是'手心出汗，深呼吸三次'\n")
                 .append("- 动作微观化：'他攥紧了拳头'比'他很愤怒'更有力\n")
                 .append("- 感官密度增强：同时调用视觉、听觉、触觉、嗅觉\n")
                 .append("- 时间切片技术：用'这时''下一刻''几秒后'创造节奏\n\n")
                 
                 .append("### 2. 对话自然化处理\n")
                 .append("- 不完整句式：'这个...怎么说呢''嗯，大概是'\n")
                 .append("- 停顿与重复：'我是说，我的意思是...'\n")
                 .append("- 个人语言特色：每个角色有专属口头禅和表达习惯\n")
                 .append("- 潜台词技巧：说一句话，暗示另一层意思\n\n")
                 
                 .append("### 3. 情节推进技术\n")
                 .append("- 冰山原理：只展现10%，暗示90%\n")
                 .append("- 延迟满足：关键信息分3次透露\n")
                 .append("- 意外的逻辑性：转折要突然但回头看有道理\n")
                 .append("- 情绪先行：先让读者有情绪反应，再解释原因\n\n");
        
        // 基于章节阶段的特殊技巧
        if (chapterNumber <= 3) {
            techniques.append("### 4. 开篇阶段特殊技巧\n")
                     .append("- 误导技术：让主角和读者都误解某些现象\n")
                     .append("- 日常异常化：把平常事物写得略微不对劲\n")
                     .append("- 问题叠加：每章末尾要多一个疑问\n")
                     .append("- 情感投资：让读者先关心角色，再关心情节\n");
        } else if (chapterNumber <= 10) {
            techniques.append("### 4. 发展阶段特殊技巧\n")
                     .append("- 能力不稳定：时强时弱，有副作用\n")
                     .append("- 社会成本：获得力量要付出社会关系代价\n")
                     .append("- 认知迷雾：主角对自己状况也不确定\n")
                     .append("- 层层剥开：像剥洋葱一样逐步揭示真相\n");
        }
        
        techniques.append("\n### 5. 文风去AI化\n")
                 .append("- 句式长短混合：长句抒情，短句紧张\n")
                 .append("- 避免'的'字句：'血红的夕阳' → '夕阳红得像血'\n")
                 .append("- 动词优先：用动词替代形容词和副词\n")
                 .append("- 留白技巧：重要情节后要有停顿感\n");
        
        return techniques.toString();
    }
    
    /**
     * 获取增强的类型特定指导（更自然的写作风格）
     */
    private String getEnhancedGenreGuidance(String genre, int chapterNumber) {
        StringBuilder guidance = new StringBuilder();
        
        switch (genre) {
            case "都市":
                guidance.append("• 描写要贴近现实但有戏剧性，避免过于平淡\\n")
                        .append("• 商业、情感、成长三线并行，互相影响\\n")
                        .append("• 主角能力要合理，成功有过程不能太突兀\\n")
                        .append("• 配角要有血有肉，不是工具人，有自己的目标\\n")
                        .append("• 冲突来源于现实：利益、情感、价值观差异\\n");
                break;
            case "玄幻":
                guidance.append("• 修炼要有感悟过程，不只是打坐吸收\\n")
                        .append("• 战斗要有战术，不是纯粹的力量碾压\\n")
                        .append("• 宝物和机缘要有代价，天上不会掉馅饼\\n")
                        .append("• 境界提升要影响性格和认知，不只是力量\\n")
                        .append("• 势力关系要复杂，有同盟有敌对有中立\\n");
                break;
            case "科幻":
                guidance.append("• 科技要有逻辑基础，不能违背基本物理\\n")
                        .append("• 未来社会要有历史进程，不是凭空出现\\n")
                        .append("• 人物关系要考虑科技对社会结构的影响\\n")
                        .append("• 冲突要与科技发展水平相匹配\\n")
                        .append("• 探索未知要有风险，不是轻松旅游\\n");
                break;
            case "历史":
                guidance.append("• 历史细节要考究，服装、建筑、社会制度\\n")
                        .append("• 人物思维要符合时代背景\\n")
                        .append("• 政治斗争要有层次，不只是你死我活\\n")
                        .append("• 战争描写要真实残酷，有策略有牺牲\\n")
                        .append("• 文化差异要体现在日常细节中\\n");
                break;
            case "仙侠":
                guidance.append("• 修仙要有哲学思考，不只是变强\\n")
                        .append("• 门派关系要复杂，有传承有竞争\\n")
                        .append("• 天道规则要一致，不能随意更改\\n")
                        .append("• 情感要克制含蓄，符合仙侠氛围\\n")
                        .append("• 打斗要有意境，不只是招式对撞\\n");
                break;
            default:
                guidance.append("• 保持类型特色的同时要有创新\\n")
                        .append("• 人物刻画要立体，有优缺点\\n")
                        .append("• 情节发展要有因果关系\\n")
                        .append("• 对话要推进情节或揭示性格\\n");
        }
        
        return guidance.toString();
    }
    
    /**
     * 获取深度类型指导（基于专业评价全新设计）
     */
    private String getAdvancedGenreGuidance(String genre, int chapterNumber) {
        StringBuilder guidance = new StringBuilder();
        
        // 特别处理早期章节的神秘感营造
        boolean isEarlyChapter = chapterNumber <= 10;
        
        switch (genre) {
            case "都市":
                guidance.append("• **现实感基础**：从真实的生活细节开始，逐步埋入神秘元素\\n")
                        .append("• **反派复杂化**：不直接伤害，而是用法律、经济、社会关系施压\\n")
                        .append("• **力量觉醒**：不是突然获得超能力，而是直觉、灵敏、判断力的提升\\n")
                        .append("• **生活质感**：具体地名、物价、交通、工作细节要真实\\n");
                if (isEarlyChapter) {
                    guidance.append("• **开篇重点**：主角遇到一个小异常，但以为是巧合\\n")
                            .append("• **禁止**：开篇就明示主角有特殊能力或神秘身份\\n");
                }
                break;
                
            case "玄幻":
                guidance.append("• **神秘现象碎片化**：通过感官异常、梦境、记忆闪回暗示\\n")
                        .append("• **禁止直接命名**：不说\"修仙\"\"真气\"\"灵根\"，用民间说法\\n")
                        .append("• **异物不解释**：铜镜、玉珮、古书只显现异象，不说明功能\\n")
                        .append("• **力量不稳定**：时有时无、难以控制、有副作用\\n")
                        .append("• **觉醒代价**：必须有身体痛苦、精神正常、社会隔阙\\n");
                if (isEarlyChapter) {
                    guidance.append("• **早期原则**：日常困境 + 一个令人疑惑的细节\\n")
                            .append("• **严禁**：系统、器灵、天选之子等套路表达\\n")
                            .append("• **目标**：3个疑问 + 1个瘦情 + 0个解答\\n");
                }
                break;
                
            case "仙侠":
                guidance.append("• **道法哲学**：不直接说教，通过行为和选择体现\\n")
                        .append("• **古风意境**：文言词汇、简洁对话、意境传递\\n")
                        .append("• **情感克制**：情由心生但不直白，无现代态口语\\n")
                        .append("• **战斗美学**：招式名称、动作描写、意境烘托并重\\n");
                if (isEarlyChapter) {
                    guidance.append("• **开篇氛围**：古朴宁静环境 + 远山如墨 + 一丝異象\\n");
                }
                break;
                
            case "科幻":
                guidance.append("• **科学逻辑**：设定不能违反基本物理定律\\n")
                        .append("• **未来质感**：有变化但不失真，今天+50年的感觉\\n")
                        .append("• **人文关怀**：科技发展对人性、伦理、社会的冲击\\n")
                        .append("• **未知惊悚**：对未来的恐惧和对未知的敏感\\n");
                if (isEarlyChapter) {
                    guidance.append("• **开篇意象**：日常未来生活 + 一个让人不安的细节\\n");
                }
                break;
                
            default:
                guidance.append("• **类型质感**：符合类型期待但避免套路化\\n")
                        .append("• **人物立体**：每个角色都有个人动机和盲点\\n")
                        .append("• **因果逻辑**：情节发展要有清晰的因果链\\n");
        }
        
        // 章节数特殊指导
        if (chapterNumber <= 3) {
            guidance.append("\\n📍 **开篇黄金法则**：\\n")
                    .append("- 日常生活基调 + 一个令人疑惑的细节\\n")
                    .append("- 主角有内心渴望但不是超能力\\n")
                    .append("- 让读者在第3章结束时产生3个疑问\\n");
        } else if (chapterNumber <= 10) {
            guidance.append("\\n📍 **初期发展原则**：\\n")
                    .append("- 不解释前面的疑问，反而增加新的谜团\\n")
                    .append("- 主角变化不明显但读者能感知到\\n")
                    .append("- 埋设关键配角和环境，为后续做铺垫\\n");
        }
        
        guidance.append("\\n");
        return guidance.toString();
    }
}