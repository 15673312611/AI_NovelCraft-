package com.novel.controller;

import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.Novel;
import com.novel.service.ChapterService;
import com.novel.service.NovelService;
import com.novel.service.LongNovelMemoryManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 章节控制器
 */
@RestController
@RequestMapping("/chapters")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class ChapterController {

    private static final Logger logger = LoggerFactory.getLogger(ChapterController.class);

    @Autowired
    private ChapterService chapterService;
    
    @Autowired
    private NovelService novelService;
    
    @Autowired
    private LongNovelMemoryManager longNovelMemoryManager;

    /**
     * 获取章节详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Chapter> getChapter(@PathVariable Long id) {
        try {
            Chapter chapter = chapterService.getChapter(id);
            if (chapter != null) {
                return ResponseEntity.ok(chapter);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("获取章节失败: chapterId={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取小说的所有章节
     */
    @GetMapping("/novel/{novelId}")
    public ResponseEntity<?> getNovelChapters(@PathVariable Long novelId,
                                              @RequestParam(value = "summary", required = false, defaultValue = "false") boolean summary) {
        try {
            java.util.List<Chapter> chapters = summary
                    ? chapterService.getChapterMetadataByNovel(novelId)
                    : chapterService.getChaptersByNovelId(novelId);
            return ResponseEntity.ok(chapters);
        } catch (Exception e) {
            logger.error("获取小说章节失败: novelId={}", novelId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 创建章节
     */
    @PostMapping
    public ResponseEntity<?> createChapter(@RequestBody Chapter chapter) {
        try {
            // 验证必要参数
            if (chapter.getNovelId() == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "参数错误");
                errorResponse.put("message", "novelId不能为空");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            if (chapter.getTitle() == null || chapter.getTitle().trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "参数错误");
                errorResponse.put("message", "章节标题不能为空");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 验证小说是否存在
            Novel novel = novelService.getNovel(chapter.getNovelId());
            if (novel == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "业务错误");
                errorResponse.put("message", "指定的小说不存在");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 验证章节编号是否重复
            if (chapter.getChapterNumber() != null) {
                java.util.List<Chapter> existingChapters = chapterService.getChaptersByNovelId(chapter.getNovelId());
                boolean numberExists = existingChapters.stream()
                    .anyMatch(c -> chapter.getChapterNumber().equals(c.getChapterNumber()));
                if (numberExists) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "业务错误");
                    errorResponse.put("message", "章节编号已存在: " + chapter.getChapterNumber());
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }
            
            logger.info("创建章节: novelId={}, title={}, chapterNumber={}", 
                       chapter.getNovelId(), chapter.getTitle(), chapter.getChapterNumber());
            
            Chapter createdChapter = chapterService.createChapter(chapter);
            return ResponseEntity.ok(createdChapter);
        } catch (Exception e) {
            logger.error("创建章节失败: title={}, novelId={}", chapter.getTitle(), chapter.getNovelId(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "创建章节失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 更新章节
     */
    @PutMapping("/{id}")
    public ResponseEntity<Chapter> updateChapter(@PathVariable Long id, @RequestBody Chapter chapterData) {
        try {
            Chapter updatedChapter = chapterService.updateChapter(id, chapterData);
            if (updatedChapter != null) {
                return ResponseEntity.ok(updatedChapter);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("更新章节失败: chapterId={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 删除章节
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChapter(@PathVariable Long id) {
        try {
            boolean deleted = chapterService.deleteChapter(id);
            if (deleted) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("删除章节失败: chapterId={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取章节统计信息
     */
    @GetMapping("/{id}/statistics")
    public ResponseEntity<Map<String, Object>> getChapterStatistics(@PathVariable Long id) {
        try {
            Map<String, Object> stats = chapterService.getChapterStatistics(id);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            // 返回默认统计信息
            Map<String, Object> defaultStats = new HashMap<>();
            defaultStats.put("wordCount", 0);
            defaultStats.put("readingTime", 0);
            defaultStats.put("lastModified", "");
            return ResponseEntity.ok(defaultStats);
        }
    }

    /**
     * 发布章节（支持前端传递AI配置）
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<Chapter> publishChapter(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> requestBody) {
        try {
            // 尝试从请求体中提取AI配置
            com.novel.dto.AIConfigRequest aiConfig = null;
            if (requestBody != null && !requestBody.isEmpty()) {
                try {
                    String provider = (String) requestBody.get("provider");
                    String apiKey = (String) requestBody.get("apiKey");
                    String model = (String) requestBody.get("model");
                    String baseUrl = (String) requestBody.get("baseUrl");
                    
                    if (provider != null && apiKey != null && model != null) {
                        aiConfig = new com.novel.dto.AIConfigRequest(provider, apiKey, model, baseUrl);
                        logger.info("发布章节时接收到AI配置: provider={}, model={}", provider, model);
                    }
                } catch (Exception e) {
                    logger.warn("解析AI配置失败: {}", e.getMessage());
                }
            }
            
            // 调用service层（使用前端配置或后端默认配置）
            Chapter publishedChapter;
            if (aiConfig != null && aiConfig.isValid()) {
                publishedChapter = chapterService.publishChapter(id, aiConfig);
            } else {
                logger.info("使用后端默认配置发布章节");
                @SuppressWarnings("deprecation")
                Chapter chapter = chapterService.publishChapter(id);
                publishedChapter = chapter;
            }
            
            return ResponseEntity.ok(publishedChapter);
        } catch (Exception e) {
            logger.error("发布章节失败: chapterId={}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 取消发布章节
     */
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<Chapter> unpublishChapter(@PathVariable Long id) {
        try {
            Chapter unpublishedChapter = chapterService.unpublishChapter(id);
            return ResponseEntity.ok(unpublishedChapter);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 概要章节内容并更新记忆库（支持前端传递AI配置）
     */
    @PostMapping("/{id}/summarize")
    public ResponseEntity<?> summarizeChapter(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> requestBody) {
        try {
            Chapter chapter = chapterService.getChapter(id);
            if (chapter == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "章节不存在");
                errorResponse.put("message", "未找到指定的章节");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            if (chapter.getContent() == null || chapter.getContent().trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "章节内容为空");
                errorResponse.put("message", "无法概要空章节");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 尝试从请求体中提取AI配置
            com.novel.dto.AIConfigRequest aiConfig = null;
            if (requestBody != null && !requestBody.isEmpty()) {
                try {
                    String provider = (String) requestBody.get("provider");
                    String apiKey = (String) requestBody.get("apiKey");
                    String model = (String) requestBody.get("model");
                    String baseUrl = (String) requestBody.get("baseUrl");
                    
                    if (provider != null && apiKey != null && model != null) {
                        aiConfig = new com.novel.dto.AIConfigRequest(provider, apiKey, model, baseUrl);
                        logger.info("概要章节时接收到AI配置: provider={}, model={}", provider, model);
                    }
                } catch (Exception e) {
                    logger.warn("解析AI配置失败: {}", e.getMessage());
                }
            }
            
            logger.info("开始概要章节: chapterId={}, novelId={}, chapterNumber={}, 使用AI配置={}", 
                       id, chapter.getNovelId(), chapter.getChapterNumber(), aiConfig != null);
            
            // 同步执行概要任务 - 直接提取当前章节信息，不需要加载记忆库
            Map<String, Object> extractedInfo;
            if (aiConfig != null && aiConfig.isValid()) {
                extractedInfo = longNovelMemoryManager.extractChapterInfo(
                    chapter.getNovelId(), 
                    chapter.getChapterNumber(), 
                    chapter.getContent(),
                    aiConfig
                );
            } else {
                logger.info("使用后端默认配置概要章节");
                extractedInfo = longNovelMemoryManager.extractChapterInfo(
                    chapter.getNovelId(), 
                    chapter.getChapterNumber(), 
                    chapter.getContent(),
                    null
                );
            }
            
            // 1. 提取章节概要并保存到chapter表
            String chapterSummary = null;
            if (extractedInfo.containsKey("chapterSummary")) {
                chapterSummary = (String) extractedInfo.get("chapterSummary");
                if (chapterSummary != null && !chapterSummary.trim().isEmpty()) {
                    chapter.setSummary(chapterSummary);
                    chapterService.updateChapter(id, chapter);
                    logger.info("✅ 章节概要已保存: chapterId={}, summary长度={}", id, chapterSummary.length());
                }
            }
            
            // 2. 保存提取的信息到记忆库
            longNovelMemoryManager.saveExtractedInfoToMemory(
                chapter.getNovelId(),
                chapter.getChapterNumber(),
                extractedInfo
            );
            
            logger.info("✅ 章节概要完成: chapterId={}, 提取到{}个角色, {}个事件", 
                       id,
                       getListSize(extractedInfo, "characterUpdates"),
                       getListSize(extractedInfo, "eventUpdates"));
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "章节概要完成");
            response.put("chapterId", id);
            response.put("characterCount", getListSize(extractedInfo, "characterUpdates"));
            response.put("eventCount", getListSize(extractedInfo, "eventUpdates"));
            response.put("summary", chapterSummary); // 返回概要内容
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("概要章节失败: chapterId={}", id, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "概要章节失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    private int getListSize(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof java.util.List) {
            return ((java.util.List<?>) value).size();
        }
        return 0;
    }
}
