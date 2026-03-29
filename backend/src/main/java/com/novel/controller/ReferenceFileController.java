package com.novel.controller;

import com.novel.dto.ReferenceFileDetailDto;
import com.novel.dto.ReferenceFileSummaryDto;
import com.novel.entity.ReferenceFile;
import com.novel.service.ReferenceFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/novels/{novelId}/references")
@CrossOrigin(origins = "*")
@Slf4j
public class ReferenceFileController {

    @Autowired
    private ReferenceFileService referenceFileService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReferenceFiles(@PathVariable Long novelId) {
        log.info("API: get reference files for novel={}", novelId);
        try {
            List<ReferenceFileSummaryDto> files = referenceFileService.getReferenceFilesByNovelId(novelId).stream()
                    .map(ReferenceFileSummaryDto::from)
                    .collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", files);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get reference files", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReferenceFile(@PathVariable Long novelId, @PathVariable Long id) {
        log.info("API: get reference file={}, novel={}", id, novelId);
        try {
            ReferenceFile file = referenceFileService.getReferenceFileByNovelId(novelId, id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", ReferenceFileDetailDto.from(file));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get reference file", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(404).body(error);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadReferenceFile(
            @PathVariable Long novelId,
            @RequestParam("file") MultipartFile file) {
        log.info("API: upload reference file for novel={}", novelId);
        try {
            ReferenceFile referenceFile = referenceFileService.uploadReferenceFile(novelId, file);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", ReferenceFileSummaryDto.from(referenceFile));
            response.put("message", "Reference file uploaded successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to upload reference file", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteReferenceFile(
            @PathVariable Long novelId,
            @PathVariable Long id) {
        log.info("API: delete reference file={}, novel={}", id, novelId);
        try {
            referenceFileService.getReferenceFileByNovelId(novelId, id);
            referenceFileService.deleteReferenceFile(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Reference file deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete reference file", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
