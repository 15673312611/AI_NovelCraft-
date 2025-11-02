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
                  .append("你是顶级网文总编，专门设计\"让读者欲罢不能\"的卷蓝图。你的任务是规划大方向和关键节点，但绝不锁死具体剧情。\n\n")
                  .append("# 核心理念\n")
                  .append("**蓝图不是剧本**：只给路线图和资源包，不写执行细节。让AI写作时有发挥空间，能根据实际情况灵活调整。\n")
                  .append("**冲突驱动一切**：每个阶段都要有\"主角想要什么→遇到什么阻碍→付出什么代价→得到什么结果\"的拉扯。\n")
                  .append("**爽点密度保证**：确保每隔几章就有一个爆点，让读者停不下来。\n\n")
                  .append("# 小说信息\n")
                  .append("**标题**：").append(novel.getTitle()).append("\n")
                  .append("**类型**：").append(novel.getGenre()).append("\n");
            if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
                prompt.append("**构思**：").append(novel.getDescription()).append("\n");
            }
            prompt.append("**超级大纲**：\n").append(superOutline.getPlotStructure()).append("\n\n")
                  .append("# 本卷信息\n")
                  .append("**卷名**：").append(volume.getTitle()).append("\n")
                  .append("**主题**：").append(volume.getTheme()).append("\n")
                  .append("**简述**：").append(volume.getDescription()).append("\n");
            if (volume.getChapterStart() != null && volume.getChapterEnd() != null) {
                prompt.append("**章节范围**：第 ").append(volume.getChapterStart()).append("-").append(volume.getChapterEnd()).append(" 章\n");
            }
            if (volume.getEstimatedWordCount() != null && volume.getEstimatedWordCount() > 0) {
                prompt.append("**目标字数**：").append(volume.getEstimatedWordCount()).append(" 字\n");
            }
            if (userAdvice != null && !userAdvice.trim().isEmpty()) {
                prompt.append("**作者补充**：").append(userAdvice.trim()).append("\n");
            }
            prompt.append("\n# 输出要求\n\n")
                  
                  .append("## 一、本卷核心定位\n")
                  .append("用2-3句话说清楚：这一卷要解决什么问题？主角要达成什么目标？读者能爽到什么？\n\n")
                  
                  .append("## 二、主角成长轨迹\n")
                  .append("**起点状态**：本卷开始时，主角的实力/地位/资源/心态是什么样？\n")
                  .append("**终点状态**：本卷结束时，主角会成长到什么程度？（要具体，比如\"从练气三层突破到筑基期\"\"从小保安变成安保部长\"）\n")
                  .append("**成长路径**：大致分几个阶段？每个阶段有什么标志性突破？\n\n")
                  
                  .append("## 三、核心冲突与对手\n")
                  .append("**主要对手**：谁在跟主角作对？他们的目标是什么？实力如何？\n")
                  .append("**冲突升级路线**：矛盾怎么一步步激化？从小摩擦到大爆发的节奏是什么？\n")
                  .append("**压力来源**：除了对手，还有什么在逼主角？（时间限制、资源短缺、规则限制等）\n")
                  .append("**代价边界**：主角为了达成目标，最多能付出什么代价？什么是绝对不能失去的？\n\n")
                  
                  .append("## 四、爽点体系设计\n")
                  .append("**基础爽点**（每2-3章）：日常小爽，比如打脸路人、小赚一笔、学会新技能。列出3-5个典型场景的触发条件。\n")
                  .append("**进阶爽点**（每5-10章）：中等爆发，比如击败小BOSS、获得重要资源、身份提升。列出2-3个关键节点。\n")
                  .append("**高潮爽点**（卷末或重大转折）：终极爆发，比如揭露隐藏身份、逆转绝境、震撼全场。描述1-2个巅峰时刻。\n\n")
                  
                  .append("## 五、开放事件池（≥8个）\n")
                  .append("提供一些\"可选事件包\"，每个事件包括：\n")
                  .append("- **事件名**：简短标题\n")
                  .append("- **触发条件**：什么情况下可以用这个事件？\n")
                  .append("- **核心矛盾**：这个事件的主要冲突是什么？\n")
                  .append("- **可能结果**：成功/失败/意外，各会导向什么？\n")
                  .append("- **爽点类型**：这个事件能给读者什么爽感？（打脸/逆袭/获得/成长/揭秘等）\n\n")
                  .append("**注意**：这些事件不规定顺序，AI写作时可以根据剧情需要灵活选用和组合。\n\n")
                  
                  .append("## 六、关键里程碑（3-5个）\n")
                  .append("本卷必须经过的几个关键节点，每个包括：\n")
                  .append("- **里程碑名称**：这个节点叫什么？\n")
                  .append("- **达成条件**：什么情况下算达成？\n")
                  .append("- **影响范围**：达成后会改变什么？（主角能力、势力格局、剧情走向等）\n")
                  .append("- **悬念钩子**：这个节点会引出什么新问题或新目标？\n\n")
                  
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
                  .append("**已解决问题**：本卷的核心矛盾解决了吗？怎么解决的？\n")
                  .append("**新增悬念**：卷末要留什么钩子引出下一卷？（新危机/新目标/新谜团）\n")
                  .append("**风险结转**：有什么隐患或代价会延续到下一卷？\n\n")
                  
                  .append("# 写作风格要求\n")
                  .append("1. **人话表达**：别用术语和套话，就像老编辑跟作者聊天一样自然\n")
                  .append("2. **具体可操作**：别说\"主角变强了\"，要说\"从练气三层突破到筑基期，获得御剑术\"\n")
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


    /**
     * 开始卷写作会话
     * 
     * 注意：前端已不再使用 memoryBank，所有上下文数据由后端在写作时直接从数据库查询
     * 
     * @param volumeId 卷ID
     * @return 写作会话数据（包含volume/novel/aiGuidance等，不再包含memoryBank）
     */
    public Map<String, Object> startVolumeWriting(Long volumeId) {
        logger.info("✍️ 开始卷 {} 的写作会话", volumeId);
        
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在");
        }
        
        // 检查是否有大纲，没有则提示先生成大纲
        if (volume.getContentOutline() == null || volume.getContentOutline().trim().isEmpty()) {
            throw new RuntimeException("缺少卷大纲，请先生成卷大纲后再开始写作");
        }
        
        // 更新卷状态为进行中
        volume.setStatus(VolumeStatus.IN_PROGRESS);
        volumeMapper.updateById(volume);
        
        Novel novel = novelService.getById(volume.getNovelId());
        
        // 创建写作会话（不再包含 memoryBank，前端也不再使用）
        Map<String, Object> writingSession = new HashMap<>();
        writingSession.put("volumeId", volumeId);
        writingSession.put("volume", volume);
        writingSession.put("novel", novel);
        writingSession.put("currentPosition", 0);
        writingSession.put("sessionStartTime", LocalDateTime.now());
        
        // 生成初始AI指导
        Map<String, Object> initialGuidance = generateWritingGuidance(novel, volume, null, "开始写作");
        writingSession.put("aiGuidance", initialGuidance);
        
        logger.info("✅ 卷 {} 写作会话创建成功", volumeId);
        return writingSession;
    }

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

    /**
     * 生成下一步写作指导
     * 
     * @param volumeId 卷ID
     * @param currentContent 当前内容
     * @param userInput 用户输入
     * @return AI指导建议
     */
    public Map<String, Object> generateNextStepGuidance(Long volumeId, String currentContent, String userInput) {
        logger.info("💡 为卷 {} 生成下一步指导", volumeId);
        
        NovelVolume volume = volumeMapper.selectById(volumeId);
        if (volume == null) {
            throw new RuntimeException("卷不存在");
        }
        
        Novel novel = novelService.getById(volume.getNovelId());
        
        return generateWritingGuidance(novel, volume, currentContent, userInput);
    }

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
        logger.info("📝 基于传统大纲生成卷规划");
        
        String volumePlanPrompt = String.format(
            "你是一位资深网文结构规划师，负责将‘全书大纲（未分卷）’拆解为卷级框架。目标是得到‘卷骨架’，供后续卷蓝图与滚动节拍使用，严禁输出逐章剧情。\n\n" +
            "【小说信息】\n" +
            "- 标题：%s\n" +
            "- 类型：%s\n" +
            "- 目标卷数（必须严格遵守）：%d\n\n" +
            
            "【拆卷原则】\n" +
            "- 不锁剧情：不固定事件顺序与执行路径，不写对话与过程，不出现‘第X章’\n" +
            "- 原则对齐：承接全书大纲的冲突升级、舞台升级、长线伏笔；不得引入破坏规则的新设定\n" +
            "- 阶段划分：以主角状态变更/地图升级/目标转折/格局变化拆为 %d 卷，每卷主题鲜明\n" +
            "- 结构完整：每卷具备‘开场承接→中段推进（≥2个中强度节点）→卷末高潮→下一卷钩子’\n" +
            "- 节奏均衡：详细细化留给后续‘卷蓝图’与‘滚动节拍’\n\n" +
            
            "【输出要求（必须严格遵守）】\n" +
            "1. 必须生成恰好 %d 个卷的规划\n" +
            "2. 只输出一个 JSON 数组，数组长度必须为 %d，不要任何其他说明/表格/注释/Markdown\n" +
            "3. 数组中每个元素仅包含4个字段：\n" +
            "   - title（卷标题，简洁有意象）\n" +
            "   - theme（核心主题/议题，短语）\n" +
            "   - description（卷描述，150-200字，说明本卷核心目标、压力来源、阶段特征）\n" +
            "   - contentOutline（卷骨架摘要，120-200字；仅含：本卷核心目标、2-3个阶段性里程碑（名称级）、卷末钩子；严禁逐章与过程细节）\n" +
            "4. 字段名必须为英文，且不得包含多余字段\n\n" +
            "【全书大纲（未分卷文本）】\n%s\n",
            novel.getTitle(),
            novel.getGenre(),
            volumeCount,
            volumeCount,
            volumeCount,
            volumeCount,
            (outline.getPlotStructure() != null && !outline.getPlotStructure().trim().isEmpty()) ? outline.getPlotStructure() : (outline.getBasicIdea() == null ? "" : outline.getBasicIdea())
        );

        try {
            logger.info("🤖 调用AI生成卷规划，提示词长度: {}", volumePlanPrompt.length());
            
            long startTime = System.currentTimeMillis();
            String response = aiService.callAI("VOLUME_PLANNER", volumePlanPrompt);
            long endTime = System.currentTimeMillis();
            
            logger.info("⏱️ AI服务响应时间: {}ms", (endTime - startTime));
            
            if (response != null && response.length() > 0) {
                List<Map<String, Object>> result = parseVolumePlansFromAI(response, volumeCount);
                logger.info("✅ 基于传统大纲成功解析出{}个卷规划", result.size());
                return result;
            } else {
                logger.error("❌ AI服务返回空响应！");
                throw new RuntimeException("AI服务返回空响应，无法生成卷规划");
            }
            
        } catch (Exception e) {
            logger.error("❌ 基于传统大纲生成卷规划失败: {}", e.getMessage(), e);
            logger.warn("⚠️ 使用简化卷规划");
            return generateSimplifiedVolumePlans(novel, outline, volumeCount);
        }
    }
    
    /**
     * 基于传统大纲生成卷规划（带AI配置）
     */
    private List<Map<String, Object>> generateVolumePlansFromOutline(Novel novel, 
        com.novel.domain.entity.NovelOutline outline, Integer volumeCount, com.novel.dto.AIConfigRequest aiConfig) {
        logger.info("📝 基于传统大纲生成卷规划（使用AI配置）");
        
        String volumePlanPrompt = String.format(
            "你是一位资深网文结构规划师，负责将‘全书大纲（未分卷）’拆解为卷级框架。目标是得到‘卷骨架’，供后续卷蓝图与滚动节拍使用，严禁输出逐章剧情。\n\n" +
            "【小说信息】\n" +
            "- 标题：%s\n" +
            "- 类型：%s\n" +
            "- 目标卷数（必须严格遵守）：%d\n\n" +
            
            "【拆卷原则】\n" +
            "- 不锁剧情：不固定事件顺序与执行路径，不写对话与过程，不出现‘第X章’\n" +
            "- 原则对齐：承接全书大纲的冲突升级、舞台升级、长线伏笔；不得引入破坏规则的新设定\n" +
            "- 阶段划分：以主角状态变更/地图升级/目标转折/格局变化拆为 %d 卷，每卷主题鲜明\n" +
            "- 结构完整：每卷具备‘开场承接→中段推进（≥2个中强度节点）→卷末高潮→下一卷钩子’\n" +
            "- 节奏均衡：详细细化留给后续‘卷蓝图’与‘滚动节拍’\n\n" +
            
            "【输出要求（必须严格遵守）】\n" +
            "1. 必须生成恰好 %d 个卷的规划\n" +
            "2. 只输出一个 JSON 数组，数组长度必须为 %d，不要任何其他说明/表格/注释/Markdown\n" +
            "3. 数组中每个元素仅包含4个字段：\n" +
            "   - title（卷标题，简洁有意象）\n" +
            "   - theme（核心主题/议题，短语）\n" +
            "   - description（卷描述，150-200字，说明本卷核心目标、压力来源、阶段特征）\n" +
            "   - contentOutline（卷骨架摘要，120-200字；仅含：本卷核心目标、2-3个阶段性里程碑（名称级）、卷末钩子；严禁逐章与过程细节）\n" +
            "4. 字段名必须为英文，且不得包含多余字段\n\n" +
            "【全书大纲（未分卷文本）】\n%s\n",
            novel.getTitle(),
            novel.getGenre(),
            volumeCount,
            volumeCount,
            volumeCount,
            volumeCount,
            (outline.getPlotStructure() != null && !outline.getPlotStructure().trim().isEmpty()) ? outline.getPlotStructure() : (outline.getBasicIdea() == null ? "" : outline.getBasicIdea())
        );

        try {
            logger.info("🤖 调用AI生成卷规划（带配置），提示词长度: {}", volumePlanPrompt.length());
            
            long startTime = System.currentTimeMillis();
            // 使用带AI配置的方法
            String response = aiWritingService.generateContent(volumePlanPrompt, "volume_planning", aiConfig);
            long endTime = System.currentTimeMillis();
            
            logger.info("⏱️ AI服务响应时间: {}ms", (endTime - startTime));
            
            if (response != null && response.length() > 0) {
                List<Map<String, Object>> result = parseVolumePlansFromAI(response, volumeCount);
                logger.info("✅ 基于传统大纲成功解析出{}个卷规划", result.size());
                return result;
            } else {
                logger.error("❌ AI服务返回空响应！");
                throw new RuntimeException("AI服务返回空响应，无法生成卷规划");
            }
            
        } catch (Exception e) {
            logger.error("❌ 基于传统大纲生成卷规划失败（带AI配置）: {}", e.getMessage(), e);
            logger.warn("⚠️ 使用简化卷规划");
            return generateSimplifiedVolumePlans(novel, outline, volumeCount);
        }
    }


    /**
     * 生成写作指导
     */
    private Map<String, Object> generateWritingGuidance(Novel novel, NovelVolume volume, String currentContent, String userInput) {
        String guidancePrompt = String.format(
            "你是【网文写作导师】，为《%s》第%d卷的写作提供指导。\\n\\n" +
            
            "## 卷信息\\n" +
            "- 标题：%s\\n" +
            "- 主题：%s\\n" +
            "- 类型：%s\\n\\n" +
            
            "## 当前状态\\n" +
            "- 已完成内容：%s\\n" +
            "- 用户输入：%s\\n\\n" +
            
            "## 指导要求\\n" +
            "1. 分析当前内容质量（如果有）\\n" +
            "2. 提供3-5个具体的下一步建议\\n" +
            "3. 预测读者可能的反应\\n" +
            "4. 建议下一段的写作重点\\n" +
            "5. 保持%s类网文的特色\\n\\n" +
            
            "## 输出格式\\n" +
            "```json\\n" +
            "{\\n" +
            "  \\\"analysis\\\": {\\n" +
            "    \\\"qualityScore\\\": 8.5,\\n" +
            "    \\\"strengths\\\": [\\\"优点1\\\", \\\"优点2\\\"],\\n" +
            "    \\\"improvements\\\": [\\\"改进点1\\\", \\\"改进点2\\\"]\\n" +
            "  },\\n" +
            "  \\\"suggestions\\\": [\\n" +
            "    {\\\"type\\\": \\\"plot\\\", \\\"content\\\": \\\"具体建议\\\", \\\"priority\\\": \\\"high\\\"},\\n" +
            "    {\\\"type\\\": \\\"character\\\", \\\"content\\\": \\\"具体建议\\\", \\\"priority\\\": \\\"medium\\\"}\\n" +
            "  ],\\n" +
            "  \\\"nextFocus\\\": \\\"下一段重点\\\",\\n" +
            "  \\\"readerReaction\\\": \\\"预期读者反应\\\",\\n" +
            "  \\\"warnings\\\": [\\\"注意事项\\\"]\\n" +
            "}\\n" +
            "```",
            
            novel.getTitle(), volume.getVolumeNumber(),
            volume.getTitle(), volume.getTheme(), novel.getGenre(),
            currentContent != null ? currentContent : "无",
            userInput != null ? userInput : "开始写作",
            novel.getGenre()
        );

        String response = aiService.callAI("WRITING_MENTOR", guidancePrompt);
        return parseWritingGuidanceFromAI(response);
    }

    /**
     * 解析AI返回的卷规划
     */
    private List<Map<String, Object>> parseVolumePlansFromAI(String response, Integer volumeCount) {
        List<Map<String, Object>> plans = new ArrayList<>();
        
        try {
            logger.info("🔍 开始解析AI卷规划响应");
            logger.info("🤖 AI响应长度: {}", response != null ? response.length() : 0);
            
            // 打印原始响应的前500字符用于调试
            if (response != null && response.length() > 0) {
                String preview = response.length() > 500 ? response.substring(0, 500) + "..." : response;
                logger.info("📄 AI原始响应预览: {}", preview);
            } else {
                logger.error("❌ AI响应为空或null！");
                throw new RuntimeException("AI响应为空，无法解析卷规划");
            }
            
            // 尝试解析JSON
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent != null && !jsonContent.trim().isEmpty()) {
                logger.info("✅ 提取到JSON内容，长度: {}", jsonContent.length());
                logger.info("🔍 完整JSON内容（前500字符）: {}", jsonContent.substring(0, Math.min(500, jsonContent.length())));
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                
                List<Map> jsonPlans = null;
                
                // 先尝试直接解析原始JSON
                try {
                    jsonPlans = mapper.readValue(jsonContent, List.class);
                    logger.info("✅ JSON解析成功（原始格式），获得{}个卷规划", jsonPlans.size());
                } catch (Exception e) {
                    logger.warn("⚠️ 原始JSON解析失败: {}", e.getMessage());
                    logger.info("🔧 尝试修复中文引号后重新解析...");
                    
                    // 修复中文引号问题：将中文引号替换为英文引号（作为备用方案）
                    String fixedJson = jsonContent
                        .replace('\u201C', '"')
                        .replace('\u201D', '"')
                        .replace('\u2018', '\'')
                        .replace('\u2019', '\'');
                    
                    logger.info("🔍 修复后的JSON（前500字符）: {}", fixedJson.substring(0, Math.min(500, fixedJson.length())));
                    
                    try {
                        jsonPlans = mapper.readValue(fixedJson, List.class);
                        logger.info("✅ JSON解析成功（修复后），获得{}个卷规划", jsonPlans.size());
                    } catch (Exception e2) {
                        logger.error("❌ 修复后仍然解析失败: {}", e2.getMessage());
                        throw e2;
                    }
                }
                
                if (jsonPlans == null) {
                    throw new RuntimeException("JSON解析失败，未获取到卷规划数据");
                }
                
                for (int i = 0; i < jsonPlans.size(); i++) {
                    Map jsonPlan = jsonPlans.get(i);
                    Map<String, Object> plan = new HashMap<>();
                    
                    String title = (String) jsonPlan.getOrDefault("title", "第" + (i + 1) + "卷");
                    String theme = (String) jsonPlan.getOrDefault("theme", "待定主题");
                    String description = (String) jsonPlan.getOrDefault("description", "待定描述");
                    Object contentOutlineObj = jsonPlan.get("contentOutline");
                    String contentOutline = contentOutlineObj instanceof String ? (String) contentOutlineObj : "";
                    
                    plan.put("title", title);
                    plan.put("theme", theme);
                    plan.put("description", description);
                    plan.put("contentOutline", contentOutline);
                    
                    logger.info("📝 卷{}解析成功: 标题='{}', 主题='{}', 描述='{}'", i + 1, title, theme, description);
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
        }
        
        // 如果解析失败，生成简化规划
        if (plans.isEmpty()) {
            logger.warn("⚠️ AI解析完全失败，使用简化卷规划");
            // 注意：这里无法获取novel和outline对象，因此只能使用基础的默认规划
            plans = generateBasicVolumePlans(volumeCount);
        }
        
        logger.info("🎯 最终返回{}个卷规划", plans.size());
        return plans;
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
                    currentVolume.put("description", "从文本解析的描述" + volumeIndex);
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
                        currentVolume.put("description", cleanTextContent(line));
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
                defaultVolume.put("description", "补充卷描述" + (i + 1));
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
                "你是网文结构规划师。根据以下大纲，为小说《%s》生成 %d 个卷的标题和主题。\n\n" +
                "小说类型：%s\n\n" +
                "大纲内容：\n%s\n\n" +
                "要求：\n" +
                "1. 必须生成恰好 %d 个卷\n" +
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
                    plan.put("description", "第" + (i+1) + "卷：" + info.get("theme"));
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
            plan.put("description", "第" + i + "卷的内容描述");
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
            // 先尝试提取 ```json ... ``` 格式
            String jsonStart = "```json";
            String jsonEnd = "```";
            
            int startIdx = response.indexOf(jsonStart);
            if (startIdx != -1) {
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
            }
            
            logger.warn("⚠️ 未能提取有效的JSON内容");
            
        } catch (Exception e) {
            logger.warn("❌ 提取JSON失败: {}", e.getMessage());
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
            
            // 4. 转换为NovelVolume实体并保存到数据库
            List<NovelVolume> savedVolumes = new ArrayList<>();
            int currentChapter = 1;
            
            // 基于用户填写的目标字数与目标章节数，计算每章字数
            int targetTotalWords = novel.getTotalWordTarget() != null ? novel.getTotalWordTarget() : 0;
            int targetTotalChapters = novel.getTargetTotalChapters() != null && novel.getTargetTotalChapters() > 0 ? novel.getTargetTotalChapters() : 0;
            int avgWordsPerChapter = targetTotalChapters > 0 && targetTotalWords > 0 ? Math.max(500, targetTotalWords / targetTotalChapters) : 1200;
            
            for (int i = 0; i < volumePlans.size(); i++) {
                Map<String, Object> plan = volumePlans.get(i);
                
                NovelVolume volume = new NovelVolume();
                volume.setNovelId(novelId);
                volume.setVolumeNumber(i + 1);
                volume.setTitle((String) plan.get("title"));
                volume.setTheme((String) plan.get("theme"));
                volume.setDescription((String) plan.get("description"));
                
                // 重要：不要直接设置 contentOutline，这会导致前端误判为已生成详细大纲
                // contentOutline 应该为空或简短摘要，只有在用户点击"生成详细大纲"后才填充
                String briefOutline = (String) plan.get("description"); // 使用 description 作为简短摘要
                if (briefOutline != null && briefOutline.length() > 50) {
                    briefOutline = briefOutline.substring(0, 50) + "..."; // 确保不超过50字符
                }
                volume.setContentOutline(briefOutline);
                
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
                
                // 保存到数据库
                volumeMapper.insert(volume);
                savedVolumes.add(volume);
                
                logger.info("✅ 卷{}保存成功: ID={}, 标题='{}', 章节范围={}-{}, 预估字数={}", 
                    i + 1, volume.getId(), volume.getTitle(), volume.getChapterStart(), volume.getChapterEnd(), estimatedWords);
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
                    
                    // 更新任务状态为运行中
                    aiTaskService.startTask(taskId);
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
                    aiTaskService.failTask(taskId, "生成失败: " + e.getMessage());
                    throw new RuntimeException(e.getMessage());
                }
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
}