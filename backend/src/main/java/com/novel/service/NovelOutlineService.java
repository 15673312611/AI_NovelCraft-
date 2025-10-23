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

    // 兼容方法：改为宏观不锁剧情的全书大纲提示词
    private String buildSuperOutlinePromptCompat(Novel novel, String originalIdea, Integer targetChapters, Integer targetWords) {
        int tc = targetChapters == null ? 0 : targetChapters;
        int volumeCount;
        if (tc >= 200) volumeCount = 8; else if (tc >= 150) volumeCount = 6; else if (tc >= 100) volumeCount = 5; else if (tc >= 50) volumeCount = 3; else volumeCount = 2;
        return String.format(
            "你的身份\n" +
            "- 资深网文总编/剧情架构师，擅长为长篇连载搭建“可持续、不跑偏”的叙事引擎。大纲只给方向与约束，不写具体执行路径。\n\n" +
            "任务目标\n" +
            "- 根据输入信息生成“全书大纲 + 分卷框架”的宏观蓝图：风格与方向明确、世界观骨架清晰、冲突与节奏稳健；不固定具体剧情与章节，不写对话与招式过程。\n\n" +
            "输入\n" +
            "- 标题：%s\n" +
            "- 类型/子类型：%s\n" +
            "- 体量：约%d章，%d字\n" +
            "- 构思/主题要义：%s\n" +
            "- 计划卷数：%d\n\n" +
            "创作总则\n" +
            "- 市场定位先行：明确目标读者与主卖点，确定叙事基调与阅读节奏承诺。\n" +
            "- 人物驱动：以“缺陷—欲望—责任—恐惧”驱动选择；转折由人物决策触发。\n" +
            "- 世界观骨架：只写第一性原理、力量/权限规则与代价、势力与秩序，保留必要留白与不确定性。\n" +
            "- 冲突系统：设定“主死亡类型”（肉体/事业/心理）为主线，其他为辅；全程维持信息差与悬念。\n" +
            "- 节奏与爽点：高能节点按等距或渐进间隔布局，延迟满足；始终有目标、代价与回报的闭环。\n" +
            "- 反派与对手：层级递进、动机自洽，能反向塑造主角段位。\n" +
            "- 运行约束：不锁具体事件顺序、不编号到章、不写细节过程；保持可扩展与可迭代。\n\n" +
            "输出结构（用流畅中文分段叙述，非表格、非JSON）\n" +
            "1) 项目定位\n" +
            "   - 目标读者与主卖点清单（2-4项）\n" +
            "   - 叙事基调与阅读节奏承诺\n" +
            "2) 底层引擎\n" +
            "   - 冲突升级链（从小到大、从外到内的递进逻辑）\n" +
            "   - 舞台升级阶梯（空间/权限/地图的层层开权）\n" +
            "   - 信息差与悬念体系（总悬念 + 阶段性悬念若干）\n" +
            "   - 力量/权限与代价规则（可用、可失、可反噬）\n" +
            "3) 核心人物弧光图\n" +
            "   - 主角：初始处境、缺陷、欲望、原则、底线、成长转折点\n" +
            "   - 关键角色（2-4名）：各自目标与与主角的动态关系张力\n" +
            "   - 关系网的进化原则与边界\n" +
            "4) 分卷框架（共%d卷）\n" +
            "   - 每卷包含：卷名（隐喻主题）、核心任务（一句话目标）\n" +
            "   - 关键节点（3-5个，给出“节点类型”标签：纵向突破/横向拓展/关系转折/认知升级/资源更替等；只写目标、阻力与结果，不写执行过程）\n" +
            "   - 卷末状态：实力与地位、认知与心态、未解悬念与下一卷钩子\n" +
            "5) 节奏与爽点规划\n" +
            "   - 高能节点分布策略（频率/强度/代价回收）\n" +
            "   - 断章钩子与时间锁/空间锁的使用原则\n" +
            "6) 长线伏笔与回收\n" +
            "   - 若干条贯穿线：埋设阶段、触发条件、回收方向（不锁具体事件）\n" +
            "7) 反跑偏守则\n" +
            "   - 人物一致性与决策准则\n" +
            "   - 世界规则的不可违背项与可演绎项\n" +
            "   - 换地图与提权的触发条件与成本\n\n" +
            "写作与风格要求\n" +
            "- 采用专业编辑的叙述语气，逻辑清晰、画面感强，但不堆砌细节过程。\n" +
            "- 只输出大纲正文内容；不输出代码、JSON、列表编号到具体章节。\n" +
            "- 禁止固定剧情走法与对话描摹；避免模板化腔调与陈词滥调。\n" +
            "- 全文保持“目标—阻力—选择—代价—新局”的因果链条。\n\n" +
            "交付格式\n" +
            "- 使用清晰层级小标题与段落叙述呈现以上七部分。\n" +
            "- 全文为中文，不添加解释性附注与示范文本。",
            novel.getTitle(),
            novel.getGenre(),
            tc,
            targetWords == null ? 0 : targetWords,
            originalIdea == null ? "" : originalIdea,
            volumeCount,
            volumeCount
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
        int tc = outline.getTargetChapterCount() != null ? outline.getTargetChapterCount() : 0;
        int volumeCount;
        if (tc >= 200) volumeCount = 8; else if (tc >= 150) volumeCount = 6; else if (tc >= 100) volumeCount = 5; else if (tc >= 50) volumeCount = 3; else volumeCount = 2;
        return String.format(
            "你的身份\n" +
            "- 资深网文总编/剧情架构师，擅长为长篇连载搭建“可持续、不跑偏”的叙事引擎。大纲只给方向与约束，不写具体执行路径。\n\n" +
            "任务目标\n" +
            "- 根据输入信息生成“全书大纲 + 分卷框架”的宏观蓝图：风格与方向明确、世界观骨架清晰、冲突与节奏稳健；不固定具体剧情与章节，不写对话与招式过程。\n\n" +
            "输入\n" +
            "- 标题：%s\n" +
            "- 类型/子类型：%s\n" +
            "- 体量：约%d章，%d字\n" +
            "- 构思/主题要义：%s\n" +
            "- 计划卷数：%d\n\n" +
            "创作总则\n" +
            "- 市场定位先行：明确目标读者与主卖点，确定叙事基调与阅读节奏承诺。\n" +
            "- 人物驱动：以“缺陷—欲望—责任—恐惧”驱动选择；转折由人物决策触发。\n" +
            "- 世界观骨架：只写第一性原理、力量/权限规则与代价、势力与秩序，保留必要留白与不确定性。\n" +
            "- 冲突系统：设定“主死亡类型”（肉体/事业/心理）为主线，其他为辅；全程维持信息差与悬念。\n" +
            "- 节奏与爽点：高能节点按等距或渐进间隔布局，延迟满足；始终有目标、代价与回报的闭环。\n" +
            "- 反派与对手：层级递进、动机自洽，能反向塑造主角段位。\n" +
            "- 运行约束：不锁具体事件顺序、不编号到章、不写细节过程；保持可扩展与可迭代。\n\n" +
            "输出结构（用流畅中文分段叙述，非表格、非JSON）\n" +
            "1) 项目定位\n" +
            "   - 目标读者与主卖点清单（2-4项）\n" +
            "   - 叙事基调与阅读节奏承诺\n" +
            "2) 底层引擎\n" +
            "   - 冲突升级链（从小到大、从外到内的递进逻辑）\n" +
            "   - 舞台升级阶梯（空间/权限/地图的层层开权）\n" +
            "   - 信息差与悬念体系（总悬念 + 阶段性悬念若干）\n" +
            "   - 力量/权限与代价规则（可用、可失、可反噬）\n" +
            "3) 核心人物弧光图\n" +
            "   - 主角：初始处境、缺陷、欲望、原则、底线、成长转折点\n" +
            "   - 关键角色（2-4名）：各自目标与与主角的动态关系张力\n" +
            "   - 关系网的进化原则与边界\n" +
            "4) 分卷框架（共%d卷）\n" +
            "   - 每卷包含：卷名（隐喻主题）、核心任务（一句话目标）\n" +
            "   - 关键节点（3-5个，给出“节点类型”标签：纵向突破/横向拓展/关系转折/认知升级/资源更替等；只写目标、阻力与结果，不写执行过程）\n" +
            "   - 卷末状态：实力与地位、认知与心态、未解悬念与下一卷钩子\n" +
            "5) 节奏与爽点规划\n" +
            "   - 高能节点分布策略（频率/强度/代价回收）\n" +
            "   - 断章钩子与时间锁/空间锁的使用原则\n" +
            "6) 长线伏笔与回收\n" +
            "   - 若干条贯穿线：埋设阶段、触发条件、回收方向（不锁具体事件）\n" +
            "7) 反跑偏守则\n" +
            "   - 人物一致性与决策准则\n" +
            "   - 世界规则的不可违背项与可演绎项\n" +
            "   - 换地图与提权的触发条件与成本\n\n" +
            "写作与风格要求\n" +
            "- 采用专业编辑的叙述语气，逻辑清晰、画面感强，但不堆砌细节过程。\n" +
            "- 只输出大纲正文内容；不输出代码、JSON、列表编号到具体章节。\n" +
            "- 禁止固定剧情走法与对话描摹；避免模板化腔调与陈词滥调。\n" +
            "- 全文保持“目标—阻力—选择—代价—新局”的因果链条。\n\n" +
            "交付格式\n" +
            "- 使用清晰层级小标题与段落叙述呈现以上七部分。\n" +
            "- 全文为中文，不添加解释性附注与示范文本。",
            novel.getTitle(),
            novel.getGenre() == null ? "" : novel.getGenre(),
            tc,
            outline.getTargetWordCount() != null ? outline.getTargetWordCount() : 0,
            outline.getBasicIdea() == null ? "" : outline.getBasicIdea(),
            volumeCount,
            volumeCount
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