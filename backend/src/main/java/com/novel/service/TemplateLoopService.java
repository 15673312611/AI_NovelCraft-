package com.novel.service;

import com.novel.domain.entity.Novel;
import com.novel.entity.NovelTemplateProgress;
import com.novel.enums.TemplateStage;
import com.novel.enums.TemplateType;
import com.novel.repository.NovelRepository;
import com.novel.repository.NovelTemplateProgressRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模板循环引擎服务
 * 核心职责：
 * 1. 管理小说的模板循环进度
 * 2. 在生成剧情构思后，基于当前阶段增强构思
 * 3. 章节生成后异步分析并推进阶段
 */
@Slf4j
@Service
public class TemplateLoopService {
    
    @Autowired
    private NovelTemplateProgressRepository progressRepository;
    
    @Autowired
    private NovelRepository novelRepository;
    
    @Autowired
    private AIWritingService aiWritingService;
    
    /**
     * 获取或初始化小说的模板进度
     * 如果不存在，则根据小说类型初始化默认进度
     */
    @Transactional
    public NovelTemplateProgress getOrInitProgress(Long novelId) {
        NovelTemplateProgress progress = progressRepository.findByNovelId(novelId);
        
        if (progress == null) {
            log.info("初始化小说 {} 的模板进度", novelId);
            progress = initializeProgress(novelId);
        }
        
        return progress;
    }
    
    /**
     * 初始化新的模板进度
     */
    private NovelTemplateProgress initializeProgress(Long novelId) {
        Novel novel = novelRepository.selectById(novelId);
        
        NovelTemplateProgress progress = new NovelTemplateProgress();
        progress.setNovelId(novelId);
        progress.setEnabled(true); // 默认启用
        progress.setCurrentStage(TemplateStage.MOTIVATION.name());
        progress.setLoopNumber(1);
        progress.setStageStartChapter(1);
        progress.setStartChapter(1); // 默认从第1章开始应用
        progress.setLastUpdatedChapter(0);
        
        // 根据小说类型推断模板类型
        if (novel != null) {
            TemplateType templateType = TemplateType.inferFromGenre(novel.getGenre());
            progress.setTemplateType(templateType.name());
        } else {
            progress.setTemplateType(TemplateType.GENERAL.name());
        }
        
        progressRepository.insert(progress);
        log.info("小说 {} 初始化模板类型: {}, 从第 {} 章开始", 
                novelId, progress.getTemplateType(), progress.getStartChapter());
        
        return progress;
    }
    
    /**
     * 增强剧情构思
     * 核心方法：分析现有剧情提取可持续动机，然后基于当前阶段加强
     * 
     * @param novelId 小说ID
     * @param chapterNumber 章节号
     * @param originalPlotIdea 原始剧情构思
     * @param progress 当前模板进度
     * @param aiConfig AI配置（前端传来）
     * @return 增强后的构思
     */
    public String enhancePlotIdea(Long novelId, int chapterNumber, 
                                  String originalPlotIdea, NovelTemplateProgress progress,
                                  com.novel.dto.AIConfigRequest aiConfig) {
        
        // 如果未启用或章节号小于启动章节，直接返回原始构思
        if (!progress.getEnabled() || chapterNumber < progress.getStartChapter()) {
            return originalPlotIdea;
        }
        
        try {
            String currentStage = progress.getCurrentStage();
            int loopNumber = progress.getLoopNumber();
            
            log.info("增强构思 - 小说: {}, 章节: {}, 循环: {}, 阶段: {}", 
                    novelId, chapterNumber, loopNumber, currentStage);
            
            // 如果是动机阶段且尚无动机分析，先提取动机
            if ("MOTIVATION".equals(currentStage) && 
                (progress.getMotivationAnalysis() == null || progress.getMotivationAnalysis().isEmpty())) {
                
                String extractedMotivation = extractSustainableMotivation(originalPlotIdea, aiConfig);
                progress.setMotivationAnalysis(extractedMotivation);
                progressRepository.update(progress);
                
                log.info("提取可持续动机: {}", extractedMotivation);
            }
            
            // 构建模板引导内容
            String templateGuide = buildTemplateGuide(currentStage, loopNumber, progress);
            
            // 构造AI提示词
            String prompt = buildEnhancePrompt(originalPlotIdea, currentStage, loopNumber, 
                                              templateGuide, progress.getMotivationAnalysis());
            
            // 调用AI增强（使用前端传来的配置）
            String enhanced = aiWritingService.generateContentWithMessages(
                buildMessageList(prompt), "template_enhance", aiConfig
            );
            
            log.debug("原始构思: {}", originalPlotIdea);
            log.debug("增强构思: {}", enhanced);
            
            return enhanced;
            
        } catch (Exception e) {
            log.error("增强构思失败，使用原始构思 - 小说: {}, 章节: {}", novelId, chapterNumber, e);
            return originalPlotIdea; // 出错时降级为原始构思
        }
    }
    
