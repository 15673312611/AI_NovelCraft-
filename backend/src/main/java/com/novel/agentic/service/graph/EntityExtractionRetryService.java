package com.novel.agentic.service.graph;

import com.novel.dto.AIConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体抽取重试服务
 * 
 * 解决问题：实体抽取失败时不应该静默忽略，应该记录并支持重试
 */
@Service
public class EntityExtractionRetryService {
    
    private static final Logger logger = LoggerFactory.getLogger(EntityExtractionRetryService.class);
    
    // 记录抽取失败的章节
    private final Map<String, FailedExtraction> failedExtractions = new ConcurrentHashMap<>();
    
    @Autowired
    private EntityExtractionService entityExtractionService;
    
    /**
     * 记录失败的抽取任务
     */
    public void recordFailure(Long novelId, Integer chapterNumber, String chapterTitle, 
                             String content, AIConfigRequest aiConfig, Exception error) {
        String key = novelId + "_" + chapterNumber;
        
        FailedExtraction failed = failedExtractions.computeIfAbsent(key, k -> {
            FailedExtraction f = new FailedExtraction();
            f.novelId = novelId;
            f.chapterNumber = chapterNumber;
            f.chapterTitle = chapterTitle;
            f.content = content;
            f.aiConfig = aiConfig;
            f.retryCount = 0;
            f.failures = new ArrayList<>();
            return f;
        });
        
        failed.retryCount++;
        failed.lastFailure = new Date();
        failed.failures.add(error.getMessage());
        
        logger.warn("⚠️ 实体抽取失败记录: novelId={}, chapter={}, 失败次数={}, 原因={}", 
            novelId, chapterNumber, failed.retryCount, error.getMessage());
        
        // 如果失败次数少于3次，自动重试
        if (failed.retryCount < 3 && failed.aiConfig != null && failed.aiConfig.isValid()) {
            logger.info("🔄 将在30秒后自动重试...");
            scheduleRetry(failed, 30000);
        } else {
            if (failed.aiConfig == null || !failed.aiConfig.isValid()) {
                logger.error("❌ 实体抽取失败且AI配置无效，已放弃自动重试: novelId={}, chapter={}",
                    novelId, chapterNumber);
            } else {
                logger.error("❌ 实体抽取失败超过3次，已放弃: novelId={}, chapter={}",
                    novelId, chapterNumber);
            }
        }
    }
    
    /**
     * 延迟重试
     */
    @Async
    protected void scheduleRetry(FailedExtraction failed, long delayMs) {
        try {
            Thread.sleep(delayMs);
            
            logger.info("🔄 重试实体抽取: novelId={}, chapter={}, 第{}次重试", 
                failed.novelId, failed.chapterNumber, failed.retryCount);

            if (failed.aiConfig == null || !failed.aiConfig.isValid()) {
                logger.error("❌ 跳过重试：AI配置无效。novelId={}, chapter={}", failed.novelId, failed.chapterNumber);
                return;
            }
            
            entityExtractionService.extractAndSave(
                failed.novelId, 
                failed.chapterNumber, 
                failed.chapterTitle, 
                failed.content,
                failed.aiConfig
            );
            
            // 成功后移除记录
            String key = failed.novelId + "_" + failed.chapterNumber;
            failedExtractions.remove(key);
            
            logger.info("✅ 重试成功: novelId={}, chapter={}", 
                failed.novelId, failed.chapterNumber);
            
        } catch (Exception e) {
            logger.error("🔄 重试失败: novelId={}, chapter={}", 
                failed.novelId, failed.chapterNumber, e);
            recordFailure(failed.novelId, failed.chapterNumber, failed.chapterTitle, 
                         failed.content, failed.aiConfig, e);
        }
    }
    
    /**
     * 获取所有失败的抽取任务
     */
    public List<Map<String, Object>> getFailedExtractions() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (FailedExtraction failed : failedExtractions.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("novelId", failed.novelId);
            info.put("chapterNumber", failed.chapterNumber);
            info.put("chapterTitle", failed.chapterTitle);
            info.put("retryCount", failed.retryCount);
            info.put("lastFailure", failed.lastFailure);
            info.put("failures", failed.failures);
            result.add(info);
        }
        
        return result;
    }
    
    /**
     * 手动重试失败的抽取
     */
    public void manualRetry(Long novelId, Integer chapterNumber) {
        String key = novelId + "_" + chapterNumber;
        FailedExtraction failed = failedExtractions.get(key);
        
        if (failed == null) {
            throw new IllegalArgumentException("未找到失败记录: " + key);
        }
        
        scheduleRetry(failed, 0);
    }
    
    /**
     * 失败记录
     */
    private static class FailedExtraction {
        Long novelId;
        Integer chapterNumber;
        String chapterTitle;
        String content;
        AIConfigRequest aiConfig;
        int retryCount;
        Date lastFailure;
        List<String> failures;
    }
}


