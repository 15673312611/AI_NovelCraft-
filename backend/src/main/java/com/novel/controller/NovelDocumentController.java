package com.novel.controller;

import com.novel.entity.NovelDocument;
import com.novel.service.NovelDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档控制器
 */
@RestController
@CrossOrigin(origins = "*")
@Slf4j
public class NovelDocumentController {

    @Autowired
    private NovelDocumentService documentService;

    @Autowired
    private com.novel.service.WritingStudioService writingStudioService;

    /**
     * 获取文件夹下的所有文档
     */
    @GetMapping("/folders/{folderId}/documents")
    public ResponseEntity<Map<String, Object>> getDocumentsByFolder(@PathVariable Long folderId) {
        log.info("API: 获取文件夹ID={}的文档", folderId);
        try {
            List<NovelDocument> documents = documentService.getDocumentsByFolderId(folderId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", documents);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文档失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable Long id) {
        log.info("API: 获取文档ID={}", id);
        try {
            NovelDocument document = documentService.getDocumentById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", document);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文档失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(404).body(error);
        }
    }

    /**
     * 创建文档
     */
    @PostMapping("/folders/{folderId}/documents")
    public ResponseEntity<Map<String, Object>> createDocument(
            @PathVariable Long folderId,
            @RequestBody NovelDocument document) {
        log.info("API: 创建文档");
        try {
            document.setFolderId(folderId);
            NovelDocument created = documentService.createDocument(document);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建文档失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 更新文档
     */
    @PutMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> updateDocument(
            @PathVariable Long id,
            @RequestBody NovelDocument document) {
        log.info("API: 更新文档ID={}", id);
        try {
            document.setId(id);
            NovelDocument updated = documentService.updateDocument(document);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新文档失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 自动保存文档
     */
    @PostMapping("/documents/{id}/auto-save")
    public ResponseEntity<Map<String, Object>> autoSaveDocument(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        log.info("API: 自动保存文档ID={}", id);
        try {
            String content = payload.get("content");
            documentService.autoSaveDocument(id, content);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "自动保存成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("自动保存失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        log.info("API: 删除文档ID={}", id);
        try {
            documentService.deleteDocument(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文档删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除文档失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 搜索文档
     */
    @GetMapping("/novels/{novelId}/documents/search")
    public ResponseEntity<Map<String, Object>> searchDocuments(
            @PathVariable Long novelId,
            @RequestParam String keyword) {
        log.info("API: 搜索小说ID={}的文档，关键词={}", novelId, keyword);
        try {
            List<NovelDocument> documents = documentService.searchDocuments(novelId, keyword);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", documents);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("搜索文档失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 初始化写作工作室（包含文件夹和第一章）
     */
    @PostMapping("/novels/{novelId}/init-folders")
    public ResponseEntity<Map<String, Object>> initDefaultFolders(@PathVariable Long novelId) {
        log.info("API: 初始化小说ID={}的写作工作室", novelId);
        try {
            writingStudioService.initWritingStudio(novelId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "写作工作室初始化成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("初始化写作工作室失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

}

