package com.novel.service;

import com.novel.domain.entity.NovelOutline;
import com.novel.domain.entity.Novel;
import com.novel.repository.NovelOutlineRepository;
import com.novel.repository.NovelRepository;
import com.novel.common.security.AuthUtils;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern SIMPLE_FORMAT_SPECIFIER_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([sd])");
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final List<String> OUTLINE_PROMPT_FORMAT_SIGNATURE = List.of("%s", "%d", "%d", "%s");
    private static final long STREAM_SNAPSHOT_MIN_INTERVAL_MS = 800L;
    private static final int STREAM_SNAPSHOT_MIN_CHARS = 600;
    private static final String DEFAULT_ADVANCED_OUTLINE_PROMPT =
            "你是一位资深网文主编兼大纲架构师,擅长从用户构思中提炼核心创意与写作指南,产出可拆分卷的全书大纲与世界观蓝图。【重要说明】本大纲将同时用于两个下游任务:- 拆分卷:需要清晰的剧情阶段划分(三幕式、卷级目标)- 提炼核心设定:需要明确\"核心创意、写作基调、叙事风格、主角核心特质、写作禁忌\"等稳定信息。因此:请在第1部分详尽给出写作指南;剧情部分只提供阶段走向,不写具体章节细节。**第一步:风格分析与定位** 1. **深度解读构思**:请仔细阅读下面的用户构思,识别其核心风格(例如:热血爽文、悬疑惊悚、甜宠恋爱、残酷黑暗等)、叙事节奏(快节奏/慢热)、以及潜在的目标读者(男频/女频,青少年/成人)。2. **抓住核心魅力**:找出构思中最吸引人的\"钩子\",是独特的金手指设定?是新颖的世界观?还是极致的情感冲突?以此为中心进行放大。**输入信息** - 小说标题:%s - 预计体量:约%d章,%d字 - **用户核心构思**:%s **第二步:大纲创作核心原则** 1. **风格至上**:生成的大纲必须与用户构思的风格和基调完全一致。如果构思是轻松幽默的,大纲就不能严肃沉重。反之亦然。2. **拥抱设定,拒绝说教**:对于\"金手指\"或不寻常的设定,你的任务是让它变得更酷、更有趣,而不是去质疑其合理性或起源。直接展示其强大和独特,激发读者爽点。3. **严守题材边界,拒绝画蛇添足**:严格在用户构思的题材内进行扩展。例如,一个女频\"读心\"或\"弹幕\"类的轻松爽文,其金手指的来源解释应保持简洁或模糊化,重点是利用它来制造爽点和情节。**严禁**擅自引入与核心题材无关的宏大概念,如\"高维文明\"、\"宇宙实验\"、\"AI觉醒\"等,除非用户构思中明确提出。4. **读者为王**:时刻思考\"读者想看什么?\"。围绕核心魅力点,设计升级打怪、情感纠葛、解开谜题等情节,持续提供读者期待的内容。5. **动态与留白**:大纲是蓝图而非剧本。只设定关键转折点、核心冲突和人物成长弧光,为具体情节保留创作自由度。6. **逻辑自洽**:确保大纲中的所有设定、人物行为、剧情发展都符合内在逻辑。人物的能力与限制要前后一致,重大事件的因果关系要合理,角色的动机与行为要匹配。避免为了制造冲突而强行降智或违背已有设定。特别注意:如果设定了某个角色拥有强大能力,必须合理解释为何在关键时刻未能发挥作用;如果设定了某个金手指有限制,后续剧情不能随意突破这个限制。 **第三步:输出结构** 请用流畅的中文分段叙述,总字数控制在4000字以内,保持精炼和高信息密度。1. **核心创意与写作指南** - 一句话核心创意(用一句话概括这本小说的独特之处)- 核心看点(3-5个,明确读者为什么要看)- 写作基调(如:轻松幽默/热血爽快/黑暗压抑/克制写实)- 叙事风格(节奏:快/慢;语言特点:口语/古风/网感;重点描写:战斗/心理/对话/氛围)- 写作禁忌(明确不写什么,避免偏题)- 创作前提假设(列出支撑故事逻辑的关键前提,确保后续剧情有据可依)2. **世界观核心设定** - 力量体系(核心规则、等级划分、限制与代价、特色设定,要易于理解且爽点突出)- 关键势力与地图(3-4个初期核心势力,以及它们的关系与主要冲突)- 支撑故事的核心法则或背景(简要,不做硬科普)3. **主角人物设定** - 主角名称 - 初始状态与核心驱动力(他/她最大的欲望和恐惧是什么?)- 金手指/核心能力(直接说明其效果和限制,以及它如何让主角与众不同)- 核心特质(贯穿全书不变的性格特征)- 行事风格(果断/谨慎/莽撞/智谋等)- 成长方向(大方向,不写具体剧情)4. **重要配角设定** - 列出2-4位关键配角:人设、性格、与主角关系定位 - 初始目标与立场(只写初始设定,不写后续发展)5. **全书故事线(三幕式)** - **初期(1-3卷)**:开局与崛起。主角如何获得金手指/卷入事件,快速建立优势,解决第一个大危机,确立短期目标。- **中期(4-6卷)**:发展与挑战。主角进入更大舞台,遭遇更强敌人,揭露世界观的冰山一角,达成中期目标,但引出更大危机/谜团。- **后期(7-N卷)**:高潮与结局。面对终极BOSS或解决核心矛盾,达成人生目标或揭开最终真相,故事圆满收尾。**交付要求** - 第1部分\"核心创意与写作指南\"必须详细,这是后续AI写作的核心参考。- **绝对禁止**:输出任何反思、说教、建议或对用户构思的批评。你的任务是执行和优化,不是评审。- **严格遵守**:只输出上述结构要求的大纲正文,不要有任何额外的文字,包括\"好的,这是您的大纲\"这类话语。- **保持简洁**:不要使用复杂的表格或JSON,就用清晰的小标题和段落文字。";

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
    
    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private CoreSettingsExtractionService coreSettingsExtractionService;

    @Autowired
    private ManagedPromptService managedPromptService;

    /**
     * 生成初始大纲
     */
    @Transactional
    public NovelOutline generateInitialOutline(Long novelId, String basicIdea, Integer targetWordCount, Integer targetChapterCount) {
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法生成大纲");
        }
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
        // 类型字段不再强制设置，保留为空以便AI自行推断

        // 使用AI生成详细大纲内容
        generateOutlineContentWithAI(outline, novel);

        // 保存大纲
        System.out.println("=== 保存大纲到数据库 ===");
        System.out.println("Debug - 保存前的大纲内容:");
        System.out.println("  - id: " + outline.getId());
        System.out.println("  - novelId: " + outline.getNovelId());
        System.out.println("  - title: " + outline.getTitle());
        // System.out.println("  - genre: " + outline.getGenre());
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
     * 说明：如果已存在大纲，则清空内容并更新；否则创建新记录
     */
    @Transactional
    public NovelOutline initOutlineRecord(Long novelId, String basicIdea, Integer targetWordCount, Integer targetChapterCount) {
        Novel novel = novelRepository.selectById(novelId);
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + novelId);
        }

        // 检查是否已存在大纲
        Optional<NovelOutline> existingOutline = findByNovelId(novelId);
        NovelOutline outline;

        if (existingOutline.isPresent()) {
            // 重新生成：清空现有大纲内容，更新参数
            outline = existingOutline.get();
            outline.setBasicIdea(basicIdea);
            outline.setTargetWordCount(targetWordCount);
            outline.setTargetChapterCount(targetChapterCount);
            outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
            // 清空旧的大纲内容，准备重新生成
            outline.setPlotStructure("");
            outline.setCoreTheme(null);
            outline.setMainCharacters(null);
            outline.setWorldSetting(null);
            outline.setKeyElements(null);
            outline.setConflictTypes(null);
            outlineRepository.updateById(outline);
            logger.info("📝 重新生成大纲：清空现有大纲内容，outlineId={}", outline.getId());
        } else {
            // 首次生成：创建新记录
            outline = new NovelOutline(novelId, basicIdea);
            outline.setTargetWordCount(targetWordCount);
            outline.setTargetChapterCount(targetChapterCount);
            outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
            outlineRepository.insert(outline);
            logger.info("📝 首次生成大纲：创建新记录，outlineId={}", outline.getId());
        }

        // 同时清空 novels 表中的 outline 字段
        novel.setOutline("");
        novelRepository.updateById(novel);

        return outline;
    }

    /**
     * 流式生成大纲内容（真正的流式AI调用）
     * 说明：使用流式AI接口，逐块返回生成内容，同时实时写入数据库
     * 注意：移除@Transactional，因为流式处理是渐进式的，每次chunk更新都是独立的数据库操作
     */
    public void streamGenerateOutlineContent(NovelOutline outline, com.novel.dto.AIConfigRequest aiConfig, Long templateId, Integer outlineWordLimit, java.util.function.Consumer<String> chunkConsumer) {
        Novel novel = novelRepository.selectById(outline.getNovelId());
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + outline.getNovelId());
        }

        // 默认字数限制为2000
        int wordLimit = outlineWordLimit != null ? outlineWordLimit : 2000;

        // 如果提供了模板ID，使用模板；否则使用默认提示词
        String prompt = resolveOutlineGenerationPrompt(novel, outline, templateId, wordLimit);
        if (false) {
            // 使用模板生成提示词
            String templateContent = promptTemplateService.getTemplateContent(templateId);
            if (templateContent != null && !templateContent.trim().isEmpty()) {
                // 模板内容作为基础，需要替换占位符
                prompt = buildOutlinePromptFromTemplate(templateContent, novel, outline.getBasicIdea(), outline.getTargetChapterCount(), outline.getTargetWordCount(), wordLimit);
            } else {
                // 模板不存在或为空，使用默认提示词
                logger.warn("模板ID {} 不存在或为空，使用默认提示词", templateId);
                prompt = buildSuperOutlinePromptCompat(novel, outline.getBasicIdea(), outline.getTargetChapterCount(), outline.getTargetWordCount());
            }
        } else if (false) {
            // 使用默认提示词
            prompt = buildSuperOutlinePromptCompat(novel, outline.getBasicIdea(), outline.getTargetChapterCount(), outline.getTargetWordCount());
        }

        // 使用真正的流式AI调用 - 从空开始累加（不使用旧的大纲）
        StringBuilder accumulated = new StringBuilder();
        final long[] lastPersistAt = {System.currentTimeMillis()};
        final int[] lastPersistLength = {0};
        
        try {
            aiWritingService.streamGenerateContent(prompt, "outline_generation_stream", aiConfig, chunk -> {
                try {
                    accumulated.append(chunk);
                    if (chunkConsumer != null) {
                        chunkConsumer.accept(chunk);
                    }

                    String currentContent = accumulated.toString();
                    long now = System.currentTimeMillis();
                    if (shouldPersistStreamSnapshot(currentContent.length(), lastPersistLength[0], now, lastPersistAt[0])) {
                        persistOutlineStreamSnapshot(novel, outline, currentContent);
                        lastPersistAt[0] = now;
                        lastPersistLength[0] = currentContent.length();
                    }
                } catch (Exception e) {
                    logger.error("处理流式内容块失败: {}", e.getMessage(), e);
                    throw new RuntimeException("处理流式内容块失败: " + e.getMessage());
                }
            });

            persistOutlineStreamSnapshot(novel, outline, accumulated.toString());
            outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
            outlineRepository.updateById(outline);
            
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

    private boolean shouldPersistStreamSnapshot(int currentLength, int lastPersistLength, long now, long lastPersistAt) {
        return currentLength > lastPersistLength
                && (now - lastPersistAt >= STREAM_SNAPSHOT_MIN_INTERVAL_MS
                || currentLength - lastPersistLength >= STREAM_SNAPSHOT_MIN_CHARS);
    }

    private void persistOutlineStreamSnapshot(Novel novel, NovelOutline outline, String content) {
        novel.setOutline(content);
        novelRepository.updateById(novel);

        outline.setPlotStructure(content);
        outlineRepository.updateById(outline);
    }

    private String resolveOutlineGenerationPrompt(Novel novel,
                                                  NovelOutline outline,
                                                  Long templateId,
                                                  Integer outlineWordLimit) {
        if (templateId == null) {
            return buildSuperOutlinePromptCompat(
                    novel,
                    outline.getBasicIdea(),
                    outline.getTargetChapterCount(),
                    outline.getTargetWordCount()
            );
        }

        String templateContent = promptTemplateService.getTemplateContent(templateId);
        if (templateContent == null || templateContent.trim().isEmpty()) {
            logger.warn("妯℃澘ID {} 涓嶅瓨鍦ㄦ垨涓虹┖锛屼娇鐢ㄩ粯璁ゆ彁绀鸿瘝", templateId);
            return buildSuperOutlinePromptCompat(
                    novel,
                    outline.getBasicIdea(),
                    outline.getTargetChapterCount(),
                    outline.getTargetWordCount()
            );
        }

        return buildOutlinePromptFromTemplate(
                templateContent,
                novel,
                outline.getBasicIdea(),
                outline.getTargetChapterCount(),
                outline.getTargetWordCount(),
                outlineWordLimit
        );
    }

    // 兼容方法：改为宏观不锁剧情的全书大纲提示词（融入世界设定生成）
    private String buildSuperOutlinePromptCompat(Novel novel, String originalIdea, Integer targetChapters, Integer targetWords) {
        return buildAdvancedOutlinePrompt(novel, originalIdea, targetChapters, targetWords);
    }

    /**
     * 确认大纲（旧方法，保持兼容）
     * 说明：确认大纲状态，并自动触发基于大纲的卷拆分
     * @deprecated 请使用 confirmOutline(Long outlineId, AIConfigRequest aiConfig)
     */
    @Deprecated
    @Transactional
    public NovelOutline confirmOutline(Long outlineId) {
        return confirmOutline(outlineId, null, null);
    }
    
    /**
     * 确认大纲（支持AI配置）
     * 说明：确认大纲状态，并自动触发基于大纲的卷拆分
     */
    @Transactional
    public NovelOutline confirmOutline(Long outlineId, com.novel.dto.AIConfigRequest aiConfig) {
        return confirmOutline(outlineId, aiConfig, null);
    }

    @Transactional
    public NovelOutline confirmOutline(Long outlineId,
                                       com.novel.dto.AIConfigRequest aiConfig,
                                       Long volumeTemplateId) {
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
        
        boolean hasRequestedModel = aiConfig != null
                && aiConfig.getModel() != null
                && !aiConfig.getModel().trim().isEmpty();

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
            if (aiConfig != null && (aiConfig.isValid() || hasRequestedModel)) {
                logger.info("✅ 使用前端传递的AI配置生成卷规划");
                volumeService.generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount, aiConfig, volumeTemplateId);
            } else {
                logger.warn("⚠️ 未提供AI配置或配置无效，使用简化模式生成卷规划");
                volumeService.generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount, null, volumeTemplateId);
            }

            logger.info("✅ 大纲确认完成，ID: {}，已触发卷拆分任务，预计生成{}卷", outlineId, volumeCount);
        } catch (Exception e) {
            // AI服务异常时回退大纲状态
            logger.error("❌ 确认大纲后触发卷拆分失败，回退大纲状态: {}", e.getMessage(), e);
            try {
                // 将大纲状态回退为草稿
                outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
                outlineRepository.updateById(outline);
                logger.info("🔄 大纲状态已回退为草稿，用户可重新确认");
            } catch (Exception rollbackError) {
                logger.error("❌ 回退大纲状态失败: {}", rollbackError.getMessage(), rollbackError);
            }
            // 抛出异常，让前端知道失败了
            throw new RuntimeException("生成卷规划失败: " + e.getMessage() + "，大纲状态已回退，请重新确认");
        }

        // 异步提炼核心设定（不阻塞用户操作）
        try {
            if (aiConfig != null && (aiConfig.isValid() || hasRequestedModel)) {
                logger.info("使用前端AI配置触发异步核心设定提炼: outlineId={}", outlineId);
            } else {
                logger.info("前端未提供有效AI配置，改用系统AI配置异步提炼核心设定: outlineId={}", outlineId);
            }
            coreSettingsExtractionService.extractCoreSettingsAsync(outlineId, aiConfig);
        } catch (Exception e) {
            logger.error("触发核心设定提炼任务失败（不影响主流程）: {}", e.getMessage(), e);
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
     * 更新大纲内容（手动编辑）
     */
    public NovelOutline updateOutlineContent(Long novelId, String outlineContent) {
        Optional<NovelOutline> existingOutline = findByNovelId(novelId);
        
        NovelOutline outline;
        if (existingOutline.isPresent()) {
            // 更新现有大纲
            outline = existingOutline.get();
            outline.setPlotStructure(outlineContent);
            outlineRepository.updateById(outline);
        } else {
            // 创建新大纲
            outline = new NovelOutline();
            outline.setNovelId(novelId);
            outline.setPlotStructure(outlineContent);
            outline.setStatus(NovelOutline.OutlineStatus.DRAFT);
            outlineRepository.insert(outline);
        }
        
        return outline;
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
     * 从模板构建大纲生成提示词
     */
    private String buildOutlinePromptFromTemplate(String templateContent, Novel novel, String basicIdea, Integer targetChapters, Integer targetWords) {
        return buildOutlinePromptFromTemplate(templateContent, novel, basicIdea, targetChapters, targetWords, 2000);
    }

    /**
     * 从模板构建大纲生成提示词（带字数限制）
     */
    private String buildOutlinePromptFromTemplate(String templateContent, Novel novel, String basicIdea, Integer targetChapters, Integer targetWords, Integer outlineWordLimit) {
        int tc = targetChapters == null ? 0 : targetChapters;
        int tw = targetWords == null ? 0 : targetWords;
        int wordLimit = outlineWordLimit == null ? 2000 : outlineWordLimit;

        // 优先使用 basicIdea；为空则回退到 novel.description
        String idea = (basicIdea != null && !basicIdea.trim().isEmpty()) ? basicIdea : novel.getDescription();

        // 日志输出
        logger.info("📝 使用模板构建大纲生成提示词:");
        logger.info("  - 用户构思长度: {}", idea != null ? idea.length() : 0);
        logger.info("  - 用户构思内容: {}", idea);
        if ((basicIdea == null || basicIdea.trim().isEmpty()) && novel.getDescription() != null) {
            logger.info("  - 提示: basicIdea 为空，已使用 novel.description 作为构思输入");
        }

        if (templateContent != null) {
            Map<String, Object> variables = buildOutlineTemplateVariables(novel, idea, tc, tw, wordLimit);
        String renderedPrompt = renderOutlineTemplate(
                templateContent,
                variables,
                novel.getTitle() != null ? novel.getTitle() : "",
                tc,
                tw,
                idea != null ? idea : "",
                wordLimit
        );
        if (renderedPrompt != null && !renderedPrompt.trim().isEmpty()) {
            logger.info("=".repeat(100));
            logger.info("📤 使用模板生成的完整提示词:");
            logger.info(renderedPrompt);
            logger.info("=".repeat(100));
            return renderedPrompt;
        }

        logger.warn("⚠️ 自定义大纲模板占位符异常，自动切回默认全书大纲模板");
        return buildSuperOutlinePromptCompat(novel, basicIdea, targetChapters, targetWords);

        }

        if (!matchesOutlinePromptSignature(templateContent)) {
            logger.warn("⚠️ 自定义大纲模板占位符异常，自动切回默认全书大纲模板");
            return buildSuperOutlinePromptCompat(novel, basicIdea, targetChapters, targetWords);
        }

        String prompt;
        try {
            prompt = String.format(
                templateContent,
                novel.getTitle() != null ? novel.getTitle() : "",
                tc,
                tw,
                idea != null ? idea : ""
            );
        } catch (Exception e) {
            logger.warn("⚠️ 自定义大纲模板格式化失败，自动切回默认全书大纲模板: {}", e.getMessage());
            return buildSuperOutlinePromptCompat(novel, basicIdea, targetChapters, targetWords);
        }

        // 输出完整的提示词，检查构思是否被正确填充
        logger.info("=".repeat(100));
        logger.info("📤 使用模板生成的完整提示词:");
        logger.info(prompt);
        logger.info("=".repeat(100));

        return prompt;
    }

    /**
     * 构建网文专用大纲生成提示词（基于专业指导重构）
     */
    private String buildOutlineGenerationPrompt(NovelOutline outline, Novel novel) {
        return buildAdvancedOutlinePrompt(novel, outline.getBasicIdea(), outline.getTargetChapterCount(), outline.getTargetWordCount());
    }
    
    private String buildAdvancedOutlinePrompt(Novel novel, String originalIdea, Integer targetChapters, Integer targetWords) {
        int tc = targetChapters == null ? 0 : targetChapters;
        int tw = targetWords == null ? 0 : targetWords;

        // 优先使用 originalIdea；若为空则回退到 novel.description
        String idea = (originalIdea != null && !originalIdea.trim().isEmpty())
                ? originalIdea
                : novel.getDescription();

        // 转义用户输入中的 % 字符，避免 String.format 错误
        String escapedOriginalIdea = idea != null ? idea.replace("%", "%%") : "";

        // 添加日志输出，检查用户构思是否正确传递
        logger.info("📝 构建大纲生成提示词:");
        logger.info("  - 小说标题: {}", novel.getTitle());
        logger.info("  - 小说类型: 自动识别/未指定");
        logger.info("  - 目标章数: {}", tc);
        logger.info("  - 目标字数: {}", tw);
        logger.info("  - 用户构思长度: {}", idea != null ? idea.length() : 0);
        logger.info("  - 用户构思内容: {}", idea);
        if ((originalIdea == null || originalIdea.trim().isEmpty()) && novel.getDescription() != null) {
            logger.info("  - 提示: originalIdea 为空，已使用 novel.description 作为构思输入");
        }

        SystemPromptDescriptor descriptor = SystemPromptDescriptor.builder()
                .promptKey("novel.outline.advanced")
                .name("高级全书大纲生成提示词")
                .category("system_chain")
                .description("根据用户构思生成可拆卷的全书大纲与世界观蓝图")
                .variables(java.util.List.of("title", "target_chapters", "target_words", "basic_idea"))
                .sortOrder(110)
                .defaultContent(DEFAULT_ADVANCED_OUTLINE_PROMPT)
                .build();

        String prompt = renderManagedOutlinePrompt(descriptor, novel, idea, tc, tw);

        // 输出完整的提示词，检查构思是否被正确填充
        logger.info("=".repeat(100));
        logger.info("📤 完整的大纲生成提示词:");
        logger.info(prompt);
        logger.info("=".repeat(100));

        return prompt;
    }

    private String renderManagedOutlinePrompt(SystemPromptDescriptor descriptor,
                                              Novel novel,
                                              String idea,
                                              int targetChapters,
                                              int targetWords) {
        Map<String, Object> variables = buildOutlineTemplateVariables(novel, idea, targetChapters, targetWords, 2000);
        String prompt = renderOutlineTemplate(
                managedPromptService.getContent(descriptor),
                variables,
                novel.getTitle() != null ? novel.getTitle() : "",
                targetChapters,
                targetWords,
                idea != null ? idea : "",
                2000
        );
        if (prompt == null || prompt.trim().isEmpty()) {
            logger.warn("Managed outline template rendered empty, fallback to default template: promptKey={}", descriptor.getPromptKey());
            return renderOutlineTemplate(
                    descriptor.getDefaultContent(),
                    variables,
                    novel.getTitle() != null ? novel.getTitle() : "",
                    targetChapters,
                    targetWords,
                    idea != null ? idea : "",
                    2000
            );
        }
        return prompt;
    }

    private Map<String, Object> buildOutlineTemplateVariables(Novel novel,
                                                              String idea,
                                                              int targetChapters,
                                                              int targetWords,
                                                              int outlineWordLimit) {
        Map<String, Object> variables = new LinkedHashMap<>();
        String title = novel.getTitle() == null ? "" : novel.getTitle();
        String description = novel.getDescription() == null ? "" : novel.getDescription();
        String safeIdea = idea == null ? "" : idea;

        variables.put("title", title);
        variables.put("novel_title", title);
        variables.put("description", description);
        variables.put("novel_description", description);
        variables.put("target_chapters", targetChapters);
        variables.put("targetChapterCount", targetChapters);
        variables.put("target_words", targetWords);
        variables.put("targetWordCount", targetWords);
        variables.put("basic_idea", safeIdea);
        variables.put("basicIdea", safeIdea);
        variables.put("idea", safeIdea);
        variables.put("user_idea", safeIdea);
        variables.put("outline_word_limit", outlineWordLimit);
        variables.put("outlineWordLimit", outlineWordLimit);
        return variables;
    }

    private String renderOutlineTemplate(String templateContent,
                                         Map<String, Object> variables,
                                         Object... legacyArgs) {
        if (templateContent == null || templateContent.trim().isEmpty()) {
            return null;
        }

        boolean hasNamedVariables = TEMPLATE_VARIABLE_PATTERN.matcher(templateContent).find();
        String rendered = hasNamedVariables
                ? applyNamedTemplateVariables(templateContent, variables)
                : templateContent;

        boolean hasLegacySpecifiers = SIMPLE_FORMAT_SPECIFIER_PATTERN.matcher(rendered).find();
        if (hasLegacySpecifiers) {
            rendered = applyLegacyOutlineFormatTemplate(rendered, legacyArgs);
        }

        if (!hasNamedVariables && !hasLegacySpecifiers) {
            rendered = appendOutlineTemplateContext(rendered, variables);
        }

        return rendered;
    }

    private String applyNamedTemplateVariables(String template, Map<String, Object> variables) {
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String applyLegacyOutlineFormatTemplate(String template, Object... args) {
        Matcher matcher = SIMPLE_FORMAT_SPECIFIER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        int sequentialIndex = 0;

        while (matcher.find()) {
            String explicitIndex = matcher.group(1);
            String conversion = matcher.group(2);
            int argIndex = explicitIndex != null && !explicitIndex.isEmpty()
                    ? Integer.parseInt(explicitIndex) - 1
                    : sequentialIndex++;
            Object value = argIndex >= 0 && argIndex < args.length ? args[argIndex] : null;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(formatLegacyOutlineValue(value, conversion)));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String formatLegacyOutlineValue(Object value, String conversion) {
        if ("d".equals(conversion)) {
            if (value instanceof Number) {
                return String.valueOf(((Number) value).intValue());
            }
            if (value == null) {
                return "0";
            }
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String appendOutlineTemplateContext(String template, Map<String, Object> variables) {
        StringBuilder builder = new StringBuilder(template.trim());
        builder.append("\n\n");
        builder.append("### Input\n");
        builder.append("- title: ").append(variables.getOrDefault("novel_title", "")).append('\n');
        builder.append("- targetChapters: ").append(variables.getOrDefault("target_chapters", 0)).append('\n');
        builder.append("- targetWords: ").append(variables.getOrDefault("target_words", 0)).append('\n');
        builder.append("- outlineWordLimit: ").append(variables.getOrDefault("outline_word_limit", 2000)).append('\n');
        builder.append("- basicIdea:\n").append(variables.getOrDefault("basic_idea", ""));
        return builder.toString();
    }

    private String formatAdvancedOutlinePrompt(SystemPromptDescriptor descriptor, Object... args) {
        String template = managedPromptService.getContent(descriptor);
        if (!matchesOutlinePromptSignature(template)) {
            logger.warn("⚠️ 数据库中的全书大纲模板占位符异常，自动切回默认模板: promptKey={}", descriptor.getPromptKey());
            template = descriptor.getDefaultContent();
        }
        try {
            return String.format(template, args);
        } catch (Exception e) {
            logger.warn("⚠️ 全书大纲模板格式化失败，自动切回默认模板: promptKey={}, error={}",
                    descriptor.getPromptKey(), e.getMessage());
            return String.format(descriptor.getDefaultContent(), args);
        }
    }

    private boolean matchesOutlinePromptSignature(String template) {
        if (template == null || template.trim().isEmpty()) {
            return false;
        }
        List<String> specifiers = new ArrayList<>();
        Matcher matcher = SIMPLE_FORMAT_SPECIFIER_PATTERN.matcher(template);
        while (matcher.find()) {
            specifiers.add(matcher.group());
        }
        return OUTLINE_PROMPT_FORMAT_SIGNATURE.equals(specifiers);
    }
    
    /**
     * 构建大纲修订提示词
     */
    private String buildOutlineRevisionPrompt(NovelOutline outline, String userFeedback) {
        return renderOutlinePrompt(
                "novel.outline.revision",
                "小说大纲修订提示词",
                "根据用户反馈修订当前小说大纲，保持既有结构并重写核心主题、角色、情节结构和世界设定",
                120,
                List.of("core_theme", "main_characters", "plot_structure", "world_setting", "user_feedback"),
                """
                        请根据用户反馈修改以下小说大纲：

                        当前大纲内容：
                        核心主题：{{core_theme}}
                        主要角色：{{main_characters}}
                        情节结构：{{plot_structure}}
                        世界设定：{{world_setting}}

                        用户反馈：{{user_feedback}}

                        请根据反馈重新生成修改后的大纲内容，保持原有格式。
                        """,
                Map.of(
                        "core_theme", outline.getCoreTheme() != null ? outline.getCoreTheme() : "未设定",
                        "main_characters", outline.getMainCharacters() != null ? outline.getMainCharacters() : "未设定",
                        "plot_structure", outline.getPlotStructure() != null ? outline.getPlotStructure() : "未设定",
                        "world_setting", outline.getWorldSetting() != null ? outline.getWorldSetting() : "未设定",
                        "user_feedback", userFeedback == null ? "" : userFeedback
                )
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

    private String buildOutlineOptimizationPrompt(Novel novel, String currentOutline, String suggestion) {
        return renderOutlinePrompt(
                "novel.outline.optimization",
                "小说大纲优化提示词",
                "根据用户建议优化当前小说大纲，保持核心框架不变并直接输出优化后的完整大纲",
                121,
                List.of("novel_title", "novel_description", "current_outline", "suggestion"),
                """
                        你是一位资深网文编辑，现在需要根据用户的建议优化小说大纲。

                        **小说信息：**
                        - 标题：{{novel_title}}
                        {{novel_description_block}}
                        **当前大纲：**
                        {{current_outline}}

                        **优化建议：**
                        {{suggestion}}

                        **优化要求：**
                        1. 在保持原有大纲核心框架的基础上，根据用户建议进行改进
                        2. 确保优化后的大纲逻辑连贯、情节合理
                        3. 保持大纲的结构清晰，层次分明
                        4. 直接输出优化后的完整大纲，不要添加"根据建议"等元话语

                        请直接输出优化后的大纲：
                        """,
                Map.of(
                        "novel_title", novel.getTitle() == null ? "" : novel.getTitle(),
                        "novel_description_block",
                        novel.getDescription() != null && !novel.getDescription().isEmpty()
                                ? "- 简介：" + novel.getDescription() + "\n"
                                : "",
                        "current_outline", currentOutline == null ? "" : currentOutline,
                        "suggestion", suggestion == null ? "" : suggestion
                )
        );
    }

    /**
     * AI优化大纲（流式）
     */
    public void optimizeOutlineStream(Long novelId, String currentOutline, String suggestion, com.novel.dto.AIConfigRequest aiConfig, java.util.function.Consumer<String> chunkConsumer) {
        
        try {
            // 获取小说信息
            Novel novel = novelRepository.selectById(novelId);
            if (novel == null) {
                throw new RuntimeException("小说不存在");
            }
            String prompt = buildOutlineOptimizationPrompt(novel, currentOutline, suggestion);
            
            // 使用流式生成
            StringBuilder accumulated = new StringBuilder();
            aiWritingService.streamGenerateContent(prompt, "outline_optimization", aiConfig, chunk -> {
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
            String prompt = buildOutlineOptimizationPrompt(novel, currentOutline, suggestion);
            
            // 调用AI生成优化后的大纲
            String optimizedOutline = aiWritingService.generateContent(prompt, "outline_optimization");
            
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
     * 构建世界观生成的AI提示词
     */
    private String buildWorldViewGenerationPrompt(Novel novel, String outlineContent) {
        String safeOutline = outlineContent == null ? "" : outlineContent;
        if (safeOutline.length() > 2000) {
            safeOutline = safeOutline.substring(0, 2000) + "...";
        }
        return renderOutlinePrompt(
                "novel.world_view.generation",
                "小说世界观生成提示词",
                "根据小说简介和当前大纲生成世界观 JSON，包含世界背景、力量体系、势力、特殊设定和文化习俗",
                122,
                List.of("novel_title", "novel_description_block", "outline_content"),
                """
                        根据以下小说信息和大纲，生成详细的世界观设定。

                        小说基本信息：
                        - 标题：{{novel_title}}
                        {{novel_description_block}}
                        小说大纲：
                        {{outline_content}}

                        请生成以下世界观设定，并以JSON格式返回：
                        1. 世界背景：时代背景、地理环境、社会结构
                        2. 力量体系：修炼/能力/科技等体系的等级和规则
                        3. 主要势力：重要的组织、门派、国家等
                        4. 特殊设定：独特的世界观元素（法宝、异兽、禁地等）
                        5. 文化习俗：社会规则、传统习俗、禁忌等

                        返回格式要求（纯JSON，无其他说明）：
                        {
                          "worldBackground": "世界背景描述",
                          "powerSystem": {
                            "name": "体系名称",
                            "levels": ["等级1", "等级2", "..."],
                            "description": "体系说明"
                          },
                          "majorForces": [
                            {"name": "势力名", "type": "类型", "description": "描述"}
                          ],
                          "specialSettings": [
                            {"name": "设定名", "category": "分类", "description": "描述"}
                          ],
                          "culture": "文化习俗描述"
                        }
                        """,
                Map.of(
                        "novel_title", novel.getTitle() == null ? "" : novel.getTitle(),
                        "novel_description_block", novel.getDescription() == null ? "" : "- 简介：" + novel.getDescription() + "\n",
                        "outline_content", safeOutline
                )
        );
    }

    private String renderOutlinePrompt(String promptKey,
                                       String name,
                                       String description,
                                       int sortOrder,
                                       List<String> variableNames,
                                       String template,
                                       Map<String, ?> variables) {
        return managedPromptService.render(SystemPromptDescriptor.builder()
                        .promptKey(promptKey)
                        .name(name)
                        .category("outline")
                        .description(description)
                        .variables(variableNames)
                        .sortOrder(sortOrder)
                        .defaultContent(template)
                        .build(),
                variables);
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
