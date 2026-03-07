package com.novel.controller;

import com.novel.dto.QimaoNovelDTO;
import com.novel.dto.QimaoStatisticsDTO;
import com.novel.entity.QimaoCategory;
import com.novel.entity.QimaoScraperTask;
import com.novel.entity.QimaoScraperConfig;
import com.novel.service.QimaoScraperService;
import com.novel.service.QimaoScheduledService;
import com.novel.mapper.QimaoScraperConfigMapper;
import com.novel.common.security.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 七猫小说爬虫控制器
 */
@Slf4j
@RestController
@RequestMapping("/qimao")
@Tag(name = "七猫小说爬虫", description = "七猫小说数据爬取和分析")
public class QimaoScraperController {

    @Autowired
    private QimaoScraperService qimaoScraperService;

    @Autowired
    private QimaoScheduledService scheduledService;

    @Autowired
    private QimaoScraperConfigMapper configMapper;

    /**
     * 开始爬取指定分类
     */
    @PostMapping("/scrape/{categoryCode}")
    @Operation(summary = "开始爬取", description = "异步爬取指定分类的小说数据")
    public ResponseEntity<Map<String, Object>> startScraping(
            @PathVariable String categoryCode,
            @RequestParam(defaultValue = "3") int maxPages) {
        
        // 验证管理员权限
        if (!AuthUtils.isAdmin()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "需要管理员权限");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            qimaoScraperService.scrapeCategory(categoryCode, maxPages);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "爬取任务已启动，正在后台执行");
            response.put("categoryCode", categoryCode);
            response.put("maxPages", maxPages);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动爬取任务失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "启动失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量爬取所有分类
     */
    @PostMapping("/scrape/all")
    @Operation(summary = "爬取所有分类", description = "批量爬取所有启用的分类")
    public ResponseEntity<Map<String, Object>> scrapeAll(
            @RequestParam(defaultValue = "3") int maxPages) {
        
        // 验证管理员权限
        if (!AuthUtils.isAdmin()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "需要管理员权限");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            List<QimaoCategory> categories = qimaoScraperService.getAllCategories();
            
            for (QimaoCategory category : categories) {
                qimaoScraperService.scrapeCategory(category.getCategoryCode(), maxPages);
                // 延迟避免同时启动太多任务
                Thread.sleep(2000);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "已启动 " + categories.size() + " 个爬取任务");
            response.put("categories", categories.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量爬取失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "启动失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    @Operation(summary = "获取分类列表", description = "获取所有可用的小说分类")
    public ResponseEntity<List<QimaoCategory>> getCategories() {
        List<QimaoCategory> categories = qimaoScraperService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * 获取统计数据
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取统计数据", description = "获取小说数据的统计分析")
    public ResponseEntity<QimaoStatisticsDTO> getStatistics() {
        QimaoStatisticsDTO statistics = qimaoScraperService.getStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取小说列表
     */
    @GetMapping("/novels")
    @Operation(summary = "获取小说列表", description = "分页获取小说数据")
    public ResponseEntity<Map<String, Object>> getNovels(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        List<QimaoNovelDTO> novels = qimaoScraperService.getNovels(category, status, page, pageSize);
        
        Map<String, Object> response = new HashMap<>();
        response.put("data", novels);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("total", novels.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务列表
     */
    @GetMapping("/tasks")
    @Operation(summary = "获取任务列表", description = "获取爬虫任务执行记录")
    public ResponseEntity<Map<String, Object>> getTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        List<QimaoScraperTask> tasks = qimaoScraperService.getTasks(page, pageSize);
        
        Map<String, Object> response = new HashMap<>();
        response.put("data", tasks);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("total", tasks.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取爬虫配置
     */
    @GetMapping("/config")
    @Operation(summary = "获取配置", description = "获取爬虫配置信息")
    public ResponseEntity<List<QimaoScraperConfig>> getConfig() {
        List<QimaoScraperConfig> configs = configMapper.selectList(null);
        return ResponseEntity.ok(configs);
    }

    /**
     * 更新爬虫配置
     */
    @PutMapping("/config/{key}")
    @Operation(summary = "更新配置", description = "更新指定配置项")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable String key,
            @RequestParam String value) {
        
        try {
            QimaoScraperConfig config = configMapper.selectOne(
                new QueryWrapper<QimaoScraperConfig>().eq("config_key", key)
            );
            
            if (config == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "配置项不存在: " + key);
                return ResponseEntity.badRequest().body(response);
            }
            
            config.setConfigValue(value);
            configMapper.updateById(config);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "配置更新成功");
            response.put("config", config);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新配置失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 手动触发定时任务检查
     */
    @PostMapping("/trigger-schedule")
    @Operation(summary = "触发定时任务", description = "手动触发定时任务检查")
    public ResponseEntity<Map<String, Object>> triggerSchedule() {
        // 验证管理员权限
        if (!AuthUtils.isAdmin()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "需要管理员权限");
            return ResponseEntity.status(403).body(response);
        }
        
        try {
            scheduledService.manualTrigger();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "定时任务已触发");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("触发定时任务失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "触发失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查爬虫服务状态")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", "qimao-scraper");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * 测试抓取单个URL（调试用）
     */
    @GetMapping("/test-url")
    @Operation(summary = "测试URL抓取", description = "测试抓取指定URL的效果")
    public ResponseEntity<Map<String, Object>> testUrl(
            @RequestParam String url,
            @RequestParam(defaultValue = "测试分类") String category) {
        
        try {
            Map<String, Object> result = qimaoScraperService.testScrapePage(url, category);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试抓取失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "测试失败: " + e.getMessage());
            response.put("error", e.toString());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 爬取单本小说的前N章
     */
    @PostMapping("/scrape-chapters/{novelId}")
    @Operation(summary = "爬取章节", description = "爬取指定小说的前N章内容")
    public ResponseEntity<Map<String, Object>> scrapeChapters(
            @PathVariable String novelId,
            @RequestParam(defaultValue = "3") int chapterCount) {
        
        try {
            Map<String, Object> result = qimaoScraperService.scrapeChapters(novelId, chapterCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("爬取章节失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "爬取失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量爬取多本小说的章节
     */
    @PostMapping("/scrape-chapters/batch")
    @Operation(summary = "批量爬取章节", description = "批量爬取多本小说的章节内容")
    public ResponseEntity<Map<String, Object>> batchScrapeChapters(
            @RequestBody Map<String, Object> request) {
        
        try {
            @SuppressWarnings("unchecked")
            List<String> novelIds = (List<String>) request.get("novelIds");
            Integer chapterCount = (Integer) request.getOrDefault("chapterCount", 3);
            Integer delaySeconds = (Integer) request.getOrDefault("delaySeconds", 3);
            
            if (novelIds == null || novelIds.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "小说ID列表不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failedCount = 0;
            
            for (int i = 0; i < novelIds.size(); i++) {
                String novelId = novelIds.get(i);
                try {
                    log.info("批量爬取进度: {}/{} - 小说ID: {}", i + 1, novelIds.size(), novelId);
                    Map<String, Object> result = qimaoScraperService.scrapeChapters(novelId, chapterCount);
                    results.add(result);
                    
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        successCount++;
                    } else {
                        failedCount++;
                    }
                    
                    // 延迟避免请求过快（除了最后一个）
                    if (i < novelIds.size() - 1) {
                        Thread.sleep(delaySeconds * 1000L);
                    }
                } catch (Exception e) {
                    log.error("批量爬取失败: {} - {}", novelId, e.getMessage());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("novelId", novelId);
                    errorResult.put("message", e.getMessage());
                    results.add(errorResult);
                    failedCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", novelIds.size());
            response.put("successCount", successCount);
            response.put("failedCount", failedCount);
            response.put("results", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量爬取失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "批量爬取失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
