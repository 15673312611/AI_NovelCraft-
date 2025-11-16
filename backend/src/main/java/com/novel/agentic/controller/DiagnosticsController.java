package com.novel.agentic.controller;

import com.novel.agentic.service.diagnostics.LongNovelDiagnosticsService;
import com.novel.agentic.service.graph.EntityExtractionRetryService;
import com.novel.agentic.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 诊断和监控API
 */
@RestController
@RequestMapping("/api/agentic/diagnostics")
public class DiagnosticsController {
    
    private static final Logger logger = LoggerFactory.getLogger(DiagnosticsController.class);
    
    @Autowired
    private LongNovelDiagnosticsService diagnosticsService;
    
    @Autowired(required = false)
    private EntityExtractionRetryService retryService;
    
    /**
     * 诊断小说生成系统健康状况
     */
    @GetMapping("/health/{novelId}")
    public ResponseEntity<Map<String, Object>> diagnoseNovel(@PathVariable Long novelId) {
        logger.info("🔍 诊断请求: novelId={}", novelId);
        
        Map<String, Object> report = diagnosticsService.diagnose(novelId);
        
        return ResponseEntity.ok(report);
    }
    
    /**
     * 获取长篇小说最佳实践建议
     */
    @GetMapping("/best-practices")
    public ResponseEntity<Map<String, Object>> getBestPractices() {
        return ResponseEntity.ok(diagnosticsService.getBestPractices());
    }
    
    /**
     * 获取实体抽取失败列表
     */
    @GetMapping("/failed-extractions")
    public ResponseEntity<?> getFailedExtractions() {
        if (retryService == null) {
            return ResponseEntity.ok(CollectionUtils.mapOf("message", "实体抽取服务未启用"));
        }
        
        return ResponseEntity.ok(retryService.getFailedExtractions());
    }
    
    /**
     * 手动重试失败的实体抽取
     */
    @PostMapping("/retry-extraction")
    public ResponseEntity<?> retryExtraction(
            @RequestParam Long novelId, 
            @RequestParam Integer chapterNumber) {
        
        if (retryService == null) {
            return ResponseEntity.ok(CollectionUtils.mapOf("message", "实体抽取服务未启用"));
        }
        
        try {
            retryService.manualRetry(novelId, chapterNumber);
            return ResponseEntity.ok(CollectionUtils.mapOf(
                "success", true, 
                "message", "重试任务已提交"
            ));
        } catch (Exception e) {
            logger.error("重试失败", e);
            return ResponseEntity.ok(CollectionUtils.mapOf(
                "success", false, 
                "message", e.getMessage()
            ));
        }
    }
}


