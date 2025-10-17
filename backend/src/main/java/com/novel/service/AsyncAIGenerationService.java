package com.novel.service;

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
    private VolumeService volumeService;

    @Autowired
    private AITaskService aiTaskService;

    // 用于实际落库更新卷内容
    @Autowired
    private com.novel.mapper.NovelVolumeMapper novelVolumeMapper;

    // 用于更新小说创作阶段
    @Autowired
    private NovelService novelService;
    
    /**
     * 使用AI配置调用AI接口
     */
    private String callAIWithConfig(String prompt, AIConfigRequest aiConfig) throws Exception {
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
        logger.info("🚀 开始异步生成卷 {} 的详细大纲（单次提示词）", volumeId);
        
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

            // 计算章节数与每章字数
            int chapterCount = volume.getChapterEnd() - volume.getChapterStart() + 1;
            int totalWords = volume.getEstimatedWordCount() != null ? volume.getEstimatedWordCount() : 0;
            int avgWordsPerChapter = chapterCount > 0 && totalWords > 0 ? Math.round((float) totalWords / chapterCount) : 3000;
            if (avgWordsPerChapter < 2000) avgWordsPerChapter = 2000;
            if (avgWordsPerChapter > 5000) avgWordsPerChapter = 5000;

            // 构建单次提示词（统一在此维护，输出为“完整可执行”的章节级大纲文本）
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一名资深网文卷大纲架构师，请生成一份“可直接用于写作”的卷级详细大纲。严禁泛泛而谈或仅给出提纲。核心要求是：大纲需具备强烈的故事性和可写性，每个描述都能让作者脑中出现具体画面和情节走向。\n\n")
                  .append("【卷基本信息】\n")
                  .append("- 卷标题：").append(volume.getTitle() != null ? volume.getTitle() : ("第" + (volume.getVolumeNumber() == null ? 1 : volume.getVolumeNumber()) + "卷")).append("\n")
                  .append("- 卷序号：第").append(volume.getVolumeNumber() != null ? volume.getVolumeNumber() : 1).append("卷\n")
                  .append("- 章节范围：第").append(volume.getChapterStart()).append("章-第").append(volume.getChapterEnd()).append("章（共").append(chapterCount).append("章）\n")
                  .append("- 预估总字数：").append(totalWords).append("字\n")
                  .append("- 平均每章字数：").append(avgWordsPerChapter).append("字\n\n")
                  .append("- 注：章节范围仅用于估算篇幅，请勿按章节逐条规划。\n\n")

                  .append("【卷级大纲输出要求】\n")
                  .append("1) 本大纲为卷级规划，不进行逐章/分章细化，严禁出现‘第X章’‘章节’字样。\n")
                  .append("2) 请按以下结构输出（段落形式，避免列表过于空泛）：\n")
                  .append("   - 卷定位：本卷主题、目标与核心矛盾（≥250字）\n")
                  .append("   - 剧情主线：从开场到收束的因果链与冲突升级路径（≥350字）\n")
                  .append("   - 关键节点：开场铺垫、引爆事件、主要反转、高潮、收束与下卷悬念（各≥120字）\n")
                  .append("   - 角色弧光：主角与重要配角在本卷的动机变化与成长落点（≥180字）\n")
                  .append("   - 反派与对抗：反派/对手的策略、压迫感营造与失手点（≥150字）\n")
                  .append("   - 爽点与钩子：爽点设计与读者期待点，分布与节奏（≥150字）\n")
                  .append("   - 伏笔与回收：本卷埋下与回收的伏笔；对后续卷的预埋（≥150字）\n")
                  .append("   - 世界观与设定：关键设定如何在本卷被使用与推进（≥120字）\n")
                  .append("   - 篇幅与节奏：建议的段落节奏与信息密度（不出现章节号，可用‘起-承-转-合’段落）（≥120字）\n")
                  .append("   - 本卷达成：本卷完成的阶段性目标与人物/局势的新状态（≥120字）\n")
                  .append("3) 语言务必具体可写，避免空话、口号化描述；整份大纲≥2000字。\n")
                  .append("4) 严禁输出JSON或代码块，仅输出中文纯文本。\n\n");
            if (userAdvice != null && !userAdvice.trim().isEmpty()) {
                prompt.append("【用户建议优先级】\n").append(userAdvice.trim()).append("\n\n");
            }
            prompt.append("【卷详细信息】\n")
                  .append("- 主题：").append(volume.getTheme() != null ? volume.getTheme() : "").append("\n")
                  .append("- 描述：").append(volume.getDescription() != null ? volume.getDescription() : "").append("\n")
                  .append("- 现有内容大纲：\n").append(volume.getContentOutline() != null ? volume.getContentOutline() : "无").append("\n\n")
                  .append("请按章节顺序完整输出所有章节，并确保整体信息量充分、可直接据此动笔写作。");

            // 直接调用AI接口，使用前端传递的AI配置
            String aiResponse = callAIWithConfig(prompt.toString(), aiConfig);

            Map<String, Object> result = new HashMap<>();
            result.put("rawResponse", aiResponse);

            logger.info("✅ 卷 {} 异步大纲生成完成（单次提示词）", volumeId);
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
        com.novel.dto.AITaskDto taskDto = aiTaskService.createTask(aiTask);
            Long taskId = Long.valueOf(taskDto.getId());

            // 启动异步生成任务
            CompletableFuture.supplyAsync(() -> {
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

                    logger.info("✅ 卷 {} 异步大纲生成完成", volumeId);
                    return result;
                } catch (Exception e) {
                    logger.error("❌ 卷 {} 异步大纲生成失败: {}", volumeId, e.getMessage(), e);
                    aiTaskService.failTask(taskId, "生成失败: " + e.getMessage());
                    throw new RuntimeException(e.getMessage());
                }
            });

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