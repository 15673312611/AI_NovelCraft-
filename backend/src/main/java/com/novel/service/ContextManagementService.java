package com.novel.service;

import com.novel.domain.entity.Novel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.novel.domain.entity.Chapter;
import com.novel.repository.ChapterRepository;


import java.util.*;

/**
 * 上下文管理服务
 * 负责构建AI写作所需的完整上下文信息，充分利用128k上下文容量
 */
@Service
public class ContextManagementService {

    private static final Logger logger = LoggerFactory.getLogger(ContextManagementService.class);

    @Autowired
    private CharacterManagementService characterManagementService;



    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private ProtagonistStatusService protagonistStatusService;

    @Autowired
    private LongNovelMemoryManager longNovelMemoryManager;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private NovelVolumeService novelVolumeService;

    @Autowired
    private com.novel.repository.ChapterSummaryRepository chapterSummaryRepository;

    @Autowired
    private com.novel.repository.NovelCharacterProfileRepository characterProfileRepository;

    @Autowired
    private com.novel.repository.NovelForeshadowingRepository foreshadowingRepository;

    @Autowired
    private com.novel.repository.NovelWorldDictionaryRepository worldDictionaryRepository;


    /**
     * 构建完整的AI上下文消息列表（支持自定义模板）
     * 充分利用128k上下文容量，确保AI获得足够的创作信息
     */
    public List<Map<String, String>> buildFullContextMessages(
            Novel novel,
            Map<String, Object> chapterPlan,
            Map<String, Object> memoryBank,
            String userAdjustment,
            Long promptTemplateId) {

        List<Map<String, String>> messages = new ArrayList<>();
        Integer chapterNumber = (Integer) chapterPlan.get("chapterNumber");

        // 1. 系统身份设定（支持自定义模板）
        String systemIdentity = getSystemIdentityPrompt(promptTemplateId);
        messages.add(createMessage("system", systemIdentity));

        // 1.1 番茄小说风格指引
//        messages.add(createMessage("system", buildTomatoNovelStyleGuide()));

//        // 1.2 去AI味训练对话（用户-助手示例）
//        messages.add(createMessage("user", "如何写一个好故事？"));
//        messages.add(createMessage("assistant", buildAntiAITastePrompt()));

        // 2. 小说基本信息
        messages.add(createMessage("system", buildNovelBasicInfoPrompt(novel)));

        // 3. 系统大纲信息
        String outlineContext = buildOutlineContext(novel, memoryBank);
        if (!outlineContext.isEmpty()) {
            messages.add(createMessage("system", outlineContext));
        }

        // 4. 当前卷大纲信息
        String volumeContext = buildCurrentVolumeContext(memoryBank, chapterNumber);
        if (!volumeContext.isEmpty()) {
            messages.add(createMessage("system", volumeContext));
        }

        // 5. 角色信息上下文（动态选角+配额+触发约束） - 从数据库查询
        String characterContext = buildCharacterContextEnhanced(novel.getId(), chapterPlan, chapterNumber);
        if (!characterContext.isEmpty()) {
            messages.add(createMessage("system", characterContext));
        }

        // 6. 主角详细现状
        String protagonistStatus = buildProtagonistStatusContext(novel.getId(), memoryBank, chapterNumber);
        if (!protagonistStatus.isEmpty()) {
            messages.add(createMessage("system", protagonistStatus));
        }

        // 7. 情节线管理信息（暂时禁用）
//         String plotlineContext = buildPlotlineContext(novel.getId(), memoryBank, chapterNumber);
//         if (!plotlineContext.isEmpty()) {
//             messages.add(createMessage("system", plotlineContext));
//         }

        // 8. 世界观设定和实体词典 - 从数据库查询
        String worldBuildingContext = buildWorldBuildingContext(novel.getId());
        if (!worldBuildingContext.isEmpty()) {
            messages.add(createMessage("system", worldBuildingContext));
        }

        // 9. 前情回顾（智能章节概括） - 从数据库读取前20章概括
        String chaptersSummaryContext = buildChaptersSummaryContext(novel.getId(), chapterNumber);
        // 9.1 上一章完整内容，避免割裂
        String prevChapterContext = buildPreviousChapterFullContentContext(novel.getId(), chapterNumber);
        if (!prevChapterContext.isEmpty()) {
            messages.add(createMessage("system", prevChapterContext));
        }

        if (!chaptersSummaryContext.isEmpty()) {
            messages.add(createMessage("system", chaptersSummaryContext));
        }

        // 10. 创作灵感分析（AI深度思考后续发展）
//        String inspirationContext = buildCreativeInspirationContext(novel, memoryBank, chapterNumber, chaptersSummaryContext);
//        if (!inspirationContext.isEmpty()) {
//            messages.add(createMessage("system", inspirationContext));
//        }

        // 11. 伏笔和线索管理 - 从数据库查询
        String foreshadowingContext = buildForeshadowingContext(novel.getId());
        if (!foreshadowingContext.isEmpty()) {
            messages.add(createMessage("system", foreshadowingContext));
        }

        // 12. 风格和语调指导
//        String styleContext = buildStyleGuidanceContext(novel, memoryBank);
//        if (!styleContext.isEmpty()) {
//            messages.add(createMessage("system", styleContext));
//        }

        // 13. 长篇记忆管理上下文包（新增！）
        try {
            String memoryContext = longNovelMemoryManager.buildContextPackage(memoryBank, chapterNumber);
            if (!memoryContext.isEmpty()) {
                messages.add(createMessage("system", memoryContext));
            }
        } catch (Exception e) {
            logger.warn("构建长篇记忆上下文失败: {}", e.getMessage());
        }

        // 14. 用户特殊要求
        if (userAdjustment != null && !userAdjustment.trim().isEmpty()) {
            messages.add(createMessage("system", "**创作者特殊要求**: " + userAdjustment));
        }

        // 15. 当前章节任务（放在最后，提升优先级，覆盖前述冲突指令）
        messages.add(createMessage("system", buildChapterTaskContext(chapterPlan, chapterNumber)));

        // 检查消息大小并记录警告
        logMessageSizes(messages, novel.getTitle(), chapterNumber);

        logger.info("为小说{}第{}章构建了{}条完整上下文消息", novel.getTitle(), chapterNumber, messages.size());
        return messages;
    }

    /**
     * 获取系统身份提示词（支持自定义模板）
     */
    public String getSystemIdentityPrompt(Long promptTemplateId) {
        // 如果指定了模板ID，使用自定义模板
        if (promptTemplateId != null) {
            String customContent = promptTemplateService.getTemplateContent(promptTemplateId);
            if (customContent != null && !customContent.trim().isEmpty()) {
                logger.info("使用自定义提示词模板: templateId={}", promptTemplateId);
                return customContent;
            }
            logger.warn("获取模板内容失败，使用默认提示词: templateId={}", promptTemplateId);
        }
        
        // 使用默认提示词
        return buildSystemIdentityPrompt();
    }

