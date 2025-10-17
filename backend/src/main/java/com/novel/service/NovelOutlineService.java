package com.novel.service;

import com.novel.domain.entity.NovelOutline;
import com.novel.domain.entity.Novel;
import com.novel.repository.NovelOutlineRepository;
import com.novel.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 小说大纲服务层
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@Service
public class NovelOutlineService {

    private static final Logger logger = LoggerFactory.getLogger(NovelOutlineService.class);

    @Autowired
    private NovelOutlineRepository outlineRepository;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private VolumeService volumeService;

    @Autowired
    private LongNovelMemoryManager longNovelMemoryManager;

    /**
     * 生成初始大纲
     */
    @Transactional
    public NovelOutline generateInitialOutline(Long novelId, String basicIdea, Integer targetWordCount, Integer targetChapterCount) {
        // 检查小说是否存在
        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + novelId);
        }

        // 检查是否已有大纲
        Optional<NovelOutline> existingOutline = findByNovelId(novelId);
        if (existingOutline.isPresent()) {
            throw new RuntimeException("该小说已有大纲，请使用修改功能");
        }

        // 创建新大纲
        NovelOutline outline = new NovelOutline(novelId, basicIdea);
        outline.setTargetWordCount(targetWordCount);
        outline.setTargetChapterCount(targetChapterCount);
        outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
        outline.setGenre(novel.getGenre()); // 设置小说类型

        // 使用AI生成详细大纲内容
        generateOutlineContentWithAI(outline, novel);

        // 保存大纲
        System.out.println("=== 保存大纲到数据库 ===");
        System.out.println("Debug - 保存前的大纲内容:");
        System.out.println("  - id: " + outline.getId());
        System.out.println("  - novelId: " + outline.getNovelId());
        System.out.println("  - title: " + outline.getTitle());
        System.out.println("  - genre: " + outline.getGenre());
        System.out.println("  - basicIdea: " + outline.getBasicIdea());
        System.out.println("  - coreTheme: " + outline.getCoreTheme());
        System.out.println("  - mainCharacters: " + outline.getMainCharacters());
        System.out.println("  - plotStructure: " + outline.getPlotStructure());
        System.out.println("  - worldSetting: " + outline.getWorldSetting());
        System.out.println("  - keyElements: " + outline.getKeyElements());
        System.out.println("  - conflictTypes: " + outline.getConflictTypes());
        
        outlineRepository.insert(outline);
        
        System.out.println("Debug - 大纲保存成功，ID: " + outline.getId());
        System.out.println("=== 大纲保存完成 ===");
        
        return outline;
    }

    /**
     * 初始化大纲记录（用于SSE流式生成前先创建占位记录）
     */
    @Transactional
    public NovelOutline initOutlineRecord(Long novelId, String basicIdea, Integer targetWordCount, Integer targetChapterCount) {
        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + novelId);
        }
        NovelOutline outline = new NovelOutline(novelId, basicIdea);
        outline.setTargetWordCount(targetWordCount);
        outline.setTargetChapterCount(targetChapterCount);
        outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
        outline.setGenre(novel.getGenre());
        outlineRepository.insert(outline);
        return outline;
    }

    /**
     * 流式生成大纲内容（真正的流式AI调用）
     * 说明：使用流式AI接口，逐块返回生成内容，同时实时写入数据库
     * 注意：移除@Transactional，因为流式处理是渐进式的，每次chunk更新都是独立的数据库操作
     */
    public void streamGenerateOutlineContent(NovelOutline outline, com.novel.dto.AIConfigRequest aiConfig, java.util.function.Consumer<String> chunkConsumer) {
        Novel novel = novelRepository.selectById(outline.getNovelId());
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + outline.getNovelId());
        }

        // 直接复用"超级大纲"的爆款提示词逻辑
        String prompt = buildSuperOutlinePromptCompat(novel, outline.getBasicIdea(), outline.getTargetChapterCount(), outline.getTargetWordCount());

        // 使用真正的流式AI调用 - 从空开始累加（不使用旧的大纲）
        StringBuilder accumulated = new StringBuilder();
        
        try {
            aiWritingService.streamGenerateContent(prompt, "outline_generation_stream", aiConfig, chunk -> {
                try {
                    // 累加内容
                    accumulated.append(chunk);
                    
                    // 实时更新数据库：直接写入 novels.outline（主存储）
                    novel.setOutline(accumulated.toString());
                    novelRepository.updateById(novel);
                    
                    // 为兼容旧逻辑，保留写回 novel_outlines.plot_structure（可后续下线）
                    outline.setPlotStructure(accumulated.toString());
                    outlineRepository.updateById(outline);
                    
                    // 回调给SSE
                    if (chunkConsumer != null) {
                        chunkConsumer.accept(chunk);
                    }
                } catch (Exception e) {
                    logger.error("处理流式内容块失败: {}", e.getMessage(), e);
                    throw new RuntimeException("处理流式内容块失败: " + e.getMessage());
                }
            });
            
            // 流式生成完成，设置状态为DRAFT
            outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
            outlineRepository.updateById(outline);
            
            logger.info("✅ 流式大纲生成完成，总长度: {} 字符", accumulated.length());
            
            // 注释掉自动生成世界观的逻辑，避免额外的AI调用
            // 世界观可以在需要时单独生成
            // try {
            //     generateAndSaveWorldView(novel, accumulated.toString(), aiConfig);
            // } catch (Exception worldViewError) {
            //     logger.error("生成世界观失败（不影响大纲）: {}", worldViewError.getMessage());
            // }
            
        } catch (Exception e) {
            logger.error("❌ 流式大纲生成失败: {}", e.getMessage(), e);
            throw new RuntimeException("流式大纲生成失败: " + e.getMessage());
        }
    }

    // 兼容方法：从 SuperOutlineService 复制 buildSuperOutlinePrompt 的核心逻辑
    private String buildSuperOutlinePromptCompat(Novel novel, String originalIdea, Integer targetChapters, Integer targetWords) {
        return String.format(
            "你现在是世界顶级的网文故事策划人，你的核心任务是帮我把一个灵感火花，变成一部血肉饱满、令人上瘾的史诗级故事。你深谙\"人物驱动剧情\"的黄金法则，坚信所有爽点和深度都应源于角色自身的选择与成长。\n\n" +
            "请根据我提供的构思，为我规划一部超长篇小说的详细剧情线。\n\n" +
            "【小说基本信息】\n" +
            "故事名称：%s\n" +
            "故事类型：%s\n" +
            "预期体量：约%d章(可参考目标章节数)，%d字(可参考目标字数)\n\n" +
            "【我的故事灵感与核心设定】\n%s\n\n" +
            "【你的核心创作指引】\n\n" +
            "1. 故事灵魂：人物驱动的真实感\n\n" +
            "动机即一切：主角的每一个重大行动，都必须源于其内在的、符合人性的驱动（如求生、守护、复仇、求知、自由），而非被动响应\"系统任务\"或\"作者安排\"。\n\n" +
            "缺陷即魅力：主角必须有显著的性格缺陷或能力短板，这些缺陷将直接引发关键危机，并使其成长弧光更加动人。\n\n" +
            "关系即舞台：人物关系不是标签，而是动态变化的权力与情感网络。盟友可能背叛，对手亦可合作，所有关系都应在剧情中经历考验与演变。\n\n" +
            "2. 叙事节奏：张弛有度的沉浸感\n\n" +
            "开篇即深渊：前三章必须让主角陷入一个具体、紧迫且情感上能引发共鸣的绝境，并被迫做出一个展现其核心性格的艰难选择。\n\n" +
            "爽点即回报：所有爽点（打脸、突破、收获）都必须是主角历经磨难、运用智慧与能力后应得的奖励，杜绝无缘无故的\"天降馅饼\"。\n\n" +
            "低谷即转机：在重大胜利后，必须设计合理的\"代价\"或\"反噬\"，让主角陷入更复杂的困境，推动故事向更深层次发展。\n\n" +
            "3. 世界构建：由近及远的探索感\n\n" +
            "从毛孔开始：世界观的展现应从主角的感官细节入手（如\"灵气的味道像铁锈\"、\"未来城市的雨水有股酸味\"），而非大段说明文。\n\n" +
            "权力有代价：任何力量体系（修仙、异能、科技）都必须有清晰且严苛的规则与代价，这本身就是冲突的来源。\n\n" +
            "地图即谜题：每次地图转换，都必须由主角的核心目标驱动，新环境应带来新的生存规则与盟友敌人。\n\n" +
            "4. 语言质感：杜绝AI腔的实战手册\n\n" +
            "绝对禁令：严禁使用\"心中一凛、闪过一丝、勾勒出、进行了一个…的操作\"等网络陈词滥调与AI高频词。\n\n" +
            "行动即心理：用具体的生理反应和动作代替心理描述。（例如，不用\"他很害怕\"，用\"他感到胃里一阵冰凉，指甲深深掐进掌心\"；不用\"她心中一喜\"，用\"她嘴角不受控制地向上弯了一下，又迅速压平\"）。\n\n" +
            "展示即比喻：摒弃\"仿佛/如同/好像\"。直接进行感官具象化描写。（例如，不用\"快得如同闪电\"，用\"身影掠过，只在视网膜上留下一道灼热的残影\"）。\n\n" +
            "【输出规划要求】\n\n" +
            "请严格遵循以下结构，用中文输出：\n\n" +
            "一、故事总纲（300字内）\n" +
            "用一句话logline（故事梗概）开场，概括整个故事的核心冲突与独特魅力。然后简述主角从开端到结局的弧光，以及故事试图探讨的核心主题。\n\n" +
            "二、分阶段详细剧情轴\n" +
            "请按\"初期：身份与困境 -> 中期：崛起与代价 -> 后期：对抗与真相 -> 终局：抉择与新生\"这样的逻辑，划分4-6个自然阶段。每个阶段需包含：\n\n" +
            "主角状态：环境、身份、能力、心智与缺陷。\n\n" +
            "核心目标与冲突：本阶段他想达成什么？主要阻碍是什么？（必须是具体、可执行的目标）\n\n" +
            "关键事件序列：至少3个环环相扣的事件，描述时请植入具体的场景、动作和对话暗示，而非概括。\n\n" +
            "人物关系演变：谁登场了？与主角的关系发生了何种具体、可感知的变化？（例如，从\"互相利用\"变为\"脆弱的信任\"）\n\n" +
            "高潮场面设计：一个充满张力的具体场景描述，包含视觉、听觉或触觉的细节。\n\n" +
            "伏笔与回收：此处埋下了什么？或回收了前文的哪个伏笔？如何回收？\n\n" +
            "三、核心人物命运图谱\n" +
            "用表格或清单形式，列出主角及5-8个关键角色。注明其初始定位、与主角的核心关系动态，以及最终命运的暗示（保留悬念，如：\"为守护誓言而牺牲\"，而非\"在第805章被主角的仇人杀死\"）。\n\n" +
            "四、长线伏笔布局\n" +
            "列出不少于5个贯穿全文的核心谜题。每个注明：\n\n" +
            "伏笔内容：是什么？（如\"母亲留下的项链会吸收月光\"）\n\n" +
            "暗示方式：如何首次呈现？（如\"主角在月圆之夜发现项链微微发烫\"）\n\n" +
            "揭示节点与影响：在哪个剧情阶段揭晓？揭晓时对主角和世界格局造成何种冲击？\n\n" +
            "请严格按上述要求直接输出中文正文，不要使用JSON格式。不要输出卷或者分卷结构 不要出现大纲之外的内容不要解释",
            novel.getTitle(),
            novel.getGenre(),
            targetChapters == null ? 0 : targetChapters,
            targetWords == null ? 0 : targetWords,
            originalIdea == null ? "" : originalIdea
        );
    }

    /**
     * 修改大纲
     */
    @Transactional
    public NovelOutline reviseOutline(Long outlineId, String userFeedback) {
        NovelOutline outline = outlineRepository.selectById(outlineId);
        if (outline == null) {
            throw new RuntimeException("大纲不存在: " + outlineId);
        }

        // 增加修订次数
        outline.incrementRevision();
        outline.setStatus(NovelOutline.OutlineStatus.REVISING);

        // 记录反馈历史
        String currentFeedback = outline.getFeedbackHistory();
        String newFeedback = currentFeedback == null ? userFeedback : currentFeedback + "\n---\n" + userFeedback;
        outline.setFeedbackHistory(newFeedback);

        // 使用AI根据反馈修改大纲
        reviseOutlineContentWithAI(outline, userFeedback);

        // 保存修改
        outlineRepository.updateById(outline);

        return outline;
    }

    /**
     * 确认大纲（旧方法，保持兼容）
     * 说明：确认大纲状态，并自动触发基于大纲的卷拆分
     * @deprecated 请使用 confirmOutline(Long outlineId, AIConfigRequest aiConfig)
     */
    @Deprecated
    @Transactional
    public NovelOutline confirmOutline(Long outlineId) {
        return confirmOutline(outlineId, null);
    }
    
    /**
     * 确认大纲（支持AI配置）
     * 说明：确认大纲状态，并自动触发基于大纲的卷拆分
     */
    @Transactional
    public NovelOutline confirmOutline(Long outlineId, com.novel.dto.AIConfigRequest aiConfig) {
        NovelOutline outline = outlineRepository.selectById(outlineId);
        if (outline == null) {
            throw new RuntimeException("大纲不存在: " + outlineId);
        }

        // 确认大纲状态
        outline.confirm();
        outlineRepository.updateById(outline);

        // 将大纲内容写入 novels.outline
        try {
            Novel novel = novelRepository.selectById(outline.getNovelId());
            if (novel != null) {
                novel.setOutline(outline.getPlotStructure());
                novelRepository.updateById(novel);
            }
        } catch (Exception e) {
            logger.error("写入novels.outline失败: {}", e.getMessage(), e);
        }
        
        // 自动触发基于大纲的卷拆分（异步任务）
        try {
            Long novelId = outline.getNovelId();

            // 优先使用用户在创建小说时输入的计划卷数
            Novel novel = novelRepository.selectById(novelId);
            Integer volumeCount = 5; // 默认5卷

            if (novel != null && novel.getPlannedVolumeCount() != null && novel.getPlannedVolumeCount() > 0) {
                // 第一优先级：使用用户输入的计划卷数
                volumeCount = novel.getPlannedVolumeCount();
                logger.info("📋 使用用户设定的计划卷数: {}", volumeCount);
            } else if (outline.getTargetChapterCount() != null && outline.getTargetChapterCount() > 0) {
                // 第二优先级：根据目标章数估算
                int targetChapters = outline.getTargetChapterCount();
                if (targetChapters >= 200) {
                    volumeCount = 8; // 超长篇分8卷
                } else if (targetChapters >= 150) {
                    volumeCount = 6; // 长篇分6卷
                } else if (targetChapters >= 100) {
                    volumeCount = 5; // 中篇分5卷
                } else if (targetChapters >= 50) {
                    volumeCount = 3; // 短篇分3卷
                } else {
                    volumeCount = 2; // 极短篇分2卷
                }
                logger.info("📊 根据目标章数({})估算卷数: {}", targetChapters, volumeCount);
            } else if (outline.getPlotStructure() != null) {
                // 第三优先级：根据大纲长度估算
                int outlineLength = outline.getPlotStructure().length();
                if (outlineLength > 10000) {
                    volumeCount = 8;
                } else if (outlineLength > 5000) {
                    volumeCount = 6;
                } else if (outlineLength > 2000) {
                    volumeCount = 5;
                } else {
                    volumeCount = 3;
                }
                logger.info("📝 根据大纲长度({})估算卷数: {}", outlineLength, volumeCount);
            } else {
                logger.info("📌 使用默认卷数: {}", volumeCount);
            }

            logger.info("📝 开始触发卷拆分任务，小说ID: {}, 最终卷数: {}", novelId, volumeCount);

            // 使用新的基于确认大纲的卷规划生成方法，传递AI配置
            if (aiConfig != null && aiConfig.isValid()) {
                logger.info("✅ 使用前端传递的AI配置生成卷规划");
                volumeService.generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount, aiConfig);
            } else {
                logger.warn("⚠️ 未提供AI配置或配置无效，使用简化模式生成卷规划");
                volumeService.generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount, null);
            }

            logger.info("✅ 大纲确认完成，ID: {}，已触发卷拆分任务，预计生成{}卷", outlineId, volumeCount);
        } catch (Exception e) {
            // 不影响确认流程，失败仅记录日志
            logger.error("❌ 确认大纲后触发卷拆分失败: {}", e.getMessage(), e);
        }
        
        return outline;
    }

    /**
     * 根据小说ID查找大纲
     */
    public Optional<NovelOutline> findByNovelId(Long novelId) {
        return outlineRepository.findByNovelId(novelId);
    }

    /**
     * 根据ID获取大纲
     */
    public NovelOutline getById(Long id) {
        return outlineRepository.selectById(id);
    }

    /**
     * 使用AI生成大纲内容
     */
    private void generateOutlineContentWithAI(NovelOutline outline, Novel novel) {
        try {
            System.out.println("=== AI大纲生成开始 ===");
            String prompt = buildOutlineGenerationPrompt(outline, novel);
            System.out.println("Debug - 生成的提示词: " + prompt);
            
            String aiResponse = aiWritingService.generateContent(prompt, "outline_generation");
            System.out.println("Debug - AI响应内容: " + aiResponse);
            
            // 解析AI响应并设置到大纲对象中
            parseAndSetOutlineContent(outline, aiResponse);
            
            System.out.println("Debug - 解析后的大纲内容:");
            System.out.println("  - coreTheme: " + outline.getCoreTheme());
            System.out.println("  - mainCharacters: " + outline.getMainCharacters());
            System.out.println("  - plotStructure: " + outline.getPlotStructure());
            System.out.println("  - worldSetting: " + outline.getWorldSetting());
            System.out.println("  - keyElements: " + outline.getKeyElements());
            System.out.println("  - conflictTypes: " + outline.getConflictTypes());
            System.out.println("=== AI大纲生成完成 ===");
            
        } catch (Exception e) {
            // 如果AI生成失败，使用默认模板
            System.err.println("AI大纲生成失败: " + e.getMessage());
            e.printStackTrace();
            System.out.println("=== 使用默认大纲内容 ===");
            setDefaultOutlineContent(outline, novel);
        }
    }

    /**
     * 使用AI修订大纲内容
     */
    private void reviseOutlineContentWithAI(NovelOutline outline, String userFeedback) {
        try {
            String prompt = buildOutlineRevisionPrompt(outline, userFeedback);
            String aiResponse = aiWritingService.generateContent(prompt, "outline_revision");
            
            // 解析AI响应并更新大纲内容
            parseAndSetOutlineContent(outline, aiResponse);
            
        } catch (Exception e) {
            // AI修订失败时，记录错误但不中断流程
            System.err.println("AI大纲修订失败: " + e.getMessage());
        }
    }

    /**
     * 构建网文专用大纲生成提示词（基于专业指导重构）
     */
    private String buildOutlineGenerationPrompt(NovelOutline outline, Novel novel) {
        String genre = novel.getGenre() != null ? novel.getGenre() : "玄幻";
        
        return String.format(
            "你是一位资深网络文学创作者，专注东方玄幻、修真小说领域十年以上。\n\n" +
            "你的写作风格融合烽火戏诸侯的沉郁、忘语的细腻、辰东的宏大。\n" +
            "你擅长：埋设伏笔、控制节奏、塑造真实人物、营造命运感。\n" +
            "你反对：系统流、无脑爽文、金手指秒生效、角色工具化。\n\n" +
            
            "**【小说基本信息】**\n" +
            "小说标题：《%s》\n" +
            "类型：东方%s / 凡人流 / 慢热成长\n" +
            "基调：压抑中见希望，平凡中藏惊雷\n" +
            "基本构思：%s\n" +
            "目标字数：%d字\n" +
            "目标章节：%d章\n" +
            "禁忌：禁止出现\"系统\"\"叮\"\"宿主\"\"秒杀\"等词汇\n\n" +
            
            "**【网文大纲核心原则】**\n" +
            "1. **黄金三章法则**: 前3章要抓住读者\n" +
            "2. **爽点布局**: 每10章左右要有一个大爽点\n" +
            "3. **节奏把控**: 快慢结合，张弛有度\n" +
            "4. **悬念管理**: 解决一个悬念，埋下新的悬念\n" +
            "5. **角色成长**: 主角要有明显的成长轨迹\n\n" +
            
            getGenreSpecificGuidance(genre) + "\n\n" +
            
            "**【网文大纲结构要求】**\n" +
            "请按照以下格式输出，每个部分用【===】分隔：\n\n" +
            
            "【===核心主题===】\n" +
            "(不要空泛哲学，要具体的情感冲突和人性主题)\n\n" +
            
            "【===主要角色===】\n" +
            "(主角：姓名、年龄、性格特点、出身背景、内心渴望、成长弧线)\n" +
            "(重要配角：2-3个，每个都要有自己的目标和动机，不是工具人)\n" +
            "(反派设定：不能脸谱化，要有复杂动机和理由)\n\n" +
            
            "【===情节结构===】\n" +
            "(三幕结构 + 卷规划)\n" +
            "第一卷（第1-100章）：建立日常世界 + 金手指初现 + 家族危机\n" +
            "第二卷（第101-200章）：进入修行世界 + 实力提升 + 人际统网\n" +
            "第三卷（第201-300章）：大派争锋 + 真相揭示 + 命运抓择\n" +
            "(每卷都要有独立的主题和完整故事弧)\n\n" +
            
            "【===世界设定===】\n" +
            "(不要一次性全部交代，要分层逐步展现)\n" +
            "- 基础世界：山村、市镇、王朝等凡人世界的社会结构\n" +
            "- 修行体系：不要直接说'修仙'，用'山中人''通灵者'等民间说法\n" +
            "- 力量规则：要有明确的限制和代价\n" +
            "- 神秘元素：只提及存在，不详细解释\n\n" +
            
            "【===关键元素===】\n" +
            "(金手指、重要道具、关键地点，但不要解释具体作用)\n" +
            "- 主角的金手指：不能是'系统'，要有神秘起源和代价\n" +
            "- 重要道具：与金手指相关的物品\n" +
            "- 关键地点：推动剧情的重要场所\n\n" +
            
            "【===冲突类型===】\n" +
            "- 内在冲突：主角的性格缺陷、价值观冲突、成长痛苦\n" +
            "- 人际冲突：家族关系、朋友背叛、爱情纷争\n" +
            "- 社会冲突：阶级固化、权势压迫、生存危机\n" +
            "- 超自然冲突：与修行者的冲突，但不是开局就有\n\n" +
            
            "**注意**：\n" +
            "1. 大纲要靠近读者的阅读习惯和期待\n" +
            "2. 每个部分都要有实质性内容，不要空话\n" +
            "3. 要为后续写作预留发展空间\n" +
            "4. 符合%s类网文的特点和读者频期\n\n" +
            
            "🏆 **目标**：创造一部能让读者一口气追下去的%s类精品网文！",
            
            novel.getTitle(),
            genre,
            outline.getBasicIdea(),
            outline.getTargetWordCount() != null ? outline.getTargetWordCount() : 100000,
            outline.getTargetChapterCount() != null ? outline.getTargetChapterCount() : 100,
            genre,
            genre
        );
    }
    
    /**
     * 获取类型特定指导
     */
    private String getGenreSpecificGuidance(String genre) {
        switch (genre) {
            case "玄幻":
                return "**【玄幻类特定指导】**\n" +
                       "- 等级体系：境界要清晰，升级要有仪式感\n" +
                       "- 装逼打脸：适度装逼，狠狠打脸\n" +
                       "- 宝物功法：让读者眼前一亮的好东西\n" +
                       "- 美女配角：傾国僾城但不能抢主角风头\n" +
                       "- 反派设计：要让读者恨得牙痒痒";
            case "都市":
                return "**【都市类特定指导】**\n" +
                       "- 装逼要自然：不刻意，顺其自然地展示实力\n" +
                       "- 金钱地位：豪车名表要恰到好处\n" +
                       "- 美女要现代：符合都市人设，不要太夸张\n" +
                       "- 反派现实感：不能太脸谱化\n" +
                       "- 专业领域：医术/商战/科技要有专业感";
            case "系统":
                return "**【系统流特定指导】**\n" +
                       "- 系统奖励：要让读者眼馋的好东西\n" +
                       "- 任务设计：有挑战但能完成\n" +
                       "- 升级仪式感：数据变化要爽\n" +
                       "- 系统互动：有个性，不死板\n" +
                       "- 商城系统：好东西要多，但不能太容易得到";
            default:
                return "**【通用类型指导】**\n" +
                       "- 角色成长清晰\n" +
                       "- 冲突层层递进\n" +
                       "- 悬念环环相扣";
        }
    }

    /**
     * 构建大纲修订提示词
     */
    private String buildOutlineRevisionPrompt(NovelOutline outline, String userFeedback) {
        return String.format(
            "请根据用户反馈修改以下小说大纲：\n\n" +
            "当前大纲内容：\n" +
            "核心主题：%s\n" +
            "主要角色：%s\n" +
            "情节结构：%s\n" +
            "世界设定：%s\n\n" +
            "用户反馈：%s\n\n" +
            "请根据反馈重新生成修改后的大纲内容，保持原有格式。",
            outline.getCoreTheme() != null ? outline.getCoreTheme() : "未设定",
            outline.getMainCharacters() != null ? outline.getMainCharacters() : "未设定",
            outline.getPlotStructure() != null ? outline.getPlotStructure() : "未设定",
            outline.getWorldSetting() != null ? outline.getWorldSetting() : "未设定",
            userFeedback
        );
    }

    /**
     * 解析AI响应并设置大纲内容（基于明确段落标题的正则解析）
     */
    private void parseAndSetOutlineContent(NovelOutline outline, String aiResponse) {
        System.out.println("=== 开始解析AI响应 ===");
        if (aiResponse == null) aiResponse = "";
        String text = aiResponse.replace("\r", "");

        // 使用段落标题进行提取
        String coreTheme = extractSection(text, "核心主题");
        String mainCharacters = extractSection(text, "主要角色");
        String plotStructure = extractSection(text, "情节结构");
        String worldSetting = extractSection(text, "世界设定");
        String keyElements = extractSection(text, "关键元素");
        String conflictTypes = extractSection(text, "冲突类型");

        if (coreTheme != null) outline.setCoreTheme(coreTheme.trim());
        if (mainCharacters != null) outline.setMainCharacters(mainCharacters.trim());
        if (plotStructure != null) outline.setPlotStructure(plotStructure.trim());
        if (worldSetting != null) outline.setWorldSetting(worldSetting.trim());
        if (keyElements != null) outline.setKeyElements(toJsonArray(keyElements.trim()));
        if (conflictTypes != null) outline.setConflictTypes(toJsonArray(conflictTypes.trim()));

        // 若仍有为空的字段，做兜底
        if (outline.getCoreTheme() == null || outline.getCoreTheme().isEmpty()) {
            outline.setCoreTheme(text.length() > 800 ? text.substring(0, 800) : text);
        }
        if (outline.getKeyElements() == null || outline.getKeyElements().isEmpty()) {
            outline.setKeyElements(toJsonArray(text));
        }
        if (outline.getConflictTypes() == null || outline.getConflictTypes().isEmpty()) {
            outline.setConflictTypes(toJsonArray(text));
        }
        System.out.println("=== AI响应解析完成 ===");
    }

    /**
     * 提取指定标题的段落内容，格式形如：
     * 【===标题===】\n内容... 直到下一个【===...===】或文本末尾
     */
    private String extractSection(String text, String title) {
        try {
            String pattern = "【===" + java.util.regex.Pattern.quote(title) + "===】\\n" + "([\\s\\S]*?)" + "(?=【===|\n【===|$)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 将自由文本规范化为JSON数组字符串
     */
    private String toJsonArray(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return "[]";
            }
            String normalized = text.replace("\r", "");
            // 统一分隔符：按换行、中文逗号/顿号/分号、英文逗号/分号切分
            String[] parts = normalized.split("\n|，|、|；|;|,");
            java.util.List<String> items = new java.util.ArrayList<>();
            for (String p : parts) {
                String s = p.trim();
                if (!s.isEmpty()) {
                    items.add(s);
                }
            }
            if (items.isEmpty()) {
                items.add(normalized.trim());
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(items);
        } catch (Exception e) {
            // 兜底返回空数组，避免写入非法JSON
            return "[]";
        }
    }

    /**
     * 设置默认大纲内容
     */
    private void setDefaultOutlineContent(NovelOutline outline, Novel novel) {
        outline.setCoreTheme("成长与变化的主题，探索人性的复杂性");
        outline.setMainCharacters("主角：勇敢而有决心的年轻人，面临重大选择；配角：智慧导师，忠实朋友；反派：强大而复杂的对手");
        outline.setPlotStructure("第一幕：建立日常世界，触发事件；第二幕：进入特殊世界，面临挑战和成长；第三幕：最终对决，获得成长");
        outline.setWorldSetting("丰富而完整的世界观，有其独特的规则和文化背景");
        outline.setKeyElements(toJsonArray("关键道具，重要地点，核心概念"));
        outline.setConflictTypes(toJsonArray("内在冲突：自我认知的挣扎；外在冲突：与对手的对抗；环境冲突：与世界规则的冲突"));
    }

    private String assembleIdeaFromOutline(NovelOutline outline) {
        // 第一阶段生成的大纲保存于 plotStructure（整体剧情结构/长线大纲）
        if (outline.getPlotStructure() != null && !outline.getPlotStructure().trim().isEmpty()) {
            return outline.getPlotStructure();
        }
        // 回退：basicIdea 作为最小可用输入
        return outline.getBasicIdea() == null ? "" : outline.getBasicIdea();
    }

    /**
     * AI优化大纲（流式）
     */
    public void optimizeOutlineStream(Long novelId, String currentOutline, String suggestion, com.novel.dto.AIConfigRequest aiConfig, java.util.function.Consumer<String> chunkConsumer) {
        logger.info("🎨 开始流式优化小说 {} 的大纲", novelId);
        
        try {
            // 获取小说信息
            Novel novel = novelRepository.selectById(novelId);
            if (novel == null) {
                throw new RuntimeException("小说不存在");
            }
            
            // 构建优化提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一位资深网文编辑，现在需要根据用户的建议优化小说大纲。\n\n");
            prompt.append("**小说信息：**\n");
            prompt.append("- 标题：").append(novel.getTitle()).append("\n");
            prompt.append("- 类型：").append(novel.getGenre()).append("\n");
            if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
                prompt.append("- 简介：").append(novel.getDescription()).append("\n");
            }
            prompt.append("\n**当前大纲：**\n");
            prompt.append(currentOutline).append("\n\n");
            prompt.append("**优化建议：**\n");
            prompt.append(suggestion).append("\n\n");
            prompt.append("**优化要求：**\n");
            prompt.append("1. 在保持原有大纲核心框架的基础上，根据用户建议进行改进\n");
            prompt.append("2. 确保优化后的大纲逻辑连贯、情节合理\n");
            prompt.append("3. 保持大纲的结构清晰，层次分明\n");
            prompt.append("4. 直接输出优化后的完整大纲，不要添加\"根据建议\"等元话语\n\n");
            prompt.append("请直接输出优化后的大纲：\n");
            
            // 使用流式生成
            StringBuilder accumulated = new StringBuilder();
            aiWritingService.streamGenerateContent(prompt.toString(), "outline_optimization", aiConfig, chunk -> {
                accumulated.append(chunk);
                chunkConsumer.accept(chunk);
            });
            
            // 更新数据库
            try {
                NovelOutline outline = outlineRepository.findByNovelId(novelId).orElse(null);
                if (outline != null) {
                    outline.setPlotStructure(accumulated.toString());
                    outline.setUpdatedAt(LocalDateTime.now());
                    outlineRepository.updateById(outline);
                }
                
                novel.setOutline(accumulated.toString());
                novelRepository.updateById(novel);
            } catch (Exception e) {
                logger.warn("更新大纲到数据库失败", e);
            }
            
            logger.info("✅ 大纲流式优化完成");
            
        } catch (Exception e) {
            logger.error("❌ 大纲流式优化失败", e);
            throw new RuntimeException("大纲优化失败: " + e.getMessage());
        }
    }

    /**
     * AI优化大纲（非流式，保留兼容）
     */
    public String optimizeOutline(Long novelId, String currentOutline, String suggestion) {
        logger.info("🎨 开始优化小说 {} 的大纲", novelId);
        
        try {
            // 获取小说信息
            Novel novel = novelRepository.selectById(novelId);
            if (novel == null) {
                throw new RuntimeException("小说不存在");
            }
            
            // 构建优化提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一位资深网文编辑，现在需要根据用户的建议优化小说大纲。\n\n");
            prompt.append("**小说信息：**\n");
            prompt.append("- 标题：").append(novel.getTitle()).append("\n");
            prompt.append("- 类型：").append(novel.getGenre()).append("\n");
            if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
                prompt.append("- 简介：").append(novel.getDescription()).append("\n");
            }
            prompt.append("\n**当前大纲：**\n");
            prompt.append(currentOutline).append("\n\n");
            prompt.append("**优化建议：**\n");
            prompt.append(suggestion).append("\n\n");
            prompt.append("**优化要求：**\n");
            prompt.append("1. 在保持原有大纲核心框架的基础上，根据用户建议进行改进\n");
            prompt.append("2. 确保优化后的大纲逻辑连贯、情节合理\n");
            prompt.append("3. 保持大纲的结构清晰，层次分明\n");
            prompt.append("4. 直接输出优化后的完整大纲，不要添加\"根据建议\"等元话语\n\n");
            prompt.append("请直接输出优化后的大纲：\n");
            
            // 调用AI生成优化后的大纲
            String optimizedOutline = aiWritingService.generateContent(prompt.toString(), "outline_optimization");
            
            // 更新数据库中的大纲
            try {
                NovelOutline outline = outlineRepository.findByNovelId(novelId).orElse(null);
                if (outline != null) {
                    outline.setPlotStructure(optimizedOutline);
                    outline.setUpdatedAt(LocalDateTime.now());
                    outlineRepository.updateById(outline);
                }
                
                // 同时更新novels表
                novel.setOutline(optimizedOutline);
                novelRepository.updateById(novel);
            } catch (Exception e) {
                logger.warn("更新大纲到数据库失败，但返回优化结果", e);
            }
            
            logger.info("✅ 大纲优化完成");
            return optimizedOutline;
            
        } catch (Exception e) {
            logger.error("❌ 大纲优化失败", e);
            throw new RuntimeException("大纲优化失败: " + e.getMessage());
        }
    }

    /**
     * 根据大纲生成世界观并保存到记忆库
     */
    private void generateAndSaveWorldView(Novel novel, String outlineContent, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("🌍 开始根据大纲生成世界观，小说ID: {}", novel.getId());
        
        try {
            // 构建AI提示词
            String prompt = buildWorldViewGenerationPrompt(novel, outlineContent);
            
            // 调用AI生成世界观（使用带AI配置的方法）
            String worldViewJson = aiWritingService.generateContent(prompt, "world_view_generation", aiConfig);
            
            // 解析世界观JSON
            Map<String, Object> worldView = parseWorldViewJson(worldViewJson);
            
            // 加载或创建记忆库
            Map<String, Object> memoryBank = longNovelMemoryManager.loadMemoryBankFromDatabase(novel.getId());
            if (memoryBank == null) {
                memoryBank = new java.util.HashMap<>();
                logger.info("创建新的记忆库");
            }
            
            // 将世界观保存到记忆库
            memoryBank.put("worldSettings", worldView);
            
            // 保存记忆库到数据库
            longNovelMemoryManager.saveMemoryBankToDatabase(novel.getId(), memoryBank, 0);
            
            logger.info("✅ 世界观生成并保存成功");
            
        } catch (Exception e) {
            logger.error("世界观生成失败: {}", e.getMessage(), e);
            // 不抛出异常，世界观生成失败不应影响大纲生成
        }
    }

    /**
     * 构建世界观生成的AI提示词
     */
    private String buildWorldViewGenerationPrompt(Novel novel, String outlineContent) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("根据以下小说信息和大纲，生成详细的世界观设定。\n\n");
        prompt.append("小说基本信息：\n");
        prompt.append("- 标题：").append(novel.getTitle()).append("\n");
        prompt.append("- 类型：").append(novel.getGenre()).append("\n");
        if (novel.getDescription() != null) {
            prompt.append("- 简介：").append(novel.getDescription()).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("小说大纲：\n");
        prompt.append(outlineContent.length() > 2000 ? outlineContent.substring(0, 2000) + "..." : outlineContent);
        prompt.append("\n\n");
        
        prompt.append("请生成以下世界观设定，并以JSON格式返回：\n");
        prompt.append("1. 世界背景：时代背景、地理环境、社会结构\n");
        prompt.append("2. 力量体系：修炼/能力/科技等体系的等级和规则\n");
        prompt.append("3. 主要势力：重要的组织、门派、国家等\n");
        prompt.append("4. 特殊设定：独特的世界观元素（法宝、异兽、禁地等）\n");
        prompt.append("5. 文化习俗：社会规则、传统习俗、禁忌等\n\n");
        
        prompt.append("返回格式要求（纯JSON，无其他说明）：\n");
        prompt.append("{\n");
        prompt.append("  \"worldBackground\": \"世界背景描述\",\n");
        prompt.append("  \"powerSystem\": {\n");
        prompt.append("    \"name\": \"体系名称\",\n");
        prompt.append("    \"levels\": [\"等级1\", \"等级2\", ...],\n");
        prompt.append("    \"description\": \"体系说明\"\n");
        prompt.append("  },\n");
        prompt.append("  \"majorForces\": [\n");
        prompt.append("    {\"name\": \"势力名\", \"type\": \"类型\", \"description\": \"描述\"},\n");
        prompt.append("    ...\n");
        prompt.append("  ],\n");
        prompt.append("  \"specialSettings\": [\n");
        prompt.append("    {\"name\": \"设定名\", \"category\": \"分类\", \"description\": \"描述\"},\n");
        prompt.append("    ...\n");
        prompt.append("  ],\n");
        prompt.append("  \"culture\": \"文化习俗描述\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }

    /**
     * 解析世界观JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseWorldViewJson(String jsonStr) {
        try {
            // 清理可能的markdown代码块标记
            jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            
            // 使用Jackson或Gson解析（这里简化处理）
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonStr, Map.class);
            
        } catch (Exception e) {
            logger.warn("解析世界观JSON失败，使用默认结构: {}", e.getMessage());
            
            // 返回基本结构
            Map<String, Object> defaultWorldView = new java.util.HashMap<>();
            defaultWorldView.put("worldBackground", "待完善");
            defaultWorldView.put("powerSystem", new java.util.HashMap<>());
            defaultWorldView.put("majorForces", new java.util.ArrayList<>());
            defaultWorldView.put("specialSettings", new java.util.ArrayList<>());
            defaultWorldView.put("culture", "待完善");
            
            return defaultWorldView;
        }
    }
}