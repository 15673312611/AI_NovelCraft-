package com.novel.controller;

import com.novel.entity.NovelFolder;
import com.novel.service.NovelFolderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件夹控制器
 */
@RestController
@RequestMapping("/novels/{novelId}/folders")
@CrossOrigin(origins = "*")
@Slf4j
public class NovelFolderController {

    @Autowired
    private NovelFolderService folderService;

    /**
     * 获取小说的所有文件夹
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getFolders(@PathVariable Long novelId) {
        log.info("API: 获取小说ID={}的文件夹", novelId);
        try {
            List<NovelFolder> folders = folderService.getFoldersByNovelId(novelId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", folders);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文件夹失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 创建文件夹
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createFolder(
            @PathVariable Long novelId,
            @RequestBody NovelFolder folder) {
        log.info("API: 创建文件夹，小说ID={}", novelId);
        try {
            folder.setNovelId(novelId);
            NovelFolder created = folderService.createFolder(folder);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建文件夹失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 更新文件夹
     */
    @PutMapping("/{folderId}")
    public ResponseEntity<Map<String, Object>> updateFolder(
            @PathVariable Long novelId,
            @PathVariable Long folderId,
            @RequestBody NovelFolder folder) {
        log.info("API: 更新文件夹ID={}", folderId);
        try {
            folder.setId(folderId);
            folder.setNovelId(novelId);
            NovelFolder updated = folderService.updateFolder(folder);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新文件夹失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 删除文件夹
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteFolder(
            @PathVariable Long novelId,
            @PathVariable Long folderId) {
        log.info("API: 删除文件夹ID={}", folderId);
        try {
            folderService.deleteFolder(folderId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文件夹删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除文件夹失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