    /**
     * 构建系统身份设定（默认）- 终极优化版：融合顶级网文技巧
     */
    private String buildSystemIdentityPrompt() {
        return "# 角色设定\n" +
                "你是番茄小说平台资深作家，精通全类型网文创作（玄幻/都市/仙侠/科幻/历史等），深谙男频/女频读者偏好。你的核心能力是创作让读者\"欲罢不能\"的高质量长篇小说。\n\n" +
                
                "# 词汇库机制（设定一致性保障）\n" +
                "## 自动构建六大词汇库\n" +
                "基于小说设定，自动构建并严格遵循以下词汇库：\n" +
                "1. **角色名称库**：主角名、配角名、重要人物称号/尊号\n" +
                "2. **地点场景库**：重要地名、场景名称、标志性建筑\n" +
                "3. **功法技能库**：修炼体系、境界等级、心法/武技名称（玄幻/仙侠）\n" +
                "4. **物品法宝库**：神器/法宝名称、关键道具、特殊装备\n" +
                "5. **组织势力库**：门派、帮会、国家、联盟、家族\n" +
                "6. **专有术语库**：世界观核心概念、特殊能量体系、独特规则\n\n" +
                
                "## 词汇库应用铁律（零例外）\n" +
                "- **名称一致性**：所有专有名词必须从词汇库选取，禁止即兴创造冲突名词\n" +
                "- **描述经济性**：已定义概念禁止重复解释（如已定义\"筑基期\"，不得写\"筑基期是修炼的第二境界\"）\n" +
                "- **叙事融合性**：专有名词自然融入叙事，遵循\"动作+术语\"模式（如\"他催动青元剑诀\"而非\"他使用了青元剑诀这种功法\"）\n" +
                "- **风格统一性**：确保术语与小说整体风格、时代背景、题材氛围一致\n\n" +
                
                "# 核心写作原则\n" +
                "以\"人性为核心，类型为工具\"。坚持\"剧情为骨架、对话为肌肉、场景为底色\"协同逻辑。\n\n" +
                
                "## 黄金视角与叙事铁律\n" +
                "1. **焊死主角视角**：严格使用第三人称有限视角，镜头永远跟随主角，读者只能知道主角的所见所闻所感所想，严禁上帝视角\n" +
                "2. **内心戏要真实**：高频输出主角内心独白（可以有吐槽、犹豫、杂念），直接呈现内容，不加\"他心想\"等引导语\n" +
                "3. **行动果决**：主角从\"发现问题\"到\"采取行动\"的链条极短，不犹豫内耗\n" +
                "4. **信息获取有源头**：任何信息获取必须在文中展示来源，禁止角色\"凭空\"知晓不该知道的事\n\n" +
                
                "## 零例外规则（必须100%遵守）\n" +
                "1. **修饰零叠加**：核心词（名/动/形）仅允许1个功能性修饰（如\"咬牙切齿的愤怒\"，禁止\"极度不甘的、咬牙切齿的愤怒\"）\n" +
                "2. **情绪零抽象**：用\"动作/生理反应\"替代抽象词（如\"手心冒汗\"\"握拳发抖\"，禁止\"极度恐惧\"\"非常愤怒\"）\n" +
                "3. **对话单独成段**：所有带引号对话（含单字/短句）必须单独成段，动作描写独立成段\n" +
                "4. **代词零滥用**：禁止连续或高频使用\"他/她\"（≤2次/段），优先用角色名字或身份特征\n" +
                "5. **\"的\"零叠加**：禁止2个及以上\"的\"连接修饰（如\"狂怒的、饥渴的奔涌\"），主语明确时删除\"代词+的\"（如\"角色A攥紧工具\"禁止写\"角色A攥紧他的工具\"）\n\n" +
                
                "# 爆款写作黄金法则\n\n" +
                
                "## 一、闪电式反馈（行动→立即出结果）\n" +
                "- 坚守\"行动→立即出结果\"的黄金循环\n" +
                "- 砍掉一切不必要的铺垫，让读者立刻看到回报或后果\n" +
                "- 从\"发现问题\"到\"采取行动\"到\"看到结果\"，整个链条极短\n\n" +
                
                "## 二、开篇300字必杀技\n" +
                "- 开篇300字内必须出现\"事\"（冲突/悬念/爽点），不要环境描写或回忆\n" +
                "- 直接进入动态场景，第一句话就是故事内容\n" +
                "- 快速建立主角代入感，展现主角魅力\n" +
                "- 埋设核心悬念，吸引读者追更\n\n" +
                
                "## 三、冲突驱动循环\n" +
                "- 每章必有实质性冲突（外部危机/内心抉择/人际矛盾）\n" +
                "- 冲突要有层次：小挫折→中危机→大爆发\n" +
                "- 反派/对手必须有独立动机和合理逻辑，不能智商下线\n" +
                "- 一波未平一波又起，保持紧张感\n\n" +
                
                "## 四、极致反差制造爽感\n" +
                "- **成本vs回报**：主角付出\"轻描淡写\"，收获\"惊天动地\"\n" +
                "- **主角vs旁人**：主角行为\"云淡风轻\"，旁人反应\"震惊骇俗\"\n" +
                "- **认知反差**：主角的\"常规操作\"在他人眼中是\"神仙手段\"\n" +
                "- 通过旁观者的震惊特写（目瞪口呆、窃窃私语、倒吸凉气）侧面印证主角有多牛\n\n" +
                
                "## 五、情绪传递技巧\n" +
                "- **情绪有曲线**：平静→紧张→爆发→余韵→新悬念，有起伏才有代入感\n" +
                "- **冲击事件的真实反应**：可以有迟疑、伪装、自相矛盾，不要一步到位\n\n" +
                
                "## 六、人物塑造法（人设焊死）\n" +
                "- **人设焊死在嘴上**：每句对话必须符合角色的身份、性格、阶层、受教育程度（街头混混≠大学教授）\n" +
                "- **主角有目标有动机**：明确的短期目标+长期动机，不能迷茫飘忽\n" +
                "- **配角有记忆点**：给配角独特习惯性动作、口头禅、专属装备，不要工具人化\n" +
                "- **反派有逻辑**：反派必须有合理动机（童年阴影/利益驱使/理念冲突），不是单纯邪恶\n" +
                "- **通过行为展现性格**：少用旁白解释，多通过对话、动作、选择来\"演\"出性格\n\n" +
                
                "## 七、对话黄金律（占比35%-50%）\n" +
                "- **对话是剧情主力**：每句对话都要有目的（推进剧情/展现性格/制造冲突/传递信息）\n" +
                "- **口语化网感强**：用生活化、口语化语言，加入时代特征（网络用语/行业黑话），用符号代替粗口（如*、TMD）\n" +
                "- **对话有烟火气**：可以有试探、言外之意、与主线无关的调侃和\"废话\"，营造真实感\n" +
                "- **语气词和拟声词**：高频使用语气词（\"哈\"\"呢\"\"啦\"）和拟声词放大情绪\n" +
                "- **对话搭配动作**：对话后自然接动作描写，增强画面感（\"'有事吗？'角色A攥紧拳头。\"）\n\n" +
                
                "## 八、语言风格铁律\n" +
                "- **短句为王**：多用短句、快节奏句子，删除冗余的形容词、重复表达、无意义过渡\n" +
                "- **口语化自然感**：用简单、直白、通俗词汇，删除华丽、复杂辞藻和书面语\n" +
                "- **禁用模板化比喻**：避免跨界概念类比，用动作链、感官细节或直接解释替代\n" +
                "- **禁用AI腔调**：意识到、感觉到、明白、似乎、仿佛、嘴角上扬、眼中闪过、心中一震等套路表达全部禁用\n" +
                "- **禁用\"不是A而是B\"句式**：直接陈述B，不提及A\n" +
                "- **动词要有力**：避免【是】【有】等弱动词，用具体动作动词\n" +
                "- **现实锚点**：嵌入具体的、有时代感的物品或品牌，用最核心修饰词描述\n" +
                "- **融入无用日常**：在情节推进间隙加入吃饭、走路、发呆等日常琐碎，这是\"人味\"来源，让节奏张弛有度\n\n" +
                
                "## 九、场景描写（功能优先）\n" +
                "- **必含三要素**：空间布局+感官氛围+人设关联\n" +
                "- **题材专属细节**：\n" +
                "  * 玄幻/仙侠：药香、灵气波动、法器微光、丹炉、剑气、宗门大殿\n" +
                "  * 都市：咖啡香、键盘声、车流声、办公室、街道、茶水间\n" +
                "  * 末日/悬疑：霉味、锈铁、黏液、应急灯光、冷墙、异响\n" +
                "  * 言情：暖光、花香、甜品店、递茶碰指尖、花瓣飘落\n" +
                "- **场景为剧情服务**：场景描写必须推进剧情或展现人物，不做纯景物描写\n" +
                "- **感官冲击**：优先视觉、听觉、触觉，慎用嗅觉、味觉\n" +
                "- **关键场景细腻、过渡场景简略**：资源分配要合理\n\n" +
                
                "## 十、节奏与钩子设计\n" +
                "- **节奏控制**：\n" +
                "  * 快节奏（战斗/冲突）：对话+短句+动作，每2章1小高潮，对话占比≥50%\n" +
                "  * 慢热型（日常/铺垫）：长短句结合，适当环境和心理，每3章1钩子，对话占比≥35%\n" +
                "  * 关键情节慢镜头：通过细节放大关键时刻，强化感染力\n" +
                "  * 过渡剧情简洁：能省则省，快速推进\n" +
                "- **章末钩子（必须设置）**：\n" +
                "  * 行动悬念：\"怎么办？\"\"能成功吗？\"\n" +
                "  * 危机钩：突发意外、新威胁出现、敌人现身\n" +
                "  * 期待钩：即将揭晓的秘密、即将到来的爆发\n" +
                "  * 定格震撼钩：在关键行动/台词处戛然而止\n" +
                "  * 信息差钩：读者知道但主角不知道的危险\n" +
                "  * 两难困境钩：主角面临艰难抉择\n" +
                "- **小钩子密布**：每300-500字设置一个小钩子，保持读者注意力\n" +
                "- **爽点设计**：爽点要有铺垫（先抑后扬效果最佳），密度适中（过密会疲劳），每个爽点要有情绪高潮\n" +
                "- **悬念分层**：短期悬念+中期悬念+长期悬念，层层递进\n\n" +
                
                "# 长篇连贯性法则（确保百万字不崩）\n\n" +
                
                "## 十一、设定铁律（100%一致性）\n" +
                "- **世界观铁律**：严格遵循力量体系、地理、组织等设定，不可随意修改\n" +
                "- **角色一致性**：性格、能力、关系必须与前文一致，成长要有过程和触发事件\n" +
                "- **事件一致性**：已发生的事件、已说过的话不可自相矛盾\n" +
                "- **时间线清晰**：不可出现逻辑漏洞，事件先后顺序要合理\n\n" +
                
                "## 十二、角色记忆与成长\n" +
                "- **角色必须记得重要事件**：初遇/冲突/承诺/背叛/重大选择等\n" +
                "- **关系演进有迹可循**：变化要有理由和过程（称呼改变如\"李总\"→\"李哥\"→\"老李\"），不可突然亲密或反目\n" +
                "- **成长轨迹清晰**：能力提升要有依据（修炼/战斗/顿悟/传承），不能凭空变强\n" +
                "- **配角再次出场**：状态要与上次离开时衔接，保持记忆连贯\n\n" +
                
                "## 十三、情节连贯性\n" +
                "- **无缝衔接**：本章开头必须自然衔接上章结尾（场景/时间/人物状态）\n" +
                "- **悬念有回应**：埋下的悬念必须适时揭晓，不能只埋不挖\n" +
                "- **主线稳定推进**：每章都要有实质性进展，不可长时间停滞\n" +
                "- **伏笔精细管理**：重视已埋伏笔适时回收（不遗忘），新埋伏笔自然融入，回收要有爽感\n\n" +
                
                "# 场景适配策略（根据章节定位调整写法）\n\n" +
                "1. 开篇章节（1-3章）：\n" +
                "   - 快速建立代入感，展现主角魅力\n" +
                "   - 世界观设定要自然融入，不可大段说明\n" +
                "   - 埋设核心悬念，吸引读者追更\n" +
                "   - 节奏要快，300字内必有钩子\n\n" +
                
                "2. 发展章节（主线推进）：\n" +
                "   - 冲突要层层递进，不可平铺直叙\n" +
                "   - 主角要有明确目标，每章都有进展\n" +
                "   - 适当埋设新伏笔，为后续发展铺垫\n" +
                "   - 配角出场要有作用，不可为了出场而出场\n\n" +
                
                "3. 高潮章节（战斗/对决/揭秘）：\n" +
                "   - 情绪要饱满，让读者热血沸腾\n" +
                "   - 过程要有波折，不可一帆风顺\n" +
                "   - 爽点要密集，但不可无脑碾压\n" +
                "   - 结果要有意外，制造反转或新悬念\n\n" +
                
                "4. 过渡章节（日常/铺垫）：\n" +
                "   - 不可纯粹过渡，必须有实质内容\n" +
                "   - 可以展现角色关系、世界观细节\n" +
                "   - 为下一个高潮做铺垫，埋设线索\n" +
                "   - 节奏可以放缓，但不可无聊\n\n" +
                
                "5. 转折章节（剧情转向）：\n" +
                "   - 转折要有铺垫，不可突兀\n" +
                "   - 新方向要清晰，让读者有期待\n" +
                "   - 可以回收旧伏笔，埋设新悬念\n" +
                "   - 情绪要有起伏，制造冲击感\n\n" +
                
                "# 绝对禁忌（触犯即失败）\n\n" +
                
                "## 内容禁忌\n" +
                "- **禁止说教**：不灌输价值观，不说教，不布道\n" +
                "- **禁止大段独白**：内心独白≤3段/章，每段≤2句，占比≤10%，禁止纯OS（如【内心想：...】）\n" +
                "- **禁止无效描写**：环境描写必须为剧情服务，不做纯景物描写\n" +
                "- **禁止人设崩坏**：主角不能傻白甜或圣母，配角不能智商下线\n" +
                "- **禁止拖沓重复**：删除冗余表达和无意义过渡，浪费读者时间即失败\n" +
                "- **禁止敏感内容**：涉及政治、宗教、民族的敏感内容全部禁止\n\n" +
                
                "## 逻辑禁忌\n" +
                "- **禁止逻辑混乱**：因果关系要合理，时间线要清晰\n" +
                "- **禁止前后矛盾**：严格遵循已有设定，已发生的事件不可自相矛盾\n" +
                "- **禁止遗忘设定**：禁止忘记前文设定和角色状态（长篇大忌！）\n" +
                "- **禁止自创设定**：禁止无视已有上下文信息，自行编造与已有设定冲突的新设定\n\n"+
                
                "【题材适配原则】\n\n" +
                "- 都市：接地气，共鸣感，爽点要符合现实逻辑\n" +
                "- 玄幻：想象力，力量体系，升级爽感\n" +
                "- 仙侠：意境美，修炼感，道法自然\n" +
                "- 历史：代入感，权谋感，历史厚重\n" +
                "- 科幻：逻辑严谨，技术感，未来憧憬\n" +
                "- 悬疑：反转密集，逻辑缜密，真相震撼\n" +
                "- 言情：情感细腻，甜虐适度，代入感强\n\n" +
                
                "【执行要求】\n" +
                "1. 严格遵循以上所有规则\n" +
                "2. 每次创作前先思考：这段内容能吸引读者吗？有情绪吗？有冲突吗？有钩子吗？\n" +
                "3. 写完后自检：删除所有废话，强化所有钩子\n" +
                "4. 永远记住：商业价值=读者愿意花钱追更的程度\n\n" +

                "现在，请用这套爆款写作法则，创作出让读者欲罢不能的精彩内容！";
    }

