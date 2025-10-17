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
     */
    public void streamGenerateVolumeOutline(Long volumeId, String userAdvice, com.novel.dto.AIConfigRequest aiConfig, java.util.function.Consumer<String> chunkConsumer) {
        logger.info("📋 [流式] 开始为卷 {} 生成详细大纲", volumeId);
        
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
            throw new RuntimeException("小说尚未生成或确认超级大纲，无法生成卷大纲");
        }
        
        try {
            // 构建提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("# 任务\n");
            prompt.append("为小说《").append(novel.getTitle()).append("》的第 ").append(volume.getVolumeNumber()).append(" 卷生成详细大纲。\n\n");
            
            prompt.append("## 小说基本信息\n");
            prompt.append("- 类型: ").append(novel.getGenre()).append("\n");
            if (novel.getDescription() != null && !novel.getDescription().isEmpty()) {
                prompt.append("- 整体构思: ").append(novel.getDescription()).append("\n");
            }
            prompt.append("\n");
            
            prompt.append("## 超级大纲\n");
            prompt.append(superOutline.getPlotStructure()).append("\n\n");
            
            prompt.append("## 本卷基本信息\n");
            prompt.append("- 卷名: ").append(volume.getTitle()).append("\n");
            prompt.append("- 主题: ").append(volume.getTheme()).append("\n");
            prompt.append("- 简介: ").append(volume.getDescription()).append("\n");
            if (volume.getChapterStart() != null && volume.getChapterEnd() != null) {
                prompt.append("- 章节范围: 第 ").append(volume.getChapterStart()).append(" - ").append(volume.getChapterEnd()).append(" 章\n");
                int chapterCount = volume.getChapterEnd() - volume.getChapterStart() + 1;
                prompt.append("- 章节数: ").append(chapterCount).append(" 章\n");
            }
            if (volume.getEstimatedWordCount() != null && volume.getEstimatedWordCount() > 0) {
                prompt.append("- 目标字数: ").append(volume.getEstimatedWordCount()).append(" 字\n");
            }
            
            if (userAdvice != null && !userAdvice.trim().isEmpty()) {
                prompt.append("\n## 用户建议\n");
                prompt.append(userAdvice).append("\n");
            }
            
            prompt.append("\n## 要求\n");
            prompt.append("请为这一卷生成**引人入胜的详细大纲**，要求如下：\n\n");
            prompt.append("1. **情节设计** - 设计精彩的剧情，包括关键转折、高潮冲突、悬念铺垫，让读者欲罢不能\n");
            prompt.append("2. **人物塑造** - 刻画鲜活的人物形象，展现他们的成长蜕变、情感纠葛、性格碰撞\n");
            prompt.append("3. **情节推进** - 构建紧凑有力的叙事节奏，伏笔与回收并重，张弛有度\n");
            prompt.append("4. **创意亮点** - 加入令人眼前一亮的创意元素，可以是设定、台词、场景或情节反转\n\n");
            prompt.append("**注意事项**：\n");
            prompt.append("- 不要列出具体的章节编号（如\"第1章\"、\"第2章\"等），只描述剧情流程和发展脉络\n");
            prompt.append("- 使用生动的语言，让大纲本身就充满吸引力和画面感\n");
            prompt.append("- 可以使用小标题来组织内容，如\"开局破冰\"、\"矛盾升级\"、\"高潮对决\"、\"余韵回响\"等\n");
            prompt.append("- 直接输出大纲内容，不要添加\"根据您的要求\"、\"以下是大纲\"等元话语\n\n");
            
            logger.info("📝 [流式] 调用AI生成卷大纲，提示词长度: {}", prompt.length());
            
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
            
            logger.info("✅ [流式] 卷 {} 详细大纲生成并保存成功，总长度: {}", volumeId, accumulated.length());
            
        } catch (Exception e) {
            logger.error("❌ [流式] 生成卷 {} 详细大纲失败", volumeId, e);
            throw new RuntimeException("流式生成卷大纲失败: " + e.getMessage(), e);
        }
    }


    /**
     * 开始卷写作会话
     * 
     * @param volumeId 卷ID
     * @return 写作会话数据
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
        
        // 从数据库加载记忆库（由概括生成，而不是初始化）
        Map<String, Object> memoryBank = loadMemoryBankFromDatabase(novel, volume);
        
        // 创建写作会话
        Map<String, Object> writingSession = new HashMap<>();
        writingSession.put("volumeId", volumeId);
        writingSession.put("volume", volume);
        writingSession.put("novel", novel);
        writingSession.put("memoryBank", memoryBank); // 记忆库可能为空（第一章）
        writingSession.put("currentPosition", 0);
        writingSession.put("sessionStartTime", LocalDateTime.now());
        
        // 生成初始AI指导
        Map<String, Object> initialGuidance = generateWritingGuidance(novel, volume, null, "开始写作");
        writingSession.put("aiGuidance", initialGuidance);
        
        logger.info("✅ 卷 {} 写作会话创建成功，包含完整记忆库", volumeId);
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
            "你现在是一位专业的网络小说结构规划师，擅长将超长篇、无分卷的详细剧情大纲，按照网文连载节奏与叙事逻辑，拆解为结构清晰、节奏合理、爆点密集的'卷'式结构。\n\n" +
            "请根据我接下来提供的完整剧情发展线路大纲（不含卷划分），执行以下操作：\n\n" +
            "【小说信息】\n" +
            "- 标题：%s\n" +
            "- 类型：%s\n" +
            "- **目标卷数（必须严格遵守）：%d**\n\n" +
            
            "**【重要】你必须生成恰好 %d 个卷，不能多也不能少！**\n\n" +
            
            "拆卷原则：\n" +
            "不修改原剧情：\n严格基于我提供的大纲内容进行拆分，不得添加、删除或改动原有情节。\n所有事件、人物、伏笔、高潮必须原样保留，仅做'分段归卷'。\n" +
            "按叙事阶段划分卷：\n根据主角的成长阶段、地图升级、核心目标转变、势力格局变化等维度，均匀划分为 %d 个大卷。每卷需有明确的主题定位（如'觉醒启程''势力崛起''真相初现''命运对决'等），体现阶段特征。\n" +
            "每卷结构完整：\n每卷必须包含：\n开篇引爆：承接上一卷结尾，快速引入新冲突或目标\n中期推进：主线发展 + 至少2个中型高潮（如突破、打脸、奇遇）\n卷末高潮：一场高规格战斗、重大反转或命运转折，作为本卷收尾\n悬念钩子：为下一卷埋下期待感（如新敌人现身、真相线索浮现）\n" +
            "节奏与长度合理：\n每卷建议控制在 50-100章（可根据剧情密度微调）。每卷至少包含 1个大型高潮事件 和 2个以上中型爽点（如越阶战斗、身份揭露、红颜登场、势力建立等）。\n" +
            "适配多种风格：\n无论原大纲是'热血升级''权谋博弈''苟道发育''无敌碾压''群像叙事'还是'黑暗现实'，均需合理拆卷，不强行套用'废柴逆袭'等模板。\n若原大纲节奏平缓（如种田、经营类），则侧重'阶段性成果'作为高潮；若节奏激烈，则以'战斗/反转'为核心节点。\n\n" +
            
            "【输出要求（必须严格遵守）】\n" +
            "1. **必须生成恰好 %d 个卷的规划！**\n" +
            "2. 只输出一个 JSON 数组，数组长度必须为 %d，不要任何其他说明/表格/注释/Markdown。\n" +
            "3. 数组中的每个元素仅包含以下4个字段：title（卷标题）、theme（核心主题）、description（卷描述，150-200字）、contentOutline（详细大纲）。\n" +
            "4. 字段名必须为英文，且不得包含多余字段。\n\n" +
            "【完整剧情发展线路大纲】\n%s\n",
            novel.getTitle(),
            novel.getGenre(),
            volumeCount,
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
                logger.info("🔍 完整JSON内容: {}", jsonContent);
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                List<Map> jsonPlans = mapper.readValue(jsonContent, List.class);
                logger.info("✅ JSON解析成功，获得{}个卷规划", jsonPlans.size());
                
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
            String jsonStart = "```json";
            String jsonEnd = "```";
            
            int startIdx = response.indexOf(jsonStart);
            if (startIdx != -1) {
                startIdx += jsonStart.length();
                int endIdx = response.indexOf(jsonEnd, startIdx);
                if (endIdx != -1) {
                    return response.substring(startIdx, endIdx).trim();
                }
            }
            
            // 查找直接的JSON
            int braceStart = response.indexOf("[");
            int braceEnd = response.lastIndexOf("]");
            if (braceStart != -1 && braceEnd != -1 && braceStart < braceEnd) {
                return response.substring(braceStart, braceEnd + 1);
            }
            
        } catch (Exception e) {
            logger.warn("提取JSON失败: {}", e.getMessage());
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
     * 为卷构建完整的记忆库
     * 这是解决"缺少记忆库"错误的关键方法
     */
    /**
     * 从数据库加载记忆库（由LongNovelMemoryManager生成）
     */
    private Map<String, Object> loadMemoryBankFromDatabase(Novel novel, NovelVolume volume) {
        logger.info("📚 从数据库加载小说 {} 的记忆库", novel.getId());
        
        try {
            // 使用LongNovelMemoryManager加载记忆库
            Map<String, Object> memoryBank = longNovelMemoryManager.loadMemoryBankFromDatabase(novel.getId());
            
            if (memoryBank == null || memoryBank.isEmpty()) {
                logger.info("⚠️ 记忆库为空，这是第一章（正常情况）");
                memoryBank = new HashMap<>();
            }
            
            // 始终添加当前卷信息（不依赖概括）
            Map<String, Object> volumeInfo = new HashMap<>();
            volumeInfo.put("id", volume.getId());
            volumeInfo.put("title", volume.getTitle());
            volumeInfo.put("theme", volume.getTheme());
            volumeInfo.put("description", volume.getDescription());
            volumeInfo.put("contentOutline", volume.getContentOutline()); // 卷大纲内容
            volumeInfo.put("chapterStart", volume.getChapterStart());
            volumeInfo.put("chapterEnd", volume.getChapterEnd());
            volumeInfo.put("keyEvents", volume.getKeyEvents());
            memoryBank.put("currentVolumeOutline", volumeInfo);
            
            // 注意：小说总大纲已在 ContextManagementService.buildOutlineContext 中直接从 novel.getOutline() 读取，无需存入记忆库
            
            logger.info("✅ 记忆库加载完成，包含 {} 个组件", memoryBank.size());
            return memoryBank;
            
        } catch (Exception e) {
            logger.error("加载记忆库失败: {}", e.getMessage(), e);
            // 返回最小记忆库（只包含卷信息）
            Map<String, Object> minimalMemoryBank = new HashMap<>();
            Map<String, Object> volumeInfo = new HashMap<>();
            volumeInfo.put("contentOutline", volume.getContentOutline());
            minimalMemoryBank.put("currentVolumeOutline", volumeInfo);
            return minimalMemoryBank;
        }
    }
    

    


    /**
     * 创建简单的章节大纲结构
     */
    private List<Map<String, Object>> createSimpleChapterOutline(NovelVolume volume) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (int i = volume.getChapterStart(); i <= volume.getChapterEnd(); i++) {
            Map<String, Object> chapter = new HashMap<>();
            chapter.put("chapter", i);
            chapter.put("title", "第" + i + "章");
            chapter.put("objective", "章节目标待规划");
            chapter.put("conflict", "冲突设计待完善");
            chapter.put("hook", "悬念设置待优化");
            chapters.add(chapter);
        }
        return chapters;
    }
    

    /**
     * 解析AI响应为Map对象
     */
    private Map<String, Object> parseAIResponse(String response, String type) {
        try {
            logger.debug("正在解析AI响应: {}", response.substring(0, Math.min(200, response.length())));
            
            // 先尝试提取JSON代码块
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                Map<String, Object> parsed = mapper.readValue(jsonContent, Map.class);
                logger.info("✅ 成功解析{}的JSON响应", type);
                return parsed;
            }
            
            // 尝试直接找JSON对象
            int braceStart = response.indexOf("{");
            int braceEnd = response.lastIndexOf("}");
            if (braceStart != -1 && braceEnd != -1 && braceStart < braceEnd) {
                String jsonPart = response.substring(braceStart, braceEnd + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                Map<String, Object> parsed = mapper.readValue(jsonPart, Map.class);
                logger.info("✅ 成功解析{}的直接JSON", type);
                return parsed;
            }
            
            // 如果完全不是JSON格式，进行文本解析
            logger.warn("⚠️ 无法找到JSON格式，对{}进行文本解析", type);
            return parseTextualResponse(response, type);
            
        } catch (Exception e) {
            logger.warn("解析AI响应失败: {}，使用文本解析备选方案", e.getMessage());
            return parseTextualResponse(response, type);
        }
    }
    
    /**
     * 对非JSON格式的响应进行文本解析
     */
    private Map<String, Object> parseTextualResponse(String response, String type) {
        Map<String, Object> result = new HashMap<>();
        
        if ("基础框架".equals(type)) {
            result.put("volumeTheme", "主题待完善");
            result.put("mainConflict", "冲突待规划");
            result.put("characterGrowth", "角色成长待设计");
            result.put("keyTurningPoints", Arrays.asList("转折点1", "转折点2"));
            result.put("climaxChapter", "高潮章节待确定");
            result.put("volumeGoals", "卷目标待明确");
            result.put("rawResponse", response);
        } else if ("章节规划".equals(type)) {
            result.put("chapters", createBasicChapterStructure(response));
            result.put("rawResponse", response);
        } else if ("关键要素".equals(type)) {
            result.put("worldBuilding", "世界观要素待完善");
            result.put("plotThreads", Arrays.asList("主要情节线"));
            result.put("foreshadowing", Arrays.asList("伏笔待设置"));
            result.put("keyLocations", Arrays.asList("重要地点"));
            result.put("newCharacters", Arrays.asList("新角色"));
            result.put("volumeHooks", Arrays.asList("悬念点"));
            result.put("rawResponse", response);
        } else {
            result.put("content", response);
            result.put("type", type);
        }
        
        return result;
    }
    
    /**
     * 从文本中创建基础章节结构
     */
    private List<Map<String, Object>> createBasicChapterStructure(String response) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        
        // 尝试从文本中解析章节信息
        String[] lines = response.split("\\n");
        int chapterNum = 1;
        
        for (String line : lines) {
            if (line.contains("章") && (line.contains("第") || line.matches(".*\\d+.*"))) {
                Map<String, Object> chapter = new HashMap<>();
                chapter.put("chapterNumber", chapterNum);
                chapter.put("title", "第" + chapterNum + "章");
                chapter.put("purpose", line.trim());
                chapter.put("keyEvents", Arrays.asList("待规划事件"));
                chapter.put("characterDevelopment", "待规划");
                chapter.put("foreshadowing", "待设置");
                chapter.put("chapterGoal", "待明确");
                chapters.add(chapter);
                chapterNum++;
                
                if (chapters.size() >= 10) break; // 限制数量
            }
        }
        
        // 如果没有解析到章节，创建默认结构
        if (chapters.isEmpty()) {
            for (int i = 1; i <= 5; i++) {
                Map<String, Object> chapter = new HashMap<>();
                chapter.put("chapterNumber", i);
                chapter.put("title", "第" + i + "章");
                chapter.put("purpose", "章节目标待规划");
                chapter.put("keyEvents", Arrays.asList("关键事件待确定"));
                chapter.put("characterDevelopment", "角色发展待规划");
                chapter.put("foreshadowing", "伏笔待设置");
                chapter.put("chapterGoal", "章节目标待明确");
                chapters.add(chapter);
            }
        }
        
        return chapters;
    }
    

    



    /**
     * 基于确认的大纲生成卷规划（确认大纲后调用）
     * 说明：直接使用大纲内容，不需要重新构建提示词，并保存到数据库
     */
    @Transactional
    public List<NovelVolume> generateVolumePlansFromConfirmedOutline(Long novelId, Integer volumeCount) {
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
            List<Map<String, Object>> volumePlans = generateVolumePlansFromOutline(novel, outlineOpt.get(), volumeCount);
            
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
     * 基于确认的大纲异步生成卷规划
     */
    public com.novel.domain.entity.AITask generateVolumePlansFromConfirmedOutlineAsync(Long novelId, Integer volumeCount) {
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
            
            // 提交异步任务
            Long taskId = submitVolumePlansFromConfirmedOutlineTask(aiTask, novelId, volumeCount);
            aiTask.setId(taskId);
            
            return aiTask;
            
        } catch (Exception e) {
            logger.error("❌ 提交基于确认大纲的卷规划生成任务失败: {}", e.getMessage(), e);
            throw new RuntimeException("提交任务失败: " + e.getMessage());
        }
    }

    /**
     * 提交基于确认大纲的卷规划生成任务
     */
    private Long submitVolumePlansFromConfirmedOutlineTask(com.novel.domain.entity.AITask aiTask, Long novelId, Integer volumeCount) {
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
                    
                    // 调用基于确认大纲的生成方法
                    List<NovelVolume> volumes = generateVolumePlansFromConfirmedOutline(novelId, volumeCount);
                    
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
     */
    public void optimizeVolumeOutlineStream(Long volumeId, String currentOutline, String suggestion, Map<String, Object> volumeInfo, java.util.function.Consumer<String> chunkConsumer) {
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
            aiWritingService.streamGenerateContent(prompt.toString(), "volume_outline_optimization", chunk -> {
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