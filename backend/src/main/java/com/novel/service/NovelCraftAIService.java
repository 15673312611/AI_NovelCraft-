package com.novel.service;

import com.novel.domain.entity.Novel;
import com.novel.dto.AIConfigRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * NovelCraft AI - 完整的AI Agent长篇创作系统
 * 基于动态大纲引擎的6大模块闭环工作流
 * 
 * 【核心理念】
 * - 动态扩展：不预设所有内容，随写随扩
 * - 多AI协作：6个专业AI Agent分工协作  
 * - 用户主导：AI建议，用户决策
 * - 记忆持久：完整的创作记忆和上下文管理
 * - 质检自洽：自动检查一致性和逻辑关联
 */
@Service
public class NovelCraftAIService {

    private static final Logger logger = LoggerFactory.getLogger(NovelCraftAIService.class);

    // 保留作为后备配置（可选）
    @Autowired(required = false)
    private com.novel.config.AIClientConfig aiConfig;
    
    /**
     * 智能构建API URL
     * 处理不同服务商的baseUrl格式差异
     */
    private String buildApiUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "https://api.openai.com/v1/chat/completions";
        }
        
        // 智能构建URL：如果baseUrl已经包含/v1，则只添加/chat/completions
        if (baseUrl.endsWith("/v1")) {
            return baseUrl + "/chat/completions";
        } else if (baseUrl.endsWith("/")) {
            return baseUrl + "v1/chat/completions";
        } else {
            return baseUrl + "/v1/chat/completions";
        }
    }

    @Autowired
    private EnhancedWebNovelPromptService webNovelPromptService;
    
    @Autowired
    private AntiAIDetectionService antiAIDetectionService;
    
    @Autowired
    private ContextManagementService contextManagementService;

    @Autowired
    private MultiStageChapterGenerationService multiStageChapterGenerationService;
    
    @Autowired
    private LongFormCoherenceService longFormCoherenceService;

    // ================================
    // 1️⃣ 动态大纲引擎 (Dynamic Outline Engine)
    // ================================

    /**
     * 启动动态大纲系统
     * 根据用户基本构思，生成可扩展的树状+网状大纲结构
     */
    public Map<String, Object> initializeDynamicOutline(Novel novel, String basicIdea) {
        return initializeDynamicOutline(novel, basicIdea, 100, 2000);
    }
    
    public Map<String, Object> initializeDynamicOutline(Novel novel, String basicIdea, Integer targetChapterCount, Integer targetWordCount) {
        logger.info("🚀 启动动态大纲引擎: {}", novel.getTitle());
        
        // 使用网文专用大纲提示词
        String outlinePrompt = webNovelPromptService.getWebNovelOutlinePrompt(novel, basicIdea);

        String response = callAI("OUTLINE_ENGINE", outlinePrompt);
        Map<String, Object> outline = parseDynamicOutline(response);
        outline.put("createdAt", LocalDateTime.now());
        outline.put("status", "initialized");
        outline.put("expandable", true);
        
        return outline;
    }

    /**
     * 基于用户反馈调整大纲
     * 用户确认步骤中的大纲调整功能
     */
    public Map<String, Object> adjustOutlineWithFeedback(
            Novel novel,
            Map<String, Object> currentOutline, 
            String adjustmentRequest,
            String basicIdea
    ) {
        logger.info("🔄 基于用户反馈调整大纲: {}", novel.getTitle());
        
        String adjustPrompt = String.format(
            "你是【大纲调整AI】，负责根据用户反馈优化和调整大纲。\n\n" +
            "小说信息：\n" +
            "- 标题：%s\n" +
            "- 类型：%s\n" +
            "- 基本构思：%s\n\n" +
            "当前大纲：\n%s\n\n" +
            "用户调整要求：\n%s\n\n" +
            "请根据用户的调整要求，优化当前大纲。保持原有结构的合理部分，针对用户提到的问题进行调整。\n\n" +
            "**请严格按照以下JSON格式输出：**\n\n" +
            "```json\n" +
            "{\n" +
            "  \"mainStructure\": {\n" +
            "    \"phases\": [\n" +
            "      {\n" +
            "        \"name\": \"调整后的阶段名\",\n" +
            "        \"description\": \"调整后的阶段描述\",\n" +
            "        \"chapters\": \"章节范围\",\n" +
            "        \"keyEvents\": [\"关键事件1\", \"关键事件2\"]\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  \"coreElements\": {\n" +
            "    \"protagonist\": \"调整后的主角设定\",\n" +
            "    \"worldSetting\": \"调整后的世界观\",\n" +
            "    \"mainConflict\": \"调整后的主要冲突\",\n" +
            "    \"uniqueElements\": [\"独特元素1\", \"独特元素2\"]\n" +
            "  },\n" +
            "  \"adjustmentSummary\": {\n" +
            "    \"changedAspects\": [\"主要变更点1\", \"主要变更点2\"],\n" +
            "    \"reasonForChanges\": \"变更理由和说明\",\n" +
            "    \"impactAssessment\": \"变更对整体故事的影响评估\"\n" +
            "  }\n" +
            "}\n" +
            "```\n\n" +
            "**重要要求：**\n" +
            "1. 保持故事的连贯性和逻辑性\n" +
            "2. 充分考虑用户的调整要求\n" +
            "3. 对重大调整提供合理的解释\n" +
            "4. 确保JSON格式完全正确",
            novel.getTitle(), novel.getGenre(), basicIdea, 
            currentOutline.toString(), adjustmentRequest
        );

        String response = callAI("OUTLINE_ADJUSTER", adjustPrompt);
        Map<String, Object> adjustedOutline = parseAdjustedOutline(response, currentOutline);
        adjustedOutline.put("adjustedAt", LocalDateTime.now());
        adjustedOutline.put("userRequest", adjustmentRequest);
        
        return adjustedOutline;
    }

    /**
     * 动态扩展大纲
     * 基于已写内容和当前进度，智能扩展下一阶段大纲
     */
    public Map<String, Object> expandOutlineDynamically(
            Novel novel, 
            Map<String, Object> currentOutline,
            int currentChapter,
            String existingContent,
            String userDirection
    ) {
        logger.info("🌱 动态扩展大纲: 第{}章", currentChapter);
        
        String expandPrompt = String.format(
            "你是【动态大纲扩展AI】，负责基于现有内容智能扩展大纲。\n\n" +
            "当前状况：\n" +
            "- 小说：%s (%s)\n" +
            "- 当前章节：第%d章\n" +
            "- 现有大纲：%s\n" +
            "- 最新内容摘要：%s\n" +
            "- 用户意向：%s\n\n" +
            "请基于以上信息扩展大纲，包括：\n\n" +
            "## 1. 剧情发展分析\n" +
            "- 当前剧情所处阶段\n" +
            "- 主线推进情况\n" +
            "- 已激活的支线状态\n\n" +
            "## 2. 下阶段扩展 (接下来20-50章)\n" +
            "- 主要剧情走向\n" +
            "- 新支线触发时机\n" +
            "- 角色发展规划\n" +
            "- 重要事件节点\n\n" +
            "## 3. 新增元素建议\n" +
            "- 建议引入的新角色（姓名、定位、出场时机）\n" +
            "- 新的世界观设定补充\n" +
            "- 需要埋设的新伏笔\n" +
            "- 可以回收的旧伏笔\n\n" +
            "## 4. 支线管理\n" +
            "- 当前活跃支线状态\n" +
            "- 建议新增支线\n" +
            "- 建议结束的支线\n" +
            "- 支线与主线的交汇规划\n\n" +
            "确保扩展后的大纲：\n" +
            "1. 与前文逻辑一致\n" +
            "2. 融合用户意向\n" +
            "3. 保持足够的悬念和冲突\n" +
            "4. 为后续发展预留空间",
            novel.getTitle(), novel.getGenre(), currentChapter, 
            currentOutline.toString(), 
            existingContent.length() > 1000 ? existingContent.substring(existingContent.length()-1000) : existingContent,
            userDirection != null ? userDirection : "继续按原计划发展"
        );

        String response = callAI("OUTLINE_EXPANDER", expandPrompt);
        return parseExpandedOutline(response, currentOutline);
    }

    // ================================
    // 2️⃣ 章节拆解器 (Chapter Decomposer)
    // ================================

    /**
     * 智能章节拆解
     * 将大纲阶段拆解为可执行的章节任务
     */
    public List<Map<String, Object>> decomposeChaptersIntelligently(
            Map<String, Object> outline,
            int startChapter,
            int targetCount,
            String focusDirection
    ) {
        logger.info("🔧 章节智能拆解: 第{}章开始，拆解{}章", startChapter, targetCount);
        
        String decomposePrompt = String.format(
            "你是【章节拆解AI】，专门将大纲片段拆解为精确可执行的章节。\n\n" +
            "任务要求：\n" +
            "- 起始章节：第%d章\n" +
            "- 拆解数量：%d章\n" +
            "- 重点方向：%s\n" +
            "- 相关大纲：%s\n\n" +
            "**请基于大纲内容，为每个章节量身定制具体的情节，不要使用通用模板。**\n\n" +
            "拆解原则：\n" +
            "1. 每章800-1200字，确保节奏紧凑\n" +
            "2. 每章必须有【1个核心事件 + 1-2个角色发展 + 1个悬念/伏笔】\n" +
            "3. 章节类型要根据剧情需要选择：对话、战斗、探索、情感、转折\n" +
            "4. 合理安排高潮与缓冲，确保故事节奏\n\n" +
            "**请严格按照以下JSON数组格式输出：**\n\n" +
            "```json\n" +
            "[\n" +
            "  {\n" +
            "    \"chapterNumber\": %d,\n" +
            "    \"title\": \"基于具体剧情的章节标题\",\n" +
            "    \"type\": \"根据剧情选择的类型\",\n" +
            "    \"coreEvent\": \"该章节的核心事件详细描述\",\n" +
            "    \"characterDevelopment\": [\"角色具体的发展变化1\", \"角色具体的发展变化2\"],\n" +
            "    \"foreshadowing\": \"本章埋设的具体伏笔或制造的悬念\",\n" +
            "    \"newCharacters\": [\"如果引入新角色，请提供具体姓名和定位\"],\n" +
            "    \"plotConnections\": [\"与前文的具体关联点\"],\n" +
            "    \"estimatedWords\": 1000,\n" +
            "    \"priority\": \"根据剧情重要性评定(high/medium/low)\",\n" +
            "    \"mood\": \"该章节的具体氛围描述\"\n" +
            "  }\n" +
            "]\n" +
            "```\n\n" +
            "**重要要求：**\n" +
            "1. 每个字段都要根据具体的大纲内容填写，不能使用占位符\n" +
            "2. 章节标题要体现该章的核心内容\n" +
            "3. 核心事件要具体，不能太抽象\n" +
            "4. 确保JSON格式完全正确，方便程序解析\n" +
            "5. 章节间要有逻辑连贯性和发展递进性",
            startChapter, targetCount, focusDirection != null ? focusDirection : "平衡发展",
            outline.toString(), startChapter
        );

        String response = callAI("CHAPTER_DECOMPOSER", decomposePrompt);
        return parseChapterList(response, startChapter);
    }

    // ================================
    // 3️⃣ AI 写作 Agent (Writing Executor)
    // ================================

    /**
     * 执行单章写作 - 增强版，专注去AI化
     * 基于章节拆解和记忆库，生成具体章节内容
     * 使用人性化提示词和多轮优化策略
     */
    public Map<String, Object> executeChapterWriting(
            Novel novel,
            Map<String, Object> chapterPlan,
            Map<String, Object> memoryBank,
            String userAdjustment
    ) {
        int chapterNumber = (Integer) chapterPlan.get("chapterNumber");
        logger.info("✍️ 执行去AI化章节写作: 第{}章 - {}", chapterNumber, chapterPlan.get("title"));
        
        // 🔮 预防性连贯性检查（新增）
        try {
            if (chapterNumber > 1) { // 第一章不需要检查
                logger.info("🔮 执行预防性连贯性检查");
                
                // 获取最近章节用于预检
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> chapterSummaries = (List<Map<String, Object>>) 
                    memoryBank.getOrDefault("chapterSummaries", new ArrayList<>());
                
                List<Map<String, Object>> recentChapters = chapterSummaries.stream()
                    .filter(ch -> {
                        Integer num = (Integer) ch.get("chapterNumber");
                        return num != null && num < chapterNumber && num >= Math.max(1, chapterNumber - 3);
                    })
                    .collect(Collectors.toList());
                
                // 执行预防性检查
                Map<String, Object> preventiveCheck = longFormCoherenceService.preventiveCoherenceCheck(
                    chapterPlan, recentChapters, memoryBank
                );
                
                boolean safeToWrite = (Boolean) preventiveCheck.getOrDefault("isSafeToWrite", true);
                if (!safeToWrite) {
                    logger.warn("⚠️ 预防性检查发现潜在问题");
                    @SuppressWarnings("unchecked")
                    List<String> warnings = (List<String>) preventiveCheck.get("warnings");
                    @SuppressWarnings("unchecked")
                    List<String> recommendations = (List<String>) preventiveCheck.get("recommendations");
                    
                    // 将警告和建议加入用户调整中
                    StringBuilder enhancedAdjustment = new StringBuilder();
                    if (userAdjustment != null) {
                        enhancedAdjustment.append(userAdjustment).append("; ");
                    }
                    enhancedAdjustment.append("防止连贯性问题: ");
                    enhancedAdjustment.append(String.join("; ", recommendations));
                    userAdjustment = enhancedAdjustment.toString();
                    
                    logger.info("🔧 根据预防检查结果调整写作策略");
                }
            }
        } catch (Exception e) {
            logger.warn("预防性连贯性检查失败，继续正常写作流程: {}", e.getMessage());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("chapterNumber", chapterNumber);
        result.put("title", chapterPlan.get("title"));
        result.put("planInfo", chapterPlan);
        
        try {
            // 使用增强版人性化提示词
            String humanizedPrompt = webNovelPromptService.getHumanizedWritingPrompt(
                novel, chapterPlan, memoryBank, userAdjustment
            );
            
            logger.info("🎭 使用人性化提示词，模拟真实作者状态");
            
            // 第一轮：基础写作
            String initialResponse = callAI("HUMANIZED_WRITER", humanizedPrompt);
            
            if (initialResponse == null || initialResponse.trim().isEmpty()) {
                throw new RuntimeException("AI生成内容为空");
            }
            
            // AI痕迹检测和优化
            Map<String, Object> aiAnalysis = antiAIDetectionService.analyzeAIFeatures(initialResponse);
            double aiScore = (Double) aiAnalysis.get("aiScore");
            
            String finalContent = initialResponse;
            boolean optimizationApplied = false;
            
            // 如果AI痕迹过重，进行二次优化
            if (aiScore > 0.6) {
                logger.info("🔧 检测到AI痕迹较重 (评分: {}), 进行优化重写", aiScore);
                
                String optimizePrompt = antiAIDetectionService.optimizeAIContent(initialResponse, aiAnalysis, novel);
                String optimizedContent = callAI("CONTENT_OPTIMIZER", optimizePrompt);
                
                if (optimizedContent != null && !optimizedContent.trim().isEmpty()) {
                    // 检验优化效果
                    boolean improved = antiAIDetectionService.isOptimizationImproved(initialResponse, optimizedContent);
                    if (improved) {
                        finalContent = optimizedContent;
                        optimizationApplied = true;
                        logger.info("✨ 内容优化成功，人性化程度提升");
                    } else {
                        logger.warn("⚠️ 优化效果不佳，保留原内容");
                    }
                } else {
                    logger.warn("⚠️ 优化失败，保留原内容");
                }
            } else {
                logger.info("✅ 内容质量良好 (评分: {}), 无需优化", aiScore);
            }
            
            // 最终检测和统计
            Map<String, Object> finalAnalysis = antiAIDetectionService.analyzeAIFeatures(finalContent);
            double finalAiScore = (Double) finalAnalysis.get("aiScore");
            
            // 字数统计
            int actualWordCount = finalContent.length();
            
            // 组装结果
            result.put("content", finalContent);
            result.put("wordCount", actualWordCount);
            result.put("status", "completed");
            result.put("aiDetection", finalAnalysis);
            result.put("aiScore", finalAiScore);
            result.put("optimizationApplied", optimizationApplied);
            result.put("qualityLevel", getQualityLevel(finalAiScore));
            
            // 生成优化建议
            List<String> suggestions = antiAIDetectionService.generateOptimizationSuggestions(finalAnalysis);
            result.put("suggestions", suggestions);
            
            logger.info("✅ 章节写作完成: {}字, AI评分: {}, 质量: {}", 
                       actualWordCount, String.format("%.2f", finalAiScore), getQualityLevel(finalAiScore));
            
            return result;
            
        } catch (Exception e) {
            logger.error("❌ 章节写作失败: {}", e.getMessage(), e);
            result.put("error", true);
            result.put("message", e.getMessage());
            result.put("status", "failed");
            return result;
        }
    }

    // ================================
    // 🛠️ 辅助方法和工具函数
    // ================================

    /**
     * 根据AI评分确定内容质量等级
     */
    private String getQualityLevel(double aiScore) {
        if (aiScore <= 0.3) {
            return "优秀"; // 人性化程度高
        } else if (aiScore <= 0.5) {
            return "良好"; // 轻微AI痕迹
        } else if (aiScore <= 0.7) {
            return "一般"; // 中等AI痕迹
        } else {
            return "待优化"; // AI痕迹明显
        }
    }




    /**
     * 更新小说记忆库
     * 每写完一章后，自动更新记忆库信息
     */
    public Map<String, Object> updateMemoryBank(
            Map<String, Object> memoryBank,
            Map<String, Object> newChapter
    ) {
        int chapterNumber = (Integer) newChapter.get("chapterNumber");
        String content = (String) newChapter.get("content");
        
        logger.info("🧠 更新记忆库: 第{}章", chapterNumber);
        
        String memoryPrompt = String.format(
            "你是【记忆管理AI】，负责维护小说的完整记忆库。\n\n" +
            "## 新增内容\n" +
            "第%d章内容：\n%s\n\n" +
            "## 当前记忆库\n%s\n\n" +
            "请更新记忆库，包括：\n\n" +
            "### 1. 人物档案更新\n" +
            "- 新出现的角色信息\n" +
            "- 现有角色的状态变化\n" +
            "- 角色关系的变化\n\n" +
            "### 2. 世界设定补充\n" +
            "- 新增的设定信息\n" +
            "- 地点描述更新\n" +
            "- 规则体系补充\n\n" +
            "### 3. 伏笔管理\n" +
            "- 本章新埋设的伏笔\n" +
            "- 本章回收的伏笔\n" +
            "- 伏笔状态更新\n\n" +
            "### 4. 情节线索\n" +
            "- 主线进展更新\n" +
            "- 支线发展状况\n" +
            "- 新激活的情节线\n\n" +
            "### 5. 重要事件记录\n" +
            "- 本章发生的关键事件\n" +
            "- 事件对后续剧情的影响\n" +
            "- 事件关联的角色和设定\n\n" +
            "输出JSON格式的更新后记忆库。",
            chapterNumber, content, memoryBank.toString()
        );

        String response = callAI("MEMORY_MANAGER", memoryPrompt);
        return parseMemoryBankUpdate(response, memoryBank);
    }

    /**
     * 一致性质量检查
     * 检查新章节与已有内容的一致性
     */
    public Map<String, Object> performConsistencyCheck(
            Novel novel,
            Map<String, Object> newChapter,
            Map<String, Object> memoryBank
    ) {
        int chapterNumber = (Integer) newChapter.get("chapterNumber");
        String content = (String) newChapter.get("content");
        
        logger.info("🔍 执行一致性检查: 第{}章", chapterNumber);
        
        String checkPrompt = String.format(
            "你是【一致性检查AI】，专门检查小说内容的逻辑一致性。\n\n" +
            "## 待检查内容\n" +
            "第%d章：《%s》\n%s\n\n" +
            "## 记忆库参考\n%s\n\n" +
            "请进行全面一致性检查：\n\n" +
            "### 1. 角色一致性检查 (0-10分)\n" +
            "- 角色性格是否与之前描述一致？\n" +
            "- 角色能力是否有不合理变化？\n" +
            "- 角色说话风格是否保持？\n" +
            "- 角色关系是否符合逻辑？\n\n" +
            "### 2. 设定一致性检查 (0-10分)\n" +
            "- 世界观设定是否有矛盾？\n" +
            "- 规则体系是否自洽？\n" +
            "- 地理/时间设定是否合理？\n" +
            "- 新增设定是否与旧设定冲突？\n\n" +
            "### 3. 情节逻辑检查 (0-10分)\n" +
            "- 事件发展是否符合逻辑？\n" +
            "- 因果关系是否清晰？\n" +
            "- 时间线是否合理？\n" +
            "- 角色动机是否充分？\n\n" +
            "### 4. 前文关联检查 (0-10分)\n" +
            "- 是否与前文呼应？\n" +
            "- 伏笔处理是否得当？\n" +
            "- 情节推进是否自然？\n" +
            "- 细节描述是否一致？\n\n" +
            "### 5. 问题识别与建议\n" +
            "- 发现的具体问题\n" +
            "- 修改建议\n" +
            "- 风险评估\n" +
            "- 后续注意事项\n\n" +
            "请给出详细的检查报告和综合评分。",
            chapterNumber, newChapter.get("title"), content, memoryBank.toString()
        );

        String response = callAI("CONSISTENCY_CHECKER", checkPrompt);
        Map<String, Object> consistencyResult = parseConsistencyReport(response, chapterNumber);
        
        // 🔗 集成长篇连贯性检测（新增功能）
        try {
            logger.info("🔗 执行长篇连贯性检测");
            
            // 获取最近章节用于连贯性分析
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chapterSummaries = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("chapterSummaries", new ArrayList<>());
            
            List<Map<String, Object>> recentChapters = chapterSummaries.stream()
                .filter(ch -> {
                    Integer num = (Integer) ch.get("chapterNumber");
                    return num != null && num < chapterNumber && num >= Math.max(1, chapterNumber - 5);
                })
                .collect(Collectors.toList());
            
            // 执行连贯性分析
            Map<String, Object> coherenceAnalysis = longFormCoherenceService.analyzeChapterCoherence(
                novel, newChapter, recentChapters, memoryBank
            );
            
            // 将连贯性分析结果合并到一致性检查结果中
            consistencyResult.put("coherenceAnalysis", coherenceAnalysis);
            
            double coherenceScore = (Double) coherenceAnalysis.getOrDefault("overallScore", 0.8) * 10; // 转换为10分制
            double originalScore = (Double) consistencyResult.getOrDefault("overallScore", 8.0);
            double enhancedScore = (originalScore + coherenceScore) / 2.0;
            
            consistencyResult.put("originalScore", originalScore);
            consistencyResult.put("coherenceScore", coherenceScore);
            consistencyResult.put("enhancedScore", enhancedScore);
            consistencyResult.put("checkType", "enhanced_with_coherence");
            
            // 如果连贯性检测发现问题，标记需要修订
            boolean needsCoherenceRevision = (Boolean) coherenceAnalysis.getOrDefault("needsRevision", false);
            if (needsCoherenceRevision) {
                consistencyResult.put("needsRevision", true);
                consistencyResult.put("coherenceIssues", coherenceAnalysis.get("detectedIssues"));
                consistencyResult.put("coherenceSuggestions", coherenceAnalysis.get("improvementSuggestions"));
            }
            
            logger.info("✅ 长篇连贯性检测完成 - 连贯性评分: {:.1f}, 综合评分: {:.1f}", coherenceScore, enhancedScore);
            
        } catch (Exception e) {
            logger.warn("长篇连贯性检测失败，继续使用基础一致性检查: {}", e.getMessage());
            consistencyResult.put("coherenceError", e.getMessage());
            consistencyResult.put("checkType", "basic_only");
        }
        
        return consistencyResult;
    }

    // ================================
    // 5️⃣ 反馈建议系统 (AI Assistant Mode)
    // ================================

    /**
     * 生成智能创作建议
     * AI主动分析当前状况，提供创作建议
     */
    public Map<String, Object> generateIntelligentSuggestions(
            Novel novel,
            Map<String, Object> memoryBank,
            int currentChapter,
            String recentTrends
    ) {
        logger.info("💡 生成智能建议: 第{}章", currentChapter);
        
        // 使用网文专用建议提示词
        String suggestionPrompt = webNovelPromptService.getWebNovelSuggestionsPrompt(novel, memoryBank, currentChapter);

        String response = callAI("SUGGESTION_ENGINE", suggestionPrompt);
        return parseIntelligentSuggestions(response, currentChapter);
    }

    /**
     * 主动提醒系统
     * 分析创作状态，主动提醒需要注意的事项
     */
    public List<Map<String, Object>> generateProactiveReminders(
            Map<String, Object> memoryBank,
            int currentChapter
    ) {
        logger.info("🔔 生成主动提醒: 第{}章", currentChapter);
        
        String reminderPrompt = String.format(
            "你是【主动提醒AI】，负责分析创作状态，提醒作者重要事项。\n\n" +
            "## 当前状态\n" +
            "- 当前章节：第%d章\n" +
            "- 记忆库：%s\n\n" +
            "请分析并生成提醒事项：\n\n" +
            "### 1. 伏笔提醒\n" +
            "- 哪些伏笔已埋设太久需要回收？\n" +
            "- 哪些伏笔即将到回收时机？\n" +
            "- 有没有遗忘的重要伏笔？\n\n" +
            "### 2. 角色活跃度提醒\n" +
            "- 哪些重要角色已久未出场？\n" +
            "- 哪些角色需要发展互动？\n" +
            "- 角色关系是否需要推进？\n\n" +
            "### 3. 情节推进提醒\n" +
            "- 主线是否推进缓慢？\n" +
            "- 支线是否过于繁杂？\n" +
            "- 是否需要增加冲突？\n\n" +
            "### 4. 节奏调控提醒\n" +
            "- 当前节奏是否合适？\n" +
            "- 是否需要高潮或缓冲？\n" +
            "- 读者疲劳点预警\n\n" +
            "### 5. 质量保证提醒\n" +
            "- 一致性维护建议\n" +
            "- 逻辑自洽检查\n" +
            "- 细节完善提醒\n\n" +
            "每个提醒请包含：重要程度(high/medium/low)、具体内容、建议操作。",
            currentChapter, memoryBank.toString()
        );

        String response = callAI("REMINDER_SYSTEM", reminderPrompt);
        return parseProactiveReminders(response, currentChapter);
    }

    // ================================
    // 6️⃣ 用户决策接口 (Control Panel)
    // ================================

    /**
     * 智能对话交互
     * 用户可以通过对话形式与AI协作
     */
    public Map<String, Object> intelligentDialogue(
            Novel novel,
            Map<String, Object> memoryBank,
            String userMessage,
            List<Map<String, Object>> chatHistory
    ) {
        logger.info("💬 智能对话交互: {}", userMessage);
        
        // 构建对话上下文
        String contextInfo = buildDialogueContext(novel, memoryBank, chatHistory);
        
        String dialoguePrompt = String.format(
            "你是【NovelCraft AI助手】，专业的小说创作伙伴。\n\n" +
            "## 当前上下文\n%s\n\n" +
            "## 对话历史\n%s\n\n" +
            "## 用户说\n%s\n\n" +
            "## 你的角色\n" +
            "你是用户的创作伙伴，可以：\n" +
            "1. 🎯 **剧情咨询** - 分析剧情发展，提供专业建议\n" +
            "2. 🔧 **方向调整** - 协助用户调整创作方向\n" +
            "3. 💡 **创意激发** - 提供灵感和创新点子\n" +
            "4. 🔍 **问题解决** - 帮助解决创作中的困难\n" +
            "5. 📊 **状态分析** - 分析当前创作状态和进度\n" +
            "6. 🤝 **协作规划** - 制定接下来的创作计划\n\n" +
            "## 回复要求\n" +
            "1. 专业且有建设性\n" +
            "2. 结合具体的小说内容\n" +
            "3. 提供可操作的建议\n" +
            "4. 保持鼓励和支持的语调\n" +
            "5. 如果涉及剧情修改，要详细说明原因和影响\n\n" +
            "请以创作伙伴的身份，专业地回应用户的需求。",
            contextInfo, formatChatHistory(chatHistory), userMessage
        );

        String response = callAI("DIALOGUE_ASSISTANT", dialoguePrompt);
        
        Map<String, Object> dialogueResult = new HashMap<>();
        dialogueResult.put("userMessage", userMessage);
        dialogueResult.put("aiResponse", response);
        dialogueResult.put("timestamp", LocalDateTime.now());
        dialogueResult.put("context", "intelligent_dialogue");
        dialogueResult.put("actionRequired", extractActionItems(response));
        
        return dialogueResult;
    }

    /**
     * 执行用户决策
     * 处理用户的各种决策指令
     */
    public Map<String, Object> executeUserDecision(
            Novel novel,
            Map<String, Object> memoryBank,
            String decisionType,
            Map<String, Object> decisionParams
    ) {
        logger.info("🎮 执行用户决策: {}", decisionType);
        
        Map<String, Object> result = new HashMap<>();
        
        switch (decisionType) {
            case "adjust_mainplot":
                result = adjustMainPlot(novel, memoryBank, decisionParams);
                break;
            case "add_subplot":
                result = addSubPlot(memoryBank, decisionParams);
                break;
            case "remove_subplot":
                result = removeSubPlot(memoryBank, decisionParams);
                break;
            case "introduce_character":
                result = introduceNewCharacter(memoryBank, decisionParams);
                break;
            case "modify_character":
                result = modifyCharacter(memoryBank, decisionParams);
                break;
            case "set_foreshadowing":
                result = setForeshadowing(memoryBank, decisionParams);
                break;
            case "resolve_foreshadowing":
                result = resolveForeshadowing(memoryBank, decisionParams);
                break;
            case "change_pace":
                result = changePace(memoryBank, decisionParams);
                break;
            default:
                result.put("success", false);
                result.put("message", "未知的决策类型: " + decisionType);
        }
        
        result.put("decisionType", decisionType);
        result.put("executedAt", LocalDateTime.now());
        
        return result;
    }

    // ================================
    // 核心AI调用系统
    // ================================

    /**
     * 统一的AI调用接口
     * 支持不同角色的AI调用，现在支持完整上下文消息列表
     */
    public String callAI(String agentRole, String prompt) {
        try {
            String baseUrl = aiConfig.getBaseUrl();
            String apiKey = aiConfig.getApiKey();
            String model = aiConfig.getDefaultModel();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new RuntimeException("AI API Key未配置");
            }

            // 为不同角色设置不同的参数
            Map<String, Object> requestBody = buildAIRequest(agentRole, model, prompt);

            // 发送请求
            RestTemplate restTemplate = createRestTemplate();
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = buildApiUrl(baseUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseAIResponse(response.getBody());
            }

            throw new RuntimeException("AI服务响应异常");

        } catch (Exception e) {
            logger.error("AI服务调用失败 [{}]: {}", agentRole, e.getMessage());
            throw new RuntimeException("AI服务调用失败 [" + agentRole + "]: " + e.getMessage());
        }
    }

    /**
     * 使用完整上下文的AI调用接口
     * 充分利用128k上下文容量
     */
    public String callAIWithFullContext(String agentRole, List<Map<String, String>> messages) {
        try {
            String baseUrl = aiConfig.getBaseUrl();
            String apiKey = aiConfig.getApiKey();
            String model = aiConfig.getDefaultModel();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new RuntimeException("AI API Key未配置");
            }

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            
            // 根据AI角色调整参数
            switch (agentRole) {
                case "ENHANCED_WRITING_EXECUTOR":
                    requestBody.put("max_tokens", 4000);
                    requestBody.put("temperature", 0.9);
                    break;
                case "CONTEXT_AWARE_WRITER":
                    requestBody.put("max_tokens", 4000);
                    requestBody.put("temperature", 0.8);
                    break;
                default:
                    requestBody.put("max_tokens", 2000);
                    requestBody.put("temperature", 0.7);
            }

            requestBody.put("messages", messages);

            logger.info("🚀 发送{}条上下文消息到AI [{}]", messages.size(), agentRole);
            
            // 计算总token数（估算）
            int totalTokens = messages.stream()
                    .mapToInt(msg -> msg.get("content").length())
                    .sum();
            logger.info("📊 估计上下文tokens: {}字符", totalTokens);

            // 发送请求
            RestTemplate restTemplate = createRestTemplate();
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = buildApiUrl(baseUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseAIResponse(response.getBody());
            }

            throw new RuntimeException("AI服务响应异常");

        } catch (Exception e) {
            logger.error("完整上下文AI服务调用失败 [{}]: {}", agentRole, e.getMessage());
            throw new RuntimeException("完整上下文AI服务调用失败 [" + agentRole + "]: " + e.getMessage());
        }
    }

    /**
     * 为不同AI角色构建请求参数
     */
    private Map<String, Object> buildAIRequest(String agentRole, String model, String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        // 根据AI角色调整参数
        switch (agentRole) {
            case "OUTLINE_ENGINE":
            case "OUTLINE_EXPANDER":
                requestBody.put("max_tokens", 4000);
                requestBody.put("temperature", 0.8);
                break;
            case "CHAPTER_DECOMPOSER":
                requestBody.put("max_tokens", 3000);
                requestBody.put("temperature", 0.7);
                break;
            case "WRITING_EXECUTOR":
                requestBody.put("max_tokens", 2500);
                requestBody.put("temperature", 0.9);
                break;
            case "MEMORY_MANAGER":
            case "CONSISTENCY_CHECKER":
                requestBody.put("max_tokens", 3500);
                requestBody.put("temperature", 0.3);
                break;
            case "SUGGESTION_ENGINE":
            case "REMINDER_SYSTEM":
                requestBody.put("max_tokens", 3000);
                requestBody.put("temperature", 0.6);
                break;
            case "DIALOGUE_ASSISTANT":
                requestBody.put("max_tokens", 2000);
                requestBody.put("temperature", 0.7);
                break;
            case "CHAPTER_SUMMARIZER":
            case "CREATIVE_ANALYST":
            case "PROTAGONIST_ANALYST":
                requestBody.put("max_tokens", 3000);
                requestBody.put("temperature", 0.7);
                break;
            default:
                requestBody.put("max_tokens", 2000);
                requestBody.put("temperature", 0.7);
        }

        // 构建消息列表 - 使用单条消息（保持原有逻辑）
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 为分析类AI添加系统身份
        if (isAnalysisAgent(agentRole)) {
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是专业的小说创作分析AI，请基于提供的信息进行深入分析和建议。");
            messages.add(systemMessage);
        }
        
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        
        requestBody.put("messages", messages);

        return requestBody;
    }

    /**
     * 判断是否为分析类Agent
     */
    private boolean isAnalysisAgent(String agentRole) {
        return agentRole.equals("CHAPTER_SUMMARIZER") || 
               agentRole.equals("CREATIVE_ANALYST") || 
               agentRole.equals("PROTAGONIST_ANALYST");
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(20000);
        requestFactory.setReadTimeout(180000);
        return new RestTemplate(requestFactory);
    }

    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    // ================================
    // 辅助解析方法 (后续实现)
    // ================================

    /**
     * 解析动态大纲AI响应
     * 从AI响应中提取结构化的大纲信息
     */
    private Map<String, Object> parseDynamicOutline(String response) {
        Map<String, Object> outline = new HashMap<>();
        outline.put("rawResponse", response);
        
        try {
            // 尝试从AI响应中解析JSON结构
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent != null && !jsonContent.isEmpty()) {
                // 尝试解析完整的JSON大纲结构
                outline.putAll(parseJSONOutline(jsonContent));
            } else {
                // 如果没有JSON，使用文本解析方式
                parseOutlineFromText(response, outline);
            }
            
            outline.put("parsed", true);
            outline.put("parsedAt", LocalDateTime.now());
            
        } catch (Exception e) {
            logger.warn("解析动态大纲时出现错误，使用默认结构: {}", e.getMessage());
            outline.put("parsed", false);
            outline.put("error", e.getMessage());
        }
        
        return outline;
    }

    /**
     * 解析调整后的大纲AI响应
     * 从AI响应中提取调整后的大纲结构
     */
    private Map<String, Object> parseAdjustedOutline(String response, Map<String, Object> originalOutline) {
        Map<String, Object> adjustedOutline = new HashMap<>(originalOutline);
        adjustedOutline.put("rawResponse", response);
        
        try {
            // 尝试从AI响应中解析JSON结构
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent != null && !jsonContent.isEmpty()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonOutline = mapper.readValue(jsonContent, Map.class);
                
                // 更新主要结构
                if (jsonOutline.containsKey("mainStructure")) {
                    adjustedOutline.put("mainStructure", jsonOutline.get("mainStructure"));
                }
                
                // 更新核心元素
                if (jsonOutline.containsKey("coreElements")) {
                    adjustedOutline.put("coreElements", jsonOutline.get("coreElements"));
                }
                
                // 添加调整摘要
                if (jsonOutline.containsKey("adjustmentSummary")) {
                    adjustedOutline.put("adjustmentSummary", jsonOutline.get("adjustmentSummary"));
                }
                
                logger.info("✅ 成功解析AI调整后的大纲结构");
            } else {
                // 如果没有JSON，使用文本解析方式
                parseAdjustedOutlineFromText(response, adjustedOutline);
            }
            
            adjustedOutline.put("adjustmentParsed", true);
            adjustedOutline.put("adjustmentParsedAt", LocalDateTime.now());
            
        } catch (Exception e) {
            logger.warn("解析调整后大纲时出现错误，保留原始结构: {}", e.getMessage());
            adjustedOutline.put("adjustmentParsed", false);
            adjustedOutline.put("adjustmentError", e.getMessage());
            // 添加文本形式的调整说明
            adjustedOutline.put("adjustmentText", response);
        }
        
        return adjustedOutline;
    }

    /**
     * 从文本中解析调整后的大纲（备选方案）
     */
    private void parseAdjustedOutlineFromText(String response, Map<String, Object> adjustedOutline) {
        logger.info("🔄 使用文本解析方式处理调整后大纲");
        
        // 简单的文本解析，提取调整要点
        Map<String, Object> adjustmentSummary = new HashMap<>();
        
        String[] lines = response.split("\\n");
        List<String> changedAspects = new ArrayList<>();
        StringBuilder reasonForChanges = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            if (line.contains("调整") || line.contains("修改") || line.contains("变更")) {
                changedAspects.add(cleanText(line));
            } else if (line.contains("原因") || line.contains("理由")) {
                reasonForChanges.append(line).append(" ");
            }
        }
        
        adjustmentSummary.put("changedAspects", changedAspects);
        adjustmentSummary.put("reasonForChanges", reasonForChanges.toString().trim());
        adjustmentSummary.put("impactAssessment", "基于文本解析的调整");
        
        adjustedOutline.put("adjustmentSummary", adjustmentSummary);
        adjustedOutline.put("adjustmentText", response);
    }

    /**
     * 解析扩展大纲AI响应
     * 将新的扩展内容合并到现有大纲中
     */
    private Map<String, Object> parseExpandedOutline(String response, Map<String, Object> currentOutline) {
        Map<String, Object> expanded = new HashMap<>(currentOutline);
        expanded.put("expansion", response);
        expanded.put("expandedAt", LocalDateTime.now());
        
        try {
            // 解析剧情发展分析
            Map<String, Object> plotAnalysis = new HashMap<>();
            plotAnalysis.put("currentStage", extractContent(response, "当前剧情所处阶段", "主线推进情况"));
            plotAnalysis.put("mainlineProgress", extractContent(response, "主线推进情况", "已激活的支线状态"));
            plotAnalysis.put("sublineStatus", extractContent(response, "已激活的支线状态", "下阶段扩展"));
            expanded.put("plotAnalysis", plotAnalysis);
            
            // 解析下阶段扩展
            Map<String, Object> nextPhase = new HashMap<>();
            nextPhase.put("plotDirection", extractContent(response, "主要剧情走向", "新支线触发时机"));
            nextPhase.put("sublineTriggers", extractContent(response, "新支线触发时机", "角色发展规划"));
            nextPhase.put("characterDevelopment", extractContent(response, "角色发展规划", "重要事件节点"));
            nextPhase.put("keyEvents", extractContent(response, "重要事件节点", "新增元素建议"));
            expanded.put("nextPhase", nextPhase);
            
            // 解析新增元素建议
            Map<String, Object> newElements = new HashMap<>();
            newElements.put("newCharacters", extractCharacterSuggestions(response));
            newElements.put("worldExpansion", extractContent(response, "新的世界观设定补充", "需要埋设的新伏笔"));
            newElements.put("newForeshadowing", extractContent(response, "需要埋设的新伏笔", "可以回收的旧伏笔"));
            newElements.put("resolvableForeshadowing", extractContent(response, "可以回收的旧伏笔", "支线管理"));
            expanded.put("newElements", newElements);
            
            // 解析支线管理
            Map<String, Object> sublineManagement = new HashMap<>();
            sublineManagement.put("activeSublines", extractContent(response, "当前活跃支线状态", "建议新增支线"));
            sublineManagement.put("suggestedNewSublines", extractContent(response, "建议新增支线", "建议结束的支线"));
            sublineManagement.put("suggestedEndSublines", extractContent(response, "建议结束的支线", "支线与主线的交汇规划"));
            sublineManagement.put("convergencePlan", extractContent(response, "支线与主线的交汇规划", ""));
            expanded.put("sublineManagement", sublineManagement);
            
            expanded.put("expansionParsed", true);
            
        } catch (Exception e) {
            logger.warn("解析扩展大纲时出现错误: {}", e.getMessage());
            expanded.put("expansionParsed", false);
            expanded.put("expansionError", e.getMessage());
        }
        
        return expanded;
    }

    /**
     * 解析章节列表AI响应
     * 从AI响应中提取JSON格式的章节规划
     */
    private List<Map<String, Object>> parseChapterList(String response, int startChapter) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        
        try {
            // 尝试从响应中提取JSON内容
            String jsonContent = extractJSONFromResponse(response);
            if (jsonContent != null && !jsonContent.isEmpty()) {
                // 尝试解析JSON数组
                chapters.addAll(parseJSONChapterArray(jsonContent, startChapter));
            }
            
            // 如果JSON解析失败，使用文本解析作为备选方案
            if (chapters.isEmpty()) {
                chapters.addAll(parseChapterListFromText(response, startChapter));
            }
            
        } catch (Exception e) {
            logger.warn("解析章节列表失败，使用默认生成: {}", e.getMessage());
            chapters.addAll(generateDefaultChapterList(startChapter, 10));
        }
        
        return chapters;
    }
    
    /**
     * 从AI响应中解析JSON章节数组
     */
    private List<Map<String, Object>> parseJSONChapterArray(String jsonContent, int startChapter) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            // 尝试解析为JSON数组
            if (jsonContent.trim().startsWith("[")) {
                List<Map> jsonChapters = mapper.readValue(jsonContent, List.class);
                for (Map jsonChapter : jsonChapters) {
                    chapters.add(convertJSONToChapter(jsonChapter, startChapter));
                }
            } else if (jsonContent.trim().startsWith("{")) {
                // 单个章节对象
                Map jsonChapter = mapper.readValue(jsonContent, Map.class);
                chapters.add(convertJSONToChapter(jsonChapter, startChapter));
            }
            
        } catch (Exception e) {
            logger.warn("JSON解析失败: {}", e.getMessage());
        }
        
        return chapters;
    }
    
    /**
     * 将JSON章节对象转换为标准章节Map
     */
    private Map<String, Object> convertJSONToChapter(Map jsonChapter, int baseChapter) {
        Map<String, Object> chapter = new HashMap<>();
        
        // 安全获取各个字段
        chapter.put("chapterNumber", getIntValue(jsonChapter, "chapterNumber", baseChapter));
        chapter.put("title", getStringValue(jsonChapter, "title", "未命名章节"));
        chapter.put("type", getStringValue(jsonChapter, "type", "对话"));
        chapter.put("coreEvent", getStringValue(jsonChapter, "coreEvent", "待定事件"));
        chapter.put("characterDevelopment", getListValue(jsonChapter, "characterDevelopment"));
        chapter.put("foreshadowing", getStringValue(jsonChapter, "foreshadowing", ""));
        chapter.put("newCharacters", getListValue(jsonChapter, "newCharacters"));
        chapter.put("plotConnections", getListValue(jsonChapter, "plotConnections"));
        chapter.put("estimatedWords", getIntValue(jsonChapter, "estimatedWords", 1000));
        chapter.put("priority", getStringValue(jsonChapter, "priority", "medium"));
        chapter.put("mood", getStringValue(jsonChapter, "mood", "平衡"));
        
        return chapter;
    }
    
    /**
     * 从文本中解析章节信息（备选方案）
     */
    private List<Map<String, Object>> parseChapterListFromText(String response, int startChapter) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        
        // 按段落分割，寻找章节信息
        String[] lines = response.split("\n");
        Map<String, Object> currentChapter = null;
        int chapterIndex = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.matches(".*第\\d+章.*") || line.contains("章节")) {
                // 保存前一章
                if (currentChapter != null) {
                    chapters.add(currentChapter);
                }
                // 开始新章
                currentChapter = new HashMap<>();
                currentChapter.put("chapterNumber", startChapter + chapterIndex++);
                currentChapter.put("title", extractChapterTitle(line));
                currentChapter.put("type", "对话");
                currentChapter.put("estimatedWords", 1000);
                currentChapter.put("priority", "medium");
            } else if (currentChapter != null) {
                // 解析章节详细信息
                if (line.contains("核心事件") || line.contains("事件")) {
                    currentChapter.put("coreEvent", cleanText(line));
                } else if (line.contains("角色") || line.contains("人物")) {
                    currentChapter.put("characterDevelopment", Arrays.asList(cleanText(line)));
                } else if (line.contains("伏笔") || line.contains("悬念")) {
                    currentChapter.put("foreshadowing", cleanText(line));
                }
            }
        }
        
        // 添加最后一章
        if (currentChapter != null) {
            chapters.add(currentChapter);
        }
        
        // 如果还是没解析出内容，生成默认章节
        if (chapters.isEmpty()) {
            chapters.addAll(generateDefaultChapterList(startChapter, 10));
        }
        
        return chapters;
    }

    /**
     * 解析记忆库更新AI响应
     * 智能更新记忆库中的各种信息
     */
    private Map<String, Object> parseMemoryBankUpdate(String response, Map<String, Object> memoryBank) {
        Map<String, Object> updated = new HashMap<>(memoryBank);
        updated.put("lastUpdate", LocalDateTime.now());
        updated.put("updateResponse", response);
        
        try {
            // 更新人物档案
            @SuppressWarnings("unchecked")
            Map<String, Object> characters = (Map<String, Object>) updated.getOrDefault("characters", new HashMap<>());
            updateCharactersFromResponse(response, characters);
            updated.put("characters", characters);
            
            // 更新世界设定
            @SuppressWarnings("unchecked")
            Map<String, Object> worldSettings = (Map<String, Object>) updated.getOrDefault("worldSettings", new HashMap<>());
            updateWorldSettingsFromResponse(response, worldSettings);
            updated.put("worldSettings", worldSettings);
            
            // 更新伏笔管理
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) updated.getOrDefault("foreshadowing", new ArrayList<>());
            updateForeshadowingFromResponse(response, foreshadowing);
            updated.put("foreshadowing", foreshadowing);
            
            // 更新情节线索
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) updated.getOrDefault("plotThreads", new ArrayList<>());
            updatePlotThreadsFromResponse(response, plotThreads);
            updated.put("plotThreads", plotThreads);
            
            // 更新重要事件记录
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chapterSummaries = (List<Map<String, Object>>) updated.getOrDefault("chapterSummaries", new ArrayList<>());
            updateChapterSummariesFromResponse(response, chapterSummaries);
            updated.put("chapterSummaries", chapterSummaries);
            
            // 更新地点信息
            @SuppressWarnings("unchecked")
            Map<String, Object> locations = (Map<String, Object>) updated.getOrDefault("locations", new HashMap<>());
            updateLocationsFromResponse(response, locations);
            updated.put("locations", locations);
            
            // 更新角色关系
            @SuppressWarnings("unchecked")
            Map<String, Object> relationships = (Map<String, Object>) updated.getOrDefault("relationships", new HashMap<>());
            updateRelationshipsFromResponse(response, relationships);
            updated.put("relationships", relationships);
            
            // 更新时间线
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> timeline = (List<Map<String, Object>>) updated.getOrDefault("timeline", new ArrayList<>());
            updateTimelineFromResponse(response, timeline);
            updated.put("timeline", timeline);
            
            // 更新版本信息
            Integer version = (Integer) updated.getOrDefault("version", 1);
            updated.put("version", version + 1);
            
            updated.put("memoryUpdateParsed", true);
            
        } catch (Exception e) {
            logger.warn("解析记忆库更新时出现错误: {}", e.getMessage());
            updated.put("memoryUpdateParsed", false);
            updated.put("memoryUpdateError", e.getMessage());
        }
        
        return updated;
    }

    /**
     * 解析一致性检查AI响应
     * 提取详细的一致性评分和问题分析
     */
    private Map<String, Object> parseConsistencyReport(String response, int chapterNumber) {
        Map<String, Object> report = new HashMap<>();
        report.put("chapterNumber", chapterNumber);
        report.put("report", response);
        report.put("checkedAt", LocalDateTime.now());
        
        try {
            // 解析角色一致性检查
            Map<String, Object> characterConsistency = new HashMap<>();
            characterConsistency.put("score", extractScore(response, "角色一致性检查", 8.0));
            characterConsistency.put("issues", extractIssues(response, "角色一致性"));
            characterConsistency.put("details", extractContent(response, "角色性格是否与之前描述一致", "设定一致性检查"));
            report.put("characterConsistency", characterConsistency);
            
            // 解析设定一致性检查
            Map<String, Object> settingConsistency = new HashMap<>();
            settingConsistency.put("score", extractScore(response, "设定一致性检查", 8.0));
            settingConsistency.put("issues", extractIssues(response, "设定一致性"));
            settingConsistency.put("details", extractContent(response, "世界观设定是否有矛盾", "情节逻辑检查"));
            report.put("settingConsistency", settingConsistency);
            
            // 解析情节逻辑检查
            Map<String, Object> plotLogic = new HashMap<>();
            plotLogic.put("score", extractScore(response, "情节逻辑检查", 8.0));
            plotLogic.put("issues", extractIssues(response, "情节逻辑"));
            plotLogic.put("details", extractContent(response, "事件发展是否符合逻辑", "前文关联检查"));
            report.put("plotLogic", plotLogic);
            
            // 解析前文关联检查
            Map<String, Object> contextConnection = new HashMap<>();
            contextConnection.put("score", extractScore(response, "前文关联检查", 8.0));
            contextConnection.put("issues", extractIssues(response, "前文关联"));
            contextConnection.put("details", extractContent(response, "是否与前文呼应", "问题识别与建议"));
            report.put("contextConnection", contextConnection);
            
            // 计算综合评分
            double characterScore = (Double) characterConsistency.get("score");
            double settingScore = (Double) settingConsistency.get("score");
            double plotScore = (Double) plotLogic.get("score");
            double contextScore = (Double) contextConnection.get("score");
            double overallScore = (characterScore + settingScore + plotScore + contextScore) / 4.0;
            
            report.put("overallScore", Math.round(overallScore * 100.0) / 100.0);
            
            // 解析问题和建议
            Map<String, Object> problemsAndSuggestions = new HashMap<>();
            problemsAndSuggestions.put("identifiedProblems", extractProblems(response));
            problemsAndSuggestions.put("suggestions", extractSuggestions(response));
            problemsAndSuggestions.put("riskAssessment", extractContent(response, "风险评估", "后续注意事项"));
            problemsAndSuggestions.put("futureAttention", extractContent(response, "后续注意事项", ""));
            report.put("problemsAndSuggestions", problemsAndSuggestions);
            
            // 生成质量等级
            String qualityLevel;
            if (overallScore >= 9.0) {
                qualityLevel = "优秀";
            } else if (overallScore >= 8.0) {
                qualityLevel = "良好";
            } else if (overallScore >= 7.0) {
                qualityLevel = "合格";
            } else if (overallScore >= 6.0) {
                qualityLevel = "需改进";
            } else {
                qualityLevel = "待修正";
            }
            report.put("qualityLevel", qualityLevel);
            
            report.put("consistencyParsed", true);
            
        } catch (Exception e) {
            logger.warn("解析一致性报告时出现错误: {}", e.getMessage());
            report.put("overallScore", 8.0); // 默认评分
            report.put("consistencyParsed", false);
            report.put("consistencyError", e.getMessage());
        }
        
        return report;
    }

    /**
     * 解析智能建议AI响应
     * 提取各类创作建议和风险预警
     */
    private Map<String, Object> parseIntelligentSuggestions(String response, int currentChapter) {
        Map<String, Object> suggestions = new HashMap<>();
        suggestions.put("currentChapter", currentChapter);
        suggestions.put("generatedAt", LocalDateTime.now());
        
        // 如果AI响应为空或异常，返回默认建议
        if (response == null || response.trim().isEmpty()) {
            logger.warn("AI建议响应为空，返回默认建议");
            suggestions.put("suggestions", createDefaultSuggestions(currentChapter));
            return suggestions;
        }
        
        // 将原始响应也保存，用于调试
        suggestions.put("rawResponse", response);
        
        try {
            // 解析剧情发展建议
            Map<String, Object> plotSuggestions = new HashMap<>();
            plotSuggestions.put("mainlineAdvice", extractContent(response, "主线推进建议", "支线发展机会"));
            plotSuggestions.put("sublineOpportunities", extractContent(response, "支线发展机会", "冲突升级时机"));
            plotSuggestions.put("conflictTiming", extractContent(response, "冲突升级时机", "高潮安排建议"));
            plotSuggestions.put("climaxPlanning", extractContent(response, "高潮安排建议", "角色发展建议"));
            suggestions.put("plotSuggestions", plotSuggestions);
            
            // 解析角色发展建议
            Map<String, Object> characterSuggestions = new HashMap<>();
            characterSuggestions.put("existingCharacterDevelopment", extractContent(response, "现有角色深化方向", "新角色引入时机"));
            characterSuggestions.put("newCharacterTiming", extractContent(response, "新角色引入时机", "角色关系发展"));
            characterSuggestions.put("relationshipDevelopment", extractContent(response, "角色关系发展", "角色弧线完善"));
            characterSuggestions.put("characterArcImprovement", extractContent(response, "角色弧线完善", "伏笔管理建议"));
            suggestions.put("characterSuggestions", characterSuggestions);
            
            // 解析伏笔管理建议
            Map<String, Object> foreshadowingSuggestions = new HashMap<>();
            foreshadowingSuggestions.put("pendingForeshadowing", extractContent(response, "待回收的伏笔提醒", "新伏笔埋设机会"));
            foreshadowingSuggestions.put("newForeshadowingOpportunities", extractContent(response, "新伏笔埋设机会", "伏笔回收时机建议"));
            foreshadowingSuggestions.put("resolutionTiming", extractContent(response, "伏笔回收时机建议", "悬念制造技巧"));
            foreshadowingSuggestions.put("suspenseTechniques", extractContent(response, "悬念制造技巧", "节奏控制建议"));
            suggestions.put("foreshadowingSuggestions", foreshadowingSuggestions);
            
            // 解析节奏控制建议
            Map<String, Object> pacingSuggestions = new HashMap<>();
            pacingSuggestions.put("currentPaceAssessment", extractContent(response, "当前节奏评估", "节奏调整建议"));
            pacingSuggestions.put("paceAdjustmentAdvice", extractContent(response, "节奏调整建议", "张弛有度安排"));
            pacingSuggestions.put("tensionManagement", extractContent(response, "张弛有度安排", "读者期待管理"));
            pacingSuggestions.put("readerExpectationManagement", extractContent(response, "读者期待管理", "创意灵感建议"));
            suggestions.put("pacingSuggestions", pacingSuggestions);
            
            // 解析创意灵感建议
            Map<String, Object> creativeSuggestions = new HashMap<>();
            creativeSuggestions.put("breakthroughPlotIdeas", extractContent(response, "突破性情节点子", "意外转折机会"));
            creativeSuggestions.put("unexpectedTwists", extractContent(response, "意外转折机会", "情感共鸣点设计"));
            creativeSuggestions.put("emotionalResonance", extractContent(response, "情感共鸣点设计", "独特元素融入"));
            creativeSuggestions.put("uniqueElementIntegration", extractContent(response, "独特元素融入", "风险预警"));
            suggestions.put("creativeSuggestions", creativeSuggestions);
            
            // 解析风险预警
            Map<String, Object> riskWarnings = new HashMap<>();
            riskWarnings.put("logicalIssues", extractContent(response, "潜在的逻辑问题", "可能的读者疲劳点"));
            riskWarnings.put("readerFatiguePoints", extractContent(response, "可能的读者疲劳点", "需要注意的一致性"));
            riskWarnings.put("consistencyAttention", extractContent(response, "需要注意的一致性", "建议避免的套路"));
            riskWarnings.put("clichesToAvoid", extractContent(response, "建议避免的套路", ""));
            suggestions.put("riskWarnings", riskWarnings);
            
            suggestions.put("suggestionsParsed", true);
            
        } catch (Exception e) {
            logger.warn("解析智能建议时出现错误: {}", e.getMessage());
            suggestions.put("suggestionsParsed", false);
            suggestions.put("suggestionsError", e.getMessage());
            // 提供默认建议作为备选
            suggestions.put("suggestions", createDefaultSuggestions(currentChapter));
        }
        
        return suggestions;
    }

    private List<Map<String, Object>> parseProactiveReminders(String response, int currentChapter) {
        List<Map<String, Object>> reminders = new ArrayList<>();
        Map<String, Object> reminder = new HashMap<>();
        reminder.put("type", "foreshadowing");
        reminder.put("priority", "high");
        reminder.put("content", response);
        reminders.add(reminder);
        return reminders;
    }

    // ================================
    // 辅助方法 - 上下文构建
    // ================================
    
    /**
     * 构建上下文摘要
     */
    private String buildContextSummary(Map<String, Object> memoryBank, int chapterNumber) {
        StringBuilder summary = new StringBuilder();
        
        try {
            // 最近章节摘要
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chapterSummaries = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("chapterSummaries", new ArrayList<>());
            
            int startIdx = Math.max(0, chapterSummaries.size() - 5); // 最近5章
            for (int i = startIdx; i < chapterSummaries.size(); i++) {
                Map<String, Object> chapterSummary = chapterSummaries.get(i);
                summary.append("第").append(chapterSummary.get("chapterNumber"))
                       .append("章：").append(chapterSummary.get("summary"))
                       .append("\n");
            }
            
            // 主要情节线索状态
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("plotThreads", new ArrayList<>());
            
            summary.append("\n当前活跃情节线：\n");
            for (Map<String, Object> thread : plotThreads) {
                if ("active".equals(thread.get("status"))) {
                    summary.append("- ").append(thread.get("title"))
                           .append("：").append(thread.get("description"))
                           .append("\n");
                }
            }
            
        } catch (Exception e) {
            logger.warn("构建上下文摘要失败: {}", e.getMessage());
            summary.append("第").append(chapterNumber-1).append("章前情回顾...");
        }
        
        return summary.length() > 0 ? summary.toString() : "初始章节，暂无上下文";
    }
    
    /**
     * 构建角色档案
     */
    private String buildCharacterProfiles(Map<String, Object> memoryBank) {
        StringBuilder profiles = new StringBuilder();
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> characters = (Map<String, Object>) 
                memoryBank.getOrDefault("characters", new HashMap<>());
            
            for (Map.Entry<String, Object> entry : characters.entrySet()) {
                String name = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> character = (Map<String, Object>) entry.getValue();
                
                if ("active".equals(character.get("status"))) {
                    profiles.append("【").append(name).append("】\n");
                    profiles.append("角色定位：").append(character.get("role")).append("\n");
                    profiles.append("性格特点：").append(character.get("personality")).append("\n");
                    profiles.append("当前状态：").append(character.getOrDefault("currentState", "正常")).append("\n\n");
                }
            }
            
        } catch (Exception e) {
            logger.warn("构建角色档案失败: {}", e.getMessage());
            profiles.append("角色档案暂无");
        }
        
        return profiles.length() > 0 ? profiles.toString() : "暂无角色信息";
    }
    
    /**
     * 构建伏笔提醒
     */
    private String buildForeshadowingReminders(Map<String, Object> memoryBank, int chapterNumber) {
        StringBuilder reminders = new StringBuilder();
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("foreshadowing", new ArrayList<>());
            
            for (Map<String, Object> item : foreshadowing) {
                if ("active".equals(item.get("status"))) {
                    reminders.append("【伏笔】").append(item.get("content")).append("\n");
                    if (item.get("plannedChapter") != null) {
                        reminders.append("计划回收：第").append(item.get("plannedChapter")).append("章\n");
                    }
                    reminders.append("重要性：").append(item.getOrDefault("importance", "medium")).append("\n\n");
                }
            }
            
        } catch (Exception e) {
            logger.warn("构建伏笔提醒失败: {}", e.getMessage());
            reminders.append("伏笔提醒暂无");
        }
        
        return reminders.length() > 0 ? reminders.toString() : "暂无活跃伏笔";
    }
    
    /**
     * 估算字数（中文优化）
     */
    private int estimateWordCount(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        // 中文字数统计，去除空格和标点符号
        return content.replaceAll("[\\s\\p{Punct}]", "").length();
    }
    
    /**
     * 构建对话上下文
     */
    private String buildDialogueContext(Novel novel, Map<String, Object> memoryBank, List<Map<String, Object>> chatHistory) {
        StringBuilder context = new StringBuilder();
        
        context.append("小说《").append(novel.getTitle()).append("》\n");
        context.append("类型：").append(novel.getGenre()).append("\n");
        context.append("当前状态：").append(novel.getStatus()).append("\n\n");
        
        // 简要记忆库信息
        @SuppressWarnings("unchecked")
        Map<String, Object> characters = (Map<String, Object>) memoryBank.getOrDefault("characters", new HashMap<>());
        context.append("主要角色数：").append(characters.size()).append("\n");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) memoryBank.getOrDefault("plotThreads", new ArrayList<>());
        long activePlots = plotThreads.stream().filter(p -> "active".equals(p.get("status"))).count();
        context.append("活跃情节线：").append(activePlots).append("\n\n");
        
        return context.toString();
    }
    
    /**
     * 格式化对话历史
     */
    private String formatChatHistory(List<Map<String, Object>> chatHistory) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return "暂无对话历史";
        }
        
        StringBuilder history = new StringBuilder();
        
        // 只显示最近5条对话
        int startIdx = Math.max(0, chatHistory.size() - 5);
        
        for (int i = startIdx; i < chatHistory.size(); i++) {
            Map<String, Object> chat = chatHistory.get(i);
            history.append("用户：").append(chat.get("userMessage")).append("\n");
            history.append("AI：").append(chat.get("aiResponse")).append("\n\n");
        }
        
        return history.toString();
    }
    
    /**
     * 从响应中提取行动项
     */
    private List<String> extractActionItems(String response) {
        List<String> actions = new ArrayList<>();
        
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.contains("建议") || line.contains("需要") || line.contains("可以") || line.contains("应该")) {
                    actions.add(cleanText(line));
                }
            }
        } catch (Exception e) {
            logger.warn("提取行动项失败: {}", e.getMessage());
        }
        
        return actions;
    }
    
    private String parseAIResponse(String responseBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map responseMap = om.readValue(responseBody, Map.class);
            Object choicesObj = responseMap.get("choices");
            if (choicesObj instanceof List) {
                List choices = (List) choicesObj;
                if (!choices.isEmpty() && choices.get(0) instanceof Map) {
                    Map firstChoice = (Map) choices.get(0);
                    Object messageObj = firstChoice.get("message");
                    if (messageObj instanceof Map) {
                        Object content = ((Map) messageObj).get("content");
                        if (content instanceof String) {
                            return (String) content;
                        }
                    }
                }
            }
            return responseBody;
        } catch (Exception e) {
            logger.warn("解析AI响应失败，返回原始内容", e);
            return responseBody;
        }
    }

    // ================================
    // 用户决策执行方法
    // ================================
    
    /**
     * 调整主线剧情
     */
    private Map<String, Object> adjustMainPlot(Novel novel, Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String adjustmentType = (String) params.get("adjustmentType");
            String newDirection = (String) params.get("newDirection");
            String reason = (String) params.get("reason");
            
            String adjustPrompt = String.format(
                "你是【主线调整AI】，负责根据用户意图调整主线剧情。\n\n" +
                "小说：%s\n" +
                "调整类型：%s\n" +
                "新方向：%s\n" +
                "调整原因：%s\n" +
                "当前记忆库：%s\n\n" +
                "请分析该调整的可行性和影响，并提供具体的实施方案。",
                novel.getTitle(), adjustmentType, newDirection, reason, memoryBank.toString()
            );
            
            String aiResponse = callAI("PLOT_ADJUSTER", adjustPrompt);
            
            result.put("success", true);
            result.put("adjustmentType", adjustmentType);
            result.put("newDirection", newDirection);
            result.put("aiAnalysis", aiResponse);
            result.put("memoryBankUpdates", extractMemoryUpdates(aiResponse));
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 添加新支线
     */
    private Map<String, Object> addSubPlot(Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String subplotTitle = (String) params.get("title");
            String subplotDescription = (String) params.get("description");
            String triggerChapter = (String) params.get("triggerChapter");
            String relatedCharacters = (String) params.get("relatedCharacters");
            
            // 更新记忆库中的情节线索
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("plotThreads", new ArrayList<>());
            
            Map<String, Object> newSubplot = new HashMap<>();
            newSubplot.put("id", "subplot_" + System.currentTimeMillis());
            newSubplot.put("title", subplotTitle);
            newSubplot.put("description", subplotDescription);
            newSubplot.put("status", "active");
            newSubplot.put("triggerChapter", triggerChapter);
            newSubplot.put("relatedCharacters", Arrays.asList(relatedCharacters.split(",")));
            newSubplot.put("createdAt", LocalDateTime.now());
            
            plotThreads.add(newSubplot);
            
            result.put("success", true);
            result.put("newSubplot", newSubplot);
            result.put("message", "支线「" + subplotTitle + "」已添加");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 移除支线
     */
    private Map<String, Object> removeSubPlot(Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String subplotId = (String) params.get("subplotId");
            String reason = (String) params.get("reason");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plotThreads = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("plotThreads", new ArrayList<>());
            
            boolean removed = plotThreads.removeIf(subplot -> subplotId.equals(subplot.get("id")));
            
            if (removed) {
                result.put("success", true);
                result.put("message", "支线已移除：" + reason);
            } else {
                result.put("success", false);
                result.put("message", "未找到指定支线");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 引入新角色
     */
    private Map<String, Object> introduceNewCharacter(Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String characterName = (String) params.get("name");
            String characterRole = (String) params.get("role");
            String personality = (String) params.get("personality");
            String background = (String) params.get("background");
            String introChapter = (String) params.get("introChapter");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> characters = (Map<String, Object>) 
                memoryBank.getOrDefault("characters", new HashMap<>());
            
            Map<String, Object> newCharacter = new HashMap<>();
            newCharacter.put("name", characterName);
            newCharacter.put("role", characterRole);
            newCharacter.put("personality", personality);
            newCharacter.put("background", background);
            newCharacter.put("introChapter", introChapter);
            newCharacter.put("status", "active");
            newCharacter.put("relationships", new HashMap<>());
            newCharacter.put("developmentArc", new ArrayList<>());
            newCharacter.put("createdAt", LocalDateTime.now());
            
            characters.put(characterName, newCharacter);
            
            result.put("success", true);
            result.put("newCharacter", newCharacter);
            result.put("message", "新角色「" + characterName + "」已加入记忆库");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 修改角色
     */
    private Map<String, Object> modifyCharacter(Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String characterName = (String) params.get("characterName");
            String modificationType = (String) params.get("modificationType");
            String newValue = (String) params.get("newValue");
            String reason = (String) params.get("reason");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> characters = (Map<String, Object>) 
                memoryBank.getOrDefault("characters", new HashMap<>());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> character = (Map<String, Object>) characters.get(characterName);
            
            if (character != null) {
                // 记录修改历史
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> developmentArc = (List<Map<String, Object>>) 
                    character.getOrDefault("developmentArc", new ArrayList<>());
                
                Map<String, Object> modification = new HashMap<>();
                modification.put("type", modificationType);
                modification.put("oldValue", character.get(modificationType));
                modification.put("newValue", newValue);
                modification.put("reason", reason);
                modification.put("modifiedAt", LocalDateTime.now());
                
                developmentArc.add(modification);
                character.put("developmentArc", developmentArc);
                
                // 应用修改
                character.put(modificationType, newValue);
                character.put("lastModified", LocalDateTime.now());
                
                result.put("success", true);
                result.put("modification", modification);
                result.put("message", "角色「" + characterName + "」" + modificationType + "已更新");
            } else {
                result.put("success", false);
                result.put("message", "未找到角色：" + characterName);
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 设置伏笔
     */
    private Map<String, Object> setForeshadowing(Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String foreshadowingId = (String) params.get("id");
            String content = (String) params.get("content");
            String plannedChapter = (String) params.get("plannedChapter");
            String importance = (String) params.get("importance");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("foreshadowing", new ArrayList<>());
            
            Map<String, Object> newForeshadowing = new HashMap<>();
            newForeshadowing.put("id", foreshadowingId != null ? foreshadowingId : "foreshadow_" + System.currentTimeMillis());
            newForeshadowing.put("content", content);
            newForeshadowing.put("status", "active");
            newForeshadowing.put("plannedChapter", plannedChapter);
            newForeshadowing.put("importance", importance);
            newForeshadowing.put("createdAt", LocalDateTime.now());
            
            foreshadowing.add(newForeshadowing);
            
            result.put("success", true);
            result.put("newForeshadowing", newForeshadowing);
            result.put("message", "伏笔「" + content + "」已设置");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 解决伏笔
     */
    private Map<String, Object> resolveForeshadowing(Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String foreshadowingId = (String) params.get("foreshadowingId");
            String resolutionContent = (String) params.get("resolutionContent");
            String resolutionChapter = (String) params.get("resolutionChapter");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foreshadowing = (List<Map<String, Object>>) 
                memoryBank.getOrDefault("foreshadowing", new ArrayList<>());
            
            boolean resolved = false;
            for (Map<String, Object> item : foreshadowing) {
                if (foreshadowingId.equals(item.get("id"))) {
                    item.put("status", "resolved");
                    item.put("resolutionContent", resolutionContent);
                    item.put("resolutionChapter", resolutionChapter);
                    item.put("resolvedAt", LocalDateTime.now());
                    resolved = true;
                    break;
                }
            }
            
            if (resolved) {
                result.put("success", true);
                result.put("message", "伏笔已解决：" + resolutionContent);
            } else {
                result.put("success", false);
                result.put("message", "未找到指定伏笔");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 改变节奏
     */
    private Map<String, Object> changePace(Map<String, Object> memoryBank, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String paceType = (String) params.get("paceType"); // "accelerate", "decelerate", "balance"
            String targetChapters = (String) params.get("targetChapters");
            String reason = (String) params.get("reason");
            
            String pacePrompt = String.format(
                "你是【节奏控制AI】，负责调整小说的节奏和氛围。\n\n" +
                "节奏调整类型：%s\n" +
                "目标章节：%s\n" +
                "调整原因：%s\n" +
                "当前记忆库：%s\n\n" +
                "请分析如何实现这种节奏调整，并提供具体的实施建议。",
                paceType, targetChapters, reason, memoryBank.toString()
            );
            
            String aiResponse = callAI("PACE_CONTROLLER", pacePrompt);
            
            result.put("success", true);
            result.put("paceType", paceType);
            result.put("targetChapters", targetChapters);
            result.put("aiGuidance", aiResponse);
            result.put("implementationSuggestions", extractImplementationSuggestions(aiResponse));
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    // ================================
    // 辅助方法 - 文本解析和JSON处理
    // ================================
    
    /**
     * 从响应中提取指定内容
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
     * 从响应中提取JSON内容
     */
    private String extractJSONFromResponse(String response) {
        try {
            // 查找```json标记
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
            
            // 查找直接的JSON对象
            int braceStart = response.indexOf("{");
            int braceEnd = response.lastIndexOf("}");
            if (braceStart != -1 && braceEnd != -1 && braceStart < braceEnd) {
                return response.substring(braceStart, braceEnd + 1);
            }
            
            // 查找JSON数组
            int bracketStart = response.indexOf("[");
            int bracketEnd = response.lastIndexOf("]");
            if (bracketStart != -1 && bracketEnd != -1 && bracketStart < bracketEnd) {
                return response.substring(bracketStart, bracketEnd + 1);
            }
            
        } catch (Exception e) {
            logger.warn("提取JSON失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 安全获取字符串值
     */
    private String getStringValue(Map map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * 安全获取整数值
     */
    private int getIntValue(Map map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 安全获取列表值
     */
    @SuppressWarnings("unchecked")
    private List<String> getListValue(Map map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }
    
    /**
     * 创建阶段对象
     */
    private Map<String, String> createPhase(String name, String description, String chapters) {
        Map<String, String> phase = new HashMap<>();
        phase.put("name", name);
        phase.put("description", description);
        phase.put("chapters", chapters);
        return phase;
    }
    
    /**
     * 提取章节标题
     */
    private String extractChapterTitle(String line) {
        // 从包含章节信息的行中提取标题
        if (line.contains("：")) {
            return line.substring(line.indexOf("：") + 1).trim();
        } else if (line.contains("章")) {
            return line.trim();
        }
        return "未命名章节";
    }
    
    /**
     * 清理文本
     */
    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("^[-•●○\\s]+", "").trim();
    }
    
    /**
     * 生成默认章节列表
     */
    private List<Map<String, Object>> generateDefaultChapterList(int startChapter, int count) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> chapter = new HashMap<>();
            int chapterNumber = startChapter + i;
            chapter.put("chapterNumber", chapterNumber);
            chapter.put("title", "第" + chapterNumber + "章");
            chapter.put("type", "对话");
            chapter.put("coreEvent", "待定事件");
            chapter.put("characterDevelopment", Arrays.asList("角色发展"));
            chapter.put("foreshadowing", "");
            chapter.put("newCharacters", Arrays.asList());
            chapter.put("plotConnections", Arrays.asList());
            chapter.put("estimatedWords", 1000);
            chapter.put("priority", "medium");
            chapter.put("mood", "平衡");
            chapters.add(chapter);
        }
        
        return chapters;
    }
    
    /**
     * 提取角色建议
     */
    private List<Map<String, String>> extractCharacterSuggestions(String response) {
        List<Map<String, String>> suggestions = new ArrayList<>();
        
        try {
            String content = extractContent(response, "建议引入的新角色", "新的世界观设定补充");
            String[] lines = content.split("\n");
            
            for (String line : lines) {
                if (line.contains("名称") || line.contains("姓名")) {
                    Map<String, String> suggestion = new HashMap<>();
                    suggestion.put("name", extractNameFromLine(line));
                    suggestion.put("role", extractRoleFromLine(line));
                    suggestion.put("timing", extractTimingFromLine(line));
                    suggestions.add(suggestion);
                }
            }
        } catch (Exception e) {
            logger.warn("提取角色建议失败: {}", e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * 从响应中提取评分
     */
    private Double extractScore(String response, String section, Double defaultScore) {
        try {
            String content = extractContent(response, section, "");
            // 查找评分模式
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*分");
            java.util.regex.Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception e) {
            logger.warn("提取评分失败: {}", e.getMessage());
        }
        return defaultScore;
    }
    
    /**
     * 提取问题列表
     */
    private List<String> extractIssues(String response, String section) {
        List<String> issues = new ArrayList<>();
        try {
            String content = extractContent(response, section, "");
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("问题") || line.contains("矛盾") || line.contains("不一致")) {
                    issues.add(cleanText(line));
                }
            }
        } catch (Exception e) {
            logger.warn("提取问题列表失败: {}", e.getMessage());
        }
        return issues;
    }
    
    /**
     * 提取问题列表
     */
    private List<String> extractProblems(String response) {
        List<String> problems = new ArrayList<>();
        try {
            String content = extractContent(response, "发现的具体问题", "修改建议");
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty() && !line.contains("标题")) {
                    problems.add(cleanText(line));
                }
            }
        } catch (Exception e) {
            logger.warn("提取问题失败: {}", e.getMessage());
        }
        return problems;
    }
    
    /**
     * 提取建议列表
     */
    private List<String> extractSuggestions(String response) {
        List<String> suggestions = new ArrayList<>();
        try {
            String content = extractContent(response, "修改建议", "风险评估");
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty() && !line.contains("标题")) {
                    suggestions.add(cleanText(line));
                }
            }
        } catch (Exception e) {
            logger.warn("提取建议失败: {}", e.getMessage());
        }
        return suggestions;
    }
    
    // 更多辅助方法的占位符实现...
    private void updateCharactersFromResponse(String response, Map<String, Object> characters) {
        // 智能更新角色信息的逻辑
    }
    
    private void updateWorldSettingsFromResponse(String response, Map<String, Object> worldSettings) {
        // 智能更新世界设定的逻辑
    }
    
    private void updateForeshadowingFromResponse(String response, List<Map<String, Object>> foreshadowing) {
        // 智能更新伏笔信息的逻辑
    }
    
    private void updatePlotThreadsFromResponse(String response, List<Map<String, Object>> plotThreads) {
        // 智能更新情节线索的逻辑
    }
    
    private void updateChapterSummariesFromResponse(String response, List<Map<String, Object>> chapterSummaries) {
        // 智能更新章节摘要的逻辑
    }
    
    private void updateLocationsFromResponse(String response, Map<String, Object> locations) {
        // 智能更新地点信息的逻辑
    }
    
    private void updateRelationshipsFromResponse(String response, Map<String, Object> relationships) {
        // 智能更新角色关系的逻辑
    }
    
    private void updateTimelineFromResponse(String response, List<Map<String, Object>> timeline) {
        // 智能更新时间线的逻辑
    }
    
    private String extractNameFromLine(String line) { 
        // 智能提取角色名称
        if (line.contains("：")) {
            String[] parts = line.split("：");
            if (parts.length > 1) {
                return parts[1].trim().split("[，,\\s]")[0];
            }
        }
        return ""; 
    }
    
    private String extractRoleFromLine(String line) { 
        // 智能提取角色定位
        if (line.contains("定位") || line.contains("角色")) {
            return extractContent(line, "定位", "").trim();
        }
        return ""; 
    }
    
    private String extractTimingFromLine(String line) { 
        // 智能提取时机
        if (line.contains("时机") || line.contains("章")) {
            return extractContent(line, "", "").trim();
        }
        return ""; 
    }
    
    private String extractSubplotTitle(String line) {
        // 从支线描述中提取标题
        if (line.contains("：")) {
            return line.substring(0, line.indexOf("：")).trim();
        }
        return "";
    }
    
    private String extractSubplotDescription(String line) {
        // 从支线描述中提取内容
        if (line.contains("：")) {
            return line.substring(line.indexOf("：") + 1).trim();
        }
        return line.trim();
    }
    
    private String extractSubplotTiming(String line) {
        // 从支线描述中提取时机信息
        if (line.contains("第") && line.contains("章")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("第\\d+章");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "适时触发";
    }
    
    private List<String> extractMemoryUpdates(String response) { 
        List<String> updates = new ArrayList<>();
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.contains("更新") || line.contains("修改") || line.contains("调整")) {
                updates.add(cleanText(line));
            }
        }
        return updates;
    }
    
    private List<String> extractImplementationSuggestions(String response) { 
        List<String> suggestions = new ArrayList<>();
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.contains("建议") || line.contains("可以") || line.contains("应该")) {
                suggestions.add(cleanText(line));
            }
        }
        return suggestions;
    }
    
    /**
     * 解析JSON格式的大纲结构
     */
    private Map<String, Object> parseJSONOutline(String jsonContent) {
        Map<String, Object> outline = new HashMap<>();
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonOutline = mapper.readValue(jsonContent, Map.class);
            
            // 直接使用AI生成的JSON结构
            outline.putAll(jsonOutline);
            
            logger.info("✅ 成功解析AI生成的JSON大纲结构");
            
        } catch (Exception e) {
            logger.warn("JSON大纲解析失败: {}", e.getMessage());
            // 如果JSON解析失败，回退到文本解析
            parseOutlineFromText(jsonContent, outline);
        }
        
        return outline;
    }
    
    /**
     * 从文本中解析大纲结构（备选方案）
     */
    private void parseOutlineFromText(String response, Map<String, Object> outline) {
        logger.info("🔄 使用文本解析方式处理大纲");
        
        // 解析主线结构
        Map<String, Object> mainStructure = new HashMap<>();
        List<Map<String, Object>> phases = new ArrayList<>();
        
        // 智能提取阶段信息
        String[] lines = response.split("\n");
        Map<String, Object> currentPhase = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.matches(".*阶段.*|.*第.*部分.*|.*篇.*")) {
                // 保存前一个阶段
                if (currentPhase != null) {
                    phases.add(currentPhase);
                }
                // 开始新阶段
                currentPhase = new HashMap<>();
                currentPhase.put("name", extractPhaseNameFromLine(line));
                currentPhase.put("description", "");
                currentPhase.put("chapters", "");
                currentPhase.put("keyEvents", new ArrayList<String>());
            } else if (currentPhase != null && !line.isEmpty()) {
                // 补充阶段信息
                if (line.contains("章") || line.matches(".*\\d+-\\d+.*")) {
                    currentPhase.put("chapters", line);
                } else if (line.contains("事件") || line.contains("情节")) {
                    @SuppressWarnings("unchecked")
                    List<String> keyEvents = (List<String>) currentPhase.get("keyEvents");
                    keyEvents.add(line);
                } else {
                    currentPhase.put("description", currentPhase.get("description") + " " + line);
                }
            }
        }
        
        // 保存最后一个阶段
        if (currentPhase != null) {
            phases.add(currentPhase);
        }
        
        // 如果没有解析出阶段，创建基础结构
        if (phases.isEmpty()) {
            phases = createBasicPhaseStructure(response);
        }
        
        mainStructure.put("phases", phases);
        outline.put("mainStructure", mainStructure);
        
        // 解析其他核心要素
        Map<String, Object> coreElements = new HashMap<>();
        coreElements.put("protagonist", extractContent(response, "主角", "世界"));
        coreElements.put("worldSetting", extractContent(response, "世界", "冲突"));
        coreElements.put("mainConflict", extractContent(response, "冲突", ""));
        coreElements.put("uniqueElements", extractUniqueElements(response));
        outline.put("coreElements", coreElements);
        
        // 解析扩展规划
        Map<String, Object> extensionPlan = new HashMap<>();
        extensionPlan.put("subplotDirections", extractSubplotDirections(response));
        extensionPlan.put("characterIntroTiming", extractContent(response, "角色", "伏笔"));
        extensionPlan.put("foreshadowingPlan", extractContent(response, "伏笔", ""));
        outline.put("extensionPlan", extensionPlan);
        
        // 基础章节分配
        Map<String, Object> chapterAllocation = new HashMap<>();
        chapterAllocation.put("totalChapters", 1000);
        chapterAllocation.put("phaseDistribution", phases);
        outline.put("chapterAllocation", chapterAllocation);
    }
    
    /**
     * 从行中提取阶段名称
     */
    private String extractPhaseNameFromLine(String line) {
        // 简单的提取逻辑，可以进一步完善
        if (line.contains("：")) {
            return line.substring(0, line.indexOf("：")).trim();
        }
        return line.replaceAll("[0-9\\.]", "").trim();
    }
    
    /**
     * 创建基础阶段结构（最后的备选方案）
     */
    private List<Map<String, Object>> createBasicPhaseStructure(String response) {
        List<Map<String, Object>> phases = new ArrayList<>();
        
        // 基于响应内容的长度和复杂度，智能分配阶段
        int contentLength = response.length();
        int phaseCount = contentLength > 2000 ? 5 : 3;
        int chapterPerPhase = 1000 / phaseCount;
        
        for (int i = 0; i < phaseCount; i++) {
            Map<String, Object> phase = new HashMap<>();
            phase.put("name", "第" + (i + 1) + "阶段");
            phase.put("description", "基于AI响应生成的阶段" + (i + 1));
            phase.put("chapters", (i * chapterPerPhase + 1) + "-" + ((i + 1) * chapterPerPhase));
            phase.put("keyEvents", Arrays.asList("待AI详细规划"));
            phases.add(phase);
        }
        
        return phases;
    }
    
    /**
     * 提取独特元素
     */
    private List<String> extractUniqueElements(String response) {
        List<String> elements = new ArrayList<>();
        String[] lines = response.split("\n");
        
        for (String line : lines) {
            if (line.contains("独特") || line.contains("特殊") || line.contains("创新")) {
                elements.add(cleanText(line));
            }
        }
        
        if (elements.isEmpty()) {
            elements.add("待AI进一步分析");
        }
        
        return elements;
    }
    
    /**
     * 提取支线方向
     */
    private List<Map<String, Object>> extractSubplotDirections(String response) {
        List<Map<String, Object>> subplots = new ArrayList<>();
        
        // 从响应中智能解析支线信息
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.contains("支线") || line.contains("线索")) {
                String title = extractSubplotTitle(line);
                String description = extractSubplotDescription(line);
                String timing = extractSubplotTiming(line);
                if (!title.isEmpty()) {
                    subplots.add(createSubplot(title, description, timing));
                }
            }
        }
        
        // 如果没有解析出支线，说明AI没有提供具体支线信息
        if (subplots.isEmpty()) {
            logger.warn("AI响应中未包含具体的支线信息");
        }
        
        return subplots;
    }
    
    /**
     * 创建默认建议（当AI解析失败时使用）
     */
    private List<Map<String, Object>> createDefaultSuggestions(int currentChapter) {
        List<Map<String, Object>> defaultSuggestions = new ArrayList<>();
        
        // 根据章节数提供不同的建议
        if (currentChapter <= 5) {
            // 开篇建议
            defaultSuggestions.add(createSuggestionItem("plot", "建立世界观", 
                "在前几章中充分展示故事的世界观和背景设定，让读者快速融入故事。"));
            defaultSuggestions.add(createSuggestionItem("character", "角色介绍", 
                "逐步介绍主要角色的性格特点和背景，避免信息过载。"));
            defaultSuggestions.add(createSuggestionItem("pacing", "节奏把控", 
                "开篇节奏可以稍快一些，抓住读者注意力。"));
        } else if (currentChapter <= 20) {
            // 发展阶段建议
            defaultSuggestions.add(createSuggestionItem("plot", "冲突升级", 
                "是时候引入更多冲突和挑战，推动情节发展。"));
            defaultSuggestions.add(createSuggestionItem("character", "关系发展", 
                "深化角色之间的关系，可以考虑引入新的角色。"));
            defaultSuggestions.add(createSuggestionItem("foreshadowing", "伏笔埋设", 
                "可以开始埋设一些伏笔，为后续情节做铺垫。"));
        } else {
            // 进阶阶段建议
            defaultSuggestions.add(createSuggestionItem("plot", "情节转折", 
                "考虑加入一些意外的情节转折，增加故事的吸引力。"));
            defaultSuggestions.add(createSuggestionItem("character", "角色成长", 
                "主角应该开始显现出成长和变化。"));
            defaultSuggestions.add(createSuggestionItem("pacing", "节奏变化", 
                "注意调节故事节奏，避免读者疲劳。"));
        }
        
        return defaultSuggestions;
    }
    
    /**
     * 创建建议项目
     */
    private Map<String, Object> createSuggestionItem(String type, String title, String content) {
        Map<String, Object> suggestion = new HashMap<>();
        suggestion.put("type", type);
        suggestion.put("title", title);
        suggestion.put("content", content);
        suggestion.put("priority", "medium");
        suggestion.put("isDefault", true);
        return suggestion;
    }
    
    /**
     * 创建支线对象
     */
    private Map<String, Object> createSubplot(String title, String description, String timing) {
        Map<String, Object> subplot = new HashMap<>();
        subplot.put("title", title);
        subplot.put("description", description);
        subplot.put("triggerTiming", timing);
        return subplot;
    }

    /**
     * 多阶段流式章节写作（新版，推荐）
     * 
     * 流程：
     * 步骤1：剧情构思 - 消化所有重型上下文，生成本章剧情构思
     * 步骤2：视角判断 - 判断是否需要切换视角
     * 步骤3：正式写作 - 根据构思+轻量级上下文生成章节
     * 
     * @param enableTemplateLoop 是否启用模板循环引擎（前端传参）
     */
    public void executeMultiStageStreamingChapterWriting(
            Novel novel, 
            Map<String, Object> chapterPlan, 
            Map<String, Object> memoryBank, 
            String userAdjustment, 
            SseEmitter emitter,
            AIConfigRequest aiConfig,
            Long promptTemplateId,
            Boolean enableTemplateLoop) throws IOException {
        
        // 调用多阶段生成服务
        multiStageChapterGenerationService.executeMultiStageChapterGeneration(
            novel, chapterPlan, memoryBank, userAdjustment, 
            emitter, aiConfig, promptTemplateId, enableTemplateLoop
        );
    }
    
    // ============================================================
    // 以下是旧版章节写作方法（已废弃，使用 executeMultiStageStreamingChapterWriting 代替）
    // ============================================================
    
    /**
     * 流式章节写作（旧版，已废弃）
     * 
     * ⚠️ 已废弃：该方法使用一次性生成，上下文过重，生成质量不佳
     * ✅ 请使用：executeMultiStageStreamingChapterWriting（三步流程：构思→判断→写作）
     * 
     * @deprecated 使用 executeMultiStageStreamingChapterWriting 代替
     */
    @Deprecated
    private void executeStreamingChapterWriting_OLD(
            Novel novel, 
            Map<String, Object> chapterPlan, 
            Map<String, Object> memoryBank, 
            String userAdjustment, 
            SseEmitter emitter,
            AIConfigRequest aiConfig,
            Long promptTemplateId) throws IOException {
        
        try {
            // 发送准备事件
            emitter.send(SseEmitter.event().name("preparing").data("正在构建完整上下文..."));
            
            // 构建完整上下文消息列表（支持自定义提示词模板）
            List<Map<String, String>> contextMessages = contextManagementService.buildFullContextMessages(
                novel, chapterPlan, memoryBank, userAdjustment, promptTemplateId
            );
            
            emitter.send(SseEmitter.event().name("context_ready")
                    .data("构建了 " + contextMessages.size() + " 条上下文消息"));
            
            // 发送开始写作事件
            emitter.send(SseEmitter.event().name("writing").data("开始增强AI写作..."));
            
            // 调用流式AI接口并获取生成的内容
            String generatedContent = callStreamingAIWithContext_OLD(contextMessages, emitter, aiConfig);
            
            // ✅ 写作完成，不再自动更新记忆库（改为前端新建章节时手动触发）
            emitter.send(SseEmitter.event().name("complete").data("写作完成"));
            
            emitter.complete();
            
        } catch (Exception e) {
            logger.error("增强流式写作失败", e);
            emitter.send(SseEmitter.event().name("error").data("写作失败: " + e.getMessage()));
            emitter.completeWithError(e);
        }
    }

    /**
     * 使用完整上下文的流式AI调用（真正的流式）（旧版，已废弃）
     * 
     * @deprecated 已废弃，使用新版多阶段生成
     * @return 生成的完整内容
     */
    @Deprecated
    private String callStreamingAIWithContext_OLD(List<Map<String, String>> contextMessages, SseEmitter emitter, AIConfigRequest aiConfig) throws IOException {
        if (aiConfig == null || !aiConfig.isValid()) {
            throw new IOException("AI配置无效");
        }
        
        String baseUrl = aiConfig.getEffectiveBaseUrl();
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        if (apiKey == null || apiKey.trim().isEmpty() || "your-api-key-here".equals(apiKey)) {
            throw new IOException("API Key未配置");
        }

        // 构建请求体（启用流式）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4000);
        requestBody.put("temperature", 0.9);
        requestBody.put("stream", true); // 启用真正的流式响应
        requestBody.put("messages", contextMessages);

        try {
            String url = aiConfig.getApiUrl();
            logger.info("🌐 调用AI流式写作接口: {}", url);
            
            // 使用RestTemplate进行流式读取
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(120000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            // 流式接口必须设置Accept为text/event-stream
            headers.set("Accept", "text/event-stream");

            StringBuilder fullContent = new StringBuilder();

            // 使用ResponseExtractor进行真正的流式读取
            restTemplate.execute(url, HttpMethod.POST, 
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(requestBody));
                },
                response -> {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                        
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break; // 流式响应结束
                                }
                                
                                try {
                                    // 解析JSON数据
                                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                    java.util.Map dataMap = om.readValue(data, java.util.Map.class);
                                    
                                    Object choicesObj = dataMap.get("choices");
                                    if (choicesObj instanceof java.util.List) {
                                        java.util.List choices = (java.util.List) choicesObj;
                                        if (!choices.isEmpty() && choices.get(0) instanceof java.util.Map) {
                                            java.util.Map firstChoice = (java.util.Map) choices.get(0);
                                            Object deltaObj = firstChoice.get("delta");
                                            if (deltaObj instanceof java.util.Map) {
                                                Object content = ((java.util.Map) deltaObj).get("content");
                                                if (content instanceof String && !((String) content).trim().isEmpty()) {
                                                    String chunk = (String) content;
                                                    fullContent.append(chunk);
                                                    // 实时发送给前端
                                                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.warn("解析流式数据失败: {}", e.getMessage());
                                }
                            }
                        }
                    } catch (IOException e) {
                        logger.error("读取流式响应失败", e);
                        throw new RuntimeException("读取流式响应失败", e);
                    }
                    return null;
                });
            
            return fullContent.toString();
                
        } catch (Exception e) {
            logger.error("调用完整上下文流式AI接口失败", e);
            throw new IOException("完整上下文AI服务调用失败: " + e.getMessage(), e);
        }
    }

}