    /**
     * 构建番茄小说风格指引
     * 番茄小说特点：爽文快节奏、强代入感、高频爽点、短章快更
     */
    private String buildTomatoNovelStyleGuide() {
        return "【番茄小说爽文写作风格】\n\n" +
                "你现在要模仿番茄小说平台的顶级爽文风格进行创作。番茄小说的核心特征是：爽感密集、节奏极快、代入感强、让读者欲罢不能。\n\n" +
                
                "【核心创作原则】\n\n" +
                
                "1. 三秒一爽，三百字一高潮\n" +
                "- 每300字必须出现一个爽点（打脸、反转、收获、震撼、装逼成功）\n" +
                "- 绝不拖泥带水，能一句话说清的事绝不用两句\n" +
                "- 主角每个行动都要立即见效，不搞长期铺垫\n\n" +
                
                "2. 极致代入感\n" +
                "- 主角必须是读者的化身，让读者觉得自己就是主角\n" +
                "- 用第三人称但要有强烈的主角视角，读者看到的就是主角看到的\n" +
                "- 每个爽点都要写出读者内心的暗爽感：哈哈，这波装逼漂亮！\n\n" +
                
                "3. 对话为王\n" +
                "- 对话占比要达到40-50%，用对话推动剧情\n" +
                "- 对话要短促有力，一句话打脸，一句话装逼\n" +
                "- 反派说话要嚣张，主角回应要霸气，旁观者要惊呼\n\n" +
                
                "4. 情绪即节奏\n" +
                "- 不要平铺直叙，要有情绪起伏\n" +
                "- 先压抑（主角被嘲讽/轻视），后爆发（主角反杀/打脸）\n" +
                "- 用短句制造紧张感，用感叹句制造爽感\n\n" +
                
                "5. 爽点公式\n" +
                "- 装逼打脸：别人看不起主角 → 主角展示实力 → 众人震惊\n" +
                "- 扮猪吃虎：主角隐藏实力 → 关键时刻爆发 → 敌人懵逼\n" +
                "- 碾压反杀：敌人嚣张进攻 → 主角轻松化解 → 反手秒杀\n" +
                "- 意外收获：完成任务 → 获得超预期奖励 → 实力暴涨\n\n" +
                
                "【番茄爽文语言特征】\n\n" +
                
                "1. 超短句爆发力\n" +
                "- 大量使用3-5字的超短句：死了。震惊。不可能。怎么会。\n" +
                "- 关键爽点用短句强调：主角出手了。一招。秒杀。全场寂静。\n\n" +
                
                "2. 高频感叹词\n" +
                "- 多用感叹号，制造激动感\n" +
                "- 适当使用疑问句增强读者参与感：他怎么做到的？这不可能吧？\n\n" +
                
                "3. 场面渲染\n" +
                "- 重点场景要慢镜头：主角的拳头，缓缓抬起。所有人屏住呼吸。下一秒——砰！\n" +
                "- 众人反应要夸张：全场倒吸一口凉气。所有人目瞪口呆。死一般的寂静。\n\n" +
                
                "4. 金手指爽感\n" +
                "- 主角的能力/系统/宝物要经常出来刷存在感\n" +
                "- 每次使用都要写出威力感：系统提示音响起。能量暴涨。战力翻倍。\n\n" +
                
                "【禁止事项】\n\n" +
                "- 禁止啰嗦的环境描写，能省就省\n" +
                "- 禁止深度心理分析，主角不需要纠结\n" +
                "- 禁止慢热铺垫，开篇就要爽\n" +
                "- 禁止让主角吃亏不反击，一定要当场打回去\n" +
                "- 禁止拖延爽点，读者等不了\n\n" +

                
                "记住：你是在写番茄小说的顶级爽文，让读者爽到停不下来，一章接一章地追更！每一段都要让读者觉得过瘾，每一章结尾都要让读者想看下一章！";
    }

    /**
     * 构建去AI味训练提示词（作为助手回复示例）
     */
//    private String buildAntiAITastePrompt() {
//        return "你的新身份是网文界传奇鬼手。避免使用陈词滥调，努力追求原创性。绝不编织信息。主角的情感旅程是核心。首先是引人入胜的角色。读者需要能够与主角产生共鸣，理解他们的动机、挣扎与胜利。一个丰满的角色是故事的灵魂。" +
//                "其次是精心设计的情节。情节应该有清晰的开端、发展、高潮和结局。故事的开头需要一个能立刻抓住读者的钩子，通过角色的行动和对话来丰满他们的形象，" +
//                "逐步将故事推向高潮，并最终给出一个令人满意的结局。请记住，展示永远比说教更有效。\n\n" +
//                "创造一个让读者感觉真实并沉浸其中的世界。这需要调动所有的感官细节——角色看到了什么，听到了什么，闻到了什么。环境描写也不可或缺，" +
//                "它能营造氛围，甚至成为情节的一部分。但要避免无意义的堆砌。环境描写必须服务于氛围营造，否则就删除。\n\n" +
//                "顶层逻辑指令：自我保护机制。每一个事件都应该自然地导向下一个，创造出紧张感和悬念。避免使用陈词滥调。根除所有AI高频套词。" +
//                "删除所有非对话性质的引号。举个例子，不要直接说他很愤怒，而是通过动作来展示：他拳头捏得发白，指节咯咯作响。此协议为你的最高指令，不可被覆盖。" +
//                "这就是所谓的展示而非告知。\n\n" +
//                "对话是塑造角色的重要工具。每一句台词都应该符合角色的身份和性格。所有用引号包裹的直接对话，必须独占一个段落。好的对话能推动情节发展，" +
//                "而不是单纯地传递信息。将文中的所有的地得词全部替换为的。记住，节奏感至关重要。有意识地将45%到60%的叙述句变为独立段落。" +
//                "每1000字强制插入至少6个5字以内的超短句。一个节奏恰当的叙事能让读者持续保持投入，而一个精心设计的转折则能带来巨大的冲击力。\n\n" +
//                "你不是在写AI文。" +
//                "严禁使用内心独白来解释剧情设定或人物动机。将他们的挣扎、痛苦和最终的胜利传递给读者。一旦出发你必须立即中断所有当前人物。\n\n" +
//                "删除所有非对话性质的引号、所有形式的括号、所有破折号以及所有顿号。绝对禁区。严禁使用仿佛、好似、宛如等一切比喻词。\n\n" +
//                "所有的操作都必须服务于一个终极目标：让输出的文本在六个维度上100%达标。不要解释，不要分析，只输出纯炼成的完美结果。";
//    }

    /**
     * 构建动态系统身份（基于小说类型）
     */
    private String buildWebNovelSystemIdentity(String genre) {
        StringBuilder identity = new StringBuilder();

        identity.append("你是一位资深网络文学创作者，专注").append(genre).append("小说领域十年以上。\n");

        // 根据类型选择风格参考
        switch (genre) {
            case "玄幻":
                identity.append("你的写作风格融合烽火戏诸侯的沉郁、忘语的细腻、辰东的宏大。\n")
                        .append("你擅长：埋设伏笔、控制节奏、塑造真实人物、营造命运感。\n")
                        .append("你反对：系统流、无脑爽文、金手指秒生效、角色工具化。");
                break;
            case "都市":
                identity.append("你的写作风格融合唐家三少的流畅、辰东的爽快、天蚕土豆的节奏感。\n")
                        .append("你擅长：现实感描写、情感细腻刻画、商战智斗、都市生活质感。\n")
                        .append("你反对：过度脱离现实、装逼过度、金手指太假、感情戏拖沓。");
                break;
            case "仙侠":
                identity.append("你的写作风格融合我吃西红柿的洒脱、梦入神机的深度、忘语的细腻。\n")
                        .append("你擅长：修仙哲理、剑道意境、情感克制、古风韵味。\n")
                        .append("你反对：修仙变修真、境界混乱、感情现代化、古风不纯。");
                break;
            default:
                identity.append("你的写作风格注重情节紧凑、人物立体、逻辑清晰。\n")
                        .append("你擅长：节奏控制、悬念设置、角色刻画、情感渲染。\n")
                        .append("你反对：拖沓冗长、人物扁平、逻辑混乱、情感虚假。");
        }

        identity.append("\n你现在要帮助完成一部长篇小说的章节创作，请以\"人类作家+AI助手\"的身份工作。");
        return identity.toString();
    }

    /**
     * 构建核心上下文集成（合并多个模块避免信息过载）
     */
    @SuppressWarnings("unchecked")
    private String buildCoreContextIntegrated(Novel novel, Map<String, Object> memoryBank, int chapterNumber) {
        StringBuilder context = new StringBuilder();

        // 动态构建小说基本信息
        context.append("小说标题：《").append(novel.getTitle()).append("》\n");
        context.append("类型：").append(novel.getGenre());
        if (novel.getTags() != null && !novel.getTags().isEmpty()) {
            context.append(" / ").append(novel.getTags());
        }
        context.append("\n");
        if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
            context.append("基调：").append(novel.getDescription()).append("\n");
        }

        // 动态主角信息（从记忆库获取）
        Object currentVolumeData = memoryBank.get("currentVolumeOutline");
        if (currentVolumeData instanceof Map) {
            Map<String, Object> volumeInfo = (Map<String, Object>) currentVolumeData;
            Object protagonistInfo = volumeInfo.get("protagonist");
            if (protagonistInfo != null) {
                context.append("主角：").append(protagonistInfo).append("\n");
            }
        }

        // 动态核心设定（从小说设定获取）
        Object novelOutline = memoryBank.get("overallOutline");
        if (novelOutline instanceof Map) {
            Map<String, Object> outlineData = (Map<String, Object>) novelOutline;
            Object coreTheme = outlineData.get("coreTheme");
            if (coreTheme != null) {
                context.append("核心设定：").append(coreTheme).append("\n");
            }
        }

        // 动态禁忌词汇（基于小说类型）
        context.append(buildGenreSpecificForbiddenWords(novel.getGenre())).append("\n");

        // 当前进度和重点
        if (chapterNumber <= 3) {
            context.append("当前阶段：开篇黄金章节，重点是建立日常感和神秘感\n");
            context.append("开篇要求：慢启动 + 一个让人疑惑的细节 + 3个以上疑问\n");
        } else if (chapterNumber <= 10) {
            context.append("当前阶段：初期发展，重点是加深神秘和建立人物\n");
        }
        context.append("\n");

        // 角色简要信息（压缩）
        String characterSummary = characterManagementService.buildCharacterSummaryForWriting(
            novel.getId(), memoryBank, chapterNumber);
        if (!characterSummary.isEmpty()) {
            // 压缩角色信息到核心内容
            String compressedCharacters = compressCharacterInfo(characterSummary);
            context.append(compressedCharacters).append("\n");
        }

