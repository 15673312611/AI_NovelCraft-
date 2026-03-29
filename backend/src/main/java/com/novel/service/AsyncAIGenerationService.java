package com.novel.service;

import com.novel.common.security.AuthUtils;
import com.novel.common.security.SecurityContextTaskWrapper;
import com.novel.domain.entity.Novel;
import com.novel.domain.entity.NovelVolume;
import com.novel.dto.AIConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 异步多轮AI生成服务
 * 解决AI单次生成内容有限的问题，通过多轮异步生成提供更详细的内容
 */
@Service
public class AsyncAIGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncAIGenerationService.class);

    @Autowired
    private NovelCraftAIService aiService;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private VolumeService volumeService;

    @Autowired
    private AITaskService aiTaskService;

    // 用于实际落库更新卷内容
    @Autowired
    private com.novel.mapper.NovelVolumeMapper novelVolumeMapper;

    // 用于更新小说创作阶段
    @Autowired
    private NovelService novelService;
    
    // 用于获取大纲
    @Autowired
    private com.novel.repository.NovelOutlineRepository outlineRepository;
    
    /**
     * 使用AI配置调用AI接口
     */
    private String callAIWithConfig(String prompt, AIConfigRequest aiConfig) throws Exception {
        if (aiWritingService != null) {
            AIConfigRequest requestConfig = new AIConfigRequest();
            requestConfig.setProvider(aiConfig.getProvider());
            requestConfig.setApiKey(aiConfig.getApiKey());
            requestConfig.setModel(aiConfig.getModel());
            requestConfig.setBaseUrl(aiConfig.getBaseUrl());
            requestConfig.setTemperature(0.8D);
            return aiWritingService.generateContent(prompt, "volume_outline_generation", requestConfig);
        }

        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();
        
        // 构建请求体
        java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 0.8);
        
        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
        java.util.Map<String, String> message = new java.util.HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        
        // 调用AI接口
        String url = aiConfig.getApiUrl();
        
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = 
            new org.springframework.http.HttpEntity<>(requestBody, headers);
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> response = restTemplate.exchange(
            url,
            org.springframework.http.HttpMethod.POST,
            entity,
            java.util.Map.class
        ).getBody();
        
        if (response == null) {
            throw new Exception("AI返回响应为空");
        }
        
        // 解析响应
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> choices = 
            (java.util.List<java.util.Map<String, Object>>) response.get("choices");
        
        if (choices == null || choices.isEmpty()) {
            throw new Exception("AI返回结果为空");
        }
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> firstChoice = choices.get(0);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> messageObj = (java.util.Map<String, Object>) firstChoice.get("message");
        String content = (String) messageObj.get("content");
        
        if (content == null || content.trim().isEmpty()) {
            throw new Exception("AI处理后的内容为空");
        }
        
        return content.trim();
    }

    /**
     * 异步生成卷大纲 - 三轮生成
     * 第一轮：基础框架
     * 第二轮：详细章节
     * 第三轮：关键要素完善
     */
    @Async("novelTaskExecutor")
    public CompletableFuture<Map<String, Object>> generateVolumeOutlineAsync(Long volumeId, String userAdvice, AIConfigRequest aiConfig) {
        logger.info("🚀 开始异步生成卷 {} 的卷蓝图（单次提示词）", volumeId);
        
        // 验证AI配置
        if (aiConfig == null || !aiConfig.isValid()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "AI配置无效");
            return CompletableFuture.completedFuture(errorResult);
        }

        try {
            // 获取卷信息
            Map<String, Object> volumeDetail = volumeService.getVolumeDetail(volumeId);
            NovelVolume volume = (NovelVolume) volumeDetail.get("volume");
            if (volume == null) throw new RuntimeException("卷不存在: " + volumeId);
            
            // 获取小说和超级大纲
            Novel novel = novelService.getNovelById(volume.getNovelId());
            if (novel == null) throw new RuntimeException("小说不存在: " + volume.getNovelId());
            
            com.novel.domain.entity.NovelOutline superOutline = null;
            try {
                superOutline = outlineRepository.findByNovelIdAndStatus(
                    novel.getId(), 
                    com.novel.domain.entity.NovelOutline.OutlineStatus.CONFIRMED
                ).orElse(null);
            } catch (Exception e) {
                logger.warn("获取超级大纲失败: {}", e.getMessage());
            }

            // 计算章节数与每章字数
            int chapterCount = volume.getChapterEnd() - volume.getChapterStart() + 1;
            int totalWords = volume.getEstimatedWordCount() != null ? volume.getEstimatedWordCount() : 0;
            int avgWordsPerChapter = chapterCount > 0 && totalWords > 0 ? Math.round((float) totalWords / chapterCount) : 3000;
            if (avgWordsPerChapter < 2000) avgWordsPerChapter = 2000;
            if (avgWordsPerChapter > 5000) avgWordsPerChapter = 5000;

            // 卷蓝图提示词（不锁剧情，信息密度高，分段文本）
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是顶级网文总编，专门设计\"让读者欲罢不能\"的卷蓝图。你的任务是规划大方向和关键节点，但绝不锁死具体剧情。\n\n")
                  .append("# 核心理念\n")
                  .append("**蓝图不是剧本**：只给路线图和资源包，不写执行细节。让AI写作时有发挥空间，能根据实际情况灵活调整。\n")
                  .append("**冲突驱动一切**：每个阶段都要有\"主角想要什么→遇到什么阻碍→付出什么代价→得到什么结果\"的拉扯。\n")
                  .append("**爽点密度保证**：确保每隔几章就有一个爆点，让读者停不下来。\n\n")
                  .append("# 小说信息\n")
                  .append("**标题**：").append(novel.getTitle()).append("\n");
            if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
                prompt.append("**构思**：").append(novel.getDescription()).append("\n");
            }
            if (superOutline != null && superOutline.getPlotStructure() != null && !superOutline.getPlotStructure().isEmpty()) {
                prompt.append("**全书大纲**：\n").append(superOutline.getPlotStructure()).append("\n\n");
            } else {
                prompt.append("\n");
            }
            prompt.append("# 本卷信息\n")
                  .append("**卷标题**：").append(volume.getTitle() != null ? volume.getTitle() : ("第" + (volume.getVolumeNumber() == null ? 1 : volume.getVolumeNumber()) + "卷")).append("\n")
                  .append("**卷序号**：第").append(volume.getVolumeNumber() != null ? volume.getVolumeNumber() : 1).append("卷\n")
                  .append("**章节范围**：第").append(volume.getChapterStart()).append("-").append(volume.getChapterEnd()).append("章（共").append(chapterCount).append("章）\n")
                  .append("**预估总字数**：").append(totalWords).append(" 字\n")
                  .append("**平均每章字数**：").append(avgWordsPerChapter).append(" 字\n");
            if (volume.getTheme() != null && !volume.getTheme().isEmpty()) {
                prompt.append("**主题**：").append(volume.getTheme()).append("\n");
            }
            if (volume.getDescription() != null && !volume.getDescription().isEmpty()) {
                prompt.append("**简述**：").append(volume.getDescription()).append("\n");
            }
            if (userAdvice != null && !userAdvice.trim().isEmpty()) {
                prompt.append("**用户建议**：").append(userAdvice.trim()).append("\n");
            }
            prompt.append("\n【对齐约束】\n")
                  .append("- 必须承接超级大纲、小说简介与本卷信息，保留原有核心冲突、人物定位、世界设定，禁止擅自改写。\n")
                  .append("- 任何新增情节需解释其如何强化原有主题或冲突张力，保持因果闭环。\n")
                  .append("- 超级大纲描述的关键线索/伏笔要在本卷中延续或进一步推进。\n\n")
                  .append("【读者体验目标】\n")
                  .append("- 设计持续升级的爽点体系，让期待-兑现循环与主角成长同步放大。\n")
                  .append("- 打造清晰的情绪曲线与高潮节奏，让读者在紧张—释放之间感到投入与惊喜。\n")
                  .append("- 对准目标受众偏好强化卖点（成长、悬念、情感、脑洞等），提升卷的市场吸引力。\n\n")
                  .append("# 输出要求\n\n")
                  
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
                  .append("**基础爽点**（每2-3章）：日常小爽的场景类型与触发条件。列出3-5个典型场景方向。\n")
                  .append("**进阶爽点**（每5-10章）：中等爆发的事件类型与实现方式。列出2-3个关键节点方向。\n")
                  .append("**高潮爽点**（卷末或重大转折）：终极爆发的时机与效果。描述1-2个巅峰时刻的设计思路。\n\n")
                  
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

            // 直接调用AI接口，使用前端传递的AI配置
            String aiResponse = callAIWithConfig(prompt.toString(), aiConfig);

            Map<String, Object> result = new HashMap<>();
            result.put("rawResponse", aiResponse);

            logger.info("✅ 卷 {} 异步卷蓝图生成完成（单次提示词）", volumeId);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            logger.error("❌ 卷 {} 异步生成失败: {}", volumeId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", true);
            errorResult.put("message", e.getMessage());
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    /**
     * 提交卷大纲生成任务
     *
     * @param aiTask AI任务对象
     * @param volumeId 卷ID
     * @param userAdvice 用户建议
     * @return 任务ID
     */
    public Long submitVolumeOutlineTask(com.novel.domain.entity.AITask aiTask, Long volumeId, String userAdvice) {
        logger.info("📋 提交卷 {} 大纲生成任务到异步队列", volumeId);

        try {
            // 从AITask的parameters中提取AI配置
            AIConfigRequest aiConfig = null;
            try {
                String parametersJson = aiTask.getParameters();
                if (parametersJson != null && !parametersJson.isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = mapper.readValue(parametersJson, Map.class);
                    if (params.get("aiConfig") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> aiConfigMap = (Map<String, String>) params.get("aiConfig");
                        aiConfig = new AIConfigRequest();
                        aiConfig.setProvider(aiConfigMap.get("provider"));
                        aiConfig.setApiKey(aiConfigMap.get("apiKey"));
                        aiConfig.setModel(aiConfigMap.get("model"));
                        aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
                    }
                }
            } catch (Exception e) {
                logger.warn("无法从任务参数中提取AI配置: {}", e.getMessage());
            }
            
            final AIConfigRequest finalAiConfig = aiConfig;
            
            // 使用 AITaskService 创建任务
            if (aiTask.getUserId() == null) {
                aiTask.setUserId(AuthUtils.getCurrentUserId());
            }
        com.novel.dto.AITaskDto taskDto = aiTaskService.createTask(aiTask);
            Long taskId = Long.valueOf(taskDto.getId());

            // 启动异步生成任务
            CompletableFuture.supplyAsync(SecurityContextTaskWrapper.wrapSupplier(() -> {
                try {
                    logger.info("🤖 开始异步生成卷 {} 的详细大纲", volumeId);

                    // 更新任务状态为运行中
                    aiTaskService.startTask(taskId);
                    aiTaskService.updateTaskProgress(taskId, 10, "RUNNING", "准备生成卷大纲");

                    // 调用单次提示词的异步生成方法
                    Map<String, Object> result = generateVolumeOutlineAsync(volumeId, userAdvice, finalAiConfig).get();

                    // 更新任务状态为完成
                    aiTaskService.updateTaskProgress(taskId, 100, "COMPLETED", "卷大纲生成完成");

                    // 构建结果
                    Map<String, Object> output = new HashMap<>();
                    output.put("outline", result);
                    output.put("volumeId", volumeId);
                    output.put("userAdvice", userAdvice);

                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String outputJson = mapper.writeValueAsString(output);
                    aiTaskService.completeTask(taskId, outputJson);

                    // 更新卷的大纲内容
                    updateVolumeWithGeneratedOutline(volumeId, result);
                    
                    // 清理并发控制标记（改为传递volumeId）
                    try {
                        volumeService.clearGeneratingFlag(volumeId);
                    } catch (Exception clearEx) {
                        logger.warn("清理生成标记失败: {}", clearEx.getMessage());
                    }

                    logger.info("✅ 卷 {} 异步大纲生成完成", volumeId);
                    return result;
                } catch (Exception e) {
                    logger.error("❌ 卷 {} 异步大纲生成失败: {}", volumeId, e.getMessage(), e);
                    aiTaskService.failTask(taskId, "生成失败: " + e.getMessage());
                    
                    // 失败时也要清理并发控制标记（改为传递volumeId）
                    try {
                        volumeService.clearGeneratingFlag(volumeId);
                    } catch (Exception clearEx) {
                        logger.warn("清理生成标记失败: {}", clearEx.getMessage());
                    }
                    
                    throw new RuntimeException(e.getMessage());
                }
            }));

            logger.info("✅ 卷 {} 大纲生成任务已提交，任务ID: {}", volumeId, taskId);
            return taskId;

        } catch (Exception e) {
            logger.error("❌ 提交卷大纲生成任务失败: {}", e.getMessage(), e);
            throw new RuntimeException("提交异步任务失败: " + e.getMessage());
        }
    }

    /**
     * 将生成的大纲更新到卷中
     */
    private void updateVolumeWithGeneratedOutline(Long volumeId, Map<String, Object> outlineResult) {
        try {
            logger.info("💾 更新卷 {} 的生成大纲", volumeId);

            // 优先使用单次调用返回的原始文本，否则回退到Map转文本
            String outlineText = null;
            if (outlineResult != null) {
                Object raw = outlineResult.get("rawResponse");
                if (raw instanceof String && !((String) raw).trim().isEmpty()) {
                    outlineText = (String) raw;
                }
            }
            if (outlineText == null) {
                outlineText = "大纲生成失败或内容为空，请稍后重试";
            }

            // 更新卷的大纲内容并持久化
            Map<String, Object> volumeDetail = volumeService.getVolumeDetail(volumeId);
            NovelVolume volume = (NovelVolume) volumeDetail.get("volume");
            if (volume != null) {
                volume.setContentOutline(outlineText);
                volume.setLastModifiedByAi(LocalDateTime.now());
                volume.setStatus(NovelVolume.VolumeStatus.PLANNED);
                novelVolumeMapper.updateById(volume);
                logger.info("✅ 卷 {} 大纲已更新到数据库", volumeId);

                // 同步更新小说创作阶段为 详细大纲已生成
                try {
                    novelService.updateCreationStage(volume.getNovelId(), Novel.CreationStage.DETAILED_OUTLINE_GENERATED);
                } catch (Exception ignore) {
                    logger.warn("⚠️ 更新小说创作阶段失败：novelId={}", volume.getNovelId());
                }
            }
        } catch (Exception e) {
            logger.error("❌ 更新卷大纲失败: {}", e.getMessage(), e);
        }
    }

}
