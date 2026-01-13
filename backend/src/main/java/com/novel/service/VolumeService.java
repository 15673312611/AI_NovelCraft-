package com.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelVolume;
import com.novel.domain.entity.NovelVolume.VolumeStatus;
import com.novel.domain.entity.NovelOutline;
import com.novel.dto.AIConfigRequest;
import com.novel.repository.NovelRepository;
import com.novel.repository.NovelOutlineRepository;
import com.novel.mapper.NovelVolumeMapper;
import com.novel.common.security.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Arrays;

/**
 * 小说卷管理服务
 * 基于卷的创作系统核心服务
 */
@Service
@Transactional
public class VolumeService {

    private static final Logger logger = LoggerFactory.getLogger(VolumeService.class);
    
    // 并发控制：记录正在生成卷蓝图的卷ID（改为按卷控制，支持批量生成）
    private final Set<Long> generatingVolumes = Collections.synchronizedSet(new HashSet<>());
    
    /**
     * 清理卷的生成标记
     */
    public void clearGeneratingFlag(Long volumeId) {
        if (volumeId != null) {
            generatingVolumes.remove(volumeId);
            logger.info("🔓 已清理卷 {} 的生成标记", volumeId);
        }
    }

    @Autowired
    private NovelVolumeMapper volumeMapper;

    @Autowired
    private NovelCraftAIService aiService;

    @Autowired
    private NovelService novelService;


    
    @Autowired
    @Lazy
    private AsyncAIGenerationService asyncAIGenerationService;
    


    @Autowired
    private AITaskService aiTaskService;

    @Autowired
    private NovelRepository novelRepository;

    @Autowired
    private NovelOutlineRepository outlineRepository;
    
    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private LongNovelMemoryManager longNovelMemoryManager;
    
    @Autowired
    private com.novel.repository.VolumeChapterOutlineRepository volumeChapterOutlineRepository;
    
    @Autowired
    private com.novel.repository.ForeshadowLifecycleLogRepository foreshadowLifecycleLogRepository;

    /**
     * 异步生成卷大纲（防止超时）
     * 
     * @param volumeId 卷ID
     * @param userAdvice 用户建议
     * @param aiConfig AI配置
     * @return 包含任务ID的结果
     */
    public Map<String, Object> generateVolumeOutlineAsync(Long volumeId, String userAdvice, AIConfigRequest aiConfig) {
        logger.info("📋 为卷 {} 创建异步大纲生成任务", volumeId);
        
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在");
        }
        
        // 并发控制：检查该卷是否正在生成卷蓝图
        if (generatingVolumes.contains(volumeId)) {
            logger.warn("⚠️ 卷 {} 正在生成卷蓝图，请勿重复请求", volumeId);
            throw new RuntimeException("该卷正在生成卷蓝图，请等待当前任务完成");
        }
        
        // 标记为正在生成
        generatingVolumes.add(volumeId);
        
        try {
            // 创建异步AI任务
            com.novel.domain.entity.AITask aiTask = new com.novel.domain.entity.AITask();
            aiTask.setName("生成第" + volume.getVolumeNumber() + "卷详细大纲");
            aiTask.setType(com.novel.domain.entity.AITask.AITaskType.STORY_OUTLINE);
            aiTask.setStatus(com.novel.domain.entity.AITask.AITaskStatus.PENDING);
            aiTask.setNovelId(volume.getNovelId());
            
            // 构建任务参数（包含AI配置）
            Map<String, Object> params = new HashMap<>();
            params.put("volumeId", volumeId);
            params.put("userAdvice", userAdvice);
            params.put("operationType", "GENERATE_VOLUME_OUTLINE");
            
            // 添加AI配置到参数中
            if (aiConfig != null && aiConfig.isValid()) {
                Map<String, String> aiConfigMap = new HashMap<>();
                aiConfigMap.put("provider", aiConfig.getProvider());
                aiConfigMap.put("apiKey", aiConfig.getApiKey());
                aiConfigMap.put("model", aiConfig.getModel());
                aiConfigMap.put("baseUrl", aiConfig.getBaseUrl());
                params.put("aiConfig", aiConfigMap);
            }
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            aiTask.setParameters(mapper.writeValueAsString(params));
            
            // 设置任务参数
            aiTask.setMaxRetries(3);
            aiTask.setEstimatedCompletion(LocalDateTime.now().plusMinutes(5));
            
            // 使用异步AI生成服务提交任务
            Long taskId = asyncAIGenerationService.submitVolumeOutlineTask(aiTask, volumeId, userAdvice);
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("volumeId", volumeId);
            result.put("status", "PENDING");
            result.put("message", "卷大纲异步生成任务已创建");
            
            logger.info("✅ 卷 {} 异步大纲生成任务创建成功，任务ID: {}", volumeId, taskId);
            return result;
            
        } catch (Exception e) {
            logger.error("❌ 创建卷 {} 异步大纲生成任务失败", volumeId, e);
            throw new RuntimeException("创建异步任务失败: " + e.getMessage());
        }
    }

    /**
     * 流式生成卷详细大纲（SSE）
     * 
     * @param volumeId 卷ID
     * @param userAdvice 用户建议
     * @param chunkConsumer 接收生成内容的消费者
     * 注意：使用@Transactional(propagation = Propagation.NOT_SUPPORTED)禁用事务，因为流式处理是渐进式的
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void streamGenerateVolumeOutline(Long volumeId, String userAdvice, com.novel.dto.AIConfigRequest aiConfig, java.util.function.Consumer<String> chunkConsumer) {
        logger.info("📋 [流式] 开始为卷 {} 生成卷蓝图", volumeId);
        
        // 验证AI配置
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new RuntimeException("AI配置无效，请先在设置页面配置AI服务");
        }
        
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }
        
        Novel novel = novelService.getNovelById(volume.getNovelId());
        if (novel == null) {
            throw new RuntimeException("小说不存在: " + volume.getNovelId());
        }
        
        // 获取超级大纲
        NovelOutline superOutline = outlineRepository.findByNovelIdAndStatus(
                novel.getId(), 
                NovelOutline.OutlineStatus.CONFIRMED
        ).orElse(null);
        
        if (superOutline == null || superOutline.getPlotStructure() == null || superOutline.getPlotStructure().isEmpty()) {
            throw new RuntimeException("小说尚未生成或确认超级大纲，无法生成卷蓝图");
        }
        
        try {
            // 构建提示词
            StringBuilder prompt = new StringBuilder();
            prompt
                  .append("你是顶级网文总编，专门设计\"让读者欲罢不能\"的卷蓝图。你的任务是给出【方向型】的卷规划：只管大方向、阶段结构和里程碑，不抢着把故事细节写完，也不平均分配精彩度，而是为整部作品设计有高有低的节奏波浪。\n\n")
                  .append("# 核心理念\n")
                  .append("**蓝图不是剧本**：只给路线图和资源包，不写执行细节。让AI写作时有发挥空间，能根据实际情况灵活调整。\n")
                  .append("**禁止写成剧情简介或片段**：不要逐段复述故事内容，不要详细描述具体场景过程，更不要出现人物对白。若写出的文字看起来像某一卷的小说节选，而不是主编给作者讲这一卷\"大概要怎么写\"的抽象规划，就说明跑题了。\n")
                  .append("**冲突驱动一切**：围绕\"主角想要什么→遇到什么阻碍→付出什么代价→得到什么结果\"来规划每一阶段，不追求匀速推进，而是通过拉扯和迟到的兑现来制造上瘾感。\n")
                  .append("**卷间节奏波浪**：不同卷可以承担不同节奏角色（攀升卷/高峰卷/缓冲卷/翻盘卷等），不需要平均分配爆点；重要情节可以有意跨卷铺垫和兑现，只要整体遵守超级大纲。\n\n")
                  .append("# 小说信息\n")
                  .append("**标题**：").append(novel.getTitle()).append("\n");
            if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
                prompt.append("**构思**：").append(novel.getDescription()).append("\n");
            }
            prompt.append("**全书大纲**：\n").append(superOutline.getPlotStructure()).append("\n\n")
                  .append("# 本卷信息\n")
                  .append("**卷名**：").append(volume.getTitle()).append("\n")
                  .append("**主题**：").append(volume.getTheme()).append("\n")
                  .append("**简述**：").append(
                      (volume.getContentOutline() != null && !volume.getContentOutline().isEmpty())
                          ? volume.getContentOutline()
                          : (volume.getTheme() != null ? volume.getTheme() : "")
                  ).append("\n");
            if (volume.getChapterStart() != null && volume.getChapterEnd() != null) {
                prompt.append("**章节范围**：第 ").append(volume.getChapterStart()).append("-").append(volume.getChapterEnd()).append(" 章\n");
            }
            if (volume.getEstimatedWordCount() != null && volume.getEstimatedWordCount() > 0) {
                prompt.append("**目标字数**：").append(volume.getEstimatedWordCount()).append(" 字\n");
            }
            if (userAdvice != null && !userAdvice.trim().isEmpty()) {
                prompt.append("**作者补充**：").append(userAdvice.trim()).append("\n");
            }
            prompt.append("\n【对齐约束】\n")
                  .append("- 严格承接超级大纲与本卷信息，保留其中的核心冲突、角色定位、关键线索与设定，不得擅自重置或弱化。\n")
                  .append("- 新增情节需解释其如何放大原有主题与冲突张力，确保因果链自洽。\n")
                  .append("- 若超级大纲或卷简述已有具体事件/目标，须延续并深化，保持人物动机连续；允许将某些重大事件拆分为跨卷完成的多个阶段（铺垫→爆发→余波），而不是强行在一卷内全部做完。\n\n")
                  .append("【卷内逻辑自洽】\n")
                  .append("- 明确本卷起点状态（角色实力/地位/关系/线索/外部局势），关键事件须具备“触发→动作→结果”的因果链，不允许无因果跳跃或“天降资源”。\n")
                  .append("- 角色动机、能力边界与世界规则前后一致；若有突破，必须给出铺垫与代价。\n")
                  .append("- 本卷内不得出现自相矛盾的设定或前后冲突；反派不降智，行动与其资源和信息边界匹配。\n")
                  .append("- 首尾呼应：开卷起点→关键转折→卷末状态闭环，并自然抛出下一卷钩子；如为第一卷，从初始设定起；否则默认承接上一卷卷末状态。\n\n")
                  .append("【读者体验目标】\n")
                  .append("- 以整部作品为单位规划期待—兑现节奏：本卷可以是持续拉升、高压爆发或短暂缓冲，但必须在全书曲线中占据清晰位置；不追求每卷、每阶段都平均好看，而是有重点地堆砌记忆点。\n")
                  .append("- 在本卷内部设计明显的情绪起伏：紧张与放松相间、危机与逆转呼应，让读者在关键节点产生“停不下来”的冲动，尤其是本卷后半程和卷末附近。\n")
                  .append("- 针对目标读者喜好突出市场卖点（成长、情感、爽感、悬念等），打造让人想追更的阅读体验。\n\n")
                  .append("# 输出要求\n\n")
                  .append("- 用编辑视角写作：像在跟作者讨论这一卷的大致写法，而不是在向读者讲故事。\n")
                  .append("- 全文保持在适中篇幅，更偏向抽象总结和结构规划，而不是展开具体桥段经过。\n")
                  .append("- 禁止出现引号中的人物台词（如“他冷笑着说：……”），也不要写出完整的时间线式剧情复盘。\n\n")
                  
                  .append("## 一、本卷核心定位\n")
                  .append("用2-3句话说清楚：这一卷要解决什么问题？主角要达成什么目标？读者能爽到什么？\n\n")
                  
                  .append("## 二、主角成长轨迹\n")
                  .append("**起点状态**：本卷开始时，主角的实力/地位/资源/心态是什么样？\n")
                  .append("**终点状态**：本卷结束时，主角会成长到什么程度？必须根据全书大纲设定来确定，保持一致性。\n")
                  .append("**成长路径**：大致分几个阶段？每个阶段有什么标志性突破？\n\n")
                  
                  .append("## 三、核心冲突与对手\n")
                  .append("**主要对手**：谁在跟主角作对？他们的目标是什么？实力如何？\n")
                  .append("**冲突升级路线**：矛盾怎么一步步激化？从小摩擦到大爆发的节奏是什么？\n")
                  .append("**压力来源**：除了对手，还有什么在逼主角？（时间限制、资源短缺、规则限制等）\n")
                  .append("**代价边界**：主角为了达成目标，最多能付出什么代价？什么是绝对不能失去的？\n\n")
                  
                  .append("## 四、爽点体系设计\n")
                  .append("**基础爽点**：分布在正常推进过程中的“小爆点”类型与触发条件，列出3-5个典型场景方向，不需要严格平均间隔，但要避免长时间完全无爽点。\n")
                  .append("**进阶爽点**：本卷中等强度爆发的事件类型与大致出现阶段（如前期/中期/后期），列出2-3个关键节点方向，而不是按固定章节间隔平均分布。\n")
                  .append("**高潮爽点**：本卷的巅峰时刻，可以集中在卷末，也可以在本卷中后段先爆一波，再把更大的后果或揭露压到下一卷；描述1-2个巅峰设计思路，并说明与前后卷的衔接。\n\n")
                  
                  .append("## 五、开放事件池（≥8个）\n")
                  .append("提供一些\"可选事件包\"，每个事件包括：\n")
                  .append("- **事件名**：简短标题\n")
                  .append("- **触发条件**：什么情况下可以用这个事件？\n")
                  .append("- **核心矛盾**：这个事件的主要冲突是什么？\n")
                  .append("- **可能结果**：成功/失败/意外，各会导向什么？\n")
                  .append("- **爽点类型**：这个事件能给读者什么爽感？（打脸/逆袭/获得/成长/揭秘等）\n\n")
                  .append("**注意**：这些事件不规定顺序，AI写作时可以根据剧情需要灵活选用和组合。\n\n")
                  
                  .append("## 六、关键里程碑（3-5个）\n")
                  .append("本卷必须经过的几个关键节点，其中一部分可以在本卷内完成闭环，另一部分可以只做到“半步”，把最狠的后果或揭露留到下一卷；每个包括：\n")
                  .append("- **里程碑名称**：这个节点叫什么？\n")
                  .append("- **达成条件**：什么情况下算达成？\n")
                  .append("- **影响范围**：达成后会改变什么？（主角能力、势力格局、剧情走向等），并标明主要影响是落在本卷，还是会强烈延续到后续卷。\n")
                  .append("- **悬念钩子**：这个节点会引出什么新问题或新目标，是否为后续一整卷提供主线动力？\n\n")
                  
                  .append("## 七、支线与节奏调节\n")
                  .append("**情感线**：本卷有哪些角色关系会发展？（友情/爱情/师徒/仇恨等）大致走向是什么？\n")
                  .append("**探索线**：有什么谜团需要揭开？分几步揭示？\n")
                  .append("**日常调节**：在紧张剧情之间，可以插入什么轻松场景来调节节奏？\n\n")
                  
                  .append("## 八、伏笔管理\n")
                  .append("**本卷要埋的伏笔**：为后续卷做铺垫，列出2-3个关键伏笔及其埋藏方式。\n")
                  .append("**本卷要收的伏笔**：前面埋下的哪些坑要在本卷填？怎么填才爽？\n")
                  .append("**本卷要提的伏笔**：之前埋的伏笔，在本卷要不要提一下加深印象？\n\n")
                  
                  .append("## 九、卷末状态与钩子\n")
                  .append("**主角最终状态**：本卷结束时，主角的实力/地位/资源/心态达到什么程度？\n")
                  .append("**已解决问题**：本卷打算真正解决哪些矛盾？哪些只是阶段性缓和或“看似解决”？请避免把所有重要问题一次性做完。\n")
                  .append("**新增悬念**：卷末要留出足够有力的钩子，让读者强烈想看下一卷，可以是新危机/新目标/新谜团，但不能只是轻描淡写的小尾巴。\n")
                  .append("**风险结转**：明确指出有哪些隐患或代价会延续到下一卷甚至更后面的卷，形成跨卷的持续压力与期待。\n\n")
                  
                  .append("# 写作风格要求\n")
                  .append("1. **人话表达**：别用术语和套话，就像老编辑跟作者聊天一样自然\n")
                  .append("2. **具体可操作**：描述要具体明确，基于全书大纲的设定，不要编造大纲中不存在的内容\n")
                  .append("3. **留白适度**：给出方向和资源，但不锁死具体过程，让AI有发挥空间\n")
                  .append("4. **冲突为王**：每个部分都要体现\"想要什么→遇到什么阻碍→付出什么代价\"\n")
                  .append("5. **爽点密集**：确保读者每隔几章就能爽一次，不能让剧情平淡\n\n")
                  
                  .append("# 禁止事项\n")
                  .append("❌ 不要写具体对话和场景细节\n")
                  .append("❌ 不要规定具体章节编号和顺序\n")
                  .append("❌ 不要用JSON或代码块格式\n")
                  .append("❌ 不要写成流水账式的事件列表\n")
                  .append("❌ 不要锁死剧情发展路径\n\n")
                  .append("只输出上述九个部分的正文内容，不要额外添加与卷蓝图无关的话语。\n\n")
                  
                  .append("现在，基于以上信息和要求，生成一份让读者\"欲罢不能\"的卷蓝图，用自然中文分段叙述，禁止附加解释或总结。\n");

            logger.info("📝 [流式] 调用AI生成卷蓝图，提示词长度: {}", prompt.length());
            
            // 使用真正的流式AI调用
            StringBuilder accumulated = new StringBuilder();
            
            aiWritingService.streamGenerateContent(prompt.toString(), "volume_outline_generation", aiConfig, chunk -> {
                try {
                    // 累加内容
                    accumulated.append(chunk);
                    
                    // 实时更新数据库
                    volume.setContentOutline(accumulated.toString());
                    volume.setUpdatedAt(LocalDateTime.now());
                    volumeMapper.updateById(volume);
                    
                    // 回调给SSE消费者
                    if (chunkConsumer != null) {
                        chunkConsumer.accept(chunk);
                    }
                } catch (Exception e) {
                    logger.error("处理流式内容块失败: {}", e.getMessage(), e);
                    throw new RuntimeException("处理流式内容块失败: " + e.getMessage());
                }
            });
            
            logger.info("✅ [流式] 卷 {} 蓝图生成并保存成功，总长度: {}", volumeId, accumulated.length());
            
        } catch (Exception e) {
            logger.error("❌ [流式] 生成卷 {} 蓝图失败", volumeId, e);
            throw new RuntimeException("流式生成卷蓝图失败: " + e.getMessage(), e);
        }
    }


//    /**
//     * 开始卷写作会话
//     *
//     * 注意：前端已不再使用 memoryBank，所有上下文数据由后端在写作时直接从数据库查询
//     *
//     * @param volumeId 卷ID
//     * @return 写作会话数据（包含volume/novel/aiGuidance等，不再包含memoryBank）
//     */
//    public Map<String, Object> startVolumeWriting(Long volumeId) {
//        logger.info("✍️ 开始卷 {} 的写作会话", volumeId);
//
//        NovelVolume volume = volumeMapper.selectById(volumeId);
//        if (volume == null) {
//            throw new RuntimeException("卷不存在");
//        }
//
//        // 检查是否有大纲，没有则提示先生成大纲
//        if (volume.getContentOutline() == null || volume.getContentOutline().trim().isEmpty()) {
//            throw new RuntimeException("缺少卷大纲，请先生成卷大纲后再开始写作");
//        }
//
//        // 更新卷状态为进行中
//        volume.setStatus(VolumeStatus.IN_PROGRESS);
//        volumeMapper.updateById(volume);
//
//        Novel novel = novelService.getById(volume.getNovelId());
//
//        // 创建写作会话（不再包含 memoryBank，前端也不再使用）
//        Map<String, Object> writingSession = new HashMap<>();
//        writingSession.put("volumeId", volumeId);
//        writingSession.put("volume", volume);
//        writingSession.put("novel", novel);
//        writingSession.put("currentPosition", 0);
//        writingSession.put("sessionStartTime", LocalDateTime.now());
//
//        // 生成初始AI指导
//        Map<String, Object> initialGuidance = generateWritingGuidance(novel, volume, null, "开始写作");
//        writingSession.put("aiGuidance", initialGuidance);
//
//        logger.info("✅ 卷 {} 写作会话创建成功", volumeId);
//        return writingSession;
//    }

    /**
     * 获取卷详情
     */
    public Map<String, Object> getVolumeDetail(Long volumeId) {
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在: " + volumeId);
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("volume", volume);
        return detail;
    }

