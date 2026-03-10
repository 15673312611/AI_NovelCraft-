package com.novel.shortstory.service;

import com.novel.shortstory.dto.WorkflowStateResponse;
import com.novel.shortstory.entity.ShortNovel;
import com.novel.shortstory.entity.ShortChapter;
import com.novel.shortstory.entity.WorkflowLog;
import com.novel.shortstory.repository.ShortNovelRepository;
import com.novel.shortstory.repository.ShortChapterRepository;
import com.novel.shortstory.repository.WorkflowLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShortStoryServiceImpl implements ShortStoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(ShortStoryServiceImpl.class);
    
    @Autowired
    private ShortNovelRepository novelRepository;
    
    @Autowired
    private ShortChapterRepository chapterRepository;
    
    @Autowired
    private WorkflowLogRepository logRepository;
    
    @Autowired
    private ShortStoryWorkflowEngine workflowEngine;
    
    // 存储正在运行的工作流任务（novelId -> Thread）
    private final ConcurrentHashMap<Long, Thread> runningWorkflows = new ConcurrentHashMap<>();
    
    @Override
    @Transactional
    public ShortNovel createNovel(ShortNovel novel) {
        return novelRepository.save(novel);
    }
    
    @Override
    public ShortNovel getNovel(Long id) {
        return novelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("短篇小说不存在"));
    }
    
    @Override
    public List<ShortNovel> getUserNovels(Long userId) {
        return novelRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }
    
    
    @Override
    public List<ShortChapter> getChapters(Long novelId) {
        return chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
    }
    
    

    @Override
    @Transactional
    public ShortChapter updateChapterContent(Long novelId, Integer chapterNumber, String content) {
        ShortChapter chapter = chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber)
            .orElseThrow(() -> new RuntimeException("章节不存在"));

        chapter.setContent(content);
        chapter.setWordCount(content != null ? content.length() : 0);
        return chapterRepository.save(chapter);
    }
    
    @Override
    public void startWorkflow(Long novelId) {
        ShortNovel novel = getNovel(novelId);
        
        // 检查是否已在运行
        if (runningWorkflows.containsKey(novelId)) {
            throw new RuntimeException("工作流已在运行中");
        }
        
        // 检查状态
        if ("COMPLETED".equals(novel.getStatus())) {
            throw new RuntimeException("小说已完成，无需再次生成");
        }
        
        // 标记为运行中
        novel.setStatus("WORKFLOW_RUNNING");
        novelRepository.save(novel);
        
        // 异步启动工作流
        Thread workflowThread = new Thread(() -> {
            try {
                workflowEngine.execute(novelId);
            } catch (Exception e) {
                logger.error("工作流执行失败: novelId={}", novelId, e);
                
                // 更新状态为失败
                ShortNovel failedNovel = novelRepository.findById(novelId).orElse(null);
                if (failedNovel != null) {
                    failedNovel.setStatus("FAILED");
                    failedNovel.setErrorMessage(e.getMessage());
                    novelRepository.save(failedNovel);
                }
            } finally {
                runningWorkflows.remove(novelId);
            }
        }, "workflow-" + novelId);
        
        runningWorkflows.put(novelId, workflowThread);
        workflowThread.start();
        
        logger.info("工作流已启动: novelId={}", novelId);
    }
    
    @Override
    @Transactional
    public void pauseWorkflow(Long novelId) {
        Thread workflowThread = runningWorkflows.remove(novelId);
        
        if (workflowThread != null && workflowThread.isAlive()) {
            workflowThread.interrupt();
            logger.info("工作流已暂停: novelId={}", novelId);
        }
        
        ShortNovel novel = novelRepository.findById(novelId).orElse(null);
        if (novel != null && ("WORKFLOW_RUNNING".equals(novel.getStatus()) || "GENERATING_OUTLINE".equals(novel.getStatus()))) {
            novel.setStatus("WORKFLOW_PAUSED");
            novelRepository.save(novel);
        }
    }
    
    @Override
    @Transactional
    public void retryChapter(Long novelId, Integer chapterNumber) {
        ShortNovel novel = getNovel(novelId);
        
        // 重置章节状态
        ShortChapter chapter = chapterRepository.findByNovelIdAndChapterNumber(novelId, chapterNumber)
            .orElseThrow(() -> new RuntimeException("章节不存在"));
        
        chapter.setStatus("PENDING");
        chapter.setContent(null);
        chapter.setReviewResult(null);
        chapter.setAnalysisResult(null);
        chapter.setLastAdjustment(null);
        chapter.setWordCount(0);
        chapter.setGenerationTime(null);
        chapterRepository.save(chapter);
        
        // 重置小说状态
        novel.setCurrentChapter(chapterNumber - 1);
        novel.setCurrentRetryCount(0);
        novel.setActiveStep("CHAPTER_" + chapterNumber + "_GENERATE");
        novel.setStatus("WORKFLOW_PAUSED");
        novelRepository.save(novel);
        
        logger.info("章节已重置: novelId={}, chapterNumber={}", novelId, chapterNumber);
    }
    
    @Override
    public List<WorkflowLog> getLogs(Long novelId, Pageable pageable) {
        return logRepository.findByNovelIdOrderByCreatedAtDesc(novelId, pageable);
    }

    @Override
    public WorkflowStateResponse getWorkflowState(Long novelId) {
        ShortNovel novel = getNovel(novelId);
        List<ShortChapter> chapters = getChapters(novelId);

        WorkflowStateResponse resp = new WorkflowStateResponse();
        resp.setNovelId(novelId);
        resp.setStatus(novel.getStatus());
        resp.setActiveStep(novel.getActiveStep());
        resp.setChapterCount(novel.getChapterCount());
        resp.setCurrentChapter(novel.getCurrentChapter());

        String active = novel.getActiveStep();

        // 全局步骤
        resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                "STORY_SETTING",
                "故事设定",
                stepStatus(active, "STORY_SETTING", isNotBlank(novel.getStorySetting()), false),
                null,
                "生成世界观/人物/风格等基础设定"
        ));
        resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                "OUTLINE",
                "生成短篇大纲",
                stepStatus(active, "OUTLINE", isNotBlank(novel.getOutline()), false),
                null,
                "生成完整大纲与章节走向"
        ));
        resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                "HOOKS",
                "生成看点标题+剧情核心",
                stepStatus(active, "HOOKS", !chapters.isEmpty() || isNotBlank(novel.getHooksJson()), false),
                null,
                "为每章设计标题与一句话剧情核心"
        ));
        resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                "PROLOGUE",
                "生成导语",
                stepStatus(active, "PROLOGUE", isNotBlank(novel.getPrologue()), false),
                null,
                "生成黄金开头/导语片段"
        ));

        // 按章节构建步骤
        for (int i = 1; i <= (novel.getChapterCount() != null ? novel.getChapterCount() : 0); i++) {
            final int chapterNum = i;
            ShortChapter ch = chapters.stream().filter(c -> c.getChapterNumber() != null && c.getChapterNumber() == chapterNum).findFirst().orElse(null);
            boolean failed = ch != null && "FAILED".equals(ch.getStatus());
            boolean contentReady = ch != null && isNotBlank(ch.getContent());
            boolean reviewReady = ch != null && isNotBlank(ch.getReviewResult());
            boolean analysisReady = ch != null && isNotBlank(ch.getAnalysisResult());
            boolean committed = ch != null && "COMPLETED".equals(ch.getStatus());

            resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                    "CHAPTER_" + i + "_GENERATE",
                    "生成第" + i + "章",
                    stepStatus(active, "CHAPTER_" + i + "_GENERATE", contentReady, failed),
                    i,
                    "根据看点核心生成正文"
            ));
            resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                    "CHAPTER_" + i + "_REVIEW",
                    "审核第" + i + "章",
                    stepStatus(active, "CHAPTER_" + i + "_REVIEW", reviewReady, failed),
                    i,
                    "AI审稿打分，未通过则返工重写"
            ));
            resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                    "CHAPTER_" + i + "_ANALYZE",
                    "看点分析",
                    stepStatus(active, "CHAPTER_" + i + "_ANALYZE", analysisReady, failed),
                    i,
                    "分析看点/风险，推演后续"
            ));
            resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                    "CHAPTER_" + i + "_DECIDE",
                    "调整决策",
                    stepStatus(active, "CHAPTER_" + i + "_DECIDE", analysisReady, failed),
                    i,
                    "判断是否调整大纲或后续标题核心"
            ));
            resp.getSteps().add(new WorkflowStateResponse.WorkflowStep(
                    "CHAPTER_" + i + "_COMMIT",
                    "封装入库",
                    stepStatus(active, "CHAPTER_" + i + "_COMMIT", committed, failed),
                    i,
                    "保存章节，准备进入下一章"
            ));
        }

        return resp;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String stepStatus(String activeStep, String key, boolean completed, boolean failed) {
        if (activeStep != null && activeStep.equals(key)) {
            return "RUNNING";
        }
        if (failed) {
            return "FAILED";
        }
        if (completed) {
            return "COMPLETED";
        }
        return "PENDING";
    }
    
    @Override
    @Transactional
    public void updateConfig(Long novelId, Map<String, String> config) {
        ShortNovel novel = getNovel(novelId);
        
        // 只允许在 DRAFT 或 WORKFLOW_PAUSED 状态下修改配置
        if (!"DRAFT".equals(novel.getStatus()) && !"WORKFLOW_PAUSED".equals(novel.getStatus())) {
            throw new RuntimeException("只能在未启动或已暂停状态下修改配置");
        }
        
        // 构建 workflowConfig JSON
        String modelId = config.get("modelId");
        if (modelId != null && !modelId.isEmpty()) {
            novel.setWorkflowConfig("{\"modelId\":\"" + modelId + "\"}");
            novelRepository.save(novel);
            logger.info("已更新工作流配置: novelId={}, modelId={}", novelId, modelId);
        }
    }
}