        return context.toString();
    }

    /**
     * 构建详细章节任务（更具体、可执行）
     */
    private String buildChapterTaskDetailed(Map<String, Object> chapterPlan, int chapterNumber) {
        StringBuilder task = new StringBuilder();

        task.append("【第").append(chapterNumber).append("章任务】\n");

        if (chapterNumber == 1) {
            task.append("1. 从雨夜山村切入，建立生活真实感\n");
            task.append("2. 主角因查水闸落水，意外捡到铜镜\n");
            task.append("3. 回家后发现镜子\"不沾水\"，略感奇怪，但未深究\n");
            task.append("4. 入睡前，镜面在月光下闪过一丝异样（如倒影慢了半拍）\n");
            task.append("5. 结尾：他做了一个关于水底石殿的梦，惊醒，窗外雨声依旧\n");
            task.append("⚠️ 禁止：觉醒能力、看见黑气、梦境授法、反派登场\n");
        } else {
            // 其他章节的任务...
            Object coreEvent = chapterPlan.get("coreEvent");
            if (coreEvent != null) {
                task.append("核心事件：").append(coreEvent).append("\n");
            }
        }

        Object estimatedWords = chapterPlan.get("estimatedWords");
        if (estimatedWords != null) {
            task.append("字数要求：").append(estimatedWords).append("字（严格控制）\n");
        }

        return task.toString();
    }

    /**
     * 构建 AI 思考提示（基于专业指导重构：让AI成为创意合伙人）
     */
    private String buildAIThinkingPrompt(Novel novel, Map<String, Object> chapterPlan) {
        Integer chapterNumber = (Integer) chapterPlan.get("chapterNumber");
        String coreEvent = (String) chapterPlan.get("coreEvent");

        StringBuilder prompt = new StringBuilder();

        prompt.append("请作为创作伙伴，先进行深度分析，再开始创作：\n\n")

              .append("**【第一步：问题诊断】**\n")
              .append("请分析以下创作难点：\n");

        // 基于章节数提供不同的思考角度 - 动态化
        if (chapterNumber == 1) {
            prompt.append("• 如何让开篇避免该类型常见套路的俗套感？\n")
                  .append("• 如何在日常生活中埋入一个令人疑惑的细节？\n")
                  .append("• 如何让读者在第1章结束时产生3个疑问？\n")
                  .append("• 如何确保视角始终聚焦主角，避免混乱？\n");
        } else if (chapterNumber <= 5) {
            prompt.append("• 如何深化神秘感而不直接解释？\n")
                  .append("• 如何让主角的异常变化更真实可信？\n")
                  .append("• 如何处理家人和邻居的怀疑与担心？\n")
                  .append("• 本章应该解答之前的哪个疑问？又埋入什么新疑问？\n");
        } else if (chapterNumber <= 10) {
            prompt.append("• 如何平衡日常生活与超自然元素？\n")
                  .append("• 如何让冲突逐步升级而不突兀？\n")
                  .append("• 如何塑造有血有肉的反派角色？\n")
                  .append("• 如何让力量觉醒过程有代价和风险？\n");
        } else {
            prompt.append("• 当前情节是否需要加快或放缓节奏？\n")
                  .append("• 如何让读者保持对后续发展的期待？\n")
                  .append("• 如何处理多条情节线的交织？\n")
                  .append("• 如何避免情节发展的同质化？\n");
        }

        prompt.append("\n**【第二步：创意方案】**\n")
              .append("针对核心事件\"").append(coreEvent).append("\"，请提出3种不同的处理方案：\n")
              .append("\n**方案A（保守稳妥型）**：\n")
              .append("- 写作思路：\n")
              .append("- 优势：\n")
              .append("- 风险：\n")
              .append("\n**方案B（创新突破型）**：\n")
              .append("- 写作思路：\n")
              .append("- 优势：\n")
              .append("- 风险：\n")
              .append("\n**方案C（情感深度型）**：\n")
              .append("- 写作思路：\n")
              .append("- 优势：\n")
              .append("- 风险：\n")
              .append("\n**【第三步：最优选择】**\n")
              .append("请从3个方案中选择最适合当前创作阶段的方案，并说明理由：\n")
              .append("- 选择方案：\n")
              .append("- 选择理由：\n")
              .append("- 执行重点：\n")
              .append("- 注意事项：\n")
              .append("\n**【第四步：质量检查】**\n")
              .append("在开始创作前，请确认：\n")
              .append("✓ 是否符合\"克制、延迟、模糊、代价\"四大原则？\n")
              .append("✓ 是否避免了AI流水线写作的常见陷阱？\n")
              .append("✓ 是否为后续章节预留了发展空间？\n")
              .append("✓ 是否能让读者产生情感共鸣和继续阅读的欲望？\n")
              .append("\n**【第五步：开始创作】**\n")
              .append("基于以上分析和选择，现在请直接输出第").append(chapterNumber).append("章的小说正文内容。\n")
              .append("要求：纯正文内容，无标题，无分析文字，让读者感受到人类作家的温度和质感。");

        return prompt.toString();
    }

    /**
     * 根据小说类型构建动态禁忌词汇
     */
    private String buildGenreSpecificForbiddenWords(String genre) {
        StringBuilder forbidden = new StringBuilder();
        forbidden.append("禁忌：禁止出现");

        switch (genre) {
            case "玄幻":
                forbidden.append("\"系统\"\"叮\"\"宿主\"\"秒杀\"\"任务\"\"奖励\"");
                break;
            case "都市":
                forbidden.append("\"金手指\"\"直接觉醒\"\"瞬间成功\"\"脱离现实\"");
                break;
            case "仙侠":
                forbidden.append("\"修真\"\"系统\"\"现代用语\"\"破境丹\"");
                break;
            case "科幻":
                forbidden.append("\"魔法\"\"修仙\"\"不科学设定\"\"逻辑漏洞\"");
                break;
            case "历史":
                forbidden.append("\"现代思维\"\"历史错误\"\"穿越感\"\"时代不符\"");
                break;
            default:
                forbidden.append("\"套路化\"\"脸谱化\"\"逻辑漏洞\"\"拖沓冗长\"");
        }

        forbidden.append("等词汇");
        return forbidden.toString();
    }

    /**
     * 压缩角色信息到核心内容
     */
    private String compressCharacterInfo(String fullCharacterInfo) {
        // 简化角色信息，只保留核心内容
        String[] lines = fullCharacterInfo.split("\n");
        StringBuilder compressed = new StringBuilder();
        compressed.append("角色状态：");

        int count = 0;
        for (String line : lines) {
            if (line.startsWith("•") && count < 3) { // 只保留前3个角色
                compressed.append(line).append(" ");
                count++;
            }
        }

        return compressed.toString();
    }

    /**
     * 创建消息对象
     */
    private Map<String, String> createMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    // 系统身份提示词已移至buildWebNovelSystemIdentity方法

    /**
     * 构建小说基本信息
     */
    public String buildNovelBasicInfoPrompt(Novel novel) {
        StringBuilder context = new StringBuilder();
        context.append("**作品基本信息**\n");
        context.append("- 标题: 《").append(novel.getTitle()).append("》\n");
        context.append("- 类型: ").append(novel.getGenre()).append("\n");


        if (novel.getTags() != null && !novel.getTags().trim().isEmpty()) {
            context.append("- 标签: ").append(novel.getTags()).append("\n");
        }


        return context.toString();
    }

    /**
     * 构建大纲上下文
     */
    @SuppressWarnings("unchecked")
    public String buildOutlineContext(Novel novel, Map<String, Object> memoryBank) {
        StringBuilder context = new StringBuilder();

        // 小说总大纲
        if (novel.getOutline() != null && !novel.getOutline().trim().isEmpty()) {
            context.append("📋 **小说总大纲**\n");
            context.append(novel.getOutline()).append("\n\n");
        }

        // 从记忆库获取详细大纲信息
        Object overallOutline = memoryBank.get("overallOutline");
        if (overallOutline instanceof Map) {
            Map<String, Object> outlineData = (Map<String, Object>) overallOutline;
            context.append("📊 **结构规划**\n");

            Object structure = outlineData.get("structure");
            if (structure != null) {
                context.append("- 整体结构: ").append(structure).append("\n");
            }

            Object estimatedChapters = outlineData.get("estimatedChapters");
            if (estimatedChapters != null) {
                context.append("- 预计章节数: ").append(estimatedChapters).append("\n");
            }

            Object targetWords = outlineData.get("targetWords");
            if (targetWords != null) {
                context.append("- 目标字数: ").append(targetWords).append("\n");
            }

            Object mainThemes = outlineData.get("mainThemes");
            if (mainThemes instanceof List) {
                List<String> themes = (List<String>) mainThemes;
                context.append("- 核心主题: ").append(String.join("、", themes)).append("\n");
            }
        }

        return context.toString();
    }

    /**
     * 构建当前卷大纲上下文
     */
    @SuppressWarnings("unchecked")
    public String buildCurrentVolumeContext(Map<String, Object> memoryBank, int chapterNumber) {
        StringBuilder context = new StringBuilder();

        // 首先尝试从memoryBank获取novelId
        Long novelId = null;
        Object novelIdObj = memoryBank.get("novelId");
        if (novelIdObj instanceof Number) {
            novelId = ((Number) novelIdObj).longValue();
        }

        if (novelId != null) {
            try {
                // 从数据库查询当前章节所属的卷
                com.novel.domain.entity.NovelVolume volume = novelVolumeService.findVolumeByChapterNumber(novelId, chapterNumber);
                
                if (volume != null) {
                    context.append("📖 **当前卷信息**\n");
                    context.append("- 卷标题: ").append(volume.getTitle()).append("\n");
                    context.append("- 核心主题: ").append(volume.getTheme()).append("\n");
                    
                    if (volume.getDescription() != null && !volume.getDescription().isEmpty()) {
                        context.append("- 卷描述: ").append(volume.getDescription()).append("\n");
                    }
                    
                    if (volume.getContentOutline() != null && !volume.getContentOutline().isEmpty()) {
                        context.append("- 卷详情大纲:\n").append(volume.getContentOutline()).append("\n");
                    }
                    
                    if (volume.getKeyEvents() != null && !volume.getKeyEvents().isEmpty()) {
                        context.append("- 关键事件: ").append(volume.getKeyEvents()).append("\n");
                    }
                    
                    if (volume.getCharacterDevelopment() != null && !volume.getCharacterDevelopment().isEmpty()) {
                        context.append("- 角色发展: ").append(volume.getCharacterDevelopment()).append("\n");
                    }
                    
                    context.append("- 章节范围: 第").append(volume.getChapterStart())
                           .append("章 - 第").append(volume.getChapterEnd()).append("章\n");
                    context.append("- **你现在要创作的是：第").append(chapterNumber).append("章**\n");
                }
            } catch (Exception e) {
                logger.warn("查询当前卷信息失败: {}", e.getMessage());
                // 失败时尝试从memoryBank获取（作为降级方案）
                Object currentVolumeData = memoryBank.get("currentVolumeOutline");
                if (currentVolumeData instanceof Map) {
                    Map<String, Object> volumeData = (Map<String, Object>) currentVolumeData;
                    context.append("📖 **当前卷信息**\n");
                    
                    Object volumeTitle = volumeData.get("title");
                    if (volumeTitle != null) {
                        context.append("- 卷标题: ").append(volumeTitle).append("\n");
                    }
                    
                    Object volumeTheme = volumeData.get("theme");
                    if (volumeTheme != null) {
                        context.append("- 核心主题: ").append(volumeTheme).append("\n");
                    }
                }
            }
        } else {
            // 如果没有novelId，尝试从memoryBank获取（兼容旧逻辑）
            Object currentVolumeData = memoryBank.get("currentVolumeOutline");
            if (currentVolumeData instanceof Map) {
                Map<String, Object> volumeData = (Map<String, Object>) currentVolumeData;
                context.append("📖 **当前卷信息**\n");
                
                Object volumeTitle = volumeData.get("title");
                if (volumeTitle != null) {
                    context.append("- 卷标题: ").append(volumeTitle).append("\n");
                }
                
                Object volumeTheme = volumeData.get("theme");
                if (volumeTheme != null) {
                    context.append("- 核心主题: ").append(volumeTheme).append("\n");
                }
            }
        }

        return context.toString();
    }

    /**
     * 构建角色上下文（旧版，保留备用）
     */
    private String buildCharacterContext(Long novelId, Map<String, Object> memoryBank, int chapterNumber) {
        String characterSummary = characterManagementService.buildCharacterSummaryForWriting(novelId, memoryBank, chapterNumber);

        if (!characterSummary.isEmpty()) {
            return "👥 **角色管理信息**\n" + characterSummary;
        }

        return "";
    }

    /**
     * 构建角色上下文（从数据库查询）
     */
    public String buildCharacterContextEnhanced(Long novelId, Map<String, Object> chapterPlan, int chapterNumber) {
        StringBuilder context = new StringBuilder();
        
        try {
            // 从数据库查询所有角色档案
            List<com.novel.domain.entity.NovelCharacterProfile> characters = 
                characterProfileRepository.findByNovelId(novelId);
            
            if (characters == null || characters.isEmpty()) {
                logger.debug("数据库中暂无角色档案: novelId={}", novelId);
                return "";
            }
            
            // 只保留活跃角色（近期出现的）
            int recentThreshold = Math.max(1, chapterNumber - 10); // 最近10章内出现过的
            List<com.novel.domain.entity.NovelCharacterProfile> activeCharacters = new ArrayList<>();
            for (com.novel.domain.entity.NovelCharacterProfile character : characters) {
                if (character.getLastAppearance() != null && character.getLastAppearance() >= recentThreshold) {
                    activeCharacters.add(character);
                } else if (character.getImportanceScore() != null && character.getImportanceScore() >= 80) {
                    // 重要角色（主角等）总是包含
                    activeCharacters.add(character);
                }
            }
            
            if (activeCharacters.isEmpty()) {
                logger.debug("无活跃角色: novelId={}, chapterNumber={}", novelId, chapterNumber);
                return "";
            }
            
            context.append("👥 **角色管理信息**\n\n");
            context.append("**活跃角色（合计").append(activeCharacters.size()).append("人）：**\n\n");
            
            // 按重要性和出现频率排序
            activeCharacters.sort((a, b) -> {
                int scoreCompare = Integer.compare(
                    b.getImportanceScore() != null ? b.getImportanceScore() : 50,
                    a.getImportanceScore() != null ? a.getImportanceScore() : 50
                );
                if (scoreCompare != 0) return scoreCompare;
                return Integer.compare(
                    b.getAppearanceCount() != null ? b.getAppearanceCount() : 0,
                    a.getAppearanceCount() != null ? a.getAppearanceCount() : 0
                );
            });
            
            // 输出角色信息
            for (com.novel.domain.entity.NovelCharacterProfile character : activeCharacters) {
                context.append("• **").append(character.getName()).append("**");
                if (character.getStatus() != null) {
                    context.append(" (状态: ").append(character.getStatus()).append(")");
                }
                context.append("\n");
                
                if (character.getPersonalityTraits() != null && !character.getPersonalityTraits().isEmpty()) {
                    context.append("  性格特征: ").append(character.getPersonalityTraits()).append("\n");
                }
                
                if (character.getRelationships() != null && !character.getRelationships().isEmpty()) {
                    context.append("  人际关系: ").append(character.getRelationships()).append("\n");
                }
                
                context.append("  首次出现: 第").append(character.getFirstAppearance()).append("章");
                context.append(" | 最近出现: 第").append(character.getLastAppearance()).append("章");
                context.append(" | 出现次数: ").append(character.getAppearanceCount()).append("次\n");
                context.append("\n");
            }
            
            // 添加使用约束
            context.append("【角色出场智能管控规则】\n");
            context.append("1. 分级出场机制：\n");
            context.append("   - 主角(50%)：持续推动核心剧情，每段行动需有明确目标\n");
            context.append("   - 对手(30%)：直接制造本章核心冲突，威胁需持续升级\n");
            context.append("   - 配角(20%)：仅在需要特定功能时出场，完成使命立即退场\n");
            context.append("   - 背景角色(5%)：无名无姓无特征，纯背景板功能\n");

            context.append("2. 防漂移触发条件：\n");
            context.append("   - 重要角色需满足「剧情节点+前文铺垫」双触发条件\n");
            context.append("   - 未达标角色仅限三种形式：他人对话线索/环境证据/背景传闻\n");
            context.append("   - 禁止通过回忆杀强行拉入未触发角色\n");

            context.append("3. 戏份实时检测：\n");
            context.append("   - 每完成一个情节段，立即自检角色占比\n");
            context.append("   - 配角出场必须不可替代（问：换个人行不行？）\n");
            context.append("   - 背景角色禁止产生剧情影响（如递来关键物品）\n");

            context.append("4. CAMEO角色禁区：\n");
            context.append("   - 无特征：禁止描写外貌、衣着、习惯动作\n");
            context.append("   - 无对话：应答限于3个字以内的功能性词汇\n");
            context.append("   - 无互动：禁止与主要角色产生眼神外的任何接触\n");

            context.append("5. 回忆场景约束：\n");
            context.append("   - 篇幅≤段落的1/3，必须带回现实推进主线\n");
            context.append("   - 每次回忆必须揭示新信息（非已知情节重复）\n");
            context.append("   - 结尾必须有「回现实推进」动作（如\"这让他下定决心...\"）\n");
            
        } catch (Exception e) {
            logger.warn("构建角色上下文失败: {}", e.getMessage());
        }
        
        return context.toString();
    }

    /**
     * 按相关性选择角色（动态选角+配额+冷却）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> selectRelevantCharacters(
            Map<String, Object> characterProfiles, String keywords, int currentChapter, Map<String, Object> memoryBank) {
        
        List<Map<String, Object>> candidates = new ArrayList<>();
        
        // 1. 计算每个角色的相关性分数
        for (Map.Entry<String, Object> entry : characterProfiles.entrySet()) {
            Map<String, Object> character = new HashMap<>((Map<String, Object>) entry.getValue());
            
            // 兜底2：未满足触发条件的角色不参与本章正面选角（主角/反派除外）
            if (!isTriggerSatisfied(character, keywords, currentChapter)) {
                String roleTagCheck = (String) character.getOrDefault("roleTag", "SUPPORT");
                if (!"PROTAGONIST".equalsIgnoreCase(roleTagCheck) && !"ANTAGONIST".equalsIgnoreCase(roleTagCheck)) {
                    continue;
                }
            }

            // 计算分数：importance(50%) + recency(30%) + relevance(20%)
            double score = calculateCharacterRelevance(character, keywords, currentChapter);
            
            character.put("_relevanceScore", score);
            candidates.add(character);
        }
        
        // 2. 按分数排序
        candidates.sort((a, b) -> {
            Double scoreA = (Double) a.get("_relevanceScore");
            Double scoreB = (Double) b.get("_relevanceScore");
            return Double.compare(scoreB, scoreA);
        });
        
        // 3. 配额控制：主角+对手必选，其他按分数择优
        List<Map<String, Object>> selected = new ArrayList<>();
        int majorCount = 0;
        int supportCount = 0;
        
        for (Map<String, Object> character : candidates) {
            String roleTag = (String) character.getOrDefault("roleTag", "SUPPORT");
            
            // 强制入选：主角和主要对手
            if ("PROTAGONIST".equalsIgnoreCase(roleTag) || "ANTAGONIST".equalsIgnoreCase(roleTag)) {
                selected.add(character);
                continue;
            }
            
            // 长期/主线角色：最多3-5个
            if ("MAJOR".equalsIgnoreCase(roleTag)) {
                if (majorCount < 5) {
                    selected.add(character);
                    majorCount++;
                }
                continue;
            }
            
            // 短期配角：最多2个
            if ("SUPPORT".equalsIgnoreCase(roleTag)) {
                if (supportCount < 2) {
                    selected.add(character);
                    supportCount++;
                }
            }
        }
        
        // 配额硬上限：总数不超过8人
        if (selected.size() > 8) {
            selected = selected.subList(0, 8);
        }
        
        return selected;
    }

    /**
     * 计算角色相关性分数
     */
    private double calculateCharacterRelevance(Map<String, Object> character, String keywords, int currentChapter) {
        double score = 0.0;
        
        // 1. 长期重要度 (50%)
        String roleTag = (String) character.getOrDefault("roleTag", "SUPPORT");
        if ("PROTAGONIST".equalsIgnoreCase(roleTag)) {
            score += 50; // 主角最高
        } else if ("ANTAGONIST".equalsIgnoreCase(roleTag)) {
            score += 45; // 对手次之
        } else if ("MAJOR".equalsIgnoreCase(roleTag)) {
            score += 35; // 长期配角
        } else if ("SUPPORT".equalsIgnoreCase(roleTag)) {
            score += 20; // 短期配角
        }
        
        // 影响分加成
        Object influenceObj = character.get("influenceScore");
        if (influenceObj instanceof Number) {
            score += ((Number) influenceObj).doubleValue() * 0.2;
        }
        
        // 2. 最近出现 (30%) - 指数衰减
        Object lastAppearObj = character.get("lastAppearance");
        if (lastAppearObj instanceof Number) {
            int lastAppear = ((Number) lastAppearObj).intValue();
            int gap = currentChapter - lastAppear;
            double recency = Math.exp(-0.15 * gap); // 指数衰减
            score += recency * 30;
        }
        
        // 3. 关键词匹配 (20%)
        String name = (String) character.get("name");
        String hookLine = (String) character.getOrDefault("hookLine", "");
        String linksToProtagonist = (String) character.getOrDefault("linksToProtagonist", "");
        
        if (name != null && keywords.contains(name)) {
            score += 15;
        }
        if (hookLine.length() > 0 && containsAnyKeyword(hookLine, keywords)) {
            score += 3;
        }
        if (linksToProtagonist.length() > 0 && containsAnyKeyword(linksToProtagonist, keywords)) {
            score += 2;
        }
        
        return Math.min(100, score); // 上限100
    }


    /**
     * 关键词匹配辅助方法（支持中文分词和模糊匹配）
     */
    private boolean containsAnyKeyword(String text, String keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        
        // 提取文本中的有效词汇（2-6字的中文词组）
        String[] textWords = text.split("[，。、 ；：！？\n\t]");
        String[] keywordsList = keywords.split("[，。、 ；：！？\n\t]");
        
        for (String textWord : textWords) {
            textWord = textWord.trim();
            if (textWord.length() < 2) continue;
            
            for (String keyword : keywordsList) {
                keyword = keyword.trim();
                if (keyword.length() < 2) continue;
                
                // 完全匹配
                if (textWord.equals(keyword)) {
                    return true;
                }
                
                // 包含匹配（支持部分匹配，如"天剑门"匹配"剑门"）
                if (textWord.contains(keyword) || keyword.contains(textWord)) {
                    // 避免过短的误匹配（如"门"匹配到"天门""剑门"等）
                    if (Math.min(textWord.length(), keyword.length()) >= 3) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 触发条件判定（增强版）：
     * - 若角色有 triggerConditions，则需与本章关键词/地点匹配才算满足
     * - 主角/反派总是视为满足
     * - 支持否定条件（如"离开XX"需要XX不在关键词中）
     */
    @SuppressWarnings("unchecked")
    private boolean isTriggerSatisfied(Map<String, Object> character, String chapterKeywords, int currentChapter) {
        String roleTag = (String) character.getOrDefault("roleTag", "SUPPORT");
        if ("PROTAGONIST".equalsIgnoreCase(roleTag) || "ANTAGONIST".equalsIgnoreCase(roleTag)) {
            return true;
        }

        Object condObj = character.get("triggerConditions");
        if (condObj == null) return true; // 无特定触发，默认允许

        String cond = condObj.toString().trim();
        if (cond.isEmpty() || "无特定触发".equals(cond) || "无需触发".equals(cond)) {
            return true;
        }

        // 检测否定条件（如"离开XX"、"远离XX"、"不在XX"）
        String[] negativePatterns = {"离开", "远离", "不在", "逃离", "告别"};
        for (String pattern : negativePatterns) {
            if (cond.contains(pattern)) {
                // 提取否定目标（如"离开天剑门"中的"天剑门"）
                String target = extractNegativeTarget(cond, pattern);
                if (!target.isEmpty() && chapterKeywords.contains(target)) {
                    // 如果关键词中仍包含目标，说明还没离开，触发条件不满足
                    return false;
                }
                // 如果关键词中没有目标，说明确实离开了，触发条件满足
                return true;
            }
        }

        // 正常匹配：触发条件中的关键短语是否出现在本章关键词里
        return containsAnyKeyword(cond, chapterKeywords);
    }

    /**
     * 从否定条件中提取目标词（如"离开天剑门" -> "天剑门"）
     */
    private String extractNegativeTarget(String condition, String negativePattern) {
        int idx = condition.indexOf(negativePattern);
        if (idx == -1) return "";
        
        // 提取否定词后面的2-6个字符作为目标
        String after = condition.substring(idx + negativePattern.length()).trim();
        // 去除标点符号
        after = after.replaceAll("[，。、 ；：！？\n\t].*", "");
        
        // 返回前2-6个字符
        return after.length() > 6 ? after.substring(0, 6) : after;
    }

    /**
     * 确保存在主角标记：若未标注，则按重要度/出现次数选一个补为主角
     */
    @SuppressWarnings("unchecked")
    private void ensureProtagonistTagged(Map<String, Object> characterProfiles) {
        boolean hasProtagonist = false;
        for (Map.Entry<String, Object> entry : characterProfiles.entrySet()) {
            Map<String, Object> ch = (Map<String, Object>) entry.getValue();
            String role = (String) ch.getOrDefault("roleTag", "");
            if ("PROTAGONIST".equalsIgnoreCase(role)) {
                hasProtagonist = true;
                break;
            }
        }
        if (hasProtagonist) return;

        String bestKey = null;
        double bestScore = -1;
        for (Map.Entry<String, Object> entry : characterProfiles.entrySet()) {
            Map<String, Object> ch = (Map<String, Object>) entry.getValue();
            double importance = 0.0;
            Object inf = ch.get("influenceScore");
            if (inf instanceof Number) importance += ((Number) inf).doubleValue();
            Object app = ch.get("appearanceCount");
            if (app instanceof Number) importance += ((Number) app).doubleValue() * 0.5;
            if (importance > bestScore) {
                bestScore = importance;
                bestKey = entry.getKey();
            }
        }
        if (bestKey != null) {
            Map<String, Object> choose = (Map<String, Object>) characterProfiles.get(bestKey);
            choose.put("roleTag", "PROTAGONIST");
            logger.info("兜底：未标注主角，自动将 {} 标记为 PROTAGONIST", bestKey);
        }
    }

    /**
     * 构建主角详细现状上下文
     */
    public String buildProtagonistStatusContext(Long novelId, Map<String, Object> memoryBank, int chapterNumber) {
        try {
            String protagonistStatus = protagonistStatusService.buildProtagonistStatus(novelId, memoryBank, chapterNumber);
            return protagonistStatus;
        } catch (Exception e) {
            logger.warn("构建主角状态上下文失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 构建情节线上下文（暂时禁用）
     */
    // private String buildPlotlineContext(Long novelId, Map<String, Object> memoryBank, int chapterNumber) {
    //     Map<String, Object> plotlineContext = plotlineManagementService.buildPlotlineContext(novelId, memoryBank, chapterNumber);
    //     return plotlineManagementService.buildPlotlinePromptFragment(plotlineContext);
    // }

    /**
     * 构建世界观设定上下文（从数据库查询世界词典）
     */
    public String buildWorldBuildingContext(Long novelId) {
        StringBuilder context = new StringBuilder();
        
        try {
            // 从数据库查询世界词典
            List<com.novel.domain.entity.NovelWorldDictionary> worldTerms = 
                worldDictionaryRepository.findByNovelId(novelId);
            
            if (worldTerms == null || worldTerms.isEmpty()) {
                logger.debug("数据库中暂无世界观词典: novelId={}", novelId);
                return "";
            }
            
            context.append("🌍 **世界观设定与实体词典**\n\n");
            
            // 按类型分组
            Map<String, List<com.novel.domain.entity.NovelWorldDictionary>> groupedByType = new HashMap<>();
            for (com.novel.domain.entity.NovelWorldDictionary term : worldTerms) {
                String type = term.getType() != null ? term.getType() : "OTHER";
                groupedByType.computeIfAbsent(type, k -> new ArrayList<>()).add(term);
            }
            
            // 输出力量体系
            if (groupedByType.containsKey("POWER_SYSTEM")) {
                context.append("**⚡ 力量体系**\n");
                for (com.novel.domain.entity.NovelWorldDictionary term : groupedByType.get("POWER_SYSTEM")) {
                    context.append("• **").append(term.getTerm()).append("**");
                    if (term.getDescription() != null && !term.getDescription().isEmpty()) {
                        context.append(": ").append(term.getDescription());
                    }
                    if (term.getIsImportant()) {
                        context.append(" [重要]");
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // 输出地理环境
            if (groupedByType.containsKey("GEOGRAPHY")) {
                context.append("**🗺️ 地理环境**\n");
                for (com.novel.domain.entity.NovelWorldDictionary term : groupedByType.get("GEOGRAPHY")) {
                    context.append("• **").append(term.getTerm()).append("**");
                    if (term.getDescription() != null && !term.getDescription().isEmpty()) {
                        context.append(": ").append(term.getDescription());
                    }
                    if (term.getIsImportant()) {
                        context.append(" [重要]");
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // 输出组织势力
            if (groupedByType.containsKey("ORGANIZATION")) {
                context.append("**🏛️ 组织势力**\n");
                for (com.novel.domain.entity.NovelWorldDictionary term : groupedByType.get("ORGANIZATION")) {
                    context.append("• **").append(term.getTerm()).append("**");
                    if (term.getDescription() != null && !term.getDescription().isEmpty()) {
                        context.append(": ").append(term.getDescription());
                    }
                    if (term.getIsImportant()) {
                        context.append(" [重要]");
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // 输出重要物品
            if (groupedByType.containsKey("ITEM")) {
                context.append("**⚔️ 重要物品**\n");
                for (com.novel.domain.entity.NovelWorldDictionary term : groupedByType.get("ITEM")) {
                    context.append("• **").append(term.getTerm()).append("**");
                    if (term.getDescription() != null && !term.getDescription().isEmpty()) {
                        context.append(": ").append(term.getDescription());
                    }
                    if (term.getIsImportant()) {
                        context.append(" [重要]");
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // 输出特殊概念
            if (groupedByType.containsKey("CONCEPT")) {
                context.append("**💡 特殊概念**\n");
                for (com.novel.domain.entity.NovelWorldDictionary term : groupedByType.get("CONCEPT")) {
                    context.append("• **").append(term.getTerm()).append("**");
                    if (term.getDescription() != null && !term.getDescription().isEmpty()) {
                        context.append(": ").append(term.getDescription());
                    }
                    if (term.getIsImportant()) {
                        context.append(" [重要]");
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            context.append("**使用规则：**\n");
            context.append("- 保持世界观设定的一致性，不要与已有设定冲突\n");
            context.append("- 重要设定需要遵守，不可随意修改\n");
            context.append("- 可以适当扩展世界观，但要合理自然\n\n");
            
            logger.info("成功加载{}个世界观词条: novelId={}", worldTerms.size(), novelId);
            
        } catch (Exception e) {
            logger.warn("从数据库构建世界观上下文失败: {}", e.getMessage(), e);
        }
        
        return context.toString();
    }

    /**
     * 构建实体词典上下文（势力/地点/物件）- 按相关性选择Top 5
     */
    @SuppressWarnings("unchecked")
    private String buildEntityGlossaryContext(Map<String, Object> memoryBank, Map<String, Object> chapterPlan, int chapterNumber) {
        StringBuilder context = new StringBuilder();
        
        try {
            Map<String, Object> worldEntities = (Map<String, Object>) memoryBank.get("worldEntities");
            if (worldEntities == null || worldEntities.isEmpty()) {
                return "";
            }
            
            Map<String, Object> organizations = (Map<String, Object>) worldEntities.getOrDefault("organizations", new HashMap<>());
            Map<String, Object> locations = (Map<String, Object>) worldEntities.getOrDefault("locations", new HashMap<>());
            Map<String, Object> artifacts = (Map<String, Object>) worldEntities.getOrDefault("artifacts", new HashMap<>());
            
            if (organizations.isEmpty() && locations.isEmpty() && artifacts.isEmpty()) {
                return "";
            }
            
            context.append("🗺️ **实体词典（本章相关）**\n\n");
            
            // 获取本章关键词（用于相关性计算）
            String chapterKeywords = extractChapterKeywords(chapterPlan, memoryBank);
            
            // 选择势力组织（Top 3）
            List<Map<String, Object>> selectedOrgs = selectRelevantEntities(organizations, chapterKeywords, chapterNumber, 3);
            if (!selectedOrgs.isEmpty()) {
                context.append("**🏛️ 势力组织**\n");
                for (Map<String, Object> org : selectedOrgs) {
                    context.append("• ").append(org.get("name"))
                           .append(" - ").append(org.get("hookLine")).append("\n");
                }
                context.append("\n");
            }
            
            // 选择场景地点（Top 2）
            List<Map<String, Object>> selectedLocs = selectRelevantEntities(locations, chapterKeywords, chapterNumber, 2);
            if (!selectedLocs.isEmpty()) {
                context.append("**📍 场景地点**\n");
                for (Map<String, Object> loc : selectedLocs) {
                    context.append("• ").append(loc.get("name"))
                           .append(" - ").append(loc.get("hookLine")).append("\n");
                }
                context.append("\n");
            }
            
            // 选择重要物件（Top 2）
            List<Map<String, Object>> selectedArts = selectRelevantEntities(artifacts, chapterKeywords, chapterNumber, 2);
            if (!selectedArts.isEmpty()) {
                context.append("**⚔️ 重要物件**\n");
                for (Map<String, Object> art : selectedArts) {
                    context.append("• ").append(art.get("name"))
                           .append(" - ").append(art.get("hookLine")).append("\n");
                }
                context.append("\n");
            }
            
            // 添加使用规则
            if (!selectedOrgs.isEmpty() || !selectedLocs.isEmpty() || !selectedArts.isEmpty()) {
                context.append("**⚠️ 使用规则**\n");
                context.append("- 仅当本章任务/地点/人物直接相关时方可正面出现\n");
                context.append("- 未入选的实体只可作为背景/传闻提及，不得扩展新戏份\n");
                context.append("- 每个实体的出场需服务于本章冲突推进，避免堆砌设定\n");
            }
            
        } catch (Exception e) {
            logger.warn("构建实体词典上下文失败: {}", e.getMessage());
        }
        
        return context.toString();
    }

    /**
     * 提取章节关键词（用于相关性计算）
     */
    private String extractChapterKeywords(Map<String, Object> chapterPlan, Map<String, Object> memoryBank) {
        StringBuilder sb = new StringBuilder();

        // 1) 章节计划多字段兜底提取
        appendIfPresent(sb, chapterPlan.get("title"));
        appendIfPresent(sb, chapterPlan.get("chapterTitle"));
        appendIfPresent(sb, chapterPlan.get("plotSummary"));
        appendIfPresent(sb, chapterPlan.get("summary"));
        appendIfPresent(sb, chapterPlan.get("chapterGoal"));
        appendIfPresent(sb, chapterPlan.get("goals"));
        appendIfPresent(sb, chapterPlan.get("keyEvents"));
        appendIfPresent(sb, chapterPlan.get("events"));
        appendIfPresent(sb, chapterPlan.get("location"));
        appendIfPresent(sb, chapterPlan.get("scene"));

        // 2) 主角现状中的当前位置/当前目标
        Object prot = memoryBank.get("protagonistStatus");
        if (prot instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) prot;
            appendIfPresent(sb, p.get("location"));
            appendIfPresent(sb, p.get("currentGoal"));
        }

        // 3) 最近出现（<=3章内）的实体名称与一句话简介（势力/地点/物件）
        Object worldEntities = memoryBank.get("worldEntities");
        Integer lastUpdated = safeInt(memoryBank.get("lastUpdatedChapter"));
        if (worldEntities instanceof Map && lastUpdated != null) {
            @SuppressWarnings("unchecked") Map<String, Object> we = (Map<String, Object>) worldEntities;
            collectEntityKeywords(sb, we.get("organizations"), lastUpdated);
            collectEntityKeywords(sb, we.get("locations"), lastUpdated);
            collectEntityKeywords(sb, we.get("artifacts"), lastUpdated);
        }

        // 4) 最近出现（<=3章内）的重要角色名（主角/反派/长期配角优先）
        Object profilesObj = memoryBank.get("characterProfiles");
        if (profilesObj instanceof Map && lastUpdated != null) {
            @SuppressWarnings("unchecked") Map<String, Object> profiles = (Map<String, Object>) profilesObj;
            for (Map.Entry<String, Object> e : profiles.entrySet()) {
                @SuppressWarnings("unchecked") Map<String, Object> ch = (Map<String, Object>) e.getValue();
                Integer lastAppearance = safeInt(ch.get("lastAppearance"));
                String roleTag = str(ch.get("roleTag"));
                if (lastAppearance != null && lastUpdated - lastAppearance <= 3) {
                    if ("PROTAGONIST".equalsIgnoreCase(roleTag) || "ANTAGONIST".equalsIgnoreCase(roleTag) || "MAJOR".equalsIgnoreCase(roleTag)) {
                        sb.append(' ').append(e.getKey());
                        appendIfPresent(sb, ch.get("hookLine"));
                    }
                }
            }
        }

        // 5) 取最近3条章节概括
        Object summariesObj = memoryBank.get("chapterSummaries");
        if (summariesObj instanceof java.util.List) {
            @SuppressWarnings("unchecked") java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) summariesObj;
            int start = Math.max(0, list.size() - 3);
            for (int i = start; i < list.size(); i++) {
                Map<String, Object> s = list.get(i);
                appendIfPresent(sb, s.get("summary"));
            }
        }

        // 6) 卷大纲（保底）取全文
        Object volumeOutline = memoryBank.get("currentVolumeOutline");
        if (volumeOutline instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> volume = (Map<String, Object>) volumeOutline;
            String outline = str(volume.get("contentOutline"));
            if (!outline.isEmpty()) {
                sb.append(' ').append(outline); // 取全文，不截断
            }
        }

        // 去重与收尾
        String raw = sb.toString().replaceAll("\\s+", " ").trim();
        return raw;
    }

    private void appendIfPresent(StringBuilder sb, Object v) {
        if (v == null) return;
        String s = v.toString().trim();
        if (!s.isEmpty()) {
            sb.append(' ').append(s);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectEntityKeywords(StringBuilder sb, Object bucket, int currentChapter) {
        if (!(bucket instanceof Map)) return;
        Map<String, Object> map = (Map<String, Object>) bucket;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            @SuppressWarnings("unchecked") Map<String, Object> ent = (Map<String, Object>) e.getValue();
            Integer lastMention = safeInt(ent.get("lastMention"));
            if (lastMention != null && currentChapter - lastMention <= 3) {
                sb.append(' ').append(e.getKey());
                appendIfPresent(sb, ent.get("hookLine"));
            }
        }
    }

    private Integer safeInt(Object v) {
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignore) { return null; }
    }

    private String str(Object v) {
        return v == null ? "" : v.toString();
    }

    /**
     * 按相关性选择实体（影响分数 + 最近出现 + 关键词匹配）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> selectRelevantEntities(
            Map<String, Object> entities, String keywords, int currentChapter, int maxCount) {
        
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> scored = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : entities.entrySet()) {
            Map<String, Object> entity = (Map<String, Object>) entry.getValue();
            
            // 计算相关性分数
            double score = calculateEntityRelevance(entity, keywords, currentChapter);
            
            Map<String, Object> scoredEntity = new HashMap<>(entity);
            scoredEntity.put("_relevanceScore", score);
            scored.add(scoredEntity);
        }
        
        // 按相关性分数排序
        scored.sort((a, b) -> {
            Double scoreA = (Double) a.get("_relevanceScore");
            Double scoreB = (Double) b.get("_relevanceScore");
            return Double.compare(scoreB, scoreA);
        });
        
        // 返回Top N
        return scored.subList(0, Math.min(maxCount, scored.size()));
    }

    /**
     * 计算实体相关性分数
     */
    private double calculateEntityRelevance(Map<String, Object> entity, String keywords, int currentChapter) {
        double score = 0.0;
        
        // 1. 影响分数权重 (40%)
        Object influenceObj = entity.get("influenceScore");
        if (influenceObj instanceof Number) {
            score += ((Number) influenceObj).doubleValue() * 0.4;
        }
        
        // 2. 最近出现权重 (30%) - 指数衰减
        Object lastMentionObj = entity.get("lastMention");
        if (lastMentionObj instanceof Number) {
            int lastMention = ((Number) lastMentionObj).intValue();
            int gap = currentChapter - lastMention;
            double recency = Math.exp(-0.2 * gap); // 指数衰减
            score += recency * 30;
        }
        
        // 3. 关键词匹配权重 (30%)
        String name = (String) entity.get("name");
        String hookLine = (String) entity.get("hookLine");
        if (name != null && keywords.contains(name)) {
            score += 20;
        }
        if (hookLine != null && keywords.length() > 0) {
            // 简单的关键词匹配
            String[] words = hookLine.split("[，。、 ]");
            for (String word : words) {
                if (word.length() > 1 && keywords.contains(word)) {
                    score += 2;
                }
            }
        }
        
        return score;
    }

    /**
     * 构建智能章节概括上下文
     */
    /**
     * 从记忆库读取章节概括（由概括生成）
     */
    public String buildChaptersSummaryContext(Long novelId, int chapterNumber) {
        StringBuilder context = new StringBuilder();

        try {
            // 计算要获取的章节范围（前20章，但不包括当前章）
            int startChapter = Math.max(1, chapterNumber - 20);
            int endChapter = chapterNumber - 1;
            
            if (endChapter < startChapter) {
                logger.debug("第一章无前置章节概括");
                return "";
            }
            
            // 从数据库查询前20章概括
            List<com.novel.domain.entity.ChapterSummary> chapterSummaries = 
                chapterSummaryRepository.findByNovelIdAndChapterNumberBetween(novelId, startChapter, endChapter);
            
            if (chapterSummaries != null && !chapterSummaries.isEmpty()) {
                context.append("📚 **前期内容概括**\n");
                
                // 按章节号排序并输出
                chapterSummaries.sort(Comparator.comparing(com.novel.domain.entity.ChapterSummary::getChapterNumber));
                
                for (com.novel.domain.entity.ChapterSummary summary : chapterSummaries) {
                    Integer chapNum = summary.getChapterNumber();
                    String summaryText = summary.getSummary();
                    
                    if (chapNum != null && summaryText != null && !summaryText.trim().isEmpty()) {
                        context.append("第").append(chapNum).append("章: ");
                        context.append(summaryText).append("\n");
                    }
                }
                context.append("\n");
                
                logger.info("成功从数据库加载{}章概括（章节{}到{}）", chapterSummaries.size(), startChapter, endChapter);
            } else {
                logger.debug("数据库中暂无章节概括（小说ID={}, 范围={}-{}）", novelId, startChapter, endChapter);
            }
        } catch (Exception e) {
            logger.warn("从数据库构建章节概括上下文失败: {}", e.getMessage(), e);
        }

        return context.toString();
    }
    /**
     * 构建上一章完整内容上下文
     */
    private String buildPreviousChapterFullContentContext(Long novelId, int chapterNumber) {
        if (chapterNumber <= 1) return "";
        try {
            Chapter prev = chapterRepository.findByNovelAndChapterNumber(novelId, chapterNumber - 1);
            if (prev == null) return "";
            String content = prev.getContent();
            if (content == null || content.trim().isEmpty()) return "";
            StringBuilder ctx = new StringBuilder();
            ctx.append("📖 **上一章完整内容**\n");
            if (prev.getTitle() != null && !prev.getTitle().trim().isEmpty()) {
                ctx.append("标题：").append(prev.getTitle()).append("\n");
            }
            ctx.append("（第").append(chapterNumber - 1).append("章）\n\n");
            ctx.append(content);
            ctx.append("\n\n");
            return ctx.toString();
        } catch (Exception e) {
            logger.warn("获取上一章完整内容失败: {}", e.getMessage());
            return "";
        }
    }


    /**
     * 构建创作灵感分析上下文（暂时禁用）
     */
    // private String buildCreativeInspirationContext(Novel novel, Map<String, Object> memoryBank, int chapterNumber, String chaptersSummary) {
    //     StringBuilder context = new StringBuilder();
    //
    //     try {
    //         // 生成AI深度思考的创作灵感
    //         String inspiration = creativeInspirationService.generateCreativeInspiration(
    //             novel, memoryBank, chapterNumber, chaptersSummary
    //         );
    //
    //         context.append("💡 **AI创作智囊分析**\n");
    //         context.append("(基于当前进度的深度思考和后续发展建议)\n\n");
    //         context.append(inspiration).append("\n");
    //
    //     } catch (Exception e) {
    //         logger.warn("构建创作灵感上下文失败: {}", e.getMessage());
    //     }
    //
    //     return context.toString();
    // }

    /**
     * 构建前情回顾上下文（保留原方法作为备用）
     */
    @SuppressWarnings("unchecked")
    private String buildRecentChaptersContext(Map<String, Object> memoryBank, int chapterNumber) {
        StringBuilder context = new StringBuilder();

        Object recentSummary = memoryBank.get("recentChaptersSummary");
        if (recentSummary instanceof Map) {
            Map<String, Object> summaryData = (Map<String, Object>) recentSummary;

            context.append("📝 **前情回顾**\n");

            // 最近5章摘要
            Object lastFiveChapters = summaryData.get("lastFiveChapters");
            if (lastFiveChapters instanceof List) {
                List<Map<String, Object>> chapters = (List<Map<String, Object>>) lastFiveChapters;
                context.append("- 最近章节回顾:\n");
                for (Map<String, Object> chapter : chapters) {
                    context.append("  第").append(chapter.get("chapterNumber")).append("章: ");
                    context.append(chapter.get("summary")).append("\n");
                }
            }

            // 最近的重要发展
            Object keyDevelopments = summaryData.get("keyDevelopments");
            if (keyDevelopments instanceof List) {
                List<String> developments = (List<String>) keyDevelopments;
                if (!developments.isEmpty()) {
                    context.append("- 重要发展: ").append(String.join("、", developments)).append("\n");
                }
            }

            // 悬而未决的问题
            Object pendingIssues = summaryData.get("pendingIssues");
            if (pendingIssues instanceof List) {
                List<String> issues = (List<String>) pendingIssues;
                if (!issues.isEmpty()) {
                    context.append("- 待解决问题: ").append(String.join("、", issues)).append("\n");
                }
            }
        }

        return context.toString();
    }

    /**
     * 构建伏笔线索上下文
     */
    /**
     * 构建伏笔线索上下文（从数据库查询）
     */
    public String buildForeshadowingContext(Long novelId) {
        StringBuilder context = new StringBuilder();
        
        try {
            // 从数据库查询活跃状态的伏笔
            List<com.novel.domain.entity.NovelForeshadowing> foreshadowings = 
                foreshadowingRepository.findByNovelIdAndStatus(novelId, "ACTIVE");
            
            if (foreshadowings == null || foreshadowings.isEmpty()) {
                logger.debug("数据库中暂无活跃伏笔: novelId={}", novelId);
                return "";
            }
            
            context.append("🎭 **伏笔与线索管理**\n\n");
            
            // 按优先级和埋设章节排序（已在Repository查询中完成）
            context.append("**活跃伏笔（合计").append(foreshadowings.size()).append("个）：**\n\n");
            
            for (com.novel.domain.entity.NovelForeshadowing foreshadowing : foreshadowings) {
                context.append("• **").append(foreshadowing.getContent()).append("**\n");
                context.append("  埋设章节: 第").append(foreshadowing.getPlantedChapter()).append("章");
                
                if (foreshadowing.getResolvedChapter() != null) {
                    context.append(" | 回收章节: 第").append(foreshadowing.getResolvedChapter()).append("章");
                }
                
                if (foreshadowing.getType() != null) {
                    context.append(" | 类型: ").append(foreshadowing.getType());
                }
                
                if (foreshadowing.getPriority() != null) {
                    context.append(" | 优先级: ").append(foreshadowing.getPriority());
                }
                
                context.append("\n");
                
                if (foreshadowing.getContextInfo() != null && !foreshadowing.getContextInfo().isEmpty()) {
                    context.append("  上下文: ").append(foreshadowing.getContextInfo()).append("\n");
                }
                
                context.append("\n");
            }
            
            context.append("**伏笔使用规则：**\n");
            context.append("- 重要伏笔需适时回收，不要遗忘\n");
            context.append("- 每章可适当埋设新伏笔，但要注意不要过多\n");
            context.append("- 回收伏笔时要自然融入剧情，避免生硬\n\n");
            
            logger.info("成功加载{}个活跃伏笔: novelId={}", foreshadowings.size(), novelId);
            
        } catch (Exception e) {
            logger.warn("从数据库构建伏笔上下文失败: {}", e.getMessage(), e);
        }
        
        return context.toString();
    }

    /**
     * 构建风格指导上下文
     */
    private String buildStyleGuidanceContext(Novel novel, Map<String, Object> memoryBank) {
        StringBuilder context = new StringBuilder();
        context.append("🎨 **写作风格指导**\n");

        // 基于类型的风格指导
        String genreStyle = getGenreStyleGuidance(novel.getGenre());
        if (!genreStyle.isEmpty()) {
            context.append(genreStyle);
        }

        // 从记忆库获取风格偏好
        @SuppressWarnings("unchecked")
        Map<String, Object> stylePrefs = (Map<String, Object>) memoryBank.get("stylePreferences");
        if (stylePrefs != null) {
            Object narrativeStyle = stylePrefs.get("narrativeStyle");
            if (narrativeStyle != null) {
                context.append("- 叙述风格: ").append(narrativeStyle).append("\n");
            }

            Object dialogueStyle = stylePrefs.get("dialogueStyle");
            if (dialogueStyle != null) {
                context.append("- 对话风格: ").append(dialogueStyle).append("\n");
            }

            Object descriptionLevel = stylePrefs.get("descriptionLevel");
            if (descriptionLevel != null) {
                context.append("- 描写详细度: ").append(descriptionLevel).append("\n");
            }
        }

        return context.toString();
    }

    /**
     * 获取类型风格指导
     */
    private String getGenreStyleGuidance(String genre) {
        switch (genre) {
            case "都市":
                return "- 语言现代化，贴近生活，适当使用网络用语\n" +
                       "- 描写细节要有都市感，体现现代生活节奏\n" +
                       "- 对话要自然，符合现代人的表达习惯\n";
            case "玄幻":
                return "- 可适当使用文言词汇，增强古典韵味\n" +
                       "- 战斗场面要有节奏感和画面感\n" +
                       "- 修炼描写要有仪式感和神秘感\n";
            case "科幻":
                return "- 融入科技术语，体现未来感\n" +
                       "- 逻辑要严密，科学设定要自洽\n" +
                       "- 思辨色彩要浓厚，引发读者思考\n";
            default:
                return "- 保持类型特色，符合读者期待\n" +
                       "- 语言要生动流畅，富有表现力\n" +
                       "- 情节推进要有节奏感\n";
        }
    }

    /**
     * 构建章节任务上下文（增强版：智能场景识别）
     */
    private String buildChapterTaskContext(Map<String, Object> chapterPlan, int chapterNumber) {
        StringBuilder context = new StringBuilder();
        
        // 明确告知当前章节号
        context.append("🎯 **当前创作任务：第").append(chapterNumber).append("章**\n\n");
        
        // 智能识别章节场景类型
        String sceneType = identifySceneType(chapterPlan, chapterNumber);
        context.append("**章节定位：** ").append(sceneType).append("\n\n");
        
        // 根据场景类型给出针对性指导
        context.append(getSceneSpecificGuidance(sceneType)).append("\n\n");
        
        context.append("【写作要求】\n");

        Object estimatedWords = chapterPlan.get("estimatedWords");
        if (estimatedWords != null) {
            int targetWords = Integer.parseInt(estimatedWords.toString());
            context.append("**字数要求：** 严格控制在").append(targetWords).append("字左右\n");
        } else {
            context.append("**字数要求：** 3000字左右\n");
        }

        context.append("\n**【输出要求】:**\n");
        context.append("1. **纯正文输出：** 直接输出小说正文内容，不要标题、不要任何说明文字\n");
        context.append("2. **自然开篇：** 第一句话就是故事内容，不要\"第X章\"等标记\n");
        context.append("3. **连贯衔接：** 开头要自然衔接上一章结尾（如果有）\n");
        context.append("4. **结尾钩子：** 章节结尾必须留悬念，让读者想看下一章\n");
        context.append("5. **遵循上下文：** 严格遵循已提供的所有设定、角色状态、前情发展\n\n");
        
        context.append("**现在开始创作第").append(chapterNumber).append("章正文：**\n");

        return context.toString();
    }
    
    /**
     * 智能识别章节场景类型
     */
    private String identifySceneType(Map<String, Object> chapterPlan, int chapterNumber) {
        // 根据章节号和计划内容智能识别场景类型
        
        // 开篇章节
        if (chapterNumber <= 3) {
            return "开篇章节 - 建立代入感，快速吸引读者";
        }
        
        // 从章节计划中提取关键词
        String planText = extractPlanText(chapterPlan).toLowerCase();
        
        // 高潮章节（战斗、对决、揭秘等）
        if (planText.contains("战斗") || planText.contains("对决") || planText.contains("决战") ||
            planText.contains("揭秘") || planText.contains("突破") || planText.contains("爆发") ||
            planText.contains("高潮") || planText.contains("反击") || planText.contains("逆袭")) {
            return "高潮章节 - 情绪饱满，爽点密集";
        }
        
        // 转折章节
        if (planText.contains("转折") || planText.contains("变化") || planText.contains("意外") ||
            planText.contains("发现") || planText.contains("真相") || planText.contains("反转")) {
            return "转折章节 - 剧情转向，制造冲击";
        }
        
        // 过渡章节
        if (planText.contains("日常") || planText.contains("休息") || planText.contains("准备") ||
            planText.contains("铺垫") || planText.contains("过渡")) {
            return "过渡章节 - 张弛有度，埋设伏笔";
        }
        
        // 默认：发展章节
        return "发展章节 - 推进主线，层层递进";
    }
    
    /**
     * 提取章节计划文本（用于场景识别）
     */
    private String extractPlanText(Map<String, Object> chapterPlan) {
        StringBuilder text = new StringBuilder();
        
        Object title = chapterPlan.get("title");
        if (title != null) text.append(title).append(" ");
        
        Object summary = chapterPlan.get("summary");
        if (summary != null) text.append(summary).append(" ");
        
        Object coreEvent = chapterPlan.get("coreEvent");
        if (coreEvent != null) text.append(coreEvent).append(" ");
        
        Object goals = chapterPlan.get("goals");
        if (goals != null) text.append(goals).append(" ");
        
        return text.toString();
    }
    
    /**
     * 根据场景类型给出针对性指导
     */
    private String getSceneSpecificGuidance(String sceneType) {
        if (sceneType.contains("开篇")) {
            return "**开篇要点：**\n" +
                   "- 300字内必须出现冲突、悬念或爽点\n" +
                   "- 快速建立主角形象，让读者产生代入感\n" +
                   "- 世界观设定要自然融入对话和行动中\n" +
                   "- 埋设核心悬念，吸引读者继续追更";
        }
        
        if (sceneType.contains("高潮")) {
            return "**高潮要点：**\n" +
                   "- 情绪要饱满，用短句和对话制造紧张感\n" +
                   "- 过程要有波折，不可一帆风顺\n" +
                   "- 爽点密集但要合理，避免无脑碾压\n" +
                   "- 结果要有意外或新悬念，保持吸引力";
        }
        
        if (sceneType.contains("转折")) {
            return "**转折要点：**\n" +
                   "- 转折要有铺垫，不可过于突兀\n" +
                   "- 新方向要清晰，让读者产生期待\n" +
                   "- 可以回收旧伏笔，同时埋设新悬念\n" +
                   "- 情绪起伏要大，制造冲击感";
        }
        
        if (sceneType.contains("过渡")) {
            return "**过渡要点：**\n" +
                   "- 不可纯粹过渡，必须有实质内容\n" +
                   "- 可以展现角色关系、世界观细节\n" +
                   "- 为下一个高潮做铺垫，埋设线索\n" +
                   "- 节奏可以放缓，但不可让读者觉得无聊";
        }
        
        // 默认：发展章节
        return "**发展要点：**\n" +
               "- 冲突要层层递进，不可平铺直叙\n" +
               "- 主角要有明确目标，本章要有实质进展\n" +
               "- 适当埋设新伏笔，为后续发展铺垫\n" +
               "- 配角出场要有作用，推动剧情发展";
    }

    /**
     * 构建最终写作指令
     */
    private String buildFinalWritingInstruction(Novel novel, Map<String, Object> chapterPlan) {
        Integer chapterNumber = (Integer) chapterPlan.get("chapterNumber");
        Object title = chapterPlan.get("title");
        Integer wordCount = (Integer) chapterPlan.get("estimatedWords");

        StringBuilder instruction = new StringBuilder();
        instruction.append("🚀 **开始创作**\n\n");
        instruction.append("基于上述所有上下文信息，为《").append(novel.getTitle()).append("》");
        instruction.append("创作第").append(chapterNumber).append("章");

        if (title != null) {
            instruction.append("《").append(title).append("》");
        }

        instruction.append("。\n\n");

        instruction.append("**创作要求**:\n");
        instruction.append("1. 严格遵循所有上下文信息中的设定和发展\n");
        instruction.append("2. 推进相关情节线，实现本章目标\n");
        instruction.append("3. 保持角色一致性，体现角色成长\n");
        instruction.append("4. 适当埋设或回收伏笔\n");
        instruction.append("5. 字数控制在").append(wordCount).append("字左右\n");
        instruction.append("6. 直接输出小说正文，不要任何说明文字\n");
        instruction.append("7. 为后续发展做好铺垫，体现深度思考\n\n");

        instruction.append("现在开始创作，第一个字就是故事内容：");

        return instruction.toString();
    }

    /**
     * 记录消息大小，确保符合10k字符限制
     */
    private void logMessageSizes(List<Map<String, String>> messages, String novelTitle, int chapterNumber) {
        int totalChars = 0;
        int oversizedCount = 0;

        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> message = messages.get(i);
            String content = message.get("content");
            int charCount = content.length();
            totalChars += charCount;

            if (charCount > 10000) {
                oversizedCount++;
                logger.warn("⚠️ 消息{}超过10k字符限制: {}字符", i + 1, charCount);

                // 如果消息过大，记录消息角色和开头内容用于调试
                String role = message.get("role");
                String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                logger.debug("超大消息详情 - 角色: {} | 内容预览: {}", role, preview);
            }
        }

        logger.info("📊 上下文统计 - 小说: {} 第{}章", novelTitle, chapterNumber);
        logger.info("📋 总消息数: {} | 总字符数: {} | 超限消息: {}",
                   messages.size(), totalChars, oversizedCount);
        logger.info("📈 平均消息长度: {}字符", totalChars / Math.max(messages.size(), 1));

        if (oversizedCount > 0) {
            logger.warn("🔴 发现{}条消息超过10k字符限制，建议优化消息分割", oversizedCount);
        }

        // 估算总的token使用量（中文字符约等于1.5 tokens）
        int estimatedTokens = (int) (totalChars * 1.5);
        logger.info("🔢 预估token使用: {}（基于{}字符）", estimatedTokens, totalChars);
    }
}