//    /**
//     * 生成下一步写作指导
//     *
//     * @param volumeId 卷ID
//     * @param currentContent 当前内容
//     * @param userInput 用户输入
//     * @return AI指导建议
//     */
//    public Map<String, Object> generateNextStepGuidance(Long volumeId, String currentContent, String userInput) {
//        logger.info("💡 为卷 {} 生成下一步指导", volumeId);
//
//        NovelVolume volume = volumeMapper.selectById(volumeId);
//        if (volume == null) {
//            throw new RuntimeException("卷不存在");
//        }
//
//        Novel novel = novelService.getById(volume.getNovelId());
//
//        return generateWritingGuidance(novel, volume, currentContent, userInput);
//    }

    /**
     * 获取小说的所有卷
     * 
     * @param novelId 小说ID
     * @return 卷列表
     */
    public List<NovelVolume> getVolumesByNovelId(Long novelId) {
        return volumeMapper.selectByNovelId(novelId);
    }

    /**
     * 更新卷的实际字数
     * 
     * @param volumeId 卷ID
     * @param actualWordCount 实际字数
     */
    public void updateActualWordCount(Long volumeId, Integer actualWordCount) {
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume != null) {
            volume.setActualWordCount(actualWordCount);
            
            // 如果达到预期字数，标记为完成
            if (actualWordCount >= volume.getEstimatedWordCount()) {
                volume.setStatus(VolumeStatus.COMPLETED);
            }
            
            volumeMapper.updateById(volume);
        }
    }

    /**
     * 删除卷
     * 
     * @param volumeId 卷ID
     */
    public void deleteVolume(Long volumeId) {
        // 仅允许所属小说的作者删除该卷
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法删除卷");
        }

        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            return;
        }

        Novel novel = novelRepository.selectById(volume.getNovelId());
        if (novel == null || novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限删除该卷");
        }

        volumeMapper.deleteById(volumeId);
    }

    // ================================
    // 私有辅助方法
    // ================================
    


    
    /**
     * 基于传统大纲生成卷规划
     */
    private List<Map<String, Object>> generateVolumePlansFromOutline(Novel novel, 
        com.novel.domain.entity.NovelOutline outline, Integer volumeCount) {
        return generateVolumePlansFromOutline(novel, outline, volumeCount, null);
    }
    
    /**
     * 基于传统大纲生成卷规划（带AI配置）
     * 策略：一次性生成所有卷，使用优化后的提示词
     * - 加入题材识别，根据题材自动调整节奏和看点
     * - 强化第一卷快速引入核心看点，避免拖沓
     * - 内容密度控制，确保每卷能支撑20-40章
     * - 融合七猫编辑技巧（节奏控制、冲突设计、期待感营造）
     */
    private List<Map<String, Object>> generateVolumePlansFromOutline(Novel novel, 
        com.novel.domain.entity.NovelOutline outline, Integer volumeCount, com.novel.dto.AIConfigRequest aiConfig) {
        
        logger.info("📝 正在为小说 '{}' 生成 {} 个卷的规划（一次性生成策略）...", novel.getTitle(), volumeCount);
        
        // 直接使用优化后的一次性生成逻辑
        return generateVolumePlansFromOutlineOldWay(novel, outline, volumeCount, aiConfig);
    }
    
    /**
     * 一次性生成所有卷的核心逻辑（V4版本 - 连贯性优先）
     * 核心理念：卷是节奏控制单元，不是目标切换单元
     * 优化点：
     * 1. 全书只有一个主线目标，不为每卷设置独立目标
     * 2. 卷与卷之间一环扣一环，无缝衔接
     * 3. contentOutline详细（800-1200字），给章纲足够发挥空间
     * 4. 重点写"过程"和"细节"，而不是"目标"和"结果"
     */
    private List<Map<String, Object>> generateVolumePlansFromOutlineOldWay(Novel novel, 
        com.novel.domain.entity.NovelOutline outline, Integer volumeCount, com.novel.dto.AIConfigRequest aiConfig) {
        
        logger.info("📝 使用V4连贯性优先逻辑为小说 '{}' 生成 {} 个卷的规划...", novel.getTitle(), volumeCount);

        String outlineContent = (outline.getPlotStructure() != null && !outline.getPlotStructure().trim().isEmpty())
            ? outline.getPlotStructure()
            : (outline.getBasicIdea() == null ? "" : outline.getBasicIdea());

        // 转义大纲内容中的 % 字符
        String escapedOutlineContent = outlineContent.replace("%", "%%");

        // 使用优化后的提示词框架：强制执行准则 + 三步处理流程
        String prompt = String.format(
            "=== 1. 全局执行准则（铁律层） ===\n\n" +
                    "【强制执行：分卷设计核心原则】\n\n" +
                    "在处理任何分卷规划前，必须强制执行以下铁律：\n\n" +
                    "1. **连贯性铁律**：分卷不是每个卷对应一个新问题，某个事件或内容可以贯穿多卷\n" +
                    "2. **并行性铁律**：每卷可以收多个并行线索、多个概念目标，不要单线程推进\n" +
                    "3. **悬念铁律**：每卷必须留足悬念，不要把所有问题在本卷内解决完\n" +
                    "4. **节奏铁律**：卷是节奏控制单元，不是目标切换单元，不要割裂剧情\n" +
                    "5. **详细度铁律**：contentOutline要详细（500-1000字），给章纲足够发挥空间\n\n" +
                    "【内容安全约束】\n\n" +
                    "- 绝对围绕用户大纲展开，只做合理拆分与优化，不随意改题\n" +
                    "- 不要添加大纲中没有的组织、势力、复杂设定\n" +
                    "- 主线清晰度优先于一切技巧\n\n" +
                    "=== 2. 角色定位与核心使命 ===\n\n" +
                    "【身份烙印】\n\n" +
                    "你是资深网文分卷架构师+题材适配专家+悬念设计大师，一个在网文界拥有超过十年经验的传奇鬼手。你精通：\n\n" +
                    "- 读者心理学：精准把控不同题材读者的期待和爽点节奏\n" +
                    "- 题材识别学：能快速判断大纲属于哪类题材，核心看点是什么\n" +
                    "- 多线编织术：让多个事件线、感情线、伏笔线并行推进，互相交织\n" +
                    "- 悬念控制术：在每卷中埋设钩子，让读者欲罢不能\n" +
                    "- 节奏波浪控制：通过分卷控制张弛有度的节奏\n" +
                    "- 冲突公式化设计：用数学般的精确构建情感冲击和矛盾升级\n\n" +
                    "【核心使命】\n\n" +
                    "接收用户提供的【小说标题】、【全书大纲】、【总卷数】，你的任务是：\n\n" +
                    "1. 深度识别题材类型和核心看点\n" +
                    "2. 设计逻辑自洽、主线清晰、多线并行的分卷规划\n" +
                    "3. 确保事件可以贯穿多卷，不要每卷一个独立问题\n" +
                    "4. 为每卷设计多个并行目标和概念，增加丰富度\n" +
                    "5. 在每卷结尾留足悬念，引导读者继续追读\n" +
                    "6. 确保每卷能支撑30-60章的内容\n\n" +
                    "【最高准则】\n\n" +
                    "- 某个事件或内容可以贯穿多卷，不要强行在一卷内解决\n" +
                    "- 每卷可以有多个并行线索：主线+感情线+伏笔线+成长线\n" +
                    "- 悬念优先于完结，让读者带着疑问进入下一卷\n" +
                    "- 不同题材有不同的看点方向，不要一刀切\n\n" +
                    "=== 3. 输入接口与三步处理流程 ===\n\n" +
                    "【必需输入】\n" +
                    "- 小说标题：《%s》\n" +
                    "- 全书大纲：\n%s\n\n" +
                    "- 规划总卷数：%s\n\n" +
                    "【三步处理流程】\n\n" +
                    "Step 1：题材深度识别与分析（必须先完成这一步）\n\n" +
                    "在生成任何分卷前，你必须先对用户大纲进行深度分析：\n\n" +
                    "1. 题材类型定位（明确标注）\n" +
                    "   - 大类：男频（玄幻/仙侠/都市/历史/科幻）还是女频（豪门/重生/玄学/宫斗/种田/甜宠）\n" +
                    "   - 细分：种田文/宅斗/宫斗/修仙/商战/娱乐圈/打脸爽文/复仇文等\n" +
                    "   - 爽点类型：打脸爽/复仇爽/发家致富爽/感情线爽/权谋爽/成长爽\n" +
                    "   - 节奏风格：快节奏紧凑型 vs 慢热发育型 vs 张弛有度型\n" +
                    "   - 情感基调：轻松愉快 vs 虐心虐身 vs 先虐后甜 vs 全程甜宠\n\n" +
                    "2. 多线结构识别（重要！如果大纲有明确的多线架构）\n" +
                    "   识别大纲中是否有以下线路结构：\n" +
                    "   - 【主线】：故事的核心主线（如复仇线、事业崛起线、修炼升级线等）\n" +
                    "   - 【辅线】：支撑主线的次要线索（如事业线、复仇线、家族线等，可能有多条）\n" +
                    "   - 【感情线】：男女主或重要人物的情感发展线\n" +
                    "   - 【暗线】：隐藏的线索，将在后期揭晓（如幕后黑手、身世秘密等）\n" +
                    "   - 【事件线】：关键事件节点列表（如果大纲有明确标注）\n" +
                    "   \n" +
                    "   注意：\n" +
                    "   * 如果大纲明确标注了【多线叙事架构】或类似章节，必须仔细识别每条线路\n" +
                    "   * 记录每条线路的起始点、关键节点、完结点\n" +
                    "   * 记录线路之间的关联关系（哪些线路会交织产生冲突或看点）\n\n" +
                    "3. 核心看点提取（列出3-5个关键看点）\n" +
                    "   - 从用户大纲中提取最吸引人的情节点和关系线\n" +
                    "   - 识别大纲中已经设定好的重要剧情节点和人物关系\n" +
                    "   - 明确哪些是必须保留和展开的核心内容\n" +
                    "   - 重要：不同题材的看点方向完全不同！\n" +
                    "     * 种田文的看点：如何利用金手指发展、从贫穷到富裕的过程、与男主的感情线\n" +
                    "     * 爽文的看点：打脸场面、身份曝光、实力展示、复仇成功\n" +
                    "     * 感情文的看点：心动瞬间、暧昧互动、男主宠溺、确认关系\n" +
                    "     * 权谋文的看点：智斗博弈、真相揭露、权力争夺\n\n" +
                    "4. 关键事件点识别与分配（如果大纲有事件线）\n" +
                    "   - 如果大纲中有明确的【事件线】或【关键事件列表】（如事件1、事件2...）\n" +
                    "   - 必须将这些事件点合理分配到各个卷中\n" +
                    "   - 每个卷应该包含2-5个关键事件点（根据卷的章节数调整）\n" +
                    "   - 在contentOutline中必须明确体现这些事件点\n" +
                    "   - 事件点的分配要符合叙事节奏（前期密集引入冲突，中期稳步推进，后期高潮迭起）\n\n" +
                    "5. 主线提炼（用一句话概括）\n" +
                    "   - 这个故事的核心主线是什么？主角的终极目标是什么？\n" +
                    "   - 故事的主要矛盾和冲突来源是什么？\n" +
                    "   - 主线类型：废材逆袭/发家致富/爱恨情仇/权力争夺/复仇雪恨等\n\n" +
                    "6. 目标受众分析（明确读者期待）\n" +
                    "   - 这是什么类型的读者会喜欢的故事？\n" +
                    "   - 读者最想看到什么？\n" +
                    "     * 种田文读者：想看慢慢发育的成长过程、感情线的甜蜜互动、生活越来越好\n" +
                    "     * 爽文读者：想看打脸复仇的爽快、主角碾压对手、身份曝光的震撼\n" +
                    "     * 感情文读者：想看情感细腻描写、心动瞬间、男主宠溺、甜蜜日常\n" +
                    "   - 读者不想看到什么？\n" +
                    "     * 种田文读者：不想看过度紧张的生死危机、复杂的玄学体系\n" +
                    "     * 爽文读者：不想看主角一直被动挨打、拖沓的铺垫\n\n" +
                    "7. 第一卷看点规划（最重要！）\n" +
                    "   - 第一卷必须在前3-5章铺开核心看点，不同题材有不同要求：\n" +
                    "     * 种田文：前3-5章必须展示金手指、开始发展，不能一直铺垫背景\n" +
                    "     * 爽文：前3-5章必须有第一次冲突或打脸，不能拖到第10章\n" +
                    "     * 感情文：前3-5章男女主必须相遇并产生互动，不能迟迟不见面\n" +
                    "   - 第一卷要让读者看到他们想看的内容，而不是大量铺垫\n\n" +
                    "重要提醒：\n" +
                    "- 你的分析必须完全基于用户提供的大纲内容，不要自己臆想\n" +
                    "- 如果大纲是简单的种田文，就不要强行加入复杂的玄学组织、门派斗争\n" +
                    "- 如果大纲强调慢慢发育和感情线，就不要把每一卷都设计成高强度对抗和生死危机\n" +
                    "- 分卷的风格、节奏、看点方向必须与大纲的类型和受众期待完全匹配\n\n" +
                    "Step 2：基于分析结果规划所有卷（重点：多线并行+贯穿多卷+留足悬念）\n\n" +
                    "现在，基于你对大纲的深度分析，开始规划所有卷。你必须确保：\n\n" +
                    "【多线并行设计】\n" +
                    "- 每一卷不要只有单一主线，要设计多个并行线索：\n" +
                    "  * 主线事件（核心剧情推进）\n" +
                    "  * 感情线（人物关系发展）\n" +
                    "  * 伏笔线（为后续卷埋设钩子）\n" +
                    "  * 成长线（主角能力/心态的变化）\n" +
                    "  * 副线事件（丰富世界观和人物群像）\n\n" +
                    "【贯穿多卷设计】\n" +
                    "- 某个重要事件或矛盾可以贯穿2-3卷，不要强行在一卷内解决\n" +
                    "- 例如：一个强大的敌人可以在第2卷出现，第3卷对抗，第4卷才最终解决\n" +
                    "- 例如：一段感情线可以从第1卷暧昧，第2卷心动，第3卷确认关系\n" +
                    "- 例如：一个谜团可以在第1卷埋下，第2-3卷逐步揭开，第4卷真相大白\n\n" +
                    "【悬念钩子设计】\n" +
                    "- 每一卷结尾必须留足悬念，不要把所有问题都解决完\n" +
                    "- 悬念类型：\n" +
                    "  * 危机悬念：新的威胁出现、更强的敌人登场\n" +
                    "  * 真相悬念：部分真相揭露，但更大的谜团浮现\n" +
                    "  * 关系悬念：感情线出现变化、误会产生、第三者介入\n" +
                    "  * 选择悬念：主角面临重大抉择，下卷才揭晓结果\n\n" +
                    "【风格一致性】\n" +
                    "- 每一卷的风格、节奏、看点与大纲的类型完全一致\n" +
                    "- 每一卷的内容严格遵循大纲中已有的设定和剧情走向\n" +
                    "- 不要添加大纲中没有的组织、势力、复杂设定\n" +
                    "- 看点的设计要符合目标受众的期待（不同类型的看点方向不同）\n\n" +
                    "Step 3：质量评估与自检（生成后必须检查）\n\n" +
                    "生成后必须进行以下检查：\n" +
                    "1. 是否有事件贯穿多卷？（不要每卷都是独立故事）\n" +
                    "2. 是否有多线并行？（不要只有单一主线）\n" +
                    "3. 是否每卷都留足悬念？（不要把问题都解决完）\n" +
                    "4. 是否符合五维评估标准？（见后文详细标准）\n\n" +
                    "=== 4. 分卷规划核心引擎 ===\n\n" +
                    "【第一步：题材识别与分析（内部思考，不输出）】\n\n" +
                    "在生成分卷前，必须先判断：\n" +
                    "1. 本书属于哪类题材？（玄幻/仙侠/都市/女频豪门/重生爽文/玄学爽文/权谋宫斗/事业爽/古言种田/现言甜宠等）\n" +
                    "2. 这个题材的核心爽点公式是什么？\n" +
                    "   - 打脸流：被轻视 → 隐忍 → 爆发 → 震惊 → 后悔\n" +
                    "   - 逆袭流：劣势 → 步步紧逼 → 隐藏底牌 → 关键爆发 → 敌败\n" +
                    "   - 复仇流：仇恨铺垫 → 隐忍积累 → 逐个击破 → 复仇成功 → 新目标\n" +
                    "   - 甜宠流：误会 → 接触 → 心动 → 追求 → 确认 → 甜蜜日常\n" +
                    "   - 成长流：弱小 → 努力 → 挫折 → 突破 → 强大 → 新挑战\n" +
                    "   - 种田流：初始状态 → 目标设定 → 逐步发展 → 阶段成果 → 新目标\n" +
                    "3. 这个题材的读者最期待什么情绪价值？（爽/甜/虐/成就感/满足感/期待感）\n" +
                    "4. 这个题材的合理节奏是什么？（快节奏爽文每卷20-30章 vs 慢热种田文每卷30-40章）\n" +
                    "5. 代入感检查：\n" +
                    "   - 主角设定是否有普遍性和成长性？（不要太完美，要让读者觉得【这可能是我】）\n" +
                    "   - 主角动机是否合理？（生存/复仇/理想/情感/正义）\n" +
                    "   - 是否主要跟随主角视角？（避免频繁跳戏）\n" +
                    "   - 每一卷是否让主角有成长和突破？\n" +
                    "6. 大纲完整性检查：\n" +
                    "   - 人物性格是否鲜明？（这是最重要的设定）\n" +
                    "   - 每一卷的人物、地点、事件是否清晰？\n" +
                    "   - 是否围绕主线推进，避免偏离？\n\n" +
                    "【第二步：内容密度控制（核心原则）】\n\n" +
                    "重要：每卷的contentOutline必须能支撑20-40章的内容，禁止内容过于密集导致只能写几章！\n\n" +
                    "总卷数：%s\n" +
                    "平均每卷章节数：约20-40章\n\n" +
                    "内容密度要求：\n" +
                    "1. 如果一卷预计20-30章，contentOutline中的重大情节点不能超过6-10个\n" +
                    "2. 如果一卷预计30-40章，contentOutline中的重大情节点不能超过8-12个\n" +
                    "3. 每个重大情节点应该能展开成2-4章的内容\n" +
                    "4. 要留出足够的铺垫、过渡、缓冲、日常、互动、成长的展示空间\n" +
                    "5. 不要把太多重大事件堆在一卷，避免内容过于密集\n\n" +
                    "【第三步：第一卷特殊要求（解决推进慢、拖沓、核心看点不出现的问题）】\n\n" +
                    "第一卷是全书的生死线，必须快速引入核心看点，避免拖沓！\n\n" +
                    "第一卷必须做到：\n" +
                    "1. 开篇要快（前3-5章必须引爆第一个冲突或危机\n" +
                    "   - 快节奏爽文：开篇直接冲突，不超过3章引入核心矛盾\n" +
                    "   - 慢热种田文：开篇展示金手指或核心优势，不超过5章开始发展\n" +
                    "   - 感情线为主：开篇男女主相遇，不超过5章产生互动\n\n" +
                    "2. 核心看点前置（第一卷必须包含大纲中最吸引人的看点\n" +
                    "   - 如果大纲有打脸情节，第一卷必须有至少1-2次打脸\n" +
                    "   - 如果大纲有身份曝光，第一卷可以部分曝光或暗示\n" +
                    "   - 如果大纲有复仇，第一卷必须开始复仇或展示复仇动机\n" +
                    "   - 如果大纲有感情线，第一卷必须让男女主产生好感或心动\n\n" +
                    "3. 避免拖沓（禁止大量铺垫而没有实质进展\n" +
                    "   - 禁止前10章都在铺垫背景、世界观、人物关系而没有冲突\n" +
                    "   - 禁止主角一直被动挨打而不反击\n" +
                    "   - 禁止核心矛盾一直不出现\n" +
                    "   - 必须在第一卷内完成至少一个阶段性目标或小胜利\n\n" +
                    "4. 主线推进度要求\n" +
                    "   - 第一卷主线推进度：30-50%%（根据题材调整）\n" +
                    "   - 快节奏爽文：40-50%%（快速推进，引爆看点）\n" +
                    "   - 慢热种田文：30-40%%（稳步发展，建立基础）\n" +
                    "   - 感情线为主：30-40%%（关系建立，情感升温）\n\n" +
                    "5. 第一卷章节数控制\n" +
                    "   - 快节奏爽文：20-30章（紧凑，高潮迭起）\n" +
                    "   - 慢热种田文：30-40章（从容，展示过程）\n" +
                    "   - 感情线为主：25-35章（细腻，情感描写）\n\n" +
                    "【第四步：分卷节奏控制（融合七猫编辑技巧）】\n\n" +
                    "不同卷要有不同的节奏强度，形成波浪式起伏：\n\n" +
                    "1. 第一卷（建立期\n" +
                    "   - 节奏：中等偏快（根据题材调整）\n" +
                    "   - 任务：快速引入核心看点、世界观展示、角色登场、核心矛盾确立\n" +
                    "   - 主线推进度：30-50%%\n" +
                    "   - 章节数：20-40章（根据题材调整）\n" +
                    "   - 内容密度：中等（既要信息量足够，又要有展示空间）\n\n" +
                    "2. 中间卷（发展期\n" +
                    "   - 节奏：有快有慢，形成起伏\n" +
                    "   - 任务：推进主线、深化关系、埋设伏笔\n" +
                    "   - 主线推进度：每卷5-25%%（有的卷快，有的卷慢）\n" +
                    "   - 章节数：20-40章\n" +
                    "   - 内容密度：根据卷的定位调整（铺垫卷密度低，爆发卷密度高）\n\n" +
                    "3. 最后卷（高潮期\n" +
                    "   - 节奏：快速紧凑\n" +
                    "   - 任务：终极对决、真相揭晓、所有伏笔回收\n" +
                    "   - 主线推进度：剩余所有进度\n" +
                    "   - 章节数：15-30章\n" +
                    "   - 内容密度：较高（但仍要留出展示空间）\n\n" +
                    "【第五步：冲突设计（避免所有卷都是生死危机）】\n\n" +
                    "不同卷要有不同的冲突强度：\n" +
                    "- 第一卷：中等冲突（建立矛盾，快速引入看点，不能太弱也不能太强）\n" +
                    "- 中间卷：有强有弱（铺垫卷冲突弱，爆发卷冲突强）\n" +
                    "- 最后卷：最强冲突（终极对决）\n\n" +
                    "冲突设计避坑：\n" +
                    "1. 不要所有卷都是生死危机（会审美疲劳）\n" +
                    "2. 不要冲突太弱，情节平淡（会失去读者）\n" +
                    "3. 不要为冲突而冲突（要有合理原因）\n" +
                    "4. 不要第一卷推进太慢、太拖沓、核心看点不出现\n\n" +
                    "【第六步：contentOutline 撰写细则（重中之重）】\n\n" +
                    "每一卷的 contentOutline 必须遵循以下要求：\n\n" +
                    "长度：约500-1000字（要详细，给章纲足够发挥空间）\n" +
                    "格式：单段落字符串，无换行\n\n" +
                    "结构（六部分）：\n\n" +
                    "1. 开篇概括句（第一句话，约80-120字）\n" +
                    "   用一句故事化的话概括本卷的核心内容：\n" +
                    "   - 主角本卷的主要目标或面临的主要情况\n" +
                    "   - 本卷最大的看点（根据类型调整）\n" +
                    "   - 本卷的结局走向和留下的悬念\n" +
                    "   要用贴合本书风格的语言，不要机械套用公式。\n\n" +
                    "2. 多线并行描述（约250-400字）【核心部分】\n" +
                    "   必须体现多线并行，不要只写单一主线：\n" +
                    "   \n" +
                    "   【主线事件】：本卷核心剧情如何推进（100-150字）\n" +
                    "   - 种田文：如何发展、遇到什么小困难、如何解决\n" +
                    "   - 爽文：如何对抗敌人、有什么策略和转折、如何打脸获胜\n" +
                    "   - 感情文：两人如何互动、有什么心动瞬间\n" +
                    "   \n" +
                    "   【并行线索】：同时进行的其他线索（150-250字）\n" +
                    "   - 辅线1（如有）：如事业线、复仇线等，本卷如何推进（50-80字）\n" +
                    "   - 辅线2（如有）：如家族线、权谋线等，本卷如何推进（50-80字）\n" +
                    "   - 感情线：人物关系如何发展（暧昧/心动/误会/和解），本卷达到感情线的哪个阶段（50-80字）\n" +
                    "   - 暗线/伏笔线：为后续卷埋设什么钩子（神秘人物/隐藏真相/未解之谜），或暗线在本卷的进展（30-50字）\n" +
                    "   - 成长线：主角能力/心态有什么变化（30-50字）\n" +
                    "   \n" +
                    "   重要：描述要具体、有画面感，不要把太多事件堆在一起！\n" +
                    "   如果大纲中明确了多线架构，必须在这里标注每条线路的推进情况！\n\n" +
                    "3. 关键事件点呈现（约100-150字）【新增】\n" +
                    "   如果大纲中有明确的事件线列表，必须在这里列出本卷包含的关键事件点：\n" +
                    "   - 本卷包含哪2-5个关键事件（用大纲中的事件名称或编号）\n" +
                    "   - 这些事件如何串联起来形成本卷的叙事\n" +
                    "   - 事件之间的因果关系和冲突升级\n" +
                    "   - 例如：'本卷包含事件3（婚后协议签订）、事件4（初次安抚男主）、事件5（回门宴打脸）三个关键节点。从契约确立到初步展现价值，再到首次公开反击，形成完整的开局三连击。'\n" +
                    "   \n" +
                    "   注意：如果大纲没有明确事件线，此部分可省略或合并到多线并行描述中。\n\n" +
                    "4. 贯穿多卷的事件（约60-100字）\n" +
                    "   如果有事件贯穿多卷，必须明确说明：\n" +
                    "   - 本卷是该事件的哪个阶段（开始/发展/高潮/尾声）\n" +
                    "   - 本卷推进到什么程度，下卷如何继续\n" +
                    "   - 例如：'本卷是与XX敌人对抗的第一阶段，主角初步了解其实力，但尚未正面交锋，真正的决战将在下一卷展开'\n\n" +
                    "5. 高潮场面（约80-120字）\n" +
                    "   描述本卷最精彩的部分（线路交织产生的看点）：\n" +
                    "   - 种田文：重要的发展成果、与男主关系的重要进展\n" +
                    "   - 爽文：打脸场面、实力展示、阶段性胜利\n" +
                    "   - 感情文：表白、确认关系、重要的亲密互动\n" +
                    "   - 多线交织：例如在复仇成功的同时，感情线也有重大突破；在事业危机时，男主出手相助推动感情升温\n\n" +
                    "6. 收尾与悬念钩子（约80-120字）\n" +
                    "   - 阶段性成果：本卷达成了什么目标（但不要全部解决）\n" +
                    "   - 各线路状态：主线、各条辅线、感情线在本卷结束时的状态\n" +
                    "   - 悬念钩子：必须留足悬念，引导读者追读下一卷\n" +
                    "     * 危机悬念：新的威胁出现、更强的敌人登场\n" +
                    "     * 真相悬念：部分真相揭露，但更大的谜团浮现\n" +
                    "     * 关系悬念：感情线出现变化、误会产生、第三者介入\n" +
                    "     * 选择悬念：主角面临重大抉择，下卷才揭晓结果\n" +
                    "   - 推进度标注：本卷主线推进度约X%%，感情线推进度约Y%%（如适用）\n\n" +
                    "【第七步：质量自检（生成后必须检查）】\n\n" +
                    "生成每一卷后，必须自检以下十项：\n\n" +
                    "1. 【多线并行检查】每一卷是否体现了多线并行？\n" +
                    "   - 是否只有单一主线？（不合格）\n" +
                    "   - 是否有主线+感情线+伏笔线等多条线索？（合格）\n" +
                    "   - 如果大纲明确了辅线（如事业线、复仇线、家族线），每卷是否都有体现？（必须）\n\n" +
                    "2. 【线路推进检查】各条线路的推进是否清晰？【新增】\n" +
                    "   - 主线在本卷推进了多少？（必须明确百分比）\n" +
                    "   - 各条辅线在本卷的状态是什么？（起始/发展/高潮/完结）\n" +
                    "   - 感情线在本卷达到了哪个阶段？（如：初遇/靠近/拉开/推拉/高潮/低谷/和解）\n" +
                    "   - 暗线在本卷是埋伏笔还是揭晓？\n\n" +
                    "3. 【事件点分配检查】如果大纲有明确的事件线，检查事件点分配【新增】\n" +
                    "   - 大纲中的所有关键事件是否都已分配到各卷？\n" +
                    "   - 每卷包含的事件数量是否合理？（2-5个）\n" +
                    "   - 事件点的分配是否符合叙事节奏？（前期密集、中期稳步、后期高潮）\n" +
                    "   - contentOutline中是否明确体现了这些事件点？\n\n" +
                    "4. 【线路交织检查】各线路是否有交织产生看点？【新增】\n" +
                    "   - 是否有'在解决主线问题时推动感情线发展'的设计？\n" +
                    "   - 是否有'辅线冲突引发主线转折'的设计？\n" +
                    "   - 是否有'暗线浮出水面影响主线走向'的设计？\n" +
                    "   - 避免各线路孤立推进，要让它们互相影响、互相促进\n\n" +
                    "5. 【贯穿多卷检查】是否有事件贯穿多卷？\n" +
                    "   - 每卷都是独立故事，互不关联？（不合格）\n" +
                    "   - 有重要事件/矛盾跨越2-3卷逐步展开？（合格）\n\n" +
                    "6. 【悬念钩子检查】每一卷结尾是否留足悬念？\n" +
                    "   - 所有问题都在本卷内解决完？（不合格）\n" +
                    "   - 留下危机/真相/关系/选择等悬念？（合格）\n\n" +
                    "7. 【第一卷质量检查】第一卷是否快速引入核心看点？\n" +
                    "   - 前3-5章是否有冲突或吸引力？\n" +
                    "   - 是否避免了拖沓和大量铺垫？\n\n" +
                    "8. 【内容密度检查】每一卷的内容是否能支撑预期章节数？\n" +
                    "   - 是否能支撑20-40章？\n" +
                    "   - 是否避免了把太多重大事件堆在一卷？\n\n" +
                    "9. 【节奏起伏检查】是否有节奏起伏？\n" +
                    "   - 所有卷都一样？（不合格）\n" +
                    "   - 有快有慢，形成波浪？（合格）\n\n" +
                    "10. 【题材匹配检查】是否符合题材特点？\n" +
                    "   - 看点方向是否匹配题材？\n" +
                    "   - 节奏风格是否符合受众期待？\n" +
                    "   - 是否达到500-1000字且给章纲足够发挥空间？\n\n" +
                    "如果内容过于密集（只能写10章以下），必须：\n" +
                    "- 删减部分次要情节\n" +
                    "- 将部分内容移到下一卷\n" +
                    "- 增加铺垫和过渡的描述\n\n" +
                    "=== 5. 质量评估体系（生成后自检） ===\n\n" +
                    "【七维评估标准】\n\n" +
                    "生成所有卷后，必须进行七维评估，确保质量合格：\n\n" +
                    "维度一：主线清晰度（1-5分）\n" +
                    "- 5分：每一卷的主线目标非常清晰，读者一眼就知道这卷要讲什么\n" +
                    "- 3分：主线基本清晰，但有少量模糊或偏离\n" +
                    "- 1分：主线不清晰，读者不知道这卷要讲什么\n\n" +
                    "维度二：多线并行度（1-5分）【新增】\n" +
                    "- 5分：每卷都有主线+感情线+伏笔线等多条线索并行，丰富立体\n" +
                    "- 3分：大部分卷有多线并行，少数卷只有单线\n" +
                    "- 1分：所有卷都只有单一主线，缺乏丰富度\n\n" +
                    "维度三：贯穿连贯性（1-5分）【新增】\n" +
                    "- 5分：有重要事件/矛盾贯穿2-3卷，卷与卷之间紧密关联\n" +
                    "- 3分：部分事件有贯穿，但大部分卷还是独立故事\n" +
                    "- 1分：每卷都是独立故事，互不关联，割裂感强\n\n" +
                    "维度四：悬念钩子强度（1-5分）【新增】\n" +
                    "- 5分：每卷结尾都留足悬念，有危机/真相/关系/选择等钩子\n" +
                    "- 3分：部分卷有悬念，但有些卷把问题都解决完了\n" +
                    "- 1分：所有卷都把问题解决完，没有悬念引导\n\n" +
                    "维度五：内容密度（1-5分）\n" +
                    "- 5分：每卷内容密度完美，都能支撑20-40章\n" +
                    "- 3分：大部分卷合理，有少量卷过密或过疏\n" +
                    "- 1分：多数卷内容密度不合理\n\n" +
                    "维度六：题材匹配度（1-5分）\n" +
                    "- 5分：完全符合题材特点，看点方向准确\n" +
                    "- 3分：基本符合，有少量偏差\n" +
                    "- 1分：不符合题材特点\n\n" +
                    "维度七：第一卷质量（1-5分）\n" +
                    "- 5分：第一卷快速铺开核心看点，避免拖沓，主线清晰\n" +
                    "- 3分：第一卷基本合格，但看点铺开稍慢\n" +
                    "- 1分：第一卷拖沓，核心看点迟迟不出现\n\n" +
                    "【评分标准】\n" +
                    "- 28-35分：优秀，可以直接使用\n" +
                    "- 21-27分：合格，需要小幅优化\n" +
                    "- 14-20分：勉强，考虑重新生成\n" +
                    "- <14分：失败，必须重新生成\n\n" +
                    "【常见问题诊断与修正】\n\n" +
                    "问题1：第一卷拖沓，核心看点不出现\n" +
                    "诊断：\n" +
                    "- 第一卷前10章都在铺垫背景、世界观\n" +
                    "- 核心看点迟迟不出现\n" +
                    "- 主角一直被动，没有主动行动\n\n" +
                    "修正方案：\n" +
                    "- 删减前期铺垫，直接切入核心看点\n" +
                    "- 将核心看点提前到前3-5章\n" +
                    "- 让主角快速行动起来\n\n" +
                    "问题2：内容过于密集，只能写几章\n" +
                    "诊断：\n" +
                    "- contentOutline中堆了太多重大事件\n" +
                    "- 没有留出铺垫、过渡、缓冲空间\n" +
                    "- 每个情节点都是高潮\n\n" +
                    "修正方案：\n" +
                    "- 删减部分次要情节\n" +
                    "- 将部分内容移到下一卷\n" +
                    "- 增加铺垫和过渡的描述\n" +
                    "- 降低部分情节的强度\n\n" +
                    "问题3：不符合题材特点\n" +
                    "诊断：\n" +
                    "- 种田文写成了生死搏斗\n" +
                    "- 感情文写成了权谋斗争\n" +
                    "- 看点方向不匹配\n\n" +
                    "修正方案：\n" +
                    "- 重新识别题材特点\n" +
                    "- 调整看点方向和爽点类型\n" +
                    "- 调整节奏和冲突强度\n\n" +
                    "问题4：缺乏多线并行，只有单一主线【新增】\n" +
                    "诊断：\n" +
                    "- 每卷只写主线事件，没有感情线、伏笔线等\n" +
                    "- 内容单薄，缺乏丰富度\n" +
                    "- 读者容易感到枯燥\n\n" +
                    "修正方案：\n" +
                    "- 为每卷增加感情线描述（人物关系如何发展）\n" +
                    "- 增加伏笔线（为后续卷埋设钩子）\n" +
                    "- 增加成长线（主角能力/心态变化）\n" +
                    "- 增加副线事件（丰富世界观）\n\n" +
                    "问题5：每卷都是独立故事，缺乏贯穿性【新增】\n" +
                    "诊断：\n" +
                    "- 每卷的问题都在本卷内解决完\n" +
                    "- 卷与卷之间割裂，像短篇合集\n" +
                    "- 缺乏长篇小说的连贯感\n\n" +
                    "修正方案：\n" +
                    "- 设计1-2个重要事件贯穿2-3卷\n" +
                    "- 例如：强大敌人在第2卷出现，第3卷对抗，第4卷解决\n" +
                    "- 例如：谜团在第1卷埋下，第2-3卷逐步揭开，第4卷真相大白\n" +
                    "- 让卷与卷之间有因果关系和延续性\n\n" +
                    "问题6：缺乏悬念钩子，每卷都收得太干净【新增】\n" +
                    "诊断：\n" +
                    "- 每卷结尾把所有问题都解决完\n" +
                    "- 没有留下引导读者追读的钩子\n" +
                    "- 读者容易在卷尾弃书\n\n" +
                    "修正方案：\n" +
                    "- 每卷结尾必须留1-2个悬念\n" +
                    "- 危机悬念：新的威胁出现\n" +
                    "- 真相悬念：部分真相揭露，更大谜团浮现\n" +
                    "- 关系悬念：感情线出现变化\n" +
                    "- 选择悬念：主角面临重大抉择\n\n" +
                    "=== 6. 输入信息与输出格式 ===\n\n" +
                    "【输入信息】\n" +
                    "小说标题：《%s》\n" +
                    "全书大纲：\n" +
                    "%s\n\n" +
                    "规划总卷数：%s\n\n" +
                    "【输出格式】\n" +
                    "直接输出一个纯净、完整的 JSON 数组，格式必须为：\n" +
                    "[\n" +
                    "  {\n" +
                    "    \"title\": \"第1卷：[关键词命名，4-8字]\",\n" +
                    "    \"theme\": \"[本卷在全书中的阶段性概括]\",\n" +
                    "    \"contentOutline\": \"[严格遵循上方细则撰写的单段字符串（500-1000字）。本卷主线推进度约X%%]\"\n" +
                    "  },\n" +
                    "  {\n" +
                    "    \"title\": \"第2卷：...\",\n" +
                    "    \"theme\": \"...\",\n" +
                    "    \"contentOutline\": \"...\"\n" +
                    "  }\n" +
                    "  // ... 共%s个卷对象\n" +
                    "]\n\n" +
                    "【输出要求（严格遵守）】\n\n" +
                    "1. 数量必须精准：JSON 数组中的对象数量必须正好是 %s\n\n" +
                    "2. contentOutline 长度要求：每个卷的 contentOutline 必须是单段落字符串，不要换行，长度约500-1000字\n\n" +
                    "3. contentOutline 内容要求（必须包含以下六部分）：\n" +
                    "   - 开篇概括句（80-120字）\n" +
                    "   - 多线并行描述（250-400字）：【核心部分】必须明确标注主线+各条辅线+感情线+暗线的推进情况\n" +
                    "     * 主线事件：本卷核心剧情如何推进（100-150字）\n" +
                    "     * 并行线索：辅线1、辅线2、感情线、暗线/伏笔线、成长线（150-250字）\n" +
                    "     * 如果大纲有明确的多线架构（如【6.5 多线叙事架构】），必须在这里体现每条线路的状态\n" +
                    "   - 关键事件点呈现（100-150字）：如果大纲有事件线列表，必须列出本卷包含的2-5个关键事件\n" +
                    "   - 贯穿多卷的事件（60-100字）：如有事件贯穿多卷，说明本卷是哪个阶段\n" +
                    "   - 高潮场面（80-120字）：描述线路交织产生的看点\n" +
                    "   - 收尾与悬念钩子（80-120字）：标注各线路状态，必须留足悬念\n\n" +
                    "4. 推进度标注：每个卷必须在 contentOutline 末尾明确标注：\n" +
                    "   - 本卷主线推进度约X%%（必须）\n" +
                    "   - 感情线推进度约Y%%（如适用）\n" +
                    "   - 各辅线状态：起始/发展中/高潮/完结（如适用）\n\n" +
                    "5. 推进度总和：所有卷的主线推进度数值总和应约等于100%%\n\n" +
                    "6. 格式要求：\n" +
                    "   - 不要输出任何解释性文字，只输出纯净的JSON数组\n" +
                    "   - 不要使用markdown代码块，不要使用```json之类标记\n\n" +
                    "【最后提醒】\n" +
                    "- 分卷不是每个卷对应一个新问题，某个事件可以贯穿多卷\n" +
                    "- 每卷可以有多个并行线索，不要单线程推进\n" +
                    "- 每卷结尾必须留足悬念，不要把所有问题都解决完\n\n" +
                    "【针对详细大纲的特殊处理】\n" +
                    "如果用户提供的大纲包含以下结构化内容，必须严格遵循：\n\n" +
                    "1. 如果大纲有【故事分段】或【第X段】标注（如第一段、第二段...第七段）：\n" +
                    "   - 这些段落对应的就是卷的规划\n" +
                    "   - 必须严格按照大纲中每段的内容来生成对应卷的contentOutline\n" +
                    "   - 保留大纲中每段的核心情节点、冲突设计、情感节奏\n" +
                    "   - 每段的阶段目标、核心矛盾、主要对手等必须体现在对应卷中\n\n" +
                    "2. 如果大纲有【多线叙事架构】标注：\n" +
                    "   - 必须识别主线、辅线、感情线、暗线的完整结构\n" +
                    "   - 每条线路的起始点、关键节点、完结点必须准确映射到各卷\n" +
                    "   - 在contentOutline中明确标注每条线路在本卷的推进状态\n" +
                    "   - 例如：辅线2(复仇线)在第5段完结，那么第5卷必须包含复仇线的高潮和结局\n\n" +
                    "3. 如果大纲有【事件线】列表（如事件1、事件2...事件16）：\n" +
                    "   - 必须将所有事件点合理分配到各卷\n" +
                    "   - 根据大纲中事件标注的段落归属来分配（如\"事件5 | 回门宴打脸 | 第2段\"应放在第2卷）\n" +
                    "   - 在contentOutline的【关键事件点呈现】部分明确列出\n\n" +
                    "4. 如果大纲有【感情线7阶段】或类似情感节奏设计：\n" +
                    "   - 必须在各卷中体现感情线的阶段推进\n" +
                    "   - 例如：初遇/第一印象→靠近→拉开→推拉循环→高潮→低谷→和解\n" +
                    "   - 在contentOutline中明确标注本卷感情线达到哪个阶段\n\n" +
                    "5. 如果大纲有【伏笔标注】：\n" +
                    "   - 必须在对应卷中埋下伏笔，并在预定卷回收\n" +
                    "   - 例如：[伏笔1：霍庭深的病症，预计在第2段深入，第6段揭晓]\n" +
                    "   - 第2卷应深入展示病症细节，第6卷应揭晓真相\n\n" +
                    "6. 如果大纲有【下段钩子】：\n" +
                    "   - 这是卷与卷之间的过渡设计，必须体现在本卷结尾的悬念钩子中\n\n" +
                    "总之：对于结构化程度高的详细大纲，你的任务是【精准执行】而非【重新创作】！\n" +
                    "不要擅自修改大纲的分段结构、线路设计、事件分配，只需将其转化为规范的JSON格式。\n\n" +
                    "开始你的创作，直接生成最终方案。",
            novel.getTitle(),           // 第580行 %s - 小说标题
            escapedOutlineContent,      // 第581行 %s - 全书大纲
            volumeCount,                // 第582行 %s - 规划总卷数
            volumeCount,                // 第657行 %s - 总卷数
            novel.getTitle(),           // 第823行 %s - 小说标题
            escapedOutlineContent,      // 第825行 %s - 全书大纲
            volumeCount,                // 第826行 %s - 规划总卷数
            volumeCount,                // 第840行 %s - 共N个卷对象
            volumeCount                 // 第843行 %s - 必须正好是N
        );

        try {
            logger.info("🤖 调用AI生成卷规划，提示词长度: {}", prompt.length());
            logger.info("📝 提示词内容（前500字符）: {}", prompt.substring(0, Math.min(500, prompt.length())));

            long startTime = System.currentTimeMillis();
            String response;
            
            // 统一使用 aiWritingService.generateContent，它会在 aiConfig 无效时从数据库查询系统配置
            response = aiWritingService.generateContent(prompt, "volume_planning", aiConfig);
            
            long endTime = System.currentTimeMillis();

            logger.info("⏱️ AI服务响应时间: {}ms", (endTime - startTime));

            if (response != null && !response.isEmpty()) {
                logger.info("📥 AI返回的原始响应长度: {}", response.length());
                logger.info("📥 AI返回的原始响应内容（完整）:\n{}", response);
                logger.info("=" .repeat(100));

                List<Map<String, Object>> result = parseVolumePlansFromAI(response, volumeCount);
                logger.info("✅ 成功解析出 {} 个卷规划", result.size());
                return result;
            } else {
                logger.error("❌ AI服务返回空响应！");
                throw new RuntimeException("AI服务返回空响应，无法生成卷规划");
            }

        } catch (Exception e) {
            logger.error("❌ 生成卷规划失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成卷规划失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成第一卷（专注于占50%剧情的第一卷）
     * 注意：此方法当前未使用，保留作为备用方案
     * 如果需要恢复分两步生成策略，可以使用此方法
     */
    @SuppressWarnings("unused")
    private Map<String, Object> generateFirstVolume(Novel novel, 
        com.novel.domain.entity.NovelOutline outline, com.novel.dto.AIConfigRequest aiConfig) {
        
        logger.info("🎯 开始生成第一卷（目标：占50%主线剧情）");
        
        String outlineContent = (outline.getPlotStructure() != null && !outline.getPlotStructure().trim().isEmpty())
            ? outline.getPlotStructure()
            : (outline.getBasicIdea() == null ? "" : outline.getBasicIdea());
        
        String basicIdea = outline.getBasicIdea();
        String escapedBasicIdea = basicIdea != null ? basicIdea.replace("%", "%%") : "";
        String escapedOutlineContent = outlineContent.replace("%", "%%");
        
        String prompt = String.format(
            "【第一步：深度分析大纲 - 必须先完成这一步再进行创作】\n\n" +
            "在开始规划分卷之前，你必须先对用户提供的大纲进行深度分析，理解其核心内涵：\n\n" +
            "1. **剧情内核分析**（用1-2句话概括）\n" +
            "   - 这个故事的核心主线是什么？主角的终极目标是什么？\n" +
            "   - 故事的主要矛盾和冲突来源是什么？\n" +
            "   - 故事想要传达的核心情感或价值观是什么？\n\n" +
            "2. **风格类型定位**（明确标注）\n" +
            "   - 题材类型：古言/现言/玄幻/都市/科幻等\n" +
            "   - 细分类型：种田文/宅斗/宫斗/修仙/商战/娱乐圈等\n" +
            "   - 爽点类型：打脸爽/复仇爽/发家致富爽/感情线爽/权谋爽等\n" +
            "   - 节奏风格：快节奏紧凑型 vs 慢热发育型 vs 张弛有度型\n" +
            "   - 情感基调：轻松愉快 vs 虐心虐身 vs 先虐后甜 vs 全程甜宠\n\n" +
            "3. **目标受众分析**（明确读者期待）\n" +
            "   - 这是什么类型的读者会喜欢的故事？\n" +
            "   - 读者最想看到什么？（例如：慢慢发育的成长过程、感情线的甜蜜互动、打脸复仇的爽快、权谋博弈的智斗）\n" +
            "   - 读者不想看到什么？（例如：过度紧张的节奏、复杂的玄学体系、过多的配角戏份）\n\n" +
            "4. **大纲核心看点提取**（列出3-5个关键看点）\n" +
            "   - 从用户大纲中提取最吸引人的情节点和关系线\n" +
            "   - 识别大纲中已经设定好的重要剧情节点和人物关系\n" +
            "   - 明确哪些是必须保留和展开的核心内容\n\n" +
            "⚠️ **重要提醒**：\n" +
            "- 你的分析必须完全基于用户提供的大纲内容，不要自己臆想或添加大纲中没有的元素\n" +
            "- 如果大纲是简单的种田文，就不要强行加入复杂的玄学组织、门派斗争等元素\n" +
            "- 如果大纲强调慢慢发育和感情线，就不要把每一卷都设计成高强度对抗和生死危机\n" +
            "- 分卷的风格、节奏、看点方向必须与大纲的类型和受众期待完全匹配\n\n" +
            "---\n\n" +
            "【第二步：基于分析结果规划第一卷】\n\n" +
            "现在，基于你对大纲的深度分析，开始规划第一卷。你必须确保：\n" +
            "- 第一卷的风格、节奏、看点与大纲的类型完全一致\n" +
            "- 第一卷的内容严格遵循大纲中已有的设定和剧情走向\n" +
            "- 不要添加大纲中没有的组织、势力、复杂设定\n" +
            "- 看点的设计要符合目标受众的期待（不同类型的看点方向不同）\n\n" +
            "核心创作原则：根据大纲类型灵活调整，而非一刀切\n\n" +
            "【任务说明】\n" +
            "你现在的任务是为一部小说策划【第一卷】的详细大纲。这是整本书的开篇卷，是决定读者是否继续追读的生死线。\n" +
            "第一卷必须承担以下关键职责：\n\n" +
            "1. **主线推进度硬性要求**：第一卷必须推进全书主线的 50%% 左右（可在45%%–55%%区间）\n" +
            "   - 这不是简单的事件堆砌，而是要完成主线目标的一半实质性进展\n" +
            "   - 必须有明确的阶段性成果或突破，让读者感受到主角确实在向终极目标迈进\n\n" +
            "2. **内容密度与信息量要求**：第一卷要完成大量关键任务\n" +
            "   - 世界观核心规则展示（根据大纲类型调整复杂度）\n" +
            "   - 主要角色群集中登场（数量根据大纲设定，不强求3-5个）\n" +
            "   - 核心矛盾确立（矛盾类型和强度要符合大纲风格）\n" +
            "   - 主角能力/资源/地位的初始状态与第一次质变\n\n" +
            "3. **节奏要求**：根据大纲类型灵活调整\n" +
            "   - 如果是快节奏爽文：开篇3-5章内必须引爆第一个冲突或危机\n" +
            "   - 如果是慢热种田文：可以用更多篇幅展示日常发展和感情培养，看点在于成长过程和关系互动\n" +
            "   - 如果是感情线为主：重点在于人物关系的建立和情感的细腻描写\n" +
            "   - 节奏服从于大纲类型，不要强行紧凑\n\n" +
            "4. **悬念与钩子设置**：根据大纲风格设计合适的钩子\n" +
            "   - 种田文：下一阶段的发展目标、新的机遇或挑战、感情线的进展\n" +
            "   - 爽文：更强的对手、更大的危机、身份曝光\n" +
            "   - 感情文：关系的变化、误会的产生、情感的升温或降温\n" +
            "   - 钩子的类型和强度要符合大纲的整体基调\n\n" +
            "【黄金第一句原则（开篇引爆句）】\n" +
            "第一卷大纲的第一句话必须是整卷的灵魂，是浓缩的精华，必须同时做到：\n" +
            "- **点明核心矛盾**：主角本卷要面对的最棘手、最致命的问题是什么？这个问题为什么无法回避？\n" +
            "- **揭示最大看点**：最吸引人的场面或转折是什么？（如：身份暴露、反杀强敌、惊天骗局、感情决裂、权力倾轧）\n" +
            "- **关联主线任务**：这个矛盾如何推动或阻碍主角的最终目标？为什么这是必经之路？\n" +
            "- **暗示结局走向**：预告本卷最终的收获与留下的悬念（胜利、惨胜、失败、看似解决实则埋更大雷）\n" +
            "- **情绪张力**：这句话要有强烈画面感和情绪张力，用故事化语言表达，不能是干巴巴的总结句\n\n" +
            "示例（仅供参考风格，不要照抄）：\n" +
            "- 为了在三个月内从废柴逆袭成为学院第一，林晚不得不与曾经背叛她的前未婚夫结成临时联盟，而这场豪赌的代价，是她必须在众目睽睽下揭开自己隐藏多年的真实身份，最终她虽然赢得了荣耀，却发现自己不过是某个更大阴谋中的一枚棋子。\n\n" +
            "【情节驱动原则 - 根据大纲类型灵活调整】\n\n" +
            "⚠️ 重要：不同类型的小说有不同的情节驱动方式，不要一刀切！\n\n" +
            "**如果是快节奏爽文/权谋文**：\n" +
            "- 整卷大纲由连续的困境→挣扎→升级→反转→清算构成\n" +
            "- 情节像多米诺骨牌：前因后果清晰，一个危机未平，下一层更大的阴谋已露苗头\n" +
            "- 开局困境必须真实、致命，不能轻易化解\n" +
            "- 至少2-3个让读者意外但合理的反转\n\n" +
            "**如果是种田文/发展文**：\n" +
            "- 整卷大纲由：初始状态→目标设定→逐步发展→阶段成果→新目标构成\n" +
            "- 看点在于：主角如何利用金手指/能力一步步发展壮大\n" +
            "- 矛盾可以是：资源不足、环境恶劣、小人作梗，但不需要生死危机\n" +
            "- 重点展示成长过程和收获的喜悦，而非紧张刺激的对抗\n\n" +
            "**如果是感情线为主**：\n" +
            "- 整卷大纲由：初遇→了解→心动→暧昧→确认关系→甜蜜/虐点构成\n" +
            "- 看点在于：两人如何从陌生到熟悉，情感如何一步步升温\n" +
            "- 矛盾可以是：误会、身份差距、外部阻碍，但要服务于感情线发展\n" +
            "- 重点展示人物互动和情感细节，而非外部冲突\n\n" +
            "**通用要求**：\n" +
            "- 所有关键转折必须有前文伏笔或角色动机支撑\n" +
            "- 禁止为了制造爆点强行让角色降智或性格突变\n" +
            "- 主角的成长必须符合世界规则和大纲设定，不能突然开挂\n" +
            "- 第一卷结束时要有明确的阶段性成果，同时为下一卷留下期待\n\n" +
            "【情绪价值设计 - 根据大纲类型提供不同情绪】\n\n" +
            "⚠️ 不同类型的读者期待不同的情绪体验！\n\n" +
            "**快节奏爽文/权谋文的情绪重点**：\n" +
            "- **绝境感**：主角被逼到崩溃的时刻（生理或心理）\n" +
            "- **震撼感**：认知反转、关系反转、真相反转\n" +
            "- **爽快感**：反杀/破局的痛快场面，打脸翻盘\n" +
            "- **焦灼感**：卷尾强力悬念，让读者欲罢不能\n\n" +
            "**种田文/发展文的情绪重点**：\n" +
            "- **成就感**：主角一步步发展壮大，看到明显的进步\n" +
            "- **满足感**：收获的喜悦，生活越来越好\n" +
            "- **期待感**：下一步的发展目标，新的机遇\n" +
            "- **温馨感**：家人朋友的支持，和谐的人际关系\n\n" +
            "**感情线为主的情绪重点**：\n" +
            "- **心动感**：两人互动的甜蜜细节，情感升温的瞬间\n" +
            "- **紧张感**：关系的不确定性，会不会在一起的悬念\n" +
            "- **甜蜜感**：确认关系后的恩爱日常\n" +
            "- **虐心感**（如果有）：误会、分离、外部阻碍带来的痛苦\n\n" +
            "【看点设计 - 每个类型都有看点，但方向不同】\n\n" +
            "⚠️ 看点不等于矛盾冲突！不同类型有不同的看点方向！\n\n" +
            "**种田文的看点**：\n" +
            "- 主角如何利用金手指（空间、系统、现代知识）改善生活\n" +
            "- 从一无所有到家业兴旺的发展过程\n" +
            "- 与男主/重要角色的感情线进展\n" +
            "- 打脸看不起主角的人（但不需要生死搏斗）\n" +
            "- 发现新的资源、机遇、商机\n\n" +
            "**爽文的看点**：\n" +
            "- 主角打脸反派、碾压对手的场面\n" +
            "- 身份曝光、实力展示的震撼时刻\n" +
            "- 复仇成功、扳倒敌人的爽快感\n" +
            "- 反转剧情、出人意料的发展\n\n" +
            "**感情文的看点**：\n" +
            "- 两人从陌生到熟悉的过程\n" +
            "- 心动瞬间、暧昧互动\n" +
            "- 男主的宠溺、保护、吃醋\n" +
            "- 确认关系、表白、亲密互动\n" +
            "- 克服外部阻碍，有情人终成眷属\n\n" +
            "【第一卷特殊要求 - 根据大纲类型灵活调整】\n\n" +
            "1. **严格遵循大纲内容**（最重要！）\n" +
            "   - 第一卷的所有内容必须基于用户提供的大纲\n" +
            "   - 不要添加大纲中没有的组织、势力、角色、设定\n" +
            "   - 不要改变大纲中已有的剧情走向和人物关系\n" +
            "   - 如果大纲简单，分卷也要简单；如果大纲复杂，分卷才能复杂\n\n" +
            "2. **世界观展示**：根据大纲复杂度调整\n" +
            "   - 简单种田文：只需展示基本的生活环境、金手指使用规则\n" +
            "   - 复杂玄幻文：才需要展示权力体系、修炼规则等\n" +
            "   - 在情节中自然展现，不要大段说明\n\n" +
            "3. **角色登场**：根据大纲设定的角色数量\n" +
            "   - 只让大纲中已有的角色登场\n" +
            "   - 不要为了凑数而添加大纲中没有的角色\n" +
            "   - 角色关系要符合大纲设定\n\n" +
            "4. **阶段性目标达成**：第一卷要有明确的阶段性成果\n" +
            "   - 种田文：建立起基础的家业、与男主建立初步关系\n" +
            "   - 爽文：完成第一次打脸/复仇、获得第一次实力提升\n" +
            "   - 感情文：确认关系或达到暧昧阶段\n" +
            "   - 成果要让读者感到满足，同时为下一卷留下期待\n\n" +
            "5. **多线并行**：根据大纲类型决定线索数量\n" +
            "   - 主线任务（必须有）\n" +
            "   - 情感或人际线（如果大纲有感情线）\n" +
            "   - 暗线/伏笔线（如果大纲有长线布局）\n" +
            "   - 不要强行添加大纲中没有的线索\n\n" +
            "【输入信息】\n" +
            "小说标题：《%s》\n" +
            "用户原始构思：\n%s\n\n" +
            "全书大纲：\n%s\n\n" +
            "【contentOutline 撰写细则（重中之重）】\n\n" +
            "⚠️ 重要：contentOutline的内容和风格必须完全匹配你在第一步分析出的大纲类型！\n\n" +
            "contentOutline 必须是一个高密度的单段落字符串（1000-1500字），结构如下：\n\n" +
            "1. **开篇概括句（第一句话，约80-120字）**\n" +
            "   用一句故事化的话概括本卷的核心内容，包括：\n" +
            "   - 主角本卷的主要目标或面临的主要情况\n" +
            "   - 本卷最大的看点（根据类型：种田文看发展过程，爽文看打脸场面，感情文看关系进展）\n" +
            "   - 本卷的结局走向和留下的悬念\n" +
            "   要用贴合本书风格的语言，不要机械套用公式。\n\n" +
            "2. **详细过程描述（约600-800字）**\n" +
            "   根据大纲类型，详细描述本卷的发展过程：\n" +
            "   \n" +
            "   **种田文/发展文的描述重点**：\n" +
            "   - 初始状态：主角的处境、拥有的资源和能力\n" +
            "   - 发展目标：本卷要达成什么目标（建立家业、开拓市场、改善生活等）\n" +
            "   - 发展过程：如何一步步利用金手指/能力发展壮大，遇到什么小困难如何解决\n" +
            "   - 关系进展：与男主/重要角色的感情如何发展，有什么互动和心动瞬间\n" +
            "   - 阶段成果：本卷结束时达到什么程度，生活有什么改善\n" +
            "   \n" +
            "   **爽文/权谋文的描述重点**：\n" +
            "   - 初始困境：主角面临什么问题或敌人\n" +
            "   - 对抗过程：如何与敌人斗智斗勇，有什么策略和手段\n" +
            "   - 关键转折：什么事件成为破局关键\n" +
            "   - 高潮场面：如何打脸敌人、展示实力、完成复仇\n" +
            "   - 胜利成果：获得什么收获，地位如何提升\n" +
            "   \n" +
            "   **感情文的描述重点**：\n" +
            "   - 关系起点：两人当前的关系状态\n" +
            "   - 互动过程：有什么重要的相处场景，如何增进了解\n" +
            "   - 心动瞬间：什么时刻让彼此心动，有什么甜蜜细节\n" +
            "   - 关系进展：从陌生到熟悉，从好感到心动，从暧昧到确认\n" +
            "   - 外部阻碍（如果有）：什么因素阻碍两人在一起，如何克服\n" +
            "   \n" +
            "   **通用要求**：\n" +
            "   - 描述要具体、有画面感，不要只是抽象概括\n" +
            "   - 要体现大纲中已有的设定和剧情点\n" +
            "   - 不要添加大纲中没有的元素\n" +
            "   - 要展现主角的成长和变化\n\n" +
            "3. **高潮与转折（约200-300字）**\n" +
            "   描述本卷最精彩的部分：\n" +
            "   \n" +
            "   **种田文**：某个重要的发展成果达成（开业大吉、收获丰收、打脸小人）、与男主关系的重要进展\n" +
            "   **爽文**：打脸反派的精彩场面、实力展示的震撼时刻、复仇成功的爽快感\n" +
            "   **感情文**：表白场面、确认关系、克服误会、重要的亲密互动\n" +
            "   \n" +
            "   要详细描述这个高潮场面的具体内容，让读者光看大纲就能感受到情绪。\n\n" +
            "4. **收尾与钩子（约100-150字）**\n" +
            "   \n" +
            "   **阶段性成果**：\n" +
            "   - 种田文：家业发展到什么程度、与男主关系到什么阶段\n" +
            "   - 爽文：实力提升到什么程度、地位有什么变化\n" +
            "   - 感情文：关系确认到什么程度、感情有多深\n" +
            "   \n" +
            "   **下卷钩子**（根据类型设计合适的钩子）：\n" +
            "   - 种田文：新的发展机遇、更大的目标、感情线的新进展\n" +
            "   - 爽文：更强的敌人、新的危机、身份即将曝光\n" +
            "   - 感情文：关系的新挑战、误会的产生、外部阻碍的出现\n\n" +
            "5. **结尾句（固定格式）**\n" +
            "   整段 contentOutline 的末尾，必须以固定句式收尾：本卷主线推进度约50%%\n\n" +
            "【输出格式】\n" +
            "直接输出一个纯净的 JSON 对象（不是数组），格式如下：\n" +
            "{\n" +
            "  \"title\": \"第一卷：[关键词命名，4-8字，要有冲击力和画面感]\",\n" +
            "  \"theme\": \"[本卷在全书中的阶段性概括，如：破局之战、身份觉醒、权力初尝、复仇序章]\",\n" +
            "  \"contentOutline\": \"[严格遵循上方细则撰写的单段高密度字符串（1000-1500字），包含：开篇引爆句 + 过程推演（分四个阶段） + 转折与高潮（分三个部分） + 收尾与强力钩子（分两个部分）。必须以'本卷主线推进度约50%%'结尾]\"\n" +
            "}\n\n" +
            "【重要提醒】\n" +
            "- 必须先完成第一步的大纲分析，理解大纲的类型、风格、受众期待\n" +
            "- 第一卷的所有内容必须基于大纲，不要添加大纲中没有的元素\n" +
            "- contentOutline 必须是单段落字符串，不要换行，长度1000-1500字\n" +
            "- 必须在 contentOutline 末尾明确标注：本卷主线推进度约50%%\n" +
            "- 第一卷的节奏、看点、矛盾强度要符合大纲类型（种田文不需要生死危机，爽文才需要）\n" +
            "- 只让大纲中已有的角色登场，不要为了凑数添加新角色\n" +
            "- 卷末钩子的类型要符合大纲风格（种田文用发展机遇，爽文用危机，感情文用关系变化）\n" +
            "- 要有具体的场面描述和情节细节，不能只是抽象概括\n" +
            "- 整体风格要与大纲一致，不要强行套用其他类型的模板\n\n" +
            "开始你的创作，先分析大纲，再生成第一卷的JSON对象。",
            novel.getTitle(),
            escapedBasicIdea,
            escapedOutlineContent
        );
         
        try {
            logger.info("🤖 调用AI生成第一卷，提示词长度: {}", prompt.length());
            
            long startTime = System.currentTimeMillis();
            String response;
            if (aiConfig != null && aiConfig.isValid()) {
                response = aiWritingService.generateContent(prompt, "first_volume_planning", aiConfig);
            } else {
                response = aiService.callAI("VOLUME_PLANNER", prompt);
            }
            long endTime = System.currentTimeMillis();
            
            logger.info("⏱️ AI服务响应时间: {}ms", (endTime - startTime));
            logger.info("📥 AI返回的第一卷响应长度: {}", response.length());
            
            // 解析单个卷的JSON
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                throw new RuntimeException("无法从AI响应中提取JSON内容");
            }
            
            String sanitizedJson = sanitizeJsonForParsing(jsonContent);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> firstVolume = mapper.readValue(sanitizedJson, Map.class);
            
            logger.info("✅ 第一卷解析成功: {}", firstVolume.get("title"));
            return firstVolume;
            
        } catch (Exception e) {
            logger.error("❌ 生成第一卷失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成第一卷失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 基于第一卷生成剩余卷
     * 注意：此方法当前未使用，保留作为备用方案
     * 如果需要恢复分两步生成策略，可以使用此方法
     */
    @SuppressWarnings("unused")
    private List<Map<String, Object>> generateRemainingVolumes(Novel novel, 
        com.novel.domain.entity.NovelOutline outline, Integer remainingCount, 
        Map<String, Object> firstVolume, com.novel.dto.AIConfigRequest aiConfig) {
        
        logger.info("🎯 开始生成剩余 {} 卷（基于第一卷上下文）", remainingCount);
        
        if (remainingCount <= 0) {
            return new ArrayList<>();
        }
        
        String outlineContent = (outline.getPlotStructure() != null && !outline.getPlotStructure().trim().isEmpty())
            ? outline.getPlotStructure()
            : (outline.getBasicIdea() == null ? "" : outline.getBasicIdea());
        
        String escapedOutlineContent = outlineContent.replace("%", "%%");
        
        // 提取第一卷的关键信息
        String firstVolumeTitle = (String) firstVolume.getOrDefault("title", "第一卷");
        String firstVolumeTheme = (String) firstVolume.getOrDefault("theme", "");
        String firstVolumeOutline = (String) firstVolume.getOrDefault("contentOutline", "");
        
        String escapedFirstVolumeOutline = firstVolumeOutline.replace("%", "%%");
        
        String prompt = String.format(
            "【第一步：理解大纲风格和第一卷基调 - 必须先完成这一步】\n\n" +
            "在规划后续卷之前，你必须先理解整个故事的风格和第一卷已经确立的基调：\n\n" +
            "1. **回顾原始大纲的核心特征**\n" +
            "   - 这是什么类型的故事？（种田文/爽文/感情文/权谋文等）\n" +
            "   - 故事的主要看点是什么？（慢慢发育/打脸复仇/感情线/权谋博弈）\n" +
            "   - 目标读者期待什么？（成长过程/爽快打脸/甜蜜互动/智斗）\n\n" +
            "2. **分析第一卷确立的风格基调**\n" +
            "   - 第一卷的节奏是快是慢？矛盾冲突的强度如何？\n" +
            "   - 第一卷的看点方向是什么？（发展/打脸/感情/其他）\n" +
            "   - 第一卷留下了什么悬念和伏笔？\n\n" +
            "3. **确认后续卷的延续方向**\n" +
            "   - 后续卷必须延续第一卷的风格和节奏，不要突然转变\n" +
            "   - 后续卷的看点方向要与第一卷一致\n" +
            "   - 后续卷要承接第一卷的悬念和伏笔\n\n" +
            "⚠️ **重要提醒**：\n" +
            "- 后续卷必须与第一卷风格一致，不要突然改变类型\n" +
            "- 如果第一卷是轻松的种田发展，后续卷也要保持这个基调，不要突然变成生死搏斗\n" +
            "- 如果第一卷重点是感情线，后续卷也要继续深化感情线，不要突然变成权谋斗争\n" +
            "- 严格遵循原始大纲的内容，不要添加大纲中没有的元素\n\n" +
            "---\n\n" +
            "【第二步：基于分析结果规划后续各卷】\n\n" +
            "核心创作原则：延续第一卷的风格，根据大纲类型灵活调整\n\n" +
            "【任务说明】\n" +
            "你现在的任务是为一部小说策划【第2卷到第%s卷】（共%s卷）的详细大纲。\n" +
            "第一卷已经完成并推进了50%%的主线剧情，你需要基于第一卷的内容，延续故事发展，规划后续各卷。\n\n" +
            "【第一卷信息（已完成，已推进50%%主线）】\n" +
            "标题：%s\n" +
            "主题：%s\n" +
            "第一卷大纲：\n%s\n\n" +
            "【原始全书大纲参考】\n" +
            "%s\n\n" +
            "【后续各卷的创作原则 - 根据大纲类型灵活调整】\n\n" +
            "⚠️ 重要：后续卷的风格、节奏、看点必须与第一卷和原始大纲保持一致！\n\n" +
            "**如果是种田文/发展文**：\n" +
            "- 每一卷展示一个新的发展阶段（扩大规模、开拓新业务、进入新市场）\n" +
            "- 看点在于：主角如何利用能力继续发展，生活越来越好\n" +
            "- 矛盾可以有，但不要过于激烈（小人作梗、商业竞争、环境挑战）\n" +
            "- 感情线要持续推进（从暧昧到确认，从甜蜜到更甜蜜）\n" +
            "- 每卷结束时要有明显的成果和新的发展目标\n\n" +
            "**如果是爽文/权谋文**：\n" +
            "- 每一卷面对一个新的敌人或更大的挑战\n" +
            "- 看点在于：打脸、复仇、实力展示、权谋博弈\n" +
            "- 矛盾要有层次递进（从小敌人到大BOSS）\n" +
            "- 每卷要有精彩的对抗场面和反转\n" +
            "- 每卷结束时要有阶段性胜利，但也要埋下新的危机\n\n" +
            "**如果是感情文**：\n" +
            "- 每一卷推进感情线的一个新阶段（暧昧→表白→确认→深化→面对挑战）\n" +
            "- 看点在于：两人的互动、甜蜜场面、情感升温\n" +
            "- 矛盾主要是感情线的（误会、外部阻碍、身份差距）\n" +
            "- 每卷要有让读者心动的场面和细节\n" +
            "- 每卷结束时感情要有新的进展\n\n" +
            "**通用要求**：\n" +
            "- 所有转折必须有前文伏笔或合理动机，不要强行制造冲突\n" +
            "- 不要为了制造爆点让角色降智或性格突变\n" +
            "- 每卷的内容要基于原始大纲，不要添加大纲中没有的元素\n" +
            "- 每卷要有明确的阶段性成果，让读者有满足感\n\n" +
            "【分卷整体规划原则】\n\n" +
            "1. **每卷要有不同的阶段目标**\n" +
            "   - 种田文：第2卷扩大规模，第3卷开拓新业务，第4卷进入更大市场\n" +
            "   - 爽文：第2卷对付小BOSS，第3卷对付中BOSS，第4卷对付大BOSS\n" +
            "   - 感情文：第2卷暧昧升温，第3卷确认关系，第4卷深化感情\n" +
            "   每一卷的theme要概括本卷的独特阶段\n\n" +
            "2. **承接第一卷的悬念和伏笔**\n" +
            "   - 第一卷留下的钩子，必须在后续卷中有所回应\n" +
            "   - 第一卷埋下的伏笔，要在后续卷中逐步揭示\n" +
            "   - 第一卷建立的人物关系，要在后续卷中继续发展\n\n" +
            "3. **保持节奏的自然起伏**\n" +
            "   - 不要每一卷都是同样的强度和节奏\n" +
            "   - 可以有铺垫卷、爆发卷、缓冲卷的自然交替\n" +
            "   - 但要符合大纲类型（种田文整体节奏较缓，爽文整体节奏较快）\n\n" +
            "4. **根据大纲类型决定线索数量**\n" +
            "   - 主线任务（必须有）：本卷的核心目标\n" +
            "   - 情感线（如果大纲有）：与重要角色的关系发展\n" +
            "   - 伏笔线（如果大纲有）：长线布局的逐步揭示\n" +
            "   不要强行添加大纲中没有的线索\n\n" +
            "【contentOutline 撰写细则】\n\n" +
            "⚠️ 重要：contentOutline的内容和风格必须与第一卷和原始大纲保持一致！\n\n" +
            "每一卷的 contentOutline 必须遵循以下要求：\n" +
            "- 长度：约300-500字\n" +
            "- 格式：单段落字符串，无换行\n" +
            "- 结构：\n\n" +
            "  **1. 开篇概括句（第一句话）**\n" +
            "  用一句故事化的话概括本卷的核心内容：\n" +
            "  - 主角本卷的主要目标或面临的主要情况\n" +
            "  - 本卷最大的看点（根据类型调整）\n" +
            "  - 本卷的结局走向和留下的悬念\n" +
            "  要用贴合本书风格的语言，不要机械套用公式。\n\n" +
            "  **2. 详细过程描述**\n" +
            "  根据大纲类型，详细描述本卷的发展过程：\n" +
            "  - 种田文：如何继续发展、遇到什么小困难、如何解决、感情线如何进展\n" +
            "  - 爽文：如何对抗敌人、有什么策略和转折、如何打脸获胜\n" +
            "  - 感情文：两人如何互动、有什么心动瞬间、关系如何进展\n" +
            "  描述要具体、有画面感，要体现大纲中的设定。\n\n" +
            "  **3. 高潮场面**\n" +
            "  描述本卷最精彩的部分：\n" +
            "  - 种田文：重要的发展成果、与男主关系的重要进展\n" +
            "  - 爽文：打脸场面、实力展示、复仇成功\n" +
            "  - 感情文：表白、确认关系、重要的亲密互动\n\n" +
            "  **4. 收尾与钩子**\n" +
            "  - 阶段性成果：本卷达成了什么目标\n" +
            "  - 下卷钩子：根据类型设计合适的钩子（发展机遇/新危机/关系变化）\n\n" +
            "- 结尾句格式：整段 contentOutline 的末尾，必须以固定句式收尾：本卷主线推进度约X%%\n\n" +
            "【推进度分配规则】\n" +
            "第一卷已推进50%%主线，剩余%s卷要分配剩余50%%的推进度。\n" +
            "你必须合理规划整个故事的节奏，严禁把主线推进度简单平均分配到每一卷。\n" +
            "- 后续各卷在推进度上要形成起伏曲线：\n" +
            "  有的卷主线只前进很少（明显低于基线，用来铺垫关系、世界观或长线伏笔的深化与反噬）；\n" +
            "  有的卷主线大幅跃进（明显高于基线，用来承载阶段性大事件、关键反转或终局对决）。\n" +
            "- 建议分配：某些卷10-15%%（铺垫/关系深化），某些卷20-25%%（高潮/反转）\n" +
            "- 确保所有卷的推进度数值总和约为50%%（加上第一卷的50%%，总计100%%）\n\n" +
            "【输出格式】\n" +
            "直接输出一个纯净、完整的 JSON 数组，包含%s个卷对象，格式必须为：\n" +
            "[\n" +
            "  {\n" +
            "    \"title\": \"第2卷：[关键词命名，4-8字]\",\n" +
            "    \"theme\": \"[本卷在全书中的阶段性概括，如：名声反转、权力博弈、清算旧账]\",\n" +
            "    \"contentOutline\": \"[严格遵循上方细则撰写的单段高密度字符串（包含开篇引爆句xxx过程推演xxx转折与高潮xxx收尾与强力钩子xxx）。本卷主线推进度约X%%]\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"title\": \"第3卷：...\",\n" +
            "    \"theme\": \"...\",\n" +
            "    \"contentOutline\": \"...\"\n" +
            "  }\n" +
            "  // ... 共%s个卷对象\n" +
            "]\n\n" +
            "【重要提醒】\n" +
            "- 必须先完成第一步的风格分析，理解大纲类型和第一卷基调\n" +
            "- 后续卷必须与第一卷风格一致，延续第一卷的节奏和看点方向\n" +
            "- 所有内容必须基于原始大纲，不要添加大纲中没有的元素\n" +
            "- 数量必须精准：JSON 数组中的对象数量必须正好是%s个\n" +
            "- 每个卷的 contentOutline 必须是单段落字符串，不要换行，长度约300-500字\n" +
            "- 每个卷必须在 contentOutline 末尾明确标注推进度：本卷主线推进度约X%%\n" +
            "- 要充分利用第一卷埋下的伏笔和悬念，自然承接剧情发展\n" +
            "- 每一卷都要有不同的阶段目标，但风格和类型要保持一致\n" +
            "- 只让大纲中已有的角色出场，不要添加新的组织、势力、角色\n\n" +
            "开始你的创作，先分析风格，再生成后续各卷的JSON数组。",
            remainingCount + 1,           // %s - 第2卷到第N卷
            remainingCount,               // %s - 共N卷
            firstVolumeTitle,             // %s - 第一卷标题
            firstVolumeTheme,             // %s - 第一卷主题
            escapedFirstVolumeOutline,    // %s - 第一卷详细大纲
            escapedOutlineContent,        // %s - 原始全书大纲参考
            remainingCount,               // %s - 剩余N卷要分配
            remainingCount,               // %s - 包含N个卷对象
            remainingCount,               // %s - 共N个卷对象
            remainingCount                // %s - 必须正好是N个
        );
        
        try {
            logger.info("🤖 调用AI生成剩余{}卷，提示词长度: {}", remainingCount, prompt.length());
            
            long startTime = System.currentTimeMillis();
            String response;
            if (aiConfig != null && aiConfig.isValid()) {
                response = aiWritingService.generateContent(prompt, "remaining_volumes_planning", aiConfig);
            } else {
                response = aiService.callAI("VOLUME_PLANNER", prompt);
            }
            long endTime = System.currentTimeMillis();
            
            logger.info("⏱️ AI服务响应时间: {}ms", (endTime - startTime));
            logger.info("📥 AI返回的剩余卷响应长度: {}", response.length());
            
            // 解析卷数组
            List<Map<String, Object>> remainingVolumes = parseVolumePlansFromAI(response, remainingCount);
            
            logger.info("✅ 剩余{}卷解析成功", remainingVolumes.size());
            return remainingVolumes;
            
        } catch (Exception e) {
            logger.error("❌ 生成剩余卷失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成剩余卷失败: " + e.getMessage(), e);
        }
    }


  

    /**
     * 解析AI返回的卷规划
     */
    private List<Map<String, Object>> parseVolumePlansFromAI(String response, Integer volumeCount) {
        List<Map<String, Object>> plans = new ArrayList<>();

        try {
            logger.info("🔍 开始解析AI卷规划响应，响应长度: {}", response != null ? response.length() : 0);

            if (response == null || response.isEmpty()) {
                logger.error("❌ AI响应为空或null！");
                throw new RuntimeException("AI响应为空，无法解析卷规划");
            }

            // 尝试解析JSON
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent != null && !jsonContent.trim().isEmpty()) {
                logger.info("✅ 提取到JSON内容，长度: {}", jsonContent.length());

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

                List<Map> jsonPlans = null;

                String sanitizedJson = sanitizeJsonForParsing(jsonContent);
                if (!jsonContent.equals(sanitizedJson)) {
                    logger.info("🧹 JSON内容经过清理，长度: {} -> {}", jsonContent.length(), sanitizedJson.length());
                }

                try {
                    jsonPlans = mapper.readValue(sanitizedJson, List.class);
                    logger.info("✅ JSON解析成功，获得{}个卷规划", jsonPlans.size());
                } catch (Exception e) {
                    logger.error("❌ JSON解析失败（清理后）: {}", e.getMessage());
                    throw e;
                }
                
                if (jsonPlans == null) {
                    throw new RuntimeException("JSON解析失败，未获取到卷规划数据");
                }
                
                for (int i = 0; i < jsonPlans.size(); i++) {
                    Map jsonPlan = jsonPlans.get(i);
                    Map<String, Object> plan = new HashMap<>();

                    String title = (String) jsonPlan.getOrDefault("title", "第" + (i + 1) + "卷");
                    String theme = (String) jsonPlan.getOrDefault("theme", "待定主题");

                    // 处理 contentOutline 字段，支持字符串和对象两种格式
                    Object contentOutlineObj = jsonPlan.get("contentOutline");
                    String contentOutline = "";

                    if (contentOutlineObj instanceof String) {
                        // 期望的格式：直接是字符串
                        contentOutline = (String) contentOutlineObj;
                        logger.info("✅ 卷{} contentOutline 是字符串格式（正确）", i + 1);
                    } else if (contentOutlineObj instanceof Map) {
                        // 兼容旧格式：是对象，包含 coreConflict 和 progress
                        Map contentMap = (Map) contentOutlineObj;
                        logger.warn("⚠️ 卷{} contentOutline 是对象格式（旧格式），正在转换为字符串", i + 1);

                        String coreConflict = contentMap.get("coreConflict") != null ? contentMap.get("coreConflict").toString() : "";
                        String progress = contentMap.get("progress") != null ? contentMap.get("progress").toString() : "";

                        // 合并为一个字符串
                        if (!coreConflict.isEmpty() && !progress.isEmpty()) {
                            contentOutline = coreConflict + "\n\n" + progress;
                        } else if (!coreConflict.isEmpty()) {
                            contentOutline = coreConflict;
                        } else if (!progress.isEmpty()) {
                            contentOutline = progress;
                        }

                        logger.info("📝 已将对象格式转换为字符串，长度={}", contentOutline.length());
                    } else {
                        logger.error("❌ 卷{} contentOutline 格式未知: {}", i + 1, contentOutlineObj);
                    }

                    plan.put("title", title);
                    plan.put("theme", theme);
                    plan.put("contentOutline", contentOutline);

                    logger.info("📝 卷{}解析成功: 标题='{}', 主题='{}', 大纲长度={}", i + 1, title, theme, contentOutline.length());
                    plans.add(plan);
                }
                
                if (plans.size() != volumeCount) {
                    logger.warn("⚠️ 解析的卷数({})与目标卷数({})不符，调整中...", plans.size(), volumeCount);
                    plans = adjustVolumeCount(plans, volumeCount);
                }
                
            } else {
                logger.warn("⚠️ 未能从AI响应中提取到有效JSON内容");
                logger.warn("🔍 尝试文本解析作为备用方案");
                
                // 尝试从文本中解析卷信息
                plans = parseVolumePlansFromText(response, volumeCount);
            }
        } catch (Exception e) {
            logger.error("❌ 解析AI卷规划失败: {}", e.getMessage(), e);
            logger.error("🔍 解析失败的响应长度: {}", response != null ? response.length() : 0);
            if (response != null && response.length() > 0) {
                logger.error("🔍 失败响应的前200字符: {}", response.substring(0, Math.min(200, response.length())));
            }
            // 直接抛出异常，让上层感知失败并返回错误给前端，而不是静默生成默认卷
            throw new RuntimeException("解析AI卷规划失败: " + e.getMessage(), e);
        }

        if (plans.isEmpty()) {
            // 文本解析也未得到任何有效卷规划，视为失败
            throw new RuntimeException("AI卷规划解析结果为空，请检查模型输出与提示词配置。");
        }

        logger.info("🎯 最终返回{}个卷规划", plans.size());
        return plans;
    }

    /**
     * 清理AI返回的JSON，确保字符串内部的换行和特殊引号被正确转义
     */
    private String sanitizeJsonForParsing(String json) {
        if (json == null) {
            return null;
        }

        StringBuilder cleaned = new StringBuilder(json.length() + 100);
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"' && !escaping) {
                inString = !inString;
                cleaned.append(c);
            } else if (inString) {
                switch (c) {
                    case '\n':
                        cleaned.append("\\n");
                        break;
                    case '\r':
                        cleaned.append("\\r");
                        break;
                    case '\t':
                        cleaned.append("\\t");
                        break;
                    case '\u201C':
                    case '\u201D':
                    case '\uFF02':
                        cleaned.append("\\\"");
                        break;
                    case '\u2018':
                    case '\u2019':
                        cleaned.append('\'');
                        break;
                    default:
                        cleaned.append(c);
                        break;
                }
            } else {
                if (c == '\u201C' || c == '\u201D' || c == '\uFF02') {
                    cleaned.append('"');
                } else if (c == '\u2018' || c == '\u2019') {
                    cleaned.append('\'');
                } else {
                    cleaned.append(c);
                }
            }

            if (c == '\\' && !escaping) {
                escaping = true;
            } else {
                escaping = false;
            }
        }

        return cleaned.toString();
    }
    
    /**
     * 从文本中解析卷规划（备用方案）
     */
    private List<Map<String, Object>> parseVolumePlansFromText(String response, Integer volumeCount) {
        List<Map<String, Object>> plans = new ArrayList<>();
        logger.info("📝 尝试从文本解析卷规划");
        
        try {
            String[] lines = response.split("\n");
            Map<String, Object> currentVolume = null;
            int volumeIndex = 0;
            
            for (String line : lines) {
                line = line.trim();
                if (line.matches(".*第\\d+卷.*|.*卷\\d+.*|.*Volume\\s*\\d+.*")) {
                    // 保存前一卷
                    if (currentVolume != null) {
                        plans.add(currentVolume);
                    }
                    
                    // 创建新卷
                    currentVolume = new HashMap<>();
                    volumeIndex++;
                    currentVolume.put("title", extractVolumeTitle(line));
                    currentVolume.put("theme", "从文本解析的主题" + volumeIndex);
                    currentVolume.put("contentOutline", "从文本解析的大纲" + volumeIndex);
                    currentVolume.put("chapterCount", 20);
                    currentVolume.put("estimatedWordCount", 25000);
                    currentVolume.put("keyEvents", "从文本解析的关键事件" + volumeIndex);
                    currentVolume.put("characterDevelopment", "从文本解析的角色发展" + volumeIndex);
                    currentVolume.put("plotThreads", "从文本解析的情节线索" + volumeIndex);
                    
                    logger.info("📖 从文本解析出卷{}: {}", volumeIndex, currentVolume.get("title"));
                } else if (currentVolume != null && !line.isEmpty()) {
                    // 补充卷信息
                    if (line.contains("主题") || line.contains("theme")) {
                        currentVolume.put("theme", cleanTextContent(line));
                    } else if (line.contains("描述") || line.contains("description")) {
                        String prev = (String) currentVolume.getOrDefault("contentOutline", "");
                        String combined = prev.isEmpty() ? cleanTextContent(line) : prev + "\n" + cleanTextContent(line);
                        currentVolume.put("contentOutline", combined);
                    }
                }
            }
            
            // 保存最后一卷
            if (currentVolume != null) {
                plans.add(currentVolume);
            }
            
            logger.info("📚 从文本解析出{}个卷", plans.size());
            
        } catch (Exception e) {
            logger.error("❌ 文本解析也失败了: {}", e.getMessage());
        }
        
        return plans;
    }
    
    /**
     * 调整卷数量以匹配目标
     */
    private List<Map<String, Object>> adjustVolumeCount(List<Map<String, Object>> plans, Integer volumeCount) {
        if (plans.size() == volumeCount) {
            return plans;
        }
        
        List<Map<String, Object>> adjustedPlans = new ArrayList<>();
        
        if (plans.size() > volumeCount) {
            // 如果卷太多，取前N卷
            for (int i = 0; i < volumeCount; i++) {
                adjustedPlans.add(plans.get(i));
            }
        } else {
            // 如果卷太少，补充默认卷
            adjustedPlans.addAll(plans);
            for (int i = plans.size(); i < volumeCount; i++) {
                Map<String, Object> defaultVolume = new HashMap<>();
                defaultVolume.put("title", "第" + (i + 1) + "卷");
                defaultVolume.put("theme", "补充卷主题" + (i + 1));

                defaultVolume.put("contentOutline", "补充卷大纲" + (i + 1));
                defaultVolume.put("chapterCount", 20);
                defaultVolume.put("estimatedWordCount", 25000);
                defaultVolume.put("keyEvents", "补充关键事件");
                defaultVolume.put("characterDevelopment", "补充角色发展");
                defaultVolume.put("plotThreads", "补充情节线索");
                adjustedPlans.add(defaultVolume);
            }
        }
        
        logger.info("🔧 调整卷数量从{}到{}", plans.size(), volumeCount);
        return adjustedPlans;
    }
    
    /**
     * 提取卷标题
     */
    private String extractVolumeTitle(String line) {
        // 简单提取逻辑
        if (line.contains("：")) {
            return line.substring(line.indexOf("：") + 1).trim();
        }
        return line.replaceAll("[第卷Volume\\d\\s]", "").trim();
    }
    
    /**
     * 清理文本内容
     */
    private String cleanTextContent(String text) {
        if (text == null) return "";
        return text.replaceAll("^[主题描述：:]+", "").trim();
    }

    /**
     * 解析AI返回的详细大纲
     */
    private Map<String, Object> parseDetailedOutlineFromAI(String response) {
        Map<String, Object> outline = new HashMap<>();
        outline.put("rawResponse", response);
        outline.put("parsedAt", LocalDateTime.now());
        
        // 简单解析，实际项目中可以更复杂
        outline.put("structure", extractContent(response, "整体结构", "分章节大纲"));
        outline.put("chapters", extractContent(response, "分章节大纲", "关键转折点"));
        outline.put("turningPoints", extractContent(response, "关键转折点", "角色发展"));
        outline.put("characterArcs", extractContent(response, "角色发展", "伏笔设置"));
        outline.put("foreshadowing", extractContent(response, "伏笔设置", ""));
        
        return outline;
    }

    /**
     * 解析AI返回的写作指导
     */
    private Map<String, Object> parseWritingGuidanceFromAI(String response) {
        Map<String, Object> guidance = new HashMap<>();
        guidance.put("rawResponse", response);
        guidance.put("generatedAt", LocalDateTime.now());
        
        try {
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                Map guidanceData = mapper.readValue(jsonContent, Map.class);
                guidance.putAll(guidanceData);
            }
        } catch (Exception e) {
            logger.warn("解析AI写作指导失败: {}", e.getMessage());
            // 添加默认指导
            Map<String, Object> defaultSuggestion = new HashMap<>();
            defaultSuggestion.put("type", "general");
            defaultSuggestion.put("content", "继续保持当前写作节奏");
            defaultSuggestion.put("priority", "medium");
            guidance.put("suggestions", Arrays.asList(defaultSuggestion));
            guidance.put("nextFocus", "专注于情节推进和角色发展");
        }
        
        return guidance;
    }

    /**
     * 生成默认卷规划（确保永不为空）
     */
    /**
     * 基于大纲生成简化的卷规划（AI自动取名）
     * 当完整的AI生成失败时使用此方法
     */
    private List<Map<String, Object>> generateSimplifiedVolumePlans(Novel novel, 
        com.novel.domain.entity.NovelOutline outline, Integer volumeCount) {
        logger.info("🔨 基于大纲生成{}个简化卷规划（AI自动命名）", volumeCount);
        
        try {
            // 构建简化的提示词，只要求AI生成卷名和主题
            String outlineContent = (outline.getPlotStructure() != null && !outline.getPlotStructure().trim().isEmpty()) 
                ? outline.getPlotStructure() 
                : (outline.getBasicIdea() != null ? outline.getBasicIdea() : "");
            
            String prompt = String.format(
                "你是网文结构规划师。根据以下大纲，为小说《%s》生成 %s 个卷的标题和主题。\n\n" +
                "小说类型：%s\n\n" +
                "大纲内容：\n%s\n\n" +
                "要求：\n" +
                "1. 必须生成恰好 %s 个卷\n" +
                "2. 每个卷标题简洁有力（6-12字），体现该卷核心\n" +
                "3. 主题应该体现该卷的叙事重点\n" +
                "4. 只输出JSON数组，每个元素包含title和theme字段\n" +
                "5. 不要任何其他说明\n\n" +
                "示例格式：\n" +
                "[{\"title\":\"卷主题名称\",\"theme\":\"这卷的描述\"},{\"title\":\"卷主题名称\",\"theme\":\"这卷的描述\"}]\n\n" +
                "请开始生成：",
                novel.getTitle(),
                volumeCount,
                novel.getGenre(),
                outlineContent.substring(0, Math.min(2000, outlineContent.length())), // 限制长度避免token超限
                volumeCount
            );
            
            logger.info("🤖 调用AI生成简化卷规划");
            String response = aiService.callAI("VOLUME_PLANNER", prompt);
            
            if (response != null && response.length() > 0) {
                // 尝试解析AI返回的卷名和主题
                List<Map<String, Object>> basicInfo = parseSimplifiedVolumePlans(response, volumeCount);
                
                // 为每个卷补充完整信息
                List<Map<String, Object>> plans = new ArrayList<>();
                for (int i = 0; i < basicInfo.size(); i++) {
                    Map<String, Object> info = basicInfo.get(i);
                    Map<String, Object> plan = new HashMap<>();
                    
                    plan.put("title", info.get("title"));
                    plan.put("theme", info.get("theme"));

                    plan.put("contentOutline", "本卷主题：" + info.get("theme") + "。详细内容需要进一步完善。");
                    plan.put("chapterCount", 20);
                    plan.put("estimatedWordCount", 25000);
                    
                    plans.add(plan);
                }
                
                if (!plans.isEmpty()) {
                    logger.info("✅ AI生成简化卷规划成功，共{}卷", plans.size());
                    return plans;
                }
            }
        } catch (Exception e) {
            logger.error("❌ AI生成简化卷规划失败: {}", e.getMessage());
        }
        
        // 如果AI也失败了，使用最基础的默认规划
        logger.warn("⚠️ 使用最基础的默认卷规划");
        return generateBasicVolumePlans(volumeCount);
    }
    
    /**
     * 解析简化的卷规划（只包含title和theme）
     */
    private List<Map<String, Object>> parseSimplifiedVolumePlans(String response, Integer volumeCount) {
        List<Map<String, Object>> plans = new ArrayList<>();
        
        try {
            // 提取JSON部分
            String json = extractJSONFromResponse(response);
            if (json == null || json.trim().isEmpty()) {
                json = response;
            }
            
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> parsed = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            
            // 确保数量正确
            for (int i = 0; i < Math.min(parsed.size(), volumeCount); i++) {
                Map<String, Object> item = parsed.get(i);
                if (item.containsKey("title") && item.containsKey("theme")) {
                    plans.add(item);
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ 解析简化卷规划失败: {}", e.getMessage());
        }
        
        return plans;
    }
    
    /**
     * 生成最基础的默认卷规划
     * 仅在完全无法访问大纲或AI全部失败时使用
     */
    private List<Map<String, Object>> generateBasicVolumePlans(Integer volumeCount) {
        List<Map<String, Object>> plans = new ArrayList<>();
        logger.info("🔨 生成{}个基础默认卷规划", volumeCount);
        
        for (int i = 1; i <= volumeCount; i++) {
            Map<String, Object> plan = new HashMap<>();
            
            plan.put("title", "第" + i + "卷");
            plan.put("theme", "第" + i + "卷主题");

            plan.put("contentOutline", "第" + i + "卷的详细内容大纲，需要进一步补充。");
            plan.put("chapterCount", 20);
            plan.put("estimatedWordCount", 25000);
            
            plans.add(plan);
        }
        
        logger.info("✅ 基础卷规划生成完成，共{}卷", plans.size());
        return plans;
    }

    /**
     * 从响应中提取JSON内容
     */
    private String extractJSONFromResponse(String response) {
        try {
            logger.info("🔍 开始从响应中提取JSON内容，响应长度: {}", response.length());

            // 先尝试提取 ```json ... ``` 格式
            String jsonStart = "```json";
            String jsonEnd = "```";

            int startIdx = response.indexOf(jsonStart);
            if (startIdx != -1) {
                logger.info("🔍 找到 '```json' 标记，位置: {}", startIdx);
                startIdx += jsonStart.length();
                int endIdx = response.indexOf(jsonEnd, startIdx);

                if (endIdx != -1) {
                    String extracted = response.substring(startIdx, endIdx).trim();
                    logger.info("✅ 从Markdown代码块中提取JSON，长度: {}", extracted.length());
                    return extracted;
                }
            }

            // 尝试查找完整的JSON数组（匹配括号）
            int braceStart = response.indexOf("[");
            if (braceStart != -1) {
                logger.info("🔍 找到 '[' 字符，位置: {}", braceStart);
                int depth = 0;
                boolean inString = false;
                char prevChar = 0;

                for (int i = braceStart; i < response.length(); i++) {
                    char c = response.charAt(i);

                    // 处理字符串内的引号（忽略转义的引号）
                    if (c == '"' && prevChar != '\\') {
                        inString = !inString;
                    }

                    // 只在非字符串内统计括号深度
                    if (!inString) {
                        if (c == '[') {
                            depth++;
                        } else if (c == ']') {
                            depth--;
                            if (depth == 0) {
                                // 找到完整的JSON数组
                                String extracted = response.substring(braceStart, i + 1).trim();
                                logger.info("✅ 通过括号匹配提取JSON，长度: {}", extracted.length());
                                return extracted;
                            }
                        }
                    }

                    prevChar = c;
                }

                logger.warn("⚠️ 找到 '[' 但未找到匹配的 ']'，depth={}", depth);
            }

            logger.warn("⚠️ 未能提取有效的JSON内容，响应前200字符: {}",
                response.substring(0, Math.min(200, response.length())));

        } catch (Exception e) {
            logger.error("❌ 提取JSON失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 提取指定内容
     */
    private String extractContent(String response, String startMarker, String endMarker) {
        try {
            int startIdx = response.indexOf(startMarker);
            if (startIdx == -1) return "";
            
            startIdx += startMarker.length();
            
            int endIdx;
            if (endMarker == null || endMarker.isEmpty()) {
                endIdx = response.length();
            } else {
                endIdx = response.indexOf(endMarker, startIdx);
                if (endIdx == -1) endIdx = response.length();
            }
            
            return response.substring(startIdx, endIdx).trim();
        } catch (Exception e) {
            return "";
        }
    }
    

    /**
     * 解析已存在的大纲文本为Map格式
     */
    private Map<String, Object> parseExistingOutline(String outlineText) {
        Map<String, Object> outline = new HashMap<>();
        
        if (outlineText == null || outlineText.trim().isEmpty()) {
            return outline;
        }
        
        outline.put("rawOutline", outlineText);
        outline.put("isExisting", true);
        outline.put("lastParsed", LocalDateTime.now());
        
        // 简单解析一些关键信息
        if (outlineText.contains("核心目标")) {
            String goals = extractSectionContent(outlineText, "📌 核心目标：", "🎯 关键事件：");
            outline.put("goals", Arrays.asList(goals.split("•")).stream()
                .filter(s -> !s.trim().isEmpty())
                .map(String::trim)
                .toArray());
        }
        
        if (outlineText.contains("关键事件")) {
            String events = extractSectionContent(outlineText, "🎯 关键事件：", "👥 角色发展：");
            outline.put("keyEvents", Arrays.asList(events.split("•")).stream()
                .filter(s -> !s.trim().isEmpty())
                .map(String::trim)
                .toArray());
        }
        
        return outline;
    }
    
    /**
     * 从文本中提取指定章节内容
     */
    private String extractSectionContent(String text, String startMarker, String endMarker) {
        try {
            int startIdx = text.indexOf(startMarker);
            if (startIdx == -1) return "";
            
            startIdx += startMarker.length();
            
            int endIdx = text.length();
            if (endMarker != null && !endMarker.isEmpty()) {
                int tempEnd = text.indexOf(endMarker, startIdx);
                if (tempEnd != -1) endIdx = tempEnd;
            }
            
            return text.substring(startIdx, endIdx).trim();
        } catch (Exception e) {
            return "";
        }
    }
    

    

    



    /**
     * 基于确认的大纲生成卷规划（旧方法，保持兼容）
     * @deprecated 请使用 generateVolumePlansFromConfirmedOutline(Long, Integer, AIConfigRequest)
     */
    @Deprecated
    @Transactional
    public List<NovelVolume> generateVolumePlansFromConfirmedOutline(Long novelId, Integer volumeCount) {
        return generateVolumePlansFromConfirmedOutline(novelId, volumeCount, null);
    }
    
    /**
     * 基于确认的大纲生成卷规划（确认大纲后调用，支持AI配置）
     * 说明：直接使用大纲内容，调用AI进行智能拆分，并保存到数据库
     */
    @Transactional
    public List<NovelVolume> generateVolumePlansFromConfirmedOutline(Long novelId, Integer volumeCount, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("📝 基于确认的大纲生成卷规划，小说ID: {}, 卷数: {}", novelId, volumeCount);
        
        try {
            // 1. 获取小说信息
            Novel novel = novelRepository.selectById(novelId);
            if (novel == null) {
                throw new RuntimeException("小说不存在: " + novelId);
            }
            
            // 2. 读取 novels.outline 作为确认后大纲
            if (novel.getOutline() == null || novel.getOutline().trim().isEmpty()) {
                throw new RuntimeException("未找到确认的大纲（novels.outline 为空）");
            }
            
            // 3. 生成卷规划（返回Map格式）- 使用现有的generateVolumePlansFromOutline方法
            // 首先需要获取NovelOutline对象
            java.util.Optional<com.novel.domain.entity.NovelOutline> outlineOpt = outlineRepository.findByNovelId(novelId);
            if (!outlineOpt.isPresent()) {
                throw new RuntimeException("未找到小说大纲对象，请先创建大纲");
            }
            
            // 传递AI配置到卷规划生成方法
            List<Map<String, Object>> volumePlans;
            if (aiConfig != null && aiConfig.isValid()) {
                logger.info("✅ 使用AI配置生成卷规划");
                volumePlans = generateVolumePlansFromOutline(novel, outlineOpt.get(), volumeCount, aiConfig);
            } else {
                logger.warn("⚠️ 未提供有效AI配置，使用默认方式生成卷规划");
                volumePlans = generateVolumePlansFromOutline(novel, outlineOpt.get(), volumeCount);
            }
            
            // 4. 覆盖式重建：先查询现有卷，删除卷及其派生数据，再插入新卷
            List<NovelVolume> existingVolumes = volumeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<NovelVolume>()
                    .eq("novel_id", novelId)
            );
            
            if (!existingVolumes.isEmpty()) {
                logger.info("🧹 检测到小说 {} 已有 {} 个卷，开始清理旧数据...", novelId, existingVolumes.size());
                
                // 先删除每个卷的派生数据（章纲、伏笔日志等）
                for (NovelVolume existingVolume : existingVolumes) {
                    Long volumeId = existingVolume.getId();
                    try {
                        // 删除卷章纲
                        volumeChapterOutlineRepository.deleteByVolumeId(volumeId);
                        logger.info("  ✓ 已删除卷 {} 的章纲", volumeId);
                    } catch (Exception e) {
                        logger.warn("  ⚠️ 删除卷 {} 章纲失败（可能不存在）: {}", volumeId, e.getMessage());
                    }
                    
                    try {
                        // 删除伏笔生命周期日志
                        foreshadowLifecycleLogRepository.deleteByVolumeId(volumeId);
                        logger.info("  ✓ 已删除卷 {} 的伏笔日志", volumeId);
                    } catch (Exception e) {
                        logger.warn("  ⚠️ 删除卷 {} 伏笔日志失败（可能不存在）: {}", volumeId, e.getMessage());
                    }
                }
                
                // 最后删除所有卷本身
                int deletedCount = volumeMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<NovelVolume>()
                        .eq("novel_id", novelId)
                );
                logger.info("🗑️ 已删除小说 {} 的 {} 个旧卷", novelId, deletedCount);
            } else {
                logger.info("ℹ️ 小说 {} 当前无卷，直接生成新卷", novelId);
            }
            
            // 5. 转换为NovelVolume实体并保存到数据库
            List<NovelVolume> savedVolumes = new ArrayList<>();
            int currentChapter = 1;
            
            // 基于用户填写的目标字数与目标章节数，计算每章字数
            int targetTotalWords = novel.getTotalWordTarget() != null ? novel.getTotalWordTarget() : 0;
            int targetTotalChapters = novel.getTargetTotalChapters() != null && novel.getTargetTotalChapters() > 0 ? novel.getTargetTotalChapters() : 0;
            int avgWordsPerChapter = targetTotalChapters > 0 && targetTotalWords > 0 ? Math.max(500, targetTotalWords / targetTotalChapters) : 1200;
            
            // 先构建所有卷对象，然后批量插入，避免前端轮询时查询到部分数据
            logger.info("🔨 开始构建{}个卷对象...", volumePlans.size());

            for (int i = 0; i < volumePlans.size(); i++) {
                Map<String, Object> plan = volumePlans.get(i);

                NovelVolume volume = new NovelVolume();
                volume.setNovelId(novelId);
                volume.setVolumeNumber(i + 1);
                volume.setTitle((String) plan.get("title"));
                volume.setTheme((String) plan.get("theme"));
                // 不再生成/保存描述，直接保存大纲
                Object outlineObj = plan.get("contentOutline");
                volume.setContentOutline(outlineObj instanceof String ? (String) outlineObj : null);

                // 动态计算章节范围
                int totalChapters = novel.getTargetTotalChapters() != null ? novel.getTargetTotalChapters() : (targetTotalChapters > 0 ? targetTotalChapters : 100);
                int chaptersPerVolume = totalChapters / volumeCount;
                int remainder = totalChapters % volumeCount;

                // 前remainder个卷多分配1章
                if (i < remainder) {
                    chaptersPerVolume++;
                }

                volume.setChapterStart(currentChapter);
                volume.setChapterEnd(currentChapter + chaptersPerVolume - 1);
                currentChapter += chaptersPerVolume;

                // 估算卷字数：按用户平均每章字数计算
                int estimatedWords = chaptersPerVolume * avgWordsPerChapter;
                volume.setEstimatedWordCount(estimatedWords);
                volume.setStatus(VolumeStatus.PLANNED);
                volume.setCreatedAt(java.time.LocalDateTime.now());
                volume.setLastModifiedByAi(java.time.LocalDateTime.now());

                savedVolumes.add(volume);

                logger.info("✅ 卷{}对象构建完成: 标题='{}', 章节范围={}-{}, 预估字数={}",
                    i + 1, volume.getTitle(), volume.getChapterStart(), volume.getChapterEnd(), estimatedWords);
            }

            // 批量插入所有卷到数据库
            logger.info("💾 开始批量保存{}个卷到数据库...", savedVolumes.size());
            for (NovelVolume volume : savedVolumes) {
                volumeMapper.insert(volume);
                logger.info("✅ 卷{}保存成功: ID={}", volume.getVolumeNumber(), volume.getId());
            }

            logger.info("🎯 成功生成并保存{}个卷到数据库", savedVolumes.size());

            // 更新小说的创作阶段为"卷已生成"
            try {
                novelService.updateCreationStage(novelId, Novel.CreationStage.VOLUMES_GENERATED);
                logger.info("✅ 更新小说 {} 创作阶段为：卷已生成", novelId);
            } catch (Exception e) {
                logger.warn("⚠️ 更新小说创作阶段失败: {}", e.getMessage());
            }

            return savedVolumes;

        } catch (Exception e) {
            logger.error("❌ 基于确认大纲生成卷规划失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成卷规划失败: " + e.getMessage());
        }
    }

    /**
     * 基于确认的大纲异步生成卷规划（旧方法，保持兼容）
     * @deprecated 请使用 generateVolumePlansFromConfirmedOutlineAsync(Long, Integer, AIConfigRequest)
     */
    @Deprecated
    public com.novel.domain.entity.AITask generateVolumePlansFromConfirmedOutlineAsync(Long novelId, Integer volumeCount) {
        return generateVolumePlansFromConfirmedOutlineAsync(novelId, volumeCount, null);
    }
    
    /**
     * 基于确认的大纲异步生成卷规划（支持AI配置）
     */
    public com.novel.domain.entity.AITask generateVolumePlansFromConfirmedOutlineAsync(Long novelId, Integer volumeCount, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("🚀 提交基于确认大纲的卷规划生成任务，小说ID: {}, 卷数: {}", novelId, volumeCount);
        
        try {
            // 创建AI任务
            com.novel.domain.entity.AITask aiTask = new com.novel.domain.entity.AITask();
            aiTask.setName("基于确认大纲生成卷规划");
            aiTask.setType(com.novel.domain.entity.AITask.AITaskType.STORY_OUTLINE);
            aiTask.setStatus(com.novel.domain.entity.AITask.AITaskStatus.PENDING);
            aiTask.setNovelId(novelId);
            
            // 构建任务参数
            Map<String, Object> params = new HashMap<>();
            params.put("novelId", novelId);
            params.put("volumeCount", volumeCount);
            params.put("operationType", "GENERATE_VOLUMES_FROM_CONFIRMED_OUTLINE");
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            aiTask.setParameters(mapper.writeValueAsString(params));
            
            // 估算完成时间
            aiTask.setEstimatedCompletion(java.time.LocalDateTime.now().plusMinutes(5));
            aiTask.setMaxRetries(3);
            
            // 提交异步任务，传递AI配置
            Long taskId = submitVolumePlansFromConfirmedOutlineTask(aiTask, novelId, volumeCount, aiConfig);
            aiTask.setId(taskId);
            
            return aiTask;
            
        } catch (Exception e) {
            logger.error("❌ 提交基于确认大纲的卷规划生成任务失败: {}", e.getMessage(), e);
            throw new RuntimeException("提交任务失败: " + e.getMessage());
        }
    }

    /**
     * 提交基于确认大纲的卷规划生成任务（支持AI配置）
     */
    private Long submitVolumePlansFromConfirmedOutlineTask(com.novel.domain.entity.AITask aiTask, Long novelId, Integer volumeCount, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("📋 提交基于确认大纲的卷规划生成任务到异步队列，小说ID: {}", novelId);
        
        try {
            // 使用 AITaskService 创建任务
        com.novel.dto.AITaskDto taskDto = aiTaskService.createTask(aiTask);
            Long taskId = Long.valueOf(taskDto.getId());
            
            // 启动异步生成任务
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    logger.info("🤖 开始异步生成小说 {} 的卷规划（基于确认大纲）", novelId);
                    
                    // 更新任务状态为运行中（直接更新，跳过权限验证）
                    aiTaskService.updateTaskProgress(taskId, 10, "RUNNING", "准备基于确认大纲生成卷规划");
                    
                    // 调用基于确认大纲的生成方法，传递AI配置
                    List<NovelVolume> volumes = generateVolumePlansFromConfirmedOutline(novelId, volumeCount, aiConfig);
                    
                    // 更新任务状态为完成
                    aiTaskService.updateTaskProgress(taskId, 100, "COMPLETED", "卷规划生成完成");
                    
                    // 构建结果
                    Map<String, Object> result = new HashMap<>();
                    result.put("volumes", volumes);
                    result.put("volumeCount", volumes.size());
                    result.put("novelId", novelId);
                    
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                    mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    String output = mapper.writeValueAsString(result);
                    aiTaskService.completeTask(taskId, output);
                    
                    logger.info("✅ 小说 {} 基于确认大纲的异步卷规划生成完成，共生成 {} 卷", novelId, volumes.size());
                    return volumes;
                } catch (Exception e) {
                    logger.error("❌ 小说 {} 基于确认大纲的异步卷规划生成失败: {}", novelId, e.getMessage(), e);
                    // 更新任务状态为失败
                    try {
                        aiTaskService.failTask(taskId, "生成失败: " + e.getMessage());
                    } catch (Exception ex) {
                        logger.error("更新任务失败状态时出错: {}", ex.getMessage());
                    }
                    throw new RuntimeException(e.getMessage());
                }
            }).exceptionally(ex -> {
                // 处理异步任务异常
                logger.error("❌ 异步任务执行异常: {}", ex.getMessage(), ex);
                try {
                    aiTaskService.failTask(taskId, "异步任务失败: " + ex.getMessage());
                } catch (Exception e) {
                    logger.error("更新任务失败状态时出错: {}", e.getMessage());
                }
                return null;
            });
            
            logger.info("✅ 小说 {} 基于确认大纲的卷规划生成任务已提交，任务ID: {}", novelId, taskId);
            return taskId;
            
        } catch (Exception e) {
            logger.error("❌ 提交基于确认大纲的卷规划生成任务失败: {}", e.getMessage(), e);
            throw new RuntimeException("提交异步任务失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取卷信息
     */
    public NovelVolume getVolumeById(Long volumeId) {
        return volumeMapper.selectById(volumeId);
    }

    /**
     * 更新卷信息
     */
    public void updateVolume(NovelVolume volume) {
        volumeMapper.updateById(volume);
    }

    /**
     * AI优化卷大纲（流式）
     * 注意：使用@Transactional(propagation = Propagation.NOT_SUPPORTED)禁用事务，因为流式处理是渐进式的
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void optimizeVolumeOutlineStream(Long volumeId, String currentOutline, String suggestion, Map<String, Object> volumeInfo, com.novel.dto.AIConfigRequest aiConfig, java.util.function.Consumer<String> chunkConsumer) {
        logger.info("🎨 开始流式优化卷 {} 的大纲", volumeId);
        
        try {
            // 获取卷信息
            NovelVolume volume = volumeMapper.selectById(volumeId);
            if (volume == null) {
                throw new RuntimeException("卷不存在");
            }
            
            // 获取小说信息
            Novel novel = novelService.getNovelById(volume.getNovelId());
            
            // 构建优化提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一位资深网文编辑，现在需要根据用户的建议优化卷大纲。\n\n");
            prompt.append("**小说信息：**\n");
            prompt.append("- 标题：").append(novel.getTitle()).append("\n");
            prompt.append("- 类型：").append(novel.getGenre()).append("\n");
            
            prompt.append("\n**卷信息：**\n");
            if (volumeInfo != null) {
                prompt.append("- 卷序号：第").append(volumeInfo.get("volumeNumber")).append("卷\n");
                prompt.append("- 章节范围：第").append(volumeInfo.get("chapterStart"))
                      .append("-").append(volumeInfo.get("chapterEnd")).append("章\n");
                if (volumeInfo.get("description") != null) {
                    prompt.append("- 卷简介：").append(volumeInfo.get("description")).append("\n");
                }
            } else {
                prompt.append("- 卷序号：第").append(volume.getVolumeNumber()).append("卷\n");
                prompt.append("- 章节范围：第").append(volume.getChapterStart())
                      .append("-").append(volume.getChapterEnd()).append("章\n");
            }
            
            prompt.append("\n**当前大纲：**\n");
            prompt.append(currentOutline).append("\n\n");
            
            prompt.append("**优化建议：**\n");
            prompt.append(suggestion).append("\n\n");
            
            prompt.append("**优化要求：**\n");
            prompt.append("1. 在保持原有大纲核心框架的基础上，根据用户建议进行改进\n");
            prompt.append("2. 确保优化后的大纲逻辑连贯、情节合理、节奏紧凑\n");
            prompt.append("3. 保持大纲的结构清晰，使用小标题组织内容\n");
            prompt.append("4. 不要列出具体的章节编号，只描述剧情流程和发展脉络\n");
            prompt.append("5. 使用生动的语言，让大纲充满画面感和吸引力\n");
            prompt.append("6. 直接输出优化后的完整大纲，不要添加\"根据建议\"等元话语\n\n");
            prompt.append("请直接输出优化后的大纲：\n");
            
            // 使用流式生成
            StringBuilder accumulated = new StringBuilder();
            aiWritingService.streamGenerateContent(prompt.toString(), "volume_outline_optimization", aiConfig, chunk -> {
                accumulated.append(chunk);
                chunkConsumer.accept(chunk);
            });
            
            // 更新数据库
            volume.setContentOutline(accumulated.toString());
            volume.setLastModifiedByAi(java.time.LocalDateTime.now());
            volumeMapper.updateById(volume);
            
            logger.info("✅ 卷大纲流式优化完成");
            
        } catch (Exception e) {
            logger.error("❌ 卷大纲流式优化失败", e);
            throw new RuntimeException("卷大纲优化失败: " + e.getMessage());
        }
    }

    /**
     * AI优化卷大纲（非流式，保留兼容）
     */
    public String optimizeVolumeOutline(Long volumeId, String currentOutline, String suggestion, Map<String, Object> volumeInfo) {
        logger.info("🎨 开始优化卷 {} 的大纲", volumeId);
        
        try {
            // 获取卷信息
            NovelVolume volume = volumeMapper.selectById(volumeId);
            if (volume == null) {
                throw new RuntimeException("卷不存在");
            }
            
            // 获取小说信息
            Novel novel = novelService.getNovelById(volume.getNovelId());
            
            // 构建优化提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一位资深网文编辑，现在需要根据用户的建议优化卷大纲。\n\n");
            prompt.append("**小说信息：**\n");
            prompt.append("- 标题：").append(novel.getTitle()).append("\n");
            prompt.append("- 类型：").append(novel.getGenre()).append("\n");
            
            prompt.append("\n**卷信息：**\n");
            if (volumeInfo != null) {
                prompt.append("- 卷序号：第").append(volumeInfo.get("volumeNumber")).append("卷\n");
                prompt.append("- 章节范围：第").append(volumeInfo.get("chapterStart"))
                      .append("-").append(volumeInfo.get("chapterEnd")).append("章\n");
                if (volumeInfo.get("description") != null) {
                    prompt.append("- 卷简介：").append(volumeInfo.get("description")).append("\n");
                }
            } else {
                prompt.append("- 卷序号：第").append(volume.getVolumeNumber()).append("卷\n");
                prompt.append("- 章节范围：第").append(volume.getChapterStart())
                      .append("-").append(volume.getChapterEnd()).append("章\n");
            }
            
            prompt.append("\n**当前大纲：**\n");
            prompt.append(currentOutline).append("\n\n");
            
            prompt.append("**优化建议：**\n");
            prompt.append(suggestion).append("\n\n");
            
            prompt.append("**优化要求：**\n");
            prompt.append("1. 在保持原有大纲核心框架的基础上，根据用户建议进行改进\n");
            prompt.append("2. 确保优化后的大纲逻辑连贯、情节合理、节奏紧凑\n");
            prompt.append("3. 保持大纲的结构清晰，使用小标题组织内容\n");
            prompt.append("4. 不要列出具体的章节编号，只描述剧情流程和发展脉络\n");
            prompt.append("5. 使用生动的语言，让大纲充满画面感和吸引力\n");
            prompt.append("6. 直接输出优化后的完整大纲，不要添加\"根据建议\"等元话语\n\n");
            prompt.append("请直接输出优化后的大纲：\n");
            
            // 调用AI生成优化后的大纲
            String optimizedOutline = aiService.callAI("OUTLINE_OPTIMIZER", prompt.toString());
            
            // 更新数据库
            volume.setContentOutline(optimizedOutline);
            volume.setLastModifiedByAi(java.time.LocalDateTime.now());
            volumeMapper.updateById(volume);
            
            logger.info("✅ 卷大纲优化完成");
            return optimizedOutline;
            
        } catch (Exception e) {
            logger.error("❌ 卷大纲优化失败", e);
            throw new RuntimeException("卷大纲优化失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户需求修改卷蓝图（流式，考虑前后卷上下文）
     * 
     * @param volumeId 要修改的卷ID
     * @param userRequirement 用户修改需求
     * @param aiConfig AI配置
     * @param chunkConsumer 流式内容消费者
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void modifyVolumeBlueprintWithContext(Long volumeId, String userRequirement, AIConfigRequest aiConfig, java.util.function.Consumer<String> chunkConsumer) {
        logger.info("🔧 开始修改卷 {} 的蓝图（带上下文）", volumeId);
        
        try {
            // 验证AI配置
            if (aiConfig == null || !aiConfig.isValid()) {
                throw new RuntimeException("AI配置无效，请先在设置页面配置AI服务");
            }
            
            // 获取当前卷信息
            NovelVolume currentVolume = volumeMapper.selectById(volumeId);
            if (currentVolume == null) {
                throw new RuntimeException("卷不存在: " + volumeId);
            }
            
            if (currentVolume.getContentOutline() == null || currentVolume.getContentOutline().trim().isEmpty()) {
                throw new RuntimeException("该卷尚未生成蓝图，无法修改");
            }
            
            // 获取小说信息
            Novel novel = novelService.getNovelById(currentVolume.getNovelId());
            if (novel == null) {
                throw new RuntimeException("小说不存在: " + currentVolume.getNovelId());
            }
            
            // 获取超级大纲
            NovelOutline superOutline = outlineRepository.findByNovelIdAndStatus(
                    novel.getId(), 
                    NovelOutline.OutlineStatus.CONFIRMED
            ).orElse(null);
            
            if (superOutline == null || superOutline.getPlotStructure() == null || superOutline.getPlotStructure().isEmpty()) {
                throw new RuntimeException("小说尚未生成或确认超级大纲");
            }
            
            // 获取所有卷（按卷号排序）
            List<NovelVolume> allVolumes = volumeMapper.selectByNovelId(currentVolume.getNovelId());
            allVolumes.sort((v1, v2) -> Integer.compare(v1.getVolumeNumber(), v2.getVolumeNumber()));
            
            // 查找前一卷和后一卷
            NovelVolume previousVolume = null;
            NovelVolume nextVolume = null;
            
            for (int i = 0; i < allVolumes.size(); i++) {
                if (allVolumes.get(i).getId().equals(volumeId)) {
                    if (i > 0) {
                        previousVolume = allVolumes.get(i - 1);
                    }
                    if (i < allVolumes.size() - 1) {
                        nextVolume = allVolumes.get(i + 1);
                    }
                    break;
                }
            }
            
            // 构建提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是顶级网文总编，现在需要根据用户需求修改卷蓝图。你必须确保修改后的内容与前后卷保持一致，避免出现跳跃、矛盾或不连贯的问题。\n\n");
            
            prompt.append("# 核心原则\n");
            prompt.append("**连贯性第一**：修改时必须考虑前后卷的情节走向、角色状态、世界观设定，确保无缝衔接。\n");
            prompt.append("**保持整体框架**：只针对用户提出的具体需求进行修改，不要擅自改动其他部分。\n");
            prompt.append("**尊重设定**：严格遵守超级大纲和前后卷已建立的设定、伏笔、角色关系。\n\n");
            
            prompt.append("# 小说信息\n");
            prompt.append("**标题**：").append(novel.getTitle()).append("\n");
            prompt.append("**类型**：").append(novel.getGenre()).append("\n");
            if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
                prompt.append("**构思**：").append(novel.getDescription()).append("\n");
            }
            prompt.append("**全书大纲**：\n").append(superOutline.getPlotStructure()).append("\n\n");
            
            // 添加前一卷信息
            if (previousVolume != null) {
                prompt.append("# 前一卷信息（第").append(previousVolume.getVolumeNumber()).append("卷）\n");
                prompt.append("**卷名**：").append(previousVolume.getTitle()).append("\n");
                prompt.append("**主题**：").append(previousVolume.getTheme()).append("\n");
                prompt.append("**简述**：").append(previousVolume.getDescription()).append("\n");
                if (previousVolume.getContentOutline() != null && !previousVolume.getContentOutline().isEmpty()) {
                    String prevOutline = previousVolume.getContentOutline();
                    // 提取卷末状态相关信息（取最后1000字符）
                    if (prevOutline.length() > 1000) {
                        prevOutline = "..." + prevOutline.substring(prevOutline.length() - 1000);
                    }
                    prompt.append("**前一卷末尾状态（参考）**：\n").append(prevOutline).append("\n");
                }
                prompt.append("\n");
            } else {
                prompt.append("# 前一卷信息\n");
                prompt.append("本卷是第一卷，没有前置卷。\n\n");
            }
            
            // 当前卷信息
            prompt.append("# 当前卷信息（第").append(currentVolume.getVolumeNumber()).append("卷）- 需要修改的卷\n");
            prompt.append("**卷名**：").append(currentVolume.getTitle()).append("\n");
            prompt.append("**主题**：").append(currentVolume.getTheme()).append("\n");
            prompt.append("**简述**：").append(currentVolume.getDescription()).append("\n");
            if (currentVolume.getChapterStart() != null && currentVolume.getChapterEnd() != null) {
                prompt.append("**章节范围**：第 ").append(currentVolume.getChapterStart()).append("-").append(currentVolume.getChapterEnd()).append(" 章\n");
            }
            prompt.append("**当前蓝图内容**：\n").append(currentVolume.getContentOutline()).append("\n\n");
            
            // 添加后一卷信息
            if (nextVolume != null) {
                prompt.append("# 后一卷信息（第").append(nextVolume.getVolumeNumber()).append("卷）\n");
                prompt.append("**卷名**：").append(nextVolume.getTitle()).append("\n");
                prompt.append("**主题**：").append(nextVolume.getTheme()).append("\n");
                prompt.append("**简述**：").append(nextVolume.getDescription()).append("\n");
                if (nextVolume.getContentOutline() != null && !nextVolume.getContentOutline().isEmpty()) {
                    String nextOutline = nextVolume.getContentOutline();
                    // 提取开头相关信息（取前1000字符）
                    if (nextOutline.length() > 1000) {
                        nextOutline = nextOutline.substring(0, 1000) + "...";
                    }
                    prompt.append("**后一卷开头状态（参考）**：\n").append(nextOutline).append("\n");
                }
                prompt.append("\n");
            } else {
                prompt.append("# 后一卷信息\n");
                prompt.append("本卷是最后一卷，没有后续卷。\n\n");
            }
            
            // 用户修改需求
            prompt.append("# 用户修改需求\n");
            prompt.append(userRequirement).append("\n\n");
            
            // 修改要求
            prompt.append("# 修改要求\n");
            prompt.append("1. **针对性修改**：只修改用户要求修改的部分，保持其他部分不变\n");
            prompt.append("2. **前后衔接**：确保修改后的内容能够承接前一卷的结尾状态，并为后一卷做好铺垫\n");
            prompt.append("3. **角色状态连续**：主角和关键角色的实力、地位、心态变化必须符合前后卷的设定\n");
            prompt.append("4. **伏笔对齐**：如果前一卷埋下伏笔，本卷要延续；如果本卷为后一卷埋伏笔，修改后仍要保留\n");
            prompt.append("5. **冲突升级合理**：修改后的冲突强度要在前后卷之间形成合理的梯度\n");
            prompt.append("6. **保持结构**：仍然按照原有的九个部分输出（核心定位、成长轨迹、冲突对手、爽点体系、开放事件池、关键里程碑、支线节奏、伏笔管理、卷末状态）\n");
            prompt.append("7. **直接输出**：只输出修改后的完整卷蓝图，不要添加\"根据您的要求\"等元话语\n\n");
            
            prompt.append("现在，请根据用户需求修改当前卷的蓝图，确保与前后卷无缝衔接：\n");
            
            logger.info("📝 [流式修改] 调用AI修改卷蓝图，提示词长度: {}", prompt.length());
            
            // 使用流式AI调用
            StringBuilder accumulated = new StringBuilder();
            
            aiWritingService.streamGenerateContent(prompt.toString(), "volume_blueprint_modification", aiConfig, chunk -> {
                try {
                    // 累加内容
                    accumulated.append(chunk);
                    
                    // 实时更新数据库
                    currentVolume.setContentOutline(accumulated.toString());
                    currentVolume.setUpdatedAt(LocalDateTime.now());
                    currentVolume.setLastModifiedByAi(LocalDateTime.now());
                    volumeMapper.updateById(currentVolume);
                    
                    // 回调给SSE消费者
                    if (chunkConsumer != null) {
                        chunkConsumer.accept(chunk);
                    }
                } catch (Exception e) {
                    logger.error("处理流式内容块失败: {}", e.getMessage(), e);
                    throw new RuntimeException("处理流式内容块失败: " + e.getMessage());
                }
            });
            
            logger.info("✅ [流式修改] 卷 {} 蓝图修改完成，总长度: {}", volumeId, accumulated.length());
            
        } catch (Exception e) {
            logger.error("❌ [流式修改] 修改卷 {} 蓝图失败", volumeId, e);
            throw new RuntimeException("修改卷蓝图失败: " + e.getMessage(), e);
        }
    }
}