    /**
     * 提取可持续的长篇动机
     * 不强行给动机，而是从现有剧情中提取并判断是否适合长期发展
     */
    private String extractSustainableMotivation(String plotIdea, com.novel.dto.AIConfigRequest aiConfig) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 任务：提取可持续的长篇动机\n\n");
        prompt.append("## 当前剧情构思\n");
        prompt.append(plotIdea).append("\n\n");
        prompt.append("## 你的任务\n");
        prompt.append("分析这个剧情构思，提取出主角的核心动机。注意：\n\n");
        prompt.append("1. **不要强行给动机**，而是从剧情中找出已经存在的动机\n");
        prompt.append("2. **判断这个动机是否适合长篇发展**：\n");
        prompt.append("   - 是否有足够的张力和冲突空间？\n");
        prompt.append("   - 能否支撑多次循环（至少50-100章）？\n");
        prompt.append("   - 是否能持续产生爽点？\n\n");
        prompt.append("3. **如果当前动机不够长期**，提出优化建议：\n");
        prompt.append("   - 如何扩展这个动机的深度和广度\n");
        prompt.append("   - 如何让这个动机更有持续性\n\n");
        prompt.append("## 输出格式（严格遵循）\n\n");
        prompt.append("【提取的动机】: （一句话概括主角的核心动机）\n\n");
        prompt.append("【长期性评估】: （评估这个动机是否适合长篇，给出GOOD/MODERATE/WEAK）\n\n");
        prompt.append("【动机分析】:\n");
        prompt.append("- 冲突空间: （这个动机能产生什么样的冲突）\n");
        prompt.append("- 发展潜力: （这个动机能支撑多少章节）\n");
        prompt.append("- 爽点来源: （基于这个动机能有哪些爽点）\n\n");
        prompt.append("【优化建议】: （如果评估不是GOOD，如何优化这个动机使其更适合长篇）\n\n");
        prompt.append("现在开始分析：\n");
        
