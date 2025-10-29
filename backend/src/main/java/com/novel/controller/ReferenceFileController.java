package com.novel.controller;

import com.novel.entity.ReferenceFile;
import com.novel.service.ReferenceFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参考文件控制器
 */
@RestController
@RequestMapping("/novels/{novelId}/references")
@CrossOrigin(origins = "*")
@Slf4j
public class ReferenceFileController {

    @Autowired
    private ReferenceFileService referenceFileService;

    /**
     * 获取小说的所有参考文件
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getReferenceFiles(@PathVariable Long novelId) {
        log.info("API: 获取小说ID={}的参考文件", novelId);
        try {
            List<ReferenceFile> files = referenceFileService.getReferenceFilesByNovelId(novelId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", files);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取参考文件失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取参考文件详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReferenceFile(@PathVariable Long novelId, @PathVariable Long id) {
        log.info("API: 获取参考文件ID={}", id);
        try {
            ReferenceFile file = referenceFileService.getReferenceFileById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取参考文件失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(404).body(error);
        }
    }

    /**
     * 上传参考文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadReferenceFile(
            @PathVariable Long novelId,
            @RequestParam("file") MultipartFile file) {
        log.info("API: 上传参考文件，小说ID={}", novelId);
        try {
            ReferenceFile referenceFile = referenceFileService.uploadReferenceFile(novelId, file);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", referenceFile);
            response.put("message", "文件上传成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("上传参考文件失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 删除参考文件
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteReferenceFile(
            @PathVariable Long novelId,
            @PathVariable Long id) {
        log.info("API: 删除参考文件ID={}", id);
        try {
            referenceFileService.deleteReferenceFile(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "参考文件删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除参考文件失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