        try {
            return aiWritingService.generateContentWithMessages(
                buildMessageList(prompt.toString()), "extract_motivation", aiConfig
            );
        } catch (Exception e) {
            log.warn("提取动机失败: {}", e.getMessage());
            return "动机提取失败，使用原始构思";
        }
    }
    
    /**
     * 构建消息列表（兼容 JDK 8）
     */
    private java.util.List<java.util.Map<String, String>> buildMessageList(String userPrompt) {
        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<java.util.Map<String, String>>();
        java.util.Map<String, String> message = new java.util.HashMap<String, String>();
        message.put("role", "user");
        message.put("content", userPrompt);
        messages.add(message);
        return messages;
    }
    
    /**
     * 构建模板引导内容
     */
    private String buildTemplateGuide(String currentStage, int loopNumber, NovelTemplateProgress progress) {
        TemplateStage stage = TemplateStage.valueOf(currentStage);
        StringBuilder guide = new StringBuilder();
        
        guide.append("**当前阶段：").append(stage.getDisplayName()).append("**\n");
        guide.append("**阶段说明：").append(stage.getDescription()).append("**\n\n");
        
        // 根据不同阶段提供具体引导
        switch (stage) {
            case MOTIVATION:
                guide.append("本章重点：\n");
                guide.append("1. 给主角一个强烈的行动动机\n");
                guide.append("2. 可以是：受到挑衅、遇到危机、发现机会、被人看不起\n");
                guide.append("3. 动机要合理且紧迫，让读者理解主角为什么必须行动\n");
                if (loopNumber > 1) {
                    guide.append("4. 注意：第").append(loopNumber).append("次循环，动机要比上一次更强、更紧迫\n");
                }
                break;
                
            case BONUS:
                guide.append("本章重点：\n");
                guide.append("1. 展示主角的独特优势或金手指\n");
                guide.append("2. 可以是：系统奖励、特殊能力、前世记忆、隐藏身份\n");
                guide.append("3. 让读者知道主角有底牌，有能力解决当前问题\n");
                guide.append("4. 不要完全暴露，留一点悬念\n");
                break;
                
            case CONFRONTATION:
                guide.append("本章重点：⭐核心爽点章节⭐\n");
                guide.append("1. 主角使用金手指或优势解决问题\n");
                guide.append("2. 关键技巧：先抑后扬，让对手先嘲笑或轻视\n");
                guide.append("3. 制造反差：主角的表现要远超敌人预期\n");
                guide.append("4. 要有打脸感和爽点，读者看了觉得解气\n");
                break;
                
            case RESPONSE:
                guide.append("本章重点：\n");
                guide.append("1. 描写多层次的震惊和反馈\n");
                guide.append("2. 第一层：现场敌人或对手的震惊恐惧\n");
                guide.append("3. 第二层：围观群众的议论和传播\n");
                guide.append("4. 第三层：权威人物或势力的评价和重视\n");
                guide.append("5. 层层递进，扩大影响力\n");
                break;
                
            case EARNING:
                guide.append("本章重点：\n");
                guide.append("1. 主角获得本次循环的收获\n");
                guide.append("2. 可以是：实力提升、获得宝物、结交盟友、名声提升\n");
                guide.append("3. 重要：在收获的同时，埋下新的伏笔\n");
                guide.append("4. 引出下一次循环的动机：更强的敌人、更大的舞台\n");
                break;
        }
        
        return guide.toString();
    }
    
    /**
     * 构建增强提示词（基于提取的动机）
     */
    private String buildEnhancePrompt(String originalPlotIdea, String currentStage, 
                                     int loopNumber, String templateGuide, String motivationAnalysis) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 任务：基于核心动机增强剧情构思\n\n");
        
        prompt.append("## 原始构思\n");
        prompt.append(originalPlotIdea).append("\n\n");
        
        prompt.append("## 当前模板状态\n");
        prompt.append("- 循环次数：第 ").append(loopNumber).append(" 次\n");
        prompt.append("- 当前阶段：").append(currentStage).append("\n\n");
        
        // 如果有动机分析，加入作为核心约束
        if (motivationAnalysis != null && !motivationAnalysis.isEmpty()) {
            prompt.append("## 核心动机（长期主线）\n");
            prompt.append(motivationAnalysis).append("\n\n");
            prompt.append("⚠️ **重要约束**：增强后的构思必须服务于这个核心动机，不能偏离主线\n\n");
        }
        
        prompt.append("## 本阶段引导\n");
        prompt.append(templateGuide).append("\n\n");
        
        prompt.append("## 你的任务\n");
        prompt.append("基于原始构思和核心动机，结合当前阶段，输出增强后的构思。\n\n");
        
        prompt.append("要求：\n");
        prompt.append("1. **紧扣核心动机**：每个情节都要推动核心动机的发展\n");
        prompt.append("2. **保留原始内容**：不改变原构思的核心情节\n");
        prompt.append("3. **加强阶段特征**：突出本阶段的重点（");
        prompt.append(TemplateStage.valueOf(currentStage).getDisplayName());
        prompt.append("）\n");
        prompt.append("4. **循环递进**：第 ").append(loopNumber).append(" 次循环要比前几次有所升级\n");
        prompt.append("5. **自然融合**：不要生硬地套用模板，要自然地引导\n");
        prompt.append("6. 输出格式：直接输出增强后的构思内容，不要任何额外说明\n\n");
        
        prompt.append("增强后的构思：\n");
        
        return prompt.toString();
    }
    
    /**
     * 基于剧情构思更新模板进度（新版，推荐）
     * 在写作前，基于剧情构思分析并推进阶段
     * 
     * @param novelId 小说ID
     * @param chapterNumber 章节号
     * @param plotIdea 剧情构思（完整响应）
     * @param aiConfig AI配置（前端传来）
     */
    @Async
    @Transactional
    public void updateProgressByPlotIdea(Long novelId, int chapterNumber, 
                                         String plotIdea,
                                         com.novel.dto.AIConfigRequest aiConfig) {
        try {
            NovelTemplateProgress progress = getOrInitProgress(novelId);
            
            // 如果未启用或章节号小于启动章节，不处理
            if (!progress.getEnabled() || chapterNumber < progress.getStartChapter()) {
                return;
            }
            
            String currentStage = progress.getCurrentStage();
            
            log.info("基于构思更新进度 - 小说: {}, 章节: {}, 当前阶段: {}", 
                    novelId, chapterNumber, currentStage);
            
            // 分析当前阶段的构思内容
            String stageAnalysis = analyzeStageContentFromPlotIdea(currentStage, plotIdea, aiConfig);
            updateStageAnalysis(progress, currentStage, stageAnalysis);
            
            // 判断当前阶段是否完成（基于构思）
            boolean stageComplete = checkStageCompletionFromPlotIdea(currentStage, plotIdea, 
                                                                     progress, aiConfig);
            
            if (stageComplete) {
                log.info("阶段完成 - 小说: {}, 章节: {}, 阶段: {} -> 推进到下一阶段", 
                        novelId, chapterNumber, currentStage);
                
                // 推进到下一阶段
                TemplateStage nextStage = TemplateStage.valueOf(currentStage).next();
                progress.setCurrentStage(nextStage.name());
                progress.setStageStartChapter(chapterNumber + 1);
                
                // 如果完成了整个循环（从EARNING回到MOTIVATION），循环次数+1
                if (nextStage == TemplateStage.MOTIVATION) {
                    progress.setLoopNumber(progress.getLoopNumber() + 1);
                    log.info("完成循环 - 小说: {}, 循环次数: {} -> {}", 
                            novelId, progress.getLoopNumber() - 1, progress.getLoopNumber());
                }
            }
            
            progress.setLastUpdatedChapter(chapterNumber);
            progressRepository.update(progress);
            
            log.info("进度已更新 - 小说: {}, 章节: {}, 新阶段: {}, 循环: {}", 
                    novelId, chapterNumber, progress.getCurrentStage(), progress.getLoopNumber());
            
        } catch (Exception e) {
            log.error("基于构思更新进度失败 - 小说: {}, 章节: {}", novelId, chapterNumber, e);
        }
    }
    
    /**
     * 异步更新模板进度（旧版，已废弃）
     * 在章节内容生成后调用，分析当前阶段是否完成，决定是否推进到下一阶段
     * 
     * @deprecated 使用 updateProgressByPlotIdea 代替（更快、更准确）
     */
    @Deprecated
    @Async
    @Transactional
    public void updateProgress(Long novelId, int chapterNumber, 
                               String generatedContent, String plotIdea,
                               com.novel.dto.AIConfigRequest aiConfig) {
        try {
            NovelTemplateProgress progress = getOrInitProgress(novelId);
            
            // 如果未启用或章节号小于启动章节，不处理
            if (!progress.getEnabled() || chapterNumber < progress.getStartChapter()) {
                return;
            }
            
            String currentStage = progress.getCurrentStage();
            
            log.info("更新进度 - 小说: {}, 章节: {}, 当前阶段: {}", 
                    novelId, chapterNumber, currentStage);
            
            // 分析当前阶段内容
            String stageAnalysis = analyzeStageContent(currentStage, generatedContent, plotIdea, aiConfig);
            updateStageAnalysis(progress, currentStage, stageAnalysis);
            
            // 判断当前阶段是否完成
            boolean stageComplete = checkStageCompletion(currentStage, generatedContent, 
                                                         plotIdea, progress, aiConfig);
            
            if (stageComplete) {
                log.info("阶段完成 - 小说: {}, 章节: {}, 阶段: {} -> 推进到下一阶段", 
                        novelId, chapterNumber, currentStage);
                
                // 推进到下一阶段
                TemplateStage nextStage = TemplateStage.valueOf(currentStage).next();
                progress.setCurrentStage(nextStage.name());
                progress.setStageStartChapter(chapterNumber + 1);
                
                // 如果完成了整个循环（从EARNING回到MOTIVATION），循环次数+1
                if (nextStage == TemplateStage.MOTIVATION) {
                    progress.setLoopNumber(progress.getLoopNumber() + 1);
                    log.info("完成循环 - 小说: {}, 循环次数: {} -> {}", 
                            novelId, progress.getLoopNumber() - 1, progress.getLoopNumber());
                }
            }
            
            progress.setLastUpdatedChapter(chapterNumber);
            progressRepository.update(progress);
            
            log.info("进度已更新 - 小说: {}, 章节: {}, 新阶段: {}, 循环: {}", 
                    novelId, chapterNumber, progress.getCurrentStage(), progress.getLoopNumber());
            
        } catch (Exception e) {
            log.error("更新进度失败 - 小说: {}, 章节: {}", novelId, chapterNumber, e);
        }
    }
    
    /**
     * 基于剧情构思分析当前阶段内容（新版，更准确）
     */
    private String analyzeStageContentFromPlotIdea(String currentStage, String plotIdea,
                                                   com.novel.dto.AIConfigRequest aiConfig) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 任务：分析剧情构思中关于\"");
        prompt.append(TemplateStage.valueOf(currentStage).getDisplayName());
        prompt.append("\"阶段的内容\n\n");
        
        prompt.append("## 剧情构思\n");
        prompt.append(plotIdea).append("\n\n");
        
        prompt.append("## 任务\n");
        prompt.append("从剧情构思中提取关于\"");
        prompt.append(TemplateStage.valueOf(currentStage).getDisplayName());
        prompt.append("\"阶段的关键信息。\n\n");
        
        prompt.append("要求：\n");
        prompt.append("1. 用1-2句话概括本章在该阶段的重点\n");
        prompt.append("2. 提取核心要素（人物、事件、冲突等）\n");
        prompt.append("3. 直接输出分析结果，不要标题或格式\n\n");
        
        prompt.append("分析结果：\n");
        
        try {
            return aiWritingService.generateContentWithMessages(
                buildMessageList(prompt.toString()), "stage_analysis_plot", aiConfig
            );
        } catch (Exception e) {
            log.warn("分析阶段内容失败: {}", e.getMessage());
            return "分析失败";
        }
    }
    
    /**
     * 分析当前阶段的内容（旧版，基于生成内容）
     * @deprecated 使用 analyzeStageContentFromPlotIdea 代替
     */
    @Deprecated
    private String analyzeStageContent(String currentStage, String content, String plotIdea,
                                      com.novel.dto.AIConfigRequest aiConfig) {
        int contentLength = content.length();
        int endIndex = contentLength > 1000 ? 1000 : contentLength;
        String contentPreview = content.substring(0, endIndex);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 任务：分析本章中关于\"");
        prompt.append(TemplateStage.valueOf(currentStage).getDisplayName());
        prompt.append("\"阶段的内容\n\n");
        
        prompt.append("## 本章构思\n");
        prompt.append(plotIdea).append("\n\n");
        
        prompt.append("## 本章内容（前1000字）\n");
        prompt.append(contentPreview).append("\n\n");
        
        prompt.append("## 任务\n");
        prompt.append("简要分析本章中关于\"");
        prompt.append(TemplateStage.valueOf(currentStage).getDisplayName());
        prompt.append("\"阶段的具体内容是什么。\n\n");
        
        prompt.append("要求：\n");
        prompt.append("1. 用1-2句话概括\n");
        prompt.append("2. 提取关键信息\n");
        prompt.append("3. 直接输出分析结果，不要任何标题或格式\n\n");
        
        prompt.append("分析结果：\n");
        
        try {
            return aiWritingService.generateContentWithMessages(
                buildMessageList(prompt.toString()), "stage_analysis", aiConfig
            );
        } catch (Exception e) {
            log.warn("分析阶段内容失败: {}", e.getMessage());
            return "分析失败";
        }
    }
    
    /**
     * 更新阶段分析到进度对象
     */
    private void updateStageAnalysis(NovelTemplateProgress progress, String stage, String analysis) {
        TemplateStage templateStage = TemplateStage.valueOf(stage);
        
        switch (templateStage) {
            case MOTIVATION:
                progress.setMotivationAnalysis(analysis);
                break;
            case BONUS:
                progress.setBonusAnalysis(analysis);
                break;
            case CONFRONTATION:
                progress.setConfrontationAnalysis(analysis);
                break;
            case RESPONSE:
                progress.setResponseAnalysis(analysis);
                break;
            case EARNING:
                progress.setEarningAnalysis(analysis);
                break;
        }
    }
    
    /**
     * 基于剧情构思判断当前阶段是否完成（新版，更准确）
     */
    private boolean checkStageCompletionFromPlotIdea(String currentStage, String plotIdea,
                                                     NovelTemplateProgress progress,
                                                     com.novel.dto.AIConfigRequest aiConfig) {
        TemplateStage stage = TemplateStage.valueOf(currentStage);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 任务：判断\"");
        prompt.append(stage.getDisplayName());
        prompt.append("\"阶段是否完成\n\n");
        
        prompt.append("## 当前阶段说明\n");
        prompt.append(stage.getDisplayName()).append(" - ");
        prompt.append(stage.getDescription()).append("\n\n");
        
        prompt.append("## 本章剧情构思\n");
        prompt.append(plotIdea).append("\n\n");
        
        prompt.append("## 判断标准\n");
        prompt.append(getStageCompletionCriteria(stage)).append("\n\n");
        
        prompt.append("## 问题\n");
        prompt.append("根据本章的剧情构思，判断\"");
        prompt.append(stage.getDisplayName());
        prompt.append("\"阶段是否已经充分规划，可以进入下一阶段？\n\n");
        
        prompt.append("要求：\n");
        prompt.append("1. 如果构思中已经充分体现了该阶段的核心内容，输出：YES\n");
        prompt.append("2. 如果该阶段还需要继续发展，输出：NO\n");
        prompt.append("3. 只输出YES或NO，不要任何解释\n\n");
        
        prompt.append("判断结果：\n");
        
        try {
            String result = aiWritingService.generateContentWithMessages(
                buildMessageList(prompt.toString()), "stage_completion_plot", aiConfig
            );
            boolean complete = result.trim().toUpperCase().contains("YES");
            log.debug("阶段完成判断（基于构思） - 阶段: {}, 结果: {}, AI输出: {}", 
                     currentStage, complete, result);
            return complete;
        } catch (Exception e) {
            log.warn("阶段完成判断失败，默认为未完成: {}", e.getMessage());
            return false; // 出错时保守处理，不推进阶段
        }
    }
    
    /**
     * 检查当前阶段是否完成（旧版，基于生成内容）
     * @deprecated 使用 checkStageCompletionFromPlotIdea 代替
     */
    @Deprecated
    private boolean checkStageCompletion(String currentStage, String content, 
                                        String plotIdea, NovelTemplateProgress progress,
                                        com.novel.dto.AIConfigRequest aiConfig) {
        TemplateStage stage = TemplateStage.valueOf(currentStage);
        
        int contentLength = content.length();
        int endIndex = contentLength > 1000 ? 1000 : contentLength;
        String contentPreview = content.substring(0, endIndex);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 任务：判断\"");
        prompt.append(stage.getDisplayName());
        prompt.append("\"阶段是否完成\n\n");
        
        prompt.append("## 当前阶段说明\n");
        prompt.append(stage.getDisplayName()).append(" - ");
        prompt.append(stage.getDescription()).append("\n\n");
        
        prompt.append("## 本章构思\n");
        prompt.append(plotIdea).append("\n\n");
        
        prompt.append("## 本章内容（前1000字）\n");
        prompt.append(contentPreview).append("\n\n");
        
        prompt.append("## 判断标准\n");
        prompt.append(getStageCompletionCriteria(stage)).append("\n\n");
        
        prompt.append("## 问题\n");
        prompt.append("根据本章的构思和内容，判断\"");
        prompt.append(stage.getDisplayName());
        prompt.append("\"阶段是否已经充分完成，可以进入下一阶段？\n\n");
        
        prompt.append("要求：\n");
        prompt.append("1. 如果本章已经充分展示了该阶段的核心内容，输出：YES\n");
        prompt.append("2. 如果该阶段还需要继续发展，输出：NO\n");
        prompt.append("3. 只输出YES或NO，不要任何解释\n\n");
        
        prompt.append("判断结果：\n");
        
        try {
            String result = aiWritingService.generateContentWithMessages(
                buildMessageList(prompt.toString()), "stage_completion", aiConfig
            );
            boolean complete = result.trim().toUpperCase().contains("YES");
            log.debug("阶段完成判断 - 阶段: {}, 结果: {}, AI输出: {}", 
                     currentStage, complete, result);
            return complete;
        } catch (Exception e) {
            log.warn("阶段完成判断失败，默认为未完成: {}", e.getMessage());
            return false; // 出错时保守处理，不推进阶段
        }
    }
    
    /**
     * 获取各阶段的完成标准
     */
    private String getStageCompletionCriteria(TemplateStage stage) {
        switch (stage) {
            case MOTIVATION:
                return "动机已清晰建立，主角有了明确的行动理由";
            case BONUS:
                return "金手指或主角优势已经展示，读者知道主角的底牌";
            case CONFRONTATION:
                return "核心冲突已经解决，打脸或爽点已经呈现";
            case RESPONSE:
                return "多层次的震惊和反馈已经描写完整";
            case EARNING:
                return "主角获得收获，并且埋下了新的伏笔或冲突";
            default:
                return "阶段内容已经充分展示";
        }
    }
    
    /**
     * 切换模板引擎开关
     */
    @Transactional
    public void toggleEnabled(Long novelId, boolean enabled) {
        NovelTemplateProgress progress = getOrInitProgress(novelId);
        progress.setEnabled(enabled);
        progressRepository.update(progress);
        log.info("小说 {} 模板引擎开关切换为: {}", novelId, enabled);
    }
    
    /**
     * 重置模板进度
     */
    @Transactional
    public void resetProgress(Long novelId) {
        progressRepository.deleteByNovelId(novelId);
        log.info("小说 {} 模板进度已重置", novelId);
    }
}

